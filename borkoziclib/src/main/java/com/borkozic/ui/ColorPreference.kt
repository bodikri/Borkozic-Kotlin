package com.borkozic.ui

/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Portions of this file have been derived from code originally licensed
 * under the Apache License, Version 2.0.
 * 
 * Changes made by Christopher McCurdy, 2009.
 * Fixes and enhancements by Andrey Novikov, 2010.
 */

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.content.res.TypedArray
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.preference.DialogPreference
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.borkozic.library.R

class ColorPreference : DialogPreference {

    private var mCurrentColor: Int = 0
    private var mDefaultColor: Int = Color.TRANSPARENT
    private var mAlpha: Int = 0
    private var mDensity: Float = 0f
    private var mCPView: ColorPickerView? = null
    private var mView: View? = null

    @JvmOverloads
    constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) : super(context, attrs, defStyle) {
        val a = context.obtainStyledAttributes(attrs, R.styleable.ColorPreference)
        mDefaultColor = a.getColor(R.styleable.ColorPreference_defaultColor, Color.TRANSPARENT)
        mDensity = context.resources.displayMetrics.density
        a.recycle()
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any {
        return a.getColor(index, mDefaultColor)
    }

    override fun onSetInitialValue(restoreValue: Boolean, defaultValue: Any?) {
        val color = if (restoreValue) value else (defaultValue as Int)
        mAlpha = color or 0x00FFFFFF.toInt()
        onColorChanged(color)
    }

    override fun onBindView(view: View) {
        super.onBindView(view)
        mView = view
        setPreviewColor()
    }

    private fun setPreviewColor() {
        val view = mView ?: return
        val iView = ImageView(context)
        val widgetFrameView = view.findViewById<ViewGroup>(android.R.id.widget_frame) ?: return
        widgetFrameView.visibility = View.VISIBLE
        val rightPaddingDip = if (android.os.Build.VERSION.SDK_INT < 14) 8 else 5
        widgetFrameView.setPadding(
            widgetFrameView.paddingLeft,
            widgetFrameView.paddingTop,
            (mDensity * rightPaddingDip).toInt(),
            widgetFrameView.paddingBottom
        )
        // remove already created preview images
        val count = widgetFrameView.childCount
        if (count > 0) {
            widgetFrameView.removeViews(0, count)
        }
        widgetFrameView.addView(iView)
        iView.setImageBitmap(previewBitmap)
    }

    private val previewBitmap: Bitmap
        get() {
            val d = (mDensity * 31).toInt() // 30dip
            val color = value
            val bm = Bitmap.createBitmap(d, d, Bitmap.Config.ARGB_8888)
            val w = bm.width
            val h = bm.height
            for (i in 0 until w) {
                for (j in i until h) {
                    val c = if (i <= 1 || j <= 1 || i >= w - 2 || j >= h - 2) Color.GRAY else color
                    bm.setPixel(i, j, c)
                    if (i != j) {
                        bm.setPixel(j, i, c)
                    }
                }
            }

            val b = Bitmap.createBitmap(d, d, Bitmap.Config.ARGB_8888)
            val bc = Canvas(b)
            val drw: Drawable = AlphaPatternDrawable((5 * mDensity).toInt())
            drw.setBounds(0, 0, d, d)
            drw.draw(bc)
            bc.drawBitmap(bm, null, Rect(0, 0, d, d), null)

            return b
        }

    val value: Int
        get() {
            try {
                if (isPersistent) {
                    mCurrentColor = getPersistedInt(mDefaultColor)
                }
            } catch (e: ClassCastException) {
                mCurrentColor = mDefaultColor
            }
            return mCurrentColor
        }

    fun onColorChanged(color: Int) {
        val c = color and mAlpha
        if (callChangeListener(c) && shouldPersist())
            persistInt(c)
        mCurrentColor = c
        setPreviewColor()
        try {
            onPreferenceChangeListener?.onPreferenceChange(this, c)
        } catch (_: NullPointerException) {
        }
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        if (positiveResult) {
            mCPView?.let { onColorChanged(it.getColor()) }
        }
    }

    override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
        super.onPrepareDialogBuilder(builder)

        val l = object : OnColorChangedListener {
            override fun colorChanged(color: Int) {
                onDialogClosed(true)
                dialog?.dismiss()
            }
        }

        val prefs: SharedPreferences = preferenceManager.sharedPreferences
        var initialColor = prefs.getInt(key, mDefaultColor)
        mAlpha = initialColor or 0x00FFFFFF.toInt()
        initialColor = initialColor or 0xFF000000.toInt()
        mCPView = ColorPickerView(context, l, initialColor)
        builder.setView(mCPView)
    }
}
