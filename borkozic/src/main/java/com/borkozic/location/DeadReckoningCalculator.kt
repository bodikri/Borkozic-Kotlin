package com.borkozic.location

import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Dead Reckoning Calculator — Trajectory-based версия.
 *
 * Вместо double-integration на акселерометър (Kalman Filter), този алгоритъм:
 *
 * 1. Извлича вектори на движение от последните 3 GPS точки:
 *    - Текуща скорост и посока (speed, bearing) от най-новата точка
 *    - Тренд на ускорението от разликата между двата velocity вектора
 *
 * 2. Коригира тези вектори със сензорни данни във времето:
 *    - Gyro / Rotation Vector → променя bearing (завой)
 *    - Accelerometer → променя speed (ускоряване/забавяне, проектирано по посоката)
 *    - Барометър → височина
 *
 * 3. Позиция = интеграл на (скорост × посока) — единична интеграция
 *    (не двойна като при acceleration→velocity→position)
 *
 * Предимства пред двойната интеграция:
 * - Началната скорост идва от GPS, не от интеграция на acceleration
 * - Accelerometer bias влияе само върху speed (m/s), не position (m²/s²)
 * - Gyro drift влияе върху bearing, но bearing грешката е линейна, не квадратична
 *
 * Координатна система: North, East (метри от стартовата GPS точка)
 */
class DeadReckoningCalculator {

    // ========================================================================
    // Trajectory state (основно състояние)
    // ========================================================================

    private var posNorth: Double = 0.0    // метри North от стартовата точка
    private var posEast: Double = 0.0     // метри East от стартовата точка
    private var speed: Double = 0.0       // текуща скорост (m/s)
    private var heading: Double = 0.0     // текуща посока (градуси, 0-360)

    // --- Начален velocity вектор (от GPS, не се нулира) ---
    private var initialSpeed: Double = 0.0
    private var initialHeading: Double = 0.0

    // --- Височина ---
    private var altitude: Double = 0.0
    private var hasBarometer: Boolean = false

    // ========================================================================
    // Rotation matrix (device → Earth frame)
    // ========================================================================

    private val rotMatrix3x3 = FloatArray(9)
    private val rotMatrix4x4 = FloatArray(16)
    private var rotMatrixReady: Boolean = false

    // ========================================================================
    // Heading източници
    // ========================================================================

    // --- Compass fallback ---
    private var compassHeading: Double = 0.0
    private var hasCompassData: Boolean = false
    private var firstCompassComputed: Boolean = false
    private val accelForCompass = FloatArray(3)
    private val magForCompass = FloatArray(3)
    private var hasAccelForCompass: Boolean = false
    private var hasMagForCompass: Boolean = false

    // ========================================================================
    // Timestamps
    // ========================================================================

    private var lastAccelTimestamp: Long = 0
    private var lastGyroTimestamp: Long = 0
    private var lastMagTimestamp: Long = 0
    private var lastBaroTimestamp: Long = 0
    private var lastRotVectorTimestamp: Long = 0
    private var lastUpdateTimestamp: Long = 0  // общ timestamp за position advance

    // ========================================================================
    // Филтрирани сензорни стойности
    // ========================================================================

    private var filteredAccelX: Double = 0.0
    private var filteredAccelY: Double = 0.0
    private var filteredAccelZ: Double = 0.0
    private var filteredGyroZ: Double = 0.0

    // ========================================================================
    // Raw сензорни стойности (за debug)
    // ========================================================================

    private var rawAccelX: Float = 0f
    private var rawAccelY: Float = 0f
    private var rawAccelZ: Float = 0f
    private var rawGyroX: Float = 0f
    private var rawGyroY: Float = 0f
    private var rawGyroZ: Float = 0f
    private var rawMagX: Float = 0f
    private var rawMagY: Float = 0f
    private var rawMagZ: Float = 0f
    private var rawRotX: Float = 0f
    private var rawRotY: Float = 0f
    private var rawRotZ: Float = 0f

