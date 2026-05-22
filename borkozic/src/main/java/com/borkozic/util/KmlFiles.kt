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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.ArrayList
import java.util.HashMap

import javax.xml.parsers.ParserConfigurationException
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import org.xmlpull.v1.XmlSerializer

import android.graphics.Color
import android.util.Log
import android.util.Xml

import com.borkozic.data.Area
import com.borkozic.data.Route
import com.borkozic.data.Track
import com.borkozic.data.Track.TrackPoint
import com.borkozic.data.Waypoint

/**
 * Helper class to read and write KML files.
 *
 * @author Andrey Novikov
 */
object KmlFiles {
    @JvmField
    val KML_NAMESPACE = "http://www.opengis.net/kml/2.2"

    @JvmStatic
    @Throws(SAXException::class, IOException::class, ParserConfigurationException::class)
    fun loadWaypointsFromFile(file: File): ArrayList<Waypoint> {
        val waypoints = ArrayList<Waypoint>()

        val factory = SAXParserFactory.newInstance()
        val parser = factory.newSAXParser()
        parser.parse(file, KmlParser(file.name, waypoints, null))

        return waypoints
    }

    @JvmStatic
    @Throws(SAXException::class, IOException::class, ParserConfigurationException::class)
    fun loadTracksFromFile(file: File): ArrayList<Track> {
        val tracks = ArrayList<Track>()

        val factory = SAXParserFactory.newInstance()
        val parser = factory.newSAXParser()
        parser.parse(file, KmlParser(file.name, null, tracks))

        return tracks
    }

    @JvmStatic
    @Throws(IOException::class)
    fun saveTrackToFile(file: File, track: Track) {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
        val serializer = Xml.newSerializer()
        serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true)
        val writer = BufferedWriter(OutputStreamWriter(FileOutputStream(file, false)))
        serializer.setOutput(writer)
        serializer.startDocument("UTF-8", null)
        serializer.setPrefix("", KML_NAMESPACE)
        serializer.startTag(KML_NAMESPACE, KmlParser.KML)
        serializer.startTag(KML_NAMESPACE, KmlParser.DOCUMENT)
        serializer.startTag(KML_NAMESPACE, KmlParser.STYLE)
        serializer.attribute("", KmlParser.ID, "trackStyle")
        serializer.startTag(KML_NAMESPACE, KmlParser.LINESTYLE)
        serializer.startTag(KML_NAMESPACE, KmlParser.COLOR)
        serializer.text(String.format("%08X", KmlParser.reverseColor(track.color)))
        serializer.endTag(KML_NAMESPACE, KmlParser.COLOR)
        serializer.startTag(KML_NAMESPACE, KmlParser.WIDTH)
        serializer.text(track.width.toString())
        serializer.endTag(KML_NAMESPACE, KmlParser.WIDTH)
        serializer.endTag(KML_NAMESPACE, KmlParser.LINESTYLE)
        serializer.endTag(KML_NAMESPACE, KmlParser.STYLE)
        serializer.startTag(KML_NAMESPACE, KmlParser.FOLDER)
        serializer.startTag(KML_NAMESPACE, KmlParser.NAME)
        serializer.text(track.name)
        serializer.endTag(KML_NAMESPACE, KmlParser.NAME)
        serializer.startTag(KML_NAMESPACE, KmlParser.OPEN)
        serializer.text("0")
        serializer.endTag(KML_NAMESPACE, KmlParser.OPEN)
        serializer.startTag(KML_NAMESPACE, KmlParser.TIMESPAN)
        serializer.startTag(KML_NAMESPACE, KmlParser.BEGIN)
        serializer.text(sdf.format(Date(track.getPoint(0).time)))
        serializer.endTag(KML_NAMESPACE, KmlParser.BEGIN)
        serializer.startTag(KML_NAMESPACE, KmlParser.END)
        val lastPt = track.getLastPoint()
        serializer.text(sdf.format(Date(lastPt!!.time)))
        serializer.endTag(KML_NAMESPACE, KmlParser.END)
        serializer.endTag(KML_NAMESPACE, KmlParser.TIMESPAN)
        serializer.startTag(KML_NAMESPACE, KmlParser.STYLE)
        serializer.startTag(KML_NAMESPACE, KmlParser.LISTSTYLE)
        serializer.startTag(KML_NAMESPACE, KmlParser.LISTITEMTYPE)
        serializer.text("checkHideChildren")
        serializer.endTag(KML_NAMESPACE, KmlParser.LISTITEMTYPE)
        serializer.endTag(KML_NAMESPACE, KmlParser.LISTSTYLE)
        serializer.endTag(KML_NAMESPACE, KmlParser.STYLE)

