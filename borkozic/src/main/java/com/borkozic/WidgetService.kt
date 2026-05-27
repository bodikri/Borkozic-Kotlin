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

package com.borkozic

import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.graphics.Color
import android.os.IBinder
import android.os.RemoteException
import androidx.preference.PreferenceManager
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.borkozic.location.ITrackingCallback
import com.borkozic.location.ITrackingRemoteService
import com.borkozic.util.StringFormatter

class WidgetService : Service() {

    companion object {
        const val WIDGET_UPDATE = "update"
        const val WIDGET_REFRESH = "refresh"
        const val TRACKING_START = "start"
        const val TRACKING_STOP = "stop"
    }

    private var remoteService: ITrackingRemoteService? = null
    private var isBound = false
    var isConnected = false
        protected set

    protected var latitude = 0.0
    protected var longitude = 0.0

    override fun onDestroy() {
        Log.d("ANDROZIC", "WidgetService: onDestroy")
        if (isBound) {
            if (remoteService != null) {
                try {
                    remoteService!!.unregisterCallback(callback)
                } catch (e: RemoteException) {
                }
                unbindService(connection)
            }
            isConnected = false
            isBound = false
        }
        super.onDestroy()
    }

    override fun onStart(intent: Intent?, startId: Int) {
        if (intent != null) {
            when (intent.action) {
                WIDGET_UPDATE -> {
                    Log.d("ANDROZIC", "WidgetService: widget update")
                    if (!isBound) {
                        isBound = bindService(
                            Intent(ITrackingRemoteService::class.java.name),
                            connection,
                            0
                        )
                        Log.d("ANDROZIC", "WidgetService: bind to service: $isBound")
                    }
                    val appWidgetId = intent.extras!!.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID)

                    val remoteView = RemoteViews(applicationContext.packageName, R.layout.location_widget)
                    val appWidgetManager = AppWidgetManager.getInstance(applicationContext)

                    val settings = PreferenceManager.getDefaultSharedPreferences(this)
                    val format = (settings.getString(getString(R.string.pref_unitcoordinate), "0") ?: "0").toInt()

                    remoteView.setTextColor(R.id.latitude, Color.WHITE)
                    remoteView.setTextColor(R.id.longitude, Color.WHITE)

                    val startIntent = Intent(baseContext, WidgetService::class.java).apply {
                        action = TRACKING_START
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    }
                    val startPendingIntent = PendingIntent.getService(
                        baseContext, 0, startIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    val stopIntent = Intent(baseContext, WidgetService::class.java).apply {
                        action = TRACKING_STOP
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    }
                    val stopPendingIntent = PendingIntent.getService(
                        baseContext, 0, stopIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    remoteView.setOnClickPendingIntent(R.id.start, startPendingIntent)
                    remoteView.setOnClickPendingIntent(R.id.stop, stopPendingIntent)

                    if (isConnected) {
                        //FIXME Needs UTM support here
                        remoteView.setTextViewText(R.id.latitude, StringFormatter.coordinate(format, latitude))
                        remoteView.setTextViewText(R.id.longitude, StringFormatter.coordinate(format, longitude))
                        remoteView.setViewVisibility(R.id.start, View.GONE)
                        remoteView.setViewVisibility(R.id.stop, View.VISIBLE)
                    } else {
                        remoteView.setTextViewText(R.id.latitude, "Tracking")
                        remoteView.setTextViewText(R.id.longitude, "disabled")
                        remoteView.setViewVisibility(R.id.start, View.VISIBLE)
                        remoteView.setViewVisibility(R.id.stop, View.GONE)
                    }

                    appWidgetManager.updateAppWidget(appWidgetId, remoteView)
                }

                TRACKING_START -> {
                    Log.d("ANDROZIC", "WidgetService: start tracking")
                    isBound = bindService(
                        Intent(ITrackingRemoteService::class.java.name),
                        connection,
                        BIND_AUTO_CREATE
                    )
                    Log.d("ANDROZIC", "WidgetService: bind to service: $isBound")
                }

                TRACKING_STOP -> {
                    Log.d("ANDROZIC", "WidgetService: stop tracking")
                    if (isBound) {
                        unbindService(connection)
                        Log.d("ANDROZIC", "WidgetService: unbind from service")
                    }
                    remoteService = null
                    isConnected = false
                    isBound = false
                }
            }
        }

        super.onStart(intent, startId)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private lateinit var connection: ServiceConnection
    private lateinit var callback: ITrackingCallback.Stub

    init {
        connection = object : ServiceConnection {
            override fun onServiceConnected(className: ComponentName, service: IBinder) {
                remoteService = ITrackingRemoteService.Stub.asInterface(service)

                try {
                    remoteService!!.registerCallback(callback)
                    isConnected = true
                    Log.d("ANDROZIC", "WidgetService: service connected")
                } catch (e: RemoteException) {
                }
            }

            override fun onServiceDisconnected(className: ComponentName) {
                isConnected = false
                remoteService = null
                if (isBound) {
                    unbindService(connection)
                    Log.d("ANDROZIC", "WidgetService: unbind from service")
                }
                isBound = false
                Log.d("ANDROZIC", "WidgetService: service disconnected")
            }
        }

        callback = object : ITrackingCallback.Stub() {
        @Throws(RemoteException::class)
        override fun onNewPoint(
            continous: Boolean,
            lat: Double,
            lon: Double,
            elev: Double,
            speed: Double,
            track: Double,
            accuracy: Double,
            time: Long
        ) {
            Log.d("ANDROZIC", "WidgetService: track point arrived")
            latitude = lat
            longitude = lon
        }
    }
    }
}
