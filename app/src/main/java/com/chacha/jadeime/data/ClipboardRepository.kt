package com.chacha.jadeime.data

import android.content.Context

private const val MAX_UNPINNED = 50

/** Clipboard history CRUD (PLAN M2 "剪贴板：监听 + 历史面板：50条、固定、清空"). */
class ClipboardRepository(context: Context) {
    private val dao = UserDataDatabase.create(context.applicationContext).clipboardDao()

    suspend fun getAll(): List<ClipboardEntryRow> = dao.getAll()

    /** Records a fresh clipboard snapshot, deduping against the most recent identical
     * content and evicting the oldest unpinned entries past the 50 cap. */
    suspend fun record(content: String) {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return
        val existing = dao.findByContent(trimmed)
        if (existing != null) {
            // Re-copying something already in history just bumps it back to the top.
            dao.update(existing.copy(timestamp = System.currentTimeMillis()))
            return
        }
        dao.insert(ClipboardEntryRow(content = trimmed, timestamp = System.currentTimeMillis()))
        dao.trimUnpinnedBeyond(MAX_UNPINNED)
    }

    suspend fun togglePin(row: ClipboardEntryRow) {
        dao.update(row.copy(pinned = !row.pinned))
    }

    suspend fun delete(row: ClipboardEntryRow) = dao.delete(row)

    /** Clears history; pinned entries are kept, matching most clipboard managers'
     * "clear" semantics (pinning something is an explicit "don't touch this"). */
    suspend fun clearUnpinned() = dao.deleteUnpinned()
}
