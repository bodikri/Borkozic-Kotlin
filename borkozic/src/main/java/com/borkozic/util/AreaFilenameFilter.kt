package com.borkozic.util

import java.io.File
import java.io.FilenameFilter

class AreaFilenameFilter : FilenameFilter {

    override fun accept(dir: File, filename: String): Boolean {
        val lc = filename.lowercase()
        return lc.endsWith(".art2")
    }
}