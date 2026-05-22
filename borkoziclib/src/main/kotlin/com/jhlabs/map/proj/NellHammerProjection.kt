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
 * Nell-Hammer is a mean of the Cylindical Equal-Area and the Sinusoidal.
 * PROJ.4 contains code for weighted means. Here n = 0.5
 *
 * Bernhard Jenny, July 2007:
 * Changed name from NellHProjection, changed superclass to 
 * PseudoCylindricalProjection.
 *
 * Bernhard Jenny, 19 September 2010: Fixed inverse projection.
 * 
 */
package com.jhlabs.map.proj

import com.jhlabs.Point2D
import com.jhlabs.map.MapMath

class NellHammerProjection : PseudoCylindricalProjection() {

    companion object {
        private const val NITER = 9
        private const val EPS = 1e-7
    }

    override fun project(lplam: Double, lpphi: Double, out: Point2D.Double): Point2D.Double {
        out.x = 0.5 * lplam * (1.0 + kotlin.math.cos(lpphi))
        out.y = 2.0 * (lpphi - kotlin.math.tan(0.5 * lpphi))
        return out
    }

    override fun projectInverse(xyx: Double, xyy: Double, out: Point2D.Double): Point2D.Double {
        val p = 0.5 * xyy
        var phi = 0.0 // PROJ.4 does not implicitly initialize phi!
        
        for (i in NITER downTo 1) {
            val c = kotlin.math.cos(0.5 * phi)
            val V = (phi - kotlin.math.tan(phi / 2) - p) / (1.0 - 0.5 / (c * c))
            phi -= V
            if (kotlin.math.abs(V) < EPS) {
                break
            }
        }
        
        if (phi == 0.0) {
            out.y = if (p < 0) -MapMath.HALFPI else MapMath.HALFPI
            out.x = 2.0 * xyx
        } else {
            out.x = 2.0 * xyx / (1.0 + kotlin.math.cos(phi))
            out.y = phi
        }
        return out
    }

    override fun hasInverse(): Boolean {
        return true
    }

    override fun toString(): String {
        return "Nell-Hammer"
    }
}