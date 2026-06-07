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

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import com.borkozic.location.BaseLocationService
import android.content.Intent
import android.content.ServiceConnection
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.IBinder
import android.text.format.DateFormat
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.TextView
import com.borkozic.location.ILocationListener
import com.borkozic.location.ILocationService
import com.borkozic.location.LocationService
import com.borkozic.util.Astro
import com.borkozic.util.StringFormatter
import java.util.*

class Information : Activity() {
    private var locationService: ILocationService? = null
    private lateinit var satsValue: TextView
    private lateinit var lastfixValue: TextView
    private lateinit var providerValue: TextView
    private lateinit var latitudeValue: TextView
    private lateinit var longitudeValue: TextView
    private lateinit var accuracyValue: TextView
    private lateinit var sunriseValue: TextView
    private lateinit var sunsetValue: TextView
    private lateinit var declinationValue: TextView
    private lateinit var hdopValue: TextView
    private lateinit var vdopValue: TextView
    private lateinit var zeroValue: TextView
    protected lateinit var application: Borkozic

    protected lateinit var shake: Animation

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.act_information)

        application = getApplication() as Borkozic
        shake = AnimationUtils.loadAnimation(this, R.anim.shake)

        satsValue = findViewById(R.id.sats)
        lastfixValue = findViewById(R.id.lastfix)
        accuracyValue = findViewById(R.id.accuracy)
        providerValue = findViewById(R.id.provider)
        latitudeValue = findViewById(R.id.latitude)
        longitudeValue = findViewById(R.id.longitude)
        sunriseValue = findViewById(R.id.sunrise)
        sunsetValue = findViewById(R.id.sunset)
        declinationValue = findViewById(R.id.declination)
        hdopValue = findViewById(R.id.hdop)
        vdopValue = findViewById(R.id.vdop)
        zeroValue = findViewById(R.id.set_zero_level)

        val update = findViewById<Button>(R.id.almanac_button)
        update.setOnClickListener(updateOnClickListener)
    }

    override fun onResume() {
        super.onResume()
        bindService(Intent(this, LocationService::class.java), locationConnection, BIND_AUTO_CREATE)
    }

    override fun onPause() {
        super.onPause()
        if (locationService != null) {
            locationService?.unregisterLocationCallback(locationListener)
            unbindService(locationConnection)
            locationService = null
        }
    }

    private val locationConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            locationService = service as ILocationService
            locationService?.registerLocationCallback(locationListener)
        }

        override fun onServiceDisconnected(className: ComponentName) {
            locationService = null
        }
    }

    private val locationListener = object : ILocationListener {
        override fun onGpsStatusChanged(provider: String, status: Int, fsats: Int, tsats: Int) {
            Log.d("Information", "onGpsStatusChanged: provider=$provider status=$status fsats=$fsats tsats=$tsats")
            runOnUiThread {
                when (status) {
                    BaseLocationService.GPS_OK -> satsValue.text = "$fsats/$tsats"
                    BaseLocationService.GPS_OFF -> satsValue.setText(R.string.sat_stop)
                    BaseLocationService.GPS_SEARCHING -> {
                        satsValue.text = "$fsats/$tsats"
                        satsValue.startAnimation(shake)
                    }
                }
                if (locationService != null) {
                    val hdop = locationService!!.getHDOP()
                    if (!java.lang.Float.isNaN(hdop)) hdopValue.text = String.format("%.1f", hdop)
                    val vdop = locationService!!.getVDOP()
                    if (!java.lang.Float.isNaN(vdop)) vdopValue.text = String.format("%.1f", vdop)
                }
            }
        }

        override fun onLocationChanged(loc: Location, continous: Boolean, geoid: Boolean, smoothspeed: Float, avgspeed: Float) {
            runOnUiThread {
                val date = Date(loc.time)
                lastfixValue.text = DateFormat.getDateFormat(this@Information).format(date) + " " +
                        DateFormat.getTimeFormat(this@Information).format(date)
                providerValue.text = if (loc.provider != null) loc.provider else "N/A"
                // FIXME Needs UTM support here
                latitudeValue.text = StringFormatter.coordinate(application.coordinateFormat, loc.latitude)
                longitudeValue.text = StringFormatter.coordinate(application.coordinateFormat, loc.longitude)
                accuracyValue.text = if (loc.hasAccuracy()) StringFormatter.distanceH(loc.accuracy.toDouble(), "%.1f", 1000) else "N/A"
                zeroValue.text = application.zeroLevel.toString()

                val now = GregorianCalendar.getInstance(TimeZone.getDefault())
                val sunrise = Astro.computeSunriseTime(application.getZenith(), loc, now)
                val sunset = Astro.computeSunsetTime(application.getZenith(), loc, now)

                sunriseValue.text = if (java.lang.Double.isNaN(sunrise)) {
                    getString(R.string.never)
                } else {
                    Astro.getLocalTimeAsString(sunrise)
                }
                
                sunsetValue.text = if (java.lang.Double.isNaN(sunset)) {
                    getString(R.string.never)
                } else {
                    Astro.getLocalTimeAsString(sunset)
                }
                
                val declination = application.declination
                declinationValue.text = String.format("%+.1f°", declination)
            }
        }

        override fun onProviderChanged(provider: String) {
            // TODO Auto-generated method stub
        }

        override fun onProviderDisabled(provider: String) {
            // TODO Auto-generated method stub
        }

        override fun onProviderEnabled(provider: String) {
            // TODO Auto-generated method stub
        }
    }

    private val updateOnClickListener = View.OnClickListener { v ->
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (locationManager != null) {
            locationManager.sendExtraCommand(LocationManager.GPS_PROVIDER, "force_xtra_injection", null)
            locationManager.sendExtraCommand(LocationManager.GPS_PROVIDER, "force_time_injection", null)
        }
    }
}