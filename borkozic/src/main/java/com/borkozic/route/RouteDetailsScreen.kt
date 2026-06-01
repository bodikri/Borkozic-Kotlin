package com.borkozic.route

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.borkozic.BaseApplication
import com.borkozic.Borkozic
import com.borkozic.data.Route
import com.borkozic.data.Waypoint
import com.borkozic.util.StringFormatter
import kotlin.math.roundToInt

/**
 * Mode for the RouteDetails screen.
 */
enum class RouteDetailsMode {
    /** Normal view — tap shows View/Edit, start-navigation action available */
    MANAGE,
    /** Navigation mode — tap shows View/Navigate, broadcast updates */
    NAVIGATION
}

/**
 * Shows the waypoints of a single route with:
 * - Drag-and-drop reorder via long-press (consecutive duplicate prevention)
 * - Tap to show quick action (View / Edit or View / Navigate)
 * - Navigation integration (progress indicator, ETE, ETA)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteDetailsScreen(
    route: Route,
    mode: RouteDetailsMode,
    onEditWaypoint: (index: Int) -> Unit,
    onNavigateToWaypoint: (index: Int) -> Unit,
    onShowWaypoint: (index: Int) -> Unit,
    onStartNavigation: () -> Unit,
    onBack: () -> Unit,
    navCurrentIndex: Int = -1,
    navDistance: Double = 0.0,
    navETE: Int = 0,
    navBearing: Double = 0.0,
    navRouteDistanceLeft: ((Int) -> Double)? = null,
    navRouteWaypointETE: ((Int) -> Int)? = null,
    navDirectionForward: Boolean = true // true = forward, false = reverse
) {
    val application = BaseApplication.getApplication<Borkozic>()!!
    val waypoints = route.waypoints

    var waypointList by remember(route) { mutableStateOf(waypoints.toMutableList()) }

    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var showActionMenu by remember { mutableStateOf<Int?>(null) }

    // Sync list when route changes externally
    LaunchedEffect(waypoints.size) {
        waypointList = waypoints.toMutableList()
    }

    val elevatedElevation by animateDpAsState(
        targetValue = if (draggedIndex != null) 6.dp else 1.dp,
        label = "dragElevation"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            route.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            StringFormatter.distanceH(route.distance),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (mode == RouteDetailsMode.MANAGE) {
                        IconButton(onClick = onStartNavigation) {
                            Icon(Icons.Default.Directions, contentDescription = "Navigate")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (waypointList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No waypoints",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                itemsIndexed(waypointList, key = { index, wpt -> "$index-${wpt.hashCode()}" }) { index, wpt ->
                    val isDragged = draggedIndex == index
                    val isActive = if (mode == RouteDetailsMode.NAVIGATION) {
                        navCurrentIndex == index
                    } else false

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(if (isDragged) 1f else 0f)
                            .then(
                                if (isDragged) {
                                    Modifier.graphicsLayer {
                                        translationX = dragOffset.x
                                        translationY = dragOffset.y
                                    }
                                } else Modifier
                            )
                            .pointerInput(index) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { offset ->
                                        draggedIndex = index
                                        dragOffset = Offset.Zero
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffset += dragAmount
                                    },
                                    onDragEnd = {
                                        val dragged = draggedIndex
                                        draggedIndex = null
                                        dragOffset = Offset.Zero
                                        if (dragged != null) {
                                            // Calculate target position from offset
                                            val itemHeight = 64  // approximate item height in dp
                                            val moved = (dragOffset.y / itemHeight.dp.toPx()).roundToInt()
                                            val targetIndex = (dragged + moved).coerceIn(0, waypointList.size - 1)

                                            if (dragged != targetIndex) {
                                                val newList = waypointList.toMutableList()
                                                // Use Route.moveWaypoint for validation
                                                if (route.moveWaypoint(dragged, targetIndex)) {
                                                    // Refresh list from route
                                                    waypointList = route.waypoints.toMutableList()
                                                }
                                            }
                                        }
                                    },
                                    onDragCancel = {
                                        draggedIndex = null
                                        dragOffset = Offset.Zero
                                    }
                                )
                            }
                            .clickable {
                                showActionMenu = index
                            }
                            .background(
                                when {
                                    isDragged -> MaterialTheme.colorScheme.tertiaryContainer
                                    isActive -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                    else -> Color.Transparent
                                }
                            ),
                        tonalElevation = if (isDragged) elevatedElevation else 0.dp
                    ) {
                        RouteWaypointRow(
                            wpt = wpt,
                            index = index,
                            route = route,
                            isActive = isActive,
                            navCurrentIndex = navCurrentIndex,
                            navDistance = navDistance,
                            navETE = navETE,
                            navBearing = navBearing,
                            navRouteDistanceLeft = navRouteDistanceLeft,
                            navRouteWaypointETE = navRouteWaypointETE,
                            navDirectionForward = navDirectionForward,
                            mode = mode
                        )
                    }
                }
            }
        }

        // Action menu dialog
        showActionMenu?.let { idx ->
            val wpt = route.getWaypoint(idx)
            AlertDialog(
                onDismissRequest = { showActionMenu = null },
                title = { Text(wpt.name) },
                text = {
                    Column {
                        val dist = idx.takeIf { it > 0 }?.let { route.distanceBetween(it - 1, it) }
                        if (dist != null) {
                            Text(
                                "Distance: ${StringFormatter.distanceH(dist)}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            val crs = route.course(idx - 1, idx)
                            Text(
                                "Course: ${StringFormatter.bearingH(crs)}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        val alt = StringFormatter.distanceC(wpt.altitude, 10000)
                        Text(
                            "Altitude: ${alt[0]}${alt[1]}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        onShowWaypoint(idx)
                        showActionMenu = null
                    }) {
                        Text("View")
                    }
                },
                dismissButton = {
                    when (mode) {
                        RouteDetailsMode.NAVIGATION -> {
                            TextButton(onClick = {
                                onNavigateToWaypoint(idx)
                                showActionMenu = null
                            }) {
                                Text("Navigate")
                            }
                        }
                        RouteDetailsMode.MANAGE -> {
                            TextButton(onClick = {
                                onEditWaypoint(idx)
                                showActionMenu = null
                            }) {
                                Text("Edit")
                            }
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun RouteWaypointRow(
    wpt: Waypoint,
    index: Int,
    route: Route,
    isActive: Boolean,
    navCurrentIndex: Int,
    navDistance: Double,
    navETE: Int,
    navBearing: Double,
    navRouteDistanceLeft: ((Int) -> Double)?,
    navRouteWaypointETE: ((Int) -> Int)?,
    navDirectionForward: Boolean,
    mode: RouteDetailsMode
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (isActive) {
                    Icon(
                        Icons.Default.NearMe,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = wpt.name,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    fontSize = if (isActive) 17.sp else 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isActive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = index.toString(),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.padding(start = 8.dp)
            )
            Spacer(Modifier.width(2.dp))
            Icon(
                Icons.Default.DragHandle,
                contentDescription = "Drag to reorder",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                modifier = Modifier.size(20.dp)
            )
        }

        if (mode == RouteDetailsMode.NAVIGATION && navCurrentIndex >= 0) {
            val progress = index - navCurrentIndex
            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (index > 0) {
                    val dist = if (progress == 0) navDistance
                        else route.distanceBetween(index - 1, index)
                    Text(
                        StringFormatter.distanceH(dist),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    val crs = if (progress == 0) navBearing
                        else if (navDirectionForward) route.course(index - 1, index)
                        else route.course(index, index - 1)
                    Text(
                        StringFormatter.bearingH(crs),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                if (progress >= 0) {
                    val totalDist = (if (progress > 0) navRouteDistanceLeft?.invoke(index)
                        ?: 0.0 else 0.0) + navDistance
                    Text(
                        StringFormatter.distanceH(totalDist),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    val ete = if (progress == 0) navETE
                        else navRouteWaypointETE?.invoke(index) ?: 0
                    Text(
                        StringFormatter.timeR(ete),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    val eta = navETE + (navRouteWaypointETE?.invoke(index) ?: 0)
                    Text(
                        StringFormatter.timeR(eta),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        } else {
            if (index > 0) {
                Spacer(Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val dist = route.distanceBetween(index - 1, index)
                    Text(
                        StringFormatter.distanceH(dist),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    val crs = route.course(index - 1, index)
                    Text(
                        StringFormatter.bearingH(crs),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    val totalDist = route.distanceBetween(0, index)
                    Text(
                        StringFormatter.distanceH(totalDist),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}
