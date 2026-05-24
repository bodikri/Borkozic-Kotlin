package com.borkozic

import android.widget.SeekBar
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Главен Compose екран за MapActivity.
 * Рендерира MapView в центъра с Compose overlay за всички бараове.
 */
@Suppress("UnusedParameter")
@Composable
fun MapScreen(
    uiState: MapUiState,
    onTrackBarValueChange: (Float) -> Unit = {},
    onAction: (MapScreenAction) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // MapView е вътре в AndroidView в центъра — управлява се от MapActivity
        // (MapActivity добавя AndroidView(MapView) чрез setContent)
        // Но тук ще оставим място за него — MapActivity ще управлява самия MapView

        // ═══════ TOP OVERLAY ═══════
        Column {
            // Sat info bar
            AnimatedVisibility(
                visible = uiState.showSatInfoBar,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                SatInfoBar(
                    coordinates = uiState.coordinates,
                    accuracy = uiState.accuracyText,
                    satInfo = uiState.satInfoText,
                    satInfoColor = uiState.satInfoColor
                )
            }

            // Moving info panel (GPS + navigation data)
            AnimatedVisibility(
                visible = uiState.showMovingInfo,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                MovingInfoPanel(uiState)
            }
        }

        // ═══════ RIGHT: Track Details (aligned to right, above bottom bar) ═══════
        AnimatedVisibility(
            visible = uiState.showTrackDetails,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = if (uiState.showMovingInfo) 80.dp else 32.dp, end = 0.dp, bottom = 80.dp),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            TrackDetailsOverlay(uiState)
        }

        // ═══════ BOTTOM OVERLAY ═══════
        Column(
            modifier = Modifier.align(Alignment.BottomStart)
        ) {
            // Edit track panel
            AnimatedVisibility(
                visible = uiState.showEditTrack,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                EditTrackPanel(
                    progress = uiState.trackBarProgress,
                    max = uiState.trackBarMax,
                    onValueChange = onTrackBarValueChange,
                    onCutBefore = { onAction(MapScreenAction.CutBefore) },
                    onCutAfter = { onAction(MapScreenAction.CutAfter) },
                    onFinishTrackEdit = { onAction(MapScreenAction.FinishTrackEdit) }
                )
            }

            // Edit route/area panel (shared UI)
            AnimatedVisibility(
                visible = uiState.showEditRoute,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                EditRoutePanel(
                    onFinishEdit = { onAction(MapScreenAction.FinishEdit) },
                    onAddPoint = { onAction(MapScreenAction.AddPoint) },
                    onInsertPoint = { onAction(MapScreenAction.InsertPoint) },
                    onRemovePoint = { onAction(MapScreenAction.RemovePoint) },
                    onOrderPoints = { onAction(MapScreenAction.OrderPoints) }
                )
            }

            // Edit area panel (same buttons, handled by Activity logic)
            AnimatedVisibility(
                visible = uiState.showEditArea,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                EditAreaPanel(
                    onFinishEdit = { onAction(MapScreenAction.FinishEdit) },
                    onAddPoint = { onAction(MapScreenAction.AddPoint) },
                    onInsertPoint = { onAction(MapScreenAction.InsertPoint) },
                    onRemovePoint = { onAction(MapScreenAction.RemovePoint) },
                    onOrderPoints = { onAction(MapScreenAction.OrderPoints) }
                )
            }

            // Map info bar
            AnimatedVisibility(
                visible = uiState.showMapInfoBar,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                MapInfoBar(
                    currentFile = uiState.currentFile,
                    mapZoom = uiState.mapZoom
                )
            }
        }

        // ═══════ CENTER: Wait bar ═══════
        AnimatedVisibility(
            visible = uiState.showWaitBar,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            WaitBar(text = uiState.waitBarText)
        }
    }
}

/** Действия от MapScreen към MapActivity. */
enum class MapScreenAction {
    CutBefore, CutAfter, FinishTrackEdit,
    FinishEdit, AddPoint, InsertPoint, RemovePoint, OrderPoints
}

// ── Sat Info Bar (top, replacing inc_satinfo.xml) ────────────────────────

@Composable
fun SatInfoBar(
    coordinates: String,
    accuracy: String,
    satInfo: String,
    satInfoColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.53f))
            .padding(horizontal = 3.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = coordinates,
            color = Color.White,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = accuracy,
            color = Color.White,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = satInfo,
            color = satInfoColor,
            fontSize = 14.sp,
            maxLines = 1,
            textAlign = TextAlign.End
        )
    }
}

// ── Moving Info Panel (replaces movinginfo + sub-layouts) ──────────────────

@Composable
fun MovingInfoPanel(state: MapUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.53f))
            .padding(horizontal = 3.dp, vertical = 1.dp)
    ) {
        // Row 1: route info + waypoint info
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            AnimatedVisibility(visible = state.showRouteInfo) {
                Text(
                    text = "${state.routeName}  ${state.routeExtra}",
                    color = Color.White,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            AnimatedVisibility(visible = state.showWaypointInfo) {
                Text(
                    text = "${state.waypointName}  ${state.waypointExtra}",
                    color = Color(0xFFFFD000),
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End
                )
            }
        }

        // Row 2: speed | turn | xtk
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            InfoValueLabel(
                value = state.speedValue,
                labels = listOf(state.speedName, state.speedUnit),
                valueSize = 25.sp,
                labelSize = 12.sp
            )
            AnimatedVisibility(visible = state.showTurn) {
                InfoValueLabel(
                    value = state.turnValue,
                    labels = listOf("turn", "deg"),
                    valueSize = 25.sp,
                    labelSize = 12.sp
                )
            }
            AnimatedVisibility(visible = state.showXtk) {
                InfoValueLabel(
                    value = state.xtkValue,
                    labels = listOf("xtk", state.xtkUnit),
                    valueSize = 25.sp,
                    labelSize = 12.sp
                )
            }
        }

        // Row 3: elevation | track | below/above
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            InfoValueLabel(
                value = state.elevationValue,
                labels = listOf(state.elevationName, state.elevationUnit),
                valueSize = 25.sp,
                labelSize = 12.sp,
                valueColor = state.elevationColor,
                labelColor = state.elevationColor
            )
            InfoValueLabel(
                value = state.trackValue,
                labels = listOf("track", state.trackUnit),
                valueSize = 25.sp,
                labelSize = 12.sp
            )
            AnimatedVisibility(visible = state.showBelowabove) {
                InfoValueLabel(
                    value = state.belowaboveValue,
                    labels = listOf(state.belowaboveName, state.belowaboveUnit),
                    valueSize = 25.sp,
                    labelSize = 12.sp,
                    valueColor = state.belowaboveColor,
                    labelColor = state.belowaboveColor
                )
            }
        }
    }
}

