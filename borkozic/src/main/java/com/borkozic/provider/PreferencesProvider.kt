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

package com.borkozic.provider

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.content.UriMatcher
import android.content.res.Resources
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.preference.PreferenceManager
import android.util.Log
import com.borkozic.R

open class PreferencesProvider : ContentProvider() {
    companion object {
        private const val TAG = "PreferenceProvider"
        private const val OBJECTS = 1
        private const val OBJECTS_ID = 2
        private val uriMatcher: UriMatcher

        init {
            uriMatcher = UriMatcher(UriMatcher.NO_MATCH)
            uriMatcher.addURI(PreferencesContract.AUTHORITY, PreferencesContract.PATH, OBJECTS)
            uriMatcher.addURI(
                PreferencesContract.AUTHORITY,
                PreferencesContract.PATH + "/#",
                OBJECTS_ID
            )
        }
    }

    override fun onCreate(): Boolean {
        return true
    }

    override fun getType(uri: Uri): String {
        return when (uriMatcher.match(uri)) {
            OBJECTS -> "vnd.android.cursor.dir/vnd.com.borkozic.provider.preference"
            OBJECTS_ID -> "vnd.android.cursor.item/vnd.com.borkozic.provider.preference"
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
        Log.d(TAG, "Query: $uri")
        var ids: IntArray? = null
        if (uriMatcher.match(uri) == OBJECTS) {
            if (PreferencesContract.DATA_SELECTION != selection) throw IllegalArgumentException("Quering multiple items is not supported")
            ids = IntArray(selectionArgs!!.size)
            for (i in ids.indices) ids[i] = selectionArgs[i].toInt(10)
        }
        if (uriMatcher.match(uri) == OBJECTS_ID) {
            ids = intArrayOf(ContentUris.parseId(uri).toInt())
        }
        if (ids == null) throw IllegalArgumentException("Unknown URI: $uri")

        val context = context!!
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        val resources = context.resources
        val cursor = MatrixCursor(projection)

        for (id in ids) {
            val row = cursor.newRow()

            when (id) {
                PreferencesContract.SPEED_FACTOR -> {
                    val speedIdx =
                        settings.getString(context.getString(R.string.pref_unitspeed), "0")!!.toInt()
                    val speedFactor =
                        resources.getStringArray(R.array.speed_factors)[speedIdx].toDouble()
                    row.add(speedFactor)
                }
                PreferencesContract.SPEED_ABBREVIATION -> {
                    val speedIdx =
                        settings.getString(context.getString(R.string.pref_unitspeed), "0")!!.toInt()
                    val speedAbbr = resources.getStringArray(R.array.speed_abbrs)[speedIdx]
                    row.add(speedAbbr)
                }
                PreferencesContract.DISTANCE_FACTOR -> {
                    val distanceIdx =
                        settings.getString(context.getString(R.string.pref_unitdistance), "0")!!.toInt()
                    val distanceFactor =
                        resources.getStringArray(R.array.distance_factors)[distanceIdx].toDouble()
                    row.add(distanceFactor)
                }
                PreferencesContract.DISTANCE_ABBREVIATION -> {
                    val distanceIdx =
                        settings.getString(context.getString(R.string.pref_unitdistance), "0")!!.toInt()
                    val distanceAbbr = resources.getStringArray(R.array.distance_abbrs)[distanceIdx]
                    row.add(distanceAbbr)
                }
                PreferencesContract.DISTANCE_SHORT_FACTOR -> {
                    val distanceIdx =
                        settings.getString(context.getString(R.string.pref_unitdistance), "0")!!.toInt()
                    val distanceShortFactor =
                        resources.getStringArray(R.array.distance_factors_short)[distanceIdx].toDouble()
                    row.add(distanceShortFactor)
                }
                PreferencesContract.DISTANCE_SHORT_ABBREVIATION -> {
                    val distanceIdx =
                        settings.getString(context.getString(R.string.pref_unitdistance), "0")!!.toInt()
                    val distanceShortAbbr =
                        resources.getStringArray(R.array.distance_abbrs_short)[distanceIdx]
                    row.add(distanceShortAbbr)
                }
                PreferencesContract.ELEVATION_FACTOR -> {
                    val elevationIdx =
                        settings.getString(context.getString(R.string.pref_unitelevation), "0")!!.toInt()
                    val elevationFactor =
                        resources.getStringArray(R.array.elevation_factors)[elevationIdx].toDouble()
                    row.add(elevationFactor)
                }
                PreferencesContract.ELEVATION_ABBREVIATION -> {
                    val elevationIdx =
                        settings.getString(context.getString(R.string.pref_unitelevation), "0")!!.toInt()
                    val elevationAbbr =
                        resources.getStringArray(R.array.elevation_abbrs)[elevationIdx]
                    row.add(elevationAbbr)
                }
                PreferencesContract.COORDINATES_FORMAT -> {
                    val coordinatesFormat =
                        settings.getString(context.getString(R.string.pref_unitcoordinate), "0")!!.toInt()
                    row.add(coordinatesFormat)
                }
                else -> throw IllegalArgumentException("Unsupported item")
            }
        }

        return cursor
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri {
        throw UnsupportedOperationException("Preferences can not be inserted")
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int {
        throw UnsupportedOperationException("Preferences can not be updated")
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        throw UnsupportedOperationException("Preferences can not be deleted")
    }
}