package com.googlecode.android.widgets.DateSlider.labeler

import android.content.Context
import com.googlecode.android.widgets.DateSlider.timeview.DayTimeLayoutView
import com.googlecode.android.widgets.DateSlider.timeview.TimeView

/**
 * A Labeler that displays days using DayTimeLayoutViews.
 */
class DayDateLabeler(formatString: String) : DayLabeler(formatString) {
    /**
     * The format string that specifies how to display the day. Since this class
     * uses a DayTimeLayoutView, the format string should consist of two strings
     * separated by a space.
     *
     * @param formatString
     */
    
    override fun createView(context: Context, isCenterView: Boolean): TimeView {
        return DayTimeLayoutView(context, isCenterView, 30, 8, 0.8f)
    }
}