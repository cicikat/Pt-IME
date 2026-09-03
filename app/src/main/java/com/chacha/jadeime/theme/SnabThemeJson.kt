package com.chacha.jadeime.theme

import android.util.Log
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val TAG = "SnabThemeJson"

/**
 * On-disk JSON shape for user themes (DESIGN.md 3.5), extended per PLAN M5 with
 * `bgImage`/`bgBlur`/`bgDim` and per-region color overrides. Field names match the
 * DESIGN.md 3.5 example verbatim (`keyboardBg`, `keyBg`, ...) rather than
 * [JadeTheme]'s property names, since that JSON shape is the actual mod-author
 * contract -- see docs/theming.md.
 *
 * Per-region overrides (`toolbar`/`candidates`/`keys`/`panel`) let a mod recolor
 * just one area; any region left null falls back to the theme's base colors.
 * Only `keys`-region overrides are wired into rendering today (that's the block
 * the keyboard actually spends most of its pixels on) -- `toolbar`/`candidates`/
 * `panel` parse and round-trip but aren't yet consumed by the composables, an
 * honest gap left for the next M5 pass rather than dead/misleading fields.
 */
@Serializable
data class SnabThemeJson(
    val name: String,
    val isDark: Boolean = false,
    val keyboardBg: String,
    val keyBg: String,
    val keyBgPressed: String,
    val keyText: String,
    val keyTextSecondary: String? = null,
    val accent: String,
    val candidateText: String? = null,
    val keyCornerRadius: Int = 8,
    val keyGap: Int = 3,
    val fontScale: Float = 1.0f,
    val bgImage: String? = null,
    val bgBlur: Int = 0,
    val bgDim: Float = 0f,
    val keySound: String? = null,
    val keyboardHeightScale: Float = 1.0f,
    val toolbar: RegionOverride? = null,
    val candidates: RegionOverride? = null,
    val keys: RegionOverride? = null,
    val panel: RegionOverride? = null,
    val formatVersion: Int = 1,
    val minAppVersion: Int = 1,
    val font: String? = null,
    val keyPressAnimation: AnimationJson? = null,
    val idleAnimation: AnimationJson? = null,
    val transparency: TransparencyJson? = null,
)

@Serializable
data class RegionOverride(
    val bg: String? = null,
    val text: String? = null,
)
@Serializable data class AnimationJson(val enabled: Boolean = false, val durationMs: Int = 180, val maxObjects: Int = 0)
@Serializable data class TransparencyJson(val background: Float = 1f, val keys: Float = 1f, val candidates: Float = 1f, val toolbar: Float = 1f, val panel: Float = 1f)

private val json = Json { ignoreUnknownKeys = true }

/** Parses one theme file's contents; returns null (logged, not thrown) on bad JSON
 * so one broken mod file can't take the whole theme list down (CLAUDE.md-style
 * "degrade, don't crash the IME process"). */
fun parseSnabThemeJson(id: String, raw: String): JadeTheme? = try {
    json.decodeFromString(SnabThemeJson.serializer(), raw).toJadeTheme(id)
} catch (error: Exception) {
    Log.e(TAG, "failed to parse theme json for id=$id", error)
    null
}

private fun SnabThemeJson.toJadeTheme(id: String): JadeTheme = JadeTheme(
    id = id,
    name = name,
    isDark = isDark,
    background = keyboardBg.toColorOrDefault(if (isDark) Color(0xFF161917) else Color(0xFFE7ECE9)),
    keyDefault = keyBg.toColorOrDefault(if (isDark) Color(0xFF262B28) else Color(0xFFF9FBFA)),
    keyPressed = keyBgPressed.toColorOrDefault(if (isDark) Color(0xFF3B443E) else Color(0xFFB7C8C0)),
    keySpecial = (keys?.bg ?: keyBg).toColorOrDefault(if (isDark) Color(0xFF2E3330) else Color(0xFFD1DBD6)),
    keyActive = accent.toColorOrDefault(Color(0xFF72A98F)),
    shiftOnce = keyBgPressed.toColorOrDefault(Color(0xFFC7CFCB)).copy(alpha = 0.7f),
    text = (keys?.text ?: keyText).toColorOrDefault(if (isDark) Color(0xFFD8E0DB) else Color(0xFF15251E)),
    popupBackground = keyBg.toColorOrDefault(Color.White),
    keyCornerRadius = keyCornerRadius.coerceIn(0, 24).dpValue(),
    keyGap = keyGap.coerceIn(0, 16).dpValue(),
    keyboardHeightScale = keyboardHeightScale.coerceIn(0.85f, 1.25f),
    bgImage = bgImage,
    bgBlur = bgBlur.coerceIn(0, 25),
    bgDim = bgDim.coerceIn(0f, 1f),
    fontPath = font,
    fontScale = fontScale.coerceIn(0.75f, 1.5f),
    keyPressAnimation = keyPressAnimation.toAnimation(),
    idleAnimation = idleAnimation.toAnimation(),
    regionAlpha = transparency.toRegionAlpha(),
)
private fun AnimationJson?.toAnimation() = AnimationSpec(this?.enabled == true, (this?.durationMs ?: 180).coerceIn(16, 1000), (this?.maxObjects ?: 0).coerceIn(0, 24))
private fun TransparencyJson?.toRegionAlpha() = RegionAlpha(
    this?.background?.coerceIn(0f, 1f) ?: 1f,
    this?.keys?.coerceIn(0f, 1f) ?: 1f,
    this?.candidates?.coerceIn(0f, 1f) ?: 1f,
    this?.toolbar?.coerceIn(0f, 1f) ?: 1f,
    this?.panel?.coerceIn(0f, 1f) ?: 1f,
)

private fun Int.dpValue() = androidx.compose.ui.unit.Dp(this.toFloat())

private fun String?.toColorOrDefault(default: Color): Color {
    if (this.isNullOrBlank()) return default
    return try {
        Color(android.graphics.Color.parseColor(this))
    } catch (error: IllegalArgumentException) {
        Log.e(TAG, "invalid color literal '$this'", error)
        default
    }
}
