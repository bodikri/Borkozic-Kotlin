package com.borkozic.ui

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.res.TypedArray
import android.util.AttributeSet
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import java.util.ArrayList
import java.util.Arrays

class ImageMultiChoiceListPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : Preference(context, attrs) {
    private val SEPARATOR = ","
    private var entries: Array<CharSequence>? = null
    private var entryValues: Array<CharSequence>? = null
    private var clickedDialogEntryIndices: BooleanArray = BooleanArray(0)
    private var currentValue: String = ""

    init {
        val a: TypedArray = context.obtainStyledAttributes(attrs,
            intArrayOf(android.R.attr.entries, android.R.attr.entryValues))
        val entriesResId = a.getResourceId(0, 0)
        val entryValuesResId = a.getResourceId(1, 0)
        if (entriesResId != 0) {
            entries = context.resources.getTextArray(entriesResId)
        }
        if (entryValuesResId != 0) {
            entryValues = context.resources.getTextArray(entryValuesResId)
        }
        a.recycle()

        clickedDialogEntryIndices = BooleanArray(entries?.size ?: 0)
        currentValue = ""

        setOnPreferenceClickListener {
            showDialog()
            true
        }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        // Refresh summary on display
        if (isPersistent) {
            currentValue = getPersistedString("")
        }
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any? {
        return a.getString(index)
    }

    override fun onSetInitialValue(restore: Boolean, defaultValue: Any?) {
        if (restore) {
            currentValue = getPersistedString(currentValue)
        } else {
            currentValue = defaultValue as? String ?: ""
        }
        if (shouldPersist()) {
            persistString(currentValue)
        }
    }

    private fun showDialog() {
        val entries = entries ?: return
        val entryValues = entryValues ?: return
        if (entries.size != entryValues.size) {
            throw IllegalStateException(
                "Requires entries and entryValues of same length"
            )
        }

        if (isPersistent) currentValue = getPersistedString("")
        restoreCheckedEntries()

        AlertDialog.Builder(context)
            .setTitle(title)
            .setMultiChoiceItems(entries, clickedDialogEntryIndices) { dialog: DialogInterface?, which: Int, isChecked: Boolean ->
                clickedDialogEntryIndices[which] = isChecked
            }
            .setPositiveButton(android.R.string.ok) { dialog: DialogInterface?, which: Int ->
                val values = ArrayList<String>()
                for (i in entryValues.indices) {
                    if (clickedDialogEntryIndices[i]) {
                        values.add(entryValues[i].toString())
                    }
                }
                currentValue = join(values, SEPARATOR)
                if (callChangeListener(currentValue) && shouldPersist()) {
                    persistString(currentValue)
                }
                notifyChanged()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun setEntries(entries: Array<CharSequence>) {
        this.entries = entries
        clickedDialogEntryIndices = BooleanArray(entries.size)
    }

    fun getEntries(): Array<CharSequence>? = entries

    fun getEntryValues(): Array<CharSequence>? = entryValues

    fun parseStoredValue(value: String): Array<String>? {
        return if (value == "") null else value.split(SEPARATOR).toTypedArray()
    }

    private fun restoreCheckedEntries() {
        val entryValues = entryValues ?: return
        val vals = parseStoredValue(currentValue) ?: return
        val valuesList = Arrays.asList(*vals)
        for (i in entryValues.indices) {
            clickedDialogEntryIndices[i] = valuesList.contains(entryValues[i].toString())
        }
    }

    companion object {
        @JvmStatic
        fun join(collection: Iterable<Any>?, separator: String): String {
            val iter = collection?.iterator() ?: return ""
            if (!iter.hasNext()) return ""
            val builder = StringBuilder(iter.next().toString())
            while (iter.hasNext()) {
                builder.append(separator).append(iter.next())
            }
            return builder.toString()
        }
    }
}
