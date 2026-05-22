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
 * Bernhard Jenny, 23 September 2010: change super class to 
 * PseudoCylindricalProjection, removed parallelsAreParallel.
 */
package com.jhlabs.map.proj

import com.jhlabs.Point2D

class DenoyerProjection : PseudoCylindricalProjection() {

    companion object {
        const val C0 = 0.95
        const val C1 = -0.08333333333333333333
        const val C3 = 0.00166666666666666666
        const val D1 = 0.9
        const val D5 = 0.03
    }

    override fun project(lplam: Double, lpphi: Double, out: Point2D.Double): Point2D.Double {
        out.y = lpphi
        out.x = lplam
        val aphi = Math.abs(lplam)
        out.x *= Math.cos((C0 + aphi * (C1 + aphi * aphi * C3))
                * (lpphi * (D1 + D5 * lpphi * lpphi * lpphi * lpphi)))
        return out
    }

    override fun toString(): String {
        return "Denoyer Semi-elliptical"
    }
}