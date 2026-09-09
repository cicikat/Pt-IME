package com.chacha.jadeime.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo

/**
 * Shared "is this field sensitive" check for the privacy red line in CLAUDE.md
 * ("`inputType` 为 password/邮箱等敏感变体时：不统计、不进剪贴板历史、不进用户词库").
 * Used today by the clipboard listener (PLAN M2 "敏感字段豁免"); M3's stats
 * collector will reuse it verbatim instead of re-deriving the mask ("为 M3 铺垫").
 */
internal fun EditorInfo?.isSensitiveField(): Boolean {
    val editor = this ?: return true
    if ((editor.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0) return true
    val type = editor.inputType
    val variation = type and InputType.TYPE_MASK_VARIATION
    return when (type and InputType.TYPE_MASK_CLASS) {
        InputType.TYPE_CLASS_TEXT -> variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
        InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        else -> false
    }
}

/** What the keyboard should auto-switch to for the currently focused field
 * (PLAN M2 "数字/URL/密码 inputType 自动切布局"). A nudge, not a lock -- the user
 * can still flip back to Chinese manually via the mode panel if they really want. */
internal enum class FieldConstraint {
    None,
    /** Password/email/URL fields: pinyin composing doesn't make sense here. */
    ForceEnglish,
    /** Number/phone/date-time fields: also jump straight to the digit page. */
    ForceEnglishNumeric,
}

internal fun EditorInfo?.deriveFieldConstraint(): FieldConstraint {
    val type = this?.inputType ?: return FieldConstraint.None
    if (isSensitiveField()) return FieldConstraint.ForceEnglish
    val cls = type and InputType.TYPE_MASK_CLASS
    val variation = type and InputType.TYPE_MASK_VARIATION
    return when {
        cls == InputType.TYPE_CLASS_NUMBER ||
            cls == InputType.TYPE_CLASS_PHONE ||
            cls == InputType.TYPE_CLASS_DATETIME -> FieldConstraint.ForceEnglishNumeric
        cls == InputType.TYPE_CLASS_TEXT && variation == InputType.TYPE_TEXT_VARIATION_URI ->
            FieldConstraint.ForceEnglish
        else -> FieldConstraint.None
    }
}
