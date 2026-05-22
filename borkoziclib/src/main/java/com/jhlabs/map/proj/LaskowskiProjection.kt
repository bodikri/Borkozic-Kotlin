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

class LaskowskiProjection : Projection() {

    companion object {
        private const val a10 = 0.975534
        private const val a12 = -0.119161
        private const val a32 = -0.0143059
        private const val a14 = -0.0547009
        private const val b01 = 1.00384
        private const val b21 = 0.0802894
        private const val b03 = 0.0998909
        private const val b41 = 0.000199025
        private const val b23 = -0.0285500
        private const val b05 = -0.0491032
    }

    override fun project(lplam: Double, lpphi: Double, out: Point2D.Double): Point2D.Double {
        val l2 = lplam * lplam
        val p2 = lpphi * lpphi
        out.x = lplam * (a10 + p2 * (a12 + l2 * a32 + p2 * a14))
        out.y = lpphi * (b01 + l2 * (b21 + p2 * b23 + l2 * b41) +
                p2 * (b03 + p2 * b05))
        return out
    }

    override fun toString(): String {
        return "Laskowski"
    }
}