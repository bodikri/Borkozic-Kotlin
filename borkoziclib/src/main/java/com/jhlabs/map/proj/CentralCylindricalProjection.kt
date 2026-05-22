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

class CentralCylindricalProjection : CylindricalProjection() {

    private var ap: Double = 0.0

    companion object {
        private const val EPS10 = 1e-10
    }

    init {
        minLatitude = Math.toRadians(-80.0)
        maxLatitude = Math.toRadians(80.0)
    }

    override fun project(lplam: Double, lpphi: Double, out: Point2D.Double): Point2D.Double {
        if (Math.abs(Math.abs(lpphi) - MapMath.HALFPI) <= EPS10) throw ProjectionException("F")
        out.x = lplam
        out.y = Math.tan(lpphi)
        return out
    }

    override fun projectInverse(xyx: Double, xyy: Double, out: Point2D.Double): Point2D.Double {
        out.y = Math.atan(xyy)
        out.x = xyx
        return out
    }

    override fun hasInverse(): Boolean {
        return true
    }

    override fun toString(): String {
        return "Central Cylindrical"
    }
}