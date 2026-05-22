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
 * Bernhard Jenny, 17. September 2010:
 * Changed initialize() to use projectionLatitude1 and projectionLatitude2 instead
 * of hard-coded values.
 * Cleaned code in initialize().
 * Fixed bugs in projectInverse().
 * FIXME: This class should be split in multiple classes for a proper object
 * oriented design. This has been done for Euler and Tissot.
 */
package com.jhlabs.map.proj

import com.jhlabs.Point2D
import com.jhlabs.map.MapMath


open class SimpleConicProjection : ConicProjection {
    private var n = 0.0
    private var rho_c = 0.0
    private var rho_0 = 0.0
    private var sig = 0.0
    private var c1 = 0.0
    private var c2 = 0.0
    private val type: Int
    
    companion object {
        const val MURD1 = 1
        const val MURD2 = 2
        const val MURD3 = 3
        const val PCONIC = 4
        const val VITK1 = 6
        private const val EPS = 1e-10
    }

    constructor() {
        this.type = MURD1
        projectionLatitude1 = Math.toRadians(50.0)
        minLatitude = Math.toRadians(0.0)
        maxLatitude = Math.toRadians(80.0)
    }

    constructor(type: Int) {
        this.type = type
        projectionLatitude1 = Math.toRadians(50.0)
        minLatitude = Math.toRadians(0.0)
        maxLatitude = Math.toRadians(80.0)
    }

    override fun toString(): String {
        return "Simple Conic"
    }

    override fun project(lplam: Double, lpphi: Double, out: Point2D.Double): Point2D.Double {
        val rho: Double

        when (type) {
            MURD2 -> rho = rho_c + Math.tan(sig - lpphi)
            PCONIC -> rho = c2 * (c1 - Math.tan(lpphi))
            else -> rho = rho_c - lpphi
        }
        out.x = rho * Math.sin(lplam * n)
        out.y = rho_0 - rho * Math.cos(lplam * n)
        return out
    }

    override fun projectInverse(xyx: Double, xyy: Double, out: Point2D.Double): Point2D.Double {
        var xyx = xyx
        var xyy = xyy
        xyy = rho_0 - xyy
        var rho = MapMath.distance(xyx, xyy)
        if (n < 0.0) {
            rho = -rho
            xyx = -xyx
            xyy = -xyy
        }
        out.x = Math.atan2(xyx, xyy) / n
        when (type) {
            PCONIC -> out.y = Math.atan(c1 - rho / c2) + sig
            MURD2 -> out.y = sig - Math.atan(rho - rho_c)
            else -> out.y = rho_c - rho
        }
        return out
    }

    override fun hasInverse(): Boolean {
        return true
    }

    override fun initialize() {
        super.initialize()

        /* get common factors for simple conics */
        var del = 0.5 * (projectionLatitude2 - projectionLatitude1)
        sig = 0.5 * (projectionLatitude2 + projectionLatitude1)
        
        if (Math.abs(del) < EPS || Math.abs(sig) < EPS) {
            throw ProjectionException("-42")
        }

        val cs: Double
        when (type) {
            MURD1 -> {
                rho_c = Math.sin(del) / (del * Math.tan(sig)) + sig
                rho_0 = rho_c - projectionLatitude
                n = Math.sin(sig)
            }
            MURD2 -> {
                cs = Math.sqrt(Math.cos(del))
                rho_c = cs / Math.tan(sig)
                rho_0 = rho_c + Math.tan(sig - projectionLatitude)
                n = Math.sin(sig) * cs
            }
            MURD3 -> {
                rho_c = del / (Math.tan(sig) * Math.tan(del)) + sig
                rho_0 = rho_c - projectionLatitude
                n = Math.sin(sig) * Math.sin(del) * Math.tan(del) / (del * del)
            }
            PCONIC -> {
                n = Math.sin(sig)
                c2 = Math.cos(del)
                c1 = 1.0 / Math.tan(sig)
                del = projectionLatitude - sig
                if (Math.abs(del) - EPS10 >= MapMath.HALFPI) {
                    throw ProjectionException("-43")
                }
                rho_0 = c2 * (c1 - Math.tan(del))
                maxLatitude = Math.toRadians(60.0) //FIXME
            }
            VITK1 -> {
                cs = Math.tan(del)
                n = cs * Math.sin(sig) / del
                rho_c = del / (cs * Math.tan(sig)) + sig
                rho_0 = rho_c - projectionLatitude
            }
        }
    }
}