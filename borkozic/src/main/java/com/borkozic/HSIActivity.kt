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

package com.borkozic
import com.borkozic.navigation.BaseNavigationService

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import androidx.core.content.ContextCompat
import android.content.SharedPreferences
import android.content.res.Resources
import android.location.Location
import android.os.Bundle
import android.os.IBinder
import androidx.preference.PreferenceManager
import android.util.Log
import android.view.Window
import android.view.WindowManager
import android.widget.TextView

import com.borkozic.location.ILocationListener
import com.borkozic.location.ILocationService
import com.borkozic.location.LocationService
import com.borkozic.navigation.NavigationService
import com.borkozic.ui.view.HSIView
import com.borkozic.util.StringFormatter

class HSIActivity : Activity() {
    private var locationService: ILocationService? = null
    private var navigationService: NavigationService? = null
    private lateinit var hsiView: HSIView
    private lateinit var distanceValue: TextView
    private lateinit var distanceUnit: TextView
    private lateinit var bearingValue: TextView
    private lateinit var bearingUnit: TextView
    private lateinit var speedValue: TextView
    private lateinit var speedUnit: TextView
    private lateinit var trackValue: TextView
    private lateinit var trackUnit: TextView
    private lateinit var elevationValue: TextView
    private lateinit var elevationUnit: TextView
    private lateinit var vmgValue: TextView
    private lateinit var vmgUnit: TextView
    private lateinit var courseValue: TextView
    private lateinit var courseUnit: TextView
    private lateinit var xtkValue: TextView
    private lateinit var xtkUnit: TextView
    private lateinit var eteValue: TextView
    private lateinit var eteUnit: TextView

    protected var speedFactor: Double = 0.0
    protected lateinit var speedAbbr: String
    protected var elevationFactor: Double = 0.0

