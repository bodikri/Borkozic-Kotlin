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
package com.jhlabs.map.proj
import kotlin.jvm.JvmName

import com.jhlabs.Point2D
import com.jhlabs.Rectangle2D
import com.jhlabs.map.AngleFormat
import com.jhlabs.map.Ellipsoid
import com.jhlabs.map.MapMath
import java.io.Serializable
import kotlin.math.hypot

/**
 * The superclass for all map projections
 * 
 * Changes:
 * Added Serializable interface, added abstract keyword, added
 * binarySearchInverse, added transform and inverseTransform with lon/lat
 * as doubles.
 * Bernhard Jenny, 18 May 2010
 */
abstract class Projection protected constructor() : Cloneable, Serializable {
    /**
     * Set the minimum latitude. This is only used for Shape clipping and doesn't affect projection.
     */
    /**
     * The minimum latitude of the bounds of this projection
     */
    var minLatitude: Double = -Math.PI / 2

    /**
     * The minimum longitude of the bounds of this projection. This is relative to the projection centre.
     */
    var minLongitude: Double = -Math.PI
    /**
     * Set the maximum latitude. This is only used for Shape clipping and doesn't affect projection.
     */
    /**
     * The maximum latitude of the bounds of this projection
     */
    var maxLatitude: Double = Math.PI / 2

    /**
     * The maximum longitude of the bounds of this projection. This is relative to the projection centre.
     */
    var maxLongitude: Double = Math.PI
    /**
     * Set the projection latitude in radians.
     */
    /**
     * The latitude of the centre of projection
     */
    var projectionLatitude: Double = 0.0

    /**
     * The longitude of the centre of projection
     */
    @get:JvmName("getProjectionLongitudeKotlin")
    @set:JvmName("setProjectionLongitudeKotlin")
    protected var projectionLongitude: Double = 0.0

    /**
     * Set the projection scale factor. This is set to 1 by default.
     */
    /**
     * The projection scale factor
     */
    var scaleFactor: Double = 1.0
    /**
     * Set the false Easting in projected units.
     */
    /**
     * The false Easting of this projection
     */
    var falseEasting: Double = 0.0
    /**
     * Set the false Northing in projected units.
     */
    /**
     * The false Northing of this projection
     */
    var falseNorthing: Double = 0.0
    /**
     * Set the latitude of true scale in radians. This is only used by certain projections.
     */
    /**
     * The latitude of true scale. Only used by specific projections.
     */
    var trueScaleLatitude: Double = 0.0

    /**
     * The equator radius
     */
    var equatorRadius: Double = 0.0
        protected set

    /**
     * The eccentricity
     */
    protected var e: Double = 0.0

    /**
     * The eccentricity squared
     */
    protected var es: Double = 0.0

    /**
     * 1-(eccentricity squared)
     */
    protected var one_es: Double = 0.0

    /**
     * 1/(1-(eccentricity squared))
     */
    protected var rone_es: Double = 0.0

    /**
     * The ellipsoid used by this projection
     */
    @get:JvmName("getEllipsoidKotlin")
    @set:JvmName("setEllipsoidKotlin")
    var ellipsoid: Ellipsoid? = null

    /**
     * True if this projection is using a sphere (es == 0)
     */
    protected var spherical: Boolean = false

    /**
     * True if this projection is geocentric
     */
    protected var geocentric: Boolean = false

    /**
     * The name of this projection
     */
    private var _name: String? = null
    /**
     * Set the conversion factor from metres to projected units. This is set to 1 by default.
     */
    /**
     * Conversion factor from metres to whatever units the projection uses.
     */
    var fromMetres: Double = 1.0

    /**
     * The total scale factor = Earth radius * units
     */
    private var totalScale = 0.0

    /**
     * falseEasting, adjusted to the appropriate units using fromMetres
     */
    private var totalFalseEasting = 0.0

    /**
     * falseNorthing, adjusted to the appropriate units using fromMetres
     */
    private var totalFalseNorthing = 0.0

    init {
        setEllipsoid(Ellipsoid.SPHERE)
    }

