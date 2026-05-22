/*
 * Copyright 2010 mapsforge.org
 *
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2013  Andrey Novikov <http://andreynovikov.info/>
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

/**
 * A tile represents a rectangular part of the world map. All tiles can be
 * identified by their X and Y number together with their zoom level. The actual
 * area that a tile covers on a map depends on the underlying map projection.
 */
class Tile(@JvmField val x: Int, @JvmField val y: Int, @JvmField val zoomLevel: Byte) {

    @JvmField
    var bitmap: Bitmap? = null

    @JvmField
    var generated = false

    private val hashCode: Int = calculateHashCode()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Tile) return false
        return this.x == other.x &&
               this.y == other.y &&
               this.zoomLevel == other.zoomLevel
    }

    override fun hashCode(): Int = hashCode

    override fun toString(): String = "$zoomLevel/$x/$y"

    fun getKey(): Long = getKey(x, y, zoomLevel)

    companion object {
        @JvmStatic
        fun getKey(tx: Int, ty: Int, tz: Byte): Long {
            return tz.toLong() shl 48 or (ty.toLong() shl 24) or (tx.toLong() and 0xFFFFFF)
        }
    }

    private fun calculateHashCode(): Int {
        val prime = 31
        var result = 1
        result = prime * result + (x xor (x ushr 32))
        result = prime * result + (y xor (y ushr 32))
        result = prime * result + zoomLevel
        return result
    }
}