    private lateinit var application: Borkozic

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.act_hsi)

        application = getApplication() as Borkozic

        hsiView = findViewById(R.id.hsiview)
        speedValue = findViewById(R.id.speed)
        speedUnit = findViewById(R.id.speedunit)
        trackValue = findViewById(R.id.track)
        trackUnit = findViewById(R.id.trackunit)
        elevationValue = findViewById(R.id.elevation)
        elevationUnit = findViewById(R.id.elevationunit)
        distanceValue = findViewById(R.id.distance)
        distanceUnit = findViewById(R.id.distanceunit)
        vmgValue = findViewById(R.id.vmg)
        vmgUnit = findViewById(R.id.vmgunit)
        xtkValue = findViewById(R.id.xtk)
        xtkUnit = findViewById(R.id.xtkunit)
        courseValue = findViewById(R.id.course)
        courseUnit = findViewById(R.id.courseunit)
        bearingValue = findViewById(R.id.bearing)
        bearingUnit = findViewById(R.id.bearingunit)
        eteValue = findViewById(R.id.ete)
        eteUnit = findViewById(R.id.eteunit)
    }


    override fun onResume() {
        super.onResume()

        val settings: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val resources: Resources = getResources()

        val speedIdx = settings.getString(getString(R.string.pref_unitspeed), "0")!!.toInt()
        speedFactor = resources.getStringArray(R.array.speed_factors)[speedIdx].toDouble()
        speedAbbr = resources.getStringArray(R.array.speed_abbrs)[speedIdx]
        speedUnit.text = speedAbbr
        vmgUnit.text = speedAbbr
        val distanceIdx = settings.getString(getString(R.string.pref_unitdistance), "0")!!.toInt()
        StringFormatter.distanceFactor = resources.getStringArray(R.array.distance_factors)[distanceIdx].toDouble()
        StringFormatter.distanceAbbr = resources.getStringArray(R.array.distance_abbrs)[distanceIdx]
        StringFormatter.distanceShortFactor = resources.getStringArray(R.array.distance_factors_short)[distanceIdx].toDouble()
        StringFormatter.distanceShortAbbr = resources.getStringArray(R.array.distance_abbrs_short)[distanceIdx]
        val elevationIdx = settings.getString(getString(R.string.pref_unitelevation), "0")!!.toInt()
        elevationFactor = resources.getStringArray(R.array.elevation_factors)[elevationIdx].toDouble()
        val elevationAbbr = resources.getStringArray(R.array.elevation_abbrs)[elevationIdx]
        elevationUnit.text = elevationAbbr
        trackUnit.text = if (application.angleType == 0) "deg" else getString(R.string.degmag)
        bearingUnit.text = if (application.angleType == 0) "deg" else getString(R.string.degmag)
        courseUnit.text = if (application.angleType == 0) "deg" else getString(R.string.degmag)
        val proximity = settings.getString(getString(R.string.pref_navigation_proximity), getString(R.string.def_navigation_proximity))!!.toInt()

        hsiView.setProximity(proximity)

        bindService(Intent(this, LocationService::class.java), locationConnection, BIND_AUTO_CREATE)
        bindService(Intent(this, NavigationService::class.java), navigationConnection, BIND_AUTO_CREATE)

        val lock = settings.getBoolean(getString(R.string.pref_wakelock), resources.getBoolean(R.bool.def_wakelock))
        if (lock)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onPause() {
        super.onPause()
        if (locationService != null) {
            locationService!!.unregisterLocationCallback(locationListener)
            unbindService(locationConnection)
            locationService = null
        }
        unregisterReceiver(navigationReceiver)
        unbindService(navigationConnection)
    }

    private fun updateNavigationInfo(full: Boolean) {
        if (full) {
            val course = application.fixDeclination(navigationService!!.navCourse).toFloat()
            hsiView.setNavigating(
                if (navigationService!!.isNavigatingViaRoute()) 2
                else if (navigationService!!.isNavigating()) 1
                else 0
            )
            hsiView.setCourse(course)
            if (navigationService!!.isNavigatingViaRoute())
                courseValue.text = Math.round(course).toString()
            else
                courseValue.text = "--"
        }
        if (navigationService!!.isNavigating()) {
            val bearing = application.fixDeclination(navigationService!!.navBearing).toFloat()
            hsiView.setBearing(bearing)
            hsiView.setXtk(if (navigationService!!.navXTK == Double.NEGATIVE_INFINITY) 0f else navigationService!!.navXTK.toFloat())
            bearingValue.text = Math.round(bearing).toString()
            val dist = StringFormatter.distanceC(navigationService!!.navDistance)
            distanceValue.text = dist[0]
            distanceUnit.text = dist[1]
            if (navigationService!!.navXTK == Double.NEGATIVE_INFINITY) {
                xtkValue.text = "--"
                xtkUnit.text = "--"
            } else {
                val xtksym = if (navigationService!!.navXTK == 0.0) "" else if (navigationService!!.navXTK > 0) "R" else "L"
                val xtks = StringFormatter.distanceC(Math.abs(navigationService!!.navXTK))
                xtkValue.text = xtks[0] + xtksym
                xtkUnit.text = xtks[1]
            }
            val vmg = navigationService!!.avvmg * speedFactor
            vmgValue.text = Math.round(vmg).toString()
            val ete = StringFormatter.timeSec(navigationService!!.navETE)
            eteValue.text = ete[0]
            eteUnit.text = ete[1]
        } else {
            bearingValue.text = "--"
            distanceValue.text = "--"
            distanceUnit.text = "--"
            xtkValue.text = "--"
            xtkUnit.text = "--"
            vmgValue.text = "--"
            eteValue.text = "--"
            eteUnit.text = "--"
        }
    }

    private val navigationConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            navigationService = (service as NavigationService.LocalBinder).getService()
            ContextCompat.registerReceiver(this@HSIActivity, navigationReceiver, IntentFilter(BaseNavigationService.BROADCAST_NAVIGATION_STATUS), ContextCompat.RECEIVER_NOT_EXPORTED)
            ContextCompat.registerReceiver(this@HSIActivity, navigationReceiver, IntentFilter(BaseNavigationService.BROADCAST_NAVIGATION_STATE), ContextCompat.RECEIVER_NOT_EXPORTED)
            Log.d("ANDROZIC", "Navigation broadcast receiver registered")
            runOnUiThread {
                updateNavigationInfo(true)
            }
        }

        override fun onServiceDisconnected(className: ComponentName) {
            unregisterReceiver(navigationReceiver)
            navigationService = null
        }
    }

    private val navigationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.e("ANDROZIC", "Broadcast: " + intent.action)
            if (intent.action == BaseNavigationService.BROADCAST_NAVIGATION_STATE) {
                runOnUiThread {
                    updateNavigationInfo(true)
                }
            }
            if (intent.action == BaseNavigationService.BROADCAST_NAVIGATION_STATUS) {
                runOnUiThread {
                    updateNavigationInfo(false)
                }
            }
        }
    }

    private val locationConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            locationService = service as ILocationService
            locationService!!.registerLocationCallback(locationListener)
        }

        override fun onServiceDisconnected(className: ComponentName) {
            locationService = null
        }
    }

    private val locationListener = object : ILocationListener {
        override fun onGpsStatusChanged(provider: String, status: Int, fsats: Int, tsats: Int) {}

        override fun onLocationChanged(loc: Location, continous: Boolean, geoid: Boolean, smoothspeed: Float, avgspeed: Float) {
            runOnUiThread {
                val track = application.fixDeclination(loc.bearing.toDouble())
                hsiView.setAzimuth(track.toFloat())
                val s = (loc.speed.toDouble() * speedFactor)
                val e = (loc.altitude.toDouble() * elevationFactor)
                speedValue.text = Math.round(s).toString()
                trackValue.text = Math.round(track).toString()
                elevationValue.text = Math.round(e).toString()
            }
        }

        override fun onProviderChanged(provider: String) {}

        override fun onProviderDisabled(provider: String) {}

        override fun onProviderEnabled(provider: String) {}
    }
}