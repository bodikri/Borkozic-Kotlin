package com.borkozic.overlay
import com.borkozic.BaseApplication

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Rect
import android.view.MotionEvent
import com.borkozic.Borkozic
import com.borkozic.MapActivity
import com.borkozic.MapView
import com.borkozic.data.Waypoint

class WaypointsOverlay(mapActivity: Activity) : MapObjectsOverlay(mapActivity) {

    private var waypoints: List<Waypoint> = emptyList()

    init {
        enabled = true
    }

    fun setWaypoints(wpt: List<Waypoint>) {
        waypoints = wpt
        clearBitmapCache()
    }

    override fun onSingleTap(e: MotionEvent, mapTap: Rect, mapView: MapView): Boolean {
        val application: Borkozic = BaseApplication.getApplication<Borkozic>()!!
        val cxy = mapView.mapCenterXY
        val lookAhead = mapView.lookAheadXY  // canvas е translate-нат с lookAheadXY

        val hitTolerance = 48  // пиксела — по-голямо тап петно за по-лесно улучване
        synchronized(waypoints) {
            android.util.Log.d("WaypointsOverlay", "onSingleTap: waypoints.size=${waypoints.size} mapTap=$mapTap centerXY=(${cxy[0]},${cxy[1]}) lookAhead=(${lookAhead[0]},${lookAhead[1]}) hitTolerance=$hitTolerance")
            for (i in waypoints.indices.reversed()) {
                val wpt = waypoints[i]
                val pointXY = application.getXYbyLatLon(wpt.latitude, wpt.longitude)
                // add lookAheadXY — canvas translate при draw, hit testing-ът трябва да е съвместим
                val hitX = pointXY[0] + lookAhead[0]
                val hitY = pointXY[1] + lookAhead[1]
                val hitRect = Rect(mapTap.left - hitTolerance, mapTap.top - hitTolerance,
                                   mapTap.right + hitTolerance, mapTap.bottom + hitTolerance)
                val hit = hitRect.contains(hitX, hitY)
                if (i < 3) android.util.Log.d("WaypointsOverlay", "  wpt[$i]=${wpt.name} lat=${wpt.latitude} lon=${wpt.longitude} xy=(${pointXY[0]},${pointXY[1]}) +lookAhead=(${hitX},${hitY}) hit=$hit")
                if (i < 3) android.util.Log.d("WaypointsOverlay", "  wpt[$i]=${wpt.name} lat=${wpt.latitude} lon=${wpt.longitude} xy=(${pointXY[0]},${pointXY[1]}) hit=$hit")
                if (hit && context is MapActivity) {
                    android.util.Log.d("WaypointsOverlay", "onSingleTap: HIT wpt=${wpt.name}")
                    return (context as MapActivity).waypointTapped(wpt, e.x.toInt(), e.y.toInt())
                }
            }
        }
        android.util.Log.d("WaypointsOverlay", "onSingleTap: no hit after checking ${waypoints.size} waypoints")
        return false
    }

    override fun onDraw(c: Canvas, mapView: MapView, centerX: Int, centerY: Int) {
    }

    override fun onDrawFinished(c: Canvas, mapView: MapView, centerX: Int, centerY: Int) {
        val application = context.application as Borkozic
        val cxy = mapView.mapCenterXY

        synchronized(waypoints) {
            for (wpt in waypoints) {
                drawMapObject(c, wpt, application, cxy)
            }
        }
    }
}