    // ========================================================================
    // Gravity & bias
    // ========================================================================

    private var gravityX: Double = 0.0
    private var gravityY: Double = 0.0
    private var gravityZ: Double = 9.81
    private var sensorIsLinearAcceleration: Boolean = false

    // Along-track accelerometer bias (m/s²) — оценен при GPS-налични периоди
    private var accelBiasAlong: Double = 0.0
    private var biasLearnCount: Int = 0

    // ========================================================================
    // Earth-frame acceleration (за debug)
    // ========================================================================

    private var earthAccelN: Double = 0.0
    private var earthAccelE: Double = 0.0

    // ========================================================================
    // Стартова GPS точка (reference за lat/lon конверсия)
    // ========================================================================

    private var refLatitude: Double = 0.0
    private var refLongitude: Double = 0.0
    private var refAltitude: Double = 0.0

    // ========================================================================
    // Accuracy & tracking
    // ========================================================================

    private var initialAccuracy: Float = 0.0f
    private var estimatedAccuracy: Float = 0.0f
    private var startTime: Long = 0

    // Drift rate: градуси/сек gyro drift × скорост → позиционна грешка
    // Типичен MEMS gyro drift: ~0.05°/s → при 10 m/s → ~0.5 m/s позиционен drift
    private val GYRO_DRIFT_DEG_PER_S = 0.05   // °/s типичен gyro drift
    private val ACCEL_BIAS_DRIFT = 0.02       // m/s² типичен accelerometer bias

    // ========================================================================
    // GPS correction tracking
    // ========================================================================

    private var lastGpsCorrectionTime: Long = 0
    private var gpsCorrectionCount: Int = 0

    // ========================================================================
    // Състояние
    // ========================================================================

    private var initialized: Boolean = false

    // ========================================================================
    // Tunable параметри
    // ========================================================================

    // Accelerometer: колко силно влияе върху скоростта (0-1)
    // 1.0 = пълно доверие на accelerometer, 0.0 = игнорирай accelerometer
    private val ACCEL_GAIN = 0.3

    // Deadzone: под това ускорение се третира като шум (m/s²)
    private val ACCEL_DEADZONE = 0.1

    // Maximum speed change per second from accelerometer (m/s²) — safety clamp
    private val MAX_ACCEL = 5.0  // 0.5g ≈ нормално возило

    // Gyro: maximum turn rate (°/s) — safety clamp
    private val MAX_TURN_RATE = 90.0

    // GPS correction smoothing factor (0-1)
    // По-голямо = по-бързо връщане към GPS, по-малко = по-плавно
    private val GPS_CORRECTION_ALPHA_POS = 0.3
    private val GPS_CORRECTION_ALPHA_SPEED = 0.5
    private val GPS_CORRECTION_ALPHA_HEADING = 0.3

    // ========================================================================
    // Инициализация
    // ========================================================================

