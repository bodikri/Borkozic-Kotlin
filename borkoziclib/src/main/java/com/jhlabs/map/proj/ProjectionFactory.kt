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
 * Bernhard Jenny, Institute of Cartography, ETH Zurich:
 * Added private static Hashtable nameMap which maps between human-readable
 * projection names and proj4 names.
 * Changed ProjectionFactoryregister() to fill the new nameMap.
 * Changed initialize() to instantiate nameMap.
 * Added method getNamedProjection() to first map from a human-readable name to a
 * proj4 name and then to a Projection class.
 * Changed register(): the human-readable name of a projection is not passed anymore
 * to register(), but the name is retrieved from an instance of the projection. This
 * avoids inconsistencies between names returned by the projection and names passed
 * to register().
 * 20 October 2010: modified readProjectionFile such that opened file with
 * coordinate system specification is always closed when the method exits.
 */
package com.jhlabs.map.proj

import com.jhlabs.Point2D
import com.jhlabs.map.AngleFormat
import com.jhlabs.map.Ellipsoid
import com.jhlabs.map.MapMath
import com.jhlabs.map.Units.findUnits
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.StreamTokenizer
import java.lang.Boolean
import java.util.Arrays
import java.util.Hashtable
import java.util.StringTokenizer
import java.util.Vector
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.Any
import kotlin.Array
import kotlin.Double
import kotlin.String
import kotlin.Throws
import kotlin.also
import kotlin.arrayOf
import kotlin.arrayOfNulls
import kotlin.code
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

object ProjectionFactory {
    private const val SIXTH = .1666666666666666667 /* 1/6 */

    private const val RA4 = .04722222222222222222 /* 17/360 */

    private const val RA6 = .02215608465608465608 /* 67/3024 */

    private const val RV4 = .06944444444444444444 /* 5/72 */

    private const val RV6 = .04243827160493827160 /* 55/1296 */

    private val format = AngleFormat(AngleFormat.ddmmssPattern, true)

