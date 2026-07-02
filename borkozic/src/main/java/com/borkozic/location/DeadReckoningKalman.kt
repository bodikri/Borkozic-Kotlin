package com.borkozic.location

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 6-state Kalman Filter за Dead Reckoning.
 *
 * State vector (6D):
 *   x = [posN, posE, velN, velE, biasAccelN, biasAccelE]
 *
 * Dynamics:
 *   posN += velN * dt + 0.5 * (accelN - biasN) * dt²
 *   posE += velE * dt + 0.5 * (accelE - biasE) * dt²
 *   velN += (accelN - biasN) * dt
 *   velE += (accelE - biasE) * dt
 *   biasN += 0  (оценен чрез process noise)
 *   biasE += 0
 *
 * Measurement (GPS, когато е наличен):
 *   z = [gpsPosN, gpsPosE, gpsVelN, gpsVelE]
 *
 * Ключови свойства:
 *   - При нулево ускорение: скоростта остава константна (закон на Нютон)
 *   - НЯМА изкуствен speed decay
 *   - Process noise Q ограничава дрифта, но НЕ bias-ва оценката
 *   - Bias states се оценяват бавно чрез GPS корекции + process noise
 */
class DeadReckoningKalman {

    // ========================================================================
    // State & covariance
    // ========================================================================

    private val x = DoubleArray(6)       // state: [posN, posE, velN, velE, biasN, biasE]
    private val P = Array(6) { DoubleArray(6) }  // covariance

    private var refLatitude: Double = 0.0
    private var refLongitude: Double = 0.0
    private var refAltitude: Double = 0.0
    private var initialAccuracy: Float = 0.0f

    private var initialized: Boolean = false
    private var startTimeMs: Long = 0
    private var lastPredictTimestamp: Long = 0

    // ========================================================================
    // Debug
    // ========================================================================

    private var predictCount: Int = 0
    private var updateCount: Int = 0
    private var lastGpsCorrectionTimeMs: Long = 0

    // ========================================================================
    // Tunable parameters
    // ========================================================================

    // Process noise — акселерометър noise density (m/s²/√Hz)
    // Типичен MEMS: 0.1–0.5 m/s²/√Hz
    private val ACCEL_NOISE_DENSITY_CRUISE = 0.02    // плавно движение — почти без Q растеж
    private val ACCEL_NOISE_DENSITY_MANEUVER = 0.3   // маневри — пълно Q

    // Adaptive Q: при ускорение > threshold, allow Q injection само на всеки 2 секунди
    private val HIGH_ACCEL_THRESHOLD = 0.5            // m/s² (~0.05g) — достатъчно за нормално каране
    private val HIGH_ACCEL_Q_INTERVAL_MS = 2_000L     // throttle интервал
    private var lastHighAccelQInjectMs: Long = 0
    private var lastNoiseDensity: Double = ACCEL_NOISE_DENSITY_CRUISE  // за debug

    // Bias random walk (m/s³/√Hz) — адаптивен: расте при маневри
    private val BIAS_NOISE_DENSITY_CRUISE = 0.001     // почти никаква промяна при плавно движение
    private val BIAS_NOISE_DENSITY_MANEUVER = 0.05   // 50x по-бърза оценка при ускорение > threshold
    private var lastBiasDensity: Double = BIAS_NOISE_DENSITY_CRUISE      // за debug

    // Максимален dt за един predict (safety clamp)
    private val MAX_DT = 1.0

    // ========================================================================
    // Инициализация
    // ========================================================================

    fun initialize(
        gpsLat: Double, gpsLon: Double, gpsAlt: Double,
        gpsSpeed: Double, gpsBearing: Double,
        gpsAccuracy: Float
    ) {
        refLatitude = gpsLat
        refLongitude = gpsLon
        refAltitude = gpsAlt
        initialAccuracy = gpsAccuracy

        val headingRad = Math.toRadians(gpsBearing)

        // State — начална позиция = 0 (релативна)
        x[0] = 0.0   // posN
        x[1] = 0.0   // posE
        x[2] = gpsSpeed * cos(headingRad)  // velN
        x[3] = gpsSpeed * sin(headingRad)  // velE
        x[4] = 0.0   // biasN — неизвестен
        x[5] = 0.0   // biasE — неизвестен

        // Covariance — диагонална
        for (i in 0..5) {
            for (j in 0..5) {
                P[i][j] = 0.0
            }
        }
        P[0][0] = 0.1   // posN: ниска несигурност (GPS беше активен)
        P[1][1] = 0.1   // posE
        P[2][2] = 0.5   // velN: средна (GPS speed има lag/шум)
        P[3][3] = 0.5   // velE
        P[4][4] = 0.3   // biasN: висока несигурност
        P[5][5] = 0.3   // biasE

        initialized = true
        startTimeMs = System.currentTimeMillis()
        lastPredictTimestamp = 0
        lastGpsCorrectionTimeMs = System.currentTimeMillis()
        predictCount = 0
        updateCount = 0
    }

