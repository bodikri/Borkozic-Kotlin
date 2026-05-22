/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2012  Andrey Novikov <http://andreynovikov.info/>
 *
 * This file is part of Androzic application.
 *
 * Androzic is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * Androzic is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with Androzic.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.borkozic.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.util.AttributeSet
import android.widget.Button

class ColorButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = android.R.attr.buttonStyle
) : Button(context, attrs, defStyle) {

    private val mDensity: Float = context.resources.displayMetrics.density
    private var mColor: Int = 0
    private var mDefColor: Int = 0
    private var mAlpha: Int = 0
    private var mColorChangedListener: OnColorChangedListener? = null

    init {
        setCompoundDrawablePadding((mDensity * 5).toInt())
        setCompoundDrawablesWithIntrinsicBounds(getPreviewBitmap(), null, null, null)
        setOnClickListener {
            mAlpha = mColor or 0x00FFFFFF
            ColorPickerDialog(
                context,
                onColorChangedListener,
                mColor or 0xFF000000.toInt(),
                mDefColor,
                true
            ).show()
        }
    }

    fun setOnColorChangeListener(listener: OnColorChangedListener?) {
        mColorChangedListener = listener
    }

    fun setColor(color: Int, defcolor: Int) {
        mColor = color
        mDefColor = defcolor
        setCompoundDrawablesWithIntrinsicBounds(getPreviewBitmap(), null, null, null)
    }

    fun getColor(): Int {
        return mColor
    }

    private fun getPreviewBitmap(): BitmapDrawable {
        val d = (mDensity * 33).toInt()
        val color = mColor
        val bm = Bitmap.createBitmap(d, d, Bitmap.Config.ARGB_8888)
        val w = bm.width
        val h = bm.height
        var c: Int
        for (i in 0 until w) {
            for (j in i until h) {
                c = if (i <= 1 || j <= 1 || i >= w - 2 || j >= h - 2) Color.GRAY else color
                bm.setPixel(i, j, c)
                if (i != j) {
                    bm.setPixel(j, i, c)
                }
            }
        }

        val b = Bitmap.createBitmap(d, d, Bitmap.Config.ARGB_8888)
        val bc = Canvas(b)
        val drw = AlphaPatternDrawable((5 * mDensity).toInt())
        drw.setBounds(0, 0, d, d)
        drw.draw(bc)
        bc.drawBitmap(bm, null, Rect(0, 0, d, d), null)

        return BitmapDrawable(resources, b)
    }

    private val onColorChangedListener = object : OnColorChangedListener {
        override fun colorChanged(newColor: Int) {
            val newColorMasked = newColor and mAlpha
            setColor(newColorMasked, mDefColor)
            mColorChangedListener?.colorChanged(newColorMasked)
        }
    }
}
