package com.borkozic.util

class FileUtils {
    companion object {
        const val unusable = "*+~|<>!?\\/:"
        
        /**
         * Replace illegal characters in a filename with "_" Illegal characters: : \ / * ? | < >
         *
         * @param name
         * @return sanitized string
         */
        @JvmStatic
        fun sanitizeFilename(name: String): String {
            val sb = StringBuilder()
            for (i in name.indices) {
                if (unusable.indexOf(name[i]) > -1)
                    sb.append("_")
                else
                    sb.append(name[i])
            }
            return sb.toString()
        }
    }
}