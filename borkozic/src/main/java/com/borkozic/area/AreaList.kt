package com.borkozic.area
import com.borkozic.BaseApplication

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.*
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
import androidx.annotation.NonNull
import androidx.fragment.app.ListFragment
import com.borkozic.Borkozic
import com.borkozic.R
import com.borkozic.data.Area
import com.borkozic.util.StringFormatter
import net.londatiga.android.ActionItem
import net.londatiga.android.QuickAction
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AreaList : ListFragment() {

    private var areaActionsCallback: OnAreaActionListener? = null

    private val threadPool: ExecutorService = Executors.newFixedThreadPool(2)
    private val handler = Handler()

    private lateinit var adapter: AreaListAdapter
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

        val emptyView = listView.emptyView as? TextView
        emptyView?.setText(R.string.msg_empty_area_list)

        val activity = requireActivity()

        mode = activity.intent.extras!!.getInt("MODE")

        if (mode == MODE_START)
            activity.setTitle(getString(R.string.selectarea_name))

        adapter = AreaListAdapter(activity)
        listAdapter = adapter

        val resources = resources
        quickAction = QuickAction(activity)
        quickAction.addActionItem(ActionItem(qaAreaDetails, getString(R.string.menu_details), resources.getDrawable(R.drawable.ic_action_list)))
        quickAction.addActionItem(ActionItem(qaAreaNavigate, getString(R.string.menu_navigate), resources.getDrawable(R.drawable.ic_action_directions)))
        quickAction.addActionItem(ActionItem(qaAreaProperties, getString(R.string.menu_properties), resources.getDrawable(R.drawable.ic_action_edit)))
        quickAction.addActionItem(ActionItem(qaAreaEdit, getString(R.string.menu_edit), resources.getDrawable(R.drawable.ic_action_track)))
        quickAction.addActionItem(ActionItem(qaAreaSave, getString(R.string.menu_save), resources.getDrawable(R.drawable.ic_action_save)))
        quickAction.addActionItem(ActionItem(qaAreaRemove, getString(R.string.menu_remove), resources.getDrawable(R.drawable.ic_action_cancel)))

        quickAction.setOnActionItemClickListener(areaActionItemClickListener)
        quickAction.setOnDismissListener(PopupWindow.OnDismissListener {
            val v = listView.findViewWithTag<View>("selected")
            v?.background = selectedBackground
            v?.tag = null
        })
    }

    override fun onAttach(@NonNull context: Context) {
        super.onAttach(context)
        try {
            areaActionsCallback = context as OnAreaActionListener
        } catch (e: ClassCastException) {
            throw ClassCastException(context.toString() + " must implement OnAreaActionListener")
        }
    }

    override fun onResume() {
        super.onResume()
        adapter.notifyDataSetChanged()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        if (mode == MODE_MANAGE) {
            inflater.inflate(R.menu.menu_area_list, menu)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menuNewArea -> {
                val application: Borkozic = BaseApplication.getApplication<Borkozic>()!!
                val area = Area("New Area", "", null, true, 10.0, 1000.0)
                application.addArea(area)
                areaActionsCallback?.onAreaEdit(area)
                return true
            }
            R.id.menuLoadArea -> {
                requireActivity().startActivityForResult(Intent(activity, AreaFileList::class.java), AreaListActivity.RESULT_LOAD_AREA)
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
                val application: Borkozic = BaseApplication.getApplication<Borkozic>()!!
                val area = application.getArea(position)!!
                areaActionsCallback?.onAreaNavigate(area)
            }
        }
    }

    private val areaActionItemClickListener = QuickAction.OnActionItemClickListener { _, _, actionId ->
        val application: Borkozic = BaseApplication.getApplication<Borkozic>()!!
        val area = application.getArea(selectedKey)!!

        when (actionId) {
            qaAreaDetails -> areaActionsCallback?.onAreaDetails(area)
            qaAreaNavigate -> areaActionsCallback?.onAreaNavigate(area)
            qaAreaProperties -> areaActionsCallback?.onAreaEdit(area)
            qaAreaEdit -> areaActionsCallback?.onAreaEditPath(area)
            qaAreaSave -> areaActionsCallback?.onAreaSave(area)
            qaAreaRemove -> {
                application.removeArea(area)
                adapter.notifyDataSetChanged()
            }
        }
    }

    inner class AreaListAdapter(context: Context) : BaseAdapter() {
        private val mInflater: LayoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        private val mItemLayout: Int = R.layout.area_list_item
        private val mDensity: Float = context.resources.displayMetrics.density
        private val mLinePath: Path = Path()
        private val mFillPaint: Paint
        private val mLinePaint: Paint
        private val mBorderPaint: Paint
        private val mPointWidth: Int
        private val mAreaWidth: Int
        private val application: Borkozic

        init {
            val settings: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

            mLinePath.setLastPoint(12 * mDensity, 5 * mDensity)
            mLinePath.lineTo(24 * mDensity, 12 * mDensity)
            mLinePath.lineTo(15 * mDensity, 24 * mDensity)
            mLinePath.lineTo(28 * mDensity, 35 * mDensity)

            mPointWidth = settings.getInt(context.getString(R.string.pref_waypoint_width), context.resources.getInteger(R.integer.def_waypoint_width))
            mAreaWidth = settings.getInt(context.getString(R.string.pref_area_linewidth), context.resources.getInteger(R.integer.def_area_linewidth))
            mFillPaint = Paint().apply {
                isAntiAlias = false
                strokeWidth = 1f
                style = Paint.Style.FILL_AND_STROKE
                color = context.resources.getColor(R.color.areawaypoint)
            }
            mLinePaint = Paint().apply {
                isAntiAlias = true
                strokeWidth = mAreaWidth * mDensity
                style = Paint.Style.STROKE
                color = context.resources.getColor(R.color.arealinecolor)
            }
            mBorderPaint = Paint().apply {
                isAntiAlias = true
                strokeWidth = 1f
                style = Paint.Style.FILL_AND_STROKE
                color = context.resources.getColor(R.color.areacolor)
            }
            application = BaseApplication.getApplication<Borkozic>()!! as Borkozic
        }

        override fun getItem(position: Int): Area {
            return application.getArea(position)!!
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getCount(): Int {
            return application.areas.size
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v: View = if (convertView == null) {
                mInflater.inflate(mItemLayout, parent, false)
            } else {
                convertView
            }
            val area = getItem(position)!!
            var text: TextView? = v.findViewById<TextView>(R.id.name)
            text?.text = area.name
            val distance = StringFormatter.distanceH(area.distance)
            text = v.findViewById<TextView>(R.id.distance)
            text?.text = distance
            text = v.findViewById<TextView>(R.id.filename)
            val fp = area.filepath
            if (fp != null) {
                val filepath = if (fp.startsWith(application.dataPath!!)) fp.substring(application.dataPath!!.length + 1) else fp
                text?.text = filepath
            } else {
                text?.text = ""
            }
            val icon = v.findViewById<ImageView>(R.id.icon)
            val bm = Bitmap.createBitmap((40 * mDensity).toInt(), (40 * mDensity).toInt(), Bitmap.Config.ARGB_8888)
            bm.eraseColor(Color.TRANSPARENT)
            val bc = Canvas(bm)
            mLinePaint.color = area.lineColor
            mBorderPaint.color = area.fillColor
            bc.drawPath(mLinePath, mLinePaint)
            val half = Math.round(mPointWidth / 4f).toFloat()
            bc.drawCircle(12f * mDensity, 5f * mDensity, half, mFillPaint)
            bc.drawCircle(12f * mDensity, 5f * mDensity, half, mBorderPaint)
            bc.drawCircle(24f * mDensity, 12f * mDensity, half, mFillPaint)
            bc.drawCircle(24f * mDensity, 12f * mDensity, half, mBorderPaint)
            bc.drawCircle(15f * mDensity, 24f * mDensity, half, mFillPaint)
            bc.drawCircle(15f * mDensity, 24f * mDensity, half, mBorderPaint)
            bc.drawCircle(28f * mDensity, 35f * mDensity, half, mFillPaint)
            bc.drawCircle(28f * mDensity, 35f * mDensity, half, mBorderPaint)
            icon.setImageBitmap(bm)

            return v
        }

        override fun hasStableIds(): Boolean {
            return true
        }
    }

    companion object {
        const val MODE_MANAGE = 1
        const val MODE_START = 2

        const val qaAreaDetails = 1
        const val qaAreaNavigate = 2
        const val qaAreaProperties = 3
        const val qaAreaEdit = 4
        const val qaAreaSave = 5
        const val qaAreaRemove = 6
    }
}