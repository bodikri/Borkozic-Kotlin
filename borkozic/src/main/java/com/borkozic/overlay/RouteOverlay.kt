package com.borkozic.overlay

import android.app.Activity
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Paint.Align
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Typeface
import android.preference.PreferenceManager
import android.view.MotionEvent
import androidx.core.content.ContextCompat
import com.borkozic.Borkozic
import com.borkozic.MapActivity
import com.borkozic.MapView
import com.borkozic.R
import com.borkozic.data.Route
import com.borkozic.data.Waypoint
import java.util.WeakHashMap

open class RouteOverlay : MapOverlay {

    @JvmField
    var linePaint: Paint = Paint()

    private val borderPaint: Paint = Paint()
    private val fillPaint: Paint = Paint()
    private val textPaint: Paint = Paint()
    private val textFillPaint: Paint = Paint()

    @JvmField
    var route: Route

    @JvmField
    var bitmaps: MutableMap<Waypoint, Bitmap>

    @JvmField
    var pointWidth = 10

    @JvmField
    var routeWidth = 2

    @JvmField
    var showNames = false

    constructor(mapActivity: Activity) : super(mapActivity) {
        route = Route()
        bitmaps = WeakHashMap()

        linePaint.isAntiAlias = true
        linePaint.strokeWidth = routeWidth.toFloat()
        linePaint.style = Paint.Style.STROKE
        linePaint.color = ContextCompat.getColor(context, R.color.routeline)

        fillPaint.isAntiAlias = false
        fillPaint.strokeWidth = 1f
        fillPaint.style = Paint.Style.FILL_AND_STROKE
        fillPaint.color = ContextCompat.getColor(context, R.color.routewaypoint)

        borderPaint.isAntiAlias = false
        borderPaint.strokeWidth = 1f
        borderPaint.style = Paint.Style.STROKE
        borderPaint.color = ContextCompat.getColor(context, R.color.routeline)

        textPaint.isAntiAlias = true
        textPaint.strokeWidth = 2f
        textPaint.style = Paint.Style.FILL
        textPaint.textAlign = Align.LEFT
        textPaint.textSize = pointWidth * 1.5f
        textPaint.typeface = Typeface.SANS_SERIF
        textPaint.color = ContextCompat.getColor(context, R.color.routewaypointtext)

        textFillPaint.isAntiAlias = false
        textFillPaint.strokeWidth = 1f
        textFillPaint.style = Paint.Style.FILL_AND_STROKE
        textFillPaint.color = ContextCompat.getColor(context, R.color.routeline)

        onPreferencesChanged(PreferenceManager.getDefaultSharedPreferences(context))

        enabled = true
    }

    constructor(mapActivity: Activity, aRoute: Route) : this(mapActivity) {
        route = aRoute
        if (route.lineColor == -1)
            route.lineColor = linePaint.color
        onRoutePropertiesChanged()
    }

    private fun initRouteColors() {
        linePaint.color = route.lineColor
        linePaint.alpha = 0xAA
        borderPaint.color = route.lineColor
        textFillPaint.color = route.lineColor
        textFillPaint.alpha = 0x88
        val y = getLuminance(route.lineColor)
        textPaint.color = if (y <= 0.5) Color.WHITE else Color.BLACK
    }

    private fun adjustValue(cc: Int): Double {
        var `val` = cc.toDouble()
        `val` /= 255
        return if (`val` <= 0.03928)
            `val` / 12.92
        else
            Math.pow((`val` + 0.055) / 1.055, 2.4)
    }

    private fun getLuminance(rgb: Int): Double {
        // http://www.w3.org/TR/WCAG20/relative-luminance.xml
        val r = (rgb and 0x00FF0000) shr 16
        val g = (rgb and 0x0000FF00) shr 8
        val b = (rgb and 0x000000FF)
        return 0.2126 * adjustValue(r) + 0.7152 * adjustValue(g) + 0.0722 * adjustValue(b)
    }

    fun onRoutePropertiesChanged() {
        if (linePaint.color != route.lineColor) {
            initRouteColors()
        }
        if (route.editing) {
            linePaint.pathEffect = DashPathEffect(floatArrayOf(5f, 2f), 0f)
            linePaint.strokeWidth = routeWidth * 3.toFloat()
        } else {
            linePaint.pathEffect = null
            linePaint.strokeWidth = routeWidth.toFloat()
        }
        bitmaps.clear()
    }