    public override fun clone(): Any {
        try {
            val e = super.clone() as Projection
            return e
        } catch (e: CloneNotSupportedException) {
            throw InternalError()
        }
    }

    /**
     * Project a lat/long point (in degrees), producing a result in metres
     */
    open fun transform(src: Point2D.Double, dst: Point2D.Double): Point2D.Double {
        var x: Double = src.x * DTR
        if (projectionLongitude != 0.0) {
            x = MapMath.normalizeLongitude(x - projectionLongitude)
        }
        project(x, src.y * DTR, dst)
        dst.x = totalScale * dst.x + totalFalseEasting
        dst.y = totalScale * dst.y + totalFalseNorthing
        return dst
    }

    /**
     * Project a lon/lat point (in degrees), producing a result in metres.
     * Important: unlike the other variations of tansform, this implementation
     * always normalizes the longitude, even if projectionLongitude is 0. This
     * is useful for projecting line features crossing +/180 degree of longitude.
     * Bernhard Jenny, May 2010.
     */
    open fun transform(lon: Double, lat: Double, dst: Point2D.Double): Point2D.Double {
        var lon = lon
        lon = MapMath.normalizeLongitude(lon * DTR - projectionLongitude)
        project(lon, lat * DTR, dst)
        dst.x = totalScale * dst.x + totalFalseEasting
        dst.y = totalScale * dst.y + totalFalseNorthing
        return dst
    }

    /**
     * Project a lat/long point, producing a result in metres
     */
    fun transformRadians(src: Point2D.Double, dst: Point2D.Double): Point2D.Double {
        var x = src.x
        if (projectionLongitude != 0.0) {
            x = MapMath.normalizeLongitude(x - projectionLongitude)
        }
        project(x, src.y, dst)
        dst.x = totalScale * dst.x + totalFalseEasting
        dst.y = totalScale * dst.y + totalFalseNorthing
        return dst
    }

    /**
     * Project a lon/lat point (in degrees), producing a result in metres.
     * Important: unlike the other variations of tansform, this implementation
     * always normalizes the longitude, even if projectionLongitude is 0. This
     * is useful for projecting line features crossing +/180 degree of longitude.
     * Bernhard Jenny, May 2010.
     */
    fun transformRadians(lon: Double, lat: Double, dst: Point2D.Double): Point2D.Double {
        var lon = lon
        lon = MapMath.normalizeLongitude(lon - projectionLongitude)
        project(lon, lat, dst)
        dst.x = totalScale * dst.x + totalFalseEasting
        dst.y = totalScale * dst.y + totalFalseNorthing
        return dst
    }

    /**
     * The method which actually does the projection. This should be overridden
     * for all projections.
     * @param x Longitude in radians.
     * @param y Latitude in radians.
     * @param dst The projected point.
     * @return The projected point, identical to parameter dst.
     */
    open fun project(x: Double, y: Double, dst: Point2D.Double): Point2D.Double {
        dst.x = x
        dst.y = y
        return dst
    }

    /**
     * Project a number of lat/long points (in degrees), producing a result in metres
     */
    open fun transform(
        srcPoints: DoubleArray,
        srcOffset: Int,
        dstPoints: DoubleArray,
        dstOffset: Int,
        numPoints: Int
    ) {
        var srcOffset = srcOffset
        var dstOffset = dstOffset
        val `in` = Point2D.Double()
        val out = Point2D.Double()
        for (i in 0 until numPoints) {
            `in`.x = srcPoints[srcOffset++]
            `in`.y = srcPoints[srcOffset++]
            transform(`in`, out)
            dstPoints[dstOffset++] = out.x
            dstPoints[dstOffset++] = out.y
        }
    }

