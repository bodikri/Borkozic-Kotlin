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
import android.content.res.TypedArray
import android.os.Environment
import android.preference.DialogPreference
import android.util.AttributeSet
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import java.io.File
import java.io.FileFilter
import java.util.Arrays

class FolderPickerPreference(context: Context, attrs: AttributeSet?) : DialogPreference(context, attrs),
    AdapterView.OnItemClickListener {

    private var mCurrentValue: String = ""
    private var mValueText: TextView? = null
    private var mFolderList: ListView? = null

    override fun onCreateDialogView(): View {
        val layout = LinearLayout(context)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(6, 6, 6, 6)

        if (dialogMessage != null) {
            val dialogText = TextView(context)
            dialogText.text = dialogMessage
            dialogText.setPadding(0, 0, 0, 12)
            layout.addView(dialogText)
        }

        mValueText = TextView(context)
        mValueText!!.textSize = 26f
        layout.addView(
            mValueText, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        mFolderList = ListView(context)
        layout.addView(
            mFolderList, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        )
        mFolderList!!.onItemClickListener = this

        if (this.isPersistent)
            mCurrentValue = getPersistedString("")

        populateList()

        return layout
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any {
        return a.getString(index) ?: ""
    }

    override fun onSetInitialValue(restore: Boolean, defaultValue: Any?) {
        if (restore)
            mCurrentValue = getPersistedString(mCurrentValue)
        else
            mCurrentValue = defaultValue as? String ?: ""

        if (mCurrentValue.isNullOrEmpty())
            mCurrentValue = Environment.getExternalStorageDirectory().toString()
        var def = File(mCurrentValue)
        if (!def.isAbsolute) {
            def = File(Environment.getExternalStorageDirectory(), mCurrentValue)
            mCurrentValue = def.absolutePath
        }
        if (shouldPersist()) {
            persistString(mCurrentValue)
        }
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        if (!positiveResult)
            return
        if (callChangeListener(mCurrentValue) && shouldPersist())
            persistString(mCurrentValue)

        notifyChanged()
    }

    override fun getSummary(): CharSequence {
        val summary = super.getSummary()
        return summary?.toString() ?: getPersistedString(mCurrentValue)
    }

    private fun populateList() {
        var initial = File(mCurrentValue)
        if (!initial.exists() || !initial.isDirectory) {
            initial = Environment.getExternalStorageDirectory()
            mCurrentValue = initial.absolutePath
        }

        mValueText?.text = initial.absolutePath

        val parent = if (initial.parentFile == null) 0 else 1

        val dirs = initial.listFiles(dirFilter)
        val length = dirs?.size ?: 0
        val folders = arrayOfNulls<String>(length + parent)
        if (parent > 0)
            folders[0] = ".."
        for (i in 0 until length) {
            folders[i + parent] = dirs!![i].name
        }
        Arrays.sort(folders)
        val folderAdapter = ArrayAdapter(
            context,
            android.R.layout.simple_list_item_1,
            android.R.id.text1,
            folders.requireNoNulls()
        )
        mFolderList?.adapter = folderAdapter
    }

    override fun onItemClick(parent: AdapterView<*>, view: View, position: Int, id: Long) {
        val folder = mFolderList?.getItemAtPosition(position) as? String ?: return
        val newFolder: File? = if (".." == folder) {
            File(mCurrentValue).parentFile
        } else {
            File(mCurrentValue, folder)
        }
        newFolder?.let {
            mCurrentValue = it.absolutePath
            populateList()
        }
    }

    private val dirFilter = FileFilter { pathname -> pathname.isDirectory }
}
