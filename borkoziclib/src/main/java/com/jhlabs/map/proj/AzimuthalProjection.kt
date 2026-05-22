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

package com.jhlabs.map.proj

import com.jhlabs.map.MapMath

/**
 * The superclass for all azimuthal map projections
 */
open class AzimuthalProjection : Projection {

    @JvmField
    protected var mode: Int = 0
    @JvmField
    protected var sinphi0: Double = 0.0
    @JvmField
    protected var cosphi0: Double = 0.0
    private var mapRadius: Double = 90.0

    constructor() : this(0.0, 0.0)

    constructor(projectionLatitude: Double, projectionLongitude: Double) {
        this.projectionLatitude = projectionLatitude
        this.projectionLongitude = projectionLongitude
        initialize()
    }

    public override fun initialize() {
        super.initialize()
        if (Math.abs(Math.abs(projectionLatitude) - MapMath.HALFPI) < EPS10)
            mode = if (projectionLatitude < 0.0) SOUTH_POLE else NORTH_POLE
        else if (Math.abs(projectionLatitude) > EPS10) {
            mode = OBLIQUE
            sinphi0 = Math.sin(projectionLatitude)
            cosphi0 = Math.cos(projectionLatitude)
        } else
            mode = EQUATOR
    }

    override fun inside(lon: Double, lat: Double): Boolean {
        return MapMath.greatCircleDistance(
            Math.toRadians(lon), Math.toRadians(lat),
            projectionLongitude, projectionLatitude
        ) < Math.toRadians(mapRadius)
    }

    /**
     * Set the map radius (in degrees). 180 shows a hemisphere, 360 shows the whole globe.
     */
    fun setMapRadius(mapRadius: Double) {
        this.mapRadius = mapRadius
    }

    fun getMapRadius(): Double {
        return mapRadius
    }

    companion object {
        const val NORTH_POLE = 1
        const val SOUTH_POLE = 2
        const val EQUATOR = 3
        const val OBLIQUE = 4
    }
}