        var part = 1
        var first = true
        startTrackPart(serializer, part, track.name)
        val trackPoints = track.points
        synchronized(trackPoints) {
            for (tp in trackPoints) {
                if (!tp.continous && !first) {
                    stopTrackPart(serializer)
                    part++
                    startTrackPart(serializer, part, track.name)
                }
                serializer.text(String.format("%f,%f,%f ", tp.longitude, tp.latitude, tp.elevation))
                first = false
            }
        }
        stopTrackPart(serializer)
        serializer.endTag(KML_NAMESPACE, KmlParser.FOLDER)
        serializer.endTag(KML_NAMESPACE, KmlParser.DOCUMENT)
        serializer.endTag(KML_NAMESPACE, KmlParser.KML)
        serializer.endDocument()
        serializer.flush()
        writer.close()
    }

    @JvmStatic
    @Throws(SAXException::class, IOException::class, ParserConfigurationException::class)
    fun loadRoutesFromFile(file: File): ArrayList<Route> {
        val tracks = loadTracksFromFile(file)
        val routes = ArrayList<Route>()
        for (track in tracks) {
            val route = Route(track.name, track.description, track.show)
            var i = 0
            for (tp in track.points) {
                val name = "RWPT$i"
                route.addWaypoint(name, tp.latitude, tp.longitude, tp.elevation)
                i++
            }
            routes.add(route)
        }
        return routes
    }

    @JvmStatic
    @Throws(SAXException::class, IOException::class, ParserConfigurationException::class)
    fun loadAreasFromFile(file: File): ArrayList<Area> {
        val tracks = loadTracksFromFile(file)
        val areas = ArrayList<Area>()
        for (track in tracks) {
            val area = Area(track.name, track.description, null, track.show, 10.0, 1000.0)
            var i = 0
            for (tp in track.points) {
                val name = "RWPT$i"
                area.addWaypoint(name, tp.latitude, tp.longitude, tp.elevation)
                i++
            }
            areas.add(area)
        }
        return areas
    }

    @Throws(java.lang.IllegalArgumentException::class, IllegalStateException::class, IOException::class)
    private fun startTrackPart(serializer: XmlSerializer, part: Int, name: String) {
        serializer.startTag(KML_NAMESPACE, KmlParser.PLACEMARK)
        serializer.startTag(KML_NAMESPACE, KmlParser.NAME)
        serializer.text(String.format("Part %d - %s", part, name))
        serializer.endTag(KML_NAMESPACE, KmlParser.NAME)
        serializer.startTag(KML_NAMESPACE, KmlParser.STYLEURL)
        serializer.text("#trackStyle")
        serializer.endTag(KML_NAMESPACE, KmlParser.STYLEURL)
        serializer.startTag(KML_NAMESPACE, KmlParser.LINESTRING)
        serializer.startTag(KML_NAMESPACE, KmlParser.TESSELLATE)
        serializer.text("1")
        serializer.endTag(KML_NAMESPACE, KmlParser.TESSELLATE)
        serializer.startTag(KML_NAMESPACE, KmlParser.COORDINATES)
    }

    @Throws(java.lang.IllegalArgumentException::class, IllegalStateException::class, IOException::class)
    private fun stopTrackPart(serializer: XmlSerializer) {
        serializer.endTag(KML_NAMESPACE, KmlParser.COORDINATES)
        serializer.endTag(KML_NAMESPACE, KmlParser.LINESTRING)
        serializer.endTag(KML_NAMESPACE, KmlParser.PLACEMARK)
    }
}

/**
 * Simple SAX parser of KML files. Loads GPX waypoints, tracks and routes.
 * KML format does not have specific entity for route, so user should decide what to treat as track and what as route.
 *
 * @author Andrey Novikov
 */
