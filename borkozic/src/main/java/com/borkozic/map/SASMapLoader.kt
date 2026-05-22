/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2014  Andrey Novikov <http://andreynovikov.info/>
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

import com.borkozic.map.sas.SASMap
import java.io.File
import java.io.IOException

class SASMapLoader {
    companion object {
        @JvmStatic
        @Throws(IOException::class)
        fun load(file: File): SASMap {
            val name = file.name
            var ext: String? = null
            var ellipsoid = false
            val zooms = file.list()
            var minZoom = Integer.MAX_VALUE
            var maxZoom = Integer.MIN_VALUE
            for (zoom in zooms!!) {
                if ("ellipsoid" == zoom) ellipsoid = true
                if (!zoom.startsWith("z")) continue
                val z = zoom.substring(1).toInt()
                if (z < minZoom) minZoom = z
                if (z > maxZoom) maxZoom = z
            }
            val corners = IntArray(4)
            ext = calculateCorners(file, maxZoom, corners)
            if (maxZoom > 0 && ext != null) {
                val map = SASMap(name, file.absolutePath, ext, minZoom - 1, maxZoom - 1)
                map.ellipsoid = ellipsoid
                map.setCornersAmount(4)
                map.cornerMarkers!![0].x = corners[0] * SASMap.TILE_WIDTH
                map.cornerMarkers!![0].y = corners[1] * SASMap.TILE_HEIGHT
                map.cornerMarkers!![1].x = corners[0] * SASMap.TILE_WIDTH
                map.cornerMarkers!![1].y = (corners[3] + 1) * SASMap.TILE_HEIGHT
                map.cornerMarkers!![2].x = (corners[2] + 1) * SASMap.TILE_WIDTH
                map.cornerMarkers!![2].y = (corners[3] + 1) * SASMap.TILE_HEIGHT
                map.cornerMarkers!![3].x = (corners[2] + 1) * SASMap.TILE_WIDTH
                map.cornerMarkers!![3].y = corners[1] * SASMap.TILE_HEIGHT
                val ll = DoubleArray(2)
                for (i in 0..3) {
                    map.getLatLonByXY(map.cornerMarkers!![i].x, map.cornerMarkers!![i].y, ll)
                    map.cornerMarkers!![i].lat = ll[0]
                    map.cornerMarkers!![i].lon = ll[1]
                }
                return map
            } else {
                throw IOException("Invalid SAS cache dir: $name")
            }
        }

        private fun calculateCorners(file: File, zoom: Int, corners: IntArray): String? {
            var minX = Integer.MAX_VALUE
            var minY = Integer.MAX_VALUE
            var maxX = Integer.MIN_VALUE
            var maxY = Integer.MIN_VALUE
            var ext: String? = null
            val root = File(file, "z$zoom")
            val x1024 = root.listFiles() ?: return null
            for (x1024file in x1024) {
                val xs = x1024file.listFiles() ?: continue
                for (xfile in xs) {
                    try {
                        val x = xfile.name.substring(1).toInt()
                        if (x < minX) minX = x
                        if (x > maxX) maxX = x
                    } catch (e: NumberFormatException) {
                        e.printStackTrace()
                    }
                    val y1024 = xfile.listFiles() ?: continue
                    for (y1024file in y1024) {
                        val ys = y1024file.list() ?: continue
                        for (yf in ys) {
                            val dot = yf.lastIndexOf(".")
                            try {
                                val y = yf.substring(1, dot).toInt()
                                if (y < minY) minY = y
                                if (y > maxY) maxY = y
                                if (ext == null) ext = yf.substring(dot)
                            } catch (e: NumberFormatException) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            }
            corners[0] = minX
            corners[1] = minY
            corners[2] = maxX
            corners[3] = maxY
            return ext
        }
    }
}