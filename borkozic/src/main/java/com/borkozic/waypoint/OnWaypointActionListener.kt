package com.borkozic.waypoint

import com.borkozic.data.Waypoint

interface OnWaypointActionListener {
    fun onWaypointView(waypoint: Waypoint)
    fun onWaypointNavigate(waypoint: Waypoint)
    fun onWaypointEdit(waypoint: Waypoint)
    fun onWaypointShare(waypoint: Waypoint)
    fun onWaypointRemove(waypoint: Waypoint)
}
