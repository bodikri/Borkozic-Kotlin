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

import android.graphics.Bitmap
import android.util.Log
import java.io.File
import java.io.IOException

class OzfReader(file: File) {
    @JvmField
    var zoom: Double = 0.0
    @JvmField
    var source: Int = 0
    @JvmField
    var factor: Double = 0.0
    @JvmField
    var zoomKey: Byte = 0
    @JvmField
    var ozf: OzfFile
    @JvmField
    var cache: TileRAMCache? = null

    init {
        ozf = OzfDecoder.open(file)
        setZoom(1.0)
    }

    fun setCache(tileRAMCache: TileRAMCache?) {
        this.cache = tileRAMCache
    }

    fun getZoom(): Double {
        return zoom
    }

    fun setZoom(zoom: Double): Double {
        android.util.Log.d("OZF", "setZoom ENTER: requested=$zoom, ozf.scales=${ozf.scales}")
        this.zoom = zoom

        val b = ozf.height().toDouble()
        var k = 0
        var delta = Double.MAX_VALUE
        var ozf_zoom = 1.0
        
        for (i in 0 until ozf.scales) {
            val a = OzfDecoder.scaleDy(ozf, i).toDouble()
        
            val tenpercents = Math.round((a / b) * 1000).toDouble()
            val z = tenpercents / 1000.0

            // if current zoom is < 100% - we need to select 
            // nearest upper native zoom
            // otherwize we need to select
            // any nearest zoom

            if (this.zoom < 1.0)
                if (this.zoom > z)
                    continue
            
            val d = Math.abs(z - this.zoom)
            if (d < delta) {
                delta = d
                k = i
                ozf_zoom = z
            }
        }        
        
        source = k
        factor = this.zoom / ozf_zoom
        zoomKey = (this.zoom * 50).toInt().toByte()

        Log.d("OZF", String.format("zoom: %f, selected source scale: %f (%d), factor: %f", this.zoom, ozf_zoom, source, factor))
        
        return this.zoom
    }

    fun close() {
        OzfDecoder.close(ozf)
    }

    fun map_x_to_c(map_x: Int): Double {
        return map_x / (OzfDecoder.OZF_TILE_WIDTH * factor)
    }

    fun map_y_to_r(map_y: Int): Double {
        return map_y / (OzfDecoder.OZF_TILE_HEIGHT * factor)
    }

    fun map_xy_to_cr(map_xy: IntArray): IntArray {
        val cr = IntArray(2)

        cr[0] = (Math.abs(map_xy[0]) / (OzfDecoder.OZF_TILE_WIDTH * factor)).toInt()
        cr[1] = (Math.abs(map_xy[1]) / (OzfDecoder.OZF_TILE_HEIGHT * factor)).toInt()
        
        return cr
    }

    fun map_xy_to_xy_on_tile(map_xy: IntArray): IntArray {
        val cr = map_xy_to_cr(map_xy)
        val xy = IntArray(2)

        xy[0] = Math.round(map_xy[0] - cr[0] * (OzfDecoder.OZF_TILE_WIDTH * factor)).toInt()
        xy[1] = Math.round(map_xy[1] - cr[1] * (OzfDecoder.OZF_TILE_HEIGHT * factor)).toInt()
        
        return xy
    }

    fun tile_dx(): Int {
        return tile_dx(0, 0)
    }
    
    fun tile_dy(): Int {
        return tile_dy(0, 0)
    }
    
    fun tile_dx(c: Int, r: Int): Int {
        if (c > tiles_per_x() - 1 || r > tiles_per_y() - 1) {
            return 0
        }

        var dx = OzfDecoder.OZF_TILE_WIDTH.toDouble()

        if (c == tiles_per_x() - 1) {
            val w = OzfDecoder.scaleDx(ozf, source).toDouble()
            dx = w - (w / OzfDecoder.OZF_TILE_WIDTH) * OzfDecoder.OZF_TILE_WIDTH
            if (dx == 0.0)
                dx = OzfDecoder.OZF_TILE_WIDTH.toDouble()
        }
        
        return (dx * factor).toInt()
    }

    fun tile_dy(c: Int, r: Int): Int {
        if (c > tiles_per_x() - 1 || r > tiles_per_y() - 1)
            return 0

        var dy = OzfDecoder.OZF_TILE_HEIGHT.toDouble()
        
        if (r == tiles_per_y() - 1) {
            val h = OzfDecoder.scaleDy(ozf, source).toDouble()
            dy = h - (h / OzfDecoder.OZF_TILE_HEIGHT) * OzfDecoder.OZF_TILE_HEIGHT
            if (dy == 0.0)
                dy = OzfDecoder.OZF_TILE_HEIGHT.toDouble()
        }
        
        return (dy * factor).toInt()
    }

    fun tiles_per_x(): Int {
        return OzfDecoder.numTilesPerX(ozf, source)
    }

    fun tiles_per_y(): Int {
        return OzfDecoder.numTilesPerY(ozf, source)
    }

    @Throws(OutOfMemoryError::class)
    fun tile_get(c: Int, r: Int): Bitmap? {
        if (c < 0 || c > tiles_per_x() - 1)
            return null

        if (r < 0 || r > tiles_per_y() - 1)
            return null

        val key = Tile.getKey(c, r, zoomKey)
        val tile = Tile(c, r, zoomKey)
        var tileBitmap: Bitmap? = null
        
        if (cache != null) {
            val t = cache?.get(key)
            if (t != null)
                tileBitmap = t.bitmap
        }
        if (tileBitmap == null) {
            var w = OzfDecoder.OZF_TILE_WIDTH
            var h = OzfDecoder.OZF_TILE_HEIGHT
            if (factor < 1.0) {
                w = (factor * w).toInt()
                h = (factor * h).toInt()
            }
            val data = OzfDecoder.getTile(ozf, source, c, r, w, h)
            if (data != null) {
                tileBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
                tileBitmap.setPixels(data, 0, w, 0, 0, w, h)
            }
            if (tileBitmap != null && factor > 1.0) {
                val sw = (factor * OzfDecoder.OZF_TILE_WIDTH).toInt()
                val sh = (factor * OzfDecoder.OZF_TILE_HEIGHT).toInt()
                val scaled = Bitmap.createScaledBitmap(tileBitmap, sw, sh, true)
                tileBitmap = scaled
            }
        
            if (cache != null && tileBitmap != null) {
                tile.bitmap = tileBitmap
                cache?.put(tile)
            }
        }
        
        return tileBitmap
    }
}