    // ========================================================================
    // Prediction (извиква се при всяко акселерометър събитие)
    // ========================================================================

    /**
     * @param accelN Earth-frame ускорение North (m/s², gravity-removed)
     * @param accelE Earth-frame ускорение East  (m/s², gravity-removed)
     * @param timestampNs сензорен timestamp в наносекунди
     */
    fun predict(accelN: Double, accelE: Double, timestampNs: Long) {
        if (!initialized) return

        val dt = if (lastPredictTimestamp > 0) {
            ((timestampNs - lastPredictTimestamp) / 1_000_000_000.0)
                .coerceIn(0.0, MAX_DT)
        } else {
            0.0
        }
        lastPredictTimestamp = timestampNs

        if (dt <= 0.0 || dt > MAX_DT) return

        // Коригирано ускорение (изваждаме оценения bias)
        val correctedAccelN = accelN - x[4]
        val correctedAccelE = accelE - x[5]

        // --- State prediction ---
        val dt2half = 0.5 * dt * dt

        // posN_new = posN + velN*dt + 0.5*correctedAccelN*dt²
        val newPosN = x[0] + x[2] * dt + correctedAccelN * dt2half
        // posE_new
        val newPosE = x[1] + x[3] * dt + correctedAccelE * dt2half
        // velN_new = velN + correctedAccelN*dt
        val newVelN = x[2] + correctedAccelN * dt
        // velE_new
        val newVelE = x[3] + correctedAccelE * dt
        // bias: остава същият (оценява се чрез process noise + GPS update)

        x[0] = newPosN
        x[1] = newPosE
        x[2] = newVelN
        x[3] = newVelE

        // --- Covariance prediction: P = F·P·F^T + G·Q_accel·G^T·dt ---

        // Jacobian F (6x6)
        val F00 = 1.0; val F01 = 0.0; val F02 = dt; val F03 = 0.0; val F04 = -dt2half; val F05 = 0.0
        val F10 = 0.0; val F11 = 1.0; val F12 = 0.0; val F13 = dt; val F14 = 0.0; val F15 = -dt2half
        val F20 = 0.0; val F21 = 0.0; val F22 = 1.0; val F23 = 0.0; val F24 = -dt; val F25 = 0.0
        val F30 = 0.0; val F31 = 0.0; val F32 = 0.0; val F33 = 1.0; val F34 = 0.0; val F35 = -dt
        val F40 = 0.0; val F41 = 0.0; val F42 = 0.0; val F43 = 0.0; val F44 = 1.0; val F45 = 0.0
        val F50 = 0.0; val F51 = 0.0; val F52 = 0.0; val F53 = 0.0; val F54 = 0.0; val F55 = 1.0

        // P_temp = F * P  (6x6 · 6x6 → 6x6)
        val FP = Array(6) { DoubleArray(6) }
        for (i in 0..5) {
            val Fi = doubleArrayOf(
                if (i == 0) F00 else if (i == 1) F10 else if (i == 2) F20 else if (i == 3) F30 else if (i == 4) F40 else F50,
                if (i == 0) F01 else if (i == 1) F11 else if (i == 2) F21 else if (i == 3) F31 else if (i == 4) F41 else F51,
                if (i == 0) F02 else if (i == 1) F12 else if (i == 2) F22 else if (i == 3) F32 else if (i == 4) F42 else F52,
                if (i == 0) F03 else if (i == 1) F13 else if (i == 2) F23 else if (i == 3) F33 else if (i == 4) F43 else F53,
                if (i == 0) F04 else if (i == 1) F14 else if (i == 2) F24 else if (i == 3) F34 else if (i == 4) F44 else F54,
                if (i == 0) F05 else if (i == 1) F15 else if (i == 2) F25 else if (i == 3) F35 else if (i == 4) F45 else F55
            )
            for (j in 0..5) {
                var sum = 0.0
                for (k in 0..5) sum += Fi[k] * P[k][j]
                FP[i][j] = sum
            }
        }

        // P_new = FP * F^T  (FP is F·P, now multiply by F^T)
        for (i in 0..5) {
            val Fj = doubleArrayOf(
                if (i == 0) F00 else if (i == 1) F01 else if (i == 2) F02 else if (i == 3) F03 else if (i == 4) F04 else F05,
                if (i == 0) F10 else if (i == 1) F11 else if (i == 2) F12 else if (i == 3) F13 else if (i == 4) F14 else F15,
                if (i == 0) F20 else if (i == 1) F21 else if (i == 2) F22 else if (i == 3) F23 else if (i == 4) F24 else F25,
                if (i == 0) F30 else if (i == 1) F31 else if (i == 2) F32 else if (i == 3) F33 else if (i == 4) F34 else F35,
                if (i == 0) F40 else if (i == 1) F41 else if (i == 2) F42 else if (i == 3) F43 else if (i == 4) F44 else F45,
                if (i == 0) F50 else if (i == 1) F51 else if (i == 2) F52 else if (i == 3) F53 else if (i == 4) F54 else F55
            ) // note: this is column i of F^T = row i of F
            for (j in 0..5) {
                var sum = 0.0
                for (k in 0..5) sum += FP[i][k] * Fj[k]
                P[i][j] = sum
            }
        }

        // --- Adaptive process noise ---
        // При плавно движение (|accel| < 1 m/s²): почти никакъв Q растеж
        // При маневра (|accel| >= 1 m/s²): пълно Q, но throttle-нато на всеки 2 секунди
        val accelMag = sqrt(accelN * accelN + accelE * accelE)
        val now = System.currentTimeMillis()
        val noiseDensity = if (accelMag < HIGH_ACCEL_THRESHOLD) {
            // Cruise: минимален растеж на несигурността
            ACCEL_NOISE_DENSITY_CRUISE
        } else if (now - lastHighAccelQInjectMs >= HIGH_ACCEL_Q_INTERVAL_MS) {
            // Maneuver + throttle изтекъл: инжектираме Q и обновяваме timestamp
            lastHighAccelQInjectMs = now
            ACCEL_NOISE_DENSITY_MANEUVER
        } else {
            // Maneuver но throttle не е изтекъл: cruise noise (чакаме 2s)
            ACCEL_NOISE_DENSITY_CRUISE
        }
        lastNoiseDensity = noiseDensity

        val qa2 = noiseDensity * noiseDensity * dt
        val dt2h = dt2half

        P[0][0] += qa2 * dt2h * dt2h
        P[0][2] += qa2 * dt2h * dt
        P[2][0] += qa2 * dt2h * dt
        P[2][2] += qa2 * dt * dt

        P[1][1] += qa2 * dt2h * dt2h
        P[1][3] += qa2 * dt2h * dt
        P[3][1] += qa2 * dt2h * dt
        P[3][3] += qa2 * dt * dt

        // Bias process noise — адаптивен: ускорява се при маневри
        val biasDensity = if (accelMag < HIGH_ACCEL_THRESHOLD) {
            BIAS_NOISE_DENSITY_CRUISE
        } else {
            BIAS_NOISE_DENSITY_MANEUVER
        }
        lastBiasDensity = biasDensity
        val qb2 = biasDensity * biasDensity * dt
        P[4][4] += qb2
        P[5][5] += qb2

        predictCount++

        // Clamp covariance — предотвратява numerical explosion при дълъг DR
        // Без clamp, P расте до 10^13-10^20 за 50s и inversion-ът дава NaN
        clampCovariance()
    }

