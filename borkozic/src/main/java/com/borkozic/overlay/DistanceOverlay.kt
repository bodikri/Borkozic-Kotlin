package com.borkozic.overlay

import android.app.Activity
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Paint.Align
import android.graphics.Typeface
import androidx.preference.PreferenceManager
import com.borkozic.Borkozic
import com.borkozic.MapView
import com.borkozic.R
import com.borkozic.util.Geo
import com.borkozic.util.StringFormatter

class DistanceOverlay(mapActivity: Activity) : MapOverlay(mapActivity) {

    private val linePaint: Paint = Paint()
    private val circlePaint: Paint = Paint()
    private val textPaint: Paint = Paint()
    private val textFillPaint: Paint = Paint()

    private var ancor: DoubleArray? = null
    private var ancorXY: IntArray? = null

    init {
        linePaint.isAntiAlias = true
        linePaint.strokeWidth = 5f
        linePaint.style = Paint.Style.STROKE
        linePaint.color = context.resources.getColor(R.color.distanceline)
        circlePaint.isAntiAlias = true
        circlePaint.strokeWidth = 1f
        circlePaint.style = Paint.Style.FILL_AND_STROKE
        circlePaint.color = context.resources.getColor(R.color.distanceline)
        circlePaint.alpha = 255
        textPaint.isAntiAlias = true
        textPaint.strokeWidth = 2f
        textPaint.style = Paint.Style.FILL
        textPaint.textAlign = Align.LEFT
        textPaint.textSize = 10f
        textPaint.typeface = Typeface.SANS_SERIF
        textPaint.color = context.resources.getColor(R.color.waypointtext)
        textFillPaint.isAntiAlias = false
        textFillPaint.strokeWidth = 1f
        textFillPaint.style = Paint.Style.FILL_AND_STROKE
        textFillPaint.color = context.resources.getColor(R.color.distanceline)

        onPreferencesChanged(PreferenceManager.getDefaultSharedPreferences(context))

        enabled = false
    }

    fun setAncor(ancor: DoubleArray) {
        this.ancor = ancor
        val application = context.application as Borkozic
        ancorXY = application.getXYbyLatLon(this.ancor!![0], this.ancor!![1])
    }

    fun getAncor(): DoubleArray? {
        return ancor
    }

    override fun onMapChanged() {
        super.onMapChanged()
        val a = ancor ?: return
        val application = context.application as Borkozic
        ancorXY = application.getXYbyLatLon(a[0], a[1])
    }

    override fun onDraw(c: Canvas, mapView: MapView, centerX: Int, centerY: Int) {
        val a = ancor ?: return
        val axy = ancorXY ?: return

        val loc = mapView.mapCenter
        val cxy = mapView.mapCenterXY

        val sx = axy[0] - cxy[0] + centerX
        val sy = axy[1] - cxy[1] + centerY

        if (axy[0] != cxy[0] || axy[1] != cxy[1]) {
            if (sx in 0..mapView.width && sy in 0..mapView.height) {
                c.drawLine(0f, 0f, (axy[0] - cxy[0]).toFloat(), (axy[1] - cxy[1]).toFloat(), linePaint)
                c.drawCircle(
                    (axy[0] - cxy[0]).toFloat(),
                    (axy[1] - cxy[1]).toFloat(),
                    linePaint.strokeWidth,
                    circlePaint
                )
            } else {
                val bearing = Geo.bearing(loc[0], loc[1], a[0], a[1])
                c.save()
                c.rotate(bearing.toFloat(), 0f, 0f)
                c.drawLine(0f, 0f, 0f, (-centerY - centerX).toFloat(), linePaint)
                c.restore()
            }
        }
    }

    override fun onDrawFinished(c: Canvas, mapView: MapView, centerX: Int, centerY: Int) {
        val a = ancor ?: return

        val loc = mapView.mapCenter

        val dist = Geo.distance(loc[0], loc[1], a[0], a[1])
        val bearing = Geo.bearing(loc[0], loc[1], a[0], a[1])
        if (dist > 0) {
            val distance = StringFormatter.distanceH(dist) + " " + StringFormatter.bearingH(bearing)

            val rect = Rect()
            textPaint.getTextBounds(distance, 0, distance.length, rect)
            val half = rect.width() / 2
            val dy = if (bearing > 90 && bearing < 270) -60 else 60 + rect.height() / 2
            rect.offset(-half, dy)
            rect.inset(-4, -4)
            c.drawRect(rect, textFillPaint)
            c.drawText(distance, (-half).toFloat(), dy.toFloat(), textPaint)
        }
    }

    override fun onPreferencesChanged(settings: SharedPreferences) {
        linePaint.strokeWidth = settings.getInt(
            context.getString(R.string.pref_navigation_linewidth),
            context.resources.getInteger(R.integer.def_navigation_linewidth)
        ).toFloat()
        val textSize = settings.getInt(
            context.getString(R.string.pref_waypoint_width),
            context.resources.getInteger(R.integer.def_waypoint_width)
        )
        textPaint.textSize = (textSize * 2).toFloat()
    }
}
