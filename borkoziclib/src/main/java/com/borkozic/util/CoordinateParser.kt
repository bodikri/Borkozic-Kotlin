/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2012  Andrey Novikov <http://andreynovikov.info/>
 *
 * This file is part of Androzic application.
 *
 * Androzic is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * Androzic is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with Androzic.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.borkozic.util

import com.jhlabs.map.GeodeticPosition
import com.jhlabs.map.ReferenceException
import com.jhlabs.map.UTMReference
import java.util.regex.Pattern

object CoordinateParser {
    /*
     *  -- DMS --
     * 	45:26:46N,          65:56:55W
     *	45:26:46.302N,      65:56:55.903W
     *	45°26'21"N,         65°58'36"W
     *  45°26'21.291"N,     65°58'36.012"W
     *  45° 26' 21.291" N,  65° 58' 36.012" W
     *  45°26'21",         -65°58'36"
     *  45°26'21.291",     -65°58'36.012"
     *  45° 26' 21.291",   -65° 58' 36.012"
     *	45N26 21,           65W58 36
     *  45N26 21.015,       65W58 36.289
     *  -- DM --
     *	45°26'N,            65°58'36"W
     *	45°26.7717'N,       65°58.0127'W
     *	45° 26.7717' N,     65° 58.0127' W
     *	45°26',            -65°58'
     *	45°26.7717',       -65°58.0127'
     *	45° 26.7717',      -65° 58.0127'
     *  -- D --
     * N45.446195,         W65.948862
     *	45.446195N,         65.948862W
     *	45.446195,         -65.948862
     * -- UTM --
     *  37U 414703 6186238
     */
    @JvmStatic
    fun parse(string: String?): DoubleArray {
        if (string == null)
            throw IllegalArgumentException("Empty string")

        val c = doubleArrayOf(Double.NaN, Double.NaN)

        // 45:26:46N, 65:56:55W
        // 45:26:46.302N, 65:56:55.903W
        var ps = "(\\d{1,2}):(\\d{2}):(\\d{2}(?:\\.\\d+)?)([NS])\\s*,?\\s+(\\d{1,3}):(\\d{2}):(\\d{2}(?:\\.\\d+)?)([EW])"
        var p = Pattern.compile(ps)
        var m = p.matcher(string)
        if (m.find()) {
            val deg1 = m.group(1)!!
            val min1 = m.group(2)!!
            val sec1 = m.group(3)!!
            val dir1 = m.group(4)!!
            c[0] = deg1.toDouble() + min1.toDouble() / 60 + sec1.toDouble() / 3600
            if ("S" == dir1)
                c[0] = -c[0]
            val deg2 = m.group(5)!!
            val min2 = m.group(6)!!
            val sec2 = m.group(7)!!
            val dir2 = m.group(8)!!
            c[1] = deg2.toDouble() + min2.toDouble() / 60 + sec2.toDouble() / 3600
            if ("W" == dir2)
                c[1] = -c[1]
            return c
        }
        // 45°26'21"N, 65°58'36"W
        // 45°26'21.291"N, 65°58'36.012"W
        // 45° 26' 21.291" N, 65° 58' 36.012" W
        ps = "(\\d{1,2})°\\s?(\\d{2})'\\s?(\\d{2}(?:\\.\\d+)?)\"\\s?([NS])\\s*,?\\s+(\\d{1,3})°\\s?(\\d{2})'\\s?(\\d{2}(?:\\.\\d+)?)\"\\s?([EW])"
        p = Pattern.compile(ps)
        m = p.matcher(string)
        if (m.find()) {
            val deg1 = m.group(1)!!
            val min1 = m.group(2)!!
            val sec1 = m.group(3)!!
            val dir1 = m.group(4)!!
            c[0] = deg1.toDouble() + min1.toDouble() / 60 + sec1.toDouble() / 3600
            if ("S" == dir1)
                c[0] = -c[0]
            val deg2 = m.group(5)!!
            val min2 = m.group(6)!!
            val sec2 = m.group(7)!!
            val dir2 = m.group(8)!!
            c[1] = deg2.toDouble() + min2.toDouble() / 60 + sec2.toDouble() / 3600
            if ("W" == dir2)
                c[1] = -c[1]
            return c
        }
        // 45°26'21",         -65°58'36"
        // 45°26'21.291",     -65°58'36.012"
        // 45° 26' 21.291",   -65° 58' 36.012"
        ps = "(\\-)?(\\d{1,2})°\\s?(\\d{2})'\\s?(\\d{2}(?:\\.\\d+)?)\"\\s*,?\\s+(\\-)?(\\d{1,3})°\\s?(\\d{2})'\\s?(\\d{2}(?:\\.\\d+)?)\""
        p = Pattern.compile(ps)
        m = p.matcher(string)
        if (m.find()) {
            val sgn1 = m.group(1)
            val deg1 = m.group(2)!!
            val min1 = m.group(3)!!
            val sec1 = m.group(4)!!
            c[0] = deg1.toDouble() + min1.toDouble() / 60 + sec1.toDouble() / 3600
            if ("-" == sgn1)
                c[0] = -c[0]
            val sgn2 = m.group(5)
            val deg2 = m.group(6)!!
            val min2 = m.group(7)!!
            val sec2 = m.group(8)!!
            c[1] = deg2.toDouble() + min2.toDouble() / 60 + sec2.toDouble() / 3600
            if ("-" == sgn2)
                c[1] = -c[1]
            return c
        }
        // 45N26 21, 65W58 36
        // 45N26 21.015, 65W58 36.289
        ps = "(\\d{1,2})([NS])(\\d{2})\\s(\\d{1,2}(?:\\.\\d+)?)\\s*,?\\s+(\\d{1,3})([EW])(\\d{2})\\s(\\d{1,2}(?:\\.\\d+)?)"
        p = Pattern.compile(ps)
        m = p.matcher(string)
        if (m.find()) {
            val deg1 = m.group(1)!!
            val min1 = m.group(3)!!
            val sec1 = m.group(4)!!
            val dir1 = m.group(2)!!
            c[0] = deg1.toDouble() + min1.toDouble() / 60 + sec1.toDouble() / 3600
            if ("S" == dir1)
                c[0] = -c[0]
            val deg2 = m.group(5)!!
            val min2 = m.group(7)!!
            val sec2 = m.group(8)!!
            val dir2 = m.group(6)!!
            c[1] = deg2.toDouble() + min2.toDouble() / 60 + sec2.toDouble() / 3600
            if ("W" == dir2)
                c[1] = -c[1]
            return c
        }
        // 45°26'N, 65°58'36"W
        // 45°26.7717'N, 65°58.0127'W
        // 45° 26.7717' N, 65° 58.0127' W
        ps = "(\\d{1,2})°\\s?(\\d{2}(?:\\.\\d+)?)'\\s?([NS])\\s*,?\\s+(\\d{1,3})°\\s?(\\d{2}(?:\\.\\d+)?)'\\s?([EW])"
        p = Pattern.compile(ps)
        m = p.matcher(string)
        if (m.find()) {
            val deg1 = m.group(1)!!
            val min1 = m.group(2)!!
            val dir1 = m.group(3)!!
            c[0] = deg1.toDouble() + min1.toDouble() / 60
            if ("S" == dir1)
                c[0] = -c[0]
            val deg2 = m.group(4)!!
            val min2 = m.group(5)!!
            val dir2 = m.group(6)!!
            c[1] = deg2.toDouble() + min2.toDouble() / 60
            if ("W" == dir2)
                c[1] = -c[1]
            return c
        }
        // 45°26', -65°58'
        // 45°26.7717', -65°58.0127'
        // 45° 26.7717', -65° 58.0127'
        ps = "(\\-)?(\\d{1,2})°\\s?(\\d{2}(?:\\.\\d+)?)'\\s*,?\\s+(\\-)?(\\d{1,3})°\\s?(\\d{2}(?:\\.\\d+)?)'"
        p = Pattern.compile(ps)
        m = p.matcher(string)
        if (m.find()) {
            val sgn1 = m.group(1)
            val deg1 = m.group(2)!!
            val min1 = m.group(3)!!
            c[0] = deg1.toDouble() + min1.toDouble() / 60
            if ("-" == sgn1)
                c[0] = -c[0]
            val sgn2 = m.group(4)
            val deg2 = m.group(5)!!
            val min2 = m.group(6)!!
            c[1] = deg2.toDouble() + min2.toDouble() / 60
            if ("-" == sgn2)
                c[1] = -c[1]
            return c
        }
        // N45.446195, W65.948862
        ps = "([NS])(\\d{1,2}\\.\\d+)\\s*,?\\s+([EW])(\\d{1,3}\\.\\d+)"
        p = Pattern.compile(ps)
        m = p.matcher(string)
        if (m.find()) {
            val dir1 = m.group(1)!!
            val deg1 = m.group(2)!!
            c[0] = deg1.toDouble()
            if ("S" == dir1)
                c[0] = -c[0]
            val dir2 = m.group(3)!!
            val deg2 = m.group(4)!!
            c[1] = deg2.toDouble()
            if ("W" == dir2)
                c[1] = -c[1]
            return c
        }
        // 45.446195N, 65.948862W
        ps = "(\\d{1,2}\\.\\d+)([NS])\\s*,?\\s+(\\d{1,3}\\.\\d+)([EW])"
        p = Pattern.compile(ps)
        m = p.matcher(string)
        if (m.find()) {
            val deg1 = m.group(1)!!
            val dir1 = m.group(2)!!
            c[0] = deg1.toDouble()
            if ("S" == dir1)
                c[0] = -c[0]
            val deg2 = m.group(3)!!
            val dir2 = m.group(4)!!
            c[1] = deg2.toDouble()
            if ("W" == dir2)
                c[1] = -c[1]
            return c
        }
        // 45.446195, -65.948862
        ps = "(\\-?\\d{1,2}\\.\\d+)\\s*,?\\s+(\\-?\\d{1,3}\\.\\d+)"
        p = Pattern.compile(ps)
        m = p.matcher(string)
        if (m.find()) {
            val deg1 = m.group(1)!!
            c[0] = deg1.toDouble()
            val deg2 = m.group(2)!!
            c[1] = deg2.toDouble()
            return c
        }
        // 37U 414703 6186238
        ps = "(\\d{1,2})([A-HJ-NP-Z])\\s(\\d+)\\s(\\d+)"
        p = Pattern.compile(ps)
        m = p.matcher(string)
        if (m.find()) {
            val zon = m.group(1)!!
            val bnd = m.group(2)!!
            val est = m.group(3)!!
            val nrt = m.group(4)!!
            val zone = zon.toInt()
            val easting = est.toDouble()
            val northing = nrt.toDouble()
            try {
                val utm = UTMReference(zone, bnd[0], easting, northing)
                val pos: GeodeticPosition = utm.toLatLng()
                c[0] = pos.lat
                c[1] = pos.lon
                return c
            } catch (_: ReferenceException) {
            }
        }
        return c
    }
}
