package com.jhlabs.map.proj

import com.jhlabs.Point2D
import com.jhlabs.map.MapMath

class SwissObliqueMercatorProjection : CylindricalProjection() {
    private var K = 0.0
    private var c = 0.0
    private var hlf_e = 0.0
    private var kR = 0.0
    private var cosp0 = 0.0
    private var sinp0 = 0.0
    private val NITER = 6

    override fun initialize() {
        super.initialize()

        val cp: Double
        val phip0: Double
        var sp: Double

        hlf_e = 0.5 * e
        cp = Math.cos(projectionLatitude)
        val cp2 = cp * cp
        c = Math.sqrt(1 + es * cp2 * cp2 * rone_es)
        sp = Math.sin(projectionLatitude)
        sinp0 = sp / c
        phip0 = Math.asin(sinp0)
        cosp0 = Math.cos(phip0)
        sp *= e
        K = Math.log(Math.tan(MapMath.QUARTERPI + 0.5 * phip0)) - c * (
                Math.log(Math.tan(MapMath.QUARTERPI + 0.5 * projectionLatitude)) - hlf_e *
                Math.log((1.0 + sp) / (1.0 - sp)))
        kR = scaleFactor * Math.sqrt(one_es) / (1.0 - sp * sp)
    }

    override fun project(lam: Double, phi: Double, xy: Point2D.Double): Point2D.Double {
        val phip: Double
        val lamp: Double
        val phipp: Double
        val lampp: Double
        val sp: Double
        val cp: Double

        sp = e * Math.sin(phi)
        phip = 2.0 * Math.atan(Math.exp(c * (
                Math.log(Math.tan(MapMath.QUARTERPI + 0.5 * phi)) - hlf_e * Math.log((1.0 + sp) / (1.0 - sp)))
                + K)) - MapMath.HALFPI
        lamp = c * lam
        cp = Math.cos(phip)
        phipp = Math.asin(cosp0 * Math.sin(phip) - sinp0 * cp * Math.cos(lamp))
        lampp = Math.asin(cp * Math.sin(lamp) / Math.cos(phipp))
        xy.x = kR * lampp
        xy.y = kR * Math.log(Math.tan(MapMath.QUARTERPI + 0.5 * phipp))
        return xy
    }

    override fun projectInverse(x: Double, y: Double, lp: Point2D.Double): Point2D.Double {
        var phip: Double
        val lamp: Double
        val phipp: Double
        val lampp: Double
        val cp: Double
        var esp: Double
        val con: Double
        var delp: Double
        var i: Int

        phipp = 2.0 * (Math.atan(Math.exp(y / kR)) - MapMath.QUARTERPI)
        lampp = x / kR
        cp = Math.cos(phipp)
        phip = Math.asin(cosp0 * Math.sin(phipp) + sinp0 * cp * Math.cos(lampp))
        lamp = Math.asin(cp * Math.sin(lampp) / Math.cos(phip))
        con = (K - Math.log(Math.tan(MapMath.QUARTERPI + 0.5 * phip))) / c
        i = NITER
        while (i > 0) {
            esp = e * Math.sin(phip)
            delp = (con + Math.log(Math.tan(MapMath.QUARTERPI + 0.5 * phip)) - hlf_e *
                    Math.log((1.0 + esp) / (1.0 - esp))) *
                    (1.0 - esp * esp) * Math.cos(phip) * rone_es
            phip -= delp
            if (Math.abs(delp) < EPS10) break
            i--
        }
        // TODO error was set in C on this condition
        if (i == 0) {
        }

        lp.y = phip
        lp.x = lamp / c

        return lp
    }

    override fun hasInverse(): Boolean {
        return true
    }

    override fun toString(): String {
        // For CH1903
        return "Swiss Oblique Mercator"
    }
}