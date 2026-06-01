/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2012  Andrey Novikov <http://andreynovikov.info/>
 *
 * This file is part of Androzic application.
 *
 * Androzic is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * Androzic is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with Androzic.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.borkozic.route

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.borkozic.BaseApplication
import com.borkozic.Borkozic
import com.borkozic.R
import com.borkozic.navigation.BaseNavigationService
import com.borkozic.navigation.NavigationService
import com.borkozic.ui.BorkozicTheme
import com.borkozic.waypoint.WaypointProperties

/**
 * Compose-based Route details screen with:
 * - Waypoint list (distance, course, altitude)
 * - Drag-and-drop reorder via long-press
 * - Tap → View/Edit popup (or View/Navigate in navigation mode)
 * - Navigation integration (broadcast updates, ETE/ETA)
 */
class RouteDetails : ComponentActivity() {

    private var navigationService: NavigationService? = null
    private var navigation = false
    private var navCurrentIndex = -1
    private var navDistance = 0.0
    private var navETE = 0
    private var navBearing = 0.0
    private var navDirection = BaseNavigationService.DIRECTION_FORWARD

    private val navigationUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BaseNavigationService.BROADCAST_NAVIGATION_STATE) {
                val state = intent.extras?.getInt("state") ?: return
                if (state == BaseNavigationService.STATE_REACHED) {
                    runOnUiThread {
                        Toast.makeText(applicationContext, R.string.arrived, Toast.LENGTH_LONG).show()
                        navigation = false
                    }
                }
            }
        }
    }

    private val navigationConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            navigationService = (service as NavigationService.LocalBinder).getService()
            registerReceiver(navigationUpdateReceiver, IntentFilter(BaseNavigationService.BROADCAST_NAVIGATION_STATE))
            Log.d(TAG, "Navigation service connected")
        }

        override fun onServiceDisconnected(className: ComponentName) {
            try { unregisterReceiver(navigationUpdateReceiver) } catch (_: Exception) {}
            navigationService = null
            Log.d(TAG, "Navigation service disconnected")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        val index = intent.extras!!.getInt("index")
        navigation = intent.extras!!.getBoolean("nav", false)

        val application = BaseApplication.getApplication<Borkozic>()!!
        val route = application.getRoute(index)!!

        if (navigation) {
            bindService(Intent(this, NavigationService::class.java), navigationConnection, BIND_AUTO_CREATE)
            val lock = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(getString(R.string.pref_wakelock), resources.getBoolean(R.bool.def_wakelock))
            if (lock) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        setContent {
            BorkozicTheme(listType = "route") {
                val navSvc = navigationService
                if (navSvc != null) {
                    navCurrentIndex = navSvc.navRouteCurrentIndex()
                    navDistance = navSvc.navDistance
                    navETE = navSvc.navETE
                    navBearing = navSvc.navBearing
                    navDirection = navSvc.navDirection
                }

                RouteDetailsScreen(
                    route = route,
                    mode = if (navigation) RouteDetailsMode.NAVIGATION else RouteDetailsMode.MANAGE,
                    onEditWaypoint = { idx ->
                        val routeIdx = application.getRouteIndex(route)
                        startActivity(
                            Intent(this, WaypointProperties::class.java)
                                .putExtra("INDEX", idx)
                                .putExtra("ROUTE", routeIdx + 1)
                        )
                    },
                    onNavigateToWaypoint = { idx ->
                        val svc = navigationService ?: return@RouteDetailsScreen
                        var adjusted = idx
                        if (svc.navDirection == BaseNavigationService.DIRECTION_REVERSE)
                            adjusted = route.length() - idx - 1
                        svc.setRouteWaypoint(adjusted)
                    },
                    onShowWaypoint = { idx ->
                        route.show = true
                        application.ensureVisible(route.getWaypoint(idx))
                        setResult(RESULT_OK)
                        finish()
                    },
                    onStartNavigation = {
                        val routeIdx = application.getRouteIndex(route)
                        startActivity(
                            Intent(this, RouteStart::class.java)
                                .putExtra("index", routeIdx)
                        )
                    },
                    onBack = { finish() },
                    navCurrentIndex = navCurrentIndex,
                    navDistance = navDistance,
                    navETE = navETE,
                    navBearing = navBearing,
                    navRouteDistanceLeft = { idx -> navigationService?.navRouteDistanceLeftTo(idx) ?: 0.0 },
                    navRouteWaypointETE = { idx -> navigationService?.navRouteWaypointETE(idx) ?: 0 },
                    navDirectionForward = navDirection == BaseNavigationService.DIRECTION_FORWARD
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (navigation) {
            try { unregisterReceiver(navigationUpdateReceiver) } catch (_: Exception) {}
            try { unbindService(navigationConnection) } catch (_: Exception) {}
        }
    }

    companion object {
        private const val TAG = "RouteDetails"
    }
}
