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
import kotlinx.coroutines.launch

@Composable
internal fun RecentDraftScreen(onBack: () -> Unit) {
    var rows by remember { mutableStateOf<List<DraftEntryRow>>(emptyList()) }
    var uploadStates by remember { mutableStateOf<Map<Long, Boolean>>(emptyMap()) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(Unit) {
        rows = ServiceLocator.draftRepository.recent()
        uploadStates = ServiceLocator.draftSyncRepository.uploadStates(rows)
    }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("保留最近三小时", style = MaterialTheme.typography.bodyMedium); TextButton(onClick = { scope.launch { ServiceLocator.clearDrafts(); rows = emptyList() } }) { Text("全部清除") } }
        if (rows.isEmpty()) Text("暂无草稿", color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows, key = { it.id }) { row -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                Text(row.content)
                Text("${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(row.createdAt))} · ${row.appPackage} · ${row.source}", style = MaterialTheme.typography.bodySmall)
                Text(
                    if (uploadStates[row.id] == true) "已上传" else "待上传（含更新）",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (uploadStates[row.id] == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { clipboard.setText(AnnotatedString(row.content)) }) { Text("复制") }
            } } }
        }
    }
}
