package com.borkozic.util

/*
 * Copyright 2008-2009 Mike Reedell / LuckyCatLabs.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * 2010 Full redesign by Andrey Novikov to make it ten times faster and easier to use
 */

import java.util.Calendar
import android.location.Location

object Astro {

    /**
     * Enumerated type that defines the available zeniths for computing the sunrise/sunset.
     */
    enum class Zenith(val degrees: Double) {
        /** Astronomical sunrise/set is when the sun is 18 degrees below the horizon. */
        ASTRONOMICAL(108.0),

        /** Nautical sunrise/set is when the sun is 12 degrees below the horizon. */
        NAUTICAL(102.0),

        /** Civil sunrise/set (dawn/dusk) is when the sun is 6 degrees below the horizon. */
        CIVIL(96.0),

        /** Official sunrise/set is when the sun is 50' below the horizon. */
        OFFICIAL(90.8333); // 90deg, 50'

        fun degrees(): Double = degrees
    }

    /**
     * Checks if it is daytime for a time contained in `date` at the given location and date.
     */
    @JvmStatic
    fun isDaytime(solarZenith: Zenith, location: Location, date: Calendar): Boolean {
        val sunrise = computeSunriseTime(solarZenith, location, date)
        val sunset = computeSunsetTime(solarZenith, location, date)
        val now = date.get(Calendar.HOUR_OF_DAY).toDouble() + date.get(Calendar.MINUTE) / 60.0
        if (!sunrise.isNaN() && !sunset.isNaN()) {
            if (now < sunrise || now > sunset) {
                return false
            }
        }
        return true
    }

    @JvmStatic
    fun computeSunriseTime(solarZenith: Zenith, location: Location, date: Calendar): Double {
        return computeSolarEventTime(solarZenith, location, date, true)
    }

    @JvmStatic
    fun computeSunsetTime(solarZenith: Zenith, location: Location, date: Calendar): Double {
        return computeSolarEventTime(solarZenith, location, date, false)
    }

    private fun computeSolarEventTime(
        solarZenith: Zenith, location: Location, date: Calendar, isSunrise: Boolean
    ): Double {
        val longitudeHour = getLongitudeHour(location, date, isSunrise)

        val meanAnomaly = getMeanAnomaly(longitudeHour)
        val sunTrueLong = getSunTrueLongitude(meanAnomaly)
        val cosineSunLocalHour = getCosineSunLocalHour(sunTrueLong, solarZenith, location)
        if (cosineSunLocalHour < -1.0 || cosineSunLocalHour > 1.0) {
            return Double.NaN
        }

        val sunLocalHour = getSunLocalHour(cosineSunLocalHour, isSunrise)
        val localMeanTime = getLocalMeanTime(sunTrueLong, longitudeHour, sunLocalHour)
        val localTime = getLocalTime(localMeanTime, location, date)
        return localTime
    }

    private fun getBaseLongitudeHour(location: Location): Double {
        return location.longitude / 15.0
    }

    private fun getLongitudeHour(location: Location, date: Calendar, isSunrise: Boolean): Double {
        val offset = if (isSunrise) 6 else 18
        return getDayOfYear(date) + (offset - getBaseLongitudeHour(location)) / 24.0
    }

    private fun getMeanAnomaly(longitudeHour: Double): Double {
        return 0.9856 * longitudeHour - 3.289
    }

    private fun getSunTrueLongitude(meanAnomaly: Double): Double {
        val sinMeanAnomaly = Math.sin(Math.toRadians(meanAnomaly))
        val sinDoubleMeanAnomaly = Math.sin(Math.toRadians(meanAnomaly) * 2)

        val firstPart = meanAnomaly + (sinMeanAnomaly * 1.916)
        val secondPart = sinDoubleMeanAnomaly * 0.02 + 282.634
        var trueLongitude = firstPart + secondPart

        if (trueLongitude > 360.0) {
            trueLongitude -= 360.0
        }
        return trueLongitude
    }

