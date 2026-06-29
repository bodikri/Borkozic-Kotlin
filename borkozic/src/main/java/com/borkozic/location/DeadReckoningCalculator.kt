package com.borkozic.location

import android.hardware.Sensor
import android.hardware.SensorManager
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Dead Reckoning Calculator — Kalman Filter версия.
 *
 * Изчислява позиция от IMU сензори (accelerometer, rotation vector, barometer)
 * когато GPS не е наличен. Използва 4-state Kalman filter за fusion на IMU и GPS.
 *
 * Координатна система: North, East, Up (ENU — Earth frame)
 *
 * State vector (4D):
 *   x = [ posN, posE, velN, velE ]
 *
 * Control input (2D):
 *   u = [ accelN, accelE ]  (линейно ускорение в Earth frame)
 *
 * Prediction (IMU, 20Hz):
 *   x = F*x + B*u
 *   P = F*P*F^T + Q
 *
 * Correction (GPS, ~1Hz, когато е наличен):
 *   K = P*H^T * (H*P*H^T + R)^{-1}
 *   x = x + K*(z - H*x)
 *   P = (I - K*H)*P
 *
 * Алгоритъм:
 * 1. Rotation vector → rotation matrix → трансформация device→Earth frame
 * 2. Accelerometer (linear) в Earth frame → Kalman prediction
 * 3. GPS (когато е наличен) → Kalman correction
 * 4. Heading от rotation matrix (азимут)
 * 5. Altitude от барометър (точно) или стартова GPS височина
 *
 * Предимства спрямо старата имплементация:
 * - Няма нефизичен drag decay — скоростта се поддържа от инерция + GPS корекция
 * - Правилна координатна трансформация чрез rotation matrix (не се предполага хоризонтално устройство)
 * - Kalman filter оптимално комбинира IMU и GPS с noise модели
 * - Accelerometer bias се компенсира индиректно чрез GPS innovation
 */
class DeadReckoningCalculator {

    // ========================================================================
    // State vector и covariance
    // ========================================================================

    // x = [posN, posE, velN, velE] — 4-state
    private val x = DoubleArray(4)

    // P = 4×4 covariance matrix (row-major: P[i*4+j])
    private val P = DoubleArray(16)

    // ========================================================================
    // Kalman filter матрици (пребuild-ват се при всеки step)
    // ========================================================================

    // F = state transition (4×4)
    private val F = DoubleArray(16)
    // B = control matrix (4×2)
    private val B = DoubleArray(8)
    // Q = process noise (4×4)
    private val Q = DoubleArray(16)
    // H = measurement matrix (4×4 identity)
    private val H = doubleArrayOf(
        1.0, 0.0, 0.0, 0.0,
        0.0, 1.0, 0.0, 0.0,
        0.0, 0.0, 1.0, 0.0,
        0.0, 0.0, 0.0, 1.0
    )
    // R = measurement noise (4×4) — rebuild при всеки GPS correction
    private val R = DoubleArray(16)
    // K = Kalman gain (4×4)
    private val K = DoubleArray(16)

    // Temporary matrices за изчисления
    private val tmp4 = DoubleArray(4)
    private val tmp44a = DoubleArray(16)
    private val tmp44b = DoubleArray(16)
    private val tmp44c = DoubleArray(16)
    private val tmpMat = DoubleArray(16)
    private val tmpVec = DoubleArray(4)
    private val innov = DoubleArray(4) // innovation = z - H*x

    // ========================================================================
    // Процес noise параметри (tunable)
    // ========================================================================

    // Process noise spectral density за ускорение (m/s²/√s)
    // MEMS accelerometer на телефон: ~0.1-0.3 m/s²/√s
    private var sigmaAccel = 0.15 // m/s²/√s

    // Measurement noise за GPS скорост (m/s) — GPS speed е сравнително точен
    private var sigmaGpsVel = 0.5 // m/s

    // ========================================================================
    // Позиция в Earth frame (относително стартовата точка)
    // ========================================================================

    private var posNorth: Double = 0.0
    private var posEast: Double = 0.0
    private var velNorth: Double = 0.0
    private var velEast: Double = 0.0

    // --- Височина ---
    private var altitude: Double = 0.0
    private var hasBarometer: Boolean = false

    // --- Heading (градуси, 0-360) ---
    private var heading: Double = 0.0

    // --- Rotation matrix (device → Earth) ---
    // Обновява се при всеки rotation vector sensor event
    // rotMatrix3x3: от SensorManager.getRotationMatrixFromVector (3×3, 9 елемента)
    // rotMatrix4x4: конвертирана 4×4 за Matrix.multiplyMV (16 елемента)
    private val rotMatrix3x3 = FloatArray(9)
    private val rotMatrix4x4 = FloatArray(16)
    private val rotMatrixInitialized = BooleanArray(1)

