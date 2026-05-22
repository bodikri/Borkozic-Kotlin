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

import android.graphics.Canvas
import android.view.View

open class MockMap(lat: Int, lon: Int) : Map("//_mock_map_//") {
    companion object {
        private const val serialVersionUID = 1L
        private var currentMap: MockMap? = null

        @JvmStatic
        fun getMap(lat: Double, lon: Double): Map {
            val ilat = (90 - lat).toInt()
            val ilon = (180 + lon).toInt()
            if (currentMap == null) {
                currentMap = MockMap(ilat, ilon)
            } else if (currentMap!!.lat != ilat || currentMap!!.lon != ilon) {
                val s = currentMap!!.zoom
                currentMap = MockMap(ilat, ilon)
                currentMap!!.zoom = s
            }
            return currentMap!!
        }
    }

    private var lat: Int = 0
    private var lon: Int = 0

    init {
        title = "-no map-"
        datum = "WGS84"
        mpp = 0.1
        this.lat = lat
        this.lon = lon
    }

    override fun activate(view: View?, pixels: Int) {}

    override fun deactivate() {}

    override fun activated(): Boolean {
        return true
    }

    override fun coversLatLon(lat: Double, lon: Double): Boolean {
        return false
    }

    override fun drawMap(
        bearing: Float,
        loc: DoubleArray,
        lookAhead: IntArray,
        width: Int,
        height: Int,
        cropBorder: Boolean,
        drawBorder: Boolean,
        c: Canvas
    ): Boolean {
        return false
    }

    override fun getLatLonByXY(x: Int, y: Int, ll: DoubleArray): Boolean {
        ll[0] = 90 - (y * 1.0 / (50000 * zoom) + lat)
        ll[1] = x * 1.0 / (50000 * zoom) + lon - 180
        return true
    }

    override fun getXYByLatLon(lat: Double, lon: Double, xy: IntArray): Boolean {
        val latCalc = 90 - lat
        val lonCalc = 180 + lon
        xy[1] = ((latCalc - this.lat) * 50000 * zoom).toInt()
        xy[0] = ((lonCalc - this.lon) * 50000 * zoom).toInt()
        return true
    }

    override fun getNextZoom(): Double {
        return if (zoom >= 10) 0.0 else zoom + 0.1
    }

    override fun getPrevZoom(): Double {
        return if (zoom <= 0.001) 0.0 else zoom - 0.1
    }

    override fun getZoom(): Double {
        return zoom
    }

    override fun setZoom(zoom: Double) {
        var zoomValue = Math.floor(zoom * 1000) / 1000
        if (zoomValue > 10) zoomValue = 10.0
        if (zoomValue < 0.001) zoomValue = 0.001
        // Note: This method should update the zoom property, but it's not clear from the Java code how it's implemented
    }
}
