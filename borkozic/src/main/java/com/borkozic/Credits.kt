package com.borkozic

import android.app.ListActivity
import android.os.Bundle
import android.widget.SimpleAdapter

class Credits : ListActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val names = resources.getStringArray(R.array.credit_names)
        val merits = resources.getStringArray(R.array.credit_merits)

        val data = mutableListOf<MutableMap<String, String>>()

        for (i in names.indices) {
            val group = mutableMapOf<String, String>()
            group["NAME"] = names[i]
            group["MERIT"] = merits[i]
            data.add(group)
        }

        listAdapter = SimpleAdapter(
            this@Credits, data,
            android.R.layout.simple_list_item_2,
            arrayOf("NAME", "MERIT"),
            intArrayOf(android.R.id.text1, android.R.id.text2)
        )
        listView.setItemsCanFocus(false)
    }
}
