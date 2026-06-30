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

package com.borkozic.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Paint.Style
import android.graphics.Path
import android.graphics.Rect
import android.util.Log
import android.view.View
import com.jhlabs.Point2D
import com.jhlabs.map.proj.Projection
import com.jhlabs.map.proj.ProjectionException
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.Serializable
import java.util.ArrayList

open class Map : Serializable {
    companion object {
        private const val serialVersionUID: Long = 5L

        private val zoomLevelsSupported = doubleArrayOf(
            // zoom must give integer if multiplied by 50 - it is used as a tile key
            0.02,
            0.06,
            0.10,
            0.25,
            0.50,
            0.75,
            1.00,
            1.25,
            1.50,
            1.75,
            2.00,
            2.50,
            3.00,
            4.00,
            5.00
        )
    }

    @JvmField
    var id: Int
    @JvmField
    var title: String = ""
    @JvmField
    var mappath: String
    @JvmField
    var imagePath: String? = null
    @JvmField
    var datum: String? = null
    @JvmField
    var origDatum: String? = null
    @JvmField
    var width: Int = 0
    @JvmField
    var height: Int = 0
    @JvmField
    var mpp: Double = 0.0
    @JvmField
    var scaleFactor: Double = 0.0
    @JvmField
    var prjName: String? = null
    @JvmField
    var llGrid: Grid? = null
    @JvmField
    var grGrid: Grid? = null
    @JvmField
    var projection: Projection? = null
    @JvmField
    var cornerMarkers: Array<MapPoint>? = null
    @JvmField
    val calibrationPoints = ArrayList<MapPoint>()
    @JvmField
    var zoom: Double = 0.0
    @JvmField
    var savedZoom: Double = 0.0
    private val binding = LinearBinding()
    @JvmField
    protected var pixels: Int = 0
    @JvmField
    protected var mapClipPath: Path? = null
    @JvmField
    var loadError: Throwable? = null
    private var ozf: OzfReader? = null
    @JvmField
    protected var cache: TileRAMCache? = null
    @JvmField
    var bounds = Bounds()
    @JvmField
    protected var borderPaint: Paint? = null

    constructor(filepath: String) {
        mappath = filepath
        id = mappath.hashCode()
        zoom = 1.0
        scaleFactor = 1.0
        savedZoom = 0.0
        loadError = null
        cache = null
    }

    @Throws(IOException::class)
    open fun activate(view: View?, pixels: Int) {
        this.pixels = pixels
        Log.d("OZI", "Image file specified: $imagePath")
        var image = File(imagePath!!)
        if (!image.exists()) {
            imagePath = imagePath!!.replace("\\\\".toRegex(), "/")
            image = File(imagePath!!)
            val map = File(mappath)
            image = File(map.parentFile, image.name)
            if (!image.exists()) {
                throw FileNotFoundException("Image file not found: $imagePath")
            }
        }
        Log.d("OZI", "Image file found: ${image.canonicalPath}")
        ozf = OzfReader(image)
        mapClipPath = Path()
        setZoom(if (savedZoom == 0.0) zoom else savedZoom)
        savedZoom = 0.0

        borderPaint = Paint()
        borderPaint!!.isAntiAlias = true
        borderPaint!!.strokeWidth = 3f
        borderPaint!!.color = Color.RED
        borderPaint!!.alpha = 128
        borderPaint!!.style = Style.STROKE
    }

    @Synchronized
    open fun deactivate() {
        //TODO This shouldn't happen but happens
        if (ozf != null) ozf!!.close()
        ozf = null
        cache!!.destroy()
        cache = null
        mapClipPath = null
        if (savedZoom != 0.0) {
            zoom = savedZoom
            bind()
        }
        savedZoom = 0.0
        borderPaint = null
    }

    @Synchronized
    open fun activated(): Boolean {
        return ozf != null
    }

    fun addCalibrationPoint(point: MapPoint) {
        calibrationPoints.add(point)
    }

