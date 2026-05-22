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
 */

/**
 * Modified by Bernhard Jenny: added setter for parameter n,
 * added missing initialization of n to 0.5, as is done by proj4,
 * added missing isEqualArea, change superclass from Projection to
 * PseudocylindricalProjection.
 */

package com.jhlabs.map.proj

import com.jhlabs.Point2D
import com.jhlabs.map.MapMath

class FoucautSinusoidalProjection : PseudoCylindricalProjection() {

    private var n = 0.5
    private var n1: Double = 0.0
    private val MAX_ITER = 10
    private val LOOP_TOL = 1e-7

    override fun project(lplam: Double, lpphi: Double, out: Point2D.Double): Point2D.Double {
        val t: Double = Math.cos(lpphi)
        out.x = lplam * t / (n + n1 * t)
        out.y = n * lpphi + n1 * Math.sin(lpphi)
        return out
    }

    override fun projectInverse(xyx: Double, xyy: Double, out: Point2D.Double): Point2D.Double {
        var V: Double
        var i: Int

        if (n != 0.0) {
            out.y = xyy
            i = MAX_ITER
            while (i > 0) {
                V = (n * out.y + n1 * Math.sin(out.y) - xyy) / (n + n1 * Math.cos(out.y))
                out.y -= V
                if (Math.abs(V) < LOOP_TOL) {
                    break
                }
                i--
            }
            if (i == 0) {
                out.y = if (xyy < 0.0) -MapMath.HALFPI else MapMath.HALFPI
            }
        } else {
            out.y = MapMath.asin(xyy)
        }
        V = Math.cos(out.y)
        out.x = xyx * (n + n1 * V) / V
        return out
    }

    override fun initialize() {
        super.initialize()
        // n = pj_param(params, "dn").f;
        if (n < 0.0 || n > 1.0) {
            throw ProjectionException("-99")
        }
        n1 = 1.0 - n
    }

    override fun hasInverse(): Boolean {
        return true
    }

    override fun isEqualArea(): Boolean {
        return true
    }

    override fun toString(): String {
        return "Foucaut Sinusoidal"
    }

    fun getN(): Double {
        return n
    }

    fun setN(n: Double) {
        if (n < 0 || n > 1) {
            throw IllegalArgumentException()
        }
        this.n = n
        this.n1 = 1.0 - n
    }
}