package com.borkozic.waypoint

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.borkozic.BaseApplication
import com.borkozic.Borkozic
import com.borkozic.R
import com.borkozic.data.Waypoint
import com.borkozic.data.WaypointSet
import com.borkozic.navigation.BaseNavigationService
import com.borkozic.navigation.NavigationService
import com.borkozic.ui.BorkozicTheme
import com.borkozic.util.StringFormatter

class WaypointListActivity : ComponentActivity(), OnWaypointActionListener {

    companion object {
        const val RESULT_LOAD_WAYPOINTS = 1
    }

    private lateinit var application: Borkozic

    // Force recomposition when returning from sub-activities (e.g. Properties rename)
    private var contentVersion by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        application = BaseApplication.getApplication<Borkozic>()!!

        val mode = intent.extras?.getInt("MODE") ?: WaypointList.MODE_MANAGE
        application.saveWaypoints()

        setContent {
            var themeVersion by remember { mutableStateOf(0) }
            BorkozicTheme(listType = "waypoint", themeVersion = themeVersion) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    WaypointListScreen(
                        mode = mode,
                        contentVersion = contentVersion,
                        themeVersion = themeVersion,
                        onThemeChanged = { themeVersion++ },
                        onWaypointAction = { waypoint, action ->
                            handleWaypointAction(waypoint, action)
                        },
                        onSetAction = { set, idx, action ->
                            handleSetAction(set, idx, action)
                        },
                        onLoadWaypoints = {
                            startActivityForResult(
                                Intent(this@WaypointListActivity, WaypointFileList::class.java),
                                RESULT_LOAD_WAYPOINTS
                            )
                        },
                        onNewWaypoint = {
                            startActivityForResult(
                                Intent(this@WaypointListActivity, WaypointProperties::class.java)
                                    .putExtra("INDEX", -1), 0
                            )
                        },
                        onNewWaypointSet = { name ->
                            showNewSetDialog(name)
                        },
                        onProjectWaypoint = {
                            startActivityForResult(
                                Intent(this@WaypointListActivity, WaypointProject::class.java), 0
                            )
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Trigger recomposition so renamed items appear immediately
        contentVersion++
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            RESULT_LOAD_WAYPOINTS -> {
                if (resultCode == RESULT_OK) finish()
            }
        }
    }

    private fun showNewSetDialog(defaultName: String) {
        val textEntryView = EditText(this)
        textEntryView.isSingleLine = true
        textEntryView.setPadding(8, 0, 8, 0)
        AlertDialog.Builder(this)
            .setTitle(R.string.name)
            .setView(textEntryView)
            .setPositiveButton(R.string.ok) { _, _ ->
                val name = textEntryView.text.toString()
                if (name.isNotEmpty()) {
                    application.addWaypointSet(WaypointSet(name))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create().show()
    }

    private fun handleWaypointAction(waypoint: Waypoint, action: WaypointAction) {
        when (action) {
            is WaypointAction.View -> onWaypointView(waypoint)
            is WaypointAction.Navigate -> onWaypointNavigate(waypoint)
            is WaypointAction.Edit -> onWaypointEdit(waypoint)
            is WaypointAction.Share -> onWaypointShare(waypoint)
            is WaypointAction.Remove -> onWaypointRemove(waypoint)
        }
    }

    private fun handleSetAction(set: WaypointSet, idx: Int, action: WaypointSetAction) {
        when (action) {
            is WaypointSetAction.Clear -> {
                application.clearWaypoints(set)
                application.saveWaypoints(set)
            }
            is WaypointSetAction.Remove -> {
                if (idx > 0) {
                    application.removeWaypointSet(idx)
                }
            }
        }
    }

    override fun onWaypointView(waypoint: Waypoint) {
        application.ensureVisible(waypoint)
        finish()
    }

    override fun onWaypointNavigate(waypoint: Waypoint) {
        val intent = Intent(application, NavigationService::class.java)
            .setAction(BaseNavigationService.NAVIGATE_MAPOBJECT)
        intent.putExtra(BaseNavigationService.EXTRA_NAME, waypoint.name)
        intent.putExtra(BaseNavigationService.EXTRA_LATITUDE, waypoint.latitude)
        intent.putExtra(BaseNavigationService.EXTRA_LONGITUDE, waypoint.longitude)
        intent.putExtra(BaseNavigationService.EXTRA_PROXIMITY, waypoint.proximity)
        application.startService(intent)
        finish()
    }

    override fun onWaypointEdit(waypoint: Waypoint) {
        val index = application.getWaypointIndex(waypoint)
        startActivity(
            Intent(application, WaypointProperties::class.java)
                .putExtra("INDEX", index)
        )
    }

    override fun onWaypointShare(waypoint: Waypoint) {
        val i = Intent(android.content.Intent.ACTION_SEND)
        i.type = "text/plain"
        i.putExtra(Intent.EXTRA_SUBJECT, R.string.currentloc)
        val coords = StringFormatter.coordinates(
            application.coordinateFormat, " ",
            waypoint.latitude, waypoint.longitude
        )
        i.putExtra(Intent.EXTRA_TEXT, waypoint.name + " @ " + coords)
        startActivity(Intent.createChooser(i, getString(R.string.menu_share)))
    }

    override fun onWaypointRemove(waypoint: Waypoint) {
        application.removeWaypoint(waypoint)
    }
}
