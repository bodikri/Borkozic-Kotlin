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
package com.borkozic.map.sas

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import com.borkozic.BaseApplication
import com.borkozic.map.Tile
import com.borkozic.map.TileRAMCache
import java.io.File
import java.io.FileInputStream
import java.io.IOException

object SASTileFactory {
    fun loadTile(map: SASMap, tx: Int, ty: Int, z: Byte): ByteArray? {
        val application = BaseApplication.getApplication<BaseApplication?>()
        if (application == null) return null

        val file = File(getTilePath(map.path, map.ext, tx, ty, z))
        if (file.exists() == false) return null
        try {
            val fileInputStream: FileInputStream?
            fileInputStream = FileInputStream(file)
            val dat = ByteArray(file.length().toInt())
            fileInputStream.read(dat)
            fileInputStream.close()
            return dat
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return null
    }

    fun loadTile(map: SASMap, t: Tile) {
        val data = loadTile(map, t.x, t.y, t.zoomLevel)
        if (data != null) t.bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
    }

    fun generateTile(map: SASMap, cache: TileRAMCache, t: Tile) {
        var parentTileZoom = (t.zoomLevel - 1).toByte()
        var parentTileX = t.x / 2
        var parentTileY = t.y / 2
        var scale = 2

        // Search for parent tile
        while (parentTileZoom >= 0) {
            var parentTile: Tile? = Tile(parentTileX, parentTileY, parentTileZoom)

            if (cache.containsKey(parentTile!!.getKey())) parentTile =
                cache.get(parentTile.getKey())
            else SASTileFactory.loadTile(map, parentTile)

            if (parentTile!!.bitmap != null && scale <= parentTile.bitmap!!.getWidth() && scale <= parentTile.bitmap!!.getHeight()) {
                val matrix = Matrix()
                matrix.postScale(scale.toFloat(), scale.toFloat())

                val miniTileWidth = parentTile.bitmap!!.getWidth() / scale
                val miniTileHeight = parentTile.bitmap!!.getHeight() / scale
                val fromX = (t.x % scale) * miniTileWidth
                val fromY = (t.y % scale) * miniTileHeight

                // Create mini bitmap which will be stretched to tile
                val miniTileBitmap = Bitmap.createBitmap(
                    parentTile.bitmap!!,
                    fromX,
                    fromY,
                    miniTileWidth,
                    miniTileHeight
                )

                // Create tile bitmap from mini bitmap
                t.bitmap = Bitmap.createBitmap(
                    miniTileBitmap,
                    0,
                    0,
                    miniTileWidth,
                    miniTileHeight,
                    matrix,
                    false
                )
                t.generated = true
                miniTileBitmap.recycle()
                break
            }
            parentTileZoom--
            parentTileX /= 2
            parentTileY /= 2
            scale *= 2
        }
    }

    fun getTilePath(path: String?, ext: String?, x: Int, y: Int, z: Byte): String {
        return (path + File.separator + "z" + z.toInt()
            .toString() + File.separator + (x / 1024).toString() + File.separator + "x" + x.toString() + File.separator + (y / 1024).toString() + File.separator + "y" + y.toString() + ext)
    }
}