    fun setCornersAmount(num: Int) {
        cornerMarkers = arrayOfNulls<MapPoint>(num) as Array<MapPoint>
        for (i in 0 until num) {
            cornerMarkers!![i] = MapPoint()
        }
    }

    open fun getBounds(): Bounds {
        if (bounds.minLat == Double.MAX_VALUE) {
            for (corner in cornerMarkers!!) {
                if (corner.lat < bounds.minLat) bounds.minLat = corner.lat
                if (corner.lat > bounds.maxLat) bounds.maxLat = corner.lat
                // FIXME think how to wrap 180 parallel
                if (corner.lon < bounds.minLon) bounds.minLon = corner.lon
                if (corner.lon > bounds.maxLon) bounds.maxLon = corner.lon
            }
        }
        return bounds
    }

    open fun getXYByLatLon(lat: Double, lon: Double, xy: IntArray): Boolean {
        var nn: Double
        var ee: Double

        val src = Point2D.Double(lon, lat)
        val dst = Point2D.Double()
        projection!!.transform(src.x, src.y, dst)
        ee = dst.x
        nn = dst.y
        xy[0] = Math.round(binding.Kx[0] * nn + binding.Kx[1] * ee + binding.Kx[2]).toInt()
        xy[1] = Math.round(binding.Ky[0] * nn + binding.Ky[1] * ee + binding.Ky[2]).toInt()

        return (xy[0] >= 0 && xy[0] < width * zoom && xy[1] >= 0 && xy[1] < height * zoom)
    }

    // never used
    fun getXYByEN(e: Int, n: Int, xy: IntArray): Boolean {
        xy[0] = Math.round(binding.Kx[0] * n + binding.Kx[1] * e + binding.Kx[2]).toInt()
        xy[1] = Math.round(binding.Ky[0] * n + binding.Ky[1] * e + binding.Ky[2]).toInt()

        return (xy[0] >= 0 && xy[0] < width * zoom && xy[1] >= 0 && xy[1] < height * zoom)
    }

    // never used
    fun getENByXY(x: Int, y: Int, en: IntArray): Boolean {
        en[1] = (binding.Klat[0] * x + binding.Klat[1] * y + binding.Klat[2]).toInt()
        en[0] = (binding.Klon[0] * x + binding.Klon[1] * y + binding.Klon[2]).toInt()

        return (x >= 0 && x < width * zoom && y >= 0 || y < height * zoom)
    }

    // never used
    fun getENByLatLon(lat: Double, lon: Double, en: IntArray) {
        val src = Point2D.Double(lon, lat)
        val dst = Point2D.Double()
        projection!!.transform(src.x, src.y, dst)
        en[0] = dst.x.toInt()
        en[1] = dst.y.toInt()
    }

    /**
     * Converts pixel coordinates to geodetic coordinates
     * @param x
     * @param y
     * @param ll
     * @return
     */
    open fun getLatLonByXY(x: Int, y: Int, ll: DoubleArray): Boolean {
        var nn: Double
        var ee: Double

        nn = binding.Klat[0] * x + binding.Klat[1] * y + binding.Klat[2]
        ee = binding.Klon[0] * x + binding.Klon[1] * y + binding.Klon[2]

        val src = Point2D.Double(ee, nn)
        val dst = Point2D.Double()
        projection!!.inverseTransform(src, dst)
        ll[0] = dst.y
        ll[1] = dst.x

        return (x >= 0 && x < width * zoom && y >= 0 || y < height * zoom)
    }

