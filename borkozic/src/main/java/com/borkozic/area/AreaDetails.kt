package com.borkozic.area
import com.borkozic.navigation.BaseNavigationService
import com.borkozic.BaseApplication

import android.app.ListActivity
import android.content.*
import android.os.Build
import androidx.core.content.ContextCompat
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.IBinder
import android.preference.PreferenceManager
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.TextView
import android.widget.Toast
import com.borkozic.Borkozic
import com.borkozic.R
import com.borkozic.data.Area
import com.borkozic.data.Waypoint
import com.borkozic.navigation.NavigationService
import com.borkozic.route.RouteDetails
import com.borkozic.util.StringFormatter
import com.borkozic.waypoint.WaypointProperties
import net.londatiga.android.ActionItem
import net.londatiga.android.QuickAction

class AreaDetails : ListActivity(), AdapterView.OnItemClickListener {

    private var navigationService: NavigationService? = null
    private lateinit var adapter: WaypointListAdapter
    private lateinit var quickAction: QuickAction

    private lateinit var area: Area
    private var navigation = false
    private var selectedPosition = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        val index = intent.extras!!.getInt("index")
        navigation = intent.extras!!.getBoolean("nav")

        val application = application as Borkozic
        area = application.getArea(index)!!

        title = if (navigation) "› " + area.name else area.name

        adapter = WaypointListAdapter(this, area)
        listAdapter = adapter

