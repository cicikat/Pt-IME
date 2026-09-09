package com.chacha.jadeime.settings.ui

import androidx.activity.compose.BackHandler
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import com.chacha.jadeime.settings.SettingsPage

/** Routes the Activity's settings destinations and owns their shared day/night chrome. */
@Composable
fun SettingsRoot(
    page: SettingsPage,
    imeEnabled: Boolean,
    darkMode: Boolean,
    themeImportTick: Int,
    onToggleDarkMode: () -> Unit,
    onOpenTheme: () -> Unit,
    onBackToGeneral: () -> Unit,
    onOpenImeSettings: () -> Unit,
    onShowPicker: () -> Unit,
    onOpenAudioSettings: () -> Unit,
    onImportTheme: () -> Unit,
) {
    BackHandler(enabled = page == SettingsPage.Theme, onBack = onBackToGeneral)
    MaterialTheme(colorScheme = if (darkMode) darkColorScheme() else lightColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            when (page) {
                SettingsPage.General -> GeneralSettingsScreen(
                    imeEnabled = imeEnabled,
                    darkMode = darkMode,
                    onToggleDarkMode = onToggleDarkMode,
                    onOpenTheme = onOpenTheme,
                    onOpenImeSettings = onOpenImeSettings,
                    onShowPicker = onShowPicker,
                    onOpenAudioSettings = onOpenAudioSettings,
                )
                SettingsPage.Theme -> ThemeSettingsScreen(
                    darkMode = darkMode,
                    importTick = themeImportTick,
                    onToggleDarkMode = onToggleDarkMode,
                    onBack = onBackToGeneral,
                    onImportTheme = onImportTheme,
                )
            }
        }
    }
}
