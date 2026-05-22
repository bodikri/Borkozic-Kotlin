package com.borkozic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============================================================
// ActionItem — data class for a single action button
// ============================================================

data class ActionItem(
    val id: Int,
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

// ============================================================
// ItemActionBar — reusable horizontal scrollable action menu
//
// Shows action buttons inline (not a popup). ~3 buttons visible
// at a time; user can scroll horizontally for more.
// Reusable across Area, Route, Track, Waypoint lists.
// ============================================================

@Composable
fun ItemActionBar(
    actions: List<ActionItem>,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.97f)
    ) {
        LazyRow(
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(actions, key = { it.id }) { action ->
                ActionButton(action)
            }
        }
    }
    // Thin border on top — matches original look
    Divider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

// ============================================================
// ActionButton — single button in the bar
// Width ~72dp so 3 fit on a typical phone screen (~216dp)
// ============================================================

@Composable
private fun ActionButton(action: ActionItem) {
    Column(
        modifier = Modifier
            .width(72.dp)
            .clickable(onClick = action.onClick)
            .padding(vertical = 2.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = action.label,
            modifier = Modifier.size(22.dp),
            tint = if (action.label == "Remove" || action.label == "Delete")
                MaterialTheme.colorScheme.error
            else
                MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = action.label,
            fontSize = 10.sp,
            color = if (action.label == "Remove" || action.label == "Delete")
                MaterialTheme.colorScheme.error
            else
                MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
