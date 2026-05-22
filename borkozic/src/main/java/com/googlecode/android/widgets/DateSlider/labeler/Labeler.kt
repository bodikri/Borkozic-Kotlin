package com.googlecode.android.widgets.DateSlider.labeler

import android.content.Context
import com.googlecode.android.widgets.DateSlider.TimeObject
import com.googlecode.android.widgets.DateSlider.timeview.TimeTextView
import com.googlecode.android.widgets.DateSlider.timeview.TimeView
import java.util.Calendar

/**
 * Abstract class for creating TimeViews and generating TimeObjects from times.
 */
abstract class Labeler(
    private val viewWidthDP: Int,
    private val viewHeightDP: Int
) {
    var minuteInterval = 1

    /**
     * Converts from a time to a TimeObject according to the rules of this labeler.
     */
    open fun getElem(time: Long): TimeObject {
        val c = Calendar.getInstance()
        c.timeInMillis = time
        return timeObjectfromCalendar(c)
    }

    /**
     * Returns a new TimeView instance appropriate for population using TimeObjects.
     */
    open fun createView(context: Context, isCenterView: Boolean): TimeView {
        return TimeTextView(context, isCenterView, 25)
    }

    /**
     * Add time units to the specified time.
     */
    abstract fun add(time: Long, valToAdd: Int): TimeObject

    /**
     * Convert a Calendar to a TimeObject.
     */
    protected abstract fun timeObjectfromCalendar(c: Calendar): TimeObject

    /**
     * Preferred width of TimeViews in pixels.
     */
    fun getPreferredViewWidth(context: Context): Int {
        return (viewWidthDP * context.resources.displayMetrics.density).toInt()
    }

    /**
     * Preferred height of TimeViews in pixels.
     */
    fun getPreferredViewHeight(context: Context): Int {
        return (viewHeightDP * context.resources.displayMetrics.density).toInt()
    }


}
