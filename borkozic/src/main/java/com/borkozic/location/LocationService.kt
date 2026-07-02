package com.borkozic.location

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.GpsStatus.NmeaListener
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationProvider
import android.location.OnNmeaMessageListener
import android.os.*
import androidx.annotation.RequiresApi
import java.io.File
import java.io.PrintWriter
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.util.Log
import android.widget.Toast
import com.borkozic.BaseApplication
import com.borkozic.Borkozic
import com.borkozic.R
import com.borkozic.Splash
import com.borkozic.data.Track
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

open class LocationService : BaseLocationService(), LocationListener, OnNmeaMessageListener, OnSharedPreferenceChangeListener, SensorEventListener {
    private val TAG = "Location"
    private val NOTIFICATION_ID = 24161
    private val NOTIFICATION_CHANNEL_ID = "com.borkozic.location"
    private val ChannelName = "Background Location Service"
    private val DEBUG_ERRORS = false

    companion object {
        const val ENABLE_LOCATIONS = "enableLocations"
        const val DISABLE_LOCATIONS = "disableLocations"
        const val ENABLE_TRACK = "enableTrack"
        const val DISABLE_TRACK = "disableTrack"
        const val BROADCAST_TRACKING_STATUS = "com.borkozic.trackingStatusChanged"

        // DR константи
        private const val DR_MAX_DURATION_MS = 180_000L     // 3 минути
        private const val DR_BROADCAST_INTERVAL_MS = 200L   // 5Hz
        const val DR_IDLE = 0
        const val DR_ACTIVE = 1
        const val DR_STOPPED = 2
    }

    private var locationsEnabled = false
    private var useNetwork = true
    private var gpsLocationTimeout = 120000

    private var locationManager: LocationManager? = null

    private var gpsStatus = GPS_OFF
    private var gnssStatus = GPS_OFF

    // Satellite tracking — populated from NMEA sentences by onNmeaMessage()
    // fsats = satellites used in position fix (from GGA)
    // tsats = total satellites in view (from GSV, accumulated across constellations)
    private var fsats = 0
    private var tsats = 0

