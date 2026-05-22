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
 * Added isEqualArea; removed two unused static variables. Changed super class
 * from Projection to PseudoCylindricalProjection.
 * Modified by Bernhard Jenny, May 2007 and May 2010.
 */
package com.jhlabs.map.proj

import com.jhlabs.Point2D
import com.jhlabs.map.MapMath

class Eckert4Projection : PseudoCylindricalProjection() {

    companion object {
        private const val C_X = 0.42223820031577120149
        private const val C_Y = 1.32650042817700232218
        private const val C_P = 3.57079632679489661922
        private const val EPS = 1e-7
        private const val NITER = 6
    }

    override fun project(lplam: Double, lpphi: Double, out: Point2D.Double): Point2D.Double {
        var lpphiVar = lpphi
        val p: Double
        var V: Double
        var s: Double
        var c: Double
        var i: Int

        p = C_P * Math.sin(lpphiVar)
        var VValue = lpphiVar * lpphiVar
        lpphiVar *= 0.895168 + VValue * (0.0218849 + VValue * 0.00826809)
        i = NITER
        while (i > 0) {
            c = Math.cos(lpphiVar)
            s = Math.sin(lpphiVar)
            V = (lpphiVar + s * (c + 2.0) - p) / (1.0 + c * (c + 2.0) - s * s)
            lpphiVar -= V
            if (Math.abs(V) < EPS) {
                break
            }
            i--
        }
        if (i == 0) {
            out.x = C_X * lplam
            out.y = if (lpphiVar < 0.0) -C_Y else C_Y
        } else {
            out.x = C_X * lplam * (1.0 + Math.cos(lpphiVar))
            out.y = C_Y * Math.sin(lpphiVar)
        }
        return out
    }

    override fun projectInverse(xyx: Double, xyy: Double, out: Point2D.Double): Point2D.Double {
        var c: Double

        out.y = MapMath.asin(xyy / C_Y)
        c = Math.cos(out.y)
        out.x = xyx / (C_X * (1.0 + c))
        out.y = MapMath.asin((out.y + Math.sin(out.y) * (c + 2.0)) / C_P)
        return out
    }

    override fun hasInverse(): Boolean {
        return true
    }

    override fun toString(): String {
        return "Eckert IV"
    }

    override fun isEqualArea(): Boolean {
        return true
    }
}