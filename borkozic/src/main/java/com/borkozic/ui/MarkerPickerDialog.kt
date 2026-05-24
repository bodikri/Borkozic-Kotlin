package com.borkozic.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.borkozic.BaseApplication
import com.borkozic.Borkozic
import java.io.File
import java.util.Collections

data class MarkerIconItem(val name: String, val bitmap: Bitmap)

@Composable
fun MarkerPickerDialog(
    onIconPicked: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val application = BaseApplication.getApplication<Borkozic>()!!

    val iconItems = remember {
        val result = mutableListOf<File>()
        val dir = File(application.iconPath!!)
        val files = dir.listFiles { _, filename -> filename.lowercase().endsWith(".png") }
        if (files != null) result.addAll(files)
        Collections.sort(result)

        result.mapNotNull { file ->
            val bmp = BitmapFactory.decodeFile(file.absolutePath)
            if (bmp != null) MarkerIconItem(file.name, bmp) else null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Select Icon",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (iconItems.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No icons found", textAlign = TextAlign.Center)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 64.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(iconItems) { _, item ->
                            Surface(
                                onClick = { onIconPicked(item.name) },
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                modifier = Modifier.padding(2.dp)
                            ) {
                                Image(
                                    bitmap = item.bitmap.asImageBitmap(),
                                    contentDescription = item.name,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .padding(4.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = onDismiss) { Text("Cancel") }
                }
            }
        }
    }
}
