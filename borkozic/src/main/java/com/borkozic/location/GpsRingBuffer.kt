package com.borkozic.location

/**
 * Ring buffer за последните 3 GPS позиции.
 * Използва се за стабилни начални вектори при Dead Reckoning старт.
 *
 * Записва само когато fsats >= 5 (минимум 5 сателита за валиден fix).
 */
class GpsRingBuffer {

    data class GpsSnapshot(
        val lat: Double,
        val lon: Double,
        val alt: Double,
        val speed: Float,    // m/s
        val bearing: Float,  // degrees (истински heading)
        val accuracy: Float, // метри
        val timestamp: Long  // System.currentTimeMillis()
    )

    private val buffer = arrayOfNulls<GpsSnapshot>(3)
    private var count = 0

    /**
     * Добавя нова GPS позиция в буфера.
     * @param fsats брой сателити използвани във fix-а
     */
    fun add(lat: Double, lon: Double, alt: Double, speed: Float, bearing: Float,
            accuracy: Float, fsats: Int) {
        if (fsats < 5) return // само валиден fix

        val snapshot = GpsSnapshot(lat, lon, alt, speed, bearing, accuracy, System.currentTimeMillis())
        // Избутване: buffer[0] става buffer[1], buffer[1] става buffer[2], новото в buffer[0]
        buffer[2] = buffer[1]
        buffer[1] = buffer[0]
        buffer[0] = snapshot
        if (count < 3) count++
    }

    /**
     * Връща броя на наличните записи (0-3).
     */
    fun size(): Int = count

    /**
     * Връща всички налични записи (най-новият е първи).
     */
    fun getAll(): List<GpsSnapshot> = buffer.filterNotNull()

    /**
     * Връща най-новия запис или null.
     */
    fun latest(): GpsSnapshot? = buffer[0]

    /**
     * Връща усреднени стойности от наличните записи.
     * Използва се за начален вектор при DR старт.
     * Връща null ако няма записи.
     */
    fun average(): GpsSnapshot? {
        val all = getAll()
        if (all.isEmpty()) return null

        val n = all.size
        var sumLat = 0.0
        var sumLon = 0.0
        var sumAlt = 0.0
        var sumSpeed = 0f
        var sumBearing = 0f
        var sumAcc = 0f

        // Bearing усредняване с векторна математика (заради wrap-around при 0°/360°)
        var sumSin = 0.0
        var sumCos = 0.0

        for (s in all) {
            sumLat += s.lat
            sumLon += s.lon
            sumAlt += s.alt
            sumSpeed += s.speed
            sumAcc += s.accuracy
            val rad = Math.toRadians(s.bearing.toDouble())
            sumSin += Math.sin(rad)
            sumCos += Math.cos(rad)
        }

        val avgBearing = Math.toDegrees(Math.atan2(sumSin / n, sumCos / n)).toFloat()
            .let { if (it < 0) it + 360f else it }

        return GpsSnapshot(
            lat = sumLat / n,
            lon = sumLon / n,
            alt = sumAlt / n,
            speed = sumSpeed / n,
            bearing = avgBearing,
            accuracy = sumAcc / n,
            timestamp = all.last().timestamp
        )
    }

    /**
     * Изчиства буфера.
     */
    fun clear() {
        buffer[0] = null
        buffer[1] = null
        buffer[2] = null
        count = 0
    }
}
