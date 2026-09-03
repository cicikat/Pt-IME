package com.chacha.jadeime.emoji

import android.content.Context

class EmojiMappingRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("emoji_mappings", Context.MODE_PRIVATE)
    fun all(): Map<String, String> = prefs.all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()
    fun put(pinyin: String, emoji: String) { prefs.edit().putString(pinyin.trim().lowercase(), emoji).apply() }
    fun remove(pinyin: String) { prefs.edit().remove(pinyin).apply() }
}
