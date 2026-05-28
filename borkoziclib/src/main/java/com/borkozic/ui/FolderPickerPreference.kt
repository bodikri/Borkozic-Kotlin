/*
 * Borkozic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 *
 * This file is part of Borkozic application.
 *
 * Borkozic is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * Borkozic is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with Borkozic.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.borkozic.ui

import android.app.AlertDialog
import android.content.Context
import android.content.res.TypedArray
import android.os.Environment
import android.util.AttributeSet
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.preference.Preference
import java.io.File
import java.io.FileFilter

open class FolderPickerPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs),
    AdapterView.OnItemClickListener {

    private val mDialogMessage: String? = attrs?.getAttributeValue("http://schemas.android.com/apk/res/android", "dialogMessage")
    private var mCurrentValue: String = ""
    private var mCurrentFolder: String = ""
    private var mValueText: TextView? = null
    private var mFolderList: ListView? = null
    private var mDialog: AlertDialog? = null

    init {
        setOnPreferenceClickListener {
            showFolderPickerDialog()
            true
        }
    }

    private fun showFolderPickerDialog() {
        if (this.isPersistent)
            mCurrentValue = getPersistedString("")

        if (mCurrentValue.isNotEmpty()) {
            val f = File(mCurrentValue)
            if (f.exists() && f.isDirectory) {
                mCurrentFolder = mCurrentValue
            } else {
                mCurrentFolder = Environment.getExternalStorageDirectory().absolutePath
            }
        } else {
            mCurrentFolder = Environment.getExternalStorageDirectory().absolutePath
        }

        val layout = LinearLayout(context)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(6, 6, 6, 6)

        if (mDialogMessage != null) {
            val dialogText = TextView(context)
            dialogText.text = mDialogMessage
            dialogText.setPadding(0, 0, 0, 12)
            layout.addView(dialogText)
        }

        mValueText = TextView(context)
        mValueText!!.textSize = 16f
        layout.addView(mValueText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        mFolderList = ListView(context)
        layout.addView(mFolderList, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        mFolderList!!.onItemClickListener = this

        // SELECT FOLDER button
        val selectBtn = Button(context)
        selectBtn.text = "SELECT FOLDER"
        selectBtn.setOnClickListener {
            if (mCurrentFolder.isNotEmpty()) {
                mCurrentValue = mCurrentFolder
                onPositiveResult()
                mDialog?.dismiss()
            }
        }
        layout.addView(selectBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        mDialog = AlertDialog.Builder(context)
            .setTitle(title)
            .setView(layout)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (mCurrentFolder.isNotEmpty()) {
                    mCurrentValue = mCurrentFolder
                    onPositiveResult()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        populateList()
        mDialog?.show()
    }

    private fun onPositiveResult() {
        if (callChangeListener(mCurrentValue) && shouldPersist())
            persistString(mCurrentValue)
        notifyChanged()
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
            mCurrentValue = ""
    }

    override fun getSummary(): CharSequence {
        val saved = getPersistedString("")
        return if (saved.isNullOrEmpty()) super.getSummary() ?: "" else saved
    }

    private fun populateList() {
        val folder = File(mCurrentFolder)
        if (!folder.exists() || !folder.isDirectory) {
            mCurrentFolder = Environment.getExternalStorageDirectory().absolutePath
        }

        val currentVal = getPersistedString("")
        mValueText?.text = mCurrentFolder +
            if (currentVal.isNullOrEmpty()) ""
            else "\nSelected: $currentVal"

        val parent = if (File(mCurrentFolder).parentFile == null) 0 else 1

        val dirs = File(mCurrentFolder).listFiles(dirFilter)

        val items = mutableListOf<String>()
        if (parent > 0)
            items.add("📁 ..")
        dirs?.forEach { items.add("📁 ${it.name}") }

        val adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, android.R.id.text1, items)
        mFolderList?.adapter = adapter
    }

    override fun onItemClick(parent: AdapterView<*>, view: View, position: Int, id: Long) {
        val item = mFolderList?.getItemAtPosition(position) as? String ?: return

        when {
            item == "📁 .." -> {
                mCurrentFolder = File(mCurrentFolder).parent ?: mCurrentFolder
                populateList()
            }
            item.startsWith("📁 ") -> {
                val dirName = item.substring(3)
                mCurrentFolder = File(mCurrentFolder, dirName).absolutePath
                populateList()
            }
        }
    }

    private val dirFilter = FileFilter { pathname -> pathname.isDirectory }
}
