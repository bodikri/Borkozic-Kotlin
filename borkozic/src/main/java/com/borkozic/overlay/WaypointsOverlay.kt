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

        val hitTolerance = 48  // пиксела — по-голямо тап петно за по-лесно улучване
        synchronized(waypoints) {
            val tapCX = (mapTap.left + mapTap.right) / 2
            val tapCY = (mapTap.top + mapTap.bottom) / 2
            android.util.Log.d("WaypointsOverlay", "onSingleTap: waypoints.size=${waypoints.size} mapTap=(${mapTap.left},${mapTap.top})-(${mapTap.right},${mapTap.bottom}) tapCenter=($tapCX,$tapCY) tolerance=$hitTolerance")
            for (i in waypoints.indices.reversed()) {
                val wpt = waypoints[i]
                // pointXY са map координати — mapTap също е в map координати (MapView.onSingleTap изважда lookAheadXY)
                val pointXY = application.getXYbyLatLon(wpt.latitude, wpt.longitude)
                val deltaX = pointXY[0] - tapCX
                val deltaY = pointXY[1] - tapCY
                val hitRect = Rect(mapTap.left - hitTolerance, mapTap.top - hitTolerance,
                                   mapTap.right + hitTolerance, mapTap.bottom + hitTolerance)
                val hit = hitRect.contains(pointXY[0], pointXY[1])
                // log every waypoint: tap center, point coords, delta — за да видим offset посоката и големината
                android.util.Log.d("WaypointsOverlay",
                    "  wpt[$i]=${wpt.name} pointXY=(${pointXY[0]},${pointXY[1]}) delta=($deltaX,$deltaY) hit=$hit")
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
