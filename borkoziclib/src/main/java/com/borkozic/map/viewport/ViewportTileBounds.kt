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
    }

    fun calculate(): TileBounds {
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

            val mapPx = mapX + sx * cosB - sy * sinB
            val mapPy = mapY + sx * sinB + sy * cosB

            val c = ozf.map_x_to_c(mapPx.toInt())
            val r = ozf.map_y_to_r(mapPy.toInt())

            val clampedC = c.coerceIn(0.0, ozf.tiles_per_x().toDouble())
            val clampedR = r.coerceIn(0.0, ozf.tiles_per_y().toDouble())

            if (clampedC < minX) minX = clampedC.toFloat()
            if (clampedC > maxX) maxX = clampedC.toFloat()
            if (clampedR < minY) minY = clampedR.toFloat()
            if (clampedR > maxY) maxY = clampedR.toFloat()
        }

        val cMin = Math.max(0, Math.floor(minX.toDouble()).toInt())
        val cMax = Math.min(ozf.tiles_per_x(), Math.ceil(maxX.toDouble()).toInt())
        val rMin = Math.max(0, Math.floor(minY.toDouble()).toInt())
        val rMax = Math.min(ozf.tiles_per_y(), Math.ceil(maxY.toDouble()).toInt())

        val tileW = ozf.tile_dx().toFloat()
        val tileH = ozf.tile_dy().toFloat()

        // Tile positions are in the already-rotated canvas coordinate system.
        // The canvas is rotated by -bearing before drawMap is called, so tiles
        // should be drawn at their direct map→canvas positions. No extra rotation
        // needed here — the canvas handles the visual rotation.
        val txb = (sw / 2f + cMin * tileW - mapX).toInt()
        val tyb = (sh / 2f + rMin * tileH - mapY).toInt()

        return TileBounds(cMin, cMax, rMin, rMax, txb, tyb)
    }
}
