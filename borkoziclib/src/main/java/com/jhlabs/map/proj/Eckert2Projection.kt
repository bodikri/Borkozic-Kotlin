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
 * Bernhard Jenny, 23 September 2010: changed base class to PseudoCylindricalProjection.
 */
package com.jhlabs.map.proj

import com.jhlabs.Point2D
import com.jhlabs.map.MapMath

class Eckert2Projection : PseudoCylindricalProjection() {

    companion object {
        private const val FXC = 0.46065886596178063902
        private const val FYC = 1.44720250911653531871
        private const val C13 = 0.33333333333333333333
        private const val ONEEPS = 1.0000001
    }

    override fun project(lplam: Double, lpphi: Double, out: Point2D.Double): Point2D.Double {
        val y = Math.sqrt(4.0 - 3.0 * Math.sin(Math.abs(lpphi)))
        out.x = FXC * lplam * y
        out.y = FYC * (2.0 - y)
        if (lpphi < 0.0) out.y = -out.y
        return out
    }

    override fun projectInverse(xyx: Double, xyy: Double, out: Point2D.Double): Point2D.Double {
        val y = 2.0 - Math.abs(xyy) / FYC
        out.x = xyx / (FXC * y)
        out.y = (4.0 - y * y) * C13
        if (Math.abs(out.y) >= 1.0) {
            if (Math.abs(out.y) > ONEEPS) throw ProjectionException("I")
            else
                out.y = if (out.y < 0.0) -MapMath.HALFPI else MapMath.HALFPI
        } else
            out.y = Math.asin(out.y)
        if (xyy < 0)
            out.y = -out.y
        return out
    }

    override fun hasInverse(): Boolean {
        return true
    }

    override fun toString(): String {
        return "Eckert II"
    }

}