package com.borkozic.location.share

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.location.Location
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import com.borkozic.Borkozic
import com.borkozic.R
import com.borkozic.data.MapObject
import com.borkozic.data.Situation
import com.borkozic.location.BaseLocationService
import com.borkozic.util.Geo
import com.borkozic.util.StringFormatter
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.ArrayList
import java.util.HashMap
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Foreground service for location sharing.
 * Replaces the original plugin SharingService with direct integration into Borkozic core.
 * No AIDL, no ContentProvider — direct access to Borkozic application and LocationService.
 */
class SharingService : Service(), SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        private const val TAG = "LocationSharing"
        private const val NOTIFICATION_ID = 24164
        private const val NOTIFICATION_CHANNEL_ID = "com.borkozic.location.share"
        const val BROADCAST_SITUATION_CHANGED = "com.borkozic.sharingSituationChanged"
    }

    var sharingEnabled = false
        private set
    var isSuspended = false
    var updateInterval = 10000 // 10 seconds default
        private set
    var timeoutInterval = 600000L // 10 minutes default
        private set
    var timeCorrection = 0L
        private set
    var speedFactor = 1.0
        private set
    var speedAbbr = "m/s"
        private set
    var elevationFactor = 1.0
        private set
    var elevationAbbr = "m"
        private set

    private var session: String? = null
    private var user: String? = null
    private var notifyNewSituation = false
    private var tagColor = 0
    private var pointWidth = 0

    private val situations = HashMap<String, Situation>()
    val situationList = ArrayList<Situation>()

    private var timer: Timer? = null
    private val executorThread = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS, LinkedBlockingQueue(1)
    )

    private var contentIntent: PendingIntent? = null

    // Drawing resources
    private val linePaint = Paint().apply {
        isAntiAlias = true
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint().apply {
        isAntiAlias = true
        strokeWidth = 2f
        style = Paint.Style.FILL
        textAlign = Paint.Align.LEFT
        textSize = 20f
        typeface = Typeface.SANS_SERIF
    }
    private val textFillPaint = Paint().apply {
        isAntiAlias = false
        strokeWidth = 1f
        style = Paint.Style.FILL_AND_STROKE
    }

    // Location receiver for periodic checks
    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (BaseLocationService.BROADCAST_LOCATING_STATUS == intent?.action) {
                // Location status changed, no action needed — timer will pick it up
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, SituationListActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        session = prefs.getString(getString(R.string.pref_sharing_session), null)
        user = prefs.getString(getString(R.string.pref_sharing_user), null)
        updateInterval = prefs.getInt(
            getString(R.string.pref_sharing_updateinterval),
            resources.getInteger(R.integer.def_sharing_updateinterval)
        ) * 1000
        timeoutInterval = prefs.getInt(
            getString(R.string.pref_sharing_timeout),
            resources.getInteger(R.integer.def_sharing_timeout)
        ).toLong() * 60000
        notifyNewSituation = prefs.getBoolean(
            getString(R.string.pref_sharing_notifications),
            resources.getBoolean(R.bool.def_notifications)
        )
        tagColor = prefs.getInt(
            getString(R.string.pref_sharing_tagcolor),
            resources.getColor(R.color.usertag, theme)
        )
        val tagSize = prefs.getInt(
            getString(R.string.pref_sharing_tagsize),
            resources.getInteger(R.integer.def_sharing_tagsize)
        )

        textPaint.textSize = tagSize * 10f
        pointWidth = tagSize * 6
        linePaint.color = tagColor
        textPaint.color = tagColor
        textFillPaint.color = resources.getColor(R.color.usertagwithalpha, theme)

        prefs.registerOnSharedPreferenceChangeListener(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(broadcastReceiver, IntentFilter(BaseLocationService.BROADCAST_LOCATING_STATUS), RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(broadcastReceiver, IntentFilter(BaseLocationService.BROADCAST_LOCATING_STATUS))
        }

        sharingEnabled = true
        isSuspended = true

        Log.i(TAG, "Service created")
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(broadcastReceiver)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopTimer()
        clearSituations()
        contentIntent = null
        Log.i(TAG, "Service destroyed")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!sharingEnabled) return START_NOT_STICKY
        if (session.isNullOrBlank() || user.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification(R.mipmap.ic_stat_sharing_out))
        isSuspended = false
        startTimer()
        return START_STICKY
    }

    private fun buildNotification(iconRes: Int): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.app_sharing_name),
                NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_sharing_name))
            .setContentText(getString(R.string.notif_sharing))
            .setSmallIcon(iconRes)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(iconRes: Int) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(iconRes))
    }

    private fun startTimer() {
        stopTimer()
        timer = Timer()
        timer?.scheduleAtFixedRate(UpdateSituationsTask(), updateInterval.toLong(), updateInterval.toLong())
    }

    private fun stopTimer() {
        timer?.cancel()
        timer = null
    }

    private fun clearSituations() {
        val ids: Array<String>
        synchronized(situationList) {
            ids = Array(situationList.size) { i -> situationList[i].id.toString() }
        }
        synchronized(situations) {
            synchronized(situationList) {
                situationList.clear()
            }
            situations.clear()
        }
        val app = application as Borkozic
        for (idStr in ids) {
            app.removeMapObject(idStr.toLong())
        }
        sendBroadcast(Intent(BROADCAST_SITUATION_CHANGED))
    }

    fun updateSituations() {
        updateNotification(R.mipmap.ic_stat_sharing_in)
        executorThread.queue.poll()
        executorThread.execute {
            Log.d(TAG, "updateSituations()")
            val app = application as Borkozic
            val loc = app.getLocationAsLocation()
            val lat = loc.latitude
            val lon = loc.longitude
            val speed = loc.speed
            val track = loc.bearing
            val ftime = loc.time
            val altitude = loc.altitude

            val client = AndrozicLocationShareClient()
            val remoteSituations = client.shareAndFetch(
                session ?: "", user ?: "",
                lat, lon, speed, track, ftime, altitude,
                updateInterval, timeoutInterval
            )

            if (remoteSituations.isNotEmpty()) {
                // Merge new situations
                synchronized(situations) {
                    for (s in remoteSituations) {
                        val name = s.name ?: continue
                        if (name == user) continue
                        situations[name] = s
                    }
                    // Remove stale situations from map
                    val now = System.currentTimeMillis()
                    val stale = mutableListOf<String>()
                    for ((name, s) in situations) {
                        if (now - s.time > timeoutInterval) {
                            stale.add(name)
                            app.removeMapObject(s.id)
                        }
                    }
                    for (name in stale) {
                        situations.remove(name)
                    }
                    synchronized(situationList) {
                        situationList.clear()
                        situationList.addAll(situations.values)
                        situationList.sortBy { it.name }
                    }
                }
                app.notifyOverlays()
                sendBroadcast(Intent(BROADCAST_SITUATION_CHANGED))
            }
            updateNotification(R.mipmap.ic_stat_sharing_out)
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        val oldSession = session
        val oldUser = user
        when (key) {
            getString(R.string.pref_sharing_session) -> {
                session = sharedPreferences?.getString(key, null)
            }
            getString(R.string.pref_sharing_user) -> {
                user = sharedPreferences?.getString(key, null)
            }
            getString(R.string.pref_sharing_updateinterval) -> {
                updateInterval = sharedPreferences?.getInt(
                    key, resources.getInteger(R.integer.def_sharing_updateinterval)
                ) ?: 10000
                if (timer != null) startTimer()
            }
            getString(R.string.pref_sharing_notifications) -> {
                notifyNewSituation = sharedPreferences?.getBoolean(
                    key, resources.getBoolean(R.bool.def_notifications)
                ) ?: false
            }
            getString(R.string.pref_sharing_tagcolor) -> {
                tagColor = sharedPreferences?.getInt(
                    key, resources.getColor(R.color.usertag, theme)
                ) ?: 0
                linePaint.color = tagColor
                textPaint.color = tagColor
            }
            getString(R.string.pref_sharing_tagsize) -> {
                val width = sharedPreferences?.getInt(
                    key, resources.getInteger(R.integer.def_sharing_tagsize)
                ) ?: 1
                textPaint.textSize = width * 10f
                pointWidth = width * 6
            }
            getString(R.string.pref_sharing_timeout) -> {
                timeoutInterval = (sharedPreferences?.getInt(
                    key, resources.getInteger(R.integer.def_sharing_timeout)
                ) ?: 10).toLong() * 60000
            }
        }
        if (session != oldSession || user != oldUser) {
            clearSituations()
        }
        if (session.isNullOrBlank() || user.isNullOrBlank()) {
            stopSelf()
        }
    }

    // Binder for SituationList binding
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): SharingService = this@SharingService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    inner class UpdateSituationsTask : TimerTask() {
        override fun run() {
            updateSituations()
        }
    }

    /**
     * Create a bitmap tag for a situation to display on the map.
     */
    fun createSituationBitmap(situation: Situation): Bitmap {
        val name = situation.name
        val track = StringFormatter.bearingSimpleH(situation.track)
        val speed = StringFormatter.distanceH(situation.speed, "%.1f")
        val altitude = StringFormatter.elevationH(situation.altitude)

        val label = "$name $track $speed $altitude"

        val bounds = Rect()
        textPaint.getTextBounds(label, 0, label.length, bounds)
        val width = bounds.width() + 8
        val height = bounds.height() + 8

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background
        textFillPaint.alpha = 160
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), textFillPaint)

        // Text
        canvas.drawText(label, 4f, (height - 4).toFloat(), textPaint)

        return bitmap
    }
}
