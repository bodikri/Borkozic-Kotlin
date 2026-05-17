/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2012  Andrey Novikov <http://andreynovikov.info/>
 *
 * This file is part of Androzic application.
 *
 * Androzic is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * Androzic is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with Androzic.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.borkozic.route

import java.util.ArrayList

import android.app.ListActivity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.View.OnClickListener
import android.widget.ListAdapter
import android.widget.ListView

import com.borkozic.Borkozic
import com.borkozic.BaseApplication
import com.borkozic.R
import com.borkozic.data.Route
import com.borkozic.data.Waypoint
import com.borkozic.waypoint.WaypointProperties
import com.ericharlow.DragNDrop.DragListener
import com.ericharlow.DragNDrop.DragNDropAdapter
import com.ericharlow.DragNDrop.DragNDropListView
import com.ericharlow.DragNDrop.DropListener
import com.ericharlow.DragNDrop.RemoveListener

open class RouteEdit : ListActivity(), DropListener, OnClickListener, RemoveListener, DragListener {
    private lateinit var route: Route
    private var index = 0

    private var backgroundColor = 0x00000000
    private var defaultBackgroundColor = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.act_route_edit)

        index = intent.extras!!.getInt("INDEX")

        val application = BaseApplication.getApplication<Borkozic>()!!
        route = application.getRoute(index)!!
        setTitle(route.name)

        val listView = listView

        if (listView is DragNDropListView) {
            listView.setDropListener(this)
            listView.setRemoveListener(this)
            listView.setDragListener(this)
        }

        findViewById<View>(R.id.done_button).setOnClickListener(this)
    }

    override fun onResume() {
        super.onResume()
        val waypoints = route.waypoints
        val content = ArrayList<String>(waypoints.size)
        for (i in waypoints.indices) {
            content.add(waypoints[i].name)
        }
        listAdapter = DragNDropAdapter(this, intArrayOf(R.layout.dragitem), intArrayOf(R.id.TextView01), content)
    }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        super.onListItemClick(l, v, position, id)
        startActivity(Intent(this, WaypointProperties::class.java).putExtra("INDEX", position).putExtra("ROUTE", index + 1))
    }

    override fun onClick(v: View) {
        setResult(RESULT_OK)
        finish()
    }

    override fun onRemove(which: Int) {
        val adapter = listAdapter
        if (adapter is DragNDropAdapter) {
            adapter.onRemove(which)
            route.removeWaypoint(route.getWaypoint(which))
            listView.invalidateViews()
        }
    }

    override fun onDrop(from: Int, to: Int) {
        val adapter = listAdapter
        if (adapter is DragNDropAdapter) {
            adapter.onDrop(from, to)
            val wpt = route.getWaypoint(from)
            route.removeWaypoint(wpt)
            route.addWaypoint(if (from < to) to - 1 else to, wpt)
            listView.invalidateViews()
        }
    }

    override fun onDrag(x: Int, y: Int, listView: ListView) {}

    override fun onStartDrag(itemView: View) {
        itemView.visibility = View.INVISIBLE
        defaultBackgroundColor = itemView.drawingCacheBackgroundColor
        itemView.setBackgroundColor(backgroundColor)
    }

    override fun onStopDrag(itemView: View) {
        itemView.visibility = View.VISIBLE
        itemView.setBackgroundColor(defaultBackgroundColor)
    }

    override fun onDestroy() {
        super.onDestroy()
        route = null!!
    }
}