package com.borkozic.route
import com.borkozic.BaseApplication

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import java.io.File
import com.borkozic.Borkozic
import com.borkozic.R
import com.borkozic.data.Route
import com.borkozic.util.FileUtils
import com.borkozic.util.OziExplorerFiles

class RouteSave : Activity() {

    private var filename: TextView? = null
    private var route: Route? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.act_save)

        filename = findViewById(R.id.filename_text)

        val index = intent.extras?.getInt("index") ?: 0

        val application: Borkozic = BaseApplication.getApplication<Borkozic>()!!
        route = application.getRoute(index)

        if (route!!.filepath != null) {
            val file = File(route!!.filepath)
            filename!!.text = file.getName()
        } else {
            filename!!.text = FileUtils.sanitizeFilename(route!!.name) + ".rt2"
        }

        val save = findViewById<Button>(R.id.save_button)
        save.setOnClickListener(saveOnClickListener)

        val cancel = findViewById<Button>(R.id.cancel_button)
        cancel.setOnClickListener { finish() }
    }

    private val saveOnClickListener = View.OnClickListener { v: View ->
        var fname = filename!!.text.toString()
        fname = fname.replace("../", "")
        fname = fname.replace("/", "")
        if (fname.isEmpty())
            return@OnClickListener

        try {
            val application: Borkozic = BaseApplication.getApplication<Borkozic>()!!
            val dir = File(application.dataPath)
            if (!dir.exists())
                dir.mkdirs()
            val file = File(dir, fname)
            if (!file.exists()) {
                file.createNewFile()
            }
            if (file.canWrite()) {
                OziExplorerFiles.saveRouteToFile(file, application.charset!!, route!!)
                route!!.filepath = file.getAbsolutePath()
            }
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, R.string.err_write, Toast.LENGTH_LONG).show()
            Log.e("BORKOZIC", e.toString(), e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        route = null
        filename = null
    }
}