    // --- Compass heading (fallback, от magnetometer + accel) ---
    private var compassHeading: Double = 0.0
    private var hasCompassData: Boolean = false
    private var firstCompassComputed: Boolean = false
    private val accelForCompass = FloatArray(3)
    private val magForCompass = FloatArray(3)
    private var hasAccelForCompass: Boolean = false
    private var hasMagForCompass: Boolean = false

    // --- Timestamps (наносекунди от SensorEvent) ---
    private var lastAccelTimestamp: Long = 0
    private var lastGyroTimestamp: Long = 0
    private var lastMagTimestamp: Long = 0
    private var lastBaroTimestamp: Long = 0
    private var lastRotVectorTimestamp: Long = 0

    // --- Филтрирани сензорни стойности ---
    private var filteredAccelX: Double = 0.0
    private var filteredAccelY: Double = 0.0
    private var filteredAccelZ: Double = 0.0
    private var filteredGyroZ: Double = 0.0

    // --- Последни raw сензорни стойности за debug ---
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

    // --- Gravity оценка (за compass heading при LINEAR_ACCELERATION сензор) ---
    private var gravityX: Double = 0.0
    private var gravityY: Double = 0.0
    private var gravityZ: Double = 9.81
    private var sensorIsLinearAcceleration: Boolean = false

    // --- Accelerometer bias estimation (running average при нулева скорост) ---
    private var accelBiasN: Double = 0.0
    private var accelBiasE: Double = 0.0
    private var biasLearnRate: Double = 0.005 // бавно учене

    // --- GPS correction tracking ---
    private var lastGpsCorrectionTime: Long = 0  // System.currentTimeMillis()
    private var gpsCorrectionCount: Int = 0

    // --- Последни Earth-frame ускорения (след трансформация + bias) за debug ---
    private var earthAccelN: Double = 0.0
    private var earthAccelE: Double = 0.0

    // --- Стартова GPS точка ---
    private var refLatitude: Double = 0.0
    private var refLongitude: Double = 0.0
    private var refAltitude: Double = 0.0

    // --- Accuracy tracking ---
    private var initialAccuracy: Float = 0.0f
    private var estimatedAccuracy: Float = 0.0f
    private var startTime: Long = 0
    private val DRIFT_RATE = 2.0f // m/s drift rate за авиация

    // --- Състояние ---
    private var initialized: Boolean = false

    // Complementary filter коефициент за heading (compass корекция на gyro)
    private val COMPASS_WEIGHT = 0.02

    // ========================================================================
    // Инициализация
    // ========================================================================

    /**
     * Инициализира калкулатора с последните GPS данни.
     * Това е стартовата точка за dead reckoning.
     *
     * @param lat Latitude в градуси
     * @param lon Longitude в градуси
     * @param alt Височина в метри
     * @param speed Скорост в m/s
     * @param bearing Heading в градуси (истински heading след fixDeclination)
     * @param accuracy GPS точност в метри
     */
    fun initialize(lat: Double, lon: Double, alt: Double, speed: Float, bearing: Float, accuracy: Float) {
        refLatitude = lat
        refLongitude = lon
        refAltitude = alt
        altitude = alt

        // State vector: позиция = 0 (относителна), скорост = GPS скорост в N/E
        posNorth = 0.0
        posEast = 0.0
        val br = Math.toRadians(bearing.toDouble())
        velNorth = speed * cos(br).toDouble()
        velEast = speed * sin(br).toDouble()

        x[0] = posNorth
        x[1] = posEast
        x[2] = velNorth
        x[3] = velEast

        // Covariance: голяма несигурност в позицията, малка в скоростта
        // P = diag(accuracy², accuracy², 1.0, 1.0)
        fillIdentity(P, 4)
        P[0] = accuracy.toDouble() * accuracy.toDouble() // P[0,0]
        P[5] = accuracy.toDouble() * accuracy.toDouble() // P[1,1]
        P[10] = 1.0 // P[2,2] — скорост несигурност 1 m/s
        P[15] = 1.0 // P[3,3]

        heading = bearing.toDouble()

        initialAccuracy = accuracy
        estimatedAccuracy = accuracy
        startTime = System.currentTimeMillis()

        // Нулиране на timestamps и филтри
        lastAccelTimestamp = 0
        lastGyroTimestamp = 0
        lastMagTimestamp = 0
        lastBaroTimestamp = 0
        lastRotVectorTimestamp = 0
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
        rotMatrixInitialized[0] = false
        accelBiasN = 0.0
        accelBiasE = 0.0

        initialized = true
    }

    // ========================================================================
    // Prediction step (IMU)
    // ========================================================================

