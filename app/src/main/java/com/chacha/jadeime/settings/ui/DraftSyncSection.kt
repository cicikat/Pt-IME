package com.chacha.jadeime.settings.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.chacha.jadeime.ServiceLocator
import com.chacha.jadeime.data.SyncEndpoint
import kotlinx.coroutines.launch

@Composable
internal fun DraftSyncSection() {
    val repo = ServiceLocator.draftSyncRepository
    val scope = rememberCoroutineScope()
    var endpoint by rememberSaveable { mutableStateOf(repo.endpoint) }
    // A secret is intentionally not written to saved instance state.
    var token by remember { mutableStateOf(repo.token) }
    var interval by rememberSaveable { mutableStateOf(repo.intervalMinutes.toString()) }
    var remote by rememberSaveable { mutableStateOf(repo.allowRemote) }
    var enabled by remember { mutableStateOf(repo.enabled) }
    var recording by remember { mutableStateOf(ServiceLocator.draftPrivacy.enabled) }
    var busy by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(true) }
    var result by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    val error = SyncEndpoint.error(endpoint, token) ?: if (interval.toIntOrNull() !in 1..1440) "间隔请输入 1–1440 分钟" else null
    fun changed() { saved = false; result = null }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false }, title = { Text("开启自动回传？") },
        text = { Text("将最近三小时的输入草稿发送至已保存的地址。数字替换为 *，其他文字、来源 App 和时间会保留。") },
        confirmButton = { TextButton(onClick = { repo.enabled = true; enabled = true; confirm = false }) { Text("开启") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("取消") } },
    )
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingToggle("保留三小时草稿", recording) {
                recording = it; ServiceLocator.draftPrivacy.enabled = it
                if (!it) { repo.enabled = false; enabled = false; ServiceLocator.breakDraftSession() }
            }
            Text("密码、邮箱等敏感输入框不记录。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            Text("接收端", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(endpoint, { endpoint = it; changed() }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text("接口地址") }, placeholder = { Text("http://192.168.1.2:8000/v1/ime/drafts") }, singleLine = true)
            OutlinedTextField(token, { token = it; changed() }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text("配对密钥 / Token") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
            OutlinedTextField(interval, { interval = it.filter(Char::isDigit); changed() }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text("回传间隔（分钟）") }, singleLine = true)
            SettingToggle("允许公网地址（内网穿透）", remote, !busy) { remote = it; changed() }
            if (endpoint.trim().startsWith("http://", true)) Text("HTTP 不加密，地址链路上的人可能读取密钥和草稿。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            if (!saved && error != null) Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            Text(if (saved) "配置已保存" else "有未保存的修改", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Button(onClick = {
                busy = true
                scope.launch {
                    try {
                        repo.saveConfiguration(endpoint, token, interval.toInt(), remote)
                        endpoint = repo.endpoint; enabled = false; saved = true; result = "已保存。可先测试连接，再开启自动回传。"
                    } catch (_: Exception) { result = "保存失败，请重试" }
                    finally { busy = false }
                }
            }, enabled = !busy && error == null && !saved, modifier = Modifier.fillMaxWidth()) { Text("保存配置") }
            OutlinedButton(onClick = {
                busy = true; result = null
                scope.launch { try { result = repo.testUpload() } finally { busy = false } }
            }, enabled = !busy && saved && error == null, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "处理中…" else "测试连接") }
            if (!saved) Text("保存后即可测试。", style = MaterialTheme.typography.bodySmall)
            result?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            HorizontalDivider()
            SettingToggle("自动回传", enabled, recording && saved && error == null && !busy) {
                if (it) confirm = true else { enabled = false; repo.enabled = false }
            }
            if (!recording) Text("开启草稿后可启用自动回传；测试连接不受影响。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
