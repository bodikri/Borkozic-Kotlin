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
import android.annotation.TargetApi
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.content.DialogInterface.OnKeyListener
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.Editor
import android.content.pm.PackageManager
import android.content.res.Resources
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Message
import android.preference.PreferenceManager
import android.text.Html
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.View.OnClickListener
import android.view.Window
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import com.borkozic.data.Area
import com.borkozic.data.Route
import com.borkozic.data.Track
import com.borkozic.overlay.AreaOverlay
import com.borkozic.overlay.CurrentTrackOverlay
import com.borkozic.overlay.RouteOverlay
import com.borkozic.util.AreaFilenameFilter
import com.borkozic.util.AutoloadedRouteFilenameFilter
import com.borkozic.util.FileList
import com.borkozic.util.GpxFiles
import com.borkozic.util.KmlFiles
import com.borkozic.util.OziExplorerFiles
import java.io.File
import java.io.IOException
import java.util.ArrayList

@Suppress("DEPRECATION")
class Splash : Activity(), OnClickListener {

    companion object {
        private const val MSG_FINISH = 1
        private const val MSG_ERROR = 2
        private const val MSG_STATUS = 3
        private const val MSG_PROGRESS = 4
        private const val MSG_ASK = 5
        private const val MSG_SAY = 6

        private const val RES_YES = 1
        private const val RES_NO = 2
        private const val PROGRESS_STEP = 10000
        private const val ALL_PERMISSIONS_RESULT = 101
    }

    private var result = 0
    private var wait = false
    protected var savedMessage: String? = null
    private lateinit var progress: ProgressBar
    private lateinit var message: TextView
    private lateinit var gotit: Button
    private lateinit var yes: Button
    private lateinit var no: Button
    private lateinit var quit: Button
    protected lateinit var application: Borkozic
    private var eulaDialog: AlertDialog? = null

    // Permission attributes
    private val permissions = ArrayList<String>()
    private var permissionsToRequest: ArrayList<String>? = null
    private val permissionsRejected = ArrayList<String>()

    private fun getAppBaseDir(): File {
        val externalDir = getExternalFilesDir(null)
        if (externalDir != null) return externalDir
        val internalDir = getFilesDir()
        if (internalDir != null) {
            Log.w("Splash", "Using internal files dir as fallback")
            return internalDir
        }
        throw RuntimeException("Cannot get app base directory")
    }

    @SuppressLint("NewApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestWindowFeature(Window.FEATURE_NO_TITLE)

        permissions.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
        permissions.add(android.Manifest.permission.INTERNET)
        permissions.add(android.Manifest.permission.ACCESS_COARSE_LOCATION)
        permissions.add(android.Manifest.permission.ACCESS_LOCATION_EXTRA_COMMANDS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        permissionsToRequest = findUnAskedPermissions(permissions)
        application = getApplication() as Borkozic

        @Suppress("DEPRECATION")
        PreferenceManager.setDefaultValues(this, R.xml.pref_behavior, true)
        @Suppress("DEPRECATION")
        PreferenceManager.setDefaultValues(this, R.xml.pref_folder, true)
        @Suppress("DEPRECATION")
        PreferenceManager.setDefaultValues(this, R.xml.pref_location, true)
        @Suppress("DEPRECATION")
        PreferenceManager.setDefaultValues(this, R.xml.pref_display, true)
        @Suppress("DEPRECATION")
        PreferenceManager.setDefaultValues(this, R.xml.pref_unit, true)
        @Suppress("DEPRECATION")
        PreferenceManager.setDefaultValues(this, R.xml.pref_tracking, true)
        @Suppress("DEPRECATION")
        PreferenceManager.setDefaultValues(this, R.xml.pref_waypoint, true)
        @Suppress("DEPRECATION")
        PreferenceManager.setDefaultValues(this, R.xml.pref_route, true)
        @Suppress("DEPRECATION")
        PreferenceManager.setDefaultValues(this, R.xml.pref_navigation, true)
        @Suppress("DEPRECATION")
        PreferenceManager.setDefaultValues(this, R.xml.pref_general, true)

        setContentView(R.layout.act_splash)

        progress = findViewById(R.id.progress)
        message = findViewById(R.id.message)

        message.setText(getString(R.string.msg_wait))
        progress.max = PROGRESS_STEP * 4

        yes = findViewById(R.id.yes)
        yes.setOnClickListener(this)
        no = findViewById(R.id.no)
        no.setOnClickListener(this)
        gotit = findViewById(R.id.gotit)
        gotit.setOnClickListener(this)
        quit = findViewById(R.id.quit)
        quit.setOnClickListener(this)

        wait = true

        showEula()

        if (!application.mapsInited) {
            InitializationThread(progressHandler).start()
        } else {
            progressHandler.sendEmptyMessage(MSG_FINISH)
        }
    }

    private fun showEula() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val hasBeenShown = prefs.getBoolean(getString(R.string.app_eulaaccepted), false)

        if (!hasBeenShown) {
            val eulaText = getString(R.string.app_eula).replace("/n", "<br/>")
            val message: SpannableString = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                SpannableString(Html.fromHtml(eulaText, Html.FROM_HTML_MODE_LEGACY))
            } else {
                SpannableString(Html.fromHtml(eulaText))
            }

            Linkify.addLinks(message, Linkify.WEB_URLS)

            eulaDialog = AlertDialog.Builder(this)
                .setTitle(getString(R.string.app_name))
                .setIcon(R.drawable.icon)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok) { dialogInterface, _ ->
                    prefs.edit().putBoolean(getString(R.string.app_eulaaccepted), true).commit()
                    this@Splash.wait = false
                    dialogInterface.dismiss()
                }
                .setOnKeyListener { dialoginterface, keyCode, event ->
                    keyCode != KeyEvent.KEYCODE_HOME
                }
                .setCancelable(false)
                .create()

