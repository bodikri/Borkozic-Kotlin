/*
 * Copyright 2006 Jerry Huxtable
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * This file was semi-automatically converted from the public-domain USGS PROJ source.
 */

/**
 * Added acute on e in string returned by toString by Bernhard Jenny, July 2007.
 */
package com.jhlabs.map.proj

import com.jhlabs.Point2D

class LarriveeProjection : Projection() {

    private val SIXTH = 0.16666666666666666

    override fun project(lplam: Double, lpphi: Double, out: Point2D.Double): Point2D.Double {
        out.x = 0.5 * lplam * (1.0 + kotlin.math.sqrt(kotlin.math.cos(lpphi)))
        out.y = lpphi / (kotlin.math.cos(0.5 * lpphi) * kotlin.math.cos(SIXTH * lplam))
        return out
    }

    override fun toString(): String {
        return "Larriv\u00E9e"
    }
}