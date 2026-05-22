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
import kotlin.math.*

class MBTFPQProjection : Projection() {

    companion object {
        private const val NITER = 20
        private const val EPS = 1e-7
        private const val ONETOL = 1.000001
        private const val C = 1.70710678118654752440
        private const val RC = 0.58578643762690495119
        private const val FYC = 1.87475828462269495505
        private const val RYC = 0.53340209679417701685
        private const val FXC = 0.31245971410378249250
        private const val RXC = 3.20041258076506210122
    }

    override fun project(lplam: Double, lpphi: Double, out: Point2D.Double): Point2D.Double {
        var c = C * Math.sin(lpphi)
        out.y = lpphi
        for (i in NITER downTo 1) {
            val th1 = (Math.sin(0.5 * out.y) + Math.sin(out.y) - c) /
                    (0.5 * Math.cos(0.5 * out.y) + Math.cos(out.y))
            out.y -= th1
            if (Math.abs(th1) < EPS) break
        }
        out.x = FXC * lplam * (1.0 + 2.0 * Math.cos(out.y) / Math.cos(0.5 * out.y))
        out.y = FYC * Math.sin(0.5 * out.y)
        return out
    }

    override fun projectInverse(xyx: Double, xyy: Double, out: Point2D.Double): Point2D.Double {
        var t = 0.0

        var lpphi = RYC * xyy
        if (Math.abs(lpphi) > 1.0) {
            if (Math.abs(lpphi) > ONETOL) throw ProjectionException("I")
            else if (lpphi < 0.0) {
                t = -1.0
                lpphi = -Math.PI
            } else {
                t = 1.0
                lpphi = Math.PI
            }
        } else {
            t = lpphi
            lpphi = 2.0 * asin(t)
        }
        out.x = RXC * xyx / (1.0 + 2.0 * Math.cos(lpphi) / Math.cos(0.5 * lpphi))
        lpphi = RC * (t + Math.sin(lpphi))
        if (Math.abs(lpphi) > 1.0) {
            if (Math.abs(lpphi) > ONETOL) {
                throw ProjectionException("I")
            } else {
                lpphi = if (lpphi < 0.0) -MapMath.HALFPI else MapMath.HALFPI
            }
        } else {
            lpphi = Math.asin(lpphi)
        }
        out.y = lpphi
        return out
    }

    override fun hasInverse(): Boolean {
        return true
    }

    override fun toString(): String {
        return "McBryde-Thomas Flat-Polar Quartic"
    }
}