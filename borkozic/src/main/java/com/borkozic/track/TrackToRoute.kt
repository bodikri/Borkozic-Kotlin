package com.borkozic.track

import android.app.Activity
import android.app.Dialog
import android.app.ProgressDialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.RadioButton
import android.widget.SeekBar
import com.borkozic.BaseApplication
import com.borkozic.Borkozic
import com.borkozic.R
import com.borkozic.overlay.RouteOverlay
import java.util.concurrent.Executors

class TrackToRoute : Activity() {
    private var dlgWait: ProgressDialog? = null
    private val threadPool = Executors.newFixedThreadPool(2)

    private lateinit var algA: RadioButton
    private lateinit var algB: RadioButton
    private var index = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.act_track_to_route)

        index = intent.extras!!.getInt("INDEX")

        algA = findViewById(R.id.alg_a)
        algB = findViewById(R.id.alg_b)
        algA.isChecked = true

        val generate = findViewById<Button>(R.id.generate_button)
        generate.setOnClickListener(saveOnClickListener)
    }

    override fun onCreateDialog(id: Int): Dialog? {
        return when (id) {
            0 -> {
                val dlg = ProgressDialog(this)
                dlg.setMessage(getString(R.string.msg_wait))
                dlg.isIndeterminate = true
                dlg.setCancelable(false)
                dlgWait = dlg
                dlg
            }
            else -> null
        }
    }

    private val saveOnClickListener = View.OnClickListener {
        showDialog(0)

        val application = BaseApplication.getApplication<Borkozic>()!!
        val track = application.getTrack(index)!!
        val alg = if (algA.isChecked) 1 else 2
        val s = findViewById<SeekBar>(R.id.sensitivity).progress
        val sensitivity = (s + 1) / 2f

        threadPool.execute {
            val route = when (alg) {
                1 -> application.trackToRoute(track, sensitivity)
                2 -> application.trackToRoute2(track, sensitivity)
                else -> null
            }
            if (route != null) {
                application.addRoute(route)
                // TODO it's a hack
                val newRoute = RouteOverlay(application.mapActivity!!, route)
                application.routeOverlays.add(newRoute)
            }
            dlgWait!!.dismiss()
            finish()
        }
    }
}
