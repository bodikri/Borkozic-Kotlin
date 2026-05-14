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

import com.jhlabs.Ellipse2D
import com.jhlabs.Point2D
import com.jhlabs.Shape
import com.jhlabs.map.MapMath
import kotlin.math.*

class EquidistantAzimuthalProjection : AzimuthalProjection {

    private companion object {
        const val TOL = 1.0e-8
    }


    private var en: DoubleArray? = null
    private var M1: Double = 0.0
    private var N1: Double = 0.0
    private var Mp: Double = 0.0
    private var He: Double = 0.0
    private var G: Double = 0.0



    constructor() : super(Math.toRadians(90.0), Math.toRadians(0.0))

    constructor(projectionLatitude: Double, projectionLongitude: Double) : super(projectionLatitude, projectionLongitude) {
        initialize()
    }

    public override fun clone(): Any {
        val p = super.clone() as EquidistantAzimuthalProjection
        if (en != null)
            p.en = en!!.clone()
        return p
    }

    override fun initialize() {
        super.initialize()
        if (abs(abs(projectionLatitude) - MapMath.HALFPI) < EPS10) {
            mode = if (projectionLatitude < 0.0) AzimuthalProjection.SOUTH_POLE else AzimuthalProjection.NORTH_POLE
            sinphi0 = if (projectionLatitude < 0.0) -1.0 else 1.0
            cosphi0 = 0.0
        } else if (abs(projectionLatitude) < EPS10) {
            mode = AzimuthalProjection.EQUATOR
            sinphi0 = 0.0
            cosphi0 = 1.0
        } else {
            mode = AzimuthalProjection.OBLIQUE
            sinphi0 = sin(projectionLatitude)
            cosphi0 = cos(projectionLatitude)
        }
        if (!spherical) {
            en = MapMath.enfn(es)
            when (mode) {
                AzimuthalProjection.NORTH_POLE -> {
                    Mp = MapMath.mlfn(MapMath.HALFPI, 1.0, 0.0, en)
                }
                AzimuthalProjection.SOUTH_POLE -> {
                    Mp = MapMath.mlfn(-MapMath.HALFPI, -1.0, 0.0, en)
                }
                AzimuthalProjection.EQUATOR,
                AzimuthalProjection.OBLIQUE -> {
                    N1 = 1.0 / sqrt(1.0 - es * sinphi0 * sinphi0)
                    He = e / sqrt(one_es)
                    G = sinphi0 * He
                    He *= cosphi0
                }
            }
        }
    }

    override fun project(lam: Double, phi: Double, out: Point2D.Double): Point2D.Double {
        if (spherical) {
            val sinphi = sin(phi)
            val cosphi = cos(phi)
            var coslam = cos(lam)
            when (mode) {
                AzimuthalProjection.EQUATOR,
                AzimuthalProjection.OBLIQUE -> {
                    if (mode == AzimuthalProjection.EQUATOR)
                        out.y = cosphi * coslam
                    else
                        out.y = sinphi0 * sinphi + cosphi0 * cosphi * coslam
                    if (abs(abs(out.y) - 1.0) < TOL) {
                        if (out.y < 0.0)
                            throw ProjectionException()
                        else
                            out.x = 0.0; out.y = 0.0
                    } else {
                        out.y = acos(out.y)
                        out.y /= sin(out.y)
                        out.x = out.y * cosphi * sin(lam)
                        if (mode == AzimuthalProjection.EQUATOR)
                            out.y *= sinphi
                        else
                            out.y *= cosphi0 * sinphi - sinphi0 * cosphi * coslam
                    }
                }
                AzimuthalProjection.NORTH_POLE -> {
                    val phi2 = -phi
                    coslam = -coslam
                    if (abs(phi2 - MapMath.HALFPI) < EPS10)
                        throw ProjectionException()
                    out.y = MapMath.HALFPI + phi2
                    out.x = out.y * sin(lam)
                    out.y *= coslam
                }
                AzimuthalProjection.SOUTH_POLE -> {
                    if (abs(phi - MapMath.HALFPI) < EPS10)
                        throw ProjectionException()
                    out.y = MapMath.HALFPI + phi
                    out.x = out.y * sin(lam)
                    out.y *= coslam
                }
            }
        } else {
            val coslam = cos(lam)
            val cosphi = cos(phi)
            val sinphi = sin(phi)
            when (mode) {
                AzimuthalProjection.NORTH_POLE -> {
                    val rho = abs(Mp - MapMath.mlfn(phi, sinphi, cosphi, en))
                    out.x = rho * sin(lam)
                    out.y = rho * (-coslam)
                }
                AzimuthalProjection.SOUTH_POLE -> {
                    val rho = abs(Mp - MapMath.mlfn(phi, sinphi, cosphi, en))
                    out.x = rho * sin(lam)
                    out.y = rho * coslam
                }
                AzimuthalProjection.EQUATOR,
                AzimuthalProjection.OBLIQUE -> {
                    if (abs(lam) < EPS10 && abs(phi - projectionLatitude) < EPS10) {
                        out.x = 0.0
                        out.y = 0.0
                    } else {
                        val t = atan2(one_es * sinphi + es * N1 * sinphi0 *
                                sqrt(1.0 - es * sinphi * sinphi), cosphi)
                        val ct = cos(t)
                        val st = sin(t)
                        val Az = atan2(sin(lam) * ct, cosphi0 * st - sinphi0 * coslam * ct)
                        val cA = cos(Az)
                        val sA = sin(Az)
                        val s = MapMath.asin(if (abs(sA) < TOL)
                            (cosphi0 * st - sinphi0 * coslam * ct) / cA
                        else
                            sin(lam) * ct / sA)
                        val H = He * cA
                        val H2 = H * H
                        val c = N1 * s * (1.0 + s * s * (
                                -H2 * (1.0 - H2) / 6.0 +
                                s * (G * H * (1.0 - 2.0 * H2 * H2) / 8.0 +
                                s * ((H2 * (4.0 - 7.0 * H2) - 3.0 * G * G * (1.0 - 7.0 * H2)) /
                                120.0 - s * G * H / 48.0))))
                        out.x = c * sA
                        out.y = c * cA
                    }
                }
            }
        }
        return out
    }

