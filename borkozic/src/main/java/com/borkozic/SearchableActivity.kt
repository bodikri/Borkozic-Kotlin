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

package com.borkozic

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.location.Address
import android.location.Geocoder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.preference.PreferenceManager
import android.provider.SearchRecentSuggestions
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.borkozic.data.Route
import com.borkozic.data.Track
import com.borkozic.data.Waypoint
import com.borkozic.provider.SuggestionProvider
import com.borkozic.util.CoordinateParser
import com.borkozic.util.Geo
import com.borkozic.util.StringFormatter
import com.jhlabs.map.GeodeticPosition
import java.io.IOException
import java.lang.ref.WeakReference

sealed interface SearchItem {
    object Coordinates : SearchItem
    class WaypointItem(val wp: Waypoint) : SearchItem
    class RouteItem(val route: Route) : SearchItem
    class TrackItem(val track: Track) : SearchItem
    class AddressItem(val address: Address) : SearchItem
}

class SearchableActivity : android.app.ListActivity() {
    private companion object {
        private const val MSG_FINISH = 1
    }

    private var results: MutableList<Any> = ArrayList()
    private var thread: SearchThread? = null
    private lateinit var finishHandler: FinishHandler
    private lateinit var adapter: SearchResultsListAdapter

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(android.view.Window.FEATURE_INDETERMINATE_PROGRESS)
        setContentView(R.layout.list_with_empty_view)

        finishHandler = FinishHandler(this)

        if (Intent.ACTION_SEARCH.equals(intent.action, ignoreCase = true)) {
            val query = intent.getStringExtra(SearchManager.QUERY)
            val suggestions = SearchRecentSuggestions(this, SuggestionProvider.AUTHORITY, SuggestionProvider.MODE)
            suggestions.saveRecentQuery(query, null)
        }

        adapter = SearchResultsListAdapter(this, results)
        setListAdapter(adapter)

