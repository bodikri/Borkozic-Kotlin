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
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

class LandsatProjection : Projection() {
    private var a2 = 0.0
    private var a4 = 0.0
    private var b = 0.0
    private var c1 = 0.0
    private var c3 = 0.0
    private var q = 0.0
    private var t = 0.0
    private var u = 0.0
    private var w = 0.0
    private var p22 = 0.0
    private var sa = 0.0
    private var ca = 0.0
    private var xj = 0.0
    private var rlm = 0.0
    private var rlm2 = 0.0

    public override fun project(lplam: Double, lpphi: Double, xy: Point2D.Double): Point2D.Double {
        var lpphi = lpphi
        var l: Int
        var nn: Int
        var lamt = 0.0
        var xlam: Double
        val sdsq: Double
        var c: Double
        val d: Double
        val s: Double
        var lamdp = 0.0
        val phidp: Double
        var lampp: Double
        val tanph: Double
        var lamtp: Double
        var cl: Double
        val sd: Double
        val sp: Double
        var fac: Double
        var sav: Double
        val tanphi: Double

        if (lpphi > MapMath.HALFPI) lpphi = MapMath.HALFPI
        else if (lpphi < -MapMath.HALFPI) lpphi = -MapMath.HALFPI
        lampp = if (lpphi >= 0.0) MapMath.HALFPI else PI_HALFPI
        tanphi = tan(lpphi)
        nn = 0
        while (true) {
            sav = lampp
            lamtp = lplam + p22 * lampp
            cl = cos(lamtp)
            if (abs(cl) < TOL) lamtp -= TOL
            fac = lampp - sin(lampp) * (if (cl < 0.0) -MapMath.HALFPI else MapMath.HALFPI)
            l = 50
            while (l > 0) {
                lamt = lplam + p22 * sav
                if (abs(cos(lamt).also { c = it }) < TOL) lamt -= TOL
                xlam = (one_es * tanphi * sa + sin(lamt) * ca) / c
                lamdp = atan(xlam) + fac
                if (abs(abs(sav) - abs(lamdp)) < TOL) break
                sav = lamdp
                --l
            }
            if (l == 0 || ++nn >= 3 || (lamdp > rlm && lamdp < rlm2)) break
            if (lamdp <= rlm) lampp = TWOPI_HALFPI
            else if (lamdp >= rlm2) lampp = MapMath.HALFPI
        }
        if (l != 0) {
            sp = sin(lpphi)
            phidp =
                MapMath.asin((one_es * ca * sp - sa * cos(lpphi) * sin(lamt)) / sqrt(1.0 - es * sp * sp))
            tanph = ln(tan(MapMath.QUARTERPI + .5 * phidp))
            sd = sin(lamdp)
            sdsq = sd * sd
            s = p22 * sa * cos(lamdp) * sqrt(
                (1.0 + t * sdsq)
                        / ((1.0 + w * sdsq) * (1.0 + q * sdsq))
            )
            d = sqrt(xj * xj + s * s)
            xy.x = b * lamdp + a2 * sin(2.0 * lamdp) + a4 * sin(lamdp * 4.0) - tanph * s / d
            xy.y = c1 * sd + c3 * sin(lamdp * 3.0) + tanph * xj / d
        } else {
            xy.y = Double.Companion.POSITIVE_INFINITY
            xy.x = xy.y
        }
        return xy
    }

