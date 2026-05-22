package com.googlecode.android.widgets.DateSlider

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import java.util.Calendar

/**
 * This is a container class for ScrollLayouts. It coordinates the scrolling
 * between them, so that if one is scrolled, the others are scrolled to
 * keep a consistent display of the time. It also notifies an optional
 * observer anytime the time is changed.
 */
class SliderContainer(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {
    private var mTime: Calendar? = null
    val time: Calendar get() = mTime!!
    private var mOnTimeChangeListener: OnTimeChangeListener? = null
    var minuteInterval: Int = 0
        private set

    init {
        orientation = VERTICAL
    }

    override fun onFinishInflate() {
        val childCount = childCount
        for (i in 0 until childCount) {
            val v = getChildAt(i)
            if (v is ScrollLayout) {
                v.setOnScrollListener { x ->
                    this.mTime!!.timeInMillis = x
                    arrangeScrollers(v)
                }
            }
        }
    }

    /**
     * Set the current time and update all of the child ScrollLayouts accordingly.
     */
    fun setTime(calendar: Calendar) {
        mTime = Calendar.getInstance(calendar.timeZone)
        mTime!!.timeInMillis = calendar.timeInMillis
        arrangeScrollers(null)
    }

    /**
     * Get the current time
     */

    /**
     * sets the minimum date that the scroller can scroll
     */
    @JvmOverloads
    fun setMinTime(c: Calendar) {
        if (mTime == null) {
            throw RuntimeException("You have to call setTime before setting a MinimumTime!")
        }
        val childCount = childCount
        for (i in 0 until childCount) {
            val v = getChildAt(i)
            if (v is ScrollLayout) {
                v.setMinTime(c.timeInMillis)
            }
        }
    }

    /**
     * sets the maximum date that the scroller can scroll
     */
    @JvmOverloads
    fun setMaxTime(c: Calendar) {
        if (mTime == null) {
            throw RuntimeException("You have to call setTime before setting a MinimumTime!")
        }
        val childCount = childCount
        for (i in 0 until childCount) {
            val v = getChildAt(i)
            if (v is ScrollLayout) {
                v.setMaxTime(c.timeInMillis)
            }
        }
    }

    /**
     * sets the minute interval of the scroll layouts.
     */
    fun setMinuteInterval(minInterval: Int) {
        this.minuteInterval = minInterval
        val childCount = childCount
        for (i in 0 until childCount) {
            val v = getChildAt(i)
            if (v is ScrollLayout) {
                v.setMinuteInterval(minInterval)
            }
        }
    }

    /**
     * Sets the OnTimeChangeListener, which will be notified anytime the time is
     * set or changed.
     */
    fun setOnTimeChangeListener(l: OnTimeChangeListener?) {
        mOnTimeChangeListener = l
    }

    /**
     * Pushes our current time into all child ScrollLayouts, except the source
     * of the time change (if specified)
     */
    private fun arrangeScrollers(source: ScrollLayout?) {
        val childCount = childCount
        for (i in 0 until childCount) {
            val v = getChildAt(i)
            if (v === source) {
                continue
            }
            if (v is ScrollLayout) {
                v.setTime(mTime!!.timeInMillis)
            }
        }

        if (mOnTimeChangeListener != null) {
            if (minuteInterval > 1) {
                val minute = mTime!!.get(Calendar.MINUTE) / minuteInterval * minuteInterval
                mTime!!.set(Calendar.MINUTE, minute)
            }
            mOnTimeChangeListener!!.onTimeChange(time)
        }
    }

    interface OnTimeChangeListener {
        fun onTimeChange(time: Calendar)
    }
}
