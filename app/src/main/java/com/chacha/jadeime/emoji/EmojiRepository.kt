package com.chacha.jadeime.emoji

import android.content.Context

private const val PREFS_NAME = "emoji_prefs"
private const val KEY_RECENT = "recent"

// A control character, never part of an actual emoji glyph -- unlike "," or a
// space, safe to split on unconditionally (multi-codepoint emoji like flags or
// ZWJ sequences must stay intact, so a plain-character delimiter could collide).
private const val SEPARATOR = ""

/**
 * Most-recently-used emoji, persisted as a small delimited string in SharedPreferences.
 * A single emoji list capped at [EmojiCatalog.MAX_RECENT] entries doesn't need Room's
 * query machinery -- SharedPreferences is the lighter, synchronous-enough fit here
 * (panel opens are a rare, human-paced interaction, not a hot path).
 */
class EmojiRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getRecent(): List<String> {
        val raw = prefs.getString(KEY_RECENT, null) ?: return emptyList()
        return raw.split(SEPARATOR).filter { it.isNotEmpty() }
    }

    /** Moves [emoji] to the front of the MRU list, trimming to the cap. */
    fun recordUsage(emoji: String) {
        val updated = listOf(emoji) + getRecent().filterNot { it == emoji }
        prefs.edit()
            .putString(KEY_RECENT, updated.take(EmojiCatalog.MAX_RECENT).joinToString(SEPARATOR))
            .apply()
    }
}
