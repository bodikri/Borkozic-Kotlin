package com.googlecode.android.widgets.DateSlider.timeview

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.googlecode.android.widgets.DateSlider.TimeObject

open class TimeLayoutView(
    context: Context,
    isCenterView: Boolean,
    topTextSize: Int,
    bottomTextSize: Int,
    lineHeight: Float
) : LinearLayout(context), TimeView {

    private var _endTime: Long = 0
    private var _startTime: Long = 0
    var text: String = ""
    var isCenter = false
    private var _isOutOfBounds = false

    lateinit var topView: TextView
    lateinit var bottomView: TextView

    init {
        setupView(context, isCenterView, topTextSize, bottomTextSize, lineHeight)
    }

    protected fun setupView(
        context: Context,
        isCenterView: Boolean,
        topTextSize: Int,
        bottomTextSize: Int,
        lineHeight: Float
    ) {
        orientation = VERTICAL
        topView = TextView(context)
        topView.gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
        topView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, topTextSize.toFloat())
        bottomView = TextView(context)
        bottomView.gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
        bottomView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, bottomTextSize.toFloat())
        topView.setLineSpacing(0f, lineHeight)
        if (isCenterView) {
            isCenter = true
            topView.typeface = Typeface.DEFAULT_BOLD
            topView.setTextColor(0xFF333333.toInt())
            bottomView.typeface = Typeface.DEFAULT_BOLD
            bottomView.setTextColor(0xFF444444.toInt())
            topView.setPadding(0, 5 - (topTextSize / 15.0f).toInt(), 0, 0)
        } else {
            topView.setPadding(0, 5, 0, 0)
            topView.setTextColor(0xFF666666.toInt())
            bottomView.setTextColor(0xFF666666.toInt())
        }
        addView(topView)
        addView(bottomView)
    }

    override fun setVals(to: TimeObject) {
        text = to.text.toString()
        setText()
        this._startTime = to.startTime
        this._endTime = to.endTime
    }

    override fun setVals(other: TimeView) {
        text = other.getTimeText().toString()
        setText()
        _startTime = other.getStartTime()
        _endTime = other.getEndTime()
    }

    protected fun setText() {
        val splitTime = text.split(" ")
        topView.text = splitTime[0]
        bottomView.text = splitTime[1]
    }

    override fun getTimeText(): String {
        return text
    }

    override fun getStartTime(): Long {
        return _startTime
    }

    override fun getEndTime(): Long {
        return _endTime
    }

    override fun isOutOfBounds(): Boolean {
        return _isOutOfBounds
    }

    override fun setOutOfBounds(outOfBounds: Boolean) {
        if (outOfBounds && !_isOutOfBounds) {
            topView.setTextColor(0x44666666.toInt())
            bottomView.setTextColor(0x44666666.toInt())
        } else if (!outOfBounds && _isOutOfBounds) {
            topView.setTextColor(0xFF666666.toInt())
            bottomView.setTextColor(0xFF666666.toInt())
        }
        _isOutOfBounds = outOfBounds
    }

    override fun setGravity(layout: Int) {
        // inherited from View
    }
}
