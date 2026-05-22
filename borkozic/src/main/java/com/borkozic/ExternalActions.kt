package com.borkozic
import com.borkozic.navigation.BaseNavigationService
import com.borkozic.BaseApplication

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import java.util.Calendar
import com.borkozic.data.Route
import com.borkozic.data.Waypoint
import com.borkozic.navigation.NavigationService
import com.borkozic.overlay.RouteOverlay

class ExternalActions : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = this.intent
        val action = intent.action
        Log.e("ANDROZIC", "New intent: " + action)

        val application = BaseApplication.getApplication<Borkozic>()!!!!
        val activity = Intent(this, MapActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)

        if (action.equals("com.borkozic.PLOT_ROUTE")) {
            val extras = intent.extras
            val wptLat = extras?.getDoubleArray("targetLat")
            val wptLon = extras?.getDoubleArray("targetLon")
            val wptNames = extras?.getStringArray("targetName")
            if (wptLat != null && wptLon != null && wptLat.size == wptLon.size) {
                val route = Route("External route", "", true)
                for (i in wptLat.indices) {
                    val name = if (wptNames != null) wptNames[i] else "RWPT$i"
                    route.addWaypoint(name, wptLat[i], wptLon[i])
                }
                val rt = application.addRoute(route)
                val newRoute = RouteOverlay(this, route)
                application.routeOverlays.add(newRoute)
                startForegroundService(Intent(this, NavigationService::class.java).setAction(BaseNavigationService.NAVIGATE_ROUTE).putExtra(BaseNavigationService.EXTRA_ROUTE_INDEX, rt))
            } else {
                Toast.makeText(baseContext, "Bad route data", Toast.LENGTH_LONG).show()
            }
        } else if (action.equals("com.google.android.radar.SHOW_RADAR")) {
            val lat = intent.getFloatExtra("latitude", 0f).toDouble()
            val lon = intent.getFloatExtra("longitude", 0f).toDouble()
            val waypoint = Waypoint("", "", lat, lon)
            waypoint.date = Calendar.getInstance().getTime()
            val wpt = application.addWaypoint(waypoint)
            waypoint.name = "WPT" + wpt
            val i = Intent(applicationContext, NavigationService::class.java).setAction(BaseNavigationService.NAVIGATE_MAPOBJECT)
            i.putExtra(BaseNavigationService.EXTRA_NAME, waypoint.name)
            i.putExtra(BaseNavigationService.EXTRA_LATITUDE, waypoint.latitude)
            i.putExtra(BaseNavigationService.EXTRA_LONGITUDE, waypoint.longitude)
            i.putExtra(BaseNavigationService.EXTRA_PROXIMITY, waypoint.proximity)
            startService(i)
        } else if ("geo".equals(intent.scheme)) {
            val uri = intent.data
            var data = uri?.schemeSpecificPart

            if (data?.contains("?") == true)
                data = data.substring(0, data.indexOf("?") - 1)
            try {
                val ll = data?.split(",")
                val lat = ll?.get(0) ?: ""
                val lon = ll?.get(1) ?: ""
                activity.putExtra("lat", lat.toDouble())
                activity.putExtra("lon", lon.toDouble())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        startActivity(activity)
        finish()
    }
}
