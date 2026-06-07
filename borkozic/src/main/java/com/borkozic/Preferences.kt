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

import android.util.Log
import android.app.AlertDialog
import android.app.backup.BackupManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.pm.PackageManager.NameNotFoundException
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import com.borkozic.map.online.TileProvider
import com.borkozic.ui.SeekbarPreference
import java.io.File

class Preferences : AppCompatActivity() {

    companion object {
        private const val TAG = "Preferences"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            val fragment = when (intent.getStringExtra("pref")) {
                "pref_behavior" -> OnlineMapPreferencesFragment()
                "pref_sharing" -> LocationSharingPreferencesFragment()
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
            Log.d("Preferences", "MainPreferencesFragment: onCreatePreferences rootKey=$rootKey")
            setPreferencesFromResource(R.xml.preferences, rootKey)

            for (i in 0 until preferenceScreen.preferenceCount) {
                val pref = preferenceScreen.getPreference(i)
                val key = pref.key
                Log.d("Preferences", "MainPreferencesFragment: pref[$i] key=$key class=${pref.javaClass.simpleName}")
                pref.setOnPreferenceClickListener {
                    Log.d("Preferences", "MainPreferencesFragment: clicked key=$key")
                    startPreference(key)
                    true
                }
            }
        }

        private fun startPreference(key: String?) {
            Log.d("Preferences", "startPreference: key=$key")
            val fragment = when (key) {
                "pref_behavior" -> OnlineMapPreferencesFragment()
                "pref_sharing" -> LocationSharingPreferencesFragment()
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
            preferenceScreen.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
            initSummaries(preferenceScreen)
        }

        override fun onPause() {
            super.onPause()
            preferenceScreen.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            if (key == null) return
            val prefs = sharedPreferences ?: return

            when (key) {
                getString(R.string.pref_folder_root) -> {
                    val application = requireActivity().application as Borkozic
                    val root = prefs.getString(
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
                            prefs.getString(key, resources.getString(R.string.def_folder_map))!!
                        )
                    }
                }
                getString(R.string.pref_charset) -> {
                    showProgressDialog(getString(R.string.msg_initializingmaps)) {
                        val application = requireActivity().application as Borkozic
                        application.charset = prefs.getString(key, "UTF-8")!!
                        application.resetMaps()
                    }
                }
                getString(R.string.pref_onlinemap) -> {
                    updateOnlineMapSettings(prefs)
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
                    sharedPreferences.edit {
                        putInt(getString(R.string.pref_onlinemapscale), curProvider.minZoom.toInt())
                    }
                }
                if (zoom > curProvider.maxZoom) {
                    sharedPreferences.edit {
                        putInt(getString(R.string.pref_onlinemapscale), curProvider.maxZoom.toInt())
                    }
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

                if (pref is PreferenceGroup) {
                    initSummaries(pref)
                }
            }
        }
    }

    class InnerPreferencesFragment : BasePreferenceFragment() {
        @Suppress("DEPRECATION")
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            val key = arguments?.getString("KEY") ?: return
            Log.d("Preferences", "InnerPreferencesFragment: key=$key rootKey=$rootKey")
            val res = resources.getIdentifier(key, "xml", requireActivity().packageName)
            Log.d("Preferences", "InnerPreferencesFragment: resId=$res")
            setPreferencesFromResource(res, rootKey)

            // Handle nested PreferenceScreen for procedures
            val proceduresScreen = findPreference<Preference>(getString(R.string.pref_procedures_key))
            Log.d("Preferences", "InnerPreferencesFragment: proceduresScreen=$proceduresScreen")
            proceduresScreen?.setOnPreferenceClickListener {
                Log.d("Preferences", "InnerPreferencesFragment: procedures clicked, opening InnerProceduresFragment")
                val fragment = InnerProceduresFragment()
                requireActivity().supportFragmentManager.beginTransaction()
                    .replace(android.R.id.content, fragment)
                    .addToBackStack(null)
                    .commit()
                true
            }
        }
    }

    class InnerProceduresFragment : BasePreferenceFragment() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            Log.d("Preferences", "InnerProceduresFragment: onCreatePreferences")
            setPreferencesFromResource(R.xml.pref_procedures, rootKey)