    /**
     * Checks if map covers given coordinates
     * @param lat latitude in degrees
     * @param lon longitude in degrees
     * @return true if coordinates are inside map
     */
    open fun coversLatLon(lat: Double, lon: Double): Boolean {
        val xy = IntArray(2)
        var inside = false
        try {
            inside = getXYByLatLon(lat, lon, xy)
        } catch (e: ProjectionException) {
            return false
        }

        // check corners
        if (inside) {
            // rescale to original size
            xy[0] = (xy[0] / zoom).toInt()
            xy[1] = (xy[1] / zoom).toInt()

            //  Note that division by zero is avoided because the division is protected
            //  by the "if" clause which surrounds it.

            var j = cornerMarkers!!.size - 1
            var odd = 0

            for (i in cornerMarkers!!.indices) {
                if (cornerMarkers!![i].y < xy[1] && cornerMarkers!![j].y >= xy[1] || cornerMarkers!![j].y < xy[1] && cornerMarkers!![i].y >= xy[1]) {
                    if (cornerMarkers!![i].x + (xy[1] - cornerMarkers!![i].y) * 1.0 / (cornerMarkers!![j].y - cornerMarkers!![i].y) * (cornerMarkers!![j].x - cornerMarkers!![i].x) < xy[0]) {
                        odd++
                    }
                }
                j = i
            }

            inside = odd % 2 == 1
        }

        return inside
    }

    open fun coversScreen(map_xy: IntArray, width: Int, height: Int): Boolean {
        val w2 = width / 2
        val h2 = height / 2

        val l = ((map_xy[0] - w2) / zoom).toInt()
        val t = ((map_xy[1] - h2) / zoom).toInt()
        val r = ((map_xy[0] + w2) / zoom).toInt()
        val b = ((map_xy[1] + h2) / zoom).toInt()

        var j = cornerMarkers!!.size - 1
        var oddTL = 0
        var oddTR = 0
        var oddBL = 0
        var oddBR = 0

        for (i in cornerMarkers!!.indices) {
            if (cornerMarkers!![i].y < t && cornerMarkers!![j].y >= t || cornerMarkers!![j].y < t && cornerMarkers!![i].y >= t) {
                val tx = (cornerMarkers!![i].x + (t - cornerMarkers!![i].y) * 1.0 / (cornerMarkers!![j].y - cornerMarkers!![i].y) * (cornerMarkers!![j].x - cornerMarkers!![i].x)).toInt()
                if (tx < l) {
                    oddTL++
                }
                if (tx < r) {
                    oddTR++
                }
            }
            if (cornerMarkers!![i].y < b && cornerMarkers!![j].y >= b || cornerMarkers!![j].y < b && cornerMarkers!![i].y >= b) {
                val bx = (cornerMarkers!![i].x + (b - cornerMarkers!![i].y) * 1.0 / (cornerMarkers!![j].y - cornerMarkers!![i].y) * (cornerMarkers!![j].x - cornerMarkers!![i].x)).toInt()
                if (bx < l) {
                    oddBL++
                }
                if (bx < r) {
                    oddBR++
                }
            }
            j = i
        }

        return oddTL % 2 == 1 && oddTR % 2 == 1 && oddBL % 2 == 1 && oddBR % 2 == 1
    }

    open fun containsArea(area: Bounds): Boolean {
        val b = getBounds()
        return Bounds.intersects(b!!, area)
    }

    open fun getNextZoom(): Double {
        val zoomCurrent = getZoom()
        var zoom = Double.NaN
        for (i in zoomLevelsSupported.indices) {
            if (zoomLevelsSupported[i] > zoomCurrent) {
                zoom = zoomLevelsSupported[i]
                break
            }
        }
        return if (!zoom.isNaN()) zoom else 0.0
    }

    open fun getPrevZoom(): Double {
        val zoomCurrent = getZoom()
        var zoom = Double.NaN
        for (i in zoomLevelsSupported.size - 1 downTo 0) {
            if (zoomLevelsSupported[i] < zoomCurrent) {
                zoom = zoomLevelsSupported[i]
                break
            }
        }
        return if (!zoom.isNaN()) zoom else 0.0
    }

    @Synchronized
    open fun getZoom(): Double {
        return ozf!!.getZoom()
    }

    fun zoomBy(factor: Double) {
        setZoom(zoom * factor)
    }

