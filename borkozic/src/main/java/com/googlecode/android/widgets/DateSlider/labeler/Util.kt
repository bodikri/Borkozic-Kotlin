package com.googlecode.android.widgets.DateSlider.labeler

import com.googlecode.android.widgets.DateSlider.TimeObject
import java.util.Calendar

/**
 * Static helpers for manipulating dates and times.
 */
internal object Util {
    @JvmStatic
    fun addYears(time: Long, years: Int): Calendar = add(time, years, Calendar.YEAR)

    @JvmStatic
    fun addMonths(time: Long, months: Int): Calendar = add(time, months, Calendar.MONTH)

    @JvmStatic
    fun addWeeks(time: Long, days: Int): Calendar = add(time, days, Calendar.WEEK_OF_YEAR)

    @JvmStatic
    fun addDays(time: Long, days: Int): Calendar = add(time, days, Calendar.DAY_OF_MONTH)

    @JvmStatic
    fun addHours(time: Long, hours: Int): Calendar = add(time, hours, Calendar.HOUR_OF_DAY)

    @JvmStatic
    fun addMinutes(time: Long, minutes: Int): Calendar = add(time, minutes, Calendar.MINUTE)

    @JvmStatic
    fun addMinutes(time: Long, minutes: Int, minInterval: Int): Calendar = add(time, minutes * minInterval, Calendar.MINUTE)

    @JvmStatic
    fun getYear(c: Calendar, formatString: String): TimeObject {
        val year = c.get(Calendar.YEAR)
        c.set(year, 0, 1, 0, 0, 0)
        c.set(Calendar.MILLISECOND, 0)
        val startTime = c.timeInMillis
        c.set(year, 11, 31, 23, 59, 59)
        c.set(Calendar.MILLISECOND, 999)
        val endTime = c.timeInMillis
        return TimeObject(String.format(formatString, c, c), startTime, endTime)
    }

    @JvmStatic
    fun getMonth(c: Calendar, formatString: String): TimeObject {
        val year = c.get(Calendar.YEAR)
        val month = c.get(Calendar.MONTH)
        c.set(year, month, 1, 0, 0, 0)
        c.set(Calendar.MILLISECOND, 0)
        val startTime = c.timeInMillis
        c.set(year, month, c.getActualMaximum(Calendar.DAY_OF_MONTH), 23, 59, 59)
        c.set(Calendar.MILLISECOND, 999)
        val endTime = c.timeInMillis
        return TimeObject(String.format(formatString, c, c), startTime, endTime)
    }

    @JvmStatic
    fun getDay(c: Calendar, formatString: String): TimeObject {
        val year = c.get(Calendar.YEAR)
        val month = c.get(Calendar.MONTH)
        val day = c.get(Calendar.DAY_OF_MONTH)
        c.set(year, month, day, 0, 0, 0)
        c.set(Calendar.MILLISECOND, 0)
        val startTime = c.timeInMillis
        c.set(year, month, day, 23, 59, 59)
        c.set(Calendar.MILLISECOND, 999)
        val endTime = c.timeInMillis
        return TimeObject(String.format(formatString, c, c), startTime, endTime)
    }

    @JvmStatic
    fun getHour(c: Calendar, formatString: String): TimeObject {
        val year = c.get(Calendar.YEAR)
        val month = c.get(Calendar.MONTH)
        val day = c.get(Calendar.DAY_OF_MONTH)
        val hour = c.get(Calendar.HOUR_OF_DAY)
        c.set(year, month, day, hour, 0, 0)
        c.set(Calendar.MILLISECOND, 0)
        val startTime = c.timeInMillis
        c.set(year, month, day, hour, 59, 59)
        c.set(Calendar.MILLISECOND, 999)
        val endTime = c.timeInMillis
        return TimeObject(String.format(formatString, c, c), startTime, endTime)
    }

    @JvmStatic
    fun getMinute(c: Calendar, formatString: String): TimeObject {
        return getMinute(c, formatString, 1)
    }

    @JvmStatic
    fun getMinute(c: Calendar, formatString: String, minInterval: Int): TimeObject {
        val year = c.get(Calendar.YEAR)
        val month = c.get(Calendar.MONTH)
        val day = c.get(Calendar.DAY_OF_MONTH)
        val hour = c.get(Calendar.HOUR_OF_DAY)
        val minute = c.get(Calendar.MINUTE)
        c.set(year, month, day, hour, kotlin.math.min(59, minute + minInterval - 1), 59)
        c.set(Calendar.MILLISECOND, 999)
        val endTime = c.timeInMillis
        c.set(year, month, day, hour, minute, 0)
        c.set(Calendar.MILLISECOND, 0)
        val startTime = c.timeInMillis
        return TimeObject(String.format(formatString, c, c), startTime, endTime)
    }

    @JvmStatic
    fun getTime(c: Calendar, formatString: String, minuteInterval: Int): TimeObject {
        val year = c.get(Calendar.YEAR)
        val month = c.get(Calendar.MONTH)
        val day = c.get(Calendar.DAY_OF_MONTH)
        val hour = c.get(Calendar.HOUR_OF_DAY)
        val minute = c.get(Calendar.MINUTE) / minuteInterval * minuteInterval
        c.set(year, month, day, hour, minute + minuteInterval - 1, 59)
        c.set(Calendar.MILLISECOND, 999)
        val endTime = c.timeInMillis
        c.set(year, month, day, hour, minute, 0)
        c.set(Calendar.MILLISECOND, 0)
        val startTime = c.timeInMillis
        return TimeObject(String.format(formatString, c, c), startTime, endTime)
    }

    private fun add(time: Long, valToAdd: Int, field: Int): Calendar {
        val c = Calendar.getInstance()
        c.timeInMillis = time
        c.add(field, valToAdd)
        return c
    }
}
