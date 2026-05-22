/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2012  Andrey Novikov <http://andreynovikov.info/>
 *
 * This file is part of Androzic application.
 *
 * Androzic is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * Androzic is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with Androzic.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.borkozic.util

object CSV {
    private const val ESCAPE = '\\'
    private const val QUOTE = '"'
    private const val SEPARATOR = ','

    private fun field(field: StringBuilder, begin: Int, end: Int): String {
        return if (begin < 0) {
            field.substring(0, end)
        } else {
            field.substring(begin, end)
        }
    }

    private fun escape(c: Char): Char {
        return when (c) {
            'n' -> '\n'
            't' -> '\t'
            'r' -> '\r'
            else -> c
        }
    }

    @JvmStatic
    fun parseLine(line: String): Array<String> {
        val length = line.length

        if (length == 0) {
            return arrayOf()
        }

        // Check here if the last character is an escape character so
        // that we don't need to check in the main loop.
        if (line[length - 1] == ESCAPE) {
            throw IllegalArgumentException(": last character is an escape character\n$line")
        }

        // The set of parsed fields.
        val result = mutableListOf<String>()

        // The characters between separators
        val buf = StringBuilder(length)
        // Marks the beginning of the field relative to buffer, -1 indicates the beginning of buffer
        var begin = -1
        // Marks the end of the field relative to buffer
        var end = 0

        // Indicates whether or not we're in a quoted string
        var quote = false

        var i = 0
        while (i < length) {
            val c = line[i]
            if (quote) {
                when (c) {
                    QUOTE -> quote = false
                    ESCAPE -> {
                        i++
                        buf.append(escape(line[i]))
                    }
                    else -> buf.append(c)
                }

                end = buf.length
            } else {
                when (c) {
                    SEPARATOR -> {
                        result.add(field(buf, begin, end))
                        buf.clear()
                        begin = -1
                        end = 0
                    }
                    ESCAPE -> {
                        if (begin < 0) begin = buf.length
                        i++
                        buf.append(escape(line[i]))
                        end = buf.length
                    }
                    QUOTE -> {
                        if (begin < 0) begin = buf.length
                        quote = true
                        end = buf.length
                    }
                    else -> {
                        if (begin < 0 && !c.isWhitespace()) {
                            begin = buf.length
                        }
                        buf.append(c)
                        if (!c.isWhitespace()) end = buf.length
                    }
                }
            }
            i++
        }

        if (quote) {
            throw IllegalArgumentException("unterminated string\n$line")
        } else {
            result.add(field(buf, begin, end))
        }

        return result.toTypedArray()
    }
}