        val resources = resources
        quickAction = QuickAction(this)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            quickAction.addActionItem(ActionItem(qaWaypointVisible, getString(R.string.menu_view), resources.getDrawable(R.drawable.ic_action_show, null)))
        } else {
            quickAction.addActionItem(ActionItem(qaWaypointVisible, getString(R.string.menu_view), resources.getDrawable(R.drawable.ic_action_show)))
        }

        if (navigation) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                quickAction.addActionItem(ActionItem(qaWaypointNavigate, getString(R.string.menu_navigate), resources.getDrawable(R.drawable.ic_action_show, null)))
            } else {
                quickAction.addActionItem(ActionItem(qaWaypointNavigate, getString(R.string.menu_navigate), resources.getDrawable(R.drawable.ic_action_show)))
            }
        } else {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                quickAction.addActionItem(ActionItem(qaWaypointProperties, getString(R.string.menu_edit), resources.getDrawable(R.drawable.ic_action_show, null)))
            } else {
                quickAction.addActionItem(ActionItem(qaWaypointProperties, getString(R.string.menu_edit), resources.getDrawable(R.drawable.ic_action_show)))
            }
        }
        quickAction.setOnActionItemClickListener(actionItemClickListener)
        listView.onItemClickListener = this
    }

    override fun onResume() {
        super.onResume()
        if (navigation) {
            bindService(Intent(this, NavigationService::class.java), navigationConnection, BIND_AUTO_CREATE)
            val lock = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(getString(R.string.pref_wakelock), resources.getBoolean(R.bool.def_wakelock))
            if (lock) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun onPause() {
        super.onPause()
        if (navigation) {
            unregisterReceiver(navigationReceiver)
            unbindService(navigationConnection)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (!navigation) {
            val inflater = menuInflater
            inflater.inflate(R.menu.routedetails_menu, menu)
        }
        return true
    }

    override fun onItemClick(parent: AdapterView<*>, view: View, position: Int, id: Long) {
        selectedPosition = position
        quickAction.show(view)
    }

    private val actionItemClickListener = object : QuickAction.OnActionItemClickListener {
        override fun onItemClick(source: QuickAction, pos: Int, actionId: Int) {
            val application: Borkozic = BaseApplication.getApplication<Borkozic>()!!
            when (actionId) {
                qaWaypointVisible -> {
                    area.show = true
                    application.ensureVisible(area.getWaypoint(selectedPosition))
                    setResult(RESULT_OK)
                    finish()
                }
                qaWaypointNavigate -> {
                    val ns = navigationService
                    if (ns != null) {
                        if (ns.navDirection == BaseNavigationService.DIRECTION_REVERSE)
                            selectedPosition = area.length() - selectedPosition - 1
                        ns.setRouteWaypoint(selectedPosition)
                        adapter.notifyDataSetChanged()
                    }
                }
                qaWaypointProperties -> {
                    val index = application.getAreaIndex(area)
                    startActivity(Intent(this@AreaDetails, WaypointProperties::class.java).putExtra("INDEX", selectedPosition).putExtra("ROUTE", index + 1))
                }
            }
        }
    }

    private val navigationConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            navigationService = (service as NavigationService.LocalBinder).getService()
            ContextCompat.registerReceiver(this@AreaDetails, navigationReceiver, IntentFilter(BaseNavigationService.BROADCAST_NAVIGATION_STATUS), ContextCompat.RECEIVER_NOT_EXPORTED)
            ContextCompat.registerReceiver(this@AreaDetails, navigationReceiver, IntentFilter(BaseNavigationService.BROADCAST_NAVIGATION_STATE), ContextCompat.RECEIVER_NOT_EXPORTED)
            Log.d(TAG, "Navigation broadcast receiver registered")
            runOnUiThread { adapter.notifyDataSetChanged() }
        }

        override fun onServiceDisconnected(className: ComponentName) {
            unregisterReceiver(navigationReceiver)
            navigationService = null
        }
    }

    private val navigationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.e(TAG, "Broadcast: " + intent.action)
            if (intent.action == BaseNavigationService.BROADCAST_NAVIGATION_STATE) {
                val state = intent.extras!!.getInt("state")
                runOnUiThread {
                    if (state == BaseNavigationService.STATE_REACHED) {
                        Toast.makeText(applicationContext, R.string.arrived, Toast.LENGTH_LONG).show()
                        navigation = false
                    }
                    adapter.notifyDataSetChanged()
                }
            }
            if (intent.action == BaseNavigationService.BROADCAST_NAVIGATION_STATUS) {
                runOnUiThread { adapter.notifyDataSetChanged() }
            }
        }
    }

    inner class WaypointListAdapter(context: Context, private val mArea: Area) : BaseAdapter() {
        private val mInflater: LayoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        private val mItemLayout: Int = R.layout.area_waypoint_list

        override fun getItem(position: Int): Waypoint {
            var pos = position
            if (navigation && navigationService != null && navigationService!!.navDirection == BaseNavigationService.DIRECTION_REVERSE)
                pos = mArea.length() - pos - 1
            return mArea.getWaypoint(pos)
        }

        override fun getItemId(position: Int): Long {
            var pos = position
            if (navigation && navigationService != null && navigationService!!.navDirection == BaseNavigationService.DIRECTION_REVERSE)
                pos = mArea.length() - pos - 1
            return pos.toLong()
        }

        override fun getCount(): Int {
            return mArea.length()
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v: View = if (convertView == null) {
                mInflater.inflate(mItemLayout, parent, false)
            } else {
                convertView
            }
            val wpt = getItem(position)
            var text: TextView? = v.findViewById<TextView>(R.id.name)
            val txtAlt: TextView? = v.findViewById<TextView>(R.id.altitude)
            if (text != null) {
                text.text = wpt.name
                val dist = StringFormatter.distanceC(wpt.altitude, 10000)
                val alt = dist[0] + dist[1]
                txtAlt?.text = alt
            }
            if (navigation && navigationService != null && navigationService!!.isNavigatingViaRoute()) {
                val progress = position - navigationService!!.navRouteCurrentIndex()
                if (position > 0) {
                    val dist = if (progress == 0) navigationService!!.navDistance else mArea.distanceBetween(position - 1, position)
                    val distance = StringFormatter.distanceH(dist)
                    text = v.findViewById<TextView>(R.id.distance)
                    text?.text = distance
                    val crs: Double = if (progress == 0)
                        navigationService!!.navBearing
                    else if (navigationService!!.navDirection == BaseNavigationService.DIRECTION_FORWARD)
                        mArea.course(position - 1, position)
                    else
                        mArea.course(position, position - 1)
                    val course = StringFormatter.bearingH(crs)
                    text = v.findViewById<TextView>(R.id.course)
                    text?.text = course
                }
                if (progress >= 0) {
                    var dist = navigationService!!.navDistance
                    if (progress > 0)
                        dist += navigationService!!.navRouteDistanceLeftTo(position)
                    val distance = StringFormatter.distanceH(dist)
                    text = v.findViewById<TextView>(R.id.total_distance)
                    text?.text = distance
                    val ete = if (progress == 0) navigationService!!.navETE else navigationService!!.navRouteWaypointETE(position)
                    var s = StringFormatter.timeR(ete)
                    text = v.findViewById<TextView>(R.id.ete)
                    text?.text = s
                    var eta = navigationService!!.navETE
                    if (progress > 0 && eta < Integer.MAX_VALUE) {
                        val t = navigationService!!.navRouteETETo(position)
                        if (t < Integer.MAX_VALUE)
                            eta += t
                    }
                    s = StringFormatter.timeR(eta)
                    text = v.findViewById<TextView>(R.id.eta)
                    text?.text = s
                    if (progress == 0) {
                        text = v.findViewById<TextView>(R.id.name)
                        text?.text = "» " + text?.text
                    }
                } else {
                    text = v.findViewById<TextView>(R.id.name)
                    text?.setTextColor(text.textColors.withAlpha(128))
                    text = v.findViewById<TextView>(R.id.distance)
                    text?.setTextColor(text.textColors.withAlpha(128))
                    text = v.findViewById<TextView>(R.id.course)
                    text?.setTextColor(text.textColors.withAlpha(128))
                }
            } else {
                if (position > 0) {
                    val dist = mArea.distanceBetween(position - 1, position)
                    val distance = StringFormatter.distanceH(dist)
                    text = v.findViewById<TextView>(R.id.distance)
                    text?.text = distance
                    val crs = mArea.course(position - 1, position)
                    val course = StringFormatter.bearingH(crs)
                    text = v.findViewById<TextView>(R.id.course)
                    text?.text = course
                }
                val dist = if (position > 0) mArea.distanceBetween(0, position) else 0.0
                val distance = StringFormatter.distanceH(dist)
                text = v.findViewById<TextView>(R.id.total_distance)
                text?.text = distance
            }
            return v
        }

        override fun hasStableIds(): Boolean {
            return true
        }
    }

    companion object {
        private const val TAG = "AreaDetails"
        private const val RESULT_START_ROUTE = 1
        private const val qaWaypointVisible = 1
        private const val qaWaypointNavigate = 2
        private const val qaWaypointProperties = 3
    }
}