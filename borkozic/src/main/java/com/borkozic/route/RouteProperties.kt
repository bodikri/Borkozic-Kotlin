package com.borkozic.route

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import com.borkozic.Borkozic
import com.borkozic.R
import com.borkozic.data.Route
import com.borkozic.ui.ColorButton

class RouteProperties : Activity() {

    private var route: Route? = null
    private var name: TextView? = null
    private var show: CheckBox? = null
    private var color: ColorButton? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.act_track_properties)

        val index = intent.extras!!.getInt("index")

        val application = application as Borkozic
        route = application.getRoute(index)

        name = findViewById<TextView>(R.id.name_text)
        name!!.text = route!!.name

        show = findViewById<CheckBox>(R.id.show_check)
        show!!.isChecked = route!!.show
        color = findViewById<ColorButton>(R.id.color_button)
        color!!.setColor(route!!.lineColor, resources.getColor(R.color.routeline))

        val width = findViewById<ViewGroup>(R.id.width_layout)
        width.visibility = View.GONE

        val save = findViewById<Button>(R.id.done_button)
        save.setOnClickListener(saveOnClickListener)

        val cancel = findViewById<Button>(R.id.cancel_button)
        cancel.setOnClickListener { finish() }
    }

    private val saveOnClickListener = View.OnClickListener {
        try {
            route!!.name = name!!.text.toString()
            route!!.show = show!!.isChecked
            route!!.lineColor = color!!.getColor()
            setResult(RESULT_OK)
            finish()
        } catch (e: Exception) {
            Toast.makeText(baseContext, "Error saving route", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        route = null
        name = null
        show = null
        color = null
    }
}
