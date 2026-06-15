package com.borkozic.route

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.borkozic.BaseApplication
import com.borkozic.Borkozic
import com.borkozic.data.Route
import com.borkozic.ui.ActionItem
import com.borkozic.ui.ItemActionBar
import com.borkozic.ui.ThemePresets
import com.borkozic.ui.getListThemeIndex
import com.borkozic.ui.setListThemeColor
import com.borkozic.util.StringFormatter

sealed class RouteAction {
    object Details : RouteAction()
    object NavigateRoute : RouteAction()
    object Properties : RouteAction()
    object Edit : RouteAction()
    object Save : RouteAction()
    object Remove : RouteAction()
}

@Composable
fun RouteListIcon(
    route: Route,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val iconSize = size.minDimension
        val scale = iconSize / 38f

        val linePath = Path().apply {
            moveTo(12f * scale, 5f * scale)
            lineTo(24f * scale, 12f * scale)
            lineTo(15f * scale, 24f * scale)
            lineTo(28f * scale, 35f * scale)
        }

        val lineColor = Color(route.lineColor.toLong())

        drawPath(path = linePath, color = lineColor, style = Stroke(width = 3f * scale))
        drawPath(path = linePath, color = Color.White, style = Stroke(width = 1f * scale))

        val pointRadius = 2.5f * scale
        val points = listOf(
            12f * scale to 5f * scale,
            24f * scale to 12f * scale,
            15f * scale to 24f * scale,
            28f * scale to 35f * scale
        )
        points.forEach { (x, y) ->
            drawCircle(color = lineColor, radius = pointRadius,
                center = androidx.compose.ui.geometry.Offset(x, y))
            drawCircle(color = Color.White, radius = pointRadius - 0.5f * scale,
                center = androidx.compose.ui.geometry.Offset(x, y))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RouteListScreen(
    mode: Int,
    onAction: (Route, RouteAction) -> Unit,
    onLoadRoute: () -> Unit = {},
    contentVersion: Int = 0,
    themeVersion: Int = 0,
    onThemeChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    val application = BaseApplication.getApplication<Borkozic>()!! as Borkozic
    val routes = application.routes

    var selectedItemIndex by remember { mutableStateOf<Int?>(null) }
    var multiSelectedIndices by remember { mutableStateOf(mutableSetOf<Int>()) }
    var showActionBar by remember { mutableStateOf(false) }
    var showMultiActionBar by remember { mutableStateOf(false) }
    var showThemePicker by remember { mutableStateOf(false) }

    val isMultiMode = multiSelectedIndices.isNotEmpty()

    val selectedRoute by remember(selectedItemIndex, routes) {
        derivedStateOf {
            selectedItemIndex?.let { idx ->
                if (idx in routes.indices) routes[idx] else null
            }
        }
    }

    val actionItems = selectedRoute?.let { route ->
        listOf(
            ActionItem(RouteList.qaRouteDetails, "Details", Icons.Default.Info) {
                onAction(route, RouteAction.Details)
                showActionBar = false; selectedItemIndex = null
            },
            ActionItem(RouteList.qaRouteNavigate, "Navigate", Icons.Default.Navigation) {
                onAction(route, RouteAction.NavigateRoute)
                showActionBar = false; selectedItemIndex = null
            },
            ActionItem(RouteList.qaRouteProperties, "Props", Icons.Default.Tune) {
                onAction(route, RouteAction.Properties)
                showActionBar = false; selectedItemIndex = null
            },
            ActionItem(RouteList.qaRouteEdit, "Edit", Icons.Default.Edit) {
                onAction(route, RouteAction.Edit)
                showActionBar = false; selectedItemIndex = null
            },
            ActionItem(RouteList.qaRouteSave, "Save", Icons.Default.Save) {
                onAction(route, RouteAction.Save)
                showActionBar = false; selectedItemIndex = null
            },
            ActionItem(RouteList.qaRouteRemove, "Remove", Icons.Default.Delete) {
                onAction(route, RouteAction.Remove)
                showActionBar = false; selectedItemIndex = null
            }
        )
    } ?: emptyList()

    val multiActionItems = if (isMultiMode) listOf(
        ActionItem(100, "Delete", Icons.Default.Delete) {
            val toRemove = multiSelectedIndices.mapNotNull { idx ->
                if (idx in routes.indices) routes[idx] else null
            }
            toRemove.forEach { r -> application.removeRoute(r) }
            multiSelectedIndices = mutableSetOf()
            showMultiActionBar = false
            selectedItemIndex = null; showActionBar = false
        },
        ActionItem(101, "Hide", Icons.Default.VisibilityOff) {
            multiSelectedIndices.forEach { idx ->
                if (idx in routes.indices) routes[idx].show = false
            }
            multiSelectedIndices = mutableSetOf()
            showMultiActionBar = false
            selectedItemIndex = null; showActionBar = false
        },
        ActionItem(102, "Show", Icons.Default.Visibility) {
            multiSelectedIndices.forEach { idx ->
                if (idx in routes.indices) routes[idx].show = true
            }
            multiSelectedIndices = mutableSetOf()
            showMultiActionBar = false
            selectedItemIndex = null; showActionBar = false
        }
    ) else emptyList()

    if (showThemePicker) {
        val currentIdx = remember { getListThemeIndex(context, "route") }
        val currentColor = ThemePresets.getOrElse(currentIdx) { ThemePresets[0] }
        AlertDialog(
            onDismissRequest = { showThemePicker = false },
            title = { Text("Route List Theme") },
            confirmButton = {
                TextButton(onClick = { showThemePicker = false }) { Text("Close") }
            },
            text = {
                Column {
                    ThemePresets.forEachIndexed { idx, tc ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    setListThemeColor(context, "route", idx)
                                    onThemeChanged()
                                    showThemePicker = false
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(tc.darkBackground)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                tc.name,
                                fontWeight = if (tc.darkBackground == currentColor.darkBackground) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (mode == RouteList.MODE_MANAGE) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showThemePicker = true }) {
                    Icon(Icons.Default.ColorLens, contentDescription = "Theme",
                        tint = MaterialTheme.colorScheme.primary)
                }
                Row {
                    IconButton(onClick = {
                        val newRoute = Route()
                        newRoute.name = "New Route"
                        newRoute.show = true
                        newRoute.lineColor = -0x1000000
                        application.addRoute(newRoute)
                        onAction(newRoute, RouteAction.Edit)
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "New Route",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onLoadRoute) {
                        Icon(Icons.Default.FileOpen, contentDescription = "Load Route",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Box(modifier = Modifier.fillMaxWidth().height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant))
        }

        if (showActionBar && !isMultiMode && actionItems.isNotEmpty()) {
            ItemActionBar(actions = actionItems)
        }

        if (isMultiMode && multiActionItems.isNotEmpty()) {
            ItemActionBar(actions = multiActionItems)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${multiSelectedIndices.size} selected",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(onClick = {
                    multiSelectedIndices = mutableSetOf()
                    showMultiActionBar = false
                }) {
                    Text("Cancel", fontSize = 12.sp)
                }
            }
        }

        if (routes.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No routes loaded",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                itemsIndexed(routes, key = { _, route -> "cv$contentVersion-${route.hashCode()}" }) { index, route ->
                    val isSelected = selectedItemIndex == index
                    val isMultiSelected = index in multiSelectedIndices

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    if (isMultiMode) {
                                        val newSet = multiSelectedIndices.toMutableSet()
                                        if (index in newSet) newSet.remove(index) else newSet.add(index)
                                        multiSelectedIndices = newSet
                                        if (newSet.isEmpty()) showMultiActionBar = false
                                    } else if (mode == RouteList.MODE_MANAGE) {
                                        if (isSelected) {
                                            selectedItemIndex = null
                                            showActionBar = false
                                        } else {
                                            selectedItemIndex = index
                                            showActionBar = true
                                        }
                                    } else {
                                        onAction(route, RouteAction.NavigateRoute)
                                    }
                                },
                                onLongClick = {
                                    if (mode == RouteList.MODE_MANAGE) {
                                        val newSet = multiSelectedIndices.toMutableSet()
                                        newSet.add(index)
                                        multiSelectedIndices = newSet
                                        showActionBar = false
                                        showMultiActionBar = true
                                        selectedItemIndex = null
                                    }
                                }
                            )
                            .background(
                                when {
                                    isMultiSelected -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                                    isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                    else -> Color.Transparent
                                }
                            ),
                        tonalElevation = if (isSelected || isMultiSelected) 2.dp else 0.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RouteListIcon(route = route, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = route.name,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 16.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                val distance = StringFormatter.distanceH(route.distance)
                                Text(
                                    text = distance,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                route.filepath?.let { fp ->
                                    if (fp.isNotEmpty()) {
                                        val displayPath = application.dataPath?.let { dp ->
                                            if (fp.startsWith(dp)) fp.substring(dp.length + 1) else fp
                                        } ?: fp
                                        Text(
                                            text = displayPath,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
