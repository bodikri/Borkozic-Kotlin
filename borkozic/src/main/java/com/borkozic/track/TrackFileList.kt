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

package com.borkozic.track

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.borkozic.Borkozic
import com.borkozic.R
import com.borkozic.data.Track
import com.borkozic.ui.FileListActivity
import com.borkozic.util.GpxFiles
import com.borkozic.util.KmlFiles
import com.borkozic.util.OziExplorerFiles
import com.borkozic.util.TrackFilenameFilter
import java.io.File
import java.io.FilenameFilter
import java.io.IOException
import java.util.ArrayList
import javax.xml.parsers.ParserConfigurationException
import org.xml.sax.SAXException

class TrackFileList : FileListActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Toast.makeText(baseContext, getString(R.string.msg_badtrackimplementation), Toast.LENGTH_LONG).show()
    }

    override fun getFilenameFilter(): FilenameFilter {
        return TrackFilenameFilter()
    }

    override fun getPath(): String {
        val application = getApplication() as Borkozic
        return application.dataPath!!
    }

    override fun loadFile(file: File) {
        val application = getApplication() as Borkozic
        var tracks: List<Track>? = null
        try {
            val lc = file.name.lowercase()
            if (lc.endsWith(".plt")) {
                tracks = ArrayList()
                tracks.add(OziExplorerFiles.loadTrackFromFile(file, application.charset!!))
            } else if (lc.endsWith(".kml")) {
                tracks = KmlFiles.loadTracksFromFile(file)
            } else if (lc.endsWith(".gpx")) {
                tracks = GpxFiles.loadTracksFromFile(file)
            }
        if (tracks != null && tracks.size > 0) {
            val index = IntArray(tracks.size)
            var i = 0
            for (track in tracks) {
                    index[i] = application.addTrack(track)
                    i++
                }
                setResult(Activity.RESULT_OK, Intent().putExtra("index", index))
            } else {
                setResult(Activity.RESULT_CANCELED, Intent())
            }
            finish()
        } catch (e: IllegalArgumentException) {
            runOnUiThread(wrongFormat)
        } catch (e: SAXException) {
            runOnUiThread(wrongFormat)
            e.printStackTrace()
        } catch (e: IOException) {
            runOnUiThread(readError)
            e.printStackTrace()
        } catch (e: ParserConfigurationException) {
            runOnUiThread(readError)
            e.printStackTrace()
        }
    }
}