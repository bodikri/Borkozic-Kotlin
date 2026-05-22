package com.borkozic.util

import android.annotation.TargetApi
import android.content.ClipData
import android.content.Context
import android.os.Build

object Clipboard {

    private val IMPL: ClipboardImpl = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
        ClipboardImplHoneyComb()
    } else {
        ClipboardImplBase()
    }

    fun copy(context: Context, text: String) {
        IMPL.copy(context, text)
    }

    fun paste(context: Context): String? {
        return IMPL.paste(context)
    }

    private interface ClipboardImpl {
        fun copy(context: Context, text: String)
        fun paste(context: Context): String?
    }

    @TargetApi(Build.VERSION_CODES.ECLAIR)
    private class ClipboardImplBase : ClipboardImpl {

        @Suppress("DEPRECATION")
        override fun copy(context: Context, text: String) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.text.ClipboardManager?
            clipboard?.setText(text)
        }

        @Suppress("DEPRECATION")
        override fun paste(context: Context): String? {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.text.ClipboardManager?
            return clipboard?.text as String?
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private class ClipboardImplHoneyComb : ClipboardImpl {

        override fun copy(context: Context, text: String) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager?
            val clip = ClipData.newPlainText("text", text)
            clipboard?.setPrimaryClip(clip)
        }

        override fun paste(context: Context): String? {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager?
            if (clipboard != null && clipboard.primaryClip != null && clipboard.primaryClip!!.itemCount > 0) {
                return clipboard.primaryClip!!.getItemAt(0).text.toString()
            }
            return null
        }
    }
}