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
 * Added initialization for minLongitude and maxLongitude.
 * Bernhard Jenny, 15 July 2010.
 */
package com.jhlabs.map.proj

import com.jhlabs.Point2D
import com.jhlabs.map.MapMath

class NicolosiProjection : Projection() {
    companion object {
        private const val EPS = 1e-10
    }

    init {
        minLongitude = Math.toRadians(-90.0)
        maxLongitude = Math.toRadians(90.0)
    }

    override fun project(lplam: Double, lpphi: Double, out: Point2D.Double): Point2D.Double {
        when {
            Math.abs(lplam) < EPS -> {
                out.x = 0.0
                out.y = lpphi
            }
            Math.abs(lpphi) < EPS -> {
                out.x = lplam
                out.y = 0.0
            }
            Math.abs(Math.abs(lplam) - MapMath.HALFPI) < EPS -> {
                out.x = lplam * Math.cos(lpphi)
                out.y = MapMath.HALFPI * Math.sin(lpphi)
            }
            Math.abs(Math.abs(lpphi) - MapMath.HALFPI) < EPS -> {
                out.x = 0.0
                out.y = lpphi
            }
            else -> {
                val tb = MapMath.HALFPI / lplam - lplam / MapMath.HALFPI
                val c = lpphi / MapMath.HALFPI
                val sp = Math.sin(lpphi)
                val d = (1 - c * c) / (sp - c)
                val r2 = tb / d
                val r2Squared = r2 * r2
                val m = (tb * sp / d - 0.5 * tb) / (1 + r2Squared)
                val n = (sp / r2Squared + 0.5 * d) / (1 + 1 / r2Squared)
                val x = Math.cos(lpphi)
                val xCalc = Math.sqrt(m * m + x * x / (1 + r2Squared))
                out.x = MapMath.HALFPI * (m + if (lplam < 0) -xCalc else xCalc)
                val y = Math.sqrt(n * n - (sp * sp / r2Squared + d * sp - 1.0) / (1 + 1 / r2Squared))
                out.y = MapMath.HALFPI * (n + if (lpphi < 0) y else -y)
            }
        }
        return out
    }

    override fun projectInverse(x: Double, y: Double, out: Point2D.Double): Point2D.Double {
        binarySearchInverse(x, y, out)
        return out
    }

    override fun hasInverse(): Boolean {
        return true
    }

    override fun toString(): String {
        return "Nicolosi Globular"
    }
}