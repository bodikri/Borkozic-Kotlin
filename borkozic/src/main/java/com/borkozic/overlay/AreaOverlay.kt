package com.borkozic.overlay

import android.app.Activity
import android.content.SharedPreferences
import android.graphics.*
import android.preference.PreferenceManager
import android.view.MotionEvent
import androidx.core.content.ContextCompat
import com.borkozic.Borkozic
import com.borkozic.MapActivity
import com.borkozic.MapView
import com.borkozic.R
import com.borkozic.data.Area
import com.borkozic.data.Waypoint
import java.util.*

class AreaOverlay(mapActivity: Activity) : MapOverlay(mapActivity) {

    private lateinit var areaLinePaint: Paint
    private lateinit var areaFillPaint: Paint
    private lateinit var borderPaint: Paint
    private lateinit var fillPaint: Paint
    private lateinit var textPaint: Paint
    private lateinit var textFillPaint: Paint
    var area: Area = Area()
    var bitmaps: WeakHashMap<Waypoint, Bitmap> = WeakHashMap()

    private var pointWidth = 10
    private var areaWidth = 3
    private var areaInWidth = 1
    private var showNames = false

    init {
        initPaints()
        onPreferencesChanged(PreferenceManager.getDefaultSharedPreferences(context))
        enabled = true
    }

    constructor(mapActivity: Activity, aArea: Area) : this(mapActivity) {
        area = aArea
        if (area.lineColor == -1)
            area.lineColor = areaLinePaint.color
        if (area.fillColor == -1)
            area.fillColor = areaFillPaint.color
        onAreaPropertiesChanged()
    }

    private fun initPaints() {
        areaLinePaint = Paint().apply {
            isAntiAlias = true
            strokeWidth = areaWidth.toFloat()
            style = Paint.Style.STROKE
            color = ContextCompat.getColor(context, R.color.arealinecolor)
        }
        areaFillPaint = Paint().apply {
            isAntiAlias = false
            strokeWidth = areaInWidth.toFloat()
            style = Paint.Style.FILL_AND_STROKE
            color = ContextCompat.getColor(context, R.color.areacolor)
        }
        fillPaint = Paint().apply {
            isAntiAlias = false
            strokeWidth = 1f
            style = Paint.Style.FILL_AND_STROKE
            color = ContextCompat.getColor(context, R.color.areawaypoint)
        }
        borderPaint = Paint().apply {
            isAntiAlias = false
            strokeWidth = 1f
            style = Paint.Style.STROKE
            color = ContextCompat.getColor(context, R.color.arealinecolor)
        }
        textPaint = Paint().apply {
            isAntiAlias = true
            strokeWidth = 2f
            style = Paint.Style.FILL
            textAlign = Paint.Align.LEFT
            textSize = pointWidth * 1.5f
            typeface = Typeface.SANS_SERIF
            color = ContextCompat.getColor(context, R.color.areawaypointtext)
        }
        textFillPaint = Paint().apply {
            isAntiAlias = false
            strokeWidth = 1f
            style = Paint.Style.FILL_AND_STROKE
            color = ContextCompat.getColor(context, R.color.arealinecolor)
        }
    }

    private fun initAreaColors() {
        areaLinePaint.color = area.lineColor
        areaLinePaint.alpha = 0xAA
        areaFillPaint.color = area.fillColor
        areaFillPaint.alpha = area.AreaTransperency
        borderPaint.color = area.lineColor
        textFillPaint.color = area.lineColor
        textFillPaint.alpha = 0x88
        val y = getLuminance(area.lineColor)
        textPaint.color = if (y <= .5) Color.WHITE else Color.BLACK
    }

    private fun adjustValue(cc: Int): Double {
        var value = cc.toDouble() / 255.0
        value = if (value <= 0.03928) value / 12.92 else Math.pow((value + 0.055) / 1.055, 2.4)
        return value
    }

    private fun getLuminance(rgb: Int): Double {
        val r = (rgb and 0x00FF0000) ushr 16
        val g = (rgb and 0x0000FF00) ushr 8
        val b = rgb and 0x000000FF
        return 0.2126 * adjustValue(r) + 0.7152 * adjustValue(g) + 0.0722 * adjustValue(b)
    }

    fun onAreaPropertiesChanged() {
        if (areaLinePaint.color != area.lineColor) {
            initAreaColors()
        }
        if (areaFillPaint.color != area.fillColor) {
            initAreaColors()
        }
        if (area.editing) {
            areaLinePaint.pathEffect = DashPathEffect(floatArrayOf(5f, 2f), 0f)
            areaLinePaint.strokeWidth = (areaWidth * 3).toFloat()
        } else {
            areaLinePaint.pathEffect = null
            areaLinePaint.strokeWidth = areaWidth.toFloat()
        }
        bitmaps.clear()
    }

    override fun onBeforeDestroy() {
        super.onBeforeDestroy()
        bitmaps.clear()
    }

