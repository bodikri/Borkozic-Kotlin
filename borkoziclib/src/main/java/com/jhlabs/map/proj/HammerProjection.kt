/*
 * Copyright 2006 Jerry Huxtable
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * This file was semi-automatically converted from the public-domain USGS PROJ source.
 */

/**
 * Bernhard Jenny, May 07: split Hammer and Eckert-Greifendorff projections.
 */
package com.jhlabs.map.proj

import com.jhlabs.Point2D
import kotlin.math.*

class HammerProjection : PseudoCylindricalProjection() {

    private val w = 0.5
    private var m = 1.0
    private var rm = 0.0

    override fun project(lplam: Double, lpphi: Double, xy: Point2D.Double): Point2D.Double {
        val cosphi: Double
        val d: Double

        val adjustedLplam = lplam * w
        cosphi = cos(lpphi)
        d = sqrt(2.0 / (1.0 + cosphi * cos(adjustedLplam)))
        xy.x = m * d * cosphi * sin(adjustedLplam)
        xy.y = rm * d * sin(lpphi)
        return xy
    }

    override fun initialize() {
        super.initialize()
        m = if (abs(m) <= 0.0) {
            throw ProjectionException("-27")
        } else {
            1.0
        }
        rm = 1.0 / m
        m /= w
        es = 0.0
    }

    /**
     * Returns true if this projection is equal area
     */
    override fun isEqualArea(): Boolean {
        return true
    }

    fun setM(m: Double) {
        this.m = m
    }

    fun getM(): Double {
        return m
    }

    override fun toString(): String {
        return "Hammer"
    }
}