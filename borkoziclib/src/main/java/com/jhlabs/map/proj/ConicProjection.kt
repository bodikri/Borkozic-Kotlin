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
 * The superclass for all Conic projections.
 *
 * Bernhard Jenny, 17 September 2010:
 * Moved projectionLatitude1 and projectionLatitude2 from super class to
 * ConicProjection, as these are specific to conics.
 */
open class ConicProjection : Projection() {

    /**
     * Standard parallel 1 (for projections which use it)
     */
    protected var projectionLatitude1: Double = 0.0
    
    /**
     * Standard parallel 2 (for projections which use it)
     */
    protected var projectionLatitude2: Double = 0.0

    override fun toString(): String {
        return "Conic"
    }

    /**
     * Set the projection latitude in degrees.
     */
    fun setProjectionLatitude1Degrees(projectionLatitude1: Double) {
        this.projectionLatitude1 = MapMath.DTR * projectionLatitude1
    }

    fun getProjectionLatitude1Degrees(): Double {
        return projectionLatitude1 * MapMath.RTD
    }

    fun setProjectionLatitude2Degrees(projectionLatitude2: Double) {
        this.projectionLatitude2 = MapMath.DTR * projectionLatitude2
    }

    fun getProjectionLatitude2Degrees(): Double {
        return projectionLatitude2 * MapMath.RTD
    }

}
