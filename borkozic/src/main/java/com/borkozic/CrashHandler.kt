package com.borkozic

import android.app.NotificationManager
import android.content.Context
import java.io.BufferedWriter
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.Thread.UncaughtExceptionHandler

class CrashHandler(context: Context, localPath: String) : UncaughtExceptionHandler {

    private var defaultUEH: UncaughtExceptionHandler? = null
    private var notificationManager: NotificationManager? = null
    private var localPath: String? = null

    init {
        this.localPath = localPath
        this.defaultUEH = Thread.getDefaultUncaughtExceptionHandler()
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun uncaughtException(t: Thread, e: Throwable) {
        val result = StringWriter()
        val printWriter = PrintWriter(result)
        e.printStackTrace(printWriter)
        val stacktrace = result.toString()
        printWriter.close()
        val filename = "Androzic_" + System.currentTimeMillis() + ".crash"

        if (localPath != null) {
            writeToFile(stacktrace, filename)
        }

        val nm = notificationManager
        if (nm != null) {
            try {
                for (id in ANDROZIC_NOTIFICATION_IDS) {
                    nm.cancel(id)
                }
            } catch (ex: Throwable) {
                ex.printStackTrace()
            }
        }
        notificationManager = null

        defaultUEH?.uncaughtException(t, e)
    }

    private fun writeToFile(stacktrace: String, filename: String) {
        try {
            val bos = BufferedWriter(FileWriter(localPath + "/" + filename))
            bos.write(stacktrace)
            bos.flush()
            bos.close()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    companion object {
        private val ANDROZIC_NOTIFICATION_IDS = intArrayOf(24161, 24162, 24163)
    }
}
