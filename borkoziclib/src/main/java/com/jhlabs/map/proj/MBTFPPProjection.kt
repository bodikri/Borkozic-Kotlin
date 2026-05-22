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
 * Bernhard Jenny, 19 September 2010: Fixed forward projection.
 */
package com.jhlabs.map.proj

import com.jhlabs.Point2D
import com.jhlabs.map.MapMath
import kotlin.math.*

class MBTFPPProjection : Projection() {

    companion object {
        private const val CS = 0.95257934441568037152
        private const val FXC = 0.92582009977255146156
        private const val FYC = 3.40168025708304504493
        private const val C23 = 0.66666666666666666666
        private const val C13 = 0.33333333333333333333
        private const val ONEEPS = 1.0000001
    }

    override fun project(lplam: Double, lpphi: Double, out: Point2D.Double): Point2D.Double {
        var phi = lpphi
        phi = kotlin.math.asin(CS * kotlin.math.sin(phi))
        out.x = FXC * lplam * (2.0 * kotlin.math.cos(C23 * phi) - 1.0)
        out.y = FYC * kotlin.math.sin(C13 * phi)
        return out
    }

    override fun projectInverse(xyx: Double, xyy: Double, out: Point2D.Double): Point2D.Double {
        out.y = xyy / FYC
        if (kotlin.math.abs(out.y) >= 1.0) {
            if (kotlin.math.abs(out.y) > ONEEPS) {
                throw ProjectionException("I")
            } else {
                out.y = if (out.y < 0.0) -MapMath.HALFPI else MapMath.HALFPI
            }
        } else {
            out.y = kotlin.math.asin(out.y)
        }
        out.y *= 3.0
        out.x = xyx / (FXC * (2.0 * cos(C23 * out.y) - 1.0))
        out.y = sin(out.y) / CS
        if (abs(out.y) >= 1.0) {
            if (kotlin.math.abs(out.y) > ONEEPS) {
                throw ProjectionException("I")
            } else {
                out.y = if (out.y < 0.0) -MapMath.HALFPI else MapMath.HALFPI
            }
        } else {
            out.y = kotlin.math.asin(out.y)
        }
        return out
    }

    override fun hasInverse(): Boolean {
        return true
    }

    override fun toString(): String {
        return "McBride-Thomas Flat-Polar Parabolic"
    }
}