    /**
     * Обработка на акселерометър данни — Kalman prediction step.
     *
     * Стъпки:
     * 1. Low-pass филтър на raw данни
     * 2. Трансформация device→Earth frame чрез rotation matrix (ако е наличен)
     *    или fallback към директно mapping (ако устройството е хоризонтално)
     * 3. Bias корекция
     * 4. Kalman prediction: x = F*x + B*u, P = F*P*F^T + Q
     *
     * @param values [x, y, z] ускорение в m/s² (device frame)
     * @param timestamp Timestamp в наносекунди (от SensorEvent.timestamp)
     * @param isLinearAcceleration true ако сензорът е TYPE_LINEAR_ACCELERATION (gravity премахнато)
     */
    fun processAccelerometer(values: FloatArray, timestamp: Long, isLinearAcceleration: Boolean) {
        if (!initialized) return

        // Запазване за compass heading (споделя се с magnetometer)
        sensorIsLinearAcceleration = isLinearAcceleration
        System.arraycopy(values, 0, accelForCompass, 0, 3)
        hasAccelForCompass = true
        if (hasMagForCompass) {
            computeCompassHeading()
        }

        // Запазване на raw данни за debug
        rawAccelX = values[0]
        rawAccelY = values[1]
        rawAccelZ = values[2]

        // Изчисляване на time delta
        val dt = if (lastAccelTimestamp > 0) {
            (timestamp - lastAccelTimestamp) / 1_000_000_000.0
        } else {
            0.0
        }
        lastAccelTimestamp = timestamp
        if (dt <= 0 || dt > 1.0) return // игнорирай невалидни dt (>1s = пропуск на сензор)

        // Low-pass филтър на raw акселерометър (alpha=0.7 — лек филтър)
        val alpha = 0.7
        filteredAccelX = alpha * filteredAccelX + (1 - alpha) * values[0]
        filteredAccelY = alpha * filteredAccelY + (1 - alpha) * values[1]
        filteredAccelZ = alpha * filteredAccelZ + (1 - alpha) * values[2]

        // Премахване на gravity само ако не е LINEAR_ACCELERATION
        val linearAccelX: Double
        val linearAccelY: Double
        val linearAccelZ: Double
        if (isLinearAcceleration) {
            linearAccelX = filteredAccelX
            linearAccelY = filteredAccelY
            linearAccelZ = filteredAccelZ
        } else {
            // Обновяване на gravity оценката чрез low-pass
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
        if (rotMatrixInitialized[0]) {
            // Използваме rotation matrix за точна трансформация
            // Android: R трансформира device→world, world = [East, North, Up]
            // earthAccel = R * deviceAccel
            // Matrix.multiplyMV работи с 4D хомогенни координати (4x4 mat × 4-vec = 4-vec)
            val deviceAccel = floatArrayOf(linearAccelX.toFloat(), linearAccelY.toFloat(), linearAccelZ.toFloat(), 0f)
            val earthAccel = FloatArray(4)
            android.opengl.Matrix.multiplyMV(earthAccel, 0, rotMatrix4x4, 0, deviceAccel, 0)

            // Android rotation matrix: earthAccel[0]=East, earthAccel[1]=North, earthAccel[2]=Up
            accelE = earthAccel[0].toDouble()
            accelN = earthAccel[1].toDouble()
            // earthAccel[2] (Up) не се използва за 2D позиция — височината е от барометър
        } else {
            // Fallback: предполагаме хоризонтално устройство (Phone X→North, Y→East)
            // Това е същото като стария алгоритъм — работи ако устройството е идеално хоризонтално
            accelN = linearAccelX
            accelE = linearAccelY
        }

        // Bias корекция
        val correctedAccelN = accelN - accelBiasN
        val correctedAccelE = accelE - accelBiasE

        // Deadzone: много малки ускорения са noise (под 0.05 m/s²)
        // По-малък от стария 0.3 — Kalman filter обработва noise по-добре
        val ACCEL_DEADZONE = 0.05
        val effectiveAccelN = if (kotlin.math.abs(correctedAccelN) < ACCEL_DEADZONE) 0.0 else correctedAccelN
        val effectiveAccelE = if (kotlin.math.abs(correctedAccelE) < ACCEL_DEADZONE) 0.0 else correctedAccelE

        // Запазване за debug лог
        earthAccelN = effectiveAccelN
        earthAccelE = effectiveAccelE

        // Kalman prediction step
        predict(effectiveAccelN, effectiveAccelE, dt)

        // Обновяване на локалните state променливи от x[]
        posNorth = x[0]
        posEast = x[1]
        velNorth = x[2]
        velEast = x[3]

        // Височина: само от барометър. Без барометър — стартова GPS височина.
        if (!hasBarometer) {
            altitude = refAltitude
        }
    }

    /**
     * Kalman prediction step: x = F*x + B*u, P = F*P*F^T + Q
     *
     * @param accelN Ускорение North (m/s²), вече в Earth frame + bias коригирано
     * @param accelE Ускорение East (m/s²), вече в Earth frame + bias коригирано
     * @param dt Времеви интервал в секунди
     */
    private fun predict(accelN: Double, accelE: Double, dt: Double) {
        // Build F (state transition matrix)
        // F = [ 1  0  dt  0  ]
        //     [ 0  1  0   dt ]
        //     [ 0  0  1   0  ]
        //     [ 0  0  0   1  ]
        fillIdentity(F, 4)
        F[2] = dt   // F[0,2] = dt
        F[7] = dt   // F[1,3] = dt

        // Build B (control matrix)
        // B = [ 0.5*dt²   0      ]
        //     [ 0         0.5*dt²]
        //     [ dt        0      ]
        //     [ 0         dt     ]
        B[0] = 0.5 * dt * dt  // B[0,0]
        B[1] = 0.0            // B[0,1]
        B[2] = 0.0            // B[1,0]
        B[3] = 0.5 * dt * dt  // B[1,1]
        B[4] = dt             // B[2,0]
        B[5] = 0.0            // B[2,1]
        B[6] = 0.0            // B[3,0]
        B[7] = dt             // B[3,1]

        // Build Q (process noise)
        // Q = sigma² * [ dt⁴/4  0      dt³/2  0     ]
        //              [ 0      dt⁴/4  0      dt³/2 ]
        //              [ dt³/2  0      dt²    0     ]
        //              [ 0      dt³/2  0      dt²   ]
        val s2 = sigmaAccel * sigmaAccel
        val dt2 = dt * dt
        val dt3 = dt2 * dt
        val dt4 = dt3 * dt
        fillZero(Q, 4)
        Q[0] = s2 * dt4 / 4.0  // Q[0,0]
        Q[5] = s2 * dt4 / 4.0  // Q[1,1]
        Q[10] = s2 * dt2       // Q[2,2]
        Q[15] = s2 * dt2       // Q[3,3]
        Q[2] = s2 * dt3 / 2.0  // Q[0,2]
        Q[7] = s2 * dt3 / 2.0  // Q[1,3]
        Q[8] = s2 * dt3 / 2.0  // Q[2,0]
        Q[13] = s2 * dt3 / 2.0 // Q[3,1]

        // x = F*x + B*u
        // tmp4 = F*x
        matVecMul(F, x, tmp4, 4, 4)
        // tmp4 += B*u (u = [accelN, accelE])
        tmp4[0] += B[0] * accelN + B[1] * accelE
        tmp4[1] += B[2] * accelN + B[3] * accelE
        tmp4[2] += B[4] * accelN + B[5] * accelE
        tmp4[3] += B[6] * accelN + B[7] * accelE
        // x = tmp4
        System.arraycopy(tmp4, 0, x, 0, 4)

        // P = F*P*F^T + Q
        // tmp44a = F*P
        matMatMul(F, P, tmp44a, 4, 4, 4)
        // tmp44b = F^T (transpose of F)
        transpose(F, tmp44b, 4)
        // tmp44c = tmp44a * tmp44b = F*P*F^T
        matMatMul(tmp44a, tmp44b, tmp44c, 4, 4, 4)
        // P = tmp44c + Q
        for (i in 0 until 16) {
            P[i] = tmp44c[i] + Q[i]
        }
    }

    // ========================================================================
    // Correction step (GPS)
    // ========================================================================

    /**
     * GPS correction step — обновява state и covariance с GPS измерване.
     *
     * Извиква се от DeadReckoningService когато GPS fix е наличен
     * (в manual mode или когато GPS се възстанови).
     *
     * Kalman update:
     *   K = P*H^T * (H*P*H^T + R)^{-1}
     *   x = x + K*(z - H*x)
     *   P = (I - K*H)*P
     *
     * @param gpsPosN GPS позиция North (метри от стартовата точка)
     * @param gpsPosE GPS позиция East (метри от стартовата точка)
     * @param gpsVelN GPS скорост North (m/s)
     * @param gpsVelE GPS скорост East (m/s)
     * @param gpsAccuracy GPS позиционна точност (метри)
     */
    fun correctWithGPS(
        gpsPosN: Double, gpsPosE: Double,
        gpsVelN: Double, gpsVelE: Double,
        gpsAccuracy: Float
    ) {
        if (!initialized) return

        // Measurement vector z = [gpsPosN, gpsPosE, gpsVelN, gpsVelE]
        val z = doubleArrayOf(gpsPosN, gpsPosE, gpsVelN, gpsVelE)

        // R = measurement noise covariance
        // R = diag(gpsAcc², gpsAcc², sigmaGpsVel², sigmaGpsVel²)
        fillZero(R, 4)
        val acc2 = gpsAccuracy.toDouble() * gpsAccuracy.toDouble()
        val vel2 = sigmaGpsVel * sigmaGpsVel
        R[0] = acc2
        R[5] = acc2
        R[10] = vel2
        R[15] = vel2

        // Innovation: innov = z - H*x = z - x (тъй като H = I)
        innov[0] = z[0] - x[0]
        innov[1] = z[1] - x[1]
        innov[2] = z[2] - x[2]
        innov[3] = z[3] - x[3]

        // S = H*P*H^T + R = P + R (тъй като H = I)
        // tmp44a = S = P + R
        for (i in 0 until 16) {
            tmp44a[i] = P[i] + R[i]
        }

        // S^{-1} — 4×4 matrix inversion
        // tmp44b = S^{-1}
        if (!invert4x4(tmp44a, tmp44b)) {
            // Сингулярна матрица — прескачаме корекцията
            return
        }

        // K = P * H^T * S^{-1} = P * S^{-1} (тъй като H = I)
        matMatMul(P, tmp44b, K, 4, 4, 4)

        // x = x + K*innov
        for (i in 0 until 4) {
            var sum = 0.0
            for (j in 0 until 4) {
                sum += K[i * 4 + j] * innov[j]
            }
            x[i] += sum
        }

        // P = (I - K*H)*P = (I - K)*P (тъй като H = I)
        // tmp44a = I - K
        fillIdentity(tmp44a, 4)
        for (i in 0 until 16) {
            tmp44a[i] -= K[i]
        }
        // tmp44b = (I - K)*P
        matMatMul(tmp44a, P, tmp44b, 4, 4, 4)
        // P = tmp44b
        System.arraycopy(tmp44b, 0, P, 0, 16)

        // Обновяване на локалните state променливи
        posNorth = x[0]
        posEast = x[1]
        velNorth = x[2]
        velEast = x[3]

        // Accelerometer bias estimation: когато GPS коригира скоростта,
        // разликата (innovation в velocity) индиректно показва accelerometer bias
        // Бавно учене: bias += learnRate * velocity_innovation
        if (kotlin.math.abs(innov[2]) < 5.0 && kotlin.math.abs(innov[3]) < 5.0) {
            accelBiasN += biasLearnRate * innov[2]
            accelBiasE += biasLearnRate * innov[3]
        }

        // Обновяване на heading от GPS bearing (ако има скорост)
        val speed = sqrt(velNorth * velNorth + velEast * velEast)
        if (speed > 0.5) {
            val gpsBearing = Math.toDegrees(atan2(velEast, velNorth))
            heading = (gpsBearing + 360.0) % 360.0
        }

        // Track GPS correction stats
        lastGpsCorrectionTime = System.currentTimeMillis()
        gpsCorrectionCount++
    }

    // ========================================================================
    // Rotation Vector обработка
    // ========================================================================

    /**
     * Обработка на rotation vector данни — обновява rotation matrix.
     *
     * TYPE_GAME_ROTATION_VECTOR (без magnetometer) или TYPE_ROTATION_VECTOR.
     *
     * Rotation vector е quaternion [x, y, z, w] (или 5-елементен с heading accuracy).
     * SensorManager.getRotationMatrixFromVector го конвертира в 3×3 rotation matrix.
     *
     * Rotation matrix R трансформира device frame → world frame:
     *   world_vector = R * device_vector
     *
     * Android world frame: [East, North, Up] (X=East, Y=North, Z=Up)
     *
     * @param values [x, y, z, w] (или 5-елементен) quaternion от rotation vector сензор
     * @param timestamp Timestamp в наносекунди
     */
    fun processRotationVector(values: FloatArray, timestamp: Long) {
        if (!initialized) return

        // Запазване на raw данни за debug
        rawRotX = if (values.size > 0) values[0] else 0f
        rawRotY = if (values.size > 1) values[1] else 0f
        rawRotZ = if (values.size > 2) values[2] else 0f

        // Изчисляване на rotation matrix от rotation vector
        try {
            SensorManager.getRotationMatrixFromVector(rotMatrix3x3, values)
            // Конвертиране 3×3 → 4×4 хомогенна матрица за Matrix.multiplyMV
            rotMatrix4x4[0] = rotMatrix3x3[0]; rotMatrix4x4[1] = rotMatrix3x3[1]; rotMatrix4x4[2] = rotMatrix3x3[2]; rotMatrix4x4[3] = 0f
            rotMatrix4x4[4] = rotMatrix3x3[3]; rotMatrix4x4[5] = rotMatrix3x3[4]; rotMatrix4x4[6] = rotMatrix3x3[5]; rotMatrix4x4[7] = 0f
            rotMatrix4x4[8] = rotMatrix3x3[6]; rotMatrix4x4[9] = rotMatrix3x3[7]; rotMatrix4x4[10] = rotMatrix3x3[8]; rotMatrix4x4[11] = 0f
            rotMatrix4x4[12] = 0f; rotMatrix4x4[13] = 0f; rotMatrix4x4[14] = 0f; rotMatrix4x4[15] = 1f
            rotMatrixInitialized[0] = true
            lastRotVectorTimestamp = timestamp

            // Heading от rotation matrix (азимут)
            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotMatrix3x3, orientation)
            // orientation[0] = azimuth в радиани [-π, π]
            val azimuthDeg = Math.toDegrees(orientation[0].toDouble())
            // Нормализиране към [0, 360)
            val normalizedAzimuth = (azimuthDeg + 360.0) % 360.0

            // Heading от rotation matrix е по-точен от gyro интеграция
            // Използваме го директно (компас/gyro е fallback)
            heading = normalizedAzimuth
        } catch (e: Exception) {
            // getRotationMatrixFromVector може да хвърли exception при невалидни стойности
            // Игнорираме и продължаваме с предишния heading
        }
    }

