package com.chacha.jadeime.ime.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PinyinEditingTest {
    @Test
    fun `segmented display cursor maps around automatic separators`() {
        val display = "ni'hao"
        val raw = "nihao"

        assertEquals(0, rawCursorForDisplayOffset(display, raw, 0))
        assertEquals(2, rawCursorForDisplayOffset(display, raw, 3))
        assertEquals(5, rawCursorForDisplayOffset(display, raw, display.length))
        assertEquals(2, displayOffsetForRawCursor(display, raw, 2))
        assertEquals(display.length, displayOffsetForRawCursor(display, raw, raw.length))
    }

    @Test
    fun `manual separator remains part of the editable raw pinyin`() {
        val display = "n'hao"
        val raw = "n'hao"

        assertEquals(2, rawCursorForDisplayOffset(display, raw, 2))
        assertEquals(2, displayOffsetForRawCursor(display, raw, 2))
    }
}
