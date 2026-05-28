package com.borkozic

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.util.Log
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
        private const val TAG = "Procedures_Text"
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
            Log.d(TAG, "safUri=$safUri, proceduresFolder=$proceduresFolder, planePath=${application?.planePath}")
            if (!safUri.isNullOrEmpty()) {
                // Read from SAF tree URI
                val treeUri = Uri.parse(safUri)
                val documentId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
                val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(
                    treeUri, documentId
                )
                var fileUri: Uri? = null
                Log.d(TAG, "Querying SAF for $xmlName in tree: $treeUri")
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
                val foundUri = fileUri
                Log.d(TAG, "SAF query result for $xmlName: foundUri=$foundUri")
                if (foundUri != null) {
                    htmlString = contentResolver.openInputStream(foundUri)!!.use { readInputStreamAsString(it) }
                    Log.d(TAG, "Read ${htmlString.length} chars from SAF: ${htmlString.take(100)}...")
                } else {
                    val msg = "File $xmlName not found in SAF folder $safUri"
                    Log.e(TAG, msg)
                    throw Exception(msg)
                }
            } else {
                // Read from file path
                val folder = proceduresFolder ?: application?.planePath
                Log.d(TAG, "Reading from file path: folder=$folder, file=$xmlName")
                val file = File(folder, xmlName)
                Log.d(TAG, "File exists=${file.exists()}, path=${file.absolutePath}")
                if (!file.exists()) {
                    val msg = "File ${file.absolutePath} does not exist"
                    Log.e(TAG, msg)
                    throw Exception(msg)
                }
                htmlString = readInputStreamAsString(FileInputStream(file))
                Log.d(TAG, "Read ${htmlString.length} chars from file: ${htmlString.take(100)}...")
            }
            Log.d(TAG, "Loading HTML into WebView, length=${htmlString.length}")
            webView.loadDataWithBaseURL(null, htmlString, "text/html", "utf-8", null)
            Log.d(TAG, "WebView loaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load procedure", e)
            // Show error in WebView
            webView.loadDataWithBaseURL(null,
                "<html><body style='padding:20px;color:red;font-size:16px'>" +
                "<h3>Error loading procedure</h3>" +
                "<p>${e.message}</p>" +
                "<p>File: $xmlName</p>" +
                "</body></html>",
                "text/html", "utf-8", null)
        }
    }
}
