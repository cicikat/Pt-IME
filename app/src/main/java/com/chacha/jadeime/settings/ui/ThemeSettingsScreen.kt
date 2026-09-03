package com.chacha.jadeime.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chacha.jadeime.ServiceLocator
import com.chacha.jadeime.theme.JadeTheme

/** Keyboard skin selection/import; intentionally separate from regular settings. */
@Composable
internal fun ThemeSettingsScreen(
    darkMode: Boolean,
    importTick: Int,
    onToggleDarkMode: () -> Unit,
    onBack: () -> Unit,
    onImportTheme: () -> Unit,
) {
    val repository = ServiceLocator.themeRepository
    var themes by remember { mutableStateOf(repository.listThemes()) }
    val selectedId by repository.selectedId.collectAsState()
    LaunchedEffect(importTick) { themes = repository.listThemes() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            SettingsPageHeader(
                title = "主题与皮肤",
                description = "键盘皮肤立即生效；此页面的日夜外观单独保存。",
                darkMode = darkMode,
                onToggleDarkMode = onToggleDarkMode,
                onBack = onBack,
            )
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("键盘皮肤", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text("选中立即同步到正在打开的键盘。「跟随系统」只影响键盘皮肤选择。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(14.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        item {
                            ThemeSwatch(
                                name = "跟随系统",
                                theme = null,
                                selected = selectedId == null,
                                onClick = { repository.setSelected(null) },
                            )
                        }
                        items(themes, key = { it.id }) { theme: JadeTheme ->
                            ThemeSwatch(
                                name = theme.name,
                                theme = theme,
                                selected = selectedId == theme.id,
                                onClick = { repository.setSelected(theme.id) },
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = onImportTheme, modifier = Modifier.fillMaxWidth()) {
                        Text("导入主题 JSON（docs/theming.md 有格式说明）")
                    }
                }
            }
        }
    }
}
