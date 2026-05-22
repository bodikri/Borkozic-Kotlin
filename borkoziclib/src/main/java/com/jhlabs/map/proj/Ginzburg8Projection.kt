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
 * Changes by Bernhard Jenny: Changed Ginsburg to Ginzburg, including the name
 * of this class file. Added "1944" in toString. Change superclass from
 * Projection to PseudoCylindricalProjection.
 */
package com.jhlabs.map.proj

import com.jhlabs.Point2D

class Ginzburg8Projection : PseudoCylindricalProjection() {

    companion object {
        private const val Cl = 0.000952426
        private const val Cp = 0.162388
        private const val C12 = 0.08333333333333333
    }

    override fun project(lplam: Double, lpphi: Double, out: Point2D.Double): Point2D.Double {
        var t = lpphi * lpphi
        out.y = lpphi * (1.0 + t * C12)
        out.x = lplam * (1.0 - Cp * t)
        t = lplam * lplam
        out.x *= (0.87 - Cl * t * t)
        return out
    }

    override fun toString(): String {
        return "Ginzburg VIII (TsNIIGAiK 1944)"
    }
}