    @Synchronized
    open fun setZoom(z: Double) {
        //Log.e("OZI", "setZoom: " + z);
        zoom = ozf!!.setZoom(z)
        if (cache != null) cache!!.destroy()
        val cacheSize = Math.ceil((pixels * 1.0 / (ozf!!.tile_dx() * ozf!!.tile_dy()) * 3)).toInt()
        //Log.e("OZI", "Cache size: " + cacheSize);
        cache = TileRAMCache(cacheSize)
        ozf!!.setCache(cache)
        bind()
        mapClipPath!!.rewind()
        mapClipPath!!.setLastPoint((cornerMarkers!![0].x * zoom).toFloat(), (cornerMarkers!![0].y * zoom).toFloat())
        for (i in 1 until cornerMarkers!!.size) mapClipPath!!.lineTo((cornerMarkers!![i].x * zoom).toFloat(), (cornerMarkers!![i].y * zoom).toFloat())
        mapClipPath!!.close()
    }

    fun setTemporaryZoom(zoom: Double) {
        savedZoom = this.zoom
        //Log.e("MAP", "setTemporaryZoom: " + zoom);
        setZoom(zoom)
    }

    open val scaledWidth: Int
        get() = (width * zoom).toInt()

    open val scaledHeight: Int
        get() = (height * zoom).toInt()

    open fun getMapCenter(center: DoubleArray) {
        val x = scaledWidth / 2
        val y = scaledHeight / 2
        getLatLonByXY(x, y, center)
    }

    @Synchronized
    open fun drawMap(bearing: Float, loc: DoubleArray, lookAhead: IntArray, width: Int, height: Int, cropBorder: Boolean, drawBorder: Boolean, c: Canvas): Boolean {
        if (ozf == null) return false
        val map_xy = IntArray(2)
        getXYByLatLon(loc[0], loc[1], map_xy)
        map_xy[0] -= lookAhead[0]
        map_xy[1] -= lookAhead[1]
        try {
            val clipPath = Path()
            if (cropBorder || drawBorder) mapClipPath!!.offset((-map_xy[0] + width / 2).toFloat(), (-map_xy[1] + height / 2).toFloat(), clipPath)
            c.save()
            if (cropBorder) c.clipPath(clipPath)

            val cr = ozf!!.map_xy_to_cr(map_xy)
            val xy = ozf!!.map_xy_to_xy_on_tile(map_xy)

            val tile_w = ozf!!.tile_dx()
            val tile_h = ozf!!.tile_dy()

            if (tile_w == 0 || tile_h == 0) {
                c.restore()
                c.drawRGB(255, 0, 0)
                return false
            }

            var c_min = Math.floor(ozf!!.map_x_to_c(map_xy[0] - width / 2)).toInt()
            var c_max = Math.ceil(ozf!!.map_x_to_c(map_xy[0] + width / 2)).toInt()

            var r_min = Math.floor(ozf!!.map_y_to_r(map_xy[1] - height / 2)).toInt()
            var r_max = Math.ceil(ozf!!.map_y_to_r(map_xy[1] + height / 2)).toInt()

            var result = true

            if (c_min < 0) {
                c_min = 0
                result = false
            }
            if (r_min < 0) {
                r_min = 0
                result = false
            }
            if (c_max > ozf!!.tiles_per_x()) {
                c_max = ozf!!.tiles_per_x()
                result = false
            }
            if (r_max > ozf!!.tiles_per_y()) {
                r_max = ozf!!.tiles_per_y()
                result = false
            }

            val txb = width / 2 - xy[0] - (cr[0] - c_min) * tile_w
            val tyb = height / 2 - xy[1] - (cr[1] - r_min) * tile_h

            for (i in r_min until r_max) {
                for (j in c_min until c_max) {
                    val tx = txb + (j - c_min) * tile_w
                    val ty = tyb + (i - r_min) * tile_h

                    val tile = ozf!!.tile_get(j, i)

                    if (tile != null) {
                        val tile_dx = ozf!!.tile_dx(j, i)
                        val tile_dy = ozf!!.tile_dy(j, i)
                        if (tile_dx < tile_w || tile_dy < tile_h) {
                            val src = Rect(0, 0, tile_dx, tile_dy)
                            val dst = Rect(tx, ty, tx + src.right, ty + src.bottom)
                            c.drawBitmap(tile, src, dst, null)
                        } else if (tile_w > tile.width || tile_h > tile.height) {
                            // Zoom > 100%: мащабирай тайла да запълни tile_w × tile_h
                            val src = Rect(0, 0, tile.width, tile.height)
                            val dst = Rect(tx, ty, tx + tile_w, ty + tile_h)
                            c.drawBitmap(tile, src, dst, null)
                        } else {
                            c.drawBitmap(tile, tx.toFloat(), ty.toFloat(), null)
                        }
                    }
                }
            }
            c.restore()
            if (drawBorder) c.drawPath(clipPath, borderPaint!!)
            if (result) result = coversScreen(map_xy, width, height)
            return result
        } catch (err: OutOfMemoryError) {
            cache!!.clear()
            throw err
        }
    }

