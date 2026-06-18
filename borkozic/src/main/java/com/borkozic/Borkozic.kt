/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2013  Andrey Novikov <http://andreynovikov.info/>
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
package com.borkozic

import android.annotation.TargetApi
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Configuration
import android.content.res.Resources
import android.content.res.Resources.NotFoundException
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.hardware.GeomagneticField
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.Message
import androidx.preference.PreferenceManager
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import com.borkozic.data.Area
import com.borkozic.data.MapObject
import com.borkozic.data.Route
import com.borkozic.data.Track
import com.borkozic.data.Track.TrackPoint
import com.borkozic.data.Waypoint
import com.borkozic.data.WaypointSet
import com.borkozic.location.LocationService
import com.borkozic.map.Map
import com.borkozic.map.MapIndex
import com.borkozic.map.MockMap.Companion.getMap
import com.borkozic.map.SASMapLoader.Companion.load
import com.borkozic.map.online.OnlineMap
import com.borkozic.map.online.TileProvider
import com.borkozic.map.online.TileProvider.Companion.fromString
import com.borkozic.navigation.NavigationService
import com.borkozic.overlay.AccuracyOverlay
import com.borkozic.overlay.AreaOverlay
import com.borkozic.overlay.CurrentTrackOverlay
import com.borkozic.overlay.DistanceOverlay
import com.borkozic.overlay.LatLonGridOverlay
import com.borkozic.overlay.MapObjectsOverlay
import com.borkozic.overlay.MapOverlay
import com.borkozic.overlay.NavigationOverlay
import com.borkozic.overlay.OtherGridOverlay
import com.borkozic.overlay.RouteOverlay
import com.borkozic.overlay.ScaleOverlay
import com.borkozic.overlay.TrackOverlay
import com.borkozic.overlay.WaypointsOverlay
import com.borkozic.util.Astro
import com.borkozic.util.Astro.Zenith
import com.borkozic.util.CSV.parseLine
import com.borkozic.util.CoordinateParser.parse
import com.borkozic.util.FileUtils.Companion.sanitizeFilename
import com.borkozic.util.Geo.bearing
import com.borkozic.util.Geo.distance
import com.borkozic.util.Geo.turn
import com.borkozic.util.Geo.xtk
import com.borkozic.util.OziExplorerFiles
import com.borkozic.util.OziExplorerFiles.Companion.loadDatums
import com.borkozic.util.StringFormatter.coordinates
import com.jhlabs.map.proj.ProjectionException
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileReader
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.OutputStream
import java.util.AbstractMap
import java.util.Locale
import java.util.Stack
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.sign

class Borkozic : BaseApplication() {
    var coordinateFormat: Int = 0
    var angleType: Int = 0
    var sunriseType: Int = 0

    //public float bearingSet = 0;
    @get:JvmName("getOnlineMapsKt")
    var onlineMaps: MutableList<TileProvider>? = null
        private set
    private var onlineMap: OnlineMap? = null
    private var maps: MapIndex? = null
    private var suitableMaps: MutableList<Map>? = null
    private var coveringMaps: MutableList<Map>? = null
    var currentMap: Map? = null
        private set
    private var coveredAll = false
    private var coveringBestMap = false
    private val coveringLoc = doubleArrayOf(0.0, 0.0)
    private val coveringScreen = Rectangle()
    private val mapCenter = doubleArrayOf(0.0, 0.0)
    private val location = doubleArrayOf(Double.Companion.NaN, Double.Companion.NaN)
    @JvmField val ensureVisible: DoubleArray = doubleArrayOf(Double.Companion.NaN, Double.Companion.NaN)
    private var magneticDeclination = 0.0

    //List of objects
    private val mapObjects: AbstractMap<Long?, MapObject?> = HashMap<Long?, MapObject?>()
    val waypoints: MutableList<Waypoint> = ArrayList<Waypoint>()
    val waypointSets: MutableList<WaypointSet> = ArrayList<WaypointSet>()
    private var defWaypointSet: WaypointSet? = null
    val tracks: MutableList<Track> = ArrayList<Track>()
    val routes: MutableList<Route> = ArrayList<Route>()

    // todo - тук трябва да добавя List<Area> - но преди това трябва да създам класа
    val areas: MutableList<Area> = ArrayList<Area>()

    // Map activity state
    internal var editingRoute: Route? = null
    internal var editingTrack: Track? = null
    internal var routeEditingWaypoints: Stack<Waypoint?>? = null
    internal var routeEditingCursor: Int? = null
    internal var editingArea: Area? = null
    internal var areaEditingWaypoints: Stack<Waypoint?>? = null

    private var memmsg = false

    // FIXME Put overlays in separate class
    var llGridOverlay: LatLonGridOverlay? = null
    var grGridOverlay: OtherGridOverlay? = null
    var currentTrackOverlay: CurrentTrackOverlay? = null
    var navigationOverlay: NavigationOverlay? = null
    var mapObjectsOverlay: MapObjectsOverlay? = null
    var waypointsOverlay: WaypointsOverlay? = null
    var distanceOverlay: DistanceOverlay? = null
    var accuracyOverlay: AccuracyOverlay? = null
    var scaleOverlay: ScaleOverlay? = null
    var fileTrackOverlays: MutableList<TrackOverlay> = ArrayList<TrackOverlay>()
    var routeOverlays: MutableList<RouteOverlay> = ArrayList<RouteOverlay>()

    //todo - тук трябва да добавя List<AreaOverlay> - но преди това трябва да създам класа
    var areaOverlays: MutableList<AreaOverlay> = ArrayList<AreaOverlay>()

    private var locale: Locale? = null
    private var handler: Handler? = null
    var charset: String? = null

    var dataPath: String? = null
    override var rootPath: String? = null
    var mapPath: String? = null
        private set
    private var sasPath: String? = null
    var iconPath: String? = null
    var planePath: String? = null
    var mapsInited: Boolean = false
    var mapActivity: MapActivity? = null
    private var screenSize = 0
    var customCursor: Drawable? = null
    var iconsEnabled: Boolean = false
    var iconX: Int = 0
    var iconY: Int = 0

    var isPaid: Boolean = false

    internal var adjacentMaps: Boolean = false
    internal var cropMapBorder: Boolean = true
    internal var drawMapBorder: Boolean = false
    internal var mapGrid: Boolean = false
    internal var userGrid: Boolean = false
    internal var gridPrefer: Int = 0

    var zeroLevel: String? = null
    var zeroLeveldouble: Double = 0.0
    private val mapsHandler = Handler()

