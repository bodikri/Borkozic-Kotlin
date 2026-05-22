package com.borkozic.overlay

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.os.IBinder
import android.preference.PreferenceManager
import com.borkozic.R
import com.borkozic.data.Track
import com.borkozic.location.ILocationService
import com.borkozic.location.ITrackingListener
import com.borkozic.location.LocationService

class CurrentTrackOverlay(mapActivity: Activity) : TrackOverlay(mapActivity) {

    private var trackingService: ILocationService? = null
    private var isBound = false

    init {
        onPreferencesChanged(PreferenceManager.getDefaultSharedPreferences(context))

        track.name = "Current Track"
        track.show = true
    }

    override fun setMapContext(activity: Activity) {
        unbind()
        super.setMapContext(activity)
        isBound = context.applicationContext.bindService(
            Intent(context.applicationContext, LocationService::class.java),
            trackingConnection, Context.BIND_AUTO_CREATE
        )
    }

    override fun setTrack(track: Track) {
        clear()
        this.track = track
    }

    fun clear() {
        track.clear()
    }

    override fun onBeforeDestroy() {
        super.onBeforeDestroy()
        unbind()
    }

    private fun unbind() {
        if (isBound) {
            trackingService?.unregisterTrackingCallback(trackingListener)
            context.applicationContext.unbindService(trackingConnection)
            isBound = false
        }
    }

    override fun onPreferencesChanged(settings: SharedPreferences) {
        super.onPreferencesChanged(settings)
        track.maxPoints = settings.getString(
            context.getString(R.string.pref_tracking_currentlength),
            context.getString(R.string.def_tracking_currentlength)
        )!!.toLong()
    }

    private val trackingConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            trackingService = service as ILocationService
            trackingService?.registerTrackingCallback(trackingListener)
        }

        override fun onServiceDisconnected(className: ComponentName) {
            trackingService = null
        }
    }

    private val trackingListener: ITrackingListener = object : ITrackingListener {
        override fun onNewPoint(
            continous: Boolean, lat: Double, lon: Double, elev: Double,
            speed: Double, trk: Double, accuracy: Double, time: Long
        ) {
            track.addPoint(continous, lat, lon, elev, speed, trk, accuracy, time)
        }
    }
}
