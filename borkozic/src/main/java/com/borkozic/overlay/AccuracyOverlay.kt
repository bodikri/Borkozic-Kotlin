package com.borkozic.overlay

import android.app.Activity
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Paint
import com.borkozic.Borkozic
import com.borkozic.MapView
import com.borkozic.R
import com.borkozic.util.Geo

class AccuracyOverlay(activity: Activity) : MapOverlay(activity) {

    private val paint: Paint = Paint()
    private var radius = 0
    private var accuracy = 0f

    init {
        paint.isAntiAlias = true
        paint.strokeWidth = 5f
        paint.style = Paint.Style.FILL_AND_STROKE
        paint.color = context.resources.getColor(R.color.accuracy)
    }

    fun setAccuracy(accuracy: Float) {
        if (accuracy > 0 && this.accuracy != accuracy) {
            this.accuracy = accuracy
            val application = context.application as Borkozic
            val loc = application.getLocation()
            val prx = Geo.projection(loc[0], loc[1], accuracy / 2.0, 90.0)
            val cxy = application.getXYbyLatLon(loc[0], loc[1])
            val pxy = application.getXYbyLatLon(prx[0], prx[1])
            radius = Math.hypot((pxy[0] - cxy[0]).toDouble(), (pxy[1] - cxy[1]).toDouble()).toInt()
        }
        enabled = accuracy > 0
    }

    override fun onMapChanged() {
        val a = accuracy
        accuracy = 0f
        setAccuracy(a)
    }

    override fun onDraw(c: Canvas, mapView: MapView, centerX: Int, centerY: Int) {
        val cxy = mapView.mapCenterXY

        if (radius > 0 && mapView.currentLocation != null) {
            c.drawCircle(
                (mapView.currentLocationXY[0] - cxy[0]).toFloat(),
                (mapView.currentLocationXY[1] - cxy[1]).toFloat(),
                radius.toFloat(),
                paint
            )
        }
    }

    override fun onDrawFinished(c: Canvas, mapView: MapView, centerX: Int, centerY: Int) {
    }

    override fun onPreferencesChanged(settings: SharedPreferences) {
    }
}
