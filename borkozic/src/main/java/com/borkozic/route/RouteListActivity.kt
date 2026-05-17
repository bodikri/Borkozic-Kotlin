/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2012 Andrey Novikov <http://andreynovikov.info/>
 *
 * This file is part of Androzic application.
 *
 * Androzic is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Androzic is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Androzic. If not, see <http://www.gnu.org/licenses/>.
 */

package com.borkozic.route

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import com.borkozic.BaseApplication
import com.borkozic.Borkozic
import com.borkozic.R
import com.borkozic.data.Route
import com.borkozic.overlay.RouteOverlay

class RouteListActivity : AppCompatActivity(), OnRouteActionListener {
    companion object {
        const val RESULT_START_ROUTE = 1
        const val RESULT_LOAD_ROUTE = 2
        const val RESULT_ROUTE_DETAILS = 3
    }

    private var application: Borkozic? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        application = BaseApplication.getApplication<Borkozic>()

        setContentView(R.layout.act_fragment)

        if (savedInstanceState == null) {
            val fragment: Fragment = Fragment.instantiate(this, RouteList::class.java.name)
            val fragmentTransaction: FragmentTransaction = supportFragmentManager.beginTransaction()
            fragmentTransaction.add(android.R.id.content, fragment, "RouteList")
            fragmentTransaction.commit()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            RESULT_START_ROUTE -> {
                if (resultCode == RESULT_OK)
                    finish()
            }
            RESULT_LOAD_ROUTE -> {
                if (resultCode == RESULT_OK) {
                    val app = BaseApplication.getApplication<Borkozic>() ?: return
                    val indexes = data?.extras?.getIntArray("index") ?: return
                    for (index in indexes) {
                        val newRoute = RouteOverlay(this, app.getRoute(index)!!)
                        app.routeOverlays.add(newRoute)
                    }
                }
            }
            RESULT_ROUTE_DETAILS -> {
                if (resultCode == RESULT_OK) {
                    finish()
                }
            }
        }
    }

    override fun onRouteDetails(route: Route) {
        startActivityForResult(
            Intent(this, RouteDetails::class.java).putExtra("index", application!!.getRouteIndex(route)),
            RESULT_ROUTE_DETAILS
        )
    }

    override fun onRouteNavigate(route: Route) {
        startActivityForResult(
            Intent(this, RouteStart::class.java).putExtra("index", application!!.getRouteIndex(route)),
            RESULT_START_ROUTE
        )
    }

    override fun onRouteEdit(route: Route) {
        startActivity(
            Intent(this, RouteProperties::class.java).putExtra("index", application!!.getRouteIndex(route))
        )
    }

    override fun onRouteEditPath(route: Route) {
        route.show = true
        setResult(RESULT_OK, Intent().putExtra("index", application!!.getRouteIndex(route)))
        finish()
    }

    override fun onRouteSave(route: Route) {
        startActivity(
            Intent(this, RouteSave::class.java).putExtra("index", application!!.getRouteIndex(route))
        )
    }
}
