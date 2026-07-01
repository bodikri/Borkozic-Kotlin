package com.borkozic.map.viewport

/**
 * Tile range for a viewport rectangle.
 */
data class TileBounds(
    val cMin: Int, val cMax: Int,
    val rMin: Int, val rMax: Int,
    val txb: Float, val tyb: Float
)

/**
 * Compute tile bounds for the viewport taking rotation into account.
 *
 * When the map is rotated (bearing != 0), the visible area in map
 * coordinates becomes a rotated rectangle.  A simple axis-aligned
 * bounding box around `(map_xy[0] ± w/2, map_xy[1] ± h/2)` will either
 * miss tiles that are visible or fetch tiles that aren't on screen.
 *
 * This class computes the axis-aligned bounding box of the **rotated**
 * viewport so every visible tile is loaded and no extras are drawn.
 *
 * Canvas rotation is **clockwise** by *bearing*.
 * To convert screen-space corners back to unrotated map XY we apply
 * the inverse: a counter-clockwise rotation of `+bearing`.
 */
class ViewportTileBounds(
    private val ozf: OzfReader,
    mapCenterXY: IntArray,       // map XY of center, already shifted by lookAhead
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

    init {
        mapX = mapCenterXY[0].toFloat()
        mapY = mapCenterXY[1].toFloat()
        hw = screenWidth / 2f
        hh = screenHeight / 2f
        cosB = Math.cos(bearingRad.toDouble()).toFloat()
        sinB = Math.sin(bearingRad.toDouble()).toFloat()
    }

    fun calculate(): TileBounds {
        // Four screen-space corners, relative to screen center
        val corners: Array<FloatArray> = arrayOf(
            floatArrayOf(-hw, -hh),  // top-left in screen local
            floatArrayOf(hw, -hh),   // top-right
            floatArrayOf(hw, hh),    // bottom-right
            floatArrayOf(-hw, hh)    // bottom-left
        )

        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE

        for (corner in corners) {
            val sx = corner[0]
            val sy = corner[1]

            // Inverse rotation (CCW +bearing) of screen corner → map XY
            // CW-rotates a screen point by bearing into mapXY frame.
            // Unrotating means CCW rotation of the corner by +bearing:
            //   (x,y) → (x·cosB − y·sinB, x·sinB + y·cosB)
            val mapPx = mapX + sx * cosB - sy * sinB
            val mapPy = mapY + sx * sinB + sy * cosB

            // Convert to tile column/row using ozf
            val c = ozf.map_x_to_c(mapPx.toInt()).toDouble()
            val r = ozf.map_y_to_r(mapPy.toInt()).toDouble()

            // Clamp to valid range so corners outside the map don't
            // pull the bounding box beyond it
            val clampedC = c.coerceIn(0.0, ozf.tiles_per_x().toDouble())
            val clampedR = r.coerceIn(0.0, ozf.tiles_per_y().toDouble())

            if (clampedC < minX) minX = clampedC.toFloat()
            if (clampedC > maxX) maxX = clampedC.toFloat()
            if (clampedR < minY) minY = clampedR.toFloat()
            if (clampedR > maxY) maxY = clampedR.toFloat()
        }

        val cMin = Math.max(0, Math.floor(minX).toInt())
        val cMax = Math.min(ozf!!.tiles_per_x(), Math.ceil(maxX).toInt())
        val rMin = Math.max(0, Math.floor(minY).toInt())
        val rMax = Math.min(ozf!!.tiles_per_y(), Math.ceil(maxY).toInt())

        // ------------------------------------------------------------------
        // txb / tyb: screen position of the top-left corner of the tile at
        // column cMin, row rMin.  Computed in unrotated screen space.
        // ------------------------------------------------------------------
        val tileW = ozf!!.tile_dx().toFloat()
        val tileH = ozf!!.tile_dy().toFloat()

        // Vector from mapXY center to (cMin, rMin) tile-top-left
        val relX = (cMin * tileW).toDouble() - mapX.toDouble()
        val relY = (rMin * tileH).toDouble() - mapY.toDouble()

        // Unrotate (CCW) to get to screen space
        val uX = (relX * cosB - relY * sinB).toFloat()
        val uY = (relX * sinB + relY * cosB).toFloat()

        val txb = screenWidth / 2f + uX
        val tyb = screenHeight / 2f + uY

        return TileBounds(cMin, cMax, rMin, rMax, txb, tyb)
    }
}
