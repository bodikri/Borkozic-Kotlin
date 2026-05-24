package com.borkozic.route

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
import com.borkozic.data.Route
import com.borkozic.ui.ColorButton
import com.borkozic.ui.OnColorChangedListener

@Composable
fun RoutePropertiesScreen(
    route: Route,
    defaultColor: Int,
    onSave: (Route) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf(route.name) }
    var show by remember { mutableStateOf(route.show) }
    var colorValue by remember { mutableStateOf(route.lineColor) }

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

            Text("Color", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            RouteColorButton(
                currentColor = colorValue,
                defaultColor = defaultColor,
                onColorChanged = { colorValue = it }
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
                route.name = name
                route.show = show
                route.lineColor = colorValue
                onSave(route)
            }) { Text("Done") }
        }
    }
}

@Composable
private fun RouteColorButton(
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
        update = { btn ->
            btn.setColor(currentColor, defaultColor)
        }
    )
}