    internal fun setMapActivity(activity: MapActivity?) {
        mapActivity = activity
        for (mo in fileTrackOverlays) {
            mo.setMapContext(mapActivity!!)
        }
        if (currentTrackOverlay != null) {
            currentTrackOverlay!!.setMapContext(mapActivity!!)
        }
        for (mo in routeOverlays) {
            mo.setMapContext(mapActivity!!)
        }

        for (mo in areaOverlays) {
            mo.setMapContext(mapActivity!!)
        }
        /*todo какво трябва да направя тук?
		*/
        if (navigationOverlay != null) {
            navigationOverlay!!.setMapContext(mapActivity!!)
        }
        if (waypointsOverlay != null) {
            waypointsOverlay!!.setMapContext(mapActivity!!)
        }
        if (distanceOverlay != null) {
            distanceOverlay!!.setMapContext(mapActivity!!)
        }
        if (accuracyOverlay != null) {
            accuracyOverlay!!.setMapContext(mapActivity!!)
        }
        if (mapObjectsOverlay != null) {
            mapObjectsOverlay!!.setMapContext(mapActivity!!)
        }
        if (scaleOverlay != null) {
            scaleOverlay!!.setMapContext(mapActivity!!)
        }
        initGrids()
    }

    fun getOverlays(order: Int): MutableList<MapOverlay> {
        val overlays: MutableList<MapOverlay> = ArrayList<MapOverlay>()
        if (order == ORDER_DRAW_PREFERENCE) {
            if (llGridOverlay != null) overlays.add(llGridOverlay!!)
            if (grGridOverlay != null) overlays.add(grGridOverlay!!)
            if (accuracyOverlay != null) overlays.add(accuracyOverlay!!)
            overlays.addAll(fileTrackOverlays)
            if (currentTrackOverlay != null) overlays.add(currentTrackOverlay!!)
            overlays.addAll(routeOverlays)
            overlays.addAll(areaOverlays) // todo - eto
            if (navigationOverlay != null) overlays.add(navigationOverlay!!)
            if (waypointsOverlay != null) overlays.add(waypointsOverlay!!)
            if (scaleOverlay != null) overlays.add(scaleOverlay!!)
            if (mapObjectsOverlay != null) overlays.add(mapObjectsOverlay!!)
            if (distanceOverlay != null) overlays.add(distanceOverlay!!)
        } else {
            if (accuracyOverlay != null) overlays.add(accuracyOverlay!!)
            if (distanceOverlay != null) overlays.add(distanceOverlay!!)
            if (scaleOverlay != null) overlays.add(scaleOverlay!!)
            if (navigationOverlay != null) overlays.add(navigationOverlay!!)
            if (currentTrackOverlay != null) overlays.add(currentTrackOverlay!!)
            overlays.addAll(routeOverlays)
            overlays.addAll(areaOverlays) //todo
            if (waypointsOverlay != null) overlays.add(waypointsOverlay!!)
            overlays.addAll(fileTrackOverlays)
            if (mapObjectsOverlay != null) overlays.add(mapObjectsOverlay!!)
            if (grGridOverlay != null) overlays.add(grGridOverlay!!)
            if (llGridOverlay != null) overlays.add(llGridOverlay!!)
        }
        return overlays
    }

    fun getZenith(): Zenith {
        return when (sunriseType) {
            0 -> Zenith.OFFICIAL
            1 -> Zenith.CIVIL
            2 -> Zenith.NAUTICAL
            3 -> Zenith.ASTRONOMICAL
            else -> Zenith.OFFICIAL
        }
    }

    fun getEnsureVisible(): DoubleArray {
        return ensureVisible
    }

    fun getLocationAsLocation(): Location {
        val loc = android.location.Location("fake")
        loc.latitude = if (location[0].isNaN()) mapCenter[0] else location[0]
        loc.longitude = if (location[1].isNaN()) mapCenter[1] else location[1]
        return loc
    }

    fun getZoom(): Double {
        return currentMap?.getZoom() ?: 0.0
    }

    fun getNextZoom(): Double {
        return currentMap?.getNextZoom() ?: 0.0
    }

    fun getPrevZoom(): Double {
        return currentMap?.getPrevZoom() ?: 0.0
    }

    fun getOnlineMaps(): List<TileProvider> {
        return onlineMaps!!
    }

    fun getMapTitle(): String? {
        return currentMap?.title
    }

    private val executorThread: ExecutorService = Executors.newSingleThreadExecutor()

    internal fun notifyOverlays() {
        val overlays = getOverlays(ORDER_SHOW_PREFERENCE)
        val states = BooleanArray(overlays.size)
        var i = 0
        for (mo in overlays) {
            states[i] = mo.setEnabled(false)
            i++
        }
        executorThread.execute(object : Runnable {
            override fun run() {
                var j = 0
                for (mo in overlays) {
                    mo.onMapChanged()
                    mo.setEnabled(states[j])
                    j++
                }
            }
        })
    }

    val newUID: Long
        get() {
            val preferences = PreferenceManager.getDefaultSharedPreferences(this)
            var uid = preferences.getLong(getString(R.string.app_lastuid), 0)
            uid++
            val editor =
                PreferenceManager.getDefaultSharedPreferences(this).edit()
            editor.putLong(getString(R.string.app_lastuid), uid)
            editor.commit() //todo - трябва ли да го заменя с apply?
            return uid
        }

    fun addMapObject(mapObject: MapObject): Long {
        mapObject._id = this.newUID
        synchronized(mapObjects) {
            mapObjects.put(mapObject._id, mapObject)
        }
        return mapObject._id
    }

    fun removeMapObject(id: Long): Boolean {
        synchronized(mapObjects) {
            val mo: MapObject? = mapObjects.remove(id)
            if (mo != null && mo.bitmap != null) mo.bitmap!!.recycle()
            return mo != null
        }
    }

    /**
     * Clear all map objects.
     */
    fun clearMapObjects() {
        synchronized(mapObjects) {
            mapObjects.clear()
        }
    }

    fun getMapObject(id: Long): MapObject? {
        return mapObjects.get(id)
    }

    fun getMapObjects(): Iterable<MapObject?> {
        return mapObjects.values
    }

    fun addWaypoint(newWaypoint: Waypoint): Int {
        newWaypoint.set = defWaypointSet
        synchronized(waypoints) {
            waypoints.add(newWaypoint)
        }
        return waypoints.lastIndexOf(newWaypoint)
    }

    fun addWaypoints(newWaypoints: MutableList<Waypoint>?): Int {
        if (newWaypoints != null) {
            for (waypoint in newWaypoints) waypoint.set = defWaypointSet
            synchronized(waypoints) {
                waypoints.addAll(newWaypoints)
            }
        }
        return waypoints.size - 1
    }

    fun addWaypoints(newWaypoints: MutableList<Waypoint>?, waypointSet: WaypointSet?): Int {
        if (newWaypoints != null) {
            for (waypoint in newWaypoints) waypoint.set = waypointSet
            synchronized(waypoints) {
                waypoints.addAll(newWaypoints)
            }
            waypointSets.add(waypointSet!!)
        }
        return waypoints.size - 1
    }

    fun removeWaypoint(delWaypoint: Waypoint?): Boolean {
        synchronized(waypoints) {
            return waypoints.remove(delWaypoint)
        }
    }

    fun removeWaypoint(delWaypoint: Int) {
        synchronized(waypoints) {
            waypoints.removeAt(delWaypoint)
        }
    }

    /**
     * Clear all waypoints.
     */
    fun clearWaypoints() {
        synchronized(waypoints) {
            waypoints.clear()
        }
    }

