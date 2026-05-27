/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2012 Andrey Novikov <http://andreynovikov.info/>
 *
 * This file is part of Androzic application.
 *
 * Androzic is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Androzic is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Androzic. If not, see <http://www.gnu.org/licenses/>.
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
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.preference.EditTextPreference
import android.preference.ListPreference
import android.preference.Preference
import android.preference.Preference.OnPreferenceClickListener
import android.preference.PreferenceActivity
import android.preference.PreferenceGroup
import android.preference.PreferenceScreen
import android.view.LayoutInflater
import android.widget.TextView
import com.borkozic.map.online.TileProvider
import com.borkozic.ui.SeekbarPreference
import java.io.File

class Preferences : PreferenceActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        addPreferencesFromResource(R.xml.preferences)

        val root = preferenceScreen

        for (i in root.preferenceCount - 1 downTo 0) {
            val pref = root.getPreference(i)
            val key = pref.key
            pref.onPreferenceClickListener = OnPreferenceClickListener {
                startPreference(key)
                true
            }
        }

        if (intent.hasExtra("pref")) {
            startPreference(intent.extras!!.getString("pref"))
            finish()
        }
    }

    private fun startPreference(key: String?) {
        val activity: Class<*> = when (key) {
            "pref_behavior" -> OnlineMapPreferences::class.java
            "pref_plugins" -> PluginsPreferences::class.java
            "pref_app_about" -> ApplicationPreferences::class.java
            else -> InnerPreferences::class.java
        }
        startActivity(Intent(this@Preferences, activity).putExtra("KEY", key))
    }

    open class InnerPreferences : PreferenceActivity(), OnSharedPreferenceChangeListener {

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            val key = intent.extras!!.getString("KEY")
            val res = resources.getIdentifier(key, "xml", packageName)

            addPreferencesFromResource(res)
        }

        override fun onResume() {
            super.onResume()
            // initialize list summaries
            initSummaries(preferenceScreen)
            preferenceScreen.sharedPreferences.registerOnSharedPreferenceChangeListener(this)

            val key = intent.extras!!.getString("KEY")
            Log.d("InnerPreferences", "onResume key=$key")

            // SAF folder picker for procedures
            val emerPref = findPreference(getString(R.string.pref_procedures_emer_folder))
            val normPref = findPreference(getString(R.string.pref_procedures_norm_folder))
            Log.d("InnerPreferences", "emerPref=$emerPref normPref=$normPref")
            emerPref?.onPreferenceClickListener = OnPreferenceClickListener {
                Log.d("InnerPreferences", "Emer folder clicked!")
                val intent = Intent(this@InnerPreferences, ProceduresFolderPickerActivity::class.java)
                intent.putExtra(ProceduresFolderPickerActivity.EXTRA_PREF_KEY,
                    getString(R.string.pref_procedures_emer_folder))
                startActivity(intent)
                true
            }
            normPref?.onPreferenceClickListener = OnPreferenceClickListener {
                Log.d("InnerPreferences", "Norm folder clicked!")
                val intent = Intent(this@InnerPreferences, ProceduresFolderPickerActivity::class.java)
                intent.putExtra(ProceduresFolderPickerActivity.EXTRA_PREF_KEY,
                    getString(R.string.pref_procedures_norm_folder))
                startActivity(intent)
                true
            }

            // Update summaries with stored URIs
            updateProceduresSummary(emerPref, getString(R.string.pref_procedures_emer_folder),
                getString(R.string.pref_procedures_emer_folder_summary))
            updateProceduresSummary(normPref, getString(R.string.pref_procedures_norm_folder),
                getString(R.string.pref_procedures_norm_folder_summary))
        }

        private fun updateProceduresSummary(pref: Preference?, key: String, defaultSummary: String) {
            val uriStr = preferenceScreen.sharedPreferences.getString(key, null)
            pref?.summary = if (uriStr.isNullOrEmpty()) defaultSummary else uriStr
        }

        override fun onPause() {
            super.onPause()
            preferenceScreen.sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        }

        @SuppressLint("NewApi")
        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
            if (key == getString(R.string.pref_folder_root)) {
                val application = application as Borkozic
                val root = sharedPreferences.getString(
                    key!!,
                    Environment.getExternalStorageDirectory().toString() + File.separator + getString(R.string.def_folder_prefix)
                )
                application.rootPath = root!!
            } else if (key == getString(R.string.pref_folder_map)) {
                val pd = ProgressDialog(this)
                pd.setIndeterminate(true)
                pd.setMessage(getString(R.string.msg_initializingmaps))
                pd.show()

                Thread {
                    val application = application as Borkozic
                    application.setMapPath(
                        sharedPreferences.getString(key!!, resources.getString(R.string.def_folder_map))!!
                    )
                    pd.dismiss()
                }.start()
            } else if (key == getString(R.string.pref_charset)) {
                val pd = ProgressDialog(this)
                pd.setIndeterminate(true)
                pd.setMessage(getString(R.string.msg_initializingmaps))
                pd.show()

                Thread {
                    val application = application as Borkozic
                    application.charset = sharedPreferences.getString(key!!, "UTF-8")
                    application.resetMaps()
                    pd.dismiss()
                }.start()
            }

            val pref = findPreference(key!!)
            setPrefSummary(pref)

            if (key == getString(R.string.pref_onlinemap)) {
                val application = application as Borkozic
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
                AlertDialog.Builder(this).setTitle(R.string.restart_needed)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setMessage(getString(R.string.restart_needed_explained))
                    .setCancelable(false).setPositiveButton(R.string.ok, null).show()
            }
            sendBroadcast(Intent("onSharedPreferenceChanged").putExtra("key", key))
            try {
                if (Build.VERSION.SDK_INT > 7)
                    BackupManager.dataChanged("com.borkozic")
            } catch (e: NoClassDefFoundError) {
                e.printStackTrace()
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

    /**
     * Preference lists Borkozic plugins preferences.
     */
    class PluginsPreferences : PreferenceActivity() {

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            val root = preferenceManager.createPreferenceScreen(this)
            root.title = getString(R.string.pref_plugins_title)
            preferenceScreen = root

            val application = application as Borkozic
            val plugins = application.getPluginsPreferences()

            for (plugin in plugins.keys) {
                val preference = Preference(this)
                preference.title = plugin
                preference.intent = plugins[plugin]
                root.addPreference(preference)
            }
        }
    }

    class OnlineMapPreferences : InnerPreferences() {

        override fun onResume() {
            val application = application as Borkozic

            val maps = findPreference(getString(R.string.pref_onlinemap)) as ListPreference
            val mapzoom = findPreference(getString(R.string.pref_onlinemapscale)) as SeekbarPreference
            // initialize map list
            val providers = application.getOnlineMaps()
            if (providers != null) {
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
            }
            super.onResume()
        }
    }

    class ApplicationPreferences : InnerPreferences() {

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            val prefAbout = findPreference(getString(R.string.pref_about))!!
            prefAbout.onPreferenceClickListener = OnPreferenceClickListener {
                val factory = LayoutInflater.from(this@ApplicationPreferences)
                val aboutView = factory.inflate(R.layout.dlg_about, null)
                val versionLabel = aboutView.findViewById<TextView>(R.id.version_label)
                val versionName: String = try {
                    this@ApplicationPreferences.packageManager.getPackageInfo(
                        this@ApplicationPreferences.packageName, 0
                    ).versionName
                } catch (ex: NameNotFoundException) {
                    "unable to retreive version"
                }
                versionLabel.text = getString(R.string.version, versionName)
                AlertDialog.Builder(this@ApplicationPreferences).setIcon(R.drawable.icon)
                    .setTitle(R.string.app_name).setView(aboutView).setPositiveButton("OK", null).create().show()
                true
            }

            val prefDonateGoogle = findPreference(getString(R.string.pref_donategoogle))!!
            prefDonateGoogle.onPreferenceClickListener = OnPreferenceClickListener {
                val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.borkozic.donate"))
                startActivity(marketIntent)
                true
            }

            val prefDonatePaypal = findPreference(getString(R.string.pref_donatepaypal))!!
            prefDonatePaypal.onPreferenceClickListener = OnPreferenceClickListener {
                startActivity(Intent(Intent.ACTION_VIEW).setData(Uri.parse(getString(R.string.paypaluri))))
                true
            }

            val application = application as Borkozic
            if (application.isPaid) {
                this@ApplicationPreferences.preferenceScreen.removePreference(prefDonateGoogle)
                this@ApplicationPreferences.preferenceScreen.removePreference(prefDonatePaypal)
            }

            val prefGooglePlus = findPreference(getString(R.string.pref_googleplus))!!
            prefGooglePlus.onPreferenceClickListener = OnPreferenceClickListener {
                startActivity(Intent(Intent.ACTION_VIEW).setData(Uri.parse(getString(R.string.googleplusuri))))
                true
            }

            val prefFacebook = findPreference(getString(R.string.pref_facebook))!!
            prefFacebook.onPreferenceClickListener = OnPreferenceClickListener {
                startActivity(Intent(Intent.ACTION_VIEW).setData(Uri.parse(getString(R.string.facebookuri))))
                true
            }

            val prefTwitter = findPreference(getString(R.string.pref_twitter))!!
            prefTwitter.onPreferenceClickListener = OnPreferenceClickListener {
                startActivity(Intent(Intent.ACTION_VIEW).setData(Uri.parse(getString(R.string.twitteruri))))
                true
            }

            val prefFaq = findPreference(getString(R.string.pref_faq))!!
            prefFaq.onPreferenceClickListener = OnPreferenceClickListener {
                startActivity(Intent(Intent.ACTION_VIEW).setData(Uri.parse(getString(R.string.faquri))))
                true
            }

            val prefFeature = findPreference(getString(R.string.pref_feature))!!
            prefFeature.onPreferenceClickListener = OnPreferenceClickListener {
                startActivity(Intent(Intent.ACTION_VIEW).setData(Uri.parse(getString(R.string.featureuri))))
                true
            }

            val prefCredits = findPreference(getString(R.string.pref_credits))!!
            prefCredits.onPreferenceClickListener = OnPreferenceClickListener {
                startActivity(Intent(this@ApplicationPreferences, Credits::class.java))
                true
            }
        }
    }
}
