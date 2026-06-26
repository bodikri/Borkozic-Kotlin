package com.borkozic.route

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
import com.borkozic.data.Route
import com.borkozic.ui.ColorSwatchButton

@Composable
fun RoutePropertiesScreen(
    route: Route,
    defaultColor: Int,
    onSave: (Route) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf(route.name) }
    var show by remember { mutableStateOf(route.show) }
    var colorValue by remember { mutableStateOf(Color(route.lineColor)) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top row: Name field + Cancel/Done buttons
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp, 16.dp, 16.dp, 0.dp),
            verticalAlignment = Alignment.Top
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                route.name = name
                route.show = show
                route.lineColor = colorValue.toArgb()
                onSave(route)
            }) { Text("Done") }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = show, onCheckedChange = { show = it })
                Text("Show", fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(12.dp))

            Text("Color", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            ColorSwatchButton(
                currentColor = colorValue,
                defaultColor = Color(defaultColor),
                onColorChanged = { colorValue = it }
            )
        }
    }
}