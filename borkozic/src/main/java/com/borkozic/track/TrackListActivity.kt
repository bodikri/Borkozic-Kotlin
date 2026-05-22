package com.borkozic.track
import com.borkozic.BaseApplication

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import com.borkozic.Borkozic
import com.borkozic.R
import com.borkozic.data.Track
import com.borkozic.overlay.TrackOverlay

class TrackListActivity : AppCompatActivity(), OnTrackActionListener {

    companion object {
        const val RESULT_LOAD_TRACK = 1
    }

    private lateinit var application: Borkozic

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        application = BaseApplication.getApplication<Borkozic>()!!

        setContentView(R.layout.act_fragment)

        if (savedInstanceState == null) {
            val fragment = Fragment.instantiate(this, TrackList::class.java.name)
            val fragmentTransaction = supportFragmentManager.beginTransaction()
            fragmentTransaction.add(android.R.id.content, fragment, "TrackList")
            fragmentTransaction.commit()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RESULT_LOAD_TRACK) {
            if (resultCode == Activity.RESULT_OK) {
                val app: Borkozic = BaseApplication.getApplication<Borkozic>()!!
                val indexes = data?.getIntArrayExtra("index")
                if (indexes != null) {
                    for (index in indexes!!) {
                        val newTrack = TrackOverlay(this, app.getTrack(index))
                        app.fileTrackOverlays.add(newTrack)
                    }
                }
            }
        }
    }

    override fun onTrackEdit(track: Track) {
        startActivity(Intent(this, TrackProperties::class.java).putExtra("INDEX", application.getTrackIndex(track)))
    }

    override fun onTrackEditPath(track: Track) {
        setResult(RESULT_OK, Intent().putExtra("index", application.getTrackIndex(track)))
        finish()
    }

    override fun onTrackToRoute(track: Track) {
        startActivity(Intent(this, TrackToRoute::class.java).putExtra("INDEX", application.getTrackIndex(track)))
        finish()
    }

    override fun onTrackSave(track: Track) {
        startActivity(Intent(this, TrackSave::class.java).putExtra("INDEX", application.getTrackIndex(track)))
    }
}
