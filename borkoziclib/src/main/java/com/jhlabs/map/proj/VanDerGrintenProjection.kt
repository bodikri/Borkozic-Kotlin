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
import com.jhlabs.map.MapMath
import com.jhlabs.map.proj.ProjectionException

class VanDerGrintenProjection : Projection() {

    companion object {
        private const val TOL = 1.0e-10
        private const val THIRD = 0.33333333333333333333
        private const val TWO_THRD = 0.66666666666666666666
        private const val C2_27 = 0.07407407407407407407
        private const val PI4_3 = 4.18879020478639098458
        private const val PISQ = 9.86960440108935861869
        private const val TPISQ = 19.73920880217871723738
        private const val HPISQ = 4.93480220054467930934
    }

    override fun project(lplam: Double, lpphi: Double, out: Point2D.Double): Point2D.Double {
        val p2 = kotlin.math.abs(lpphi / MapMath.HALFPI)
        if ((p2 - TOL) > 1.0) throw ProjectionException("F")
        val p2Adjusted = if (p2 > 1.0) 1.0 else p2
        
        return when {
            kotlin.math.abs(lpphi) <= TOL -> {
                out.x = lplam
                out.y = 0.0
                out
            }
            kotlin.math.abs(lplam) <= TOL || kotlin.math.abs(p2Adjusted - 1.0) < TOL -> {
                out.x = 0.0
                out.y = kotlin.math.PI * kotlin.math.tan(0.5 * kotlin.math.asin(p2Adjusted))
                if (lpphi < 0.0) out.y = -out.y
                out
            }
            else -> {
                val al = 0.5 * kotlin.math.abs(kotlin.math.PI / lplam - lplam / kotlin.math.PI)
                val al2 = al * al
                var g = kotlin.math.sqrt(1.0 - p2Adjusted * p2Adjusted)
                g = g / (p2Adjusted + g - 1.0)
                val g2 = g * g
                val p2Modified = g * (2.0 / p2Adjusted - 1.0)
                val p2Squared = p2Modified * p2Modified
                out.x = g - p2Squared
                val gModified = p2Squared + al2
                out.x = kotlin.math.PI * (al * out.x + kotlin.math.sqrt(al2 * out.x * out.x - gModified * (g2 - p2Squared))) / gModified
                if (lplam < 0.0) out.x = -out.x
                out.y = kotlin.math.abs(out.x / kotlin.math.PI)
                out.y = 1.0 - out.y * (out.y + 2.0 * al)
                if (out.y < -TOL) throw ProjectionException("F")
                if (out.y < 0.0)
                    out.y = 0.0
                else
                    out.y = kotlin.math.sqrt(out.y) * (if (lpphi < 0.0) -kotlin.math.PI else kotlin.math.PI)
                out
            }
        }
    }

    override fun projectInverse(xyx: Double, xyy: Double, out: Point2D.Double): Point2D.Double {
        val x2 = xyx * xyx
        val ay = kotlin.math.abs(xyy)
        
        if (ay < TOL) {
            out.y = 0.0
            val t = x2 * x2 + TPISQ * (x2 + HPISQ)
            out.x = if (kotlin.math.abs(xyx) <= TOL) 0.0 else 0.5 * (x2 - PISQ + kotlin.math.sqrt(t)) / xyx
            return out
        }
        
        val y2 = xyy * xyy
        val r = x2 + y2
        val r2 = r * r
        val c1 = -kotlin.math.PI * ay * (r + PISQ)
        val c3 = r2 + MapMath.TWOPI * (ay * r + kotlin.math.PI * (y2 + kotlin.math.PI * (ay + MapMath.HALFPI)))
        val c2 = c1 + PISQ * (r - 3.0 * y2)
        val c0 = kotlin.math.PI * ay
        val c2DivC3 = c2 / c3
        val al = c1 / c3 - THIRD * c2DivC3 * c2DivC3
        val m = 2.0 * kotlin.math.sqrt(-THIRD * al)
        val d = C2_27 * c2DivC3 * c2DivC3 * c2DivC3 + (c0 * c0 - THIRD * c2DivC3 * c1) / c3
        val absD = kotlin.math.abs(d)
        
        if ((absD - TOL) <= 1.0) {
            val dValue = when {
                absD > 1.0 -> if (d > 0.0) 0.0 else kotlin.math.PI
                else -> kotlin.math.acos(3.0 * d / (al * m))
            }
            
            out.y = kotlin.math.PI * (m * kotlin.math.cos(dValue * THIRD + PI4_3) - THIRD * c2DivC3)
            if (xyy < 0.0) out.y = -out.y
            val t = r2 + TPISQ * (x2 - y2 + HPISQ)
            out.x = if (kotlin.math.abs(xyx) <= TOL) 0.0 else 0.5 * (r - PISQ + (if (t <= 0.0) 0.0 else kotlin.math.sqrt(t))) / xyx
        } else {
            throw ProjectionException("I")
        }
        
        return out
    }

    override fun hasInverse(): Boolean {
        return true
    }

    override fun toString(): String {
        return "van der Grinten (I)"
    }
}