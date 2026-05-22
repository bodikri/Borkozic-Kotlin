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
 * Bernhard Jenny, 19 September 2010:
 * Fixed forward and inverse projections, removed unused constants, added validity
 * test for parameter phi1.
 */
package com.jhlabs.map.proj

import com.jhlabs.Point2D
import com.jhlabs.map.MapMath

class LoximuthalProjection : PseudoCylindricalProjection() {

    companion object {
        private const val EPS = 1e-8
    }

    private var phi1: Double = Math.toRadians(40.0) // FIXME - param
    private var cosphi1: Double = Math.cos(phi1)
    private var tanphi1: Double = Math.tan(MapMath.QUARTERPI + 0.5 * phi1)

    init {
        if (cosphi1 < EPS) {
            throw ProjectionException("-22")
        }
    }

    override fun project(lplam: Double, lpphi: Double, out: Point2D.Double): Point2D.Double {
        val y = lpphi - phi1
        val x = if (Math.abs(y) < EPS) {
            lplam * cosphi1
        } else {
            val temp = MapMath.QUARTERPI + 0.5 * lpphi
            if (Math.abs(temp) < EPS || Math.abs(Math.abs(temp) - MapMath.HALFPI) < EPS) {
                0.0
            } else {
                lplam * y / Math.log(Math.tan(temp) / tanphi1)
            }
        }
        out.x = x
        out.y = y
        return out
    }

    override fun projectInverse(xyx: Double, xyy: Double, out: Point2D.Double): Point2D.Double {
        val latitude = xyy + phi1
        val longitude = if (Math.abs(xyy) < EPS) {
            xyx / cosphi1
        } else {
            val temp = MapMath.QUARTERPI + 0.5 * latitude
            if (Math.abs(temp) < EPS || Math.abs(Math.abs(temp) - MapMath.HALFPI) < EPS) {
                0.0
            } else {
                xyx * Math.log(Math.tan(temp) / tanphi1) / xyy
            }
        }

        out.x = longitude
        out.y = latitude
        return out
    }

    override fun hasInverse(): Boolean {
        return true
    }

    override fun toString(): String {
        return "Loximuthal"
    }
}