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
    private val packageRepository = ThemePackageRepository(appContext)

    private val _selectedId = MutableStateFlow(prefs.getString(KEY_SELECTED_ID, null))
    /** Null means "follow system light/dark", matching the pre-M5 default behavior. */
    val selectedId: StateFlow<String?> = _selectedId.asStateFlow()

    fun setSelected(id: String?) {
        prefs.edit().putString(KEY_SELECTED_ID, id).apply()
        _selectedId.value = id
    }

    /** Built-in themes plus every parseable `*.json` file in `filesDir/themes/`. */
    fun listThemes(): List<JadeTheme> {
        val packaged = themesDir.listFiles { file -> file.isDirectory && !file.name.startsWith(".") }
            ?.sortedBy { it.name }
            ?.mapNotNull { dir ->
                val raw = File(dir, "theme.json").takeIf(File::isFile)?.readText() ?: return@mapNotNull null
                val theme = parseSnabThemeJson("user_${dir.name}", raw) ?: return@mapNotNull null
                theme.copy(
                    bgImage = theme.bgImage?.let { resolveThemeResource(dir, it) },
                    fontPath = theme.fontPath?.let { resolveThemeResource(dir, it) },
                )
            }
            .orEmpty()
        val userThemes = themesDir.listFiles { file -> file.extension == "json" }
            ?.sortedBy { it.name }
            ?.mapNotNull { file ->
                parseSnabThemeJson(id = "user_${file.nameWithoutExtension}", raw = file.readText())
            }
            .orEmpty()
        return BuiltInThemes.all + packaged + userThemes
    }

    private fun resolveThemeResource(themeDir: File, path: String): String? = runCatching {
        val file = File(path).let { if (it.isAbsolute) it else File(themeDir, path) }.canonicalFile
        if (file.path.startsWith(themeDir.canonicalFile.path + File.separator) && file.isFile) file.path else null
    }.getOrNull()

    fun resolveActive(isSystemDark: Boolean): JadeTheme {
        val id = _selectedId.value ?: return if (isSystemDark) BuiltInThemes.dark else BuiltInThemes.light
        return listThemes().find { it.id == id } ?: BuiltInThemes.light
    }

    /** Copies a mod author's JSON file into `filesDir/themes/` under [fileName] (no
     * path segments allowed) so it shows up in [listThemes] from then on. */
    fun importThemeFile(fileName: String, contents: String): Boolean {
        val safeName = File(fileName).name.removeSuffix(".json") + ".json"
        if (safeName == ".json") return false
        val parsed = runCatching { kotlinx.serialization.json.Json.decodeFromString(SnabThemeJson.serializer(), contents) }.getOrNull() ?: return false
        if (parsed.formatVersion != 1 || parsed.minAppVersion > 1) return false
        return try {
            if (!themesDir.exists()) themesDir.mkdirs()
            File(themesDir, safeName).writeText(contents)
            true
        } catch (error: Exception) {
            false
        }
    }

    /** Installs a validated pt-theme.zip and returns its selectable id. */
    fun importThemePackage(bytes: ByteArray, fileName: String): String? =
        packageRepository.importZip(bytes, fileName).getOrNull()

    fun exportThemePackage(id: String): ByteArray? = packageRepository.export(id)

    fun deleteTheme(id: String): Boolean {
        if (!id.startsWith("user_")) return false
        val removed = File(themesDir, id.removePrefix("user_")).deleteRecursively()
        if (removed && _selectedId.value == id) setSelected(null)
        return removed
    }
}
