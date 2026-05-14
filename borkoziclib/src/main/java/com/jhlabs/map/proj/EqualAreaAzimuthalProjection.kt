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

class EqualAreaAzimuthalProjection : AzimuthalProjection() {

    private var sinb1: Double = 0.0
    private var cosb1: Double = 0.0
    private var xmf: Double = 0.0
    private var ymf: Double = 0.0
    private var mmf: Double = 0.0
    private var qp: Double = 0.0
    private var dd: Double = 0.0
    private var rq: Double = 0.0
    private var apa: DoubleArray? = null

    public override fun clone(): Any {
        val p = super.clone() as EqualAreaAzimuthalProjection
        if (apa != null)
            p.apa = apa!!.clone()
        return p
    }

    override fun initialize() {
        super.initialize()
        if (spherical) {
            if (mode == AzimuthalProjection.OBLIQUE) {
                sinphi0 = sin(projectionLatitude)
                cosphi0 = cos(projectionLatitude)
            }
        } else {
            qp = MapMath.qsfn(1.0, e, one_es)
            mmf = .5 / (1.0 - es)
            apa = MapMath.authset(es)
            when (mode) {
                AzimuthalProjection.NORTH_POLE,
                AzimuthalProjection.SOUTH_POLE -> dd = 1.0
                AzimuthalProjection.EQUATOR -> {
                    rq = sqrt(.5 * qp)
                    dd = 1.0 / rq
                    xmf = 1.0
                    ymf = .5 * qp
                }
                AzimuthalProjection.OBLIQUE -> {
                    rq = sqrt(.5 * qp)
                    val sinphi = sin(projectionLatitude)
                    sinb1 = MapMath.qsfn(sinphi, e, one_es) / qp
                    cosb1 = sqrt(1.0 - sinb1 * sinb1)
                    dd = cos(projectionLatitude) / (sqrt(1.0 - es * sinphi * sinphi) *
                            rq * cosb1)
                    xmf = rq
                    ymf = xmf / dd
                    xmf *= dd
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
                AzimuthalProjection.EQUATOR -> {
                    out.y = 1.0 + cosphi * coslam
                    if (out.y <= EPS10) throw ProjectionException()
                    out.y = sqrt(2.0 / out.y)
                    out.x = out.y * cosphi * sin(lam)
                    out.y *= sinphi
                }
                AzimuthalProjection.OBLIQUE -> {
                    out.y = 1.0 + sinphi0 * sinphi + cosphi0 * cosphi * coslam
                    if (out.y <= EPS10) throw ProjectionException()
                    out.y = sqrt(2.0 / out.y)
                    out.x = out.y * cosphi * sin(lam)
                    out.y *= cosphi0 * sinphi - sinphi0 * cosphi * coslam
                }
                AzimuthalProjection.NORTH_POLE -> {
                    coslam = -coslam
                    if (abs(phi + projectionLatitude) < EPS10) throw ProjectionException()
                    out.y = MapMath.QUARTERPI - phi * .5
                    out.y = 2.0 * sin(out.y)
                    out.x = out.y * sin(lam)
                    out.y *= coslam
                }
                AzimuthalProjection.SOUTH_POLE -> {
                    if (abs(phi + projectionLatitude) < EPS10) throw ProjectionException()
                    out.y = MapMath.QUARTERPI - phi * .5
                    out.y = 2.0 * cos(out.y)
                    out.x = out.y * sin(lam)
                    out.y *= coslam
                }
            }
        } else {
            val coslam = cos(lam)
            val sinlam = sin(lam)
            val sinphi = sin(phi)
            val q = MapMath.qsfn(sinphi, e, one_es)
            var sinb = 0.0
            var cosb = 0.0
            if (mode == AzimuthalProjection.OBLIQUE || mode == AzimuthalProjection.EQUATOR) {
                sinb = q / qp
                cosb = sqrt(1.0 - sinb * sinb)
            }
            val b: Double = when (mode) {
                AzimuthalProjection.OBLIQUE -> 1.0 + sinb1 * sinb + cosb1 * cosb * coslam
                AzimuthalProjection.EQUATOR -> 1.0 + cosb * coslam
                AzimuthalProjection.NORTH_POLE -> {
                    MapMath.HALFPI + phi
                }
                AzimuthalProjection.SOUTH_POLE -> {
                    phi - MapMath.HALFPI
                }
                else -> 0.0
            }
            if (mode == AzimuthalProjection.NORTH_POLE || mode == AzimuthalProjection.SOUTH_POLE) {
                val q1 = if (mode == AzimuthalProjection.NORTH_POLE) qp - q else qp + q
                if (abs(b) < EPS10) throw ProjectionException()
                val sqrtq = sqrt(q1)
                out.x = sqrtq * sinlam
                out.y = coslam * if (mode == AzimuthalProjection.SOUTH_POLE) sqrtq else -sqrtq
            } else {
                if (abs(b) < EPS10) throw ProjectionException()
                when (mode) {
                    AzimuthalProjection.OBLIQUE -> {
                        val bb = sqrt(2.0 / b)
                        out.y = ymf * bb * (cosb1 * sinb - sinb1 * cosb * coslam)
                        out.x = xmf * bb * cosb * sinlam
                    }
                    AzimuthalProjection.EQUATOR -> {
                        val bb = sqrt(2.0 / (1.0 + cosb * coslam))
                        out.y = bb * sinb * ymf
                        out.x = xmf * bb * cosb * sinlam
                    }
                    else -> {}
                }
            }
        }
        return out
    }

    override fun projectInverse(x: Double, y: Double, lp: Point2D.Double): Point2D.Double {
        if (spherical) {
            val rh = MapMath.distance(x, y)
            lp.y = rh * 0.5
            if (lp.y > 1.0) throw ProjectionException()
            lp.y = 2.0 * asin(lp.y)
            val sinz = sin(lp.y)
            val cosz = cos(lp.y)
            when (mode) {
                AzimuthalProjection.EQUATOR -> {
                    lp.y = if (abs(rh) <= EPS10) 0.0 else asin(y * sinz / rh)
                    val xx = x * sinz
                    val yy = cosz * rh
                    lp.x = if (yy == 0.0) 0.0 else atan2(xx, yy)
                }
                AzimuthalProjection.OBLIQUE -> {
                    lp.y = if (abs(rh) <= EPS10) projectionLatitude
                        else asin(cosz * sinphi0 + y * sinz * cosphi0 / rh)
                    val xx = x * sinz * cosphi0
                    val yy = (cosz - sin(lp.y) * sinphi0) * rh
                    lp.x = if (yy == 0.0) 0.0 else atan2(xx, yy)
                }
                AzimuthalProjection.NORTH_POLE -> {
                    lp.y = MapMath.HALFPI - lp.y
                    lp.x = atan2(x, -y)
                }
                AzimuthalProjection.SOUTH_POLE -> {
                    lp.y = MapMath.HALFPI - lp.y
                    lp.x = atan2(x, y)
                }
            }
        } else {
            var cCe: Double
            var sCe: Double
            var q: Double
            var rho: Double
            var ab: Double = 0.0
            var localX = x
            var localY = y
            when (mode) {
                AzimuthalProjection.EQUATOR,
                AzimuthalProjection.OBLIQUE -> {
                    localX /= dd
                    localY *= dd
                    rho = MapMath.distance(localX, localY)
                    if (rho < EPS10) {
                        lp.x = 0.0
                        lp.y = projectionLatitude
                        return lp
                    }
                    cCe = cos(2.0 * asin(.5 * rho / rq).also { sCe = it })
                    sCe = sin(sCe)
                    localX *= sCe
                    if (mode == AzimuthalProjection.OBLIQUE) {
                        ab = cCe * sinb1 + localY * sCe * cosb1 / rho
                        q = qp * ab
                        localY = rho * cosb1 * cCe - localY * sinb1 * sCe
                    } else {
                        ab = localY * sCe / rho
                        q = qp * ab
                        localY = rho * cCe
                    }
                }
                AzimuthalProjection.NORTH_POLE -> {
                    localY = -localY
                    q = localX * localX + localY * localY
                    if (q == 0.0) {
                        lp.x = 0.0
                        lp.y = projectionLatitude
                        return lp
                    }
                    ab = 1.0 - q / qp
                }
                AzimuthalProjection.SOUTH_POLE -> {
                    q = localX * localX + localY * localY
                    if (q == 0.0) {
                        lp.x = 0.0
                        lp.y = projectionLatitude
                        return lp
                    }
                    ab = 1.0 - q / qp
                    ab = -ab
                }
            }
            lp.x = atan2(localX, localY)
            lp.y = MapMath.authlat(asin(ab), apa)
        }
        return lp
    }

    fun getBoundingShape(): Shape {
        val r = 1.414 * equatorRadius
        return Ellipse2D.Double(-r, -r, 2 * r, 2 * r)
    }

    override fun isEqualArea(): Boolean = true

    override fun hasInverse(): Boolean = true

    override fun toString(): String = "Lambert Equal Area Azimuthal"
}
