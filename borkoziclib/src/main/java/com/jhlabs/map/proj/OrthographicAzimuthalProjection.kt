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
/**
 * Added initialization for minLongitude and maxLongitude.
 * Bernhard Jenny, 15 July 2010.
 */
package com.jhlabs.map.proj

import com.jhlabs.Point2D
import com.jhlabs.map.MapMath
import com.jhlabs.map.proj.ProjectionException

/**
 * The Orthographic Azimuthal or Globe map projection.
 */
class OrthographicAzimuthalProjection : AzimuthalProjection {

    constructor() {
        minLongitude = Math.toRadians(-90.0)
        maxLongitude = Math.toRadians(90.0)
        initialize()
    }

    override fun project(lam: Double, phi: Double, xy: Point2D.Double): Point2D.Double {
        val sinphi: Double
        val cosphi = Math.cos(phi)
        var coslam = Math.cos(lam)

        // Theoretically we should throw the ProjectionExceptions below, but for practical purposes
        // it's better not to as they tend to crop up a lot up due to rounding errors.
        when (mode) {
            EQUATOR -> {
                //			if (cosphi * coslam < - EPS10)
                //				throw new ProjectionException();
                xy.y = Math.sin(phi)
            }
            OBLIQUE -> {
                sinphi = Math.sin(phi)
                //			if (sinphi0 * (sinphi) + cosphi0 * cosphi * coslam < - EPS10)
                //				;
                //			   throw new ProjectionException();
                xy.y = cosphi0 * sinphi - sinphi0 * cosphi * coslam
            }
            NORTH_POLE -> {
                coslam = -coslam
            }
            SOUTH_POLE -> {
                //			if (Math.abs(phi - projectionLatitude) - EPS10 > MapMath.HALFPI)
                //				throw new ProjectionException();
                xy.y = cosphi * coslam
            }
        }
        xy.x = cosphi * Math.sin(lam)
        return xy
    }

    override fun projectInverse(x: Double, y: Double, lp: Point2D.Double): Point2D.Double {
        var x = x
        var y = y
        val rh = MapMath.distance(x, y)
        val cosc: Double
        val sinc: Double

        sinc = if (rh > 1.0) {
            if (rh - 1.0 > EPS10) {
                throw ProjectionException()
            }
            1.0
        } else {
            rh
        }
        cosc = Math.sqrt(1.0 - sinc * sinc) /* in this range OK */
        if (Math.abs(rh) <= EPS10) {
            lp.y = projectionLatitude
        } else {
            when (mode) {
                NORTH_POLE -> {
                    y = -y
                    lp.y = Math.acos(sinc)
                }
                SOUTH_POLE -> {
                    lp.y = -Math.acos(sinc)
                }
                EQUATOR -> {
                    lp.y = y * sinc / rh
                    x *= sinc
                    y = cosc * rh
                    if (Math.abs(lp.y) >= 1.0) {
                        lp.y = if (lp.y < 0) -MapMath.HALFPI else MapMath.HALFPI
                    } else {
                        lp.y = Math.asin(lp.y)
                    }
                }
                OBLIQUE -> {
                    lp.y = cosc * sinphi0 + y * sinc * cosphi0 / rh
                    y = (cosc - sinphi0 * lp.y) * rh
                    x *= sinc * cosphi0
                    if (Math.abs(lp.y) >= 1.0) {
                        lp.y = if (lp.y < 0.0) -MapMath.HALFPI else MapMath.HALFPI
                    } else {
                        lp.y = Math.asin(lp.y)
                    }
                }
            }
        }
        lp.x = if (y == 0.0 && (mode == OBLIQUE || mode == EQUATOR)) {
            if (x == 0.0) 0.0 else if (x < 0.0) -MapMath.HALFPI else MapMath.HALFPI
        } else {
            Math.atan2(x, y)
        }
        return lp
    }

    override fun hasInverse(): Boolean {
        return true
    }

    override fun toString(): String {
        return "Orthographic Azimuthal"
    }
}