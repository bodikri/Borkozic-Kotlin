/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2013 Andrey Novikov <http://andreynovikov.info/>
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

package com.borkozic.navigation

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.location.Location
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.preference.PreferenceManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.borkozic.Borkozic
import com.borkozic.BaseApplication
import com.borkozic.HSIActivity
import com.borkozic.MapActivity
import com.borkozic.R
import com.borkozic.area.AreaDetails
import com.borkozic.data.Area
import com.borkozic.data.MapObject
import com.borkozic.data.Route
import com.borkozic.location.ILocationListener
import com.borkozic.location.ILocationService
import com.borkozic.location.LocationService
import com.borkozic.route.RouteDetails
import com.borkozic.util.Geo
import kotlin.math.round

open class NavigationService : BaseNavigationService(), OnSharedPreferenceChangeListener {
    companion object {
        const val NAVIGATE_ROUTE = BaseNavigationService.NAVIGATE_ROUTE
        const val NAVIGATE_MAPOBJECT = BaseNavigationService.NAVIGATE_MAPOBJECT
        const val NAVIGATE_AREA = BaseNavigationService.NAVIGATE_AREA
        const val NAVIGATE_MAPOBJECT_WITH_ID = BaseNavigationService.NAVIGATE_MAPOBJECT_WITH_ID
        const val EXTRA_NAME = BaseNavigationService.EXTRA_NAME
        const val EXTRA_LATITUDE = BaseNavigationService.EXTRA_LATITUDE
        const val EXTRA_LONGITUDE = BaseNavigationService.EXTRA_LONGITUDE
        const val EXTRA_PROXIMITY = BaseNavigationService.EXTRA_PROXIMITY
        const val EXTRA_ROUTE_INDEX = BaseNavigationService.EXTRA_ROUTE_INDEX
        const val EXTRA_ROUTE_DIRECTION = BaseNavigationService.EXTRA_ROUTE_DIRECTION
        const val EXTRA_AREA_INDEX = BaseNavigationService.EXTRA_AREA_INDEX
        const val EXTRA_ROUTE_START = BaseNavigationService.EXTRA_ROUTE_START
        const val EXTRA_AREA_START = BaseNavigationService.EXTRA_AREA_START
        const val EXTRA_ID = BaseNavigationService.EXTRA_ID
        private const val TAG = "Navigation"
        private const val NOTIFICATION_ID = 24163
        private const val NOTIFICATION_CHANNEL_ID = "com.borkozic.navigation"
        private const val ChannelName = "Background Navigation Service"
    }

    private var application: Borkozic? = null
    private var locationService: ILocationService? = null
    protected var lastKnownLocation: Location? = null
    private var notification: Notification? = null
    private var isForeground = false
    private var contentIntent: PendingIntent? = null
    private var routeProximity = 200
    private var useTraverse = true

    /**
     * Active route waypoint
     */
    @JvmField
    var navWaypoint: MapObject? = null

    /**
     * Previous route waypoint
     */
    @JvmField
    var prevWaypoint: MapObject? = null

    /**
     * Active route
     */
    @JvmField
    var navRoute: Route? = null
    @JvmField
    var navDirection = 0

    /**
     * Active route waypoint index
     */
    @JvmField
    var navCurrentRoutePoint = -1
    private var navRouteDistance = -1.0

    /**
     * Active area
     */
    @JvmField
    var navArea: Area? = null

    /**
     * Distance to active waypoint
     */
    @JvmField
    var navProximity = 0
    @JvmField
    var navDistance = 0.0

    /**
     * Current route Slope Angle between 2 route's wpts
     */
    @JvmField
    var SlopeAngle = 0.0 //in radians
    @JvmField
    var navBearing = 0.0
    @JvmField
    var navTurn: Long = 0

    //public double navVMG = 0.0;
    @JvmField
    var navETE = 0
    @JvmField
    var navCourse = 0.0
    @JvmField
    var navXTK = Double.NEGATIVE_INFINITY

    //private long tics = 0;
    //private float[] vmgav = null;
    @JvmField
    var avvmg = 0.0

