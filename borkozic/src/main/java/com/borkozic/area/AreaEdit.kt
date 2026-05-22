package com.borkozic.area

import android.app.ListActivity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ListAdapter
import android.widget.ListView
import com.borkozic.Borkozic
import com.borkozic.R
import com.borkozic.waypoint.WaypointProperties
import com.ericharlow.DragNDrop.DragListener
import com.ericharlow.DragNDrop.DragNDropAdapter
import com.ericharlow.DragNDrop.DragNDropListView
import com.ericharlow.DragNDrop.DropListener
import com.ericharlow.DragNDrop.RemoveListener
import java.util.ArrayList

open class AreaEdit : ListActivity(), DropListener, View.OnClickListener, RemoveListener, DragListener {

    private var area: com.borkozic.data.Area? = null
    private var index = 0

    private val backgroundColor = 0x00000000
    private var defaultBackgroundColor = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.act_area_edit)

        index = intent.extras!!.getInt("INDEX")

        val application = application as Borkozic
        area = application.getArea(index)
        title = area!!.name

        val listView = listView
        if (listView is DragNDropListView) {
            (listView as DragNDropListView).setDropListener(this)
            (listView as DragNDropListView).setRemoveListener(this)
            (listView as DragNDropListView).setDragListener(this)
        }

        findViewById<View>(R.id.done_button).setOnClickListener(this)
    }

    override fun onResume() {
        super.onResume()
        val waypoints = area!!.waypoints
        val content = ArrayList<String>(waypoints.size)
        for (i in waypoints.indices) {
            content.add(waypoints[i].name)
        }
        listAdapter = DragNDropAdapter(
            this, intArrayOf(R.layout.dragitem), intArrayOf(R.id.TextView01), content
        )
    }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        super.onListItemClick(l, v, position, id)
        startActivity(
            Intent(this, WaypointProperties::class.java)
                .putExtra("INDEX", position)
                .putExtra("AREA", index + 1)
        )
    }

    override fun onClick(v: View) {
        setResult(RESULT_OK)
        finish()
    }

    override fun onRemove(which: Int) {
        val adapter = listAdapter
        if (adapter is DragNDropAdapter) {
            adapter.onRemove(which)
            area!!.removeWaypoint(area!!.getWaypoint(which))
            listView.invalidateViews()
        }
    }

    override fun onDrop(from: Int, to: Int) {
        val adapter = listAdapter
        if (adapter is DragNDropAdapter) {
            adapter.onDrop(from, to)
            val wpt = area!!.getWaypoint(from)
            area!!.removeWaypoint(wpt)
            area!!.addWaypoint(if (from < to) to - 1 else to, wpt)
            listView.invalidateViews()
        }
    }

    override fun onDrag(x: Int, y: Int, listView: ListView) {
    }

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
        area = null
    }
}
