package com.borkozic.area

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
    val isCircle = area.isCircleArea()

    // Circle area: center coordinates + radius
    var latText by remember { mutableStateOf(if (area.AreaCenter != null) String.format("%.6f", area.AreaCenter!!.latitude) else "") }
    var lonText by remember { mutableStateOf(if (area.AreaCenter != null) String.format("%.6f", area.AreaCenter!!.longitude) else "") }
    var radiusText by remember { mutableStateOf(
        if (isCircle && area.AreaRadius > 0) {
            if (area.AreaRadius >= 1000.0) String.format("%.2f", area.AreaRadius / 1000.0) else area.AreaRadius.toLong().toString()
        } else ""
    ) }
    var radiusUnit by remember { mutableStateOf<String>(if (area.AreaRadius >= 1000.0) "km" else "m") }

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

            // Circle area: center coordinates + radius
            if (isCircle) {
                Spacer(Modifier.height(16.dp))

                // Center coordinates
                Text("Center", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))

                Text("Latitude", fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                OutlinedTextField(
                    value = latText,
                    onValueChange = { value: String -> latText = value.filter { ch: Char -> ch.isDigit() || ch == '.' || ch == '-' } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    label = { Text("DD.DDDDDD") }
                )

                Spacer(Modifier.height(8.dp))

                Text("Longitude", fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                OutlinedTextField(
                    value = lonText,
                    onValueChange = { value: String -> lonText = value.filter { ch: Char -> ch.isDigit() || ch == '.' || ch == '-' } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    label = { Text("DD.DDDDDD") }
                )

                Spacer(Modifier.height(16.dp))

                // Radius with m/km toggle
                Text("Radius", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = radiusText,
                        onValueChange = { value: String ->
                            radiusText = value.filter { ch: Char -> ch.isDigit() || ch == '.' }
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        label = { Text("0") }
                    )
                    // m / km toggle
                    FilterChip(
                        selected = radiusUnit == "m",
                        onClick = { radiusUnit = "m" },
                        label = { Text("m") }
                    )
                    FilterChip(
                        selected = radiusUnit == "km",
                        onClick = { radiusUnit = "km" },
                        label = { Text("km") }
                    )
                }
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

                // Update circle area center + radius
                if (isCircle) {
                    val lat = latText.toDoubleOrNull()
                    val lon = lonText.toDoubleOrNull()
                    val radVal = radiusText.toDoubleOrNull() ?: 0.0
                    val radiusMeters = if (radiusUnit == "km") radVal * 1000.0 else radVal
                    area.AreaRadius = radiusMeters

                    // Only set center if user entered coordinates
                    // If lat/lon are empty, leave AreaCenter as null (map will use GPS location)
                    if (lat != null && lon != null) {
                        if (area.AreaCenter == null) {
                            area.AreaCenter = com.borkozic.data.Waypoint(name, "", lat, lon, 0.0)
                        } else {
                            area.AreaCenter!!.latitude = lat
                            area.AreaCenter!!.longitude = lon
                        }
                    }
                }

                // Calculate area size
                area.areaSize = area.calculateArea()

                onSave(area)
            }) { Text("Done") }
        }
    }
}