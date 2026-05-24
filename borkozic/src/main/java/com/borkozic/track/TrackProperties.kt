package com.borkozic.track

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.borkozic.BaseApplication
import com.borkozic.Borkozic
import com.borkozic.R
import com.borkozic.ui.BorkozicTheme

class TrackProperties : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val index = intent.extras!!.getInt("INDEX")
        val application = BaseApplication.getApplication<Borkozic>()!!
        val track = application.getTrack(index)!!
        val defaultColor = resources.getColor(R.color.currenttrack)

        setContent {
            BorkozicTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    TrackPropertiesScreen(
                        track = track,
                        defaultColor = defaultColor,
                        onSave = {
                            setResult(RESULT_OK)
                            finish()
                        },
                        onCancel = { finish() }
                    )
                }
            }
        }
    }
}
