package com.chacha.jadeime.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Runtime token bundle for every color/shape value the keyboard UI draws (PLAN M5
 * "UI 组件化前置...严禁硬编码颜色尺寸，一律读 theme token"). This is the Snab theme
 * engine from DESIGN.md 3.5: built-in themes are hardcoded [JadeTheme] instances
 * (see [BuiltInThemes]), user themes are `*.json` files under `filesDir/themes/`
 * parsed by [SnabThemeJson] into the same shape -- neither the UI nor a mod author
 * needs to care which source a given theme came from.
 */
data class JadeTheme(
    val id: String,
    val name: String,
    val isDark: Boolean,
    val background: Color,
    val keyDefault: Color,
    val keyPressed: Color,
    val keySpecial: Color,
    val keyActive: Color,
    /** Light-gray shift-once highlight (PLAN M1.7 "单击=浅灰"), distinct from the
     * brighter [keyActive] used for the caps-lock state so the two read differently. */
    val shiftOnce: Color,
    val text: Color,
    val popupBackground: Color,
    val keyCornerRadius: Dp = 8.dp,
    val keyGap: Dp = 3.dp,
    /** Scales the 268dp default keyboard surface for one-handed and accessibility needs. */
    val keyboardHeightScale: Float = 1.0f,
    /** Absolute file path to a background image, or null for a flat [background] fill. */
    val bgImage: String? = null,
    /** 0-25, matches DESIGN.md 3.5 / PLAN M5's `bgBlur` field. */
    val bgBlur: Int = 0,
    /** 0f-1f black scrim over the background image, for text legibility. */
    val bgDim: Float = 0f,
)

val LocalJadeTheme = staticCompositionLocalOf { BuiltInThemes.light }
