package com.borkozic.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import com.borkozic.MapActivity
import com.borkozic.R
// DRLogger за файл-базирано логване към Downloads/Borkozic/dr_log_<дата>.txt

/**
 * Dead Reckoning Service — използва IMU сензори за изчисляване на позиция при GPS загуба.
 *
 * Архитектура:
 * - Extends BaseLocationService, implements SensorEventListener
 * - Foreground service с notification channel
 * - Регистрира сензори: accelerometer, gyroscope, magnetometer, barometer
 * - Съхранява последните GPS данни за начална точка
 * - Използва DeadReckoningCalculator за sensor fusion алгоритъма
 * - State machine: DR_IDLE → DR_ACTIVE → DR_STOPPED
 * - Излъчва broadcasts при промяна на състоянието и изчислена позиция
 * - Всички broadcasts са explicit (.setPackage) за RECEIVER_NOT_EXPORTED съвместимост
 */
class DeadReckoningService : BaseLocationService(), SensorEventListener {
    companion object {
        private const val TAG = "DeadReckoning"
        private const val NOTIFICATION_ID = 24162
        private const val NOTIFICATION_CHANNEL_ID = "com.borkozic.deadreckoning"

        // Broadcast actions
        const val BROADCAST_DR_STATE = "com.borkozic.deadReckoningStateChanged"
        const val BROADCAST_DR_LOCATION = "com.borkozic.deadReckoningLocation"

        // Константи за DR алгоритъм
        const val MAX_DURATION_MS = 180_000L      // 3 минути максимална продължителност
        const val ACTIVATION_DELAY_MS = 5_000L    // 5 секунди чакане преди активиране
        const val BROADCAST_INTERVAL_MS = 200L    // 200ms интервал за broadcast/логване (5Hz)

        // Статуси на DR state machine
        const val DR_IDLE = 0      // Неактивен, чака GPS загуба + activation delay
        const val DR_ACTIVE = 1    // GPS загубен, изчислява позиция от сензори
        const val DR_STOPPED = 2   // Максимално време достигнато или GPS възстановен
    }

    // Сензори и сензорен мениджър
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var magnetometer: Sensor? = null
    private var barometer: Sensor? = null
    private var rotationVector: Sensor? = null

    // DR калкулатор — изпълнява sensor fusion алгоритъма
    private val calculator = DeadReckoningCalculator()

    // Последни GPS данни (начална точка за DR)
    var lastLat: Double = 0.0
    var lastLon: Double = 0.0
    var lastAltitude: Double = 0.0
    var lastSpeed: Float = 0f       // m/s
    var lastBearing: Float = 0f     // degrees (истински heading, след fixDeclination)
    var lastAccuracy: Float = 0f
    var lastGpsTime: Long = 0       // SystemClock.elapsedRealtime()

    // DR състояние
    private var drState = DR_IDLE
    // Ръчен режим — DR включен от потребителя, не спира при GPS recovery
    private var manualMode: Boolean = false
    private var activationTime: Long = 0
    private var drStartTime: Long = 0  // Време на активиране (за MAX_DURATION проверка)
    private var lastBroadcastTime: Long = 0  // Честотно ограничение за broadcast/логване (5Hz)

    // Notification и foreground service
    private var notification: Notification? = null
    private var isForeground = false
    private var contentIntent: PendingIntent? = null

