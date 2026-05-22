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
 * Bernhard Jenny, 17 September 2010:
 * Tissot projection does no longer derive from SimpleConicProjection base class,
 * but from ConicProjection.
 * Fixed bugs in projectInverse().
 */
package com.jhlabs.map.proj

import com.jhlabs.Point2D
import com.jhlabs.map.MapMath

class TissotProjection : ConicProjection() {

    private val EPS = 1e-10
    private var rho_c: Double = 0.0
    private var rho_0: Double = 0.0
    private var n: Double = 0.0

    init {
        projectionLatitude = Math.toRadians(45.0)
        projectionLatitude1 = Math.toRadians(35.0)
        projectionLatitude2 = Math.toRadians(60.0)
    }

    override fun project(lplam: Double, lpphi: Double, out: Point2D.Double): Point2D.Double {
        var rho = rho_c - lpphi
        val tempLplam = lplam * n
        out.x = rho * Math.sin(tempLplam)
        out.y = rho_0 - rho * Math.cos(tempLplam)
        return out
    }

    override fun projectInverse(xyx: Double, xyy: Double, out: Point2D.Double): Point2D.Double {
        var xyyVar = xyy
        xyyVar = rho_0 - xyyVar
        var rho = MapMath.distance(xyx, xyyVar)
        if (n < 0.0) {
            rho = -rho
            val xyxNeg = -xyx
            val xyyNeg = -xyyVar
        }
        out.x = Math.atan2(xyx, xyyVar) / n
        out.y = rho_c - rho
        return out
    }

    override fun initialize() {
        super.initialize()

        val del = 0.5 * (projectionLatitude2 - projectionLatitude1)
        val sig = 0.5 * (projectionLatitude2 + projectionLatitude1)

        if (Math.abs(del) < EPS || Math.abs(sig) < EPS) {
            throw ProjectionException("-42")
        }
        n = Math.sin(sig)
        val cs = Math.cos(del)
        rho_c = n / cs + cs / n
        rho_0 = Math.sqrt((rho_c - 2 * Math.sin(projectionLatitude)) / n)
    }

    override fun hasInverse(): Boolean {
        return true
    }

    override fun toString(): String {
        return "Tissot"
    }
}