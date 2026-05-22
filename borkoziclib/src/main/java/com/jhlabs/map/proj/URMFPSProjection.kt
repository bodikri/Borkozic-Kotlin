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
 * With the default parameter n, this is identical to Wagner I.
 * 
 * Bernhard Jenny, 19 September 2010: fixed forward projection.
 * 23 September 2010: fixed type in toString, changed super class to
 * PseudoCylindricalProjection.
 */
package com.jhlabs.map.proj

import com.jhlabs.Point2D
import com.jhlabs.map.MapMath
import com.jhlabs.map.proj.ProjectionException

open class URMFPSProjection : PseudoCylindricalProjection() {

    companion object {
        private const val C_x = 0.8773826753
        private const val Cy = 1.139753528477
    }

    private var n = 0.8660254037844386467637231707 // wag1
    private var C_y: Double = 0.0

    override fun project(lplam: Double, lpphi: Double, out: Point2D.Double): Point2D.Double {
        val adjustedPhi = MapMath.asin(n * Math.sin(lpphi))
        out.x = C_x * lplam * Math.cos(adjustedPhi)
        out.y = C_y * adjustedPhi
        return out
    }

    override fun projectInverse(xyx: Double, xyy: Double, out: Point2D.Double): Point2D.Double {
        val yAdjusted = xyy / C_y
        out.y = MapMath.asin(Math.sin(yAdjusted) / n)
        out.x = xyx / (C_x * Math.cos(yAdjusted))
        return out
    }

    override fun hasInverse(): Boolean {
        return true
    }

    override fun initialize() { // urmfps
        super.initialize()
        if (n <= 0.0 || n > 1.0) {
            throw ProjectionException("-40")
        }
        C_y = Cy / n
    }

    // Properties
    fun setN(n: Double) {
        this.n = n
    }

    fun getN(): Double {
        return n
    }

    override fun toString(): String {
        return "Urmayev Flat-Polar Sinusoidal"
    }
}