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

import java.text.DecimalFormat
import java.text.NumberFormat
import java.text.ParsePosition
import java.text.FieldPosition
import kotlin.math.*

open class AngleFormat : NumberFormat {

    var format: DecimalFormat
    private var pattern: String
    private var isDegrees: Boolean

    constructor() : this(ddmmssPattern)

    constructor(pattern: String) : this(pattern, false)

    constructor(pattern: String, isDegrees: Boolean) : super() {
        this.pattern = pattern
        this.isDegrees = isDegrees
        format = DecimalFormat()
        format.maximumFractionDigits = 0
        format.isGroupingUsed = false
    }

    override fun format(number: Long, result: StringBuffer, fieldPosition: FieldPosition?): StringBuffer {
        return format(number.toDouble(), result, fieldPosition)
    }

    override fun format(number: Double, result: StringBuffer, fieldPosition: FieldPosition?): StringBuffer {
        val length = pattern.length
        var num = number
        var negative = false

        if (num < 0) {
            for (i in length - 1 downTo 0) {
                val c = pattern[i]
                if (c == 'W' || c == 'N') {
                    num = -num
                    negative = true
                    break
                }
            }
        }

        val ddmmss = if (isDegrees) num else Math.toDegrees(num)
        var iddmmss = round(ddmmss * 3600).toInt()
        if (iddmmss < 0)
            iddmmss = -iddmmss
        var fraction = iddmmss % 3600

        for (i in 0 until length) {
            val c = pattern[i]
            when (c) {
                'R' -> result.append(number)
                'D' -> result.append(ddmmss.toInt())
                'M' -> {
                    val f = fraction / 60
                    if (f < 10)
                        result.append('0')
                    result.append(f)
                }
                'S' -> {
                    val f = fraction % 60
                    if (f < 10)
                        result.append('0')
                    result.append(f)
                }
                'F' -> result.append(fraction)
                'W' -> result.append(if (negative) 'W' else 'E')
                'N' -> result.append(if (negative) 'S' else 'N')
                else -> result.append(c)
            }
        }
        return result
    }

    override fun parse(text: String, parsePosition: ParsePosition): Number {
        var result: Double = 0.0
        var m = 0.0
        var s = 0.0
        var negate = false
        var txt = text

        val length = txt.length
        if (length > 0) {
            val c = txt[length - 1].uppercaseChar()
            when (c) {
                'W', 'S' -> {
                    negate = true
                }
                'E', 'N' -> {
                    // no negate
                }
            }
            if (c == 'W' || c == 'S' || c == 'E' || c == 'N') {
                txt = txt.substring(0, length - 1)
            }
        }

        val i = txt.indexOf('d')
        val iDeg = if (i == -1) txt.indexOf('\u00b0') else i
        if (iDeg != -1) {
            val dd = txt.substring(0, iDeg)
            var mmss = txt.substring(iDeg + 1)
            val d = dd.toDouble()
            val mi = mmss.indexOf('m')
            val mi2 = if (mi == -1) mmss.indexOf('\'') else mi
            if (mi2 != -1) {
                if (mi2 != 0) {
                    m = mmss.substring(0, mi2).toDouble()
                }
                if (mmss.endsWith("s") || mmss.endsWith("\""))
                    mmss = mmss.substring(0, mmss.length - 1)
                if (mi2 != mmss.length - 1) {
                    s = mmss.substring(mi2 + 1).toDouble()
                }
                if (m < 0 || m > 59)
                    throw NumberFormatException("Minutes must be between 0 and 59")
                if (s < 0 || s >= 60)
                    throw NumberFormatException("Seconds must be between 0 and 59")
            } else if (mi2 != 0)
                m = mmss.toDouble()
            result = if (isDegrees)
                MapMath.dmsToDeg(d, m, s)
            else
                MapMath.dmsToRad(d, m, s)
        } else {
            result = txt.toDouble()
            if (!isDegrees)
                result = Math.toRadians(result)
        }
        parsePosition.index = text.length
        if (negate)
            result = -result
        return result
    }

    companion object {
        @JvmField
        val ddmmssPattern = "DdM"
        @JvmField
        val ddmmssPattern2 = "DdM'S\""
        @JvmField
        val ddmmssLongPattern = "DdM'S\"W"
        @JvmField
        val ddmmssLatPattern = "DdM'S\"N"
        @JvmField
        val ddmmssPattern4 = "DdMmSs"
        @JvmField
        val decimalPattern = "D.F"
    }
}
