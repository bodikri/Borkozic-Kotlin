package com.borkozic.track

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
import com.borkozic.data.Track
import com.borkozic.overlay.TrackOverlay
import com.borkozic.ui.BorkozicTheme

class TrackListActivity : ComponentActivity(), OnTrackActionListener {

    companion object {
        const val RESULT_LOAD_TRACK = 1
    }

    private lateinit var application: Borkozic

    // Force recomposition when returning from sub-activities (e.g. Properties rename)
    private var contentVersion by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        application = BaseApplication.getApplication<Borkozic>()!!

        val mode = intent.extras?.getInt("MODE") ?: TrackList.MODE_MANAGE

        setContent {
            var themeVersion by remember { mutableStateOf(0) }
            BorkozicTheme(listType = "track", themeVersion = themeVersion) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TrackListScreen(
                        mode = mode,
                        contentVersion = contentVersion,
                        themeVersion = themeVersion,
                        onThemeChanged = { themeVersion++ },
                        onAction = { track, action ->
                            handleTrackAction(track, action)
                        },
                        onLoadTrack = {
                            startActivityForResult(
                                Intent(this@TrackListActivity, TrackFileList::class.java),
                                RESULT_LOAD_TRACK
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

        if (requestCode == RESULT_LOAD_TRACK) {
            if (resultCode == RESULT_OK) {
                val indexes = data?.extras?.getIntArray("index") ?: return
                for (index in indexes) {
                    val newTrack = TrackOverlay(this, application.getTrack(index)!!)
                    application.fileTrackOverlays.add(newTrack)
                }
                setResult(RESULT_OK, Intent())
                finish()
            }
        }
    }

    private fun handleTrackAction(track: Track, action: TrackAction) {
        when (action) {
            is TrackAction.Properties -> {
                startActivity(Intent(this, TrackProperties::class.java).putExtra("INDEX", application.getTrackIndex(track)))
            }
            is TrackAction.Edit -> {
                setResult(RESULT_OK, Intent().putExtra("index", application.getTrackIndex(track)))
                finish()
            }
            is TrackAction.ToRoute -> {
                startActivity(Intent(this, TrackToRoute::class.java).putExtra("INDEX", application.getTrackIndex(track)))
                finish()
            }
            is TrackAction.Save -> {
                startActivity(Intent(this, TrackSave::class.java).putExtra("INDEX", application.getTrackIndex(track)))
            }
            is TrackAction.Remove -> {
                application.removeTrack(track)
            }
        }
    }

    // OnTrackActionListener — now delegate to the same handleTrackAction dispatcher
    override fun onTrackEdit(track: Track) {
        handleTrackAction(track, TrackAction.Properties)
    }

    override fun onTrackEditPath(track: Track) {
        handleTrackAction(track, TrackAction.Edit)
    }

    override fun onTrackToRoute(track: Track) {
        handleTrackAction(track, TrackAction.ToRoute)
    }

    override fun onTrackSave(track: Track) {
        handleTrackAction(track, TrackAction.Save)
    }
}
