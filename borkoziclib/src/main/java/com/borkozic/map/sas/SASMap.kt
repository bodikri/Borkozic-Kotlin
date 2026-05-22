/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2014  Andrey Novikov <http://andreynovikov.info/>
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
package com.borkozic.map.sas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.Log
import android.view.View
import com.borkozic.map.Map
import com.borkozic.map.Tile
import com.borkozic.map.Tile.Companion.getKey
import com.borkozic.map.TileRAMCache
import com.jhlabs.map.Ellipsoid
import com.jhlabs.map.proj.ProjectionFactory.fromPROJ4Specification
import java.io.IOException
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.tan

class SASMap(var name: String, @JvmField var path: String?, @JvmField var ext: String?, zmin: Int, zmax: Int) : Map(
    name
) {
    private var isActive = false
    private var srcZoom: Byte
    private val defZoom: Byte
    private var dynZoom: Double

    var minZoom: Byte
    var maxZoom: Byte
    @JvmField
    var ellipsoid: Boolean = false

    init {
        minZoom = zmin.toByte()
        maxZoom = zmax.toByte()

        datum = "WGS84"
        projection =
            fromPROJ4Specification("+proj=merc".split(" ".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray())
        projection!!.setEllipsoid(Ellipsoid.WGS_1984)
        projection!!.initialize()

        title = String.format("%s (%d)", name, maxZoom)
        srcZoom = maxZoom
        defZoom = maxZoom
        zoom = 1.0
        dynZoom = 1.0

        /*
	     * The distance represented by one pixel (S) is given by
	     * S=C*cos(y)/2^(z+8) 
	     *
	     * where...
	     *
	     * C is the (equatorial) circumference of the Earth 
	     * z is the zoom level 
	     * y is the latitude of where you're interested in the scale. 
	     *
	     * Make sure your calculator is in degrees mode, unless you want to express latitude
	     * in radians for some reason. C should be expressed in whatever scale unit you're
	     * interested in (miles, meters, feet, smoots, whatever). Since the earth is actually
	     * ellipsoidal, there will be a slight error in this calculation. But it's very slight.
	     * (0.3% maximum error) 
	     */
        mpp =
            projection!!.getEllipsoid().equatorRadius * Math.PI * 2 * cos(0.0) / 2.0.pow((srcZoom + 8).toDouble())
    }

    @Throws(IOException::class, OutOfMemoryError::class)
    public override fun activate(view: View?, pixels: Int) {
        this.pixels = pixels
        mapClipPath = Path()
        setZoom(if (savedZoom == 0.0) zoom else savedZoom)
        savedZoom = 0.0

        borderPaint = Paint()
        borderPaint!!.setAntiAlias(true)
        borderPaint!!.setStrokeWidth(3f)
        borderPaint!!.setColor(Color.RED)
        borderPaint!!.setAlpha(128)
        borderPaint!!.setStyle(Paint.Style.STROKE)

        isActive = true
    }

    public override fun deactivate() {
        isActive = false
        cache!!.destroy()
        if (savedZoom != 0.0) zoom = savedZoom
        savedZoom = 0.0
        cache = null
        mapClipPath = null
        borderPaint = null
    }

    public override fun activated(): Boolean {
        return isActive
    }

    @Throws(OutOfMemoryError::class)
    public override fun drawMap(
        bearing: Float,
        loc: DoubleArray,
        lookAhead: IntArray,
        width: Int,
        height: Int,
        cropBorder: Boolean,
        drawBorder: Boolean,
        c: Canvas
    ): Boolean {
        if (isActive == false) return false

        val map_xy = IntArray(2)
        getXYByLatLon(loc[0], loc[1], map_xy)
        map_xy[0] -= lookAhead[0]
        map_xy[1] -= lookAhead[1]

        val clipPath = Path()

        if (cropBorder || drawBorder) mapClipPath!!.offset(
            (-map_xy[0] + width / 2).toFloat(),
            (-map_xy[1] + height / 2).toFloat(),
            clipPath
        )

        val tile_w = (TILE_WIDTH * dynZoom).toInt()
        val tile_h = (TILE_HEIGHT * dynZoom).toInt()

        val sas_x = map_xy[0] / tile_w
        val sas_y = map_xy[1] / tile_h

        val x = Math.round((map_xy[0] - sas_x * tile_w).toFloat())
        val y = Math.round((map_xy[1] - sas_y * tile_h).toFloat())

        val tiles_per_x = Math.round(width * 1f / tile_w / 2 + .5f)
        val tiles_per_y = Math.round(height * 1f / tile_h / 2 + .5f)

        var c_min = sas_x - tiles_per_x
        var c_max = sas_x + tiles_per_x + 1

        var r_min = sas_y - tiles_per_y
        var r_max = sas_y + tiles_per_y + 1

        var result = true

        if (c_min < 0) {
            c_min = 0
            result = false
        }
        if (r_min < 0) {
            r_min = 0
            result = false
        }
        if (c_max > 2.0.pow(srcZoom.toDouble())) {
            c_max = (2.0.pow(srcZoom.toDouble())).toInt()
            result = false
        }
        if (r_max > 2.0.pow(srcZoom.toDouble())) {
            r_max = (2.0.pow(srcZoom.toDouble())).toInt()
            result = false
        }

        val txb = width / 2 - x - (sas_x - c_min) * tile_w
        val tyb = height / 2 - y - (sas_y - r_min) * tile_h

        for (i in r_min until r_max) {
            for (j in c_min until c_max) {
                val tx = txb + (j - c_min) * tile_w
                val ty = tyb + (i - r_min) * tile_h

                val tile = getTile(j, i)

                if (tile != null && !tile.isRecycled()) {
                    c.drawBitmap(tile, tx.toFloat(), ty.toFloat(), null)
                } else {
                    result = false
                }
            }
        }

        if (drawBorder && borderPaint != null) c.drawPath(clipPath, borderPaint!!)

        return result
    }

    @Throws(OutOfMemoryError::class)
    fun getTile(x: Int, y: Int): Bitmap? {
        try {
            // SAS counts zooms from 1, not from 0
            val key = getKey(x, y, (srcZoom + 1).toByte())
            val c = cache!!
            var tile = c.get(key)
            if (tile == null) {
                tile = Tile(x, y, (srcZoom + 1).toByte())
                SASTileFactory.loadTile(this, tile)
                if (tile.bitmap == null) {
                    SASTileFactory.generateTile(this, c, tile)
                }
                if (tile.bitmap != null) {
                    if (dynZoom != 1.0) {
                        val sw = (dynZoom * TILE_WIDTH).toInt()
                        val sh = (dynZoom * TILE_HEIGHT).toInt()
                        val scaled = Bitmap.createScaledBitmap(tile.bitmap!!, sw, sh, true)
                        tile.bitmap = scaled
                    }
                    cache!!.put(tile)
                }
            }
            return tile.bitmap
        } catch (e: NullPointerException) {
            // Strange situation when cache becomes null in the middle of the method
            return null
        }
    }

    public override fun getLatLonByXY(x: Int, y: Int, ll: DoubleArray): Boolean {
        val map_x = (x * 1.0 / dynZoom).toInt()
        val map_y = (y * 1.0 / dynZoom).toInt()
        val dx: Double = map_x * 1.0 / TILE_WIDTH
        val dy: Double = map_y * 1.0 / TILE_HEIGHT

        val n = 2.0.pow(srcZoom.toDouble())
        if (ellipsoid) {
            ll[0] = (map_y - TILE_HEIGHT * n / 2) / -(TILE_HEIGHT * n / (2 * Math.PI))
            ll[0] = (2 * atan(exp(ll[0])) - Math.PI / 2) * 180 / Math.PI

            var Zu = Math.toRadians(ll[0])
            var Zum1 = Zu + 1
            val yy: Double = (map_y - TILE_HEIGHT * n / 2)
            var i = 100000
            while ((abs(Zum1 - Zu) > 0.0000001) && (i != 0)) {
                i--
                Zum1 = Zu
                Zu = asin(
                    1 - ((1 + sin(Zum1)) * (1 - 0.0818197 * sin(Zum1)).pow(0.0818197))
                            / (exp((2 * yy) / -(TILE_HEIGHT * n / (2 * Math.PI))) * (1 + 0.0818197 * sin(
                        Zum1
                    )).pow(0.0818197))
                )
            }
            ll[0] = Math.toDegrees(Zu)
        } else {
            ll[0] = Math.toDegrees(atan((sinh(Math.PI * (1 - 2 * dy / n)))))
        }
        ll[1] = dx * 360.0 / n - 180.0

        return true
    }

    public override fun getXYByLatLon(lat: Double, lon: Double, xy: IntArray): Boolean {
        val n = 2.0.pow(srcZoom.toDouble())

        xy[0] = floor((lon + 180.0) / 360.0 * n * TILE_WIDTH * dynZoom).toInt()

        if (ellipsoid) {
            val z = sin(Math.toRadians(lat))
            xy[1] =
                floor((1 - (atanh(z) - 0.0818197 * atanh(0.0818197 * z)) / Math.PI) / 2 * n * TILE_HEIGHT * dynZoom).toInt()
        } else {
            xy[1] =
                floor((1 - (ln(tan(Math.toRadians(lat)) + 1 / cos(Math.toRadians(lat))) / Math.PI)) / 2 * n * TILE_HEIGHT * dynZoom).toInt()
        }
        return true
    }

    public override fun getNextZoom(): Double {
        val z = defZoom + (ln(zoom) / ln(2.0)).toInt()
        if (z - maxZoom > 1) return 0.0
        else if (z < minZoom || z > maxZoom) return zoom * 2
        else return 2.0.pow((srcZoom + 1 - defZoom).toDouble())
    }

    public override fun getPrevZoom(): Double {
        val z = defZoom + (ln(zoom) / ln(2.0)).toInt()
        if (z - minZoom < -1) return 0.0
        else if (z < minZoom || z > maxZoom) return zoom / 2
        else return 2.0.pow((srcZoom - 1 - defZoom).toDouble())
    }

    public override fun getZoom(): Double {
        return zoom
    }

    public override fun setZoom(z: Double) {
        var zDiff = (ln(z) / ln(2.0)).toInt()
        Log.e("SAS", "Zoom: " + z + " diff: " + zDiff)

        srcZoom = (defZoom + zDiff).toByte()

        if (srcZoom > maxZoom) {
            zDiff -= srcZoom - maxZoom
            srcZoom = maxZoom
        }
        if (srcZoom < minZoom) {
            zDiff -= srcZoom - minZoom
            srcZoom = minZoom
        }

        zoom = z
        dynZoom = zoom / 2.0.pow((srcZoom - defZoom).toDouble())
        if (abs(dynZoom - 1) < 0.0078125) dynZoom = 1.0

        //Log.e("SAS", "z: " + srcZoom + " diff: " + zDiff + " zoom: " + zoom + " dymZoom: " + dynZoom);
        if (cache != null) cache!!.destroy()
        val cacheSize = (pixels * 1.0 / (TILE_WIDTH * dynZoom * TILE_HEIGHT * dynZoom) * 3).toInt()
        //Log.e("SAS", "Cache size: " + cacheSize);
        cache = TileRAMCache(cacheSize)

        title = String.format("%s (%d)", name, srcZoom)

        mapClipPath!!.rewind()
        mapClipPath!!.setLastPoint(
            (cornerMarkers!![0].x * zoom).toFloat(),
            (cornerMarkers!![0].y * zoom).toFloat()
        )
        for (i in 1 until cornerMarkers!!.size) mapClipPath!!.lineTo(
            (cornerMarkers!![i].x * zoom).toFloat(),
            (cornerMarkers!![i].y * zoom).toFloat()
        )
        mapClipPath!!.close()
    }

    override val scaledWidth: Int
        get() = (2.0.pow(srcZoom.toDouble()) * TILE_WIDTH * zoom).toInt()

    override val scaledHeight: Int
        get() = (2.0.pow(srcZoom.toDouble()) * TILE_HEIGHT * zoom).toInt()

    public override fun getMapCenter(center: DoubleArray) {
        val x = ((cornerMarkers!![2].x + cornerMarkers!![0].x) / 2 * zoom).toInt()
        val y = ((cornerMarkers!![2].y + cornerMarkers!![0].y) / 2 * zoom).toInt()
        getLatLonByXY(x, y, center)
    }

    public override fun info(): List<String> {
        val info = ArrayList<String>()

        info.add("title: " + title)
        info.add("path: " + path)
        info.add("minimum zoom: " + minZoom)
        info.add("maximum zoom: " + maxZoom)
        info.add("tile extention: " + ext)
        if (projection != null) {
            info.add("projection: " + prjName + " (" + projection!!.getEPSGCode() + ")")
            info.add("\t" + projection!!.pROJ4Description)
        }
        info.add("datum: " + datum)
        info.add("scale (mpp): " + mpp)

        return info
    }

    companion object {
        private const val serialVersionUID = 1L

        const val TILE_WIDTH: Int = 256
        const val TILE_HEIGHT: Int = 256

        /**
         * Calculates the inverse hyperbolic tangent of the number, i.e.
         * the value whose hyperbolic tangent is number
         * @param arg number
         * @return inverse hyperbolic tangent
         */
        private fun atanh(arg: Double): Double {
            return 0.5 * ln((1 + arg) / (1 - arg))
        }
    }
}
