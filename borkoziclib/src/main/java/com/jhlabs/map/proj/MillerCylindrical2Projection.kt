/*
Copyright 2008 Bernhard Jenny

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

package com.jhlabs.map.proj

import com.jhlabs.Point2D
import com.jhlabs.map.MapMath

/**
 * Miller's second cylindrical projection, which is not used as often as his
 * first one. Very similar to Braun's cylindrical stereographic projection.
 * Based on proj4.
 * @author Bernhard Jenny, Institute of Cartography, ETH Zurich.
 */
class MillerCylindrical2Projection : CylindricalProjection() {

    override fun project(lplam: Double, lpphi: Double, out: Point2D.Double): Point2D.Double {
        out.x = lplam
        out.y = kotlin.math.ln(kotlin.math.tan(MapMath.QUARTERPI + lpphi / 3.0)) * 1.5
        return out
    }

    override fun projectInverse(xyx: Double, xyy: Double, out: Point2D.Double): Point2D.Double {
        out.x = xyx
        out.y = 2.5 * (kotlin.math.atan(kotlin.math.exp(0.8 * xyy)) - MapMath.QUARTERPI)
        return out
    }

    override fun hasInverse(): Boolean {
        return true
    }

    override fun toString(): String {
        return "Miller Cylindrical II"
    }
}