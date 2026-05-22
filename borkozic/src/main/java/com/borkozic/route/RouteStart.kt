package com.borkozic.route
import com.borkozic.navigation.BaseNavigationService

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.RadioButton
import android.widget.Toast
import com.borkozic.Borkozic
import com.borkozic.R
import com.borkozic.data.Route
import com.borkozic.data.Waypoint
import com.borkozic.navigation.NavigationService

class RouteStart : Activity() {

    private var route: Route? = null
    private var forward: RadioButton? = null
    private var reverse: RadioButton? = null

    private var index = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.act_route_start)

        index = intent.extras!!.getInt("index")

        val application = application as Borkozic
        route = application.getRoute(index)

        if (route!!.length() < 2) {
            Toast.makeText(baseContext, R.string.err_shortroute, Toast.LENGTH_LONG).show()
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        title = route!!.name

        val start = route!!.getWaypoint(0)
        val end = route!!.getWaypoint(route!!.length() - 1)

        forward = findViewById<RadioButton>(R.id.forward)
        forward!!.text = start.name + " to " + end.name
        reverse = findViewById<RadioButton>(R.id.reverse)
        reverse!!.text = end.name + " to " + start.name

        forward!!.isChecked = true

        val navigate = findViewById<Button>(R.id.navigate_button)
        navigate.setOnClickListener(navigateOnClickListener)
    }

    private val navigateOnClickListener = View.OnClickListener {
        route!!.show = true
        val dir = if (forward!!.isChecked) BaseNavigationService.DIRECTION_FORWARD else BaseNavigationService.DIRECTION_REVERSE
        startService(
            Intent(this, NavigationService::class.java)
                .setAction(BaseNavigationService.NAVIGATE_ROUTE)
                .putExtra("index", index)
                .putExtra("direction", dir)
        )
        setResult(RESULT_OK)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        route = null
        forward = null
        reverse = null
    }
}
