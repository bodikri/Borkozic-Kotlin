package com.borkozic.waypoint

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.borkozic.BaseApplication
import com.borkozic.Borkozic
import com.borkozic.R
import com.borkozic.data.Waypoint
import com.borkozic.data.WaypointSet
import com.borkozic.ui.ActionItem
import com.borkozic.ui.ItemActionBar
import com.borkozic.ui.ThemePresets
import com.borkozic.ui.getListThemeIndex
import com.borkozic.ui.setListThemeColor
import com.borkozic.util.Geo
import com.borkozic.util.StringFormatter
import java.io.File

// ============================================================
// Action sealed classes
// ============================================================

sealed class WaypointAction {
    object View : WaypointAction()
    object Navigate : WaypointAction()
    object Edit : WaypointAction()
    object Share : WaypointAction()
    object Remove : WaypointAction()
}

sealed class WaypointSetAction {
    object Clear : WaypointSetAction()
    object Remove : WaypointSetAction()
}

// ============================================================
// Canvas icon — custom bitmap or colored rectangle
// ============================================================

@Composable
fun WaypointListIcon(
    waypoint: Waypoint,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val application = remember { BaseApplication.getApplication<Borkozic>()!! }

    val bitmap = remember(waypoint) {
        if (application.iconsEnabled && waypoint.drawImage) {
            try {
                val options = BitmapFactory.Options()
                options.inScaled = false
                BitmapFactory.decodeFile(application.iconPath + File.separator + waypoint.image, options)
            } catch (e: Exception) {
                null
            }
        } else null
    }

    Canvas(modifier = modifier) {
        val iconSize = size.minDimension
        val pointSize = 10f * (iconSize / 40f)

        if (bitmap != null) {
            val left = (size.width - bitmap!!.width.toFloat()) / 2f
            val top = (size.height - bitmap!!.height.toFloat()) / 2f
            drawContext.canvas.nativeCanvas.drawBitmap(bitmap!!, left, top, null)
        } else {
            val borderColor = if (waypoint.textcolor != Int.MIN_VALUE)
                Color(waypoint.textcolor)
            else
                Color(ContextCompat.getColor(context, R.color.waypointtext))

            val fillColor = if (waypoint.backcolor != Int.MIN_VALUE)
                Color(waypoint.backcolor)
            else
                Color(ContextCompat.getColor(context, R.color.waypoint))

            val rectLeft = (size.width - pointSize) / 2f
            val rectTop = (size.height - pointSize) / 2f

            // Border rect
            drawRect(
                color = borderColor,
                topLeft = Offset(rectLeft, rectTop),
                size = Size(pointSize, pointSize),
                style = Stroke(1f)
            )
            // Fill rect (1px inset)
            drawRect(
                color = fillColor,
                topLeft = Offset(rectLeft + 1f, rectTop + 1f),
                size = Size(pointSize - 2f, pointSize - 2f),
                style = Fill
            )
        }
    }
}

// ============================================================
// Flat list item model (for expandable groups)
// ============================================================

private sealed class FlatItem {
    data class GroupHeader(val setIdx: Int, val set: WaypointSet) : FlatItem()
    data class ChildItem(val setIdx: Int, val childIdx: Int, val waypoint: Waypoint) : FlatItem()
}

