package com.borkozic.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface

/**
 * Compass sensor helper — използва акселерометър + магнитометър за изчисляване
 * на посоката на устройството (азимут) спрямо магнитния север.
 *
 * Архитектура:
 * - Проверява за TYPE_MAGNETIC_FIELD и TYPE_ACCELEROMETER сензори.
 * - Регистрира listeners с SENSOR_DELAY_UI (подходящо за UI обновявания).
 * - В onSensorChanged: запазва последните accel/mag данни, извиква
 *   SensorManager.getRotationMatrix() → SensorManager.getOrientation().
 * - Прилага low-pass филтър (alpha = 0.15) за изглаждане на азимута.
 * - Добавя корекция за магнитна деклинация (подава се от caller-а).
 * - Remap-ва ротационната матрица според ротацията на екрана, за да бъде
 *   посоката правилна в портрет и пейзаж режим.
 *
 * Употреба:
 * - [hasCompass] — проверява дали устройството има магнитометър.
 * - [start] — регистрира listeners (напр. в onResume или при following ON).
 * - [stop] — deregister-ва listeners (напр. в onPause или при following OFF).
 * - [declination] — задава магнитна корекция (от Borkozic.declination).
 * - [CompassListener] — интерфейс за получаване на bearing обновявания.
 */
class CompassSensorHelper(
    private val context: Context,
    private val listener: CompassListener
) : SensorEventListener {

    /**
     * Интерфейс за обратна връзка от компас сензора.
     */
    interface CompassListener {
        /**
         * Извиква се при промяна на компас bearing (в градуси, 0–360).
         * Bearing-ът вече е коригиран за ротация на екрана и магнитна деклинация.
         */
        fun onCompassBearing(bearing: Float)

        /**
         * Извиква се при промяна на наличността на компаса (сензор намерен/изгубен).
         */
        fun onCompassAvailabilityChanged(hasCompass: Boolean)
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    private var isRegistered = false

    // Последни показания от сензорите
    private val accelValues = FloatArray(3)
    private val magValues = FloatArray(3)
    private var hasAccelData = false
    private var hasMagData = false

    // Състояние на low-pass филтъра
    private var filteredAzimuth = 0f
    private var hasFilteredAzimuth = false

    // Корекция за магнитна деклинация (в градуси), задава се от caller-а
    var declination: Float = 0f

    /**
     * True ако устройството има и акселерометър, и магнитометър.
     */
    val hasCompass: Boolean
        get() = magnetometer != null && accelerometer != null

    init {
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    }

    /**
     * Регистрира sensor listeners. Извиква се когато компасът трябва да е активен
     * (напр. following ON + устройството е неподвижно).
     */
    fun start() {
        if (isRegistered || !hasCompass) return

        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        magnetometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        isRegistered = true
        listener.onCompassAvailabilityChanged(true)
    }

    /**
     * Deregister-ва sensor listeners. Извиква се когато компасът трябва да спре
     * (напр. following OFF, app paused, устройството се движи).
     */
    fun stop() {
        if (!isRegistered) return
        sensorManager.unregisterListener(this)
        isRegistered = false
        hasAccelData = false
        hasMagData = false
        hasFilteredAzimuth = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor?.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, accelValues, 0, 3)
                hasAccelData = true
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, magValues, 0, 3)
                hasMagData = true
            }
            else -> return
        }

        if (hasAccelData && hasMagData) {
            computeBearing()
        }
    }

    /**
     * Изчислява bearing от последните accel/mag данни:
     * 1. getRotationMatrix() — ротационна матрица от device координати към света
     * 2. Remap според ротацията на екрана (портрет/пейзаж)
     * 3. getOrientation() — извлича азимут (Z ос), pitch, roll
     * 4. Конвертира в градуси [0, 360) и добавя деклинация
     * 5. Low-pass филтър за изглаждане
     */
    private fun computeBearing() {
        val rotationMatrix = FloatArray(9)
        val inclinationMatrix = FloatArray(9)

        val success = SensorManager.getRotationMatrix(
            rotationMatrix,
            inclinationMatrix,
            accelValues,
            magValues
        )

        if (!success) return

        // Remap на ротационната матрица според ротацията на екрана
        val display = (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay
        val remappedMatrix = FloatArray(9)
        when (display.rotation) {
            Surface.ROTATION_0 -> SensorManager.remapCoordinateSystem(
                rotationMatrix, SensorManager.AXIS_X, SensorManager.AXIS_Y, remappedMatrix
            )
            Surface.ROTATION_90 -> SensorManager.remapCoordinateSystem(
                rotationMatrix, SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, remappedMatrix
            )
            Surface.ROTATION_180 -> SensorManager.remapCoordinateSystem(
                rotationMatrix, SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y, remappedMatrix
            )
            Surface.ROTATION_270 -> SensorManager.remapCoordinateSystem(
                rotationMatrix, SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X, remappedMatrix
            )
            else -> SensorManager.remapCoordinateSystem(
                rotationMatrix, SensorManager.AXIS_X, SensorManager.AXIS_Y, remappedMatrix
            )
        }

        val orientationAngles = FloatArray(3)
        SensorManager.getOrientation(remappedMatrix, orientationAngles)

        // azimuth е в радиани, диапазон [-π, π]
        // Конвертиране в градуси [0, 360)
        var azimuthDeg = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
        azimuthDeg = (azimuthDeg + 360f) % 360f

        // Добавяне на корекция за магнитна деклинация
        azimuthDeg = (azimuthDeg + declination + 360f) % 360f

        // Low-pass филтър за изглаждане
        if (hasFilteredAzimuth) {
            // Обработка на wraparound (напр. 359° → 1°)
            val delta = ((azimuthDeg - filteredAzimuth + 540f) % 360f) - 180f
            filteredAzimuth = (filteredAzimuth + delta * ALPHA + 360f) % 360f
        } else {
            filteredAzimuth = azimuthDeg
            hasFilteredAzimuth = true
        }

        listener.onCompassBearing(filteredAzimuth)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Не се изисква действие — промените в точността се обработват
        // индиректно чрез low-pass филтъра
    }

    companion object {
        private const val ALPHA = 0.15f // Коефициент на low-pass филтъра (по-малко = по-гладко)
    }
}