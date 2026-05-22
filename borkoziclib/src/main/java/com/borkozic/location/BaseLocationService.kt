package com.borkozic.location

import android.app.Service

abstract class BaseLocationService : Service() {
    companion object {
        const val BORKOZIC_LOCATION_SERVICE = "com.borkozic.location"
        /**
         * Broadcast sent when service status changes
         */
        const val BROADCAST_LOCATING_STATUS = "com.borkozic.locatingStatusChanged"
        /**
         * GPS status code
         */
        const val GPS_OFF = 1
        /**
         * GPS status code
         */
        const val GPS_SEARCHING = 2
        /**
         * GPS status code
         */
        const val GPS_OK = 3
    }
}