    /**
     * Project a number of lat/long points (in radians), producing a result in metres
     */
    fun transformRadians(
        srcPoints: DoubleArray,
        srcOffset: Int,
        dstPoints: DoubleArray,
        dstOffset: Int,
        numPoints: Int
    ) {
        var srcOffset = srcOffset
        var dstOffset = dstOffset
        val `in` = Point2D.Double()
        val out = Point2D.Double()
        for (i in 0 until numPoints) {
            `in`.x = srcPoints[srcOffset++]
            `in`.y = srcPoints[srcOffset++]
            transform(`in`, out)
            dstPoints[dstOffset++] = out.x
            dstPoints[dstOffset++] = out.y
        }
    }

    /**
     * Inverse-project a point (in metres), producing a lat/long result in degrees
     */
    open fun inverseTransform(src: Point2D.Double, dst: Point2D.Double): Point2D.Double {
        val x = (src.x - totalFalseEasting) / totalScale
        val y = (src.y - totalFalseNorthing) / totalScale
        projectInverse(x, y, dst)
        if (dst.x < -Math.PI) {
            dst.x = -Math.PI
        } else if (dst.x > Math.PI) {
            dst.x = Math.PI
        }
        if (projectionLongitude != 0.0) {
            dst.x = MapMath.normalizeLongitude(dst.x + projectionLongitude)
        }
        dst.x *= RTD
        dst.y *= RTD
        return dst
    }

    /**
     * Inverse-project a point (in metres), producing a lat/long result in radians
     */
    fun inverseTransformRadians(src: Point2D.Double, dst: Point2D.Double): Point2D.Double {
        val x = (src.x - totalFalseEasting) / totalScale
        val y = (src.y - totalFalseNorthing) / totalScale
        projectInverse(x, y, dst)
        if (dst.x < -Math.PI) {
            dst.x = -Math.PI
        } else if (dst.x > Math.PI) {
            dst.x = Math.PI
        }
        if (projectionLongitude != 0.0) {
            dst.x = MapMath.normalizeLongitude(dst.x + projectionLongitude)
        }
        return dst
    }

    /**
     * Inverse-project a point (in meters), producing a lat/long result in radians.
     * Added by Bernhard Jenny, May 2007.
     */
    fun inverseTransformRadians(srcX: Double, srcY: Double, dst: Point2D.Double) {
        val x = (srcX - totalFalseEasting) / totalScale
        val y = (srcY - totalFalseNorthing) / totalScale
        projectInverse(x, y, dst)
        if (dst.x < -Math.PI) {
            dst.x = -Math.PI
        } else if (dst.x > Math.PI) {
            dst.x = Math.PI
        }
        if (projectionLongitude != 0.0) {
            dst.x = MapMath.normalizeLongitude(dst.x + projectionLongitude)
        }
    }


    /**
     * The method which actually does the inverse projection. This should be overridden for all projections.
     */
    open fun projectInverse(x: Double, y: Double, dst: Point2D.Double): Point2D.Double {
        dst.x = x
        dst.y = y
        return dst
    }

    /**
     * Inverse-project a number of points (in metres), producing a lat/long result in degrees
     */
    open fun inverseTransform(
        srcPoints: DoubleArray,
        srcOffset: Int,
        dstPoints: DoubleArray,
        dstOffset: Int,
        numPoints: Int
    ) {
        var srcOffset = srcOffset
        var dstOffset = dstOffset
        val `in` = Point2D.Double()
        val out = Point2D.Double()
        for (i in 0 until numPoints) {
            `in`.x = srcPoints[srcOffset++]
            `in`.y = srcPoints[srcOffset++]
            inverseTransform(`in`, out)
            dstPoints[dstOffset++] = out.x
            dstPoints[dstOffset++] = out.y
        }
    }

    /**
     * Inverse-project a number of points (in metres), producing a lat/long result in radians
     */
    fun inverseTransformRadians(
        srcPoints: DoubleArray,
        srcOffset: Int,
        dstPoints: DoubleArray,
        dstOffset: Int,
        numPoints: Int
    ) {
        var srcOffset = srcOffset
        var dstOffset = dstOffset
        val `in` = Point2D.Double()
        val out = Point2D.Double()
        for (i in 0 until numPoints) {
            `in`.x = srcPoints[srcOffset++]
            `in`.y = srcPoints[srcOffset++]
            inverseTransformRadians(`in`, out)
            dstPoints[dstOffset++] = out.x
            dstPoints[dstOffset++] = out.y
        }
    }