    /**
     * Инициализира калкулатора с GPS точки от ring buffer.
     *
     * От трите точки извлича:
     * - Текуща скорост и посока (speed, bearing) от най-новата точка
     * - Тренд на скоростта от предходните точки (използва се за начална оценка
     *   на ускорението, но основното коригиране идва от сензорите)
     *
     * @param snapshots последните 1-3 GPS точки (най-новата е първа — index 0)
     * @param accuracy GPS точност в метри
     */
    fun initialize(snapshots: List<GpsRingBuffer.GpsSnapshot>, accuracy: Float) {
        require(snapshots.isNotEmpty()) { "Нужна е поне 1 GPS точка за инициализация" }

        val latest = snapshots[0]

        refLatitude = latest.lat
        refLongitude = latest.lon
        refAltitude = latest.alt
        altitude = latest.alt

        // Начална позиция = 0 (относителна)
        posNorth = 0.0
        posEast = 0.0

        // Начална скорост и посока от GPS
        speed = latest.speed.toDouble()
        heading = latest.bearing.toDouble() % 360.0
        if (heading < 0) heading += 360.0

        initialSpeed = speed
        initialHeading = heading

        // Ако имаме 2+ точки, изчисляваме тренд на скоростта
        // за по-добра начална оценка на acceleration очакването
        if (snapshots.size >= 2) {
            val prev = snapshots[1]
            val dt = ((latest.timestamp - prev.timestamp) / 1000.0).coerceIn(0.5, 10.0)

            // Ако скоростта се променя значително, ползваме средна скорост
            // (по-стабилна от моментната)
            val speedDiff = abs(latest.speed - prev.speed)
            if (speedDiff > 2.0 && dt > 0) {
                // Значителна промяна — усредняваме с предходната точка
                speed = (latest.speed + prev.speed) / 2.0
            }

            // Ако посоката е стабилна (разлика < 10°), ползваме я директно
            val bearingDiff = angleDiff(latest.bearing.toDouble(), prev.bearing.toDouble())
            if (bearingDiff < 10.0 && latest.speed > 1.0) {
                // Посоката е стабилна — достоверна
            } else if (snapshots.size >= 3) {
                // 3 точки — изчисляваме посока от позиционния вектор
                // (по-стабилна от моментния GPS bearing при ниска скорост)
                val oldest = snapshots[2]
                val dN = latest.lat - oldest.lat
                val dE = latest.lon - oldest.lon
                val dist = sqrt(dN * dN + dE * dE)
                if (dist > 0.00001) { // ~1 метър
                    val trajectoryBearing = Math.toDegrees(atan2(dE, dN))
                    heading = (trajectoryBearing + 360.0) % 360.0
                }
            }
        }

        initialAccuracy = accuracy
        estimatedAccuracy = accuracy
        startTime = System.currentTimeMillis()

        // Нулиране на сензорно състояние
        lastAccelTimestamp = 0
        lastGyroTimestamp = 0
        lastMagTimestamp = 0
        lastBaroTimestamp = 0
        lastRotVectorTimestamp = 0
        lastUpdateTimestamp = 0
        filteredAccelX = 0.0
        filteredAccelY = 0.0
        filteredAccelZ = 0.0
        filteredGyroZ = 0.0
        gravityX = 0.0
        gravityY = 0.0
        gravityZ = 9.81
        sensorIsLinearAcceleration = false
        hasCompassData = false
        firstCompassComputed = false
        hasAccelForCompass = false
        hasMagForCompass = false
        hasBarometer = false
        rotMatrixReady = false
        accelBiasAlong = 0.0
        biasLearnCount = 0
        earthAccelN = 0.0
        earthAccelE = 0.0
        lastGpsCorrectionTime = 0
        gpsCorrectionCount = 0

        initialized = true
    }

    // ========================================================================
    // Общ метод за придвижване на позицията
    // Извиква се при всеки сензорен event
    // ========================================================================

    /**
     * Придвижва позицията напред с текущите speed и heading.
     *
     * Това е ядрото на trajectory-based подхода:
     * позиция += скорост × посока × dt
     * (единична интеграция, не двойна)
     *
     * @param dt времеви интервал в секунди
     */
    private fun advancePosition(dt: Double) {
        if (dt <= 0 || dt > 2.0) return  // safety clamp: макс 2 секунди

        val headingRad = Math.toRadians(heading)

        // Обновяване на velocity компонентите от speed и heading
        val velN = speed * cos(headingRad)
        val velE = speed * sin(headingRad)

        // Придвижване на позицията
        posNorth += velN * dt
        posEast += velE * dt

        // Обновяване на очакваната точност (расте с времето)
        val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
        val gyroDriftError = GYRO_DRIFT_DEG_PER_S * elapsed * speed * elapsed / 2.0  // m
        val accelBiasError = ACCEL_BIAS_DRIFT * elapsed * elapsed / 2.0               // m
        estimatedAccuracy = (initialAccuracy + gyroDriftError + accelBiasError).toFloat()
    }

    // ========================================================================
    // Акселерометър — коригира скоростта (speed)
    // ========================================================================

