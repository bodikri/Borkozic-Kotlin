/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2012  Andrey Novikov <http://andreynovikov.info/>
 *
 * This file is part of Androzic application.
 *
 * Androzic is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * Androzic is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with Androzic.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.borkozic.data

import com.borkozic.util.Geo
import java.util.*

class Route {
    @JvmField var name: String = ""
    @JvmField var description: String = ""
    @JvmField var show: Boolean = false
    @JvmField var wptColor: Int = -1
    @JvmField var lineColor: Int = -1
    @JvmField var width: Int = 0
    
    @JvmField var distance: Double = 0.0
    @JvmField var filepath: String? = null
    @JvmField var removed: Boolean = false
    @JvmField var editing: Boolean = false

    val waypoints: MutableList<Waypoint> = ArrayList()
    private var lastWaypoint: Waypoint? = null
    
    constructor() : this("", "", false)

    constructor(name: String, description: String, show: Boolean) {
        this.name = name
        this.description = description
        this.show = show
        distance = 0.0
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

    /** Add a waypoint at a specific position in the route. */
    fun addWaypointAt(pos: Int, name: String, lat: Double, lon: Double, alt: Double): Waypoint {
        val waypoint = Waypoint(name, "", lat, lon, alt)
        addWaypoint(pos, waypoint)
        return waypoint
    }

    private fun insertWaypoint(waypoint: Waypoint) {
        if (waypoints.size < 2) {
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

    /**
     * Moves a waypoint from [fromIndex] to [toIndex] and recalculates total distance.
     * Returns false if the move would place the same waypoint after itself
     * (consecutive duplicate prevention).
     */
    fun moveWaypoint(fromIndex: Int, toIndex: Int): Boolean {
        val size = waypoints.size
        if (fromIndex < 0 || fromIndex >= size || toIndex < 0 || toIndex >= size) return false
        if (fromIndex == toIndex) return true

        // Prevent moving a waypoint right after itself
        if (toIndex == fromIndex + 1) return false

        val wpt = waypoints.removeAt(fromIndex)
        // adjust toIndex if we removed before it
        val insertAt = if (toIndex > fromIndex) toIndex - 1 else toIndex

        // Check consecutive duplicate: wpt at insertAt (the one before insertion point) must differ from wpt
        if (insertAt >= 0 && waypoints[insertAt] === wpt) return false
        // Also check the one after insertion point
        if (insertAt + 1 < waypoints.size && waypoints[insertAt + 1] === wpt) return false

        waypoints.add(insertAt + 1, wpt)
        lastWaypoint = waypoints[waypoints.size - 1]
        distance = distanceBetween(0, waypoints.size - 1)
        return true
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
