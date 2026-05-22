/*
Copyright 2010 Bernhard Jenny

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

class Eckert6Projection : PseudoCylindricalProjection() {

    companion object {
        private const val n = 2.570796326794896619231321691
        private val C_y = Math.sqrt((2.0) / n)
        private val C_x = C_y / 2.0
        private const val MAX_ITER = 8
        private const val LOOP_TOL = 1e-7
    }

    override fun project(lam: Double, phi: Double, xy: Point2D.Double): Point2D.Double {

        var phiVar = phi
        var i: Int
        var k: Double
        var V: Double
        k = n * Math.sin(phiVar)
        i = MAX_ITER
        while (i > 0) {
            V = (phiVar + Math.sin(phiVar) - k) / (1.0 + Math.cos(phiVar))
            phiVar -= V
            if (Math.abs(V) < LOOP_TOL) {
                break
            }
            --i
        }
        if (i == 0) {
            throw ProjectionException("F_ERROR")
        }

        xy.x = C_x * lam * (1.0 + Math.cos(phiVar))
        xy.y = C_y * phiVar
        return xy
    }

    override fun projectInverse(x: Double, y: Double, lp: Point2D.Double): Point2D.Double {
        val yVar = y / C_y
        lp.y = MapMath.asin((yVar + Math.sin(yVar)) / n)
        lp.x = x / (C_x * (1.0 + Math.cos(yVar)))
        return lp
    }

    override fun hasInverse(): Boolean {
        return true
    }

    override fun isEqualArea(): Boolean {
        return true
    }

    override fun toString(): String {
        return "Eckert VI"
    }
}