    override fun onSingleTap(e: MotionEvent, mapTap: Rect, mapView: MapView): Boolean {
        if (!area.show) return false
        val application = context.application as Borkozic
        val waypoints = area.waypoints
        synchronized(waypoints) {
            for (i in waypoints.size - 1 downTo 0) {
                val wpt = waypoints[i]
                val pointXY = application.getXYbyLatLon(wpt.latitude, wpt.longitude)
                if (mapTap.contains(pointXY[0], pointXY[1]) && context is MapActivity) {
                    val mapActivity = context as MapActivity
                    return mapActivity.areaWaypointTapped(application.getAreaIndex(area), i, e.x.toInt(), e.y.toInt())
                }
            }
        }
        return false
    }

    override fun onDraw(c: Canvas, mapView: MapView, centerX: Int, centerY: Int) {
        if (!area.show) return
        val application = context.application as Borkozic
        val cxy = mapView.mapCenterXY
        val path = Path()
        val path2 = Path()
        var i = 0
        var lastX = 0
        var lastY = 0
        var firstX = 0
        var firstY = 0
        val waypoints = area.waypoints
        synchronized(waypoints) {
            for (wpt in waypoints) {
                val xy = application.getXYbyLatLon(wpt.latitude, wpt.longitude)
                if (i == 0) {
                    path.setLastPoint((xy[0] - cxy[0]).toFloat(), (xy[1] - cxy[1]).toFloat())
                    path2.setLastPoint((xy[0] - cxy[0]).toFloat(), (xy[1] - cxy[1]).toFloat())
                    lastX = xy[0]
                    lastY = xy[1]
                    firstX = xy[0]
                    firstY = xy[1]
                } else {
                    if (Math.abs(lastX - xy[0]) > 2 || Math.abs(lastY - xy[1]) > 2) {
                        path.lineTo((xy[0] - cxy[0]).toFloat(), (xy[1] - cxy[1]).toFloat())
                        path2.lineTo((xy[0] - cxy[0]).toFloat(), (xy[1] - cxy[1]).toFloat())
                        lastX = xy[0]
                        lastY = xy[1]
                    }
                }
                i++
            }
            if (Math.abs(lastX - firstX) > 2 || Math.abs(lastY - firstY) > 2) {
                path.lineTo((firstX - cxy[0]).toFloat(), (firstY - cxy[1]).toFloat())
                path2.lineTo((firstX - cxy[0]).toFloat(), (firstY - cxy[1]).toFloat())
            }
        }
        c.drawPath(path, areaLinePaint)
        c.drawPath(path2, areaFillPaint)
    }

    override fun onDrawFinished(c: Canvas, mapView: MapView, centerX: Int, centerY: Int) {
        if (!area.show) return
        val application = context.application as Borkozic
        val cxy = mapView.mapCenterXY
        val half = Math.round(pointWidth / 2f)
        val waypoints = area.waypoints
        synchronized(waypoints) {
            for (wpt in waypoints) {
                var bitmap = bitmaps[wpt]
                if (bitmap == null) {
                    var width = pointWidth
                    var height = pointWidth + 2
                    if (showNames) {
                        val bounds = Rect()
                        textPaint.getTextBounds(wpt.name, 0, wpt.name.length, bounds)
                        bounds.inset(-2, -4)
                        width += 5 + bounds.width()
                        if (height < bounds.height()) height = bounds.height()
                    }
                    bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val bc = Canvas(bitmap)
                    bc.translate(half.toFloat(), half.toFloat())
                    if (showNames) bc.translate(0f, 2f)
                    bc.drawCircle(0f, 0f, half.toFloat(), fillPaint)
                    bc.drawCircle(0f, 0f, half.toFloat(), borderPaint)
                    if (showNames) {
                        val rect = Rect()
                        textPaint.getTextBounds(wpt.name, 0, wpt.name.length, rect)
                        rect.inset(-2, -4)
                        rect.offset(+half + 5, +half - 3)
                        bc.drawRect(rect, textFillPaint)
                        bc.drawText(wpt.name, +half + 6f, +half.toFloat(), textPaint)
                    }
                    bitmaps[wpt] = bitmap
                }
                val xy = application.getXYbyLatLon(wpt.latitude, wpt.longitude)
                c.drawBitmap(bitmap, (xy[0] - half - cxy[0]).toFloat(), (xy[1] - half - cxy[1]).toFloat(), null)
            }
        }
    }

    override fun onPreferencesChanged(settings: SharedPreferences) {
        areaWidth = settings.getInt(context.getString(R.string.pref_area_linewidth), context.resources.getInteger(R.integer.def_area_linewidth))
        pointWidth = settings.getInt(context.getString(R.string.pref_area_pointwidth), context.resources.getInteger(R.integer.def_area_pointwidth))
        showNames = settings.getBoolean(context.getString(R.string.pref_area_showname), true)
        if (!area.editing) {
            areaLinePaint.strokeWidth = areaWidth.toFloat()
        }
        textPaint.textSize = pointWidth * 1.5f
        bitmaps.clear()
    }
}