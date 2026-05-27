package com.borkozic

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.webkit.WebView
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream

class Procedures_Text : Activity() {
    protected var btnName: String? = null
    protected var application: Borkozic? = null

    companion object {
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.act__procedures__txt)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        application = getApplication() as Borkozic

        val webView = findViewById<WebView>(R.id.webView)

        btnName = intent.getStringExtra(BtnsProceduresSet.BTNS_TITLE)
        val proceduresFolder = intent.getStringExtra(BtnsProceduresSet.PROCEDURES_FOLDER)
        val safUri = intent.getStringExtra("SAF_URI")
        val xmlName = "$btnName.txt"

        try {
            val htmlString: String
            if (!safUri.isNullOrEmpty()) {
                // Read from SAF tree URI
                val treeUri = Uri.parse(safUri)
                val documentId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
                val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(
                    treeUri, documentId
                )
                var fileUri: Uri? = null
                contentResolver.query(
                    childrenUri,
                    arrayOf(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                    null, null, null
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(
                            cursor.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                        )
                        if (name == xmlName) {
                            val childDocId = cursor.getString(
                                cursor.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                            )
                            fileUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
                            break
                        }
                    }
                }
                if (fileUri != null) {
                    htmlString = contentResolver.openInputStream(fileUri)!!.use { readInputStreamAsString(it) }
                } else {
                    throw Exception("File $xmlName not found in SAF folder")
                }
            } else {
                // Read from file path
                val file = File(proceduresFolder ?: application?.planePath, xmlName)
                htmlString = readInputStreamAsString(FileInputStream(file))
            }
            webView.loadDataWithBaseURL(null, htmlString, "text/html", "utf-8", null)
        } catch (e: Exception) {
            // TODO Show user what is wrong
        }
    }
}
