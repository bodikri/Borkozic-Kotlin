package com.borkozic.util

/*
 * Funambol is a mobile platform developed by Funambol, Inc.
 * Copyright (C) 2003 - 2007 Funambol, Inc.
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Affero General Public License version 3 as published by
 * the Free Software Foundation with the addition of the following permission
 * added to Section 15 as permitted in Section 7(a): FOR ANY PART OF THE COVERED
 * WORK IN WHICH THE COPYRIGHT IS OWNED BY FUNAMBOL, FUNAMBOL DISCLAIMS THE
 * WARRANTY OF NON INFRINGEMENT OF THIRD PARTY RIGHTS.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program; if not, see http://www.gnu.org/licenses or write to
 * the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301 USA.
 * 
 * You can contact Funambol, Inc. headquarters at 643 Bair Island Road, Suite
 * 305, Redwood City, CA 94063, USA, or at email address info@funambol.com.
 * 
 * The interactive user interfaces in modified source and object code versions
 * of this program must display Appropriate Legal Notices, as required under
 * Section 5 of the GNU Affero General Public License version 3.
 * 
 * In accordance with Section 7(b) of the GNU Affero General Public License
 * version 3, these Appropriate Legal Notices must retain the display of the
 * "Powered by Funambol" logo. If the display of the logo is not reasonably
 * feasible for technical reasons, the Appropriate Legal Notices must display
 * the words "Powered by Funambol".
 */

/*
 * Modified by Andrey Novikov for Androzic
 */

import java.util.Hashtable

/**
 * Escapes/unescapes special chars according to XML specifications
 */
class Entities {

    private var map: EntityMap = LookupEntityMap()

    companion object {
        val XML: Entities by lazy {
            val entities = Entities()
            entities.addEntities(BASIC_ARRAY)
            entities
        }

        private val BASIC_ARRAY = arrayOf(
            arrayOf("quot", "34"),   // " - double-quote
            arrayOf("amp", "38"),   // & - ampersand
            arrayOf("lt", "60"),    // < - less-than
            arrayOf("gt", "62"),    // > - greater-than
            arrayOf("apos", "39"),  // XML apostrophe
            arrayOf("comma", "44")  // XML apostrophe
        )
    }

    interface EntityMap {
        fun add(name: String, value: Int)
        fun name(value: Int): String?
        fun value(name: String): Int
    }

    open class PrimitiveEntityMap : EntityMap {
        private val mapNameToValue = Hashtable<String, Int>()
        private val mapValueToName = Hashtable<Int, String>()

        override fun add(name: String, value: Int) {
            mapNameToValue[name] = value
            mapValueToName[value] = name
        }

        override fun name(value: Int): String? {
            return mapValueToName[value]
        }

        override fun value(name: String): Int {
            return mapNameToValue[name] ?: -1
        }
    }

    inner class LookupEntityMap : PrimitiveEntityMap() {

        private var lookupTable: Array<String?>? = null
        private val LOOKUP_TABLE_SIZE = 256

        override fun name(value: Int): String? {
            if (value < LOOKUP_TABLE_SIZE) {
                return lookupTable()[value]
            }
            return super.name(value)
        }

        private fun lookupTable(): Array<String?> {
            if (lookupTable == null) {
                createLookupTable()
            }
            return lookupTable!!
        }

        private fun createLookupTable() {
            lookupTable = Array(LOOKUP_TABLE_SIZE) { i ->
                super.name(i)
            }
        }
    }

    fun addEntities(entityArray: Array<Array<String>>) {
        for (i in entityArray.indices) {
            addEntity(entityArray[i][0], entityArray[i][1].toInt())
        }
    }

    fun addEntity(name: String, value: Int) {
        map.add(name, value)
    }

    fun entityName(value: Int): String? {
        return map.name(value)
    }

    fun entityValue(name: String): Int {
        return map.value(name)
    }

    /**
     * Escapes special characters in a `String`.
     *
     * @param str The `String` to escape.
     * @return A escaped `String`.
     */
    fun escape(str: String): String {
        val buf = StringBuilder(str.length * 2)

        for (i in str.indices) {
            val ch = str[i]
            val entityName = this.entityName(ch.toInt())

            if (entityName == null) {
                if (ch < 0x20.toChar()) {
                    buf.append("&#")
                    buf.append(ch.code)
                    buf.append(';')
                } else {
                    buf.append(ch)
                }
            } else {
                buf.append('&')
                buf.append(entityName)
                buf.append(';')
            }
        }

        return buf.toString()
    }

    /**
     * Unescapes special characters in a `String`.
     *
     * @param str The `String` to escape.
     * @return A un-escaped `String`.
     */
    fun unescape(str: String): String {
        val buf = StringBuilder(str.length)

        var i = 0
        while (i < str.length) {
            val ch = str[i]

            if (ch == '&') {
                val semi = str.indexOf(';', i + 1)

                if (semi == -1) {
                    buf.append(ch)
                    i++
                    continue
                }

                val entityName = str.substring(i + 1, semi)

                var entityValue: Int
                if (entityName[0] == '#') {
                    val charAt1 = entityName[1]
                    if (charAt1 == 'x' || charAt1 == 'X') {
                        entityValue = entityName.substring(2).toInt(16)
                    } else {
                        entityValue = entityName.substring(1).toInt()
                    }
                } else {
                    entityValue = this.entityValue(entityName)
                }

                if (entityValue == -1) {
                    buf.append('&')
                    buf.append(entityName)
                    buf.append(';')
                } else {
                    buf.append(entityValue.toChar())
                }

                i = semi + 1
            } else {
                buf.append(ch)
                i++
            }
        }

        return buf.toString()
    }
}
