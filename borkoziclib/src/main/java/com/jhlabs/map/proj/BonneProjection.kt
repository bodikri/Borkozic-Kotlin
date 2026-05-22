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
 * Bernhard Jenny, 19 September 2010: fixed inverse spherical.
 * 23. September 2010: Change super class to ConicProjection.
 */
package com.jhlabs.map.proj

import com.jhlabs.Point2D
import com.jhlabs.map.MapMath
import kotlin.math.*

class BonneProjection : ConicProjection() {

    private var phi1: Double = 0.0
    private var cphi1: Double = 0.0
    private var am1: Double = 0.0
    private var m1: Double = 0.0
    private var en: DoubleArray? = null

    override fun project(lplam: Double, lpphi: Double, out: Point2D.Double): Point2D.Double {
        if (spherical) {
            val rh = cphi1 + phi1 - lpphi
            if (abs(rh) > EPS10) {
                val E = lplam * cos(lpphi) / rh
                out.x = rh * sin(E)
                out.y = cphi1 - rh * cos(E)
            } else {
                out.x = 0.0
                out.y = 0.0
            }
        } else {
            val sphi = sin(lpphi)
            val cphi = cos(lpphi)
            val rh = am1 + m1 - MapMath.mlfn(lpphi, sphi, cphi, en)
            val E = cphi * lplam / (rh * sqrt(1.0 - es * sphi * sphi))
            out.x = rh * sin(E)
            out.y = am1 - rh * cos(E)
        }
        return out
    }

    override fun projectInverse(xyx: Double, xyy: Double, out: Point2D.Double): Point2D.Double {
        if (spherical) {
            val y = cphi1 - xyy
            val rh = MapMath.distance(xyx, y)
            out.y = cphi1 + phi1 - rh
            if (abs(out.y) > MapMath.HALFPI) {
                throw ProjectionException("I")
            }
            if (abs(abs(out.y) - MapMath.HALFPI) <= EPS10) {
                out.x = 0.0
            } else {
                out.x = rh * atan2(xyx, y) / cos(out.y)
            }
        } else {
            val y = am1 - xyy
            val rh = MapMath.distance(xyx, y)
            out.y = MapMath.inv_mlfn(am1 + m1 - rh, es, en)
            val s = abs(out.y)
            if (s < MapMath.HALFPI) {
                out.x = rh * atan2(xyx, y) *
                        sqrt(1.0 - es * sin(out.y) * sin(out.y)) / cos(out.y)
            } else if (abs(s - MapMath.HALFPI) <= EPS10) {
                out.x = 0.0
            } else {
                throw ProjectionException("I")
            }
        }
        return out
    }

    override fun isEqualArea(): Boolean = true

    override fun hasInverse(): Boolean = true

    override fun initialize() {
        super.initialize()

        //phi1 = pj_param(params, "rlat_1").f;
        phi1 = MapMath.HALFPI
        if (abs(phi1) < EPS10) {
            throw ProjectionException("-23")
        }
        if (!spherical) {
            en = MapMath.enfn(es)
            val sphi1 = sin(phi1)
            val cosPhi1 = cos(phi1)
            m1 = MapMath.mlfn(phi1, sphi1, cosPhi1, en)
            am1 = cosPhi1 / (sqrt(1.0 - es * sphi1 * sphi1) * sphi1)
        } else {
            cphi1 = if (abs(phi1) + EPS10 >= MapMath.HALFPI) 0.0 else 1.0 / tan(phi1)
        }
    }

    override fun toString(): String = "Bonne"
}
