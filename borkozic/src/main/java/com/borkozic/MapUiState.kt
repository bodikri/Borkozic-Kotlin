package com.borkozic

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Всички UI state полета от MapActivity заместват TextView-вете от XML.
 * MutableState в MapActivity се update-ва, MapScreen чете от него.
 */
@Immutable
data class MapUiState(
    // satinfo (topbar)
    val coordinates: String = "0.00000 0.00000",
    val accuracyText: String = "N/A",
    val satInfoText: String = "",
    val satInfoColor: Color = Color.White.copy(alpha = 0.5f),

    // mapinfo (bottom bar)
    val currentFile: String = "-no map-",
    val mapZoom: String = "---%",

    // speed name (for label)
    val speedName: String = "speed",

    // waypoint info (navigation)
    val waypointName: String = "",
    val waypointExtra: String = "--",
    val showWaypointInfo: Boolean = false,

    // route info (navigation)
    val routeName: String = "",
    val routeExtra: String = "--",
    val showRouteInfo: Boolean = false,

    // area info (navigation)
    val areaName: String = "",
    val areaExtra: String = "--",
    val showAreaInfo: Boolean = false,

    // moving info rows
    val showMovingInfo: Boolean = false,

    // speed
    val speedValue: String = "--",
    val speedUnit: String = "--",
    // track
    val trackValue: String = "--",
    val trackUnit: String = "deg",
    // elevation
    val elevationValue: String = "--",
    val elevationUnit: String = "m",
    val elevationName: String = "elev",
    val elevationColor: Color = Color.White,
    // turn
    val turnValue: String = "--",
    val showTurn: Boolean = false,
    // xtk
    val xtkValue: String = "--",
    val xtkUnit: String = "--",
    val showXtk: Boolean = false,
    // above/below glide slope
    val belowaboveValue: String = "--",
    val belowaboveUnit: String = "m",
    val belowaboveName: String = "",
    val belowaboveColor: Color = Color.White,
    val showBelowabove: Boolean = false,

    // track details overlay
    val showTrackDetails: Boolean = false,
    val tpNumber: String = "",
    val tpLatitude: String = "",
    val tpLongitude: String = "",
    val tpElevation: String = "",
    val tpTime: String = "",

    // wait bar
    val showWaitBar: Boolean = false,
    val waitBarText: String = "",

    // edit panels visibility (bottom bars)
    val showEditTrack: Boolean = false,
    val showEditRoute: Boolean = false,
    val showEditArea: Boolean = false,

    // top/bottom bars visibility settings
    val showSatInfoBar: Boolean = true,
    val showMapInfoBar: Boolean = true,

    // track bar values
    val trackBarProgress: Float = 0f,
    val trackBarMax: Float = 0f
)
