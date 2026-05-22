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
 * Bernhard Jenny, May 25 2010:
 * Changed super class from Projection to PseudoCylindricalProjection.
 * Bernhard Jenny, 19 September 2010:
 * Fixed inverse projection.
 */
package com.jhlabs.map.proj

import com.jhlabs.Point2D

class FaheyProjection : PseudoCylindricalProjection() {

    companion object {
        private const val TOL = 1e-6
        private const val COEFFICIENT_1 = 1.819152
        private const val COEFFICIENT_2 = 0.819152
    }

    override fun project(lplam: Double, lpphi: Double, out: Point2D.Double): Point2D.Double {
        val tanValue = Math.tan(0.5 * lpphi)
        out.y = COEFFICIENT_1 * tanValue
        out.x = COEFFICIENT_2 * lplam * asqrt(1.0 - tanValue * tanValue)
        return out
    }

    override fun projectInverse(xyx: Double, xyy: Double, out: Point2D.Double): Point2D.Double {
        var yValue = xyy / COEFFICIENT_1
        out.y = 2.0 * Math.atan(yValue)
        val ySquared = 1.0 - yValue * yValue
        out.x = if (Math.abs(ySquared) < TOL) 0.0 else xyx / (COEFFICIENT_2 * Math.sqrt(ySquared))
        return out
    }

    private fun asqrt(v: Double): Double {
        return if (v <= 0) 0.0 else Math.sqrt(v)
    }

    override fun hasInverse(): Boolean {
        return true
    }

    override fun toString(): String {
        return "Fahey"
    }
}