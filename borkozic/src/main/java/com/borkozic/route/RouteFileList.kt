package com.borkozic.route

import android.content.Intent
import com.borkozic.Borkozic
import com.borkozic.data.Route
import com.borkozic.ui.FileListActivity
import com.borkozic.util.GpxFiles
import com.borkozic.util.KmlFiles
import com.borkozic.util.OziExplorerFiles
import com.borkozic.util.RouteFilenameFilter
import java.io.File
import java.io.FilenameFilter
import java.io.IOException
import javax.xml.parsers.ParserConfigurationException
import org.xml.sax.SAXException

class RouteFileList : FileListActivity() {

    override fun getFilenameFilter(): FilenameFilter {
        return RouteFilenameFilter()
    }

    override fun getPath(): String {
        val application = application as Borkozic
        return application.dataPath!!
    }

    override fun loadFile(file: File) {
        val application = application as Borkozic
        var routes: List<Route>

        try {
            val lc = file.name.toLowerCase()
            if (lc.endsWith(".rt2") || lc.endsWith(".rte")) {
                routes = OziExplorerFiles.loadRoutesFromFile(file, application.charset!!)
            } else if (lc.endsWith(".kml")) {
                routes = KmlFiles.loadRoutesFromFile(file)
            } else if (lc.endsWith(".gpx")) {
                routes = GpxFiles.loadRoutesFromFile(file)
            } else {
                routes = emptyList()
            }

            if (routes.isNotEmpty()) {
                val index = IntArray(routes.size)
                var i = 0
                for (route in routes) {
                    index[i] = application.addRoute(route)
                    i++
                }
                setResult(RESULT_OK, Intent().putExtra("index", index))
            } else {
                setResult(RESULT_CANCELED, Intent())
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