    /**
     * Finds the smallest lat/long rectangle wholly inside the given view rectangle.
     * This is only a rough estimate.
     */
    fun inverseTransform(r: Rectangle2D): Rectangle2D {
        val `in` = Point2D.Double()
        val out = Point2D.Double()
        var bounds: Rectangle2D? = null
        if (this.isRectilinear()) {
            for (ix in 0..1) {
                val x = r.getX() + r.getWidth() * ix
                for (iy in 0..1) {
                    val y = r.getY() + r.getHeight() * iy
                    `in`.x = x
                    `in`.y = y
                    inverseTransform(`in`, out)
                    if (ix == 0 && iy == 0) {
                        bounds = Rectangle2D.Double(out.x, out.y, 0.0, 0.0)
                    } else {
                        bounds!!.add(out.x, out.y)
                    }
                }
            }
        } else {
            for (ix in 0..6) {
                val x = r.getX() + r.getWidth() * ix / 6
                for (iy in 0..6) {
                    val y = r.getY() + r.getHeight() * iy / 6
                    `in`.x = x
                    `in`.y = y
                    inverseTransform(`in`, out)
                    if (ix == 0 && iy == 0) {
                        bounds = Rectangle2D.Double(out.x, out.y, 0.0, 0.0)
                    } else {
                        bounds!!.add(out.x, out.y)
                    }
                }
            }
        }
        return bounds!!
    }

    fun testBinarySearchInverse() {
        val pt1 = Point2D.Double()
        val pt2 = Point2D.Double()

        for (lon in -180..180) {
            for (lat in -89..89) {
                if (!inside(lon.toDouble(), lat.toDouble())) {
                    continue
                }
                transform(lon.toDouble(), lat.toDouble(), pt1)
                binarySearchInverse(
                    pt1.x, pt1.y,
                    Math.toRadians(0.0),
                    Math.toRadians(0.0),
                    pt2
                )
                val lon2 = Math.toDegrees(pt2.x)
                val lat2 = Math.toDegrees(pt2.y)
                val d = hypot(lon - lon2, lat - lat2)
                if (d > 1e-6) {
                    println(lon.toString() + "/" + lat + ": " + d)
                }
            }
        }
    }

    /**
     * Compute the inverse projection by a binary search.
     * Use this method carefully! It is slow and only an approximation for
     * projections that do not provide their own projectInverse method.
     * Added by Bernhard Jenny, 18 May 2010.
     * @param x The projected x coordinate relative to the unary sphere.
     * @param y The projected y coordinate relative to the unary sphere.
     * @param lp A point that will receive the result.
     */
    protected fun binarySearchInverse(x: Double, y: Double, lp: Point2D.Double) {
        binarySearchInverse(x, y, 0.0, 0.0, lp)
    }

    /**
     * Compute the inverse projection by a binary search.
     * Use this method carfully! It is slow and only an approximation for
     * projections that do not provide their own projectInverse method.
     * Added by Bernhard Jenny, 18 May 2010.
     * @param x The projected x coordinate relative to the unary sphere.
     * @param y The projected y coordinate relative to the unary sphere.
     * @param lon An approximation of the longitude in radians.
     * @param lat An approximation of the latitude in radians.
     * @param lp A point that will receive the result.
     */
    protected fun binarySearchInverse(
        x: Double, y: Double,
        lon: Double, lat: Double, lp: Point2D.Double
    ) {
        // tolerance for approximating longitude and latitude
        var lon = lon
        var lat = lat
        val TOL = 1e-9 // less than a hundreth of a second

        // maximum number of loops
        val MAX_LOOP = 1000
        var counter = 0
        var dx: Double
        var dy: Double
        do {
            // forward projection
            this.project(lon, lat, lp)
            dx = x - lp.x // horizontal difference in projected coordinates
            dy = y - lp.y // vertical difference in projected coordinates
            lon += dx * 0.5 // add half of the horizontal difference to the longitude
            lat += dy * 0.5 // add half of the vertical difference to the latitude

            // stop if it is not converging
            if (counter++ == MAX_LOOP) {
                lon = Double.Companion.NaN
                lat = Double.Companion.NaN
                break
            }

            // stop when difference is small enough
        } while (dx > TOL || dx < -TOL || dy > TOL || dy < -TOL)

        lp.x = lon
        lp.y = lat
    }

