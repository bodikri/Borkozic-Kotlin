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

package com.borkozic.ui.preference

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.preference.PreferenceViewHolder
import com.borkozic.library.R
import com.borkozic.ui.QuickView
import com.borkozic.ui.SeekbarPreference

class HelpfulSeekbarPreference : SeekbarPreference {
    private var helpClickListener: OnClickListener? = null
    private var summary: CharSequence? = null
    private var helpView: QuickView? = null

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        summary = summary
        setSummary(null)
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val view = holder.itemView
        if (summary != null) {
            helpView = QuickView(context)
            helpView?.setText(summary)

            val helpImage = ImageView(context)
            val widgetFrameView = view.findViewById<View>(android.R.id.widget_frame) as ViewGroup? ?: return

            widgetFrameView.visibility = View.VISIBLE
            val rightPaddingDip = if (android.os.Build.VERSION.SDK_INT < 14) 8 else 5
            val mDensity = context.resources.displayMetrics.density
            if (widgetFrameView is LinearLayout) {
                widgetFrameView.orientation = LinearLayout.HORIZONTAL
            }
            widgetFrameView.addView(helpImage, 0)
            helpImage.setImageResource(R.drawable.ic_action_info)
            helpImage.setPadding(
                helpImage.paddingLeft,
                helpImage.paddingTop,
                (mDensity * rightPaddingDip).toInt(),
                helpImage.paddingBottom
            )
            helpImage.setOnClickListener {
                if (helpClickListener != null) {
                    helpClickListener?.onClick(helpImage)
                } else {
                    helpView?.show(helpImage)
                }
            }
        }
    }

    fun setOnHelpClickListener(l: OnClickListener?) {
        helpClickListener = l
    }
}