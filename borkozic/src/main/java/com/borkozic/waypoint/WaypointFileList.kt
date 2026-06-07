package com.borkozic.waypoint
import com.borkozic.BaseApplication

import java.io.File
import java.io.FilenameFilter
import java.io.IOException
import javax.xml.parsers.ParserConfigurationException
import org.xml.sax.SAXException
import android.content.Intent
import com.borkozic.Borkozic
import com.borkozic.data.Waypoint
import com.borkozic.data.WaypointSet
import com.borkozic.ui.FileListActivity
import com.borkozic.util.GpxFiles
import com.borkozic.util.KmlFiles
import com.borkozic.util.OziExplorerFiles
import com.borkozic.util.WaypointFilenameFilter

class WaypointFileList : FileListActivity() {

    override fun getFilenameFilter(): FilenameFilter {
        return WaypointFilenameFilter()
    }

    override fun getPath(): String {
        val application: Borkozic = BaseApplication.getApplication<Borkozic>()!!
        return application.dataPath!!
    }

    override fun loadFile(file: File) {
        val application: Borkozic = BaseApplication.getApplication<Borkozic>()!!

        try {
            val wptset = WaypointSet(file)
            val lc = file.name.lowercase()
            var waypoints: List<Waypoint>? = null

            if (lc.endsWith(".wpt")) {
                waypoints = OziExplorerFiles.loadWaypointsFromFile(file, application.charset!!)
            } else if (lc.endsWith(".kml")) {
                wptset.path = null
                waypoints = KmlFiles.loadWaypointsFromFile(file)
            } else if (lc.endsWith(".gpx")) {
                wptset.path = null
                waypoints = GpxFiles.loadWaypointsFromFile(file)
            }

            if (waypoints != null) {
                for (waypoint in waypoints) {
                    waypoint.set = wptset
                }
                val count = application.addWaypoints(waypoints.toMutableList(), wptset)
                setResult(android.app.Activity.RESULT_OK, Intent().putExtra("count", count))
            } else {
                setResult(android.app.Activity.RESULT_CANCELED, Intent())
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