            eulaDialog?.show()
            // Make the textview clickable. Must be called after show()
            (eulaDialog?.findViewById<TextView>(android.R.id.message))?.movementMethod = LinkMovementMethod.getInstance()
        } else {
            wait = false
        }

        if (permissionsToRequest!!.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(permissionsToRequest!!.toTypedArray(), ALL_PERMISSIONS_RESULT)
        }
    }

    private val progressHandler = object : Handler() {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MSG_STATUS -> {
                    message.text = msg.data.getString("message")
                }
                MSG_PROGRESS -> {
                    val total = msg.data.getInt("total")
                    progress.progress = total
                }
                MSG_ASK -> {
                    progress.visibility = View.GONE
                    savedMessage = message.text.toString()
                    message.text = msg.data.getString("message")
                    result = 0
                    yes.visibility = View.VISIBLE
                    no.visibility = View.VISIBLE
                }
                MSG_SAY -> {
                    progress.visibility = View.GONE
                    savedMessage = message.text.toString()
                    message.text = msg.data.getString("message")
                    result = 0
                    gotit.visibility = View.VISIBLE
                }
                MSG_FINISH -> {
                    startActivity(Intent(this@Splash, MapActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtras(intent))
                    finish()
                }
                MSG_ERROR -> {
                    progress.visibility = View.INVISIBLE
                    message.text = msg.data.getString("message")
                    quit.visibility = View.VISIBLE
                }
            }
        }
    }

    private inner class InitializationThread(private val mHandler: Handler) : Thread() {
        private var total = 0

        override fun run() {
            while (wait) {
                try {
                    sleep(100)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }

            total = 0

            var msg = mHandler.obtainMessage(MSG_STATUS)
            val b = Bundle()
            b.putString("message", getString(R.string.msg_initializingdata))
            msg.data = b
            mHandler.sendMessage(msg)

            val resources = resources
            @Suppress("DEPRECATION")
            val settings = PreferenceManager.getDefaultSharedPreferences(this@Splash)

            Log.d("Splash", "Step 1: start location service")
            application.enableLocating(settings.getBoolean(getString(R.string.lc_locate), true))

            val rootPath = getAppBaseDir().absolutePath + File.separator + resources.getString(R.string.def_folder_prefix)

            val editor: Editor = settings.edit()
            editor.putString(getString(R.string.pref_folder_root), rootPath)
            editor.apply()

            Log.d("Splash", "Step 2: root path = $rootPath")
            val root = File(rootPath)
            if (!root.exists()) {
                var created = false
                try {
                    created = root.mkdirs()
                } catch (e: Exception) {
                    Log.e("Splash", "mkdirs exception", e)
                }
                if (!created) {
                    Log.e("Splash", "Failed to create root directory: $rootPath")
                    val parent = root.parentFile
                    if (parent != null) {
                        Log.e("Splash", "Parent exists: ${parent.exists()} canWrite: ${parent.canWrite()}")
                    } else {
                        Log.e("Splash", "Parent is null")
                    }
                    msg = mHandler.obtainMessage(MSG_ERROR)
                    val errorBundle = Bundle()
                    errorBundle.putString("message", "Cannot create root directory")
                    msg.data = errorBundle
                    mHandler.sendMessage(msg)
                    return
                }
                try {
                    File(root, ".nomedia").createNewFile()
                } catch (e: IOException) {
                    Log.w("Splash", "Failed to create .nomedia", e)
                }
            } else {
                Log.d("Splash", "Root directory already exists")
            }

            // check maps folder existence
            val mapPath = getAppBaseDir().absolutePath + File.separator + resources.getString(R.string.def_folder_map)
            val mapdir = File(mapPath)
            editor.putString(getString(R.string.pref_folder_map), mapPath)

            val oldmap = settings.getString(getString(R.string.pref_folder_map_old), null)
            Log.d("Splash", "Step 3: oldmap (ignored) = $oldmap")

            if (!mapdir.exists()) {
                val created = mapdir.mkdirs()
                if (!created) {
                    Log.e("Splash", "Failed to create map directory: ${mapdir.absolutePath}")
                }
            }

            // check data folder existence
            val datadir = File(getAppBaseDir(), resources.getString(R.string.def_folder_data))
            Log.d("Splash", "data directory: ${datadir.absolutePath}")

            if (!datadir.exists()) {
                val created = datadir.mkdirs()
                if (!created) {
                    Log.e("Splash", "Failed to create data directory: ${datadir.absolutePath}")
                }
            }

            try {
                application.copyAssets("zoni", datadir)
                Log.d("Splash", "zoni copied successfully")
            } catch (e: Exception) {
                Log.e("Splash", "Error copying zoni", e)
            }

            // check icons folder existence
            val iconsdir = File(getAppBaseDir(), resources.getString(R.string.def_folder_icon))
            if (!iconsdir.exists()) {
                val created = iconsdir.mkdirs()
                if (created) {
                    try {
                        File(iconsdir, ".nomedia").createNewFile()
                    } catch (e: IOException) {
                        Log.e("Splash", "Failed to create .nomedia file", e)
                    }
                } else {
                    Log.e("Splash", "Failed to create icons directory")
                }
            }
            try {
                application.copyAssets("icons", iconsdir)
            } catch (e: Exception) {
                Log.e("Splash", "Error copying icons", e)
            }

            val sasdir = File(settings.getString(getString(R.string.pref_folder_sas), getAppBaseDir().absolutePath + File.separator + resources.getString(R.string.def_folder_sas)))
            val planeType = settings.getString(getString(R.string.pref_plane_type), "L39")!!
            val planesdir = File(getAppBaseDir(), "planes/$planeType")

            val dirL = File(getAppBaseDir(), "planes/L39")
            val dirPC = File(getAppBaseDir(), "planes/PC9")
            val dirMiG = File(getAppBaseDir(), "planes/MiG29")

            // initialize paths
            application.rootPath = root.absolutePath
            application.setMapPath(mapdir.absolutePath)
            application.setDataPath(Borkozic.PATH_DATA, datadir.absolutePath)
            application.setDataPath(Borkozic.PATH_SAS, sasdir.absolutePath)
            application.setDataPath(Borkozic.PATH_ICONS, iconsdir.absolutePath)
            application.setDataPath(Borkozic.PATH_PLANES, planesdir.absolutePath)

            // planes folders
            if (!dirL.exists()) {
                try {
                    dirL.mkdirs()
                    application.copyAssets("planes/L39", dirL)
                } catch (e: Exception) {
                    Log.e("Splash", "Error_Create folders", e)
                }
            } else {
                try {
                    application.copyAssets("planes/L39", dirL)
                } catch (e: Exception) {
                    Log.e("Splash", "Error_Create folders", e)
                }
            }
            if (!dirPC.exists()) {
                try {
                    dirPC.mkdirs()
                    application.copyAssets("planes/PC9", dirPC)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            if (!dirMiG.exists()) {
                try {
                    dirMiG.mkdirs()
                    application.copyAssets("planes/MiG29", dirMiG)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // initialize data
            application.installData()

            // start tracking service
            application.enableTracking(settings.getBoolean(getString(R.string.lc_track), true))

            // read default waypoints
            val wptFile = File(application.dataPath, "myWaypoints.wpt")
            if (wptFile.exists() && wptFile.canRead()) {
                try {
                    application.addWaypoints(OziExplorerFiles.loadWaypointsFromFile(wptFile, application.charset ?: "").toMutableList())
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // read track tail
            if (settings.getBoolean(getString(R.string.pref_showcurrenttrack), true)) {
                val currentTrackOverlay = CurrentTrackOverlay(this@Splash)
                application.currentTrackOverlay = currentTrackOverlay
                if (settings.getBoolean(getString(R.string.pref_tracking_currentload), resources.getBoolean(R.bool.def_tracking_currentload))) {
                    val length = Integer.parseInt(settings.getString(getString(R.string.pref_tracking_currentlength), getString(R.string.def_tracking_currentlength)) ?: "0")
                    val pathTo = File(application.dataPath, "myTrack.db")
                    try {
                        val trackDB = SQLiteDatabase.openDatabase(pathTo.absolutePath, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS)
                        val cursor: Cursor = trackDB.rawQuery("SELECT * FROM track ORDER BY _id DESC LIMIT $length", null)
                        if (cursor.count > 0) {
                            val track = Track()
                            var hasItem = cursor.moveToLast()
                            while (hasItem) {
                                val latitude = cursor.getDouble(cursor.getColumnIndex("latitude"))
                                val longitude = cursor.getDouble(cursor.getColumnIndex("longitude"))
                                val altitude = cursor.getDouble(cursor.getColumnIndex("elevation"))
                                val speed = cursor.getDouble(cursor.getColumnIndex("speed"))
                                val bearing = cursor.getDouble(cursor.getColumnIndex("track"))
                                val accuracy = cursor.getDouble(cursor.getColumnIndex("accuracy"))
                                val code = cursor.getInt(cursor.getColumnIndex("code"))
                                val time = cursor.getLong(cursor.getColumnIndex("datetime"))
                                val continous = if (cursor.isFirst || cursor.isLast) false else code == 0
                                track.addPoint(continous, latitude, longitude, altitude, speed, bearing, accuracy, time)
                                hasItem = cursor.moveToPrevious()
                            }
                            track.show = true
                            currentTrackOverlay.setTrack(track)
                        }
                        cursor.close()
                        trackDB.close()
                    } catch (e: Exception) {
                        Log.e("Splash", "Read track tail", e)
                    }
                }
            }

            // load routes
            if (settings.getBoolean(getString(R.string.pref_route_preload), resources.getBoolean(R.bool.def_route_preload))) {
                val hide = settings.getBoolean(getString(R.string.pref_route_preload_hidden), resources.getBoolean(R.bool.def_route_preload_hidden))
                val files = FileList.getFileListing(File(application.dataPath), AutoloadedRouteFilenameFilter())
                for (file in files) {
                    var routes: List<Route>? = null
                    try {
                        val lc = file.name.lowercase()
                        if (lc.endsWith(".rt2") || lc.endsWith(".rte")) {
                            routes = OziExplorerFiles.loadRoutesFromFile(file, application.charset ?: "")
                        } else if (lc.endsWith(".kml")) {
                            routes = KmlFiles.loadRoutesFromFile(file)
                        } else if (lc.endsWith(".gpx")) {
                            routes = GpxFiles.loadRoutesFromFile(file)
                        }
                        application.addRoutes(routes ?: emptyList())
                        for (route in routes!!) {
                            val newRoute = RouteOverlay(this@Splash, route)
                            application.routeOverlays.add(newRoute)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            // load areas
            if (settings.getBoolean(getString(R.string.pref_area_preload), resources.getBoolean(R.bool.def_area_preload))) {
                val hide = settings.getBoolean(getString(R.string.pref_area_preload_hidden), resources.getBoolean(R.bool.def_area_preload_hidden))
                val areaFiles = FileList.getFileListing(File(application.dataPath), AreaFilenameFilter())
                for (file in areaFiles) {
                    var areas: List<Area>? = null
                    try {
                        val lc = file.name.lowercase()
                        if (lc.endsWith(".art2")) {
                            areas = OziExplorerFiles.loadAreasFromFile(file, application.charset ?: "")
                        } else if (lc.endsWith(".kml")) {
                            areas = KmlFiles.loadAreasFromFile(file)
                        } else if (lc.endsWith(".gpx")) {
                            areas = GpxFiles.loadAreasFromFile(file)
                        }
                        application.addAreas(areas ?: emptyList())
                        for (area in areas!!) {
                            val newArea = AreaOverlay(this@Splash, area)
                            if (hide) {
                                newArea.area.show = false
                            }
                            application.areaOverlays.add(newArea)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            total += PROGRESS_STEP
            msg = mHandler.obtainMessage(MSG_PROGRESS)
            b.putInt("total", total)
            msg.data = b
            mHandler.sendMessage(msg)

            // put world map if no any found
            val mapfiles = mapdir.list()
            if (mapfiles != null && mapfiles.isEmpty()) {
                application.copyAssets("maps", mapdir)
            }

            msg = mHandler.obtainMessage(MSG_STATUS)
            b.putString("message", getString(R.string.msg_initializingmaps))
            msg.data = b
            mHandler.sendMessage(msg)

            application.initializeMaps()

            total += PROGRESS_STEP
            msg = mHandler.obtainMessage(MSG_PROGRESS)
            b.putInt("total", total)
            msg.data = b
            mHandler.sendMessage(msg)

            msg = mHandler.obtainMessage(MSG_STATUS)
            b.putString("message", getString(R.string.msg_initializingplugins))
            msg.data = b
            mHandler.sendMessage(msg)

            application.initializePlugins()

            total += PROGRESS_STEP
            msg = mHandler.obtainMessage(MSG_PROGRESS)
            b.putInt("total", total)
            msg.data = b
            mHandler.sendMessage(msg)

            msg = mHandler.obtainMessage(MSG_STATUS)
            b.putString("message", getString(R.string.msg_initializingview))
            msg.data = b
            mHandler.sendMessage(msg)

            application.initializeMapCenter()

            total += PROGRESS_STEP
            msg = mHandler.obtainMessage(MSG_PROGRESS)
            b.putInt("total", total)
            msg.data = b
            mHandler.sendMessage(msg)
            mHandler.sendEmptyMessage(MSG_FINISH)
        }
    }

    private fun findUnAskedPermissions(wanted: Collection<String>): ArrayList<String> {
        val result = ArrayList<String>()
        for (perm in wanted) {
            if (!hasPermission(perm)) {
                result.add(perm)
            }
        }
        return result
    }

    private fun hasPermission(permission: String): Boolean {
        if (canAskPermission()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
            }
        }
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        eulaDialog?.dismiss()
        eulaDialog = null
    }

    private fun canAskPermission(): Boolean {
        return Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1
    }

    @TargetApi(Build.VERSION_CODES.M)
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == ALL_PERMISSIONS_RESULT) {
            for (perm in permissionsToRequest!!) {
                if (!hasPermission(perm)) {
                    permissionsRejected.add(perm)
                }
            }

            if (permissionsRejected.isNotEmpty()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (shouldShowRequestPermissionRationale(permissionsRejected[0])) {
                        val msg = "These permissions are mandatory for the application. Please allow access."
                        showMessageOKCancel(msg) { dialog, which ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                requestPermissions(permissionsRejected.toTypedArray(), ALL_PERMISSIONS_RESULT)
                            }
                        }
                        return
                    }
                }
            }
        }
    }

    private fun showMessageOKCancel(message: String, okListener: DialogInterface.OnClickListener) {
        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton("OK", okListener)
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.yes -> result = RES_YES
            R.id.no -> result = RES_NO
            R.id.quit -> finish()
        }
        gotit.visibility = View.GONE
        yes.visibility = View.GONE
        no.visibility = View.GONE
        progress.visibility = View.VISIBLE
        message.text = savedMessage
        wait = false
    }
}
