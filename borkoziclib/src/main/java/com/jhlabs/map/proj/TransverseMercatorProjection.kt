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
/**
 * This file was semi-automatically converted from the public-domain USGS PROJ source.
 * 
 * Bernhard Jenny, February 2 2010: Corrected code for spherical case in
 * projectInverse, added isConformal.
 * 27 September 2010: added missing tests to forward spherical, removed
 * initialization code in constructor.
 */
package com.jhlabs.map.proj

import com.jhlabs.Point2D
import com.jhlabs.map.Ellipsoid
import com.jhlabs.map.MapMath
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt

/**
 * Transverse Mercator Projection algorithm is taken from the USGS PROJ package.
 */
open class TransverseMercatorProjection : CylindricalProjection {
    private var esp = 0.0
    private var ml0 = 0.0
    private var en: DoubleArray? = null
    protected var utmzone: Int = -1

    constructor()

    /**
     * Set up a projection suitable for State Plane Coordinates.
     */
    constructor(
        ellipsoid: Ellipsoid,
        lon_0: Double,
        lat_0: Double,
        k: Double,
        x_0: Double,
        y_0: Double
    ) {
        setEllipsoid(ellipsoid)
        projectionLongitude = lon_0
        projectionLatitude = lat_0
        scaleFactor = k
        falseEasting = x_0
        falseNorthing = y_0
    }

    override fun clone(): Any {
        val p = super.clone() as TransverseMercatorProjection
        if (en != null) {
            p.en = en!!.clone() as DoubleArray?
        }
        return p
    }

    public override fun initialize() {
        super.initialize()
        if (spherical) {
            esp = scaleFactor
            ml0 = .5 * esp
        } else {
            en = MapMath.enfn(es)
            ml0 = MapMath.mlfn(
                projectionLatitude,
                sin(projectionLatitude),
                cos(projectionLatitude),
                en
            )
            esp = es / (1.0 - es)
        }
    }

    fun getRowFromNearestParallel(latitude: Double): Int {
        val degrees = MapMath.radToDeg(MapMath.normalizeLatitude(latitude)).toInt()
        if (degrees < -80 || degrees > 84) {
            return 0
        }
        if (degrees > 80) {
            return 24
        }
        return (degrees + 80) / 8 + 3
    }

    fun getZoneFromNearestMeridian(longitude: Double): Int {
        val zone = (floor(((180.0 + longitude) / 6)) + 1).toInt()

        /*
        if( Lat >= 56.0 && Lat < 64.0 && LongTemp >= 3.0 && LongTemp < 12.0 )
    		ZoneNumber = 32;

		// Special zones for Svalbard
    	if( Lat >= 72.0 && Lat < 84.0 ) 
    	{
    	  if(      LongTemp >= 0.0  && LongTemp <  9.0 ) ZoneNumber = 31;
    	  else if( LongTemp >= 9.0  && LongTemp < 21.0 ) ZoneNumber = 33;
    	  else if( LongTemp >= 21.0 && LongTemp < 33.0 ) ZoneNumber = 35;
    	  else if( LongTemp >= 33.0 && LongTemp < 42.0 ) ZoneNumber = 37;
    	 }
    	 */
        return zone
    }

    fun clearUTMZone() {
        utmzone = -1
    }

    fun setUTMZone(zone: Int) {
        utmzone = zone - 1

        projectionLongitude = (utmzone * 6 - 180 + 3) * DTR //+3 puts origin in middle of zone
        projectionLatitude = 0.0
        scaleFactor = 0.9996
        falseNorthing = 0.0
        falseEasting = 500000.0
        //initialize();
    }

