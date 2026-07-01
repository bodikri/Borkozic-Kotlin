package com.borkozic.map.viewport

import com.borkozic.map.OzfReader

data class TileBounds(
    val cMin: Int, val cMax: Int,
    val rMin: Int, val rMax: Int,
    val txb: Int, val tyb: Int
)

class ViewportTileBounds(
    private val ozf: OzfReader,
    mapCenterXY: IntArray,
    screenWidth: Int,
    screenHeight: Int,
    bearingRad: Float = 0f
) {
    private val mapX: Float
    private val mapY: Float
    private val hw: Float
    private val hh: Float
    private val cosB: Float
    private val sinB: Float
    private val bearingDeg: Double
    private val sw: Int
    private val sh: Int

    init {
        mapX = mapCenterXY[0].toFloat()
        mapY = mapCenterXY[1].toFloat()
        hw = screenWidth / 2f
        hh = screenHeight / 2f
        sw = screenWidth
        sh = screenHeight
        cosB = Math.cos(bearingRad.toDouble()).toFloat()
        sinB = Math.sin(bearingRad.toDouble()).toFloat()
        bearingDeg = Math.toDegrees(bearingRad.toDouble())
    }

    fun calculate(): TileBounds {
        // Corners relative to canvas center (= map_xy position on canvas)
        val corners: Array<FloatArray> = arrayOf(
            floatArrayOf(-hw, -hh),
            floatArrayOf(hw, -hh),
            floatArrayOf(hw, hh),
            floatArrayOf(-hw, hh)
        )

        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE

        for (corner in corners) {
            val sx = corner[0]
            val sy = corner[1]

            // Canvas is rotated CCW by -bearing around rotation center.
            // To find map coords of a screen corner, we need INVERSE rotation (CW).
            // CW rotation by bearing: x' = x*cos + y*sin, y' = -x*sin + y*cos
            val mapPx = mapX + sx * cosB + sy * sinB
            val mapPy = mapY - sx * sinB + sy * cosB

            val c = ozf.map_x_to_c(mapPx.toInt())
            val r = ozf.map_y_to_r(mapPy.toInt())

            if (c < minX) minX = c.toFloat()
            if (c > maxX) maxX = c.toFloat()
            if (r < minY) minY = r.toFloat()
            if (r > maxY) maxY = r.toFloat()
        }

        val cMin = Math.max(0, Math.floor(minX.toDouble()).toInt())
        val cMax = Math.min(ozf.tiles_per_x(), Math.ceil(maxX.toDouble()).toInt())
        val rMin = Math.max(0, Math.floor(minY.toDouble()).toInt())
        val rMax = Math.min(ozf.tiles_per_y(), Math.ceil(maxY.toDouble()).toInt())

        val tileW = ozf.tile_dx().toFloat()
        val tileH = ozf.tile_dy().toFloat()

        // Debug: tile range for rotated viewport
        android.util.Log.d("VTB", "bearing=%.1f° hw=%.0f hh=%.0f mapXY=(%.0f,%.0f) tiles: c[%d..%d] r[%d..%d] (total=%d) tileW=%d tileH=%d"
            .format(bearingDeg, hw, hh, mapX, mapY,
                cMin, cMax, rMin, rMax,
                (cMax - cMin) * (rMax - rMin), tileW.toInt(), tileH.toInt()))

        // Tile positions are in the already-rotated canvas coordinate system.
        // The canvas is rotated by -bearing before drawMap is called, so tiles
        // should be drawn at their direct map→canvas positions. No extra rotation
        // needed here — the canvas handles the visual rotation.
        val txb = (sw / 2f + cMin * tileW - mapX).toInt()
        val tyb = (sh / 2f + rMin * tileH - mapY).toInt()

        android.util.Log.d("VTB", "txb=%d tyb=%d (first tile at canvas pos)".format(txb, tyb))

        return TileBounds(cMin, cMax, rMin, rMax, txb, tyb)
    }
}
