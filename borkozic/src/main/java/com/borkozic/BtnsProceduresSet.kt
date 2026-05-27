package com.borkozic

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.preference.PreferenceManager
import android.provider.DocumentsContract
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.act_btn_procedures)

        application = getApplication() as Borkozic
        val ll = findViewById<LinearLayout>(R.id.linearLayotSet)
        btnName = intent.getStringExtra(MapActivity.BTN_TITLE)!!
        setTitle("$btnName Procedures List")
        Log.d(TAG, "ReceiveBtn: $btnName")

        val settings = PreferenceManager.getDefaultSharedPreferences(this)
        val prefKey = when (btnName) {
            getString(R.string.buttonEP) -> getString(R.string.pref_procedures_emer_folder)
            getString(R.string.buttonNP) -> getString(R.string.pref_procedures_norm_folder)
            else -> null
        }
        Log.d(TAG, "prefKey=$prefKey, btnName=$btnName")

        var storedUriStr = prefKey?.let { settings.getString(it, null) }
        val planePath = application.planePath ?: ""

        // Validate legacy stored path — if not a content:// URI and file doesn't exist, fall back
        if (!storedUriStr.isNullOrEmpty() && !storedUriStr.startsWith("content://")) {
            val testFile = File(storedUriStr, "$btnName.txt")
            if (!testFile.exists()) {
                Log.w(TAG, "Legacy path invalid or incomplete: $storedUriStr -> falling back to planePath")
                storedUriStr = null
            }
        }

        Log.d(TAG, "prefKey=$prefKey, btnName=$btnName storedUriStr=$storedUriStr planePath=$planePath")

        var btnsString = ""
        try {
            if (!storedUriStr.isNullOrEmpty()) {
                val uri = Uri.parse(storedUriStr)
                if (uri.scheme == "content") {
                    // Read from SAF tree URI
                    val childUri = findChildUri(uri, "$btnName.txt")
                    if (childUri != null) {
                        contentResolver.openInputStream(childUri)?.use { stream ->
                            btnsString = readInputStreamAsString(stream)
                        }
                    }
                } else {
                    // Legacy: raw file path
                    val file = File(storedUriStr, "$btnName.txt")
                    Log.d(TAG, "Legacy file read: ${file.absolutePath}, exists=${file.exists()}")
                    if (file.exists()) {
                        btnsString = readInputStreamAsString(FileInputStream(file))
                    }
                }
            }
            // Fallback to planePath
            if (btnsString.isEmpty()) {
                val file = File(planePath, "$btnName.txt")
                Log.d(TAG, "Fallback reading: ${file.absolutePath}, exists=${file.exists()}")
                if (file.exists()) {
                    btnsString = readInputStreamAsString(FileInputStream(file))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read procedures file", e)
            Toast.makeText(this,
                if (!storedUriStr.isNullOrEmpty()) "Не може да се прочете от избраната папка: $storedUriStr"
                else "Няма избрана папка и няма процедури в $planePath",
                Toast.LENGTH_LONG).show()
            finish()
            return
        }

        if (btnsString.isEmpty()) {
            Toast.makeText(this, "Файлът $btnName.txt е празен или не съществува", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Determine proceduresFolder for sub-procedure files
        val proceduresFolderUri = storedUriStr
        val proceduresFolderPath = if (proceduresFolderUri.isNullOrEmpty()) planePath else null

        // Split into buttons
        val splitbtns = btnsString.split(";").filter { it.contains(":") }.toTypedArray()
        Log.d(TAG, "Parsing ${splitbtns.size} procedure entries from content: ${btnsString.take(200)}...")

        for (element in splitbtns) {
            val splitbtnName = element.split(":").toTypedArray()

            try {
                if (splitbtnName.size < 2) {
                    Log.w(TAG, "Skipping invalid line (no colon): '$element'")
                    continue
                }
                val btn_txt = splitbtnName[0].replace("\n", "").replace("\r", "").trim()
                if (btn_txt.isEmpty()) {
                    Log.w(TAG, "Skipping line with empty button text")
                    continue
                }
                val btnID = splitbtnName[1].trim().toIntOrNull()
                if (btnID == null) {
                    Log.w(TAG, "Skipping line with invalid ID: '${splitbtnName[1]}'")
                    continue
                }

                val b = Button(this)
                b.text = btn_txt
                b.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                b.id = btnID
                b.setOnClickListener {
                    val btnsIntent = Intent(this@BtnsProceduresSet, Procedures_Text::class.java)
                    btnsIntent.putExtra(BTNS_TITLE, btnID.toString())
                    btnsIntent.putExtra(PROCEDURES_FOLDER, proceduresFolderPath)
                    btnsIntent.putExtra("SAF_URI", proceduresFolderUri)
                    startActivity(btnsIntent)
                }
                ll.addView(b)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create button for: $element", e)
                // skip this button, don't finish
            }
        }
    }

    /**
     * Find a child file by name under a SAF tree URI using DocumentsContract.
     */
    private fun findChildUri(treeUri: Uri, fileName: String): Uri? {
        val documentId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri, documentId
        )
        contentResolver.query(
            childrenUri,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(
                    cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                )
                if (name == fileName) {
                    val childDocId = cursor.getString(
                        cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    )
                    return DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
                }
            }
        }
        return null
    }
}
