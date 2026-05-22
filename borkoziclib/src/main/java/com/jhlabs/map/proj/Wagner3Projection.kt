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
package com.jhlabs.map.proj

import com.jhlabs.Point2D
import com.jhlabs.map.*

class Wagner3Projection : PseudoCylindricalProjection() {
	
    companion object {
        private const val TWOTHIRD = 0.6666666666666666666667
    }

    private var C_x: Double = 0.0

    override fun project(lplam: Double, lpphi: Double, xy: Point2D.Double): Point2D.Double {
        xy.x = C_x * lplam * kotlin.math.cos(TWOTHIRD * lpphi)
        xy.y = lpphi
        return xy
    }

    override fun projectInverse(x: Double, y: Double, lp: Point2D.Double): Point2D.Double {
        lp.y = y
        lp.x = x / (C_x * kotlin.math.cos(TWOTHIRD * lp.y))
        return lp
    }

    override fun initialize() {
        super.initialize()
        C_x = kotlin.math.cos(trueScaleLatitude) / kotlin.math.cos(2.0 * trueScaleLatitude / 3.0)
        es = 0.0
    }

    override fun hasInverse(): Boolean {
        return true
    }

    override fun toString(): String {
        return "Wagner III"
    }

}