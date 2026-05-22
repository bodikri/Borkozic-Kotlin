package com.borkozic.ui

import android.app.Activity
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckedTextView
import android.widget.ImageView
import com.borkozic.R

/**
 * The ImageArrayAdapter is the array adapter used for displaying an additional
 * image to a list preference item.
 *
 * @author Casper Wakkers
 */
class ImageArrayAdapter(
    context: Context,
    textViewResourceId: Int,
    objects: Array<CharSequence>,
    private var resourceIds: IntArray?,
    private var index: Int
) : ArrayAdapter<CharSequence>(context, textViewResourceId, objects) {

    /**
     * {@inheritDoc}
     */
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val inflater = (context as Activity).layoutInflater
        val row = inflater.inflate(R.layout.imagemultichoicelistitem, parent, false)

        val imageView = row.findViewById<ImageView>(R.id.image)
        imageView.setImageResource(resourceIds?.get(position) ?: 0)

        val checkedTextView = row.findViewById<CheckedTextView>(R.id.check)
        checkedTextView.text = getItem(position)

        if (position == index) {
            checkedTextView.isChecked = true
        }
        return row
    }
}
