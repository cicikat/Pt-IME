package com.chacha.jadeime.ime.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class KeyboardLayoutTest {
    @Test
    fun `numeric page remains a calculator-style nine-key pad`() {
        assertEquals(listOf("1", "2", "3"), KeyboardLayouts.numeric.first().map { it.label })
        assertEquals(listOf("7", "8", "9"), KeyboardLayouts.numeric[2].map { it.label })
    }

    @Test
    fun `symbols page has no duplicate number row`() {
        assertFalse(KeyboardLayouts.symbols.first().any { it.label in "0123456789" })
        assertEquals(KeyAction.Letters, KeyboardLayouts.symbols.last().first().action)
    }
}
