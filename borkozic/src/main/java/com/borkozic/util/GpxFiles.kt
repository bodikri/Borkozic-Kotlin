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

import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.ArrayList

import javax.xml.parsers.ParserConfigurationException
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import org.xmlpull.v1.XmlSerializer

import android.util.Xml

import com.borkozic.data.Area
import com.borkozic.data.Route
import com.borkozic.data.Track
import com.borkozic.data.Track.TrackPoint
import com.borkozic.data.Waypoint
import com.borkozic.BaseApplication

/**
 * Helper class to read and write GPX files.
 *
 * @author Andrey Novikov
 */
object GpxFiles {
    @JvmField
    val GPX_NAMESPACE = "http://www.topografix.com/GPX/1/1"

    @JvmStatic
    @Throws(SAXException::class, IOException::class, ParserConfigurationException::class)
    fun loadWaypointsFromFile(file: File): ArrayList<Waypoint> {
        val waypoints = ArrayList<Waypoint>()

        val factory = SAXParserFactory.newInstance()
        val parser = factory.newSAXParser()
        parser.parse(file, GpxParser(file.name, waypoints, null, null, null))

        return waypoints
    }

    @JvmStatic
    @Throws(SAXException::class, IOException::class, ParserConfigurationException::class)
    fun loadTracksFromFile(file: File): ArrayList<Track> {
        val tracks = ArrayList<Track>()

        val factory = SAXParserFactory.newInstance()
        val parser = factory.newSAXParser()
        parser.parse(file, GpxParser(file.name, null, tracks, null, null))

        if (tracks.size > 0) {
            tracks[0].filepath = file.canonicalPath
        }

        return tracks
    }

    @JvmStatic
    @Throws(IOException::class)
    fun saveTrackToFile(file: File, track: Track) {
        val serializer = Xml.newSerializer()
        serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true)
        val writer = BufferedWriter(OutputStreamWriter(FileOutputStream(file, false)))
        serializer.setOutput(writer)
        serializer.startDocument("UTF-8", null)
        serializer.setPrefix("", GPX_NAMESPACE)
        serializer.startTag(GPX_NAMESPACE, GpxParser.GPX)
        serializer.attribute("", "creator", "Borkozic http://borkozic.com")
        serializer.startTag(GPX_NAMESPACE, GpxParser.TRK)
        serializer.startTag(GPX_NAMESPACE, GpxParser.NAME)
        serializer.text(track.name)
        serializer.endTag(GPX_NAMESPACE, GpxParser.NAME)
        serializer.startTag(GPX_NAMESPACE, GpxParser.SRC)
        serializer.text(BaseApplication.deviceName)
        serializer.endTag(GPX_NAMESPACE, GpxParser.SRC)

