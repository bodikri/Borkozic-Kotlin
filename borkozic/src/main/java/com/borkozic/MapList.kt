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

package com.borkozic

import android.app.ListActivity
import android.app.ProgressDialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import androidx.preference.PreferenceManager
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.ListView
import android.widget.SimpleAdapter
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MapList : ListActivity() {

    private var maps: List<com.borkozic.map.Map> = ArrayList()
    private val mapData: MutableList<MutableMap<String, String>> = ArrayList()

    private val threadPool: ExecutorService = Executors.newFixedThreadPool(2)
    private val handler = Handler()

    companion object {
        private const val KEY_NAME = "NAME"
        private const val KEY_DESC = "DESC"
    }

    override fun onResume() {
        populateItems()
        super.onResume()
    }

    private fun populateItems() {
        val pd = ProgressDialog(this)
        pd.isIndeterminate = true
        pd.setMessage(getString(R.string.msg_wait))
        pd.show()

        Thread {
            val application = application as Borkozic
            val extras = intent.extras

            if (extras != null && extras.getBoolean("pos")) {
                val loc = application.getMapCenter()
                maps = application.getMaps(loc)
            } else {
                maps = application.getMaps()
            }

            mapData.clear()

            val mappath = application.mapPath ?: ""

            for (map in maps) {
                var fn = map.mappath
                if (mappath.isNotEmpty() && fn.startsWith(mappath)) {
                    fn = fn.substring(mappath.length + 1)
                }
                val group: MutableMap<String, String> = HashMap()
                group[KEY_NAME] = map.title
                group[KEY_DESC] = String.format("MPP: %.2f - %s", map.mpp, fn)
                mapData.add(group)
            }

            pd.dismiss()
            handler.post(updateResults)
        }.start()
    }

    private val updateList = Runnable {
        populateItems()
    }

    private val updateResults = Runnable {
        listAdapter = SimpleAdapter(
            this@MapList,
            mapData,
            android.R.layout.simple_list_item_2,
            arrayOf(KEY_NAME, KEY_DESC),
            intArrayOf(android.R.id.text1, android.R.id.text2)
        )
        listView.isTextFilterEnabled = true
    }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        super.onListItemClick(l, v, position, id)
        setResult(RESULT_OK, Intent().putExtra("id", maps[position].id))
        finish()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.maplist_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val settings = PreferenceManager.getDefaultSharedPreferences(this)
        val useIndex = settings.getBoolean(
            getString(R.string.pref_usemapindex),
            resources.getBoolean(R.bool.def_usemapindex)
        )

        menu.findItem(R.id.menuResetMapIndex).isEnabled = useIndex
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menuResetMapIndex -> {
                val pd = ProgressDialog(this)
                pd.isIndeterminate = true
                pd.setMessage(getString(R.string.msg_initializingmaps))
                pd.show()

                Thread {
                    val application = application as Borkozic
                    application.resetMaps()

                    pd.dismiss()
                    handler.post(updateList)
                }.start()
            }
        }
        return true
    }

    override fun onStop() {
        super.onStop()
        mapData.clear()
    }
}