    override fun onBeforeDestroy() {
        super.onBeforeDestroy()
        bitmaps.clear()
    }

    fun getRoute(): Route {
        return route
    }

    override fun onSingleTap(e: MotionEvent, mapTap: Rect, mapView: MapView): Boolean {
        if (!route.show) return false

        val application = context.application as Borkozic

        val waypoints = route.waypoints
        synchronized(waypoints) {
            for (i in waypoints.indices.reversed()) {
                val wpt = waypoints[i]
                val pointXY = application.getXYbyLatLon(wpt.latitude, wpt.longitude)
                if (mapTap.contains(pointXY[0], pointXY[1]) && context is MapActivity) {
                    return (context as MapActivity).routeWaypointTapped(
                        application.getRouteIndex(route), i, e.x.toInt(), e.y.toInt()
                    )
                }
            }
        }
        return false
    }

    override fun onDraw(c: Canvas, mapView: MapView, centerX: Int, centerY: Int) {
        if (!route.show) return

        val application = context.application as Borkozic

        val cxy = mapView.mapCenterXY

        val path = Path()
        var i = 0
        var lastX = 0
        var lastY = 0
        val waypoints = route.waypoints
        synchronized(waypoints) {
            for (wpt in waypoints) {
                val xy = application.getXYbyLatLon(wpt.latitude, wpt.longitude)

                if (i == 0) {
                    path.setLastPoint(
                        (xy[0] - cxy[0]).toFloat(),
                        (xy[1] - cxy[1]).toFloat()
                    )
                    lastX = xy[0]
                    lastY = xy[1]
                } else {
                    if (Math.abs(lastX - xy[0]) > 2 || Math.abs(lastY - xy[1]) > 2) {
                        path.lineTo(
                            (xy[0] - cxy[0]).toFloat(),
                            (xy[1] - cxy[1]).toFloat()
                        )
                        lastX = xy[0]
                        lastY = xy[1]
                    }
                }
                i++
            }
        }
        c.drawPath(path, linePaint)
    }

    override fun onDrawFinished(c: Canvas, mapView: MapView, centerX: Int, centerY: Int) {
        if (!route.show) return

        val application = context.application as Borkozic

        val cxy = mapView.mapCenterXY

        val half = Math.round(pointWidth / 2.0f)

        val waypoints = route.waypoints

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
                        if (height < bounds.height())
                            height = bounds.height()
                    }

                    bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val bc = Canvas(bitmap!!)

                    bc.translate(half.toFloat(), half.toFloat())
                    if (showNames)
                        bc.translate(0f, 2f)
                    bc.drawCircle(0f, 0f, half.toFloat(), fillPaint)
                    bc.drawCircle(0f, 0f, half.toFloat(), borderPaint)

                    if (showNames) {
                        val rect = Rect()
                        textPaint.getTextBounds(wpt.name, 0, wpt.name.length, rect)
                        rect.inset(-2, -4)
                        rect.offset(half + 5, half - 3)
                        bc.drawRect(rect, textFillPaint)
                        bc.drawText(wpt.name, (half + 6).toFloat(), half.toFloat(), textPaint)
                    }
                    bitmaps[wpt] = bitmap
                }
                val xy = application.getXYbyLatLon(wpt.latitude, wpt.longitude)
                c.drawBitmap(bitmap!!, (xy[0] - half - cxy[0]).toFloat(), (xy[1] - half - cxy[1]).toFloat(), null)
            }
        }
    }

    override fun onPreferencesChanged(settings: SharedPreferences) {
        routeWidth = settings.getInt(
            context.getString(R.string.pref_route_linewidth),
            context.resources.getInteger(R.integer.def_route_linewidth)
        )
        pointWidth = settings.getInt(
            context.getString(R.string.pref_route_pointwidth),
            context.resources.getInteger(R.integer.def_route_pointwidth)
        )
        showNames = settings.getBoolean(context.getString(R.string.pref_route_showname), true)

        if (!route.editing) {
            linePaint.strokeWidth = routeWidth.toFloat()
        }
        textPaint.textSize = pointWidth * 1.5f
        bitmaps.clear()
    }
}
