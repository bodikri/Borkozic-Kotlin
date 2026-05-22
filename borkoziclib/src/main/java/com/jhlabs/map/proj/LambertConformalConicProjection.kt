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

package com.jhlabs.map.proj

import com.jhlabs.Point2D
import com.jhlabs.map.Ellipsoid
import com.jhlabs.map.MapMath
import kotlin.math.*

class LambertConformalConicProjection : ConicProjection {

    private var n: Double = 0.0
    private var rho0: Double = 0.0
    private var c: Double = 0.0

    constructor() : super() {
        minLatitude = Math.toRadians(0.0)
        maxLatitude = Math.toRadians(80.0)
        projectionLatitude = MapMath.QUARTERPI
        projectionLatitude1 = 0.0
        projectionLatitude2 = 0.0
    }

    /**
     * Set up a projection suitable for State Place Coordinates.
     */
    constructor(
        ellipsoid: Ellipsoid,
        lon0: Double,
        lat1: Double,
        lat2: Double,
        lat0: Double,
        x0: Double,
        y0: Double
    ) : super() {
        setEllipsoid(ellipsoid)
        projectionLongitude = lon0
        projectionLatitude = lat0
        scaleFactor = 1.0
        falseEasting = x0
        falseNorthing = y0
        projectionLatitude1 = lat1
        projectionLatitude2 = lat2
    }

    override fun project(x: Double, y: Double, out: Point2D.Double): Point2D.Double {
        val rho = if (abs(abs(y) - MapMath.HALFPI) < 1e-10)
            0.0
        else
            c * (if (spherical)
                tan(MapMath.QUARTERPI + 0.5 * y).pow(-n)
            else
                MapMath.tsfn(y, sin(y), e).pow(n))
        var xVar = x * n
        out.x = scaleFactor * (rho * sin(xVar))
        out.y = scaleFactor * (rho0 - rho * cos(xVar))
        return out
    }

    override fun projectInverse(x: Double, y: Double, out: Point2D.Double): Point2D.Double {
        var xVar = x / scaleFactor
        var yVar = y / scaleFactor
        yVar = rho0 - yVar
        var rho = MapMath.distance(xVar, yVar)
        if (rho != 0.0) {
            if (n < 0.0) {
                rho = -rho
                xVar = -xVar
                yVar = -yVar
            }
            if (spherical)
                out.y = 2.0 * atan((c / rho).pow(1.0 / n)) - MapMath.HALFPI
            else
                out.y = MapMath.phi2((rho / c).pow(1.0 / n), e)
            out.x = atan2(xVar, yVar) / n
        } else {
            out.x = 0.0
            out.y = if (n > 0.0) MapMath.HALFPI else -MapMath.HALFPI
        }
        return out
    }

    override fun initialize() {
        super.initialize()
        var cosphi: Double
        var sinphi: Double

        if (projectionLatitude1 == 0.0)
            projectionLatitude1 = projectionLatitude2

        if (abs(projectionLatitude1 + projectionLatitude2) < 1e-10)
            throw ProjectionException()
        n = sin(projectionLatitude1)
        sinphi = n
        cosphi = cos(projectionLatitude1)
        val secant = abs(projectionLatitude1 - projectionLatitude2) >= 1e-10
        spherical = (es == 0.0)
        if (!spherical) {
            val m1 = MapMath.msfn(sinphi, cosphi, es)
            val ml1 = MapMath.tsfn(projectionLatitude1, sinphi, e)
            if (secant) {
                n = ln(m1 /
                    MapMath.msfn(sin(projectionLatitude2).also { sinphi = it }, cos(projectionLatitude2), es))
                n /= ln(ml1 / MapMath.tsfn(projectionLatitude2, sinphi, e))
            }
            c = m1 * ml1.pow(-n) / n
            rho0 = c
            rho0 *= if (abs(abs(projectionLatitude) - MapMath.HALFPI) < 1e-10) 0.0
            else MapMath.tsfn(projectionLatitude, sin(projectionLatitude), e).pow(n)
        } else {
            if (secant)
                n = ln(cosphi / cos(projectionLatitude2)) /
                    ln(tan(MapMath.QUARTERPI + 0.5 * projectionLatitude2) /
                    tan(MapMath.QUARTERPI + 0.5 * projectionLatitude1))
            c = cosphi * tan(MapMath.QUARTERPI + 0.5 * projectionLatitude1).pow(n) / n
            rho0 = if (abs(abs(projectionLatitude) - MapMath.HALFPI) < 1e-10) 0.0
            else c * tan(MapMath.QUARTERPI + 0.5 * projectionLatitude).pow(-n)
        }
    }

    /**
     * Returns true if this projection is conformal
     */
    override fun isConformal(): Boolean = true

    override fun hasInverse(): Boolean = true

    override fun toString(): String = "Lambert Conformal Conic"
}