    // ========================================================================
    // Gyroscope обработка (fallback за heading, когато няма rotation vector)
    // ========================================================================

    /**
     * Обработка на жироскоп данни — complementary filter за heading.
     *
     * Използва се само когато rotation vector сензор НЕ е наличен.
     * Ако rotation vector-ът работи, heading се обновява от него (по-точен).
     *
     * heading = gyro интеграция + compass корекция (complementary filter)
     *
     * @param values [x, y, z] ъглова скорост в rad/s
     * @param timestamp Timestamp в наносекунди
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
        if (dt <= 0) return

        // Low-pass филтър на gyro Z
        val alpha = 0.7
        filteredGyroZ = alpha * filteredGyroZ + (1 - alpha) * values[2]

        // Ако rotation vector-ът е активен и наскоро обновен, не ползваме gyro за heading
        // (rotation vector е по-точен)
        val rotVectorFresh = (lastRotVectorTimestamp > 0 &&
                (timestamp - lastRotVectorTimestamp) < 500_000_000L) // < 500ms
        if (rotVectorFresh) return

        // Gyro интеграция (rad/s → deg)
        val gyroDelta = Math.toDegrees(filteredGyroZ * dt)

        // Rate limit: максимум 15°/сек
        val maxRate = 15.0
        val effectiveGyroDelta = gyroDelta.coerceIn(-maxRate * dt, maxRate * dt)

        heading += effectiveGyroDelta
        heading %= 360.0
        if (heading < 0) heading += 360.0

        // Complementary filter с compass
        val speed = sqrt(velNorth * velNorth + velEast * velEast)
        if (hasCompassData) {
            val compassInfluence = if (speed < 2.0) (1.0 - speed / 4.0) else 0.5
            val diff = ((compassHeading - heading + 540.0) % 360.0) - 180.0
            heading = (heading + diff * compassInfluence * COMPASS_WEIGHT * 10.0 + 360.0) % 360.0
        }
    }

    // ========================================================================
    // Magnetometer обработка (fallback за compass heading)
    // ========================================================================

    /**
     * Обработка на магнитометър данни — изчислява абсолютен compass heading.
     *
     * Използва се само когато rotation vector сензор НЕ е наличен.
     *
     * @param values [x, y, z] magnetic field в μT
     * @param timestamp Timestamp в наносекунди
     */
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

