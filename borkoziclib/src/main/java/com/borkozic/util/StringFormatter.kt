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
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sign

object StringFormatter {

    // http://code.google.com/p/android/issues/detail?id=2626
    private val coordDegFormat = DecimalFormat("#0.000000", DecimalFormatSymbols(Locale.ENGLISH))
    private val coordIntFormat = DecimalFormat("00", DecimalFormatSymbols(Locale.ENGLISH))
    private val coordMinFormat = DecimalFormat("00.0000", DecimalFormatSymbols(Locale.ENGLISH))
    private val coordSecFormat = DecimalFormat("00.000", DecimalFormatSymbols(Locale.ENGLISH))

    private val timeFormat = DecimalFormat("00")

    @JvmField
    var distanceFactor = 1.0
    @JvmField
    var distanceAbbr = "km"
    @JvmField
    var distanceShortFactor = 1.0
    @JvmField
    var distanceShortAbbr = "m"

    @JvmField
    var elevationFormat = "%.0f"
    @JvmField
    var elevationFactor = 1.0
    @JvmField
    var elevationAbbr = "m"

    @JvmField
    var angleFormat = "%.0f"
    @JvmField
    var angleFactor = 1.0
    @JvmField
    var angleAbbr: String? = null

    // FIXME Should localize:
    @JvmField
    var secondAbbr = "sec"
    @JvmField
    var minuteAbbr = "min"
    @JvmField
    var hourAbbr = "h"

    @JvmStatic
    fun distanceH(distance: Double): String {
        return distanceH(distance, 2000)
    }

    @JvmStatic
    fun distanceH(distance: Double, threshold: Int): String {
        val dist = distanceC(distance, threshold)
        return "${dist[0]} ${dist[1]}"
    }

    @JvmStatic
    fun distanceH(distance: Double, format: String): String {
        return distanceH(distance, format, 2000)
    }

    @JvmStatic
    fun distanceH(distance: Double, format: String, threshold: Int): String {
        val dist = distanceC(distance, format, threshold)
        return "${dist[0]} ${dist[1]}"
    }

    @JvmStatic
    fun distanceC(distance: Double): Array<String> {
        return distanceC(distance, 2000)
    }

    @JvmStatic
    fun distanceC(distance: Double, threshold: Int): Array<String> {
        return distanceC(distance, "%.0f", threshold)
    }

    @JvmStatic
    fun distanceC(distance: Double, format: String): Array<String> {
        return distanceC(distance, format, 2000)
    }

    @JvmStatic
    fun distanceC(distance: Double, format: String, threshold: Int): Array<String> {
        var dist = distance * distanceShortFactor
        var distunit = distanceShortAbbr
        if (abs(dist) > threshold) {
            dist = dist / distanceShortFactor / 1000 * distanceFactor
            distunit = distanceAbbr
        }

        return arrayOf(String.format(format, dist), distunit)
    }

    @JvmStatic
    fun elevationH(elevation: Double): String {
        return "${elevationC(elevation)} $elevationAbbr"
    }

    @JvmStatic
    fun elevationC(elevation: Double): String {
        return String.format(elevationFormat, elevation * elevationFactor) + elevationAbbr
    }

    @JvmStatic
    fun angleH(angle: Double): String {
        if (angleFactor == 1.0) {
            // Special case for degrees: use symbol instead of abbreviation
            return String.format(angleFormat, angle) + "\u00B0"
        } else {
            return "${angleC(angle)} $angleAbbr"
        }
    }

    @JvmStatic
    fun angleC(angle: Double): String {
        return String.format(angleFormat, angle / angleFactor)
    }

    @JvmStatic
    fun coordinate(format: Int, coordinate: Double): String {
        return when (format) {
            0 -> coordDegFormat.format(coordinate)
            1 -> {
                val sign = sign(coordinate)
                val coord = abs(coordinate)
                val degrees = floor(coord).toInt()
                val minutes = (coord - degrees) * 60
                "${coordIntFormat.format(sign * degrees)}° ${coordMinFormat.format(minutes)}'"
            }
            2 -> {
                val sign = sign(coordinate)
                val coord = abs(coordinate)
                val degrees = floor(coord).toInt()
                val min = (coord - degrees) * 60
                val minutes = floor(min).toInt()
                val seconds = (min - minutes) * 60
                "${coordIntFormat.format(sign * degrees)}° ${coordIntFormat.format(minutes)}' ${coordSecFormat.format(seconds)}\""
            }
            else -> coordinate.toString()
        }
    }

