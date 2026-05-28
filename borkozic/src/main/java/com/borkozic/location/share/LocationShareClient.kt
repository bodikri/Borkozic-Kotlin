package com.borkozic.location.share

import com.borkozic.data.Situation
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Abstract interface for location sharing data providers.
 * Allows swapping between different backends (e.g. original Androzic server,
 * FlightRadar24, bulatsa.com/bflip, etc.) without changing SharingService.
 */
interface LocationShareClient {
    /**
     * Share own location and fetch other users' locations.
     *
     * @param session Session identifier
     * @param user User identifier within the session
     * @param lat Current latitude
     * @param lon Current longitude
     * @param speed Current speed in m/s
     * @param track Current track/bearing in degrees
     * @param ftime Location fix time (epoch millis)
     * @param altitude Current altitude in meters
     * @param updateInterval Update interval in milliseconds
     * @param timeoutInterval Timeout interval in milliseconds
     * @return List of other users' situations, or empty list if no updates
     */
    fun shareAndFetch(
        session: String,
        user: String,
        lat: Double,
        lon: Double,
        speed: Float,
        track: Float,
        ftime: Long,
        altitude: Double,
        updateInterval: Int,
        timeoutInterval: Long
    ): List<Situation>
}

/**
 * Reference implementation of the original Androzic location sharing backend.
 * Uses HttpURLConnection (no extra dependencies). Server is currently offline.
 *
 * Protocol: GET http://androzic.com/cgi-bin/loc.cgi?session=...;user=...;lat=...;lon=...
 * Response: JSON with users array
 */
class AndrozicLocationShareClient : LocationShareClient {

    private val serverUrl = "http://androzic.com/cgi-bin/loc.cgi"

    override fun shareAndFetch(
        session: String,
        user: String,
        lat: Double,
        lon: Double,
        speed: Float,
        track: Float,
        ftime: Long,
        altitude: Double,
        updateInterval: Int,
        timeoutInterval: Long
    ): List<Situation> {
        val situations = mutableListOf<Situation>()

        try {
            val query = buildQuery(session, user, lat, lon, speed, track, ftime, altitude)
            val url = URL("$serverUrl?$query")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = updateInterval / 2
            conn.readTimeout = updateInterval / 2
            conn.useCaches = false

            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val entries = json.optJSONArray("users") ?: JSONArray()

                for (i in 0 until entries.length()) {
                    val obj = entries.getJSONObject(i)
                    val name = obj.optString("user", "")
                    if (name == user) continue

                    val s = Situation()
                    s.name = name
                    s.latitude = obj.optDouble("lat", 0.0)
                    s.longitude = obj.optDouble("lon", 0.0)
                    s.speed = obj.optDouble("speed", 0.0)
                    s.track = obj.optDouble("track", 0.0)
                    s.altitude = obj.optDouble("elevation", 0.0)
                    s.time = obj.optLong("ftime", 0)
                    s.id = obj.optLong("id", 0)
                    situations.add(s)
                }
            }
            conn.disconnect()
        } catch (e: Exception) {
            // Server offline or network error — silently ignore
        }

        return situations
    }

    private fun buildQuery(
        session: String,
        user: String,
        lat: Double,
        lon: Double,
        speed: Float,
        track: Float,
        ftime: Long,
        altitude: Double
    ): String {
        return "session=" + URLEncoder.encode(session, "UTF-8") +
                ";user=" + URLEncoder.encode(user, "UTF-8") +
                ";lat=" + lat +
                ";lon=" + lon +
                ";track=" + track +
                ";speed=" + speed +
                ";ftime=" + ftime +
                ";altitude=" + altitude
    }
}
