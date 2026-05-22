package com.borkozic.route

import com.borkozic.data.Route

interface OnRouteActionListener {
    fun onRouteDetails(route: Route)
    fun onRouteNavigate(route: Route)
    fun onRouteEdit(route: Route)
    fun onRouteEditPath(route: Route)
    fun onRouteSave(route: Route)
}
