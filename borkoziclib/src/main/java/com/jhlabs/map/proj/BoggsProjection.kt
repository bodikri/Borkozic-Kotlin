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

class BoggsProjection : PseudoCylindricalProjection() {

    override fun project(lplam: Double, lpphi: Double, out: Point2D.Double): Point2D.Double {
        var theta = lpphi

        if (Math.abs(Math.abs(lpphi) - MapMath.HALFPI) < EPS)
            out.x = 0.0
        else {
            val c = Math.sin(theta) * Math.PI
            for (i in 0 until NITER) {
                val th1 = (theta + Math.sin(theta) - c) /
                        (1.0 + Math.cos(theta))
                theta -= th1
                if (Math.abs(th1) < EPS) break
            }
            theta *= 0.5
            out.x = FXC * lplam / (1.0 / Math.cos(lpphi) + FXC2 / Math.cos(theta))
        }
        out.y = FYC * (lpphi + FYC2 * Math.sin(theta))
        return out
    }

    /**
     * Returns true if this projection is equal area
     */
    override fun isEqualArea(): Boolean {
        return true
    }

    override fun hasInverse(): Boolean {
        return false
    }

    override fun toString(): String {
        return "Boggs Eumorphic"
    }

    companion object {
        private const val NITER = 20
        private const val EPS = 1e-7
        private const val ONETOL = 1.000001
        private const val FXC = 2.00276
        private const val FXC2 = 1.11072
        private const val FYC = 0.49931
        private const val FYC2 = 1.41421356237309504880
    }
}
