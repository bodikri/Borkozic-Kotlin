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

/**
 * Recursive file listing under a specified directory.
 *
 * @author Andrey Novikov
 */
package com.borkozic.util

import java.io.File
import java.io.FilenameFilter
import java.util.Collections

object FileList {
    /**
     * Recursively walk a directory tree and return a List of all
     * files found; the List is sorted using File.compareTo().
     *
     * @param aStartingDir root directory, must be valid directory which can be read.
     * @param aFilter `FilenameFilter` to filter files.
     * @return `List` containing found `File` objects or empty `List` otherwise.
     */
    @JvmStatic
    fun getFileListing(aStartingDir: File, aFilter: FilenameFilter?): List<File> {
        val result = getFileListingNoSort(aStartingDir, aFilter)
        Collections.sort(result)
        return result
    }

    private fun getFileListingNoSort(aStartingDir: File, aFilter: FilenameFilter?): MutableList<File> {
        val result = ArrayList<File>()

        // find files
        aStartingDir.listFiles(aFilter)?.let { files ->
            result.addAll(files.toList())
        }

        // go deeper
        aStartingDir.listFiles(DirFileFilter())?.let { files ->
            for (dir in files) {
                val deeperList = getFileListingNoSort(dir, aFilter)
                result.addAll(deeperList)
            }
        }
        return result
    }
}