    // ========================================================================
    // GPS Update (корекция с GPS измерване)
    // ========================================================================

    /**
     * @param gpsPosN GPS позиция North (метри от reference)
     * @param gpsPosE GPS позиция East (метри от reference)
     * @param gpsVelN GPS скорост North (m/s)
     * @param gpsVelE GPS скорост East (m/s)
     * @param gpsAccuracyGPS точност (метри, от location.getAccuracy())
     */
    fun updateWithGPS(
        gpsPosN: Double, gpsPosE: Double,
        gpsVelN: Double, gpsVelE: Double,
        gpsAccuracy: Float
    ) {
        if (!initialized) return

        // Measurement noise R (4x4 diagonal)
        val rPos = (gpsAccuracy * gpsAccuracy).toDouble().coerceAtLeast(1.0)
        val rVel = (gpsAccuracy * 0.25).coerceAtLeast(0.5).let { it * it }  // ~1.5 m/s at 6m acc

        // Innovation: y = z - H·x
        // H = [I₄ | 0₄ₓ₂], so H·x = [posN, posE, velN, velE]
        val yPosN = gpsPosN - x[0]
        val yPosE = gpsPosE - x[1]
        val yVelN = gpsVelN - x[2]
        val yVelE = gpsVelE - x[3]

        // Kalman gain K = P·H^T · (H·P·H^T + R)^⁻¹
        // Since H is [I₄ | 0], H·P·H^T = top-left 4×4 of P
        // (H·P·H^T + R)⁻¹ is 4×4. We invert it.

        // S = H·P·H^T + R  (4x4 matrix)
        val s00 = P[0][0] + rPos;  val s01 = P[0][1];         val s02 = P[0][2];         val s03 = P[0][3]
        val s10 = P[1][0];         val s11 = P[1][1] + rPos;  val s12 = P[1][2];         val s13 = P[1][3]
        val s20 = P[2][0];         val s21 = P[2][1];         val s22 = P[2][2] + rVel;  val s23 = P[2][3]
        val s30 = P[3][0];         val s31 = P[3][1];         val s32 = P[3][2];         val s33 = P[3][3] + rVel

        // Invert 4x4 S
        val det = invert4x4(s00, s01, s02, s03, s10, s11, s12, s13, s20, s21, s22, s23, s30, s31, s32, s33)
        val sInv = det // destructured below
        // sInv is the inverted matrix

        val sInv00 = sInv[0]; val sInv01 = sInv[1]; val sInv02 = sInv[2]; val sInv03 = sInv[3]
        val sInv10 = sInv[4]; val sInv11 = sInv[5]; val sInv12 = sInv[6]; val sInv13 = sInv[7]
        val sInv20 = sInv[8]; val sInv21 = sInv[9]; val sInv22 = sInv[10]; val sInv23 = sInv[11]
        val sInv30 = sInv[12]; val sInv31 = sInv[13]; val sInv32 = sInv[14]; val sInv33 = sInv[15]

        // K = P·H^T · S⁻¹
        // P·H^T = first 4 columns of P (since H = [I₄ | 0])
        // So K[i][j] = Σₖ P[i][k] · S⁻¹[k][j] for k=0..3, i=0..5, j=0..3
        val K = Array(6) { DoubleArray(4) }
        for (i in 0..5) {
            val pi = P[i]
            val ki = K[i]
            ki[0] = pi[0] * sInv00 + pi[1] * sInv10 + pi[2] * sInv20 + pi[3] * sInv30
            ki[1] = pi[0] * sInv01 + pi[1] * sInv11 + pi[2] * sInv21 + pi[3] * sInv31
            ki[2] = pi[0] * sInv02 + pi[1] * sInv12 + pi[2] * sInv22 + pi[3] * sInv32
            ki[3] = pi[0] * sInv03 + pi[1] * sInv13 + pi[2] * sInv23 + pi[3] * sInv33
        }

        // State update: x = x + K·y
        x[0] += K[0][0] * yPosN + K[0][1] * yPosE + K[0][2] * yVelN + K[0][3] * yVelE
        x[1] += K[1][0] * yPosN + K[1][1] * yPosE + K[1][2] * yVelN + K[1][3] * yVelE
        x[2] += K[2][0] * yPosN + K[2][1] * yPosE + K[2][2] * yVelN + K[2][3] * yVelE
        x[3] += K[3][0] * yPosN + K[3][1] * yPosE + K[3][2] * yVelN + K[3][3] * yVelE
        x[4] += K[4][0] * yPosN + K[4][1] * yPosE + K[4][2] * yVelN + K[4][3] * yVelE
        x[5] += K[5][0] * yPosN + K[5][1] * yPosE + K[5][2] * yVelN + K[5][3] * yVelE

        // Covariance update: P = (I - K·H)·P
        // K·H = K extended with zeros for bias columns (K is 6x4, H=[I|0])
        // So (I - K·H)[i][j] = δᵢⱼ - K[i][col(j)] for j < 4, else δᵢⱼ
        // Update: P_new[i][j] = P[i][j] - Σₖ K[i][k]·P[k][j] for k=0..3
        val Pnew = Array(6) { DoubleArray(6) }
        for (i in 0..5) {
            for (j in 0..5) {
                var sum = P[i][j]
                for (k in 0..3) sum -= K[i][k] * P[k][j]
                Pnew[i][j] = sum
            }
        }
        for (i in 0..5) {
            System.arraycopy(Pnew[i], 0, P[i], 0, 6)
        }

        lastGpsCorrectionTimeMs = System.currentTimeMillis()
        updateCount++
    }

