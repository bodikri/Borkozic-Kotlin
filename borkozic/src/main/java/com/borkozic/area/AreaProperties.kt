package com.borkozic.area

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.borkozic.BaseApplication
import com.borkozic.Borkozic
import com.borkozic.R
import com.borkozic.ui.BorkozicTheme

class AreaProperties : ComponentActivity() {

    companion object {
        private const val MIN_VALUE = 10
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val index = intent.extras!!.getInt("index")
        val editAfterSave = intent.extras?.getBoolean("editAfterSave", false) ?: false
        val application = BaseApplication.getApplication<Borkozic>()!!
        val area = application.getArea(index)!!
        val defTransparency = if (area.AreaTransperency < 0) resources.getInteger(R.integer.def_area_transparensy) else area.AreaTransperency
        val defLineColor = resources.getColor(R.color.arealinecolor)
        val defFillColor = resources.getColor(R.color.areacolor)

        setContent {
            BorkozicTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AreaPropertiesScreen(
                        area = area,
                        defaultLineColor = defLineColor,
                        defaultFillColor = defFillColor,
                        defaultTransparency = defTransparency,
                        onSave = {
                            setResult(RESULT_OK, Intent().putExtra("index", index).putExtra("editAfterSave", editAfterSave))
                            finish()
                        },
                        onCancel = { finish() }
                    )
                }
            }
        }
    }
}
