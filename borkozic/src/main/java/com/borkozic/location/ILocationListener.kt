package com.borkozic.location

import android.location.Location

interface ILocationListener {
    fun onLocationChanged(loc: Location, continous: Boolean, geoid: Boolean, smoothspeed: Float, avgspeed: Float)
    fun onProviderChanged(provider: String)
    fun onProviderDisabled(provider: String)
    fun onProviderEnabled(provider: String)
    fun onGpsStatusChanged(provider: String, status: Int, fsats: Int, tsats: Int)
}