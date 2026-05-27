package com.borkozic

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.provider.DocumentsContract
import android.util.Log

/**
 * Helper activity that launches ACTION_OPEN_DOCUMENT_TREE for selecting a procedures folder.
 * Receives the preference key via Intent extra and stores the tree URI in SharedPreferences.
 */
class ProceduresFolderPickerActivity : Activity() {

    companion object {
        private const val TAG = "ProceduresFolderPicker"
        const val EXTRA_PREF_KEY = "pref_key"
        const val REQUEST_CODE = 4242
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefKey = intent.getStringExtra(EXTRA_PREF_KEY)
        if (prefKey.isNullOrEmpty()) {
            Log.e(TAG, "No pref_key provided")
            finish()
            return
        }

        Log.d(TAG, "Launching SAF tree picker for key=$prefKey")

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            // Show only the real filesystem, not virtual providers
            putExtra("android.content.extra.SHOW_ADVANCED", true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        }

        startActivityForResult(intent, REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        val prefKey = intent.getStringExtra(EXTRA_PREF_KEY) ?: return

        if (requestCode == REQUEST_CODE && resultCode == RESULT_OK) {
            val treeUri = data?.data
            if (treeUri != null) {
                // Take persistent permission
                contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )

                // Save URI to preferences
                PreferenceManager.getDefaultSharedPreferences(this)
                    .edit()
                    .putString(prefKey, treeUri.toString())
                    .apply()

                Log.d(TAG, "Saved tree URI for $prefKey: $treeUri")
            }
        }

        finish()
    }
}
