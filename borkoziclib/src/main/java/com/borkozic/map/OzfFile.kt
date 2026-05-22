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

package com.borkozic.map

import java.io.File
import java.io.RandomAccessFile

class OzfFile(file: File, @JvmField internal val reader: RandomAccessFile, type: Int) {
    companion object {
        @JvmField
        val OZF_STREAM_DEFAULT = 0
        @JvmField
        val OZF_STREAM_ENCRYPTED = 1
    }

    @JvmField
    var fileptr: Long = 0
    @JvmField
    var type: Int = type
    @JvmField
    var key: Int = 0
    @JvmField
    var size: Long = 0

    @JvmField
    var scales: Int = 0
    @JvmField
    var scales_table: IntArray? = null
    @JvmField
    var images: Array<OzfImageHeader>? = null

    @JvmField
    var ozf2: Ozf2Header? = null
    @JvmField
    var ozf3: Ozf3Header? = null

    init {
        if (this.type == OZF_STREAM_DEFAULT) {
            ozf2 = Ozf2Header()
        } else if (this.type == OZF_STREAM_ENCRYPTED) {
            ozf3 = Ozf3Header()
        }
        size = file.length()
    }

    class OzfImageHeader {
        @JvmField
        var width: Int = 0
        @JvmField
        var height: Int = 0
        @JvmField
        var xtiles: Int = 0
        @JvmField
        var ytiles: Int = 0

        @JvmField
        var palette: ByteArray = ByteArray(1024)
        @JvmField
        var encryption_depth: Int = 0
    }

    class Ozf2Header {
        @JvmField
        var magic: Int = 0
        @JvmField
        var dummy1: Int = 0
        @JvmField
        var dummy2: Int = 0
        @JvmField
        var dummy3: Int = 0
        @JvmField
        var dummy4: Int = 0

        @JvmField
        var width: Int = 0
        @JvmField
        var height: Int = 0

        @JvmField
        var depth: Int = 0
        @JvmField
        var bpp: Int = 0

        @JvmField
        var dummy5: Int = 0

        @JvmField
        var memsiz: Int = 0

        @JvmField
        var dummy6: Int = 0
        @JvmField
        var dummy7: Int = 0
        @JvmField
        var dummy8: Int = 0
        @JvmField
        var version: Int = 0
    }

    class Ozf3Header {
        @JvmField
        var size: Int = 0
        @JvmField
        var width: Int = 0
        @JvmField
        var height: Int = 0
        @JvmField
        var depth: Int = 0
        @JvmField
        var bpp: Int = 0
    }

    fun newImages() {
        images = arrayOfNulls<OzfImageHeader>(scales) as Array<OzfImageHeader>
        for (i in 0 until scales) {
            images!![i] = OzfImageHeader()
        }
    }

    fun width(): Int {
        if (ozf2 != null) return ozf2!!.width
        if (ozf3 != null) return ozf3!!.width
        return 0
    }

    fun height(): Int {
        if (ozf2 != null) return ozf2!!.height
        if (ozf3 != null) return ozf3!!.height
        return 0
    }
}