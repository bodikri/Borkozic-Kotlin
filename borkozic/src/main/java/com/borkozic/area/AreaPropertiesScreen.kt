package com.borkozic.area

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.borkozic.data.Area
import com.borkozic.ui.ColorSwatchButton

@Composable
fun AreaPropertiesScreen(
    area: Area,
    defaultLineColor: Int,
    defaultFillColor: Int,
    defaultTransparency: Int,
    onSave: (Area) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf(area.name) }
    var show by remember { mutableStateOf(area.show) }
    var lineColor by remember { mutableStateOf(Color(area.lineColor)) }
    var fillColor by remember { mutableStateOf(Color(area.fillColor)) }
    var transparency by remember { mutableStateOf(area.AreaTransperency.toFloat()) }

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

            Text("Line Color", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            ColorSwatchButton(
                currentColor = lineColor,
                defaultColor = Color(defaultLineColor),
                onColorChanged = { lineColor = it }
            )

            Spacer(Modifier.height(12.dp))

            Text("Fill Color", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            ColorSwatchButton(
                currentColor = fillColor,
                defaultColor = Color(defaultFillColor),
                onColorChanged = { fillColor = it }
            )

            Spacer(Modifier.height(12.dp))

            Text("Transparency: ${transparency.toInt()}%", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Slider(
                value = transparency,
                onValueChange = { transparency = it },
                valueRange = 10f..200f,
                modifier = Modifier.fillMaxWidth()
            )
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
                area.name = name
                area.show = show
                area.lineColor = lineColor.toArgb()
                area.fillColor = fillColor.toArgb()
                area.AreaTransperency = transparency.toInt()
                onSave(area)
            }) { Text("Done") }
        }
    }
}
