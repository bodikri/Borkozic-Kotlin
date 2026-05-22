/* The following code was written by Matthew Wiggins
 * and is released under the APACHE 2.0 license
 *
 * Redesigned, fixed bugs and made customizable by Andrey Novikov
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package com.borkozic.ui

import android.content.Context
import android.content.res.TypedArray
import android.preference.DialogPreference
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.borkozic.library.R
import java.text.DecimalFormat

/**
 * SeekbarPreference class implements seekbar [android.preference.DialogPreference] edit.
 *
 * Attributes supported:<br/>
 * `android:text` - current value display suffix (not required)<br/>
 * `android:dialogMessage` - dialog title (not required)<br/>
 *
 * Styled attributes supported:<br/>
 * `min` - minimum value, integer, default 0<br/>
 * `max` - maximum value, integer, default 100<br/>
 * `defaultValue` - default value, integer, default 0<br/>
 * `multiplier` - multiplier used for value display (note that it will not affect persisted value), default 1<br/>
 *
 * `format` - format of value display, suitable for [java.text.DecimalFormat], default "0"
 *
 * @author Andrey Novikov
 */
open class SeekbarPreference(context: Context, attrs: AttributeSet) :
    DialogPreference(context, attrs), SeekBar.OnSeekBarChangeListener {

    private val androidns = "http://schemas.android.com/apk/res/android"

    private var mSeekBar: SeekBar? = null
    private var mSplashText: TextView? = null
    private var mValueText: TextView? = null

    private var mDialogMessage: String? = null
    private var mSuffix: String? = null
    private var mDefault: Int = 0
    private var mMin: Int = 0
    private var mMax: Int = 100
    private var mValue: Int = 0
    private var mStep: Int = 0
    private var mMultiplier: Float = 1.0f
    private var format: DecimalFormat

    init {
        mDialogMessage = attrs.getAttributeValue(androidns, "dialogMessage")
        mSuffix = attrs.getAttributeValue(androidns, "text")

        // max and default values are in private namespace because values from integer resource where
        // incorrectly processed when specified in android namespace
        val sattrs: TypedArray = context.obtainStyledAttributes(attrs, R.styleable.SeekbarPreference)
        mDefault = sattrs.getInt(R.styleable.SeekbarPreference_defaultValue, 0)
        mMin = sattrs.getInt(R.styleable.SeekbarPreference_min, 0)
        mMax = sattrs.getInt(R.styleable.SeekbarPreference_max, 100)
        mStep = sattrs.getInt(R.styleable.SeekbarPreference_step, 1)
        mMultiplier = sattrs.getFloat(R.styleable.SeekbarPreference_multiplier, 1f)
        var fmt = sattrs.getString(R.styleable.SeekbarPreference_format)
        if (fmt == null)
            fmt = "0"
        format = DecimalFormat(fmt)
        sattrs.recycle()
    }

    override fun onBindView(view: View) {
        super.onBindView(view)
        getValue()
    }

    override fun onCreateDialogView(): View {
        val params: LinearLayout.LayoutParams
        val layout = LinearLayout(context)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(6, 6, 6, 6)

        if (mDialogMessage != null) {
            mSplashText = TextView(context)
            mSplashText!!.text = mDialogMessage
            layout.addView(mSplashText)
        }

        mValueText = TextView(context)
        mValueText!!.gravity = Gravity.CENTER_HORIZONTAL
        mValueText!!.textSize = 26f
        params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        layout.addView(mValueText, params)

        mSeekBar = SeekBar(context)
        layout.addView(mSeekBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        if (isPersistent)
            mValue = getPersistedInt(mDefault)

        mSeekBar!!.max = mMax - mMin
        setProgress(mValue - mMin)
        mSeekBar!!.setOnSeekBarChangeListener(this)
        return layout
    }

    override fun onBindDialogView(v: View) {
        super.onBindDialogView(v)
        mSeekBar!!.max = mMax - mMin
        setProgress(mValue - mMin)
    }

    override fun onSetInitialValue(restore: Boolean, defaultValue: Any?) {
        super.onSetInitialValue(restore, defaultValue)
        if (restore)
            mValue = getPersistedInt(mValue)
        else
            mValue = defaultValue as Int
        if (shouldPersist()) {
            persistInt(mValue)
        }
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        if (positiveResult) {
            if (callChangeListener(mValue) && shouldPersist())
                persistInt(mValue)
        }
    }

    /**
     * Called when user changes progress value, stores value in persistent storage if applicable
     */
    override fun onProgressChanged(seek: SeekBar, value: Int, fromTouch: Boolean) {
        mValue = mStep * ((value + mMin) / mStep)
        mValueText?.text = getText(mValue)
    }

    override fun onStartTrackingTouch(seek: SeekBar) {}

    override fun onStopTrackingTouch(seek: SeekBar) {}

    /**
     * Sets real maximum possible value
     * @param max new maximum
     */
    fun setMax(max: Int) {
        mMax = max
    }

    /**
     * Returns real maximum possible value
     * @return maximum value
     */
    fun getMax(): Int {
        return mMax
    }

    /**
     * Sets real minimum possible value
     * @param min new minimum
     */
    fun setMin(min: Int) {
        mMin = min
    }

    /**
     * Returns real minimum possible value
     * @return minimum value
     */
    fun getMin(): Int {
        return mMin
    }

    /**
     * Sets fake progress (for internal use)
     * @param progress fake progress
     */
    fun setProgress(progress: Int) {
        val value = progress + mMin
        mSeekBar?.setProgress(progress)
        mValueText?.text = getText(value)
    }

    fun getValue(): Int {
        if (isPersistent) {
            mValue = getPersistedInt(mDefault)
        }
        return mValue
    }

    fun getText(): String {
        return getText(getValue())
    }

    private fun getText(value: Int): String {
        var t = format.format(value * mMultiplier)
        if (mSuffix != null)
            t = t.plus(mSuffix)
        return t
    }

    /**
     * Returns fake progress for internal use
     * @return progress
     */
    fun getProgress(): Int {
        return mValue - mMin
    }
}
