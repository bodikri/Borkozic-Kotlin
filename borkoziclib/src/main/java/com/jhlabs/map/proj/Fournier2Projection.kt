/*
Copyright 2010 Bernhard Jenny

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
package com.jhlabs.map.proj

import com.jhlabs.Point2D

/**
 * Fournier II projection.
 * Code from PROJ.4.
 * 23 September: changed super class to PseudoCylindricalProjection.
 * @author Bernhard Jenny, Institute of Cartography, ETH Zurich
 */
class Fournier2Projection : PseudoCylindricalProjection() {

    companion object {
        private const val Cx = 0.5641895835477562869480794515
        private const val Cy = 0.8862269254527580136490837416
    }

    override fun project(lplam: Double, lpphi: Double, out: Point2D.Double): Point2D.Double {
        out.x = Cx * lplam * Math.cos(lpphi)
        out.y = Cy * Math.sin(lpphi)
        return out
    }

    override fun projectInverse(xyx: Double, xyy: Double, out: Point2D.Double): Point2D.Double {
        out.y = Math.asin(xyy / Cy)
        out.x = xyx / (Cx * Math.cos(out.y))
        return out
    }

    override fun hasInverse(): Boolean {
        return true
    }

    override fun toString(): String {
        return "Fournier II"
    }
}