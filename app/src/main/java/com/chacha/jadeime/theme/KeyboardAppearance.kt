package com.chacha.jadeime.theme

data class KeyboardAppearance(
    val background: String? = null,
    val crop: BackgroundCrop = BackgroundCrop(),
    val keyOpacity: Float = 1f,
    val dim: Float = .15f,
    /** null uses the theme font, empty string explicitly uses the system font. */
    val font: String? = null,
)
