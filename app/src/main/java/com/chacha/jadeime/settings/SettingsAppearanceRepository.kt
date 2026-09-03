package com.chacha.jadeime.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Persistent day/night choice for the two ordinary settings pages. */
class SettingsAppearanceRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _darkMode = MutableStateFlow(prefs.getBoolean(KEY_DARK_MODE, false))
    val darkMode: StateFlow<Boolean> = _darkMode.asStateFlow()

    fun toggle() = setDarkMode(!_darkMode.value)

    fun setDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply()
        _darkMode.value = enabled
    }

    private companion object {
        const val PREFS_NAME = "settings_appearance"
        const val KEY_DARK_MODE = "dark_mode"
    }
}
