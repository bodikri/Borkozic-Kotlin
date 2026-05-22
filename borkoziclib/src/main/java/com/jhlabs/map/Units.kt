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

object Units {

    // Angular units
    val DEGREES: Unit = DegreeUnit()
    val RADIANS: Unit = Unit("radian", "radians", "rad", Math.toDegrees(1.0))
    val ARC_MINUTES: Unit = Unit("arc minute", "arc minutes", "min", 1.0/60.0)
    val ARC_SECONDS: Unit = Unit("arc second", "arc seconds", "sec", 1.0/3600.0)

    // Distance units
    
    // Metric units
    val KILOMETRES: Unit = Unit("kilometre", "kilometres", "km", 1000.0)
    val METRES: Unit = Unit("metre", "metres", "m", 1.0)
    val DECIMETRES: Unit = Unit("decimetre", "decimetres", "dm", 0.1)
    val CENTIMETRES: Unit = Unit("centimetre", "centimetres", "cm", 0.01)
    val MILLIMETRES: Unit = Unit("millimetre", "millimetres", "mm", 0.001)

    // International units
    val NAUTICAL_MILES: Unit = Unit("nautical mile", "nautical miles", "nm", 1852.0)
    val MILES: Unit = Unit("mile", "miles", "mi", 1609.344)
    val CHAINS: Unit = Unit("chain", "chains", "ch", 20.1168)
    val YARDS: Unit = Unit("yard", "yards", "yd", 0.9144)
    val FEET: Unit = Unit("foot", "feet", "ft", 0.3048)
    val INCHES: Unit = Unit("inch", "inches", "in", 0.0254)

    // U.S. units
    val US_MILES: Unit = Unit("U.S. mile", "U.S. miles", "us-mi", 1609.347218694437)
    val US_CHAINS: Unit = Unit("U.S. chain", "U.S. chains", "us-ch", 20.11684023368047)
    val US_YARDS: Unit = Unit("U.S. yard", "U.S. yards", "us-yd", 0.914401828803658)
    val US_FEET: Unit = Unit("U.S. foot", "U.S. feet", "us-ft", 0.304800609601219)
    val US_INCHES: Unit = Unit("U.S. inch", "U.S. inches", "us-in", 1.0/39.37)

    // Miscellaneous units
    val FATHOMS: Unit = Unit("fathom", "fathoms", "fath", 1.8288)
    val LINKS: Unit = Unit("link", "links", "link", 0.201168)
    val POINTS: Unit = Unit("point", "points", "point", 0.0254/72.27)

    val units: Array<Unit> = arrayOf(
        DEGREES,
        KILOMETRES, METRES, DECIMETRES, CENTIMETRES, MILLIMETRES,
        MILES, YARDS, FEET, INCHES,
        US_MILES, US_YARDS, US_FEET, US_INCHES,
        NAUTICAL_MILES
    )

    @JvmStatic
    fun findUnits(name: String): Unit {
        for (unit in units) {
            if (name == unit.name || name == unit.plural || name == unit.abbreviation)
                return unit
        }
        return METRES
    }

    fun convert(value: Double, from: Unit, to: Unit): Double {
        if (from === to)
            return value
        return to.fromBase(from.toBase(value))
    }

}