package com.borkozic.ui

import android.R
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.DialogInterface.OnClickListener
import android.widget.ArrayAdapter
import android.widget.ListView
import java.io.File
import java.io.FileFilter

class FolderPickerDialog(context: Context, initialPath: String, defaultPath: String) :
    AlertDialog(context), OnClickListener {

    private val dirFilter = FileFilter { pathname -> pathname.isDirectory }

    private lateinit var folderList: ListView

    init {
        setTitle(initialPath)
        var folders: Array<String>? = null
        val initial = File(initialPath)
        if (initial.exists() && initial.isDirectory) {
            val dirs = initial.listFiles(dirFilter) ?: emptyArray()
            folders = Array(dirs.size) { i -> dirs[i].name }
        }

        folderList = ListView(context)
        val folderAdapter = ArrayAdapter(
            context,
            android.R.layout.simple_list_item_1,
            android.R.id.text1,
            folders ?: emptyArray<String>()
        )
        folderList.adapter = folderAdapter

        setView(folderList)

        setButton(AlertDialog.BUTTON_POSITIVE, context.getText(R.string.ok), this)
        setButton(AlertDialog.BUTTON_NEGATIVE, context.getText(R.string.cancel), null as OnClickListener?)
    }

    override fun onClick(arg0: DialogInterface, arg1: Int) {
    }
}