    /**
     * Return a projection initialized with a PROJ.4 argument list.
     */
    @JvmStatic
    fun fromPROJ4Specification(args: Array<String?>): Projection {
        var projection: Projection? = null
        var ellipsoid: Ellipsoid? = null
        var a = 0.0
        var b = 0.0
        var es = 0.0

        val params: Hashtable<Any?, Any?> = Hashtable<Any?, Any?>()
        for (i in args.indices) {
            val arg = args[i]!!
            if (arg.startsWith("+")) {
                val index = arg.indexOf('=')
                if (index != -1) {
                    val key = arg.substring(1, index)
                    val value = arg.substring(index + 1)
                    params.put(key, value)
                }
            }
        }

        var s: String?
        s = params.get("proj") as String?
        if (s != null) {
            projection = getNamedPROJ4Projection(s)
            if (projection == null) {
                throw ProjectionException("Unknown projection: " + s)
            }
        }

        s = params.get("init") as String?
        if (s != null) {
            projection = getNamedPROJ4CoordinateSystem(s)
            if (projection == null) {
                throw ProjectionException("Unknown projection: " + s)
            }
            a = projection.equatorRadius
            es = projection.getEllipsoid().getEccentricitySquared()
        }

        // Set the ellipsoid
        var ellipsoidName: String? = ""
        s = params.get("R") as String?
        if (s != null) {
            a = s.toDouble()
        } else {
            s = params.get("ellps") as String?
            if (s == null) {
                s = params.get("datum") as String?
            }
            if (s != null) {
                val ellipsoids: Array<Ellipsoid?> = Ellipsoid.ellipsoids as Array<Ellipsoid?>
                for (i in ellipsoids.indices) {
                    if (ellipsoids[i]!!.shortName == s) {
                        ellipsoid = ellipsoids[i]
                        break
                    }
                }
                if (ellipsoid == null) {
                    throw ProjectionException("Unknown ellipsoid: " + s)
                }
                es = ellipsoid.eccentricity2
                a = ellipsoid.equatorRadius
                ellipsoidName = s
            } else {
                s = params.get("a") as String?
                if (s != null) {
                    a = s.toDouble()
                }
                s = params.get("es") as String?
                if (s != null) {
                    es = s.toDouble()
                } else {
                    s = params.get("rf") as String?
                    if (s != null) {
                        es = s.toDouble()
                        es = es * (2.0 - es)
                    } else {
                        s = params.get("f") as String?
                        if (s != null) {
                            es = s.toDouble()
                            es = 1.0 / es
                            es = es * (2.0 - es)
                        } else {
                            s = params.get("b") as String?
                            if (s != null) {
                                b = s.toDouble()
                                es = 1.0 - (b * b) / (a * a)
                            }
                        }
                    }
                }
                if (b == 0.0) {
                    b = a * sqrt(1.0 - es)
                }
            }

            s = params.get("R_A") as String?
            if (s != null && Boolean.getBoolean(s)) {
                a *= 1.0 - es * (SIXTH + es * (RA4 + es * RA6))
            } else {
                s = params.get("R_V") as String?
                if (s != null && Boolean.getBoolean(s)) {
                    a *= 1.0 - es * (SIXTH + es * (RV4 + es * RV6))
                } else {
                    s = params.get("R_a") as String?
                    if (s != null && Boolean.getBoolean(s)) {
                        a = .5 * (a + b)
                    } else {
                        s = params.get("R_g") as String?
                        if (s != null && Boolean.getBoolean(s)) {
                            a = sqrt(a * b)
                        } else {
                            s = params.get("R_h") as String?
                            if (s != null && Boolean.getBoolean(s)) {
                                a = 2.0 * a * b / (a + b)
                                es = 0.0
                            } else {
                                s = params.get("R_lat_a") as String?
                                if (s != null) {
                                    var tmp = sin(parseAngle(s))
                                    if (abs(tmp) > MapMath.HALFPI) {
                                        throw ProjectionException("-11")
                                    }
                                    tmp = 1.0 - es * tmp * tmp
                                    a *= .5 * (1.0 - es + tmp) / (tmp * sqrt(tmp))
                                    es = 0.0
                                } else {
                                    s = params.get("R_lat_g") as String?
                                    if (s != null) {
                                        var tmp = sin(parseAngle(s))
                                        if (abs(tmp) > MapMath.HALFPI) {
                                            throw ProjectionException("-11")
                                        }
                                        tmp = 1.0 - es * tmp * tmp
                                        a *= sqrt(1.0 - es) / tmp
                                        es = 0.0
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        //FIXME Buggy - ellipsoid is often not specified in string
        projection!!.setEllipsoid(Ellipsoid(ellipsoidName, a, es, ellipsoidName))

        // Other arguments
//		projection.setProjectionLatitudeDegrees( 0 );
//		projection.setProjectionLatitude1Degrees( 0 );
//		projection.setProjectionLatitude2Degrees( 0 );
        s = params.get("lat_0") as String?
        if (s != null) {
            projection!!.projectionLatitudeDegrees = parseAngle(s)
        }
        s = params.get("lon_0") as String?
        if (s != null) {
            projection!!.projectionLongitudeDegrees = parseAngle(s)
        }
        s = params.get("lat_1") as String?
        if (s != null && projection is ConicProjection) {
            val conic = projection
            conic.setProjectionLatitude1Degrees(parseAngle(s))
        }
        s = params.get("lat_2") as String?
        if (s != null && projection is ConicProjection) {
            val conic = projection
            conic.setProjectionLatitude2Degrees(parseAngle(s))
        }
        s = params.get("lat_ts") as String?
        if (s != null) {
            projection!!.trueScaleLatitudeDegrees = parseAngle(s)
        }
        s = params.get("x_0") as String?
        if (s != null) {
            projection!!.falseEasting = s.toDouble()
        }
        s = params.get("y_0") as String?
        if (s != null) {
            projection!!.falseNorthing = s.toDouble()
        }

        s = params.get("k_0") as String?
        if (s == null) {
            s = params.get("k") as String?
        }
        if (s != null) {
            projection!!.scaleFactor = s.toDouble()
        }

        s = params.get("units") as String?
        if (s != null) {
            val unit = findUnits(s)
            if (unit != null) {
                projection!!.fromMetres = 1.0 / unit.value
            }
        }
        s = params.get("to_meter") as String?
        if (s != null) {
            projection!!.fromMetres = 1.0 / s.toDouble()
        }

        if (projection is TransverseMercatorProjection) {
            s = params.get("zone") as String?
            if (s != null) {
                projection.setUTMZone(s.toInt())
            }
        }
        if (projection is UniversalTransverseMercatorProjection) {
            s = params.get("bsouth") as String?
            if (s != null) {
                projection.setIsSouth(true)
            }
        }


        //zone
//towgs84 - see Datum
//alpha
//datum
//lat_ts
//azi
//lonc
//rf
//pm

        //FIXME !!!
        // projection.initialize();
        return projection!!
    }

    private fun parseAngle(s: String): Double {
        return format.parse(s, null).toDouble()
    }

    private var registry: Hashtable<Any?, Any?>? = null
    private var nameMap: Hashtable<Any?, Any?>? = null

    @Throws(InstantiationException::class, IllegalAccessException::class)
    private fun register(proj4Name: String?, cls: Class<*>) {
        try {
            registry!!.put(proj4Name, cls)
            val projection = cls.newInstance() as Projection
            val readableName = projection.getName()
            nameMap!!.put(readableName, proj4Name)
        } catch (e: InstantiationException) {
            System.err.println("unable to register " + proj4Name)
            throw e
        }
    }

    fun getNamedProjection(name: String?): Projection? {
        if (registry == null) {
            initialize()
        }
        val proj4Name = nameMap!!.get(name) as String?
        return getNamedPROJ4Projection(proj4Name)
    }

    fun getNamedPROJ4Projection(name: String?): Projection? {
        if (registry == null) {
            initialize()
        }
        val cls = registry!!.get(name) as Class<*>?
        if (cls != null) {
            try {
                val projection = cls.newInstance() as Projection
                if (projection != null) {
                    projection.setName(name) // is this needed ? FIXME
                }
                return projection!!
            } catch (e: IllegalAccessException) {
                e.printStackTrace()
            } catch (e: InstantiationException) {
                e.printStackTrace()
            }
        }
        return null
    }

    val orderedProjectionNames: Array<Any?>
        get() {
            if (registry == null) {
                initialize()
            }
            val names = nameMap!!.keys.toTypedArray()
            Arrays.sort(names)
            return names
        }

    private fun initialize() {
        try {
            registry = Hashtable<Any?, Any?>()
            nameMap = Hashtable<Any?, Any?>()

            register("aea", AlbersProjection::class.java)
            register("aeqd", EquidistantAzimuthalProjection::class.java)
            register("airy", AiryProjection::class.java)
            register("aitoff", AitoffProjection::class.java)
            //            register("alsk", Projection.class);
            register("apian1", Apian1Projection::class.java)
            register("apian2", Apian2Projection::class.java)
            register("august", AugustProjection::class.java)
            register("bacon", BaconProjection::class.java)
            register("bipc", BipolarProjection::class.java)
            register("boggs", BoggsProjection::class.java)
            register("bonne", BonneProjection::class.java)
            register("cass", CassiniProjection::class.java)
            register("cc", CentralCylindricalProjection::class.java)
            register("cea", CylindricalEqualAreaProjection::class.java)
            //		register( "chamb", Projection.class);
            register("collg", CollignonProjection::class.java)
            register("crast", CrasterProjection::class.java)
            register("denoy", DenoyerProjection::class.java)
            register("eck1", Eckert1Projection::class.java)
            register("eck2", Eckert2Projection::class.java)
            register("eck3", Eckert3Projection::class.java)
            register("eck4", Eckert4Projection::class.java)
            register("eck5", Eckert5Projection::class.java)
            register("eck6", Eckert6Projection::class.java)
            register("eckgreif", EckertGreifendorffProjection::class.java)
            register("eqc", EquidistantCylindricalProjection::class.java)
            register("eqdc", EquidistantConicProjection::class.java)
            register("euler", EulerProjection::class.java)
            register("fahey", FaheyProjection::class.java)
            register("fouc", FoucautProjection::class.java)
            register("fouc_s", FoucautSinusoidalProjection::class.java)
            register("four2", Fournier2Projection::class.java)
            register("gall", GallProjection::class.java)
            register("gins8", Ginzburg8Projection::class.java)
            //		register( "gn_sinu", Projection.class);
            register("gnom", GnomonicAzimuthalProjection::class.java)
            register("goode", GoodeProjection::class.java)
            //		register( "gs48", Projection.class, "Mod. Stererographics of 48 U.S." );
//		register( "gs50", Projection.class, "Mod. Stererographics of 50 U.S." );
            register("hammer", HammerProjection::class.java) // Eckert-Greifendorff is in own class
            register("hatano", HatanoProjection::class.java)
            register("holzel", HolzelProjection::class.java)
            //		register( "imw_p", Projection.class, "Internation Map of the World Polyconic" );
            register("kav5", Kavraisky5Projection::class.java)
            register("kav7", Kavraisky7Projection::class.java)
            //		register( "labrd", Projection.class, "Laborde" );
//		register( "laea", Projection.class, "Lambert Azimuthal Equal Area" );
            register("lagrng", LagrangeProjection::class.java)
            register("larr", LarriveeProjection::class.java)
            register("lask", LaskowskiProjection::class.java)
            register("lcc", LambertConformalConicProjection::class.java)
            register("leac", LambertEqualAreaConicProjection::class.java)
            //		register( "lee_os", Projection.class, "Lee Oblated Stereographic" );
            register("longlat", LinearProjection::class.java)
            register("loxim", LoximuthalProjection::class.java)
            register("lsat", LandsatProjection::class.java)
            register("mbt_s", McBrydeThomasSine1Projection::class.java)
            register("mbt_fps", McBrydeThomasFlatPolarSine2Projection::class.java)
            register("mbtfpp", McBrydeThomasFlatPolarParabolicProjection::class.java)
            register("mbtfpq", McBrydeThomasFlatPolarQuarticProjection::class.java)
            // register("mbtfps", .class);
            register("merc", MercatorProjection::class.java)
            //		register( "mil_os", Projection.class, "Miller Oblated Stereographic" );
            register("mill", MillerCylindrical1Projection::class.java)
            //		register( "mpoly", Projection.class, "Modified Polyconic" );
            register("moll", MollweideProjection::class.java)
            register("murd1", Murdoch1Projection::class.java)
            register("murd2", Murdoch2Projection::class.java)
            register("murd3", Murdoch3Projection::class.java)
            register("nell", NellProjection::class.java)
            register("nell_h", NellHammerProjection::class.java)
            register("nicol", NicolosiProjection::class.java)
            register("nsper", PerspectiveProjection::class.java)
            register("nzmg", NZMGProjection::class.java)
            //		register( "ob_tran", Projection.class, "General Oblique Transformation" );
//		register( "ocea", Projection.class, "Oblique Cylindrical Equal Area" );
//		register( "oea", Projection.class, "Oblated Equal Area" );
            register("omerc", ObliqueMercatorProjection::class.java)
            register("ortel", OrteliusProjection::class.java)
            register("ortho", OrthographicAzimuthalProjection::class.java)
            register("pconic", PerspectiveConicProjection::class.java)
            register("poly", PolyconicProjection::class.java)
            register("putp1", PutninsP1Projection::class.java)
            register("putp2", PutninsP2Projection::class.java)
            //		register( "putp3", Projection.class, "Putnins P3" );            
            register("putp4p", PutninsP4PProjection::class.java)
            register("putp5", PutninsP5Projection::class.java)
            register("putp5p", PutninsP5PProjection::class.java)
            //		register( "putp6", Projection.class, "Putnins P6" );
//		register( "putp6p", Projection.class, "Putnins P6'" );
            register("qua_aut", QuarticAuthalicProjection::class.java)
            register(
                "robin",
                RobinsonProjection::class.java
            ) // RobinsonProjectionOriginal_Proj4_JHL has vertical shift at latitude +/-40 degrees
            register("rpoly", RectangularPolyconicProjection::class.java)
            register("sinu", SinusoidalProjection::class.java)
            register("somerc", SwissObliqueMercatorProjection::class.java)
            register("stere", StereographicAzimuthalProjection::class.java)
            register("tcc", TCCProjection::class.java)
            register("tcea", TCEAProjection::class.java)
            register("tissot", TissotProjection::class.java)
            register("tmerc", TransverseMercatorProjection::class.java)
            //		register( "tpeqd", Projection.class, "Two Point Equidistant" );
//		register( "tpers", Projection.class, "Tilted perspective" );
//		register( "ups", Projection.class, "Universal Polar Stereographic" );
//		register( "urm5", Projection.class, "Urmaev V" );
            register("urmfps", URMFPSProjection::class.java) // Urmaev Flat-Polar Sinusoidal
            register("utm", UniversalTransverseMercatorProjection::class.java)
            register("vandg", VanDerGrintenProjection::class.java)
            //		register( "vandg2", Projection.class, "van der Grinten II" );
//		register( "vandg3", Projection.class, "van der Grinten III" );
//		register( "vandg4", Projection.class, "van der Grinten IV" );
            register("vitk1", VitkovskyProjection::class.java)
            register("wag1", Wagner1Projection::class.java)
            register("wag2", Wagner2Projection::class.java)
            register("wag3", Wagner3Projection::class.java)
            register("wag4", Wagner4Projection::class.java)
            register("wag5", Wagner5Projection::class.java)
            register("wag6", Wagner6Projection::class.java)
            register("wag7", Wagner7Projection::class.java)
            register("weren", Werenskiold1Projection::class.java)
            register("wink1", Winkel1Projection::class.java)
            register("wink2", Winkel2Projection::class.java)
            register("wintri", WinkelTripelProjection::class.java)
        } catch (ex: InstantiationException) {
            Logger.getLogger(ProjectionFactory::class.java.getName()).log(Level.SEVERE, null, ex)
            ex.printStackTrace()
        } catch (ex: IllegalAccessException) {
            Logger.getLogger(ProjectionFactory::class.java.getName()).log(Level.SEVERE, null, ex)
            ex.printStackTrace()
        }
    }

    @Throws(IOException::class)
    fun readProjectionFile(file: String, name: String?): Projection? {
        var reader: BufferedReader? = null
        try {
            val filePath = "/coordsys/" + file
            val `is` = ProjectionFactory::class.java.getResourceAsStream(filePath)
            reader = BufferedReader(InputStreamReader(`is`))
            val t = StreamTokenizer(reader)
            t.commentChar('#'.code)
            t.ordinaryChars('0'.code, '9'.code)
            t.ordinaryChars('.'.code, '.'.code)
            t.ordinaryChars('-'.code, '-'.code)
            t.ordinaryChars('+'.code, '+'.code)
            t.wordChars('0'.code, '9'.code)
            t.wordChars('\''.code, '\''.code)
            t.wordChars('"'.code, '"'.code)
            t.wordChars('_'.code, '_'.code)
            t.wordChars('.'.code, '.'.code)
            t.wordChars('-'.code, '-'.code)
            t.wordChars('+'.code, '+'.code)
            t.wordChars(','.code, ','.code)
            t.nextToken()

            while (t.ttype == '<'.code) {
                t.nextToken()
                if (t.ttype != StreamTokenizer.TT_WORD) {
                    throw IOException(t.lineno().toString() + ": Word expected after '<'")
                }

                val cname = t.sval
                t.nextToken()
                if (t.ttype != '>'.code) {
                    throw IOException(t.lineno().toString() + ": '>' expected")
                }
                t.nextToken()
                val v: Vector<Any?> = Vector<Any?>()
                while (t.ttype != '<'.code) {
                    if (t.ttype == '+'.code) {
                        t.nextToken()
                    }
                    if (t.ttype != StreamTokenizer.TT_WORD) {
                        throw IOException(t.lineno().toString() + ": Word expected after '+'")
                    }
                    val key = t.sval
                    t.nextToken()
                    if (t.ttype == '='.code) {
                        t.nextToken()
                        //Removed check to allow for proj4 hack +nadgrids=@null
                        //if ( t.ttype != StreamTokenizer.TT_WORD )
                        //	throw new IOException( t.lineno()+": Value expected after '='" );
                        val value = t.sval
                        t.nextToken()
                        if (key!!.startsWith("+")) {
                            v.add(key + "=" + value!!)
                        } else {
                            v.add("+" + key + "=" + value!!)
                        }
                    }
                }
                t.nextToken()
                if (t.ttype != '>'.code) {
                    throw IOException(t.lineno().toString() + ": '<>' expected")
                }
                t.nextToken()
                if (cname == name) {
                    val args: Array<String?> = arrayOfNulls<String>(v.size)
                    v.copyInto(args)
                    return fromPROJ4Specification(args)
                }
            }
            return null
        } finally {
            if (reader != null) {
                reader.close()
            }
        }
    }

    fun getNamedPROJ4CoordinateSystem(name: String): Projection? {
        val files = arrayOf<String?>(
            "world",
            "nad83",
            "nad27",
            "esri",
            "epsg",
        )

        try {
            val p = name.indexOf(':')
            if (p >= 0) {
                return readProjectionFile(name.substring(0, p), name.substring(p + 1))
            }

            for (i in files.indices) {
                val projection = ProjectionFactory.readProjectionFile(files[i]!!, name)
                if (projection != null) {
                    return projection!!
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return null
    }

    @JvmStatic
    fun main(args: Array<String?>) {
        val projection = fromPROJ4Specification(args)


        if (projection != null) {
            println(projection.pROJ4Description)

            var i = 0
            while (i
                < args.size
            ) {
                val arg = args[i]


                if (!arg!!.startsWith("+") && !arg!!.startsWith("-")) {
                    try {
                        val reader = BufferedReader(FileReader(File(args[i])))
                        val p = Point2D.Double()
                        var line: String?
                        while ((reader.readLine().also { line = it }) != null) {
                            val t = StringTokenizer(line, " ")
                            val slon = t.nextToken()
                            val slat = t.nextToken()
                            p.x = format.parse(slon, null).toDouble()
                            p.y = format.parse(slat, null).toDouble()
                            projection.transform(p, p)
                            println(p.x.toString() + " " + p.y)
                        }
                    } catch (e: IOException) {
                        println("IOException: " + args[i] + ": " + e.message)
                    }
                }
                i++
            }
        } else {
            println("Can't find projection " + args[0])
        }
    }
}
