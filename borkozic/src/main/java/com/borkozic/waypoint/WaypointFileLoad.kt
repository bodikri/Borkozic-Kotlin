package com.borkozic.waypoint
import com.borkozic.BaseApplication

import android.app.Activity
import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.borkozic.Borkozic
import com.borkozic.MapActivity
import com.borkozic.R
import com.borkozic.data.Waypoint
import com.borkozic.data.WaypointSet
import com.borkozic.util.GpxFiles
import com.borkozic.util.KmlFiles
import com.borkozic.util.OziExplorerFiles
import java.io.File
import java.io.IOException
import javax.xml.parsers.ParserConfigurationException
import org.xml.sax.SAXException

class WaypointFileLoad : Activity() {

    @android.annotation.SuppressLint("DefaultLocale")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = intent
        val filepath = intent.data?.getPath()

        val pd = ProgressDialog(this)
        pd.isIndeterminate = true
        pd.show()

        val application: Borkozic = BaseApplication.getApplication<Borkozic>()!!

        try {
            val file = File(filepath)
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
                setResult(Activity.RESULT_OK, Intent().putExtra("count", count))
            } else {
                setResult(Activity.RESULT_CANCELED, Intent())
            }
        } catch (e: IllegalArgumentException) {
            Toast.makeText(baseContext, R.string.err_wrongformat, Toast.LENGTH_LONG).show()
        } catch (e: SAXException) {
            Toast.makeText(baseContext, R.string.err_wrongformat, Toast.LENGTH_LONG).show()
            e.printStackTrace()
        } catch (e: IOException) {
            Toast.makeText(baseContext, R.string.err_read, Toast.LENGTH_LONG).show()
            e.printStackTrace()
        } catch (e: ParserConfigurationException) {
            Toast.makeText(baseContext, R.string.err_read, Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }

        pd.dismiss()
        startActivity(Intent(this, MapActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}
