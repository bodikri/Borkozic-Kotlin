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
 *
 * Bernhard Jenny, 19 September 2010: fixed inverse.
 * 23 September 2010: changed super class to PseudoCylindricalProjection.
 */
package com.jhlabs.map.proj

import com.jhlabs.Point2D
import com.jhlabs.map.MapMath

class CollignonProjection : PseudoCylindricalProjection() {
    companion object {
        private const val FXC = 1.12837916709551257390
        private const val FYC = 1.77245385090551602729
        private const val ONEEPS = 1.0000001
    }

    override fun project(lplam: Double, lpphi: Double, out: Point2D.Double): Point2D.Double {
        out.y = 1.0 - Math.sin(lpphi)
        if (out.y <= 0.0) {
            out.y = 0.0
        } else {
            out.y = Math.sqrt(out.y)
        }
        out.x = FXC * lplam * out.y
        out.y = FYC * (1.0 - out.y)
        return out
    }

    override fun projectInverse(xyx: Double, xyy: Double, out: Point2D.Double): Point2D.Double {
        var lpphi = xyy / FYC - 1.0
        lpphi = 1.0 - lpphi * lpphi
        if (Math.abs(lpphi) < 1.0) {
            lpphi = Math.asin(lpphi)
        } else if (Math.abs(lpphi) > ONEEPS) {
            throw ProjectionException("I")
        } else {
            lpphi = if (lpphi < 0.0) -MapMath.HALFPI else MapMath.HALFPI
        }
        out.x = 1.0 - Math.sin(lpphi)
        if (out.x <= 0.0) {
            out.x = 0.0
        } else {
            out.x = xyx / (FXC * Math.sqrt(out.x))
        }
        out.y = lpphi
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
        return "Collignon"
    }
}