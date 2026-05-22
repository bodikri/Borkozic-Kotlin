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
 * Added isConformal method, removed isRectilinear (duplicate of super class)
 * by Bernhard Jenny, June 26, 2008.
 */
package com.jhlabs.map.proj

import com.jhlabs.Point2D
import com.jhlabs.map.MapMath

class MercatorProjection : CylindricalProjection() {

    init {
        minLatitude = MapMath.degToRad(-85.0)
        maxLatitude = MapMath.degToRad(85.0)
    }

    override fun project(lam: Double, phi: Double, out: Point2D.Double): Point2D.Double {
        if (spherical) {
            out.x = scaleFactor * lam
            out.y = scaleFactor * kotlin.math.ln(kotlin.math.tan(MapMath.QUARTERPI + 0.5 * phi))
        } else {
            out.x = scaleFactor * lam
            out.y = -scaleFactor * kotlin.math.ln(MapMath.tsfn(phi, kotlin.math.sin(phi), e))
        }
        return out
    }

    override fun projectInverse(x: Double, y: Double, out: Point2D.Double): Point2D.Double {
        if (spherical) {
            out.y = MapMath.HALFPI - 2.0 * kotlin.math.atan(kotlin.math.exp(-y / scaleFactor))
            out.x = x / scaleFactor
        } else {
            out.y = MapMath.phi2(kotlin.math.exp(-y / scaleFactor), e)
            out.x = x / scaleFactor
        }
        return out
    }

    override fun hasInverse(): Boolean {
        return true
    }

    override fun isConformal(): Boolean {
        return true
    }

    /**
     * Returns the EPSG code for this projection, or 0 if unknown.
     */
    override fun getEPSGCode(): Int {
        return 9804
    }

    override fun toString(): String {
        return "Mercator"
    }
}