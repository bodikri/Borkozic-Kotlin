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

class TCCProjection : CylindricalProjection() {

    init {
        minLongitude = MapMath.degToRad(-60.0)
        maxLongitude = MapMath.degToRad(60.0)
    }

    override fun project(lplam: Double, lpphi: Double, out: Point2D.Double): Point2D.Double {
        val b: Double
        val bt: Double

        b = Math.cos(lpphi) * Math.sin(lplam)
        bt = 1.0 - b * b
        if (bt < EPS10) {
            throw ProjectionException("F")
        }
        out.x = b / Math.sqrt(bt)
        out.y = Math.atan2(Math.tan(lpphi), Math.cos(lplam))
        return out
    }

    override fun isRectilinear(): Boolean {
        return false
    }

    override fun toString(): String {
        return "Transverse Central Cylindrical"
    }
}