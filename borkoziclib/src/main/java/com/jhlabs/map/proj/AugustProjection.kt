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
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

class AugustProjection : Projection() {

    companion object {
        private const val M = 1.333333333333333
    }

    override fun project(lplam: Double, lpphi: Double, out: Point2D.Double): Point2D.Double {
        val t = tan(.5 * lpphi)
        val c1 = sqrt(1.0 - t * t)
        val lam = lplam * .5
        val c = 1.0 + c1 * cos(lam)
        val x1 = sin(lam) * c1 / c
        val y1 = t / c
        val x12 = x1 * x1
        val y12 = y1 * y1
        out.x = M * x1 * (3.0 + x12 - 3.0 * y12)
        out.y = M * y1 * (3.0 + 3.0 * x12 - y12)
        return out
    }

    /**
     * Returns true if this projection is conformal
     */
    override fun isConformal(): Boolean = true

    override fun hasInverse(): Boolean = false

    override fun toString(): String = "August Epicycloidal"
}
