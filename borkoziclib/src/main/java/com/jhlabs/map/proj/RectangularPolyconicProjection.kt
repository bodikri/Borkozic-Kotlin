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
package com.jhlabs.map.proj

import com.jhlabs.Point2D
import com.jhlabs.map.*

class RectangularPolyconicProjection : Projection() {

    private var phi0: Double = 0.0
    private var phi1: Double = 0.0
    private var fxa: Double = 0.0
    private var fxb: Double = 0.0
    private var mode: Boolean = false

    override fun project(lplam: Double, lpphi: Double, out: Point2D.Double): Point2D.Double {
        val fa: Double

        if (mode)
            fa = Math.tan(lplam * fxb) * fxa
        else
            fa = 0.5 * lplam
        if (Math.abs(lpphi) < EPS) {
            out.x = fa + fa
            out.y = - phi0
        } else {
            out.y = 1.0 / Math.tan(lpphi)
            val fa2 = 2.0 * Math.atan(fa * Math.sin(lpphi))
            out.x = Math.sin(fa2) * out.y
            out.y = lpphi - phi0 + (1.0 - Math.cos(fa2)) * out.y
        }
        return out
    }

    override fun initialize() { // rpoly
        super.initialize()
/*FIXME
        if ((mode = (phi1 = Math.abs(pj_param(params, "rlat_ts").f)) > EPS)) {
            fxb = 0.5 * Math.sin(phi1)
            fxa = 0.5 / fxb
        }
*/
    }

    override fun toString(): String {
        return "Rectangular Polyconic"
    }

    companion object {
        private const val EPS = 1e-9
    }
}