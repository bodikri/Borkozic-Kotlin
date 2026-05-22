package com.googlecode.android.widgets.DateSlider.labeler

import android.util.Log
import com.googlecode.android.widgets.DateSlider.TimeObject
import java.util.Calendar

/**
 * A Labeler that displays times in increments of [MINUTEINTERVAL] minutes.
 */
class TimeLabeler(formatString: String) : Labeler(80, 60) {
    private val mFormatString: String

    companion object {
        @JvmField
        var MINUTEINTERVAL = 15
    }

    init {
        mFormatString = formatString
    }

    override fun add(time: Long, `val`: Int): TimeObject {
        return timeObjectfromCalendar(Util.addMinutes(time, `val` * MINUTEINTERVAL))
    }

    /**
     * override this method to set the initial TimeObject to a multiple of MINUTEINTERVAL
     */
    override fun getElem(time: Long): TimeObject {
        val c = Calendar.getInstance()
        c.timeInMillis = time
        c[Calendar.MINUTE] = c[Calendar.MINUTE] / MINUTEINTERVAL * MINUTEINTERVAL
        Log.v("GETELEM", "getelem: " + c[Calendar.MINUTE])
        return timeObjectfromCalendar(c)
    }

    override fun timeObjectfromCalendar(c: Calendar): TimeObject {
        return Util.getTime(c, mFormatString, MINUTEINTERVAL)
    }
}
