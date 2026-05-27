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

import android.app.AlertDialog
import android.app.backup.BackupManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.pm.PackageManager.NameNotFoundException
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceScreen
import com.borkozic.map.online.TileProvider
import com.borkozic.ui.SeekbarPreference
import java.io.File

class Preferences : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            val fragment = when (intent.getStringExtra("pref")) {
                "pref_behavior" -> OnlineMapPreferencesFragment()
                "pref_plugins" -> PluginsPreferencesFragment()
                "pref_app_about" -> ApplicationPreferencesFragment()
                else -> MainPreferencesFragment()
            }
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, fragment)
                .commit()
        }
    }

    class MainPreferencesFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)

            for (i in 0 until preferenceScreen.preferenceCount) {
                val pref = preferenceScreen.getPreference(i)
                val key = pref.key
                pref.setOnPreferenceClickListener {
                    startPreference(key)
                    true
                }
            }
        }

        private fun startPreference(key: String?) {
            val fragment = when (key) {
                "pref_behavior" -> OnlineMapPreferencesFragment()
                "pref_plugins" -> PluginsPreferencesFragment()
                "pref_app_about" -> ApplicationPreferencesFragment()
                else -> InnerPreferencesFragment().apply {
                    arguments = Bundle().apply { putString("KEY", key) }
                }
            }
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, fragment)
                .addToBackStack(null)
                .commit()
        }
    }

    abstract class BasePreferenceFragment : PreferenceFragmentCompat(),
        SharedPreferences.OnSharedPreferenceChangeListener {

        override fun onResume() {
            super.onResume()
            preferenceScreen.sharedPreferences.registerOnSharedPreferenceChangeListener(this)
            initSummaries(preferenceScreen)
        }

        override fun onPause() {
            super.onPause()
            preferenceScreen.sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
            if (key == null) return

            when (key) {
                getString(R.string.pref_folder_root) -> {
                    val application = requireActivity().application as Borkozic
                    val root = sharedPreferences.getString(
                        key,
                        Environment.getExternalStorageDirectory().toString() + File.separator +
                                getString(R.string.def_folder_prefix)
                    )
                    application.rootPath = root!!
                }
                getString(R.string.pref_folder_map) -> {
                    showProgressDialog(getString(R.string.msg_initializingmaps)) {
                        val application = requireActivity().application as Borkozic
                        application.setMapPath(
                            sharedPreferences.getString(key, resources.getString(R.string.def_folder_map))!!
                        )
                    }
                }
                getString(R.string.pref_charset) -> {
                    showProgressDialog(getString(R.string.msg_initializingmaps)) {
                        val application = requireActivity().application as Borkozic
                        application.charset = sharedPreferences.getString(key, "UTF-8")
                        application.resetMaps()
                    }
                }
                getString(R.string.pref_onlinemap) -> {
                    updateOnlineMapSettings(sharedPreferences)
                }
                getString(R.string.pref_locale) -> {
                    AlertDialog.Builder(requireContext())
                        .setTitle(R.string.restart_needed)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(getString(R.string.restart_needed_explained))
                        .setCancelable(false)
                        .setPositiveButton(R.string.ok, null)
                        .show()
                }
            }

            val pref = findPreference<Preference>(key)
            setPrefSummary(pref)

            requireActivity().sendBroadcast(Intent("onSharedPreferenceChanged").putExtra("key", key))
            try {
                BackupManager.dataChanged("com.borkozic")
            } catch (_: Exception) {
            }
        }

        private fun updateOnlineMapSettings(sharedPreferences: SharedPreferences) {
            val application = requireActivity().application as Borkozic
            val mapzoom = findPreference<SeekbarPreference>(getString(R.string.pref_onlinemapscale))
            val providers = application.getOnlineMaps()
            val current = sharedPreferences.getString(
                getString(R.string.pref_onlinemap),
                resources.getString(R.string.def_onlinemap)
            )
            val curProvider = providers.find { it.code == current }
            if (curProvider != null && mapzoom != null) {
                mapzoom.setMin(curProvider.minZoom.toInt())
                mapzoom.setMax(curProvider.maxZoom.toInt())
                val zoom = sharedPreferences.getInt(
                    getString(R.string.pref_onlinemapscale),
                    resources.getInteger(R.integer.def_onlinemapscale)
                )
                if (zoom < curProvider.minZoom) {
                    sharedPreferences.edit()
                        .putInt(getString(R.string.pref_onlinemapscale), curProvider.minZoom.toInt())
                        .apply()
                }
                if (zoom > curProvider.maxZoom) {
                    sharedPreferences.edit()
                        .putInt(getString(R.string.pref_onlinemapscale), curProvider.maxZoom.toInt())
                        .apply()
                }
            }
        }

        private fun showProgressDialog(message: String, backgroundTask: () -> Unit) {
            val progressBar = ProgressBar(requireContext())
            progressBar.isIndeterminate = true
            val dialog = AlertDialog.Builder(requireContext())
                .setMessage(message)
                .setView(progressBar)
                .setCancelable(false)
                .create()
            dialog.show()

            Thread {
                backgroundTask()
                requireActivity().runOnUiThread { dialog.dismiss() }
            }.start()
        }

        private fun setPrefSummary(pref: Preference?) {
            when (pref) {
                is ListPreference -> {
                    pref.entry?.let { pref.summary = it }
                }
                is EditTextPreference -> {
                    pref.text?.let { pref.summary = it }
                }
                is SeekbarPreference -> {
                    pref.summary = pref.getText()
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

    class InnerPreferencesFragment : BasePreferenceFragment() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            val key = arguments?.getString("KEY") ?: return
            val res = resources.getIdentifier(key, "xml", requireActivity().packageName)
            setPreferencesFromResource(res, rootKey)

            // SAF folder picker for procedures
            val emerPref = findPreference<Preference>(getString(R.string.pref_procedures_emer_folder))
            val normPref = findPreference<Preference>(getString(R.string.pref_procedures_norm_folder))
            emerPref?.setOnPreferenceClickListener {
                val intent = Intent(requireActivity(), ProceduresFolderPickerActivity::class.java)
                intent.putExtra(ProceduresFolderPickerActivity.EXTRA_PREF_KEY,
                    getString(R.string.pref_procedures_emer_folder))
                startActivity(intent)
                true
            }
            normPref?.setOnPreferenceClickListener {
                val intent = Intent(requireActivity(), ProceduresFolderPickerActivity::class.java)
                intent.putExtra(ProceduresFolderPickerActivity.EXTRA_PREF_KEY,
                    getString(R.string.pref_procedures_norm_folder))
                startActivity(intent)
                true
            }
            updateProceduresSummary(emerPref, getString(R.string.pref_procedures_emer_folder),
                getString(R.string.pref_procedures_emer_folder_summary))
            updateProceduresSummary(normPref, getString(R.string.pref_procedures_norm_folder),
                getString(R.string.pref_procedures_norm_folder_summary))
        }

        private fun updateProceduresSummary(pref: Preference?, key: String, defaultSummary: String) {
            val uriStr = preferenceScreen.sharedPreferences.getString(key, null)
            pref?.summary = if (uriStr.isNullOrEmpty()) defaultSummary else uriStr
        }
    }

    class PluginsPreferencesFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            val context = preferenceManager.context
            val screen = preferenceManager.createPreferenceScreen(context)
            screen.title = getString(R.string.pref_plugins_title)
            preferenceScreen = screen

            val application = requireActivity().application as Borkozic
            val plugins = application.getPluginsPreferences()

            for (plugin in plugins.keys) {
                val preference = Preference(context)
                preference.title = plugin
                preference.intent = plugins[plugin]
                screen.addPreference(preference)
            }
        }
    }

    class OnlineMapPreferencesFragment : BasePreferenceFragment() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.pref_behavior, rootKey)
        }

        override fun onResume() {
            val application = requireActivity().application as Borkozic

            val maps = findPreference<ListPreference>(getString(R.string.pref_onlinemap))
            val mapzoom = findPreference<SeekbarPreference>(getString(R.string.pref_onlinemapscale))
            val providers = application.getOnlineMaps()
            if (providers.isNotEmpty() && maps != null) {
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

                if (curProvider != null && mapzoom != null) {
                    mapzoom.setMin(curProvider.minZoom.toInt())
                    mapzoom.setMax(curProvider.maxZoom.toInt())
                }
            }
            super.onResume()
        }
    }

    class ApplicationPreferencesFragment : BasePreferenceFragment() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.pref_application, rootKey)

            val prefAbout = findPreference<Preference>(getString(R.string.pref_about))!!
            prefAbout.setOnPreferenceClickListener {
                val factory = LayoutInflater.from(requireContext())
                val aboutView = factory.inflate(R.layout.dlg_about, null)
                val versionLabel = aboutView.findViewById<TextView>(R.id.version_label)
                val versionName: String = try {
                    requireActivity().packageManager.getPackageInfo(
                        requireActivity().packageName, 0
                    ).versionName
                } catch (ex: NameNotFoundException) {
                    "unable to retreive version"
                }
                versionLabel.text = getString(R.string.version, versionName)
                AlertDialog.Builder(requireContext())
                    .setIcon(R.drawable.icon)
                    .setTitle(R.string.app_name)
                    .setView(aboutView)
                    .setPositiveButton("OK", null)
                    .create().show()
                true
            }

            val prefDonateGoogle = findPreference<Preference>(getString(R.string.pref_donategoogle))
            prefDonateGoogle?.setOnPreferenceClickListener {
                val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.borkozic.donate"))
                startActivity(marketIntent)
                true
            }

            val prefDonatePaypal = findPreference<Preference>(getString(R.string.pref_donatepaypal))
            prefDonatePaypal?.setOnPreferenceClickListener {
                startActivity(Intent(Intent.ACTION_VIEW).setData(Uri.parse(getString(R.string.paypaluri))))
                true
            }

            val application = requireActivity().application as Borkozic
            if (application.isPaid) {
                prefDonateGoogle?.let { preferenceScreen.removePreference(it) }
                prefDonatePaypal?.let { preferenceScreen.removePreference(it) }
            }

            val prefGooglePlus = findPreference<Preference>(getString(R.string.pref_googleplus))
            prefGooglePlus?.setOnPreferenceClickListener {
                startActivity(Intent(Intent.ACTION_VIEW).setData(Uri.parse(getString(R.string.googleplusuri))))
                true
            }

            val prefFacebook = findPreference<Preference>(getString(R.string.pref_facebook))
            prefFacebook?.setOnPreferenceClickListener {
                startActivity(Intent(Intent.ACTION_VIEW).setData(Uri.parse(getString(R.string.facebookuri))))
                true
            }

            val prefTwitter = findPreference<Preference>(getString(R.string.pref_twitter))
            prefTwitter?.setOnPreferenceClickListener {
                startActivity(Intent(Intent.ACTION_VIEW).setData(Uri.parse(getString(R.string.twitteruri))))
                true
            }

            val prefFaq = findPreference<Preference>(getString(R.string.pref_faq))
            prefFaq?.setOnPreferenceClickListener {
                startActivity(Intent(Intent.ACTION_VIEW).setData(Uri.parse(getString(R.string.faquri))))
                true
            }

            val prefFeature = findPreference<Preference>(getString(R.string.pref_feature))
            prefFeature?.setOnPreferenceClickListener {
                startActivity(Intent(Intent.ACTION_VIEW).setData(Uri.parse(getString(R.string.featureuri))))
                true
            }

            val prefCredits = findPreference<Preference>(getString(R.string.pref_credits))
            prefCredits?.setOnPreferenceClickListener {
                startActivity(Intent(requireActivity(), Credits::class.java))
                true
            }
        }
    }
}
