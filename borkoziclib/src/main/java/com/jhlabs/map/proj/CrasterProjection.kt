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
 * Changed super class from Projection to PseudoCylindricalProjection.
 * Bernhard Jenny, May 25 2010.
 */
package com.jhlabs.map.proj

import com.jhlabs.Point2D

class CrasterProjection : PseudoCylindricalProjection() {

    companion object {
        private const val XM = 0.97720502380583984317
        private const val RXM = 1.02332670794648848847
        private const val YM = 3.06998012383946546542
        private const val RYM = 0.32573500793527994772
        private const val THIRD = 0.333333333333333333
    }

    override fun project(lplam: Double, lpphi: Double, out: Point2D.Double): Point2D.Double {
        val modifiedLpphi = lpphi * THIRD
        out.x = XM * lplam * (2.0 * Math.cos(modifiedLpphi + modifiedLpphi) - 1.0)
        out.y = YM * Math.sin(modifiedLpphi)
        return out
    }

    override fun projectInverse(xyx: Double, xyy: Double, out: Point2D.Double): Point2D.Double {
        out.y = 3.0 * Math.asin(xyy * RYM)
        out.x = xyx * RXM / (2.0 * Math.cos((out.y + out.y) * THIRD) - 1)
        return out
    }

    /**
     * Returns true if this projection is equal area
     */
    override fun isEqualArea(): Boolean {
        return true
    }

    override fun hasInverse(): Boolean {
        return true
    }

    override fun toString(): String {
        return "Craster Parabolic (Putnins P4)"
    }
}