        var first = true
        serializer.startTag(GPX_NAMESPACE, GpxParser.TRKSEG)
        val trackPoints = track.points
        synchronized(trackPoints) {
            for (tp in trackPoints) {
                if (!tp.continous && !first) {
                    serializer.endTag(GPX_NAMESPACE, GpxParser.TRKSEG)
                    serializer.startTag(GPX_NAMESPACE, GpxParser.TRKSEG)
                }
                serializer.startTag(GPX_NAMESPACE, GpxParser.TRKPT)
                serializer.attribute("", GpxParser.LAT, tp.latitude.toString())
                serializer.attribute("", GpxParser.LON, tp.longitude.toString())
                serializer.startTag(GPX_NAMESPACE, GpxParser.ELE)
                serializer.text(tp.elevation.toString())
                serializer.endTag(GPX_NAMESPACE, GpxParser.ELE)
                serializer.startTag(GPX_NAMESPACE, GpxParser.TIME)
                serializer.text(GpxParser.trktime.format(Date(tp.time)))
                serializer.endTag(GPX_NAMESPACE, GpxParser.TIME)
                serializer.endTag(GPX_NAMESPACE, GpxParser.TRKPT)
                first = false
            }
        }
        serializer.endTag(GPX_NAMESPACE, GpxParser.TRKSEG)
        serializer.endTag(GPX_NAMESPACE, GpxParser.TRK)
        serializer.endTag(GPX_NAMESPACE, GpxParser.GPX)
        serializer.endDocument()
        serializer.flush()
        writer.close()
    }

    @JvmStatic
    @Throws(SAXException::class, IOException::class, ParserConfigurationException::class)
    fun loadRoutesFromFile(file: File): ArrayList<Route> {
        val routes = ArrayList<Route>()

        val factory = SAXParserFactory.newInstance()
        val parser = factory.newSAXParser()
        parser.parse(file, GpxParser(file.name, null, null, routes, null))

        if (routes.size > 0) {
            routes[0].filepath = file.canonicalPath
        }

        return routes
    }

    @JvmStatic
    @Throws(SAXException::class, IOException::class, ParserConfigurationException::class)
    fun loadAreasFromFile(file: File): ArrayList<Area> {
        val areas = ArrayList<Area>()

        val factory = SAXParserFactory.newInstance()
        val parser = factory.newSAXParser()
        parser.parse(file, GpxParser(file.name, null, null, null, areas))

        if (areas.size > 0) {
            areas[0].filepath = file.canonicalPath
        }

        return areas
    }
}

/**
 * Simple SAX parser of GPX files. Loads GPX waypoints, tracks and routes.
 *
 * @author Andrey Novikov
 */
