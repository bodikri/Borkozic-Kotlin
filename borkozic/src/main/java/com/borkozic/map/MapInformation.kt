package com.borkozic.map

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import com.borkozic.Borkozic
import com.borkozic.R

class MapInformation : Activity() {

    private var information: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.act_mapinfo)

        val application = application as Borkozic

        val info = application.currentMap!!.info()

        val sb = StringBuilder()
        for (s in info) {
            sb.append(s)
            sb.append("\n")
        }

        information = findViewById<TextView>(R.id.mapinfo)
        information!!.text = sb
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
