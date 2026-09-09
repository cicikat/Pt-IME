package com.chacha.jadeime.settings.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.chacha.jadeime.ServiceLocator
import com.chacha.jadeime.data.DraftEntryRow
import java.text.SimpleDateFormat
import java.util.*

@Composable
internal fun RecentDraftScreen(onBack: () -> Unit) {
    var rows by remember { mutableStateOf<List<DraftEntryRow>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(Unit) { rows = ServiceLocator.draftRepository.recent() }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("最近输入（3小时）", style = MaterialTheme.typography.titleLarge); Row { TextButton(onClick = { scope.launch { ServiceLocator.draftRepository.clear(); rows = emptyList() } }) { Text("全部清除") }; TextButton(onClick = onBack) { Text("返回") } } }
        Text("仅显示已脱敏内容，数字显示为 *。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows, key = { it.id }) { row -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                Text(row.content)
                Text("${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(row.createdAt))} · ${row.appPackage} · ${row.source}", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { clipboard.setText(AnnotatedString(row.content)) }) { Text("复制") }
            } } }
        }
    }
}
