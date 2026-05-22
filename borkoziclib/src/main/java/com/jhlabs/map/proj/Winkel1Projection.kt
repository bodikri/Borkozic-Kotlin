/*
 * Winkel1Projection.kt
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
package com.jhlabs.map.proj

import com.jhlabs.Point2D
import com.jhlabs.map.MapMath
import java.lang.Math.*

/**
 * Ported from Proj4 by Bernhard Jenny, Institute of Cartography, ETH Zurich.
 */
class Winkel1Projection : PseudoCylindricalProjection() {

    /**
     * latitude of true scale on central meridian.
     * Default is 50 degree and 28 minutes. This differs from the default value
     * of proj4, which uses 0 degrees. 0 degrees is not appropriate,
     * as in this case the Winkel I projection is equal to the Eckert V projection.
     */
    private var phi1 = toRadians(50.0 + 28.0 / 60.0)
    
    /**
     * cosine of latitude of true scale on central meridian
     */
    private var cosphi1 = cos(phi1)

    fun setLatitudeOfTrueScale(phi1: Double) {
        if (phi1 < -MapMath.HALFPI || phi1 > MapMath.HALFPI) {
            throw ProjectionException()
        }
        this.phi1 = phi1
        this.cosphi1 = cos(this.phi1)
    }

    override fun project(lplam: Double, lpphi: Double, out: Point2D.Double): Point2D.Double {
        out.x = 0.5 * lplam * (cosphi1 + cos(lpphi))
        out.y = lpphi
        return out
    }

    override fun projectInverse(xyx: Double, xyy: Double, out: Point2D.Double): Point2D.Double {
        out.y = xyy
        out.x = 2.0 * xyx / (this.cosphi1 + cos(xyy))
        return out
    }

    override fun hasInverse(): Boolean {
        return true
    }

    override fun toString(): String {
        return "Winkel I"
    }
}