    fun bind() {
        val points = arrayOfNulls<MapPoint>(calibrationPoints.size)

        var i = 0
        for (mp in calibrationPoints) {
            points[i] = MapPoint()
            points[i]!!.lat = mp.lat
            points[i]!!.lon = mp.lon
            points[i]!!.x = (mp.x * zoom).toInt()
            points[i]!!.y = (mp.y * zoom).toInt()
            val src = Point2D.Double(points[i]!!.lon, points[i]!!.lat)
            val dst = Point2D.Double()
            projection!!.transform(src.x, src.y, dst)
            points[i]!!.n = dst.y
            points[i]!!.e = dst.x
            //			Log.e("OZI","point transform: "+points[i].lat+" "+points[i].lon+" -> "+points[i].n+" "+points[i].e);
            src.x = dst.x
            src.y = dst.y
            projection!!.inverseTransform(src, dst)
            //			Log.e("OZI","point reverse transform: "+src.y+" "+src.x+" -> "+dst.y+" "+dst.x);
            i++
        }

        getKx(points)
        getKy(points)
        getKLat(points)
        getKLon(points)
    }

    private fun getKx(points: Array<MapPoint?>) {
        val a = Array(3) { DoubleArray(3) }
        val b = DoubleArray(3)
        val p = Array(3) { DoubleArray(points.size) }

        var i = 0
        for (mp in points) {
            p[0][i] = mp!!.n
            p[1][i] = mp.e
            p[2][i] = mp.x.toDouble()
            i++
        }

        init_3x3(a, b, p, points.size)
        gauss(a, b, binding.Kx, 3)
        //Log.e("OZI", "Kx: "+binding.Kx[0]+","+binding.Kx[1]+","+binding.Kx[2]);
    }

    private fun getKy(points: Array<MapPoint?>) {
        val a = Array(3) { DoubleArray(3) }
        val b = DoubleArray(3)
        val p = Array(3) { DoubleArray(points.size) }

        var i = 0
        for (mp in points) {
            p[0][i] = mp!!.n
            p[1][i] = mp.e
            p[2][i] = mp.y.toDouble()
            i++
        }

        init_3x3(a, b, p, points.size)
        gauss(a, b, binding.Ky, 3)
        //Log.e("OZI", "Ky: "+binding.Ky[0]+","+binding.Ky[1]+","+binding.Ky[2]);
    }

    private fun getKLat(points: Array<MapPoint?>) {
        val a = Array(3) { DoubleArray(3) }
        val b = DoubleArray(3)
        val p = Array(3) { DoubleArray(points.size) }

        var i = 0
        for (mp in points) {
            p[0][i] = mp!!.x.toDouble()
            p[1][i] = mp.y.toDouble()
            p[2][i] = mp.n
            i++
        }

        init_3x3(a, b, p, points.size)
        gauss(a, b, binding.Klat, 3)
        //Log.e("OZI", "Klat: "+binding.Klat[0]+","+binding.Klat[1]+","+binding.Klat[2]);
    }

