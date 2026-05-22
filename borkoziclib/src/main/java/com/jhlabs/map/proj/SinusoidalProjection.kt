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
 * Added isEqualArea by Bernhard Jenny, October 28 2008.
 */

package com.jhlabs.map.proj

import com.jhlabs.Point2D
import com.jhlabs.map.MapMath

class SinusoidalProjection : PseudoCylindricalProjection() {

    override fun project(lam: Double, phi: Double, xy: Point2D.Double): Point2D.Double {
        xy.x = lam * kotlin.math.cos(phi)
        xy.y = phi
        return xy
    }

    override fun projectInverse(x: Double, y: Double, lp: Point2D.Double): Point2D.Double {
        lp.x = x / kotlin.math.cos(y)
        lp.y = y
        return lp
    }

    fun getWidth(y: Double): Double {
        return MapMath.normalizeLongitude(kotlin.math.PI) * kotlin.math.cos(y) // FIXME
    }

    override fun hasInverse(): Boolean {
        return true
    }

    override fun isEqualArea(): Boolean {
        return true
    }

    override fun toString(): String {
        return "Sinusoidal"
    }
}