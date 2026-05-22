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
 * Bernhard Jenny, 19 September 2010: fixed forward projection.
 * 23 September 2010: changed super class to PseudoCylindricalProjection.
 */
package com.jhlabs.map.proj

import com.jhlabs.Point2D
import com.jhlabs.map.MapMath

class NellProjection : PseudoCylindricalProjection() {

    companion object {
        private const val MAX_ITER = 10
        private const val LOOP_TOL = 1e-7
    }

    override fun project(lplam: Double, lpphi: Double, out: Point2D.Double): Point2D.Double {
        var lpphiVar = lpphi
        val k: Double
        val V: Double
        var i: Int

        k = 2.0 * Math.sin(lpphiVar)
        var VVar = lpphiVar * lpphiVar
        lpphiVar *= 1.00371 + VVar * (-0.0935382 + VVar * -0.011412)
        i = MAX_ITER
        while (i > 0) {
            VVar = (lpphiVar + Math.sin(lpphiVar) - k) / (1.0 + Math.cos(lpphiVar))
            lpphiVar -= VVar
            if (Math.abs(VVar) < LOOP_TOL) {
                break
            }
            i--
        }
        out.x = 0.5 * lplam * (1.0 + Math.cos(lpphiVar))
        out.y = lpphiVar
        return out
    }

    override fun projectInverse(xyx: Double, xyy: Double, out: Point2D.Double): Point2D.Double {
        out.x = 2.0 * xyx / (1.0 + Math.cos(xyy))
        out.y = MapMath.asin(0.5 * (xyy + Math.sin(xyy)))
        return out
    }

    override fun hasInverse(): Boolean {
        return true
    }

    override fun toString(): String {
        return "Nell"
    }
}