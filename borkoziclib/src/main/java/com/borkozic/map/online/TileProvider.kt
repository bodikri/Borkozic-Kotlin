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

package com.borkozic.map.online

import android.util.Log
import com.borkozic.util.CSV
import java.util.ArrayList
import java.util.Locale

class TileProvider {
    @JvmField
    var servers: ArrayList<String> = ArrayList()
    @JvmField
    var path: String? = null
    @JvmField
    var name: String? = null
    @JvmField
    var code: String? = null
    @JvmField
    var secret: String? = null
    @JvmField
    var minZoom: Byte = 0
    @JvmField
    var maxZoom: Byte = 0
    @JvmField
    var inverseY = false
    @JvmField
    var ellipsoid = false
    @JvmField
    var tileSize = 25000

    private var nextServer = 0
    //TODO Better initialization?
    private val locale = Locale.getDefault().toString()

    fun getTileUri(x: Int, y: Int, z: Byte): String {
        var uri = path ?: ""
        if (servers.isNotEmpty()) {
            if (servers.size <= nextServer) nextServer = 0
            uri = uri.replace("{\$s}", servers[nextServer])
            nextServer++
        }
        var newY = y
        if (inverseY) newY = (Math.pow(2.0, z.toDouble()) - 1 - y).toInt()
        uri = uri.replace("{\$l}", locale)
        uri = uri.replace("{\$z}", z.toString())
        uri = uri.replace("{\$x}", x.toString())
        uri = uri.replace("{\$y}", newY.toString())
        if (uri.contains("{\$q}")) uri = uri.replace("{\$q}", encodeQuadTree(z.toInt(), x, newY))
        if (uri.contains("{\$g}") && secret != null) {
            val stringlen = (3 * x + newY) and 7
            uri = uri.replace("{\$g}", secret!!.substring(0, stringlen))
        }
        Log.d("TILE_DEBUG", "Generated URI: $uri")
        return uri
    }

    companion object {
        @JvmStatic
        fun fromString(s: String): TileProvider? {
            val provider = TileProvider()
            val fields = CSV.parseLine(s)
            if (fields.size < 6) return null
            if ("" == fields[0] || "" == fields[1] || "" == fields[5]) return null
            provider.name = fields[0]
            provider.code = fields[1]
            provider.path = fields[5]
            provider.path = provider.path?.replace("{comma}", ",")
            try {
                provider.minZoom = fields[2].toInt().toByte()
                provider.maxZoom = fields[3].toInt().toByte()
                if ("" != fields[4]) provider.tileSize = fields[4].toInt()
            } catch (e: NumberFormatException) {
                return null
            }
            if (fields.size > 6 && "" != fields[6]) provider.servers.add(fields[6])
            if (fields.size > 7 && "" != fields[7]) provider.servers.add(fields[7])
            if (fields.size > 8 && "" != fields[8]) provider.servers.add(fields[8])
            if (fields.size > 9 && "" != fields[9]) provider.servers.add(fields[9])
            provider.inverseY = fields.size > 10 && "yinverse" == fields[10]
            provider.ellipsoid = fields.size > 10 && "ellipsoid" == fields[10]
            if (fields.size > 11 && "" != fields[11]) provider.secret = fields[11]
            return provider
        }
    }

    private val NUM_CHAR = charArrayOf('0', '1', '2', '3')

    /**
     * See: http://msdn.microsoft.com/en-us/library/bb259689.aspx
     * @param zoom
     * @param tilex
     * @param tiley
     * @return quadtree encoded tile number
     */
    private fun encodeQuadTree(zoom: Int, tilex: Int, tiley: Int): String {
        val tileNum = CharArray(zoom)
        var tx = tilex
        var ty = tiley
        for (i in zoom - 1 downTo 0) {
            // Binary encoding using ones for tilex and twos for tiley. if a bit
            // is set in tilex and tiley we get a three.
            val num = (tx % 2) or ((ty % 2) shl 1)
            tileNum[i] = NUM_CHAR[num]
            tx = tx shr 1
            ty = ty shr 1
        }
        return String(tileNum)
    }
}
