package com.chacha.jadeime.theme

import android.content.Context
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS_NAME = "theme_prefs"
private const val KEY_SELECTED_ID = "selected_theme_id"
private const val THEMES_DIR = "themes"

/**
 * Theme selection + the `*.json` files under `filesDir/themes/` (PLAN M5
 * "用户主题导入" / "设置页主题列表 + 预览 + 热切换"). [selectedId] is a [StateFlow] so
 * a theme change made in Settings reaches an already-open IME window immediately
 * instead of waiting for the next `onCreateInputView` -- that's the "热切换" part.
 */
class ThemeRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val themesDir = File(appContext.filesDir, THEMES_DIR)

    private val _selectedId = MutableStateFlow(prefs.getString(KEY_SELECTED_ID, null))
    /** Null means "follow system light/dark", matching the pre-M5 default behavior. */
    val selectedId: StateFlow<String?> = _selectedId.asStateFlow()

    fun setSelected(id: String?) {
        prefs.edit().putString(KEY_SELECTED_ID, id).apply()
        _selectedId.value = id
    }

    /** Built-in themes plus every parseable `*.json` file in `filesDir/themes/`. */
    fun listThemes(): List<JadeTheme> {
        val userThemes = themesDir.listFiles { file -> file.extension == "json" }
            ?.sortedBy { it.name }
            ?.mapNotNull { file ->
                parseSnabThemeJson(id = "user_${file.nameWithoutExtension}", raw = file.readText())
            }
            .orEmpty()
        return BuiltInThemes.all + userThemes
    }

    fun resolveActive(isSystemDark: Boolean): JadeTheme {
        val id = _selectedId.value ?: return if (isSystemDark) BuiltInThemes.dark else BuiltInThemes.light
        return listThemes().find { it.id == id } ?: BuiltInThemes.light
    }

    /** Copies a mod author's JSON file into `filesDir/themes/` under [fileName] (no
     * path segments allowed) so it shows up in [listThemes] from then on. */
    fun importThemeFile(fileName: String, contents: String): Boolean {
        val safeName = File(fileName).name.removeSuffix(".json") + ".json"
        if (safeName == ".json") return false
        return try {
            if (!themesDir.exists()) themesDir.mkdirs()
            File(themesDir, safeName).writeText(contents)
            true
        } catch (error: Exception) {
            false
        }
    }
}
