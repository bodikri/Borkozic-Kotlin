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

class BaconProjection : Projection() {

    init {
        minLongitude = Math.toRadians(-90.0)
        maxLongitude = Math.toRadians(90.0)
        initialize()
    }

    override fun project(lplam: Double, lpphi: Double, out: Point2D.Double): Point2D.Double {
        out.y = MapMath.HALFPI * Math.sin(lpphi)
        val ax = Math.abs(lplam)
        if (ax >= EPS) {
            val f = 0.5 * (HLFPI2 / ax + ax)
            out.x = ax - f + Math.sqrt(f * f - out.y * out.y)
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

    override fun hasInverse(): Boolean {
        return true
    }

    override fun toString(): String {
        return "Bacon Globular"
    }

    companion object {
        private const val HLFPI2 = 2.46740110027233965467
        private const val EPS = 1e-10
    }
}
