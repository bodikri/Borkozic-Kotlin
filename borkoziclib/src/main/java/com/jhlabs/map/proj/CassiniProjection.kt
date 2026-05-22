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
 *
 * Bernhard Jenny, 23 September 2010: change super class to CylindricalProjection.
 */
package com.jhlabs.map.proj

import com.jhlabs.Point2D
import com.jhlabs.map.MapMath
import kotlin.math.*

class CassiniProjection : CylindricalProjection() {

    private var m0: Double = 0.0
    private var n: Double = 0.0
    private var t: Double = 0.0
    private var a1: Double = 0.0
    private var c: Double = 0.0
    private var r: Double = 0.0
    private var dd: Double = 0.0
    private var d2: Double = 0.0
    private var a2: Double = 0.0
    private var tn: Double = 0.0
    private var en: DoubleArray? = null

    private companion object {
        const val C1 = .16666666666666666666
        const val C2 = .00833333333333333333
        const val C3 = .04166666666666666666
        const val C4 = .33333333333333333333
        const val C5 = .06666666666666666666
    }

    init {
        projectionLatitude = Math.toRadians(0.0)
        projectionLongitude = Math.toRadians(0.0)
        minLongitude = Math.toRadians(-90.0)
        maxLongitude = Math.toRadians(90.0)
        initialize()
    }

    override fun project(lplam: Double, lpphi: Double, out: Point2D.Double): Point2D.Double {
        if (spherical) {
            out.x = asin(cos(lpphi) * sin(lplam))
            out.y = atan2(tan(lpphi), cos(lplam)) - projectionLatitude
        } else {
            n = sin(lpphi)
            c = cos(lpphi)
            out.y = MapMath.mlfn(lpphi, n, c, en)
            n = 1.0 / sqrt(1.0 - es * n * n)
            tn = tan(lpphi)
            t = tn * tn
            a1 = lplam * c
            c *= es * c / (1 - es)
            a2 = a1 * a1
            out.x = n * a1 * (1.0 - a2 * t
                    * (C1 - (8.0 - t + 8.0 * c) * a2 * C2))
            out.y = out.y - (m0 - n * tn * a2 *
                    (.5 + (5.0 - t + 6.0 * c) * a2 * C3))
        }
        return out
    }

    override fun projectInverse(xyx: Double, xyy: Double, out: Point2D.Double): Point2D.Double {
        if (spherical) {
            dd = xyy + projectionLatitude
            out.y = asin(sin(dd) * cos(xyx))
            out.x = atan2(tan(xyx), cos(dd))
        } else {
            val ph1 = MapMath.inv_mlfn(m0 + xyy, es, en)
            tn = tan(ph1)
            t = tn * tn
            n = sin(ph1)
            r = 1.0 / (1.0 - es * n * n)
            n = sqrt(r)
            r *= (1.0 - es) * n
            dd = xyx / n
            d2 = dd * dd
            out.y = ph1 - (n * tn / r) * d2 *
                    (.5 - (1.0 + 3.0 * t) * d2 * C3)
            out.x = dd * (1.0 + t * d2
                    * (-C4 + (1.0 + 3.0 * t) * d2 * C5)) / cos(ph1)
        }
        return out
    }

    override fun initialize() {
        super.initialize()
        if (!spherical) {
            en = MapMath.enfn(es) ?: throw IllegalArgumentException()
            m0 = MapMath.mlfn(projectionLatitude, sin(projectionLatitude), cos(projectionLatitude), en)
        }
    }

    override fun hasInverse(): Boolean = true

    override fun getEPSGCode(): Int = 9806

    override fun toString(): String = "Cassini"
}
