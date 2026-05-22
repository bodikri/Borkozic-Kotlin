/*
 * Copyright 2006 Jerry Huxtable
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
 * Bernhard Jenny, 21 September 2010: Changed name from MBTFPSProjection to
 * McBrydeThomasFlatPolarSine2Projection.
 * 23 September 2010: changed super class to PseudoCylindricalProjection, added
 * isEqualArea.
 */
package com.jhlabs.map.proj

import com.jhlabs.Point2D
import com.jhlabs.map.MapMath

class McBrydeThomasFlatPolarSine2Projection : PseudoCylindricalProjection() {

    companion object {
        private const val MAX_ITER = 10
        private const val LOOP_TOL = 1e-7
        private const val C1 = 0.45503
        private const val C2 = 1.36509
        private const val C3 = 1.41546
        private const val C_X = 0.22248
        private const val C_Y = 1.44492
        private const val C1_2 = 0.33333333333333333333333333
    }

    override fun project(lplam: Double, lpphi: Double, out: Point2D.Double): Point2D.Double {
        var k = C3 * Math.sin(lpphi)
        out.y = lpphi
        for (i in MAX_ITER downTo 1) {
            val t = lpphi / C2
            val V = (C1 * Math.sin(t) + Math.sin(lpphi) - k) /
                    (C1_2 * Math.cos(t) + Math.cos(lpphi))
            out.y -= V
            if (Math.abs(V) < LOOP_TOL) {
                break
            }
        }
        val t = lpphi / C2
        out.x = C_X * lplam * (1.0 + 3.0 * Math.cos(lpphi) / Math.cos(t))
        out.y = C_Y * Math.sin(t)
        return out
    }

    override fun projectInverse(xyx: Double, xyy: Double, out: Point2D.Double): Point2D.Double {
        val t = MapMath.asin(xyy / C_Y)
        out.y = C2 * t
        out.x = xyx / (C_X * (1.0 + 3.0 * Math.cos(out.y) / Math.cos(t)))
        out.y = MapMath.asin((C1 * Math.sin(t) + Math.sin(out.y)) / C3)
        return out
    }

    override fun hasInverse(): Boolean {
        return true
    }

    override fun isEqualArea(): Boolean {
        return true // FIXME verify if correct.
    }

    override fun toString(): String {
        return "McBryde-Thomas Flat-Pole Sine (No. 2)"
    }
}