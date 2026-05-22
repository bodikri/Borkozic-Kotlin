package com.googlecode.android.widgets.DateSlider.timeview

import com.googlecode.android.widgets.DateSlider.TimeObject

interface TimeView {
    fun setVals(to: TimeObject)
    fun setVals(other: TimeView)
    fun getTimeText(): String
    fun getStartTime(): Long
    fun getEndTime(): Long
    fun isOutOfBounds(): Boolean
    fun setOutOfBounds(outOfBounds: Boolean)
    fun setGravity(layout: Int)
}
