package com.googlecode.android.widgets.DateSlider.timeview

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import com.googlecode.android.widgets.DateSlider.TimeObject

open class TimeTextView(
    context: Context,
    isCenterView: Boolean,
    textSize: Int
) : TextView(context), TimeView {

    private var _endTime: Long = 0
    private var _startTime: Long = 0
    private var _isOutOfBounds = false

    init {
        setupView(isCenterView, textSize)
    }

    open fun setupView(isCenterView: Boolean, textSize: Int) {
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, textSize.toFloat())
        if (isCenterView) {
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF333333.toInt())
        } else {
            setTextColor(0xFF666666.toInt())
        }
    }

    override fun setVals(to: TimeObject) {
        text = to.text
        this._startTime = to.startTime
        this._endTime = to.endTime
    }

    override fun setVals(other: TimeView) {
        text = other.getTimeText()
        _startTime = other.getStartTime()
        _endTime = other.getEndTime()
    }

    override fun getStartTime(): Long {
        return this._startTime
    }

    override fun getEndTime(): Long {
        return this._endTime
    }

    override fun getTimeText(): String {
        return text.toString()
    }

    override fun isOutOfBounds(): Boolean {
        return _isOutOfBounds
    }

    override fun setOutOfBounds(outOfBounds: Boolean) {
        if (outOfBounds && !_isOutOfBounds) {
            setTextColor(0x44666666.toInt())
        } else if (!outOfBounds && _isOutOfBounds) {
            setTextColor(0xFF666666.toInt())
        }
        _isOutOfBounds = outOfBounds
    }

    override fun setGravity(layout: Int) {
        // inherited from View
    }
}