    /**
     * Обработка на акселерометър данни.
     *
     * Алгоритъм:
     * 1. Low-pass филтър на raw данни
     * 2. Премахване на gravity (ако не е LINEAR_ACCELERATION)
     * 3. Трансформация device→Earth frame чрез rotation matrix
     * 4. Проекция на ускорението по посоката на движение
     * 5. Корекция на скоростта: speed += projectedAccel * dt * GAIN
     * 6. Придвижване на позицията
     */
    fun processAccelerometer(values: FloatArray, timestamp: Long, isLinearAcceleration: Boolean) {
        if (!initialized) return

        // Запазване за compass heading
        sensorIsLinearAcceleration = isLinearAcceleration
        System.arraycopy(values, 0, accelForCompass, 0, 3)
        hasAccelForCompass = true
        if (hasMagForCompass) {
            computeCompassHeading()
        }

        rawAccelX = values[0]
        rawAccelY = values[1]
        rawAccelZ = values[2]

        // Time delta
        val dt = if (lastAccelTimestamp > 0) {
            (timestamp - lastAccelTimestamp) / 1_000_000_000.0
        } else {
            0.0
        }
        lastAccelTimestamp = timestamp
        if (dt <= 0 || dt > 1.0) {
            // Дори без валиден dt, придвижваме позицията ако имаме общ timestamp
            advanceWithCommonTimestamp(timestamp)
            return
        }

        // Low-pass филтър
        val alpha = 0.7
        filteredAccelX = alpha * filteredAccelX + (1 - alpha) * values[0]
        filteredAccelY = alpha * filteredAccelY + (1 - alpha) * values[1]
        filteredAccelZ = alpha * filteredAccelZ + (1 - alpha) * values[2]

        // Премахване на gravity
        val linearAccelX: Double
        val linearAccelY: Double
        val linearAccelZ: Double
        if (isLinearAcceleration) {
            linearAccelX = filteredAccelX
            linearAccelY = filteredAccelY
            linearAccelZ = filteredAccelZ
        } else {
            val beta = 0.9
            gravityX = beta * gravityX + (1 - beta) * values[0]
            gravityY = beta * gravityY + (1 - beta) * values[1]
            gravityZ = beta * gravityZ + (1 - beta) * values[2]
            linearAccelX = filteredAccelX - gravityX
            linearAccelY = filteredAccelY - gravityY
            linearAccelZ = filteredAccelZ - gravityZ
        }

        // Трансформация device→Earth frame
        val accelN: Double
        val accelE: Double
        if (rotMatrixReady) {
            val deviceAccel = floatArrayOf(linearAccelX.toFloat(), linearAccelY.toFloat(), linearAccelZ.toFloat(), 0f)
            val earthAccel = FloatArray(4)
            android.opengl.Matrix.multiplyMV(earthAccel, 0, rotMatrix4x4, 0, deviceAccel, 0)
            // Android: earthAccel[0]=East, earthAccel[1]=North
            accelE = earthAccel[0].toDouble()
            accelN = earthAccel[1].toDouble()
        } else {
            // Fallback: предполагаме хоризонтално устройство
            accelN = linearAccelX
            accelE = linearAccelY
        }

        earthAccelN = accelN
        earthAccelE = accelE

        // --- КЛЮЧОВА РАЗЛИКА от Kalman Filter: ---
        // Проектираме ускорението по посоката на движение
        // и коригираме САМО скоростта (не интегрираме позиция директно от acceleration)
        val headingRad = Math.toRadians(heading)
        val alongTrackAccel = accelN * cos(headingRad) + accelE * sin(headingRad)

        // Deadzone + bias корекция
        val effectiveAccel = if (abs(alongTrackAccel - accelBiasAlong) < ACCEL_DEADZONE) {
            0.0
        } else {
            (alongTrackAccel - accelBiasAlong).coerceIn(-MAX_ACCEL, MAX_ACCEL)
        }

        // Коригиране на скоростта с along-track ускорение
        // GAIN < 1.0 → не се доверяваме напълно на accelerometer-а
        speed += effectiveAccel * dt * ACCEL_GAIN
        speed = max(0.0, speed)  // скоростта не може да е отрицателна

        // Придвижване на позицията
        advancePosition(dt)
        lastUpdateTimestamp = timestamp

        // Височина
        if (!hasBarometer) {
            altitude = refAltitude
        }
    }

