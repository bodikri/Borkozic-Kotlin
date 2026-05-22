package com.borkozic

import android.text.TextUtils
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

class Log {
    companion object {
        @JvmField
        var logMode = 1

        const val LOG_MODE_FULL = 1
        const val LOG_MODE_LIGHT = 2
        const val LOG_MODE_NONE = 3

        @JvmStatic
        fun w(TAG: String, msg: String) {
            when (logMode) {
                LOG_MODE_FULL -> android.util.Log.w(TAG, getLocation() + msg)
                LOG_MODE_LIGHT -> android.util.Log.w(TAG, msg)
            }
        }

        private fun getLocation(): String {
            val className = Log::class.java.name
            val traces = Thread.currentThread().stackTrace
            var found = false

            for (i in traces.indices) {
                val trace = traces[i]

                try {
                    if (found) {
                        if (!trace.className.startsWith(className)) {
                            val clazz = Class.forName(trace.className)
                            return "[${getClassName(clazz)}:${trace.methodName}:${trace.lineNumber}]: "
                        }
                    } else if (trace.className.startsWith(className)) {
                        found = true
                        continue
                    }
                } catch (e: ClassNotFoundException) {
                    // Ignore
                }
            }

            return "[]: "
        }

        private fun getClassName(clazz: Class<*>?): String {
            if (clazz != null) {
                if (!TextUtils.isEmpty(clazz.simpleName)) {
                    return clazz.simpleName
                }

                return getClassName(clazz.enclosingClass)
            }

            return ""
        }
    }
}