    // ========================================================================
    // Coordinate transforms
    // ========================================================================

    /** GPS lat/lon → North/East relative to reference (метри) */
    fun gpsToRelative(lat: Double, lon: Double): DoubleArray {
        val latRad = Math.toRadians(refLatitude)
        val dN = (lat - refLatitude) * 111320.0
        val dE = (lon - refLongitude) * 111320.0 * cos(latRad)
        return doubleArrayOf(dN, dE)
    }

    // ========================================================================
    // Outputs
    // ========================================================================

    fun getCurrentLocation(): DRLocation {
        if (!initialized) throw IllegalStateException("Kalman not initialized")

        val latRad = Math.toRadians(refLatitude)
        val deltaLat = x[0] / 111320.0
        val deltaLon = x[1] / (111320.0 * cos(latRad))

        val speed = sqrt(x[2] * x[2] + x[3] * x[3])
        val bearing = Math.toDegrees(atan2(x[3], x[2]))

        // Estimated accuracy from covariance
        val posCov = sqrt(P[0][0] + P[1][1]).toFloat()
        val accuracy = (initialAccuracy + posCov).coerceAtMost(100.0f)

        return DRLocation(
            latitude = refLatitude + deltaLat,
            longitude = refLongitude + deltaLon,
            altitude = refAltitude,
            bearing = ((bearing + 360.0) % 360.0).toFloat(),
            speed = speed.toFloat(),
            accuracy = accuracy,
            timestamp = System.currentTimeMillis()
        )
    }

