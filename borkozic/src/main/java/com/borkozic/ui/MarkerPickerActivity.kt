package com.borkozic.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.AdapterView.OnItemLongClickListener
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.Toast
import com.borkozic.BaseApplication
import com.borkozic.Borkozic
import com.borkozic.R
import java.io.File
import java.io.FilenameFilter
import java.util.ArrayList
import java.util.Collections

class MarkerPickerActivity : Activity(), OnItemClickListener, OnItemLongClickListener {
    private lateinit var grid: GridView
    private var names: MutableList<String> = ArrayList()
    private var icons: MutableList<Bitmap> = ArrayList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.act_markericon)

        val application = BaseApplication.getApplication<Borkozic>()!!
        val dir = File(application.iconPath!!)

        val result = ArrayList<File>()

        val files = dir.listFiles(iconFilter)
        if (files != null) result.addAll(files)
        Collections.sort(result)

        for (file in result) {
            val b = BitmapFactory.decodeFile(file.absolutePath)
            if (b != null) {
                names.add(file.name)
                icons.add(b)
            }
        }

        grid = findViewById(R.id.marker_grid)
        grid.adapter = ImageAdapter(this, icons)
        grid.onItemClickListener = this
        grid.onItemLongClickListener = this
    }

    override fun onDestroy() {
        super.onDestroy()
        for (b in icons) {
            b.recycle()
        }
        names.clear()
        icons.clear()
    }

    override fun onItemClick(parent: AdapterView<*>, view: View, position: Int, id: Long) {
        setResult(RESULT_OK, Intent().putExtra("icon", names[position]))
        finish()
    }

    override fun onItemLongClick(parent: AdapterView<*>, view: View, position: Int, id: Long): Boolean {
        val name = names[position]
        Toast.makeText(this, name.substring(0, name.lastIndexOf(".")), Toast.LENGTH_SHORT).show()
        return true
    }

    private inner class ImageAdapter(
        private val context: Context,
        private val images: List<Bitmap>
    ) : BaseAdapter() {
        override fun getCount(): Int = images.size

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView as? ImageView ?: ImageView(context)
            view.setImageBitmap(images[position])
            return view
        }

        override fun getItem(position: Int): Any? = null
        override fun getItemId(position: Int): Long = 0
    }

    private val iconFilter = FilenameFilter { _, filename ->
        filename.lowercase().endsWith(".png")
    }
}
