package com.borkozic.area

import android.content.Intent
import android.os.Bundle
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
import com.borkozic.data.Area
import com.borkozic.overlay.AreaOverlay
import com.borkozic.ui.BorkozicTheme

class AreaListActivity : ComponentActivity(), OnAreaActionListener {

    companion object {
        const val RESULT_START_AREA = 1
        const val RESULT_LOAD_AREA = 2
        const val RESULT_AREA_DETAILS = 3
        const val RESULT_AREA_PROPERTIES = 4
    }

    private lateinit var application: Borkozic

    // Force recomposition when returning from sub-activities (e.g. Properties rename)
    private var contentVersion by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        application = BaseApplication.getApplication<Borkozic>()!!

        val mode = intent.extras?.getInt("MODE") ?: AreaList.MODE_MANAGE

        setContent {
            var themeVersion by remember { mutableStateOf(0) }
            BorkozicTheme(listType = "area", themeVersion = themeVersion) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AreaListScreen(
                        mode = mode,
                        contentVersion = contentVersion,
                        themeVersion = themeVersion,
                        onThemeChanged = { themeVersion++ },
                        onAction = { area, action ->
                            handleAreaAction(area, action)
                        },
                        onLoadArea = {
                            startActivityForResult(
                                Intent(this@AreaListActivity, AreaFileList::class.java),
                                RESULT_LOAD_AREA
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
            RESULT_START_AREA, RESULT_AREA_DETAILS -> {
                if (resultCode == RESULT_OK) finish()
            }
            RESULT_LOAD_AREA -> {
                if (resultCode == RESULT_OK) {
                    val indexes = data?.extras?.getIntArray("index") ?: return
                    for (index in indexes) {
                        val newArea = AreaOverlay(this, application.getArea(index)!!)
                        application.areaOverlays.add(newArea)
                    }
                    setResult(RESULT_OK, Intent())
                    finish()
                }
            }
            RESULT_AREA_PROPERTIES -> {
                if (resultCode == RESULT_OK) {
                    // Check if we should open edit mode on map
                    val editAfterSave = data?.extras?.getBoolean("editAfterSave", false) ?: false
                    if (editAfterSave) {
                        val index = data?.extras?.getInt("index") ?: -1
                        if (index >= 0) {
                            val area = application.getArea(index)
                            if (area != null) {
                                area.show = true
                                setResult(RESULT_OK, Intent().putExtra("index", index).putExtra("dir", 0))
                                finish()
                            }
                        }
                    } else {
                        // Just refresh list
                        contentVersion++
                    }
                }
            }
        }
    }

    private fun handleAreaAction(area: Area, action: AreaAction) {
        when (action) {
            is AreaAction.Details -> {
                startActivityForResult(
                    Intent(this, AreaDetails::class.java).putExtra("index", application.getAreaIndex(area)),
                    RESULT_AREA_DETAILS
                )
            }
            is AreaAction.NavigateArea -> {
                startActivityForResult(
                    Intent(this, AreaStart::class.java).putExtra("index", application.getAreaIndex(area)),
                    RESULT_START_AREA
                )
            }
            is AreaAction.Properties -> {
                val isNew = area.waypoints.isEmpty() && !area.isCircleArea()
                val isNewCircle = area.isCircleArea() && area.AreaCenter == null
                startActivityForResult(
                    Intent(this, AreaProperties::class.java)
                        .putExtra("index", application.getAreaIndex(area))
                        .putExtra("editAfterSave", isNew || isNewCircle),
                    RESULT_AREA_PROPERTIES
                )
            }
            is AreaAction.Edit -> {
                area.show = true
                setResult(RESULT_OK, Intent().putExtra("index", application.getAreaIndex(area)).putExtra("dir", 0))
                finish()
            }
            is AreaAction.Save -> {
                startActivity(Intent(this, AreaSave::class.java).putExtra("index", application.getAreaIndex(area)))
            }
            is AreaAction.Remove -> {
                application.removeArea(area)
                contentVersion++
            }
        }
    }

    override fun onAreaDetails(area: Area) {
        startActivityForResult(
            Intent(this, AreaDetails::class.java).putExtra("index", application.getAreaIndex(area)),
            RESULT_AREA_DETAILS
        )
    }

    override fun onAreaNavigate(area: Area) {
        startActivityForResult(
            Intent(this, AreaStart::class.java).putExtra("index", application.getAreaIndex(area)),
            RESULT_START_AREA
        )
    }

    override fun onAreaEdit(area: Area) {
        startActivityForResult(
            Intent(this, AreaProperties::class.java)
                .putExtra("index", application.getAreaIndex(area))
                .putExtra("editAfterSave", false),
            RESULT_AREA_PROPERTIES
        )
    }

    override fun onAreaEditPath(area: Area) {
        area.show = true
        setResult(RESULT_OK, Intent().putExtra("index", application.getAreaIndex(area)))
        finish()
    }

    override fun onAreaSave(area: Area) {
        startActivity(Intent(this, AreaSave::class.java).putExtra("index", application.getAreaIndex(area)))
    }
}
