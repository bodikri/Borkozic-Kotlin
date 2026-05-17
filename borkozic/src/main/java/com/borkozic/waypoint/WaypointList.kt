/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2014 Andrey Novikov <http://andreynovikov.info/>
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

package com.borkozic.waypoint

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.*
import android.widget.*
import androidx.annotation.NonNull
import androidx.core.content.res.ResourcesCompat
import com.borkozic.BaseApplication
import com.borkozic.Borkozic
import com.borkozic.R
import com.borkozic.data.Waypoint
import com.borkozic.data.WaypointSet
import com.borkozic.ui.ExpandableListFragment
import com.borkozic.util.Geo
import com.borkozic.util.StringFormatter
import net.londatiga.android.ActionItem
import net.londatiga.android.QuickAction
import net.londatiga.android.QuickAction.OnActionItemClickListener
import java.io.File
import java.util.*

class WaypointList : ExpandableListFragment(), AdapterView.OnItemLongClickListener {

    companion object {
        private const val qaWaypointVisible = 1
        private const val qaWaypointNavigate = 2
        private const val qaWaypointProperties = 3
        private const val qaWaypointShare = 4
        private const val qaWaypointDelete = 5
        private const val qaWaypointSetClear = 101
        private const val qaWaypointSetRemove = 102
    }

    private var waypointActionsCallback: OnWaypointActionListener? = null
    private lateinit var adapter: WaypointExpandableListAdapter
    private lateinit var quickAction: QuickAction
    private lateinit var setQuickAction: QuickAction
    private var selectedKey: Long = 0
    private var selectedSetKey: Int = 0
    private var selectedBackground: Drawable? = null
    private var mSortMode: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        setHasOptionsMenu(true)

        val activity = activity!!

        adapter = WaypointExpandableListAdapter(activity)
        setListAdapter(adapter)

        getExpandableListView().setOnItemLongClickListener(this)

        val resources = resources
        quickAction = QuickAction(activity)
        quickAction.addActionItem(ActionItem(qaWaypointVisible, getString(R.string.menu_view), ResourcesCompat.getDrawable(resources, R.drawable.ic_action_show, null)))
        quickAction.addActionItem(ActionItem(qaWaypointNavigate, getString(R.string.menu_navigate), ResourcesCompat.getDrawable(resources, R.drawable.ic_action_directions, null)))
        quickAction.addActionItem(ActionItem(qaWaypointProperties, getString(R.string.menu_edit), ResourcesCompat.getDrawable(resources, R.drawable.ic_action_edit, null)))
        quickAction.addActionItem(ActionItem(qaWaypointShare, getString(R.string.menu_share), ResourcesCompat.getDrawable(resources, R.drawable.ic_action_share, null)))
        quickAction.addActionItem(ActionItem(qaWaypointDelete, getString(R.string.menu_delete), ResourcesCompat.getDrawable(resources, R.drawable.ic_action_trash, null)))

        quickAction.setOnActionItemClickListener(waypointActionItemClickListener)
        quickAction.setOnDismissListener(object : PopupWindow.OnDismissListener {
            override fun onDismiss() {
                val v = getExpandableListView().findViewWithTag<View?>("selected")
                if (v != null) {
                    v.background = selectedBackground
                    v.tag = null
                }
            }
        })

