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
 * 22 September 2010: changed name from MBTFPPProjection to
 * McBrydeThomasFlatPolarParabolicProjection
 * 23 September 2010: changed super class to PseudoCylindricalProjection, added
 * isEqualArea.
 */
package com.jhlabs.map.proj

import com.jhlabs.Point2D
import com.jhlabs.map.MapMath
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

class McBrydeThomasFlatPolarParabolicProjection : PseudoCylindricalProjection() {
    public override fun project(lplam: Double, lpphi: Double, out: Point2D.Double): Point2D.Double {
        var lpphi = lpphi
        lpphi = asin(CS * sin(lpphi))
        out.x = FXC * lplam * (2.0 * cos(C23 * lpphi) - 1.0)
        out.y = FYC * sin(C13 * lpphi)
        return out
    }

    public override fun projectInverse(
        xyx: Double,
        xyy: Double,
        out: Point2D.Double
    ): Point2D.Double {
        out.y = xyy / FYC
        if (abs(out.y) >= 1.0) {
            if (abs(out.y) > ONEEPS) {
                throw ProjectionException("I")
            } else {
                out.y = if (out.y < 0.0) -MapMath.HALFPI else MapMath.HALFPI
            }
        } else {
            out.y = asin(out.y)
        }
        out.x = xyx / (FXC * (2.0 * cos(C23 * (3.0.let { out.y *= it; out.y })) - 1.0))
        if (abs((sin(out.y) / CS).also { out.y = it }) >= 1.0) {
            if (abs(out.y) > ONEEPS) {
                throw ProjectionException("I")
            } else {
                out.y = if (out.y < 0.0) -MapMath.HALFPI else MapMath.HALFPI
            }
        } else {
            out.y = asin(out.y)
        }
        return out
    }

    public override fun hasInverse(): Boolean {
        return true
    }

    override fun isEqualArea(): Boolean {
        return true
    }

    public override fun toString(): String {
        return "McBride-Thomas Flat-Pole Parabolic"
    }

    companion object {
        private const val CS = .95257934441568037152
        private const val FXC = .92582009977255146156
        private const val FYC = 3.40168025708304504493
        private const val C23 = .66666666666666666666
        private const val C13 = .33333333333333333333
        private const val ONEEPS = 1.0000001
    }
}
