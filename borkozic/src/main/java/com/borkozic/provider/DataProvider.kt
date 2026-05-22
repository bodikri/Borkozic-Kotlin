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

package com.borkozic.provider

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.borkozic.BaseApplication
import com.borkozic.Borkozic
import com.borkozic.data.MapObject
import java.io.ByteArrayOutputStream
import java.io.File

open class DataProvider : ContentProvider() {
    companion object {
        private const val TAG = "DataProvider"
        private const val MAPOBJECTS = 1
        private const val MAPOBJECTS_ID = 2
        private const val ICONS_ID = 3
        private val uriMatcher: UriMatcher

        init {
            uriMatcher = UriMatcher(UriMatcher.NO_MATCH)
            uriMatcher.addURI(DataContract.AUTHORITY, DataContract.MAPOBJECTS_PATH, MAPOBJECTS)
            uriMatcher.addURI(
                DataContract.AUTHORITY,
                DataContract.MAPOBJECTS_PATH + "/#",
                MAPOBJECTS_ID
            )
            uriMatcher.addURI(DataContract.AUTHORITY, DataContract.ICONS_PATH + "/*", ICONS_ID)
        }
    }

    override fun onCreate(): Boolean {
        return true
    }

    override fun getType(uri: Uri): String {
        return when (uriMatcher.match(uri)) {
            MAPOBJECTS -> "vnd.android.cursor.dir/vnd.com.borkozic.provider.mapobject"
            MAPOBJECTS_ID -> "vnd.android.cursor.item/vnd.com.borkozic.provider.mapobject"
            ICONS_ID -> "vnd.android.cursor.item/vnd.com.borkozic.provider.icon"
            else -> throw IllegalArgumentException("Unknown URI $uri")
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor {
        Log.e(TAG, uri.toString())
        if (uriMatcher.match(uri) != ICONS_ID) {
            throw UnsupportedOperationException("Quering objects is not supported")
        }

        val id = uri.lastPathSegment
        val cursor = MatrixCursor(projection)

        val application = BaseApplication.getApplication<Borkozic>()
        val bitmap = BitmapFactory.decodeFile(application!!.iconPath + File.separator + id)
        if (bitmap != null) {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            val bytes = stream.toByteArray()
            val row = cursor.newRow()
            row.add(bytes)
        }

        return cursor
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        if (uriMatcher.match(uri) != MAPOBJECTS) {
            throw IllegalArgumentException("Unknown URI $uri")
        }

        if (values == null) {
            throw IllegalArgumentException("Values can not be null")
        }

        val mo = MapObject()
        populateFields(mo, values)

        val application = BaseApplication.getApplication<Borkozic>()
        if (application == null) return null

        val id = application.addMapObject(mo)
        val objectUri = ContentUris.withAppendedId(DataContract.MAPOBJECTS_URI, id)
        context!!.contentResolver.notifyChange(objectUri, null)
        return objectUri
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int {
        if (uriMatcher.match(uri) != MAPOBJECTS_ID) {
            if (uriMatcher.match(uri) == MAPOBJECTS) throw UnsupportedOperationException("Currently only updating one object by ID is supported") else throw IllegalArgumentException(
                "Unknown URI $uri"
            )
        }

        if (values == null) {
            throw IllegalArgumentException("Values can not be null")
        }
        val id = ContentUris.parseId(uri)
        val application = BaseApplication.getApplication<Borkozic>()
        if (application == null) return 0

        val mo = application.getMapObject(id)
        if (mo == null) return 0

        synchronized(mo) {
            populateFields(mo, values)
        }

        context!!.contentResolver.notifyChange(uri, null)
        return 1
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        var ids: LongArray? = null
        if (uriMatcher.match(uri) == MAPOBJECTS) {
            if (DataContract.MAPOBJECT_ID_SELECTION != selection) throw IllegalArgumentException("Deleting is supported only by ID")
            ids = LongArray(selectionArgs!!.size)
            for (i in ids.indices) ids[i] = selectionArgs[i].toLong(10)
        }
        if (uriMatcher.match(uri) == MAPOBJECTS_ID) {
            ids = longArrayOf(ContentUris.parseId(uri))
        }
        if (ids == null) throw IllegalArgumentException("Unknown URI: $uri")

        val application = BaseApplication.getApplication<Borkozic>()
        if (application == null) return 0

        var result = 0
        for (id in ids) {
            if (application.removeMapObject(id)) result++
        }
        return result
    }

    private fun populateFields(mo: MapObject, values: ContentValues) {
        var key = DataContract.MAPOBJECT_COLUMNS[DataContract.MAPOBJECT_NAME_COLUMN]
        if (values.containsKey(key)) mo.name = values.getAsString(key)

        key = DataContract.MAPOBJECT_COLUMNS[DataContract.MAPOBJECT_DESCRIPTION_COLUMN]
        if (values.containsKey(key)) mo.description = values.getAsString(key)

        key = DataContract.MAPOBJECT_COLUMNS[DataContract.MAPOBJECT_LATITUDE_COLUMN]
        if (values.containsKey(key)) mo.latitude = values.getAsDouble(key)!!

        key = DataContract.MAPOBJECT_COLUMNS[DataContract.MAPOBJECT_LONGITUDE_COLUMN]
        if (values.containsKey(key)) mo.longitude = values.getAsDouble(key)!!

        key = DataContract.MAPOBJECT_COLUMNS[DataContract.MAPOBJECT_IMAGE_COLUMN]
        if (values.containsKey(key)) mo.image = values.getAsString(key)

        key = DataContract.MAPOBJECT_COLUMNS[DataContract.MAPOBJECT_TEXTCOLOR_COLUMN]
        if (values.containsKey(key)) mo.textcolor = values.getAsInteger(key)!!

        key = DataContract.MAPOBJECT_COLUMNS[DataContract.MAPOBJECT_BACKCOLOR_COLUMN]
        if (values.containsKey(key)) mo.backcolor = values.getAsInteger(key)!!

        key = DataContract.MAPOBJECT_COLUMNS[DataContract.MAPOBJECT_BITMAP_COLUMN]
        if (values.containsKey(key)) {
            val bytes = values.getAsByteArray(key)
            if (mo.bitmap != null) {
                mo.bitmap!!.recycle()
                mo.bitmap = null
            }
            if (bytes != null) mo.bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    }
}