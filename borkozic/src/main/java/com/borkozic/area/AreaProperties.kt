package com.borkozic.area

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.borkozic.Borkozic
import com.borkozic.R
import com.borkozic.data.Area
import com.borkozic.ui.ColorButton

class AreaProperties : Activity() {

    private var area: Area? = null
    private var name: TextView? = null
    private var textViewProcentage: TextView? = null
    private var seekBarAreaTransparency: SeekBar? = null
    private var show: CheckBox? = null
    private var colorLine: ColorButton? = null
    private var colorArea: ColorButton? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.act_area_properties)

        val index = intent.extras!!.getInt("index")

        val application = application as Borkozic
        area = application.getArea(index)

        name = findViewById<TextView>(R.id.name_text)
        name!!.text = area!!.name

        show = findViewById<CheckBox>(R.id.show_check)
        show!!.isChecked = area!!.show

        colorLine = findViewById<ColorButton>(R.id.colorLine_button)
        colorLine!!.setColor(area!!.lineColor, resources.getColor(R.color.arealinecolor))

        colorArea = findViewById<ColorButton>(R.id.colorArea_button)
        colorArea!!.setColor(area!!.fillColor, resources.getColor(R.color.areacolor))

        val width = findViewById<ViewGroup>(R.id.width_layout)
        width.visibility = View.GONE

        val save = findViewById<Button>(R.id.done_button)
        save.setOnClickListener(saveOnClickListener)

        val cancel = findViewById<Button>(R.id.cancel_button)
        cancel.setOnClickListener { finish() }

        textViewProcentage = findViewById<TextView>(R.id.textView)

        seekBarAreaTransparency = findViewById<SeekBar>(R.id.seekBar)
        seekBarAreaTransparency!!.max = 200
        if (area!!.AreaTransperency < 0) {
            area!!.AreaTransperency = resources.getInteger(R.integer.def_area_transparensy)
        }
        seekBarAreaTransparency!!.progress = area!!.AreaTransperency
        textViewProcentage!!.text = "" + area!!.AreaTransperency + "%"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            seekBarAreaTransparency!!.min = 10
        }
        seekBarAreaTransparency!!.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                textViewProcentage!!.text = "" + progress + "%"
                if (progress < MIN_VALUE) {
                    seekBar.progress = MIN_VALUE
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
            }
        })
    }

    private val saveOnClickListener = View.OnClickListener {
        try {
            area!!.name = name!!.text.toString()
            area!!.show = show!!.isChecked
            area!!.lineColor = colorLine!!.getColor()
            area!!.fillColor = colorArea!!.getColor()
            area!!.AreaTransperency = seekBarAreaTransparency!!.progress
            setResult(RESULT_OK)
            finish()
        } catch (e: Exception) {
            Toast.makeText(baseContext, "Error saving Area", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        area = null
        name = null
        show = null
        colorLine = null
        colorArea = null
    }

    companion object {
        private const val MIN_VALUE = 10
    }
}
