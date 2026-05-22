package com.borkozic.provider

import android.net.Uri

object PreferencesContract {
    const val AUTHORITY = "com.borkozic.PreferencesProvider"
    const val PATH = "preferences"
    val PREFERENCES_URI: Uri = Uri.parse("content://$AUTHORITY/$PATH")

    val DATA_COLUMNS = arrayOf("VALUE")
    const val DATA_COLUMN = 0
    const val DATA_SELECTION = "IDLIST"

    /**
     * double
     */
    const val SPEED_FACTOR = 1
    /**
     * String
     */
    const val SPEED_ABBREVIATION = 2
    /**
     * double
     */
    const val DISTANCE_FACTOR = 3
    /**
     * String
     */
    const val DISTANCE_ABBREVIATION = 4
    /**
     * double
     */
    const val DISTANCE_SHORT_FACTOR = 5
    /**
     * String
     */
    const val DISTANCE_SHORT_ABBREVIATION = 6
    /**
     * double
     */
    const val ELEVATION_FACTOR = 7
    /**
     * String
     */
    const val ELEVATION_ABBREVIATION = 8
    /**
     * int
     */
    const val COORDINATES_FORMAT = 9
}