    override fun projectInverse(x: Double, y: Double, lp: Point2D.Double): Point2D.Double {
        if (spherical) {
            var c_rh = MapMath.distance(x, y)
            if (c_rh > Math.PI) {
                if (c_rh - EPS10 > Math.PI)
                    throw ProjectionException()
                c_rh = Math.PI
            } else if (c_rh < EPS10) {
                lp.y = projectionLatitude
                lp.x = 0.0
                return lp
            }
            val sinc = sin(c_rh)
            val cosc = cos(c_rh)
            when {
                mode == AzimuthalProjection.OBLIQUE || mode == AzimuthalProjection.EQUATOR -> {
                    if (mode == AzimuthalProjection.EQUATOR) {
                        lp.y = MapMath.asin(y * sinc / c_rh)
                        val xx = x * sinc
                        val yy = cosc * c_rh
                        lp.x = if (yy == 0.0) 0.0 else atan2(xx, yy)
                    } else {
                        lp.y = MapMath.asin(cosc * sinphi0 + y * sinc * cosphi0 / c_rh)
                        val xx = x * sinc * cosphi0
                        val yy = (cosc - sinphi0 * sin(lp.y)) * c_rh
                        lp.x = if (yy == 0.0) 0.0 else atan2(xx, yy)
                    }
                }
                mode == AzimuthalProjection.NORTH_POLE -> {
                    lp.y = MapMath.HALFPI - c_rh
                    lp.x = atan2(x, -y)
                }
                else -> {
                    lp.y = c_rh - MapMath.HALFPI
                    lp.x = atan2(x, y)
                }
            }
        } else {
            val c = MapMath.distance(x, y)
            if (c < EPS10) {
                lp.y = projectionLatitude
                lp.x = 0.0
                return lp
            }
            when {
                mode == AzimuthalProjection.OBLIQUE || mode == AzimuthalProjection.EQUATOR -> {
                    val Az = atan2(x, y)
                    val cosAz = cos(Az)
                    val t = cosphi0 * cosAz
                    var B = es * t / one_es
                    val A = -B * t
                    B *= 3.0 * (1.0 - A) * sinphi0
                    val D = c / N1
                    val E = D * (1.0 - D * D * (A * (1.0 + A) / 6.0 + B * (1.0 + 3.0 * A) * D / 24.0))
                    val F = 1.0 - E * E * (A / 2.0 + B * E / 6.0)
                    val psi = MapMath.asin(sinphi0 * cos(E) + t * sin(E))
                    lp.x = MapMath.asin(sin(Az) * sin(E) / cos(psi))
                    val t2 = abs(psi)
                    if (t2 < EPS10)
                        lp.y = 0.0
                    else if (abs(t2 - MapMath.HALFPI) < 0.0)
                        lp.y = MapMath.HALFPI
                    else
                        lp.y = atan((1.0 - es * F * sinphi0 / sin(psi)) * tan(psi) / one_es)
                }
                else -> {
                    lp.y = MapMath.inv_mlfn(if (mode == AzimuthalProjection.NORTH_POLE) Mp - c else Mp + c, es, en)
                    lp.x = atan2(x, if (mode == AzimuthalProjection.NORTH_POLE) -y else y)
                }
            }
        }
        return lp
    }

    fun getBoundingShape(): Shape {
        val r = MapMath.HALFPI * equatorRadius
        return Ellipse2D.Double(-r, -r, 2 * r, 2 * r)
    }

    override fun hasInverse(): Boolean = true

    override fun toString(): String = "Equidistant Azimuthal"
}
