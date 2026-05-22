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
package com.borkozic.map.online

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import android.view.View
import com.borkozic.map.Map
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

class OnlineMap(provider: TileProvider, z: Byte) : Map("http://...") {
    private val tileController: TileController
    val tileProvider: TileProvider
    private var isActive = false
    private var srcZoom: Byte
    private val defZoom: Byte

    init {
        datum = "WGS84"
        projection =
            fromPROJ4Specification("+proj=merc".split(" ".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray())
        projection!!.setEllipsoid(Ellipsoid.WGS_1984)
        projection!!.initialize()

        tileProvider = provider
        tileController = TileController()

        title = String.format("%s (%d)", tileProvider.name, z)
        srcZoom = z
        defZoom = z
        zoom = 1.0
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
        setZoom(if (savedZoom == 0.0) zoom else savedZoom)
        savedZoom = 0.0
        val cacheSize = (pixels / (TILE_WIDTH * TILE_HEIGHT) * 4)
        cache = TileRAMCache(cacheSize)
        tileController.setView(view)
        tileController.setCache(cache)
        tileController.setProvider(tileProvider)
        isActive = true
    }

    public override fun deactivate() {
        if (!isActive) return
        isActive = false
        tileController.interrupt()
        cache!!.destroy()
        if (savedZoom != 0.0) zoom = savedZoom
        savedZoom = 0.0
        cache = null
    }

    public override fun activated(): Boolean {
        return isActive
    }

    public override fun coversLatLon(lat: Double, lon: Double): Boolean {
        if (!isActive) mpp =
            projection!!.getEllipsoid().equatorRadius * Math.PI * 2 * cos(Math.toRadians(lat)) / 2.0.pow(
                (srcZoom + 8).toDouble()
            )
        return lat < 85.051129 && lat > -85.047336
    }

    public override fun coversScreen(map_xy: IntArray, width: Int, height: Int): Boolean {
        // TODO Should check North and South edges
        return true
    }

    public override fun containsArea(area: Bounds): Boolean {
        // FIXME disabled online maps in adjacent maps
        return false
        //		return area.minLat < 85.051129 && area.maxLat > -85.047336;
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
        val map_xy = IntArray(2)
        getXYByLatLon(loc[0], loc[1], map_xy)
        map_xy[0] -= lookAhead[0]
        map_xy[1] -= lookAhead[1]
        val osm_x: Int = map_xy[0] / TILE_WIDTH
        val osm_y: Int = map_xy[1] / TILE_HEIGHT
        //double x0=(double) map_xy[0]/(double)TILE_WIDTH;//центъра на координатна система
        //double y0=(double) map_xy[1]/(double)TILE_HEIGHT;
        //android.util.Log.d("OnlineMap","map_xy[1]:"+map_xy[1]+" lookAhead[1]:"+lookAhead[1]/200);
        //int x = (int) Math.round(map_xy[0] - osm_x * TILE_WIDTH);
        //int y = (int) Math.round(map_xy[1] - osm_y * TILE_HEIGHT);
        val t_per_x = (width.toDouble() / TILE_WIDTH.toDouble() / 2 + .5) + .4
        val t_per_y = (height * 1f / TILE_HEIGHT / 2 + .5f).toDouble() + .7
        val tiles_per_x: Int = Math.round(width * 1f / TILE_WIDTH / 2 + .5f)
        val tiles_per_y: Int = Math.round(height * 1f / TILE_HEIGHT / 2 + .5f)

        val c_min = osm_x - tiles_per_x
        val c_max = osm_x + tiles_per_x

        val r_min = osm_y - tiles_per_y
        val r_max = osm_y + tiles_per_y + 1

        var result = true

        if (c_min < 0) { //c_min = 0;
            result = false
        }
        if (r_min < 0) { //r_min = 0;
            result = false
        }
        if (c_max > 2.0.pow(srcZoom.toDouble())) { //c_max = (int) (Math.pow(2.0, srcZoom));
            result = false
        }
        if (r_max > 2.0.pow(srcZoom.toDouble())) { //r_max = (int) (Math.pow(2.0, srcZoom));
            result = false
        }

        val latw = (lookAhead[1].toDouble() / TILE_HEIGHT.toDouble())
        val x1: Double
        val x2: Double
        val x3: Double
        val x4: Double
        val y1: Double
        val y2: Double
        val y3: Double
        val y4: Double
        var step_y: Double
        var step_x: Double
        val sx: Double
        val sy: Double
        var max_y: Double
        var min_y: Double
        if (bearing > 3 && bearing <= 87) {
            x1 =
                (t_per_x * cos(Math.toRadians(bearing.toDouble())) + t_per_y * sin(Math.toRadians(-bearing.toDouble())))
            y1 = (t_per_y * cos(Math.toRadians(bearing.toDouble())) + t_per_x * sin(
                Math.toRadians(bearing.toDouble())
            ))
            x2 =
                (t_per_x * cos(Math.toRadians(bearing.toDouble())) - t_per_y * sin(Math.toRadians(-bearing.toDouble())))
            //y2 = (-t_per_y*Math.cos(Math.toRadians(bearing))+t_per_x*Math.sin(Math.toRadians(bearing)));
            x3 =
                (-t_per_x * cos(Math.toRadians(bearing.toDouble())) - t_per_y * sin(Math.toRadians(-bearing.toDouble())))
            y3 = (-t_per_y * cos(Math.toRadians(bearing.toDouble())) - t_per_x * sin(
                Math.toRadians(bearing.toDouble())
            ))
            x4 =
                (-t_per_x * cos(Math.toRadians(bearing.toDouble())) + t_per_y * sin(Math.toRadians(-bearing.toDouble())))
            y4 = (t_per_y * cos(Math.toRadians(bearing.toDouble())) - t_per_x * sin(
                Math.toRadians(bearing.toDouble())
            ))
            /*x1= ((double)tiles_per_x*Math.cos(Math.toRadians(bearing))+(double)tiles_per_y*Math.sin(Math.toRadians(-bearing)));
			y1 = ((double)tiles_per_y*Math.cos(Math.toRadians(bearing))+(double)tiles_per_x*Math.sin(Math.toRadians(bearing)));
			x2= ((double)tiles_per_x*Math.cos(Math.toRadians(bearing))-(double)tiles_per_y*Math.sin(Math.toRadians(-bearing)));
			y2 = (-(double)tiles_per_y*Math.cos(Math.toRadians(bearing))+(double)tiles_per_x*Math.sin(Math.toRadians(bearing)));
			x3= (-(double)tiles_per_x*Math.cos(Math.toRadians(bearing))-(double)tiles_per_y*Math.sin(Math.toRadians(-bearing)));
			y3 = (-(double)tiles_per_y*Math.cos(Math.toRadians(bearing))-(double)tiles_per_x*Math.sin(Math.toRadians(bearing)));
			x4= (-(double)tiles_per_x*Math.cos(Math.toRadians(bearing))+(double)tiles_per_y*Math.sin(Math.toRadians(-bearing)));
			y4 = ((double)tiles_per_y*Math.cos(Math.toRadians(bearing))-(double)tiles_per_x*Math.sin(Math.toRadians(bearing)));*/
            //int mt = (int)Math.round(latw*(1-Math.cos(Math.toRadians(bearing))));
            //int kt = (int)Math.round(latw*Math.sin(Math.toRadians(bearing)));
            step_x = (y1 - y4) / (x1 - x4)
            sx = step_x
            step_y = (y4 - y3) / (x4 - x3) //отрицателно за този кваадрант
            if (step_y < (y3 - y4)) step_y = y3 //би трябвало да е отрицателно число

            sy = step_y
            //android.util.Log.d("OnlineMap","lA/TW:"+lookAhead[1]/TILE_HEIGHT+" latw:"+latw);
            val mt = Math.round(latw * (1 - cos(Math.toRadians(bearing.toDouble())))).toInt()
            val kt = Math.round(latw * sin(Math.toRadians(bearing.toDouble()))).toInt()
            max_y = y4
            min_y = y4 + step_y
            for (fx in Math.round(x4).toInt()..Math.round(x2).toInt()) {
                for (fy in Math.round(min_y).toInt()..Math.round(max_y).toInt()) {
                    val jx = osm_x + kt + fx
                    val iy = osm_y + mt + fy
                    val tx: Int =
                        width / 2 - map_xy[0] + jx * TILE_WIDTH //int tx = txb + (j - c_min) * TILE_WIDTH;
                    val ty: Int =
                        height / 2 - map_xy[1] + iy * TILE_HEIGHT //int ty = tyb + (i - r_min) * TILE_HEIGHT;
                    val tile = getTile(jx, iy)
                    if (tile != null && !tile.isRecycled()) {
                        c.drawBitmap(tile, tx.toFloat(), ty.toFloat(), null)
                    }
                }
                min_y += step_y
                if (min_y < y3) {
                    min_y = y3
                    step_y = sx
                }
                max_y += step_x
                if (max_y > y1) {
                    max_y = y1
                    step_x = sy
                }
            }
        } else if (bearing > 87 && bearing < 93) { //Изток
            val mt = Math.round(latw * (1 - cos(Math.toRadians(bearing.toDouble())))).toInt()
            val kt = Math.round(latw * sin(Math.toRadians(bearing.toDouble()))).toInt()
            for (fx in -tiles_per_x - 1..tiles_per_x) {
                for (fy in kt - tiles_per_y - 1..tiles_per_y) {
                    val jx = osm_x + kt + fx
                    val iy = osm_y + mt + fy
                    val tx: Int = width / 2 - map_xy[0] + jx * TILE_WIDTH
                    val ty: Int = height / 2 - map_xy[1] + iy * TILE_HEIGHT
                    val tile = getTile(jx, iy)
                    if (tile != null && !tile.isRecycled()) {
                        c.drawBitmap(tile, tx.toFloat(), ty.toFloat(), null)
                    }
                }
            }
        } else if (bearing >= 93 && bearing <= 177) {
            x1 =
                (t_per_x * cos(Math.toRadians(bearing.toDouble())) + t_per_y * sin(Math.toRadians(-bearing.toDouble())))
            y1 = (t_per_y * cos(Math.toRadians(bearing.toDouble())) + t_per_x * sin(
                Math.toRadians(bearing.toDouble())
            ))
            x2 =
                (t_per_x * cos(Math.toRadians(bearing.toDouble())) - t_per_y * sin(Math.toRadians(-bearing.toDouble())))
            y2 = ((-t_per_y - .3) * cos(Math.toRadians(bearing.toDouble())) + t_per_x * sin(
                Math.toRadians(bearing.toDouble())
            ))
            x3 =
                (-t_per_x * cos(Math.toRadians(bearing.toDouble())) - t_per_y * sin(Math.toRadians(-bearing.toDouble())))
            //y3 = (-t_per_y*Math.cos(Math.toRadians(bearing))-t_per_x*Math.sin(Math.toRadians(bearing)));
            x4 =
                (-t_per_x * cos(Math.toRadians(bearing.toDouble())) + t_per_y * sin(Math.toRadians(-bearing.toDouble())))
            y4 = ((t_per_y + .3) * cos(Math.toRadians(bearing.toDouble())) - t_per_x * sin(
                Math.toRadians(bearing.toDouble())
            ))

            step_x = (y2 - y1) / (x2 - x1)
            sx = step_x
            step_y = (y1 - y4) / (x1 - x4) //отрицателно за този кваадрант
            if (step_y < (y4 - y1)) step_y = y4 //би трябвало да е отрицателно число

            sy = step_y
            val mt = Math.round(latw * (1 - cos(Math.toRadians(bearing.toDouble())))).toInt()
            val kt = Math.round(latw * sin(Math.toRadians(bearing.toDouble()))).toInt()
            max_y = y1
            min_y = y1 + step_y

            for (fx in Math.round(x1).toInt()..Math.round(x3).toInt()) {
                for (fy in Math.round(min_y).toInt()..Math.round(max_y).toInt()) {
                    val jx = osm_x + kt + fx
                    val iy = osm_y + mt + fy
                    val tx: Int =
                        width / 2 - map_xy[0] + jx * TILE_WIDTH //int tx = txb + (j - c_min) * TILE_WIDTH;
                    val ty: Int =
                        height / 2 - map_xy[1] + iy * TILE_HEIGHT //int ty = tyb + (i - r_min) * TILE_HEIGHT;
                    val tile = getTile(jx, iy)
                    if (tile != null && !tile.isRecycled()) {
                        c.drawBitmap(tile, tx.toFloat(), ty.toFloat(), null)
                    }
                }
                min_y += step_y
                if (min_y < y4) {
                    min_y = y4
                    step_y = sx
                }
                max_y += step_x
                if (max_y > y2) {
                    max_y = y2
                    step_x = sy
                }
            }
        } else if (bearing > 177 && bearing < 183) { //Юг
            val mt = Math.round(latw * (1 - cos(Math.toRadians(bearing.toDouble())))).toInt()
            val kt = Math.round(latw * sin(Math.toRadians(bearing.toDouble()))).toInt()
            for (fx in -tiles_per_x..tiles_per_x) {
                for (fy in -tiles_per_y..tiles_per_y) {
                    val jx = osm_x + kt + fx
                    val iy = osm_y + mt + fy
                    val tx: Int =
                        width / 2 - map_xy[0] + jx * TILE_WIDTH //int tx = txb + (j - c_min) * TILE_WIDTH;
                    val ty: Int =
                        height / 2 - map_xy[1] + iy * TILE_HEIGHT //int ty = tyb + (i - r_min) * TILE_HEIGHT;
                    val tile = getTile(jx, iy)
                    if (tile != null && !tile.isRecycled()) {
                        c.drawBitmap(tile, tx.toFloat(), ty.toFloat(), null)
                    }
                }
            }
        } else if (bearing >= 183 && bearing <= 267) {
            x1 =
                (t_per_x * cos(Math.toRadians(bearing.toDouble())) + t_per_y * sin(Math.toRadians(-bearing.toDouble())))
            y1 = (t_per_y * cos(Math.toRadians(bearing.toDouble())) + t_per_x * sin(
                Math.toRadians(bearing.toDouble())
            ))
            x2 =
                (t_per_x * cos(Math.toRadians(bearing.toDouble())) - t_per_y * sin(Math.toRadians(-bearing.toDouble())))
            y2 = (-t_per_y * cos(Math.toRadians(bearing.toDouble())) + t_per_x * sin(
                Math.toRadians(bearing.toDouble())
            ))
            x3 =
                (-t_per_x * cos(Math.toRadians(bearing.toDouble())) - t_per_y * sin(Math.toRadians(-bearing.toDouble())))
            y3 = (-t_per_y * cos(Math.toRadians(bearing.toDouble())) - t_per_x * sin(
                Math.toRadians(bearing.toDouble())
            ))
            x4 =
                (-t_per_x * cos(Math.toRadians(bearing.toDouble())) + t_per_y * sin(Math.toRadians(-bearing.toDouble())))
            //y4 = (t_per_y*Math.cos(Math.toRadians(bearing))-t_per_x*Math.sin(Math.toRadians(bearing)));
            step_x = (y3 - y2) / (x3 - x2)
            sx = step_x
            step_y = (y2 - y1) / (x2 - x1) //отрицателно за този кваадрант
            if (step_y < (y1 - y2)) step_y = y1 //би трябвало да е отрицателно число

            sy = step_y
            //android.util.Log.d("OnlineMap","lA/TW:"+lookAhead[1]/TILE_HEIGHT+" latw:"+latw);
            val mt = Math.round(latw * (1 - cos(Math.toRadians(bearing.toDouble())))).toInt()
            val kt = Math.round(latw * sin(Math.toRadians(bearing.toDouble()))).toInt()
            max_y = y2
            min_y = y2 + step_y

            for (fx in Math.round(x2).toInt()..Math.round(x4).toInt()) {
                for (fy in Math.round(min_y).toInt()..Math.round(max_y).toInt()) {
                    val jx = osm_x + kt + fx
                    val iy = osm_y + mt + fy
                    val tx: Int =
                        width / 2 - map_xy[0] + jx * TILE_WIDTH //int tx = txb + (j - c_min) * TILE_WIDTH;
                    val ty: Int =
                        height / 2 - map_xy[1] + iy * TILE_HEIGHT //int ty = tyb + (i - r_min) * TILE_HEIGHT;
                    val tile = getTile(jx, iy)
                    if (tile != null && !tile.isRecycled()) {
                        c.drawBitmap(tile, tx.toFloat(), ty.toFloat(), null)
                    }
                }
                min_y += step_y
                if (min_y < y1) {
                    min_y = y1
                    step_y = sx
                }
                max_y += step_x
                if (max_y > y3) {
                    max_y = y3
                    step_x = sy
                }
            }
        } else if (bearing > 267 && bearing < 273) { //запад
            val mt = Math.round(latw * (1 - cos(Math.toRadians(bearing.toDouble())))).toInt()
            val kt = Math.round(latw * sin(Math.toRadians(bearing.toDouble()))).toInt()
            for (fx in -tiles_per_x..tiles_per_x) {
                for (fy in -tiles_per_y..tiles_per_y) {
                    val jx = osm_x + kt + fx
                    val iy = osm_y + mt + fy
                    val tx: Int =
                        width / 2 - map_xy[0] + jx * TILE_WIDTH //int tx = txb + (j - c_min) * TILE_WIDTH;
                    val ty: Int =
                        height / 2 - map_xy[1] + iy * TILE_HEIGHT //int ty = tyb + (i - r_min) * TILE_HEIGHT;

                    val tile = getTile(jx, iy)

                    if (tile != null && !tile.isRecycled()) {
                        c.drawBitmap(tile, tx.toFloat(), ty.toFloat(), null)
                    }
                }
            }
        } else if (bearing >= 273 && bearing <= 357) {
            x1 =
                (t_per_x * cos(Math.toRadians(bearing.toDouble())) + t_per_y * sin(Math.toRadians(-bearing.toDouble())))
            //y1 = (t_per_y*Math.cos(Math.toRadians(bearing))+t_per_x*Math.sin(Math.toRadians(bearing)));
            x2 =
                (t_per_x * cos(Math.toRadians(bearing.toDouble())) - t_per_y * sin(Math.toRadians(-bearing.toDouble())))
            y2 = (-t_per_y * cos(Math.toRadians(bearing.toDouble())) + t_per_x * sin(
                Math.toRadians(bearing.toDouble())
            ))
            x3 =
                (-t_per_x * cos(Math.toRadians(bearing.toDouble())) - t_per_y * sin(Math.toRadians(-bearing.toDouble())))
            y3 = (-t_per_y * cos(Math.toRadians(bearing.toDouble())) - t_per_x * sin(
                Math.toRadians(bearing.toDouble())
            ))
            x4 =
                (-t_per_x * cos(Math.toRadians(bearing.toDouble())) + t_per_y * sin(Math.toRadians(-bearing.toDouble())))
            y4 = (t_per_y * cos(Math.toRadians(bearing.toDouble())) - t_per_x * sin(
                Math.toRadians(bearing.toDouble())
            ))
            step_x = (y4 - y3) / (x4 - x3)
            sx = step_x
            step_y = (y3 - y2) / (x3 - x2) //отрицателно за този кваадрант
            if (step_y < (y2 - y3)) step_y = y2 //би трябвало да е отрицателно число

            sy = step_y
            //android.util.Log.d("OnlineMap","lA/TW:"+lookAhead[1]/TILE_HEIGHT+" latw:"+latw);
            val mt = Math.round(latw * (1 - cos(Math.toRadians(bearing.toDouble())))).toInt()
            val kt = Math.round(latw * sin(Math.toRadians(bearing.toDouble()))).toInt()
            max_y = y3
            min_y = y3 + step_y

            for (fx in Math.round(x3).toInt()..Math.round(x1).toInt()) {
                for (fy in Math.round(min_y).toInt()..Math.round(max_y).toInt()) {
                    val jx = osm_x + kt + fx
                    val iy = osm_y + mt + fy
                    val tx: Int =
                        width / 2 - map_xy[0] + jx * TILE_WIDTH //int tx = txb + (j - c_min) * TILE_WIDTH;
                    val ty: Int =
                        height / 2 - map_xy[1] + iy * TILE_HEIGHT //int ty = tyb + (i - r_min) * TILE_HEIGHT;
                    val tile = getTile(jx, iy)
                    if (tile != null && !tile.isRecycled()) {
                        c.drawBitmap(tile, tx.toFloat(), ty.toFloat(), null)
                    }
                }
                min_y += step_y
                if (min_y < y2) {
                    min_y = y2
                    step_y = sx
                }
                max_y += step_x
                if (max_y > y4) {
                    max_y = y4
                    step_x = sy
                }
            }
        } else {
            for (ic in -tiles_per_x..tiles_per_x) {
                for (jr in -tiles_per_y..tiles_per_y) {
                    val jx = osm_x + ic
                    val iy = osm_y + jr
                    val tx: Int =
                        width / 2 - map_xy[0] + jx * TILE_WIDTH //int tx = txb + (j - c_min) * TILE_WIDTH;
                    val ty: Int =
                        height / 2 - map_xy[1] + iy * TILE_HEIGHT //int ty = tyb + (i - r_min) * TILE_HEIGHT;

                    val tile = getTile(jx, iy)

                    if (tile != null && !tile.isRecycled()) {
                        c.drawBitmap(tile, tx.toFloat(), ty.toFloat(), null)
                    }
                }
            }
        }
        return result
    }

    @Throws(OutOfMemoryError::class)
    fun getTile(x: Int, y: Int): Bitmap? {
        val tile = tileController.getTile(x, y, srcZoom)
        return tile.bitmap
    }

    public override fun getBounds(): Bounds {
        if (bounds == null) {
            bounds = Bounds()
            bounds.minLat = -85.047336
            bounds.maxLat = 85.051129
            bounds.minLon = -180.0
            bounds.maxLon = 180.0
        }
        return bounds
    }

    public override fun getLatLonByXY(x: Int, y: Int, ll: DoubleArray): Boolean {
        val dx: Double = x * 1.0 / TILE_WIDTH
        val dy: Double = y * 1.0 / TILE_HEIGHT

        val n = 2.0.pow(srcZoom.toDouble())
        if (tileProvider.ellipsoid) {
            ll[0] = (y - TILE_HEIGHT * n / 2) / -(TILE_HEIGHT * n / (2 * Math.PI))
            ll[0] = (2 * atan(exp(ll[0])) - Math.PI / 2) * 180 / Math.PI

            var Zu = Math.toRadians(ll[0])
            var Zum1 = Zu + 1
            val yy: Double = (y - TILE_HEIGHT * n / 2)
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

        xy[0] = floor((lon + 180.0) / 360.0 * n * TILE_WIDTH).toInt()

        if (tileProvider.ellipsoid) {
            val z = sin(Math.toRadians(lat))
            xy[1] =
                floor((1 - (atanh(z) - 0.0818197 * atanh(0.0818197 * z)) / Math.PI) / 2 * n * TILE_HEIGHT).toInt()
        } else {
            xy[1] =
                floor((1 - (ln(tan(Math.toRadians(lat)) + 1 / cos(Math.toRadians(lat))) / Math.PI)) / 2 * n * TILE_HEIGHT).toInt()
        }
        return true
    }

    fun getOsmXYByLatLon(lat: Double, lon: Double, xy: IntArray): Boolean {
        val n = 2.0.pow(srcZoom.toDouble())

        xy[0] = floor((lon + 180) / 360 * n).toInt()
        if (xy[0].toDouble() == n) xy[0] -= 1
        if (tileProvider.ellipsoid) {
            val z = sin(Math.toRadians(lat))
            xy[1] =
                floor((1 - (atanh(z) - 0.0818197 * atanh(0.0818197 * z)) / Math.PI) / 2 * n).toInt()
        } else {
            xy[1] =
                floor((1 - ln(tan(Math.toRadians(lat)) + 1 / cos(Math.toRadians(lat))) / Math.PI) / 2 * n).toInt()
        }
        if (xy[1] < 0) xy[1] = 0
        return true
    }

    public override fun getNextZoom(): Double {
        if (srcZoom >= tileProvider.maxZoom) return 0.0
        Log.e("ONLINE", "Next zoom: " + 2.0.pow((this.srcZoom + 1 - defZoom).toDouble()))
        return 2.0.pow((this.srcZoom + 1 - defZoom).toDouble())
    }

    public override fun getPrevZoom(): Double {
        if (srcZoom <= tileProvider.minZoom) return 0.0
        Log.e("ONLINE", "Prev zoom: " + 2.0.pow((this.srcZoom - 1 - defZoom).toDouble()))
        return 2.0.pow((this.srcZoom - 1 - defZoom).toDouble())
    }

    public override fun getZoom(): Double {
        return zoom
    }

    public override fun setZoom(z: Double) {
//		setZoom(srcZoom + Math.log(factor)/Math.log(2));

        var zDiff = (ln(z) / ln(2.0)).toInt()
        Log.e("ONLINE", "Zoom: " + z + " diff: " + zDiff)

        srcZoom = (defZoom + zDiff).toByte()

        if (srcZoom > tileProvider.maxZoom) {
            zDiff -= srcZoom - tileProvider.maxZoom
            srcZoom = tileProvider.maxZoom
        }
        if (srcZoom < tileProvider.minZoom) {
            zDiff -= srcZoom - tileProvider.minZoom
            srcZoom = tileProvider.minZoom
        }

        zoom = z
        Log.e("ONLINE", "z: " + srcZoom + " zoom: " + zoom + " diff: " + zDiff)


//		zoom = Math.pow(2, this.srcZoom - defZoom);
        tileController.reset()
        title = String.format("%s (%d)", tileProvider.name, srcZoom)
    }

    override val scaledWidth: Int
        get() = (2.0.pow(srcZoom.toDouble()) * TILE_WIDTH * zoom).toInt()

    override val scaledHeight: Int
        get() = (2.0.pow(srcZoom.toDouble()) * TILE_HEIGHT * zoom).toInt()

    public override fun info(): List<String> {
        val info = ArrayList<String>()

        info.add("title: " + title)
        if (projection != null) {
            info.add("projection: " + prjName + " (" + projection!!.getEPSGCode() + ")")
            info.add("\t" + projection!!.pROJ4Description)
        }
        info.add("datum: " + datum)
        info.add("scale (mpp): " + mpp)

        /*
		info.add("calibration points:");
		
		int i = 1;
		for (MapPoint mp : calibrationPoints)
		{
			info.add(String.format("\t%02d: x: %d y: %d lat: %f lon: %f", i, mp.x, mp.y, mp.lat, mp.lon));
			i++;
		}
		double[] ll = new double[2];
		getLatLonByXY(width/2, height/2, ll);
		info.add("map center (calibration) test: "+ll[0] + " " + ll[1]);
		
		info.add("corners:");
*/
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