    /**
     * Transform a bounding box. This is only a rough estimate.
     */
    fun transform(r: Rectangle2D): Rectangle2D {
        val `in` = Point2D.Double()
        val out = Point2D.Double()
        var bounds: Rectangle2D? = null
        if (this.isRectilinear()) {
            for (ix in 0..1) {
                val x = r.getX() + r.getWidth() * ix
                for (iy in 0..1) {
                    val y = r.getY() + r.getHeight() * iy
                    `in`.x = x
                    `in`.y = y
                    transform(`in`, out)
                    if (ix == 0 && iy == 0) {
                        bounds = Rectangle2D.Double(out.x, out.y, 0.0, 0.0)
                    } else {
                        bounds!!.add(out.x, out.y)
                    }
                }
            }
        } else {
            for (ix in 0..6) {
                val x = r.getX() + r.getWidth() * ix / 6
                for (iy in 0..6) {
                    val y = r.getY() + r.getHeight() * iy / 6
                    `in`.x = x
                    `in`.y = y
                    transform(`in`, out)
                    if (ix == 0 && iy == 0) {
                        bounds = Rectangle2D.Double(out.x, out.y, 0.0, 0.0)
                    } else {
                        bounds!!.add(out.x, out.y)
                    }
                }
            }
        }
        return bounds!!
    }

    /**
     * Returns true if this projection is conformal
     */
    open fun isConformal(): Boolean {
        return false
    }

    /**
     * Returns true if this projection is equal area
     */
    open fun isEqualArea(): Boolean {
        return false
    }

    /**
     * Returns true if this projection has an inverse
     */
    open fun hasInverse(): Boolean {
        return false
    }

    /**
     * Returns true if lat/long lines form a rectangular grid for this projection.
     * This is generally only the case for cylindrical projections, but not
     * for oblique cylindrical projections.
     */
    open fun isRectilinear(): Boolean {
        return false
    }

    /**
     * Returns true if latitude lines are parallel for this projection
     */
    open fun parallelsAreParallel(): Boolean {
        return this.isRectilinear()
    }

    /**
     * Returns true if the given lat/lon point is visible in this projection.
     * @param x longitude in degrees.
     * @param y latitude in degrees.
     * @return
     */
    open fun inside(lon: Double, lat: Double): Boolean {
        var lon = lon
        var lat = lat
        lon = MapMath.normalizeLongitude(lon * DTR - projectionLongitude)
        lat *= DTR
        return minLongitude <= lon && lon <= maxLongitude && minLatitude <= lat && lat <= maxLatitude
    }

    /**
     * Set the name of this projection.
     */
    fun setName(name: String?) {
        this._name = name
    }

    open fun getName(): String? {
        if (_name != null) {
            return _name
        }
        return toString()
    }

    val projectionDescription: String
        /**
         * Returns a human readable description of this projection in telegram style.
         * @return
         */
        get() {
            val sb = StringBuilder()

            // projection type
            if (this is CylindricalProjection) {
                sb.append("cylindrical ")
            }
            if (this is ConicProjection) {
                sb.append("conic ")
            }
            if (this is PseudoCylindricalProjection) {
                sb.append("pseudo cylindrical ")
            }
            if (this is AzimuthalProjection) {
                sb.append("azimuthal ")
            }

            // distortion
            if (this.isConformal()) {
                sb.append("conformal")
            }
            if (this.isEqualArea()) {
                sb.append("equal-area")
            }
            return sb.toString()
        }

