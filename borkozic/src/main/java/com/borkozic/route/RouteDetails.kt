/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2012  Andrey Novikov <http://andreynovikov.info/>
 *
 * This file is part of Androzic application.
 *
 * Androzic is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * Androzic is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with Androzic.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.borkozic.route

import com.borkozic.BaseApplication
import com.borkozic.navigation.BaseNavigationService

import android.app.ListActivity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.content.res.Resources
import android.os.Bundle
import android.os.IBinder
import android.preference.PreferenceManager
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.BaseAdapter
import android.widget.TextView
import android.widget.Toast

import com.borkozic.Borkozic
import com.borkozic.R
import com.borkozic.data.Route
import com.borkozic.data.Waypoint
import com.borkozic.navigation.NavigationService
import com.borkozic.util.StringFormatter
import com.borkozic.waypoint.WaypointProperties

import net.londatiga.android.ActionItem
import net.londatiga.android.QuickAction
import net.londatiga.android.QuickAction.OnActionItemClickListener

class RouteDetails : ListActivity(), OnItemClickListener {
    private var navigationService: NavigationService? = null
    private lateinit var adapter: WaypointListAdapter
    private lateinit var quickAction: QuickAction

    private lateinit var route: Route
    private var navigation = false
    private var selectedPosition = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        val index = intent.extras!!.getInt("index")
        navigation = intent.extras!!.getBoolean("nav")

        val application = BaseApplication.getApplication<Borkozic>()!!
        route = application.getRoute(index)!!

        setTitle(if (navigation) "› ${route.name}" else route.name)

