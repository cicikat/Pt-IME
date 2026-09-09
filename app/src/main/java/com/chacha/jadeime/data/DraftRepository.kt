package com.chacha.jadeime.data

import android.content.Context

object DraftRedactor {
    fun redact(value: String): String = value.map { ch ->
        when (ch) {
            in '0'..'9', in '\uFF10'..'\uFF19' -> '*'
            else -> ch
        }
    }.joinToString("")
}

class DraftRepository(context: Context) {
    private val dao = UserDataDatabase.create(context.applicationContext).draftDao()
    suspend fun record(text: String, appPackage: String, source: String, now: Long = System.currentTimeMillis()) {
        if (text.isBlank()) return
        dao.deleteBefore(now - THREE_HOURS_MS)
        dao.insert(DraftEntryRow(createdAt = now, appPackage = appPackage, source = source, content = DraftRedactor.redact(text)))
    }
    suspend fun recent(now: Long = System.currentTimeMillis()) = dao.recent(now - THREE_HOURS_MS)
    companion object { const val THREE_HOURS_MS = 3 * 60 * 60 * 1000L }
}
