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
 * Bernhard Jenny, 23 September 2010: changed super class to ModifiedAzimuthalProjection.
 */
package com.jhlabs.map.proj

import com.jhlabs.Point2D
import com.jhlabs.map.*

class Wagner7Projection : ModifiedAzimuthalProjection() {

    override fun project(lplam: Double, lpphi: Double, out: Point2D.Double): Point2D.Double {
        val theta = Math.asin(0.90630778703664996 * Math.sin(lpphi))
        out.y = Math.sin(theta)
        val ct = Math.cos(theta)
        val lam3 = lplam / 3.0
        out.x = 2.66723 * ct * Math.sin(lam3)
        out.y = 0.90630778703664996 * Math.sin(lpphi)
        val D = 1.0 / (Math.sqrt(0.5 * (1.0 + ct * Math.cos(lam3))))
        out.y *= 1.24104 * D
        out.x *= D
        return out
    }

    /**
     * Returns true if this projection is equal area
     */
    override fun isEqualArea(): Boolean {
        return true
    }

    override fun toString(): String {
        return "Wagner VII"
    }

}