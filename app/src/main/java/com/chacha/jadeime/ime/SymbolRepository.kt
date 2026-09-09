package com.chacha.jadeime.ime

import android.content.Context

/** Small persistent MRU, independent of whether the symbol panel is composed. */
class SymbolRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("symbol_recents", Context.MODE_PRIVATE)
    fun recent(): List<String> = prefs.getString("recent", "").orEmpty()
        .split('\u0001').filter { it.isNotEmpty() }.take(40)
    fun record(value: String) {
        prefs.edit().putString("recent", (listOf(value) + recent().filterNot { it == value })
            .take(40).joinToString("\u0001")).apply()
    }
}
