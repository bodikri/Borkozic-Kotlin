package com.borkozic.location

interface ITrackingListener {
    fun onNewPoint(continous: Boolean, lat: Double, lon: Double, elev: Double, speed: Double, track: Double, accuracy: Double, time: Long)
}