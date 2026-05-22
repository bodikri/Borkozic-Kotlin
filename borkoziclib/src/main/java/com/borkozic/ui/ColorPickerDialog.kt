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

import android.R
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle

class ColorPickerDialog(
    context: Context,
    private var mListener: OnColorChangedListener?,
    initialColor: Int,
    defaultColor: Int,
    buttons: Boolean
) : AlertDialog(context), DialogInterface.OnClickListener, OnColorChangedListener {

    private val mColorPicker: ColorPickerView

    companion object {
        private const val COLOR = "color"
    }

    init {
        mColorPicker = ColorPickerView(context, this, initialColor)
        setView(mColorPicker)

        if (buttons) {
            setButton(AlertDialog.BUTTON_POSITIVE, context.getText(R.string.ok), this)
            setButton(AlertDialog.BUTTON_NEGATIVE, context.getText(R.string.cancel), null as DialogInterface.OnClickListener?)
        }
    }

    override fun onClick(dialog: DialogInterface, which: Int) {
        mListener?.colorChanged(mColorPicker.getColor())
    }

    override fun colorChanged(color: Int) {
        onClick(this, AlertDialog.BUTTON_POSITIVE)
        dismiss()
    }

    override fun onSaveInstanceState(): Bundle {
        val state = super.onSaveInstanceState()
        state.putInt(COLOR, mColorPicker.getColor())
        return state
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        val color = savedInstanceState.getInt(COLOR)
        mColorPicker.setColor(color)
        mColorPicker.setOnColorChangedListener(this)
    }
}
