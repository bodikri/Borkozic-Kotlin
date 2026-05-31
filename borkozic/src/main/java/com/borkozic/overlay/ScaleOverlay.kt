package com.borkozic.overlay

import android.app.Activity
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Paint.Align
import android.graphics.Typeface
import androidx.preference.PreferenceManager
import com.borkozic.Borkozic
import com.borkozic.MapView
import com.borkozic.R
import com.borkozic.util.StringFormatter

class ScaleOverlay(activity: Activity) : MapOverlay(activity) {

    private val linePaint: Paint = Paint()
    private val textPaint: Paint = Paint()
    private var mpp = 0.0

    init {
        linePaint.isAntiAlias = false
        linePaint.strokeWidth = 2f
        linePaint.style = Paint.Style.STROKE
        linePaint.color = context.resources.getColor(R.color.scalebar)
        textPaint.isAntiAlias = true
        textPaint.strokeWidth = 2f
        textPaint.style = Paint.Style.FILL
        textPaint.textAlign = Align.CENTER
        textPaint.textSize = 16f
        textPaint.typeface = Typeface.SANS_SERIF
        textPaint.color = context.resources.getColor(R.color.scalebar)
        mpp = 0.0
        onPreferencesChanged(PreferenceManager.getDefaultSharedPreferences(context))
        enabled = true
    }

    @Synchronized
    override fun onMapChanged() {
        val application = context.application as Borkozic
        val map = application.currentMap ?: return
        mpp = map.mpp / map.zoom
    }

    override fun onDraw(c: Canvas, mapView: MapView, centerX: Int, centerY: Int) {
        if (mpp == 0.0) return

        val w = mapView.width
        var m = (mpp * w / 6).toInt()
        m = when {
            m < 40 -> m / 10 * 10
            m < 80 -> 50
            m < 130 -> 100
            m < 300 -> 200
            m < 700 -> 500
            m < 900 -> 800
            m < 1300 -> 1000
            m < 3000 -> 2000
            m < 7000 -> 5000
            m < 10000 -> 8000
            m < 80000 -> Math.ceil(m * 1.0 / 10000).toInt() * 10000
            else -> Math.ceil(m * 1.0 / 100000).toInt() * 100000
        }

        var x = (m / mpp).toInt()

        if (x > w / 4) {
            x /= 2
            m /= 2
        }

        val x2 = x * 2
        val x3 = x * 3
        val xd2 = x / 2
        val xd4 = x / 4

        val cx = -mapView.lookAheadXY[0] - centerX + 30
        val cy = -mapView.lookAheadXY[1] - centerY + mapView.viewArea.bottom - 30
        val cty = -10

        // Overlays are drawn in the already-rotated canvas (doDraw applies +bearing
        // in Track Up mode). Counter-rotate to keep the scale bar fixed and horizontal
        // at the bottom, regardless of map rotation.
        if (mapView.isTrackUp) {
            c.save()
            c.rotate(-mapView.bearing)
            drawScaleBar(c, cx, cy, x, x2, x3, xd2, xd4, cty, m)
            c.restore()
        } else {
            drawScaleBar(c, cx, cy, x, x2, x3, xd2, xd4, cty, m)
        }
    }

    private fun drawScaleBar(
        c: Canvas, cx: Int, cy: Int,
        x: Int, x2: Int, x3: Int, xd2: Int, xd4: Int,
        cty: Int, m: Int
    ) {
        c.drawLine(cx.toFloat(), cy.toFloat(), (cx + x3).toFloat(), cy.toFloat(), linePaint)
        c.drawLine(cx.toFloat(), (cy + 10).toFloat(), (cx + x3).toFloat(), (cy + 10).toFloat(), linePaint)
        c.drawLine(cx.toFloat(), cy.toFloat(), cx.toFloat(), (cy + 10).toFloat(), linePaint)
        c.drawLine((cx + x3).toFloat(), cy.toFloat(), (cx + x3).toFloat(), (cy + 10).toFloat(), linePaint)
        // markers
        c.drawLine((cx + x).toFloat(), cy.toFloat(), (cx + x).toFloat(), (cy + 10).toFloat(), linePaint)
        c.drawLine((cx + x2).toFloat(), cy.toFloat(), (cx + x2).toFloat(), (cy + 10).toFloat(), linePaint)
        // scale patterns
        c.drawLine((cx + x).toFloat(), (cy + 5).toFloat(), (cx + x2).toFloat(), (cy + 5).toFloat(), linePaint)
        c.drawLine(cx.toFloat(), (cy + 5).toFloat(), (cx + xd4).toFloat(), (cy + 5).toFloat(), linePaint)
        c.drawLine((cx + xd2).toFloat(), (cy + 5).toFloat(), (cx + xd2 + xd4).toFloat(), (cy + 5).toFloat(), linePaint)
        // small ticks
        c.drawLine((cx + xd4).toFloat(), cy.toFloat(), (cx + xd4).toFloat(), (cy + 10).toFloat(), linePaint)
        c.drawLine((cx + xd2).toFloat(), cy.toFloat(), (cx + xd2).toFloat(), (cy + 10).toFloat(), linePaint)
        c.drawLine((cx + xd2 + xd4).toFloat(), cy.toFloat(), (cx + xd2 + xd4).toFloat(), (cy + 10).toFloat(), linePaint)
        // text
        c.drawText("0", (cx + x).toFloat(), (cy + cty).toFloat(), textPaint)
        var t = 2000
        if (m <= t && m * 2 > t) t = m * 3
        val d = StringFormatter.distanceC(m.toDouble(), t)
        c.drawText(d[0], (cx + x2).toFloat(), (cy + cty).toFloat(), textPaint)
        c.drawText(d[0], cx.toFloat(), (cy + cty).toFloat(), textPaint)
        c.drawText(StringFormatter.distanceH((m * 2).toDouble(), t), (cx + x3).toFloat(), (cy + cty).toFloat(), textPaint)
    }

    override fun onDrawFinished(c: Canvas, mapView: MapView, centerX: Int, centerY: Int) {
    }

    override fun onPreferencesChanged(settings: SharedPreferences) {
        val color = settings.getInt(
            context.getString(R.string.pref_scalebarcolor),
            context.resources.getColor(R.color.scalebar)
        )
        linePaint.color = color
        textPaint.color = color
    }
}
