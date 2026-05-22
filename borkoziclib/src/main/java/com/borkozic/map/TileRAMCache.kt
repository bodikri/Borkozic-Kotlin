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

import java.util.LinkedHashMap

/**
 * A thread-safe cache for tiles with a fixed size and LRU policy.
 */
class TileRAMCache(private val capacity: Int) {

    private var map: LinkedHashMap<Long, Tile>?

    init {
        if (capacity < 0) {
            throw IllegalArgumentException()
        }
        map = createMap(capacity)
    }

    private fun createMap(initialCapacity: Int): LinkedHashMap<Long, Tile> {
        return object : LinkedHashMap<Long, Tile>(initialCapacity + 1, LOAD_FACTOR, true) {
            private val serialVersionUID = 3L

            override fun remove(key: Long): Tile? {
                val tile = super.remove(key)
                if (tile?.bitmap != null) {
                    tile.bitmap?.recycle()
                }
                tile?.bitmap = null
                return tile
            }

            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Tile>?): Boolean {
                return size > initialCapacity
            }
        }
    }

    @Synchronized
    fun containsKey(key: Long): Boolean {
        return map?.containsKey(key) ?: false
    }

    @Synchronized
    fun clear() {
        map?.let { m ->
            for (tile in m.values) {
                if (tile.bitmap != null) {
                    tile.bitmap?.recycle()
                }
                tile.bitmap = null
            }
            m.clear()
        }
    }

    @Synchronized
    fun destroy() {
        clear()
        map = null
    }

    @Synchronized
    operator fun get(key: Long): Tile? {
        return map?.get(key)
    }

    @Synchronized
    fun put(tile: Tile) {
        if (capacity > 0 && map != null) {
            val key = Tile.getKey(tile.x, tile.y, tile.zoomLevel)
            if (map!!.containsKey(key)) {
                val t = map!![key]
                if (t != null && t.generated && !tile.generated) {
                    map!!.remove(key)
                } else {
                    return
                }
            }
            map!![key] = tile
        }
    }

    companion object {
        private const val LOAD_FACTOR = 1.1f
    }
}
