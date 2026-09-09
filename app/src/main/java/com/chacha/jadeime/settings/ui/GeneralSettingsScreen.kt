package com.chacha.jadeime.settings.ui

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chacha.jadeime.ServiceLocator
import com.chacha.jadeime.data.CustomPhraseRow
import kotlinx.coroutines.launch

/** IME enablement, app-level controls, and phrase management. */
@Composable
internal fun GeneralSettingsScreen(
    imeEnabled: Boolean,
    darkMode: Boolean,
    onToggleDarkMode: () -> Unit,
    onOpenTheme: () -> Unit,
    onOpenRecent: () -> Unit,
    onOpenImeSettings: () -> Unit,
    onShowPicker: () -> Unit,
    onOpenAudioSettings: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            SettingsPageHeader(
                title = "Pt键盘设置",
                description = "本地离线优先，不记录或上传输入明文。",
                darkMode = darkMode,
                onToggleDarkMode = onToggleDarkMode,
            )
        }
        item { ImeStatusSection(imeEnabled, onOpenImeSettings, onShowPicker) }
        item {
            val granted = androidx.compose.ui.platform.LocalContext.current.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            Card(modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (granted) "录音权限：已允许" else "录音权限：未允许", style = MaterialTheme.typography.titleMedium)
                Text("长按空格使用语音输入。拒绝权限不影响普通空格输入。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = onOpenAudioSettings) { Text("打开录音权限设置") }
            }}
        }
        item { SettingsNavigationCard(onOpenTheme) }
        item { OutlinedButton(onClick = onOpenRecent, modifier = Modifier.fillMaxWidth()) { Text("最近输入（3小时）") } }
        item { CustomPhraseSection() }
        item { EmojiMappingSection() }
        item { DraftSyncSection() }
    }
}

@Composable
private fun DraftSyncSection() {
    val repo = ServiceLocator.draftSyncRepository
    var enabled by remember { mutableStateOf(repo.enabled) }
    var endpoint by remember { mutableStateOf(repo.endpoint) }
    var token by remember { mutableStateOf(repo.token) }
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("内网增量回传", style = MaterialTheme.typography.titleMedium)
        Text("默认关闭，仅发送已脱敏的三小时记录；地址必须使用 HTTPS。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.material3.Switch(checked = enabled, onCheckedChange = { enabled = it; repo.enabled = it })
        OutlinedTextField(endpoint, { endpoint = it; repo.endpoint = it }, Modifier.fillMaxWidth(), label = { Text("HTTPS 地址") }, singleLine = true)
        OutlinedTextField(token, { token = it; repo.token = it }, Modifier.fillMaxWidth(), label = { Text("配对密钥") }, singleLine = true)
    }}
}

@Composable
private fun EmojiMappingSection() {
    val repo = ServiceLocator.emojiMappingRepository
    val scope = rememberCoroutineScope()
    var mappings by remember { mutableStateOf(repo.all()) }
    var pinyin by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("") }
    Card(modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("自定义 Emoji 拼音", style = MaterialTheme.typography.titleMedium)
        Text("输入拼音即可把对应 emoji 放在候选首位。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(pinyin, { pinyin = it }, Modifier.weight(1f), placeholder = { Text("mao") }, singleLine = true)
            OutlinedTextField(emoji, { emoji = it }, Modifier.weight(1f).padding(start = 8.dp), placeholder = { Text("🐭") }, singleLine = true)
            Button(onClick = { if (pinyin.isNotBlank() && emoji.isNotBlank()) { repo.put(pinyin, emoji); mappings = repo.all(); pinyin = ""; emoji = "" } }, Modifier.padding(start = 8.dp)) { Text("添加") }
        }
        mappings.forEach { (key, value) -> Text("$key  $value") }
    }}
}

@Composable
private fun ImeStatusSection(
    imeEnabled: Boolean,
    onOpenImeSettings: () -> Unit,
    onShowPicker: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    text = if (imeEnabled) "状态：已启用" else "状态：尚未启用",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (imeEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(8.dp))
                Text("先在系统设置中启用 Pt键盘，再从输入法切换器中选择它。")
            }
        }
        Button(onClick = onOpenImeSettings, modifier = Modifier.fillMaxWidth()) { Text("1. 启用 Pt键盘") }
        OutlinedButton(onClick = onShowPicker, modifier = Modifier.fillMaxWidth(), enabled = imeEnabled) {
            Text("2. 切换到 Pt键盘")
        }
    }
}

/** PLAN M2 custom phrases, kept independent from Activity navigation and appearance. */
@Composable
private fun CustomPhraseSection() {
    val repository = ServiceLocator.phraseRepository
    val scope = rememberCoroutineScope()
    var phrases by remember { mutableStateOf<List<CustomPhraseRow>>(emptyList()) }
    var draft by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { phrases = repository.getAll() }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text("自定义短语", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text("键盘 emoji 面板的「短语」标签页会显示这里添加的内容。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("例如：稍后联系你") },
                    singleLine = true,
                )
                Button(
                    onClick = {
                        val content = draft
                        if (content.isBlank()) return@Button
                        draft = ""
                        scope.launch {
                            repository.add(content)
                            phrases = repository.getAll()
                        }
                    },
                    modifier = Modifier.padding(start = 8.dp),
                ) { Text("添加") }
            }
            if (phrases.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                LazyColumn(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                    items(phrases, key = { it.id }) { row ->
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(row.content, modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                scope.launch {
                                    repository.delete(row)
                                    phrases = repository.getAll()
                                }
                            }) { Text("✕") }
                        }
                    }
                }
            }
        }
    }
}
