package com.borkozic.area

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.borkozic.Borkozic
import com.borkozic.R
import com.borkozic.data.Area
import com.borkozic.util.FileUtils
import com.borkozic.util.OziExplorerFiles
import java.io.File

class AreaSave : Activity() {

    private var filename: TextView? = null
    private var area: Area? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.act_save)

        filename = findViewById<TextView>(R.id.filename_text)

        val index = intent.extras!!.getInt("index")

        val application = application as Borkozic
        area = application.getArea(index)

        if (area!!.filepath != null) {
            val file = File(area!!.filepath)
            filename!!.text = file.name
        } else {
            filename!!.text = FileUtils.sanitizeFilename(area!!.name) + ".art2"
        }

        val save = findViewById<Button>(R.id.save_button)
        save.setOnClickListener(saveOnClickListener)

        val cancel = findViewById<Button>(R.id.cancel_button)
        cancel.setOnClickListener { finish() }
    }

    private val saveOnClickListener = View.OnClickListener {
        var fname = filename!!.text.toString()
        fname = fname.replace("../", "")
        fname = fname.replace("/", "")
        if (fname.isEmpty()) return@OnClickListener

        try {
            val application = application as Borkozic
            val dir = File(application.dataPath!!)
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, fname)
            if (!file.exists()) file.createNewFile()
            if (file.canWrite()) {
                OziExplorerFiles.saveAreaToFile(file, application.charset!!, area!!)
                area!!.filepath = file.absolutePath
            }
            finish()
        } catch (e: Exception) {
            Toast.makeText(this@AreaSave, R.string.err_write, Toast.LENGTH_LONG).show()
            Log.e("BORKOZIC", e.toString(), e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        area = null
        filename = null
    }
}
