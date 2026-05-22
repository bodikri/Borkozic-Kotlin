package com.borkozic

import android.annotation.TargetApi
import android.app.backup.BackupAgentHelper
import android.app.backup.SharedPreferencesBackupHelper
import android.os.Build

@TargetApi(Build.VERSION_CODES.FROYO)
class Backup : BackupAgentHelper() {
    companion object {
        // The name of the SharedPreferences file
        const val PREFS = "com.androzic_preferences"

        // A key to uniquely identify the set of backup data
        const val PREFS_BACKUP_KEY = "preferences"
    }

    // Allocate a helper and add it to the backup agent
    override fun onCreate() {
        val helper = SharedPreferencesBackupHelper(this, PREFS)
        addHelper(PREFS_BACKUP_KEY, helper)
    }
}
