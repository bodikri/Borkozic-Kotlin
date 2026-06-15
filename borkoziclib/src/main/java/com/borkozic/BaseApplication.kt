/*
 * Borkozic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2012 Andrey Novikov <http://andreynovikov.info/>
 * 
 * This file is part of Borkozic application.
 * 
 * Borkozic is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Borkozic is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Borkozic. If not, see <http://www.gnu.org/licenses/>.
 */
package com.borkozic

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.preference.PreferenceManager
import java.util.Locale

abstract class BaseApplication : Application() {
    abstract val rootPath: String?

    companion object {
        private var self: BaseApplication? = null

        /** Saved locale from attachBaseContext — available before any Activity onCreate. */
        var savedLocale: Locale? = null
            private set

        @JvmStatic
        fun <T : BaseApplication?> getApplication(): T? {
            return self as T?
        }

        @JvmStatic
        protected fun setInstance(instance: BaseApplication?) {
            self = instance
        }

        @JvmStatic
        val deviceName: String
            get() {
                val manufacturer = Build.MANUFACTURER
                val model = Build.MODEL
                return if (model.startsWith(manufacturer)) capitalize(model)
                else capitalize(manufacturer) + " " + model
            }

        private fun capitalize(s: String?): String {
            if (s.isNullOrEmpty()) return ""
            val first = s[0]
            return if (Character.isUpperCase(first)) s
            else first.uppercaseChar().toString() + s.substring(1)
        }
    }

    override fun attachBaseContext(base: Context?) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(base!!)
        val lang = prefs.getString("locale", "") ?: ""
        if (lang.isNotEmpty()) {
            val locale = Locale(lang)
            Locale.setDefault(locale)
            savedLocale = locale  // persist for Activity onCreate without SharedPreferences access
            val config = Configuration(base.resources.configuration)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                config.setLocale(locale)
            } else {
                @Suppress("DEPRECATION")
                config.locale = locale
            }
            @Suppress("DEPRECATION")
            super.attachBaseContext(base.createConfigurationContext(config))
            return
        }
        savedLocale = null
        super.attachBaseContext(base)
    }
}