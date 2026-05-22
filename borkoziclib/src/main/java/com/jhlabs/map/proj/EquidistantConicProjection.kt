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

/**
 * This file was semi-automatically converted from the public-domain USGS PROJ source.
 *
 * Bernhard Jenny, 16 September 2010:
 * Added project and projectInverse, commented out transform and transformInverse
 * and related variables (which are not functional). Only the spherical case is
 * currently supported.
 */
package com.jhlabs.map.proj

import com.jhlabs.Point2D
import com.jhlabs.map.MapMath

/**
 * The Equidistant Conic projection.
 */
class EquidistantConicProjection : ConicProjection() {

    /**
     * pre-computed values derived from projectionLatitude, projectionLatitude1
     * and projectionLatitude2.
     */
    private var rho0: Double = 0.0
    private var c: Double = 0.0
    private var n: Double = 0.0

    init {
        projectionLatitude = Math.toRadians(45.0)
        projectionLatitude1 = Math.toRadians(35.0)
        projectionLatitude2 = Math.toRadians(60.0)
    }

    override fun project(lplam: Double, lpphi: Double, out: Point2D.Double): Point2D.Double {
        val rho = c - lpphi
        val tempLplam = lplam * n
        out.x = rho * Math.sin(tempLplam)
        out.y = rho0 - rho * Math.cos(tempLplam)
        return out
    }

    override fun projectInverse(x: Double, y: Double, dst: Point2D.Double): Point2D.Double {
        val tempY = rho0 - y
        var rho = Math.hypot(x, tempY)
        if (rho > 0) {
            if (n < 0) {
                rho = -rho
                val tempX = -x
                val newX = tempX
                val newY = -tempY
                dst.x = newX
                dst.y = newY
            }
            dst.y = c - rho
            //if (P->ellips)
            //	lp.phi = proj_inv_mdist(lp.phi, P->en);
            dst.x = Math.atan2(x, tempY) / n
        } else {
            dst.x = 0.0
            dst.y = if (n > 0) MapMath.HALFPI else -MapMath.HALFPI
        }
        return dst
    }

    override fun initialize() {
        super.initialize()

        if (Math.abs(projectionLatitude1 + projectionLatitude2) < EPS10) {
            throw ProjectionException("-21")
        }
        val sinphi = Math.sin(projectionLatitude1)
        val cosphi = Math.cos(projectionLatitude1)
        val secant = Math.abs(projectionLatitude1 - projectionLatitude2) >= EPS10

        n = if (secant) {
            (cosphi - Math.cos(projectionLatitude2)) / (projectionLatitude2 - projectionLatitude1)
        } else {
            sinphi
        }
        c = projectionLatitude1 + Math.cos(projectionLatitude1) / n
        rho0 = c - projectionLatitude /*phi0*/
    }

    override fun hasInverse(): Boolean {
        return true
    }

    override fun toString(): String {
        return "Equidistant Conic"
    }
}