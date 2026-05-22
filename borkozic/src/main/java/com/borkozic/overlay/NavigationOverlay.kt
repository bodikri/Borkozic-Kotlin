package com.borkozic.overlay
import com.borkozic.BaseApplication

import android.app.Activity
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Paint
import android.preference.PreferenceManager
import com.borkozic.Borkozic
import com.borkozic.MapActivity
import com.borkozic.MapView
import com.borkozic.R
import com.borkozic.navigation.NavigationService

class NavigationOverlay(activity: Activity) : MapOverlay(activity) {

    private val paint: Paint = Paint()
    private var proximity = 0
    private var mpp = 0.0
    private var drawCircle = false
    private var navigationService: NavigationService? = null

    init {
        paint.isAntiAlias = true
        paint.strokeWidth = 5f
        paint.style = Paint.Style.STROKE
        paint.color = context.resources.getColor(R.color.navigationline)

        onPreferencesChanged(PreferenceManager.getDefaultSharedPreferences(context))
        mpp = 0.0

        if (context is MapActivity) {
            navigationService = (context as MapActivity).navigationService
        }

        enabled = true
    }

    override fun setMapContext(activity: Activity) {
        super.setMapContext(activity)
        navigationService = (context as MapActivity).navigationService
    }

    @Synchronized
    override fun onMapChanged() {
        mpp = 0.0
        val application: Borkozic = BaseApplication.getApplication<Borkozic>()!!
        val map = application.currentMap ?: return

        mpp = map.mpp / map.zoom
    }

    override fun onDraw(c: Canvas, mapView: MapView, centerX: Int, centerY: Int) {
        val navService = navigationService ?: return
        if (navService.navWaypoint == null) return

        val application = context.application as Borkozic

        val cxy = mapView.mapCenterXY

        var xy = application.getXYbyLatLon(navService.navWaypoint!!.latitude, navService.navWaypoint!!.longitude)

        if (mapView.currentLocation != null) {
            val lxy = mapView.currentLocationXY
            c.drawLine(
                (lxy[0] - cxy[0]).toFloat(), (lxy[1] - cxy[1]).toFloat(),
                (xy[0] - cxy[0]).toFloat(), (xy[1] - cxy[1]).toFloat(),
                paint
            )
        }
        if (drawCircle && navService.navRoute != null) {
            val waypoints = navService.navRoute!!.waypoints
            synchronized(waypoints) {
                for (wpt in waypoints) {
                    var radius = if (wpt.proximity > 0) wpt.proximity else proximity
                    radius = (radius / mpp).toInt()
                    if (radius > 0) {
                        xy = application.getXYbyLatLon(wpt.latitude, wpt.longitude)
                        c.drawCircle(
                            (xy[0] - cxy[0]).toFloat(),
                            (xy[1] - cxy[1]).toFloat(),
                            radius.toFloat(),
                            paint
                        )
                    }
                }
            }
        }
    }

    override fun onDrawFinished(c: Canvas, mapView: MapView, centerX: Int, centerY: Int) {
    }

    override fun onPreferencesChanged(settings: SharedPreferences) {
        proximity = settings.getString(
            context.getString(R.string.pref_navigation_proximity),
            context.getString(R.string.def_navigation_proximity)
        )!!.toInt()
        drawCircle = settings.getBoolean(
            context.getString(R.string.pref_navigation_proximitycircle),
            context.resources.getBoolean(R.bool.def_navigation_proximitycircle)
        )
        paint.strokeWidth = settings.getInt(
            context.getString(R.string.pref_navigation_linewidth),
            context.resources.getInteger(R.integer.def_navigation_linewidth)
        ).toFloat()
    }
}
