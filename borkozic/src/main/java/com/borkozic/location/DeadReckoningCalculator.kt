package com.borkozic.location

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Dead Reckoning Calculator за авиационна навигация.
 *
 * Изчислява позиция от IMU сензори (accelerometer, gyroscope, magnetometer, barometer)
 * когато GPS не е наличен. Устройството е закрепено хоризонтално на коляното на пилота.
 *
 * Координатна система:
 * - Phone X → North (forward / нос на самолета)
 * - Phone Y → East (right / дясно крило)
 * - Phone Z → Down (надолу към земята)
 *
 * Алгоритъм:
 * 1. Heading: complementary filter (gyro + compass) — gyro за плавност, compass против drift
 * 2. Position: double integration на linear acceleration в Earth frame
 * 3. Altitude: барометър (точно) или double integration (по-неточно)
 * 4. Accuracy: линейна деградация с времето (drift rate ~2 m/s за авиация)
 */
class DeadReckoningCalculator {

    // --- Позиция в Earth frame (относително стартовата точка) ---
    private var posNorth: Double = 0.0  // метри North от стартовата точка
    private var posEast: Double = 0.0  // метри East от стартовата точка
    private var velNorth: Double = 0.0 // m/s North
    private var velEast: Double = 0.0  // m/s East

    // --- Височина ---
    private var altitude: Double = 0.0
    private var velUp: Double = 0.0
    private var hasBarometer: Boolean = false

    // --- Heading (градуси, 0-360) ---
    private var heading: Double = 0.0

    // --- Compass heading от магнитометър + акселерометър (абсолютен референтен ъгъл) ---
    private var compassHeading: Double = 0.0
    private var hasCompassData: Boolean = false
    private var firstCompassComputed: Boolean = false

    // --- Последни raw стойности за compass heading изчисление ---
    private val accelForCompass = FloatArray(3)
    private val magForCompass = FloatArray(3)
    private var hasAccelForCompass: Boolean = false
    private var hasMagForCompass: Boolean = false

    // --- Timestamps (наносекунди от SensorEvent) ---
    private var lastAccelTimestamp: Long = 0
    private var lastGyroTimestamp: Long = 0
    private var lastMagTimestamp: Long = 0
    private var lastBaroTimestamp: Long = 0

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

    // --- Gravity оценка (за премахване от accelerometer) ---
    // Устройството е хоризонтално → gravity ≈ [0, 0, 9.81] в device frame
    // Също използвана за tilt компенсация на compass heading при LINEAR_ACCELERATION сензор
    private var gravityX: Double = 0.0
    private var gravityY: Double = 0.0
    private var gravityZ: Double = 9.81
    // Флаг: дали сензорът е LINEAR_ACCELERATION (без gravity)
    private var sensorIsLinearAcceleration: Boolean = false

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

    // Коефициент за complementary filter (compass корекция на gyro heading)
    // По-малка тежест за компаса — компасът е шумен, gyro е по-стабилен
    private val COMPASS_WEIGHT = 0.02

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

        // Нулиране на позиция
        posNorth = 0.0
        posEast = 0.0
        velUp = 0.0

        // Инициализиране на velocity векторите с GPS скоростта
        val br = Math.toRadians(bearing.toDouble())
        velNorth = speed * cos(br).toDouble()
        velEast = speed * sin(br).toDouble()

        heading = bearing.toDouble()

        initialAccuracy = accuracy
        estimatedAccuracy = accuracy
        startTime = System.currentTimeMillis()

        // Нулиране на timestamps и филтри
        lastAccelTimestamp = 0
        lastGyroTimestamp = 0
        lastMagTimestamp = 0
        lastBaroTimestamp = 0
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

