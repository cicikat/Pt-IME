package com.chacha.jadeime.settings.ui
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton

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
import com.chacha.jadeime.settings.SettingsPage

@Composable
internal fun GeneralSettingsScreen(
    imeEnabled: Boolean,
    onNavigate: (SettingsPage) -> Unit,
    onOpenImeSettings: () -> Unit,
    onShowPicker: () -> Unit,
    onOpenAudioSettings: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (imeEnabled) "键盘已就绪" else "开始使用你的键盘", style = MaterialTheme.typography.titleLarge)
                    Text("中文拼音 · 离线语音 · 自定义外观", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = if (imeEnabled) onShowPicker else onOpenImeSettings, modifier = Modifier.fillMaxWidth()) { Text(if (imeEnabled) "切换到 Pt JadeBoard" else "启用键盘") }
                }
            }
        }
        item { SettingsLink("主题与皮肤", "背景、按键与字体") { onNavigate(SettingsPage.Theme) } }
        item { SettingsLink("常用短语", "保存经常输入的句子") { onNavigate(SettingsPage.Phrases) } }
        item { SettingsLink("Emoji 快捷词", "用拼音快速输入表情") { onNavigate(SettingsPage.Emoji) } }
        item { SettingsLink("数据回传", "保存配置与测试连接") { onNavigate(SettingsPage.Sync) } }
        item { SettingsLink("最近输入", "查看最近三小时的草稿") { onNavigate(SettingsPage.Recent) } }
        item { SettingsLink("语音权限", "长按空格，离线听写") { onOpenAudioSettings() } }
        item { TextButton(onClick = onOpenImeSettings, modifier = Modifier.fillMaxWidth()) { Text("系统输入法设置") } }
    }
}

@Composable
internal fun EmojiMappingSection() {
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
internal fun CustomPhraseSection() {
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
