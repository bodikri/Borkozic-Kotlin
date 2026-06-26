package com.borkozic.data

import com.borkozic.util.Geo
import java.util.*

class Area {
    @JvmField var name: String = ""
    @JvmField var description: String = ""
    @JvmField var show: Boolean = false
    @JvmField var wptColor: Int = -1
    @JvmField var lineColor: Int = -1
    @JvmField var lineWidth: Int = 0
    @JvmField var fillColor: Int = -1
    @JvmField var AreaTransperency: Int = -1

    @JvmField var bottomArea: Double = 10.0
    @JvmField var topArea: Double = 1000.0
    @JvmField var AreaCenter: Waypoint? = null
    /** Radius in meters for circle areas. 0.0 = polygon area (waypoints-based) */
    @JvmField var AreaRadius: Double = 0.0
    /** Calculated area in square meters. Updated via calculateArea() */
    @JvmField var areaSize: Double = 0.0
    @JvmField var distance: Double = 0.0
    @JvmField var filepath: String? = null
    @JvmField var removed: Boolean = false
    @JvmField var editing: Boolean = false

    private var firstWaypoint: Waypoint? = null
    private var lastWaypoint: Waypoint? = null

    val waypoints: MutableList<Waypoint> = ArrayList(0)

    constructor() : this("", "", null, false, 10.0, 1000.0, 0.0)

    constructor(name: String, description: String, AreaCenter: Waypoint?, show: Boolean, bottomArea: Double, topArea: Double, areaRadius: Double = 0.0) {
        this.name = name
        this.description = description
        this.AreaCenter = AreaCenter
        this.show = show
        this.bottomArea = bottomArea
        this.topArea = topArea
        this.AreaRadius = areaRadius
    }

    /** Returns true if this is a circle area (has radius, no polygon waypoints needed) */
    fun isCircleArea(): Boolean = AreaRadius > 0.0

    /**
     * Calculate the area of this zone in square meters.
     * - Circle area: π × r²
     * - Polygon area: Shoelace formula on geographic coordinates
     *   Uses the surveyor's formula with latitude/longitude converted to
     *   local meters using equirectangular projection (valid for small areas).
     *
     * Reference: https://en.wikipedia.org/wiki/Shoelace_formula
     * For geographic coords: https://gis.stackexchange.com/questions/127607/area-of-polygon-outside-of-equirectangular-projection
     *
     * @return area in square meters
     */
    fun calculateArea(): Double {
        return if (isCircleArea()) {
            // Circle: A = π × r² (r in meters)
            Math.PI * AreaRadius * AreaRadius
        } else {
            // Polygon: Shoelace formula on geographic coordinates
            // Convert lat/lon to local meters using equirectangular projection
            if (waypoints.size < 3) {
                0.0
            } else {
                // Use the first waypoint as reference point for local projection
                val refLat = waypoints[0].latitude
                val refLon = waypoints[0].longitude
                val latPerMeter = 111320.0 // meters per degree of latitude
                val lonPerMeter = 111320.0 * Math.cos(Math.toRadians(refLat)) // meters per degree of longitude

                // Convert all waypoints to local meters (x = east, y = north)
                val xArr = DoubleArray(waypoints.size)
                val yArr = DoubleArray(waypoints.size)
                synchronized(waypoints) {
                    for (i in waypoints.indices) {
                        xArr[i] = (waypoints[i].longitude - refLon) * lonPerMeter
                        yArr[i] = (waypoints[i].latitude - refLat) * latPerMeter
                    }
                }

                // Shoelace formula: A = 0.5 * |Σ(x_i × y_{i+1} - x_{i+1} × y_i)|
                var sum = 0.0
                for (i in 0 until waypoints.size) {
                    val j = (i + 1) % waypoints.size
                    sum += xArr[i] * yArr[j] - xArr[j] * yArr[i]
                }
                Math.abs(sum) / 2.0
            }
        }
    }

    /**
     * Format the area size for display.
     * @return formatted string like "1.23 km²" or "4567 m²"
     */
    fun getFormattedAreaSize(): String {
        return if (areaSize >= 1000000.0) {
            String.format("%.2f km²", areaSize / 1000000.0)
        } else if (areaSize >= 10000.0) {
            String.format("%.1f ha", areaSize / 10000.0)
        } else {
            String.format("%.0f m²", areaSize)
        }
    }

    fun addWaypoint(waypoint: Waypoint) {
        if (lastWaypoint != null) {
            distance += Geo.distance(
                lastWaypoint!!.latitude, lastWaypoint!!.longitude,
                waypoint.latitude, waypoint.longitude,
                lastWaypoint!!.altitude, waypoint.altitude
            )
        }
        lastWaypoint = waypoint
        waypoints.add(lastWaypoint!!)
    }

    fun addWaypoint(pos: Int, waypoint: Waypoint) {
        waypoints.add(pos, waypoint)
        lastWaypoint = waypoints[waypoints.size - 1]
        distance = distanceBetween(0, waypoints.size - 1)
    }

