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
import com.jhlabs.map.Ellipsoid
import com.jhlabs.map.MapMath
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Oblique Mercator Projection algorithm is taken from the USGS PROJ package.
 * 
 * Bernhard Jenny, 23 September 2010: changed super class to Cylindrical
 */
class ObliqueMercatorProjection : CylindricalProjection {
    private var alpha: Double
    private var lamc = 0.0
    private val lam1 = 0.0
    private val phi1 = 0.0
    private var lam2 = 0.0
    private val phi2 = 0.0
    private var Gamma = 0.0
    private var al = 0.0
    private var bl = 0.0
    private var el = 0.0
    private var singam = 0.0
    private var cosgam = 0.0
    private var sinrot = 0.0
    private var cosrot = 0.0
    private var u_0 = 0.0
    private val ellips = false
    private var rot = false

    constructor() {
        ellipsoid = Ellipsoid.WGS_1984
        projectionLatitude = Math.toRadians(0.0)
        projectionLongitude = Math.toRadians(0.0)
        minLongitude = Math.toRadians(-60.0)
        maxLongitude = Math.toRadians(60.0)
        minLatitude = Math.toRadians(-80.0)
        maxLatitude = Math.toRadians(80.0)
        alpha = Math.toRadians(-45.0) //FIXME
    }

    /**
     * Set up a projection suitable for State Plane Coordinates.
     */
    constructor(
        ellipsoid: Ellipsoid,
        lon_0: Double,
        lat_0: Double,
        alpha: Double,
        k: Double,
        x_0: Double,
        y_0: Double
    ) {
        setEllipsoid(ellipsoid)
        lamc = lon_0
        projectionLatitude = lat_0
        this.alpha = alpha
        scaleFactor = k
        falseEasting = x_0
        falseNorthing = y_0
    }

    public override fun initialize() {
        super.initialize()
        var con = 0.0
        val com: Double
        val cosphi0: Double
        val d: Double
        var f: Double
        val h: Double
        val l: Double
        val sinphi0: Double
        val p: Double
        var j: Double
        val azi = 0 //FIXME-param

        //FIXME-setup rot, alpha, longc,lon/lat1/2
        rot = true

        if (azi != 0) { //alpha specified
            if (abs(alpha) <= TOL || abs(abs(projectionLatitude) - MapMath.HALFPI) <= TOL || abs(
                    abs(
                        alpha
                    ) - MapMath.HALFPI
                ) <= TOL
            ) {
                throw ProjectionException("Obl 1")
            }
        } else {
            if (abs(phi1 - phi2) <= TOL || (abs(phi1).also {
                    con = it
                }) <= TOL || abs(con - MapMath.HALFPI) <= TOL || abs(
                    abs(projectionLatitude) - MapMath.HALFPI
                ) <= TOL || abs(abs(phi2) - MapMath.HALFPI) <= TOL
            ) {
                throw ProjectionException("Obl 2")
            }
        }
        com = if ((es == 0.0).also { spherical = it }) 1.0 else sqrt(one_es)
        if (abs(projectionLatitude) > EPS10) {
            sinphi0 = sin(projectionLatitude)
            cosphi0 = cos(projectionLatitude)
            if (!spherical) {
                con = 1.0 - es * sinphi0 * sinphi0
                bl = cosphi0 * cosphi0
                bl = sqrt(1.0 + es * bl * bl / one_es)
                al = bl * scaleFactor * com / con
                d = bl * com / (cosphi0 * sqrt(con))
            } else {
                bl = 1.0
                al = scaleFactor
                d = 1.0 / cosphi0
            }
            if (((d * d - 1.0).also { f = it }) <= 0.0) {
                f = 0.0
            } else {
                f = sqrt(f)
                if (projectionLatitude < 0.0) {
                    f = -f
                }
            }
            f += d
            el = f
            if (!spherical) {
                el *= MapMath.tsfn(projectionLatitude, sinphi0, e).pow(bl)
            } else {
                el *= tan(.5 * (MapMath.HALFPI - projectionLatitude))
            }
        } else {
            bl = 1.0 / com
            al = scaleFactor
            f = 1.0
            d = f
            el = d
        }
        if (azi != 0) {
            Gamma = asin(sin(alpha) / d)
            projectionLongitude = lamc - asin(
                (.5 * (f - 1.0 / f))
                        * tan(Gamma)
            ) / bl
        } else {
            if (!spherical) {
                h = MapMath.tsfn(phi1, sin(phi1), e).pow(bl)
                l = MapMath.tsfn(phi2, sin(phi2), e).pow(bl)
            } else {
                h = tan(.5 * (MapMath.HALFPI - phi1))
                l = tan(.5 * (MapMath.HALFPI - phi2))
            }
            f = el / h
            p = (l - h) / (l + h)
            j = el * el
            j = (j - l * h) / (j + l * h)
            if (((lam1 - lam2).also { con = it }) < -Math.PI) {
                lam2 -= MapMath.TWOPI
            } else if (con > Math.PI) {
                lam2 += MapMath.TWOPI
            }
            projectionLongitude = MapMath.normalizeLongitude(
                .5 * (lam1 + lam2) - atan(
                    j * tan(.5 * bl * (lam1 - lam2)) / p
                ) / bl
            )
            Gamma = atan(
                2.0 * sin(bl * MapMath.normalizeLongitude(lam1 - projectionLongitude))
                        / (f - 1.0 / f)
            )
            alpha = asin(d * sin(Gamma))
        }
        singam = sin(Gamma)
        cosgam = cos(Gamma)
        //		f = MapMath.param(params, "brot_conv").i ? Gamma : alpha;
        f = alpha //FIXME
        sinrot = sin(f)
        cosrot = cos(f)
        //		u_0 = MapMath.param(params, "bno_uoff").i ? 0. :
        u_0 = if (false) 0.0 else abs(al * atan(sqrt(d * d - 1.0) / cosrot) / bl)
        if (projectionLatitude < 0.0) {
            u_0 = -u_0
        }
    }

