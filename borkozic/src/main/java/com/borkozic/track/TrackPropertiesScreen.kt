package com.borkozic.track

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.borkozic.R
import com.borkozic.data.Track
import com.borkozic.ui.ColorButton
import com.borkozic.ui.OnColorChangedListener

@Composable
fun TrackPropertiesScreen(
    track: Track,
    defaultColor: Int,
    onSave: (Track) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf(track.name) }
    var show by remember { mutableStateOf(track.show) }
    var colorValue by remember { mutableStateOf(track.color) }
    var width by remember { mutableStateOf(track.width.toString()) }
    var showWidthDropdown by remember { mutableStateOf(false) }

    val widths = remember { (1..30).map { it.toString() } }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text("Name", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = show, onCheckedChange = { show = it })
                Text("Show", fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Color", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    TrackColorButton(
                        currentColor = colorValue,
                        defaultColor = defaultColor,
                        onColorChanged = { colorValue = it }
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Line Width", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Box {
                        OutlinedButton(
                            onClick = { showWidthDropdown = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("   $width    ")
                        }
                        DropdownMenu(
                            expanded = showWidthDropdown,
                            onDismissRequest = { showWidthDropdown = false }
                        ) {
                            widths.forEach { w ->
                                DropdownMenuItem(
                                    text = { Text("   $w    ") },
                                    onClick = { width = w; showWidthDropdown = false }
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                track.name = name
                track.show = show
                track.color = colorValue
                track.width = width.toIntOrNull() ?: track.width
                onSave(track)
            }) { Text("Done") }
        }
    }
}

@Composable
private fun TrackColorButton(
    currentColor: Int,
    defaultColor: Int,
    onColorChanged: (Int) -> Unit
) {
    AndroidView(
        factory = { ctx ->
            ColorButton(ctx).apply {
                setColor(currentColor, defaultColor)
                setOnColorChangeListener(object : OnColorChangedListener {
                    override fun colorChanged(newColor: Int) {
                        onColorChanged(newColor)
                    }
                })
            }
        },
        modifier = Modifier.wrapContentWidth(),
        update = { btn -> btn.setColor(currentColor, defaultColor) }
    )
}
