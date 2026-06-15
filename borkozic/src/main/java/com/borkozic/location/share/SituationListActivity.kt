package com.borkozic.location.share

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.CompoundButton
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.borkozic.Borkozic
import com.borkozic.R
import com.borkozic.data.Situation
import com.borkozic.util.Geo
import com.borkozic.util.StringFormatter
import java.util.Timer
import java.util.TimerTask

/**
 * Activity displaying the list of shared-location users in the current session.
 * Replaces the plugin SituationList with direct integration.
 */
class SituationListActivity : AppCompatActivity(),
    SharedPreferences.OnSharedPreferenceChangeListener,
    AdapterView.OnItemClickListener,
    CompoundButton.OnCheckedChangeListener,
    PopupMenu.OnMenuItemClickListener,
    PopupMenu.OnDismissListener {

    companion object {
        private const val TAG = "SituationList"
        private const val PERMISSIONS_REQUEST = 1001

        private val BORKOZIC_PERMISSIONS = arrayOf(
            "com.borkozic.permission.RECEIVE_LOCATION",
            "com.borkozic.permission.NAVIGATION",
            "com.borkozic.permission.READ_PREFERENCES",
            "com.borkozic.permission.READ_MAP_DATA",
            "com.borkozic.permission.WRITE_MAP_DATA"
        )
    }

    private var listView: ListView? = null
    private var emptyView: TextView? = null
    private var adapter: SituationListAdapter? = null
    var sharingService: SharingService? = null
        private set

    private var timer: Timer? = null
    private var enableSwitch: Switch? = null
    private var selectedPosition = -1
    private var selectedBackground: Drawable? = null
    private var lastSelectedPosition = 0
    private var accentColor = 0

    private val sharingConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as SharingService.LocalBinder
            sharingService = binder.getService()
            if (sharingService?.sharingEnabled == true) {
                sharingService?.isSuspended = false
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            sharingService = null
        }
    }

    private val sharingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (SharingService.BROADCAST_SITUATION_CHANGED == intent?.action) {
                adapter?.notifyDataSetChanged()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.act_userlist)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        listView = findViewById(android.R.id.list)
        emptyView = findViewById(android.R.id.empty)
        listView?.emptyView = emptyView

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.registerOnSharedPreferenceChangeListener(this)

        accentColor = ContextCompat.getColor(this, R.color.theme_accent_color)

        adapter = SituationListAdapter(this)
        listView?.adapter = adapter
        listView?.onItemClickListener = this

        onSharedPreferenceChanged(prefs, null)
    }

    override fun onResume() {
        super.onResume()
        if (isServiceRunning()) {
            connect()
        }
    }

    override fun onPause() {
        super.onPause()
        disconnect()
    }

    override fun onDestroy() {
        super.onDestroy()
        PreferenceManager.getDefaultSharedPreferences(this)
            .unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.situation_list, menu)
        menu.findItem(R.id.action_enable)?.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        enableSwitch = menu.findItem(R.id.action_enable)?.actionView as? Switch
        enableSwitch?.setOnCheckedChangeListener(this)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        onSharedPreferenceChanged(prefs, null)
        if (isServiceRunning()) {
            enableSwitch?.isChecked = true
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onItemClick(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
        selectedPosition = position
        selectedBackground = view.background
        lastSelectedPosition = position
        view.setBackgroundColor(accentColor)

        val popup = PopupMenu(this, view.findViewById(R.id.name))
        popup.inflate(R.menu.situation_popup)
        popup.setOnMenuItemClickListener(this)
        popup.setOnDismissListener(this)
        popup.show()
    }

    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        if (isChecked && !isServiceRunning()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val missing = mutableListOf<String>()
                for (perm in BORKOZIC_PERMISSIONS) {
                    if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
                        missing.add(perm)
                    }
                }
                if (missing.isNotEmpty()) {
                    requestPermissions(missing.toTypedArray(), PERMISSIONS_REQUEST)
                } else {
                    start()
                }
            } else {
                start()
            }
        } else if (!isChecked && isServiceRunning()) {
            disconnect()
            stopService(Intent(this, SharingService::class.java))
            supportActionBar?.subtitle = ""
            emptyView?.setText(R.string.msg_needs_enable)
            adapter?.notifyDataSetChanged()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        if (requestCode == PERMISSIONS_REQUEST) {
            val granted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (granted) {
                start()
            } else {
                enableSwitch?.isChecked = false
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    private fun start() {
        emptyView?.setText(R.string.msg_no_users)
        startService(Intent(this, SharingService::class.java))
        connect()
    }

    private fun connect() {
        bindService(Intent(this, SharingService::class.java), sharingConnection, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(sharingReceiver, IntentFilter(SharingService.BROADCAST_SITUATION_CHANGED), RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(sharingReceiver, IntentFilter(SharingService.BROADCAST_SITUATION_CHANGED))
        }
        timer = Timer()
        timer?.scheduleAtFixedRate(UpdateTask(), 1000, 1000)
    }

    private fun disconnect() {
        if (sharingService != null) {
            unregisterReceiver(sharingReceiver)
            unbindService(sharingConnection)
            sharingService = null
        }
        timer?.cancel()
        timer = null
    }

    private fun isServiceRunning(): Boolean {
        val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (SharingService::class.java.name == service.service.className && service.pid > 0) {
                return true
            }
        }
        return false
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        val situation = adapter?.getItem(lastSelectedPosition) ?: return false
        return when (item?.itemId) {
            R.id.action_view -> {
                sendBroadcast(
                    Intent("com.borkozic.CENTER_ON_COORDINATES")
                        .putExtra("lat", situation.latitude)
                        .putExtra("lon", situation.longitude)
                )
                finish()
                true
            }
            R.id.action_navigate -> {
                val intent = Intent("com.borkozic.navigateMapObjectWithId")
                    .putExtra("id", situation.id)
                val explicit = getExplicitIntent(intent)
                if (explicit != null) startService(explicit)
                finish()
                true
            }
            else -> false
        }
    }

    override fun onDismiss(menu: PopupMenu?) {
        if (selectedPosition >= 0) {
            listView?.getChildAt(selectedPosition)?.background = selectedBackground
            selectedPosition = -1
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        val session = sharedPreferences?.getString(getString(R.string.pref_sharing_session), "") ?: ""
        val user = sharedPreferences?.getString(getString(R.string.pref_sharing_user), "") ?: ""
        if (session.isNotBlank() && user.isNotBlank()) {
            enableSwitch?.isEnabled = true
            emptyView?.setText(R.string.msg_needs_enable)
        } else {
            enableSwitch?.isEnabled = false
            emptyView?.setText(R.string.msg_needs_setup)
        }
        adapter?.notifyDataSetChanged()
    }

    private fun getExplicitIntent(implicitIntent: Intent): Intent? {
        val pm = packageManager
        val resolveInfo = pm.queryIntentServices(implicitIntent, 0)
        if (resolveInfo == null || resolveInfo.size != 1) return null
        val serviceInfo = resolveInfo[0]
        val component = ComponentName(
            serviceInfo.serviceInfo.packageName,
            serviceInfo.serviceInfo.name
        )
        return Intent(implicitIntent).setComponent(component)
    }

    inner class UpdateTask : TimerTask() {
        override fun run() {
            runOnUiThread {
                if (selectedPosition == -1) {
                    adapter?.notifyDataSetChanged()
                }
            }
        }
    }

    inner class SituationListAdapter(context: Context) : BaseAdapter() {
        private val inflater = LayoutInflater.from(context)

        override fun getCount(): Int = sharingService?.situationList?.size ?: 0

        override fun getItem(position: Int): Situation? =
            sharingService?.situationList?.getOrNull(position)

        override fun getItemId(position: Int): Long =
            sharingService?.situationList?.getOrNull(position)?.id ?: 0L

        override fun hasStableIds(): Boolean = true

        @SuppressLint("SetTextI18n")
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val v = convertView ?: inflater.inflate(R.layout.situation_list_item, parent, false)
            val svc = sharingService ?: return v
            val stn = getItem(position) ?: return v
            val app = application as Borkozic
            val loc = app.getLocationAsLocation()

            var text = v.findViewById<TextView>(R.id.name)
            text?.text = stn.name

            val dist = Geo.distance(loc.latitude, loc.longitude, stn.latitude, stn.longitude)
            val distStr = StringFormatter.distanceH(dist)
            text = v.findViewById(R.id.distance)
            text?.text = distStr

            text = v.findViewById(R.id.track)
            text?.text = StringFormatter.bearingSimpleH(stn.track)

            val speed = StringFormatter.distanceH(stn.speed * svc.speedFactor, "%.1f")
            text = v.findViewById(R.id.speed)
            text?.text = speed

            val altitude = StringFormatter.elevationH(stn.altitude * svc.elevationFactor)
            text = v.findViewById(R.id.altitude)
            text?.text = altitude

            val now = System.currentTimeMillis()
            val d = stn.time - svc.timeCorrection
            val delay = DateUtils.getRelativeTimeSpanString(
                d, now, DateUtils.SECOND_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE
            )
            text = v.findViewById(R.id.delay)
            text?.text = delay

            // Dim text for silent users
            val alpha = if (stn.silent) 128 else 255
            val colorName = v.findViewById<TextView>(R.id.name)?.textColors?.withAlpha(alpha)
            if (colorName != null) v.findViewById<TextView>(R.id.name)?.setTextColor(colorName)
            val colorDistance = v.findViewById<TextView>(R.id.distance)?.textColors?.withAlpha(alpha)
            if (colorDistance != null) v.findViewById<TextView>(R.id.distance)?.setTextColor(colorDistance)
            val colorTrack = v.findViewById<TextView>(R.id.track)?.textColors?.withAlpha(alpha)
            if (colorTrack != null) v.findViewById<TextView>(R.id.track)?.setTextColor(colorTrack)
            val colorSpeed = v.findViewById<TextView>(R.id.speed)?.textColors?.withAlpha(alpha)
            if (colorSpeed != null) v.findViewById<TextView>(R.id.speed)?.setTextColor(colorSpeed)
            val colorAltitude = v.findViewById<TextView>(R.id.altitude)?.textColors?.withAlpha(alpha)
            if (colorAltitude != null) v.findViewById<TextView>(R.id.altitude)?.setTextColor(colorAltitude)
            val colorDelay = v.findViewById<TextView>(R.id.delay)?.textColors?.withAlpha(alpha)
            if (colorDelay != null) v.findViewById<TextView>(R.id.delay)?.setTextColor(colorDelay)

            return v
        }
    }

}
