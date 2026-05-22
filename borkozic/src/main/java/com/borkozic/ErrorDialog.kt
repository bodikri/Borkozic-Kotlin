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

import android.app.Activity
import android.os.Bundle
import android.text.Html
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.TextView

class ErrorDialog : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_LEFT_ICON)
        setContentView(R.layout.act_error)

        val title = intent.getStringExtra("title")
        setTitle(title)
        setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, android.R.drawable.ic_dialog_alert)

        val message = intent.getStringExtra("message")
        findViewById<TextView>(R.id.message).setText(Html.fromHtml(message))

        findViewById<Button>(R.id.ok_button).setOnClickListener { finish() }
    }
}
