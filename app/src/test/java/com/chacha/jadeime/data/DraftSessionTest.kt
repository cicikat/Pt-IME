package com.chacha.jadeime.data

import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking

class DraftSessionTest {
    @Test fun idleBoundaryAndAppSwitch() {
        val session = DraftSession()
        val first = session.activity("a", 0)
        assertEquals(first, session.activity("a", 299_999))
        assertNotEquals(first, session.activity("a", 599_999))
        val second = session.activity("a", 600_000)
        assertNotEquals(second, session.activity("b", 600_001))
        assertNotEquals(second, session.activity("a", 600_002))
    }

    @Test fun continuousActivityHasNoMaximumSentenceDuration() {
        val session = DraftSession()
        val first = session.activity("a", 0)
        for (minute in 1..240) assertEquals(first, session.activity("a", minute * 60_000L))
        session.breakSession()
        assertNotEquals(first, session.activity("a", 241 * 60_000L))
    }

    @Test fun appendsPreserveWhitespaceAndMergeVoiceOnlyOnce() = runBlocking {
        val dao = MemoryDraftDao()
        val repo = DraftRepository(dao)
        repo.record("hello", "a", "keyboard", 1, 100)
        repo.record(" ", "a", "keyboard", 1, 101)
        repo.record("world", "a", "voice", 1, 102)
        repo.record("\n", "a", "keyboard", 1, 103)
        val row = repo.recent(104).single()
        assertEquals("hello world\n", row.content)
        assertEquals("mixed", row.source)
        assertEquals(4L, row.revision)
        assertEquals(100L, row.createdAt)
        assertEquals(103L, row.updatedAt)
        repo.record("next", "a", "keyboard", 2, 105)
        assertEquals(2, repo.recent(106).size)
    }

    @Test fun clearAndExpiryDoNotResurrectOldContent() = runBlocking {
        val repo = DraftRepository(MemoryDraftDao())
        repo.record("123", "a", "keyboard", 1, 100)
        assertEquals("***", repo.recent(101).single().content)
        repo.clear()
        repo.record("new", "a", "voice", 1, 102)
        assertEquals("new", repo.recent(103).single().content)
        assertTrue(repo.recent(103 + DraftRepository.THREE_HOURS_MS).isEmpty())
        repo.record("fresh", "a", "keyboard", 1, 104 + DraftRepository.THREE_HOURS_MS)
        assertEquals("fresh", repo.recent(105 + DraftRepository.THREE_HOURS_MS).single().content)
    }
}

private class MemoryDraftDao : DraftDao {
    private val rows = linkedMapOf<Long, DraftEntryRow>()
    private var nextId = 0L
    override suspend fun insert(row: DraftEntryRow): Long = (++nextId).also { rows[it] = row.copy(id = it) }
    override suspend fun update(row: DraftEntryRow) { rows[row.id] = row }
    override suspend fun find(id: Long) = rows[id]
    override suspend fun recent(since: Long) = rows.values.filter { it.updatedAt >= since }.sortedByDescending { it.updatedAt }
    override suspend fun deleteBefore(before: Long) { rows.entries.removeAll { it.value.updatedAt < before } }
    override suspend fun deleteAll() { rows.clear() }
    override suspend fun after(after: Long) = rows.values.filter { it.id > after }
}
