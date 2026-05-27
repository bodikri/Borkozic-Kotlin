package com.borkozic.ui

import android.app.AlertDialog.Builder
import android.content.Context
import android.content.DialogInterface
import android.content.res.TypedArray
import androidx.preference.ListPreference
import android.util.AttributeSet
import java.util.ArrayList
import java.util.Arrays

class ImageMultiChoiceListPreference @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : ListPreference(context, attrs) {
    private val SEPARATOR = ","
    private var clickedDialogEntryIndices: BooleanArray = BooleanArray(0)
    private var currentValue: String = ""

    init {
        clickedDialogEntryIndices = BooleanArray(entries?.size ?: 0)
        currentValue = ""
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

    override fun setEntries(entries: Array<CharSequence>) {
        super.setEntries(entries)
        clickedDialogEntryIndices = BooleanArray(entries.size)
    }

    override fun onPrepareDialogBuilder(builder: Builder) {
        val entries = getEntries()
        val entryValues = getEntryValues()
        if (entries == null || entryValues == null || entries.size != entryValues.size) {
            throw IllegalStateException(
                "ListPreference requires an entries array and an entryValues array which are both the same length"
            )
        }

        if (isPersistent) currentValue = getPersistedString("")
        restoreCheckedEntries()

        builder.setMultiChoiceItems(
            entries,
            clickedDialogEntryIndices
        ) { _, which, isChecked ->
            clickedDialogEntryIndices[which] = isChecked
        }
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        val values = ArrayList<String>()
        val entryValues = getEntryValues()
        if (positiveResult && entryValues != null) {
            for (i in entryValues.indices) {
                if (clickedDialogEntryIndices[i]) {
                    values.add(entryValues[i].toString())
                }
            }
            currentValue = join(values, SEPARATOR)
            if (callChangeListener(currentValue) && shouldPersist()) {
                persistString(currentValue)
            }
        }
    }

    fun parseStoredValue(value: String): Array<String>? {
        return if (value == "") null else value.split(SEPARATOR).toTypedArray()
    }

    private fun restoreCheckedEntries() {
        val entryValues = getEntryValues() ?: return
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