    private fun getKLon(points: Array<MapPoint?>) {
        val a = Array(3) { DoubleArray(3) }
        val b = DoubleArray(3)
        val p = Array(3) { DoubleArray(points.size) }

        var i = 0
        for (mp in points) {
            p[0][i] = mp!!.x.toDouble()
            p[1][i] = mp.y.toDouble()
            p[2][i] = mp.e
            i++
        }

        init_3x3(a, b, p, points.size)
        gauss(a, b, binding.Klon, 3)
        //Log.e("OZI", "Klon: "+binding.Klon[0]+","+binding.Klon[1]+","+binding.Klon[2]);
    }

    /**
     *  Solves linear equation.  Finds vector x such that ax = b.
     *
     *	@param a nXn matrix
     *	@param b vector size n
     *	@param x vector size n
     *	@param n number of variables (size of vectors) (must be > 1)
     *
     *	This function will alter a and b, and put the solution in x.
     *	@return true if the solution was found, false otherwise.
     */
    private fun gauss(a: Array<DoubleArray>, b: DoubleArray, x: DoubleArray, n: Int): Boolean {
        var ip = 0
        var temp: Double
        var pivot: Double
        var q: Double

        /*
         *	transform matrix to echelon form.
         */
        for (i in 0 until n - 1) {
            /*
             *	Find the pivot.
             */
            pivot = 0.0
            for (j in i until n) {
                temp = Math.abs(a[j][i])
                if (temp > pivot) {
                    pivot = temp
                    ip = j
                }
            }

            if (pivot < 1e-14) {
                /*
                 *   Error - singular matrix.
                 */
                return false
            }

            /*
             *	Move the pivot row to the ith position
             */
            if (ip != i) {
                val temp_p = a[i]
                a[i] = a[ip]
                a[ip] = temp_p
                temp = b[i]
                b[i] = b[ip]
                b[ip] = temp
            }

            /*
             *	Zero entries below the diagonal.
             */
            for (k in i + 1 until n) {
                q = -a[k][i] / a[i][i]

                a[k][i] = 0.0

                for (j in i + 1 until n) a[k][j] = q * a[i][j] + a[k][j]
                b[k] = q * b[i] + b[k]
            }
        }

        if (Math.abs(a[n - 1][n - 1]) < 1e-14) {
            return false
        }

        /*
         *	Backsolve to obtain solution vector x.
         */
        var kk = n - 1
        x[kk] = b[kk] / a[kk][kk]
        for (k in 0 until n - 1) {
            kk = n - k - 2
            q = 0.0

            for (j in 0 until k) {
                val jj = n - j - 1
                q += a[kk][jj] * x[jj]
            }
            x[kk] = (b[kk] - q) / a[kk][kk]
        }

        return true
    }

    private fun init_3x3(a: Array<DoubleArray>, b: DoubleArray, p: Array<DoubleArray>, size: Int) {
        for (i in 0..2) {
            b[i] = 0.0

            for (j in 0..2) a[i][j] = 0.0
        }

        for (i in 0 until size) {
            a[0][0] += p[0][i] * p[0][i]
            a[0][1] += p[0][i] * p[1][i]
            a[0][2] += p[0][i]
            a[1][1] += p[1][i] * p[1][i]
            a[1][2] += p[1][i]
            b[0] += p[2][i] * p[0][i]
            b[1] += p[2][i] * p[1][i]
            b[2] += p[2][i]
        }

        a[1][0] = a[0][1]
        a[2][0] = a[0][2]
        a[2][1] = a[1][2]
        a[2][2] = size.toDouble()
    }

    private class LinearBinding : Serializable {
        companion object {
            private const val serialVersionUID: Long = 1L
        }

        var Kx = DoubleArray(3)
        var Ky = DoubleArray(3)
        var Klat = DoubleArray(3)
        var Klon = DoubleArray(3)
    }

    inner class Grid : Serializable {
        @JvmField var enabled: Boolean = false
        @JvmField var spacing: Double = 0.0
        @JvmField var autoscale: Boolean = false
        @JvmField var color1: Int = 0
        @JvmField var color2: Int = 0
        @JvmField var color3: Int = 0
        @JvmField var labelSpacing: Double = 0.0
        @JvmField var labelForeground: Int = 0
        @JvmField var labelBackground: Int = 0
        @JvmField var labelSize: Int = 0
        @JvmField var labelShowEverywhere: Boolean = false
        @JvmField var maxMPP: Int = 0
    }