    fun getDebugState(): String {
        val speed = sqrt(x[2] * x[2] + x[3] * x[3])
        val bearing = Math.toDegrees(atan2(x[3], x[2]))
        val gpsAge = if (lastGpsCorrectionTimeMs > 0)
            (System.currentTimeMillis() - lastGpsCorrectionTimeMs) / 1000 else -1
        val posCov = sqrt(P[0][0] + P[1][1])

        return String.format(
            java.util.Locale.US,
            "DR_KALMAN: heading=%.2f, speed=%.2f, posN=%.2f, posE=%.2f, " +
            "velN=%.2f, velE=%.2f, biasN=%.4f, biasE=%.4f, " +
            "covPos=%.1f, covVel=%.2f, gpsAge=%ds(#%d), Q=%.2f, Qb=%.3f, pred=#%d",
            (bearing + 360.0) % 360.0, speed,
            x[0], x[1], x[2], x[3], x[4], x[5],
            posCov, sqrt(P[2][2] + P[3][3]),
            gpsAge, updateCount, lastNoiseDensity, lastBiasDensity, predictCount
        )
    }

    /** Начален snapshot за логване — връща 6-те компонента като текст */
    fun getInitialSnapshot(): String {
        val speed = sqrt(x[2] * x[2] + x[3] * x[3])
        val bearing = Math.toDegrees(atan2(x[3], x[2]))
        return String.format(
            java.util.Locale.US,
            "INIT_SNAPSHOT: velN=%.2f, velE=%.2f, speed=%.2f, bearing=%.0f, biasN=0, biasE=0",
            x[2], x[3], speed, (bearing + 360.0) % 360.0
        )
    }

    fun isActive(): Boolean = initialized
    fun getElapsedSeconds(): Long =
        if (initialized) (System.currentTimeMillis() - startTimeMs) / 1000 else 0
    fun getGpsAgeSeconds(): Long =
        if (lastGpsCorrectionTimeMs > 0) (System.currentTimeMillis() - lastGpsCorrectionTimeMs) / 1000 else -1

    fun reset() {
        for (i in 0..5) {
            x[i] = 0.0
            for (j in 0..5) P[i][j] = 0.0
        }
        initialized = false
        predictCount = 0
        updateCount = 0
        lastGpsCorrectionTimeMs = 0
        lastPredictTimestamp = 0
        lastHighAccelQInjectMs = 0
    }

    // ========================================================================
    // Bias estimation (velocity innovation running average)
    // ========================================================================

