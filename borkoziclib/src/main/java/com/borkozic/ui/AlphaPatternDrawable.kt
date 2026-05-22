/*
 * Copyright (C) 2010 Daniel Nilsson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.borkozic.ui

import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable

/**
 * This drawable that draws a simple white and gray chessboard pattern.
 * It's pattern you will often see as a background behind a
 * partly transparent image in many applications.
 * @author Daniel Nilsson
 */
class AlphaPatternDrawable(rectangleSize: Int) : Drawable() {

    private var mRectangleSize = 10

    private val mPaintWhite = Paint()
    private val mPaintGray = Paint()

    /**
     * Bitmap in which the pattern will be cached.
     */
    private var mBitmap: Bitmap? = null

    init {
        mRectangleSize = rectangleSize
        mPaintWhite.color = -1 // 0xffffffff
        mPaintGray.color = -0x343435 // 0xffcbcbcb
    }

    override fun draw(canvas: Canvas) {
        mBitmap?.let { bmp ->
            canvas.drawBitmap(bmp, null, bounds, null)
        }
    }

    override fun getOpacity(): Int = 0

    override fun setAlpha(alpha: Int) {
        throw UnsupportedOperationException("Alpha is not supported by this drawable.")
    }

    override fun setColorFilter(cf: ColorFilter?) {
        throw UnsupportedOperationException("ColorFilter is not supported by this drawable.")
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        generatePatternBitmap()
    }

    /**
     * This will generate a bitmap with the pattern
     * as big as the rectangle we were allow to draw on.
     * We do this to cache the bitmap so we don't need to
     * recreate it each time draw() is called since it
     * takes a few milliseconds.
     */
    private fun generatePatternBitmap() {
        val height = bounds.height()
        val width = bounds.width()

        if (width <= 0 || height <= 0) return

        val numRectanglesHorizontal = Math.ceil((width / mRectangleSize).toDouble()).toInt()
        val numRectanglesVertical = Math.ceil(height / mRectangleSize.toDouble()).toInt()

        mBitmap = Bitmap.createBitmap(width, height, Config.ARGB_8888)
        val canvas = Canvas(mBitmap!!)

        val r = Rect()
        var verticalStartWhite = true
        for (i in 0..numRectanglesVertical) {
            var isWhite = verticalStartWhite
            for (j in 0..numRectanglesHorizontal) {
                r.top = i * mRectangleSize
                r.left = j * mRectangleSize
                r.bottom = r.top + mRectangleSize
                r.right = r.left + mRectangleSize

                canvas.drawRect(r, if (isWhite) mPaintWhite else mPaintGray)
                isWhite = !isWhite
            }
            verticalStartWhite = !verticalStartWhite
        }
    }
}
