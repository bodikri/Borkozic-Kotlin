package com.borkozic.provider

import android.net.Uri

object DataContract {
    const val AUTHORITY = "com.borkozic.DataProvider"
    const val ACTION_PICK_ICON = "com.borkozic.PICK_ICON"

    const val MAPOBJECTS_PATH = "mapobjects"
    @JvmField val MAPOBJECTS_URI = Uri.parse("content://" + AUTHORITY + "/" + MAPOBJECTS_PATH)
    
    const val ICONS_PATH = "icons"
    @JvmField val ICONS_URI = Uri.parse("content://" + AUTHORITY + "/" + ICONS_PATH)

    @JvmField val MAPOBJECT_COLUMNS = arrayOf("latitude", "longitude", "bitmap", "name", "description", "image", "marker", "textcolor", "backcolor")
    
    /**
     * Latitude (double, required)
     */
    const val MAPOBJECT_LATITUDE_COLUMN = 0
    
    /**
     * Longitude (double, required)
     */
    const val MAPOBJECT_LONGITUDE_COLUMN = 1
    
    /**
     * Bitmap (ByteArray, required if name is not provided)
     */
    const val MAPOBJECT_BITMAP_COLUMN = 2
    
    /**
     * Name (String, required if bitmap is not provided)
     */
    const val MAPOBJECT_NAME_COLUMN = 3
    
    /**
     * Description (String, optional)
     */
    const val MAPOBJECT_DESCRIPTION_COLUMN = 4
    
    /**
     * Image name, from icons pack (String, optional)
     */
    const val MAPOBJECT_IMAGE_COLUMN = 5
    
    /**
     * Image marker, from markers pack (String, optional)
     */
    const val MAPOBJECT_MARKER_COLUMN = 6
    
    /**
     * Text color (int, optional)
     */
    const val MAPOBJECT_TEXTCOLOR_COLUMN = 7
    
    /**
     * Marker/background color (int, optional)
     */
    const val MAPOBJECT_BACKCOLOR_COLUMN = 8

    const val MAPOBJECT_ID_SELECTION = "IDLIST"

    @JvmField val ICON_COLUMNS = arrayOf("BITMAP")
    const val ICON_COLUMN = 0

    @JvmField val MARKER_COLUMNS = arrayOf("BITMAP")
    const val MARKER_COLUMN = 0
}