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
    private val _appearance = MutableStateFlow(KeyboardAppearance(
        background = prefs.getString("custom_background", null),
        crop = BackgroundCrop(prefs.getFloat("crop_x", .5f), prefs.getFloat("crop_y", .5f), prefs.getFloat("crop_zoom", 1f)).bounded(),
        keyOpacity = prefs.getFloat("key_opacity", 1f).coerceIn(0f, 1f),
        dim = prefs.getFloat("custom_dim", .15f).coerceIn(0f, 1f),
        font = prefs.getString("custom_font", null),
    ))
    val appearance = _appearance.asStateFlow()
    private val _revision = MutableStateFlow(0)
    val revision = _revision.asStateFlow()

    fun saveAppearance(value: KeyboardAppearance) {
        val safe = value.copy(crop = value.crop.bounded(), keyOpacity = value.keyOpacity.coerceIn(0f, 1f), dim = value.dim.coerceIn(0f, 1f))
        prefs.edit().putString("custom_background", safe.background).putFloat("crop_x", safe.crop.x)
            .putFloat("crop_y", safe.crop.y).putFloat("crop_zoom", safe.crop.zoom)
            .putFloat("key_opacity", safe.keyOpacity).putFloat("custom_dim", safe.dim)
            .putString("custom_font", safe.font).apply()
        _appearance.value = safe
    }

    fun customize(theme: JadeTheme, appearance: KeyboardAppearance = _appearance.value): JadeTheme = theme.copy(
        bgImage = appearance.background ?: theme.bgImage,
        backgroundCrop = if (appearance.background != null) appearance.crop else theme.backgroundCrop,
        bgDim = if (appearance.background != null) appearance.dim else theme.bgDim,
        fontPath = appearance.font ?: theme.fontPath,
        regionAlpha = theme.regionAlpha.copy(keys = theme.regionAlpha.keys * appearance.keyOpacity),
    )

    /** Caller performs imports off the main thread. Unique filenames keep live decoders stable. */
    fun importAsset(uri: android.net.Uri, font: Boolean): String {
        val directory = File(appContext.filesDir, if (font) "fonts" else "backgrounds").apply { mkdirs() }
        val extension = if (font) "ttf" else "image"
        val file = File(directory, "${java.util.UUID.randomUUID()}.$extension")
        try {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= 32L * 1024 * 1024) { "文件不能超过 32 MB" }
                        output.write(buffer, 0, read)
                    }
                }
            } ?: error("无法读取文件")
            if (font) {
                android.graphics.Typeface.createFromFile(file)
                val name = appContext.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                } ?: "导入字体"
                prefs.edit().putString("font_name_${file.name}", name).apply()
            } else {
                val drawable = decodeBackground(file.path)
                require(drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) { "图片无法解码" }
                (drawable as? android.graphics.drawable.AnimatedImageDrawable)?.stop()
            }
            return file.path
        } catch (error: Exception) { file.delete(); throw error }
    }

    fun fonts(): List<Pair<String, String>> {
        val bundled = if (appContext.assets.list("fonts")?.contains("zpix.ttf") == true) listOf("asset:fonts/zpix.ttf" to "Zpix 最像素") else emptyList()
        return bundled + File(appContext.filesDir, "fonts").listFiles().orEmpty().filter { it.isFile }.map {
            it.path to (prefs.getString("font_name_${it.name}", null) ?: "导入字体")
        }
    }

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
        val id = _selectedId.value
        val base = if (id == null) { if (isSystemDark) BuiltInThemes.dark else BuiltInThemes.light }
            else listThemes().find { it.id == id } ?: BuiltInThemes.light
        return customize(base)
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
            _revision.value++
            true
        } catch (error: Exception) {
            false
        }
    }

    /** Installs a validated pt-theme.zip and returns its selectable id. */
    fun importThemePackage(bytes: ByteArray, fileName: String): String? =
        packageRepository.importZip(bytes, fileName).getOrNull()?.also { _revision.value++ }

    fun exportThemePackage(id: String): ByteArray? = packageRepository.export(id)

    fun deleteTheme(id: String): Boolean {
        if (!id.startsWith("user_")) return false
        val name = id.removePrefix("user_")
        if (File(name).name != name || name == "." || name == "..") return false
        val folder = File(themesDir, name)
        val removed = if (folder.isDirectory) folder.deleteRecursively() else File(themesDir, "$name.json").delete()
        if (removed && _selectedId.value == id) setSelected(null)
        if (removed) _revision.value++
        return removed
    }
}
