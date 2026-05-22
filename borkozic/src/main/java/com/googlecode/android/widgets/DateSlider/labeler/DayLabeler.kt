package com.googlecode.android.widgets.DateSlider.labeler

import com.googlecode.android.widgets.DateSlider.TimeObject
import java.util.Calendar

/**
 * A Labeler that displays days
 */
open class DayLabeler(formatString: String) : Labeler(150, 60) {
    private val mFormatString: String

    init {
        mFormatString = formatString
    }

    override fun add(time: Long, `val`: Int): TimeObject {
        return timeObjectfromCalendar(Util.addDays(time, `val`))
    }

    override fun timeObjectfromCalendar(c: Calendar): TimeObject {
        return Util.getDay(c, mFormatString)
    }
}