        initialized = true
    }

    /**
     * Обработка на акселерометър данни — double integration за позиция.
     *
     * Устройството е хоризонтално: Phone X→North, Phone Y→East, Phone Z→Down.
     *
     * Стъпки:
     * 1. Запазване на raw accel за compass heading (споделя се с magnetometer-а)
     * 2. Low-pass филтър на raw данни
     * 3. Премахване на gravity (само ако е TYPE_ACCELEROMETER) → linear acceleration
     * 4. Трансформация към Earth frame (за хоризонтално устройство е директна)
     * 5. Double integration: velocity += accel*dt, position += vel*dt + 0.5*accel*dt²
     * 6. Леко damping за избягване на безкрайно нарастване от noise
     *
     * @param values [x, y, z] ускорение в m/s²
     * @param timestamp Timestamp в наносекунди (от SensorEvent.timestamp)
     * @param isLinearAcceleration true ако сензорът е TYPE_LINEAR_ACCELERATION (gravity вече е премахнато)
     */
    fun processAccelerometer(values: FloatArray, timestamp: Long, isLinearAcceleration: Boolean) {
        if (!initialized) return

        // Запазване на raw accel за compass heading изчисление
        // ВНИМАНИЕ: Ако сензорът е LINEAR_ACCELERATION, данните НЕ съдържат gravity.
        // Tilt компенсацията има нужда от gravity за pitch/roll изчисление.
        // Запазваме raw данните + флага, а в computeCompassHeading добавяме gravity обратно.
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

        // Изчисляване на time delta в секунди
        val dt = if (lastAccelTimestamp > 0) {
            (timestamp - lastAccelTimestamp) / 1_000_000_000.0
        } else {
            0.0
        }
        lastAccelTimestamp = timestamp
        if (dt <= 0) return

        // Low-pass филтър на raw акселерометър стойности (по-малко агресивен, alpha=0.7)
        // Също обновява gravity оценката при TYPE_ACCELEROMETER (за премахване по-късно)
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

        // Трансформация към Earth frame (устройството е хоризонтално)
        val accelNorth = linearAccelX   // Phone X → North
        val accelEast = linearAccelY    // Phone Y → East
        // Забележка: accelUp НЕ се използва за височина без барометър —
        // double integration на accel Z е твърде неточна (noise → голям drift)
        // Височината остава фиксирана на стартовата GPS височина

        // Deadzone: ако ускорението е под noise threshold, не интегрира
        // Това предотвратява натрупване на скорост от sensor noise
        val ACCEL_DEADZONE = 0.3 // m/s² — под този праг ускорението е noise
        val effectiveAccelNorth = if (kotlin.math.abs(accelNorth) < ACCEL_DEADZONE) 0.0 else accelNorth
        val effectiveAccelEast = if (kotlin.math.abs(accelEast) < ACCEL_DEADZONE) 0.0 else accelEast

        // Single integration за скорост (не double — позицията се изчислява от скоростта)
        velNorth += effectiveAccelNorth * dt
        velEast += effectiveAccelEast * dt

        // Drag: скоростта намалява към 0 с лек decay (симулира аеродинамично съпротивление)
        // 0.98 per frame at ~20Hz → ~33% загуба за 5 сек (разумно за колело/самолет)
        val drag = 0.98.pow(dt * 20.0)
        velNorth *= drag
        velEast *= drag

        // Позиция от скоростта
        posNorth += velNorth * dt
        posEast += velEast * dt

        // Височина: само от барометър. Без барометър — фиксира стартовата GPS височина.
        // Double integration на accel Z дава голям drift (6.8m за 10 сек в теста).
        if (!hasBarometer) {
            // Altitude остава на refAltitude (зададена при initialize())
            altitude = refAltitude
        }
    }

    /**
     * Обработка на жироскоп данни — complementary filter за heading.
     *
     * heading = 0.98 * (heading + gyroZ*dt) + 0.02 * compassHeading
     *
     * Gyro е точен за кратък срок (малко drift), compass е абсолютен референтен.
     * Комбинацията дава плавен heading без дългосрочен drift.
     *
     * gyroZ: rotation rate около Z ос (надолу, перпендикулярна на екрана).
     * Положително rotation = завиване надясно (CW отгоре).
     *
     * @param values [x, y, z] ъглова скорост в rad/s
     * @param timestamp Timestamp в наносекунди
     */
    fun processGyroscope(values: FloatArray, timestamp: Long) {
        if (!initialized) return

        // Запазване на raw данни за debug
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

        // Gyro интеграция (rad/s → deg/s)
        val gyroDelta = Math.toDegrees(filteredGyroZ * dt)

        // Rate limit: максимум 15°/сек за завиване (колело/самолет не се завърта по-бързо)
        // Това предотвратява големи скокове от gyro noise или кратки glitches
        val maxRate = 15.0 // deg/s
        val effectiveGyroDelta = gyroDelta.coerceIn(-maxRate * dt, maxRate * dt)

        heading += effectiveGyroDelta
        heading %= 360.0
        if (heading < 0) heading += 360.0

        // Алгоритъм за heading: при ниска скорост компасът е авторитет (плавен преход),
        // при движение се използва complementary filter с малка тежест на компаса
        val speed = sqrt(velNorth * velNorth + velEast * velEast)
        if (hasCompassData) {
            // Плавен преход: колкото по-ниска скорост, толкова повече компас
            // compassInfluence = max(0.5, 1.0 - speed/2.0) → 1.0 при speed=0, 0.5 при speed>=1
            val compassInfluence = if (speed < 2.0) (1.0 - speed / 4.0) else 0.5
            val diff = ((compassHeading - heading + 540.0) % 360.0) - 180.0
            heading = (heading + diff * compassInfluence * COMPASS_WEIGHT * 10.0 + 360.0) % 360.0
        }
    }

    /**
     * Обработка на магнитометър данни — изчислява абсолютен compass heading
     * чрез комбинация от магнитометър и акселерометър.
     *
     * За хоризонтално устройство (екран нагоре):
     * - Phone X → forward (North), Phone Y → right (East), Phone Z → down
     *
     * Използва tilt компенсация чрез акселерометъра за по-точен heading
     * дори когато устройството не е идеално хоризонтално.
     *
     * @param values [x, y, z] magnetic field в μT
     * @param timestamp Timestamp в наносекунди
     */
    fun processMagnetometer(values: FloatArray, timestamp: Long) {
        if (!initialized) return

        // Запазване на raw данни за debug
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
     *
     * Tilt-компенсиран heading:
     * 1. Изчислява pitch и roll от акселерометъра
     * 2. Трансформира магнитния вектор в Earth frame
     * 3. heading = atan2(magEast, magNorth)
     *
     * За идеално хоризонтално устройство pitch ≈ 0, roll ≈ 0,
     * и heading ≈ atan2(-magY, magX), но tilt компенсацията
     * дава по-точен резултат при леки наклони.
     */
    private fun computeCompassHeading() {
        val magX = magForCompass[0].toDouble()
        val magY = magForCompass[1].toDouble()
        val magZ = magForCompass[2].toDouble()

        // Ако сензорът е LINEAR_ACCELERATION, данните НЕ съдържат gravity.
        // Tilt компенсацията има нужда от gravity вектора за pitch/roll.
        // Добавяме estimated gravity обратно към accel стойностите.
        val ax: Double
        val ay: Double
        val az: Double
        if (sensorIsLinearAcceleration) {
            // Добавяме gravity оценката обратно (gravity е в device frame: [0, 0, 9.81])
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

        // Pitch и roll от акселерометъра (сега включва gravity → правилни стойности)
        val pitch = asin(-ax / accelMag)
        val roll = atan2(ay, az)

        val cosPitch = cos(pitch)
        val sinPitch = sin(pitch)
        val cosRoll = cos(roll)
        val sinRoll = sin(roll)

        // Трансформация на магнитния вектор в Earth frame (North, East)
        val magNorth = magX * cosPitch + magY * sinRoll * sinPitch + magZ * cosRoll * sinPitch
        val magEast = -magY * cosRoll + magZ * sinRoll

        // Heading спрямо магнитния север (0 = North, 90 = East)
        var newCompass = atan2(magEast, magNorth)
        newCompass = Math.toDegrees(newCompass)
        newCompass = (newCompass + 360.0) % 360.0

        if (!firstCompassComputed) {
            // При първо изчисление задаваме compass heading директно
            compassHeading = newCompass
            firstCompassComputed = true
        } else {
            // Glitch rejection: ако скокът е твърде голям (>45° за 1 update), игнорираме
            // Това предотвратява големи скокове от магнитни смущения при движение
            val diff = ((newCompass - compassHeading + 540.0) % 360.0) - 180.0
            if (kotlin.math.abs(diff) > 45.0) {
                // Голям скок — вероятно магнитен glitch, игнорираме
                return
            }
            // Low-pass филтър за плавно compass heading (alpha=0.05 — по-силен филтър)
            // Използваме angle interpolation за правилно прехвърляне през 0/360
            compassHeading = (compassHeading + diff * 0.05 + 360.0) % 360.0
        }
        hasCompassData = true
    }

    /**
     * Обработка на барометър данни — височина от атмосферно налягане.
     *
     * Стандартна барометрична формула:
     * h = 44330 * (1 - (P/P0)^(1/5.255))
     * където P0 = 1013.25 hPa (налягане на морско ниво)
     *
     * Това дава абсолютна височина над морско ниво.
     *
     * @param pressure Атмосферно налягане в hPa
     * @param timestamp Timestamp в наносекунди
     */
    fun processBarometer(pressure: Float, timestamp: Long) {
        if (!initialized) return
        lastBaroTimestamp = timestamp
        hasBarometer = true

        val P0 = 1013.25f
        altitude = 44330.0 * (1.0 - (pressure.toDouble() / P0.toDouble()).pow(1.0 / 5.255))
    }

    /**
     * Връща текущата изчислена позиция.
     *
     * Конвертира относителната позиция (North/East в метри) в геодезични координати
     * чрез плоска Earth апроксимация (валидна за малки разстояния до ~100 km).
     *
     * @return DRLocation с текущата позиция, heading, скорост и оценка на точността
     */
    fun getCurrentLocation(): DRLocation {
        if (!initialized) {
            throw IllegalStateException("Калкулаторът не е инициализиран")
        }

        // Геодезична конверсия (плоска Earth за малки разстояния)
        val latRad = Math.toRadians(refLatitude)
        val deltaLat = posNorth / 111320.0
        val deltaLon = posEast / (111320.0 * cos(latRad))

        val currentLat = refLatitude + deltaLat
        val currentLon = refLongitude + deltaLon

        // Скорост от вектора на скоростта
        val speed = sqrt(velNorth * velNorth + velEast * velEast).toFloat()

        // Обновяване на оценка на точността
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
     */
    fun getDebugState(): String {
        return String.format(
            java.util.Locale.US,
            "DR_DEBUG: heading=%.2f, compassHeading=%.2f, " +
            "velNorth=%.3f, velEast=%.3f, posNorth=%.3f, posEast=%.3f, " +
            "altitude=%.1f, " +
            "accel=[%.4f,%.4f,%.4f], gyro=[%.4f,%.4f,%.4f], mag=[%.4f,%.4f,%.4f]",
            heading, compassHeading,
            velNorth, velEast, posNorth, posEast,
            altitude,
            rawAccelX, rawAccelY, rawAccelZ,
            rawGyroX, rawGyroY, rawGyroZ,
            rawMagX, rawMagY, rawMagZ
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
        posNorth = 0.0
        posEast = 0.0
        velNorth = 0.0
        velEast = 0.0
        altitude = 0.0
        velUp = 0.0
        heading = 0.0
        lastAccelTimestamp = 0
        lastGyroTimestamp = 0
        lastMagTimestamp = 0
        lastBaroTimestamp = 0
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
        estimatedAccuracy = initialAccuracy
    }

    /**
     * Дали калкулаторът е активен (инициализиран).
     */
    fun isActive(): Boolean {
        return initialized
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
