/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2013 Andrey Novikov <http://andreynovikov.info/>
 * 
 * This file is part of Androzic application.
 * 
 * Androzic is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Androzic is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Androzic. If not, see <http://www.gnu.org/licenses/>.
 */
package com.borkozic.navigation

import android.app.Service

abstract class BaseNavigationService : Service() {
    companion object {
        /**
         * Action to initiate navigation to map object registered by Androzic (by id),
         * which allows to navigate to moving object. In this mode navigation
         * is not restored if application is restarted.
         */
        const val NAVIGATE_MAPOBJECT_WITH_ID: String = "com.borkozic.navigateMapObjectWithId"

        /**
         * Action to initiate navigation to map object. Navigation is restored if
         * application is restarted.
         */
        const val NAVIGATE_MAPOBJECT: String = "com.borkozic.navigateMapObject"

        /**
         * Action to initiate navigation via route. Navigation is restored if
         * application is restarted.
         */
        const val NAVIGATE_ROUTE: String = "com.borkozic.navigateRoute"

        /**
         * Action to initiate navigation via area. Navigation is restored if
         * application is restarted.
         */
        const val NAVIGATE_AREA: String = "com.borkozic.navigateArea"

        /**
         * Map object id as returned by Androzic. Used with NAVIGATE_MAPOBJECT_WITH_ID action. Type: long
         */
        const val EXTRA_ID: String = "id"

        /**
         * Map object name. Type: String
         */
        const val EXTRA_NAME: String = "name"

        /**
         * Map object latitude. Type: double
         */
        const val EXTRA_LATITUDE: String = "latitude"

        /**
         * Map object longitude. Type: double
         */
        const val EXTRA_LONGITUDE: String = "longitude"

        /**
         * Map object proximity. Type: int
         */
        const val EXTRA_PROXIMITY: String = "proximity"

        /**
         * Route index as returned by Borkozic. Type: int
         */
        const val EXTRA_ROUTE_INDEX: String = "index"

        /**
         * Area index as returned by Borkozic. Type: int
         */
        const val EXTRA_AREA_INDEX: String = "indexArea" //todo - да намеря дали е неоходимо

        /**
         * Route direction: DIRECTION_FORWARD or DIRECTION_REVERSE.
         */
        const val EXTRA_ROUTE_DIRECTION: String = "direction"

        /**
         * Route start waypoint index. Zero based, optional. Type: int
         */
        const val EXTRA_ROUTE_START: String = "start"

        /**
         * Area start waypoint index. Zero based, optional. Type: int
         */
        const val EXTRA_AREA_START: String = "startArea" //todo - да намеря дали е неоходимо

        const val BROADCAST_NAVIGATION_STATUS: String = "com.borkozic.navigationStatusChanged"
        const val BROADCAST_NAVIGATION_STATE: String = "com.borkozic.navigationStateChanged"

        const val STATE_STARTED: Int = 1
        const val STATE_NEXTWPT: Int = 2
        const val STATE_REACHED: Int = 3
        const val STATE_STOPED: Int = 4

        const val DIRECTION_FORWARD: Int = 1
        @JvmField
        val DIRECTION_REVERSE: Int = -1
    }
}
