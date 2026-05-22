/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2013  Andrey Novikov <http://andreynovikov.info/>
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

package com.borkozic.map

import android.util.Log
import com.borkozic.util.CSV
import com.borkozic.util.OziExplorerFiles
import com.jhlabs.Point2D
import com.jhlabs.map.Datum
import com.jhlabs.map.Ellipsoid
import com.jhlabs.map.GeodeticPosition
import com.jhlabs.map.proj.ConicProjection
import com.jhlabs.map.proj.ProjectionException
import com.jhlabs.map.proj.ProjectionFactory
import com.jhlabs.map.proj.UniversalTransverseMercatorProjection
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.util.Hashtable

open class MapLoader {
    companion object {
        private const val serialVersionUID = 1L
        private var projections: Hashtable<String, String>? = null
        private val ellipsoids = arrayOf(
            Ellipsoid.AIRY,
            Ellipsoid.AIRY_MOD,
            Ellipsoid.AUSTRALIAN,
            Ellipsoid.BESSEL,
            Ellipsoid.CLARKE_1866,
            Ellipsoid.CLARKE_1880,
            Ellipsoid.EVEREST_30,
            Ellipsoid.EVEREST_48,
            Ellipsoid.FISCHER_MOD,
            Ellipsoid.EVEREST_PA,
            Ellipsoid.INDONESIAN,
            Ellipsoid.GRS_1980,
            Ellipsoid.HELMET,
            Ellipsoid.HOUGH,
            Ellipsoid.INTERNATIONAL_1924,
            Ellipsoid.KRASOVSKY,
            Ellipsoid.SA_1969,
            Ellipsoid.EVEREST_69,
            Ellipsoid.EVEREST_SS,
            Ellipsoid.WGS_1972,
            Ellipsoid.WGS_1984,
            Ellipsoid.BESSEL_NAM,
            Ellipsoid.EVEREST_56,
            Ellipsoid.CLARKE_1880_PAL,
            Ellipsoid.CLARKE_1880_IGN,
            Ellipsoid.HAYFORD,
            Ellipsoid.CLARKE_1858,
            Ellipsoid.BESSEL_NOR,
            Ellipsoid.PLESSIS,
            Ellipsoid.HAYFORD
        )

        @JvmStatic
        fun load(file: File, charset: String): Map {
            if (projections == null) {
                initialize()
            }

            val reader = BufferedReader(InputStreamReader(FileInputStream(file), charset))
            val map = Map(file.canonicalPath)
            try {
                var fields: Array<String>
                var line = reader.readLine()
                if (line == null || !line.startsWith("OziExplorer Map Data File")) {
                    reader.close()
                    throw IllegalArgumentException("Bad map header: " + map.mappath)
                }
                line = reader.readLine()
                map.title = line
                line = reader.readLine()
                map.imagePath = line
                reader.readLine() // Map Code
                line = reader.readLine()
                fields = CSV.parseLine(line)
                map.datum = fields[0]
                line = reader.readLine()
                fields = CSV.parseLine(line)
                if (fields[0] == "MSF") map.scaleFactor = 1 / fields[1].toDouble()
                reader.readLine() // Reserved
                while (reader.readLine().also({ line = it }) != null) {
                    fields = CSV.parseLine(line)
                    if (fields.isEmpty()) continue
                    if (fields[0].startsWith("Point") && fields.size == 17) {
                        val point = parsePoint(map, fields)
                        if (point != null) map.addCalibrationPoint(point)
                    }
                    if ("LLGRID" == fields[0] && fields.size == 14) {
                        parseLLGrid(map, fields)
                    }
                    if ("GRGRID" == fields[0] && fields.size == 15) {
                        parseOtherGrid(map, fields)
                    }
                    if ("IWH" == fields[0]) {
                        map.width = (fields[2].toInt() * map.scaleFactor).toInt()
                        map.height = (fields[3].toInt() * map.scaleFactor).toInt()
                    }
                    if ("MMPNUM" == fields[0]) {
                        map.setCornersAmount(fields[1].toInt())
                    }
                    if ("MMPXY" == fields[0]) {
                        try {
                            val i = fields[1].toInt() - 1
                            val x = (fields[2].toInt() * map.scaleFactor).toInt()
                            val y = (fields[3].toInt() * map.scaleFactor).toInt()
                            map.cornerMarkers!![i].x = x
                            map.cornerMarkers!![i].y = y
                        } catch (e: Exception) {
                            reader.close()
                            e.printStackTrace()
                            throw IllegalArgumentException("Bad XY corner marker: " + map.mappath)
                        }
                    }
                    if ("MMPLL" == fields[0]) {
                        try {
                            val i = fields[1].toInt() - 1
                            val lon = fields[2].toDouble()
                            val lat = fields[3].toDouble()
                            map.cornerMarkers!![i].lat = lat
                            map.cornerMarkers!![i].lon = lon
                        } catch (e: Exception) {
                            reader.close()
                            e.printStackTrace()
                            throw IllegalArgumentException("Bad LL corner marker: " + map.mappath)
                        }
                    }
                    if ("MM1B" == fields[0]) {
                        map.mpp = fields[1].toDouble()
                    }
                    if ("Map Projection" == fields[0]) {
                        map.prjName = fields[1]
                        val prj4spec = projections!![map.prjName]
                        if (prj4spec == null) {
                            reader.close()
                            throw ProjectionException("Unimplemented projection: " + map.prjName)
                        }
                        map.projection = ProjectionFactory.fromPROJ4Specification(prj4spec.split(" ".toRegex()).toTypedArray())
                    }
                    if ("Projection Setup" == fields[0]) {
                        parseProjectionParams(map, fields)
                    }
                }
                val datum = Datum.get(map.datum)
                if (datum == null) {
                    reader.close()
                    throw IllegalArgumentException("Datum " + map.datum + " not found")
                }
                if (datum != Datum.WGS_1984) map.projection!!.setEllipsoid(datum.ellipsoid)
                if ("" == map.projection!!.ellipsoid!!.shortName) map.projection!!.setEllipsoid(Ellipsoid.WGS_1984)
                map.projection!!.initialize()
                fixCalibration(map)
                fixCoords(map, datum)
                map.bind()
                fixCornerMarkers(map)
                map.debug()
                reader.close()
            } catch (e: IllegalArgumentException) {
                e.printStackTrace()
                map.loadError = e
            } catch (e: ProjectionException) {
                e.printStackTrace()
                map.loadError = e
            } catch (e: IndexOutOfBoundsException) {
                e.printStackTrace()
                map.loadError = e
            } catch (e: IOException) {
                e.printStackTrace()
                map.loadError = e
            }
            return map
        }

        private fun fixCoords(map: Map, datum: Datum) {
            Log.d("OZI", "map datum: $datum")
            if (Datum.WGS_1984 == datum) return
            for (mp in map.calibrationPoints) {
                val from = GeodeticPosition(mp.lat, mp.lon)
                val to = datum.transformToWGS84(from)
                // TODO MapPoint should extend GeodeticPosition
                mp.lat = to.lat
                mp.lon = to.lon
            }
            if (map.cornerMarkers != null) {
                for (mp in map.cornerMarkers!!) {
                    val from = GeodeticPosition(mp.lat, mp.lon)
                    val to = datum.transformToWGS84(from)
                    // TODO MapPoint should extend GeodeticPosition
                    mp.lat = to.lat
                    mp.lon = to.lon
                }
            }
            map.origDatum = map.datum
            map.datum = "WGS84"
            Log.d("OZI", "new datum: " + map.datum)
        }

        private fun fixCalibration(map: Map) {
            for (mp in map.calibrationPoints) {
                if (map.projection is UniversalTransverseMercatorProjection) {
                    if (mp.zone != 0) {
                        (map.projection!! as UniversalTransverseMercatorProjection).setUTMZone(mp.zone)
                    } else {
                        map.projection!!.projectionLongitudeDegrees =(mp.lon)
                        (map.projection!! as UniversalTransverseMercatorProjection).clearUTMZone()
                    }
                    (map.projection!! as UniversalTransverseMercatorProjection).setIsSouth(mp.hemisphere == 1)
                    map.projection!!.initialize()
                }
                if (mp.n != 0.0 && mp.e != 0.0) {
                    //Log.e("OZI", "fix: "+map.projection.getPROJ4Description());
                    val src = Point2D.Double(mp.e, mp.n)
                    val dst = Point2D.Double()
                    map.projection!!.inverseTransform(src, dst)
                    mp.lat = dst.y
                    mp.lon = dst.x
                    //Log.e("OZI", "fix: "+mp.n+" "+mp.e+" | "+mp.lat+" "+mp.lon);
                }
            }
            if (map.calibrationPoints.size == 2) {
                val mp1 = map.calibrationPoints[0]
                val mp2 = map.calibrationPoints[1]
                val mp3 = MapPoint(mp1)
                val mp4 = MapPoint(mp2)
                mp3.x = mp2.x
                mp4.x = mp1.x
                var src: Point2D.Double
                src = Point2D.Double(mp1.lon, mp1.lat)
                val dst1 = Point2D.Double()
                map.projection!!.transform(src.x, src.y, dst1)
                src = Point2D.Double(mp2.lon, mp2.lat)
                val dst2 = Point2D.Double()
                map.projection!!.transform(src.x, src.y, dst2)
                mp3.n = dst1.y
                mp3.e = dst2.x
                mp4.n = dst2.y
                mp4.e = dst1.x
                val dst = Point2D.Double()
                src = Point2D.Double(mp3.e, mp3.n)
                map.projection!!.inverseTransform(src, dst)
                mp3.lat = dst.y
                mp3.lon = dst.x
                src = Point2D.Double(mp4.e, mp4.n)
                map.projection!!.inverseTransform(src, dst)
                mp4.lat = dst.y
                mp4.lon = dst.x
                map.calibrationPoints.add(mp3)
                map.calibrationPoints.add(mp4)
            }
        }

        private fun fixCornerMarkers(map: Map) {
            if (map.cornerMarkers == null) {
                map.setCornersAmount(4)
                map.cornerMarkers!![0].x = 0
                map.cornerMarkers!![0].y = 0
                map.cornerMarkers!![1].x = 0
                map.cornerMarkers!![1].y = map.height - 1
                map.cornerMarkers!![2].x = map.width - 1
                map.cornerMarkers!![2].y = map.height - 1
                map.cornerMarkers!![3].x = map.width - 1
                map.cornerMarkers!![3].y = 0
                val ll = DoubleArray(2)
                for (i in 0..3) {
                    map.getLatLonByXY(map.cornerMarkers!![i].x, map.cornerMarkers!![i].y, ll)
                    map.cornerMarkers!![i].lat = ll[0]
                    map.cornerMarkers!![i].lon = ll[1]
                }
            }
        }

        private fun parseProjectionParams(map: Map, fields: Array<String>) {
            try {
                val origin_latitude = fields[1].toDouble()
                map.projection!!.projectionLatitudeDegrees =(origin_latitude)
            } catch (e: NumberFormatException) {
            }
            try {
                val central_meridian = fields[2].toDouble()
                map.projection!!.projectionLongitudeDegrees =(central_meridian)
            } catch (e: NumberFormatException) {
            }
            try {
                val scale_factor = fields[3].toDouble()
                map.projection!!.scaleFactor =(scale_factor)
            } catch (e: NumberFormatException) {
            }
            try {
                val false_easting = fields[4].toDouble()
                map.projection!!.falseEasting =(false_easting)
            } catch (e: NumberFormatException) {
            }
            try {
                val false_northing = fields[5].toDouble()
                map.projection!!.falseNorthing =(false_northing)
            } catch (e: NumberFormatException) {
            }
            try {
                val latitude_1 = fields[6].toDouble()
                if (map.projection is ConicProjection) {
                    (map.projection!! as ConicProjection).setProjectionLatitude1Degrees(latitude_1)
                }
            } catch (e: NumberFormatException) {
            }
            try {
                val latitude_2 = fields[7].toDouble()
                if (map.projection is ConicProjection) {
                    (map.projection!! as ConicProjection).setProjectionLatitude2Degrees(latitude_2)
                }
            } catch (e: NumberFormatException) {
            }
        }

        private fun parsePoint(map: Map, fields: Array<String>): MapPoint? {
            val point = MapPoint()
            //int n = Integer.parseInt(fields[0].substring("Point".length()));
            if ("ex" == fields[4]) return null
            try {
                point.x = (fields[2].toInt() * map.scaleFactor).toInt()
            } catch (e: NumberFormatException) {
                return null
            }
            try {
                point.y = (fields[3].toInt() * map.scaleFactor).toInt()
            } catch (e: NumberFormatException) {
                return null
            }
            try {
                val dlat = fields[6].toInt()
                val mlat = fields[7].toDouble()
                val hlat = fields[8]
                point.lat = dms_to_deg(dlat.toDouble(), mlat, 0.0)
                if ("S" == hlat) point.lat = -point.lat
            } catch (e: NumberFormatException) {
            }
            try {
                val dlon = fields[9].toInt()
                val mlon = fields[10].toDouble()
                val hlon = fields[11]
                point.lon = dms_to_deg(dlon.toDouble(), mlon, 0.0)
                if ("W" == hlon) point.lon = -point.lon
            } catch (e: NumberFormatException) {
            }
            try {
                point.zone = fields[13].toInt()
            } catch (e: NumberFormatException) {
            }
            try {
                point.e = fields[14].toDouble()
            } catch (e: NumberFormatException) {
            }
            try {
                point.n = fields[15].toDouble()
            } catch (e: NumberFormatException) {
            }
            point.hemisphere = if ("S" == fields[16]) 1 else 0
            return point
        }

        private fun parseLLGrid(map: Map, fields: Array<String>) {
            val grid = map.Grid()
            grid.enabled = "Yes" == fields[1]
            try {
                val sf = fields[2].split("\\s+".toRegex()).toTypedArray()
                grid.spacing = sf[0].toDouble()
                if ("Min" == sf[1]) grid.spacing /= 60
                if ("Sec" == sf[1]) grid.spacing /= 3660
            } catch (e: NumberFormatException) {
                e.printStackTrace()
                return
            }
            grid.autoscale = "Yes" == fields[3]
            try {
                grid.color1 = OziExplorerFiles.bgr2rgb(fields[4].toInt())
            } catch (e: NumberFormatException) {
                e.printStackTrace()
            }
            try {
                grid.color2 = OziExplorerFiles.bgr2rgb(fields[5].toInt())
            } catch (e: NumberFormatException) {
                e.printStackTrace()
            }
            try {
                grid.color3 = OziExplorerFiles.bgr2rgb(fields[6].toInt())
            } catch (e: NumberFormatException) {
                e.printStackTrace()
            }
            try {
                if ("No Labels" != fields[7]) {
                    val sf = fields[7].split("\\s+".toRegex()).toTypedArray()
                    grid.labelSpacing = sf[0].toDouble()
                    if ("Min" == sf[1]) grid.labelSpacing /= 60
                    if ("Sec" == sf[1]) grid.labelSpacing /= 3660
                }
            } catch (e: NumberFormatException) {
                e.printStackTrace()
                return
            }
            try {
                grid.labelForeground = OziExplorerFiles.bgr2rgb(fields[8].toInt())
            } catch (e: NumberFormatException) {
                e.printStackTrace()
            }
            try {
                grid.labelBackground = OziExplorerFiles.bgr2rgb(fields[9].toInt())
            } catch (e: NumberFormatException) {
                e.printStackTrace()
            }
            try {
                grid.labelSize = fields[10].toInt()
            } catch (e: NumberFormatException) {
                e.printStackTrace()
            }
            try {
                val i = fields[11].toInt()
                grid.labelShowEverywhere = i == 1
            } catch (e: NumberFormatException) {
                e.printStackTrace()
            }
            map.llGrid = grid
        }

        private fun parseOtherGrid(map: Map, fields: Array<String>) {
            val grid = map.Grid()
            grid.enabled = "Yes" == fields[1]
            try {
                val sf = fields[2].split("\\s+".toRegex()).toTypedArray()
                grid.spacing = sf[0].toDouble()
                if ("Km" == sf[1]) grid.spacing *= 1000
            } catch (e: NumberFormatException) {
                e.printStackTrace()
                return
            }
            grid.autoscale = "Yes" == fields[3]
            try {
                grid.color1 = OziExplorerFiles.bgr2rgb(fields[4].toInt())
            } catch (e: NumberFormatException) {
                e.printStackTrace()
            }
            try {
                grid.color2 = OziExplorerFiles.bgr2rgb(fields[5].toInt())
            } catch (e: NumberFormatException) {
                e.printStackTrace()
            }
            try {
                if ("No Labels" != fields[6]) {
                    val sf = fields[6].split("\\s+".toRegex()).toTypedArray()
                    grid.labelSpacing = sf[0].toDouble()
                    if ("Km" == sf[1]) grid.labelSpacing *= 1000
                }
            } catch (e: NumberFormatException) {
                e.printStackTrace()
                return
            }
            try {
                grid.labelForeground = OziExplorerFiles.bgr2rgb(fields[7].toInt())
            } catch (e: NumberFormatException) {
                e.printStackTrace()
            }
            try {
                grid.labelBackground = OziExplorerFiles.bgr2rgb(fields[8].toInt())
            } catch (e: NumberFormatException) {
                e.printStackTrace()
            }
            try {
                grid.labelSize = fields[9].toInt()
            } catch (e: NumberFormatException) {
                e.printStackTrace()
            }
            try {
                val i = fields[10].toInt()
                grid.labelShowEverywhere = i == 1
            } catch (e: NumberFormatException) {
                e.printStackTrace()
            }
            map.grGrid = grid
        }

        @JvmStatic
        fun dms_to_deg(deg: Double, min: Double, sec: Double): Double {
            return deg + min / 60 + sec / 3600
        }

        @JvmStatic
        fun getEllipsoid(index: Int): Ellipsoid? {
            if (index < 0 || index >= ellipsoids.size) return null
            return ellipsoids[index]
        }

        private fun initialize() {
            projections = Hashtable()
            projections!!["Latitude/Longitude"] = "+proj=longlat"
            projections!!["Mercator"] = "+proj=merc"
            projections!!["Transverse Mercator"] = "+proj=tmerc"
            projections!!["(UTM) Universal Transverse Mercator"] = "+proj=utm"
            projections!!["(BNG) British National Grid"] = "+proj=tmerc +lat_0=49 +lon_0=-2 +k=0.999601 +x_0=400000 +y_0=-100000"
            projections!!["(IG) Irish Grid"] = "+proj=tmerc +lat_0=53.5 +lon_0=-8 +k=1.000035 +x_0=200000 +y_0=250000 +a=6377340.189 +b=6356034.447938534"
            projections!!["(NZG) New Zealand Grid"] = "+proj=nzmg +lat_0=-41 +lon_0=173 +x_0=2510000 +y_0=6023150"
            projections!!["(SG) Swedish Grid"] = "+proj=tmerc +lat_0=0 +lon_0=15.80827777777778 +k=1 +x_0=1500000 +y_0=0"
            projections!!["(SUI) Swiss Grid"] = "+proj=somerc +ellps=bessel +x_0=600000 +y_0=200000"
            projections!!["(I) France Zone I"] = "+proj=lcc +lat_1=48.598523 +lat_2=50.395912 +lat_0=49.5 +lon_0=2.337229 +x_0=600000 +y_0=200000 +a=6378249.2 +b=6356515"
            projections!!["(II) France Zone II"] = "+proj=lcc +lat_1=45.898919 +lat_2=47.696014 +lat_0=46.8 +lon_0=2.337229 +x_0=600000 +y_0=2200000 +a=6378249.2 +b=6356515"
            projections!!["(III) France Zone III"] = "+proj=lcc +lat_1=43.199291 +lat_2=44.996094 +lat_0=44.1 +lon_0=2.337229 +x_0=600000 +y_0=200000 +a=6378249.2 +b=6356515"
            projections!!["(IV) France Zone IV"] = "+proj=lcc +lat_1=41.560388 +lat_2=42.767663 +lat_0=42.165 +lon_0=2.337229 +x_0=234.358 +y_0=4185861.369 +a=6378249.2 +b=6356515"
            projections!!["Lambert Conformal Conic"] = "+proj=lcc"
            projections!!["(A)Lambert Azimuthual Equal Area"] = "+proj=laea"
            projections!!["(EQC) Equidistant Conic"] = "+proj=eqdc"
            projections!!["Sinusoidal"] = "+proj=sinu"
            projections!!["Polyconic (American)"] = "+proj=poly"
            projections!!["Albers Equal Area"] = "+proj=aea"
            projections!!["Van Der Grinten"] = "+proj=vandg"
            projections!!["Vertical Near-Sided Perspective"] = "+proj=nsper"
            projections!!["(WIV) Wagner IV"] = "+proj=wag4"
            projections!!["Bonne"] = "+proj=bonne"
            projections!!["(MT0) Montana State Plane Zone 2500"] = "+proj=lcc +lat_1=45 +lat_2=49 +lat_0=44.25 +lon_0=-109.5 +x_0=600000 +y_0=0"
            projections!!["(ITA1) Italy Grid Zone 1"] = "+proj=tmerc +lat_0=0 +lon_0=-3.45233333333333 +k=0.999600 +x_0=1500000 +y_0=0"
            projections!!["(ITA2) Italy Grid Zone 2"] = "+proj=tmerc +lat_0=0 +lon_0=2.54766666666666 +k=0.999600 +x_0=2520000 +y_0=0"
            projections!!["(VICMAP-TM) Victoria Aust.(pseudo AMG)"] = "+proj=tmerc +lat_0=145 +x_0=500000 +y_0=10000000"
            projections!!["(VICGRID) Victoria Australia"] = "+proj=lcc +lat_1=-36 +lat_2=-38 +lat_0=-37 +lon_0=145 +x_0=2500000 +y_0=4500000"
            projections!!["(VG94) VICGRID94 Victoria Australia"] = "+proj=lcc +lat_1=-36 +lat_2=-38 +lat_0=-37 +lon_0=145 +x_0=2500000 +y_0=2500000"
        }
    }
}