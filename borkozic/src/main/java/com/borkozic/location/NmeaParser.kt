package com.borkozic.location

import android.location.Location
import android.os.Bundle
import android.util.Log
import java.util.*

/**
 * {@hide}
 */
class NmeaParser(private val mName: String) {

    companion object {
        private const val TAG = "NmeaParser"
        
        private val sUtcTimeZone: TimeZone = TimeZone.getTimeZone("UTC")
        
        private const val KNOTS_TO_METERS_PER_SECOND = 0.51444444444f
    }

    private var mYear = -1
    private var mMonth = 0
    private var mDay = 0

    private var mTime: Long = -1
    private var mBaseTime: Long = 0
    private var mLatitude = 0.0
    private var mLongitude = 0.0

    private var mHasAltitude = false
    private var mAltitude = 0.0
    private var mHasBearing = false
    private var mBearing = 0f
    private var mHasSpeed = false
    private var mSpeed = 0f

    private var mNewWaypoint = false
    private var mLocation: Location? = null
    private var mExtras: Bundle? = null

    private fun updateTime(time: String): Boolean {
        if (time.length < 6) {
            return false
        }
        if (mYear == -1) {
            // Since we haven't seen a day/month/year yet,
            // we can't construct a meaningful time stamp.
            // Clean up any old data.
            mLatitude = 0.0
            mLongitude = 0.0
            mHasAltitude = false
            mHasBearing = false
            mHasSpeed = false
            mExtras = null
            return false
        }

        val hour: Int
        val minute: Int
        val second: Float
        try {
            hour = time.substring(0, 2).toInt()
            minute = time.substring(2, 4).toInt()
            second = time.substring(4, time.length).toFloat()
        } catch (nfe: NumberFormatException) {
            Log.e(TAG, "Error parsing timestamp $time")
            return false
        }

        val isecond = second.toInt()
        val millis = ((second - isecond) * 1000).toInt()
        val c = GregorianCalendar(sUtcTimeZone)
        c[mYear, mMonth, mDay, hour, minute] = isecond
        var newTime = c.timeInMillis + millis

        if (mTime == -1L) {
            mTime = 0
            mBaseTime = newTime
        }
        newTime -= mBaseTime

        // If the timestamp has advanced, copy the temporary data
        // into a new Location
        if (newTime != mTime) {
            mNewWaypoint = true
            mLocation = Location(mName)
            mLocation!!.time = mTime
            mLocation!!.latitude = mLatitude
            mLocation!!.longitude = mLongitude
            if (mHasAltitude) {
                mLocation!!.altitude = mAltitude
            }
            if (mHasBearing) {
                mLocation!!.bearing = mBearing
            }
            if (mHasSpeed) {
                mLocation!!.speed = mSpeed
            }
            mLocation!!.extras = mExtras
            mExtras = null

            mTime = newTime
            mHasAltitude = false
            mHasBearing = false
            mHasSpeed = false
        }
        return true
    }

    private fun updateDate(date: String): Boolean {
        if (date.length != 6) {
            return false
        }
        val day: Int
        val month: Int
        val year: Int
        try {
            day = date.substring(0, 2).toInt()
            month = date.substring(2, 4).toInt()
            year = 2000 + date.substring(4, 6).toInt()
        } catch (nfe: NumberFormatException) {
            Log.e(TAG, "Error parsing date $date")
            return false
        }

        mYear = year
        mMonth = month
        mDay = day
        return true
    }

    private fun updateTime(time: String, date: String): Boolean {
        return if (!updateDate(date)) {
            false
        } else updateTime(time)
    }

    private fun updateIntExtra(name: String, value: String): Boolean {
        val valInt: Int
        try {
            valInt = value.toInt()
        } catch (nfe: NumberFormatException) {
            Log.e(TAG, "Exception parsing int $name: $value", nfe)
            return false
        }
        if (mExtras == null) {
            mExtras = Bundle()
        }
        mExtras!!.putInt(name, valInt)
        return true
    }

