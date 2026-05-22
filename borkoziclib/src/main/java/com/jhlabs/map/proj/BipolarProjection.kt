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
import kotlin.math.*

class BipolarProjection : Projection() {

    private var noskew = false

    private companion object {
        const val EPS = 1e-10
        const val ONEEPS = 1.000000001
        const val NITER = 10
        const val lamB = -.34894976726250681539
        const val n = .63055844881274687180
        const val F = 1.89724742567461030582
        const val Azab = .81650043674686363166
        const val Azba = 1.82261843856185925133
        const val T = 1.27246578267089012270
        const val rhoc = 1.20709121521568721927
        const val cAzc = .69691523038678375519
        const val sAzc = .71715351331143607555
        const val C45 = .70710678118654752469
        const val S45 = .70710678118654752410
        const val C20 = .93969262078590838411
        const val S20 = -.34202014332566873287
        const val R110 = 1.91986217719376253360
        const val R104 = 1.81514242207410275904
    }

    init {
        minLatitude = Math.toRadians(-80.0)
        maxLatitude = Math.toRadians(80.0)
        projectionLongitude = Math.toRadians(-90.0)
        minLongitude = Math.toRadians(-90.0)
        maxLongitude = Math.toRadians(90.0)
    }

    override fun project(lplam: Double, lpphi: Double, out: Point2D.Double): Point2D.Double {
        val cphi = cos(lpphi)
        val sphi = sin(lpphi)
        var sdlam = lamB - lplam
        var cdlam = cos(sdlam)
        sdlam = sin(sdlam)

        var Az: Double
        val tphi: Double
        if (abs(abs(lpphi) - MapMath.HALFPI) < EPS10) {
            Az = if (lpphi < 0.0) Math.PI else 0.0
            tphi = Double.MAX_VALUE
        } else {
            tphi = sphi / cphi
            Az = atan2(sdlam, C45 * (tphi - cdlam))
        }

        val tag = Az > Azba
        val Av: Double
        val z: Double
        if (tag) {
            sdlam = lplam + R110
            cdlam = cos(sdlam)
            sdlam = sin(sdlam)
            var z1 = S20 * sphi + C20 * cphi * cdlam
            z = when {
                abs(z1) > 1.0 -> {
                    if (abs(z1) > ONEEPS) throw ProjectionException("F")
                    else if (z1 < 0.0) -1.0 else 1.0
                }
                else -> acos(z1)
            }
            if (tphi != Double.MAX_VALUE)
                Az = atan2(sdlam, C20 * tphi - S20 * cdlam)
            Av = Azab
            out.y = rhoc
        } else {
            var z1 = S45 * (sphi + cphi * cdlam)
            z = when {
                abs(z1) > 1.0 -> {
                    if (abs(z1) > ONEEPS) throw ProjectionException("F")
                    else if (z1 < 0.0) -1.0 else 1.0
                }
                else -> acos(z1)
            }
            Av = Azba
            out.y = -rhoc
        }

        if (z < 0.0) throw ProjectionException("F")
        var r = F * tan(.5 * z).pow(n)
        val al0 = 0.5 * (R104 - z)
        if (al0 < 0.0) throw ProjectionException("F")
        var al = (tan(.5 * z).pow(n) + al0.pow(n)) / T
        al = when {
            abs(al) > 1.0 -> {
                if (abs(al) > ONEEPS) throw ProjectionException("F")
                else if (al < 0.0) -1.0 else 1.0
            }
            else -> acos(al)
        }
        val t = n * (Av - Az)
        if (abs(t) < al)
            r /= cos(al + if (tag) t else -t)
        out.x = r * sin(t)
        out.y += (if (tag) -r else r) * cos(t)

        if (noskew) {
            val tx = out.x
            out.x = -out.x * cAzc - out.y * sAzc
            out.y = -out.y * cAzc + tx * sAzc
        }
        return out
    }

    override fun projectInverse(xyx: Double, xyy: Double, out: Point2D.Double): Point2D.Double {
        var xyx = xyx
        var xyy = xyy
        if (noskew) {
            val t = xyx
            out.x = -xyx * cAzc + xyy * sAzc
            out.y = -xyy * cAzc - t * sAzc
        }

        val neg: Boolean
        val s: Double
        val c: Double
        val Av: Double
        if (xyx < 0.0) {
            neg = true
            out.y = rhoc - xyy
            s = S20
            c = C20
            Av = Azab
        } else {
            neg = false
            out.y += rhoc
            s = S45
            c = C45
            Av = Azba
        }

        val rp = MapMath.distance(xyx, xyy)
        var r = rp
        val Az = atan2(xyx, xyy)
        val fAz = abs(Az)
        var z = 0.0
        var rl = r

        var i = NITER
        while (i > 0) {
            z = 2.0 * atan((r / F).pow(1 / n))
            val al = acos((tan(.5 * z).pow(n) + tan(.5 * (R104 - z)).pow(n)) / T)
            if (fAz < al)
                r = rp * cos(al + if (neg) Az else -Az)
            if (abs(rl - r) < EPS)
                break
            rl = r
            i--
        }
        if (i == 0) throw ProjectionException("I")

        val Az2 = Av - Az / n
        out.y = asin(s * cos(z) + c * sin(z) * cos(Az2))
        out.x = atan2(sin(Az2), c / tan(z) - s * cos(Az2))
        if (neg)
            out.x -= R110
        else
            out.x = lamB - out.x
        return out
    }

    override fun hasInverse(): Boolean = true

    override fun initialize() {
        super.initialize()
        //noskew = pj_param(params, "bns").i;//FIXME
    }

    override fun toString(): String = "Bipolar Conic of Western Hemisphere"
}
