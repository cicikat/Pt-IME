package com.chacha.jadeime.settings.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chacha.jadeime.settings.SettingsPage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoot(
    page: SettingsPage,
    imeEnabled: Boolean,
    darkMode: Boolean,
    themeImportTick: Int,
    onToggleDarkMode: () -> Unit,
    onNavigate: (SettingsPage) -> Unit,
    onBack: () -> Unit,
    onOpenImeSettings: () -> Unit,
    onShowPicker: () -> Unit,
    onOpenAudioSettings: () -> Unit,
    onImportTheme: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val colors = if (darkMode) darkColorScheme(
        primary = Color(0xFFA6CDBB), background = Color(0xFF141A18), surface = Color(0xFF1C2420),
        surfaceVariant = Color(0xFF27352E), secondaryContainer = Color(0xFF30483C),
    ) else lightColorScheme(
        primary = Color(0xFF376653), onPrimary = Color.White,
        background = Color(0xFFF4F6F2), surface = Color.White,
        surfaceVariant = Color(0xFFEDF2EB), secondaryContainer = Color(0xFFE0EBE2),
    )
    MaterialTheme(colorScheme = colors) {
        Scaffold(containerColor = colors.background, topBar = {
            TopAppBar(
                title = { Text(page.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { BackArrow() } },
                actions = { TextButton(onClick = onToggleDarkMode) { Text(if (darkMode) "浅色" else "深色") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background),
            )
        }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).imePadding()) {
                when (page) {
                    SettingsPage.General -> GeneralSettingsScreen(imeEnabled, onNavigate, onOpenImeSettings, onShowPicker, onOpenAudioSettings)
                    SettingsPage.Theme -> ThemeSettingsScreen(darkMode, themeImportTick, onToggleDarkMode, onBack, onImportTheme)
                    SettingsPage.Recent -> RecentDraftScreen(onBack)
                    else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        when (page) {
                            SettingsPage.Sync -> DraftSyncSection()
                            SettingsPage.Phrases -> CustomPhraseSection()
                            SettingsPage.Emoji -> EmojiMappingSection()
                            else -> Unit
                        }
                    }
                }
            }
        }
    }
}