    class Bounds : Serializable {
        @JvmField
        var minLat = Double.MAX_VALUE
        @JvmField
        var maxLat = Double.MIN_VALUE
        @JvmField
        var minLon = Double.MAX_VALUE
        @JvmField
        var maxLon = Double.MIN_VALUE

        fun intersects(area: Bounds): Boolean {
            return intersects(this, area)
        }

        companion object {
            fun intersects(a: Bounds, b: Bounds): Boolean {
                //FIXME Should wrap 180 parallel
                return a.minLon < b.maxLon && b.minLon < a.maxLon && a.minLat < b.maxLat && b.minLat < a.maxLat
            }
        }

        override fun toString(): String {
            return "[" + maxLat + "," + minLon + "," + minLat + "," + maxLon + "]"
        }
    }

    fun debug() {
        val info = info()
        for (line in info) {
            Log.d("OZI", line)
        }
    }

    open fun info(): List<String> {
        val info = ArrayList<String>()

        info.add("title: $title")
        if (projection != null) {
            info.add("projection: $prjName (${projection!!.getEPSGCode()})")
            info.add("\t${projection!!.pROJ4Description}")
            info.add("ellipsoid: ${projection!!.getEllipsoid().toString()}")
        }
        if (origDatum != null) {
            info.add("datum: $origDatum -> $datum")
            info.add("  (coordinates shown in $datum)")
        } else {
            info.add("datum: $datum")
        }
        info.add("mpp: $mpp")
        info.add("image width: $width")
        info.add("image height: $height")
        info.add("image file: $imagePath")
        info.add("scale factor: ${1 / scaleFactor}")
        info.add("calibration points:")

        var i = 1
        for (mp in calibrationPoints) {
            info.add(String.format("\t%02d: x: %d y: %d lat: %f lon: %f", i, mp.x, mp.y, mp.lat, mp.lon))
            i++
        }
        val ll = DoubleArray(2)
        getLatLonByXY(width / 2, height / 2, ll)
        info.add("map center (calibration) test: ${ll[0]} ${ll[1]}")

        info.add("corners:")

        if (cornerMarkers != null) {
            i = 1
            for (mp in cornerMarkers!!) {
                info.add(String.format("\t%02d: x: %d y: %d lat: %f lon: %f", i, mp.x, mp.y, mp.lat, mp.lon))
                i++
            }
        }

        if (llGrid != null) {
            info.add("lat/lon grid:")
            info.add("  enabled: ${llGrid!!.enabled}")
            info.add("  spacing: ${llGrid!!.spacing}")
            info.add("  autoscale: ${llGrid!!.autoscale}")
            info.add("  deg. color: ${llGrid!!.color1}")
            info.add("  min. color: ${llGrid!!.color2}")
            info.add("  sec. color: ${llGrid!!.color3}")
            info.add("  label spacing: ${llGrid!!.labelSpacing}")
            info.add("  label foreground: ${llGrid!!.labelForeground}")
            info.add("  label background: ${llGrid!!.labelBackground}")
            info.add("  label size: ${llGrid!!.labelSize}")
            info.add("  label everywhere: ${llGrid!!.labelShowEverywhere}")
        }

        if (grGrid != null) {
            info.add("other grid:")
            info.add("  enabled: ${grGrid!!.enabled}")
            info.add("  spacing: ${grGrid!!.spacing}")
            info.add("  autoscale: ${grGrid!!.autoscale}")
            info.add("  km color: ${grGrid!!.color1}")
            info.add("  meter color: ${grGrid!!.color2}")
            info.add("  label spacing: ${grGrid!!.labelSpacing}")
            info.add("  label foreground: ${grGrid!!.labelForeground}")
            info.add("  label background: ${grGrid!!.labelBackground}")
            info.add("  label size: ${grGrid!!.labelSize}")
            info.add("  label everywhere: ${grGrid!!.labelShowEverywhere}")
        }

        return info
    }
}