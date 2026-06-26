package com.borkozic.area

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
import com.borkozic.data.Area
import com.borkozic.ui.ActionItem
import com.borkozic.ui.ItemActionBar
import com.borkozic.ui.ThemePresets
import com.borkozic.ui.getListThemeColor
import com.borkozic.ui.getListThemeIndex
import com.borkozic.ui.setListThemeColor
import com.borkozic.util.StringFormatter

// ============================================================
// AreaAction — sealed class for popup actions
// ============================================================

sealed class AreaAction {
    object Details : AreaAction()
    object NavigateArea : AreaAction()
    object Properties : AreaAction()
    object Edit : AreaAction()
    object Save : AreaAction()
    object Remove : AreaAction()
}

// ============================================================
// Canvas icon — draws different shapes for circle vs polygon areas
// ============================================================

@Composable
fun AreaListIcon(
    area: Area,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val iconSize = size.minDimension
        val scale = iconSize / 38f

        val lineColor = Color(area.lineColor.toLong())
        val borderColor = Color(area.fillColor.toLong())

        if (area.isCircleArea()) {
            // Circle area icon: circle with center dot
            val cx = iconSize / 2f
            val cy = iconSize / 2f
            val radius = iconSize * 0.38f

            // Filled circle (translucent)
            drawCircle(
                color = borderColor.copy(alpha = 0.3f),
                radius = radius,
                center = androidx.compose.ui.geometry.Offset(cx, cy)
            )
            // Circle border
            drawCircle(
                color = lineColor,
                radius = radius,
                center = androidx.compose.ui.geometry.Offset(cx, cy),
                style = Stroke(width = 2.5f * scale)
            )
            // Center dot
            val pointRadius = 2.5f * scale
            drawCircle(
                color = lineColor,
                radius = pointRadius,
                center = androidx.compose.ui.geometry.Offset(cx, cy)
            )
            drawCircle(
                color = borderColor,
                radius = pointRadius + 0.8f * scale,
                center = androidx.compose.ui.geometry.Offset(cx, cy),
                style = Stroke(width = 1f * scale)
            )
        } else {
            // Polygon area icon: trapezoid/diamond shape
            val linePath = Path().apply {
                // Diamond/trapezoid shape
                moveTo(iconSize * 0.5f, iconSize * 0.12f)  // top
                lineTo(iconSize * 0.82f, iconSize * 0.38f)  // right
                lineTo(iconSize * 0.65f, iconSize * 0.82f)  // bottom-right
                lineTo(iconSize * 0.22f, iconSize * 0.68f)  // bottom-left
                close()
            }

            // Fill (translucent)
            drawPath(
                path = linePath,
                color = borderColor.copy(alpha = 0.3f)
            )
            // Border
            drawPath(
                path = linePath,
                color = lineColor,
                style = Stroke(width = 2.5f * scale)
            )

            // Vertex dots
            val pointRadius = 2.5f * scale
            val points = listOf(
                iconSize * 0.5f to iconSize * 0.12f,
                iconSize * 0.82f to iconSize * 0.38f,
                iconSize * 0.65f to iconSize * 0.82f,
                iconSize * 0.22f to iconSize * 0.68f
            )
            points.forEach { (x, y) ->
                drawCircle(color = lineColor, radius = pointRadius, center = androidx.compose.ui.geometry.Offset(x, y))
                drawCircle(color = borderColor, radius = pointRadius + 0.8f * scale, center = androidx.compose.ui.geometry.Offset(x, y), style = Stroke(width = 1f * scale))
            }
        }
    }
}

