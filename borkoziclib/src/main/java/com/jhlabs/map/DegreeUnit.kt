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

import java.text.ParseException

class DegreeUnit : Unit("degree", "degrees", "deg", 1.0) {

    companion object {
        private const val serialVersionUID = -3212757578604686538L
        private val format = AngleFormat(AngleFormat.ddmmssPattern, true)
    }

    @Throws(NumberFormatException::class)
    override fun parse(s: String): Double {
        return try {
            format.parse(s).toDouble()
        } catch (e: ParseException) {
            throw NumberFormatException(e.message)
        }
    }

    override fun format(n: Double): String {
        return "${format.format(n)} $abbreviation"
    }

    override fun format(n: Double, abbrev: Boolean): String {
        return if (abbrev) {
            "${format.format(n)} $abbreviation"
        } else {
            format.format(n)
        }
    }

    override fun format(x: Double, y: Double, abbrev: Boolean): String {
        return if (abbrev) {
            "${format.format(x)}/${format.format(y)} $abbreviation"
        } else {
            "${format.format(x)}/${format.format(y)}"
        }
    }
}