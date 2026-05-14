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
 * Bernhard Jenny, 19 September 2010: fixed forward projection.
 * 23 September 2010: changed super class to PseudoCylindricalProjection.
 */
package com.jhlabs.map.proj

import com.jhlabs.Point2D
import com.jhlabs.map.MapMath
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class PutninsP2Projection : PseudoCylindricalProjection() {
    public override fun project(lplam: Double, lpphi: Double, out: Point2D.Double): Point2D.Double {
        var lpphi = lpphi
        val p: Double
        var c: Double
        var s: Double
        var V: Double
        var i: Int

        p = C_p * sin(lpphi)
        s = lpphi * lpphi
        lpphi *= 0.615709 + s * (0.00909953 + s * 0.0046292)
        i = NITER
        while (i > 0) {
            c = cos(lpphi)
            s = sin(lpphi)
            V = ((lpphi + s * (c - 1.0) - p)
                    / (1.0 + c * (c - 1.0) - s * s))
            lpphi -= V
            if (abs(V) < EPS) {
                break
            }
            --i
        }
        if (i == 0) {
            lpphi = if (lpphi < 0) -PI_DIV_3 else PI_DIV_3
        }
        out.x = C_x * lplam * (cos(lpphi) - 0.5)
        out.y = C_y * sin(lpphi)
        return out
    }

    public override fun projectInverse(
        xyx: Double,
        xyy: Double,
        out: Point2D.Double
    ): Point2D.Double {
        val c: Double

        out.y = MapMath.asin(xyy / C_y)
        out.x = xyx / (C_x * ((cos(out.y).also { c = it }) - 0.5))
        out.y = MapMath.asin((out.y + sin(out.y) * (c - 1.0)) / C_p)
        return out
    }

    public override fun hasInverse(): Boolean {
        return true
    }

    public override fun toString(): String {
        return "Putnins P2"
    }

    companion object {
        private const val C_x = 1.89490
        private const val C_y = 1.71848
        private const val C_p = 0.6141848493043784
        private const val EPS = 1e-10
        private const val NITER = 10
        private const val PI_DIV_3 = 1.0471975511965977
    }
}
