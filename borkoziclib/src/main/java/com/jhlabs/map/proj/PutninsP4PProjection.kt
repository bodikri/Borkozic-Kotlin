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
 * Was confounded with Putnins P4. Corrected name of projection, changed
 * name of class from PutninsP4Projection to PutninsP4PProjection, changed
 * superclass to PseudoCylindricalProjection.
 * Bernhard Jenny, October 28 2008.
 */
package com.jhlabs.map.proj

import com.jhlabs.Point2D
import com.jhlabs.map.MapMath
import kotlin.math.cos
import kotlin.math.sin

open class PutninsP4PProjection : PseudoCylindricalProjection() {
    protected var C_x: Double = 0.874038744
    protected var C_y: Double = 3.883251825

    public override fun project(lplam: Double, lpphi: Double, xy: Point2D.Double): Point2D.Double {
        var lpphi = lpphi
        lpphi = MapMath.asin(0.883883476 * sin(lpphi))
        xy.x = C_x * lplam * cos(lpphi)
        xy.x /= cos(0.333333333333333.let { lpphi *= it; lpphi })
        xy.y = C_y * sin(lpphi)
        return xy
    }

    public override fun projectInverse(
        xyx: Double,
        xyy: Double,
        lp: Point2D.Double
    ): Point2D.Double {
        lp.y = MapMath.asin(xyy / C_y)
        lp.x = xyx * cos(lp.y) / C_x
        lp.y *= 3.0
        lp.x /= cos(lp.y)
        lp.y = MapMath.asin(1.13137085 * sin(lp.y))
        return lp
    }

    override fun isEqualArea(): Boolean {
        return true
    }

    public override fun hasInverse(): Boolean {
        return true
    }

    public override fun toString(): String {
        return "Putnins P4'"
    }
}
