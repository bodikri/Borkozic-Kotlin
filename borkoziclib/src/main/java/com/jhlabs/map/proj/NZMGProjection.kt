package com.jhlabs.map.proj

import com.jhlabs.Point2D
import com.jhlabs.map.MapMath

class NZMGProjection : Projection() {
    companion object {
        private const val SEC5_TO_RAD = 0.4848136811095359935899141023
        private const val RAD_TO_SEC5 = 2.062648062470963551564733573

        private val bf = arrayOf(
            doubleArrayOf(.7557853228, 0.0),
            doubleArrayOf(.249204646, .003371507),
            doubleArrayOf(-.001541739, .041058560),
            doubleArrayOf(-.10162907, .01727609),
            doubleArrayOf(-.26623489, -.36249218),
            doubleArrayOf(-.6870983, -1.1651967)
        )
        
        private val tphi = doubleArrayOf(
            1.5627014243, .5185406398, -.03333098, -.1052906, -.0368594,
            .007317, .01220, .00394, -.0013
        )
        
        private val tpsi = doubleArrayOf(
            .6399175073, -.1358797613, .063294409, -.02526853, .0117879,
            -.0055161, .0026906, -.001333, .00067, -.00034
        )
    }

    override fun initialize() {
        // force to International major axis
        equatorRadius = 6378388.0
        super.initialize()
        //ra = 1.0 / a
        projectionLongitude = DTR * 173.0
        projectionLatitude = DTR * -41.0
        falseEasting = 2510000.0
        falseNorthing = 6023150.0
    }

    override fun project(lam: Double, phi: Double, xy: Point2D.Double): Point2D.Double {
        val p = DoubleArray(2)

        var phiAdjusted = (phi - projectionLatitude) * RAD_TO_SEC5
        p[0] = tpsi[tpsi.size - 1]
        for (i in tpsi.size - 2 downTo 0) {
            p[0] = tpsi[i] + phiAdjusted * p[0]
        }
        p[0] *= phiAdjusted
        p[1] = lam
        val result = MapMath.zpoly1(p, bf, bf.size)
        xy.x = result[1]
        xy.y = result[0]
        return xy
    }

    override fun projectInverse(x: Double, y: Double, lp: Point2D.Double): Point2D.Double {
        var nn: Int
        var i: Int
        val p = DoubleArray(2)
        val fp = DoubleArray(2)
        val dp = DoubleArray(2)
        var den: Double

        p[0] = y
        p[1] = x
        nn = 20
        while (nn > 0) {
            val f = MapMath.zpolyd1(p, bf, bf.size, fp)
            f[0] -= y
            f[1] -= x
            den = fp[0] * fp[0] + fp[1] * fp[1]
            val rhs0 = -(f[0] * fp[0] + f[1] * fp[1]) / den
            val rhs1 = -(f[1] * fp[0] - f[0] * fp[1]) / den
            dp[0] = rhs0
            dp[1] = rhs1
            p[0] += rhs0
            p[1] += rhs1
            if (Math.abs(dp[0]) + Math.abs(dp[1]) <= EPS10)
                break
            nn--
        }
        lp.x = p[1]
        lp.y = tphi[tphi.size - 1]
        i = tphi.size - 2
        while (i >= 0) {
            lp.y = tphi[i] + p[0] * lp.y
            i--
        }
        lp.y = projectionLatitude + p[0] * lp.y * SEC5_TO_RAD
        return lp
    }

    override fun hasInverse(): Boolean {
        return true
    }

    override fun toString(): String {
        return "New Zealand Map Grid"
    }
}