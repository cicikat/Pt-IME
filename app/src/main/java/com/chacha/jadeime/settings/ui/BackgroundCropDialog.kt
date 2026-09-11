package com.chacha.jadeime.settings.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chacha.jadeime.theme.*

@Composable
internal fun BackgroundCropDialog(path: String, initial: BackgroundCrop, theme: JadeTheme, onCancel: () -> Unit, onSave: (BackgroundCrop) -> Unit) {
    var crop by remember(path) { mutableStateOf(initial) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var image by remember { mutableStateOf(IntSize.Zero) }
    val currentCrop by rememberUpdatedState(crop)
    val aspect = LocalConfiguration.current.screenWidthDp / (268f * theme.keyboardHeightScale)
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCancel) { BackArrow() }
                    Text("调整背景", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { onSave(crop) }, enabled = image.width > 0) { Text("保存") }
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.fillMaxWidth().aspectRatio(aspect).clipToBounds().background(Color.DarkGray)
                    .onSizeChanged { viewport = it }
                    .pointerInput(image, viewport) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            crop = currentCrop.copy(zoom = (currentCrop.zoom * zoom).coerceIn(1f, 4f))
                                .dragged(pan.x, pan.y, image.width.toFloat(), image.height.toFloat(), viewport.width.toFloat(), viewport.height.toFloat())
                        }
                    }) {
                    KeyboardBackground(path, crop, 0f, Modifier.fillMaxSize(), onSize = { w, h -> image = IntSize(w, h) })
                    Canvas(Modifier.fillMaxSize()) {
                        for (i in 1..2) {
                            drawLine(Color.White.copy(alpha = .5f), Offset(size.width * i / 3, 0f), Offset(size.width * i / 3, size.height), 1.dp.toPx())
                            drawLine(Color.White.copy(alpha = .5f), Offset(0f, size.height * i / 3), Offset(size.width, size.height * i / 3), 1.dp.toPx())
                        }
                    }
                }
                Text("拖动选择区域，双指或滑块缩放", style = MaterialTheme.typography.bodyMedium)
                Slider(crop.zoom, { crop = crop.copy(zoom = it) }, valueRange = 1f..4f)
                TextButton(onClick = { crop = BackgroundCrop() }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("居中重置") }
                Spacer(Modifier.weight(1f))
            }
        }
    }
}
