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

import java.io.Serializable

class MapPoint : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }

    @JvmField var x: Int = 0
    @JvmField var y: Int = 0
    @JvmField var lat: Double = 0.0
    @JvmField var lon: Double = 0.0
    @JvmField var zone: Int = 0
    @JvmField var n: Double = 0.0
    @JvmField var e: Double = 0.0
    @JvmField var hemisphere: Int = 0

    constructor()

    constructor(mp: MapPoint) {
        this.x = mp.x
        this.y = mp.y
        this.zone = mp.zone
        this.n = mp.n
        this.e = mp.e
        this.hemisphere = mp.hemisphere
        this.lat = mp.lat
        this.lon = mp.lon
    }
}