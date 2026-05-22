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

import android.app.Dialog
import android.app.ListActivity
import android.app.ProgressDialog
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.view.View
import android.widget.ListView
import android.widget.SimpleAdapter
import android.widget.Toast
import com.borkozic.R
import com.borkozic.util.FileList
import java.io.File
import java.io.FilenameFilter
import java.util.concurrent.Executors

abstract class FileListActivity : ListActivity() {
    var files: List<File>? = null
    val fileData: MutableList<MutableMap<String, String>> = ArrayList()

    private var dlgWait: ProgressDialog? = null
    protected val threadPool = Executors.newFixedThreadPool(2)
    val handler = Handler()

    companion object {
        private const val KEY_FILE = "FILE"
        private const val KEY_PATH = "DIR"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val state = Environment.getExternalStorageState()
        if (Environment.MEDIA_MOUNTED != state) {
            Toast.makeText(this, R.string.err_nosdcard, Toast.LENGTH_LONG).show()
            finish()
        }

        val pd = ProgressDialog(this)

        pd.setIndeterminate(true)
        pd.setMessage(getString(R.string.msg_scansdcard))
        pd.show()

        Thread {
            val root = File(getPath())
            files = FileList.getFileListing(root, getFilenameFilter())
            /*
                            Collections.sort(files, new Comparator()
                                    {
                                        @Override
                                        public int compare(Object o1, Object o2)
                                        {
                                            return ((File) o1).getName().compareToIgnoreCase(((File) o2).getName());
                                        }
                                    });
            */
            for (file in files!!) {
                val group: MutableMap<String, String> = HashMap()
                group[KEY_FILE] = file.name
                group[KEY_PATH] = file.parent
                fileData.add(group)
            }

            pd.dismiss()

            handler.post(updateResults)
        }.start()
    }

    private val updateResults = Runnable {
        listAdapter = SimpleAdapter(
            this@FileListActivity, fileData,
            android.R.layout.simple_list_item_2,
            arrayOf(KEY_FILE, KEY_PATH),
            intArrayOf(android.R.id.text1, android.R.id.text2)
        )
        listView.setTextFilterEnabled(true)
    }

    override fun onCreateDialog(id: Int): Dialog? {
        return when (id) {
            0 -> {
                dlgWait = ProgressDialog(this)
                dlgWait!!.setMessage(getString(R.string.msg_wait))
                dlgWait!!.setIndeterminate(true)
                dlgWait!!.setCancelable(false)
                dlgWait
            }
            else -> null
        }
    }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        super.onListItemClick(l, v, position, id)

        val file = files!![position]

        if (!file.exists()) {
            Toast.makeText(this, R.string.err_nofile, Toast.LENGTH_LONG).show()
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        showDialog(0)

        threadPool.execute {
            loadFile(file)
            dlgWait!!.dismiss()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        files = null
        fileData.clear()
    }

    protected abstract fun getFilenameFilter(): FilenameFilter

    protected abstract fun getPath(): String

    protected abstract fun loadFile(file: File)

    protected val wrongFormat = Runnable {
        Toast.makeText(baseContext, R.string.err_wrongformat, Toast.LENGTH_LONG).show()
    }

    protected val readError = Runnable {
        Toast.makeText(baseContext, R.string.err_read, Toast.LENGTH_LONG).show()
    }
}
