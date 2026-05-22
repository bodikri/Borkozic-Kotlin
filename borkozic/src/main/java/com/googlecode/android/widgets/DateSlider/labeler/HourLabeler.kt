package com.googlecode.android.widgets.DateSlider.labeler

import com.googlecode.android.widgets.DateSlider.TimeObject
import java.util.Calendar

/**
 * A Labeler that displays hours
 */
class HourLabeler(formatString: String) : Labeler(90, 60) {
    private val mFormatString: String

    init {
        mFormatString = formatString
    }

    override fun add(time: Long, `val`: Int): TimeObject {
        return timeObjectfromCalendar(Util.addHours(time, `val`))
    }

    override fun timeObjectfromCalendar(c: Calendar): TimeObject {
        return Util.getHour(c, mFormatString)
    }
}
