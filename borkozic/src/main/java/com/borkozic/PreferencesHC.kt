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

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.ProgressDialog
import android.app.backup.BackupManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.pm.PackageManager.NameNotFoundException
import android.os.Bundle
import android.os.Environment
import android.preference.EditTextPreference
import android.preference.ListPreference
import android.preference.Preference
import android.preference.Preference.OnPreferenceClickListener
import android.preference.PreferenceActivity
import android.preference.PreferenceFragment
import android.preference.PreferenceGroup
import android.preference.PreferenceScreen
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.borkozic.map.online.TileProvider
import com.borkozic.ui.SeekbarPreference
import java.io.File

@SuppressLint("NewApi")
open class PreferencesHC : PreferenceActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent.hasExtra("pref")) {
            for (i in 0 until listAdapter.count) {
                if (intent.getIntExtra("pref", -1).toLong() == (listAdapter.getItem(i) as Header).id) {
                    startWithFragment(
                        (listAdapter.getItem(i) as Header).fragment,
                        (listAdapter.getItem(i) as Header).fragmentArguments, null, 0
                    )
                    finish()
                }
            }
        }
    }

    override fun onBuildHeaders(target: MutableList<Header>) {
        loadHeadersFromResource(R.xml.preference_headers, target)
    }

    override fun isValidFragment(name: String): Boolean {
        return true
    }

    open class PreferencesFragment : PreferenceFragment(), OnSharedPreferenceChangeListener {

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            val arguments = arguments ?: return

            val resource = arguments.getString("resource")
            if (resource != null) {
                val res = activity.resources.getIdentifier(resource, "xml", activity.packageName)
                addPreferencesFromResource(res)
            }

            if (arguments.getBoolean("disable", false)) {
                val screen = preferenceScreen
                for (i in 0 until screen.preferenceCount) {
                    preferenceScreen.getPreference(i).isEnabled = false
                }
            }
        }

        override fun onResume() {
            super.onResume()

            // initialize list summaries
            initSummaries(preferenceScreen)
            preferenceScreen.sharedPreferences.registerOnSharedPreferenceChangeListener(this)
        }

        override fun onPause() {
            super.onPause()

            preferenceScreen.sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
            if (key == getString(R.string.pref_folder_root)) {
                val application = activity.application as Borkozic
                val root = sharedPreferences.getString(
                    key,
                    Environment.getExternalStorageDirectory().toString() + File.separator + getString(R.string.def_folder_prefix)
                )
                application.rootPath = root!!
            } else if (key == getString(R.string.pref_folder_map)) {
                val pd = ProgressDialog(activity)
                pd.setIndeterminate(true)
                pd.setMessage(getString(R.string.msg_initializingmaps))
                pd.show()

                Thread {
                    val application = activity.application as Borkozic
                    application.setMapPath(
                        sharedPreferences.getString(
                            key,
                            activity.resources.getString(R.string.def_folder_map)
                        )!!
                    )
                    pd.dismiss()
                }.start()
            } else if (key == getString(R.string.pref_charset)) {
                val pd = ProgressDialog(activity)
                pd.setIndeterminate(true)
                pd.setMessage(getString(R.string.msg_initializingmaps))
                pd.show()

                Thread {
                    val application = activity.application as Borkozic
                    application.charset = sharedPreferences.getString(key, "UTF-8")
                    application.resetMaps()
                    pd.dismiss()
                }.start()
            }

            val pref = findPreference(key)
            setPrefSummary(pref)

            if (key == getString(R.string.pref_onlinemap)) {
                val application = activity.application as Borkozic
                val mapzoom = findPreference(getString(R.string.pref_onlinemapscale)) as SeekbarPreference
                val providers = application.getOnlineMaps()
                val current = sharedPreferences.getString(key, resources.getString(R.string.def_onlinemap))
                var curProvider: TileProvider? = null
                for (provider in providers) {
                    if (current == provider.code)
                        curProvider = provider
                }
                if (curProvider != null) {
                    mapzoom.setMin(curProvider.minZoom.toInt())
                    mapzoom.setMax(curProvider.maxZoom.toInt())
                    val zoom = sharedPreferences.getInt(
                        getString(R.string.pref_onlinemapscale),
                        resources.getInteger(R.integer.def_onlinemapscale)
                    )
                    if (zoom < curProvider.minZoom) {
                        val editor = sharedPreferences.edit()
                        editor.putInt(getString(R.string.pref_onlinemapscale), curProvider.minZoom.toInt())
                        editor.commit()
                    }
                    if (zoom > curProvider.maxZoom) {
                        val editor = sharedPreferences.edit()
                        editor.putInt(getString(R.string.pref_onlinemapscale), curProvider.maxZoom.toInt())
                        editor.commit()
                    }
                }
            }
            if (key == getString(R.string.pref_locale)) {
                AlertDialog.Builder(activity).setTitle(R.string.restart_needed)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setMessage(getString(R.string.restart_needed_explained)).setCancelable(false)
                    .setPositiveButton(R.string.ok, null).show()
            }
            // TODO change intent name
            activity.sendBroadcast(Intent("onSharedPreferenceChanged").putExtra("key", key))
            try {
                BackupManager.dataChanged("com.borkozic")
            } catch (e: NoClassDefFoundError) {
            }
        }

        private fun setPrefSummary(pref: Preference?) {
            if (pref is ListPreference) {
                val summary = pref.entry
                if (summary != null) {
                    pref.summary = summary
                }
            } else if (pref is EditTextPreference) {
                val summary = pref.text
                if (summary != null) {
                    pref.summary = summary
                }
            } else if (pref is SeekbarPreference) {
                val summary = pref.getText()
                if (summary != null) {
                    pref.summary = summary
                }
            }
        }

        private fun initSummaries(preference: PreferenceGroup) {
            for (i in preference.preferenceCount - 1 downTo 0) {
                val pref = preference.getPreference(i)
                setPrefSummary(pref)

                if (pref is PreferenceGroup || pref is PreferenceScreen) {
                    initSummaries(pref as PreferenceGroup)
                }
            }
        }
    }

    class PluginsPreferencesFragment : PreferencesFragment() {

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            val root = preferenceManager.createPreferenceScreen(activity)
            root.title = getString(R.string.pref_plugins_title)
            preferenceScreen = root

            val application = activity.application as Borkozic
            val plugins = application.getPluginsPreferences()

            for (plugin in plugins.keys) {
                val preference = Preference(activity)
                preference.title = plugin
                preference.intent = plugins[plugin]
                root.addPreference(preference)
            }
        }
    }

    class OnlineMapPreferencesFragment : PreferencesFragment() {

        override fun onResume() {
            val application = activity.application as Borkozic

            val maps = findPreference(getString(R.string.pref_onlinemap)) as ListPreference
            val mapzoom = findPreference(getString(R.string.pref_onlinemapscale)) as SeekbarPreference
            // initialize map list
            val providers = application.getOnlineMaps()
            val entries = arrayOfNulls<String>(providers.size)
            val entryValues = arrayOfNulls<String>(providers.size)
            val current = preferenceScreen.sharedPreferences.getString(
                getString(R.string.pref_onlinemap),
                resources.getString(R.string.def_onlinemap)
            )
            var curProvider: TileProvider? = null
            var i = 0
            for (provider in providers) {
                entries[i] = provider.name
                entryValues[i] = provider.code
                if (current == provider.code)
                    curProvider = provider
                i++
            }
            maps.entries = entries
            maps.entryValues = entryValues

            if (curProvider != null) {
                mapzoom.setMin(curProvider.minZoom.toInt())
                mapzoom.setMax(curProvider.maxZoom.toInt())
            }

            super.onResume()
        }
    }

    class ApplicationPreferencesFragment : PreferencesFragment() {

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            val prefAbout = findPreference(getString(R.string.pref_about))
            prefAbout!!.onPreferenceClickListener = OnPreferenceClickListener {
                val factory = LayoutInflater.from(this@ApplicationPreferencesFragment.activity)
                val aboutView = factory.inflate(R.layout.dlg_about, null)
                val versionLabel = aboutView.findViewById<TextView>(R.id.version_label)
                val versionName = try {
                    this@ApplicationPreferencesFragment.activity.packageManager.getPackageInfo(
                        this@ApplicationPreferencesFragment.activity.packageName, 0
                    ).versionName
                } catch (ex: NameNotFoundException) {
                    "unable to retreive version"
                }
                versionLabel.text = getString(R.string.version, versionName)
                AlertDialog.Builder(this@ApplicationPreferencesFragment.activity)
                    .setIcon(R.drawable.icon)
                    .setTitle(R.string.app_name)
                    .setView(aboutView)
                    .setPositiveButton("OK", null)
                    .create().show()
                true
            }

            val prefCredits = findPreference(getString(R.string.pref_credits))
            prefCredits!!.onPreferenceClickListener = OnPreferenceClickListener {
                startActivity(Intent(this@ApplicationPreferencesFragment.activity, Credits::class.java))
                true
            }
        }
    }
}