    /**
     * Clear waypoints from specific waypoint set.
     * @param set waypoint set
     */
    fun clearWaypoints(set: WaypointSet?) {
        val iter = waypoints.iterator()
        while (iter.hasNext()) {
            val wpt = iter.next()
            if (wpt.set == set) {
                iter.remove()
            }
        }
    }

    /**
     * Clear waypoints from default waypoint set.
     */
    fun clearDefaultWaypoints() {
        clearWaypoints(defWaypointSet)
    }

    fun getWaypoint(index: Int): Waypoint? {
        return waypoints.get(index)
    }

    fun getWaypointIndex(wpt: Waypoint?): Int {
        return waypoints.indexOf(wpt)
    }

    fun getWaypointCount(set: WaypointSet?): Int {
        var n = 0
        synchronized(waypoints) {
            for (wpt in waypoints) {
                if (wpt.set == set) {
                    n++
                }
            }
        }
        return n
    }

    fun getWaypoints(set: WaypointSet?): MutableList<Waypoint?> {
        val wpts: MutableList<Waypoint?> = ArrayList<Waypoint?>()
        synchronized(waypoints) {
            for (wpt in waypoints) {
                if (wpt.set == set) {
                    wpts.add(wpt)
                }
            }
        }
        return wpts
    }

    val defaultWaypoints: MutableList<Waypoint?>
        get() = getWaypoints(defWaypointSet)

    fun hasWaypoints(): Boolean {
        return waypoints.size > 0
    }

