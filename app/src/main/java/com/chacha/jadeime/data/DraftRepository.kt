package com.chacha.jadeime.data

import android.content.Context
import kotlinx.coroutines.sync.withLock

object DraftRedactor {
    fun redact(value: String): String = value.map { ch ->
        when (ch) {
            in '0'..'9', in '\uFF10'..'\uFF19' -> '*'
            else -> ch
        }
    }.joinToString("")
}

class DraftRepository internal constructor(private val dao: DraftDao) {
    constructor(context: Context) : this(UserDataDatabase.create(context.applicationContext).draftDao())
    private val mutex = kotlinx.coroutines.sync.Mutex()
    private var activeSession: Long? = null
    private var activeId: Long? = null

    suspend fun record(text: String, appPackage: String, source: String, session: Long, now: Long = System.currentTimeMillis()) = mutex.withLock {
        if (text.isEmpty()) return@withLock
        dao.deleteBefore(now - THREE_HOURS_MS)
        val previous = activeId?.takeIf { activeSession == session }?.let { dao.find(it) }
        if (previous != null && previous.appPackage == appPackage) {
            dao.update(previous.copy(
                content = (previous.content + DraftRedactor.redact(text)).takeLast(500_000),
                editEvents = DraftEditEvents.append(previous.editEvents, DraftEditEvent(previous.revision + 1, now, "insert", text, "applied")),
                source = if (previous.source == source) source else "mixed",
                updatedAt = now,
                revision = previous.revision + 1,
            ))
        } else {
            if (text.isBlank()) return@withLock
            activeId = dao.insert(DraftEntryRow(createdAt = now, appPackage = appPackage, source = source, content = DraftRedactor.redact(text).takeLast(500_000), editEvents = DraftEditEvents.append("[]", DraftEditEvent(1, now, "insert", text, "applied"))))
            activeSession = session
        }
    }
    suspend fun recordEdit(kind: String, text: String, outcome: String, appPackage: String,
                           session: Long, now: Long = System.currentTimeMillis()) = mutex.withLock {
        require(kind in setOf("delete_backward", "compose_delete", "clear", "restore"))
        require(outcome in setOf("requested", "applied"))
        dao.deleteBefore(now - THREE_HOURS_MS)
        val previous = activeId?.takeIf { activeSession == session }?.let { dao.find(it) }
            ?.takeIf { it.appPackage == appPackage }
        val revision = (previous?.revision ?: 0) + 1
        val events = DraftEditEvents.append(previous?.editEvents ?: "[]", DraftEditEvent(revision, now, kind, text, outcome))
        if (previous != null) {
            dao.update(previous.copy(editEvents = events, updatedAt = now, revision = revision))
        } else {
            activeId = dao.insert(DraftEntryRow(createdAt = now, appPackage = appPackage,
                source = "keyboard", content = "", editEvents = events))
            activeSession = session
        }
    }
    suspend fun recent(now: Long = System.currentTimeMillis()) = mutex.withLock {
        dao.deleteBefore(now - THREE_HOURS_MS)
        dao.recent(now - THREE_HOURS_MS)
    }
    suspend fun prune(now: Long = System.currentTimeMillis()) = mutex.withLock { dao.deleteBefore(now - THREE_HOURS_MS) }
    suspend fun clear() = mutex.withLock { activeSession = null; activeId = null; dao.deleteAll() }
    companion object { const val THREE_HOURS_MS = 3 * 60 * 60 * 1000L }
}
