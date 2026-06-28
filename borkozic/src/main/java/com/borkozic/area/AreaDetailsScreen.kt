package com.borkozic.area

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.borkozic.BaseApplication
import com.borkozic.Borkozic
import com.borkozic.data.Area
import com.borkozic.data.Waypoint
import com.borkozic.util.StringFormatter

/**
 * Режим на AreaDetails екрана.
 */
enum class AreaDetailsMode {
    /** Нормален режим — тап показва View/Edit */
    MANAGE,
    /** Навигационен режим — тап показва View/Navigate, broadcast обновявания */
    NAVIGATION
}

/**
 * Compose базиран екран за детайли на зона (area).
 * Показва списък с всички точки на зоната с:
 * - Тап → View/Edit (или View/Navigate в навигационен режим)
 * - Навигационна интеграция (progress indicator, ETE, ETA)
 * - БЕЗ drag-and-drop / пренареждане (за разлика от RouteDetails)
 *
 * @param refreshKey Увеличава се от AreaDetails за да предизвика recomposition
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AreaDetailsScreen(
    area: Area,
    mode: AreaDetailsMode,
    refreshKey: Int = 0,
    onEditWaypoint: (index: Int) -> Unit,
    onNavigateToWaypoint: (index: Int) -> Unit,
    onShowWaypoint: (index: Int) -> Unit,
    onRemoveWaypoint: (index: Int) -> Unit = {},
    onBack: () -> Unit,
    onAreaProperties: () -> Unit = {},
    navCurrentIndex: Int = -1,
    navDistance: Double = 0.0,
    navETE: Int = 0,
    navBearing: Double = 0.0,
    navRouteDistanceLeft: ((Int) -> Double)? = null,
    navRouteWaypointETE: ((Int) -> Int)? = null,
    navDirectionForward: Boolean = true
) {
    val application = BaseApplication.getApplication<Borkozic>()!!
    val waypoints = area.waypoints

    var showActionMenu by remember { mutableStateOf<Int?>(null) }
    val density = LocalContext.current

    // Списък на точките — refreshKey предизвиква recomposition при външни промени
    var waypointList by remember(area, refreshKey) { mutableStateOf(waypoints.toMutableList()) }

    // Sync list when area changes externally
    LaunchedEffect(area.waypoints.size, refreshKey) {
        waypointList = area.waypoints.toMutableList()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            area.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium
                        )
                        // Показвай типа на зоната (кръг или полигон) + брой точки
                        val subtitle = if (area.isCircleArea()) {
                            "Circle • r=${StringFormatter.distanceH(area.AreaRadius)} • ${waypointList.size} pts"
                        } else {
                            "Polygon • ${StringFormatter.distanceH(area.distance)} • ${waypointList.size} pts"
                        }
                        Text(
                            subtitle,
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
                    if (mode == AreaDetailsMode.MANAGE) {
                        IconButton(onClick = onAreaProperties) {
                            Icon(Icons.Default.Tune, contentDescription = "Area Properties")
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
                itemsIndexed(waypointList, key = { idx, _ -> idx }) { index, wpt ->
                    val isActive = if (mode == AreaDetailsMode.NAVIGATION) {
                        navCurrentIndex == index
                    } else false

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showActionMenu = index
                            }
                            .background(
                                if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                else Color.Transparent
                            ),
                        tonalElevation = 0.dp
                    ) {
                        AreaWaypointRow(
                            waypoint = wpt,
                            index = index,
                            area = area,
                            isActive = isActive,
                            mode = mode,
                            navCurrentIndex = navCurrentIndex,
                            navDistance = navDistance,
                            navBearing = navBearing,
                            navETE = navETE,
                            navRouteDistanceLeft = navRouteDistanceLeft,
                            navRouteWaypointETE = navRouteWaypointETE,
                            navDirectionForward = navDirectionForward
                        )
                    }
                }
            }
        }
    }

    // Quick action меню — идентично с RouteDetails
    showActionMenu?.let { idx ->
        val wpt = area.getWaypoint(idx)
        if (wpt != null) {
            AlertDialog(
                onDismissRequest = { showActionMenu = null },
                title = { Text(wpt.name) },
                text = {
                    Column {
                        // Дистанция и курс от предходната точка
                        if (idx > 0) {
                            val dist = area.distanceBetween(idx - 1, idx)
                            Text(
                                "Distance: ${StringFormatter.distanceH(dist)}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            val crs = area.course(idx - 1, idx)
                            Text(
                                "Course: ${StringFormatter.bearingH(crs)}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        // Надморска височина
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
                        AreaDetailsMode.NAVIGATION -> {
                            TextButton(onClick = {
                                onNavigateToWaypoint(idx)
                                showActionMenu = null
                            }) {
                                Text("Navigate")
                            }
                        }
                        AreaDetailsMode.MANAGE -> {
                            Row {
                                IconButton(onClick = {
                                    onNavigateToWaypoint(idx)
                                    showActionMenu = null
                                }) {
                                    Icon(Icons.Default.NearMe, contentDescription = "Navigate", tint = MaterialTheme.colorScheme.primary)
                                }
                                TextButton(onClick = {
                                    onEditWaypoint(idx)
                                    showActionMenu = null
                                }) {
                                    Text("Edit")
                                }
                                TextButton(onClick = {
                                    onRemoveWaypoint(idx)
                                    showActionMenu = null
                                }) {
                                    Text("Remove")
                                }
                            }
                        }
                    }
                }
            )
        }
    }
}

/**
 * Ред от списъка с точки на зоната.
 * Показва име, надморска височина, разстояние, курс и ETA (при навигация).
 * БЕЗ иконка за drag — няма пренареждане.
 */
@Composable
fun AreaWaypointRow(
    waypoint: Waypoint,
    index: Int,
    area: Area,
    isActive: Boolean,
    mode: AreaDetailsMode,
    navCurrentIndex: Int,
    navDistance: Double,
    navBearing: Double,
    navETE: Int,
    navRouteDistanceLeft: ((Int) -> Double)?,
    navRouteWaypointETE: ((Int) -> Int)?,
    navDirectionForward: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Име на точката + надморска височина
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isActive) "» ${waypoint.name}" else waypoint.name,
                fontSize = 16.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
                color = if (isActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
            )
            if (waypoint.altitude > 0) {
                val alt = StringFormatter.distanceC(waypoint.altitude, 10000)
                Text(
                    text = "${alt[0]} ${alt[1]}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        // Навигационна информация или разстояние/курс
        if (mode == AreaDetailsMode.NAVIGATION && navCurrentIndex >= 0) {
            val progress = index - navCurrentIndex
            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (index > 0) {
                    val dist = if (progress == 0) navDistance
                    else area.distanceBetween(index - 1, index)
                    Text(
                        StringFormatter.distanceH(dist),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    val crs = if (progress == 0) navBearing
                    else if (navDirectionForward) area.course(index - 1, index)
                    else area.course(index, index - 1)
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
                    val dist = area.distanceBetween(index - 1, index)
                    Text(
                        StringFormatter.distanceH(dist),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    val crs = area.course(index - 1, index)
                    Text(
                        StringFormatter.bearingH(crs),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    val totalDist = area.distanceBetween(0, index)
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