    fun saveWaypoints(set: WaypointSet) {
        try {
            if (set.path == null) set.path =
                dataPath + File.separator + sanitizeFilename(set.name) + ".wpt"
            val file = File(set.path)
            val dir = file.getParentFile()
            if (!dir!!.exists()) dir.mkdirs()
            if (!file.exists()) file.createNewFile()
            if (file.canWrite()) OziExplorerFiles.saveWaypointsToFile(
                file,
                charset!!,
                getWaypoints(set).filterNotNull()
            )
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.err_write), Toast.LENGTH_LONG).show()
            Log.e("BORKOZIC", e.toString(), e)
        }
    }

    fun saveWaypoints() {
        for (wptset in waypointSets) {
            saveWaypoints(wptset)
        }
    }

    fun saveDefaultWaypoints() {
        saveWaypoints(defWaypointSet!!)
    }

    fun ensureVisible(waypoint: MapObject) {
        ensureVisible(waypoint.latitude, waypoint.longitude)
    }

    fun ensureVisible(lat: Double, lon: Double) {
        this.ensureVisible[0] = lat
        this.ensureVisible[1] = lon
    }

    fun hasEnsureVisible(): Boolean {
        return !java.lang.Double.isNaN(this.ensureVisible[0])
    }

    fun clearEnsureVisible() {
        this.ensureVisible[0] = Double.Companion.NaN
        this.ensureVisible[1] = Double.Companion.NaN
    }

    fun addWaypointSet(newWaypointSet: WaypointSet?): Int {
        waypointSets.add(newWaypointSet!!)
        return waypointSets.lastIndexOf(newWaypointSet)
    }

    fun removeWaypointSet(index: Int) {
        require(index != 0) { "Default waypoint set should be never removed" }
        val wptset = waypointSets.removeAt(index)
        val iter = waypoints.iterator()
        while (iter.hasNext()) {
            val wpt = iter.next()
            if (wpt.set == wptset) {
                iter.remove()
            }
        }
    }

    private fun clearWaypointSets() {
        waypointSets.clear()
    }

    fun addTrack(newTrack: Track?): Int {
        tracks.add(newTrack!!)
        return tracks.lastIndexOf(newTrack)
    }

    fun removeTrack(delTrack: Track): Boolean {
        delTrack.removed = true
        // Delete the file from disk so it doesn't reappear on restart
        val fp = delTrack.filepath
        if (fp != null) {
            val file = File(fp)
            if (file.exists()) {
                file.delete()
                Log.d(TAG, "removeTrack: deleted file $fp")
            }
        }
        return tracks.remove(delTrack)
    }

    fun clearTracks() {
        for (track in tracks) {
            track.removed = true
        }
        tracks.clear()
    }

    fun getTrack(index: Int): Track? {
        return tracks.get(index)
    }

    fun getTrackIndex(track: Track?): Int {
        return tracks.indexOf(track)
    }

    fun hasTracks(): Boolean {
        return tracks.size > 0
    }

    @Throws(IllegalArgumentException::class)
    fun trackToRoute2(track: Track, sensitivity: Float): Route {
        val route = Route()
        val points = track.points
        var tp = points.get(0)
        route.addWaypoint("RWPT", tp.latitude, tp.longitude, tp.elevation).proximity = 0

        require(points.size >= 2) { "Track too short" }

        tp = points.get(points.size - 1)
        route.addWaypoint("RWPT", tp.latitude, tp.longitude, tp.elevation).proximity =
            points.size - 1

        val prx = PreferenceManager.getDefaultSharedPreferences(this).getString(
            getString(R.string.pref_navigation_proximity),
            getString(R.string.def_navigation_proximity)
        )!!.toInt()
        val proximity = (prx * sensitivity).toDouble()
        var peaks = true
        var s = 1

        while (peaks) {
            peaks = false
            //Log.d("Borkozic", s+","+peaks);
            for (i in s downTo 1) {
                val sp = route.getWaypoint(i - 1)
                val fp = route.getWaypoint(i)
                if (fp.silent) continue
                val c = bearing(sp.latitude, sp.longitude, fp.latitude, fp.longitude)
                var xtkMin = 0.0
                var xtkMax = 0.0
                var tpMin = 0
                var tpMax = 0
                //Log.d("Borkozic", "vector: "+i+","+c);
                //Log.d("Borkozic", sp.name+"-"+fp.name+","+sp.proximity+"-"+fp.proximity);
                for (j in sp.proximity until fp.proximity) {
                    tp = points.get(j)
                    val b = bearing(tp.latitude, tp.longitude, fp.latitude, fp.longitude)
                    val d = distance(
                        tp.latitude,
                        tp.longitude,
                        fp.latitude,
                        fp.longitude,
                        tp.elevation,
                        fp.altitude
                    )
                    val xtk = xtk(d, c, b)
                    if (xtk != Double.Companion.NEGATIVE_INFINITY && xtk < xtkMin) {
                        xtkMin = xtk
                        tpMin = j
                    }
                    if (xtk != Double.Companion.NEGATIVE_INFINITY && xtk > xtkMax) {
                        xtkMax = xtk
                        tpMax = j
                    }
                }
                // mark this vector to skip it on next pass
                if (xtkMin >= -proximity && xtkMax <= proximity) {
                    fp.silent = true
                    continue
                }
                if (xtkMin < -proximity) {
                    tp = points.get(tpMin)
                    route.insertWaypoint(
                        i - 1,
                        "RWPT",
                        tp.latitude,
                        tp.longitude,
                        tp.elevation
                    ).proximity = tpMin
                    //Log.w("Borkozic", "min peak: "+s+","+tpMin+","+xtkMin);
                    s++
                    peaks = true
                }
                if (xtkMax > proximity) {
                    tp = points.get(tpMax)
                    val after = if (xtkMin < -proximity && tpMin < tpMax) i else i - 1
                    route.insertWaypoint(
                        after,
                        "RWPT",
                        tp.latitude,
                        tp.longitude,
                        tp.elevation
                    ).proximity = tpMax
                    //Log.w("Borkozic", "max peak: "+s+","+tpMax+","+xtkMax);
                    s++
                    peaks = true
                }
            }
            //Log.d("Borkozic", s+","+peaks);
            if (s > 500) peaks = false
        }
        s = 0
        for (wpt in route.waypoints) {
            wpt.name += s
            wpt.proximity = prx
            wpt.silent = false
            s++
        }
        route.name = "RT_" + track.name
        route.show = true
        return route
    }

    @Throws(IllegalArgumentException::class)
    fun trackToRoute(track: Track, sensitivity: Float): Route {
        val route = Route()
        val points = track.points
        var lrp: TrackPoint? = points.get(0)
        route.addWaypoint("RWPT0", lrp!!.latitude, lrp.longitude, lrp.elevation)

        require(points.size >= 2) { "Track too short" }

        var cp = points.get(1)
        var lp = lrp
        var tp: TrackPoint? = null
        var i = 1
        val prx = PreferenceManager.getDefaultSharedPreferences(this).getString(
            getString(R.string.pref_navigation_proximity),
            getString(R.string.def_navigation_proximity)
        )!!.toInt()
        val proximity = (prx * sensitivity).toDouble()
        var d = 0.0
        var t = 0.0
        var b: Double
        var pb = 0.0
        var cb = -1.0
        var icb = 0.0
        var xtk = 0.0

        while (i < points.size) {
            cp = points.get(i)
            d += distance(
                lp!!.latitude,
                lp.longitude,
                cp.latitude,
                cp.longitude,
                lp.elevation,
                cp.elevation
            )
            b = bearing(lp.latitude, lp.longitude, cp.latitude, cp.longitude)
            t += turn(pb, b)
            if (abs(t) >= 360) {
                t = t - 360 * sign(t)
            }
            //Log.d("Borkozic", i+","+b+","+t);
            lp = cp
            pb = b
            i++

            // calculate initial track
            if (cb < 0) {
                if (d > proximity) {
                    cb = bearing(lrp!!.latitude, lrp.longitude, cp.latitude, cp.longitude)
                    pb = cb
                    t = 0.0
                    icb = cb + 180
                    if (icb >= 360) icb -= 360.0
                    // Log.w("Borkozic", "Found vector:" + cb);
                }
                continue
            }
            // find turn
            if (abs(t) > 10) {
                if (tp == null) {
                    tp = cp
                    // Log.w("Borkozic", "Found turn: "+i);
                    continue
                }
            } else if (tp != null && xtk < proximity / 10) {
                tp = null
                xtk = 0.0
                // Log.w("Borkozic", "Reset turn: "+i);
            }
            // if turn in progress check xtk
            if (tp != null) {
                val xd = distance(
                    cp.latitude,
                    cp.longitude,
                    tp.latitude,
                    tp.longitude,
                    cp.elevation,
                    tp.elevation
                )
                val xb = bearing(cp.latitude, cp.longitude, tp.latitude, tp.longitude)
                xtk = xtk(xd, icb, xb)
                // turned at sharp angle
                if (xtk == Double.Companion.NEGATIVE_INFINITY) xtk = xtk(xd, cb, xb)
                // Log.w("Borkozic", "XTK: "+xtk);
                if (abs(xtk) > proximity * 3) {
                    lrp = tp
                    route.addWaypoint(
                        "RWPT" + route.length(),
                        lrp.latitude,
                        lrp.longitude,
                        lrp.elevation
                    )
                    cb = bearing(lrp.latitude, lrp.longitude, cp.latitude, cp.longitude)
                    // Log.e("Borkozic", "Set WPT: "+(route.length()-1)+","+cb);
                    pb = cb
                    t = 0.0
                    icb = cb + 180
                    if (icb >= 360) icb -= 360.0
                    tp = null
                    d = 0.0
                    xtk = 0.0
                }
                continue
            }
            // if still direct but pretty far away add a point
            if (d > proximity * 200) {
                lrp = cp
                route.addWaypoint(
                    "RWPT" + route.length(),
                    lrp.latitude,
                    lrp.longitude,
                    lrp.elevation
                )
                // Log.e("Borkozic", "Set WPT: "+(route.length()-1));
                d = 0.0
            }
        }
        lrp = points.get(i - 1)
        route.addWaypoint("RWPT" + route.length(), lrp.latitude, lrp.longitude, lrp.elevation)
        route.name = "RT_" + track.name
        route.show = true
        return route
    }

    fun addRoute(newRoute: Route?): Int {
        routes.add(newRoute!!)
        return routes.lastIndexOf(newRoute)
    }

    fun removeRoute(delRoute: Route): Boolean {
        delRoute.removed = true
        // Delete the file from disk so it doesn't reappear on restart
        val fp = delRoute.filepath
        if (fp != null) {
            val file = File(fp)
            if (file.exists()) {
                file.delete()
                Log.d(TAG, "removeRoute: deleted file $fp")
            }
        }
        return routes.remove(delRoute)
    }

    fun addRoutes(newRoutes: Iterable<Route?>) {
        val iterator: Iterator<Route?> = newRoutes.iterator()
        while (iterator.hasNext()) routes.add(iterator.next()!!)
    }

    fun clearRoutes() {
        for (route in routes) {
            route.removed = true
        }
        routes.clear()
    }

    fun getRoute(index: Int): Route? {
        return routes.get(index)
    }

    fun getRouteByFile(filepath: String): Route? {
        for (route in routes) {
            if (filepath == route.filepath) return route
        }
        return null
    }

    fun getRouteIndex(route: Route?): Int {
        return routes.indexOf(route)
    }

    fun hasRoutes(): Boolean {
        return routes.size > 0
    }

    /*todo*/
    fun addArea(newArea: Area?): Int {
        areas.add(newArea!!)
        return areas.lastIndexOf(newArea)
    }

    fun removeArea(delArea: Area): Boolean {
        delArea.removed = true
        // Delete the file from disk so it doesn't reappear on restart
        val fp = delArea.filepath
        if (fp != null) {
            val file = File(fp)
            if (file.exists()) {
                file.delete()
                Log.d(TAG, "removeArea: deleted file $fp")
            }
        }
        return areas.remove(delArea)
    }

    fun addAreas(newAreas: Iterable<Area?>) {
        val iterator: Iterator<Area?> = newAreas.iterator()
        while (iterator.hasNext()) areas.add(iterator.next()!!)
    }

    fun clearAreas() {
        for (area in areas) {
            area.removed = true
        }
        areas.clear()
    }

    fun getArea(index: Int): Area? {
        return areas.get(index)
    }

    fun getAreaByFile(filepath: String): Area? {
        for (area in areas) {
            if (filepath == area.filepath) return area
        }
        return null
    }

    fun getAreaIndex(area: Area?): Int {
        return areas.indexOf(area)
    }

    fun hasAreas(): Boolean {
        return areas.size > 0
    }


    val declination: Double
        get() {
            if (angleType == 0) {
                val lat =
                    (if (java.lang.Double.isNaN(location[0])) mapCenter[0] else location[0]).toFloat()
                val lon =
                    (if (java.lang.Double.isNaN(location[1])) mapCenter[1] else location[1]).toFloat()
                val mag =
                    GeomagneticField(lat, lon, 0.0f, System.currentTimeMillis())
                magneticDeclination = mag.getDeclination().toDouble()
            }
            return magneticDeclination
        }

    fun fixDeclination(declination: Double): Double {
        var declination = declination
        if (angleType == 1) {
            declination -= magneticDeclination
            declination = (declination + 360.0) % 360.0
        }
        return declination
    }

    fun getLocation(): DoubleArray {
        val res = DoubleArray(2)
        res[0] = if (java.lang.Double.isNaN(location[0])) mapCenter[0] else location[0]
        res[1] = if (java.lang.Double.isNaN(location[1])) mapCenter[1] else location[1]
        return res
    }

    @get:JvmName("getLocationAsLocationKt")
    val locationAsLocation: Location
        get() {
            val loc = Location("fake")
            loc.setLatitude(if (java.lang.Double.isNaN(location[0])) mapCenter[0] else location[0])
            loc.setLongitude(if (java.lang.Double.isNaN(location[1])) mapCenter[1] else location[1])
            return loc
        }

    fun setLocation(loc: Location, updatemag: Boolean) {
        location[0] = loc.getLatitude()
        location[1] = loc.getLongitude()
        if (updatemag && angleType == 1) {
            val mag = GeomagneticField(
                location[0].toFloat(),
                location[1].toFloat(),
                loc.getAltitude().toFloat(),
                System.currentTimeMillis()
            )
            magneticDeclination = mag.getDeclination().toDouble()
        }
    }

    fun initializeMapCenter() {
        var coordinate: DoubleArray? = null
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val loc = sharedPreferences.getString(getString(R.string.loc_last), null)
        if (loc != null) {
            coordinate = parse(loc)
            setMapCenter(coordinate[0], coordinate[1], true, true)
        }
        if (coordinate == null) {
            setMapCenter(0.0, 0.0, true, true)
        }
    }

    fun getMapCenter(): DoubleArray {
        val res = DoubleArray(2)
        res[0] = mapCenter[0]
        res[1] = mapCenter[1]
        return res
    }

    fun setMapCenter(lat: Double, lon: Double, reindex: Boolean, findbest: Boolean): Boolean {
        mapCenter[0] = lat
        mapCenter[1] = lon
        return updateLocationMaps(reindex, findbest)
    }

    /**
     * Updates available map list for current location
     * 
     * @param findbest
     * Look for better map in current location
     * @param findbest
     * @return true if current map was changed
     */
    fun updateLocationMaps(reindex: Boolean, findbest: Boolean): Boolean {
        if (maps == null) return false

        val covers = currentMap != null && currentMap!!.coversLatLon(mapCenter[0], mapCenter[1])

        if (reindex || findbest) suitableMaps = maps!!.getMaps(mapCenter[0], mapCenter[1]).toMutableList().toMutableList()

        if (covers && !findbest) return false

        var newMap: Map? = null
        if (suitableMaps!!.size > 0) {
            newMap = suitableMaps!!.get(0)
        }
        if (newMap == null) {
            newMap = getMap(mapCenter[0], mapCenter[1])
        }
        return setMap(newMap)
    }

    fun scrollMap(dx: Int, dy: Int): Boolean {
        if (currentMap != null) {
            val xy = IntArray(2)
            val ll = DoubleArray(2)

            currentMap!!.getXYByLatLon(mapCenter[0], mapCenter[1], xy)
            currentMap!!.getLatLonByXY(xy[0] + dx, xy[1] + dy, ll)

            if (ll[0] > 90.0) ll[0] = 90.0
            if (ll[0] < -90.0) ll[0] = -90.0
            if (ll[1] > 180.0) ll[1] = 180.0
            if (ll[1] < -180.0) ll[1] = -180.0

            return setMapCenter(ll[0], ll[1], false, false)
        }
        return false
    }

    fun getXYbyLatLon(lat: Double, lon: Double): IntArray {
        val xy = intArrayOf(0, 0)
        if (currentMap != null) {
            currentMap!!.getXYByLatLon(lat, lon, xy)
        }
        return xy
    }

    @get:JvmName("getZoomKt")
    val zoom: Double
        get() {
            if (currentMap != null) return currentMap!!.getZoom()
            else return 0.0
        }

    fun zoomIn(): Boolean {
        if (currentMap != null) {
            val zoom = this.nextZoom
            if (zoom > 0) {
                currentMap!!.setZoom(zoom)
                coveringMaps = null
                return true
            }
        }
        return false
    }

    fun zoomOut(): Boolean {
        if (currentMap != null) {
            val zoom = this.prevZoom
            if (zoom > 0) {
                currentMap!!.setZoom(zoom)
                coveringMaps = null
                return true
            }
        }
        return false
    }

    @get:JvmName("getNextZoomKt")
    val nextZoom: Double
        get() {
            if (currentMap != null) return currentMap!!.getNextZoom()
            else return 0.0
        }

    @get:JvmName("getPrevZoomKt")
    val prevZoom: Double
        get() {
            if (currentMap != null) return currentMap!!.getPrevZoom()
            else return 0.0
        }

    fun zoomBy(factor: Float): Boolean {
        if (currentMap != null) {
            currentMap!!.zoomBy(factor.toDouble())
            coveringMaps = null
            return true
        }
        return false
    }

    @get:JvmName("getMapTitleKt")
    val mapTitle: String?
        get() {
            if (currentMap != null) return currentMap!!.title
            else return null
        }

    fun getMaps(): MutableList<Map> {
        return maps!!.getMaps().toMutableList()
    }

    fun getMaps(loc: DoubleArray): MutableList<Map> {
        return maps!!.getMaps(loc[0], loc[1]).toMutableList()
    }

    fun nextMap(): Boolean {
        updateLocationMaps(true, false)
        var id = 0
        if (currentMap != null) {
            val pos = suitableMaps!!.indexOf(currentMap!!)
            if (pos >= 0 && pos < suitableMaps!!.size - 1) {
                id = suitableMaps!!.get(pos + 1).id
            }
        } else if (suitableMaps!!.size > 0) {
            id = suitableMaps!!.get(suitableMaps!!.size - 1).id
        }
        if (id != 0) return selectMap(id)
        else return false
    }

    fun prevMap(): Boolean {
        updateLocationMaps(true, false)
        var id = 0
        if (currentMap != null) {
            val pos = suitableMaps!!.indexOf(currentMap!!)
            if (pos > 0) {
                id = suitableMaps!!.get(pos - 1).id
            }
        } else if (suitableMaps!!.size > 0) {
            id = suitableMaps!!.get(0).id
        }
        if (id != 0) return selectMap(id)
        else return false
    }

    fun selectMap(id: Int): Boolean {
        if (currentMap != null && currentMap!!.id == id) return false

        var newMap: Map? = null
        for (map in suitableMaps!!) {
            if (map.id == id) {
                newMap = map
                break
            }
        }
        return setMap(newMap)
    }

    fun loadMap(id: Int): Boolean {
        var newMap: Map? = null
        for (map in maps!!.getMaps()) {
            if (map.id == id) {
                newMap = map
                break
            }
        }
        val newmap = setMap(newMap)
        if (currentMap != null) {
            currentMap!!.getMapCenter(mapCenter)
            suitableMaps = maps!!.getMaps(mapCenter[0], mapCenter[1]).toMutableList()
            coveringMaps = null
        }
        return newmap
    }

    internal fun initGrids() {
        llGridOverlay = null
        grGridOverlay = null
        if (mapGrid && currentMap != null && currentMap!!.llGrid != null && currentMap!!.llGrid!!.enabled && mapActivity != null) {
            val llgo = LatLonGridOverlay(mapActivity!!)
            llgo.setGrid(currentMap!!.llGrid!!)
            llGridOverlay = llgo
        }
        if (mapGrid && currentMap != null && currentMap!!.grGrid != null && currentMap!!.grGrid!!.enabled && mapActivity != null && (!userGrid || gridPrefer == 0)) {
            val ogo = OtherGridOverlay(mapActivity!!)
            ogo.setGrid(currentMap!!.grGrid!!)
            grGridOverlay = ogo
        } else if (userGrid && currentMap != null && mapActivity != null) {
            val settings = PreferenceManager.getDefaultSharedPreferences(this)
            val ogo = OtherGridOverlay(mapActivity!!)
            val grid = currentMap!!.Grid()
            grid.color1 = -0xffff01
            grid.color2 = -0xffff01
            grid.color3 = -0xffff01
            grid.enabled = true
            grid.spacing = settings.getString(
                getString(R.string.pref_grid_userscale),
                getResources().getString(R.string.def_grid_userscale)
            )!!.toInt().toDouble()
            val distanceIdx =
                settings.getString(getString(R.string.pref_grid_userunit), "0")!!.toInt()
            grid.spacing *= getResources().getStringArray(R.array.distance_factors_short)[distanceIdx].toDouble()
            grid.maxMPP = settings.getString(
                getString(R.string.pref_grid_usermpp),
                getResources().getString(R.string.def_grid_usermpp)
            )!!.toInt()
            ogo.setGrid(grid)
            grGridOverlay = ogo
        }
    }

    @Synchronized
    private fun setMap(newMap: Map?): Boolean {
        // TODO should override equals()?
        if (newMap != null && (newMap != currentMap) && mapActivity != null) {
            Log.d("BORKOZIC", "Set map: " + newMap)
            try {
                newMap.activate(mapActivity!!.map, screenSize)
            } catch (e: Throwable) {
                e.printStackTrace()
                handler!!.post(object : Runnable {
                    override fun run() {
                        Toast.makeText(
                            this@Borkozic,
                            newMap.imagePath + ": " + e.message,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                })
                return false
            }
            if (currentMap != null) {
                currentMap!!.deactivate()
            }
            coveringMaps = null
            currentMap = newMap
            initGrids()
            return true
        }
        return false
    }

    fun setOnlineMap(provider: String) {
        if (onlineMaps == null || maps == null) return
        val om = onlineMaps
        for (map in om!!) {
            if (provider == map.code) {
                val s = currentMap === onlineMap
                maps!!.removeMap(onlineMap!!)
                val zoom = PreferenceManager.getDefaultSharedPreferences(this).getInt(
                    getString(R.string.pref_onlinemapscale),
                    getResources().getInteger(R.integer.def_onlinemapscale)
                ).toByte()
                onlineMap = OnlineMap(map, zoom)
                maps!!.addMap(onlineMap!!)
                if (s) setMap(onlineMap)
            }
        }
    }

    /*
	 * ���������:
	 * Clip map to corners
	 * Draw corners
	 * Show adjacent maps
	 * Adjacent maps diff factor
	 */
    private fun updateCoveringMaps() {
        if (!mapsHandler.hasMessages(1)) {
            val m = Message.obtain(mapsHandler, object : Runnable {
                override fun run() {
                    val area = Map.Bounds()
                    val xy = IntArray(2)
                    val ll = DoubleArray(2)
                    currentMap!!.getXYByLatLon(mapCenter[0], mapCenter[1], xy)
                    currentMap!!.getLatLonByXY(
                        xy[0] + coveringScreen.left.toInt(),
                        xy[1] + coveringScreen.top.toInt(),
                        ll
                    )
                    area.maxLat = ll[0]
                    area.minLon = ll[1]
                    currentMap!!.getLatLonByXY(
                        xy[0] + coveringScreen.right.toInt(),
                        xy[1] + coveringScreen.bottom.toInt(),
                        ll
                    )
                    area.minLat = ll[0]
                    area.maxLon = ll[1]
                    val cmr: MutableList<Map> = ArrayList<Map>()
                    if (coveringMaps != null) cmr.addAll(coveringMaps!!)
                    val cma: MutableList<Map> =
                        maps!!.getCoveringMaps(currentMap!!, area, coveredAll, coveringBestMap).toMutableList()
                    val icma = cma.iterator()
                    while (icma.hasNext()) {
                        val map = icma.next()
                        try {
                            if (!map.activated()) map.activate(mapActivity!!.map, screenSize)
                            val zoom = map.mpp / currentMap!!.mpp * currentMap!!.getZoom()
                            if (zoom != map.getZoom()) map.setTemporaryZoom(zoom)
                            cmr.remove(map)
                        } catch (e: Exception) {
                            cma.remove(map)
                            e.printStackTrace()
                        }
                    }
                    synchronized(this@Borkozic) {
                        for (map in cmr) {
                            if (map !== currentMap) map.deactivate()
                        }
                        coveringMaps = cma
                    }
                }
            })
            m.what = 1
            mapsHandler.sendMessage(m)
        }
    }

    fun drawMap(
        bearing: Float,
        loc: DoubleArray,
        lookAhead: IntArray,
        bestmap: Boolean,
        width: Int,
        height: Int,
        c: Canvas
    ) {
        val cm = currentMap

        if (cm != null) {
            if (adjacentMaps) {
                val l = -(width / 2 + lookAhead[0])
                val t = -(height / 2 + lookAhead[1])
                val r = l + width
                val b = t + height
                if (coveringMaps == null || loc[0] != coveringLoc[0] || loc[1] != coveringLoc[1] || coveringBestMap != bestmap || l.toDouble() != coveringScreen.left || t.toDouble() != coveringScreen.top || r.toDouble() != coveringScreen.right || b.toDouble() != coveringScreen.bottom) {
                    coveringScreen.left = l.toDouble()
                    coveringScreen.top = t.toDouble()
                    coveringScreen.right = r.toDouble()
                    coveringScreen.bottom = b.toDouble()
                    coveringLoc[0] = loc[0]
                    coveringLoc[1] = loc[1]
                    coveringBestMap = bestmap
                    updateCoveringMaps()
                }
            }
            try {
                val cm = coveringMaps
                if (cm != null && !cm.isEmpty()) {
                    var drawn = false
                    for (map in cm) {
                        if (!drawn && coveringBestMap && map.mpp < currentMap!!.mpp) {
                            coveredAll = currentMap!!.drawMap(
                                bearing,
                                loc,
                                lookAhead,
                                width,
                                height,
                                cropMapBorder,
                                drawMapBorder,
                                c
                            )
                            drawn = true
                        }
                        map.drawMap(
                            bearing,
                            loc,
                            lookAhead,
                            width,
                            height,
                            cropMapBorder,
                            drawMapBorder,
                            c
                        )
                    }
                    if (!drawn) {
                        coveredAll = currentMap!!.drawMap(
                            bearing,
                            loc,
                            lookAhead,
                            width,
                            height,
                            cropMapBorder,
                            drawMapBorder,
                            c
                        )
                    }
                } else {
                    coveredAll = currentMap!!.drawMap(
                        bearing,
                        loc,
                        lookAhead,
                        width,
                        height,
                        cropMapBorder,
                        drawMapBorder,
                        c
                    )
                }
            } catch (err: OutOfMemoryError) {
                if (!memmsg && mapActivity != null) mapActivity!!.runOnUiThread(object : Runnable {
                    override fun run() {
                        Toast.makeText(this@Borkozic, R.string.err_nomemory, Toast.LENGTH_LONG)
                            .show()
                    }
                })
                memmsg = true
                err.printStackTrace()
            }
        }
    }

    fun clear() {
        clearRoutes()
        clearAreas()
        clearTracks()
        clearWaypoints()
        clearWaypointSets()
        clearMapObjects()
        val editor = PreferenceManager.getDefaultSharedPreferences(this).edit()
        editor.putString(
            getString(R.string.loc_last),
            coordinates(0, " ", mapCenter[0], mapCenter[1])
        )
        editor.apply() //todo трябва ли да го заменя с apply

        stopService(Intent(this, NavigationService::class.java))
        stopService(Intent(this, LocationService::class.java))

        llGridOverlay = null
        grGridOverlay = null
        mapActivity = null
        currentMap = null
        suitableMaps = null
        maps = null
        mapsInited = false
        memmsg = false
    }

    fun enableLocating(enable: Boolean) {
        Log.e(TAG, "enableLocating()")
        val action =
            if (enable) LocationService.ENABLE_LOCATIONS else LocationService.DISABLE_LOCATIONS
        startService(Intent(this, LocationService::class.java).setAction(action))
    }

    fun enableTracking(enable: Boolean) {
        val action = if (enable) LocationService.ENABLE_TRACK else LocationService.DISABLE_TRACK
        startService(Intent(this, LocationService::class.java).setAction(action))
    }

    fun setDataPath(pathtype: Int, path: String) {
        if (pathtype == PATH_DATA) dataPath = path
        if ((pathtype and PATH_ICONS) > 0) iconPath = path
        if (pathtype == PATH_PLANES) planePath = path
        if ((pathtype and PATH_SAS) > 0) sasPath = path
    }

    fun setMapPath(path: String?): Boolean {
        //String newPath = path;
        if (mapPath == null || mapPath != path) {
            mapPath = path
            if (mapsInited) {
                resetMaps()
                return true
            }
        }
        return false
    }

    fun setZeroLevelDouble(currentGPSAltitude: Double) {
        zeroLeveldouble = currentGPSAltitude
    }

    fun initializeMaps() {
        val settings = PreferenceManager.getDefaultSharedPreferences(this)
        val useIndex = settings.getBoolean(
            getString(R.string.pref_usemapindex),
            getResources().getBoolean(R.bool.def_usemapindex)
        )
        maps = null
        val index = File(rootPath, "maps.idx")
        if (useIndex && index.exists()) {
            try {
                val fs = FileInputStream(index)
                val `in` = ObjectInputStream(fs)
                maps = `in`.readObject() as MapIndex?
                `in`.close()
                val hash = MapIndex.getMapsHash(mapPath!!)
                if (hash != maps.hashCode()) {
                    maps = null
                }
            } catch (e: IOException) {
                e.printStackTrace()
            } catch (e: ClassNotFoundException) {
                e.printStackTrace()
            }
        }
        if (maps == null) {
            maps = MapIndex(mapPath!!, charset!!)
            val sb = StringBuilder()
            for (mp in maps!!.getMaps()) {
                if (mp.loadError != null) {
                    var fn = java.lang.String(mp.mappath) as String
                    if (fn.startsWith(mapPath!!)) {
                        fn = fn.substring(mapPath!!.length + 1)
                    }
                    sb.append("<b>")
                    sb.append(fn)
                    sb.append(":</b> ")
                    if (mp.loadError is ProjectionException) {
                        sb.append("projection error: ")
                    }
                    sb.append(mp.loadError!!.message)
                    sb.append("<br />\n")
                }
            }
            if (sb.length > 0) {
                maps!!.cleanBadMaps()
                startActivity(
                    Intent(
                        this,
                        ErrorDialog::class.java
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra("title", getString(R.string.badmaps))
                        .putExtra("message", sb.toString())
                )
            }

            if (useIndex) {
                try {
                    val fs = FileOutputStream(index)
                    val out = ObjectOutputStream(fs)
                    out.writeObject(maps)
                    out.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }

        // SAS maps
        val sasRoot = File(sasPath)
        val files = sasRoot.listFiles()
        if (files != null) {
            for (file in files) {
                if (file.isDirectory()) {
                    try {
                        maps!!.addMap(load(file))
                    } catch (e: IOException) {
                        // TODO Auto-generated catch block
                        e.printStackTrace()
                    }
                }
            }
        }


        // Online maps
        onlineMaps = ArrayList<TileProvider>()
        val useOnline = settings.getBoolean(
            getString(R.string.pref_useonlinemap),
            getResources().getBoolean(R.bool.def_useonlinemap)
        )
        val current: String = settings.getString(
            getString(R.string.pref_onlinemap),
            getResources().getString(R.string.def_onlinemap)
        )!!
        val zoom = settings.getInt(
            getString(R.string.pref_onlinemapscale),
            getResources().getInteger(R.integer.def_onlinemapscale)
        ).toByte()
        var curProvider: TileProvider? = null
        val om = this.getResources().getStringArray(R.array.online_maps)
        for (s in om) {
            val provider = fromString(s)
            if (provider != null) {
                onlineMaps!!.add(provider)
                if (current == provider.code) curProvider = provider
            }
        }
        val mapproviders = File(rootPath, "providers.dat")
        if (mapproviders.exists()) {
            try {
                val reader = BufferedReader(FileReader(mapproviders))
                var line: String?
                while (true) {
                    line = reader.readLine()
                    if (line == null) break
                    val trimmed = line.trim { it <= ' ' }
                    if (trimmed.startsWith("#") || trimmed.isEmpty()) continue
                    val provider = fromString(trimmed)
                    if (provider != null) {
                        onlineMaps!!.add(provider)
                        if (current == provider.code) curProvider = provider
                    }
                }
                reader.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        if (useOnline && !onlineMaps!!.isEmpty()) {
            if (curProvider == null) curProvider = onlineMaps!!.get(0)
            onlineMap = OnlineMap(curProvider, zoom)
            maps!!.addMap(onlineMap!!)
        }
        suitableMaps = maps!!.getMaps().toMutableList()
        coveredAll = true
        coveringBestMap = true
        mapsInited = true
    }

    fun resetMaps() {
        val index = File(rootPath, "maps.idx")
        if (index.exists()) index.delete()
        initializeMaps()
    }

    /**
     * Copies file assets from installation package to filesystem.
     */
    fun copyAssets(folder: String, path: File?) {
        val assetManager = getAssets()
        var files: Array<String?>? = null
        try {
            files = assetManager.list(folder)
        } catch (e: IOException) {
            //android.util.Log.e("Borkozic", "Failed to get assets list");
            Log.e("Borkozic", "Failed to get assets list", e)
            return
        }
        for (i in files!!.indices) {
            try {
                val `in` = assetManager.open(folder + "/" + files[i])
                val out: OutputStream = FileOutputStream(File(path, files[i]))
                val buffer = ByteArray(1024)
                var read: Int
                while ((`in`.read(buffer).also { read = it }) != -1) {
                    out.write(buffer, 0, read)
                }
                `in`.close()
                out.flush()
                out.close()
            } catch (e: Exception) {
                Log.e("Borkozic", "Asset copy error", e)
            }
        }
    }

    fun installData() {
        defWaypointSet = WaypointSet(dataPath + File.separator + "myWaypoints.wpt", "myWaypoints")
        waypointSets.add(defWaypointSet!!)

        val icons = File(iconPath, "icons.dat")
        if (icons.exists()) {
            try {
                val reader = BufferedReader(FileReader(icons))
                val fields: Array<String> = parseLine(reader.readLine())
                if (fields.size == 3) {
                    iconsEnabled = true
                    iconX = fields[0]!!.toInt()
                    iconY = fields[1]!!.toInt()
                }
                reader.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }


        val datums = File(rootPath, "datums.dat")
        if (datums.exists()) {
            try {
                loadDatums(datums)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        val cursor = File(planePath, "plane_logo.png")
        if (cursor.exists()) {
            try {
                customCursor = BitmapDrawable(getResources(), cursor.getAbsolutePath())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        //installRawResource(R.raw.datums, "datums.xml");
    }

    fun installRawResource(id: Int, path: String?) {
        try {
            // TODO Needs versioning
            openFileInput(path).close()
        } catch (e: Exception) {
        } finally {
            val `in` = getResources().openRawResource(id)
            var out: FileOutputStream? = null

            try {
                out = openFileOutput(path, MODE_PRIVATE)

                val size = `in`.available()

                val buffer = ByteArray(size)
                `in`.read(buffer)
                `in`.close()

                out.write(buffer)
                out.close()
            } catch (ex: Exception) {
            }
        }
    }

    fun getDependVeDrawable(id: Int, res: Resources): Drawable? //моя метод
    {
        //ContextCompat.getDrawable(context, R.drawable.id)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return res.getDrawable(id, null)
        } else {
            return res.getDrawable(id)
        }
    }

    @Suppress("deprecation")
    fun getSystemLocaleLegacy(config: Configuration): Locale? {
        return config.locale
    }

    @TargetApi(Build.VERSION_CODES.N)
    fun getSystemLocale(config: Configuration): Locale? {
        return config.getLocales().get(0)
    }

    @Suppress("deprecation")
    fun setSystemLocaleLegacy(config: Configuration, locale: Locale?) {
        config.locale = locale
    }

    @TargetApi(Build.VERSION_CODES.N)
    fun setSystemLocale(config: Configuration, locale: Locale?) {
        config.setLocale(locale)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (locale != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                setSystemLocale(newConfig, locale)
            } else {
                setSystemLocaleLegacy(newConfig, locale)
            }
            //newConfig.locale = locale; - заменено с горното If
            Locale.setDefault(locale)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                getBaseContext().createConfigurationContext(newConfig)
            } else {
                getBaseContext().getResources().updateConfiguration(
                    newConfig,
                    getBaseContext().getResources().getDisplayMetrics()
                )
            }
            //getBaseContext().getResources().updateConfiguration(newConfig, getBaseContext().getResources().getDisplayMetrics()); - заменено с горното If
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.e(TAG, "App onCreate()")
        setInstance(this)
        handler = Handler()

        val intentToCheck = "com.borkozic.donate"
        val myPackageName = getPackageName()
        val pm = getPackageManager()
        val pi: PackageInfo
        try {
            pi = pm.getPackageInfo(intentToCheck, 0)
            isPaid = (pm.checkSignatures(
                myPackageName,
                pi.packageName
            ) == PackageManager.SIGNATURE_MATCH)
        } catch (e: PackageManager.NameNotFoundException) {
        }

        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this, cacheDir.absolutePath))

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager?
        if (wm != null) {
            val metrics = DisplayMetrics()
            wm.getDefaultDisplay().getMetrics(metrics)
            screenSize = metrics.widthPixels * metrics.heightPixels
        } else {
            screenSize = 320 * 480
        }

        val settings = PreferenceManager.getDefaultSharedPreferences(this)
        val config = getBaseContext().getResources().getConfiguration()

        charset = settings.getString(getString(R.string.pref_charset), "UTF-8")
        val lang: String = settings.getString(getString(R.string.pref_locale), "")!!
        if ("" != lang && config.locale.getLanguage() != lang) {
            locale = Locale(lang)
            Locale.setDefault(locale)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                setSystemLocale(config, locale)
            } else {
                setSystemLocaleLegacy(config, locale)
            }
            //config.locale = locale; - заменено от горния иф
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                getBaseContext().createConfigurationContext(config)
            } else {
                getBaseContext().getResources().updateConfiguration(
                    config,
                    getBaseContext().getResources().getDisplayMetrics()
                )
            }
            //getBaseContext().getResources().updateConfiguration(config, getBaseContext().getResources().getDisplayMetrics()); - заменено от горния иф
        }
    }

    companion object {
        const val PATH_DATA: Int = 0x001
        const val PATH_SAS: Int = 0x002
        const val PATH_ICONS: Int = 0x008
        const val PATH_PLANES: Int = 0x009
        const val ORDER_SHOW_PREFERENCE: Int = 0
        const val ORDER_DRAW_PREFERENCE: Int = 1
        private const val TAG = "Borkozic"
    }
}
