package com.ericharlow.DragNDrop

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import java.util.ArrayList

open class DragNDropAdapter() : BaseAdapter(), RemoveListener, DropListener {

    private var mIds: IntArray = intArrayOf()
    private var mLayouts: IntArray = intArrayOf()
    private var mInflater: LayoutInflater? = null
    private var mContent: ArrayList<String>? = null

    constructor(context: Context, content: ArrayList<String>) : this() {
        init(context, intArrayOf(android.R.layout.simple_list_item_1), intArrayOf(android.R.id.text1), content)
    }

    constructor(context: Context, itemLayouts: IntArray, itemIDs: IntArray, content: ArrayList<String>) : this() {
        init(context, itemLayouts, itemIDs, content)
    }

    private fun init(context: Context, layouts: IntArray, ids: IntArray, content: ArrayList<String>) {
        mInflater = LayoutInflater.from(context)
        mIds = ids
        mLayouts = layouts
        mContent = content
    }

    override fun getCount(): Int {
        return mContent!!.size
    }

    override fun getItem(position: Int): String {
        return mContent!!.get(position)
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var cv = convertView
        val holder: ViewHolder

        if (cv == null) {
            cv = mInflater!!.inflate(mLayouts[0], null)

            holder = ViewHolder()
            holder.text = cv.findViewById<TextView>(mIds[0])

            cv.tag = holder
        } else {
            holder = cv.tag as ViewHolder
        }

        holder.text!!.text = mContent!!.get(position)

        return cv!!
    }

    inner class ViewHolder {
        var text: TextView? = null
    }

    override fun onRemove(which: Int) {
        if (which < 0 || which > mContent!!.size) return
        mContent!!.removeAt(which)
    }

    override fun onDrop(from: Int, to: Int) {
        val temp = mContent!!.get(from)
        mContent!!.removeAt(from)
        mContent!!.add(to, temp)
    }
}
