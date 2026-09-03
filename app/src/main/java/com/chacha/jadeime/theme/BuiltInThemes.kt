package com.chacha.jadeime.theme

import androidx.compose.ui.graphics.Color

/** The 4 built-in themes DESIGN.md 3.5 asks for ("内置 4 套（亮/暗/护眼绿/粉）"). */
object BuiltInThemes {
    val light = JadeTheme(
        id = "light",
        name = "亮色",
        isDark = false,
        background = Color(0xFFE7ECE9),
        keyDefault = Color(0xFFF9FBFA),
        keyPressed = Color(0xFFB7C8C0),
        keySpecial = Color(0xFFD1DBD6),
        keyActive = Color(0xFF72A98F),
        shiftOnce = Color(0xFFC7CFCB),
        text = Color(0xFF15251E),
        popupBackground = Color.White,
    )

    val dark = JadeTheme(
        id = "dark",
        name = "暗色",
        isDark = true,
        background = Color(0xFF161917),
        keyDefault = Color(0xFF262B28),
        keyPressed = Color(0xFF3B443E),
        keySpecial = Color(0xFF2E3330),
        keyActive = Color(0xFF4C7A63),
        shiftOnce = Color(0xFF454C48),
        text = Color(0xFFD8E0DB),
        popupBackground = Color(0xFF262B28),
    )

    val eyeCareGreen = JadeTheme(
        id = "eye_care_green",
        name = "护眼绿",
        isDark = false,
        background = Color(0xFFC7E0C8),
        keyDefault = Color(0xFFE3F2E4),
        keyPressed = Color(0xFF9AC49E),
        keySpecial = Color(0xFFB3D4B6),
        keyActive = Color(0xFF4F8A5B),
        shiftOnce = Color(0xFFA0C6A3),
        text = Color(0xFF1E3320),
        popupBackground = Color(0xFFF2FAF2),
    )

    val pink = JadeTheme(
        id = "pink",
        name = "粉色",
        isDark = false,
        background = Color(0xFFFBE3EC),
        keyDefault = Color(0xFFFFF5F8),
        keyPressed = Color(0xFFF3B8CE),
        keySpecial = Color(0xFFF8CBDD),
        keyActive = Color(0xFFE87DA6),
        shiftOnce = Color(0xFFF0C2D4),
        text = Color(0xFF4A1E2E),
        popupBackground = Color.White,
    )

    val all: List<JadeTheme> = listOf(light, dark, eyeCareGreen, pink)

    fun byId(id: String?): JadeTheme? = all.find { it.id == id }
}