    private val speed = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
    private val speedav = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
    private val speedavex = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)

    private var lastLocationMillis: Long = 0
    private var tics: Long = 0
    private var pause = 1

    private var lastKnownLocation: Location? = null
    private var isContinous = false
    private var justStarted = true
    private var smoothSpeed = 0.0f
    private var avgSpeed = 0.0f
    private var nmeaGeoidHeight = Float.NaN
    private var HDOP = Float.NaN
    private var VDOP = Float.NaN

    private var trackDB: SQLiteDatabase? = null
    private var trackingEnabled = false
    private var errorMsg = ""
    private var errorTime: Long = 0

    private var lastWritenLocation: Location? = null
    private var lastLocation: Location? = null
    private var distanceFromLastWriting = 0.0
    private var timeFromLastWriting: Long = 0

    private var minTime: Long = 2000 // 2 seconds (default)
    private val maxTime: Long = 300000 // 5 minutes
    private var minDistance = 3 // 3 meters (default)

    private val binder = LocalBinder()

    // ========================================================================
    // Dead Reckoning (автономно изчисление при GPS загуба)
    // Архитектура: Kalman 6-state filter [posN,posE,velN,velE,biasN,biasE]
    // Сензорите работят ПОСТОЯННО (SENSOR_DELAY_GAME, ~50Hz)
    // Kalman predict — при всяко accel събитие; GPS update — при всеки fix
    // ========================================================================

    // Сензори за DR (регистрирани постоянно)
    private var drSensorManager: SensorManager? = null
    private var drAccelerometer: Sensor? = null
    private var drGyroscope: Sensor? = null
    private var drMagnetometer: Sensor? = null
    private var drBarometer: Sensor? = null
    private var drRotationVector: Sensor? = null
    private var sensorsRegistered: Boolean = false

    // Kalman филтър (замества trajectory-based DeadReckoningCalculator)
    private val drKalman = DeadReckoningKalman()
    private val gpsRingBuffer = GpsRingBuffer()
    private var drActive = false
    private var drStartTime: Long = 0
    private var drLastBroadcastTime: Long = 0
    private var dispatchCount: Int = 0

    private var drState = DR_IDLE
    private var lastFsats: Int = 0

    // Rotation matrix (device → Earth frame) за gravity removal + accel transform
    private val drRotMatrix3x3 = FloatArray(9)
    private val drRotMatrix4x4 = FloatArray(16)
    private var drRotMatrixReady: Boolean = false
    private var drLastRotVectorTimestamp: Long = 0

    // Gravity estimation (low-pass на raw акселерометър)
    private var drGravityX: Double = 0.0
    private var drGravityY: Double = 0.0
    private var drGravityZ: Double = 9.81
    private var drSensorIsLinearAccel: Boolean = false

    // Accelerometer Earth-frame (за snapshot/debug)
    private var drEarthAccelN: Double = 0.0
    private var drEarthAccelE: Double = 0.0

    // Heading filter — предотвратява скокове > 30°
    private val drHeadingHistory = DoubleArray(5) { Double.NaN }
    private var drHeadingHistoryIdx: Int = 0
    private var drFilteredHeading: Double = Double.NaN

    // Raw сензорни стойности за snapshot
    private var drSnapshotAccelX: Float = 0f
    private var drSnapshotAccelY: Float = 0f
    private var drSnapshotAccelZ: Float = 0f
    private var drSnapshotGyroX: Float = 0f
    private var drSnapshotGyroY: Float = 0f
    private var drSnapshotGyroZ: Float = 0f
    private var drSnapshotMagX: Float = 0f
    private var drSnapshotMagY: Float = 0f
    private var drSnapshotMagZ: Float = 0f
    private var drSnapshotRotX: Float = 0f
    private var drSnapshotRotY: Float = 0f
    private var drSnapshotRotZ: Float = 0f
    private var drSensorValuesCaptured: Boolean = false

    // === DR debug лог файл ===
    private var drLogFile: File? = null
    private var drLogWriter: PrintWriter? = null
    private val drLogDateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private var drSensorFirstEvent: Boolean = true

    private fun drToast(msg: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this, "DR: $msg", Toast.LENGTH_SHORT).show()
        }
    }

    private fun drLog(msg: String) {
        val ts = drLogDateFormat.format(Date())
        val line = "[$ts] $msg"
        Log.i(TAG, line)
        try {
            drLogWriter?.let {
                it.println(line)
                it.flush()
            }
        } catch (_: Exception) {}
        try { DRLogger.log(msg) } catch (_: Exception) {}
    }

    private val locationRemoteCallbacks = RemoteCallbackList<ILocationCallback>()
    private val locationCallbacks = HashSet<ILocationListener>()
    private val trackingRemoteCallbacks = RemoteCallbackList<ITrackingCallback>()
    private val trackingCallbacks = HashSet<ITrackingListener>()

    override fun onCreate() {
        super.onCreate()
        //Log.e(TAG, "onCreate()")

        lastKnownLocation = Location("unknown")

        val sharedPreferences = getSharedPreferences(packageName + "_preferences", Context.MODE_PRIVATE)
        onSharedPreferenceChanged(sharedPreferences, getString(R.string.pref_loc_usenetwork))
        onSharedPreferenceChanged(sharedPreferences, getString(R.string.pref_loc_gpstimeout))
        onSharedPreferenceChanged(sharedPreferences, getString(R.string.pref_tracking_mintime))
        onSharedPreferenceChanged(sharedPreferences, getString(R.string.pref_tracking_mindistance))

        sharedPreferences.registerOnSharedPreferenceChangeListener(this)

        // Инициализация на DR сензори — РЕГИСТРИРАНИ ПОСТОЯННО
        drSensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        drAccelerometer = drSensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: drSensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        drSensorIsLinearAccel = (drSensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) != null)
        drGyroscope = drSensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        drMagnetometer = drSensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        drBarometer = drSensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE)
        drRotationVector = drSensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: drSensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        // Регистриране на сензори ПОСТОЯННО (SENSOR_DELAY_GAME ~50Hz, ~7mA общо)
        registerDrSensorsContinuously()

        // Инициализация на DR debug лог файл
        // Ползваме getExternalFilesDir() → достъпен без root/adb
        // на пътя Android/data/com.borkozic/files/dr_logs/
        try {
            val logDir = File(getExternalFilesDir(null) ?: filesDir, "dr_logs")
            logDir.mkdirs()
            val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            drLogFile = File(logDir, "dr_$dateStr.txt")
            drLogWriter = PrintWriter(drLogFile, "UTF-8")
            drLog("=== DR Logger initialized ===")
            drLog("Device: ${Build.MANUFACTURER} ${Build.MODEL}, SDK=${Build.VERSION.SDK_INT}")
            drLog("Sensors: accel=${drAccelerometer != null}(${drAccelerometer?.name}) linearAccel=$drSensorIsLinearAccel, gyro=${drGyroscope != null}, mag=${drMagnetometer != null}, baro=${drBarometer != null}, rotVec=${drRotationVector != null}")
            drLog("Log path: ${drLogFile!!.absolutePath}")
            Log.i(TAG, "DR log file: ${drLogFile!!.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "DR log init failed: ${e.message}", e)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startMyOwnForeground()
        } else {
            startForeground(NOTIFICATION_ID, Notification())
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || intent.action == null) return Service.START_NOT_STICKY

        val action = intent.action!!
        when {
            action == ENABLE_LOCATIONS && !locationsEnabled -> {
                locationsEnabled = true
                // Ако DR е бил активен, спираме го преди connect
                if (drActive) {
                    drLog("ENABLE_LOCATIONS: stopping active DR")
                    stopDeadReckoning()
                }
                connect()
                sendBroadcast(Intent(BROADCAST_LOCATING_STATUS))
                if (trackingEnabled) {
                    sendBroadcast(Intent(BROADCAST_TRACKING_STATUS))
                }
            }
            action == DISABLE_LOCATIONS && locationsEnabled -> {
                locationsEnabled = false
                disconnect()
                updateProvider(LocationManager.GPS_PROVIDER, false)
                updateProvider(LocationManager.NETWORK_PROVIDER, false)
                // Стартирай DR ако GPS-ът се изключва ръчно
                val isStartingDr = !drActive
                if (isStartingDr) {
                    drLog("DISABLE_LOCATIONS: starting DR")
                    startDeadReckoning()
                }
                sendBroadcast(Intent(BROADCAST_LOCATING_STATUS))
                if (trackingEnabled) {
                    if (isStartingDr) {
                        // DR ще продължи да записва трака през updateLocation()
                        drLog("DISABLE_LOCATIONS: keeping track DB open for DR recording")
                        sendBroadcast(Intent(BROADCAST_TRACKING_STATUS))
                    } else {
                        closeDatabase()
                        sendBroadcast(Intent(BROADCAST_TRACKING_STATUS))
                    }
                }
            }
            action == ENABLE_TRACK && !trackingEnabled -> {
                errorMsg = ""
                errorTime = 0
                trackingEnabled = true
                isContinous = false
                openDatabase()
                sendBroadcast(Intent(BROADCAST_TRACKING_STATUS))
            }
            action == DISABLE_TRACK && trackingEnabled -> {
                trackingEnabled = false
                closeDatabase()
                errorMsg = ""
                errorTime = 0
                sendBroadcast(Intent(BROADCAST_TRACKING_STATUS))
            }
        }
        updateNotification()
        return Service.START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterDrSensors()
        drLog("=== DR Logger closing ===")
        try { drLogWriter?.close() } catch (_: Exception) {}
        drLogWriter = null
        getSharedPreferences(packageName + "_preferences", Context.MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(this)
        disconnect()
        closeDatabase()
        //Log.i(TAG, "Service stopped")
    }

    private val locationRemoteBinder = object : ILocationRemoteService.Stub() {
        override fun registerCallback(cb: ILocationCallback?) {
            //Log.i(TAG, "Register location callback")
            if (cb != null) locationRemoteCallbacks.register(cb)
        }

        override fun unregisterCallback(cb: ILocationCallback?) {
            if (cb != null) locationRemoteCallbacks.unregister(cb)
        }

        override fun isLocating(): Boolean {
            return locationsEnabled
        }
    }

    private val trackingRemoteBinder = object : ITrackingRemoteService.Stub() {
        override fun registerCallback(cb: ITrackingCallback?) {
            //Log.i(TAG, "Register track callback")
            if (cb != null) trackingRemoteCallbacks.register(cb)
        }

        override fun unregisterCallback(cb: ITrackingCallback?) {
            if (cb != null) trackingRemoteCallbacks.unregister(cb)
        }
    }

    override fun onBind(intent: Intent): IBinder {
        return when {
            BORKOZIC_LOCATION_SERVICE == intent.action || ILocationRemoteService::class.java.name == intent.action -> {
                locationRemoteBinder
            }
            "com.borkozic.tracking" == intent.action || ITrackingRemoteService::class.java.name == intent.action -> {
                trackingRemoteBinder
            }
            else -> binder
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        when (key) {
            getString(R.string.pref_loc_usenetwork) -> {
                useNetwork = sharedPreferences.getBoolean(key, resources.getBoolean(R.bool.def_loc_usenetwork))
            }
            getString(R.string.pref_loc_gpstimeout) -> {
                gpsLocationTimeout = 1000 * sharedPreferences.getInt(key, resources.getInteger(R.integer.def_loc_gpstimeout))
            }
            getString(R.string.pref_tracking_mintime) -> {
                try {
                    minTime = sharedPreferences.getString(key, "500")?.toInt()?.toLong() ?: 2000
                } catch (ignored: NumberFormatException) {
                }
            }
            getString(R.string.pref_tracking_mindistance) -> {
                try {
                    minDistance = sharedPreferences.getString(key, "5")?.toInt() ?: 3
                } catch (ignored: NumberFormatException) {
                }
            }
            getString(R.string.pref_folder_data) -> {
                closeDatabase()
                openDatabase()
            }
        }
    }

    private fun connect() {
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager?
        if (locationManager == null) return

        lastLocationMillis = 0
        pause = 1
        isContinous = false
        justStarted = true
        smoothSpeed = 0.0f
        avgSpeed = 0.0f

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        if (useNetwork) {
            try {
                locationManager!!.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0f, this)
                //Log.d(TAG, "Network provider set")
            } catch (e: IllegalArgumentException) {
                Toast.makeText(this, getString(R.string.err_no_network_provider), Toast.LENGTH_LONG).show()
            }
        }
        try {
            locationManager!!.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0f, this)
            //Log.d(TAG, "Gps provider set")
            // Register modern NMEA listener for satellite info (API 24+)
            locationManager!!.addNmeaListener(this, null)
            //Log.d(TAG, "OnNmeaMessageListener registered")
        } catch (e: IllegalArgumentException) {
            //Log.d(TAG, "Cannot set gps provider, likely no gps on device")
        }

        updateNotification()
    }

    private fun disconnect() {
        if (locationManager != null) {
            locationManager!!.removeUpdates(this)
            locationManager!!.removeNmeaListener(this)
            locationManager = null
            stopForeground(true)
        }
    }

    private fun getNotification(): Notification {
        var msgId = R.string.notif_loc_started
        var ntfId = R.drawable.ic_stat_locating
        if (trackingEnabled) {
            msgId = R.string.notif_trk_started
            ntfId = R.drawable.ic_stat_tracking
        }
        if (gpsStatus != GPS_OK) {
            msgId = R.string.notif_loc_waiting
            ntfId = R.drawable.ic_stat_waiting
        }
        if (gpsStatus == GPS_OFF) {
            ntfId = R.drawable.ic_stat_off
        }
        if (gnssStatus != GPS_OK) {
            msgId = R.string.notif_loc_waiting
            ntfId = R.drawable.ic_stat_waiting
        }
        if (gnssStatus == GPS_OFF) {
            ntfId = R.drawable.ic_stat_off
        }
        if (errorTime > 0) {
            msgId = R.string.notif_trk_failure
            ntfId = R.drawable.ic_stat_failure
        }

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
        builder.setWhen(errorTime)
        builder.setSmallIcon(ntfId)
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        intent.component = ComponentName(applicationContext, Splash::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        val contentIntent = PendingIntent.getActivity(this, NOTIFICATION_ID, intent, PendingIntent.FLAG_IMMUTABLE)
        builder.setContentIntent(contentIntent)
        builder.setContentTitle(getText(R.string.notif_loc_short))
        if (errorTime > 0 && DEBUG_ERRORS) {
            builder.setContentText(errorMsg)
        } else {
            builder.setContentText(getText(msgId))
        }
        builder.setOngoing(true)
        return builder.build()
    }

    private fun updateNotification() {
        if (locationManager != null) {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, getNotification())
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private fun startMyOwnForeground() {
        val chan = NotificationChannel(NOTIFICATION_CHANNEL_ID, ChannelName, NotificationManager.IMPORTANCE_NONE)
        chan.lightColor = Color.BLUE
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(chan)

        val notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
        notificationBuilder.setOngoing(true)
            .setSmallIcon(R.drawable.info)
            .setContentTitle("App is running in background")
            .setPriority(NotificationManager.IMPORTANCE_MIN)
            .setCategory(Notification.CATEGORY_SERVICE)

        val notification = notificationBuilder.build()
        //Log.d(TAG, "startMyOwnForeground")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                //Log.w(TAG, "POST_NOTIFICATIONS permission not granted")
            }
        }
    }

    private fun openDatabase() {
        val application = BaseApplication.getApplication<Borkozic>() ?: return
        if (application.dataPath == null) {
            //Log.e(TAG, "Data path is null")
            errorMsg = "Data path is null"
            errorTime = System.currentTimeMillis()
            updateNotification()
            return
        }
        val dir = File(application.dataPath!!)
        if (!dir.exists() && !dir.mkdirs()) {
            //Log.e(TAG, "Failed to create data folder")
            errorMsg = "Failed to create data folder"
            errorTime = System.currentTimeMillis()
            updateNotification()
            return
        }
        val path = File(dir, "myTrack.db")
        //Log.i(TAG, path.toString())
        try {
            trackDB = SQLiteDatabase.openDatabase(
                path.absolutePath, null,
                SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
            )
            val cursor = trackDB!!.rawQuery("SELECT DISTINCT tbl_name FROM sqlite_master WHERE tbl_name = 'track'", null)
            if (cursor.count == 0) {
                trackDB!!.execSQL("CREATE TABLE track (_id INTEGER PRIMARY KEY, latitude REAL, longitude REAL, code INTEGER, elevation REAL, speed REAL, track REAL, accuracy REAL, datetime INTEGER)")
            }
            cursor.close()
        } catch (e: SQLiteException) {
            trackDB = null
            //Log.e(TAG, "openDatabase", e)
            errorMsg = "Failed to open DB"
            errorTime = System.currentTimeMillis()
            updateNotification()
        }
    }

    private fun closeDatabase() {
        if (trackDB != null) {
            trackDB!!.close()
            trackDB = null
        }
    }

    fun getTrack(): Track {
        return getTrack(0)
    }

    fun getTrack(limit: Long): Track {
        if (trackDB == null) openDatabase()
        val track = Track()
        if (trackDB == null) return track

        val limitStr = if (limit > 0) " LIMIT $limit" else ""
        val cursor = trackDB!!.rawQuery("SELECT * FROM track ORDER BY _id DESC$limitStr", null)
        if (cursor == null) return track

        val latIdx = cursor.getColumnIndex("latitude")
        val lonIdx = cursor.getColumnIndex("longitude")
        val eleIdx = cursor.getColumnIndex("elevation")
        val speedIdx = cursor.getColumnIndex("speed")
        val bearingIdx = cursor.getColumnIndex("track")
        val accIdx = cursor.getColumnIndex("accuracy")
        val codeIdx = cursor.getColumnIndex("code")
        val timeIdx = cursor.getColumnIndex("datetime")

        if (latIdx == -1 || lonIdx == -1 || eleIdx == -1 || speedIdx == -1 ||
            bearingIdx == -1 || accIdx == -1 || codeIdx == -1 || timeIdx == -1
        ) {
            //Log.e(TAG, "Database schema mismatch: missing columns")
            cursor.close()
            return track
        }

        var hasItem = cursor.moveToLast()
        while (hasItem) {
            val lat = cursor.getDouble(latIdx)
            val lon = cursor.getDouble(lonIdx)
            val ele = cursor.getDouble(eleIdx)
            val spd = cursor.getDouble(speedIdx)
            val brg = cursor.getDouble(bearingIdx)
            val acc = cursor.getDouble(accIdx)
            val code = cursor.getInt(codeIdx)
            val tm = cursor.getLong(timeIdx)
            track.addPoint(code == 0, lat, lon, ele, spd, brg, acc, tm)
            hasItem = cursor.moveToPrevious()
        }
        cursor.close()
        return track
    }

    fun getTrack(start: Long, end: Long): Track {
        if (trackDB == null) openDatabase()
        val track = Track()
        if (trackDB == null) return track

        val cursor = trackDB!!.rawQuery(
            "SELECT * FROM track WHERE datetime >= ? AND datetime <= ? ORDER BY _id DESC",
            arrayOf(start.toString(), end.toString())
        )
        if (cursor == null) return track

        val latIdx = cursor.getColumnIndex("latitude")
        val lonIdx = cursor.getColumnIndex("longitude")
        val eleIdx = cursor.getColumnIndex("elevation")
        val speedIdx = cursor.getColumnIndex("speed")
        val bearingIdx = cursor.getColumnIndex("track")
        val accIdx = cursor.getColumnIndex("accuracy")
        val codeIdx = cursor.getColumnIndex("code")
        val timeIdx = cursor.getColumnIndex("datetime")

        if (latIdx == -1 || lonIdx == -1 || eleIdx == -1 || speedIdx == -1 ||
            bearingIdx == -1 || accIdx == -1 || codeIdx == -1 || timeIdx == -1
        ) {
            //Log.e(TAG, "Database schema mismatch: missing columns")
            cursor.close()
            return track
        }

        var hasItem = cursor.moveToLast()
        while (hasItem) {
            val lat = cursor.getDouble(latIdx)
            val lon = cursor.getDouble(lonIdx)
            val ele = cursor.getDouble(eleIdx)
            val spd = cursor.getDouble(speedIdx)
            val brg = cursor.getDouble(bearingIdx)
            val acc = cursor.getDouble(accIdx)
            val code = cursor.getInt(codeIdx)
            val tm = cursor.getLong(timeIdx)
            track.addPoint(code == 0, lat, lon, ele, spd, brg, acc, tm)
            hasItem = cursor.moveToPrevious()
        }
        cursor.close()
        return track
    }

    fun getTrackStartTime(): Long {
        if (trackDB == null) openDatabase()
        if (trackDB == null) return Long.MIN_VALUE
        val cursor = trackDB!!.rawQuery("SELECT MIN(datetime) FROM track WHERE datetime > 0", null)
        var res = Long.MIN_VALUE
        if (cursor.moveToFirst()) {
            res = cursor.getLong(0)
        }
        cursor.close()
        return res
    }

    fun getTrackEndTime(): Long {
        if (trackDB == null) openDatabase()
        if (trackDB == null) return Long.MAX_VALUE
        val cursor = trackDB!!.rawQuery("SELECT MAX(datetime) FROM track", null)
        var res = Long.MAX_VALUE
        if (cursor.moveToFirst()) {
            res = cursor.getLong(0)
        }
        cursor.close()
        return res
    }

    fun clearTrack() {
        if (trackDB == null) openDatabase()
        if (trackDB != null) {
            trackDB!!.execSQL("DELETE FROM track")
        }
    }

    fun addPoint(continous: Boolean, latitude: Double, longitude: Double, elevation: Double,
                 speed: Float, bearing: Float, accuracy: Float, time: Long) {
        if (trackDB == null) {
            openDatabase()
            if (trackDB == null) return
        }

        val values = ContentValues()
        values.put("latitude", latitude)
        values.put("longitude", longitude)
        values.put("code", if (continous) 0 else 1)
        values.put("elevation", elevation)
        values.put("speed", speed)
        values.put("track", bearing)
        values.put("accuracy", accuracy)
        values.put("datetime", time)

        try {
            trackDB!!.insertOrThrow("track", null, values)
            if (!continous) {
                drLog("TRACK_DR: point written lat=$latitude lon=$longitude speed=$speed")
            }
        } catch (e: SQLException) {
            //Log.e(TAG, "addPoint", e)
            errorMsg = e.message ?: "Unknown error"
            errorTime = System.currentTimeMillis()
            updateNotification()
            closeDatabase()
        }
    }

    private fun writeLocation(loc: Location, continous: Boolean) {
        //Log.d(TAG, "Fix needs writing")
        lastWritenLocation = loc
        distanceFromLastWriting = 0.0
        addPoint(continous, loc.latitude, loc.longitude, loc.altitude,
            loc.speed, loc.bearing, loc.accuracy, loc.time)

        for (callback in trackingCallbacks) {
            callback.onNewPoint(continous, loc.latitude, loc.longitude,
                loc.altitude, loc.speed.toDouble(), loc.bearing.toDouble(), loc.accuracy.toDouble(), loc.time)
        }

        val n = trackingRemoteCallbacks.beginBroadcast()
        for (i in 0 until n) {
            val callback = trackingRemoteCallbacks.getBroadcastItem(i)
            try {
                callback.onNewPoint(continous, loc.latitude, loc.longitude,
                    loc.altitude, loc.speed.toDouble(), loc.bearing.toDouble(), loc.accuracy.toDouble(), loc.time)
            } catch (e: RemoteException) {
                //Log.e(TAG, "Point broadcast error", e)
            }
        }
        trackingRemoteCallbacks.finishBroadcast()
    }

    private fun writeTrack(loc: Location, continous: Boolean) {
        var needsWrite = false
        val lastLoc = lastLocation
        if (lastLoc != null) {
            distanceFromLastWriting += loc.distanceTo(lastLoc)
        }
        if (lastWritenLocation != null) {
            timeFromLastWriting = loc.time - lastWritenLocation!!.time
        }

        if (lastLocation == null || lastWritenLocation == null || !continous ||
            timeFromLastWriting > maxTime ||
            (distanceFromLastWriting > minDistance && timeFromLastWriting > minTime)
        ) {
            needsWrite = true
        }

        lastLocation = loc

        if (needsWrite) {
            writeLocation(loc, continous)
        }
    }

    private fun tearTrack() {
        if (lastLocation != null && (lastWritenLocation == null || lastLocation.toString() != lastWritenLocation.toString())) {
            writeLocation(lastLocation!!, isContinous)
        }
        isContinous = false
    }

    private fun updateLocation() {
        val location = lastKnownLocation!!
        val continous = isContinous
        val geoid = !nmeaGeoidHeight.isNaN()
        val smoothspeed = smoothSpeed
        val avgspeed = avgSpeed

        // Винаги main looper — DR callbacks идват от sensor thread
        val handler = Handler(Looper.getMainLooper())

        if (trackingEnabled) {
            if (!continous) {
                drLog("TRACK_DR: writeTrack called via updateLocation (DR point)")
            }
            handler.post { writeTrack(location, continous) }
        }
        for (callback in locationCallbacks) {
            handler.post { callback.onLocationChanged(location, continous, geoid, smoothspeed, avgspeed) }
        }

        val n = locationRemoteCallbacks.beginBroadcast()
        for (i in 0 until n) {
            val callback = locationRemoteCallbacks.getBroadcastItem(i)
            try {
                callback.onLocationChanged(location, continous, geoid, smoothspeed, avgspeed)
            } catch (e: RemoteException) {
                //Log.e(TAG, "Location broadcast error", e)
            }
        }
        locationRemoteCallbacks.finishBroadcast()
    }

    private fun updateLocation(callback: ILocationListener) {
        if (lastKnownLocation?.provider != "unknown") {
            callback.onLocationChanged(lastKnownLocation!!, isContinous, !nmeaGeoidHeight.isNaN(), smoothSpeed, avgSpeed)
        }
    }

    private fun updateProvider(provider: String, enabled: Boolean) {
        if (LocationManager.GPS_PROVIDER == provider) {
            updateNotification()
        }
        val handler = Handler()
        for (callback in locationCallbacks) {
            handler.post {
                if (enabled) {
                    callback.onProviderEnabled(provider)
                } else {
                    callback.onProviderDisabled(provider)
                }
            }
        }

        val n = locationRemoteCallbacks.beginBroadcast()
        for (i in 0 until n) {
            val callback = locationRemoteCallbacks.getBroadcastItem(i)
            try {
                if (enabled) {
                    callback.onProviderEnabled(provider)
                } else {
                    callback.onProviderDisabled(provider)
                }
            } catch (e: RemoteException) {
                //Log.e(TAG, "Provider broadcast error", e)
            }
        }
        locationRemoteCallbacks.finishBroadcast()
        Log.d(TAG, "Provider status dispatched: " + (locationCallbacks.size + n))
    }

    private fun updateProvider(callback: ILocationListener) {
        if (gpsStatus == GPS_OFF) {
            callback.onProviderDisabled(LocationManager.GPS_PROVIDER)
        } else {
            callback.onProviderEnabled(LocationManager.GPS_PROVIDER)
        }
    }

    override fun onLocationChanged(location: Location) {
        tics++

        var fromGps = false
        var sendUpdate = false
        val time = SystemClock.elapsedRealtime()

        if (LocationManager.NETWORK_PROVIDER == location.provider) {
            if (useNetwork && (gpsStatus == GPS_OFF || (gpsStatus == GPS_SEARCHING && time > lastLocationMillis + gpsLocationTimeout))) {
                Log.d(TAG, "New location")
                lastKnownLocation = location
                lastLocationMillis = time
                isContinous = false
                sendUpdate = true
            } else {
                return
            }
        } else {
            fromGps = true
            val prevLocationMillis = lastLocationMillis
            val prevSpeed = lastKnownLocation!!.speed
            val prevTrack = lastKnownLocation!!.bearing
            lastKnownLocation = location
            if (lastKnownLocation!!.speed == 0f && prevTrack != 0f) {
                lastKnownLocation!!.bearing = prevTrack
            }
            lastLocationMillis = time

            // Обновяване на ring buffer за DR (последни 3 GPS точки)
            val bufSizeBefore = gpsRingBuffer.size()
            gpsRingBuffer.add(
                location.latitude, location.longitude, location.altitude,
                location.speed, location.bearing, location.accuracy, lastFsats
            )
            if (gpsRingBuffer.size() != bufSizeBefore) {
                drLog("RINGBUF: add #${gpsRingBuffer.size()} lat=${location.latitude} lon=${location.longitude} speed=${location.speed} fsats=$lastFsats")
            }

            // Kalman GPS update
            if (!drKalman.isActive()) {
                // Lazy init при първи GPS fix
                drKalman.initialize(
                    location.latitude, location.longitude, location.altitude,
                    location.speed.toDouble(), location.bearing.toDouble(),
                    location.accuracy
                )
                drLog("KALMAN_INIT: lazy init from GPS fix, speed=${location.speed}")
            } else if (drActive) {
                // GPS се е върнал по време на активен DR
                // ПЪРВО спираме DR dispatch, ПОСЛЕ реинициализираме
                drLog("GPS_RECOVERY: GPS fix arrived during active DR, stopping...")
                drActive = false
                drState = DR_STOPPED
                drToast("GPS recovered")
                DRLogger.log(this, "GPS_RECOVERY: reinitializing Kalman")
                DRLogger.close()
                drKalman.initialize(
                    location.latitude, location.longitude, location.altitude,
                    location.speed.toDouble(), location.bearing.toDouble(),
                    location.accuracy
                )
                drLog("GPS_RECOVERY: Kalman reinitialized, GPS will take over")
            } else {
                // Нормален режим: GPS е наличен, DR не е активен → учим bias и обновяваме
                drKalman.estimateBiasFromVelocity(
                    location.speed.toDouble(),
                    location.bearing.toDouble()
                )
                val relPos = drKalman.gpsToRelative(location.latitude, location.longitude)
                val velN = location.speed.toDouble() * kotlin.math.cos(Math.toRadians(location.bearing.toDouble()))
                val velE = location.speed.toDouble() * kotlin.math.sin(Math.toRadians(location.bearing.toDouble()))
                drKalman.updateWithGPS(relPos[0], relPos[1], velN, velE, location.accuracy)
            }

            sendUpdate = true
            if (!nmeaGeoidHeight.isNaN()) {
                lastKnownLocation!!.altitude = lastKnownLocation!!.altitude + nmeaGeoidHeight
            }
            if (justStarted) {
                justStarted = prevSpeed == 0f
            } else if (lastKnownLocation!!.speed > 0) {
                val a = 2 * 9.8 * (lastLocationMillis - prevLocationMillis) / 1000
                if (Math.abs(lastKnownLocation!!.speed - prevSpeed) > a) {
                    lastKnownLocation!!.speed = prevSpeed
                }
            }

            var smoothspeed = 0f
            val curspeed = lastKnownLocation!!.speed
            for (i in speed.size - 1 downTo 2) {
                smoothspeed += speed[i]
                speed[i] = speed[i - 1]
            }
            smoothspeed += speed[1]
            if (speed[1] < speed[0] && speed[0] > curspeed) {
                speed[0] = (speed[1] + curspeed) / 2
            }
            smoothspeed += speed[0]
            speed[1] = speed[0]
            lastKnownLocation!!.speed = speed[1]
            speed[0] = curspeed
            smoothspeed = if (speed[0] == 0f && speed[1] == 0f) 0f else smoothspeed / speed.size

            var avspeed = 0f
            for (v in speedav) {
                avspeed += v
            }
            avspeed /= speedav.size
            if (tics % pause == 0L) {
                if (avspeed > 0) {
                    val diff = curspeed / avspeed
                    if (0.95 < diff && diff < 1.05) {
                        System.arraycopy(speedav, 0, speedav, 1, speedav.size - 1)
                        speedav[0] = curspeed
                    }
                }
                var fluct = 0f
                for (i in speedavex.size - 1 downTo 1) {
                    fluct += speedavex[i] / curspeed
                    speedavex[i] = speedavex[i - 1]
                }
                fluct += speedavex[0] / curspeed
                speedavex[0] = curspeed
                fluct /= speedavex.size
                if (0.95 < fluct && fluct < 1.05) {
                    System.arraycopy(speedavex, 0, speedav, 0, speedav.size)
                    if (pause < 5) pause++
                }
            }

            smoothSpeed = smoothspeed
            avgSpeed = avspeed
        }

        if (sendUpdate) {
            updateLocation()
        }
        isContinous = fromGps
    }

    /**
     * Processes NMEA sentences received from the GPS hardware via OnNmeaMessageListener.
     * Extracts satellite counts (fsats from GGA, tsats from GSV), geoid height (GGA),
     * and dilution-of-precision values (GSA). Dispatches satellite count changes to all
     * registered ILocationListener callbacks (local + AIDL remote).
     *
     * Note: Parameters are ordered (nmea, timestamp) for OnNmeaMessageListener (API 24+),
     * which is the reverse of the deprecated NmeaListener.onNmeaReceived(timestamp, nmea).
     */
    override fun onNmeaMessage(nmea: String, timestamp: Long) {
        if (nmea.indexOf('\n') == 0) return
        var nmeaLine = nmea
        if (nmea.indexOf('\n') > 0) {
            nmeaLine = nmea.substring(0, nmea.indexOf('\n') - 1)
        }
        var len = nmeaLine.length
        if (len < 9) return
        // Strip NMEA checksum (e.g. *7F)
        if (nmeaLine[len - 3] == '*') {
            nmeaLine = nmeaLine.substring(0, len - 3)
        }
        val tokens = nmeaLine.split(",")
        val sentenceId = if (tokens[0].length > 5) tokens[0].substring(3, 6) else ""

        try {
            when (sentenceId) {
                "GGA" -> {
                    // GGA: time,lat,latH,lon,lonH,quality,numSat,hdop,alt,altU,geoid,geoidU,...
                    // token[7] = number of satellites used in fix
                    if (tokens.size > 7) {
                        val numSat = tokens[7]
                        if (numSat.isNotEmpty()) {
                            val newFsats = numSat.toInt()
                            if (newFsats != fsats) {
                                fsats = newFsats
                                dispatchSatelliteUpdate()
                            }
                        }
                    }
                    // token[11] = height of geoid above WGS84 ellipsoid (meters)
                    if (tokens.size > 11) {
                        val heightOfGeoid = tokens[11]
                        if (heightOfGeoid.isNotEmpty()) {
                            nmeaGeoidHeight = heightOfGeoid.toFloat()
                        }
                    }
                }
                "GSA" -> {
                    // GSA: mode,fixType,sat1..sat12,pdop,hdop,vdop
                    if (tokens.size > 17) {
                        val hdop = tokens[16]
                        val vdop = tokens[17]
                        if (hdop.isNotEmpty()) {
                            HDOP = hdop.toFloat()
                        }
                        if (vdop.isNotEmpty()) {
                            VDOP = vdop.toFloat()
                        }
                    }
                }
                "GSV" -> {
                    // GSV: numMsgs,msgNum,svsInView, then 4× (prn,elev,azim,snr)
                    // token[3] = total satellites in view for this constellation
                    // NOTE: multiple GSV sentences per constellation — last one's svsInView wins.
                    // For multi-constellation (GPS+GLONASS+Galileo), individual GSV per constellation
                    // overwrites tsats. A complete implementation would sum them up.
                    if (tokens.size > 3) {
                        val inViewStr = tokens[3]
                        if (inViewStr.isNotEmpty()) {
                            val inView = inViewStr.toInt()
                            if (inView != tsats) {
                                tsats = inView
                                dispatchSatelliteUpdate()
                            }
                        }
                    }
                }
            }
        } catch (e: NumberFormatException) {
            //Log.e(TAG, "NMEA parse error", e)
        } catch (e: ArrayIndexOutOfBoundsException) {
            //Log.e(TAG, "NMEA parse error", e)
        }
    }

    /**
     * Dispatches satellite counts (fsats/tsats) to all registered listeners.
     * Updates the GPS status indicator: GPS_OK if any satellites are visible,
     * GPS_SEARCHING otherwise. Notifies both in-process (locationCallbacks)
     * and cross-process (locationRemoteCallbacks, via AIDL) listeners.
     */
    private fun dispatchSatelliteUpdate() {
        // Update GPS status based on satellite counts
        val newStatus = when {
            tsats > 0 || fsats > 0 -> GPS_OK
            else -> GPS_SEARCHING
        }
        if (newStatus != gpsStatus) {
            gpsStatus = newStatus
            gnssStatus = newStatus
            updateNotification()
        }

        lastFsats = fsats  // за DR ring buffer

        // Dispatch to local in-process callbacks (Information, MapActivity, HSI, NavigationService)
        for (callback in locationCallbacks) {
            callback.onGpsStatusChanged(LocationManager.GPS_PROVIDER, gpsStatus, fsats, tsats)
        }

        // Dispatch to remote cross-process callbacks (AIDL)
        val n = locationRemoteCallbacks.beginBroadcast()
        for (i in 0 until n) {
            val callback = locationRemoteCallbacks.getBroadcastItem(i)
            try {
                callback.onGpsStatusChanged(LocationManager.GPS_PROVIDER, gpsStatus, fsats, tsats)
            } catch (e: RemoteException) {
                Log.e(TAG, "Satellite update broadcast error", e)
            }
        }
        locationRemoteCallbacks.finishBroadcast()
    }

    override fun onProviderDisabled(provider: String) {
        updateProvider(provider, false)
        if (LocationManager.GPS_PROVIDER == provider) {
            drLog("GPS_DISABLED: drActive=$drActive, ringBuf=${gpsRingBuffer.size()}, fsats=$fsats")
            if (!drActive) {
                startDeadReckoning()
            }
        }
    }

    override fun onProviderEnabled(provider: String) {
        updateProvider(provider, true)
        if (LocationManager.GPS_PROVIDER == provider) {
            drLog("GPS_ENABLED: drActive=$drActive")
            if (drActive) {
                stopDeadReckoning()
            }
        }
    }

    override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {
        if (LocationManager.GPS_PROVIDER == provider) {
            if (status == LocationProvider.TEMPORARILY_UNAVAILABLE || status == LocationProvider.OUT_OF_SERVICE) {
                tearTrack()
                updateNotification()
            }
        }
    }

    // ========================================================================
    // Dead Reckoning — Kalman 6-state архитектура
    // Сензорите са регистрирани ПОСТОЯННО (registerDrSensorsContinuously в onCreate)
    // Kalman.predict() → всяко accel събитие (Earth-frame, gravity-removed)
    // Kalman.updateWithGPS() → всеки GPS fix (в onLocationChanged)
    // DR dispatch → 5Hz когато drActive=true
    // ========================================================================

    private fun registerDrSensorsContinuously() {
        if (sensorsRegistered) return
        var count = 0
        drAccelerometer?.let {
            drSensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            count++
        }
        drGyroscope?.let {
            drSensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            count++
        }
        drMagnetometer?.let {
            drSensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            count++
        }
        drBarometer?.let {
            drSensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            count++
        }
        drRotationVector?.let {
            drSensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            count++
        }
        sensorsRegistered = true
        drLog("DR_SENSORS_CONTINUOUS: registered $count sensors @SENSOR_DELAY_GAME")
    }

    private fun unregisterDrSensors() {
        drSensorManager?.unregisterListener(this)
        sensorsRegistered = false
    }

    private fun startDeadReckoning() {
        drLog("DR_START: requested (Kalman)")
        val snapshots = gpsRingBuffer.getAll()
        drLog("DR_START: ringBuffer size=${snapshots.size}")
        if (snapshots.isEmpty()) {
            drLog("DR_START: FAILED — ring buffer empty, cannot start")
            drToast("FAILED: ring buffer empty")
            return
        }

        val latest = snapshots[0]

        // Ре-инициализация на Kalman с последната GPS точка
        // (Kalman вече работи от първия GPS fix, но refresh-ваме reference)
        drKalman.initialize(
            latest.lat, latest.lon, latest.alt,
            latest.speed.toDouble(), latest.bearing.toDouble(),
            latest.accuracy
        )

        drState = DR_ACTIVE
        drActive = true
        drStartTime = SystemClock.elapsedRealtime()
        drLastBroadcastTime = 0
        dispatchCount = 0
        drHeadingHistory.fill(Double.NaN)
        drHeadingHistoryIdx = 0
        drFilteredHeading = Double.NaN

        // Log snapshot — 6-те компонента + начални условия
        drLog("DR_START: OK (Kalman) — lat=${latest.lat}, lon=${latest.lon}, speed=${latest.speed}, bearing=${latest.bearing}, acc=${latest.accuracy}")
        drLog(drKalman.getInitialSnapshot())

        // Ако вече имаме кеширани сензорни стойности, логваме ги като snapshot
        if (drSensorValuesCaptured) {
            drLog(String.format(java.util.Locale.US,
                "SENSOR_SNAPSHOT: accel=[%.3f,%.3f,%.3f], gyro=[%.4f,%.4f,%.4f], mag=[%.1f,%.1f,%.1f], rot=[%.4f,%.4f,%.4f], earthAccel=[%.4f,%.4f]",
                drSnapshotAccelX, drSnapshotAccelY, drSnapshotAccelZ,
                drSnapshotGyroX, drSnapshotGyroY, drSnapshotGyroZ,
                drSnapshotMagX, drSnapshotMagY, drSnapshotMagZ,
                drSnapshotRotX, drSnapshotRotY, drSnapshotRotZ,
                drEarthAccelN, drEarthAccelE))
        }

        drToast("ACTIVE (K)! speed=${String.format("%.1f", latest.speed)}m/s")
        DRLogger.log(this, "DR_START_KALMAN: speed=${latest.speed}, bearing=${latest.bearing}")
    }

    private fun stopDeadReckoning() {
        val elapsed = if (drStartTime > 0) SystemClock.elapsedRealtime() - drStartTime else 0
        drLog("DR_STOP: duration=${elapsed}ms, state=$drState, kalman=${drKalman.getDebugState()}")
        drToast("STOPPED (${elapsed/1000}s)")

        if (drState != DR_STOPPED) {
            drState = DR_STOPPED
        }
        drActive = false
        // Kalman продължава да работи — bias estimation не спира
        // Само спираме DR dispatch-а
        drSensorValuesCaptured = false

        DRLogger.log(this, "DR_STOP_KALMAN: duration=$elapsed")
        DRLogger.close()
    }

    /**
     * Heading филтър — предотвратява скокове > 30°
     * Ако нов heading се различава от медианата на последните 5 с > 30°:
     *   - Игнорира се освен ако 3+ последователни показания са в новата посока
     */
    private fun filterHeading(newHeading: Double): Double {
        // Добавяне в историята
        drHeadingHistory[drHeadingHistoryIdx] = newHeading
        drHeadingHistoryIdx = (drHeadingHistoryIdx + 1) % 5

        val valid = drHeadingHistory.filter { !it.isNaN() }.sorted()
        if (valid.size < 2) return newHeading

        val median = valid[valid.size / 2]

        // Изчисляване на ъглова разлика
        var diff = newHeading - median
        if (diff > 180.0) diff -= 360.0
        if (diff < -180.0) diff += 360.0

        if (kotlin.math.abs(diff) < 30.0) {
            // В рамките на 30° → приемаме
            drFilteredHeading = newHeading
            return newHeading
        }

        // Голям скок — проверяваме дали е устойчив
        val recentCount = drHeadingHistory.count { h ->
            if (h.isNaN()) return@count false
            var d = newHeading - h
            if (d > 180.0) d -= 360.0
            if (d < -180.0) d += 360.0
            kotlin.math.abs(d) < 30.0
        }

        return if (recentCount >= 3) {
            // 3+ показания в новата посока → реален завой, приемаме
            drFilteredHeading = newHeading
            newHeading
        } else {
            // Отхвърляме — връщаме филтрирания или медианата
            if (drFilteredHeading.isNaN()) median else drFilteredHeading
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val sensorType = event.sensor?.type ?: return

        try {
            // Логване на първото сензорно събитие (потвърждава че постоянната регистрация работи)
            if (drSensorFirstEvent) {
                drSensorFirstEvent = false
                val sensorName = event.sensor?.name ?: "unknown"
                drLog("DR_FIRST_SENSOR: $sensorName (continuous mode)")
            }

            when (sensorType) {
                Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_ACCELEROMETER -> {
                    val isLinear = (sensorType == Sensor.TYPE_LINEAR_ACCELERATION)
                    val ax = event.values[0].toDouble()
                    val ay = event.values[1].toDouble()
                    val az = event.values[2].toDouble()

                    // Кеширане за snapshot
                    drSnapshotAccelX = event.values[0]
                    drSnapshotAccelY = event.values[1]
                    drSnapshotAccelZ = event.values[2]

                    // Gravity removal
                    val linearAccelX: Double
                    val linearAccelY: Double
                    val linearAccelZ: Double
                    if (isLinear) {
                        linearAccelX = ax
                        linearAccelY = ay
                        linearAccelZ = az
                    } else {
                        val beta = 0.9
                        drGravityX = beta * drGravityX + (1 - beta) * ax
                        drGravityY = beta * drGravityY + (1 - beta) * ay
                        drGravityZ = beta * drGravityZ + (1 - beta) * az
                        linearAccelX = ax - drGravityX
                        linearAccelY = ay - drGravityY
                        linearAccelZ = az - drGravityZ
                    }

                    // Transform device→Earth frame
                    val accelN: Double
                    val accelE: Double
                    if (drRotMatrixReady) {
                        val deviceAccel = floatArrayOf(linearAccelX.toFloat(), linearAccelY.toFloat(), linearAccelZ.toFloat(), 0f)
                        val earthAccel = FloatArray(4)
                        android.opengl.Matrix.multiplyMV(earthAccel, 0, drRotMatrix4x4, 0, deviceAccel, 0)
                        // Android: earthAccel[0]=East, earthAccel[1]=North
                        accelE = earthAccel[0].toDouble()
                        accelN = earthAccel[1].toDouble()
                    } else {
                        // Fallback: предполагаме хоризонтално устройство (phone X→North, Y→East)
                        accelN = linearAccelX
                        accelE = linearAccelY
                    }

                    drEarthAccelN = accelN
                    drEarthAccelE = accelE
                    drSensorValuesCaptured = true

                    // Kalman prediction (винаги — и при GPS ON, и при DR)
                    drKalman.predict(accelN, accelE, event.timestamp)
                }

                Sensor.TYPE_GAME_ROTATION_VECTOR, Sensor.TYPE_ROTATION_VECTOR -> {
                    drSnapshotRotX = if (event.values.size > 0) event.values[0] else 0f
                    drSnapshotRotY = if (event.values.size > 1) event.values[1] else 0f
                    drSnapshotRotZ = if (event.values.size > 2) event.values[2] else 0f
                    drSensorValuesCaptured = true

                    try {
                        SensorManager.getRotationMatrixFromVector(drRotMatrix3x3, event.values)
                        // Конвертиране 3×3 → 4×4
                        drRotMatrix4x4[0] = drRotMatrix3x3[0]; drRotMatrix4x4[1] = drRotMatrix3x3[1]
                        drRotMatrix4x4[2] = drRotMatrix3x3[2]; drRotMatrix4x4[3] = 0f
                        drRotMatrix4x4[4] = drRotMatrix3x3[3]; drRotMatrix4x4[5] = drRotMatrix3x3[4]
                        drRotMatrix4x4[6] = drRotMatrix3x3[5]; drRotMatrix4x4[7] = 0f
                        drRotMatrix4x4[8] = drRotMatrix3x3[6]; drRotMatrix4x4[9] = drRotMatrix3x3[7]
                        drRotMatrix4x4[10] = drRotMatrix3x3[8]; drRotMatrix4x4[11] = 0f
                        drRotMatrix4x4[12] = 0f; drRotMatrix4x4[13] = 0f
                        drRotMatrix4x4[14] = 0f; drRotMatrix4x4[15] = 1f
                        drRotMatrixReady = true
                        drLastRotVectorTimestamp = event.timestamp
                    } catch (_: Exception) {}
                }

                Sensor.TYPE_GYROSCOPE -> {
                    drSnapshotGyroX = event.values[0]
                    drSnapshotGyroY = event.values[1]
                    drSnapshotGyroZ = event.values[2]
                    drSensorValuesCaptured = true
                }

                Sensor.TYPE_MAGNETIC_FIELD -> {
                    drSnapshotMagX = event.values[0]
                    drSnapshotMagY = event.values[1]
                    drSnapshotMagZ = event.values[2]
                    drSensorValuesCaptured = true
                }

                Sensor.TYPE_PRESSURE -> {
                    // Барометър — само кеширане
                    drSensorValuesCaptured = true
                }
            }

            // ============================================================
            // DR Dispatch (5Hz, само когато GPS е изключен)
            // ============================================================
            if (drActive && drState == DR_ACTIVE && drKalman.isActive()) {
                // Проверка за timeout
                if (SystemClock.elapsedRealtime() - drStartTime > DR_MAX_DURATION_MS) {
                    drLog("DR_TIMEOUT: max duration reached")
                    stopDeadReckoning()
                    return
                }

                val now = SystemClock.elapsedRealtime()
                if (now - drLastBroadcastTime >= DR_BROADCAST_INTERVAL_MS) {
                    drLastBroadcastTime = now

                    val drLoc = drKalman.getCurrentLocation()
                    val location = Location("dead_reckoning")
                    location.latitude = drLoc.latitude
                    location.longitude = drLoc.longitude
                    location.altitude = drLoc.altitude
                    location.speed = drLoc.speed
                    location.bearing = drLoc.bearing
                    location.accuracy = drLoc.accuracy
                    location.time = drLoc.timestamp

                    // Rate-limited logging (всеки 10-ти dispatch ≈ на 2 сек)
                    dispatchCount++
                    if (dispatchCount % 10 == 0) {
                        val prevLoc = lastKnownLocation
                        if (prevLoc != null && prevLoc.provider != "dead_reckoning") {
                            val dist = location.distanceTo(prevLoc)
                            drLog("DR_DISPATCH: lat=${drLoc.latitude}, lon=${drLoc.longitude}, speed=${drLoc.speed}, bearing=${drLoc.bearing}, distFromGPS=${"%.2f".format(dist)}m")
                        }
                        drLog(drKalman.getDebugState())
                    }

                    // Подаване през съществуващия pipeline
                    lastKnownLocation = location
                    isContinous = false
                    updateLocation()
                }
            }
        } catch (e: Exception) {
            drLog("DR_SENSOR_ERROR: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Не се изисква действие
    }

    inner class LocalBinder : Binder(), ILocationService {
        override fun registerLocationCallback(callback: ILocationListener) {
            updateProvider(callback)
            updateLocation(callback)
            locationCallbacks.add(callback)
        }

        override fun unregisterLocationCallback(callback: ILocationListener) {
            locationCallbacks.remove(callback)
        }

        override fun registerTrackingCallback(callback: ITrackingListener) {
            trackingCallbacks.add(callback)
        }

        override fun unregisterTrackingCallback(callback: ITrackingListener) {
            trackingCallbacks.remove(callback)
        }

        override fun isLocating(): Boolean {
            return locationsEnabled
        }

        override fun isTracking(): Boolean {
            return trackingEnabled
        }

        override fun getHDOP(): Float {
            return HDOP
        }

        override fun getVDOP(): Float {
            return VDOP
        }

        override fun getTrack(): Track {
            return this@LocationService.getTrack()
        }

        override fun getTrack(start: Long, end: Long): Track {
            return this@LocationService.getTrack(start, end)
        }

        override fun clearTrack() {
            this@LocationService.clearTrack()
        }

        override fun getTrackStartTime(): Long {
            return this@LocationService.getTrackStartTime()
        }

        override fun getTrackEndTime(): Long {
            return this@LocationService.getTrackEndTime()
        }

        override fun addPoint(continous: Boolean, latitude: Double, longitude: Double, elevation: Double,
                              speed: Float, bearing: Float, accuracy: Float, time: Long) {
            this@LocationService.addPoint(continous, latitude, longitude, elevation, speed, bearing, accuracy, time)
        }
    }
}