open class GpxParser @JvmOverloads constructor(
    filename: String?,
    waypoints: ArrayList<Waypoint>?,
    tracks: ArrayList<Track>?,
    routes: ArrayList<Route>?,
    areas: ArrayList<Area>?
) : DefaultHandler() {

    companion object {
        const val GPX = "gpx"
        const val LAT = "lat"
        const val LON = "lon"
        const val NAME = "name"
        const val DESC = "desc"
        const val SRC = "src"
        const val ELE = "ele"
        const val TIME = "time"
        const val WPT = "wpt"
        const val RTE = "rte"
        const val RTEPT = "rtept"
        const val TRK = "trk"
        const val TRKSEG = "trkseg"
        const val TRKPT = "trkpt"

        val trktime: DateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    }

    private val builder = StringBuilder()

    private var waypoints: ArrayList<Waypoint>? = waypoints
    private var waypoint: Waypoint? = null
    private var tracks: ArrayList<Track>? = tracks
    private var track: Track? = null
    private var trkpt: TrackPoint? = null
    private var continous = false
    private var routes: ArrayList<Route>? = routes
    private var route: Route? = null
    private var areas: ArrayList<Area>? = areas
    private var area: Area? = null
    private var rtwpt: Waypoint? = null
    private var filename: String = filename ?: ""

    @Throws(SAXException::class)
    override fun characters(ch: CharArray, start: Int, length: Int) {
        builder.append(ch, start, length)
        super.characters(ch, start, length)
    }

    @Throws(SAXException::class)
    override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
        builder.delete(0, builder.length)
        // <wpt>
        if (localName?.equals(WPT, ignoreCase = true) == true && waypoints != null) {
            waypoint = Waypoint()
            waypoint!!.latitude = attributes.getValue(LAT).toDouble()
            waypoint!!.longitude = attributes.getValue(LON).toDouble()
            waypoint!!.altitude = attributes.getValue(ELE).toDouble()
        }
        // <rte>
        if (localName?.equals(RTE, ignoreCase = true) == true && routes != null) {
            route = Route()
        }
        // <rte> Area added from me
        if (localName?.equals(RTE, ignoreCase = true) == true && areas != null) {
            area = Area()
        }
        // <rtept>
        if (localName?.equals(RTEPT, ignoreCase = true) == true && route != null) {
            rtwpt = Waypoint()
            rtwpt!!.latitude = attributes.getValue(LAT).toDouble()
            rtwpt!!.longitude = attributes.getValue(LON).toDouble()
            rtwpt!!.altitude = attributes.getValue(ELE).toDouble()
        }
        // <trk>
        if (localName?.equals(TRK, ignoreCase = true) == true && tracks != null) {
            track = Track()
        }
        // <trkseg>
        if (localName?.equals(TRKSEG, ignoreCase = true) == true) {
            continous = false
        }
        // <trkpt>
        if (localName?.equals(TRKPT, ignoreCase = true) == true && track != null) {
            track!!.addPoint(
                continous,
                attributes.getValue(LAT).toDouble(),
                attributes.getValue(LON).toDouble(),
                attributes.getValue(ELE).toDouble(),
                0.0, 0.0, 0.0, 0
            )
            trkpt = track!!.getLastPoint()
            continous = true
        }
        super.startElement(uri, localName, qName, attributes)
    }

    @Throws(SAXException::class)
    override fun endElement(uri: String?, localName: String?, qName: String?) {
        // </wpt>
        if (waypoint != null && localName?.equals(WPT, ignoreCase = true) == true) {
            if (waypoint!!.name.equals(""))
                waypoint!!.name = "WPT" + waypoints!!.size
            waypoints!!.add(waypoint!!)
            waypoint = null
        }
        // </rte>
        else if (route != null && localName?.equals(RTE, ignoreCase = true) == true) {
            if (route!!.name.equals("")) {
                route!!.name = filename
                if (routes!!.size > 0)
                    route!!.name += "_" + routes!!.size
            }
            route!!.show = true
            routes!!.add(route!!)
            route = null
        }
        // </rte> Area added from me
        else if (area != null && localName?.equals(RTE, ignoreCase = true) == true) {
            if (area!!.name.equals("")) {
                area!!.name = filename
                if (areas!!.size > 0)
                    area!!.name += "_" + areas!!.size
            }
            area!!.show = true
            areas!!.add(area!!)
            area = null
        }
        // <rtept>
        else if (rtwpt != null && localName?.equals(RTEPT, ignoreCase = true) == true) {
            if (rtwpt!!.name.equals(""))
                rtwpt!!.name = "RWPT" + route!!.length()
            route!!.addWaypoint(rtwpt!!)
            rtwpt = null
        }
        // </trk>
        else if (track != null && localName?.equals(TRK, ignoreCase = true) == true) {
            if (track!!.name.equals("")) {
                track!!.name = filename
                if (tracks!!.size > 0)
                    track!!.name += "_" + tracks!!.size
            }
            track!!.show = true
            tracks!!.add(track!!)
            track = null
        }
        // </trkpt>
        else if (trkpt != null && localName?.equals(TRKPT, ignoreCase = true) == true) {
            trkpt = null
        }
        // </name>
        else if (localName?.equals(NAME, ignoreCase = true) == true) {
            if (waypoint != null)
                waypoint!!.name = builder.toString().trim()
            if (route != null)
                route!!.name = builder.toString().trim()
            if (rtwpt != null)
                rtwpt!!.name = builder.toString().trim()
            if (track != null)
                track!!.name = builder.toString().trim()
        }
        // </desc>
        else if (localName?.equals(DESC, ignoreCase = true) == true) {
            if (waypoint != null)
                waypoint!!.description = builder.toString().trim()
            if (route != null)
                route!!.description = builder.toString().trim()
            if (rtwpt != null)
                rtwpt!!.description = builder.toString().trim()
            if (track != null)
                track!!.description = builder.toString().trim()
        }
        // </ele>
        else if (localName?.equals(ELE, ignoreCase = true) == true) {
            if (trkpt != null)
                trkpt!!.elevation = builder.toString().trim().toDouble()
        }
        // </time>
        else if (localName?.equals(TIME, ignoreCase = true) == true) {
            if (trkpt != null) {
                try {
                    trkpt!!.time = trktime.parse(builder.toString().trim()).time
                } catch (e: ParseException) {
                    e.printStackTrace()
                }
            }
        }

        super.endElement(uri, localName, qName)
    }
}
