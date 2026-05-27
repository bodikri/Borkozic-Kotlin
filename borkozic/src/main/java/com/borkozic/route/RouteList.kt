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
import com.borkozic.BaseApplication

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import androidx.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.PopupWindow
import android.widget.TextView

import androidx.fragment.app.ListFragment

import com.borkozic.Borkozic
import com.borkozic.R
import com.borkozic.data.Route
import com.borkozic.util.StringFormatter

import net.londatiga.android.ActionItem
import net.londatiga.android.QuickAction
import net.londatiga.android.QuickAction.OnActionItemClickListener

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class RouteList : ListFragment() {
    private var routeActionsCallback: OnRouteActionListener? = null

    protected val threadPool: ExecutorService = Executors.newFixedThreadPool(2)
    val handler = Handler()

    private lateinit var adapter: RouteListAdapter
    private lateinit var quickAction: QuickAction
    private var selectedKey = 0
    private var selectedBackground: Drawable? = null

    private var mode = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.list_with_empty_view, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        setHasOptionsMenu(true)

        val emptyView = listView.emptyView as TextView?
        if (emptyView != null)
            emptyView.setText(R.string.msg_empty_route_list)

        val activity = requireActivity()

        mode = activity.intent.extras!!.getInt("MODE")

        if (mode == MODE_START)
            activity.setTitle(getString(R.string.selectroute_name))

        adapter = RouteListAdapter(activity)
        listAdapter = adapter

        val resources = resources
        quickAction = QuickAction(activity)
        quickAction.addActionItem(ActionItem(qaRouteDetails, getString(R.string.menu_details), resources.getDrawable(R.drawable.ic_action_list)))
        quickAction.addActionItem(ActionItem(qaRouteNavigate, getString(R.string.menu_navigate), resources.getDrawable(R.drawable.ic_action_directions)))
        quickAction.addActionItem(ActionItem(qaRouteProperties, getString(R.string.menu_properties), resources.getDrawable(R.drawable.ic_action_edit)))
        quickAction.addActionItem(ActionItem(qaRouteEdit, getString(R.string.menu_edit), resources.getDrawable(R.drawable.ic_action_track)))
        quickAction.addActionItem(ActionItem(qaRouteSave, getString(R.string.menu_save), resources.getDrawable(R.drawable.ic_action_save)))
        quickAction.addActionItem(ActionItem(qaRouteRemove, getString(R.string.menu_remove), resources.getDrawable(R.drawable.ic_action_cancel)))

        quickAction.setOnActionItemClickListener(routeActionItemClickListener)
        quickAction.setOnDismissListener(object : PopupWindow.OnDismissListener {
            override fun onDismiss() {
                val v = listView.findViewWithTag<View>("selected")
                if (v != null) {
                    v.background = selectedBackground
                    v.tag = null
                }
            }
        })
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // This makes sure that the container activity has implemented
        // the callback interface. If not, it throws an exception
        try {
            routeActionsCallback = context as OnRouteActionListener
        } catch (e: ClassCastException) {
            throw ClassCastException("$context must implement OnRouteActionListener")
        }
    }

    override fun onResume() {
        super.onResume()
        adapter.notifyDataSetChanged()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        if (mode == MODE_MANAGE) {
            inflater.inflate(R.menu.routelist_menu, menu)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menuNewRoute -> {
                val application = BaseApplication.getApplication<Borkozic>()!!
                val route = Route("New route", "", true)
                application.addRoute(route)
                routeActionsCallback!!.onRouteEdit(route)
                return true
            }
            R.id.menuLoadRoute -> {
                requireActivity().startActivityForResult(Intent(requireActivity(), RouteFileList::class.java), RouteListActivity.RESULT_LOAD_ROUTE)
                return true
            }
        }
        return false
    }

    override fun onListItemClick(lv: ListView, v: View, position: Int, id: Long) {
        when (mode) {
            MODE_MANAGE -> {
                v.tag = "selected"
                selectedKey = position
                selectedBackground = v.background
                val l = v.paddingLeft
                val t = v.paddingTop
                val r = v.paddingRight
                val b = v.paddingBottom
                v.setBackgroundResource(R.drawable.list_selector_background_focus)
                v.setPadding(l, t, r, b)
                quickAction.show(v)
            }
            MODE_START -> {
                val application = BaseApplication.getApplication<Borkozic>()!!
                val route = application.getRoute(position)!!
                routeActionsCallback!!.onRouteNavigate(route)
            }
        }
    }

    private val routeActionItemClickListener = object : OnActionItemClickListener {
        override fun onItemClick(source: QuickAction, pos: Int, actionId: Int) {
            val application = BaseApplication.getApplication<Borkozic>()!!
            val route = application.getRoute(selectedKey)!!

            when (actionId) {
                qaRouteDetails -> routeActionsCallback!!.onRouteDetails(route)
                qaRouteNavigate -> routeActionsCallback!!.onRouteNavigate(route)
                qaRouteProperties -> routeActionsCallback!!.onRouteEdit(route)
                qaRouteEdit -> routeActionsCallback!!.onRouteEditPath(route)
                qaRouteSave -> routeActionsCallback!!.onRouteSave(route)
                qaRouteRemove -> {
                    application.removeRoute(route)
                    adapter.notifyDataSetChanged()
                }
            }
        }
    }

    companion object {
        private const val TAG = "RouteList"
        const val MODE_MANAGE = 1
        const val MODE_START = 2

        const val qaRouteDetails = 1
        const val qaRouteNavigate = 2
        const val qaRouteProperties = 3
        const val qaRouteEdit = 4
        const val qaRouteSave = 5
        const val qaRouteRemove = 6
    }

    inner class RouteListAdapter internal constructor(context: Context) : BaseAdapter() {
        private val mInflater: LayoutInflater
        private val mItemLayout: Int
        private val mDensity: Float
        private val mLinePath: Path
        private val mFillPaint: Paint
        private val mLinePaint: Paint
        private val mBorderPaint: Paint
        private val mPointWidth: Int
        private val mRouteWidth: Int
        private val application: Borkozic

        init {
            mItemLayout = R.layout.route_list_item
            mInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            mDensity = context.resources.displayMetrics.density

            val settings = PreferenceManager.getDefaultSharedPreferences(context)

            mLinePath = Path()
            mLinePath.setLastPoint(12 * mDensity, 5 * mDensity)
            mLinePath.lineTo(24 * mDensity, 12 * mDensity)
            mLinePath.lineTo(15 * mDensity, 24 * mDensity)
            mLinePath.lineTo(28 * mDensity, 35 * mDensity)

            mPointWidth = settings.getInt(context.getString(R.string.pref_waypoint_width), context.resources.getInteger(R.integer.def_waypoint_width))
            mRouteWidth = settings.getInt(context.getString(R.string.pref_route_linewidth), context.resources.getInteger(R.integer.def_route_linewidth))
            mFillPaint = Paint()
            mFillPaint.isAntiAlias = false
            mFillPaint.strokeWidth = 1f
            mFillPaint.style = Paint.Style.FILL_AND_STROKE
            mFillPaint.color = context.resources.getColor(R.color.routewaypoint)
            mLinePaint = Paint()
            mLinePaint.isAntiAlias = true
            mLinePaint.strokeWidth = mRouteWidth * mDensity
            mLinePaint.style = Paint.Style.STROKE
            mLinePaint.color = context.resources.getColor(R.color.routeline)
            mBorderPaint = Paint()
            mBorderPaint.isAntiAlias = true
            mBorderPaint.strokeWidth = 1f
            mBorderPaint.style = Paint.Style.STROKE
            mBorderPaint.color = context.resources.getColor(R.color.routeline)

            application = BaseApplication.getApplication<Borkozic>()!!
        }

        override fun getItem(position: Int): Route {
            return application.getRoute(position)!!
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getCount(): Int = application.routes.size

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v: View
            if (convertView == null) {
                v = mInflater.inflate(mItemLayout, parent, false)
            } else {
                v = convertView
            }
            val route = getItem(position)
            var text = v.findViewById<TextView>(R.id.name)
            text.text = route.name
            val distance = StringFormatter.distanceH(route.distance)
            text = v.findViewById(R.id.distance)
            text.text = distance
            text = v.findViewById(R.id.filename)
            if (route.filepath != null) {
                val filepath = if (route.filepath!!.startsWith(application.dataPath!!)) route.filepath!!.substring(application.dataPath!!.length + 1) else route.filepath!!
                text.text = filepath
            } else {
                text.text = ""
            }
            val icon = v.findViewById<ImageView>(R.id.icon)
            val bm = Bitmap.createBitmap((40 * mDensity).toInt(), (40 * mDensity).toInt(), Config.ARGB_8888)
            bm.eraseColor(Color.TRANSPARENT)
            val bc = Canvas(bm)
            mLinePaint.color = route.lineColor
            mBorderPaint.color = route.lineColor
            bc.drawPath(mLinePath, mLinePaint)
            val half = Math.round(mPointWidth / 4f).toInt()
            bc.drawCircle(12 * mDensity, 5 * mDensity, half.toFloat(), mFillPaint)
            bc.drawCircle(12 * mDensity, 5 * mDensity, half.toFloat(), mBorderPaint)
            bc.drawCircle(24 * mDensity, 12 * mDensity, half.toFloat(), mFillPaint)
            bc.drawCircle(24 * mDensity, 12 * mDensity, half.toFloat(), mBorderPaint)
            bc.drawCircle(15 * mDensity, 24 * mDensity, half.toFloat(), mFillPaint)
            bc.drawCircle(15 * mDensity, 24 * mDensity, half.toFloat(), mBorderPaint)
            bc.drawCircle(28 * mDensity, 35 * mDensity, half.toFloat(), mFillPaint)
            bc.drawCircle(28 * mDensity, 35 * mDensity, half.toFloat(), mBorderPaint)
            icon.setImageBitmap(bm)

            return v
        }

        override fun hasStableIds(): Boolean {
            return true
        }
    }
}