            // SAF folder picker for procedures
            val emerPref = findPreference<Preference>(getString(R.string.pref_procedures_emer_folder))
            val normPref = findPreference<Preference>(getString(R.string.pref_procedures_norm_folder))
            Log.d("Preferences", "InnerProceduresFragment: emerPref=$emerPref normPref=$normPref")
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
            updateAllProceduresSummaries()
        }

        override fun onResume() {
            super.onResume()
            Log.d("Preferences", "InnerProceduresFragment: onResume — refreshing summaries")
            updateAllProceduresSummaries()
        }

        private fun updateAllProceduresSummaries() {
            val emerPref = findPreference<Preference>(getString(R.string.pref_procedures_emer_folder))
            val normPref = findPreference<Preference>(getString(R.string.pref_procedures_norm_folder))
            updateProceduresSummary(emerPref, getString(R.string.pref_procedures_emer_folder),
                getString(R.string.pref_procedures_emer_folder_summary))
            updateProceduresSummary(normPref, getString(R.string.pref_procedures_norm_folder),
                getString(R.string.pref_procedures_norm_folder_summary))
        }

        private fun updateProceduresSummary(pref: Preference?, key: String, defaultSummary: String) {
            val uriStr = preferenceScreen.sharedPreferences?.getString(key, null)
            pref?.summary = if (uriStr.isNullOrEmpty()) defaultSummary else uriStr
        }
    }

    class LocationSharingPreferencesFragment : BasePreferenceFragment() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            Log.d("Preferences", "LocationSharingPreferencesFragment: onCreatePreferences")
            setPreferencesFromResource(R.xml.pref_sharing, rootKey)
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
                val current = preferenceScreen.sharedPreferences?.getString(
                    getString(R.string.pref_onlinemap),
                    resources.getString(R.string.def_onlinemap)
                )
                var curProvider: TileProvider? = null
                for ((i, provider) in providers.withIndex()) {
                    entries[i] = provider.name
                    entryValues[i] = provider.code
                    if (current == provider.code)
                        curProvider = provider
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
                    ).versionName ?: "unable to retreive version"
                } catch (_: NameNotFoundException) {
                    "unable to retreive version"
                }
                val fmt = getString(R.string.version)
                @Suppress("StringFormatInvalid")
                versionLabel.text = String.format(fmt, versionName)
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
                val marketIntent = Intent(Intent.ACTION_VIEW, "market://details?id=com.borkozic.donate".toUri())
                startActivity(marketIntent)
                true
            }

            val prefDonatePaypal = findPreference<Preference>(getString(R.string.pref_donatepaypal))
            prefDonatePaypal?.setOnPreferenceClickListener {
                startActivity(Intent(Intent.ACTION_VIEW).setData(getString(R.string.paypaluri).toUri()))
                true
            }

            val application = requireActivity().application as Borkozic
            if (application.isPaid) {
                prefDonateGoogle?.let { preferenceScreen.removePreference(it) }
                prefDonatePaypal?.let { preferenceScreen.removePreference(it) }
            }

            val prefGooglePlus = findPreference<Preference>(getString(R.string.pref_googleplus))
            prefGooglePlus?.setOnPreferenceClickListener {
                startActivity(Intent(Intent.ACTION_VIEW).setData(getString(R.string.googleplusuri).toUri()))
                true
            }

            val prefFacebook = findPreference<Preference>(getString(R.string.pref_facebook))
            prefFacebook?.setOnPreferenceClickListener {
                startActivity(Intent(Intent.ACTION_VIEW).setData(getString(R.string.facebookuri).toUri()))
                true
            }

            val prefTwitter = findPreference<Preference>(getString(R.string.pref_twitter))
            prefTwitter?.setOnPreferenceClickListener {
                startActivity(Intent(Intent.ACTION_VIEW).setData(getString(R.string.twitteruri).toUri()))
                true
            }

            val prefFaq = findPreference<Preference>(getString(R.string.pref_faq))
            prefFaq?.setOnPreferenceClickListener {
                startActivity(Intent(Intent.ACTION_VIEW).setData(getString(R.string.faquri).toUri()))
                true
            }

            val prefFeature = findPreference<Preference>(getString(R.string.pref_feature))
            prefFeature?.setOnPreferenceClickListener {
                startActivity(Intent(Intent.ACTION_VIEW).setData(getString(R.string.featureuri).toUri()))
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
