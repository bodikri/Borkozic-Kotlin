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

package com.borkozic.track

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
import android.preference.PreferenceManager
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
import com.borkozic.BaseApplication
import com.borkozic.R
import com.borkozic.data.Track
import com.borkozic.util.StringFormatter
import net.londatiga.android.ActionItem
import net.londatiga.android.QuickAction
import net.londatiga.android.QuickAction.OnActionItemClickListener
import java.util.List
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class TrackList : ListFragment() {
    var tracks: List<Track>? = null

    private val qaTrackProperties = 1
    private val qaTrackEdit = 2
    private val qaTrackToRoute = 3
    private val qaTrackSave = 4
    private val qaTrackRemove = 5

    private var trackActionsCallback: OnTrackActionListener? = null

    protected var threadPool: ExecutorService = Executors.newFixedThreadPool(2)
    val handler = Handler()

    private var adapter: TrackListAdapter? = null
    private var quickAction: QuickAction? = null
    private var selectedKey = 0
    private var selectedBackground: Drawable? = null

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
        if (emptyView != null) emptyView.setText(R.string.msg_empty_track_list)

        val activity = activity!!

        adapter = TrackListAdapter(activity)
        listAdapter = adapter

        val resources = resources
        quickAction = QuickAction(activity)
        quickAction!!.addActionItem(
            ActionItem(
                qaTrackProperties,
                getString(R.string.menu_properties),
                resources.getDrawable(R.drawable.ic_action_edit)
            )
        )
        quickAction!!.addActionItem(
            ActionItem(
                qaTrackEdit,
                getString(R.string.menu_edit),
                resources.getDrawable(R.drawable.ic_action_track)
            )
        )
        quickAction!!.addActionItem(
            ActionItem(
                qaTrackToRoute,
                getString(R.string.menu_track2route),
                resources.getDrawable(R.drawable.ic_action_directions)
            )
        )
        quickAction!!.addActionItem(
            ActionItem(
                qaTrackSave,
                getString(R.string.menu_save),
                resources.getDrawable(R.drawable.ic_action_save)
            )
        )
        quickAction!!.addActionItem(
            ActionItem(
                qaTrackRemove,
                getString(R.string.menu_remove),
                resources.getDrawable(R.drawable.ic_action_cancel)
            )
        )

        quickAction!!.setOnActionItemClickListener(trackActionItemClickListener)
        quickAction!!.setOnDismissListener(object : PopupWindow.OnDismissListener {
            override fun onDismiss() {
                val v = listView.findViewWithTag<View>("selected")
                if (v != null) {
                    v.background = selectedBackground
                    v.tag = null
                }
            }
        })
    }

    override fun onAttach(activity: Activity) {
        super.onAttach(activity)

        // This makes sure that the container activity has implemented
        // the callback interface. If not, it throws an exception
        try {
            trackActionsCallback = activity as OnTrackActionListener
        } catch (e: ClassCastException) {
            throw ClassCastException(activity.toString() + " must implement OnTrackActionListener")
        }
    }

    override fun onResume() {
        super.onResume()
        adapter!!.notifyDataSetChanged()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.tracklist_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menuLoadTrack -> {
                activity!!.startActivityForResult(
                    Intent(activity, TrackFileList::class.java),
                    TrackListActivity.RESULT_LOAD_TRACK
                )
                return true
            }
        }
        return false
    }

    override fun onListItemClick(lv: ListView, v: View, position: Int, id: Long) {
        v.tag = "selected"
        selectedKey = position
        selectedBackground = v.background
        val l = v.paddingLeft
        val t = v.paddingTop
        val r = v.paddingRight
        val b = v.paddingBottom
        v.setBackgroundResource(R.drawable.list_selector_background_focus)
        v.setPadding(l, t, r, b)
        quickAction!!.show(v)
    }

    private val trackActionItemClickListener = object : OnActionItemClickListener {
        override fun onItemClick(source: QuickAction, pos: Int, actionId: Int) {
            val position = selectedKey
            val application = BaseApplication.getApplication<Borkozic>()!!
            val track = application.getTrack(position)!!

            when (actionId) {
                qaTrackProperties -> trackActionsCallback!!.onTrackEdit(track)
                qaTrackEdit -> trackActionsCallback!!.onTrackEditPath(track)
                qaTrackToRoute -> trackActionsCallback!!.onTrackToRoute(track)
                qaTrackSave -> trackActionsCallback!!.onTrackSave(track)
                qaTrackRemove -> {
                    application.removeTrack(track)
                    adapter!!.notifyDataSetChanged()
                }
            }
        }
    }

    inner class TrackListAdapter internal constructor(context: Context) : BaseAdapter() {
        private val mInflater: LayoutInflater
        private val mItemLayout: Int
        private val mDensity: Float
        private val mLinePath: Path
        private val mLinePaint: Paint
        private val mTrackWidth: Int
        private val application: Borkozic

        init {
            mItemLayout = R.layout.list_item_track
            mInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            mDensity = context.resources.displayMetrics.density

            mLinePath = Path()
            mLinePath.setLastPoint(12 * mDensity, 5 * mDensity)
            mLinePath.lineTo(24 * mDensity, 12 * mDensity)
            mLinePath.lineTo(15 * mDensity, 24 * mDensity)
            mLinePath.lineTo(28 * mDensity, 35 * mDensity)

            val settings = PreferenceManager.getDefaultSharedPreferences(context)

            mTrackWidth = settings.getInt(
                context.getString(R.string.pref_tracking_linewidth),
                context.resources.getInteger(R.integer.def_track_linewidth)
            )
            mLinePaint = Paint()
            mLinePaint.isAntiAlias = true
            mLinePaint.strokeWidth = mTrackWidth * mDensity
            mLinePaint.style = Paint.Style.STROKE
            mLinePaint.color = context.resources.getColor(R.color.routeline)

            application = BaseApplication.getApplication<Borkozic>()!!
        }

        override fun getItem(position: Int): Track {
            return application.getTrack(position)!!
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getCount(): Int {
            return application.tracks.size
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v: View
            if (convertView == null) {
                v = mInflater.inflate(mItemLayout, parent, false)
            } else {
                v = convertView
            }
            val track = getItem(position)
            var text = v.findViewById<TextView>(R.id.name)
            text.text = track.name
            val distance = StringFormatter.distanceH(track.distance)
            text = v.findViewById(R.id.distance)
            text.text = distance
            text = v.findViewById(R.id.filename)
            if (track.filepath != null) {
                val filepath = if (track.filepath!!.startsWith(application.dataPath!!)) track.filepath!!.substring(
                    application.dataPath!!.length + 1,
                    track.filepath!!.length
                ) else track.filepath
                text.text = filepath
            } else {
                text.text = ""
            }
            val icon = v.findViewById<ImageView>(R.id.icon)
            val bm = Bitmap.createBitmap((40 * mDensity).toInt(), (40 * mDensity).toInt(), Config.ARGB_8888)
            bm.eraseColor(Color.TRANSPARENT)
            val bc = Canvas(bm)
            mLinePaint.color = track.color
            bc.drawPath(mLinePath, mLinePaint)
            icon.setImageBitmap(bm)

            return v
        }

        override fun hasStableIds(): Boolean {
            return true
        }
    }
}