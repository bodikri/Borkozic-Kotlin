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
import kotlin.math.cos
import kotlin.math.sin

class PerspectiveProjection : Projection() {
    private var height = 0.0
    private val psinph0 = 0.0
    private val pcosph0 = 0.0
    private var p = 0.0
    private var rp = 0.0
    private var pn1 = 0.0
    private var pfact = 0.0
    private var h = 0.0
    private val cg = 0.0
    private val sg = 0.0
    private val sw = 0.0
    private val cw = 0.0
    private var mode = 0
    private var tilt = 0

    override fun project(lplam: Double, lpphi: Double, xy: Point2D.Double): Point2D.Double {
        var coslam: Double
        val cosphi: Double
        val sinphi: Double

        sinphi = sin(lpphi)
        cosphi = cos(lpphi)
        coslam = cos(lplam)
        when (mode) {
            OBLIQ -> xy.y = psinph0 * sinphi + pcosph0 * cosphi * coslam
            EQUIT -> xy.y = cosphi * coslam
            S_POLE -> xy.y = -sinphi
            N_POLE -> xy.y = sinphi
        }
        //		if (xy.y < rp)
//			throw new ProjectionException("");
        xy.y = pn1 / (p - xy.y)
        xy.x = xy.y * cosphi * sin(lplam)
        when (mode) {
            OBLIQ -> xy.y *= (pcosph0 * sinphi -
                    psinph0 * cosphi * coslam)

            EQUIT -> xy.y *= sinphi
            N_POLE -> {
                coslam = -coslam
                xy.y *= cosphi * coslam
            }

            S_POLE -> xy.y *= cosphi * coslam
        }
        if (tilt != 0) {
            val yt: Double
            val ba: Double

            yt = xy.y * cg + xy.x * sg
            ba = 1.0 / (yt * sw * h + cw)
            xy.x = (xy.x * cg - xy.y * sg) * cw * ba
            xy.y = yt * ba
        }
        return xy
    }

    override fun hasInverse(): Boolean {
        return false // FIXME
    }

    /*FIXME
INVERSE(s_inverse); / * spheroid * /
	double  rh, cosz, sinz;

	if (tilt) {
		double bm, bq, yt;

		yt = 1./(pn1 - xy.y * sw);
		bm = pn1 * xy.x * yt;
		bq = pn1 * xy.y * cw * yt;
		xy.x = bm * cg + bq * sg;
		xy.y = bq * cg - bm * sg;
	}
	rh = hypot(xy.x, xy.y);
	if ((sinz = 1. - rh * rh * pfact) < 0.) I_ERROR;
	sinz = (p - Math.sqrt(sinz)) / (pn1 / rh + rh / pn1);
	cosz = sqrt(1. - sinz * sinz);
	if (fabs(rh) <= EPS10) {
		lp.lam = 0.;
		lp.phi = phi0;
	} else {
		switch (mode) {
		case OBLIQ:
			lp.phi = Math.asin(cosz * sinph0 + xy.y * sinz * cosph0 / rh);
			xy.y = (cosz - sinph0 * sin(lp.phi)) * rh;
			xy.x *= sinz * cosph0;
			break;
		case EQUIT:
			lp.phi = Math.asin(xy.y * sinz / rh);
			xy.y = cosz * rh;
			xy.x *= sinz;
			break;
		case N_POLE:
			lp.phi = Math.asin(cosz);
			xy.y = -xy.y;
			break;
		case S_POLE:
			lp.phi = - Math.asin(cosz);
			break;
		}
		lp.lam = Math.atan2(xy.x, xy.y);
	}
	return (lp);
}
*/
    override fun initialize() {
        super.initialize()
        mode = EQUIT
        height = equatorRadius
        tilt = 0
        //		if ((height = pj_param(params, "dh").f) <= 0.) E_ERROR(-30);
        /*
		if (fabs(fabs(phi0) - Math.HALFPI) < EPS10)
			mode = phi0 < 0. ? S_POLE : N_POLE;
		else if (fabs(phi0) < EPS10)
			mode = EQUIT;
		else {
			mode = OBLIQ;
			psinph0 = Math.sin(phi0);
			pcosph0 = Math.cos(phi0);
		}
*/
        pn1 = height / equatorRadius /* normalize by radius */
        p = 1.0 + pn1
        rp = 1.0 / p
        h = 1.0 / pn1
        pfact = (p + 1.0) * h
        es = 0.0
    }

    /*FIXME
ENTRY0(nsper)
	tilt = 0;
ENDENTRY(setup(P))
ENTRY0(tpers)
	double omega, gamma;

	omega = pj_param(params, "dtilt").f * DEG_TO_RAD;
	gamma = pj_param(params, "dazi").f * DEG_TO_RAD;
	tilt = 1;
	cg = cos(gamma); sg = sin(gamma);
	cw = cos(omega); sw = sin(omega);
ENDENTRY(setup(P))
*/
    override fun toString(): String {
        return "Perspective"
    }

    companion object {
        private const val EPS10 = 1e-10
        private const val N_POLE = 0
        private const val S_POLE = 1
        private const val EQUIT = 2
        private const val OBLIQ = 3
    }
}
