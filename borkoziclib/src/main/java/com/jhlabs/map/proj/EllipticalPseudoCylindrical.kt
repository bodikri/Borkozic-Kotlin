/*
Copyright 2007 Bernhard Jenny

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

 * EllipticalPseudoCylindrical.kt
 *
 * Created on March 11, 2007, 11:21 PM
 *
 */

package com.jhlabs.map.proj

import com.jhlabs.Point2D

/**
 * Abstract base class for Eckert 3, Putnins P1, Wagner VI (Putnin P'1), and
 * Kavraisky VII. Based on Proj4.
 * @author Bernhard Jenny, Institute of Cartography, ETH Zurich
 */
abstract class EllipticalPseudoCylindrical(
    private val C_x: Double,
    private val C_y: Double,
    private val A: Double,
    private val B: Double
) : PseudoCylindricalProjection() {

    override fun project(x: Double, y: Double, dst: Point2D.Double): Point2D.Double {
        dst.y = C_y * y
        dst.x = C_x * x * (A + asqrt(1.0 - B * y * y))
        return dst
    }

    override fun hasInverse(): Boolean {
        return true
    }

    override fun projectInverse(x: Double, y: Double, dst: Point2D.Double): Point2D.Double {
        dst.y = y / C_y
        dst.x = x / (C_x * (A + asqrt(1.0 - B * y * y)))
        return dst
    }

    private fun asqrt(v: Double): Double {
        return if (v <= 0) 0.0 else Math.sqrt(v)
    }
}