    override fun onCreate() {
        application = BaseApplication.getApplication<Borkozic>()
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        onSharedPreferenceChanged(sharedPreferences, getString(R.string.pref_navigation_proximity))
        onSharedPreferenceChanged(sharedPreferences, getString(R.string.pref_navigation_traverse))
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)

        // Create notification channel (required for foreground service on Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val chan = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                ChannelName,
                NotificationManager.IMPORTANCE_LOW
            )
            chan.lightColor = Color.BLUE
            chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            manager.createNotificationChannel(chan)
        }

        // Build notification using platform Builder (NOT NotificationCompat — it doesn't set channel properly on API 34+)
        val fallbackActivity = Intent(this, MapActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        )
        contentIntent = PendingIntent.getActivity(
            this,
            NOTIFICATION_ID,
            fallbackActivity,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentIntent(contentIntent)
                .setSmallIcon(R.drawable.ic_stat_navigation)
                .setWhen(0)
                .setContentTitle(getText(R.string.notif_nav_short))
                .setContentText(getText(R.string.notif_nav_started))
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentIntent(contentIntent)
                .setSmallIcon(R.drawable.ic_stat_navigation)
                .setWhen(0)
                .setContentTitle(getText(R.string.notif_nav_short))
                .setContentText(getText(R.string.notif_nav_started))
                .build()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "POST_NOTIFICATIONS permission not granted")
            }
        }
        // Do NOT call ensureForeground() here — service goes foreground only when navigation actually starts
        Log.i(TAG, "Service created")
    }

    /**
     * Builds a fresh notification and calls startForeground.
     * Rebuilds every time to ensure the notification object is valid
     * (Android 14+ is extremely strict about notification validity at call time).
     */
    private fun ensureForeground() {
        if (isForeground) return

        // On Android 14+, startForeground with TYPE_LOCATION requires ACCESS_FINE_LOCATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "ensureForeground: ACCESS_FINE_LOCATION not granted, skipping startForeground")
                return
            }
        }

        // Rebuild notification fresh every time — stale Notification objects cause channel=null on Android 14+
        // Samsung Android 14+ also requires setOngoing(true) for foreground service notifications
        val notif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val builder = Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentIntent(contentIntent)
                .setSmallIcon(R.drawable.ic_stat_navigation)
                .setWhen(0)
                .setOngoing(true)
                .setContentTitle(getText(R.string.notif_nav_short))
                .setContentText(getText(R.string.notif_nav_started))
            // Explicitly set channel ID — Samsung Android 14 sometimes loses it from constructor
            builder.setChannelId(NOTIFICATION_CHANNEL_ID)
            // FLAG_FOREGROUND_SERVICE is mandatory on Android 14+ for startForeground()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                builder.setFlag(Notification.FLAG_FOREGROUND_SERVICE, true)
            }
            builder.build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentIntent(contentIntent)
                .setSmallIcon(R.drawable.ic_stat_navigation)
                .setWhen(0)
                .setOngoing(true)
                .setContentTitle(getText(R.string.notif_nav_short))
                .setContentText(getText(R.string.notif_nav_started))
                .build()
        }
        notification = notif

        Log.d(TAG, "ensureForeground")
        // Samsung Android 14 workaround: post notification via NotificationManager
        // BEFORE calling startForeground(). Some Samsung firmware rejects
        // startForeground() if the notification hasn't been posted first.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, notif)
            startForeground(NOTIFICATION_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notif)
        }
        isForeground = true
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private fun startMyOwnForeground() {
        ensureForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val activity = Intent(this, MapActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            )
            val action = intent.action
            if (action == null) return START_STICKY //return 0;
            val extras = intent.extras
            if (action == NAVIGATE_MAPOBJECT) {
                val mo = MapObject()
                mo.name = extras!!.getString(EXTRA_NAME) ?: ""
                mo.latitude = extras.getDouble(EXTRA_LATITUDE)
                mo.longitude = extras.getDouble(EXTRA_LONGITUDE)
                mo.proximity = extras.getInt(EXTRA_PROXIMITY)
                activity.putExtra("launch", HSIActivity::class.java)
                contentIntent = PendingIntent.getActivity(
                    this,
                    NOTIFICATION_ID,
                    activity,
                    PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                navigateTo(mo)
            }
            if (action == NAVIGATE_MAPOBJECT_WITH_ID) {
                val id = extras!!.getLong(EXTRA_ID)
                val mo = application!!.getMapObject(id)
                activity.putExtra("launch", HSIActivity::class.java)
                contentIntent = PendingIntent.getActivity(
                    this,
                    NOTIFICATION_ID,
                    activity,
                    PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                navigateTo(mo!!)
            }
            if (action == NAVIGATE_ROUTE) {
                val index = extras!!.getInt(EXTRA_ROUTE_INDEX)
                val dir = extras.getInt(EXTRA_ROUTE_DIRECTION, DIRECTION_FORWARD)
                val start = extras.getInt(EXTRA_ROUTE_START, -1)
                activity.putExtra("launch", RouteDetails::class.java)
                activity.putExtra("index", index)
                activity.putExtra("nav", true)
                contentIntent = PendingIntent.getActivity(
                    this,
                    NOTIFICATION_ID,
                    activity,
                    PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                navigateTo(application!!.getRoute(index)!!, dir)
                if (start != -1) setRouteWaypoint(start)
            }
            if (action == NAVIGATE_AREA) {
                val index = extras!!.getInt(EXTRA_AREA_INDEX)
                val dir = extras.getInt(EXTRA_ROUTE_DIRECTION, DIRECTION_FORWARD)
                val start = extras.getInt(EXTRA_AREA_START, -1)
                activity.putExtra("launch", AreaDetails::class.java)
                activity.putExtra("INDEX", index)
                activity.putExtra("nav", true)
                contentIntent = PendingIntent.getActivity(
                    this,
                    NOTIFICATION_ID,
                    activity,
                    PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                // Създаваме временен Route от Area waypoints за навигация
                val area = application!!.getArea(index)!!
                val tempRoute = Route(area.name, "", true)
                tempRoute.waypoints.addAll(area.waypoints)
                tempRoute.distance = area.distance
                navigateTo(tempRoute, dir)
                if (start != -1) setRouteWaypoint(start)
            }
            /*
            if (action.equals(NAVIGATE_AREA))
            {//todo - не съм сигурен как действа този код и дали не трябва да добавя допълнителни константи които да отчитат добавеният от мен елемент
                int index = extras.getInt(EXTRA_AREA_INDEX);
                MapObject mo = new MapObject();
                mo.name = extras.getString(EXTRA_NAME);// EXTRA_NAME_AREA
                mo.latitude = extras.getDouble(EXTRA_LATITUDE);// EXTRA_LATITUDE_AREA
                mo.longitude = extras.getDouble(EXTRA_LONGITUDE);// EXTRA_LONGITUDE_AREA
                mo.proximity = extras.getInt(EXTRA_PROXIMITY);// EXTRA_PROXIMITY_AREA
                activity.putExtra("launch", HSIActivity.class);
                contentIntent = PendingIntent.getActivity(this, NOTIFICATION_ID, activity, PendingIntent.FLAG_CANCEL_CURRENT);
                navigateTo(mo);
            }*/
        }
        return START_STICKY
    }

    override fun onDestroy() {
        disconnect()
        clearNavigation()
        PreferenceManager.getDefaultSharedPreferences(this)
            .unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
        Log.i(TAG, "Service stopped")
    }

    private val binder: IBinder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): NavigationService {
            return this@NavigationService
        }
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (getString(R.string.pref_navigation_proximity) == key) {
            routeProximity = sharedPreferences.getString(key, getString(R.string.def_navigation_proximity))!!.toInt()
        }
        if (getString(R.string.pref_navigation_traverse) == key) {
            useTraverse = sharedPreferences.getBoolean(
                key,
                resources.getBoolean(R.bool.def_navigation_traverse)
            )
        }
    }

    private fun connect() {
        bindService(
            Intent(this, LocationService::class.java),
            locationConnection,
            BIND_AUTO_CREATE
        )
    }

    private fun disconnect() {
        if (locationService != null) {
            locationService!!.unregisterLocationCallback(locationListener)
            unbindService(locationConnection)
            locationService = null
        }
    }

    fun stopNavigation() {
        clearNavigation()
        updateNavigationState(STATE_STOPED)
        stopForeground(true)
        isForeground = false
        disconnect()
    }

    private fun clearNavigation() {
        navWaypoint = null
        prevWaypoint = null
        navRoute = null
        navArea = null
        navDirection = 0
        navCurrentRoutePoint = -1
        navProximity = routeProximity
        navDistance = 0.0
        navBearing = 0.0
        navTurn = 0
        //navVMG = 0.0;
        navETE = 0
        navCourse = 0.0
        navXTK = Double.NEGATIVE_INFINITY

        //vmgav = null;
        avvmg = 0.0
    }

    fun isNavigating(): Boolean {
        return navWaypoint != null
    }

    fun isNavigatingViaRoute(): Boolean {
        return navRoute != null
    }

    fun isNavigatingViaArea(): Boolean {
        return navArea != null
    }

    fun navigateTo(waypoint: MapObject) {
        clearNavigation()
        connect()
        // ensureForeground only on first start; service stays foreground between navigateTo() calls
        if (!isForeground) ensureForeground()

        //vmgav = new float[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

        navWaypoint = waypoint
        navProximity = if (navWaypoint!!.proximity > 0) navWaypoint!!.proximity else routeProximity
        updateNavigationState(STATE_STARTED)
        if (lastKnownLocation != null) calculateNavigationStatus(
            lastKnownLocation!!,
            0f,
            0f
        )
    }

    fun navigateTo(route: Route, direction: Int) {
        clearNavigation()
        connect()
        // ensureForeground only on first start; service stays foreground between navigateTo() calls
        if (!isForeground) ensureForeground()

        //vmgav = new float[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

        navRoute = route
        navDirection = direction
        navCurrentRoutePoint =
            if (navDirection == 1) 0 else navRoute!!.length() - 1 // започва от първата точка (0) или последната (length-1) при reverse

        navWaypoint = navRoute!!.getWaypoint(navCurrentRoutePoint)
        val prev = navCurrentRoutePoint - navDirection
        prevWaypoint = if (prev >= 0 && prev < navRoute!!.length()) {
            navRoute!!.getWaypoint(prev)
        } else {
            null
        }
        SlopeAngle = if (prevWaypoint != null) Geo.SlopeAngle(
            prevWaypoint!!.latitude,
            prevWaypoint!!.longitude,
            navWaypoint!!.latitude,
            navWaypoint!!.longitude,
            prevWaypoint!!.altitude,
            navWaypoint!!.altitude
        ) else 0.0
        //double saDegre = Math.toDegrees(SlopeAngle);
        //Log.i(TAG + "TO", "SlopeAngle: " + saDegre);
        navProximity = if (navWaypoint!!.proximity > 0) navWaypoint!!.proximity else routeProximity
        navRouteDistance = -1.0
        navCourse = if (prevWaypoint == null) 0.0 else Geo.bearing(
            prevWaypoint!!.latitude,
            prevWaypoint!!.longitude,
            navWaypoint!!.latitude,
            navWaypoint!!.longitude
        )
        updateNavigationState(STATE_STARTED)
        if (lastKnownLocation != null) calculateNavigationStatus(
            lastKnownLocation!!,
            0f,
            0f
        )
    }

    fun setRouteWaypoint(waypoint: Int) {
        navCurrentRoutePoint = waypoint
        navWaypoint = navRoute!!.getWaypoint(navCurrentRoutePoint)
        val prev = navCurrentRoutePoint - navDirection
        prevWaypoint = if (prev >= 0 && prev < navRoute!!.length()) {
            navRoute!!.getWaypoint(prev)
        } else {
            null
        }
        navProximity = if (navWaypoint!!.proximity > 0) navWaypoint!!.proximity else routeProximity
        navRouteDistance = -1.0
        navCourse = if (prevWaypoint == null) 0.0 else Geo.bearing(
            prevWaypoint!!.latitude,
            prevWaypoint!!.longitude,
            navWaypoint!!.latitude,
            navWaypoint!!.longitude
        )
        updateNavigationState(STATE_NEXTWPT)
    }

    fun getNextRouteWaypoint(): MapObject? {
        return try {
            navRoute!!.getWaypoint(navCurrentRoutePoint + navDirection)
        } catch (e: IndexOutOfBoundsException) {
            null
        }
    }

    @Throws(IndexOutOfBoundsException::class)
    fun nextRouteWaypoint() {
        navCurrentRoutePoint += navDirection
        navWaypoint = navRoute!!.getWaypoint(navCurrentRoutePoint)
        prevWaypoint = navRoute!!.getWaypoint(navCurrentRoutePoint - navDirection)
        SlopeAngle = Geo.SlopeAngle(
            prevWaypoint!!.latitude,
            prevWaypoint!!.longitude,
            navWaypoint!!.latitude,
            navWaypoint!!.longitude,
            prevWaypoint!!.altitude,
            navWaypoint!!.altitude
        )
        //double saDegre = Math.toDegrees(SlopeAngle);
        //Log.i(TAG + "TOwpt", "SlopeAngle: " + saDegre);
        navProximity = if (navWaypoint!!.proximity > 0) navWaypoint!!.proximity else routeProximity
        navRouteDistance = -1.0
        navCourse = Geo.bearing(
            prevWaypoint!!.latitude,
            prevWaypoint!!.longitude,
            navWaypoint!!.latitude,
            navWaypoint!!.longitude
        )
        updateNavigationState(STATE_NEXTWPT)
    }

    @Throws(IndexOutOfBoundsException::class)
    fun prevRouteWaypoint() {
        navCurrentRoutePoint -= navDirection
        navWaypoint = navRoute!!.getWaypoint(navCurrentRoutePoint)
        val prev = navCurrentRoutePoint - navDirection
        prevWaypoint = if (prev >= 0 && prev < navRoute!!.length()) {
            val prevWaypointTemp = navRoute!!.getWaypoint(prev)
            SlopeAngle = Geo.SlopeAngle(
                prevWaypointTemp.latitude,
                prevWaypointTemp.longitude,
                navWaypoint!!.latitude,
                navWaypoint!!.longitude,
                prevWaypointTemp.altitude,
                navWaypoint!!.altitude
            )
            //Log.i(TAG + "TOwpt", "SlopeAngle: " + saDegre);
            // ToDO Да направя да се покава колко градуса е  ъгъла и дали е в изкачване или в снижение
            prevWaypointTemp
        } else {
            null
        }
        navProximity = if (navWaypoint!!.proximity > 0) navWaypoint!!.proximity else routeProximity
        navRouteDistance = -1.0
        navCourse = if (prevWaypoint == null) 0.0 else Geo.bearing(
            prevWaypoint!!.latitude,
            prevWaypoint!!.longitude,
            navWaypoint!!.latitude,
            navWaypoint!!.longitude
        )
        updateNavigationState(STATE_NEXTWPT)
    }

    fun hasNextRouteWaypoint(): Boolean {
        if (navRoute == null) return false
        var hasNext = false
        if (navDirection == DIRECTION_FORWARD) hasNext =
            navCurrentRoutePoint + navDirection < navRoute!!.length()
        if (navDirection == DIRECTION_REVERSE) hasNext =
            navCurrentRoutePoint + navDirection >= 0
        return hasNext
    }

    fun hasPrevRouteWaypoint(): Boolean {
        if (navRoute == null) return false
        var hasPrev = false
        if (navDirection == DIRECTION_FORWARD) hasPrev = navCurrentRoutePoint - navDirection >= 0
        if (navDirection == DIRECTION_REVERSE) hasPrev =
            navCurrentRoutePoint - navDirection < navRoute!!.length()
        return hasPrev
    }

    fun navRouteCurrentIndex(): Int {
        return if (navDirection == DIRECTION_FORWARD) navCurrentRoutePoint else navRoute!!.length() - navCurrentRoutePoint - 1
    }

    /**
     * Calculates distance between current route waypoint and last route waypoint.
     * @return distance left
     */
    fun navRouteDistanceLeft(): Double {
        if (navRouteDistance < 0) {
            navRouteDistance = navRouteDistanceLeftTo(navRoute!!.length() - 1)
        }
        return navRouteDistance
    }

    /**
     * Calculates distance between current route waypoint and route waypoint with specified index.
     * Method honors navigation direction.
     * @param index
     * @return distance left
     */
    fun navRouteDistanceLeftTo(index: Int): Double {
        val current = navRouteCurrentIndex()
        val progress = index - current
        if (progress <= 0) return 0.0
        var distance = 0.0
        if (navDirection == DIRECTION_FORWARD) distance =
            navRoute!!.distanceBetween(navCurrentRoutePoint, index)
        if (navDirection == DIRECTION_REVERSE) distance =
            navRoute!!.distanceBetween(navRoute!!.length() - index - 1, navCurrentRoutePoint)
        return distance
    }

    fun navRouteWaypointETE(index: Int): Int {
        if (index == 0) return 0
        var ete = Integer.MAX_VALUE
        if (avvmg > 0) {
            val i = if (navDirection == DIRECTION_FORWARD) index else navRoute!!.length() - index - 1
            val j = i - navDirection
            val w1 = navRoute!!.getWaypoint(i)
            val w2 = navRoute!!.getWaypoint(j)
            val distance = Geo.distance(
                w1.latitude,
                w1.longitude,
                w2.latitude,
                w2.longitude,
                w1.altitude,
                w2.altitude
            )
            //ete = (int) Math.round(distance / avvmg / 60); //променено от мен за да се използва до секундата
            ete = round(distance / avvmg).toInt() // в секунди
        }
        return ete
    }

    /**
     * Calculates route ETE.
     * @param distance route distance
     * @return route ETE
     */
    fun navRouteETE(distance: Double): Int {
        var eta = Integer.MAX_VALUE
        if (avvmg > 0) {
            //eta = (int) Math.round(distance / avvmg / 60);
            eta = round(distance / avvmg).toInt() // in seconds
        }
        return eta
    }

    fun navRouteETETo(index: Int): Int {
        val distance = navRouteDistanceLeftTo(index)
        return if (distance <= 0.0) 0 else navRouteETE(distance)
    }

    private fun calculateNavigationStatus(loc: Location, smoothspeed: Float, avgspeed: Float) {
        //android.util.Log.d(TAG,"GPS H= " + loc.getAltitude() + "::" + "WPT H= " + navWaypoint.altitude);
        val distance = Geo.distance(
            loc.latitude,
            loc.longitude,
            navWaypoint!!.latitude,
            navWaypoint!!.longitude,
            loc.altitude,
            navWaypoint!!.altitude
        )
        //return distance in meters
        val bearing = Geo.bearing(
            loc.latitude,
            loc.longitude,
            navWaypoint!!.latitude,
            navWaypoint!!.longitude
        )
        val track = loc.bearing

        // turn
        var turn = Math.round(bearing - track)
        if (kotlin.math.abs(turn) > 180) {
            turn = turn - (kotlin.math.sign(turn.toDouble())).toLong() * 360
        }

        // vmg
        //double vmg = Geo.vmg(smoothspeed, Math.abs(turn)); - за полетна навигация е ненужно

        // ete - estimate time on route/ eta - estimate time of arrival
        /*  долното пресмятане считам за ненужно тъй като се отнася само за яхтинг
             float curavvmg = (float) Geo.vmg(avgspeed, Math.abs(turn));
             if (avvmg == 0.0 || tics % 10 == 0)
             {
                 for (int i = vmgav.length - 1; i > 0; i--)
                 {    avvmg += vmgav[i];
                     vmgav[i] = vmgav[i - 1];
                 }
                 avvmg += curavvmg;
                 vmgav[0] = curavvmg;
                 avvmg = avvmg / vmgav.length;
             }
                 int ete = Integer.MAX_VALUE;
                 if (avvmg > 0)
                 ete = (int) Math.round(distance / avvmg / 60);
         */
        // ete
        avvmg = avgspeed.toDouble() // тъй като навсякъде използва тази променлива
        var ete = Integer.MAX_VALUE
        if (avgspeed > 0) ete = round(distance / avvmg).toInt() // in seconds

        var xtk = Double.NEGATIVE_INFINITY
        if (navRoute != null) {
            val hasNext = hasNextRouteWaypoint()
            if (distance < navProximity) {
                if (hasNext) {
                    nextRouteWaypoint()
                    return
                } else {
                    updateNavigationState(STATE_REACHED)
                    stopNavigation()
                    return
                }
            }
            if (prevWaypoint != null) {
                val dtk = Geo.bearing(
                    prevWaypoint!!.latitude,
                    prevWaypoint!!.longitude,
                    navWaypoint!!.latitude,
                    navWaypoint!!.longitude
                )
                xtk = Geo.xtk(distance, dtk, bearing)
                if (xtk == Double.NEGATIVE_INFINITY) {
                    if (useTraverse && hasNext) {
                        var cxtk2 = Double.NEGATIVE_INFINITY
                        val nextWpt = getNextRouteWaypoint()
                        if (nextWpt != null) {
                            val dtk2 = Geo.bearing(
                                nextWpt.latitude,
                                nextWpt.longitude,
                                navWaypoint!!.latitude,
                                navWaypoint!!.longitude
                            )
                            cxtk2 = Geo.xtk(0.0, dtk2, bearing)
                        }
                        if (cxtk2 != Double.NEGATIVE_INFINITY) {
                            nextRouteWaypoint()
                            return
                        }
                    }
                }
            }
        }

        //tics++;

        if (distance != navDistance || bearing != navBearing || turn != navTurn || ete != navETE || xtk != navXTK) {
            navDistance = distance
            navBearing = bearing
            navTurn = turn
            //navVMG = vmg;
            navETE = ete
            navXTK = xtk
            updateNavigationStatus()
        }
    }

    private fun updateNavigationState(state: Int) {
        if (state != STATE_STOPED && state != STATE_REACHED) {
            notification!!.`when` = System.currentTimeMillis()
            val builder = NotificationCompat.Builder(this)
            builder.setContentIntent(contentIntent)
            builder.setSmallIcon(R.drawable.ic_stat_navigation)
            builder.setWhen(System.currentTimeMillis())
            builder.setContentTitle(getText(R.string.notif_nav_short))
            builder.setContentText(
                String.format(
                    getText(R.string.notif_nav_to) as String,
                    navWaypoint!!.name
                )
            )
            notification = builder.build()
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, notification!!)
        }
        sendBroadcast(Intent(BROADCAST_NAVIGATION_STATE).putExtra("state", state))
        //Log.d(TAG, "State dispatched");
    }

    private fun updateNavigationStatus() {
        sendBroadcast(Intent(BROADCAST_NAVIGATION_STATUS))
        //Log.d(TAG, "Status dispatched");
    }

    private val locationConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            locationService = service as ILocationService
            locationService!!.registerLocationCallback(locationListener)
            Log.i(TAG, "Location service connected")
        }

        override fun onServiceDisconnected(className: ComponentName) {
            locationService = null
            Log.i(TAG, "Location service disconnected")
        }
    }

    private val locationListener: ILocationListener = object : ILocationListener {
        override fun onGpsStatusChanged(provider: String, status: Int, fsats: Int, tsats: Int) {}
        override fun onLocationChanged(
            loc: Location,
            continous: Boolean,
            geoid: Boolean,
            smoothspeed: Float,
            avgspeed: Float
        ) {
            //Log.d(TAG, "Location arrived");
            lastKnownLocation = loc
            if (navWaypoint != null) calculateNavigationStatus(loc, smoothspeed, avgspeed)
        }

        override fun onProviderChanged(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        override fun onProviderEnabled(provider: String) {}
    }
}