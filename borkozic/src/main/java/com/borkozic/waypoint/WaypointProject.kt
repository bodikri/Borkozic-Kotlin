package com.borkozic.waypoint
import com.borkozic.BaseApplication

import android.app.Activity
import android.content.Intent
import android.hardware.GeomagneticField
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.borkozic.Borkozic
import com.borkozic.R
import com.borkozic.data.Waypoint
import com.borkozic.util.Geo
import com.borkozic.util.StringFormatter
import java.util.Calendar
import java.util.Collections
import java.util.Comparator

class WaypointProject : Activity() {

    private var waypoints: List<Waypoint>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.act_waypoint_project)

        val application: Borkozic = BaseApplication.getApplication<Borkozic>()!!
        waypoints = application.waypoints

        findViewById<TextView>(R.id.name_text).text = "WPT${waypoints!!.size}"

        Collections.sort(waypoints!!, Comparator { o1: Waypoint, o2: Waypoint ->
            java.lang.String.CASE_INSENSITIVE_ORDER.compare(o1.name, o2.name)
        })

        val items = arrayOfNulls<String>((waypoints?.size ?: 0) + 1)
        items[0] = getString(R.string.currentloc)
        var i = 1
        for (wpt in waypoints!!) {
            items[i] = wpt.name
            i++
        }
        var adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, items)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        findViewById<Spinner>(R.id.source_spinner).adapter = adapter

        val distItems = arrayOfNulls<String>(2)
        distItems[0] = StringFormatter.distanceAbbr
        distItems[1] = StringFormatter.distanceShortAbbr
        adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, distItems)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        findViewById<Spinner>(R.id.distance_spinner).adapter = adapter

        val angleItems = resources.getStringArray(R.array.angle_units)
        adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, angleItems)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        findViewById<Spinner>(R.id.bearing_spinner).adapter = adapter
        findViewById<Spinner>(R.id.bearing_spinner).setSelection(application.angleType)

        findViewById<Button>(R.id.done_button).setOnClickListener(doneOnClickListener)
        findViewById<Button>(R.id.cancel_button).setOnClickListener { finish() }
    }

    private val doneOnClickListener = View.OnClickListener { v: View ->
        try {
            val application: Borkozic = BaseApplication.getApplication<Borkozic>()!!
            val waypoint = Waypoint()
            waypoint.name = findViewById<TextView>(R.id.name_text).text.toString()
            var distance = findViewById<TextView>(R.id.distance_text).text.toString().toInt()
            var bearing = findViewById<TextView>(R.id.bearing_text).text.toString().toInt()
            val src = findViewById<Spinner>(R.id.source_spinner).selectedItemPosition
            val df = findViewById<Spinner>(R.id.distance_spinner).selectedItemPosition
            val bf = findViewById<Spinner>(R.id.bearing_spinner).selectedItemPosition
            val loc: DoubleArray

            if (src > 0) {
                loc = DoubleArray(2)
                loc[0] = waypoints!![src - 1].latitude
                loc[1] = waypoints!![src - 1].longitude
            } else {
                loc = application.getLocation()
            }

            if (df == 0) {
                distance = (distance / StringFormatter.distanceFactor * 1000).toInt()
            } else {
                distance = (distance / StringFormatter.distanceShortFactor).toInt()
            }
            if (bf == 1) {
                val mag = GeomagneticField(loc[0].toFloat(), loc[1].toFloat(), 0.0f, System.currentTimeMillis())
                bearing -= mag.declination.toInt()
            }
            val prj = Geo.projection(loc[0], loc[1], distance.toDouble(), bearing.toDouble())
            waypoint.latitude = prj[0]
            waypoint.longitude = prj[1]
            waypoint.date = Calendar.getInstance().time
            application.addWaypoint(waypoint)
            setResult(RESULT_OK, Intent().putExtra("index", application.getWaypointIndex(waypoint)))
            finish()
        } catch (e: Exception) {
            Toast.makeText(baseContext, "Invalid input", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        waypoints = null
    }
}
