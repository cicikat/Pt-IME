package com.chacha.jadeime.ime.ui

internal enum class KeyAction {
    Text,
    Shift,
    Backspace,
    Space,
    Enter,
    /** Opens the horizontal symbols page. */
    Symbols,
    /** Opens the calculator-style nine-key numeric page. */
    Numeric,
    Letters,
    /** Compatibility-only action for old user layout JSON. */
    LangToggle,
    Separator,
    /** Inert filler occupying row width only -- no background, no tap target (PLAN
     * M1.7 "错位排列"): used to inset a-l/z-m off the row edges like a physical keyboard. */
    Spacer,
}

internal data class KeySpec(
    val label: String,
    val action: KeyAction,
    val value: String? = null,
    val weight: Float = 1f,
    val showsPopup: Boolean = action == KeyAction.Text,
    /** Small corner glyph (PLAN M1.6 "键帽角标"), long-press commits this instead of [label]. */
    val corner: String? = null,
)

internal object KeyboardLayouts {
    val letters: List<List<KeySpec>> = listOf(
        textRow("qwertyuiop", corners = "1234567890"),
        // a-l centered with a half-key gap on each side, not flush to the edges
        // (PLAN M1.7 "居中，左右各留约半键间隙"). 0.5 + 9x1 + 0.5 = 10, matching
        // row 1's total weight so letters render the same physical width.
        listOf(spacer(0.5f)) +
            textRow("asdfghjkl", corners = "@#\$%&-+()") +
            listOf(spacer(0.5f)),
        // Same stagger idea; the `'` separator moves off the left (next to shift)
        // to the right, between m and backspace (PLAN M1.7).
        listOf(KeySpec("⇧", KeyAction.Shift, weight = 1.35f)) +
            // Chinese quotes are a pair: the corner shows the opening mark,
            // while a long press commits its matching closing mark.
            textRow("zxcvbnm", corners = ":;“‘!?/") +
            listOf(
                KeySpec("'", KeyAction.Separator, weight = 0.85f, showsPopup = true),
                KeySpec("⌫", KeyAction.Backspace, weight = 1.35f),
            ),
        listOf(
            KeySpec("?123", KeyAction.Numeric, weight = 1.35f),
            KeySpec(",", KeyAction.Text, value = ","),
            KeySpec("中文", KeyAction.Space, weight = 4.5f, showsPopup = false),
            KeySpec(".", KeyAction.Text, value = "."),
            KeySpec("↵", KeyAction.Enter, weight = 1.35f, showsPopup = false),
        ),
    )

    /** A familiar calculator-style numeric pad: 1–9 from top-left to bottom-right. */
    val numeric: List<List<KeySpec>> = listOf(
        textRow("123"),
        textRow("456"),
        textRow("789"),
        listOf(
            KeySpec("ABC", KeyAction.Letters, weight = 1.35f),
            KeySpec(",", KeyAction.Text, value = ","),
            KeySpec("0", KeyAction.Text, value = "0", weight = 1.5f),
            KeySpec(".", KeyAction.Text, value = "."),
            KeySpec("⌫", KeyAction.Backspace, weight = 1.35f, showsPopup = false),
        ),
    )

    /**
     * Common symbols stay separate from the nine-key numeric pad. The former
     * horizontal layout's number row is deliberately omitted: numbers already
     * have their own dedicated page, so this is a compact symbols-only page.
     */
    val symbols: List<List<KeySpec>> = listOf(
        textRow(listOf("！", "？", "，", "。", "、", "；", "：", "“", "”", "‘", "’")),
        textRow(listOf("（", "）", "【", "】", "《", "》", "「", "」", "…", "—", "·")),
        textRow(listOf("@", "#", "￥", "&", "*", "+", "=", "/", "%", "^", "~")),
        listOf(
            KeySpec("ABC", KeyAction.Letters, weight = 1.35f),
            KeySpec(",", KeyAction.Text, value = ","),
            KeySpec(" ", KeyAction.Space, weight = 4.5f, showsPopup = false),
            KeySpec(".", KeyAction.Text, value = "."),
            KeySpec("⌫", KeyAction.Backspace, weight = 1.35f, showsPopup = false),
        ),
    )

    private fun spacer(weight: Float): KeySpec =
        KeySpec(label = "", action = KeyAction.Spacer, weight = weight, showsPopup = false)

    private fun textRow(characters: String, corners: String? = null): List<KeySpec> =
        textRow(characters.map(Char::toString), corners?.map(Char::toString))

    private fun textRow(values: List<String>, corners: List<String>? = null): List<KeySpec> =
        values.mapIndexed { index, value ->
            KeySpec(label = value, action = KeyAction.Text, value = value, corner = corners?.getOrNull(index))
        }
}
