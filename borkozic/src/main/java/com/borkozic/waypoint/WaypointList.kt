package com.borkozic.waypoint

/**
 * Constants only — actual UI is in WaypointListScreen.kt (Compose).
 */
class WaypointList {

    companion object {
        const val MODE_MANAGE = 1
        const val MODE_START = 2

        const val qaWaypointVisible = 1
        const val qaWaypointNavigate = 2
        const val qaWaypointProperties = 3
        const val qaWaypointShare = 4
        const val qaWaypointDelete = 5
        const val qaWaypointSetClear = 101
        const val qaWaypointSetRemove = 102
    }
}
