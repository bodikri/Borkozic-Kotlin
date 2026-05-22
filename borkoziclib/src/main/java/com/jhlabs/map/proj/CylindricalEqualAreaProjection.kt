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
 * Changes by Bernhard Jenny, May 2007: added missing toString() and
 * isEqualArea(); this class now derives from CylindricalProjection instead of
 * Projection; removed isRectilinear, which is defined in the new superclass
 * CylindricalProjection; and removed trueScaleLatitude that did hide a field
 * of the Projection class of the same name.
 */

package com.jhlabs.map.proj

import com.jhlabs.Point2D
import com.jhlabs.map.MapMath

class CylindricalEqualAreaProjection : CylindricalProjection {
    private var qp: Double = 0.0
    private var apa: DoubleArray? = null

    constructor() : this(0.0, 0.0, 0.0)

    constructor(
        projectionLatitude: Double,
        projectionLongitude: Double,
        trueScaleLatitude: Double
    ) : super() {
        this.projectionLatitude = projectionLatitude
        this.projectionLongitude = projectionLongitude
        this.trueScaleLatitude = trueScaleLatitude
        initialize()
    }

    override fun initialize() {
        super.initialize()
        var t = trueScaleLatitude

        scaleFactor = kotlin.math.cos(t)
        if (es != 0.0) {
            t = kotlin.math.sin(t)
            scaleFactor /= kotlin.math.sqrt(1.0 - es * t * t)
            apa = MapMath.authset(es)
            qp = MapMath.qsfn(1.0, e, one_es)
        }
    }

    override fun project(lam: Double, phi: Double, xy: Point2D.Double): Point2D.Double {
        if (spherical) {
            xy.x = scaleFactor * lam
            xy.y = kotlin.math.sin(phi) / scaleFactor
        } else {
            xy.x = scaleFactor * lam
            xy.y = 0.5 * MapMath.qsfn(kotlin.math.sin(phi), e, one_es) / scaleFactor
        }
        return xy
    }

    override fun projectInverse(x: Double, y: Double, lp: Point2D.Double): Point2D.Double {
        if (spherical) {
            val yScaled = y * scaleFactor
            val t = kotlin.math.abs(yScaled)

            if (t - EPS10 <= 1.0) {
                if (t >= 1.0) {
                    lp.y = if (yScaled < 0.0) -MapMath.HALFPI else MapMath.HALFPI
                } else {
                    lp.y = kotlin.math.asin(yScaled)
                }
                lp.x = x / scaleFactor
            } else {
                throw ProjectionException()
            }
        } else {
            lp.y = MapMath.authlat(kotlin.math.asin(2.0 * y * scaleFactor / qp), apa)
            lp.x = x / scaleFactor
        }
        return lp
    }

    override fun hasInverse(): Boolean {
        return true
    }

    override fun isEqualArea(): Boolean {
        return true
    }

    override fun toString(): String {
        return "Cylindrical Equal-Area"
    }
}