/**
 * Reusable: big value on left, small label + unit stacked on right.
 */
@Composable
fun InfoValueLabel(
    value: String,
    labels: List<String>,
    valueSize: androidx.compose.ui.unit.TextUnit,
    labelSize: androidx.compose.ui.unit.TextUnit,
    valueColor: Color = Color.White,
    labelColor: Color = Color.White,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(3.dp)
    ) {
        Text(
            text = value,
            color = valueColor,
            fontSize = valueSize,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
        Column {
            labels.forEach { label ->
                Text(text = label, color = labelColor, fontSize = labelSize)
            }
        }
    }
}

// ── Map Info Bar (bottom, replaces inc_mapinfo.xml) ───────────────────────

@Composable
fun MapInfoBar(
    currentFile: String,
    mapZoom: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.53f))
            .padding(horizontal = 3.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = currentFile,
            color = Color.White,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = mapZoom,
            color = Color.White,
            fontSize = 14.sp,
            textAlign = TextAlign.End
        )
    }
}

// ── Edit Track Panel (replaces inc_trackedit.xml) ─────────────────────────

@Composable
fun EditTrackPanel(
    progress: Float,
    max: Float,
    onValueChange: (Float) -> Unit,
    onCutBefore: () -> Unit,
    onCutAfter: () -> Unit,
    onFinishTrackEdit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.53f))
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onFinishTrackEdit) {
            Icon(
                painter = painterResource(id = R.drawable.ic_action_tick),
                contentDescription = "Finish edit",
                tint = Color.White
            )
        }

        Slider(
            value = if (max > 0) progress / max else 0f,
            onValueChange = { onValueChange(it * max) },
            modifier = Modifier.weight(1f)
        )

        IconButton(onClick = onCutBefore) {
            Icon(
                painter = painterResource(id = R.drawable.ic_action_list),
                contentDescription = "Cut before",
                tint = Color.White
            )
        }
        IconButton(onClick = onCutAfter) {
            Icon(
                painter = painterResource(id = R.drawable.ic_action_list),
                contentDescription = "Cut after",
                tint = Color.White
            )
        }
    }
}

// ── Edit Route Panel (replaces inc_routeedit.xml) ─────────────────────────

@Composable
fun EditRoutePanel(
    onFinishEdit: () -> Unit,
    onAddPoint: () -> Unit,
    onInsertPoint: () -> Unit,
    onRemovePoint: () -> Unit,
    onOrderPoints: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.53f))
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onFinishEdit,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_action_tick),
                contentDescription = "Finish",
                tint = Color.White
            )
        }
        IconButton(
            onClick = onAddPoint,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_action_tick),
                contentDescription = "Add point",
                tint = Color.White
            )
        }
        IconButton(
            onClick = onInsertPoint,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_action_tick),
                contentDescription = "Insert point",
                tint = Color.White
            )
        }
        IconButton(
            onClick = onRemovePoint,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_action_tick),
                contentDescription = "Remove point",
                tint = Color.White
            )
        }
        IconButton(
            onClick = onOrderPoints,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_action_tick),
                contentDescription = "Order points",
                tint = Color.White
            )
        }
    }
}

// Edit Area Panel — same layout as route
@Composable
fun EditAreaPanel(
    onFinishEdit: () -> Unit,
    onAddPoint: () -> Unit,
    onInsertPoint: () -> Unit,
    onRemovePoint: () -> Unit,
    onOrderPoints: () -> Unit,
) {
    // Identical UI, MapActivity distinguishes via editingArea != null
    EditRoutePanel(
        onFinishEdit = onFinishEdit,
        onAddPoint = onAddPoint,
        onInsertPoint = onInsertPoint,
        onRemovePoint = onRemovePoint,
        onOrderPoints = onOrderPoints
    )
}

// ── Track Details Overlay (replaces inc_trackdetails.xml) ────────────────

@Composable
fun TrackDetailsOverlay(state: MapUiState) {
    Column(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.53f))
            .padding(3.dp)
            .wrapContentSize()
    ) {
        Text(text = "#${state.tpNumber}", color = Color.White, fontSize = 12.sp)
        Text(text = state.tpLatitude, color = Color.White, fontSize = 12.sp)
        Text(text = state.tpLongitude, color = Color.White, fontSize = 12.sp)
        Text(text = state.tpElevation, color = Color.White, fontSize = 12.sp)
        Text(text = state.tpTime, color = Color.White, fontSize = 12.sp)
    }
}

// ── Wait Bar (replaces inc_wait.xml) ──────────────────────────────────────

@Composable
fun WaitBar(text: String) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 16.sp,
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.53f))
            .padding(6.dp)
    )
}