    private fun updateFloatExtra(name: String, value: String): Boolean {
        val valFloat: Float
        try {
            valFloat = value.toFloat()
        } catch (nfe: NumberFormatException) {
            Log.e(TAG, "Exception parsing float $name: $value", nfe)
            return false
        }
        if (mExtras == null) {
            mExtras = Bundle()
        }
        mExtras!!.putFloat(name, valFloat)
        return true
    }

    private fun updateDoubleExtra(name: String, value: String): Boolean {
        val valDouble: Double
        try {
            valDouble = value.toDouble()
        } catch (nfe: NumberFormatException) {
            Log.e(TAG, "Exception parsing double $name: $value", nfe)
            return false
        }
        if (mExtras == null) {
            mExtras = Bundle()
        }
        mExtras!!.putDouble(name, valDouble)
        return true
    }

    private fun convertFromHHMM(coord: String): Double {
        val valDouble = coord.toDouble()
        val degrees = (Math.floor(valDouble)).toInt() / 100
        val minutes = valDouble - (degrees * 100)
        val dcoord = degrees + minutes / 60.0
        return dcoord
    }

    private fun updateLatLon(
        latitude: String, latitudeHemi: String,
        longitude: String, longitudeHemi: String
    ): Boolean {
        if (latitude.isEmpty() || longitude.isEmpty()) {
            return false
        }

        // Lat/long values are expressed as {D}DDMM.MMMM
        var lat: Double
        var lon: Double
        try {
            lat = convertFromHHMM(latitude)
            if (latitudeHemi[0] == 'S') {
                lat = -lat
            }
        } catch (nfe1: NumberFormatException) {
            Log.e(TAG, "Exception parsing lat/long: $nfe1", nfe1)
            return false
        }

        try {
            lon = convertFromHHMM(longitude)
            if (longitudeHemi[0] == 'W') {
                lon = -lon
            }
        } catch (nfe2: NumberFormatException) {
            Log.e(TAG, "Exception parsing lat/long: $nfe2", nfe2)
            return false
        }

        // Only update if both were parsed cleanly
        mLatitude = lat
        mLongitude = lon
        return true
    }

    private fun updateAltitude(altitude: String): Boolean {
        if (altitude.isEmpty()) {
            return false
        }
        val alt: Double
        try {
            alt = altitude.toDouble()
        } catch (nfe: NumberFormatException) {
            Log.e(TAG, "Exception parsing altitude $altitude: $nfe", nfe)
            return false
        }

        mHasAltitude = true
        mAltitude = alt
        return true
    }

    private fun updateBearing(bearing: String): Boolean {
        val brg: Float
        try {
            brg = bearing.toFloat()
        } catch (nfe: NumberFormatException) {
            Log.e(TAG, "Exception parsing bearing $bearing: $nfe", nfe)
            return false
        }

        mHasBearing = true
        mBearing = brg
        return true
    }

    private fun updateSpeed(speed: String): Boolean {
        val spd: Float
        try {
            spd = speed.toFloat() * KNOTS_TO_METERS_PER_SECOND
        } catch (nfe: NumberFormatException) {
            Log.e(TAG, "Exception parsing speed $speed: $nfe", nfe)
            return false
        }

        mHasSpeed = true
        mSpeed = spd
        return true
    }

