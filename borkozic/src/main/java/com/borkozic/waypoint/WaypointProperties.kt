package com.borkozic.waypoint

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.borkozic.BaseApplication
import com.borkozic.Borkozic
import com.borkozic.R
import com.borkozic.data.Waypoint
import com.borkozic.ui.BorkozicTheme
import com.borkozic.ui.MarkerPickerActivity
import java.util.Calendar

class WaypointProperties : ComponentActivity() {

    private var waypoint: Waypoint? = null
    private var route: Int = 0
    private var area: Int = 0
    private var defMarkerColor: Int = 0
    private var defTextColor: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val index = intent.extras!!.getInt("INDEX")
        route = intent.extras!!.getInt("ROUTE")
        area = intent.extras!!.getInt("AREA")

        val application = BaseApplication.getApplication<Borkozic>()!!

        waypoint = if (area > 0) {
            // Точката е част от зона (area) — не от route
            application.getArea(area - 1)!!.waypoints[index]
        } else if (route > 0) {
            application.getRoute(route - 1)!!.waypoints[index]
        } else if (index >= 0) {
            application.getWaypoint(index)
        } else {
            val wp = Waypoint()
            wp.date = Calendar.getInstance().time
            wp
        }

        var iconState by mutableStateOf(
            if (application.iconsEnabled && waypoint!!.drawImage) waypoint!!.image else null
        )

        val settings = android.preference.PreferenceManager.getDefaultSharedPreferences(this)
        defMarkerColor = settings.getInt(getString(R.string.pref_waypoint_color), resources.getColor(R.color.waypoint))
        defTextColor = settings.getInt(getString(R.string.pref_waypoint_namecolor), resources.getColor(R.color.waypointtext))

        setContent {
            BorkozicTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    WaypointPropertiesScreen(
                        routeIdx = route,
                        waypoint = waypoint!!,
                        iconValue = iconState,
                        defMarkerColor = defMarkerColor,
                        defTextColor = defTextColor,
                        onIconChanged = { iconState = it },
                        onSave = { wp ->
                            val wpIndex = application.getWaypointIndex(wp)
                            if (wpIndex != -1) {
                                setResult(RESULT_OK, Intent().putExtra("index", wpIndex))
                            } else {
                                setResult(RESULT_OK)
                            }
                            finish()
                        },
                        onCancel = { finish() }
                    )
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 0 && resultCode == RESULT_OK) {
            // iconState updated via MarkerPicker result — handled through recomposition
        }
    }

    fun showIconMenu(anchorView: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            val popup = PopupMenu(this, anchorView)
            popup.menuInflater.inflate(R.menu.marker_popup, popup.menu)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.change -> {
                        startActivityForResult(Intent(this, MarkerPickerActivity::class.java), 0)
                        true
                    }
                    R.id.remove -> {
                        // handled via onIconChanged callback
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }
}