    val pROJ4Description: String
        /**
         * Get a string which describes this projection in PROJ.4 format.
         */
        get() {
            val format = AngleFormat(AngleFormat.ddmmssPattern, false)
            val sb = StringBuffer()
            sb.append(
                ("+proj=" + getName()
                        + " +a=" + this.equatorRadius)
            )
            if (es != 0.0) {
                sb.append(" +es=" + es)
            }
            sb.append(" +ellps=" + ellipsoid!!.shortName)
            sb.append(" +lon_0=")
            format.format(projectionLongitude, sb, java.text.FieldPosition(0))
            sb.append(" +lat_0=")
            format.format(projectionLatitude, sb, java.text.FieldPosition(0))
            if (falseEasting != 1.0) {
                sb.append(" +x_0=" + falseEasting)
            }
            if (falseNorthing != 1.0) {
                sb.append(" +y_0=" + falseNorthing)
            }
            if (scaleFactor != 1.0) {
                sb.append(" +k=" + scaleFactor)
            }
            if (fromMetres != 1.0) {
                sb.append(" +fr_meters=" + fromMetres)
            }
            return sb.toString()
        }

    override fun toString(): String {
        return "None"
    }

    val maxLatitudeDegrees: Double
        get() = maxLatitude * RTD

    val minLatitudeDegrees: Double
        get() = minLatitude * RTD
    var minLongitudeDegrees: Double
        get() = minLongitude * RTD
        set(minLongitude) {
            this.minLongitude = DTR * minLongitude
        }
    var maxLongitudeDegrees: Double
        get() = maxLongitude * RTD
        set(maxLongitude) {
            this.maxLongitude = DTR * maxLongitude
        }
    var projectionLatitudeDegrees: Double
        get() = projectionLatitude * RTD
        /**
         * Set the projection latitude in degrees.
         */
        set(projectionLatitude) {
            this.projectionLatitude = DTR * projectionLatitude
        }

    /**
     * Set the projection longitude in radians.
     */
    fun setProjectionLongitude(projectionLongitude: Double) {
        this.projectionLongitude = MapMath.normalizeLongitude(projectionLongitude)
    }

    fun getProjectionLongitude(): Double {
        return projectionLongitude
    }
    var projectionLongitudeDegrees: Double
        get() = projectionLongitude * RTD
        /**
         * Set the projection longitude in degrees.
         */
        set(projectionLongitude) {
            this.projectionLongitude = DTR * projectionLongitude
        }
    var trueScaleLatitudeDegrees: Double
        get() = trueScaleLatitude * RTD
        /**
         * Set the latitude of true scale in degrees. This is only used by certain projections.
         */
        set(trueScaleLatitude) {
            this.trueScaleLatitude = DTR * trueScaleLatitude
        }

    fun setEllipsoid(ellipsoid: Ellipsoid) {
        this.ellipsoid = ellipsoid
        this.equatorRadius = ellipsoid.equatorRadius
        e = ellipsoid.eccentricity
        es = ellipsoid.eccentricity2
    }

    fun getEllipsoid(): Ellipsoid {
        return ellipsoid!!
    }

    /**
     * Returns the ESPG code for this projection, or 0 if unknown.
     */
    open fun getEPSGCode(): Int {
        return 0
    }

    /**
     * Initialize the projection. This should be called after setting parameters and before using the projection.
     * This is for performance reasons as initialization may be expensive.
     */
    open fun initialize() {
        spherical = e == 0.0
        one_es = 1 - es
        rone_es = 1.0 / one_es
        totalScale = this.equatorRadius * fromMetres
        totalFalseEasting = falseEasting * fromMetres
        totalFalseNorthing = falseNorthing * fromMetres
    }

    // Some useful constants

    protected val EPS10: Double = 1e-10

    protected val RTD: Double = 180.0 / Math.PI

    protected val DTR: Double = Math.PI / 180.0

    companion object {

            fun main(args: Array<String>) {
            val p: Projection = OrteliusProjection()
            val unarySphere = Ellipsoid(null, 1.0, 0.0, null)
            p.setEllipsoid(unarySphere)
            p.initialize()
            p.testBinarySearchInverse()
        }
    }
}

