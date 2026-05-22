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

package com.borkozic.ui

import android.content.Context
import android.graphics.Rect
import android.text.Html
import android.text.util.Linkify
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import com.borkozic.library.R
import net.londatiga.android.PopupWindows

class QuickView(context: Context) : PopupWindows(context), PopupWindow.OnDismissListener {

    private lateinit var mArrowUp: ImageView
    private lateinit var mArrowDown: ImageView
    private val inflater: LayoutInflater
    private lateinit var mText: TextView
    private var mDismissListener: OnDismissListener? = null

    private var arrowWidth = 0
    private var mAnimStyle: Int

    companion object {
        const val ANIM_GROW_FROM_LEFT = 1
        const val ANIM_GROW_FROM_RIGHT = 2
        const val ANIM_GROW_FROM_CENTER = 3
        const val ANIM_AUTO = 4
    }

    init {
        inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

        setRootViewId(R.layout.quickview)

        mAnimStyle = ANIM_AUTO
    }

    fun setText(text: CharSequence?) {
        if (text != null && mText != null) {
            mText.text = Html.fromHtml(text.toString().replace("\n", "<br/>"))
            Linkify.addLinks(mText, Linkify.ALL)
        }
    }

    fun setRootViewId(id: Int) {
        mRootView = inflater.inflate(id, null) as ViewGroup
        mText = mRootView.findViewById(R.id.text)
        mArrowDown = mRootView.findViewById(R.id.arrow_down)
        mArrowUp = mRootView.findViewById(R.id.arrow_up)
        mRootView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        setContentView(mRootView)
    }

    fun setAnimStyle(animStyle: Int) {
        this.mAnimStyle = animStyle
    }

    fun show(anchor: View) {
        preShow()

        val location = IntArray(2)

        anchor.getLocationOnScreen(location)

        val anchorRect = Rect(
            location[0] + anchor.paddingLeft,
            location[1] + anchor.paddingTop,
            location[0] + anchor.width - anchor.paddingRight,
            location[1] + anchor.height - anchor.paddingBottom
        )

        val screenWidth = mWindowManager.defaultDisplay.width
        val screenHeight = mWindowManager.defaultDisplay.height

        mRootView.measure(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        if (arrowWidth == 0) {
            arrowWidth = mArrowUp.measuredWidth
        }

        val rootWidth = if (screenWidth > screenHeight) screenWidth / 2 else screenWidth * 2 / 3
        val rootHeight = mRootView.measuredHeight
        val x = anchorRect.width() / 2

        var xPos = anchorRect.left + x - rootWidth / 2
        var yPos = 0
        var arrowPos = rootWidth / 2
        if (xPos < 0) {
            xPos = 0
            arrowPos = x
        }
        if ((xPos + rootWidth) > screenWidth) {
            xPos = screenWidth - rootWidth
            arrowPos = anchorRect.left + x - xPos
        }
        if ((arrowPos - arrowWidth / 2) < 0)
            arrowPos = arrowWidth / 2
        if ((arrowPos + xPos + arrowWidth / 2) > screenWidth)
            arrowPos = rootWidth - arrowWidth / 2

        val dyTop = anchorRect.top + anchorRect.height()

        val onTop = anchorRect.height() + rootHeight > screenHeight

        if (onTop) {
            yPos = dyTop - rootHeight
        } else {
            yPos = dyTop
        }

        showArrow((if (onTop) R.id.arrow_down else R.id.arrow_up), arrowPos)

        setAnimationStyle(screenWidth, anchorRect.centerX(), onTop)

        mWindow.showAtLocation(anchor, Gravity.NO_GRAVITY, xPos, yPos)
        mWindow.update(rootWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun setAnimationStyle(screenWidth: Int, requestedX: Int, onTop: Boolean) {
        val arrowPos = requestedX - mArrowUp.measuredWidth / 2

        when (mAnimStyle) {
            ANIM_GROW_FROM_LEFT ->
                mWindow.animationStyle =
                    if (onTop) R.style.Animations_PopUpMenu_Left else R.style.Animations_PopDownMenu_Left

            ANIM_GROW_FROM_RIGHT ->
                mWindow.animationStyle =
                    if (onTop) R.style.Animations_PopUpMenu_Right else R.style.Animations_PopDownMenu_Right

            ANIM_GROW_FROM_CENTER ->
                mWindow.animationStyle =
                    if (onTop) R.style.Animations_PopUpMenu_Center else R.style.Animations_PopDownMenu_Center

            ANIM_AUTO ->
                if (arrowPos <= screenWidth / 4) {
                    mWindow.animationStyle =
                        if (onTop) R.style.Animations_PopUpMenu_Left else R.style.Animations_PopDownMenu_Left
                } else if (arrowPos > screenWidth / 4 && arrowPos < 3 * (screenWidth / 4)) {
                    mWindow.animationStyle =
                        if (onTop) R.style.Animations_PopUpMenu_Center else R.style.Animations_PopDownMenu_Center
                } else {
                    mWindow.animationStyle =
                        if (onTop) R.style.Animations_PopDownMenu_Right else R.style.Animations_PopDownMenu_Right
                }
        }
    }

    private fun showArrow(whichArrow: Int, requestedX: Int) {
        val showArrow: View = if (whichArrow == R.id.arrow_up) mArrowUp else mArrowDown
        val hideArrow: View = if (whichArrow == R.id.arrow_up) mArrowDown else mArrowUp

        val arrowWidth = mArrowUp.measuredWidth

        showArrow.visibility = View.VISIBLE

        val param = showArrow.layoutParams as ViewGroup.MarginLayoutParams

        param.leftMargin = requestedX - arrowWidth / 2

        hideArrow.visibility = View.INVISIBLE
    }

    fun setOnDismissListener(listener: OnDismissListener?) {
        setOnDismissListener(this)

        mDismissListener = listener
    }

    override fun onDismiss() {
        mDismissListener?.onDismiss()
    }

    interface OnDismissListener {
        fun onDismiss()
    }
}