        setQuickAction = QuickAction(activity)
        setQuickAction.addActionItem(ActionItem(qaWaypointSetClear, getString(R.string.menu_clear), ResourcesCompat.getDrawable(resources, R.drawable.ic_action_document_clear, null)))
        setQuickAction.addActionItem(ActionItem(qaWaypointSetRemove, getString(R.string.menu_remove), ResourcesCompat.getDrawable(resources, R.drawable.ic_action_cancel, null)))
        setQuickAction.setOnActionItemClickListener(setActionItemClickListener)
    }

    override fun onAttach(@NonNull context: Context) {
        super.onAttach(context)
        try {
            waypointActionsCallback = context as OnWaypointActionListener
        } catch (e: ClassCastException) {
            throw ClassCastException(context.toString() + " must implement OnWaypointActionListener")
        }
    }

    override fun onStart() {
        super.onStart()

        val application = BaseApplication.getApplication<Borkozic>()!!
        application.saveWaypoints()

        mSortMode = -1
        adapter.sort(0)
        getExpandableListView().expandGroup(0)
    }

    override fun onChildClick(parent: ExpandableListView, v: View, groupPosition: Int, childPosition: Int, id: Long): Boolean {
        v.tag = "selected"
        selectedKey = adapter.getCombinedChildId(groupPosition.toLong(), childPosition.toLong())
        selectedBackground = v.background
        val l = v.paddingLeft
        val t = v.paddingTop
        val r = v.paddingRight
        val b = v.paddingBottom
        v.setBackgroundResource(R.drawable.list_selector_background_focus)
        v.setPadding(l, t, r, b)
        quickAction.show(v)
        return true
    }

    override fun onItemLongClick(parent: AdapterView<*>, view: View, position: Int, id: Long): Boolean {
        val pos = getExpandableListView().getExpandableListPosition(position)
        if (ExpandableListView.getPackedPositionType(pos) == ExpandableListView.PACKED_POSITION_TYPE_GROUP) {
            selectedSetKey = ExpandableListView.getPackedPositionGroup(pos)
            setQuickAction.show(view)
        }
        return true
    }

    override fun onCreateOptionsMenu(@NonNull menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.waypointlist_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onPrepareOptionsMenu(@NonNull menu: Menu) {
        if (mSortMode != -1) {
            val icon = menu.findItem(mSortMode).icon
            menu.findItem(R.id.action_sort).icon = icon
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val activity = activity ?: return true
        when (item.itemId) {
            R.id.action_sort_alpha -> {
                adapter.sort(0)
                mSortMode = R.id.action_sort_alpha
                activity.invalidateOptionsMenu()
            }
            R.id.action_sort_size -> {
                adapter.sort(1)
                mSortMode = R.id.action_sort_size
                activity.invalidateOptionsMenu()
            }
            R.id.menuLoadWaypoints -> startActivity(Intent(activity, WaypointFileList::class.java))
            R.id.menuNewWaypointSet -> {
                val textEntryView = EditText(activity)
                textEntryView.isSingleLine = true
                textEntryView.setPadding(8, 0, 8, 0)
                AlertDialog.Builder(activity).setTitle(R.string.name).setView(textEntryView)
                    .setPositiveButton(R.string.ok) { _, _ ->
                        val name = textEntryView.text.toString()
                        if (name.isNotEmpty()) {
                            val set = WaypointSet(name)
                            val app = BaseApplication.getApplication<Borkozic>()!!
                            app.addWaypointSet(set)
                            adapter.notifyDataSetChanged()
                        }
                    }
                    .setNegativeButton(R.string.cancel, null).create().show()
            }
            R.id.menuNewWaypoint -> {
                startActivityForResult(Intent(activity, WaypointProperties::class.java).putExtra("INDEX", -1), 0)
                return true
            }
            R.id.menuProjectWaypoint -> {
                startActivityForResult(Intent(activity, WaypointProject::class.java), 0)
                return true
            }
        }
        return true
    }

    private val waypointActionItemClickListener = OnActionItemClickListener { source, pos, actionId ->
        val application = BaseApplication.getApplication<Borkozic>()!!
        val waypoint = adapter.getChild(
            ExpandableListView.getPackedPositionGroup(selectedKey),
            ExpandableListView.getPackedPositionChild(selectedKey)
        ) as Waypoint

        when (actionId) {
            qaWaypointVisible -> waypointActionsCallback?.onWaypointView(waypoint)
            qaWaypointNavigate -> waypointActionsCallback?.onWaypointNavigate(waypoint)
            qaWaypointProperties -> waypointActionsCallback?.onWaypointEdit(waypoint)
            qaWaypointShare -> waypointActionsCallback?.onWaypointShare(waypoint)
            qaWaypointDelete -> {
                waypointActionsCallback?.onWaypointRemove(waypoint)
                adapter.notifyDataSetChanged()
            }
            qaWaypointSetClear -> {
                val set = application.waypointSets[selectedSetKey]
                application.clearWaypoints(set)
                application.saveWaypoints(set)
                adapter.notifyDataSetChanged()
            }
            qaWaypointSetRemove -> {
                if (selectedSetKey > 0) {
                    application.removeWaypointSet(selectedSetKey)
                    adapter.notifyDataSetChanged()
                }
            }
        }
    }

    private val setActionItemClickListener = OnActionItemClickListener { source, pos, actionId ->
        val application = BaseApplication.getApplication<Borkozic>()!!

        when (actionId) {
            qaWaypointSetClear -> {
                val set = application.waypointSets[selectedSetKey]
                application.clearWaypoints(set)
                application.saveWaypoints(set)
                adapter.notifyDataSetChanged()
            }
            qaWaypointSetRemove -> {
                if (selectedSetKey > 0) {
                    application.removeWaypointSet(selectedSetKey)
                    adapter.notifyDataSetChanged()
                }
            }
        }
    }

    inner class WaypointExpandableListAdapter(context: Context) : BaseExpandableListAdapter() {
        private val mInflater: LayoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        private val mExpandedGroupLayout: Int = android.R.layout.simple_expandable_list_item_1
        private val mCollapsedGroupLayout: Int = android.R.layout.simple_expandable_list_item_1
        private val mChildLayout: Int = R.layout.waypoint_list_item
        private val mDensity: Float = context.resources.displayMetrics.density
        private val mBorderPaint: Paint = Paint()
        private val mFillPaint: Paint = Paint()
        private var mPointWidth: Int = 0
        private val application: Borkozic = BaseApplication.getApplication<Borkozic>()!!
        private val loc: DoubleArray = application.getLocation()

        init {
            val settings = PreferenceManager.getDefaultSharedPreferences(context)
            mPointWidth = settings.getInt(context.getString(R.string.pref_waypoint_width), context.resources.getInteger(R.integer.def_waypoint_width))
            mFillPaint.isAntiAlias = false
            mFillPaint.strokeWidth = 1f
            mFillPaint.style = Paint.Style.FILL_AND_STROKE
            mFillPaint.color = settings.getInt(context.getString(R.string.pref_waypoint_color), context.resources.getColor(R.color.waypoint))
            mBorderPaint.isAntiAlias = false
            mBorderPaint.strokeWidth = 1f
            mBorderPaint.style = Paint.Style.STROKE
            mBorderPaint.color = settings.getInt(context.getString(R.string.pref_waypoint_namecolor), context.resources.getColor(R.color.waypointtext))
        }

        override fun getChild(groupPosition: Int, childPosition: Int): Any {
            return application.getWaypoints(application.waypointSets[groupPosition])[childPosition]!!
        }

        override fun getChildId(groupPosition: Int, childPosition: Int): Long = childPosition.toLong()

        override fun getChildrenCount(groupPosition: Int): Int {
            return application.getWaypointCount(application.waypointSets[groupPosition])
        }

        override fun getChildView(groupPosition: Int, childPosition: Int, isLastChild: Boolean, convertView: View?, parent: ViewGroup): View {
            val v: View = convertView ?: mInflater.inflate(mChildLayout, parent, false)
            val wpt = getChild(groupPosition, childPosition) as Waypoint
            var text = v.findViewById<TextView>(R.id.name)
            text?.text = wpt.name
            val coordinates = StringFormatter.coordinates(application.coordinateFormat, " ", wpt.latitude, wpt.longitude)
            text = v.findViewById<TextView>(R.id.coordinates)
            text?.text = coordinates
            val dist = Geo.distance(loc[0], loc[1], wpt.latitude, wpt.longitude)
            val bearing = Geo.bearing(loc[0], loc[1], wpt.latitude, wpt.longitude)
            val distance = StringFormatter.distanceH(dist) + " " + StringFormatter.bearingSimpleH(bearing)
            text = v.findViewById<TextView>(R.id.distance)
            text?.text = distance
            val icon = v.findViewById<ImageView>(R.id.icon)
            var b: Bitmap? = null
            if (application.iconsEnabled && wpt.drawImage) {
                val options = BitmapFactory.Options()
                options.inScaled = false
                b = BitmapFactory.decodeFile(application.iconPath + File.separator + wpt.image, options)
            }
            val h = b?.height ?: 30
            val bm = Bitmap.createBitmap((40 * mDensity).toInt(), h, Bitmap.Config.ARGB_8888)
            bm.eraseColor(Color.TRANSPARENT)
            val bc = Canvas(bm)
            if (b != null) {
                b.density = Bitmap.DENSITY_NONE
                val l = ((38 * mDensity - b.width) / 2).toInt()
                bc.drawBitmap(b, null, Rect(l, 0, b.width + l, b.height), null)
            } else {
                var tc = 0
                var bgc = 0
                if (wpt.textcolor != Int.MIN_VALUE) {
                    tc = mBorderPaint.color
                    mBorderPaint.color = wpt.textcolor
                }
                if (wpt.backcolor != Int.MIN_VALUE) {
                    bgc = mFillPaint.color
                    mFillPaint.color = wpt.backcolor
                }
                val rect = Rect(0, 0, mPointWidth, mPointWidth)
                bc.translate(((38 * mDensity - mPointWidth) / 2), ((30 - mPointWidth) / 2).toFloat())
                bc.drawRect(rect, mBorderPaint)
                rect.inset(1, 1)
                bc.drawRect(rect, mFillPaint)
                if (wpt.textcolor != Int.MIN_VALUE) {
                    mBorderPaint.color = tc
                }
                if (wpt.backcolor != Int.MIN_VALUE) {
                    mFillPaint.color = bgc
                }
            }
            icon.setImageBitmap(bm)

            return v
        }

        override fun getGroup(groupPosition: Int): Any = application.waypointSets[groupPosition]

        override fun getGroupCount(): Int = application.waypointSets.size

        override fun getGroupId(groupPosition: Int): Long = groupPosition.toLong()

        override fun getGroupView(groupPosition: Int, isExpanded: Boolean, convertView: View?, parent: ViewGroup): View {
            val v = convertView ?: mInflater.inflate(if (isExpanded) mExpandedGroupLayout else mCollapsedGroupLayout, parent, false)

            val text = v.findViewById<TextView>(android.R.id.text1)
            text?.text = (getGroup(groupPosition) as WaypointSet).name

            return v
        }

        override fun isChildSelectable(groupPosition: Int, childPosition: Int): Boolean = true

        override fun hasStableIds(): Boolean = true

        fun sort(type: Int) {
            Collections.sort(application.waypoints, Comparator { o1, o2 ->
                if (type == 1) {
                    val dist1 = Geo.distance(loc[0], loc[1], o1.latitude, o1.longitude)
                    val dist2 = Geo.distance(loc[0], loc[1], o2.latitude, o2.longitude)
                    java.lang.Double.compare(dist1, dist2)
                } else {
                    o1.name.compareTo(o2.name, ignoreCase = true)
                }
            })
            notifyDataSetChanged()
        }
    }
}
