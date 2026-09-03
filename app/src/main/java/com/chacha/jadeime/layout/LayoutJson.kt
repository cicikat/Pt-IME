package com.chacha.jadeime.layout

import com.chacha.jadeime.ime.ui.KeyAction
import com.chacha.jadeime.ime.ui.KeySpec
import kotlinx.serialization.Serializable

/**
 * On-disk shape for a JSON-overridable keyboard layout (PLAN M5 "布局也 JSON 化：
 * 行列/键宽权重/特殊键位置可由布局文件覆写"). One-to-one with [KeySpec]/[KeyAction] --
 * a mod author edits rows of these, not Kotlin. `action` is the [KeyAction] enum
 * name (`"Text"`, `"Shift"`, `"Backspace"`, `"Space"`, `"Enter"`, `"Symbols"`, `"Numeric"`,
 * `"Letters"`, `"Separator"`, `"Spacer"`). `"LangToggle"` is accepted only for
 * backwards compatibility and renders as an inert spacer; an unrecognized value
 * falls back to `"Text"` rather than failing the whole layout.
 */
@Serializable
data class KeySpecJson(
    val label: String,
    val action: String,
    val value: String? = null,
    val weight: Float = 1f,
    val showsPopup: Boolean? = null,
    val corner: String? = null,
)

internal fun List<List<KeySpecJson>>.toKeySpecs(): List<List<KeySpec>> = map { row ->
    row.map { it.toKeySpec() }
}

private fun KeySpecJson.toKeySpec(): KeySpec {
    val resolvedAction = runCatching { KeyAction.valueOf(action) }.getOrDefault(KeyAction.Text)
    return KeySpec(
        label = label,
        action = resolvedAction,
        value = value,
        weight = weight,
        showsPopup = showsPopup ?: (resolvedAction == KeyAction.Text),
        corner = corner,
    )
}
