package com.borkozic.overlay

import android.app.Activity
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.util.Log
import com.borkozic.Borkozic
import com.borkozic.MapView
import com.borkozic.R
import com.borkozic.map.Map
import com.borkozic.util.Geo
import java.util.ArrayList

class OtherGridOverlay(activity: Activity) : MapOverlay(activity) {

    private val paths = ArrayList<Path>()
    private val linePaint: Paint = Paint()
    private var clip: Rect? = null
    var spacing = 100000
    var maxMPP = 0

    init {
        linePaint.isAntiAlias = true
        linePaint.strokeWidth = 1f
        linePaint.style = Paint.Style.STROKE
        linePaint.color = context.resources.getColor(R.color.distanceline)
    }

    fun setGrid(grid: Map.Grid) {
        spacing = grid.spacing.toInt()
        maxMPP = grid.maxMPP
        linePaint.color = if (grid.spacing >= 1000) grid.color1 else grid.color2
        enabled = true
    }

    @Synchronized
    override fun onMapChanged() {
        paths.clear()
        val application = context.application as Borkozic
        val map = application.currentMap ?: return

        Log.e("GRID", "mpp: $maxMPP ${map.mpp / map.zoom}")
        if (maxMPP > 0 && maxMPP < (map.mpp / map.zoom).toInt())
            return

        clip = Rect(0, 0, map.scaledWidth, map.scaledHeight)

        val points = ArrayList<IntArray>()
        val refPoints = ArrayList<DoubleArray>()

        // find map center coordinates
        val cll = DoubleArray(2)
        val cxy = intArrayOf(map.scaledWidth / 2, map.scaledHeight / 2)
        map.getLatLonByXY(cxy[0], cxy[1], cll)

        // build vertical reference path
        var y = cxy[1]
        var ll = doubleArrayOf(cll[0], cll[1])
        refPoints.add(doubleArrayOf(cll[0], cll[1]))
        points.add(cxy)
        while (y < map.scaledHeight) {
            val pxy = IntArray(2)
            val pll = Geo.projection(ll[0], ll[1], (spacing * 3).toDouble(), 180.0)
            map.getXYByLatLon(pll[0], pll[1], pxy)
            refPoints.add(pll)
            points.add(pxy)
            ll[0] = pll[0]
            ll[1] = pll[1]
            y = pxy[1]
        }
        ll = doubleArrayOf(cll[0], cll[1])
        while (y > 0) {
            val pxy = IntArray(2)
            val pll = Geo.projection(ll[0], ll[1], (spacing * 3).toDouble(), 0.0)
            map.getXYByLatLon(pll[0], pll[1], pxy)
            refPoints.add(0, pll)
            points.add(0, pxy)
            ll[0] = pll[0]
            ll[1] = pll[1]
            y = pxy[1]
        }
        var path = buildPath(points)
        paths.add(path)

        // build vertical paths
        var i = 1
        var onmap = true
        while (onmap) {
            points.clear()
            onmap = false
            for (pll in refPoints) {
                val pxy = IntArray(2)
                val dll = Geo.projection(pll[0], pll[1], (spacing * i).toDouble(), 90.0)
                map.getXYByLatLon(dll[0], dll[1], pxy)
                points.add(pxy)
                onmap = onmap || pxy[0] <= map.scaledWidth
            }
            path = buildPath(points)
            paths.add(path)
            i++
        }
        i = 1
        onmap = true
        while (onmap) {
            points.clear()
            onmap = false
            for (pll in refPoints) {
                val pxy = IntArray(2)
                val dll = Geo.projection(pll[0], pll[1], (spacing * i).toDouble(), 270.0)
                map.getXYByLatLon(dll[0], dll[1], pxy)
                points.add(pxy)
                onmap = onmap || pxy[0] >= 0
            }
            path = buildPath(points)
            paths.add(path)
            i++
        }

        // build horizontal reference path
        refPoints.clear()
        points.clear()
        var x = cxy[0]
        ll = doubleArrayOf(cll[0], cll[1])
        refPoints.add(doubleArrayOf(cll[0], cll[1]))
        points.add(cxy)
        while (x < map.scaledWidth) {
            val pxy = IntArray(2)
            val pll = Geo.projection(ll[0], ll[1], (spacing * 3).toDouble(), 90.0)
            map.getXYByLatLon(pll[0], pll[1], pxy)
            refPoints.add(pll)
            points.add(pxy)
            ll[0] = pll[0]
            ll[1] = pll[1]
            x = pxy[0]
        }
        ll = doubleArrayOf(cll[0], cll[1])
        while (x > 0) {
            val pxy = IntArray(2)
            val pll = Geo.projection(ll[0], ll[1], (spacing * 3).toDouble(), 270.0)
            map.getXYByLatLon(pll[0], pll[1], pxy)
            refPoints.add(0, pll)
            points.add(0, pxy)
            ll[0] = pll[0]
            ll[1] = pll[1]
            x = pxy[0]
        }
        path = buildPath(points)
        paths.add(path)

        // build horizontal paths
        i = 1
        onmap = true
        while (onmap) {
            points.clear()
            onmap = false
            for (pll in refPoints) {
                val pxy = IntArray(2)
                val dll = Geo.projection(pll[0], pll[1], (spacing * i).toDouble(), 180.0)
                map.getXYByLatLon(dll[0], dll[1], pxy)
                points.add(pxy)
                onmap = onmap || pxy[1] <= map.scaledHeight
            }
            path = buildPath(points)
            paths.add(path)
            i++
        }
        i = 1
        onmap = true
        while (onmap) {
            points.clear()
            onmap = false
            for (pll in refPoints) {
                val pxy = IntArray(2)
                val dll = Geo.projection(pll[0], pll[1], (spacing * i).toDouble(), 0.0)
                map.getXYByLatLon(dll[0], dll[1], pxy)
                points.add(pxy)
                onmap = onmap || pxy[1] >= 0
            }
            path = buildPath(points)
            paths.add(path)
            i++
        }
    }

    private fun buildPath(points: ArrayList<IntArray>): Path {
        val path = Path()
        for (p in points) {
            if (path.isEmpty) {
                path.moveTo(p[0].toFloat(), p[1].toFloat())
                path.lineTo(p[0].toFloat(), p[1].toFloat())
            } else {
                path.lineTo(p[0].toFloat(), p[1].toFloat())
            }
        }
        return path
    }

    override fun onPreferencesChanged(settings: SharedPreferences) {
    }

    @Synchronized
    override fun onDraw(c: Canvas, mapView: MapView, centerX: Int, centerY: Int) {
        c.save()
        c.translate((-mapView.mapCenterXY[0]).toFloat(), (-mapView.mapCenterXY[1]).toFloat())
        clip?.let { c.clipRect(it) }
        for (path in paths) {
            c.drawPath(path, linePaint)
        }
        c.restore()
    }

    override fun onDrawFinished(c: Canvas, mapView: MapView, centerX: Int, centerY: Int) {
    }
}
