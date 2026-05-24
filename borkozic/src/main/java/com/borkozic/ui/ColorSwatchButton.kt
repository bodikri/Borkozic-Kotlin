package com.borkozic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Compose replacement for the legacy ColorButton + ColorPickerDialog.
 * A small swatch button that shows the current color with a checkerboard alpha background.
 */
@Composable
fun ColorSwatchButton(
    currentColor: Color,
    defaultColor: Color,
    onColorChanged: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }

    Button(
        onClick = { showDialog = true },
        modifier = modifier.size(48.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
                .clip(CircleShape)
                .alphaCheckerboard()
                .background(currentColor, CircleShape)
                .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
        )
    }

    if (showDialog) {
        ColorPickerDialog(
            initialColor = currentColor,
            defaultColor = defaultColor,
            onColorChanged = onColorChanged,
            onDismiss = { showDialog = false }
        )
    }
}

/**
 * Alpha checkerboard background modifier (like GIMP/Photoshop transparency indicator).
 */
fun Modifier.alphaCheckerboard(): Modifier = this.drawBehind {
    val tileSize = 8f
    val tilesX = (size.width / tileSize).toInt() + 1
    val tilesY = (size.height / tileSize).toInt() + 1
    for (y in 0 until tilesY) {
        for (x in 0 until tilesX) {
            val isWhite = (x + y) % 2 == 0
            drawRect(
                color = if (isWhite) Color(0xFFFFFFFF) else Color(0xFFCCCCCC),
                topLeft = Offset(x * tileSize, y * tileSize),
                size = Size(tileSize, tileSize)
            )
        }
    }
}

/**
 * Compose Dialog that wraps the legacy ColorPickerView via AndroidView,
 * preserving the complex hue wheel + HSV slider rendering.
 */
@Composable
fun ColorPickerDialog(
    initialColor: Color,
    defaultColor: Color,
    onColorChanged: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var currentColor by remember { mutableStateOf(initialColor) }

    val colorListener = remember {
        object : OnColorChangedListener {
            override fun colorChanged(newColor: Int) {
                val withAlpha = (newColor and 0x00FFFFFF) or (currentColor.toArgb() and 0xFF000000.toInt())
                currentColor = Color(withAlpha)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Pick Color", style = MaterialTheme.typography.titleMedium)

                Spacer(Modifier.height(12.dp))

                // Legacy ColorPickerView wrapped in AndroidView
                AndroidView(
                    factory = { ctx ->
                        ColorPickerView(ctx, colorListener, initialColor.toArgb()).apply {
                            setColor(initialColor.toArgb())
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    update = { view ->
                        view.setColor(currentColor.toArgb())
                    }
                )

                Spacer(Modifier.height(12.dp))

                // Preview swatch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Preview: ", style = MaterialTheme.typography.bodyMedium)
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .alphaCheckerboard()
                            .background(currentColor, CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    )
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        onColorChanged(currentColor)
                        onDismiss()
                    }) {
                        Text("OK")
                    }
                }
            }
        }
    }
}