    // Binder за клиентите
    private val binder = LocalBinder()

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    inner class LocalBinder : Binder() {
        fun getService(): DeadReckoningService = this@DeadReckoningService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()

        // Глобален crash handler — прилага UncaughtExceptionHandler за улавяне на crash-ове
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                DRLogger.log("CRASH: ${throwable.javaClass.simpleName}: ${throwable.message}")
                DRLogger.log("CRASH thread: ${thread.name}")
                // Запис на stack trace
                val sw = java.io.StringWriter()
                throwable.printStackTrace(java.io.PrintWriter(sw))
                DRLogger.log("CRASH stacktrace: ${sw.toString()}")
                DRLogger.close()
            } catch (e: Exception) {
                // Нищо не можем да направим ако самият логър crash-не
            }
            // Предаване към предишния handler за нормално crash поведение
            previousHandler?.uncaughtException(thread, throwable)
        }

        // Инициализация на сензорите
        // TYPE_LINEAR_ACCELERATION е за предпочитане (вече без gravity), иначе TYPE_ACCELEROMETER
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        magnetometer = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        barometer = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE)
        // Rotation vector: TYPE_GAME_ROTATION_VECTOR (без magnetometer) е за предпочитане за превозни средства
        // (не се влияе от магнитни смущения от мотор/рамка). Fallback към TYPE_ROTATION_VECTOR.
        rotationVector = sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        // Създаване на notification channel (задължително за foreground service на Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val chan = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Dead Reckoning",
                NotificationManager.IMPORTANCE_LOW
            )
            chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            manager.createNotificationChannel(chan)
        }

        // Създаване на content intent за notification (към MapActivity)
        val fallbackActivity = Intent(this, MapActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        )
        contentIntent = PendingIntent.getActivity(
            this,
            NOTIFICATION_ID,
            fallbackActivity,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Проверка за POST_NOTIFICATIONS permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "POST_NOTIFICATIONS permission not granted")
            }
        }
    }

    /**
     * Регистрира sensor listeners с висока честота (SENSOR_DELAY_GAME = 50ms / 20Hz)
     * за точна интеграция на сензорните данни.
     */
    private fun registerSensors() {
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gyroscope?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        magnetometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        barometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        rotationVector?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    /**
     * Дерегистрира sensor listeners.
     */
    private fun unregisterSensors() {
        sensorManager?.unregisterListener(this)
    }

    /**
     * Осигурява foreground service статус.
     * Следва патерна от NavigationService за съвместимост с Samsung Android 14:
     * 1. IMPORTANCE_LOW channel (не IMPORTANCE_NONE)
     * 2. Валиден contentIntent преди build()
     * 3. Notification.Builder(ctx, channelId) — НЕ NotificationCompat
     * 4. setOngoing(true), setChannelId() експлицитно
     * 5. FLAG_FOREGROUND_SERVICE флаг (Android 14+)
     * 6. Rebuild всеки път
     * 7. isForeground флаг — не вика startForeground() повторно
     */
    private fun ensureForeground() {
        if (isForeground) return

        try {
        val notif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val builder = Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentIntent(contentIntent)
                .setSmallIcon(R.drawable.ic_stat_navigation)
                .setWhen(0)
                .setOngoing(true)
                .setContentTitle("Borkozic DR")
                .setContentText("Dead reckoning активен")
            builder.setChannelId(NOTIFICATION_CHANNEL_ID)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                builder.setFlag(Notification.FLAG_FOREGROUND_SERVICE, true)
            }
            builder.build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentIntent(contentIntent)
                .setSmallIcon(R.drawable.ic_stat_navigation)
                .setWhen(0)
                .setOngoing(true)
                .setContentTitle("Borkozic DR")
                .setContentText("Dead reckoning активен")
                .build()
        }
        notification = notif

        // Samsung Android 14 workaround: първо публикуване на notification през NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, notif)
            startForeground(NOTIFICATION_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notif)
        }
        isForeground = true
        } catch (e: Exception) {
            // Notification/foreground crash (често на Samsung Android 14) — логваме и продължаваме
            DRLogger.log(this, "FG_ERROR: ${e.javaClass.simpleName}: ${e.message}")
            Log.e(TAG, "ensureForeground crash: ${e.message}", e)
        }
    }

    /**
     * Стартира Dead Reckoning алгоритъма с последните GPS данни.
     *
     * Извиква се от MapActivity при GPS загуба (onProviderDisabled).
     * Запазва последните GPS данни като начална точка, регистрира сензори,
     * и стартира activation delay таймер.
     *
     * @param lat последна известна ширина
     * @param lon последна известна дължина
     * @param alt последна известна височина
     * @param speed последна известна скорост (m/s)
     * @param bearing последен известен heading (истински, след fixDeclination)
     * @param accuracy последна известна точност (m)
     */
    fun startDeadReckoning(
        lat: Double, lon: Double, alt: Double,
        speed: Float, bearing: Float, accuracy: Float
    ) {
        // При автоматично стартиране manualMode винаги е false
        manualMode = false
        
        // Запазване на последните GPS данни
        lastLat = lat
        lastLon = lon
        lastAltitude = alt
        lastSpeed = speed
        lastBearing = bearing
        lastAccuracy = accuracy
        lastGpsTime = SystemClock.elapsedRealtime()

        // Задаване на време за активиране (след ACTIVATION_DELAY_MS секунди)
        activationTime = SystemClock.elapsedRealtime() + ACTIVATION_DELAY_MS
        drStartTime = SystemClock.elapsedRealtime()

        // Промяна на състоянието на IDLE (чака активиране)
        if (drState != DR_IDLE) {
            drState = DR_IDLE
            sendBroadcast(Intent(BROADCAST_DR_STATE).putExtra("state", drState).setPackage(packageName))
        }

        // Регистриране на сензорите
        registerSensors()

        Log.i(TAG, "Dead Reckoning started with initial position: $lat, $lon, $alt, speed=$speed, bearing=$bearing")
        // Запис в файл за анализ след полет
        DRLogger.log(this, "DR_START: lat=$lat, lon=$lon, alt=$alt, speed=$speed, bearing=$bearing, acc=$accuracy")
        DRLogger.log(this, "DR_START: activation delay=${ACTIVATION_DELAY_MS}ms, max duration=${MAX_DURATION_MS}ms")
        DRLogger.log(this, "DR_START: sensors — accel=${accelerometer != null} (${accelerometer?.name}), gyro=${gyroscope != null} (${gyroscope?.name}), mag=${magnetometer != null} (${magnetometer?.name}), baro=${barometer != null} (${barometer?.name}), rotVec=${rotationVector != null} (${rotationVector?.name})")
        DRLogger.log(this, "DR_START: accel type=${if (accelerometer?.type == Sensor.TYPE_LINEAR_ACCELERATION) "LINEAR_ACCELERATION" else "ACCELEROMETER"}")
        DRLogger.log(this, "DR_START: rotVec type=${if (rotationVector?.type == Sensor.TYPE_GAME_ROTATION_VECTOR) "GAME_ROTATION_VECTOR" else "ROTATION_VECTOR"}")
    }

    /**
     * Спира Dead Reckoning и връща контрола към GPS.
     *
     * Извиква се от MapActivity при GPS възстановяване (onProviderEnabled)
     * или при изтичане на максималната продължителност.
     */
    fun stopDeadReckoning() {
        // Дерегистриране на сензорите
        unregisterSensors()

        // Нулиране на калкулатора
        calculator.reset()

        // Промяна на състоянието на STOPPED
        if (drState != DR_STOPPED) {
            drState = DR_STOPPED
            sendBroadcast(Intent(BROADCAST_DR_STATE).putExtra("state", drState).setPackage(packageName))
        }

        // Спиране на foreground service
        if (isForeground) {
            stopForeground(true)
            isForeground = false
        }

        Log.i(TAG, "Dead Reckoning stopped")
        // Запис в файл за анализ след полет
        DRLogger.log(this, "DR_STOP: state=$drState")
        DRLogger.log(this, "DR_STOP: duration=${if (drStartTime > 0) SystemClock.elapsedRealtime() - drStartTime else 0}ms")
        DRLogger.log(this, "=== DR session ended ===")
        DRLogger.close()
    }

    /**
     * Проверява дали Dead Reckoning е активен.
     */
    fun isDeadReckoningActive(): Boolean {
        return drState == DR_ACTIVE
    }

    /**
     * Връща текущото DR състояние (DR_IDLE, DR_ACTIVE, DR_STOPPED).
     */
    fun getDRState(): Int {
        return drState
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Service се стартира чрез startDeadReckoning(), не чрез intents
        return Service.START_NOT_STICKY
    }

    /**
     * Обработка на сензорни събития.
     *
     * Разпределва данните към DeadReckoningCalculator и изпраща
     * broadcast с изчислената позиция на всеки sensor update.
     */
    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        try {
        // Определяне на сензор тип за логване и правилна обработка
        val sensorType = event.sensor?.type ?: return
        val sensorName = when (sensorType) {
            Sensor.TYPE_LINEAR_ACCELERATION -> "LINEAR_ACCEL"
            Sensor.TYPE_ACCELEROMETER -> "ACCEL"
            Sensor.TYPE_GYROSCOPE -> "GYRO"
            Sensor.TYPE_MAGNETIC_FIELD -> "MAG"
            Sensor.TYPE_PRESSURE -> "BARO"
            Sensor.TYPE_GAME_ROTATION_VECTOR -> "GAME_ROT"
            Sensor.TYPE_ROTATION_VECTOR -> "ROT_VEC"
            else -> "UNKNOWN($sensorType)"
        }

        // Логване на raw сензорни данни преди обработка
        DRLogger.logSensorData(this, sensorName, event.values, event.timestamp)

        when (sensorType) {
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                calculator.processAccelerometer(event.values, event.timestamp, true)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                calculator.processAccelerometer(event.values, event.timestamp, false)
            }
            Sensor.TYPE_GYROSCOPE -> {
                calculator.processGyroscope(event.values, event.timestamp)
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                calculator.processMagnetometer(event.values, event.timestamp)
            }
            Sensor.TYPE_PRESSURE -> {
                calculator.processBarometer(event.values[0], event.timestamp)
            }
            Sensor.TYPE_GAME_ROTATION_VECTOR, Sensor.TYPE_ROTATION_VECTOR -> {
                calculator.processRotationVector(event.values, event.timestamp)
            }
            else -> return
        }

        // Проверка за активиране на DR след изтичане на ACTIVATION_DELAY_MS
        if (drState == DR_IDLE && SystemClock.elapsedRealtime() >= activationTime) {
            // Иницизиране на калкулатора с последните GPS данни
            calculator.initialize(lastLat, lastLon, lastAltitude, lastSpeed, lastBearing, lastAccuracy)
            drState = DR_ACTIVE
            drStartTime = SystemClock.elapsedRealtime()
            sendBroadcast(Intent(BROADCAST_DR_STATE).putExtra("state", drState).setPackage(packageName))
            ensureForeground()
            Log.i(TAG, "Dead Reckoning activated")
            DRLogger.log(this, "DR_ACTIVATED: sensors initialized, starting position calculation")
        }

        // Ако DR е активен, изчисляване и изпращане на позиция
        if (drState == DR_ACTIVE) {
            // Проверка за изтичане на максималното време (3 мин)
            if (SystemClock.elapsedRealtime() - drStartTime > MAX_DURATION_MS) {
                Log.i(TAG, "Dead Reckoning max duration reached, stopping")
                DRLogger.log(this, "DR_TIMEOUT: max duration ${MAX_DURATION_MS}ms reached")
                stopDeadReckoning()
                return
            }

            // Честотно ограничение за broadcast и логване (5Hz = 200ms интервал)
            // Сензорите работят на ~20Hz, но няма нужда да пращаме broadcast на всеки event
            val now = SystemClock.elapsedRealtime()
            if (now - lastBroadcastTime >= BROADCAST_INTERVAL_MS) {
                lastBroadcastTime = now

                // Изчисляване на текущата позиция от калкулатора
                if (calculator.isActive()) {
                    val loc = calculator.getCurrentLocation()

                    // Излъчване на broadcast с новата изчислена позиция
                    val intent = Intent(BROADCAST_DR_LOCATION)
                        .setPackage(packageName)
                        .putExtra("latitude", loc.latitude)
                        .putExtra("longitude", loc.longitude)
                        .putExtra("altitude", loc.altitude)
                        .putExtra("speed", loc.speed)
                        .putExtra("bearing", loc.bearing)
                        .putExtra("accuracy", loc.accuracy)
                        .putExtra("timestamp", loc.timestamp)
                    sendBroadcast(intent)
                    // Логване на изчислена позиция
                    DRLogger.logLocation(this, loc.latitude, loc.longitude, loc.altitude, loc.speed, loc.bearing, loc.accuracy)
                    // Логване на детайлен debug state от калкулатора (heading, velocity, raw сензорни стойности)
                    DRLogger.log(this, calculator.getDebugState())
                }
            }
        }
        } catch (e: Exception) {
            // Улавяне на грешки в сензорната обработка — не позволяваме crash на service-а
            DRLogger.log(this, "SENSOR_ERROR: ${e.javaClass.simpleName}: ${e.message} (sensor type=${event.sensor?.type})")
            val sw = java.io.StringWriter()
            e.printStackTrace(java.io.PrintWriter(sw))
            DRLogger.log(this, "SENSOR_ERROR stacktrace: $sw")
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Не се изисква действие при промяна на точността на сензорите
    }

    /**
     * Ръчно стартиране на Dead Reckoning (от бутона в SidePanel).
     * Разликата с automatic startDeadReckoning:
     * - Няма activation delay (веднага е ACTIVE)
     * - manualMode=true → не се спира при GPS recovery
     * - Използва последните GPS данни като стартова точка
     */
    fun startManualDeadReckoning(
        lat: Double, lon: Double, alt: Double,
        speed: Float, bearing: Float, accuracy: Float
    ) {
        manualMode = true
        
        // Веднага ACTIVE без activation delay
        drStartTime = SystemClock.elapsedRealtime()
        activationTime = drStartTime  // Веднага активиран
        
        // Запазване на последните GPS данни
        lastLat = lat
        lastLon = lon
        lastAltitude = alt
        lastSpeed = speed
        lastBearing = bearing
        lastAccuracy = accuracy
        lastGpsTime = SystemClock.elapsedRealtime()
        
        // Регистриране на сензорите
        registerSensors()
        
        // Директно DR_ACTIVE (без IDLE чакане)
        if (drState != DR_ACTIVE) {
            drState = DR_ACTIVE
            sendBroadcast(Intent(BROADCAST_DR_STATE).putExtra("state", drState).setPackage(packageName))
        }
        
        // Инициализиране на калкулатора
        calculator.initialize(lat, lon, alt, speed, bearing, accuracy)
        
        Log.i(TAG, "Manual Dead Reckoning started: $lat, $lon, $alt, speed=$speed, bearing=$bearing")
        DRLogger.log(this, "DR_MANUAL_START: lat=$lat, lon=$lon, alt=$alt, speed=$speed, bearing=$bearing, acc=$accuracy")
        DRLogger.log(this, "DR_MANUAL_START: manual mode — DR will NOT stop on GPS recovery")
        DRLogger.log(this, "DR_START: sensors — accel=${accelerometer != null} (${accelerometer?.name}), gyro=${gyroscope != null} (${gyroscope?.name}), mag=${magnetometer != null} (${magnetometer?.name}), baro=${barometer != null} (${barometer?.name})")
    }

    /**
     * Проверява дали Dead Reckoning е в ръчен режим.
     */
    fun isManualMode(): Boolean {
        return manualMode
    }

    /**
     * Връща калкулатора за директен достъп (за GPS correction в manual mode).
     */
    fun getCalculator(): DeadReckoningCalculator? {
        return if (drState == DR_ACTIVE) calculator else null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopDeadReckoning()
    }
}
