package com.borkozic

import android.app.Activity
import android.content.pm.ActivityInfo
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
        val xmlName = "$btnName.txt"

        try {
            val stream = FileInputStream(File(application?.planePath, xmlName))
            val htmlString = readInputStreamAsString(stream)
            webView.loadDataWithBaseURL(null, htmlString, "text/html", "utf-8", null)
        } catch (e: Exception) {
            // TODO Show user what is wrong
        }
    }
}
