/*
Copyright 2006 Jerry Huxtable

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

/*
 * This file was semi-automatically converted from the public-domain USGS PROJ source.
 */
/**
 * Changed class name from Molleweide to Mollweide, added missing isEqualArea
 * method by Bernhard Jenny, Oct 2007.
 */
package com.jhlabs.map.proj

import com.jhlabs.Point2D
import kotlin.math.*

open class MollweideProjection : PseudoCylindricalProjection {

    // Wagner 4 and Wagner 5 should be separated
    // FIXME
    companion object {
        const val MOLLWEIDE = 0
        const val WAGNER4 = 1
        const val WAGNER5 = 2
        private const val MAX_ITER = 10
        private const val TOLERANCE = 1e-7
    }

    private var type = MOLLWEIDE
    private var cx: Double = 0.0
    private var cy: Double = 0.0
    private var cp: Double = 0.0

    constructor() : this(PI / 2)

    constructor(type: Int) {
        this.type = type
        when (type) {
            MOLLWEIDE -> init(PI / 2)
            WAGNER4 -> init(PI / 3)
            WAGNER5 -> {
                init(PI / 2)
                cx = 0.90977
                cy = 1.65014
                cp = 3.00896
            }
        }
    }

    constructor(p: Double) {
        init(p)
    }

    constructor(cx: Double, cy: Double, cp: Double) {
        this.cx = cx
        this.cy = cy
        this.cp = cp
    }

    fun init(p: Double) {
        val sp: Double = sin(p)
        val p2 = p + p
        val r = sqrt(PI * 2.0 * sp / (p2 + sin(p2)))
        cx = 2.0 * r / PI
        cy = r / sp
        cp = p2 + sin(p2)
    }

    override fun project(lplam: Double, lpphi: Double, xy: Point2D.Double): Point2D.Double {
        var lpphiVar = lpphi
        val k: Double = cp * sin(lpphiVar)
        var v: Double
        var i = MAX_ITER

        while (i != 0) {
            i--
            v = (lpphiVar + sin(lpphiVar) - k) / (1.0 + cos(lpphiVar))
            lpphiVar -= v
            if (abs(v) < TOLERANCE) {
                break
            }
        }

        if (i == 0) {
            lpphiVar = if (lpphiVar < 0.0) -PI / 2 else PI / 2
        } else {
            lpphiVar *= 0.5
        }

        xy.x = cx * lplam * cos(lpphiVar)
        xy.y = cy * sin(lpphiVar)
        return xy
    }

    override fun projectInverse(x: Double, y: Double, lp: Point2D.Double): Point2D.Double {
        val lat = asin(y / cy)
        val lon = x / (cx * cos(lat))
        val lat2 = lat + lat
        val lat3 = asin((lat2 + sin(lat2)) / cp)
        lp.x = lon
        lp.y = lat3
        return lp
    }

    override fun hasInverse(): Boolean {
        return true
    }

    override fun isEqualArea(): Boolean {
        return type == WAGNER4 || type == MOLLWEIDE
    }

    override fun toString(): String {
        return when (type) {
            WAGNER4 -> "Wagner IV"
            WAGNER5 -> "Wagner V"
            else -> "Mollweide"
        }
    }
}