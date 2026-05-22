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
 *
 * Bernhard Jenny, 17 September 2010:
 * Changed base class to ConicProjection
 */
package com.jhlabs.map.proj

import com.jhlabs.Point2D
import com.jhlabs.map.MapMath

open class AlbersProjection : ConicProjection() {

    private var ec: Double = 0.0
    private var n: Double = 0.0
    private var c: Double = 0.0
    private var dd: Double = 0.0
    private var n2: Double = 0.0
    private var rho0: Double = 0.0
    private var phi1: Double = 0.0
    private var phi2: Double = 0.0
    private var en: DoubleArray? = null

    init {
        minLatitude = Math.toRadians(0.0)
        maxLatitude = Math.toRadians(80.0)
        projectionLatitude1 = MapMath.degToRad(45.5)
        projectionLatitude2 = MapMath.degToRad(29.5)
    }

    override fun project(lplam: Double, lpphi: Double, out: Point2D.Double): Point2D.Double {
        var rho: Double
        val rhoCalc = c - (if (!spherical) n * MapMath.qsfn(Math.sin(lpphi), e, one_es) else n2 * Math.sin(lpphi))
        if (rhoCalc < 0.0)
            throw ProjectionException("F")
        rho = rhoCalc
        rho = dd * Math.sqrt(rho)
        val lam = lplam * n
        out.x = rho * Math.sin(lam)
        out.y = rho0 - rho * Math.cos(lam)
        return out
    }

    override fun projectInverse(xyx: Double, xyy: Double, out: Point2D.Double): Point2D.Double {
        var x = xyx
        var y = xyy
        val rho: Double
        y = rho0 - y
        rho = MapMath.distance(x, y)
        if (rho != 0.0) {
            var lpphi: Double
            var lplam: Double
            if (n < 0.0) {
                x = -x
                y = -y
            }
            lpphi = rho / dd
            if (!spherical) {
                lpphi = (c - lpphi * lpphi) / n
                if (Math.abs(ec - Math.abs(lpphi)) > TOL7) {
                    lpphi = phi1_(lpphi, e, one_es)
                    if (lpphi == Double.MAX_VALUE)
                        throw ProjectionException("I")
                } else
                    lpphi = if (lpphi < 0.0) -MapMath.HALFPI else MapMath.HALFPI
            } else {
                lpphi = (c - lpphi * lpphi) / n2
                if (Math.abs(lpphi) <= 1.0)
                    lpphi = Math.asin(lpphi)
                else
                    lpphi = if (lpphi < 0.0) -MapMath.HALFPI else MapMath.HALFPI
            }
            lplam = Math.atan2(x, y) / n
            out.x = lplam
            out.y = lpphi
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
        var secant: Boolean

        phi1 = projectionLatitude1
        phi2 = projectionLatitude2

        if (Math.abs(phi1 + phi2) < EPS10)
            throw IllegalArgumentException("-21")
        n = Math.sin(phi1).also { sinphi = it }
        cosphi = Math.cos(phi1)
        secant = Math.abs(phi1 - phi2) >= EPS10
        spherical = es <= 0.0
        if (!spherical) {
            var ml1: Double
            var m1: Double

            en = MapMath.enfn(es)
            if (en == null)
                throw IllegalArgumentException("0")
            m1 = MapMath.msfn(sinphi, cosphi, es)
            ml1 = MapMath.qsfn(sinphi, e, one_es)
            if (secant) { /* secant cone */
                sinphi = Math.sin(phi2)
                cosphi = Math.cos(phi2)
                val m2 = MapMath.msfn(sinphi, cosphi, es)
                val ml2 = MapMath.qsfn(sinphi, e, one_es)
                n = (m1 * m1 - m2 * m2) / (ml2 - ml1)
            }
            ec = 1.0 - .5 * one_es * Math.log((1.0 - e) /
                    (1.0 + e)) / e
            c = m1 * m1 + n * ml1
            dd = 1.0 / n
            rho0 = dd * Math.sqrt(c - n * MapMath.qsfn(Math.sin(projectionLatitude),
                    e, one_es))
        } else {
            if (secant) n = .5 * (n + Math.sin(phi2))
            n2 = n + n
            c = cosphi * cosphi + n2 * sinphi
            dd = 1.0 / n
            rho0 = dd * Math.sqrt(c - n2 * Math.sin(projectionLatitude))
        }
    }

    /**
     * Returns true if this projection is equal area
     */
    override fun isEqualArea(): Boolean {
        return true
    }

    override fun hasInverse(): Boolean {
        return true
    }

    /**
     * Returns the ESPG code for this projection, or 0 if unknown.
     */
    override fun getEPSGCode(): Int {
        return 9822
    }

    override fun toString(): String {
        return "Albers Equal Area"
    }

    companion object {
        private const val EPS10 = 1.0e-10
        private const val TOL7 = 1.0e-7
        private const val N_ITER = 15
        private const val EPSILON = 1.0e-7
        private const val TOL = 1.0e-10

        private fun phi1_(qs: Double, Te: Double, Tone_es: Double): Double {
            var i: Int
            var Phi: Double
            var sinpi: Double
            var cospi: Double
            var con: Double
            var com: Double
            var dphi: Double

            Phi = Math.asin(.5 * qs)
            if (Te < EPSILON)
                return Phi
            i = N_ITER
            do {
                sinpi = Math.sin(Phi)
                cospi = Math.cos(Phi)
                con = Te * sinpi
                com = 1.0 - con * con
                dphi = .5 * com * com / cospi * (qs / Tone_es -
                        sinpi / com + .5 / Te * Math.log((1.0 - con) /
                        (1.0 + con)))
                Phi += dphi
            } while (Math.abs(dphi) > TOL && --i != 0)
            return if (i != 0) Phi else Double.MAX_VALUE
        }
    }
}
