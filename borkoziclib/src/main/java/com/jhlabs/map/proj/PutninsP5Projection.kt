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
 */
/**
 * Changed superclass to PseudoCylindricalProjection.
 * Bernhard Jenny, May 25 2010.
 */
package com.jhlabs.map.proj

import com.jhlabs.Point2D
import kotlin.math.sqrt

open class PutninsP5Projection : PseudoCylindricalProjection() {
    protected var A: Double = 2.0
    protected var B: Double = 1.0

    public override fun project(lplam: Double, lpphi: Double, xy: Point2D.Double): Point2D.Double {
        xy.x = C * lplam * (A - B * sqrt(1.0 + D * lpphi * lpphi))
        xy.y = C * lpphi
        return xy
    }

    public override fun projectInverse(
        xyx: Double,
        xyy: Double,
        lp: Point2D.Double
    ): Point2D.Double {
        lp.y = xyy / C
        lp.x = xyx / (C * (A - B * sqrt(1.0 + D * lp.y * lp.y)))
        return lp
    }

    public override fun hasInverse(): Boolean {
        return true
    }

    public override fun toString(): String {
        return "Putnins P5"
    }

    companion object {
        private const val C = 1.01346
        private const val D = 1.2158542
    }
}
