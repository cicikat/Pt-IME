package com.chacha.jadeime.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chacha.jadeime.theme.JadeTheme

@Composable
internal fun SettingsPageHeader(
    title: String,
    description: String,
    darkMode: Boolean,
    onToggleDarkMode: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            OutlinedButton(onClick = onBack) { Text("‹ 返回") }
            Spacer(Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onClick = onToggleDarkMode) {
            Text(if (darkMode) "日间" else "夜间")
        }
    }
}

@Composable
internal fun SettingsNavigationCard(onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(18.dp)) {
            Text("主题与皮肤", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text("选择内置或导入的键盘皮肤，修改立即同步到键盘。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun ThemeSwatch(name: String, theme: JadeTheme?, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(64.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .background(theme?.background ?: MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    shape = CircleShape,
                ),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(16.dp)
                    .background(theme?.keyActive ?: MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(name, fontSize = 11.sp, maxLines = 1)
    }
}
