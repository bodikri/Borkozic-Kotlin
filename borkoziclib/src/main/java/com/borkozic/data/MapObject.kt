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

package com.borkozic.data

import android.graphics.Bitmap

open class MapObject {
    @JvmField var _id: Long = 0
    @JvmField var name: String = ""
    @JvmField var description: String = ""
    @JvmField var image: String = ""
    @JvmField var drawImage: Boolean = false
    @JvmField var latitude: Double = 0.0
    @JvmField var longitude: Double = 0.0
    @JvmField var altitude: Double = Int.MIN_VALUE.toDouble()
    @JvmField var proximity: Int = 0
    @JvmField var bitmap: Bitmap? = null
    @JvmField var textcolor: Int = Int.MIN_VALUE
    @JvmField var backcolor: Int = Int.MIN_VALUE

    constructor()

    constructor(lat: Double, lon: Double) {
        latitude = lat
        longitude = lon
    }
}
