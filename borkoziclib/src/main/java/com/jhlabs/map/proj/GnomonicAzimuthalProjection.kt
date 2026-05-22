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
import kotlin.math.*

class GnomonicAzimuthalProjection : AzimuthalProjection {

    constructor() : this(Math.toRadians(90.0), Math.toRadians(0.0))

    constructor(projectionLatitude: Double, projectionLongitude: Double) : super(projectionLatitude, projectionLongitude) {
        minLatitude = Math.toRadians(0.0)
        maxLatitude = Math.toRadians(90.0)
        initialize()
    }

    override fun initialize() {
        super.initialize()
    }

    override fun project(lam: Double, phi: Double, xy: Point2D.Double): Point2D.Double {
        val sinphi = sin(phi)
        val cosphi = cos(phi)
        var coslam = cos(lam)

        when (mode) {
            AzimuthalProjection.EQUATOR -> xy.y = cosphi * coslam
            AzimuthalProjection.OBLIQUE -> xy.y = sinphi0 * sinphi + cosphi0 * cosphi * coslam
            AzimuthalProjection.SOUTH_POLE -> xy.y = -sinphi
            AzimuthalProjection.NORTH_POLE -> xy.y = sinphi
        }
        if (abs(xy.y) <= EPS10)
            throw ProjectionException()
        xy.y = 1.0 / xy.y
        xy.x = xy.y * cosphi * sin(lam)
        when (mode) {
            AzimuthalProjection.EQUATOR -> xy.y *= sinphi
            AzimuthalProjection.OBLIQUE -> xy.y *= cosphi0 * sinphi - sinphi0 * cosphi * coslam
            AzimuthalProjection.NORTH_POLE -> {
                coslam = -coslam
                xy.y *= cosphi * coslam
            }
            AzimuthalProjection.SOUTH_POLE -> xy.y *= cosphi * coslam
        }
        return xy
    }

    override fun projectInverse(x: Double, y: Double, lp: Point2D.Double): Point2D.Double {
        val rh = MapMath.distance(x, y)
        val sinz = sin(atan(rh))
        val cosz = sqrt(1.0 - sinz * sinz)
        if (abs(rh) <= EPS10) {
            lp.y = projectionLatitude
            lp.x = 0.0
        } else {
            var xVar = x
            var yVar = y
            when (mode) {
                AzimuthalProjection.OBLIQUE -> {
                    lp.y = cosz * sinphi0 + yVar * sinz * cosphi0 / rh
                    if (abs(lp.y) >= 1.0)
                        lp.y = if (lp.y > 0.0) MapMath.HALFPI else -MapMath.HALFPI
                    else
                        lp.y = asin(lp.y)
                    yVar = (cosz - sinphi0 * sin(lp.y)) * rh
                    xVar *= sinz * cosphi0
                }
                AzimuthalProjection.EQUATOR -> {
                    lp.y = yVar * sinz / rh
                    if (abs(lp.y) >= 1.0)
                        lp.y = if (lp.y > 0.0) MapMath.HALFPI else -MapMath.HALFPI
                    else
                        lp.y = asin(lp.y)
                    yVar = cosz * rh
                    xVar *= sinz
                }
                AzimuthalProjection.SOUTH_POLE -> lp.y -= MapMath.HALFPI
                AzimuthalProjection.NORTH_POLE -> {
                    lp.y = MapMath.HALFPI - lp.y
                    yVar = -yVar
                }
            }
            lp.x = atan2(xVar, yVar)
        }
        return lp
    }

    override fun hasInverse(): Boolean = true

    override fun toString(): String = "Gnomonic Azimuthal"
}
