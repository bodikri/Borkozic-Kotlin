/*
 * Copyright (C) 2007 The Android Open Source Project
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

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

class ColorPickerView(c: Context, l: OnColorChangedListener, color: Int) : View(c) {
    private val mPaint: Paint
    private val mCenterPaint: Paint
    private val mHSVPaint: Paint
    private val mColors: IntArray
    private var mHSVColors: IntArray
    private var mRedrawHSV: Boolean
    private var mTrackingCenter = false
    private var mHighlightCenter = false
    private var mListener: OnColorChangedListener?

    private var width = 100
    private var radius = 33
    private var horizontal = false

    fun getColor(): Int {
        return mCenterPaint.color
    }

    fun setColor(color: Int) {
        mCenterPaint.color = color
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val sw = MeasureSpec.getSize(widthMeasureSpec)
        val sh = MeasureSpec.getSize(heightMeasureSpec)

        val pw = if (sw > sh * 1.2) MAX_WIDTH + 45 else MAX_WIDTH
        val ph = if (sw > sh * 1.2) MAX_WIDTH else MAX_WIDTH + 45

        val w = getMeasurement(widthMeasureSpec, pw)
        val h = getMeasurement(heightMeasureSpec, ph)

        if (w > h * 1.2) {
            width = h / 2 - 20
            horizontal = true
        } else {
            width = (h - 45) / 2 - 20
        }

        radius = width / 3

        setMeasuredDimension(w, h)
    }

    private fun getMeasurement(measureSpec: Int, preferred: Int): Int {
        val specSize = MeasureSpec.getSize(measureSpec)
        return when (MeasureSpec.getMode(measureSpec)) {
            MeasureSpec.EXACTLY -> specSize
            MeasureSpec.AT_MOST -> min(preferred, specSize)
            else -> preferred
        }
    }

    override fun onDraw(canvas: Canvas) {
        var cx = getWidth()
        var cy = getHeight()

        if (horizontal)
            cx -= 45
        else
            cy -= 45

        cx /= 2
        cy /= 2

        val r = width - mPaint.strokeWidth * 0.5f

        canvas.translate(cx.toFloat(), cy.toFloat())
        if (horizontal)
            canvas.rotate(-90f)

        val c = mCenterPaint.color

        if (mRedrawHSV) {
            mHSVColors[1] = c
            mHSVPaint.shader = LinearGradient(
                -width.toFloat(), 0f, width.toFloat(), 0f,
                mHSVColors, null, Shader.TileMode.CLAMP
            )
        }

        canvas.drawOval(RectF(-r, -r, r, r), mPaint)
        canvas.drawCircle(0f, 0f, radius.toFloat(), mCenterPaint)
        canvas.drawRect(
            RectF(-width.toFloat(), (width + 25).toFloat(), width.toFloat(), (width + 45).toFloat()),
            mHSVPaint
        )

        if (mTrackingCenter) {
            mCenterPaint.style = Paint.Style.STROKE

            if (mHighlightCenter) {
                mCenterPaint.alpha = 0xFF
            } else {
                mCenterPaint.alpha = 0x80
            }
            canvas.drawCircle(
                0f, 0f,
                radius + mCenterPaint.strokeWidth,
                mCenterPaint
            )

            mCenterPaint.style = Paint.Style.FILL
            mCenterPaint.color = c
        }

        mRedrawHSV = true
    }

    private fun ave(s: Int, d: Int, p: Double): Int {
        return (s + (p * (d - s)).roundToInt())
    }

    private fun interpColor(colors: IntArray, unit: Double): Int {
        if (unit <= 0) {
            return colors[0]
        }
        if (unit >= 1) {
            return colors[colors.size - 1]
        }

        var p = unit * (colors.size - 1)
        val i = p.toInt()
        p -= i

        // now p is just the fractional part [0...1) and i is the index
        val c0 = colors[i]
        val c1 = colors[i + 1]
        val a = ave(Color.alpha(c0), Color.alpha(c1), p)
        val r = ave(Color.red(c0), Color.red(c1), p)
        val g = ave(Color.green(c0), Color.green(c1), p)
        val b = ave(Color.blue(c0), Color.blue(c1), p)

        return Color.argb(a, r, g, b)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val cx = getWidth()
        val cy = getHeight()

        val y: Float
        val x: Float

        if (horizontal) {
            // rotate coordinate system - it's simpler than handling it through all code
            y = event.x - (cx - 45) / 2
            x = cy / 2 - event.y
        } else {
            x = event.x - cx / 2
            y = event.y - (cy - 45) / 2
        }

        var cyAdjusted = cy
        if (!horizontal) cyAdjusted -= 45

        val inCenter = sqrt(x * x + y * y) <= radius
        val inPeeker = sqrt(x * x + y * y) <= width

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                mTrackingCenter = inCenter
                if (inCenter) {
                    mHighlightCenter = true
                    invalidate()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (mTrackingCenter) {
                    if (mHighlightCenter != inCenter) {
                        mHighlightCenter = inCenter
                        invalidate()
                    }
                } else if ((x >= -width && x <= width) && (y <= width + 45 && y >= width + 25)) {
                    // see if we're in the hsv slider
                    val c0: Int
                    val c1: Int
                    val p: Float

                    // set the center paint to this color
                    if (x < 0) {
                        c0 = mHSVColors[0]
                        c1 = mHSVColors[1]
                        p = (x + width) / width
                    } else {
                        c0 = mHSVColors[1]
                        c1 = mHSVColors[2]
                        p = x / width
                    }

                    val a = ave(Color.alpha(c0), Color.alpha(c1), p.toDouble())
                    val r = ave(Color.red(c0), Color.red(c1), p.toDouble())
                    val g = ave(Color.green(c0), Color.green(c1), p.toDouble())
                    val b = ave(Color.blue(c0), Color.blue(c1), p.toDouble())

                    mCenterPaint.color = Color.argb(a, r, g, b)

                    mRedrawHSV = false
                    invalidate()
                } else if (inPeeker) {
                    val angle = atan2(y.toDouble(), x.toDouble())
                    // need to turn angle [-PI ... PI] into unit [0....1]
                    var unit = angle / (2 * Math.PI)
                    if (unit < 0) {
                        unit += 1
                    }
                    mCenterPaint.color = interpColor(mColors, unit)
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                if (mTrackingCenter) {
                    if (inCenter) {
                        mListener?.colorChanged(mCenterPaint.color)
                    }
                    mTrackingCenter = false    // so we draw w/o halo
                    invalidate()
                }
            }
        }
        return true
    }

    fun setOnColorChangedListener(l: OnColorChangedListener?) {
        mListener = l
    }

    companion object {
        private const val MAX_WIDTH = 300
    }

    init {
        mListener = l
        mColors = intArrayOf(
            0xFFFF0000.toInt(), 0xFFFF00FF.toInt(), 0xFF0000FF.toInt(), 0xFF00FFFF.toInt(), 0xFF00FF00.toInt(),
            0xFFFFFF00.toInt(), 0xFFFF0000.toInt()
        )
        val s = SweepGradient(0f, 0f, mColors, null)

        mPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        mPaint.shader = s
        mPaint.style = Paint.Style.STROKE
        mPaint.strokeWidth = 32f

        mCenterPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        mCenterPaint.color = color
        mCenterPaint.strokeWidth = 5f

        mHSVColors = intArrayOf(0xFF000000.toInt(), color, 0xFFFFFFFF.toInt())

        mHSVPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        mHSVPaint.strokeWidth = 10f

        mRedrawHSV = true
    }
}
