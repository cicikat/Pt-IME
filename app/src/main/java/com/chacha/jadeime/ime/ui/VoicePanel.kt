package com.chacha.jadeime.ime.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.chacha.jadeime.theme.JadeTheme

internal data class VoiceUiState(
    val message: String = "正在启动麦克风…",
    val listening: Boolean = false,
    val busy: Boolean = true,
    val level: Float = 0f,
    val partial: String = "",
)

@Composable
internal fun VoicePanel(state: VoiceUiState, theme: JadeTheme, onStop: () -> Unit, onCancel: () -> Unit, onSettings: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(state.message, color = theme.text)
        Canvas(Modifier.fillMaxWidth().height(48.dp)) {
            // The recognizer's measured RMS drives the bars; no fake recording animation.
            for (i in 0 until 25) {
                val x = size.width * (i + 1) / 26
                val shape = 0.25f + 0.75f * (1f - kotlin.math.abs(i - 12) / 12f)
                val h = 3.dp.toPx() + size.height * 0.8f * state.level * shape
                drawLine(theme.text, Offset(x, (size.height - h) / 2), Offset(x, (size.height + h) / 2), 4.dp.toPx(), StrokeCap.Round)
            }
        }
        Text(
            state.partial.ifBlank { if (state.listening) "请说话，音量条随声音变化" else "识别文字会显示在这里" },
            color = theme.text,
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
        )
        Row {
            if (state.listening) TextButton(onClick = onStop) { Text("完成") }
            if (!state.busy) TextButton(onClick = onSettings) { Text("录音权限设置") }
            TextButton(onClick = onCancel) { Text(if (state.busy) "取消" else "返回键盘") }
        }
    }
}
