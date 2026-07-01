package com.borkozic.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.borkozic.R

/**
 * Actions dispatched by the side panel buttons.
 * (Действия, изпращани от бутоните на страничния панел.)
 */
enum class SidePanelAction {
    EP, NP,
    ZOOM_IN, ZOOM_OUT, NEXT_MAP, PREV_MAP, MAPS_AT_CURSOR, WAYPOINTS,
    INFO, FOLLOW, LOCATE, TRACKING, EXPAND,
    ZERO_LEVEL, CLEAR,
    NORTH,
}

// ── Static button data ──────────────────────────────────────────────────────
private data class StaticButton(
    val labelId: Int,
    val action: SidePanelAction,
)

private val staticTopButtons = listOf(
    StaticButton(R.string.buttonEP, SidePanelAction.EP),
    StaticButton(R.string.buttonNP, SidePanelAction.NP),
)

private val staticBottomButtons = listOf(
    StaticButton(R.string.buttonZeroLevel, SidePanelAction.ZERO_LEVEL),
    StaticButton(R.string.buttonClear, SidePanelAction.CLEAR),
)

// ── Action → drawable mapping ──────────────────────────────────────────────
private fun actionDrawableId(action: String): Int = when (action) {
    "zoomin"   -> R.drawable.zoom_in
    "zoomout"  -> R.drawable.zoom_out
    "nextmap"  -> R.drawable.arrow_right
    "prevmap"  -> R.drawable.arrow_left
    "maps"     -> R.drawable.globe
    "waypoints" -> R.drawable.flag
    "info"     -> R.drawable.info
    "north"    -> R.drawable.compass_needle_north_blue
    else       -> R.drawable.zoom_in
}

private fun actionToEnum(action: String): SidePanelAction = when (action) {
    "zoomin"    -> SidePanelAction.ZOOM_IN
    "zoomout"   -> SidePanelAction.ZOOM_OUT
    "nextmap"   -> SidePanelAction.NEXT_MAP
    "prevmap"   -> SidePanelAction.PREV_MAP
    "maps"      -> SidePanelAction.MAPS_AT_CURSOR
    "waypoints" -> SidePanelAction.WAYPOINTS
    "info"      -> SidePanelAction.INFO
    "follow"    -> SidePanelAction.FOLLOW
    "locate"    -> SidePanelAction.LOCATE
    "tracking"  -> SidePanelAction.TRACKING
    "expand"    -> SidePanelAction.EXPAND
    "north"     -> SidePanelAction.NORTH
    else        -> SidePanelAction.ZOOM_IN
}

// ── Constants ───────────────────────────────────────────────────────────────
private const val ANIM_DURATION = 300
private const val HANDLE_WIDTH_DP = 19
private const val HANDLE_HEIGHT_DP = 57
private const val PANEL_CONTENT_DP = 80

