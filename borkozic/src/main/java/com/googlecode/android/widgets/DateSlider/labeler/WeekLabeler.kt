package com.googlecode.android.widgets.DateSlider.labeler

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import com.googlecode.android.widgets.DateSlider.TimeObject
import com.googlecode.android.widgets.DateSlider.timeview.TimeTextView
import com.googlecode.android.widgets.DateSlider.timeview.TimeView
import java.util.Calendar

/**
 * A customized Labeler that displays weeks using a CustomTimeTextView
 */
class WeekLabeler(formatString: String) : Labeler(120, 60) {
    private val mFormatString: String

    init {
        mFormatString = formatString
    }

    override fun add(time: Long, `val`: Int): TimeObject {
        return timeObjectfromCalendar(Util.addWeeks(time, `val`))
    }

    /**
     * We implement this as custom code rather than a method in Util because there
     * is no format string that shows the week of the year as an integer, so we just
     * format the week directly rather than extracting it from a Calendar object.
     */
    override fun timeObjectfromCalendar(c: Calendar): TimeObject {
        val week = c[Calendar.WEEK_OF_YEAR]
        val dayOfWeek = c[Calendar.DAY_OF_WEEK] - 1
        // set calendar to first millisecond of the week
        c.add(Calendar.DAY_OF_MONTH, -dayOfWeek)
        c[Calendar.HOUR_OF_DAY] = 0
        c[Calendar.MINUTE] = 0
        c[Calendar.SECOND] = 0
        c[Calendar.MILLISECOND] = 0
        val startTime = c.timeInMillis
        // set calendar to last millisecond of the week
        c.add(Calendar.DAY_OF_WEEK, 6)
        c[Calendar.HOUR_OF_DAY] = 23
        c[Calendar.MINUTE] = 59
        c[Calendar.SECOND] = 59
        c[Calendar.MILLISECOND] = 999
        val endTime = c.timeInMillis
        return TimeObject(String.format(mFormatString, week), startTime, endTime)
    }

    /**
     * create our customized TimeTextView and return it
     */
    override fun createView(context: Context, isCenterView: Boolean): TimeView {
        return CustomTimeTextView(context, isCenterView, 25)
    }

    /**
     * Here we define our Custom TimeTextView which will display the fonts in its very own way.
     */
    private class CustomTimeTextView(context: Context, isCenterView: Boolean, textSize: Int) :
        TimeTextView(context, isCenterView, textSize) {

        /**
         * Here we set up the text characteristics for the TextView, i.e. red colour,
         * serif font and semi-transparent white background for the centerView... and shadow!!!
         */
        override fun setupView(isCenterView: Boolean, textSize: Int) {
            gravity = Gravity.CENTER
            setTextColor(0xFF883333.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, textSize.toFloat())
            typeface = Typeface.SERIF
            if (isCenterView) {
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                setBackgroundColor(0x55FFFFFF)
                setShadowLayer(2.5f, 3f, 3f, 0xFF999999.toInt())
            }
        }
    }
}