    fun addWaypoint(name: String, lat: Double, lon: Double): Waypoint {
        val waypoint = Waypoint(name, "", lat, lon, 0.0) //Todo fixme - must find elevation at current point
        addWaypoint(waypoint)
        return waypoint
    }

    fun addWaypoint(name: String, lat: Double, lon: Double, alt: Double): Waypoint {
        val waypoint = Waypoint(name, "", lat, lon, alt)
        addWaypoint(waypoint)
        return waypoint
    }

    fun addAreaCenter(name: String, lat: Double, lon: Double, alt: Double): Waypoint {
        val waypoint = Waypoint(name, "", lat, lon, alt)
        AreaCenter = waypoint
        return this.AreaCenter!!
    }

    private fun insertWaypoint(waypoint: Waypoint) {
        if (waypoints.size < 3) {
            addWaypoint(waypoint)
            return
        }
        var after = waypoints.size - 1
        var xtk = Double.MAX_VALUE
        synchronized(waypoints) {
            for (i in 0 until waypoints.size - 1) {
                val distance = Geo.distance(
                    waypoint.latitude, waypoint.longitude,
                    waypoints[i + 1].latitude, waypoints[i + 1].longitude,
                    waypoint.altitude, waypoints[i + 1].altitude
                )
                val bearing1 = Geo.bearing(
                    waypoint.latitude, waypoint.longitude,
                    waypoints[i + 1].latitude, waypoints[i + 1].longitude
                )
                val dtk1 = Geo.bearing(
                    waypoints[i].latitude, waypoints[i].longitude,
                    waypoints[i + 1].latitude, waypoints[i + 1].longitude
                )
                val cxtk1 = Math.abs(Geo.xtk(distance, dtk1, bearing1))
                val bearing2 = Geo.bearing(
                    waypoint.latitude, waypoint.longitude,
                    waypoints[i].latitude, waypoints[i].longitude
                )
                val dtk2 = Geo.bearing(
                    waypoints[i + 1].latitude, waypoints[i + 1].longitude,
                    waypoints[i].latitude, waypoints[i].longitude
                )
                val cxtk2 = Math.abs(Geo.xtk(distance, dtk2, bearing2))

                if (cxtk2 != Double.POSITIVE_INFINITY && cxtk1 < xtk) {
                    xtk = cxtk1
                    after = i
                }
            }
        }
        waypoints.add(after + 1, waypoint)
        lastWaypoint = waypoints[waypoints.size - 1]
        distance = distanceBetween(0, waypoints.size - 1)
    }

    fun insertWaypoint(name: String, lat: Double, lon: Double): Waypoint {
        val waypoint = Waypoint(name, "", lat, lon, 0.0)
        insertWaypoint(waypoint)
        return waypoint
    }

    fun insertWaypoint(name: String, lat: Double, lon: Double, alt: Double): Waypoint {
        val waypoint = Waypoint(name, "", lat, lon, alt)
        insertWaypoint(waypoint)
        return waypoint
    }

    fun insertWaypoint(after: Int, waypoint: Waypoint) {
        waypoints.add(after + 1, waypoint)
        lastWaypoint = waypoints[waypoints.size - 1]
        distance = distanceBetween(0, waypoints.size - 1)
    }

    fun insertWaypoint(after: Int, name: String, lat: Double, lon: Double): Waypoint {
        val waypoint = Waypoint(name, "", lat, lon)
        insertWaypoint(after, waypoint)
        return waypoint
    }

    fun insertWaypoint(after: Int, name: String, lat: Double, lon: Double, alt: Double): Waypoint {
        val waypoint = Waypoint(name, "", lat, lon, alt)
        insertWaypoint(after, waypoint)
        return waypoint
    }

    fun removeWaypoint(waypoint: Waypoint) {
        waypoints.remove(waypoint)
        if (waypoints.size > 0) {
            lastWaypoint = waypoints[waypoints.size - 1]
            distance = distanceBetween(0, waypoints.size - 1)
        }
    }

    fun getWaypoint(index: Int): Waypoint {
        return waypoints[index]
    }

    fun length(): Int {
        return waypoints.size
    }

    fun clear() {
        synchronized(waypoints) {
            waypoints.clear()
        }
        lastWaypoint = null
        distance = 0.0
    }

    fun distanceBetween(first: Int, last: Int): Double {
        var dist = 0.0
        synchronized(waypoints) {
            for (i in first until last) {
                dist += Geo.distance(
                    waypoints[i].latitude, waypoints[i].longitude,
                    waypoints[i + 1].latitude, waypoints[i + 1].longitude,
                    waypoints[i].altitude, waypoints[i + 1].altitude
                )
            }
        }
        return dist
    }

    fun course(prev: Int, next: Int): Double {
        synchronized(waypoints) {
            return Geo.bearing(
                waypoints[prev].latitude, waypoints[prev].longitude,
                waypoints[next].latitude, waypoints[next].longitude
            )
        }
    }
}
