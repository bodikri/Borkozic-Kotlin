/*
Copyright 2006 Jerry Huxtable

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.jhlabs.map

import java.io.Serializable
import java.text.NumberFormat
import java.text.ParseException

open class Unit(@JvmField val name: String, @JvmField val plural: String, @JvmField val abbreviation: String, @JvmField val value: Double) : Serializable {

    companion object {
        private const val serialVersionUID = -6704954923429734628L

        const val ANGLE_UNIT = 0
        const val LENGTH_UNIT = 1
        const val AREA_UNIT = 2
        const val VOLUME_UNIT = 3

        val format: NumberFormat = NumberFormat.getNumberInstance().apply {
            maximumFractionDigits = 2
            isGroupingUsed = false
        }
    }

    fun toBase(n: Double): Double {
        return n * value
    }

    fun fromBase(n: Double): Double {
        return n / value
    }

    @Throws(NumberFormatException::class)
    open fun parse(s: String): Double {
        return try {
            format.parse(s).toDouble()
        } catch (e: ParseException) {
            throw NumberFormatException(e.message)
        }
    }

    open fun format(n: Double): String {
        return "${format.format(n)} $abbreviation"
    }

    open fun format(n: Double, abbrev: Boolean): String {
        return if (abbrev) {
            "${format.format(n)} $abbreviation"
        } else {
            format.format(n).toString()
        }
    }

    open fun format(x: Double, y: Double, abbrev: Boolean): String {
        return if (abbrev) {
            "${format.format(x)}/${format.format(y)} $abbreviation"
        } else {
            "${format.format(x)}/${format.format(y)}"
        }
    }

    open fun format(x: Double, y: Double): String {
        return format(x, y, true)
    }

    override fun toString(): String {
        return plural
    }

    override fun equals(other: Any?): Boolean {
        if (other is Unit) {
            return other.value == value
        }
        return false
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }
}