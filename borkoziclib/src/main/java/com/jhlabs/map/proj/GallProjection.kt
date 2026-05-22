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
 * Changed superclass from Projection to CylindricalProjection.
 * Bernhard Jenny, May 25 2010.
 */
package com.jhlabs.map.proj

import com.jhlabs.Point2D

class GallProjection : CylindricalProjection() {

    override fun project(lplam: Double, lpphi: Double, out: Point2D.Double): Point2D.Double {
        out.x = XF * lplam
        out.y = YF * kotlin.math.tan(0.5 * lpphi)
        return out
    }

    override fun projectInverse(xyx: Double, xyy: Double, out: Point2D.Double): Point2D.Double {
        out.x = RXF * xyx
        out.y = 2.0 * kotlin.math.atan(xyy * RYF)
        return out
    }

    override fun hasInverse(): Boolean {
        return true
    }

    override fun toString(): String {
        return "Gall (Gall Stereographic)"
    }

    companion object {
        private const val YF = 1.70710678118654752440
        private const val XF = 0.70710678118654752440
        private const val RYF = 0.58578643762690495119
        private const val RXF = 1.41421356237309504880
    }
}