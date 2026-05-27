package com.borkozic

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream

class BtnsProceduresSet : Activity() {

    companion object {
        private const val TAG = "BtnsProceduresSet"
        const val BTNS_TITLE = "ButonsTitle"
        const val PROCEDURES_FOLDER = "ProceduresFolder"

        @Throws(IOException::class)
        fun readInputStreamAsString(`in`: InputStream): String {
            val bis = BufferedInputStream(`in`)
            val buf = ByteArrayOutputStream()
            var result = bis.read()
            while (result != -1) {
                val b = result.toByte()
                buf.write(b.toInt())
                result = bis.read()
            }
            return buf.toString()
        }
    }

    private lateinit var btnName: String
    private lateinit var application: Borkozic

    /* Идея:
        изпращам информация чрез бутона кой xml файл трябва да зареди за да се покаже
        на стартираното активити.
         Всеки бутон трябва да съдържа тази информация.(как?)
         Цел първа е как да заредя бутоните от файл без да ги
         описвам програмно, като във този файл трябва да има за всеки бутон
         отпратка към съответния файл който трябва да зареди
         */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.act_btn_procedures)

        application = getApplication() as Borkozic
        val ll = findViewById<LinearLayout>(R.id.linearLayotSet)
        btnName = intent.getStringExtra(MapActivity.BTN_TITLE)!! // Receive btn Number
        setTitle("$btnName Procedures List")
        Log.d(TAG, "ReceiveBtn: $btnName")

        // Read procedures folder from preferences
        val settings = PreferenceManager.getDefaultSharedPreferences(this)
        val prefKey = when (btnName) {
            getString(R.string.buttonEP) -> getString(R.string.pref_procedures_emer_folder)
            getString(R.string.buttonNP) -> getString(R.string.pref_procedures_norm_folder)
            else -> null
        }
        val storedFolder = prefKey?.let { settings.getString(it, null) }
        val baseFolder = if (storedFolder.isNullOrEmpty()) application.planePath else storedFolder
        val proceduresFile = File(baseFolder, "$btnName.txt").absolutePath
        Log.d(TAG, "proceduresFile: $proceduresFile (storedFolder=$storedFolder, btn=$btnName)")

        var btnsString = ""
        try {
            val infilestream = FileInputStream(File(proceduresFile))
            btnsString = readInputStreamAsString(infilestream)
        } catch (e: Exception) {
            Toast.makeText(
                this@BtnsProceduresSet,
                "Папката за процедури е празна или пътя към нея е неточен!",
                Toast.LENGTH_LONG
            ).show()
        }

        //Следва код който да разделя на отделни бутони
        val splitbtns = btnsString.split(";").toTypedArray()
        val proceduresFolder = File(proceduresFile).parent ?: application.planePath

        for (element in splitbtns) {
            val splitbtnName = element.split(":").toTypedArray()

            // add buttons
            try {
                val b = Button(this)
                val btn_txt = splitbtnName[0].replace(System.getProperty("line.separator") ?: "\n", "")
                b.text = btn_txt
                b.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                val btnID = splitbtnName[1].trim().toInt()
                b.id = btnID
                b.setOnClickListener {
                    val btnsIntent = Intent(this@BtnsProceduresSet, Procedures_Text::class.java)
                    btnsIntent.putExtra(BTNS_TITLE, btnID.toString())
                    btnsIntent.putExtra(PROCEDURES_FOLDER, proceduresFolder)
                    startActivity(btnsIntent)
                }
                ll.addView(b)
            } catch (e: Exception) {
                Toast.makeText(
                    this@BtnsProceduresSet,
                    "Избраната папка е: ${application.planePath}",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }
}