    // ========================================================================
    // Rotation Vector — абсолютен heading (приоритетен)
    // ========================================================================

    /**
     * Обработка на rotation vector данни.
     *
     * Rotation vector дава абсолютна ориентация на устройството.
     * Използваме го за:
     * 1. Обновяване на rotation matrix (за accelerometer трансформация)
     * 2. Абсолютен heading (азимут) — по-точен от gyro интеграция
     */
    fun processRotationVector(values: FloatArray, timestamp: Long) {
        if (!initialized) return

        rawRotX = if (values.size > 0) values[0] else 0f
        rawRotY = if (values.size > 1) values[1] else 0f
        rawRotZ = if (values.size > 2) values[2] else 0f

        try {
            SensorManager.getRotationMatrixFromVector(rotMatrix3x3, values)
            // Конвертиране 3×3 → 4×4
            rotMatrix4x4[0] = rotMatrix3x3[0]; rotMatrix4x4[1] = rotMatrix3x3[1]
            rotMatrix4x4[2] = rotMatrix3x3[2]; rotMatrix4x4[3] = 0f
            rotMatrix4x4[4] = rotMatrix3x3[3]; rotMatrix4x4[5] = rotMatrix3x3[4]
            rotMatrix4x4[6] = rotMatrix3x3[5]; rotMatrix4x4[7] = 0f
            rotMatrix4x4[8] = rotMatrix3x3[6]; rotMatrix4x4[9] = rotMatrix3x3[7]
            rotMatrix4x4[10] = rotMatrix3x3[8]; rotMatrix4x4[11] = 0f
            rotMatrix4x4[12] = 0f; rotMatrix4x4[13] = 0f
            rotMatrix4x4[14] = 0f; rotMatrix4x4[15] = 1f
            rotMatrixReady = true
            lastRotVectorTimestamp = timestamp

            // Абсолютен heading от rotation matrix
            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotMatrix3x3, orientation)
            val azimuthDeg = Math.toDegrees(orientation[0].toDouble())
            val newHeading = (azimuthDeg + 360.0) % 360.0

            // Плавно обновяване на heading-а (не скача рязко)
            // Това е важно: rotation vector може да има краткотрайни аномалии
            val diff = angleDiff(newHeading, heading)
            if (abs(diff) < 30.0) {
                // Малка разлика → плавна корекция
                heading = (heading + diff * 0.3 + 360.0) % 360.0
            } else {
                // Голяма разлика → вероятно реален завой, обнови директно
                heading = newHeading
            }

            // Придвижване на позицията с новия heading
            advanceWithCommonTimestamp(timestamp)
        } catch (_: Exception) {
            // getRotationMatrixFromVector може да хвърли exception
        }
    }

    // ========================================================================
    // Gyroscope — относителен heading (fallback)
    // ========================================================================

    /**
     * Обработка на жироскоп данни.
     *
     * Използва се САМО когато rotation vector НЕ е наличен или не е свеж.
     * Gyro Z → промяна на heading (Δheading = gyroZ * dt в градуси).
     */
    fun processGyroscope(values: FloatArray, timestamp: Long) {
        if (!initialized) return

        rawGyroX = values[0]
        rawGyroY = values[1]
        rawGyroZ = values[2]

        val dt = if (lastGyroTimestamp > 0) {
            (timestamp - lastGyroTimestamp) / 1_000_000_000.0
        } else {
            0.0
        }
        lastGyroTimestamp = timestamp
        if (dt <= 0 || dt > 1.0) {
            advanceWithCommonTimestamp(timestamp)
            return
        }

        // Low-pass филтър
        val alpha = 0.7
        filteredGyroZ = alpha * filteredGyroZ + (1 - alpha) * values[2]

        // Ако rotation vector е свеж (< 500ms), не ползваме gyro
        val rotVectorFresh = (lastRotVectorTimestamp > 0 &&
                (timestamp - lastRotVectorTimestamp) < 500_000_000L)
        if (rotVectorFresh) {
            advanceWithCommonTimestamp(timestamp)
            return
        }

        // Gyro интеграция: rad/s → °
        val gyroDeltaDeg = Math.toDegrees(filteredGyroZ * dt)
        val clampedDelta = gyroDeltaDeg.coerceIn(-MAX_TURN_RATE * dt, MAX_TURN_RATE * dt)

        heading += clampedDelta
        heading = (heading + 360.0) % 360.0

        // Complementary filter с compass (ако има)
        if (hasCompassData && speed < 5.0) {
            val compassDiff = angleDiff(compassHeading, heading)
            heading = (heading + compassDiff * 0.02 + 360.0) % 360.0
        }

        // Придвижване на позицията
        advancePosition(dt)
        lastUpdateTimestamp = timestamp
    }

    // ========================================================================
    // Magnetometer — compass heading (fallback за gyro)
    // ========================================================================

    fun processMagnetometer(values: FloatArray, timestamp: Long) {
        if (!initialized) return

        rawMagX = values[0]
        rawMagY = values[1]
        rawMagZ = values[2]

        lastMagTimestamp = timestamp
        System.arraycopy(values, 0, magForCompass, 0, 3)
        hasMagForCompass = true

        if (hasAccelForCompass && hasMagForCompass) {
            computeCompassHeading()
        }
    }

    private fun computeCompassHeading() {
        val magX = magForCompass[0].toDouble()
        val magY = magForCompass[1].toDouble()
        val magZ = magForCompass[2].toDouble()

        val ax: Double
        val ay: Double
        val az: Double
        if (sensorIsLinearAcceleration) {
            ax = accelForCompass[0].toDouble() + gravityX
            ay = accelForCompass[1].toDouble() + gravityY
            az = accelForCompass[2].toDouble() + gravityZ
        } else {
            ax = accelForCompass[0].toDouble()
            ay = accelForCompass[1].toDouble()
            az = accelForCompass[2].toDouble()
        }

        val accelMag = sqrt(ax * ax + ay * ay + az * az)
        if (accelMag < 0.1) return

        val pitch = kotlin.math.asin(-ax / accelMag)
        val roll = atan2(ay, az)

        val cosPitch = cos(pitch)
        val sinPitch = sin(pitch)
        val cosRoll = cos(roll)
        val sinRoll = sin(roll)

        val magNorth = magX * cosPitch + magY * sinRoll * sinPitch + magZ * cosRoll * sinPitch
        val magEast = -magY * cosRoll + magZ * sinRoll

        var newCompass = atan2(magEast, magNorth)
        newCompass = Math.toDegrees(newCompass)
        newCompass = (newCompass + 360.0) % 360.0

        if (!firstCompassComputed) {
            compassHeading = newCompass
            firstCompassComputed = true
        } else {
            val diff = angleDiff(newCompass, compassHeading)
            if (abs(diff) > 45.0) return
            compassHeading = (compassHeading + diff * 0.05 + 360.0) % 360.0
        }
        hasCompassData = true
    }

    // ========================================================================
    // Барометър
    // ========================================================================

    fun processBarometer(pressure: Float, timestamp: Long) {
        if (!initialized) return
        lastBaroTimestamp = timestamp
        hasBarometer = true
        val P0 = 1013.25
        altitude = 44330.0 * (1.0 - Math.pow(pressure.toDouble() / P0, 1.0 / 5.255))
    }

    // ========================================================================
    // GPS correction — експоненциално изглаждане (вместо Kalman)
    // ========================================================================

    /**
     * Коригира trajectory-то с GPS измерване.
     *
     * Използва експоненциално изглаждане (не Kalman filter):
     * - Позиция: pos = α*gps + (1-α)*pos
     * - Скорост: speed = α*gps_speed + (1-α)*speed
     * - Посока: heading = α*gps_bearing + (1-α)*heading
     *
     * Това е по-просто и по-стабилно от Kalman filter за този use case.
     * При добра GPS точност → бърза корекция (голямо α).
     * При лоша GPS точност → бавна корекция (малко α).
     */
    fun correctWithGPS(
        gpsPosN: Double, gpsPosE: Double,
        gpsVelN: Double, gpsVelE: Double,
        gpsAccuracy: Float
    ) {
        if (!initialized) return

        // Адаптивен alpha: колкото по-точен GPS, толкова по-силна корекция
        val accFactor = (gpsAccuracy / 20.0).coerceIn(0.2, 1.5)

        // Корекция на позиция
        posNorth += (gpsPosN - posNorth) * GPS_CORRECTION_ALPHA_POS * accFactor
        posEast += (gpsPosE - posEast) * GPS_CORRECTION_ALPHA_POS * accFactor

        // Корекция на скорост
        val gpsSpeed = sqrt(gpsVelN * gpsVelN + gpsVelE * gpsVelE)
        if (gpsSpeed > 0.5) {
            speed += (gpsSpeed - speed) * GPS_CORRECTION_ALPHA_SPEED * accFactor

            // Корекция на heading
            val gpsBearing = Math.toDegrees(atan2(gpsVelE, gpsVelN))
            val gpsHeading = (gpsBearing + 360.0) % 360.0
            val hDiff = angleDiff(gpsHeading, heading)
            heading = (heading + hDiff * GPS_CORRECTION_ALPHA_HEADING * accFactor + 360.0) % 360.0
        }

        // Оценка на accelerometer bias от разликата в скоростите
        // Ако GPS показва различна скорост от нашата, акумулираме bias
        if (gpsSpeed > 1.0 && speed > 1.0 && abs(gpsSpeed - speed) < 5.0) {
            // Разлика в скоростта → вероятен accelerometer bias
            // Бавно учене (голяма инерция)
            val biasDelta = (gpsSpeed - speed) * 0.001  // много бавно
            accelBiasAlong += biasDelta
            accelBiasAlong = accelBiasAlong.coerceIn(-0.5, 0.5)
            biasLearnCount++
        }

        estimatedAccuracy = gpsAccuracy  // GPS дава реална точност
        lastGpsCorrectionTime = System.currentTimeMillis()
        gpsCorrectionCount++
    }

    // ========================================================================
    // GPS helper
    // ========================================================================

    fun gpsToRelative(lat: Double, lon: Double): DoubleArray {
        val latRad = Math.toRadians(refLatitude)
        val deltaLat = (lat - refLatitude) * 111320.0
        val deltaLon = (lon - refLongitude) * 111320.0 * cos(latRad)
        return doubleArrayOf(deltaLat, deltaLon)
    }

    // ========================================================================
    // Резултати
    // ========================================================================

    fun getCurrentLocation(): DRLocation {
        if (!initialized) {
            throw IllegalStateException("Калкулаторът не е инициализиран")
        }

        val latRad = Math.toRadians(refLatitude)
        val deltaLat = posNorth / 111320.0
        val deltaLon = posEast / (111320.0 * cos(latRad))

        return DRLocation(
            latitude = refLatitude + deltaLat,
            longitude = refLongitude + deltaLon,
            altitude = altitude,
            bearing = heading.toFloat(),
            speed = speed.toFloat(),
            accuracy = estimatedAccuracy,
            timestamp = System.currentTimeMillis()
        )
    }

    fun getDebugState(): String {
        val gpsAge = if (lastGpsCorrectionTime > 0)
            (System.currentTimeMillis() - lastGpsCorrectionTime) / 1000 else -1
        return String.format(
            java.util.Locale.US,
            "DR_DEBUG: heading=%.2f, compassHeading=%.2f, speed=%.2f, " +
            "posN=%.2f, posE=%.2f, earthAccelN=%.4f, earthAccelE=%.4f, " +
            "alongBias=%.4f(#%d), rotMat=$rotMatrixReady, " +
            "gpsAge=${gpsAge}s, gpsCorr=#$gpsCorrectionCount, altitude=%.1f, " +
            "accel=[%.3f,%.3f,%.3f], gyro=[%.4f,%.4f,%.4f], mag=[%.1f,%.1f,%.1f], " +
            "rot=[%.4f,%.4f,%.4f]",
            heading, compassHeading, speed,
            posNorth, posEast, earthAccelN, earthAccelE,
            accelBiasAlong, biasLearnCount,
            altitude,
            rawAccelX, rawAccelY, rawAccelZ,
            rawGyroX, rawGyroY, rawGyroZ,
            rawMagX, rawMagY, rawMagZ,
            rawRotX, rawRotY, rawRotZ
        )
    }

    fun getElapsedSeconds(): Long {
        return if (initialized) (System.currentTimeMillis() - startTime) / 1000 else 0
    }

    fun getEstimatedAccuracy(): Float = estimatedAccuracy

    fun isActive(): Boolean = initialized

    fun reset() {
        posNorth = 0.0; posEast = 0.0
        speed = 0.0; heading = 0.0
        altitude = 0.0
        lastAccelTimestamp = 0; lastGyroTimestamp = 0
        lastMagTimestamp = 0; lastBaroTimestamp = 0
        lastRotVectorTimestamp = 0; lastUpdateTimestamp = 0
        filteredAccelX = 0.0; filteredAccelY = 0.0; filteredAccelZ = 0.0
        filteredGyroZ = 0.0
        rawAccelX = 0f; rawAccelY = 0f; rawAccelZ = 0f
        rawGyroX = 0f; rawGyroY = 0f; rawGyroZ = 0f
        rawMagX = 0f; rawMagY = 0f; rawMagZ = 0f
        rawRotX = 0f; rawRotY = 0f; rawRotZ = 0f
        gravityX = 0.0; gravityY = 0.0; gravityZ = 9.81
        initialized = false
        hasBarometer = false; hasCompassData = false
        firstCompassComputed = false
        hasAccelForCompass = false; hasMagForCompass = false
        sensorIsLinearAcceleration = false
        rotMatrixReady = false
        accelBiasAlong = 0.0; biasLearnCount = 0
        lastGpsCorrectionTime = 0; gpsCorrectionCount = 0
        earthAccelN = 0.0; earthAccelE = 0.0
        estimatedAccuracy = initialAccuracy
    }

    // ========================================================================
    // Utility
    // ========================================================================

    /**
     * Изчислява разликата между два ъгъла в градуси (най-късият път).
     * @return разлика в [-180, 180]
     */
    private fun angleDiff(a: Double, b: Double): Double {
        var diff = (a - b + 540.0) % 360.0 - 180.0
        // Нормализиране (handle floating point)
        if (diff > 180.0) diff -= 360.0
        if (diff < -180.0) diff += 360.0
        return diff
    }

    /**
     * Придвижва позицията напред използвайки общ timestamp.
     * Извиква се от сензорни handler-и които нямат собствен dt
     * (напр. rotation vector, magnetometer).
     */
    private fun advanceWithCommonTimestamp(timestamp: Long) {
        if (lastUpdateTimestamp > 0 && speed > 0.01) {
            val dt = (timestamp - lastUpdateTimestamp) / 1_000_000_000.0
            if (dt > 0 && dt < 2.0) {
                advancePosition(dt)
            }
        }
        lastUpdateTimestamp = timestamp
    }
}

/**
 * Data class представляваща изчислена позиция от dead reckoning.
 */
data class DRLocation(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val bearing: Float,
    val speed: Float,
    val accuracy: Float,
    val timestamp: Long
)
