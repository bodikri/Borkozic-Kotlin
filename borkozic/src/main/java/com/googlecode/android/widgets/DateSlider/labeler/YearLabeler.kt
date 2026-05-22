package com.googlecode.android.widgets.DateSlider.labeler

import com.googlecode.android.widgets.DateSlider.TimeObject
import java.util.Calendar

/**
 * A Labeler that displays months
 */
open class YearLabeler(formatString: String) : Labeler(200, 60) {
    private val mFormatString: String

    init {
        mFormatString = formatString
    }

    override fun add(time: Long, `val`: Int): TimeObject {
        return timeObjectfromCalendar(Util.addYears(time, `val`))
    }

    override fun timeObjectfromCalendar(c: Calendar): TimeObject {
        return Util.getYear(c, mFormatString)
    }
}