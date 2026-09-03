package com.chacha.jadeime.data

import android.content.Context
import android.util.Log
import com.chacha.jadeime.engine.CandidateMemoryV2
import com.chacha.jadeime.engine.LexiconEntry
import com.chacha.jadeime.engine.TrieLexiconEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Loads the static lexicon into a [TrieLexiconEngine] while keeping the
 * plaintext-free v2 ranking state in the data layer. Keeps Room/Android types out of
 * the `engine` package (CLAUDE.md: engine must stay UI/framework-agnostic).
 */
class PinyinRepository(context: Context) {
    private val lexiconDb = LexiconDatabase.create(context.applicationContext)
    private val userDb = UserDataDatabase.create(context.applicationContext)
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Runs on a background dispatcher; construction of a 20万-entry Trie is not free. */
    suspend fun loadEngine(): TrieLexiconEngine {
        val staticEntries = lexiconDb.lexiconDao().getAll().map {
            LexiconEntry(
                id = it.id,
                pinyinKey = it.pinyinKey,
                initials = it.initials,
                word = it.word,
                baseFreq = it.baseFreq,
            )
        }
        val initialMemory = userDb.userDictDao().getCandidateMemoryV2().mapNotNull { row ->
            val ids = row.entryIds.split(',').mapNotNull { it.toLongOrNull() }
            if (ids.isEmpty() || ids.size != row.entryIds.split(',').size) return@mapNotNull null
            CandidateMemoryV2(
                entryIds = ids,
                frequency = row.frequency,
                lastUsed = row.lastUsed,
                lexiconVersion = row.lexiconVersion,
            )
        }
        return TrieLexiconEngine(
            staticEntries = staticEntries,
            initialMemory = initialMemory,
            onMemoryChanged = ::persistMemory,
        )
    }

    private fun persistMemory(memory: CandidateMemoryV2) {
        persistenceScope.launch {
            try {
                userDb.userDictDao().upsertCandidateMemoryV2(
                    keyKind = if (memory.entryIds.size == 1) 0 else 1,
                    entryIds = memory.entryIds.joinToString(","),
                    frequency = memory.frequency,
                    lastUsed = memory.lastUsed,
                    lexiconVersion = memory.lexiconVersion,
                )
            } catch (error: Exception) {
                // Never log candidate text or pinyin; the opaque row is enough to diagnose the write path.
                Log.e("PinyinRepository", "failed to persist candidate memory", error)
            }
        }
    }
}
