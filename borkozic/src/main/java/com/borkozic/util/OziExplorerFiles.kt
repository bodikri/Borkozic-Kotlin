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

import com.borkozic.data.Area
import com.borkozic.data.Route
import com.borkozic.data.Track
import com.borkozic.data.Track.TrackPoint
import com.borkozic.data.Waypoint
import com.borkozic.map.MapLoader
import com.jhlabs.map.Datum
import com.jhlabs.map.Ellipsoid
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.ArrayList
import java.util.Locale

/**
 * Helper class to read and write OziExplorer files.
 * 
 * @author Andrey Novikov
 */
class OziExplorerFiles {
    companion object {
        @JvmStatic
        val numFormat = DecimalFormat("* ###0")
        
        @JvmStatic
        val coordFormat = DecimalFormat("* ###0.000000", DecimalFormatSymbols(Locale.ENGLISH))
        
        /**
         * Loads waypoints from file
         * 
         * @param file valid `File` with waypoints
         * @return `List` of `Waypoint`s
         * @throws IOException 
         */
        @JvmStatic
        fun loadWaypointsFromFile(file: File, charset: String): List<Waypoint> {
            val waypoints = ArrayList<Waypoint>()

            val reader = BufferedReader(InputStreamReader(FileInputStream(file), charset))
            
            var line: String? = null

            // OziExplorer Waypoint File Version 1.0
            reader.readLine()
            // WGS 84
            reader.readLine()
            // Reserved 2
            reader.readLine()
            // Reserved 3
            reader.readLine()
            //21,PTRS          , -26.636541, 152.449640,35640.91155, 0, 1, 3,  16777215,  16711935,Peach Trees Camping area                , 0, 0
            while (reader.readLine().also { line = it } != null) {
                try {
                    val fields = CSV.parseLine(line!!)
                    if (fields.size >= 11) {
                        if ("" == fields[1])
                            fields[1] = "WPT"+fields[0]
                        
                        val waypoint = Waypoint(fields[1].replace(209.toChar(), ','), Entities.XML.unescape(fields[10]), fields[2].toDouble(), fields[3].toDouble())

                        if ("" != fields[4]) {
                            try {
                                waypoint.date = TDateTime.dateFromDateTime(fields[4].toDouble())
                            } catch (e: NumberFormatException) {
                                e.printStackTrace()
                            }
                        }
                        
                        if ("" != fields[8]) {
                            try {
                                val fgcolor = fields[8].toInt()
                                if (fgcolor != 0)
                                    waypoint.textcolor = bgr2rgb(fgcolor)
                            } catch (e: NumberFormatException) {
                            }
                        }
                        if ("" != fields[9]) {
                            try {
                                val bgcolor = fields[9].toInt()
                                if (bgcolor != 65535)
                                    waypoint.backcolor = bgr2rgb(bgcolor)
                            } catch (e: NumberFormatException) {
                            }
                        }

                        if (fields.size >= 14 && "" != fields[13]) {
                            try {
                                waypoint.proximity = fields[13].toInt()
                            } catch (e: NumberFormatException) {
                            }
                        }

                        if (fields.size >= 15 && "" != fields[14]) {
                            try {
                                val alt = fields[14].toDouble().toInt()
                                waypoint.altitude = if (alt == -777) Integer.MIN_VALUE.toDouble() else alt.toDouble()
                            } catch (e: NumberFormatException) {
                            }
                        }

                        if (fields.size >= 22 && "" != fields[21]) {
                            waypoint.image = fields[21]
                        }
                        waypoints.add(waypoint)
                    }
                } catch (e: IllegalArgumentException) {
                    //TODO Show error to user
                    e.printStackTrace()
                }
            }
            reader.close()

            return waypoints
        }
        
        /**
         * Saves waypoints to file.
         * 
         * @param file valid `File`
         * @param waypoints `List` of `Waypoint`s to save
         * @throws IOException
         */
        @JvmStatic
        fun saveWaypointsToFile(file: File, charset: String, waypoints: List<Waypoint>) {
            val writer = BufferedWriter(OutputStreamWriter(FileOutputStream(file, false), charset))

            writer.write("OziExplorer Waypoint File Version 1.1\n" +
                      "WGS 84\n" +
                      "Reserved 2\n" +
                      "Reserved 3\n")
            
            //*  One line per waypoint
            //* each field separated by a comma
            //* comma's not allowed in text fields, character 209 can be used instead and a comma will be substituted.
            //* non essential fields need not be entered but comma separators must still be used (example ,,)
            //  defaults will be used for empty fields
            //* Any number of the last fields in a data line need not be included at all not even the commas.

            //Field 1 : Number - this is the location in the array (max 1000), must be unique, usually start at 1 and increment. Can be set to -1 (minus 1) and the number will be auto generated.
            //Field 2 : Name - the waypoint name, use the correct length name to suit the GPS type.
            //Field 3 : Latitude - decimal degrees
            //Field 4 : Longitude - decimal degrees
            //Field 5 : Date - see Date Format below, if blank a preset date will be used
            //Field 6 : Symbol - 0 to number of symbols in GPS
            //Field 7 : Status - always set to 1
            //Field 8 : Map Display Format
            //Field 9 : Foreground Color (RGB value)
            //Field 10 : Background Color (RGB value)
            //Field 11 : Description (max 40), no commas
            //Field 12 : Pointer Direction
            //Field 13 : Garmin Display Format
            //Field 14 : Proximity Distance - 0 is off any other number is valid
            //Field 15 : Altitude - in feet (-777 if not valid)
            //Field 16 : Font Size - in points
            //Field 17 : Font Style - 0 is normal, 1 is bold.
            //Field 18 : Symbol Size - 17 is normal size
            //Field 19 : Proximity Symbol Position
            //Field 20 : Proximity Time
            //Field 21 : Proximity or Route or Both
            //Field 22 : File Attachment Name
            //Field 23 : Proximity File Attachment Name
            //Field 24 : Proximity Symbol Name 
        
            synchronized(waypoints) {
                for (wpt in waypoints) {
                    writer.write("-1,")
                    writer.write(wpt.name.replace(',', 209.toChar())+",")
                    writer.write(coordFormat.format(wpt.latitude)+","+coordFormat.format(wpt.longitude)+",")
                    writer.write((if (wpt.date == null) "" else TDateTime.toDateTime(wpt.date!!).toString())+",")
                    writer.write("0,1,3,")
                    writer.write(if (wpt.textcolor != Integer.MIN_VALUE) rgb2bgr(wpt.textcolor).toString() else "")
                    writer.write(",")
                    writer.write(if (wpt.backcolor != Integer.MIN_VALUE) rgb2bgr(wpt.backcolor).toString() else "")
                    writer.write(",")
                    writer.write(Entities.XML.escape(wpt.description) + ",")
                    writer.write("2,0,")
                    writer.write(wpt.proximity.toString() + ",")
                    writer.write(if (wpt.altitude == Integer.MIN_VALUE.toDouble()) "-777" else wpt.altitude.toInt().toString() + ",")
                    writer.write(",,,,,,")
                    writer.write(wpt.image+",,")
                    writer.write("\n")
                }
            }
            writer.close()
        }
        
        /**
         * Loads track from file.
         * 
         * @param file valid `File` with track points
         * @return `Track` with track points
         * @throws IOException on file read error
         * @throws IllegalArgumentException if file format is not plt
         */
        @JvmStatic
        fun loadTrackFromFile(file: File, charset: String): Track {
            return loadTrackFromFile(file, charset, 0)
        }
        
        /**
         * Loads track from file.
         * 
         * @param file valid `File` with track points
         * @param lines number of last lines to read
         * @return `Track` with track points
         * @throws IOException on file read error
         * @throws IllegalArgumentException if file format is not plt
         */
        @JvmStatic
        fun loadTrackFromFile(file: File, charset: String, lines: Long): Track {
            val track = Track()
            
            var skip: Long = 0
            if (lines > 0) {
                skip = file.length() - 35 * lines // 35 - average line length in conventional track file
            }

            val reader = BufferedReader(InputStreamReader(FileInputStream(file), charset))

            var line: String? = null

            // OziExplorer Track Point File Version 2.0
            if (reader.readLine().also { line = it } == null) {
                reader.close()
                throw IllegalArgumentException("Bad track file")
            }
            skip -= line!!.length
            // WGS 84
            if (reader.readLine().also { line = it } == null) {
                reader.close()
                throw IllegalArgumentException("Bad track file")
            }
            skip -= line!!.length
            // Altitude is in Feet
            if (reader.readLine().also { line = it } == null) {
                reader.close()
                throw IllegalArgumentException("Bad track file")
            }
            skip -= line!!.length
            // Reserved 3
            if (reader.readLine().also { line = it } == null) {
                reader.close()
                throw IllegalArgumentException("Bad track file")
            }
            skip -= line!!.length
            // 0,2,255,OziCE Track Log File,1
            if (reader.readLine().also { line = it } == null) {
                reader.close()
                throw IllegalArgumentException("Bad track file")
            }
            skip -= line!!.length
            val fields = CSV.parseLine(line!!)
            if (fields.size < 4) {
                reader.close()
                throw IllegalArgumentException("Bad track file")
            }
            track.width = fields[1].toInt()
            track.color = bgr2rgb(fields[2].toInt())
            track.name = fields[3]
            // 0
            if (reader.readLine().also { line = it } == null) {
                reader.close()
                throw IllegalArgumentException("Bad track file")
            }
            skip -= line!!.length
            skip -= 12 // new line characters
            
            if (skip > 0) {
                reader.skip(skip)
                reader.readLine() // skip broken line
            }

            //   55.6384683,  37.3516133,0,    583.0,    0.0000000 ,290705,185332.996
            while (reader.readLine().also { line = it } != null) {
                val parsedFields = CSV.parseLine(line!!)
                val time = if (parsedFields.size > 4) TDateTime.fromDateTime(parsedFields[4].toDouble()) else 0L
                val elevation = if (parsedFields.size > 3) parsedFields[3].toDouble() * 0.3048 else 0.0 // TODO Какво преобразуване се прави тук?
                if (parsedFields.size >= 3)
                    track.addPoint("0" == parsedFields[2], parsedFields[0].toDouble(), parsedFields[1].toDouble(), elevation, 0.0, 0.0, 0.0, time)
            }
            reader.close()
            
            track.show = true
            track.filepath = file.canonicalPath
            if ("" == track.name)
                track.name = track.filepath!!

            return track
        }

        /**
         * Saves track to file.
         * 
         * @param file valid `File`
         * @param charset the string describing the desired character encoding
         * @param track `Track` object containing the list of track points to save
         * @throws IOException
         */
        @JvmStatic
        fun saveTrackToFile(file: File, charset: String, track: Track) {
            val writer = BufferedWriter(OutputStreamWriter(FileOutputStream(file, false), charset))

            writer.write("OziExplorer Track Point File Version 2.1\n" +
                    "WGS 84\n" +
                    "Altitude is in Feet\n" +
                    "Reserved 3\n")

            // Field 1 : always zero (0)
            // Field 2 : width of track plot line on screen - 1 or 2 are usually the best
            // Field 3 : track color (RGB)
            // Field 4 : track description (no commas allowed)
            // Field 5 : track skip value - reduces number of track points plotted, usually set to 1
            // Field 6 : track type - 0 = normal , 10 = closed polygon , 20 = Alarm Zone
            // Field 7 : track fill style - 0 =bsSolid; 1 =bsClear; 2 =bsBdiagonal; 3 =bsFdiagonal; 4 =bsCross;
            // 5 =bsDiagCross; 6 =bsHorizontal; 7 =bsVertical;
            // Field 8 : track fill color (RGB)
            writer.write("0,"+track.width.toString()+","+
                    rgb2bgr(track.color).toString()+","+
                    track.name.replace(',', 209.toChar())+",0,0\n"+
                    "0\n")
        
            //Field 1 : Latitude - decimal degrees
            //Field 2 : Longitude - decimal degrees
            //Field 3 : Code - 0 if normal, 1 if break in track line
            //Field 4 : Altitude in feet (-777 if not valid)
            //Field 5 : Date - see Date Format below, if blank a preset date will be used
            //Field 6 : Date as a string
            //Field 7 : Time as a string
            // Note that OziExplorer reads the Date/Time from field 5, the date and time in fields 6 & 7 are ignored.
        
            //-27.350436, 153.055540,1,-777,36169.6307194, 09-Jan-99, 3:08:14 
        
            val trackPoints = track.points
            synchronized(trackPoints) {
                for (tp in trackPoints) {
                    writer.write(coordFormat.format(tp.latitude)+","+coordFormat.format(tp.longitude)+",")
                    if (tp.continous)
                        writer.write("0")
                    else
                        writer.write("1")
                    writer.write(","+(Math.round(tp.elevation * 3.2808399)).toString())
                    if (tp.time > 0) {
                        writer.write(","+TDateTime.toDateTime(tp.time).toString())
                    }
                    writer.write("\n")
                }
            }
            writer.close()
        }
        
        /**
         * Loads routes from file.
         * 
         * @param file valid `File` with route waypoints
         * @return `List<Route>` the list of routes
         * @throws IOException on file read error
         * @throws IllegalArgumentException if file format is not rt2 or rte
         */
        @JvmStatic
        fun loadRoutesFromFile(file: File, charset: String): List<Route> {
            val routes = ArrayList<Route>()
            
            val reader = BufferedReader(InputStreamReader(FileInputStream(file), charset))
            
            var line = reader.readLine()
            var fields = CSV.parseLine(line)
            
            if ("H1" == fields[0]) {
                // rt2 format
                // H1,OziExplorer CE Route2 File Version 1.0
                // H2,WGS 84
                line = reader.readLine()//чете цял ред
                fields = CSV.parseLine(line) // чете поле от ред (полетата са разделени от запетайка)
                if ("H2" != fields[0]) {
                    reader.close()
                    throw IllegalArgumentException("Bad rt2 header")
                }
                /* H3,My route,show/hide,0
                Field 0 : H3
                Field 1 : Name
                Field 2 : show/hide (1/0)
                Field 3 : color
                */
                line = reader.readLine()
                fields = CSV.parseLine(line)
                if ("H3" != fields[0]) {
                    reader.close()
                    throw IllegalArgumentException("Bad rt2 header")
                }
                val route = Route()
                routes.add(route)
                route.name = fields[1].replace(209.toChar(), ',')
                try {
                    route.show = fields[2].toBoolean()
                } catch (e: NumberFormatException) {
                }
                try {
                    val color = fields[3].toInt()
                    if (color != 0)
                        route.lineColor = bgr2rgb(color)
                } catch (e: NumberFormatException) {
                }
                /* W,Tsapelka,  58.0460242,  28.9465437,1500,0
                Field 0 : W
                Field 1 : Name
                Field 2 : Latitude - decimal degrees
                Field 3 : Longitude - decimal degrees
                Field 4 : Altitude - meters - added from me Boris Stoykov
                Field 5 : proximity - ако е въведено!
                Field 6 : Code - 0 if normal, 1 if silent
                */
                //
                while (reader.readLine().also { line = it } != null) {
                    fields = CSV.parseLine(line)
                    if ("W" != fields[0])
                        continue
                    route.addWaypoint(fields[1].replace(209.toChar(), ','), fields[2].toDouble(), fields[3].toDouble(), fields[4].toDouble())//Fields[4] - for Altitude is added from me
                    // Format extension (probably not compatible with OziExplorer)
                    if (fields.size > 6) //Changed from 5 to 6
                    {
                        try {
                            val proximity = fields[5].toInt()
                            if (proximity > 0)
                                route.getWaypoint(route.length() - 1).proximity = proximity
                        } catch (e: NumberFormatException) {
                        }
                    }

                }
                reader.close()
                
                route.show = true
                route.filepath = file.canonicalPath
                if ("" == route.name)
                    route.name = route.filepath!!
            } else if ("OziExplorer Route File Version 1.0" == fields[0]) {
                // rte format

                //OziExplorer Route File Version 1.0
                //WGS 84
                line = reader.readLine()
                //Reserved 1
                line = reader.readLine()
                //Reserved 2
                line = reader.readLine()
                var route: Route? = null
                var routeNum = -1
                var wptNum = 0
                //R,  0,ROUTE 1         ,Description,255
                //W,  0,  1, 29,29              , -26.568702, 152.369428,35640.9202400, 0, 1, 0,   8388608,     65535,, 0, 0
                //W,  1,  2, 35,35              , -26.550290, 152.416844,35641.5077900, 0, 1, 0,   8388608,     65535,, 0, 0
                while (reader.readLine().also { line = it } != null) {
                    fields = CSV.parseLine(line)
                    val rtn = fields[1].toInt()
                    if ("R" == fields[0]) {
                        if (rtn == routeNum + 1) {
                            if (route != null) {
                                if (route.length() > 0) {
                                    route.show = true
                                    if (routeNum == 0)
                                        route.filepath = file.canonicalPath
                                    if ("" == route.name)
                                        route.name = "R$routeNum"
                                    routes.add(route)
                                }
                                route = null                            
                            }
                            route = Route()
                            route.name = fields[2].replace(209.toChar(), ',')
                            route.description = fields[3].replace(209.toChar(), ',')
                            route.show = true
                            route.filepath = file.canonicalPath
                            try {
                                val color = fields[4].toInt()
                                if (color != 0)
                                    route.lineColor = bgr2rgb(color)
                            } catch (e: NumberFormatException) {
                            }
                            routeNum = rtn
                            wptNum = 0
                        } else {
                            reader.close()
                            throw IllegalArgumentException("Bad route file")
                        }
                    } else if ("W" == fields[0]) {
                        if (rtn != routeNum) {
                            reader.close()
                            throw IllegalArgumentException("Bad route file")
                        }
                        val wpn = fields[2].toInt()
                        if (wpn != wptNum + 1) {
                            reader.close()
                            throw IllegalArgumentException("Bad route file")
                        }
                        wptNum++
                        //W, 1, 2, 35,35              , -26.550290, 152.416844,35641.5077900, 0, 1, 0,   8388608,     65535,, 0, 0
                        if ("" == fields[4])
                            fields[4] = "RWPT$wptNum"
                        route!!.addWaypoint(Waypoint(fields[4].replace(209.toChar(), ','), fields[13].replace(209.toChar(), ','), fields[5].toDouble(), fields[6].toDouble(), fields[7].toDouble()))
                        //Fields[7] - for Altitude is added from me
                    }
                }
            } else {
                reader.close()
                throw IllegalArgumentException("Bad route file")
            }

            return routes
        }

        @JvmStatic
        fun saveRouteToFile(file: File, charset: String, route: Route) {
            val writer = BufferedWriter(OutputStreamWriter(FileOutputStream(file, false), charset))
            
            writer.write("H1,OziExplorer CE Route2 File Version 1.0\n" + "H2,WGS 84\n")

            // Field 0 : H3
            // Field 1 : route name (no commas allowed)
            // Field 2 : ???-проба да записва Show/hide
            // Field 3 : route color (RGB)
            writer.write("H3,"+route.name.replace(',', 209.toChar())+"," + route.show.toString() + ","+rgb2bgr(route.lineColor).toString()+"\n")
        
            //Field 0 : W
            //Field 1 : Name
            //Field 2 : Latitude - decimal degrees
            //Field 3 : Longitude - decimal degrees
            //Field 4 : Altitude - meters - added from me
            //Field 5 : proximity
            //Field 6 : Code - 0 if normal, 1 if silent
        
            // W,Tsapelka,  58.0460242,  28.9465437, 1500,0
        
            val waypoints = route.waypoints
            synchronized(waypoints) {
                for (wpt in waypoints) {
                    writer.write("W,") //Field 0
                    writer.write(wpt.name.replace(',', 209.toChar())+",")//Field 1
                    writer.write(coordFormat.format(wpt.latitude)+","+coordFormat.format(wpt.longitude)+","+wpt.altitude.toString()+",")//Field 2,3, [4] - for Altitude is added from me
                    if (wpt.silent)
                        writer.write("1")
                    else
                        writer.write("0")
                    // Format extension (probably not compatible with OziExplorer)
                    if (wpt.proximity > 0)
                        writer.write("," + wpt.proximity.toString())
                    writer.write("\n")
                }
            }
            writer.close()
        }

        /**
         * Loads areas from file.
         *
         * @param file valid `File` with area waypoints
         * @return `List<Area>` the list of areas
         * @throws IOException on file read error
         * @throws IllegalArgumentException if file format is not rt2 or rte
         */
        @JvmStatic
        fun loadAreasFromFile(file: File, charset: String): List<Area> {
            val areas = ArrayList<Area>()

            val reader = BufferedReader(InputStreamReader(FileInputStream(file), charset))

            var line = reader.readLine()
            var fields = CSV.parseLine(line)

            if ("H1" == fields[0]) {
                // rt2 format
                // H1,OziExplorer CE Area2 File Version 1.0
                // H2,WGS 84
                line = reader.readLine()
                fields = CSV.parseLine(line)
                if ("H2" != fields[0]) {
                    reader.close()
                    throw IllegalArgumentException("Bad rt2 header")
                }
                /* H3,My_area_NAME,show/hide,0(colorLine),0(colorFill)
                 Field 0 : H3
                 Field 1 : area name (no commas allowed)
                 Field 2 : ???-проба да записва Show/hide
                 Field 3 : area line color (RGB)
                 Field 4 : area fill color (RGB)
                 Field 5 : area fill color transparency (int)
                */
                line = reader.readLine()
                fields = CSV.parseLine(line)
                if ("H3" != fields[0]) {
                    reader.close()
                    throw IllegalArgumentException("Bad rt2 header")
                }
                val area = Area()
                areas.add(area)
                area.name = fields[1].replace(209.toChar(), ',')
                try {
                    area.show = fields[2].toBoolean()
                } catch (e: NumberFormatException) {
                }
                try {
                    val color = fields[3].toInt()
                    if (color != 0)
                        area.lineColor = bgr2rgb(color)
                } catch (e: NumberFormatException) {
                }
                try {
                    val color = fields[4].toInt()
                    if (color != 0)
                        area.fillColor = bgr2rgb(color)
                } catch (e: NumberFormatException) {
                }

                try {
                    area.AreaTransperency = fields[5].toInt()
                } catch (e: NumberFormatException) {
                }
                /*
                 W,Tsapelka,  58.0460242,  28.9465437,1500,0
                Field 0 : W
                Field 1 : Name
                Field 2 : Latitude - decimal degrees
                Field 3 : Longitude - decimal degrees
                Field 4 : Altitude - meters - added from me Boris Stoykov
                Field 5 : Code - 0 if normal, 1 if silent
                */
                while (reader.readLine().also { line = it } != null) {
                    fields = CSV.parseLine(line)
                    if ("C" == fields[0]) {
                        area.addAreaCenter(fields[1].replace(209.toChar(), ','), fields[2].toDouble(), fields[3].toDouble(), fields[4].toDouble())//Fields[4] - for Altitude is added from me
                        // Format extension (probably not compatible with OziExplorer)
                        if (fields.size > 6) //Changed from 5 to 6
                        {
                            try {
                                val proximity = fields[5].toInt()
                                if (proximity > 0)
                                    area.getWaypoint(area.length() - 1).proximity = proximity
                            } catch (e: NumberFormatException) {
                            }
                        }
                    }
                    if ("W" != fields[0])
                        continue
                    area.addWaypoint(fields[1].replace(209.toChar(), ','), fields[2].toDouble(), fields[3].toDouble(), fields[4].toDouble())//Fields[4] - for Altitude is added from me
                    // Format extension (probably not compatible with OziExplorer)
                    if (fields.size > 6) //Changed from 5 to 6
                    {
                        try {
                            val proximity = fields[5].toInt()
                            if (proximity > 0)
                                area.getWaypoint(area.length() - 1).proximity = proximity
                        } catch (e: NumberFormatException) {
                        }
                    }

                }
                reader.close()

                area.show = true
                area.filepath = file.canonicalPath
                if ("" == area.name)
                    area.name = area.filepath!!
            } else {
                reader.close()
                throw IllegalArgumentException("Bad area file")
            }

            return areas
        }

        @JvmStatic
        fun saveAreaToFile(file: File, charset: String, area: Area) {
            val writer = BufferedWriter(OutputStreamWriter(FileOutputStream(file, false), charset))

            writer.write("H1,OziExplorer CE Area2 File Version 1.0\n" +
                    "H2,WGS 84\n")

            // Field 0 : H3
            // Field 1 : area name (no commas allowed)
            // Field 2 : ???-проба да записва Show/hide
            // Field 3 : area line color (RGB)
            // Field 4 : area fill color (RGB)
            // Field 5 : area fill color transparency (int)
            writer.write("H3,"+area.name.replace(',', 209.toChar())+"," + area.show.toString() + ","+rgb2bgr(area.lineColor).toString()+ ","+rgb2bgr(area.fillColor).toString()+","+area.AreaTransperency.toString()+"\n")

            /*AreaCenter
            writer.write("C,")
            writer.write(area.AreaCenter.name.replace(',', 209.toChar())+",")
            writer.write(coordFormat.format(area.AreaCenter.latitude)+","+coordFormat.format(area.AreaCenter.longitude)+","+area.AreaCenter.altitude.toString()+",")//Fields[5] - for Altitude is added from me
            if (area.AreaCenter.silent)
                writer.write("1")
            else
                writer.write("0")
            // Format extension (probably not compatible with OziExplorer)
            if (area.AreaCenter.proximity > 0)
                writer.write("," + area.AreaCenter.proximity.toString())
            writer.write("\n")
            */
            val waypoints = area.waypoints
            //Field 0 : W
            //Field 1 : Name
            //Field 2 : Latitude - decimal degrees
            //Field 3 : Longitude - decimal degrees
            //Field 4 : Altitude - meters - added from me
            //Field 5 : proximity
            //Field 6 : Code - 0 if normal, 1 if silent
            // W,Tsapelka,  58.0460242,  28.9465437,1500,0
            synchronized(waypoints) {
                for (wpt in waypoints) {
                    writer.write("W,")
                    writer.write(wpt.name.replace(',', 209.toChar())+",")
                    writer.write(coordFormat.format(wpt.latitude)+","+coordFormat.format(wpt.longitude)+","+wpt.altitude.toString()+",")//Fields[5] - for Altitude is added from me
                    if (wpt.silent)
                        writer.write("1")
                    else
                        writer.write("0")
                    // Format extension (probably not compatible with OziExplorer)
                    if (wpt.proximity > 0)
                        writer.write("," + wpt.proximity.toString())
                    writer.write("\n")
                }
            }

            writer.close()
        }

        @JvmStatic
        fun bgr2rgb(bgr: Int): Int {
            return 0xFF000000.toInt() or ((bgr and 0x00FF0000) ushr 16) or ((bgr and 0x000000FF) shl 16) or (bgr and 0x0000FF00)
        }

        @JvmStatic
        fun rgb2bgr(rgb: Int): Int {
            return ((rgb and 0x00FF0000) ushr 16) or ((rgb and 0x000000FF) shl 16) or (rgb and 0x0000FF00)
        }

        @JvmStatic
        @Throws(IOException::class)
        fun loadDatums(file: File) {
            val reader = BufferedReader(FileReader(file))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val fields = CSV.parseLine(line!!)
                if (fields.size == 5) {
                    try {
                        val e = fields[1].toInt()
                        val ellipsoid = MapLoader.getEllipsoid(e)
                        val dx = fields[2].toDouble()
                        val dy = fields[3].toDouble()
                        val dz = fields[4].toDouble()
                        if (ellipsoid != null) {
                            // no need to get object reference because it is registered in constructor
                            Datum(fields[0], ellipsoid, dx, dy, dz)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            reader.close()
        }
    }
}