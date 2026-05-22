package com.borkozic.track

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.borkozic.BaseApplication
import com.borkozic.Borkozic
import com.borkozic.R
import com.borkozic.data.Track
import com.borkozic.ui.ColorButton
import java.util.ArrayList

class TrackProperties : Activity() {
    private var track: Track? = null
    private lateinit var nameText: TextView
    private lateinit var showCheck: CheckBox
    private lateinit var colorBtn: ColorButton
    private lateinit var widthSpinner: Spinner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.act_track_properties)

        val index = intent.extras!!.getInt("INDEX")
        val application = BaseApplication.getApplication<Borkozic>()!!
        track = application.getTrack(index)
        val t = track!!

        nameText = findViewById(R.id.name_text)
        nameText.text = t.name

        showCheck = findViewById(R.id.show_check)
        showCheck.isChecked = t.show
        colorBtn = findViewById(R.id.color_button)
        colorBtn.setColor(t.color, resources.getColor(R.color.currenttrack))

        var sel = -1
        val widths = ArrayList<String>(30)
        for (i in 1..30) {
            widths.add(String.format("   %d    ", i))
            if (t.width == i) sel = i - 1
        }
        if (sel == -1) {
            widths.add(t.width.toString())
            sel = widths.size - 1
        }

        widthSpinner = findViewById(R.id.width_spinner)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, widths)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        widthSpinner.adapter = adapter
        widthSpinner.setSelection(sel)

        val save = findViewById<Button>(R.id.done_button)
        save.setOnClickListener(saveOnClickListener)

        val cancel = findViewById<Button>(R.id.cancel_button)
        cancel.setOnClickListener { finish() }
    }

    private val saveOnClickListener = View.OnClickListener {
        try {
            val t = track!!
            t.name = nameText.text.toString()
            t.show = showCheck.isChecked
            t.color = colorBtn.getColor()
            val w = widthSpinner.getItemAtPosition(widthSpinner.selectedItemPosition) as String
            t.width = w.trim().toInt()
            setResult(RESULT_OK)
            finish()
        } catch (e: Exception) {
            Log.e("TrackProperties", "Track save error", e)
            Toast.makeText(baseContext, "Error saving track", Toast.LENGTH_LONG).show()
        }
    }
}