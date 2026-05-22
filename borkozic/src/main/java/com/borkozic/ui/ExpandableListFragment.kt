/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.borkozic.ui

import android.os.Bundle
import android.os.Handler
import android.view.*
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.fragment.app.Fragment

open class ExpandableListFragment : Fragment(),
    View.OnCreateContextMenuListener,
    ExpandableListView.OnChildClickListener,
    ExpandableListView.OnGroupCollapseListener,
    ExpandableListView.OnGroupExpandListener {

    companion object {
        const val INTERNAL_EMPTY_ID = 0x00ff0001
        const val INTERNAL_PROGRESS_CONTAINER_ID = 0x00ff0002
        const val INTERNAL_LIST_CONTAINER_ID = 0x00ff0003
    }

    private val mHandler = Handler()

    private val mRequestFocus = Runnable {
        mExpandableList!!.focusableViewAvailable(mExpandableList)
    }

    private val mOnClickListener = AdapterView.OnItemClickListener { parent, v, position, id ->
        onListItemClick(parent as ExpandableListView, v, position, id)
    }

    private val mOnChildClickListener = ExpandableListView.OnChildClickListener { parent, v, groupPosition, childPosition, id ->
        onChildClick(parent, v, groupPosition, childPosition, id)
    }

    var mAdapter: BaseExpandableListAdapter? = null
    private var mExpandableList: ExpandableListView? = null
    private var mFinishedStart: Boolean = false
    private var mEmptyView: View? = null
    private var mStandardEmptyView: TextView? = null
    private var mProgressContainer: View? = null
    private var mExpandableListContainer: View? = null
    private var mEmptyText: CharSequence? = null
    private var mExpandableListShown: Boolean = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = activity ?: throw IllegalStateException()

        val root = FrameLayout(context)

        val pframe = LinearLayout(context)
        pframe.id = INTERNAL_PROGRESS_CONTAINER_ID
        pframe.orientation = LinearLayout.VERTICAL
        pframe.visibility = View.GONE
        pframe.gravity = Gravity.CENTER

        val progress = ProgressBar(context, null, android.R.attr.progressBarStyleLarge)
        pframe.addView(progress, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        root.addView(pframe, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val lframe = FrameLayout(context)
        lframe.id = INTERNAL_LIST_CONTAINER_ID

        val tv = TextView(context)
        tv.id = INTERNAL_EMPTY_ID
        tv.gravity = Gravity.CENTER
        lframe.addView(tv, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val lv = ExpandableListView(context)
        lv.id = android.R.id.list
        lv.setDrawSelectorOnTop(false)
        lframe.addView(lv, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        root.addView(lframe, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        root.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ensureList()
    }

    override fun onDestroyView() {
        mHandler.removeCallbacks(mRequestFocus)
        mExpandableList = null
        mExpandableListShown = false
        mEmptyView = null
        mProgressContainer = null
        mExpandableListContainer = null
        mStandardEmptyView = null
        super.onDestroyView()
    }

    open fun onListItemClick(l: ExpandableListView, v: View, position: Int, id: Long) {}

    fun setListAdapter(adapter: BaseExpandableListAdapter) {
        val hadAdapter = mAdapter != null
        mAdapter = adapter
        if (mExpandableList != null) {
            mExpandableList!!.setAdapter(adapter)
            if (!mExpandableListShown && !hadAdapter) {
                setListShown(true, view?.windowToken != null)
            }
        }
    }

    fun setSelection(position: Int) {
        ensureList()
        mExpandableList!!.setSelection(position)
    }

    fun getSelectedItemPosition(): Int {
        ensureList()
        return mExpandableList!!.selectedItemPosition
    }

    fun getSelectedItemId(): Long {
        ensureList()
        return mExpandableList!!.selectedItemId
    }

    fun getListView(): ExpandableListView {
        ensureList()
        return mExpandableList!!
    }

    fun setListContainer(view: View) {
        mExpandableListContainer = view
    }

    fun setProgressContainer(view: View) {
        mProgressContainer = view
    }

    fun setEmptyText(text: CharSequence) {
        ensureList()
        if (mStandardEmptyView == null) {
            throw IllegalStateException("Can't be used with a custom content view")
        }
        mStandardEmptyView!!.text = text
        if (mEmptyText == null) {
            mExpandableList!!.setEmptyView(mStandardEmptyView)
        }
        mEmptyText = text
    }

    fun setListShown(shown: Boolean) {
        setListShown(shown, true)
    }

    fun setListShownNoAnimation(shown: Boolean) {
        setListShown(shown, false)
    }

    private fun setListShown(shown: Boolean, animate: Boolean) {
        ensureList()
        if (mProgressContainer == null) {
            throw IllegalStateException("Can't be used with a custom content view")
        }
        if (mExpandableListShown == shown) return
        mExpandableListShown = shown
        if (shown) {
            if (animate) {
                mProgressContainer!!.startAnimation(AnimationUtils.loadAnimation(activity, android.R.anim.fade_out))
                mExpandableListContainer!!.startAnimation(AnimationUtils.loadAnimation(activity, android.R.anim.fade_in))
            } else {
                mProgressContainer!!.clearAnimation()
                mExpandableListContainer!!.clearAnimation()
            }
            mProgressContainer!!.visibility = View.GONE
            mExpandableListContainer!!.visibility = View.VISIBLE
        } else {
            if (animate) {
                mProgressContainer!!.startAnimation(AnimationUtils.loadAnimation(activity, android.R.anim.fade_in))
                mExpandableListContainer!!.startAnimation(AnimationUtils.loadAnimation(activity, android.R.anim.fade_out))
            } else {
                mProgressContainer!!.clearAnimation()
                mExpandableListContainer!!.clearAnimation()
            }
            mProgressContainer!!.visibility = View.VISIBLE
            mExpandableListContainer!!.visibility = View.GONE
        }
    }

    fun getListAdapter(): BaseExpandableListAdapter? = mAdapter

    private fun ensureList() {
        if (mExpandableList != null) return
        val root = view ?: throw IllegalStateException("Content view not yet created")
        if (root is ExpandableListView) {
            mExpandableList = root
        } else {
            mStandardEmptyView = root.findViewById<TextView>(INTERNAL_EMPTY_ID)
            if (mStandardEmptyView == null) {
                mEmptyView = root.findViewById(android.R.id.empty)
            } else {
                mStandardEmptyView!!.visibility = View.GONE
            }
            if (mProgressContainer == null)
                mProgressContainer = root.findViewById(INTERNAL_PROGRESS_CONTAINER_ID)
            if (mExpandableListContainer == null)
                mExpandableListContainer = root.findViewById(INTERNAL_LIST_CONTAINER_ID)
            val rawExpandableListView = root.findViewById<View>(android.R.id.list)
            if (rawExpandableListView !is ExpandableListView) {
                throw RuntimeException(
                    if (rawExpandableListView == null) {
                        "Your content must have a ListView whose id attribute is 'android.R.id.list'"
                    } else {
                        "Content has view with id attribute 'android.R.id.list' that is not a ListView class"
                    }
                )
            }
            mExpandableList = rawExpandableListView
            if (mEmptyView != null) {
                mExpandableList!!.setEmptyView(mEmptyView)
            } else if (mEmptyText != null) {
                mStandardEmptyView!!.text = mEmptyText
                mExpandableList!!.setEmptyView(mStandardEmptyView)
            }
        }
        mExpandableListShown = true
        mExpandableList!!.setOnItemClickListener(mOnClickListener)
        mExpandableList!!.setOnChildClickListener(mOnChildClickListener)
        if (mAdapter != null) {
            val adapter = mAdapter!!
            mAdapter = null
            setListAdapter(adapter)
        } else {
            if (mProgressContainer != null) {
                setListShown(false, false)
            }
        }
        mHandler.post(mRequestFocus)
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {}

    override fun onChildClick(parent: ExpandableListView, v: View, groupPosition: Int, childPosition: Int, id: Long): Boolean = false

    override fun onGroupCollapse(groupPosition: Int) {}

    override fun onGroupExpand(groupPosition: Int) {}

    fun onContentChanged() {
        val emptyView = view?.findViewById<View>(android.R.id.empty)
        mExpandableList = view?.findViewById<ExpandableListView>(android.R.id.list)
            ?: throw RuntimeException("Your content must have a ExpandableListView whose id attribute is 'android.R.id.list'")
        if (emptyView != null) {
            mExpandableList!!.setEmptyView(emptyView)
        }
        mExpandableList!!.setOnChildClickListener(this)
        mExpandableList!!.setOnGroupExpandListener(this)
        mExpandableList!!.setOnGroupCollapseListener(this)

        if (mFinishedStart) {
            setListAdapter(mAdapter!!)
        }
        mFinishedStart = true
    }

    fun getExpandableListView(): ExpandableListView {
        ensureList()
        return mExpandableList!!
    }

    fun getExpandableListAdapter(): ExpandableListAdapter? = mAdapter

    fun getSelectedId(): Long = mExpandableList!!.selectedId

    fun getSelectedPosition(): Long = mExpandableList!!.selectedPosition

    fun setSelectedChild(groupPosition: Int, childPosition: Int, shouldExpandGroup: Boolean): Boolean =
        mExpandableList!!.setSelectedChild(groupPosition, childPosition, shouldExpandGroup)

    fun setSelectedGroup(groupPosition: Int) {
        mExpandableList!!.setSelectedGroup(groupPosition)
    }
}