    public override fun project(lam: Double, phi: Double, xy: Point2D.Double): Point2D.Double {
        val con: Double
        val q: Double
        val s: Double
        val ul: Double
        var us: Double
        val vl: Double
        val vs: Double

        vl = sin(bl * lam)
        if (abs(abs(phi) - MapMath.HALFPI) <= EPS10) {
            ul = if (phi < 0.0) -singam else singam
            us = al * phi / bl
        } else {
            q = el / (if (!spherical) MapMath.tsfn(phi, sin(phi), e)
                .pow(bl) else tan(.5 * (MapMath.HALFPI - phi)))
            s = .5 * (q - 1.0 / q)
            ul = 2.0 * (s * singam - vl * cosgam) / (q + 1.0 / q)
            con = cos(bl * lam)
            if (abs(con) >= TOL) {
                us = al * atan((s * cosgam + vl * singam) / con) / bl
                if (con < 0.0) {
                    us += Math.PI * al / bl
                }
            } else {
                us = al * bl * lam
            }
        }
        if (abs(abs(ul) - 1.0) <= EPS10) {
            throw ProjectionException("Obl 3")
        }
        vs = .5 * al * ln((1.0 - ul) / (1.0 + ul)) / bl
        us -= u_0
        if (!rot) {
            xy.x = us
            xy.y = vs
        } else {
            xy.x = vs * cosrot + us * sinrot
            xy.y = us * cosrot - vs * sinrot
        }
        return xy
    }

    public override fun projectInverse(x: Double, y: Double, lp: Point2D.Double): Point2D.Double {
        val q: Double
        val s: Double
        val ul: Double
        var us: Double
        val vl: Double
        val vs: Double

        if (!rot) {
            us = x
            vs = y
        } else {
            vs = x * cosrot - y * sinrot
            us = y * cosrot + x * sinrot
        }
        us += u_0
        q = exp(-bl * vs / al)
        s = .5 * (q - 1.0 / q)
        vl = sin(bl * us / al)
        ul = 2.0 * (vl * cosgam + s * singam) / (q + 1.0 / q)
        if (abs(abs(ul) - 1.0) < EPS10) {
            lp.x = 0.0
            lp.y = if (ul < 0.0) -MapMath.HALFPI else MapMath.HALFPI
        } else {
            lp.y = el / sqrt((1.0 + ul) / (1.0 - ul))
            if (!spherical) {
                lp.y = MapMath.phi2(lp.y.pow(1.0 / bl), e)
            } else {
                lp.y = MapMath.HALFPI - 2.0 * atan(lp.y)
            }
            lp.x = -atan2(
                (s * cosgam
                        - vl * singam), cos(bl * us / al)
            ) / bl
        }
        return lp
    }

    public override fun hasInverse(): Boolean {
        return true
    }

    public override fun toString(): String {
        return "Oblique Mercator"
    }

    companion object {
        private const val TOL = 1.0e-7
    }
}
