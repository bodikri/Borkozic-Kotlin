package com.borkozic.track
import com.borkozic.BaseApplication

import android.app.Activity
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import java.io.File
import com.borkozic.Borkozic
import com.borkozic.R
import com.borkozic.data.Track
import com.borkozic.util.FileUtils
import com.borkozic.util.OziExplorerFiles

class TrackSave : Activity() {

    private var filename: TextView? = null
    private var track: Track? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.act_save)

        filename = findViewById(R.id.filename_text)

        val index = intent.extras?.getInt("INDEX") ?: 0

        val application: Borkozic = BaseApplication.getApplication<Borkozic>()!!
        track = application.getTrack(index)

        if (track!!.filepath != null) {
            val file = File(track!!.filepath)
            filename!!.text = file.getName()
        } else {
            filename!!.text = FileUtils.sanitizeFilename(track!!.name) + ".plt"
        }

        val save = findViewById<Button>(R.id.save_button)
        save.setOnClickListener(saveOnClickListener)

        val cancel = findViewById<Button>(R.id.cancel_button)
        cancel.setOnClickListener { finish() }
    }

    private val saveOnClickListener = View.OnClickListener { v: View ->
        var fname = FileUtils.sanitizeFilename(filename!!.text.toString())
        if (fname.isEmpty())
            return@OnClickListener

        try {
            val state = Environment.getExternalStorageState()
            if (!Environment.MEDIA_MOUNTED.equals(state))
                throw java.io.FileNotFoundException(getString(R.string.err_nosdcard))

            val application: Borkozic = BaseApplication.getApplication<Borkozic>()!!
            val dir = File(application.dataPath!!)
            if (!dir.exists())
                dir.mkdirs()
            val file = File(dir, fname)
            if (!file.exists()) {
                file.createNewFile()
            }
            if (file.canWrite()) {
                OziExplorerFiles.saveTrackToFile(file, application.charset!!, track!!)
                track!!.filepath = file.getAbsolutePath()
            }
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, R.string.err_write, Toast.LENGTH_LONG).show()
            Log.e("ANDROZIC", e.toString(), e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        track = null
        filename = null
    }
}
