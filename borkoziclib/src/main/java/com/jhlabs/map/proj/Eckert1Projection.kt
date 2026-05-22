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
 * Changed super class from Projection to PseudoCylindricalProjection.
 * Bernhard Jenny, May 25 2010.
 */
package com.jhlabs.map.proj

import com.jhlabs.Point2D
import kotlin.math.abs

class Eckert1Projection : PseudoCylindricalProjection() {

    companion object {
        private const val FC = .92131773192356127802
        private const val RP = .31830988618379067154
    }

    override fun project(lplam: Double, lpphi: Double, out: Point2D.Double): Point2D.Double {
        out.x = FC * lplam * (1.0 - RP * abs(lpphi))
        out.y = FC * lpphi
        return out
    }

    override fun projectInverse(xyx: Double, xyy: Double, out: Point2D.Double): Point2D.Double {
        out.y = xyy / FC
        out.x = xyx / (FC * (1.0 - RP * abs(out.y)))
        return out
    }

    override fun hasInverse(): Boolean = true

    override fun toString(): String = "Eckert I"
}