        adapter = WaypointListAdapter(this, route)
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
                quickAction.addActionItem(ActionItem(qaWaypointVisible, getString(R.string.menu_navigate), resources.getDrawable(R.drawable.ic_action_show, null)))
            } else {
                quickAction.addActionItem(ActionItem(qaWaypointVisible, getString(R.string.menu_navigate), resources.getDrawable(R.drawable.ic_action_show)))
            }
        } else {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                quickAction.addActionItem(ActionItem(qaWaypointVisible, getString(R.string.menu_edit), resources.getDrawable(R.drawable.ic_action_show, null)))
            } else {
                quickAction.addActionItem(ActionItem(qaWaypointVisible, getString(R.string.menu_edit), resources.getDrawable(R.drawable.ic_action_show)))
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menuStartNavigation -> {
                val application = BaseApplication.getApplication<Borkozic>()!!
                val index = application.getRouteIndex(route)
                startActivityForResult(Intent(this, RouteStart::class.java).putExtra("index", index), RESULT_START_ROUTE)
                return true
            }
        }
        return false
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            RESULT_START_ROUTE -> {
                if (resultCode == RESULT_OK) {
                    setResult(RESULT_OK)
                    finish()
                }
            }
        }
    }

    override fun onItemClick(parent: AdapterView<*>, view: View, position: Int, id: Long) {
        selectedPosition = position
        quickAction.show(view)
    }

    private val actionItemClickListener = object : OnActionItemClickListener {
        override fun onItemClick(source: QuickAction, pos: Int, actionId: Int) {
            val application = BaseApplication.getApplication<Borkozic>()!!
            when (actionId) {
                qaWaypointVisible -> {
                    route.show = true
                    application.ensureVisible(route.getWaypoint(selectedPosition))
                    setResult(RESULT_OK)
                    finish()
                }
                qaWaypointNavigate -> {
                    if (navigationService != null) {
                        if (navigationService!!.navDirection == BaseNavigationService.DIRECTION_REVERSE)
                            selectedPosition = route.length() - selectedPosition - 1
                        navigationService!!.setRouteWaypoint(selectedPosition)
                        adapter.notifyDataSetChanged()
                    }
                }
                qaWaypointProperties -> {
                    val index = application.getRouteIndex(route)
                    startActivity(Intent(this@RouteDetails, WaypointProperties::class.java).putExtra("INDEX", selectedPosition).putExtra("ROUTE", index + 1))
                }
            }
        }
    }

    private val navigationConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            navigationService = (service as NavigationService.LocalBinder).getService()
            ContextCompat.registerReceiver(this@RouteDetails, navigationReceiver, IntentFilter(BaseNavigationService.BROADCAST_NAVIGATION_STATUS), ContextCompat.RECEIVER_NOT_EXPORTED)
            ContextCompat.registerReceiver(this@RouteDetails, navigationReceiver, IntentFilter(BaseNavigationService.BROADCAST_NAVIGATION_STATE), ContextCompat.RECEIVER_NOT_EXPORTED)
            Log.d(TAG, "Navigation broadcast receiver registered")
            runOnUiThread {
                adapter.notifyDataSetChanged()
            }
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
                runOnUiThread {
                    adapter.notifyDataSetChanged()
                }
            }
        }
    }

    companion object {
        private const val TAG = "RouteDetails"

        private const val RESULT_START_ROUTE = 1

        private const val qaWaypointVisible = 1
        private const val qaWaypointNavigate = 2
        private const val qaWaypointProperties = 3
    }

    inner class WaypointListAdapter internal constructor(context: Context, route: Route) : BaseAdapter() {
        private val mInflater: LayoutInflater
        private val mItemLayout: Int
        private val mRoute: Route

        init {
            mItemLayout = R.layout.route_waypoint_list_item
            mInflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            mRoute = route
        }

        override fun getItem(position: Int): Waypoint {
            var adjustedPosition = position
            if (navigation && navigationService != null && navigationService!!.navDirection == BaseNavigationService.DIRECTION_REVERSE)
                adjustedPosition = mRoute.length() - adjustedPosition - 1
            return mRoute.getWaypoint(adjustedPosition)
        }

        override fun getItemId(position: Int): Long {
            var adjustedPosition = position
            if (navigation && navigationService != null && navigationService!!.navDirection == BaseNavigationService.DIRECTION_REVERSE)
                adjustedPosition = mRoute.length() - adjustedPosition - 1
            return adjustedPosition.toLong()
        }

        override fun getCount(): Int = mRoute.length()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            var v: View
            if (convertView == null) {
                v = mInflater.inflate(mItemLayout, parent, false)
            } else {
                v = convertView
                v = mInflater.inflate(mItemLayout, parent, false)
            }
            val wpt = getItem(position) as Waypoint
            var text = v.findViewById<TextView>(R.id.name)
            val txtAlt = v.findViewById<TextView>(R.id.altitude)
            if (text != null) {
                text.text = wpt.name
                val dist = StringFormatter.distanceC(wpt.altitude, 10000)
                val alt = dist[0] + dist[1]
                txtAlt.text = alt
            }
            if (navigation && navigationService != null && navigationService!!.isNavigatingViaRoute()) {
                val progress = position - navigationService!!.navRouteCurrentIndex()
                if (position > 0) {
                    val dist = if (progress == 0) navigationService!!.navDistance else mRoute.distanceBetween(position - 1, position)
                    val distance = StringFormatter.distanceH(dist)
                    text = v.findViewById(R.id.distance)
                    if (text != null) {
                        text.text = distance
                    }
                    val crs: Double
                    if (progress == 0)
                        crs = navigationService!!.navBearing
                    else if (navigationService!!.navDirection == BaseNavigationService.DIRECTION_FORWARD)
                        crs = mRoute.course(position - 1, position)
                    else
                        crs = mRoute.course(position, position - 1)
                    val course = StringFormatter.bearingH(crs)
                    text = v.findViewById(R.id.course)
                    if (text != null) {
                        text.text = course
                    }
                }
                if (progress >= 0) {
                    var dist = navigationService!!.navDistance
                    if (progress > 0)
                        dist += navigationService!!.navRouteDistanceLeftTo(position)
                    val distance = StringFormatter.distanceH(dist)
                    text = v.findViewById(R.id.total_distance)
                    if (text != null) {
                        text.text = distance
                    }
                    var ete = if (progress == 0) navigationService!!.navETE else navigationService!!.navRouteWaypointETE(position)
                    var s = StringFormatter.timeR(ete)
                    text = v.findViewById(R.id.ete)
                    if (text != null) {
                        text.text = s
                    }
                    var eta = navigationService!!.navETE
                    if (progress > 0 && eta < Int.MAX_VALUE) {
                        val t = navigationService!!.navRouteETETo(position)
                        if (t < Int.MAX_VALUE)
                            eta += t
                    }
                    s = StringFormatter.timeR(eta)
                    text = v.findViewById(R.id.eta)
                    if (text != null) {
                        text.text = s
                    }

                    if (progress == 0) {
                        text = v.findViewById(R.id.name)
                        text.text = "» " + text.text
                    }
                } else {
                    text = v.findViewById(R.id.name)
                    text.setTextColor(text.textColors.withAlpha(128))
                    text = v.findViewById(R.id.distance)
                    text.setTextColor(text.textColors.withAlpha(128))
                    text = v.findViewById(R.id.course)
                    text.setTextColor(text.textColors.withAlpha(128))
                }
            } else {
                if (position > 0) {
                    val dist = mRoute.distanceBetween(position - 1, position)
                    val distance = StringFormatter.distanceH(dist)
                    text = v.findViewById(R.id.distance)
                    if (text != null) {
                        text.text = distance
                    }
                    val crs = mRoute.course(position - 1, position)
                    val course = StringFormatter.bearingH(crs)
                    text = v.findViewById(R.id.course)
                    if (text != null) {
                        text.text = course
                    }
                }
                val dist = if (position > 0) mRoute.distanceBetween(0, position) else 0.0
                val distance = StringFormatter.distanceH(dist)
                text = v.findViewById(R.id.total_distance)
                if (text != null) {
                    text.text = distance
                }
            }

            return v
        }

        override fun hasStableIds(): Boolean {
            return true
        }
    }
}