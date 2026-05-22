package com.googlecode.android.widgets.DateSlider.labeler

import com.googlecode.android.widgets.DateSlider.TimeObject
import java.util.Calendar

/**
 * A Labeler that displays minutes
 */
class MinuteLabeler(formatString: String) : Labeler(45, 60) {
    private val mFormatString: String

    init {
        mFormatString = formatString
    }

    override fun add(time: Long, `val`: Int): TimeObject {
        return timeObjectfromCalendar(Util.addMinutes(time, `val`, minuteInterval))
    }

    override fun timeObjectfromCalendar(c: Calendar): TimeObject {
        if (minuteInterval > 1) {
            val minutes = c[Calendar.MINUTE]
            c[Calendar.MINUTE] = minutes - minutes % minuteInterval
        }
        return Util.getMinute(c, mFormatString, minuteInterval)
    }
}
