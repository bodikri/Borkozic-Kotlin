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
 * Bernhard Jenny, May 25 2010:
 * Changed superclass from ConicProjection to PseudoCylindricalProjection, and
 * made abstract.
 * Bernhard Jenny, 19 September 2010:
 * Fixed inverse projection.
 */
package com.jhlabs.map.proj

import com.jhlabs.Point2D
import com.jhlabs.map.MapMath

/**
 * Sine-Tangent Series
 * Abstract base class for Kavraisky5Projection, McBrydeThomasSine1Projection,
 * FoucautProjection, and QuarticAuthalicProjection.
 */
abstract class STSProjection(p: Double, q: Double, mode: Boolean) : PseudoCylindricalProjection() {

    private val C_x: Double
    private val C_y: Double
    private val C_p: Double
    private val tan_mode: Boolean

    init {
        es = 0.0
        C_x = q / p
        C_y = p
        C_p = 1 / q
        tan_mode = mode
        initialize()
    }

    override fun project(lplam: Double, lpphi: Double, xy: Point2D.Double): Point2D.Double {
        xy.x = C_x * lplam * Math.cos(lpphi)
        xy.y = C_y
        val lpphiModified = lpphi * C_p
        val c = Math.cos(lpphiModified)
        if (tan_mode) {
            xy.x *= c * c
            xy.y *= Math.tan(lpphiModified)
        } else {
            xy.x /= c
            xy.y *= Math.sin(lpphiModified)
        }
        return xy
    }

    override fun projectInverse(xyx: Double, xyy: Double, lp: Point2D.Double): Point2D.Double {
        val xyyModified = xyy / C_y
        lp.y = if (tan_mode) Math.atan(xyyModified) else MapMath.asin(xyyModified)
        val c = Math.cos(lp.y)
        lp.y /= C_p
        lp.x = xyx / (C_x * Math.cos(lp.y))
        if (tan_mode) {
            lp.x /= c * c
        } else {
            lp.x *= c
        }
        return lp
    }

    override fun hasInverse(): Boolean {
        return true
    }
}