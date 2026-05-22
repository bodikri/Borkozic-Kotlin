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
 * Added isEqualArea and changed base class to CylindricalProjection
 * by Bernhard Jenny, July 12 2010.
 */
package com.jhlabs.map.proj

import com.jhlabs.Point2D

class TCEAProjection : CylindricalProjection() {

    private var rk0: Double = 0.0

    init {
        initialize()
    }

    override fun project(lplam: Double, lpphi: Double, out: Point2D.Double): Point2D.Double {
        out.x = rk0 * Math.cos(lpphi) * Math.sin(lplam)
        out.y = scaleFactor * (Math.atan2(Math.tan(lpphi), Math.cos(lplam)) - projectionLatitude)
        return out
    }

    override fun projectInverse(xyx: Double, xyy: Double, out: Point2D.Double): Point2D.Double {
        val t: Double

        out.y = xyy * rk0 + projectionLatitude
        out.x *= scaleFactor
        t = Math.sqrt(1.0 - xyx * xyx)
        out.y = Math.asin(t * Math.sin(xyy))
        out.x = Math.atan2(xyx, t * Math.cos(xyy))
        return out
    }

    override fun initialize() { // tcea
        super.initialize()
        rk0 = 1.0 / scaleFactor
    }

    override fun isRectilinear(): Boolean {
        return false
    }

    override fun hasInverse(): Boolean {
        return true
    }

    override fun isEqualArea(): Boolean {
        return false
    }

    override fun toString(): String {
        return "Transverse Cylindrical Equal Area"
    }
}