    private fun getRightAscension(sunTrueLong: Double): Double {
        val tanL = Math.tan(Math.toRadians(sunTrueLong))

        val innerParens = Math.toDegrees(tanL) * 0.91764
        var rightAscension = Math.atan(Math.toRadians(innerParens))
        rightAscension = Math.toDegrees(rightAscension)

        if (rightAscension < 0) {
            rightAscension += 360.0
        } else if (rightAscension > 360) {
            rightAscension -= 360.0
        }

        val longitudeQuadrant = Math.floor(sunTrueLong / 90.0) * 90.0
        val rightAscensionQuadrant = Math.floor(rightAscension / 90.0) * 90.0

        return (rightAscension + longitudeQuadrant - rightAscensionQuadrant) / 15.0
    }

    private fun getCosineSunLocalHour(sunTrueLong: Double, zenith: Zenith, location: Location): Double {
        val sinSunDeclination = getSinOfSunDeclination(sunTrueLong)
        val cosineSunDeclination = getCosineOfSunDeclination(sinSunDeclination)

        val cosineZenith = Math.cos(Math.toRadians(zenith.degrees))
        val sinLatitude = Math.sin(Math.toRadians(location.latitude))
        val cosLatitude = Math.cos(Math.toRadians(location.latitude))

        val sinDeclinationTimesSinLat = sinSunDeclination * sinLatitude
        return (cosineZenith - sinDeclinationTimesSinLat) / (cosineSunDeclination * cosLatitude)
    }

    private fun getSinOfSunDeclination(sunTrueLong: Double): Double {
        return Math.sin(Math.toRadians(sunTrueLong)) * 0.39782
    }

    private fun getCosineOfSunDeclination(sinSunDeclination: Double): Double {
        return Math.cos(Math.asin(sinSunDeclination))
    }

    private fun getSunLocalHour(cosineSunLocalHour: Double, isSunrise: Boolean): Double {
        var localHour = Math.toDegrees(Math.acos(cosineSunLocalHour))
        if (isSunrise) {
            localHour = 360.0 - localHour
        }
        return localHour / 15.0
    }

    private fun getLocalMeanTime(sunTrueLong: Double, longitudeHour: Double, sunLocalHour: Double): Double {
        var localMeanTime = sunLocalHour + getRightAscension(sunTrueLong) - longitudeHour * 0.06571 - 6.622
        if (localMeanTime < 0) {
            localMeanTime += 24.0
        } else if (localMeanTime > 24) {
            localMeanTime -= 24.0
        }
        return localMeanTime
    }

    private fun getLocalTime(localMeanTime: Double, location: Location, date: Calendar): Double {
        val utcTime = localMeanTime - getBaseLongitudeHour(location)
        val utcOffSet = getUTCOffSet(date)
        val utcOffSetTime = utcTime + utcOffSet
        return adjustForDST(utcOffSetTime, date)
    }

    private fun adjustForDST(localMeanTime: Double, date: Calendar): Double {
        var localTime = localMeanTime
        if (date.timeZone.inDaylightTime(date.time)) {
            localTime++
        }
        if (localTime > 24.0) {
            localTime -= 24.0
        }
        return localTime
    }

    @JvmStatic
    fun getLocalTimeAsString(localTime: Double): String {
        var hour = Math.floor(localTime).toInt()
        var minutes = Math.round((localTime - hour) * 60).toInt()

        if (minutes == 60) {
            minutes = 0
            hour++
        }

        val minuteString = if (minutes < 10) "0${minutes}" else "$minutes"
        val hourString = if (hour < 10) "0${hour}" else "$hour"
        return "$hourString:$minuteString"
    }

    private fun getDayOfYear(date: Calendar): Int {
        return date.get(Calendar.DAY_OF_YEAR)
    }

    private fun getUTCOffSet(date: Calendar): Double {
        return date.get(Calendar.ZONE_OFFSET) / 3600000.0
    }
}
