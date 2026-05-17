/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2012 Andrey Novikov <http://andreynovikov.info/>
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

package com.borkozic.waypoint

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.*
import android.widget.*
import com.borkozic.Borkozic
import com.borkozic.R
import com.borkozic.data.Waypoint
import com.borkozic.data.WaypointSet
import com.borkozic.ui.ColorButton
import com.borkozic.ui.MarkerPickerActivity
import com.borkozic.util.StringFormatter
import com.jhlabs.map.GeodeticPosition
import com.jhlabs.map.ReferenceException
import com.jhlabs.map.UTMReference
import java.io.File
import java.util.*

class WaypointProperties : Activity(), AdapterView.OnItemSelectedListener {

    private var waypoint: Waypoint? = null
    private lateinit var tabHost: TabHost
    private lateinit var name: TextView
    private lateinit var description: TextView
    private lateinit var altitude: TextView
    private lateinit var proximity: TextView
    private lateinit var markercolor: ColorButton
    private lateinit var textcolor: ColorButton
    private lateinit var coordDeg: ViewGroup
    private lateinit var coordUtm: ViewGroup
    private lateinit var coordLatDeg: ViewGroup
    private lateinit var coordLatMin: ViewGroup
    private lateinit var coordLatSec: ViewGroup
    private lateinit var coordLonDeg: ViewGroup
    private lateinit var coordLonMin: ViewGroup
    private lateinit var coordLonSec: ViewGroup
    private var curFormat: Int = -1
    private var iconValue: String? = null
    private var route: Int = 0
    private var defMarkerColor: Int = 0
    private var defTextColor: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.act_waypoint_properties)
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        val index = intent.extras!!.getInt("INDEX")
        route = intent.extras!!.getInt("ROUTE")

        tabHost = findViewById<TabHost>(R.id.tabhost)
        tabHost.setup()
        tabHost.addTab(tabHost.newTabSpec("main").setIndicator(getString(R.string.primary)).setContent(R.id.properties))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB)
            tabHost.tabWidget.getChildAt(0).layoutParams.height = 50

        if (route == 0) {
            tabHost.addTab(tabHost.newTabSpec("advanced").setIndicator(getString(R.string.advanced)).setContent(R.id.advanced))
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB)
                tabHost.tabWidget.getChildAt(1).layoutParams.height = 50
        } else {
            findViewById<View>(android.R.id.tabs).visibility = View.GONE
        }

        if (savedInstanceState != null) {
            tabHost.setCurrentTabByTag(savedInstanceState.getString("tab")!!)
        }

        val application = application as Borkozic
        if (route > 0) {
            waypoint = application.getRoute(route - 1)!!.waypoints[index]
            findViewById<View>(R.id.advanced).visibility = View.GONE
            findViewById<View>(R.id.icon_container).visibility = View.GONE
        } else if (index >= 0) {
            waypoint = application.getWaypoint(index)
        } else {
            waypoint = Waypoint()
            waypoint!!.date = Calendar.getInstance().time
        }

        name = findViewById<TextView>(R.id.name_text)
        name.text = waypoint!!.name
        description = findViewById<TextView>(R.id.description_text)
        description.text = waypoint!!.description

        altitude = findViewById<TextView>(R.id.altitude_text)
        if (waypoint!!.altitude.toInt() != Int.MIN_VALUE)
            altitude.text = waypoint!!.altitude.toString()
        proximity = findViewById<TextView>(R.id.proximity_text)
        if (waypoint!!.proximity != 0)
            proximity.text = waypoint!!.proximity.toString()

        iconValue = null
        val icon = findViewById<ImageButton>(R.id.icon_button)
        icon.setImageDrawable(resources.getDrawable(R.drawable.ic_action_halt))
        if (application.iconsEnabled) {
            if (waypoint!!.drawImage) {
                val b = BitmapFactory.decodeFile(application.iconPath + File.separator + waypoint!!.image)
                if (b != null) {
                    icon.setImageBitmap(b)
                    iconValue = waypoint!!.image
                }
            }
            icon.setOnClickListener(iconOnClickListener)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
                registerForContextMenu(icon)
            }
        } else {
            icon.isEnabled = false
        }

        val set = if (waypoint!!.set == null) 0 else application.waypointSets.indexOf(waypoint!!.set)

        val items = ArrayList<String>()
        for (wptset in application.waypointSets) {
            items.add(wptset.name)
        }

        val spinner = findViewById<Spinner>(R.id.set_spinner)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, items)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.setSelection(set)

        var markerColorValue = waypoint!!.backcolor
        var textColorValue = waypoint!!.textcolor

        val settings = PreferenceManager.getDefaultSharedPreferences(this)
        defMarkerColor = settings.getInt(getString(R.string.pref_waypoint_color), resources.getColor(R.color.waypoint))
        defTextColor = settings.getInt(getString(R.string.pref_waypoint_namecolor), resources.getColor(R.color.waypointtext))

        if (markerColorValue == Int.MIN_VALUE) markerColorValue = defMarkerColor
        if (textColorValue == Int.MIN_VALUE) textColorValue = defTextColor

        markercolor = findViewById<ColorButton>(R.id.markercolor_button)
        markercolor.setColor(markerColorValue, defMarkerColor)

        textcolor = findViewById<ColorButton>(R.id.textcolor_button)
        textcolor.setColor(textColorValue, defTextColor)

        coordDeg = findViewById<ViewGroup>(R.id.coord_deg)
        coordUtm = findViewById<ViewGroup>(R.id.coord_utm)
        coordLatDeg = findViewById<ViewGroup>(R.id.coord_lat_deg)
        coordLatMin = findViewById<ViewGroup>(R.id.coord_lat_min)
        coordLatSec = findViewById<ViewGroup>(R.id.coord_lat_sec)
        coordLonDeg = findViewById<ViewGroup>(R.id.coord_lon_deg)
        coordLonMin = findViewById<ViewGroup>(R.id.coord_lon_min)
        coordLonSec = findViewById<ViewGroup>(R.id.coord_lon_sec)

        val coordformat = findViewById<Spinner>(R.id.coordformat_spinner)
        coordformat.onItemSelectedListener = this
        coordformat.setSelection(application.coordinateFormat)

        findViewById<Button>(R.id.done_button).setOnClickListener(doneOnClickListener)
        findViewById<Button>(R.id.cancel_button).setOnClickListener { finish() }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        iconValue = savedInstanceState.getString("icon")
        val icon = findViewById<ImageButton>(R.id.icon_button)
        if (iconValue != null) {
            val application = application as Borkozic
            val b = BitmapFactory.decodeFile(application.iconPath + File.separator + iconValue!!)
            if (b != null) icon.setImageBitmap(b)
        } else {
            icon.setImageDrawable(resources.getDrawable(R.drawable.ic_action_halt))
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("tab", tabHost.currentTabTag)
        outState.putString("icon", iconValue)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 0 && resultCode == RESULT_OK) {
            iconValue = data?.getStringExtra("icon")
            val icon = findViewById<ImageButton>(R.id.icon_button)
            val application = application as Borkozic
            val b = BitmapFactory.decodeFile(application.iconPath + File.separator + iconValue)
            if (b != null) icon.setImageBitmap(b)
        }
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        val inflater = menuInflater
        inflater.inflate(R.menu.marker_popup, menu)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.change -> startActivityForResult(Intent(this, MarkerPickerActivity::class.java), 0)
            R.id.remove -> {
                iconValue = null
                val icon = findViewById<ImageButton>(R.id.icon_button)
                icon.setImageDrawable(resources.getDrawable(R.drawable.ic_action_halt))
            }
        }
        return true
    }

    private val iconOnClickListener = View.OnClickListener {
        @SuppressLint("NewApi")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            it.showContextMenu()
        } else {
            val popup = PopupMenu(this@WaypointProperties, it)
            popup.menuInflater.inflate(R.menu.marker_popup, popup.menu)
            popup.setOnMenuItemClickListener { item -> onContextItemSelected(item) }
            popup.show()
        }
    }

    private val doneOnClickListener = View.OnClickListener {
        try {
            val application = application as Borkozic

            if (name.text.isEmpty()) return@OnClickListener

            waypoint!!.name = name.text.toString()
            waypoint!!.description = description.text.toString()
            val coords = getLatLon()
            waypoint!!.latitude = coords.lat
            waypoint!!.longitude = coords.lon

            try {
                val p = proximity.text.toString()
                waypoint!!.proximity = if (p.isEmpty()) 0 else p.toInt()
            } catch (e: NumberFormatException) {
                e.printStackTrace()
            }

            try {
                val a = altitude.text.toString()
                waypoint!!.altitude = if (a.isEmpty()) Int.MIN_VALUE.toDouble() else a.toDouble()
            } catch (e: NumberFormatException) {
                e.printStackTrace()
            }

            if (iconValue == null) {
                waypoint!!.image = ""
                waypoint!!.drawImage = false
            } else {
                waypoint!!.image = iconValue!!
                waypoint!!.drawImage = true
            }
            val markerColorValue = markercolor.getColor()
            if (markerColorValue != defMarkerColor) waypoint!!.backcolor = markerColorValue
            val textColorValue = textcolor.getColor()
            if (textColorValue != defTextColor) waypoint!!.textcolor = textColorValue

            var index = -1
            if (route == 0) {
                if (waypoint!!.set == null) {
                    application.addWaypoint(waypoint!!)
                    index = application.getWaypointIndex(waypoint!!)
                }
                val set = findViewById<Spinner>(R.id.set_spinner).selectedItemPosition
                waypoint!!.set = application.waypointSets[set]
            }

            if (index != -1) {
                setResult(RESULT_OK, Intent().putExtra("index", index))
            } else {
                setResult(RESULT_OK)
            }
            finish()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(baseContext, "Invalid input", Toast.LENGTH_LONG).show()
        }
    }

    private fun getLatLon(): GeodeticPosition {
        val coords = GeodeticPosition()
        when (curFormat) {
            -1 -> {
                coords.lat = waypoint!!.latitude
                coords.lon = waypoint!!.longitude
            }
            0 -> {
                coords.lat = findViewById<TextView>(R.id.lat_dd_text).text.toString().toDouble()
                coords.lon = findViewById<TextView>(R.id.lon_dd_text).text.toString().toDouble()
            }
            1 -> {
                var degrees = findViewById<TextView>(R.id.lat_md_text).text.toString().toInt()
                var minutes = findViewById<TextView>(R.id.lat_mm_text).text.toString().toDouble() / 60
                if (degrees != 0) minutes *= Math.signum(degrees.toDouble())
                coords.lat = degrees + minutes
                degrees = findViewById<TextView>(R.id.lon_md_text).text.toString().toInt()
                minutes = findViewById<TextView>(R.id.lon_mm_text).text.toString().toDouble() / 60
                if (degrees != 0) minutes *= Math.signum(degrees.toDouble())
                coords.lon = degrees + minutes
            }
            2 -> {
                var degrees = findViewById<TextView>(R.id.lat_sd_text).text.toString().toInt()
                var minutes = findViewById<TextView>(R.id.lat_sm_text).text.toString().toDouble()
                val seconds = findViewById<TextView>(R.id.lat_ss_text).text.toString().toDouble() / 60
                minutes = ((minutes + seconds) / 60)
                if (degrees != 0) minutes = (minutes * Math.signum(degrees.toDouble()))
                coords.lat = degrees + minutes
                degrees = findViewById<TextView>(R.id.lon_sd_text).text.toString().toInt()
                minutes = findViewById<TextView>(R.id.lon_sm_text).text.toString().toDouble()
                val seconds2 = findViewById<TextView>(R.id.lon_ss_text).text.toString().toDouble() / 60
                minutes = ((minutes + seconds2) / 60)
                if (degrees != 0) minutes = (minutes * Math.signum(degrees.toDouble()))
                coords.lon = degrees + minutes
            }
            3 -> {
                val easting = findViewById<TextView>(R.id.utm_easting_text).text.toString().toDouble()
                val northing = findViewById<TextView>(R.id.utm_northing_text).text.toString().toDouble()
                val zone = findViewById<TextView>(R.id.utm_zone_text).text.toString().toInt()
                val hemi = findViewById<RadioButton>(R.id.utm_hemi_s).isChecked
                val band = UTMReference.getUTMNorthingZoneLetter(hemi, northing)
                try {
                    val utm = UTMReference(zone, band, easting, northing)
                    val latLng = utm.toLatLng()
                    coords.lat = latLng.lat
                    coords.lon = latLng.lon
                } catch (e: ReferenceException) {
                    Toast.makeText(baseContext, e.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
        return coords
    }

    override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
        val coords = getLatLon()

        when (position) {
            0 -> {
                coordUtm.visibility = View.GONE
                coordDeg.visibility = View.VISIBLE
                findViewById<TextView>(R.id.lat_dd_text).text = StringFormatter.coordinate(0, coords.lat)
                coordLatMin.visibility = View.GONE
                coordLatSec.visibility = View.GONE
                coordLatDeg.visibility = View.VISIBLE
                findViewById<TextView>(R.id.lon_dd_text).text = StringFormatter.coordinate(0, coords.lon)
                coordLonMin.visibility = View.GONE
                coordLonSec.visibility = View.GONE
                coordLonDeg.visibility = View.VISIBLE
            }
            1 -> {
                coordUtm.visibility = View.GONE
                coordDeg.visibility = View.VISIBLE
                var degrees = Math.floor(Math.abs(coords.lat)).toInt()
                var min = (Math.abs(coords.lat) - degrees) * 60
                degrees = (degrees * Math.signum(coords.lat)).toInt()
                findViewById<TextView>(R.id.lat_md_text).text = degrees.toString()
                findViewById<TextView>(R.id.lat_mm_text).text = min.toString()
                coordLatDeg.visibility = View.GONE
                coordLatSec.visibility = View.GONE
                coordLatMin.visibility = View.VISIBLE
                degrees = Math.floor(Math.abs(coords.lon)).toInt()
                min = (Math.abs(coords.lon) - degrees) * 60
                degrees = (degrees * Math.signum(coords.lon)).toInt()
                findViewById<TextView>(R.id.lon_md_text).text = degrees.toString()
                findViewById<TextView>(R.id.lon_mm_text).text = min.toString()
                coordLonDeg.visibility = View.GONE
                coordLonSec.visibility = View.GONE
                coordLonMin.visibility = View.VISIBLE
            }
            2 -> {
                coordUtm.visibility = View.GONE
                coordDeg.visibility = View.VISIBLE
                var degrees = Math.floor(Math.abs(coords.lat)).toInt()
                var min = (Math.abs(coords.lat) - degrees) * 60
                degrees = (degrees * Math.signum(coords.lat)).toInt()
                val minutes = Math.floor(min).toInt()
                val seconds = (min - minutes) * 60
                findViewById<TextView>(R.id.lat_sd_text).text = degrees.toString()
                findViewById<TextView>(R.id.lat_sm_text).text = minutes.toString()
                findViewById<TextView>(R.id.lat_ss_text).text = seconds.toString()
                coordLatDeg.visibility = View.GONE
                coordLatMin.visibility = View.GONE
                coordLatSec.visibility = View.VISIBLE
                degrees = Math.floor(Math.abs(coords.lon)).toInt()
                min = (Math.abs(coords.lon) - degrees) * 60
                degrees = (degrees * Math.signum(coords.lon)).toInt()
                val minutes2 = Math.floor(min).toInt()
                val seconds2 = (min - minutes2) * 60
                findViewById<TextView>(R.id.lon_sd_text).text = degrees.toString()
                findViewById<TextView>(R.id.lon_sm_text).text = minutes2.toString()
                findViewById<TextView>(R.id.lon_ss_text).text = seconds2.toString()
                coordLonDeg.visibility = View.GONE
                coordLonMin.visibility = View.GONE
                coordLonSec.visibility = View.VISIBLE
            }
            3 -> {
                try {
                    val utm = UTMReference.toUTMRef(GeodeticPosition(coords.lat, coords.lon))
                    coordDeg.visibility = View.GONE
                    coordUtm.visibility = View.VISIBLE
                    findViewById<TextView>(R.id.utm_easting_text).text = Math.round(utm.easting).toString()
                    findViewById<TextView>(R.id.utm_northing_text).text = Math.round(utm.northing).toString()
                    findViewById<TextView>(R.id.utm_zone_text).text = utm.lngZone.toString()
                    findViewById<RadioButton>(if (utm.isSouthernHemisphere) R.id.utm_hemi_s else R.id.utm_hemi_n).isChecked = true
                } catch (e: ReferenceException) {
                    Toast.makeText(baseContext, e.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
        curFormat = position
    }

    override fun onNothingSelected(parent: AdapterView<*>) {}

    override fun onDestroy() {
        super.onDestroy()
        waypoint = null
    }
}
