package com.googlecode.android.widgets.DateSlider.timeview

import android.content.Context
import com.googlecode.android.widgets.DateSlider.TimeObject
import java.util.Calendar

open class DayTimeLayoutView(
    context: Context,
    isCenterView: Boolean,
    topTextSize: Int,
    bottomTextSize: Int,
    lineHeight: Float
) : TimeLayoutView(context, isCenterView, topTextSize, bottomTextSize, lineHeight) {

    var isSunday = false

    override fun setVals(to: TimeObject) {
        super.setVals(to)
        val c = Calendar.getInstance()
        c.timeInMillis = to.endTime
        if (c.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY && !isSunday) {
            isSunday = true
            colorMeSunday()
        } else if (isSunday && c.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
            isSunday = false
            colorMeWorkday()
        }
    }

    protected fun colorMeSunday() {
        if (isOutOfBounds()) return
        if (isCenter) {
            bottomView.setTextColor(0xFF773333.toInt())
            topView.setTextColor(0xFF553333.toInt())
        } else {
            bottomView.setTextColor(0xFF442222.toInt())
            topView.setTextColor(0xFF553333.toInt())
        }
    }

    protected fun colorMeWorkday() {
        if (isOutOfBounds()) return
        if (isCenter) {
            topView.setTextColor(0xFF333333.toInt())
            bottomView.setTextColor(0xFF444444.toInt())
        } else {
            topView.setTextColor(0xFF666666.toInt())
            bottomView.setTextColor(0xFF666666.toInt())
        }
    }

    override fun setVals(other: TimeView) {
        super.setVals(other)
        val otherDay = other as DayTimeLayoutView
        if (otherDay.isSunday && !isSunday) {
            isSunday = true
            colorMeSunday()
        } else if (isSunday && !otherDay.isSunday) {
            isSunday = false
            colorMeWorkday()
        }
    }
}
