package com.borkozic.data

import com.borkozic.util.Geo
import java.util.*

class Track {
    @JvmField var name: String = ""
    @JvmField var description: String = ""
    @JvmField var show: Boolean = false
    @JvmField var color: Int = -1
    @JvmField var width: Int = 0

    @JvmField var maxPoints: Long = 0
    @JvmField var distance: Double = 0.0
    @JvmField var filepath: String? = null
    @JvmField var removed: Boolean = false
    @JvmField var editing: Boolean = false
    @JvmField var editingPos: Int = -1

    val points: MutableList<TrackPoint> = ArrayList(0)
    private var lastTrackPoint: TrackPoint? = null

    inner class TrackPoint {
        @JvmField var continous: Boolean = false
        @JvmField var latitude: Double = 0.0
        @JvmField var longitude: Double = 0.0
        @JvmField var elevation: Double = 0.0
        @JvmField var speed: Double = 0.0
        @JvmField var bearing: Double = 0.0
        @JvmField var accuracy: Double = Double.MAX_VALUE
        @JvmField var time: Long = 0
        // Map position cache fields
        @JvmField var dirty: Boolean = true
        @JvmField var x: Int = 0
        @JvmField var y: Int = 0

        constructor() {
            continous = false
            latitude = 0.0
            longitude = 0.0
            elevation = 0.0
            speed = 0.0
            bearing = 0.0
            accuracy = Double.MAX_VALUE
            time = 0
        }

        constructor(cont: Boolean, lat: Double, lon: Double, elev: Double, spd: Double, brn: Double, acc: Double, t: Long) {
            continous = cont
            latitude = lat
            longitude = lon
            elevation = elev
            speed = spd
            bearing = brn
            accuracy = acc
            time = t
        }
    }

    constructor() : this("", "", false)

    constructor(pname: String, pdescr: String, pshow: Boolean) {
        name = pname
        description = pdescr
        show = pshow
        distance = 0.0
    }

    constructor(pname: String, pdescr: String, pshow: Boolean, max: Long) : this(pname, pdescr, pshow) {
        maxPoints = max
    }

    fun addPoint(continous: Boolean, lat: Double, lon: Double, elev: Double, speed: Double, bearing: Double, accuracy: Double, time: Long) {
        if (lastTrackPoint != null) {
            distance += Geo.distance(
                lastTrackPoint!!.latitude, lastTrackPoint!!.longitude, lat, lon,
                lastTrackPoint!!.elevation, elev
            )
        }
        lastTrackPoint = TrackPoint(continous, lat, lon, elev, speed, bearing, accuracy, time)
        synchronized(points) {
            if (maxPoints > 0 && points.size > maxPoints) {
                // TODO add correct cleaning if preferences changed
                val fp = points[0]
                val sp = points[1]
                distance -= Geo.distance(
                    fp.latitude, fp.longitude, sp.latitude, sp.longitude,
                    fp.elevation, sp.elevation
                )
                points.removeAt(0)
            }
            points.add(lastTrackPoint!!)
        }
    }

    fun clear() {
        synchronized(points) {
            points.clear()
        }
        lastTrackPoint = null
        distance = 0.0
    }

    fun getPoint(location: Int): TrackPoint {
        return points[location]
    }

    fun getLastPoint(): TrackPoint? {
        return lastTrackPoint
    }

    fun removePoint(location: Int) {
        synchronized(points) {
            val last = location == points.size - 1
            val pp = points[location - 1]
            val cp = points[location]
            distance -= Geo.distance(
                pp.latitude, pp.longitude, cp.latitude, cp.longitude,
                pp.elevation, cp.elevation
            )
            if (!last) {
                val np = points[location + 1]
                distance -= Geo.distance(
                    cp.latitude, cp.longitude, np.latitude, np.longitude,
                    cp.elevation, np.elevation
                )
                distance += Geo.distance(
                    pp.latitude, pp.longitude, np.latitude, np.longitude,
                    pp.elevation, np.elevation
                )
            }
            points.removeAt(location)
            if (last)
                lastTrackPoint = pp
        }
    }

    fun cutAfter(location: Int) {
        synchronized(points) {
            val tps = ArrayList(points.subList(0, location + 1))
            points.clear()
            points.addAll(tps)
            if (points.size > 0)
                lastTrackPoint = points[points.size - 1]
            else
                lastTrackPoint = null
        }
    }

    fun cutBefore(location: Int) {
        synchronized(points) {
            val tps = ArrayList(points.subList(location, points.size))
            points.clear()
            points.addAll(tps)
        }
    }
}
