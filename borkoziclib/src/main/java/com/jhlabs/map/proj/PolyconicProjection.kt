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
 * Bernhard Jenny, 19 September 2010: fixed spherical inverse.
 */
package com.jhlabs.map.proj

import com.jhlabs.Point2D
import com.jhlabs.map.MapMath
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

class PolyconicProjection : Projection() {
    private var ml0 = 0.0
    private var en: DoubleArray? = null

    init {
        minLatitude = MapMath.degToRad(0.0)
        maxLatitude = MapMath.degToRad(80.0)
        minLongitude = MapMath.degToRad(-60.0)
        maxLongitude = MapMath.degToRad(60.0)
    }

    override fun project(lplam: Double, lpphi: Double, out: Point2D.Double): Point2D.Double {
        if (spherical) {
            val cot: Double
            val E: Double

            if (abs(lpphi) <= TOL) {
                out.x = lplam
                out.y = ml0
            } else {
                cot = 1.0 / tan(lpphi)
                out.x = sin((lplam * sin(lpphi)).also { E = it }) * cot
                out.y = lpphi - projectionLatitude + cot * (1.0 - cos(E))
            }
        } else {
            val ms: Double
            val sp: Double
            val cp: Double

            if (abs(lpphi) <= TOL) {
                out.x = lplam
                out.y = -ml0
            } else {
                sp = sin(lpphi)
                ms = if (abs(cos(lpphi).also { cp = it }) > TOL) MapMath.msfn(
                    sp,
                    cp,
                    es
                ) / sp else 0.0
                out.x = ms * sin(sp.let { out.x *= it; out.x })
                out.y = (MapMath.mlfn(lpphi, sp, cp, en) - ml0) + ms * (1.0 - cos(lplam))
            }
        }
        return out
    }

    override fun projectInverse(xyx: Double, xyy: Double, out: Point2D.Double): Point2D.Double {
        var xyy = xyy
        var lpphi: Double
        if (spherical) {
            val B: Double
            var dphi: Double
            var tp: Double
            var i: Int

            if (abs((projectionLatitude + xyy).also { xyy = it }) <= TOL) {
                out.x = xyx
                out.y = 0.0
            } else {
                lpphi = xyy
                B = xyx * xyx + xyy * xyy
                i = N_ITER
                do {
                    tp = tan(lpphi)
                    lpphi -= (((xyy * (lpphi * tp + 1.0) - lpphi
                            - .5 * (lpphi * lpphi + B) * tp)
                            / ((lpphi - xyy) / tp - 1.0)).also { dphi = it })
                } while (abs(dphi) > CONV && --i > 0)
                if (i == 0) {
                    throw ProjectionException("I")
                }
                out.x = asin(xyx * tan(lpphi)) / sin(lpphi)
                out.y = lpphi
            }
        } else {
            xyy += ml0
            if (abs(xyy) <= TOL) {
                out.x = xyx
                out.y = 0.0
            } else {
                val r: Double
                var c: Double
                var sp: Double
                var cp: Double
                var s2ph: Double
                var ml: Double
                var mlb: Double
                var mlp: Double
                var dPhi: Double
                var i: Int

                r = xyy * xyy + xyx * xyx
                lpphi = xyy
                i = I_ITER
                while (i > 0) {
                    sp = sin(lpphi)
                    s2ph = sp * (cos(lpphi).also { cp = it })
                    if (abs(cp) < ITOL) {
                        throw ProjectionException("I")
                    }
                    c = sp * (sqrt(1.0 - es * sp * sp).also { mlp = it }) / cp
                    ml = MapMath.mlfn(lpphi, sp, cp, en)
                    mlb = ml * ml + r
                    mlp = (1.0 / es) / (mlp * mlp * mlp)
                    lpphi += ((
                            (ml + ml + c * mlb - 2.0 * xyy * (c * ml + 1.0)) / ((es * s2ph * (mlb - 2.0 * xyy * ml) / c
                                    + 2.0 * (xyy - ml) * (c * mlp - 1.0 / s2ph)) - mlp - mlp)).also {
                        dPhi = it
                    })
                    if (abs(dPhi) <= ITOL) {
                        break
                    }
                    --i
                }
                if (i == 0) {
                    throw ProjectionException("I")
                }
                c = sin(lpphi)
                out.x = asin(xyx * tan(lpphi) * sqrt(1.0 - es * c * c)) / sin(lpphi)
                out.y = lpphi
            }
        }
        return out
    }

    override fun hasInverse(): Boolean {
        return true
    }

    override fun initialize() {
        super.initialize()
        spherical = true //FIXME
        if (!spherical) {
            en = MapMath.enfn(es)
            if (en == null) {
                throw ProjectionException("E")
            }
            ml0 = MapMath.mlfn(
                projectionLatitude,
                sin(projectionLatitude),
                cos(projectionLatitude),
                en
            )
        } else {
            ml0 = -projectionLatitude
        }
    }

    override fun toString(): String {
        return "Polyconic (American)"
    }

    companion object {
        private const val TOL = 1e-10
        private const val CONV = 1e-10
        private const val N_ITER = 10
        private const val I_ITER = 20
        private const val ITOL = 1e-12
    }
}
