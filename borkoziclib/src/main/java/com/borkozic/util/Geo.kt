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

import kotlin.math.*

object Geo {

    @JvmStatic
    fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val a = 6378137.0
        val b = 6356752.314245
        val f = 1.0 / 298.257223563
        val L = Math.toRadians(lon2 - lon1)
        val U1 = atan((1 - f) * tan(Math.toRadians(lat1)))
        val U2 = atan((1 - f) * tan(Math.toRadians(lat2)))
        val sinU1 = sin(U1)
        val cosU1 = cos(U1)
        val sinU2 = sin(U2)
        val cosU2 = cos(U2)

        var lambda = L
        var lambdaP = 0.0
        var iterLimit = 100.0
        var sigma = 0.0
        var cosSqAlpha = 0.0
        var sinSigma = 0.0
        var cosSigma = 0.0
        var cos2SigmaM = 0.0

        do {
            val sinLambda = sin(lambda)
            val cosLambda = cos(lambda)
            sinSigma = sqrt((cosU2 * sinLambda) * (cosU2 * sinLambda) + (cosU1 * sinU2 - sinU1 * cosU2 * cosLambda) * (cosU1 * sinU2 - sinU1 * cosU2 * cosLambda))
            if (sinSigma == 0.0) return 0.0
            cosSigma = sinU1 * sinU2 + cosU1 * cosU2 * cosLambda
            sigma = atan2(sinSigma, cosSigma)
            val sinAlpha = cosU1 * cosU2 * sinLambda / sinSigma
            cosSqAlpha = 1 - sinAlpha * sinAlpha
            try {
                cos2SigmaM = cosSigma - 2 * sinU1 * sinU2 / cosSqAlpha
            } catch (_: ArithmeticException) {
                cos2SigmaM = 0.0
            }
            val C = f / 16 * cosSqAlpha * (4 + f * (4 - 3 * cosSqAlpha))
            lambdaP = lambda
            lambda = L + (1 - C) * f * sinAlpha * (sigma + C * sinSigma * (cos2SigmaM + C * cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM)))
        } while (abs(lambda - lambdaP) > 1e-12 && --iterLimit > 0)

        if (iterLimit == 0.0) return -1.0

        val uSq = cosSqAlpha * (a * a - b * b) / (b * b)
        val A = 1 + uSq / 16384 * (4096 + uSq * (-768 + uSq * (320 - 175 * uSq)))
        val B = uSq / 1024 * (256 + uSq * (-128 + uSq * (74 - 47 * uSq)))
        val deltaSigma = B * sinSigma * (cos2SigmaM + B / 4 * (cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM) - B / 6 * cos2SigmaM * (-3 + 4 * sinSigma * sinSigma) * (-3 + 4 * cos2SigmaM * cos2SigmaM)))
        val s = b * A * (sigma - deltaSigma)

        return s
    }

    @JvmStatic
    fun distanceBorko(lat1: Double, lat2: Double, lon1: Double, lon2: Double): Double {
        val R = 6378137
        val latDistance = Math.toRadians(lat2 - lat1)
        val lonDistance = Math.toRadians(lon2 - lon1)
        val a = sin(latDistance / 2) * sin(latDistance / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(lonDistance / 2) * sin(lonDistance / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }

    @JvmStatic
    fun distanceBorko(lat1: Double, lat2: Double, lon1: Double, lon2: Double, el1: Double, el2: Double): Double {
        val R = 6378137
        val latDistance = Math.toRadians(lat2 - lat1)
        val lonDistance = Math.toRadians(lon2 - lon1)
        val a = sin(latDistance / 2) * sin(latDistance / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(lonDistance / 2) * sin(lonDistance / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        var distance = R * c

        val height = el1 - el2
        distance = distance.pow(2.0) + height.pow(2.0)

        return sqrt(distance)
    }

    @JvmStatic
    fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double, el1: Double, el2: Double): Double {
        val a = 6378137.0
        val b = 6356752.314245
        val f = 1.0 / 298.257223563
        val L = Math.toRadians(lon2 - lon1)
        val U1 = atan((1 - f) * tan(Math.toRadians(lat1)))
        val U2 = atan((1 - f) * tan(Math.toRadians(lat2)))
        val sinU1 = sin(U1)
        val cosU1 = cos(U1)
        val sinU2 = sin(U2)
        val cosU2 = cos(U2)

        var lambda = L
        var lambdaP = 0.0
        var iterLimit = 100.0
        var sigma = 0.0
        var cosSqAlpha = 0.0
        var sinSigma = 0.0
        var cosSigma = 0.0
        var cos2SigmaM = 0.0

        do {
            val sinLambda = sin(lambda)
            val cosLambda = cos(lambda)
            sinSigma = sqrt((cosU2 * sinLambda) * (cosU2 * sinLambda) + (cosU1 * sinU2 - sinU1 * cosU2 * cosLambda) * (cosU1 * sinU2 - sinU1 * cosU2 * cosLambda))
            if (sinSigma == 0.0) return 0.0
            cosSigma = sinU1 * sinU2 + cosU1 * cosU2 * cosLambda
            sigma = atan2(sinSigma, cosSigma)
            val sinAlpha = cosU1 * cosU2 * sinLambda / sinSigma
            cosSqAlpha = 1 - sinAlpha * sinAlpha
            try {
                cos2SigmaM = cosSigma - 2 * sinU1 * sinU2 / cosSqAlpha
            } catch (_: ArithmeticException) {
                cos2SigmaM = 0.0
            }
            val C = f / 16 * cosSqAlpha * (4 + f * (4 - 3 * cosSqAlpha))
            lambdaP = lambda
            lambda = L + (1 - C) * f * sinAlpha * (sigma + C * sinSigma * (cos2SigmaM + C * cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM)))
        } while (abs(lambda - lambdaP) > 1e-12 && --iterLimit > 0)

        if (iterLimit == 0.0) return -1.0

        val uSq = cosSqAlpha * (a * a - b * b) / (b * b)
        val A = 1 + uSq / 16384 * (4096 + uSq * (-768 + uSq * (320 - 175 * uSq)))
        val B = uSq / 1024 * (256 + uSq * (-128 + uSq * (74 - 47 * uSq)))
        val deltaSigma = B * sinSigma * (cos2SigmaM + B / 4 * (cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM) - B / 6 * cos2SigmaM * (-3 + 4 * sinSigma * sinSigma) * (-3 + 4 * cos2SigmaM * cos2SigmaM)))
        val distance = b * A * (sigma - deltaSigma)
        val height = el1 - el2
        if (height == 0.0) return distance
        val slopedistance = distance.pow(2.0) + height.pow(2.0)

        return sqrt(slopedistance)
    }

    @JvmStatic
    fun SlopeAngle(lat1: Double, lon1: Double, lat2: Double, lon2: Double, el1: Double, el2: Double): Double {
        val a = 6378137.0
        val b = 6356752.314245
        val f = 1.0 / 298.257223563
        val L = Math.toRadians(lon2 - lon1)
        val U1 = atan((1 - f) * tan(Math.toRadians(lat1)))
        val U2 = atan((1 - f) * tan(Math.toRadians(lat2)))
        val sinU1 = sin(U1)
        val cosU1 = cos(U1)
        val sinU2 = sin(U2)
        val cosU2 = cos(U2)

        var lambda = L
        var lambdaP = 0.0
        var iterLimit = 100.0
        var sigma = 0.0
        var cosSqAlpha = 0.0
        var sinSigma = 0.0
        var cosSigma = 0.0
        var cos2SigmaM = 0.0

        do {
            val sinLambda = sin(lambda)
            val cosLambda = cos(lambda)
            sinSigma = sqrt((cosU2 * sinLambda) * (cosU2 * sinLambda) + (cosU1 * sinU2 - sinU1 * cosU2 * cosLambda) * (cosU1 * sinU2 - sinU1 * cosU2 * cosLambda))
            if (sinSigma == 0.0) return 0.0
            cosSigma = sinU1 * sinU2 + cosU1 * cosU2 * cosLambda
            sigma = atan2(sinSigma, cosSigma)
            val sinAlpha = cosU1 * cosU2 * sinLambda / sinSigma
            cosSqAlpha = 1 - sinAlpha * sinAlpha
            try {
                cos2SigmaM = cosSigma - 2 * sinU1 * sinU2 / cosSqAlpha
            } catch (_: ArithmeticException) {
                cos2SigmaM = 0.0
            }
            val C = f / 16 * cosSqAlpha * (4 + f * (4 - 3 * cosSqAlpha))
            lambdaP = lambda
            lambda = L + (1 - C) * f * sinAlpha * (sigma + C * sinSigma * (cos2SigmaM + C * cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM)))
        } while (abs(lambda - lambdaP) > 1e-12 && --iterLimit > 0)

        if (iterLimit == 0.0) return -1.0

        val uSq = cosSqAlpha * (a * a - b * b) / (b * b)
        val A = 1 + uSq / 16384 * (4096 + uSq * (-768 + uSq * (320 - 175 * uSq)))
        val B = uSq / 1024 * (256 + uSq * (-128 + uSq * (74 - 47 * uSq)))
        val deltaSigma = B * sinSigma * (cos2SigmaM + B / 4 * (cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM) - B / 6 * cos2SigmaM * (-3 + 4 * sinSigma * sinSigma) * (-3 + 4 * cos2SigmaM * cos2SigmaM)))
        val distance = b * A * (sigma - deltaSigma)
        val height = el1 - el2
        return atan2(height, distance)
    }

    @JvmStatic
    fun belowAboveGlidePath(lat1: Double, lon1: Double, lat2: Double, lon2: Double, el1: Double, el2: Double, SlopeAngle: Double): Double {
        val a = 6378137.0
        val b = 6356752.314245
        val f = 1.0 / 298.257223563
        val L = Math.toRadians(lon2 - lon1)
        val U1 = atan((1 - f) * tan(Math.toRadians(lat1)))
        val U2 = atan((1 - f) * tan(Math.toRadians(lat2)))
        val sinU1 = sin(U1)
        val cosU1 = cos(U1)
        val sinU2 = sin(U2)
        val cosU2 = cos(U2)

        var lambda = L
        var lambdaP = 0.0
        var iterLimit = 100.0
        var sigma = 0.0
        var cosSqAlpha = 0.0
        var sinSigma = 0.0
        var cosSigma = 0.0
        var cos2SigmaM = 0.0

        do {
            val sinLambda = sin(lambda)
            val cosLambda = cos(lambda)
            sinSigma = sqrt((cosU2 * sinLambda) * (cosU2 * sinLambda) + (cosU1 * sinU2 - sinU1 * cosU2 * cosLambda) * (cosU1 * sinU2 - sinU1 * cosU2 * cosLambda))
            if (sinSigma == 0.0) return 0.0
            cosSigma = sinU1 * sinU2 + cosU1 * cosU2 * cosLambda
            sigma = atan2(sinSigma, cosSigma)
            val sinAlpha = cosU1 * cosU2 * sinLambda / sinSigma
            cosSqAlpha = 1 - sinAlpha * sinAlpha
            try {
                cos2SigmaM = cosSigma - 2 * sinU1 * sinU2 / cosSqAlpha
            } catch (_: ArithmeticException) {
                cos2SigmaM = 0.0
            }
            val C = f / 16 * cosSqAlpha * (4 + f * (4 - 3 * cosSqAlpha))
            lambdaP = lambda
            lambda = L + (1 - C) * f * sinAlpha * (sigma + C * sinSigma * (cos2SigmaM + C * cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM)))
        } while (abs(lambda - lambdaP) > 1e-12 && --iterLimit > 0)

        if (iterLimit == 0.0) return -1.0

        val uSq = cosSqAlpha * (a * a - b * b) / (b * b)
        val A = 1 + uSq / 16384 * (4096 + uSq * (-768 + uSq * (320 - 175 * uSq)))
        val B = uSq / 1024 * (256 + uSq * (-128 + uSq * (74 - 47 * uSq)))
        val deltaSigma = B * sinSigma * (cos2SigmaM + B / 4 * (cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM) - B / 6 * cos2SigmaM * (-3 + 4 * sinSigma * sinSigma) * (-3 + 4 * cos2SigmaM * cos2SigmaM)))
        val distance = b * A * (sigma - deltaSigma)
        val neededElev = tan(SlopeAngle) * distance + el2
        return el1 - neededElev
    }

    @JvmStatic
    fun belowAboveGlidePathH(lat1: Double, lon1: Double, lat2: Double, lon2: Double, el1: Double, el2: Double, SlopeAngle: Double): Double {
        val R = 6378137
        val latDistance = Math.toRadians(lat2 - lat1)
        val lonDistance = Math.toRadians(lon2 - lon1)
        val a = sin(latDistance / 2) * sin(latDistance / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(lonDistance / 2) * sin(lonDistance / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        val distance = R * c
        val neededElev = tan(SlopeAngle) * distance + el2
        return el1 - neededElev
    }

    @JvmStatic
    fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double = bearingDeg(lat1, lon1, lat2, lon2)

    @JvmStatic
    fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val rlat1 = Math.toRadians(lat1)
        val rlat2 = Math.toRadians(lat2)
        val deltaLong = Math.toRadians(lon2 - lon1)

        val y = sin(deltaLong) * cos(rlat2)
        val x = cos(rlat1) * sin(rlat2) - sin(rlat1) * cos(rlat2) * cos(deltaLong)
        var result = atan2(y, x)
        result = Math.toDegrees(result)
        if (result < 0) result += 360
        return result
    }

    @JvmStatic
    fun bearingRad(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val rlat1 = Math.toRadians(lat1)
        val rlat2 = Math.toRadians(lat2)
        val deltaLong = Math.toRadians(lon2 - lon1)

        val y = sin(deltaLong) * cos(rlat2)
        val x = cos(rlat1) * sin(rlat2) - sin(rlat1) * cos(rlat2) * cos(deltaLong)
        return atan2(y, x)
    }

    @JvmStatic
    fun calculateHipDist(lat1: Double, lon1: Double, lat2: Double, lon2: Double, lat3: Double, lon3: Double, displacement: Double): Double {
        val bearing1 = bearingRad(lat1, lon1, lat2, lon2)
        val bearing2 = bearingRad(lat2, lon2, lat3, lon3)

        val angleBetwin2Lines = PI - bearing1 - bearing2
        val hipotenuseDistance = displacement / sin(angleBetwin2Lines / 2)

        return hipotenuseDistance
    }

    @JvmStatic
    fun projection(lat: Double, lon: Double, distance: Double, bearing: Double): DoubleArray {
        val a = 6378137.0
        val b = 6356752.3142
        val f = 1.0 / 298.257223563

        val s = distance
        val alpha1 = Math.toRadians(bearing)
        val sinAlpha1 = sin(alpha1)
        val cosAlpha1 = cos(alpha1)

        val tanU1 = (1 - f) * tan(Math.toRadians(lat))
        val cosU1 = 1 / sqrt(1 + tanU1 * tanU1)
        val sinU1 = tanU1 * cosU1
        val sigma1 = atan2(tanU1, cosAlpha1)
        val sinAlpha = cosU1 * sinAlpha1
        val cosSqAlpha = 1 - sinAlpha * sinAlpha
        val uSq = cosSqAlpha * (a * a - b * b) / (b * b)
        val A = 1 + uSq / 16384 * (4096 + uSq * (-768 + uSq * (320 - 175 * uSq)))
        val B = uSq / 1024 * (256 + uSq * (-128 + uSq * (74 - 47 * uSq)))

        var sigma = s / (b * A)
        var sigmaP = 2 * Math.PI

        var sinSigma = 0.0
        var cosSigma = 0.0
        var cos2SigmaM = 0.0
        var iterLimit = 100.0

        do {
            cos2SigmaM = cos(2 * sigma1 + sigma)
            sinSigma = sin(sigma)
            cosSigma = cos(sigma)
            val deltaSigma = B * sinSigma * (cos2SigmaM + B / 4 * (cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM) -
                    B / 6 * cos2SigmaM * (-3 + 4 * sinSigma * sinSigma) * (-3 + 4 * cos2SigmaM * cos2SigmaM)))
            sigmaP = sigma
            sigma = s / (b * A) + deltaSigma
        } while (abs(sigma - sigmaP) > 1e-12 && --iterLimit > 0)

        val tmp = sinU1 * sinSigma - cosU1 * cosSigma * cosAlpha1
        val lat2 = atan2(sinU1 * cosSigma + cosU1 * sinSigma * cosAlpha1,
                (1 - f) * sqrt(sinAlpha * sinAlpha + tmp * tmp))
        val lambda = atan2(sinSigma * sinAlpha1, cosU1 * cosSigma - sinU1 * sinSigma * cosAlpha1)
        val C = f / 16 * cosSqAlpha * (4 + f * (4 - 3 * cosSqAlpha))
        val L = lambda - (1 - C) * f * sinAlpha *
                (sigma + C * sinSigma * (cos2SigmaM + C * cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM)))

        val result = doubleArrayOf(Math.toDegrees(lat2), lon + Math.toDegrees(L))
        return result
    }

    @JvmStatic
    fun vmg(speed: Double, turn: Double): Double {
        return speed * cos(Math.toRadians(turn))
    }

    @JvmStatic
    fun xtk(distance: Double, dtk: Double, bearing: Double): Double {
        var dte = 0.0
        var dtesign = 1.0
        if (bearing > dtk) {
            dte = bearing - dtk
        } else if (bearing < dtk) {
            dte = dtk - bearing
            dtesign = -1.0
        }
        if (dte > 180) {
            dte = 360 - dte
            dtesign *= -1
        }
        if (dte > 90)
            return Double.NEGATIVE_INFINITY

        return distance * sin(Math.toRadians(dte)) * dtesign
    }

    @JvmStatic
    fun turn(deg1: Double, deg2: Double): Double {
        var deg = 0.0
        var degsign = 1.0
        if (deg2 > deg1) {
            deg = deg2 - deg1
        } else if (deg2 < deg1) {
            deg = deg1 - deg2
            degsign = -1.0
        }
        if (deg > 180) {
            deg = 360 - deg
            degsign *= -1
        }
        return deg * degsign
    }
}
