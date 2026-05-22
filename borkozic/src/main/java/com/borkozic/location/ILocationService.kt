package com.borkozic.location

import com.borkozic.data.Track

interface ILocationService {
    fun registerLocationCallback(callback: ILocationListener)
    fun unregisterLocationCallback(callback: ILocationListener)
    fun registerTrackingCallback(callback: ITrackingListener)
    fun unregisterTrackingCallback(callback: ITrackingListener)
    fun isLocating(): Boolean
    fun isTracking(): Boolean
    fun getHDOP(): Float
    fun getVDOP(): Float
    fun getTrack(): Track
    fun getTrack(start: Long, end: Long): Track
    fun clearTrack()
    fun getTrackStartTime(): Long
    fun getTrackEndTime(): Long
}