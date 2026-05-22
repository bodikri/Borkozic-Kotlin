package com.borkozic.waypoint
import com.borkozic.BaseApplication

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.TextView
import com.borkozic.Borkozic
import com.borkozic.R
import com.borkozic.util.Geo
import com.borkozic.util.StringFormatter

class CoordinatesReceived : Activity(), View.OnClickListener {

    private var lat: Double = 0.0
    private var lon: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestWindowFeature(Window.FEATURE_LEFT_ICON)
        setContentView(R.layout.act_coordinates_received)

        val extras = intent.extras

        val title = extras?.getString("title")
        val sender = extras?.getString("sender")
        lat = extras?.getDouble("lat") ?: 0.0
        lon = extras?.getDouble("lon") ?: 0.0

        if (title != null && title.isNotEmpty()) {
            setTitle(title)
        }
        setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, android.R.drawable.ic_dialog_map)

        val application: Borkozic = BaseApplication.getApplication<Borkozic>()!!
        val ll = application.getLocation()

        findViewById<TextView>(R.id.message).text = getString(R.string.new_coordinates, sender)

        val coords = StringFormatter.coordinates(application.coordinateFormat, " ", lat, lon)
        findViewById<TextView>(R.id.coordinates).text = coords

        var dist = Geo.distance(ll[0], ll[1], lat, lon)
        var bearing = Geo.bearing(ll[0], ll[1], lat, lon)
        bearing = application.fixDeclination(bearing)
        val distance = StringFormatter.distanceH(dist) + " " + StringFormatter.bearingH(bearing)
        findViewById<TextView>(R.id.distance).text = distance

        findViewById<Button>(R.id.show_button).setOnClickListener(this)
        findViewById<Button>(R.id.dismiss_button).setOnClickListener(this)
    }

    override fun onClick(v: View) {
        if (v.id == R.id.show_button) {
            val application: Borkozic = BaseApplication.getApplication<Borkozic>()!!
            application.ensureVisible(lat, lon)
        }
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