    /*
	public Point2D.Double projectInverse(double xyx, double xyy, Point2D.Double out) {
		int nn;
		double lamt, sdsq, s, lamdp, phidp, sppsq, dd, sd, sl, fac, scl, sav, spp;

		lamdp = xy.x / b;
		nn = 50;
		do {
			sav = lamdp;
			sd = Math.sin(lamdp);
			sdsq = sd * sd;
			s = p22 * sa * Math.cos(lamdp) * sqrt((1. + t * sdsq)
				 / ((1. + w * sdsq) * (1. + q * sdsq)));
			lamdp = xy.x + xy.y * s / xj - a2 * Math.sin(
				2. * lamdp) - a4 * Math.sin(lamdp * 4.) - s / xj * (
				c1 * Math.sin(lamdp) + c3 * Math.sin(lamdp * 3.));
			lamdp /= b;
		} while (Math.abs(lamdp - sav) >= TOL && --nn);
		sl = Math.sin(lamdp);
		fac = exp(sqrt(1. + s * s / xj / xj) * (xy.y - 
			c1 * sl - c3 * Math.sin(lamdp * 3.)));
		phidp = 2. * (Math.atan(fac) - FORTPI);
		dd = sl * sl;
		if (Math.abs(Math.cos(lamdp)) < TOL)
			lamdp -= TOL;
		spp = Math.sin(phidp);
		sppsq = spp * spp;
		lamt = Math.atan(((1. - sppsq * rone_es) * Math.tan(lamdp) * 
			ca - spp * sa * sqrt((1. + q * dd) * (
			1. - sppsq) - sppsq * u) / Math.cos(lamdp)) / (1. - sppsq 
			* (1. + u)));
		sl = lamt >= 0. ? 1. : -1.;
		scl = Math.cos(lamdp) >= 0. ? 1. : -1;
		lamt -= HALFPI * (1. - scl) * sl;
		lp.lam = lamt - p22 * lamdp;
		if (Math.abs(sa) < TOL)
			lp.phi = aasin(spp / sqrt(one_es * one_es + es * sppsq));
		else
			lp.phi = Math.atan((Math.tan(lamdp) * Math.cos(lamt) - ca * Math.sin(lamt)) /
				(one_es * sa));
		return lp;
	}
*/
    private fun seraz0(lam: Double, mult: Double) {
        var lam = lam
        val sdsq: Double
        val h: Double
        val s: Double
        var fc: Double
        val sd: Double
        val sq: Double
        val d__1: Double

        lam *= DTR
        sd = sin(lam)
        sdsq = sd * sd
        s = p22 * sa * cos(lam) * sqrt((1.0 + t * sdsq) / ((1.0 + w * sdsq) * (1.0 + q * sdsq)))
        d__1 = 1.0 + q * sdsq
        h = sqrt((1.0 + q * sdsq) / (1.0 + w * sdsq)) * ((1.0 +
                w * sdsq) / (d__1 * d__1) - p22 * ca)
        sq = sqrt(xj * xj + s * s)
        fc = mult * (h * xj - s * s) / sq
        b += fc
        a2 += fc * cos(lam + lam)
        a4 += fc * cos(lam * 4.0)
        fc = mult * s * (h + xj) / sq
        c1 += fc * cos(lam)
        c3 += fc * cos(lam * 3.0)
    }

    public override fun initialize() {
        super.initialize()
        val land: Int
        val path: Int
        var lam: Double
        val alf: Double
        val esc: Double
        val ess: Double

        //FIXME		land = pj_param(params, "ilsat").i;
        land = 1
        if (land <= 0 || land > 5) throw ProjectionException("-28")
        //FIXME		path = pj_param(params, "ipath").i;
        path = 120
        if (path <= 0 || path > (if (land <= 3) 251 else 233)) throw ProjectionException("-29")
        if (land <= 3) {
            projectionLongitude = DTR * 128.87 - MapMath.TWOPI / 251.0 * path
            p22 = 103.2669323
            alf = DTR * 99.092
        } else {
            projectionLongitude = DTR * 129.3 - MapMath.TWOPI / 233.0 * path
            p22 = 98.8841202
            alf = DTR * 98.2
        }
        p22 /= 1440.0
        sa = sin(alf)
        ca = cos(alf)
        if (abs(ca) < 1e-9) ca = 1e-9
        esc = es * ca * ca
        ess = es * sa * sa
        w = (1.0 - esc) * rone_es
        w = w * w - 1.0
        q = ess * rone_es
        t = ess * (2.0 - es) * rone_es * rone_es
        u = esc * rone_es
        xj = one_es * one_es * one_es
        rlm = Math.PI * (1.0 / 248.0 + .5161290322580645)
        rlm2 = rlm + MapMath.TWOPI
        c3 = 0.0
        c1 = c3
        b = c1
        a4 = b
        a2 = a4
        seraz0(0.0, 1.0)
        lam = 9.0
        while (lam <= 81.0001) {
            seraz0(lam, 4.0)
            lam += 18.0
        }
        lam = 18.0
        while (lam <= 72.0001) {
            seraz0(lam, 2.0)
            lam += 18.0
        }
        seraz0(90.0, 1.0)
        a2 /= 30.0
        a4 /= 60.0
        b /= 30.0
        c1 /= 15.0
        c3 /= 45.0
    }

    public override fun hasInverse(): Boolean {
        return true
    }

    public override fun toString(): String {
        return "Landsat"
    }

    companion object {
        private const val TOL = 1e-7
        private const val PI_HALFPI = 4.71238898038468985766
        private const val TWOPI_HALFPI = 7.85398163397448309610
    }
}