    public override fun project(lplam: Double, lpphi: Double, xy: Point2D.Double): Point2D.Double {
        if (spherical) {
            val cosphi = cos(lpphi)
            var b = cosphi * sin(lplam)
            if (abs(abs(b) - 1.0) <= EPS10) {
                throw ProjectionException("F_ERROR") // FIXME F_ERROR macro returns 0/0 and error -20
            }

            xy.x = ml0 * scaleFactor * ln((1.0 + b) / (1.0 - b))
            xy.y = cosphi * cos(lplam) / sqrt(1.0 - b * b)
            b = abs(xy.y)
            if (b >= 1.0) {
                if ((b - 1.0) > EPS10) {
                    throw ProjectionException("F_ERROR") // FIXME F_ERROR macro returns 0/0 and error -20
                } else {
                    xy.y = 0.0
                }
            } else {
                xy.y = MapMath.acos(xy.y)
            }
            if (lpphi < 0.0) {
                xy.y = -xy.y
            }
            xy.y = esp * (xy.y - projectionLatitude)
        } else {
            var al: Double
            val als: Double
            val n: Double
            var t: Double
            val sinphi = sin(lpphi)
            val cosphi = cos(lpphi)
            t = if (abs(cosphi) > 1e-10) sinphi / cosphi else 0.0
            t *= t
            al = cosphi * lplam
            als = al * al
            al /= sqrt(1.0 - es * sinphi * sinphi)
            n = esp * cosphi * cosphi
            xy.x = scaleFactor * al * (FC1
                    + FC3 * als * (1.0 - t + n
                    + FC5 * als * (5.0 + t * (t - 18.0) + n * (14.0 - 58.0 * t) + FC7 * als * (61.0 + t * (t * (179.0 - t) - 479.0)))))
            xy.y = scaleFactor * (MapMath.mlfn(lpphi, sinphi, cosphi, en) - ml0
                    + sinphi * al * lplam * FC2 * (1.0
                    + FC4 * als * (5.0 - t + n * (9.0 + 4.0 * n) + FC6 * als * (61.0 + t * (t - 58.0) + n * (270.0 - 330 * t) + FC8 * als * (1385.0 + t * (t * (543.0 - t) - 3111.0))))))
        }
        return xy
    }

    public override fun projectInverse(x: Double, y: Double, out: Point2D.Double): Point2D.Double {
        if (spherical) {
            /*
            Original code
            x = Math.exp(x / scaleFactor);
            y = .5 * (x - 1. / x);
            x = Math.cos(projectionLatitude + y / scaleFactor);
            out.y = MapMath.asin(Math.sqrt((1. - x * x) / (1. + y * y)));
            if (y < 0) {
            out.y = -out.y;
            }
            out.x = Math.atan2(y, x);
             */

            // new code by Bernhard Jenny, February 2 2010

            val D = y / scaleFactor + projectionLatitude
            val xp = x / scaleFactor

            out.y = asin(sin(D) / cosh(xp))
            out.x = atan2(sinh(xp), cos(D))
        } else {
            val n: Double
            var con: Double
            val cosphi: Double
            val d: Double
            val ds: Double
            val sinphi: Double
            var t: Double

            out.y = MapMath.inv_mlfn(ml0 + y / scaleFactor, es, en)
            if (abs(y) >= MapMath.HALFPI) {
                out.y = if (y < 0.0) -MapMath.HALFPI else MapMath.HALFPI
                out.x = 0.0
            } else {
                sinphi = sin(out.y)
                cosphi = cos(out.y)
                t = if (abs(cosphi) > 1e-10) sinphi / cosphi else 0.0
                n = esp * cosphi * cosphi
                d = x * sqrt((1.0 - es * sinphi * sinphi).also { con = it }) / scaleFactor
                con *= t
                t *= t
                ds = d * d
                out.y -= (con * ds / (1.0 - es)) * FC2 * (1.0
                        - ds * FC4 * (5.0 + t * (3.0 - 9.0 * n) + n * (1.0 - 4 * n)
                        - ds * FC6 * (61.0 + t * (90.0 - 252.0 * n
                        + 45.0 * t) + 46.0 * n
                        - ds * FC8 * (1385.0 + t * (3633.0 + t * (4095.0 + 1574.0 * t))))))
                out.x = d * (FC1
                        - ds * FC3 * (1.0 + 2.0 * t + n
                        - ds * FC5 * (5.0 + t * (28.0 + 24.0 * t + 8.0 * n) + 6.0 * n
                        - ds * FC7 * (61.0 + t * (662.0 + t * (1320.0 + 720.0 * t)))))) / cosphi
            }
        }
        return out
    }

    public override fun hasInverse(): Boolean {
        return true
    }

    override fun isConformal(): Boolean {
        return true
    }

    public override fun isRectilinear(): Boolean {
        return false
    }

    public override fun toString(): String {
        return "Transverse Mercator"
    }

    companion object {
        private const val FC1 = 1.0
        private const val FC2 = 0.5
        private const val FC3 = 0.16666666666666666666
        private const val FC4 = 0.08333333333333333333
        private const val FC5 = 0.05
        private const val FC6 = 0.03333333333333333333
        private const val FC7 = 0.02380952380952380952
        private const val FC8 = 0.01785714285714285714
    }
}