// ============================================================
// Composable: WaypointListScreen
// ============================================================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun WaypointListScreen(
    mode: Int,
    onWaypointAction: (Waypoint, WaypointAction) -> Unit,
    onSetAction: (WaypointSet, Int, WaypointSetAction) -> Unit,
    onLoadWaypoints: () -> Unit = {},
    onNewWaypoint: () -> Unit = {},
    onNewWaypointSet: (String) -> Unit = {},
    onProjectWaypoint: () -> Unit = {},
    themeVersion: Int = 0,
    onThemeChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    val application = BaseApplication.getApplication<Borkozic>()!! as Borkozic
    val waypointSets = application.waypointSets

    // Expanded groups state
    var expandedGroups by remember { mutableStateOf(mutableMapOf<Int, Boolean>()) }
    // Selection state
    var selectedSetIdx by remember { mutableStateOf<Int?>(null) }
    var selectedChildIdx by remember { mutableStateOf<Int?>(null) }
    // Multi selection
    var multiSelected by remember { mutableStateOf(mutableSetOf<Pair<Int, Int>>()) }
    // Bar visibility
    var showWaypointBar by remember { mutableStateOf(false) }
    var showSetBar by remember { mutableStateOf(false) }
    var showMultiBar by remember { mutableStateOf(false) }
    // Dialogs
    var showThemePicker by remember { mutableStateOf(false) }

    val isMultiMode = multiSelected.isNotEmpty()

    fun clearSelection() {
        showWaypointBar = false
        showSetBar = false
        selectedSetIdx = null
        selectedChildIdx = null
    }

    // Resolve selected waypoint
    val selWaypoint = remember(selectedSetIdx, selectedChildIdx) {
        if (selectedSetIdx != null && selectedChildIdx != null) {
            val set = waypointSets.getOrNull(selectedSetIdx!!) ?: return@remember null
            application.getWaypoints(set).filterNotNull().getOrNull(selectedChildIdx!!)
        } else null
    }

    // Resolve selected set
    val selSet = remember(selectedSetIdx) {
        selectedSetIdx?.let { waypointSets.getOrNull(it) }
    }

    // Waypoint action bar
    val waypointItems = selWaypoint?.let { wp ->
        listOf(
            ActionItem(WaypointList.qaWaypointVisible, "View", Icons.Default.Visibility) {
                onWaypointAction(wp, WaypointAction.View); clearSelection()
            },
            ActionItem(WaypointList.qaWaypointNavigate, "Navigate", Icons.Default.Navigation) {
                onWaypointAction(wp, WaypointAction.Navigate); clearSelection()
            },
            ActionItem(WaypointList.qaWaypointProperties, "Edit", Icons.Default.Edit) {
                onWaypointAction(wp, WaypointAction.Edit); clearSelection()
            },
            ActionItem(WaypointList.qaWaypointShare, "Share", Icons.Default.Share) {
                onWaypointAction(wp, WaypointAction.Share); clearSelection()
            },
            ActionItem(WaypointList.qaWaypointDelete, "Remove", Icons.Default.Delete) {
                onWaypointAction(wp, WaypointAction.Remove); clearSelection()
            }
        )
    } ?: emptyList()

    // Set action bar
    val setItems = selSet?.let { set ->
        val idx = selectedSetIdx!!
        listOf(
            ActionItem(WaypointList.qaWaypointSetClear, "Clear", Icons.Default.LayersClear) {
                onSetAction(set, idx, WaypointSetAction.Clear); clearSelection()
            },
            ActionItem(WaypointList.qaWaypointSetRemove, "Remove Set", Icons.Default.Cancel) {
                onSetAction(set, idx, WaypointSetAction.Remove); clearSelection()
            }
        )
    } ?: emptyList()

    // Multi-select action bar
    val multiItems = if (isMultiMode) listOf(
        ActionItem(200, "Delete", Icons.Default.Delete) {
            val toRemove = multiSelected.mapNotNull { (si, ci) ->
                val set = waypointSets.getOrNull(si) ?: return@mapNotNull null
                application.getWaypoints(set).filterNotNull().getOrNull(ci)
            }
            toRemove.forEach { application.removeWaypoint(it) }
            multiSelected = mutableSetOf(); showMultiBar = false
        }
    ) else emptyList()

    // Theme picker dialog
    if (showThemePicker) {
        val currentIdx = remember { getListThemeIndex(context, "waypoint") }
        val currentColor = ThemePresets.getOrElse(currentIdx) { ThemePresets[0] }
        AlertDialog(
            onDismissRequest = { showThemePicker = false },
            title = { Text("Waypoint List Theme") },
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
                                    setListThemeColor(context, "waypoint", idx)
                                    onThemeChanged()
                                    showThemePicker = false
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(28.dp).background(tc.darkBackground))
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
        // Top toolbar (manage mode)
        if (mode == WaypointList.MODE_MANAGE) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showThemePicker = true }) {
                    Icon(Icons.Default.ColorLens, "Theme",
                        tint = MaterialTheme.colorScheme.primary)
                }
                Row {
                    IconButton(onClick = onLoadWaypoints) {
                        Icon(Icons.Default.FileOpen, "Load",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { onNewWaypointSet("New Set") }) {
                        Icon(Icons.Default.CreateNewFolder, "New Set",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onNewWaypoint) {
                        Icon(Icons.Default.Add, "New Waypoint",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onProjectWaypoint) {
                        Icon(Icons.Default.ArrowForward, "Project",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Box(modifier = Modifier.fillMaxWidth().height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant))
        }

        // Waypoint action bar
        if (showWaypointBar && !isMultiMode && waypointItems.isNotEmpty()) {
            ItemActionBar(actions = waypointItems)
        }
        // Set action bar
        if (showSetBar && !isMultiMode && setItems.isNotEmpty()) {
            ItemActionBar(actions = setItems)
        }
        // Multi-select action bar
        if (isMultiMode && multiItems.isNotEmpty()) {
            ItemActionBar(actions = multiItems)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("${multiSelected.size} selected",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface)
                TextButton(onClick = {
                    multiSelected = mutableSetOf(); showMultiBar = false
                }) { Text("Cancel", fontSize = 12.sp) }
            }
        }

        if (waypointSets.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text("No waypoints loaded",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)))
            }
        } else {
            // Build flat list from groups + expanded children
            val flatItems = remember(waypointSets.size, expandedGroups) {
                buildList {
                    waypointSets.forEachIndexed { si, set ->
                        add(FlatItem.GroupHeader(si, set))
                        if (expandedGroups[si] == true) {
                            application.getWaypoints(set).filterNotNull()
                                .forEachIndexed { ci, wp ->
                                    add(FlatItem.ChildItem(si, ci, wp))
                                }
                        }
                    }
                }
            }

            LazyColumn(modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)) {
                itemsIndexed(flatItems, key = { idx, item ->
                    when (item) {
                        is FlatItem.GroupHeader -> "G${item.setIdx}_${item.set.hashCode()}"
                        is FlatItem.ChildItem -> "C${item.setIdx}_${item.childIdx}_${item.waypoint.hashCode()}"
                    }
                }) { _, item ->
                    when (item) {
                        is FlatItem.GroupHeader -> WaypointGroupRow(
                            setIdx = item.setIdx,
                            set = item.set,
                            isExpanded = expandedGroups[item.setIdx] == true,
                            isSelected = showSetBar && selectedSetIdx == item.setIdx,
                            childCount = application.getWaypointCount(item.set),
                            onToggle = {
                                val m = expandedGroups.toMutableMap()
                                m[item.setIdx] = !(m[item.setIdx] ?: false)
                                expandedGroups = m
                            },
                            onLongPress = {
                                clearSelection()
                                selectedSetIdx = item.setIdx
                                showSetBar = true
                            }
                        )
                        is FlatItem.ChildItem -> WaypointChildRow(
                            setIdx = item.setIdx,
                            childIdx = item.childIdx,
                            waypoint = item.waypoint,
                            isSelected = showWaypointBar &&
                                selectedSetIdx == item.setIdx &&
                                selectedChildIdx == item.childIdx,
                            isMultiSelected = Pair(item.setIdx, item.childIdx) in multiSelected,
                            isMultiMode = isMultiMode,
                            application = application,
                            onTap = {
                                if (isMultiMode) {
                                    val s = multiSelected.toMutableSet()
                                    val p = Pair(item.setIdx, item.childIdx)
                                    if (p in s) s.remove(p) else s.add(p)
                                    multiSelected = s
                                    if (s.isEmpty()) showMultiBar = false
                                } else {
                                    if (showWaypointBar &&
                                        selectedSetIdx == item.setIdx &&
                                        selectedChildIdx == item.childIdx) {
                                        clearSelection()
                                    } else {
                                        clearSelection()
                                        selectedSetIdx = item.setIdx
                                        selectedChildIdx = item.childIdx
                                        showWaypointBar = true
                                    }
                                }
                            },
                            onLongPress = {
                                clearSelection()
                                multiSelected = multiSelected.toMutableSet().apply {
                                    add(Pair(item.setIdx, item.childIdx))
                                }
                                showMultiBar = true
                            }
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
// Group header row
// ============================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WaypointGroupRow(
    setIdx: Int,
    set: WaypointSet,
    isExpanded: Boolean,
    isSelected: Boolean,
    childCount: Int,
    onToggle: () -> Unit,
    onLongPress: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle,
                onLongClick = onLongPress
            )
            .background(
                if (isSelected) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                else Color.Transparent
            ),
        tonalElevation = if (isSelected) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandMore
                    else Icons.Default.ChevronRight,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = set.name,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "$childCount",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

// ============================================================
// Waypoint child row
// ============================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WaypointChildRow(
    setIdx: Int,
    childIdx: Int,
    waypoint: Waypoint,
    isSelected: Boolean,
    isMultiSelected: Boolean,
    isMultiMode: Boolean,
    application: Borkozic,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
                onLongClick = onLongPress
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 48.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WaypointListIcon(waypoint = waypoint, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = waypoint.name,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = StringFormatter.coordinates(
                        application.coordinateFormat, " ",
                        waypoint.latitude, waypoint.longitude
                    ),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(1.dp))
                val loc = application.getLocation()
                if (loc.size >= 2) {
                    val dist = Geo.distance(loc[0], loc[1], waypoint.latitude, waypoint.longitude)
                    val bearing = Geo.bearing(loc[0], loc[1], waypoint.latitude, waypoint.longitude)
                    Text(
                        text = StringFormatter.distanceH(dist) + " " +
                            StringFormatter.bearingSimpleH(bearing),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