    /**
     * Оценява accelerometer bias от velocity innovation (GPS - predicted velocity).
     * Трябва да се вика при всеки GPS fix, преди updateWithGPS.
     * Работи само когато скоростта > 1 m/s (статичният телефон не дава полезна информация).
     */
    fun estimateBiasFromVelocity(gpsSpeed: Double, gpsBearing: Double) {
        if (!initialized || gpsSpeed < 1.0) return

        val headingRad = Math.toRadians(gpsBearing)
        val gpsVelN = gpsSpeed * cos(headingRad)
        val gpsVelE = gpsSpeed * sin(headingRad)

        // Innovation = measured - predicted velocity
        val innovN = gpsVelN - x[2]
        val innovE = gpsVelE - x[3]

        // Running average с голяма инерция (bias се мени бавно)
        val alpha = 0.01  // learn rate: 1% per GPS fix (~1Hz)
        x[4] += alpha * innovN
        x[5] += alpha * innovE

        // Clamp bias — realistic MEMS bias range
        x[4] = x[4].coerceIn(-1.0, 1.0)
        x[5] = x[5].coerceIn(-1.0, 1.0)
    }

    // ========================================================================
    // Covariance clamp — предотвратява numerical explosion
    // ========================================================================

    /**
     * Ограничава P елементите до разумни стойности.
     * Без това, след 40-50s DR, P расте до 10^13-10^15 и matrix inversion дава NaN.
     * Covariance cap от 10^6 значи позиционна несигурност до ~1 km.
     */
    private fun clampCovariance() {
        val maxCov = 1_000_000.0  // ~1 km² variance ceiling
        for (i in 0..5) {
            for (j in 0..5) {
                if (P[i][j].isNaN() || P[i][j].isInfinite()) {
                    P[i][j] = 0.0
                }
                P[i][j] = P[i][j].coerceIn(-maxCov, maxCov)
            }
        }
    }

    // ========================================================================
    // 4x4 Matrix Inversion (Gaussian elimination)
    // ========================================================================

    private fun invert4x4(
        a00: Double, a01: Double, a02: Double, a03: Double,
        a10: Double, a11: Double, a12: Double, a13: Double,
        a20: Double, a21: Double, a22: Double, a23: Double,
        a30: Double, a31: Double, a32: Double, a33: Double
    ): DoubleArray {
        // NaN/Inf guard — връща identity ако входът е numerical garbage
        val allElements = doubleArrayOf(a00, a01, a02, a03, a10, a11, a12, a13, a20, a21, a22, a23, a30, a31, a32, a33)
        if (allElements.any { it.isNaN() || it.isInfinite() }) {
            return doubleArrayOf(1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0)
        }

        // Augmented matrix [A | I]
        val m = arrayOf(
            doubleArrayOf(a00, a01, a02, a03, 1.0, 0.0, 0.0, 0.0),
            doubleArrayOf(a10, a11, a12, a13, 0.0, 1.0, 0.0, 0.0),
            doubleArrayOf(a20, a21, a22, a23, 0.0, 0.0, 1.0, 0.0),
            doubleArrayOf(a30, a31, a32, a33, 0.0, 0.0, 0.0, 1.0)
        )

        for (col in 0..3) {
            // Pivot: намираме най-големия елемент в колоната
            var maxRow = col
            var maxVal = kotlin.math.abs(m[col][col])
            for (row in (col + 1)..3) {
                val v = kotlin.math.abs(m[row][col])
                if (v > maxVal) { maxVal = v; maxRow = row }
            }
            if (maxVal < 1e-12) continue  // singular

            if (maxRow != col) {
                val tmp = m[col]; m[col] = m[maxRow]; m[maxRow] = tmp
            }

            val pivot = m[col][col]
            // Normalize row
            for (j in 0..7) m[col][j] /= pivot

            // Eliminate other rows
            for (row in 0..3) {
                if (row == col) continue
                val factor = m[row][col]
                for (j in 0..7) m[row][j] -= factor * m[col][j]
            }
        }

        // NaN check след inversion — ако нещо се е счупило, връща identity
        val result = doubleArrayOf(
            m[0][4], m[0][5], m[0][6], m[0][7],
            m[1][4], m[1][5], m[1][6], m[1][7],
            m[2][4], m[2][5], m[2][6], m[2][7],
            m[3][4], m[3][5], m[3][6], m[3][7]
        )
        if (result.any { it.isNaN() || it.isInfinite() }) {
            return doubleArrayOf(1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0)
        }
        return result
    }
}
