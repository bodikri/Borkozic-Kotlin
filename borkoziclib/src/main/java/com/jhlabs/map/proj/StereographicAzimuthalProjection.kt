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
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

class StereographicAzimuthalProjection @JvmOverloads constructor(
    projectionLatitude: Double = Math.toRadians(
        90.0
    ), projectionLongitude: Double = Math.toRadians(0.0)
) : AzimuthalProjection(projectionLatitude, projectionLongitude) {
    private var akm1 = 0.0

    init {
        initialize()
    }

    fun setupUPS(pole: Int) {
        projectionLatitude = if (pole == SOUTH_POLE) -MapMath.HALFPI else MapMath.HALFPI
        projectionLongitude = 0.0
        scaleFactor = 0.994
        falseEasting = 2000000.0
        falseNorthing = 2000000.0
        trueScaleLatitude = MapMath.HALFPI
        initialize()
    }

    override fun initialize() {
        var t: Double

        super.initialize()
        if (abs((abs(projectionLatitude).also { t = it }) - MapMath.HALFPI) < EPS10) mode =
            if (projectionLatitude < 0.0) SOUTH_POLE else NORTH_POLE
        else mode = if (t > EPS10) OBLIQUE else EQUATOR
        trueScaleLatitude = abs(trueScaleLatitude)
        if (spherical) {
            val X: Double

            when (mode) {
                NORTH_POLE, SOUTH_POLE -> if (abs(trueScaleLatitude - MapMath.HALFPI) < EPS10) akm1 =
                    2.0 * scaleFactor / sqrt((1 + e).pow(1 + e) * (1 - e).pow(1 - e))
                else {
                    akm1 = cos(trueScaleLatitude) /
                            MapMath.tsfn(
                                trueScaleLatitude,
                                sin(trueScaleLatitude).also { t = it },
                                e
                            )
                    t *= e
                    akm1 /= sqrt(1.0 - t * t)
                }

                EQUATOR -> akm1 = 2.0 * scaleFactor
                OBLIQUE -> {
                    t = sin(projectionLatitude)
                    X = 2.0 * atan(ssfn(projectionLatitude, t, e)) - MapMath.HALFPI
                    t *= e
                    akm1 = 2.0 * scaleFactor * cos(projectionLatitude) / sqrt(1.0 - t * t)
                    sinphi0 = sin(X)
                    cosphi0 = cos(X)
                }
            }
        } else {
            when (mode) {
                OBLIQUE -> {
                    sinphi0 = sin(projectionLatitude)
                    cosphi0 = cos(projectionLatitude)
                    akm1 = 2.0 * scaleFactor
                }

                EQUATOR -> akm1 = 2.0 * scaleFactor
                SOUTH_POLE, NORTH_POLE -> akm1 =
                    if (abs(trueScaleLatitude - MapMath.HALFPI) >= EPS10) cos(trueScaleLatitude) / tan(
                        MapMath.QUARTERPI - .5 * trueScaleLatitude
                    ) else 2.0 * scaleFactor
            }
        }
    }

    public override fun project(lam: Double, phi: Double, xy: Point2D.Double): Point2D.Double {
        var phi = phi
        var coslam = cos(lam)
        val sinlam = sin(lam)
        var sinphi = sin(phi)

        if (spherical) {
            val cosphi = cos(phi)

            when (mode) {
                EQUATOR -> {
                    xy.y = 1.0 + cosphi * coslam
                    if (xy.y <= EPS10) throw ProjectionException()
                    xy.x = ((akm1 / xy.y).also { xy.y = it }) * cosphi * sinlam
                    xy.y *= sinphi
                }

                OBLIQUE -> {
                    xy.y = 1.0 + sinphi0 * sinphi + cosphi0 * cosphi * coslam
                    if (xy.y <= EPS10) throw ProjectionException()
                    xy.x = ((akm1 / xy.y).also { xy.y = it }) * cosphi * sinlam
                    xy.y *= cosphi0 * sinphi - sinphi0 * cosphi * coslam
                }

                NORTH_POLE -> {
                    coslam = -coslam
                    phi = -phi
                    if (abs(phi - MapMath.HALFPI) < TOL) throw ProjectionException()
                    xy.x = sinlam * ((akm1 * tan(MapMath.QUARTERPI + .5 * phi)).also { xy.y = it })
                    xy.y *= coslam
                }

                SOUTH_POLE -> {
                    if (abs(phi - MapMath.HALFPI) < TOL) throw ProjectionException()
                    xy.x = sinlam * ((akm1 * tan(MapMath.QUARTERPI + .5 * phi)).also { xy.y = it })
                    xy.y *= coslam
                }
            }
        } else {
            var sinX = 0.0
            var cosX = 0.0
            val X: Double
            val A: Double

            if (mode == OBLIQUE || mode == EQUATOR) {
                sinX = sin((2.0 * atan(ssfn(phi, sinphi, e)) - MapMath.HALFPI).also { X = it })
                cosX = cos(X)
            }
            when (mode) {
                OBLIQUE -> {
                    A = akm1 / (cosphi0 * (1.0 + sinphi0 * sinX + cosphi0 * cosX * coslam))
                    xy.y = A * (cosphi0 * sinX - sinphi0 * cosX * coslam)
                    xy.x = A * cosX
                }

                EQUATOR -> {
                    A = 2.0 * akm1 / (1.0 + cosX * coslam)
                    xy.y = A * sinX
                    xy.x = A * cosX
                }

                SOUTH_POLE -> {
                    phi = -phi
                    coslam = -coslam
                    sinphi = -sinphi
                    xy.x = akm1 * MapMath.tsfn(phi, sinphi, e)
                    xy.y = -xy.x * coslam
                }

                NORTH_POLE -> {
                    xy.x = akm1 * MapMath.tsfn(phi, sinphi, e)
                    xy.y = -xy.x * coslam
                }
            }
            xy.x = xy.x * sinlam
        }
        return xy
    }

    public override fun projectInverse(x: Double, y: Double, lp: Point2D.Double): Point2D.Double {
        var x = x
        var y = y
        if (spherical) {
            var c: Double
            val rh: Double
            val sinc: Double
            val cosc: Double

            sinc =
                sin((2.0 * atan((MapMath.distance(x, y).also { rh = it }) / akm1)).also { c = it })
            cosc = cos(c)
            lp.x = 0.0
            when (mode) {
                EQUATOR -> {
                    if (abs(rh) <= EPS10) lp.y = 0.0
                    else lp.y = asin(y * sinc / rh)
                    if (cosc != 0.0 || x != 0.0) lp.x = atan2(x * sinc, cosc * rh)
                }

                OBLIQUE -> {
                    if (abs(rh) <= EPS10) lp.y = projectionLatitude
                    else lp.y = asin(cosc * sinphi0 + y * sinc * cosphi0 / rh)
                    if (((cosc - sinphi0 * sin(lp.y)).also { c = it }) != 0.0 || x != 0.0) lp.x =
                        atan2(x * sinc * cosphi0, c * rh)
                }

                NORTH_POLE -> {
                    y = -y
                    if (abs(rh) <= EPS10) lp.y = projectionLatitude
                    else lp.y = asin(if (mode == SOUTH_POLE) -cosc else cosc)
                    lp.x = if (x == 0.0 && y == 0.0) 0.0 else atan2(x, y)
                }

                SOUTH_POLE -> {
                    if (abs(rh) <= EPS10) lp.y = projectionLatitude
                    else lp.y = asin(if (mode == SOUTH_POLE) -cosc else cosc)
                    lp.x = if (x == 0.0 && y == 0.0) 0.0 else atan2(x, y)
                }
            }
        } else {
            val cosphi: Double
            var sinphi: Double
            var tp: Double
            var phi_l: Double
            val rho: Double
            val halfe: Double
            val halfpi: Double

            rho = MapMath.distance(x, y)
            when (mode) {
                OBLIQUE, EQUATOR -> {
                    cosphi = cos((2.0 * atan2(rho * cosphi0, akm1)).also { tp = it })
                    sinphi = sin(tp)
                    phi_l = asin(cosphi * sinphi0 + (y * sinphi * cosphi0 / rho))
                    tp = tan(.5 * (MapMath.HALFPI + phi_l))
                    x *= sinphi
                    y = rho * cosphi0 * cosphi - y * sinphi0 * sinphi
                    halfpi = MapMath.HALFPI
                    halfe = .5 * e
                }

                NORTH_POLE -> {
                    y = -y
                    phi_l = MapMath.HALFPI - 2.0 * atan((-rho / akm1).also { tp = it })
                    halfpi = -MapMath.HALFPI
                    halfe = -.5 * e
                }

                SOUTH_POLE -> {
                    phi_l = MapMath.HALFPI - 2.0 * atan((-rho / akm1).also { tp = it })
                    halfpi = -MapMath.HALFPI
                    halfe = -.5 * e
                }

                else -> {
                    cosphi = cos((2.0 * atan2(rho * cosphi0, akm1)).also { tp = it })
                    sinphi = sin(tp)
                    phi_l = asin(cosphi * sinphi0 + (y * sinphi * cosphi0 / rho))
                    tp = tan(.5 * (MapMath.HALFPI + phi_l))
                    x *= sinphi
                    y = rho * cosphi0 * cosphi - y * sinphi0 * sinphi
                    halfpi = MapMath.HALFPI
                    halfe = .5 * e
                }
            }
            var i = 8
            while (i-- != 0) {
                sinphi = e * sin(phi_l)
                lp.y = 2.0 * atan(tp * ((1.0 + sinphi) / (1.0 - sinphi)).pow(halfe)) - halfpi
                if (abs(phi_l - lp.y) < EPS10) {
                    if (mode == SOUTH_POLE) lp.y = -lp.y
                    lp.x = if (x == 0.0 && y == 0.0) 0.0 else atan2(x, y)
                    return lp
                }
                phi_l = lp.y
            }
            throw RuntimeException("Iteration didn't converge")
        }
        return lp
    }

    override fun isConformal(): Boolean {
        /**
         * Returns true if this projection is conformal
         */
        return true
    }

    public override fun hasInverse(): Boolean {
        return true
    }

    private fun ssfn(phit: Double, sinphi: Double, eccen: Double): Double {
        var sinphi = sinphi
        sinphi *= eccen
        return tan(.5 * (MapMath.HALFPI + phit)) * ((1.0 - sinphi) / (1.0 + sinphi)).pow(.5 * eccen)
    }

    public override fun toString(): String {
        return "Stereographic Azimuthal"
    }

    companion object {
        private const val TOL = 1e-8
    }
}