/**
 * Side panel — sliding overlay with action buttons, flush to screen edge.
 * (Страничен панел — плъзгащ се overlay с бутони, плътно до ръба на екрана.)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SidePanel(
    isOpen: Boolean,
    isOnLeft: Boolean,
    onOpenChanged: (Boolean) -> Unit,
    activeActions: List<String>,
    isFollowing: Boolean,
    isLocating: Boolean,
    isTracking: Boolean,
    isFullscreen: Boolean,
    onAction: (SidePanelAction) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // ── Scrim: tap to close ────────────────────────────────────────────
        AnimatedVisibility(
            visible = isOpen,
            enter = fadeIn(tween(ANIM_DURATION)),
            exit = fadeOut(tween(ANIM_DURATION)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { onOpenChanged(false) }
            )
        }

        // ── Handle button: centered vertically, flush to edge ──────────────
        //     Visible only when panel is CLOSED.
        AnimatedVisibility(
            visible = !isOpen,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = fadeIn(tween(ANIM_DURATION)),
            exit = fadeOut(tween(ANIM_DURATION)),
        ) {
            Box(
                modifier = Modifier
                    .width(HANDLE_WIDTH_DP.dp)
                    .height(HANDLE_HEIGHT_DP.dp)
                    .background(Color.White.copy(alpha = 0.35f))
                    .clickable { onOpenChanged(true) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.arrow_left),
                    contentDescription = "Open panel",
                    tint = Color.DarkGray,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        // ── Panel: slides in from right, stays flush to right edge ─────────
        AnimatedVisibility(
            visible = isOpen,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally(
                animationSpec = tween(ANIM_DURATION),
                initialOffsetX = { it },
            ),
            exit = slideOutHorizontally(
                animationSpec = tween(ANIM_DURATION),
                targetOffsetX = { it },
            ),
        ) {
            Row(
                modifier = Modifier.fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Close handle at the inner (left) edge of the panel — same size as open button
                Box(
                    modifier = Modifier
                        .width(HANDLE_WIDTH_DP.dp)
                        .height(HANDLE_HEIGHT_DP.dp)
                        .background(Color.White.copy(alpha = 0.35f))
                        .clickable { onOpenChanged(false) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_right),
                        contentDescription = "Close panel",
                        tint = Color.DarkGray,
                        modifier = Modifier.size(14.dp),
                    )
                }

                // Button area
                SidePanelContent(
                    activeActions = activeActions,
                    isFollowing = isFollowing,
                    isLocating = isLocating,
                    isTracking = isTracking,
                    isFullscreen = isFullscreen,
                    onAction = onAction,
                )
            }
        }
    }
}

// ── Panel content: buttons column ──────────────────────────────────────────
@Composable
private fun SidePanelContent(
    activeActions: List<String>,
    isFollowing: Boolean,
    isLocating: Boolean,
    isTracking: Boolean,
    isFullscreen: Boolean,
    onAction: (SidePanelAction) -> Unit,
) {
    val allActions = listOf(
        "zoomin", "zoomout", "nextmap", "prevmap",
        "maps", "waypoints", "info",
        "follow", "locate", "tracking", "expand",
        "north",
    )
    val actionsToShow = activeActions.filter { it in allActions }

    Surface(
        color = Color.White.copy(alpha = 0.37f),
        tonalElevation = 4.dp,
        modifier = Modifier.width(PANEL_CONTENT_DP.dp),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 6.dp),
        ) {
            items(staticTopButtons.size) { index ->
                val btn = staticTopButtons[index]
                StaticActionButton(
                    labelId = btn.labelId,
                    onClick = { onAction(btn.action) },
                )
            }

            items(actionsToShow.size) { index ->
                DynamicActionButton(
                    action = actionsToShow[index],
                    isFollowing = isFollowing,
                    isLocating = isLocating,
                    isTracking = isTracking,
                    isFullscreen = isFullscreen,
                    onClick = { onAction(actionToEnum(actionsToShow[index])) },
                )
            }

            items(staticBottomButtons.size) { index ->
                val btn = staticBottomButtons[index]
                StaticActionButton(
                    labelId = btn.labelId,
                    onClick = { onAction(btn.action) },
                )
            }
        }
    }
}

// ── Static text button (EP, NP, Zero, Clear) ───────────────────────────────
@Composable
private fun StaticActionButton(
    labelId: Int,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .width(69.dp)
            .padding(vertical = 2.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF555555),
            contentColor = Color.White,
        ),
    ) {
        Text(
            text = stringResource(labelId),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

// ── Dynamic icon button (zoom, follow, locate, tracking, expand) ───────────
@Composable
private fun DynamicActionButton(
    action: String,
    isFollowing: Boolean,
    isLocating: Boolean,
    isTracking: Boolean,
    isFullscreen: Boolean,
    onClick: () -> Unit,
) {
    val drawableId = when (action) {
        "follow"   -> if (isFollowing) R.drawable.cursor_drag_arrow else R.drawable.target
        "locate"   -> if (isLocating) R.drawable.pin_map_no else R.drawable.pin_map
        "tracking" -> if (isTracking) R.drawable.doc_delete else R.drawable.doc_edit
        "expand"   -> if (isFullscreen) R.drawable.collapse else R.drawable.expand
        else       -> actionDrawableId(action)
    }

    IconButton(
        onClick = onClick,
        modifier = Modifier
            .padding(vertical = 3.dp)
            .size(48.dp),
    ) {
        Icon(
            painter = painterResource(drawableId),
            contentDescription = action,
            modifier = Modifier.size(36.dp),
            tint = Color.Unspecified,
        )
    }
}