    fun parseSentence(s: String): Boolean {
        var sentence = s
        val len = sentence.length
        if (len < 9) {
            return false
        }
        if (sentence[len - 3] == '*') {
            // String checksum = sentence.substring(len - 4, len);
            sentence = sentence.substring(0, len - 3)
        }
        val tokens = sentence.split(",")
        val sentenceId = tokens[0].substring(3, 6)

        var idx = 1
        try {
            when (sentenceId) {
                "GGA" -> {
                    val time = tokens[idx++]
                    val latitude = tokens[idx++]
                    val latitudeHemi = tokens[idx++]
                    val longitude = tokens[idx++]
                    val longitudeHemi = tokens[idx++]
                    val fixQuality = tokens[idx++]
                    val numSatellites = tokens[idx++]
                    val horizontalDilutionOfPrecision = tokens[idx++]
                    val altitude = tokens[idx++]
                    val altitudeUnits = tokens[idx++]
                    val heightOfGeoid = tokens[idx++]
                    val heightOfGeoidUnits = tokens[idx++]
                    val timeSinceLastDgpsUpdate = tokens[idx++]

                    updateTime(time)
                    updateLatLon(latitude, latitudeHemi,
                        longitude, longitudeHemi)
                    updateAltitude(altitude)
                    // updateQuality(fixQuality);
                    updateIntExtra("numSatellites", numSatellites)
                    updateFloatExtra("hdop", horizontalDilutionOfPrecision)

                    if (mNewWaypoint) {
                        mNewWaypoint = false
                        return true
                    }
                }
                "GSA" -> {
                    // DOP and active satellites
                    val selectionMode = tokens[idx++] // m=manual, a=auto 2d/3d
                    val mode = tokens[idx++] // 1=no fix, 2=2d, 3=3d
                    for (i in 0..11) {
                        val id = tokens[idx++]
                    }
                    val pdop = tokens[idx++]
                    val hdop = tokens[idx++]
                    val vdop = tokens[idx++]

                    // TODO - publish satellite ids
                    updateFloatExtra("pdop", pdop)
                    updateFloatExtra("hdop", hdop)
                    updateFloatExtra("vdop", vdop)
                }
                "GSV" -> {
                    // Satellites in view
                    val numMessages = tokens[idx++]
                    val messageNum = tokens[idx++]
                    val svsInView = tokens[idx++]
                    for (i in 0..3) {
                        if (idx + 2 < tokens.size) {
                            val prnNumber = tokens[idx++]
                            val elevation = tokens[idx++]
                            val azimuth = tokens[idx++]
                            if (idx < tokens.size) {
                                val snr = tokens[idx++]
                            }
                        }
                    }
                    // TODO - publish this info
                }
                "RMC" -> {
                    // Recommended minimum navigation information
                    val time = tokens[idx++]
                    val fixStatus = tokens[idx++]
                    val latitude = tokens[idx++]
                    val latitudeHemi = tokens[idx++]
                    val longitude = tokens[idx++]
                    val longitudeHemi = tokens[idx++]
                    val speed = tokens[idx++]
                    val bearing = tokens[idx++]
                    val utcDate = tokens[idx++]
                    val magneticVariation = tokens[idx++]
                    val magneticVariationDir = tokens[idx++]
                    val mode = tokens[idx++]

                    if (fixStatus[0] == 'A') {
                        updateTime(time, utcDate)
                        updateLatLon(latitude, latitudeHemi,
                            longitude, longitudeHemi)
                        updateBearing(bearing)
                        updateSpeed(speed)
                    }

                    if (mNewWaypoint) {
                        return true
                    }
                }
                else -> {
                    Log.e(TAG, "Unknown sentence: $sentence")
                }
            }
        } catch (e: ArrayIndexOutOfBoundsException) {
            // do nothing - sentence will have no effect
            Log.e(TAG, "AIOOBE", e)

            for (i in tokens.indices) {
                Log.e(TAG, "Got token #$i = ${tokens[i]}")
            }
        }

        return false
    }

    //  } else if (sentenceId.equals("GLL")) {
    //  // Geographics position lat/long
    //  String latitude = tokens[idx++];
    //  String latitudeHemi = tokens[idx++];
    //  String longitude = tokens[idx++];
    //  String longitudeHemi = tokens[idx++];
    //  String time = tokens[idx++];
    //  String status = tokens[idx++];
    //  String mode = tokens[idx++];
    //  String checksum = tokens[idx++];
    //
    //  if (status.charAt(0) == 'A') {
    //      updateTime(time);
    //      updateLatLon(latitude, latitudeHemi, longitude, longitudeHemi);
    //  }
    //} else if (sentenceId.equals("VTG")) {
    //    String trackMadeGood = tokens[idx++];
    //    String t = tokens[idx++];
    //    String unused1 = tokens[idx++];
    //    String unused2 = tokens[idx++];
    //    String groundSpeedKnots = tokens[idx++];
    //    String n = tokens[idx++];
    //    String groundSpeedKph = tokens[idx++];
    //    String k = tokens[idx++];
    //    String checksum = tokens[idx++];
    //
    //    updateSpeed(groundSpeedKph);

    val location: Location?
        get() = mLocation
}