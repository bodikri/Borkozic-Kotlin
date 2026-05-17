package com.borkozic.area

import android.content.Intent
import com.borkozic.Borkozic
import com.borkozic.data.Area
import com.borkozic.ui.FileListActivity
import com.borkozic.util.AreaFilenameFilter
import com.borkozic.util.OziExplorerFiles
import java.io.File
import java.io.FilenameFilter
import java.io.IOException

class AreaFileList : FileListActivity() {

    override fun getFilenameFilter(): FilenameFilter {
        return AreaFilenameFilter()
    }

    override fun getPath(): String {
        val application = application as Borkozic
        return application.dataPath!!
    }

    override fun loadFile(file: File) {
        val application = application as Borkozic
        var areas: List<Area>
        try {
            val lc = file.name.toLowerCase()
            if (lc.endsWith(".art2")) {
                areas = OziExplorerFiles.loadAreasFromFile(file, application.charset!!)
            } else {
                areas = emptyList()
            }

            if (areas.isNotEmpty()) {
                val index = IntArray(areas.size)
                var i = 0
                for (area in areas) {
                    index[i] = application.addArea(area)
                    i++
                }
                setResult(RESULT_OK, intent.putExtra("index", index))
            } else {
                setResult(RESULT_CANCELED, Intent())
            }
            finish()
        } catch (e: IllegalArgumentException) {
            runOnUiThread(wrongFormat)
        } catch (e: IOException) {
            runOnUiThread(readError)
            e.printStackTrace()
        }
    }
}
