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

package com.borkozic.ui.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.View
import com.borkozic.library.R
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign

class HSIView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var widthHSI = 220
    private var scale = 1f

    private var compassArrow: Bitmap? = null

    private val borderPaint: Paint
    private val scalePaint: Paint
    private val navPaint: Paint
    private val textPaint: Paint
    private val errorPaint: Paint
    private val warnPaint: Paint
    private val rect30: RectF
    private val rect10: RectF
    private val rect5: RectF
    private val planePath: Path
    private val arrowPath: Path
    private val xtkPath: Path
    private val clipPath: Path
    private val bearingArrow: Path

    private var compassMode = false

    private var azimuth = 0f
    private var pitch = 0f
    private var course = 0f
    private var bearing = 0f
    private var xtk = 0f
    private var proximity = 200
    private var navigating = 0

    private var rtA = 0f
    private var rtAS = 0f
    private var rtB = 0f
    private var rtBS = 0f
    private var rtC = 0f
    private var rtCS = 0f
    private var beSmooth = true

    init {
        borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        borderPaint.style = Paint.Style.STROKE
        borderPaint.color = Color.DKGRAY
        borderPaint.strokeWidth = 5f
        scalePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        scalePaint.style = Paint.Style.FILL
        scalePaint.color = Color.LTGRAY
        scalePaint.strokeWidth = 1f
        navPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        navPaint.style = Paint.Style.FILL
        navPaint.color = Color.WHITE
        navPaint.strokeWidth = 1f
        errorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        errorPaint.style = Paint.Style.FILL
        errorPaint.color = Color.rgb(160, 0, 0)
        errorPaint.strokeWidth = 1f
        warnPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        warnPaint.style = Paint.Style.FILL
        warnPaint.color = Color.rgb(255, 80, 0)
        warnPaint.strokeWidth = 1f
        textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
        textPaint.isAntiAlias = true
        textPaint.strokeWidth = 1f
        textPaint.style = Paint.Style.FILL
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 35f
        textPaint.typeface = Typeface.DEFAULT
        textPaint.color = Color.LTGRAY

        rect30 = RectF(-4f, -widthHSI.toFloat(), 4f, -widthHSI + 16 * scale)
        rect10 = RectF(-2f, -widthHSI.toFloat(), 2f, -widthHSI + 10 * scale)
        rect5 = RectF(-1f, -widthHSI.toFloat(), 1f, -widthHSI + 5 * scale)
        planePath = Path()
        arrowPath = Path()
        xtkPath = Path()
        clipPath = Path()
        bearingArrow = Path()

        setSaveEnabled(true)
    }

    fun initialize(proximity: Int, azimuth: Float) {
        this.proximity = proximity
        this.azimuth = azimuth
        rtA = azimuth
    }

    fun setSmothing(smoothing: Boolean) {
        beSmooth = smoothing
    }

    fun setCompassMode(mode: Boolean) {
        compassMode = mode
        if (compassMode) {
            compassArrow = BitmapFactory.decodeResource(resources, R.drawable.compass_needle)
        }
    }

    fun setProximity(proximity: Int) {
        this.proximity = proximity
    }

    fun setAzimuth(azimuth: Float) {
        this.azimuth = azimuth
        if (beSmooth) {
            calcAzimuthRotation()
        } else {
            rtA = azimuth
            postInvalidate()
        }
    }

    private fun calcAzimuthRotation() {
        if (azimuth != rtA) {
            var turn = azimuth - rtA
            if (abs(turn) > 180) {
                turn -= sign(turn) * 360
            }
            if (abs(turn) > abs(rtAS) * (MAX_ROTATION_SPEED / INC_ROTATION_SPEED)) {
                rtAS += sign(turn) * INC_ROTATION_SPEED
                if (abs(rtAS) > MAX_ROTATION_SPEED) {
                    rtAS = sign(rtAS) * MAX_ROTATION_SPEED
                }
            } else if (sign(turn) != sign(rtAS)) {
                rtAS += sign(turn) * INC_ROTATION_SPEED * 2
            } else if (abs(rtAS) > INC_ROTATION_SPEED) {
                rtAS -= sign(turn) * INC_ROTATION_SPEED * 0.5f
            }
            if (abs(turn) < INC_ROTATION_SPEED) {
                rtA = azimuth
                rtAS = 0f
            } else {
                rtA += rtAS
                if (rtA >= 360) rtA -= 360
                if (rtA < 0) rtA = 360 - rtA
            }
            postInvalidate()
        }
    }

    fun setPitch(pitch: Float) {
        this.pitch = pitch
    }

    fun setCourse(course: Float) {
        this.course = course
        calcCourseRotation()
    }

    private fun calcCourseRotation() {
        if (course != rtC) {
            var turn = course - rtC
            if (abs(turn) > 180) {
                turn -= sign(turn) * 360
            }
            if (abs(turn) > abs(rtCS) * (MAX_ROTATION_SPEED / INC_ROTATION_SPEED)) {
                rtCS += sign(turn) * INC_ROTATION_SPEED
                if (abs(rtCS) > MAX_ROTATION_SPEED) {
                    rtCS = sign(rtCS) * MAX_ROTATION_SPEED
                }
            } else if (sign(turn) != sign(rtCS)) {
                rtCS += sign(turn) * INC_ROTATION_SPEED * 2
            } else if (abs(rtCS) > INC_ROTATION_SPEED) {
                rtCS -= sign(turn) * INC_ROTATION_SPEED * 0.5f
            }
            if (abs(turn) < INC_ROTATION_SPEED) {
                rtC = course
                rtCS = 0f
            } else {
                rtC += rtCS
                if (rtC >= 360) rtC -= 360
                if (rtC < 0) rtC = 360 - rtC
            }
            postInvalidate()
        }
    }

    fun setBearing(bearing: Float) {
        this.bearing = bearing
        calcBearingRotation()
    }

    private fun calcBearingRotation() {
        if (bearing != rtB) {
            var turn = bearing - rtB
            if (abs(turn) > 180) {
                turn -= sign(turn) * 360
            }
            if (abs(turn) > abs(rtBS) * (MAX_ROTATION_SPEED / INC_ROTATION_SPEED)) {
                rtBS += sign(turn) * INC_ROTATION_SPEED
                if (abs(rtBS) > MAX_ROTATION_SPEED) {
                    rtBS = sign(rtBS) * MAX_ROTATION_SPEED
                }
            } else if (sign(turn) != sign(rtBS)) {
                rtBS += sign(turn) * INC_ROTATION_SPEED * 2
            } else if (abs(rtBS) > INC_ROTATION_SPEED) {
                rtBS -= sign(turn) * INC_ROTATION_SPEED * 0.5f
            }
            if (abs(turn) < INC_ROTATION_SPEED) {
                rtB = bearing
                rtBS = 0f
            } else {
                rtB += rtBS
                if (rtB >= 360) rtB -= 360
                if (rtB < 0) rtB = 360 - rtB
            }
            postInvalidate()
        }
    }

    fun setXtk(xtk: Float) {
        if (this.xtk != xtk)
            postInvalidate()
        this.xtk = xtk
    }

    fun setNavigating(navigating: Int) {
        course = 0f
        bearing = 0f
        this.xtk = 0f
        this.navigating = navigating
        postInvalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = getMeasurement(widthMeasureSpec, MAX_WIDTH)
        val h = getMeasurement(heightMeasureSpec, MAX_WIDTH)

        widthHSI = if (w > h) h / 2 - 20 else w / 2 - 20
        scale = widthHSI / 220.0f

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
        val cx = getWidth()
        val cy = getHeight()

        canvas.translate((cx / 2).toFloat(), (cy / 2).toFloat())
        if (compassMode) {
            canvas.scale(1f, (90 - abs(pitch)) / 90)
        }
        canvas.drawCircle(0f, 0f, widthHSI + 10 * scale, borderPaint)

        if (!compassMode) {
            // plane
            canvas.drawPath(planePath, scalePaint)
        }

        canvas.rotate(-rtA)

        // scale
        for (i in 72 downTo 1) {
            if (i % 2 == 1)
                canvas.drawRect(rect5, scalePaint)
            if (i % 6 == 0) {
                canvas.drawRect(rect30, scalePaint)
                if (i % 18 == 0) {
                    val cd = when (i) {
                        72 -> "N"
                        54 -> "E"
                        36 -> "S"
                        else -> "W"
                    }
                    canvas.drawText(cd, 0f, -widthHSI + 80 * scale, textPaint)
                } else {
                    canvas.drawText(((72 - i) / 2).toString(), 0f, -widthHSI + 80 * scale, textPaint)
                }
            } else if (i % 2 == 0) {
                canvas.drawRect(rect10, scalePaint)
            }
            canvas.rotate(5f)
        }
        if (compassMode) {
            compassArrow?.let {
                canvas.drawBitmap(it, -it.width / 2f, -it.height / 2f, null)
            }
        }

        canvas.save()

        if (!compassMode) {
            canvas.rotate(if (navigating == 2) rtC else rtB)
            // course arrow
            canvas.drawPath(arrowPath, navPaint)
            canvas.drawPath(xtkPath, scalePaint)

            // xtk unavailable
            if (navigating == 0)
                canvas.drawRect(-110 * scale, -50 * scale, -60 * scale, -20 * scale, errorPaint)
            else if (navigating == 1)
                canvas.drawRect(-110 * scale, -50 * scale, -60 * scale, -20 * scale, warnPaint)

            // xtk bar
            val offset = (xtk / proximity * 20 * scale).roundToInt()
            val rectXtk = RectF(-6f, -widthHSI + 124 * scale, 6f, widthHSI - 124 * scale)
            rectXtk.offset(offset.toFloat(), 0f)
            canvas.clipPath(clipPath)
            canvas.drawRect(rectXtk, navPaint)

            canvas.restore()

            canvas.rotate(rtB)
            // bearing bug
            canvas.drawPath(bearingArrow, navPaint)
        }

        if (beSmooth) {
            calcAzimuthRotation()
            if (!compassMode) {
                calcBearingRotation()
                calcCourseRotation()
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        if (w == 0 || h == 0)
            return

        textPaint.textSize = 35 * scale

        rect30.set(-4f, -widthHSI.toFloat(), 4f, -widthHSI + 16 * scale)
        rect10.set(-2f, -widthHSI.toFloat(), 2f, -widthHSI + 10 * scale)
        rect5.set(-1f, -widthHSI.toFloat(), 1f, -widthHSI + 5 * scale)

        planePath.reset()
        planePath.moveTo(0f, widthHSI - 5f)
        planePath.lineTo(0f, -widthHSI + 5f)
        planePath.close()

        arrowPath.reset()
        arrowPath.moveTo(-6f, -widthHSI + 5f)
        arrowPath.lineTo(-6f, -widthHSI + 120f * scale)
        arrowPath.lineTo(-14f, -widthHSI + 130f * scale)
        arrowPath.lineTo(0f, -widthHSI + 5f)
        arrowPath.close()
        arrowPath.moveTo(+6f, -widthHSI + 5f)
        arrowPath.lineTo(+6f, -widthHSI + 120f * scale)
        arrowPath.lineTo(+14f, -widthHSI + 130f * scale)
        arrowPath.lineTo(0f, -widthHSI + 5f)
        arrowPath.close()
        arrowPath.moveTo(-6f, widthHSI - 5f)
        arrowPath.lineTo(-6f, widthHSI - 120f * scale)
        arrowPath.lineTo(-14f, widthHSI - 130f * scale)
        arrowPath.lineTo(0f, widthHSI - 5f)
        arrowPath.close()
        arrowPath.moveTo(+6f, widthHSI - 5f)
        arrowPath.lineTo(+6f, widthHSI - 120f * scale)
        arrowPath.lineTo(+14f, widthHSI - 130f * scale)
        arrowPath.lineTo(0f, widthHSI - 5f)
        arrowPath.close()

        arrowPath.moveTo(-6f, -widthHSI + 5f)
        arrowPath.lineTo(-6f, -widthHSI + 120f * scale)
        arrowPath.lineTo(-14f, -widthHSI + 130f * scale)
        arrowPath.lineTo(0f, -widthHSI + 5f)
        arrowPath.close()
        arrowPath.moveTo(+6f, -widthHSI + 5f)
        arrowPath.lineTo(+6f, -widthHSI + 120f * scale)
        arrowPath.lineTo(+14f, -widthHSI + 130f * scale)
        arrowPath.lineTo(0f, -widthHSI + 5f)
        arrowPath.close()
        arrowPath.moveTo(-6f, widthHSI - 5f)
        arrowPath.lineTo(-6f, widthHSI - 120f * scale)
        arrowPath.lineTo(-14f, widthHSI - 130f * scale)
        arrowPath.lineTo(0f, widthHSI - 5f)
        arrowPath.close()
        arrowPath.moveTo(+6f, widthHSI - 5f)
        arrowPath.lineTo(+6f, widthHSI - 120f * scale)
        arrowPath.lineTo(+14f, widthHSI - 130f * scale)
        arrowPath.lineTo(0f, widthHSI - 5f)
        arrowPath.close()

        xtkPath.reset()
        xtkPath.addCircle(+60 * scale, 0f, 6f, Path.Direction.CW)
        xtkPath.addCircle(+120 * scale, 0f, 6f, Path.Direction.CW)
        xtkPath.addCircle(-60 * scale, 0f, 6f, Path.Direction.CW)
        xtkPath.addCircle(-120 * scale, 0f, 6f, Path.Direction.CW)

        clipPath.reset()
        clipPath.addCircle(0f, 0f, widthHSI - 90 * scale, Path.Direction.CW)

        bearingArrow.reset()
        bearingArrow.addRect(-20 * scale, -widthHSI - 20 * scale, -2f, -widthHSI - 5f, Path.Direction.CW)
        bearingArrow.addRect(+2f, -widthHSI - 20 * scale, +20 * scale, -widthHSI - 5f, Path.Direction.CW)
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is SavedState) {
            super.onRestoreInstanceState(state)
            return
        }

        val ss = state
        super.onRestoreInstanceState(ss.superState)

        setCompassMode(ss.compassMode)

        azimuth = ss.azimuth
        course = ss.course
        bearing = ss.bearing
        xtk = ss.xtk
        proximity = ss.proximity
        navigating = ss.navigating

        rtA = ss.rtA
        rtAS = ss.rtAS
        rtB = ss.rtB
        rtBS = ss.rtBS
        rtB = ss.rtB
        rtBS = ss.rtBS

        beSmooth = ss.beSmooth
    }

    override fun onSaveInstanceState(): Parcelable? {
        val superState = super.onSaveInstanceState()
        val ss = SavedState(superState)

        ss.compassMode = compassMode

        ss.azimuth = azimuth
        ss.course = course
        ss.bearing = bearing
        ss.xtk = xtk
        ss.proximity = proximity
        ss.navigating = navigating

        ss.rtA = rtA
        ss.rtAS = rtAS
        ss.rtB = rtB
        ss.rtBS = rtBS
        ss.rtB = rtB
        ss.rtBS = rtBS

        ss.beSmooth = beSmooth

        return ss
    }

    class SavedState : BaseSavedState {
        var beSmooth: Boolean = false
        var compassMode: Boolean = false

        var azimuth: Float = 0f
        var course: Float = 0f
        var bearing: Float = 0f
        var xtk: Float = 0f
        var proximity: Int = 0
        var navigating: Int = 0

        var rtA: Float = 0f
        var rtAS: Float = 0f
        var rtB: Float = 0f
        var rtBS: Float = 0f
        var rtC: Float = 0f
        var rtCS: Float = 0f

        constructor(superState: Parcelable?) : super(superState)

        private constructor(parcel: Parcel) : super(parcel) {
            this.compassMode = parcel.readInt() == 1
            this.azimuth = parcel.readFloat()
            this.course = parcel.readFloat()
            this.bearing = parcel.readFloat()
            this.xtk = parcel.readFloat()
            this.proximity = parcel.readInt()
            this.navigating = parcel.readInt()
            this.rtA = parcel.readFloat()
            this.rtAS = parcel.readFloat()
            this.rtB = parcel.readFloat()
            this.rtBS = parcel.readFloat()
            this.rtC = parcel.readFloat()
            this.rtCS = parcel.readFloat()
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeInt(if (compassMode) 1 else 0)
            out.writeFloat(this.azimuth)
            out.writeFloat(this.course)
            out.writeFloat(this.bearing)
            out.writeFloat(this.xtk)
            out.writeInt(this.proximity)
            out.writeInt(this.navigating)
            out.writeFloat(this.rtA)
            out.writeFloat(this.rtAS)
            out.writeFloat(this.rtB)
            out.writeFloat(this.rtBS)
            out.writeFloat(this.rtC)
            out.writeFloat(this.rtCS)
        }

        companion object {
            @JvmField
            val CREATOR: Parcelable.Creator<SavedState> = object : Parcelable.Creator<SavedState> {
                override fun createFromParcel(parcel: Parcel): SavedState = SavedState(parcel)
                override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
            }
        }
    }

    companion object {
        private const val MAX_WIDTH = 1000
        private const val MAX_ROTATION_SPEED = 2f
        private const val INC_ROTATION_SPEED = 0.05f
    }
}