    /**
     * Изчислява compass heading от последните accel + mag данни.
     * Tilt-компенсиран heading за хоризонтално устройство.
     */
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
            val diff = ((newCompass - compassHeading + 540.0) % 360.0) - 180.0
            if (kotlin.math.abs(diff) > 45.0) return
            compassHeading = (compassHeading + diff * 0.05 + 360.0) % 360.0
        }
        hasCompassData = true
    }

    // ========================================================================
    // Барометър
    // ========================================================================

    /**
     * Обработка на барометър данни — височина от атмосферно налягане.
     *
     * h = 44330 * (1 - (P/P0)^(1/5.255))
     *
     * @param pressure Атмосферно налягане в hPa
     * @param timestamp Timestamp в наносекунди
     */
    fun processBarometer(pressure: Float, timestamp: Long) {
        if (!initialized) return
        lastBaroTimestamp = timestamp
        hasBarometer = true

        val P0 = 1013.25f
        altitude = 44330.0 * (1.0 - Math.pow(pressure.toDouble() / P0.toDouble(), 1.0 / 5.255))
    }

    // ========================================================================
    // GPS helper: конвертира GPS lat/lon в относителна позиция N/E
    // ========================================================================

    /**
     * Конвертира GPS lat/lon в относителна позиция North/East (метри от стартовата точка).
     *
     * Използва плоска Earth апроксимация (валидна за малки разстояния до ~100 km).
     *
     * @return doubleArrayOf(posN, posE) в метри
     */
    fun gpsToRelative(lat: Double, lon: Double): DoubleArray {
        val latRad = Math.toRadians(refLatitude)
        val deltaLat = (lat - refLatitude) * 111320.0
        val deltaLon = (lon - refLongitude) * 111320.0 * cos(latRad)
        return doubleArrayOf(deltaLat, deltaLon)
    }

    // ========================================================================
    // Резултати
    // ========================================================================

    /**
     * Връща текущата изчислена позиция.
     *
     * @return DRLocation с текущата позиция, heading, скорост и оценка на точността
     */
    fun getCurrentLocation(): DRLocation {
        if (!initialized) {
            throw IllegalStateException("Калкулаторът не е инициализиран")
        }

        val latRad = Math.toRadians(refLatitude)
        val deltaLat = posNorth / 111320.0
        val deltaLon = posEast / (111320.0 * cos(latRad))

        val currentLat = refLatitude + deltaLat
        val currentLon = refLongitude + deltaLon

        val speed = sqrt(velNorth * velNorth + velEast * velEast).toFloat()

        estimatedAccuracy = initialAccuracy + DRIFT_RATE * getElapsedSeconds()

        return DRLocation(
            latitude = currentLat,
            longitude = currentLon,
            altitude = altitude,
            bearing = heading.toFloat(),
            speed = speed,
            accuracy = estimatedAccuracy,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Връща текст с всички вътрешни стойности за debug.
     * Включва: heading, velocity, position, Earth-frame acceleration,
     * covariance diagonal (несигурност), bias, GPS correction stats.
     */
    fun getDebugState(): String {
        val gpsAge = if (lastGpsCorrectionTime > 0) (System.currentTimeMillis() - lastGpsCorrectionTime) / 1000 else -1
        return String.format(
            java.util.Locale.US,
            "DR_DEBUG: heading=%.2f, compassHeading=%.2f, " +
            "velN=%.3f, velE=%.3f, posN=%.3f, posE=%.3f, " +
            "earthAccelN=%.4f, earthAccelE=%.4f, " +
            "P_diag=[%.2f,%.2f,%.2f,%.2f], " +
            "biasN=%.4f, biasE=%.4f, " +
            "rotMat=${rotMatrixInitialized[0]}, gpsAge=${gpsAge}s, gpsCorr=#${gpsCorrectionCount}, " +
            "altitude=%.1f, " +
            "accel=[%.4f,%.4f,%.4f], gyro=[%.4f,%.4f,%.4f], mag=[%.4f,%.4f,%.4f], rot=[%.4f,%.4f,%.4f]",
            heading, compassHeading,
            velNorth, velEast, posNorth, posEast,
            earthAccelN, earthAccelE,
            P[0], P[5], P[10], P[15],
            accelBiasN, accelBiasE,
            altitude,
            rawAccelX, rawAccelY, rawAccelZ,
            rawGyroX, rawGyroY, rawGyroZ,
            rawMagX, rawMagY, rawMagZ,
            rawRotX, rawRotY, rawRotZ
        )
    }

    /**
     * Изминало време от инициализацията в секунди.
     */
    fun getElapsedSeconds(): Long {
        return if (initialized) {
            (System.currentTimeMillis() - startTime) / 1000
        } else {
            0
        }
    }

    /**
     * Очаквана точност в метри (нараства с времето).
     */
    fun getEstimatedAccuracy(): Float {
        return estimatedAccuracy
    }

    /**
     * Нулиране на калкулатора.
     */
    fun reset() {
        fillZero(x, 4)
        fillZero(P, 4)
        posNorth = 0.0
        posEast = 0.0
        velNorth = 0.0
        velEast = 0.0
        altitude = 0.0
        heading = 0.0
        lastAccelTimestamp = 0
        lastGyroTimestamp = 0
        lastMagTimestamp = 0
        lastBaroTimestamp = 0
        lastRotVectorTimestamp = 0
        filteredAccelX = 0.0
        filteredAccelY = 0.0
        filteredAccelZ = 0.0
        filteredGyroZ = 0.0
        rawAccelX = 0f
        rawAccelY = 0f
        rawAccelZ = 0f
        rawGyroX = 0f
        rawGyroY = 0f
        rawGyroZ = 0f
        rawMagX = 0f
        rawMagY = 0f
        rawMagZ = 0f
        rawRotX = 0f
        rawRotY = 0f
        rawRotZ = 0f
        gravityX = 0.0
        gravityY = 0.0
        gravityZ = 9.81
        initialized = false
        hasBarometer = false
        hasCompassData = false
        firstCompassComputed = false
        hasAccelForCompass = false
        hasMagForCompass = false
        sensorIsLinearAcceleration = false
        rotMatrixInitialized[0] = false
        accelBiasN = 0.0
        accelBiasE = 0.0
        lastGpsCorrectionTime = 0
        gpsCorrectionCount = 0
        earthAccelN = 0.0
        earthAccelE = 0.0
        estimatedAccuracy = initialAccuracy
    }

    /**
     * Дали калкулаторът е активен (инициализиран).
     */
    fun isActive(): Boolean {
        return initialized
    }

    // ========================================================================
    // Matrix utility функции (4×4 и 4×1)
    // ========================================================================

    /**
     * Запълва n×n матрица (row-major) с identity.
     */
    private fun fillIdentity(m: DoubleArray, n: Int) {
        fillZero(m, n)
        for (i in 0 until n) {
            m[i * n + i] = 1.0
        }
    }

    /**
     * Запълва n×n матрица (row-major) с нули.
     */
    private fun fillZero(m: DoubleArray, n: Int) {
        for (i in 0 until n * n) {
            m[i] = 0.0
        }
    }

    /**
     * Matrix-vector multiply: result = M * v
     * M is rows×cols (row-major), v is cols×1, result is rows×1
     */
    private fun matVecMul(M: DoubleArray, v: DoubleArray, result: DoubleArray, rows: Int, cols: Int) {
        for (i in 0 until rows) {
            var sum = 0.0
            for (j in 0 until cols) {
                sum += M[i * cols + j] * v[j]
            }
            result[i] = sum
        }
    }

    /**
     * Matrix-matrix multiply: result = A * B
     * A is rowsA×colsA (row-major), B is colsA×colsB (row-major), result is rowsA×colsB
     */
    private fun matMatMul(A: DoubleArray, B: DoubleArray, result: DoubleArray, rowsA: Int, colsA: Int, colsB: Int) {
        for (i in 0 until rowsA) {
            for (j in 0 until colsB) {
                var sum = 0.0
                for (k in 0 until colsA) {
                    sum += A[i * colsA + k] * B[k * colsB + j]
                }
                result[i * colsB + j] = sum
            }
        }
    }

    /**
     * Transpose: result = M^T
     * M is n×n (row-major), result is n×n (row-major)
     */
    private fun transpose(M: DoubleArray, result: DoubleArray, n: Int) {
        for (i in 0 until n) {
            for (j in 0 until n) {
                result[j * n + i] = M[i * n + j]
            }
        }
    }

    /**
     * 4×4 matrix inversion using Gauss-Jordan elimination.
     *
     * @param m 4×4 matrix (row-major, 16 elements)
     * @param result 4×4 inverted matrix (row-major, 16 elements)
     * @return true ако успешно, false ако матрицата е сингулярна
     */
    private fun invert4x4(m: DoubleArray, result: DoubleArray): Boolean {
        // Создаваме augmented matrix [m | I]
        val aug = DoubleArray(32) // 4 rows × 8 cols
        for (i in 0 until 4) {
            for (j in 0 until 4) {
                aug[i * 8 + j] = m[i * 4 + j]
            }
            aug[i * 8 + 4 + i] = 1.0
        }

        // Gauss-Jordan елиминация
        for (col in 0 until 4) {
            // Намиране на pivot (най-голям абсолютен стойност в колоната)
            var maxRow = col
            var maxVal = kotlin.math.abs(aug[col * 8 + col])
            for (row in (col + 1) until 4) {
                val val_ = kotlin.math.abs(aug[row * 8 + col])
                if (val_ > maxVal) {
                    maxVal = val_
                    maxRow = row
                }
            }

            // Проверка за сингулярност
            if (maxVal < 1e-12) return false

            // Размяна на редове
            if (maxRow != col) {
                for (j in 0 until 8) {
                    val tmp = aug[col * 8 + j]
                    aug[col * 8 + j] = aug[maxRow * 8 + j]
                    aug[maxRow * 8 + j] = tmp
                }
            }

            // Нормализиране на pivot реда
            val pivot = aug[col * 8 + col]
            for (j in 0 until 8) {
                aug[col * 8 + j] /= pivot
            }

            // Елиминация на другите редове
            for (row in 0 until 4) {
                if (row == col) continue
                val factor = aug[row * 8 + col]
                for (j in 0 until 8) {
                    aug[row * 8 + j] -= factor * aug[col * 8 + j]
                }
            }
        }

        // Извличане на резултата (десните 4 колони)
        for (i in 0 until 4) {
            for (j in 0 until 4) {
                result[i * 4 + j] = aug[i * 8 + 4 + j]
            }
        }

        return true
    }

}

/**
 * Data class представляваща изчислена позиция от dead reckoning.
 *
 * @property latitude Ширина в градуси
 * @property longitude Дължина в градуси
 * @property altitude Височина в метри
 * @property bearing Heading в градуси (0-360)
 * @property speed Скорост в m/s
 * @property accuracy Очаквана точност в метри
 * @property timestamp Време на изчисление (System.currentTimeMillis())
 */
data class DRLocation(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val bearing: Float,    // degrees 0-360
    val speed: Float,      // m/s
    val accuracy: Float,   // estimated accuracy in meters
    val timestamp: Long
)