open class KmlParser @JvmOverloads constructor(
    filename: String?,
    waypoints: ArrayList<Waypoint>?,
    tracks: ArrayList<Track>?
) : DefaultHandler() {

    companion object {
        const val TAG = "KmlFiles"

        const val KML = "kml"
        const val ID = "id"
        const val DOCUMENT = "Document"
        const val FOLDER = "Folder"
        const val OPEN = "open"
        const val TIMESPAN = "TimeSpan"
        const val BEGIN = "begin"
        const val END = "end"
        const val PLACEMARK = "Placemark"
        const val POINT = "Point"
        const val LINESTRING = "LineString"
        const val TESSELLATE = "tessellate"
        const val NAME = "name"
        const val COORDINATES = "coordinates"
        const val DESCRIPTION = "description"
        const val STYLE = "Style"
        const val LINESTYLE = "LineStyle"
        const val LISTSTYLE = "ListStyle"
        const val STYLEURL = "styleUrl"
        const val COLOR = "color"
        const val WIDTH = "width"
        const val LISTITEMTYPE = "listItemType"

        @JvmStatic
        fun reverseColor(color: Int): Int {
            Log.e(TAG, String.format("CB %8X", color))
            val c = ((color and 0x00FF0000.toInt()) ushr 16) or ((color and 0x000000FF.toInt()) shl 16) or (color and 0xFF00FF00.toInt())
            Log.e(TAG, String.format("CA %8X", c))
            return ((color and 0x00FF0000.toInt()) ushr 16) or ((color and 0x000000FF.toInt()) shl 16) or (color and 0xFF00FF00.toInt())
        }
    }

    private val builder = StringBuilder()

    private val styles: MutableMap<String, Style> = HashMap()
    private var style: Style? = null
    private var waypoints: ArrayList<Waypoint>? = waypoints
    private var waypoint: Waypoint? = null
    private var ispoint = false
    private var tracks: ArrayList<Track>? = tracks
    private var track: Track? = null
    private var istrack = false
    private var filename: String = filename ?: ""

    @Throws(SAXException::class)
    override fun characters(ch: CharArray, start: Int, length: Int) {
        builder.append(ch, start, length)
    }

    @Throws(SAXException::class)
    override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
        builder.delete(0, builder.length)
        if (localName?.equals(PLACEMARK, ignoreCase = true) == true) {
            waypoint = Waypoint()
            track = Track()
            ispoint = false
            istrack = false
        } else if (localName?.equals(POINT, ignoreCase = true) == true) {
            ispoint = true
        } else if (localName?.equals(LINESTRING, ignoreCase = true) == true) {
            istrack = true
        } else if (localName?.equals(STYLE, ignoreCase = true) == true) {
            style = Style()
            style!!.id = attributes.getValue(ID)
        } else if (localName?.equals(LINESTYLE, ignoreCase = true) == true) {
            style!!.lineStyle = LineStyle()
        }
    }

    @Throws(SAXException::class)
    override fun endElement(uri: String?, localName: String?, qName: String?) {
        if (localName?.equals(PLACEMARK, ignoreCase = true) == true) {
            if (ispoint && waypoints != null && waypoint != null) {
                if (waypoint!!.name.equals(""))
                    waypoint!!.name = "WPT" + waypoints!!.size
                waypoints!!.add(waypoint!!)
            }
            if (istrack && tracks != null && track != null) {
                if (track!!.name.equals("")) {
                    track!!.name = filename
                    if (tracks!!.size > 0)
                        track!!.name += "_" + tracks!!.size
                }
                track!!.show = true
                tracks!!.add(track!!)
            }
            waypoint = null
            track = null
        } else if (localName?.equals(NAME, ignoreCase = true) == true) {
            if (waypoint != null)
                waypoint!!.name = builder.toString().trim()
            if (track != null)
                track!!.name = builder.toString().trim()
        } else if (localName?.equals(DESCRIPTION, ignoreCase = true) == true) {
            if (waypoint != null)
                waypoint!!.description = builder.toString().trim()
            if (track != null)
                track!!.description = builder.toString().trim()
        } else if (localName?.equals(COORDINATES, ignoreCase = true) == true) {
            if (ispoint) {
                val coords = builder.toString().split(",")
                waypoint!!.latitude = coords[1].trim().toDouble()
                waypoint!!.longitude = coords[0].trim().toDouble()
                waypoint!!.altitude = coords[2].trim().toDouble()
            }
            if (istrack) {
                val points = builder.toString().split("[\\s\\n]".toRegex())
                var continous = false
                for (point in points) {
                    val coords = point.split(",")
                    if (coords.size == 3) {
                        track!!.addPoint(
                            continous,
                            coords[1].trim().toDouble(),
                            coords[0].trim().toDouble(),
                            coords[2].trim().toDouble(),
                            0.0, 0.0, 0.0, 0
                        )
                        continous = true
                    }
                }
            }
        } else if (localName?.equals(STYLEURL, ignoreCase = true) == true) {
            var id = builder.toString().trim()
            try {
                id = id.substring(id.indexOf("#") + 1)
            } catch (e: IndexOutOfBoundsException) {
                Log.e(TAG, "StyleURL error", e)
            }
            val s = styles[id]
            if (track != null && s != null)
                setTrackStyle(track!!, s)
        } else if (localName?.equals(STYLE, ignoreCase = true) == true) {
            if (style != null) {
                if (style!!.id != null)
                    styles[style!!.id!!] = style!!
                else if (track != null)
                    setTrackStyle(track!!, style!!)
                style = null
            }
        } else if (localName?.equals(COLOR, ignoreCase = true) == true) {
            if (style != null && style!!.lineStyle != null) {
                try {
                    style!!.lineStyle!!.color = reverseColor(builder.toString().trim().toLong(16).toInt())
                } catch (e: NumberFormatException) {
                    style!!.lineStyle!!.color = Color.RED
                    Log.e(TAG, "Color format error", e)
                }
            }
        } else if (localName?.equals(WIDTH, ignoreCase = true) == true) {
            if (style != null && style!!.lineStyle != null) {
                try {
                    style!!.lineStyle!!.width = builder.toString().trim().toInt()
                } catch (e: NumberFormatException) {
                    style!!.lineStyle!!.width = 1
                    Log.e(TAG, "Width format error", e)
                }
            }
        }
    }

    private fun setTrackStyle(trk: Track, stl: Style) {
        if (stl.lineStyle != null) {
            trk.color = stl.lineStyle!!.color
            trk.width = stl.lineStyle!!.width
        }
    }

    open class Style {
        var id: String? = null
        var lineStyle: LineStyle? = null
    }

    open class ColorStyle {
        var color: Int = 0
    }

    open class LineStyle : ColorStyle() {
        var width: Int = 0
    }
}