        handleIntent(intent)
    }

    @Deprecated("Deprecated in Java")
    override fun onNewIntent(intent: Intent?) {
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (Intent.ACTION_SEARCH.equals(intent?.action, ignoreCase = true)) {
            val query = intent!!.getStringExtra(SearchManager.QUERY)
            doSearch(query)
        }
    }

    private fun doSearch(query: String?) {
        if (query.isNullOrEmpty()) {
            finish()
            return
        }

        setProgressBarIndeterminateVisibility(true)

        val threadRef = thread
        if (threadRef == null) {
            synchronized(results) {
                results.clear()
            }
            adapter.notifyDataSetChanged()
            thread = SearchThread(finishHandler, query)
            thread!!.start()
        } else if (threadRef.isAlive) {
            threadRef.setHandler(finishHandler)
        } else {
            onSearchFinished()
        }
    }

    override fun onBackPressed() {
        thread = null
        super.onBackPressed()
    }

    private fun onSearchFinished() {
        val emptyView = listView.emptyView as? TextView
        emptyView?.text = getString(R.string.msg_nothing_found)
        setProgressBarIndeterminateVisibility(false)
        adapter.notifyDataSetChanged()
    }

    @SuppressLint("HandlerLeak")
    private inner class FinishHandler(activity: SearchableActivity) : Handler(Looper.getMainLooper()) {
        private val target: WeakReference<SearchableActivity> = WeakReference(activity)

        override fun handleMessage(msg: Message) {
            val activity = target.get() ?: return
            when (msg.what) {
                MSG_FINISH -> activity.onSearchFinished()
            }
        }
    }

    private inner class SearchThread(h: Handler, q: String) : Thread() {
        private var handler: Handler = h
        private val query = q

        override fun run() {
            val application = application as Borkozic

            // Coordinates
            val c = CoordinateParser.parse(query)
            if (!c[0].isNaN() && !c[1].isNaN()) {
                val coordinates = GeodeticPosition(c[0], c[1])
                synchronized(results) {
                    results.add(coordinates)
                }
                runOnUiThread(updateResults)
            }

            val lq = query.lowercase()

            // Waypoints
            for (waypoint in application.waypoints) {
                if (waypoint.name.lowercase().contains(lq) || waypoint.description.lowercase().contains(lq)) {
                    synchronized(results) {
                        results.add(waypoint)
                    }
                    runOnUiThread(updateResults)
                }
            }

            // Routes
            for (route in application.routes) {
                if (route.name.lowercase().contains(lq) || route.description.lowercase().contains(lq)) {
                    synchronized(results) {
                        results.add(route)
                    }
                    runOnUiThread(updateResults)
                    continue
                }
                for (waypoint in route.waypoints) {
                    if (waypoint.name.lowercase().contains(lq) || waypoint.description.lowercase().contains(lq)) {
                        synchronized(results) {
                            results.add(route)
                        }
                        runOnUiThread(updateResults)
                        break
                    }
                }
            }

            // Tracks
            for (track in application.tracks) {
                if (track.name.lowercase().contains(lq) || track.description.lowercase().contains(lq)) {
                    synchronized(results) {
                        results.add(track)
                    }
                    runOnUiThread(updateResults)
                }
            }

            // Addresses
            try {
                val geocoder = Geocoder(application)
                val addresses = geocoder.getFromLocationName(query, 15)
                if (!addresses.isNullOrEmpty()) {
                    synchronized(results) {
                        results.addAll(addresses)
                    }
                    runOnUiThread(updateResults)
                }
            } catch (e: IOException) {
                runOnUiThread(noConnection)
            }

            synchronized(this) {
                handler.sendEmptyMessage(MSG_FINISH)
            }
        }

        @Synchronized
        fun setHandler(h: Handler) {
            handler = h
        }
    }

    private val noConnection = Runnable {
        Toast.makeText(baseContext, getString(R.string.err_noconnection), Toast.LENGTH_LONG).show()
    }

    private val updateResults = Runnable {
        adapter.notifyDataSetChanged()
    }

    @Deprecated("Deprecated in Java")
    override fun onListItemClick(l: ListView?, v: View?, position: Int, id: Long) {
        val item = adapter.getItem(position)
        val application = BaseApplication.getApplication<Borkozic>()!!
        val location = application.getLocation()!!

        when (item) {
            is GeodeticPosition -> {
                location[0] = item.lat
                location[1] = item.lon
            }
            is Waypoint -> {
                location[0] = item.latitude
                location[1] = item.longitude
            }
            is Route -> {
                val wp = item.waypoints[0]
                location[0] = wp.latitude
                location[1] = wp.longitude
            }
            is Track -> {
                val tp = item.points[0] as Track.TrackPoint
                location[0] = tp.latitude
                location[1] = tp.longitude
            }
            is Address -> {
                location[0] = item.latitude
                location[1] = item.longitude
            }
        }

        application.ensureVisible(location[0], location[1])
        finish()
    }

    inner class SearchResultsListAdapter(
        context: Context,
        private val items: MutableList<Any>
    ) : BaseAdapter() {
        private val inflater: LayoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        private val density: Float = context.resources.displayMetrics.density

        private val coordinatesItemLayout = R.layout.list_item_coordinates
        private val waypointItemLayout = R.layout.waypoint_list_item
        private val routeItemLayout = R.layout.route_list_item
        private val trackItemLayout = R.layout.list_item_track
        private val addressItemLayout = R.layout.list_item_address

        private val mApplication: Borkozic = BaseApplication.getApplication<Borkozic>()!!
        private val mLocation: DoubleArray = mApplication.getLocation()!!

        private val mWaypointBorderPaint: Paint
        private val mWaypointFillPaint: Paint
        private var mPointWidth: Int = 0

        private val mRouteLinePath: Path
        private val mRouteFillPaint: Paint
        private val mRouteLinePaint: Paint
        private val mRouteBorderPaint: Paint
        private var mRouteWidth: Int = 0

        init {
            val settings: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            val res = context.resources

            mPointWidth = settings.getInt(
                context.getString(R.string.pref_waypoint_width),
                res.getInteger(R.integer.def_waypoint_width)
            )

            mWaypointFillPaint = Paint().apply {
                isAntiAlias = false
                strokeWidth = 1f
                style = Paint.Style.FILL_AND_STROKE
                color = settings.getInt(
                    context.getString(R.string.pref_waypoint_color),
                    res.getColor(R.color.waypoint)
                )
            }
            mWaypointBorderPaint = Paint().apply {
                isAntiAlias = false
                strokeWidth = 1f
                style = Paint.Style.STROKE
                color = res.getColor(R.color.waypointtext)
                color = settings.getInt(
                    context.getString(R.string.pref_waypoint_namecolor),
                    res.getColor(R.color.waypointtext)
                )
            }

            mRouteWidth = settings.getInt(
                context.getString(R.string.pref_route_linewidth),
                res.getInteger(R.integer.def_route_linewidth)
            )
            mRouteLinePath = Path().apply {
                setLastPoint(12 * density, 5 * density)
                lineTo(24 * density, 12 * density)
                lineTo(15 * density, 24 * density)
                lineTo(28 * density, 35 * density)
            }
            mRouteFillPaint = Paint().apply {
                isAntiAlias = false
                strokeWidth = 1f
                style = Paint.Style.FILL_AND_STROKE
                color = res.getColor(R.color.routewaypoint)
            }
            mRouteLinePaint = Paint().apply {
                isAntiAlias = true
                strokeWidth = (mRouteWidth * density).toFloat()
                style = Paint.Style.STROKE
                color = res.getColor(R.color.routeline)
            }
            mRouteBorderPaint = Paint().apply {
                isAntiAlias = true
                strokeWidth = 1f
                style = Paint.Style.STROKE
                color = res.getColor(R.color.routeline)
            }
        }

        override fun getItem(position: Int): Any {
            synchronized(items) {
                return items[position]
            }
        }

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getCount(): Int {
            synchronized(items) {
                return items.size
            }
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val item = getItem(position)

            val isCoordinates = item is GeodeticPosition
            val isWaypoint = item is Waypoint
            val isRoute = item is Route
            val isTrack = item is Track
            val isAddress = item is Address

            val coordinates: GeodeticPosition? = if (isCoordinates) item as GeodeticPosition else null
            val waypoint: Waypoint? = if (isWaypoint) item as Waypoint else null
            val route: Route? = if (isRoute) item as Route else null
            val track: Track? = if (isTrack) item as Track else null
            val address: Address? = if (isAddress) item as Address else null

            val layout = if (isCoordinates) coordinatesItemLayout
            else if (isWaypoint) waypointItemLayout
            else if (isRoute) routeItemLayout
            else if (isTrack) trackItemLayout
            else if (isAddress) addressItemLayout
            else android.R.layout.simple_list_item_1

            val v = inflater.inflate(layout, parent, false)

            if (isCoordinates) {
                val textView = v.findViewById<TextView>(R.id.name)
                val coords = StringFormatter.coordinates(
                    mApplication.coordinateFormat, " ",
                    coordinates!!.lat, coordinates.lon
                )
                textView.text = coords
                val dist = Geo.distance(mLocation[0], mLocation[1], coordinates.lat, coordinates.lon)
                val bearing = Geo.bearing(mLocation[0], mLocation[1], coordinates.lat, coordinates.lon)
                val distance = StringFormatter.distanceH(dist) + " " + StringFormatter.bearingSimpleH(bearing)
                v.findViewById<TextView>(R.id.distance).text = distance
            } else if (isWaypoint) {
                val textView = v.findViewById<TextView>(R.id.name)
                textView.text = waypoint!!.name
                val coords = StringFormatter.coordinates(mApplication.coordinateFormat, " ", waypoint.latitude, waypoint.longitude)
                v.findViewById<TextView>(R.id.coordinates).text = coords
                val dist = Geo.distance(mLocation[0], mLocation[1], waypoint.latitude, waypoint.longitude)
                val bearing = Geo.bearing(mLocation[0], mLocation[1], waypoint.latitude, waypoint.longitude)
                v.findViewById<TextView>(R.id.distance).text = StringFormatter.distanceH(dist) + " " + StringFormatter.bearingSimpleH(bearing)

                val icon = v.findViewById<ImageView>(R.id.icon)
                val b: Bitmap? = if (mApplication.iconsEnabled && waypoint.drawImage) {
                    val options = BitmapFactory.Options().apply { inScaled = false }
                    BitmapFactory.decodeFile(mApplication.iconPath!! + java.io.File.separator + waypoint.image!!, options)
                } else null

                val h = b?.height ?: 30
                val bm = Bitmap.createBitmap((40 * density).toInt(), h, Bitmap.Config.ARGB_8888)
                val bc = Canvas(bm)
                bm.eraseColor(Color.TRANSPARENT)

                if (b != null) {
                    b.setDensity(Bitmap.DENSITY_NONE)
                    val l = ((38 * density - b.width) / 2).toInt()
                    bc.drawBitmap(b, null, Rect(l, 0, b.width + l, b.height), null)
                } else {
                    var tc = 0
                    var bgc = 0
                    if (waypoint.textcolor != Int.MIN_VALUE) {
                        tc = mWaypointBorderPaint.color
                        mWaypointBorderPaint.color = waypoint.textcolor
                    }
                    if (waypoint.backcolor != Int.MIN_VALUE) {
                        bgc = mWaypointFillPaint.color
                        mWaypointFillPaint.color = waypoint.backcolor
                    }
                    val rect = Rect(0, 0, mPointWidth, mPointWidth)
                    bc.translate((38 * density - mPointWidth) / 2f, (30f - mPointWidth.toFloat()) / 2f)
                    bc.drawRect(rect, mWaypointBorderPaint)
                    rect.inset(1, 1)
                    bc.drawRect(rect, mWaypointFillPaint)
                    if (waypoint.textcolor != Int.MIN_VALUE) {
                        mWaypointBorderPaint.color = tc
                    }
                    if (waypoint.backcolor != Int.MIN_VALUE) {
                        mWaypointFillPaint.color = bgc
                    }
                }
                icon.setImageBitmap(bm)
            } else if (isRoute) {
                val textView = v.findViewById<TextView>(R.id.name)
                textView.text = route!!.name
                v.findViewById<TextView>(R.id.distance).text = StringFormatter.distanceH(route.distance)
                val filepathView = v.findViewById<TextView>(R.id.filename)
                route.filepath?.let { filepath ->
                    if (filepath.startsWith(mApplication.dataPath!!)) {
                        filepathView.text = filepath.substring(mApplication.dataPath!!.length + 1)
                    } else {
                        filepathView.text = filepath
                    }
                }
                val icon = v.findViewById<ImageView>(R.id.icon)
                val bm = Bitmap.createBitmap((40 * density).toInt(), (40 * density).toInt(), Bitmap.Config.ARGB_8888)
                val bc = Canvas(bm)
                bm.eraseColor(Color.TRANSPARENT)
                mRouteLinePaint.color = route.lineColor
                mRouteBorderPaint.color = route.lineColor
                bc.drawPath(mRouteLinePath, mRouteLinePaint)
                val half = Math.round((mPointWidth / 4).toFloat())
                bc.drawCircle((12 * density).toFloat(), (5 * density).toFloat(), half.toFloat(), mRouteFillPaint)
                bc.drawCircle((12 * density).toFloat(), (5 * density).toFloat(), half.toFloat(), mRouteBorderPaint)
                bc.drawCircle((24 * density).toFloat(), (12 * density).toFloat(), half.toFloat(), mRouteFillPaint)
                bc.drawCircle((24 * density).toFloat(), (12 * density).toFloat(), half.toFloat(), mRouteBorderPaint)
                bc.drawCircle((15 * density).toFloat(), (24 * density).toFloat(), half.toFloat(), mRouteFillPaint)
                bc.drawCircle((15 * density).toFloat(), (24 * density).toFloat(), half.toFloat(), mRouteBorderPaint)
                bc.drawCircle((28 * density).toFloat(), (35 * density).toFloat(), half.toFloat(), mRouteFillPaint)
                bc.drawCircle((28 * density).toFloat(), (35 * density).toFloat(), half.toFloat(), mRouteBorderPaint)
                icon.setImageBitmap(bm)
            } else if (isTrack) {
                val textView = v.findViewById<TextView>(R.id.name)
                textView.text = track!!.name
                v.findViewById<TextView>(R.id.distance).text = StringFormatter.distanceH(track.distance)
                val filepathView = v.findViewById<TextView>(R.id.filename)
                track.filepath?.let { filepath ->
                    if (filepath.startsWith(mApplication.dataPath!!)) {
                        filepathView.text = filepath.substring(mApplication.dataPath!!.length + 1)
                    } else {
                        filepathView.text = filepath
                    }
                }
                val icon = v.findViewById<ImageView>(R.id.icon)
                val bm = Bitmap.createBitmap((40 * density).toInt(), (40 * density).toInt(), Bitmap.Config.ARGB_8888)
                val bc = Canvas(bm)
                bm.eraseColor(Color.TRANSPARENT)
                mRouteLinePaint.color = track.color
                bc.drawPath(mRouteLinePath, mRouteLinePaint)
                icon.setImageBitmap(bm)
            } else if (isAddress) {
                val name = address!!.featureName
                val sb = StringBuilder()
                for (i in 0 until address.maxAddressLineIndex) {
                    sb.append(address.getAddressLine(i))
                    if (i < address.maxAddressLineIndex - 1) {
                        sb.append(" ")
                    }
                }
                val addr = sb.toString()
                val fullName = if (!addr.contains(name)) name + " " + addr else addr
                v.findViewById<TextView>(R.id.name).text = fullName
                val coords = StringFormatter.coordinates(mApplication.coordinateFormat, " ", address.latitude, address.longitude)
                v.findViewById<TextView>(R.id.coordinates).text = coords
                val dist = Geo.distance(mLocation[0], mLocation[1], address.latitude, address.longitude)
                val bearing = Geo.bearing(mLocation[0], mLocation[1], address.latitude, address.longitude)
                v.findViewById<TextView>(R.id.distance).text = StringFormatter.distanceH(dist) + " " + StringFormatter.bearingSimpleH(bearing)
            }

            return v
        }

        override fun hasStableIds(): Boolean = true
    }
}
