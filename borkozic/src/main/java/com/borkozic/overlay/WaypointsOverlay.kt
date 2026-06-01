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

    /**
     * Hit-tests a single tap against all route waypoints (in reverse paint order).
     * mapTap is already in map-space coordinates — MapView.onSingleTap() converts
     * from screen coordinates by subtracting lookAheadXY and accounting for Track Up rotation.
     * We use the same coordinate space as RouteOverlay and AreaOverlay: pointXY from
     * getXYbyLatLon is compared directly against mapTap without any additional offsets.
     *
     * @return true if a waypoint was hit (consumes the event), false otherwise
     */
    override fun onSingleTap(e: MotionEvent, mapTap: Rect, mapView: MapView): Boolean {
        val application: Borkozic = BaseApplication.getApplication<Borkozic>()!!

        synchronized(waypoints) {
            for (i in waypoints.indices.reversed()) {
                val wpt = waypoints[i]
                val pointXY = application.getXYbyLatLon(wpt.latitude, wpt.longitude)
                if (mapTap.contains(pointXY[0], pointXY[1]) && context is MapActivity) {
                    return (context as MapActivity).waypointTapped(wpt, e.x.toInt(), e.y.toInt())
                }
            }
        }
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