// ============================================================
// Composable: AreaListScreen
// ============================================================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AreaListScreen(
    mode: Int,
    onAction: (Area, AreaAction) -> Unit,
    onLoadArea: () -> Unit = {},
    contentVersion: Int = 0,
    themeVersion: Int = 0,
    onThemeChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    val application = BaseApplication.getApplication<Borkozic>()!! as Borkozic
    val areas = application.areas

    // Single selection
    var selectedItemIndex by remember { mutableStateOf<Int?>(null) }
    // Multi selection (long press)
    var multiSelectedIndices by remember { mutableStateOf(mutableSetOf<Int>()) }
    // Action bar visibility
    var showActionBar by remember { mutableStateOf(false) }
    var showMultiActionBar by remember { mutableStateOf(false) }
    // Theme picker dialog
    var showThemePicker by remember { mutableStateOf(false) }
    // Circle area creation dialog

    val isMultiMode = multiSelectedIndices.isNotEmpty()

    val selectedArea by remember(selectedItemIndex, areas) {
        derivedStateOf {
            selectedItemIndex?.let { idx ->
                if (idx in areas.indices) areas[idx] else null
            }
        }
    }

    // Single-select action bar items
    val actionItems = selectedArea?.let { area ->
        listOf(
            ActionItem(AreaList.qaAreaDetails, "Details", Icons.Default.Info) {
                onAction(area, AreaAction.Details)
                showActionBar = false; selectedItemIndex = null
            },
            ActionItem(AreaList.qaAreaNavigate, "Navigate", Icons.Default.Navigation) {
                onAction(area, AreaAction.NavigateArea)
                showActionBar = false; selectedItemIndex = null
            },
            ActionItem(AreaList.qaAreaProperties, "Props", Icons.Default.Tune) {
                onAction(area, AreaAction.Properties)
                showActionBar = false; selectedItemIndex = null
            },
            ActionItem(AreaList.qaAreaEdit, "Edit", Icons.Default.Edit) {
                onAction(area, AreaAction.Edit)
                showActionBar = false; selectedItemIndex = null
            },
            ActionItem(AreaList.qaAreaSave, "Save", Icons.Default.Save) {
                onAction(area, AreaAction.Save)
                showActionBar = false; selectedItemIndex = null
            },
            ActionItem(AreaList.qaAreaRemove, "Remove", Icons.Default.Delete) {
                onAction(area, AreaAction.Remove)
                showActionBar = false; selectedItemIndex = null
            }
        )
    } ?: emptyList()

    // Multi-select action bar items
    val multiActionItems = if (isMultiMode) listOf(
        ActionItem(100, "Delete", Icons.Default.Delete) {
            val toRemove = multiSelectedIndices.mapNotNull { idx ->
                if (idx in areas.indices) areas[idx] else null
            }
            toRemove.forEach { a -> application.removeArea(a) }
            multiSelectedIndices = mutableSetOf()
            showMultiActionBar = false
            selectedItemIndex = null; showActionBar = false
        },
        ActionItem(101, "Hide", Icons.Default.VisibilityOff) {
            multiSelectedIndices.forEach { idx ->
                if (idx in areas.indices) areas[idx].show = false
            }
            multiSelectedIndices = mutableSetOf()
            showMultiActionBar = false
            selectedItemIndex = null; showActionBar = false
        },
        ActionItem(102, "Show", Icons.Default.Visibility) {
            multiSelectedIndices.forEach { idx ->
                if (idx in areas.indices) areas[idx].show = true
            }
            multiSelectedIndices = mutableSetOf()
            showMultiActionBar = false
            selectedItemIndex = null; showActionBar = false
        }
    ) else emptyList()

    // Theme picker dialog
    if (showThemePicker) {
        val currentIdx = remember { getListThemeIndex(context, "area") }
        val currentColor = ThemePresets.getOrElse(currentIdx) { ThemePresets[0] }
        AlertDialog(
            onDismissRequest = { showThemePicker = false },
            title = { Text("Area List Theme") },
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
                                    setListThemeColor(context, "area", idx)
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
        // Top bar
        if (mode == AreaList.MODE_MANAGE) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Theme picker — far left
                IconButton(onClick = { showThemePicker = true }) {
                    Icon(Icons.Default.ColorLens, contentDescription = "Theme", tint = MaterialTheme.colorScheme.primary)
                }
                // Add (Polygon) + Circle + Load — right
                Row {
                    // Polygon area button — creates new area, opens Properties
                    IconButton(onClick = {
                        val newArea = Area("New Area", "", null, true, 10.0, 1000.0)
                        application.addArea(newArea)
                        onAction(newArea, AreaAction.Properties)
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "New Polygon Area", tint = MaterialTheme.colorScheme.primary)
                    }
                    // Circle area button — creates new circle area with NO center, opens Properties
                    IconButton(onClick = {
                        val newArea = Area("New Circle", "", null, true, 10.0, 1000.0, 500.0)
                        application.addArea(newArea)
                        onAction(newArea, AreaAction.Properties)
                    }) {
                        Icon(Icons.Default.Circle, contentDescription = "New Circle Area", tint = MaterialTheme.colorScheme.primary)
                    }
                    // Load from file button
                    IconButton(onClick = onLoadArea) {
                        Icon(Icons.Default.FileOpen, contentDescription = "Load Area", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        }

        // Single-select action bar
        if (showActionBar && !isMultiMode && actionItems.isNotEmpty()) {
            ItemActionBar(actions = actionItems)
        }

        // Multi-select action bar
        if (isMultiMode && multiActionItems.isNotEmpty()) {
            ItemActionBar(actions = multiActionItems)
            // Header showing count
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

        if (areas.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No areas loaded",
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
                itemsIndexed(areas, key = { _, area -> "cv$contentVersion-${area.hashCode()}" }) { index, area ->
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
                                        // In multi-mode, tap adds/removes from set
                                        val newSet = multiSelectedIndices.toMutableSet()
                                        if (index in newSet) newSet.remove(index) else newSet.add(index)
                                        multiSelectedIndices = newSet
                                        if (newSet.isEmpty()) showMultiActionBar = false
                                    } else if (mode == AreaList.MODE_MANAGE) {
                                        // Single select: toggle
                                        if (isSelected) {
                                            selectedItemIndex = null
                                            showActionBar = false
                                        } else {
                                            selectedItemIndex = index
                                            showActionBar = true
                                        }
                                    } else {
                                        onAction(area, AreaAction.NavigateArea)
                                    }
                                },
                                onLongClick = {
                                    if (mode == AreaList.MODE_MANAGE) {
                                        // Enter multi-select mode
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
                            AreaListIcon(area = area, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = area.name,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 16.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                val distance = StringFormatter.distanceH(area.distance)
                                val areaSizeText = area.getFormattedAreaSize()
                                Text(
                                    text = "$distance · $areaSizeText",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                area.filepath?.let { fp ->
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
