package com.borkozic.overlay

import android.app.Activity
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import com.borkozic.Borkozic
import com.borkozic.MapView
import com.borkozic.R
import com.borkozic.map.Map

class LatLonGridOverlay(activity: Activity) : MapOverlay(activity) {

    private val linePaint: Paint = Paint()
    private val grid: ArrayList<Array<IntArray>> = ArrayList()
    private var clip: Rect? = null
    private var spacing = 0.0

    init {
        linePaint.isAntiAlias = true
        linePaint.strokeWidth = 1f
        linePaint.style = Paint.Style.STROKE
        linePaint.color = context.resources.getColor(R.color.distanceline)
    }

    fun setGrid(grid: Map.Grid) {
        spacing = grid.spacing
        linePaint.color = when {
            grid.spacing >= 1 -> grid.color1
            grid.spacing >= 0.0166666666666667 -> grid.color2
            else -> grid.color3
        }
        enabled = true
    }

    @Synchronized
    override fun onMapChanged() {
        grid.clear()
        val application = context.application as Borkozic
        val map = application.currentMap ?: return
        clip = Rect(0, 0, map.scaledWidth, map.scaledHeight)
        val bounds = map.bounds

        var lat = bounds.minLat - (bounds.minLat % spacing)
        var lon = bounds.minLon - (bounds.minLon % spacing)
        val mlat = (bounds.minLat + bounds.maxLat) / 2
        val mlon = (bounds.minLon + bounds.maxLon) / 2

        val xy = IntArray(2)
        while (lon <= bounds.maxLon) {
            val curve = Array(3) { IntArray(2) }
            map.getXYByLatLon(bounds.minLat, lon, xy)
            curve[0][0] = xy[0]
            curve[0][1] = xy[1]
            map.getXYByLatLon(mlat, lon, xy)
            curve[1][0] = xy[0]
            curve[1][1] = xy[1]
            map.getXYByLatLon(bounds.maxLat, lon, xy)
            curve[2][0] = xy[0]
            curve[2][1] = xy[1]
            val cp = interpolate(
                curve[0][0], curve[0][1], curve[1][0], curve[1][1],
                curve[2][0], curve[2][1], 0.5
            )
            curve[1][0] = cp[0]
            curve[1][1] = cp[1]
            grid.add(curve)
            lon += spacing
            if (lon >= 180) lon -= 180
        }
        while (lat <= bounds.maxLat) {
            val curve = Array(3) { IntArray(2) }
            map.getXYByLatLon(lat, bounds.minLon, xy)
            curve[0][0] = xy[0]
            curve[0][1] = xy[1]
            map.getXYByLatLon(lat, mlon, xy)
            curve[1][0] = xy[0]
            curve[1][1] = xy[1]
            map.getXYByLatLon(lat, bounds.maxLon, xy)
            curve[2][0] = xy[0]
            curve[2][1] = xy[1]
            val cp = interpolate(
                curve[0][0], curve[0][1], curve[1][0], curve[1][1],
                curve[2][0], curve[2][1], 0.5
            )
            curve[1][0] = cp[0]
            curve[1][1] = cp[1]
            grid.add(curve)
            lat += spacing
        }
    }

    @Synchronized
    override fun onDraw(c: Canvas, mapView: MapView, centerX: Int, centerY: Int) {
        c.save()
        c.translate((-mapView.mapCenterXY[0]).toFloat(), (-mapView.mapCenterXY[1]).toFloat())
        clip?.let { c.clipRect(it) }
        for (curve in grid) {
            val p = Path()
            p.moveTo(curve[0][0].toFloat(), curve[0][1].toFloat())
            p.quadTo(
                curve[1][0].toFloat(), curve[1][1].toFloat(),
                curve[2][0].toFloat(), curve[2][1].toFloat()
            )
            c.drawPath(p, linePaint)
        }
        c.restore()
    }

    override fun onDrawFinished(c: Canvas, mapView: MapView, centerX: Int, centerY: Int) {
    }

    override fun onPreferencesChanged(settings: SharedPreferences) {
    }

    // interpolate three points with second point at specified parameter value
    protected fun interpolate(
        x0: Int, y0: Int, x1: Int, y1: Int,
        x2: Int, y2: Int, t: Double
    ): IntArray {
        val t1 = 1.0 - t
        val tSq = t * t
        val denom = 2.0 * t * t1

        val cx = ((x1 - t1 * t1 * x0 - tSq * x2) / denom).toInt()
        val cy = ((y1 - t1 * t1 * y0 - tSq * y2) / denom).toInt()

        return intArrayOf(cx, cy)
    }
}