    @JvmStatic
    fun coordinates(format: Int, delimeter: String, latitude: Double, longitude: Double): String {
        return when (format) {
            0, 1, 2 -> "${coordinate(format, latitude)}$delimeter${coordinate(format, longitude)}"
            3 -> {
                try {
                    UTMReference.toUTMRefString(GeodeticPosition(latitude, longitude))
                } catch (_: ReferenceException) {
                }
                latitude.toString() + delimeter + longitude.toString()
            }
            else -> latitude.toString() + delimeter + longitude.toString()
        }
    }

    @JvmStatic
    fun bearingH(bearing: Double): String {
        return String.format("%.0f", bearing) + "°"
    }

    @JvmStatic
    fun bearingSimpleH(bearing: Double): String {
        return when {
            bearing < 22 || bearing >= 338 -> "N"
            bearing < 67 -> "NE"
            bearing < 112 -> "E"
            bearing < 158 -> "SE"
            bearing < 202 -> "S"
            bearing < 248 -> "SW"
            bearing < 292 -> "W"
            bearing < 338 -> "NW"
            else -> "."
        }
    }

    @JvmStatic
    fun timeH(minutes: Int): String {
        val time = timeC(minutes)
        return "${time[0]} ${time[1]}"
    }

    @JvmStatic
    fun timeHSec(seconds: Int): String {
        val time = timeSec(seconds)
        return "${time[0]} ${time[1]}"
    }

    @JvmStatic
    fun timeHP(seconds: Int, timeout: Int): String {
        val time = timeCP(seconds, timeout)
        return "${time[0]} ${time[1]}"
    }

    /**
     * Formats time period in four ways:<br/>
     * "< 1 min" - for 1 minute<br/>
     * "12 min" - for period less than 1 hour<br/>
     * "1:53 min" - for period more than 1 hour<br/>
     * "> 24 h" - for period more than 1 day
     *
     * @param minutes time in minutes
     * @return Time period
     */
    @JvmStatic
    fun timeC(minutes: Int): Array<String> {
        var hour = 0
        var min = minutes

        if (min <= 1)
            return arrayOf("< 1", minuteAbbr)

        if (min > 59) {
            hour = floor(min / 60.0).toInt()
            min = min - hour * 60
        }
        if (hour > 23)
            return arrayOf("> 24", hourAbbr)

        return arrayOf("${timeFormat.format(hour)}:${timeFormat.format(min)}", minuteAbbr)
    }

    /**
     * Formats time period in three ways:<br/>
     * "07:33 sec" - for period less than 1 hour<br/>
     * "1:53 min" - for period more than 1 hour<br/>
     * "> 24 h" - for period more than 1 day
     *
     * @param seconds time in seconds
     * @return Time period
     */
    @JvmStatic
    fun timeSec(seconds: Int): Array<String> {
        var hour = 0
        var min = 0
        val sec = seconds

        if (sec <= 3599) {
            min = floor(seconds / 60.0).toInt()
            val secRem = sec - min * 60
            return arrayOf("${timeFormat.format(min)}:${timeFormat.format(secRem)}", secondAbbr)
        }
        if (sec > 3599) {
            hour = floor(sec / 3600.0).toInt()
            min = floor(sec / 60.0).toInt()
            min = min - hour * 60
        }
        if (sec > 82800) // >23h
            return arrayOf("> 24", hourAbbr)

        return arrayOf("${timeFormat.format(hour)}:${timeFormat.format(min)}", minuteAbbr)
    }

    /**
     * Formats time period in three ways:<br/>
     * "12 sec" - for period less than 1 minute<br/>
     * "34 min" - for period more than 1 minute<br/>
     * "> 40 min" - for period more than timeout (where 40 is timeout)
     *
     * @param seconds time period in seconds
     * @param timeout timeout in seconds
     * @return Time period
     */
    @JvmStatic
    fun timeCP(seconds: Int, timeout: Int): Array<String> {
        val sec = seconds
        var min = 0
        val t = sec > timeout

        System.err.print("CP $seconds $timeout")
        if (sec <= 59) {
            return if (t)
                arrayOf("> $timeout", secondAbbr)
            else
                arrayOf("$sec", secondAbbr)
        }
        min = floor(sec / 60.0).toInt()
        if (t) {
            min = floor(timeout / 60.0).toInt()
            return arrayOf("> $min", minuteAbbr)
        } else
            return arrayOf("$min", minuteAbbr)
    }

    @JvmStatic
    fun timeR(minutes: Int): String {
        var hour = 0
        var min = minutes

        if (min > 59) {
            hour = floor(min / 60.0).toInt()
            min = min - hour * 60
        }
        if (hour > 99) {
            return "--:--"
        }

        return "${timeFormat.format(hour)}:${timeFormat.format(min)}"
    }
}
