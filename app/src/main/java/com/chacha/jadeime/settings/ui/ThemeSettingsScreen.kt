package com.chacha.jadeime.settings.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chacha.jadeime.ServiceLocator
import com.chacha.jadeime.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ThemeSettingsScreen(
    darkMode: Boolean,
    importTick: Int,
    onToggleDarkMode: () -> Unit,
    onBack: () -> Unit,
    onImportTheme: () -> Unit,
) {
    val repo = ServiceLocator.themeRepository
    val scope = rememberCoroutineScope()
    val selected by repo.selectedId.collectAsState()
    val appearance by repo.appearance.collectAsState()
    var themes by remember { mutableStateOf(BuiltInThemes.all) }
    var fonts by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var pendingBackground by rememberSaveable { mutableStateOf<String?>(null) }
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val baseTheme = themes.find { it.id == selected } ?: if (systemDark) BuiltInThemes.dark else BuiltInThemes.light
    val theme = repo.customize(baseTheme)
    LaunchedEffect(importTick) {
        themes = withContext(Dispatchers.IO) { repo.listThemes() }
        fonts = withContext(Dispatchers.IO) { repo.fonts() }
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                try { pendingBackground = withContext(Dispatchers.IO) { repo.importAsset(uri, font = false) } }
                catch (_: Exception) { message = "图片导入失败，请选择 32 MB 内的图片、GIF 或 WebP" }
                finally { busy = false }
            }
        }
    }
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                try {
                    val path = withContext(Dispatchers.IO) { repo.importAsset(uri, font = true) }
                    fonts = withContext(Dispatchers.IO) { repo.fonts() }
                    repo.saveAppearance(repo.appearance.value.copy(font = path))
                    message = "字体已导入并应用"
                } catch (_: Exception) { message = "字体导入失败，请选择有效的 TTF 或 OTF 文件（32 MB 内）" }
                finally { busy = false }
            }
        }
    }
    pendingBackground?.let { path ->
        BackgroundCropDialog(path, if (path == appearance.background) appearance.crop else BackgroundCrop(), theme,
            onCancel = { pendingBackground = null },
            onSave = { crop -> repo.saveAppearance(repo.appearance.value.copy(background = path, crop = crop)); pendingBackground = null; message = "背景已保存" })
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                KeyboardStylePreview(theme)
                Text("预览 · 修改自动保存", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            }
        }
        item {
            AppearanceCard("配色") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { ThemeSwatch("随系统", null, selected == null) { repo.setSelected(null) } }
                    items(themes, key = { it.id }) { item -> ThemeSwatch(item.name, item, selected == item.id) { repo.setSelected(item.id) } }
                }
            }
        }
        item {
            AppearanceCard("背景") {
                Text("图片 / GIF / 动态 WebP", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { imagePicker.launch("image/*") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("选择背景") }
                if (appearance.background != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { pendingBackground = appearance.background }) { Text("调整裁剪") }
                        TextButton(onClick = { repo.saveAppearance(appearance.copy(background = null, crop = BackgroundCrop())) }) { Text("恢复主题背景") }
                    }
                    AppearanceSlider("背景压暗", appearance.dim) { repo.saveAppearance(repo.appearance.value.copy(dim = it)) }
                }
            }
        }
        item {
            AppearanceCard("按键") {
                AppearanceSlider("按键不透明度", appearance.keyOpacity) { repo.saveAppearance(repo.appearance.value.copy(keyOpacity = it)) }
            }
        }
        item {
            AppearanceCard("字体") {
                FontChoice("跟随主题", appearance.font == null) { repo.saveAppearance(appearance.copy(font = null)) }
                FontChoice("系统字体", appearance.font == "") { repo.saveAppearance(appearance.copy(font = "")) }
                fonts.forEach { (path, name) -> FontChoice(name, appearance.font == path) { repo.saveAppearance(appearance.copy(font = path)) } }
                OutlinedButton(onClick = { fontPicker.launch("*/*") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("导入字体 · TTF / OTF") }
            }
        }
        item {
            AppearanceCard("主题包") {
                OutlinedButton(onClick = onImportTheme, modifier = Modifier.fillMaxWidth()) { Text("导入主题 · ZIP / JSON") }
                themes.filter { it.id.startsWith("user_") }.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.name, Modifier.weight(1f))
                        TextButton(onClick = { scope.launch {
                            withContext(Dispatchers.IO) { repo.deleteTheme(item.id) }
                            themes = withContext(Dispatchers.IO) { repo.listThemes() }
                        } }) { Text("删除") }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppearanceCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun AppearanceSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("${(value * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
        }
        Slider(value, onChange)
    }
}

@Composable
private fun FontChoice(name: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick)
        Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
    }
}

@Composable
internal fun KeyboardStylePreview(theme: JadeTheme, modifier: Modifier = Modifier) {
    val font = rememberKeyboardFont(theme.fontPath)
    val aspect = LocalConfiguration.current.screenWidthDp / (268f * theme.keyboardHeightScale)
    Box(modifier.fillMaxWidth().aspectRatio(aspect).clip(RoundedCornerShape(16.dp)).background(theme.background)) {
        theme.bgImage?.let { KeyboardBackground(it, theme.backgroundCrop, theme.bgDim, Modifier.fillMaxSize(), theme.bgBlur) }
        Column(Modifier.fillMaxSize().padding(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth().weight(.8f), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
                Text("你好", color = theme.text, fontFamily = font, fontSize = 16.sp)
                Text("JadeBoard", color = theme.text, fontFamily = font, fontSize = 14.sp)
                Text("预览", color = theme.text, fontFamily = font, fontSize = 12.sp)
            }
            for (row in listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")) {
                Row(Modifier.fillMaxWidth().weight(1f).padding(horizontal = if (row.length == 10) 0.dp else 10.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (letter in row) Box(Modifier.weight(1f).fillMaxHeight().background(theme.keyDefault.copy(alpha = theme.keyDefault.alpha * theme.regionAlpha.keys), RoundedCornerShape(5.dp)), contentAlignment = Alignment.Center) {
                        Text(letter.toString(), color = theme.text, fontFamily = font, fontSize = 17.sp)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for ((label, weight) in listOf("?123" to 1f, "，" to .7f, "中文" to 3f, "。" to .7f, "↵" to 1f)) {
                    Box(Modifier.weight(weight).fillMaxHeight().background(theme.keyDefault.copy(alpha = theme.keyDefault.alpha * theme.regionAlpha.keys), RoundedCornerShape(5.dp)), contentAlignment = Alignment.Center) {
                        Text(label, color = theme.text, fontFamily = font, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
