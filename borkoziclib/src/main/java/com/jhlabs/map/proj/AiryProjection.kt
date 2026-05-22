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
 * Bernhard Jenny, 23 September 2010: changed base class to AzimuthalProjection.
 */
package com.jhlabs.map.proj

import com.jhlabs.Point2D
import com.jhlabs.map.MapMath

class AiryProjection : AzimuthalProjection() {

    private var p_halfpi: Double = 0.0
    private var sinph0: Double = 0.0
    private var cosph0: Double = 0.0
    private var Cb: Double = 0.0
    private var airyMode: Int = 0
    private var no_cut: Boolean = true   /* do not cut at hemisphere limit */

    init {
        minLatitude = Math.toRadians(-60.0)
        maxLatitude = Math.toRadians(60.0)
        minLongitude = Math.toRadians(-90.0)
        maxLongitude = Math.toRadians(90.0)
        initialize()
    }

    override fun project(lplam: Double, lpphi: Double, out: Point2D.Double): Point2D.Double {
        val sinlam: Double
        val coslam: Double
        val cosphi: Double
        val sinphi: Double
        var t: Double
        var s: Double
        var Krho: Double
        var cosz: Double

        sinlam = Math.sin(lplam)
        coslam = Math.cos(lplam)
        when (airyMode) {
            EQUIT, OBLIQ -> {
                sinphi = Math.sin(lpphi)
                cosphi = Math.cos(lpphi)
                cosz = cosphi * coslam
                if (airyMode == OBLIQ)
                    cosz = sinph0 * sinphi + cosph0 * cosz
                if (!no_cut && cosz < -EPS)
                    throw ProjectionException("F")
                s = 1.0 - cosz
                if (Math.abs(s) > EPS) {
                    t = 0.5 * (1.0 + cosz)
                    Krho = -Math.log(t) / s - Cb / t
                } else
                    Krho = 0.5 - Cb
                out.x = Krho * cosphi * sinlam
                if (airyMode == OBLIQ)
                    out.y = Krho * (cosph0 * sinphi -
                            sinph0 * cosphi * coslam)
                else
                    out.y = Krho * sinphi
            }
            S_POLE, N_POLE -> {
                out.y = Math.abs(p_halfpi - lpphi)
                if (!no_cut && (lpphi - EPS) > MapMath.HALFPI)
                    throw ProjectionException("F")
                out.y *= 0.5
                if (out.y > EPS) {
                    t = Math.tan(lpphi)
                    Krho = -2.0 * (Math.log(Math.cos(lpphi)) / t + t * Cb)
                    out.x = Krho * sinlam
                    out.y = Krho * coslam
                    if (airyMode == N_POLE)
                        out.y = -out.y
                } else
                    out.x = 0.0.also { out.y = 0.0 }
            }
        }
        return out
    }

    override fun initialize() { // airy
        super.initialize()

//      no_cut = pj_param(params, "bno_cut").i;
//      beta = 0.5 * (MapMath.HALFPI - pj_param(params, "rlat_b").f);
        no_cut = false//FIXME
        val beta = 0.5 * (MapMath.HALFPI - 0)//FIXME
        if (Math.abs(beta) < EPS)
            Cb = -0.5
        else {
            Cb = 1.0 / Math.tan(beta)
            Cb *= Cb * Math.log(Math.cos(beta))
        }
        if (Math.abs(Math.abs(projectionLatitude) - MapMath.HALFPI) < EPS)
            if (projectionLatitude < 0.0) {
                p_halfpi = -MapMath.HALFPI
                airyMode = S_POLE
            } else {
                p_halfpi = MapMath.HALFPI
                airyMode = N_POLE
            }
        else {
            if (Math.abs(projectionLatitude) < EPS)
                airyMode = EQUIT
            else {
                airyMode = OBLIQ
                sinph0 = Math.sin(projectionLatitude)
                cosph0 = Math.cos(projectionLatitude)
            }
        }
    }

    override fun toString(): String {
        return "Airy"
    }

    companion object {
        private const val EPS = 1.0e-10
        private const val N_POLE = 0
        private const val S_POLE = 1
        private const val EQUIT = 2
        private const val OBLIQ = 3
    }
}
