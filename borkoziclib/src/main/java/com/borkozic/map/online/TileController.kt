/*
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

package com.borkozic.map.online

import android.util.Log
import android.view.View
import com.borkozic.map.Tile
import com.borkozic.map.TileRAMCache
import java.util.Hashtable
import java.util.LinkedList

class TileController : Thread() {
    private var pendingList = LinkedList<Tile>()
    private var tileMap = Hashtable<Long, Tile>()
    private var mThreada = Thread(this)
    private var mThreadb = Thread(this)
    private var mThreadc = Thread(this)
    private var mThreadd = Thread(this)
    private var view: View? = null
    private var provider: TileProvider? = null
    private var cache: TileRAMCache? = null

    init {
        mThreada.start()
        mThreadb.start()
        mThreadc.start()
        mThreadd.start()
    }

    private val update = Runnable { view?.postInvalidate() }

    override fun run() {
        while (!this.isInterrupted) {
            try {
                val t: Tile?
                synchronized(pendingList) {
                    t = pendingList.poll()
                }
                if (t == null) {
                    synchronized(this) {
                        (this as java.lang.Object).wait()
                    }
                    continue
                }
                tileMap.remove(t.getKey())
                TileFactory.downloadTile(provider!!, t)
                if (t.bitmap != null && !t.generated) {
                    TileFactory.saveTile(provider!!, t)
                    cache?.put(t)
                    if (view?.handler != null) {
                        view?.handler?.post(update)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Interrupts all the Threads
     */
    override fun interrupt() {
        mThreada.interrupt()
        mThreadb.interrupt()
        mThreadc.interrupt()
        mThreadd.interrupt()
    }

    /**
     * @param tx The X position of the Tile to draw
     * @param ty The Y position of the Tile to draw
     * @param tz The Zoom value of the Tile to draw
     * @return The recovered Tile
     */
    fun getTile(tx: Int, ty: Int, tz: Byte): Tile {
        val key = Tile.getKey(tx, ty, tz)
        var t = cache?.get(key)
        if (t == null) {
            t = tileMap[key]
        }
        if (t == null) {
            t = Tile(tx, ty, tz)
            // ⬇️ LOG: опит за зареждане от диск
            //Log.d("TILE_DEBUG", "getTile: cache miss, loading from disk: $tx,$ty zoom=$tz")
            TileFactory.loadTile(provider!!, t)
            if (t.bitmap == null) {
                // ⬇️ LOG: не е намерен на диска – ще генерираме placeholder
                //Log.d("TILE_DEBUG", "getTile: not on disk, generating placeholder: $tx,$ty")
                TileFactory.generateTile(provider!!, cache!!, t)
                if (t.bitmap != null) cache?.put(t)
                tileMap[key] = t
                synchronized(pendingList) {
                    pendingList.add(t)
                }
                synchronized(this) {
                    (this as java.lang.Object).notifyAll()
                }
            } else {
                // ⬇️ LOG: зареден от диск
                //Log.d("TILE_DEBUG", "getTile: loaded from disk: $tx,$ty")
                cache?.put(t)
            }
        } else {
            // ⬇️ LOG: намерен в кеша
            //Log.d("TILE_DEBUG", "getTile: cache hit: $tx,$ty")
        }
        return t
    }

    fun startTileDownloading() {
        synchronized(this) {
            (this as java.lang.Object).notifyAll()
        }
    }

    /**
     * Reset the Tiles to 0
     */
    fun reset() {
        tileMap = Hashtable()
        pendingList = LinkedList()
    }

    fun setView(view: View?) {
        this.view = view
    }

    fun setCache(cache: TileRAMCache?) {
        this.cache = cache
    }

    fun setProvider(provider: TileProvider?) {
        this.provider = provider
    }
}
