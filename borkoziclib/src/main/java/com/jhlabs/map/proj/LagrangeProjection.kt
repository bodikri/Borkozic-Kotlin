/*
 * Copyright 2006 Jerry Huxtable
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * This file was semi-automatically converted from the public-domain USGS PROJ source.
 *
 * Bernhard Jenny, 17 September 2010:
 * Changed initialize() to not use projectionLatitude1.
 */
package com.jhlabs.map.proj

import com.jhlabs.Point2D
import com.jhlabs.map.MapMath

class LagrangeProjection : Projection() {

    // Parameters
    private var hrw: Double = 0.0
    private var rw = 1.4
    private var a1: Double = 0.0
    private var phi1: Double = 0.0

    override fun project(lplam: Double, lpphi: Double, xy: Point2D.Double): Point2D.Double {
        var lpphiCopy = lpphi
        var lplamCopy = lplam
        val v: Double
        val c: Double

        if (Math.abs(Math.abs(lpphiCopy) - MapMath.HALFPI) < TOL) {
            xy.x = 0.0
            xy.y = if (lpphiCopy < 0) -2.0 else 2.0
        } else {
            lpphiCopy = Math.sin(lpphiCopy)
            v = a1 * Math.pow((1.0 + lpphiCopy) / (1.0 - lpphiCopy), hrw)
            lplamCopy *= rw
            c = 0.5 * (v + 1.0 / v) + Math.cos(lplamCopy)
            if (c < TOL) {
                throw ProjectionException()
            }
            xy.x = 2.0 * Math.sin(lplamCopy) / c
            xy.y = (v - 1.0 / v) / c
        }
        return xy
    }

    fun setW(w: Double) {
        this.rw = w
    }

    fun getW(): Double {
        return rw
    }

    override fun initialize() {
        super.initialize()
        if (rw <= 0) throw ProjectionException("-27")
        hrw = 0.5 * (1.0 / rw).also { rw = it }
        phi1 = 0.0 // projectionLatitude1; FIXME
        phi1 = Math.sin(phi1)
        if (Math.abs(Math.abs(phi1) - 1.0) < TOL) throw ProjectionException("-22")
        a1 = Math.pow((1.0 - phi1) / (1.0 + phi1), hrw)
    }

    /**
     * Returns true if this projection is conformal
     */
    override fun isConformal(): Boolean {
        return true
    }

    override fun hasInverse(): Boolean {
        return false
    }

    override fun toString(): String {
        return "Lagrange"
    }

    companion object {
        private const val TOL = 1e-10
    }
}