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
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Apian I Globular projection.
 * Code from proj4.
 * @author Bernhard Jenny, Institute of Cartography, ETH Zurich
 */
class Apian1Projection : Projection() {

    companion object {
        private const val HLFPI2 = 2.46740110027233965467
        private const val EPS = 1e-10
    }

    override fun project(lplam: Double, lpphi: Double, out: Point2D.Double): Point2D.Double {
        val ax = abs(lplam)
        out.y = lpphi
        if (ax >= EPS) {
            val f = 0.5 * (HLFPI2 / ax + ax)
            out.x = ax - f + sqrt(f * f - lpphi * lpphi)
            if (lplam < 0.0) {
                out.x = -out.x
            }
        } else {
            out.x = 0.0
        }
        return out
    }

    override fun projectInverse(x: Double, y: Double, out: Point2D.Double): Point2D.Double {
        binarySearchInverse(x, y, out)
        return out
    }

    override fun hasInverse(): Boolean = true

    override fun toString(): String = "Apian Globular I"
}
