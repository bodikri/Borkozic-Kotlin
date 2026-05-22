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

/*
 * This file was semi-automatically converted from the public-domain USGS PROJ source.
 *
 * Bernhard Jenny, May 2007: Separated Aitoff from Winkel Tripel.
 * 23 September 2010: Changed super class to ModifiedAzimuthalProjection.
 */
package com.jhlabs.map.proj

import com.jhlabs.Point2D

class WinkelTripelProjection : ModifiedAzimuthalProjection() {

    override fun project(lplam: Double, lpphi: Double, out: Point2D.Double): Point2D.Double {
        val c = 0.5 * lplam
        val d = kotlin.math.acos(kotlin.math.cos(lpphi) * kotlin.math.cos(c))

        if (d != 0.0) {
            out.x = 2.0 * d * kotlin.math.cos(lpphi) * kotlin.math.sin(c) * (1.0 / kotlin.math.sin(d))
            out.y = out.x * d * kotlin.math.sin(lpphi)
        } else {
            out.x = 0.0
            out.y = 0.0
        }
        out.x = (out.x + lplam * 0.636619772367581343) * 0.5
        out.y = (out.y + lpphi) * 0.5
        return out
    }

    override fun toString(): String {
        return "Winkel Tripel"
    }
}