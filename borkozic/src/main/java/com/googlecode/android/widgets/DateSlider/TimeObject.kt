package com.googlecode.android.widgets.DateSlider

/**
 * Very simple helper class that defines a time unit with a label (text) its start-
 * and end date
 */
class TimeObject(text: CharSequence, startTime: Long, endTime: Long) {
    @JvmField
    val text: CharSequence = text
    
    @JvmField
    val startTime: Long = startTime
    
    @JvmField
    val endTime: Long = endTime
}