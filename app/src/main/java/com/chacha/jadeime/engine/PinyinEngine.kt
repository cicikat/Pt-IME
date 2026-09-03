package com.chacha.jadeime.engine

/** One row of the lexicon: a word reachable by typing [pinyinKey] (toneless, no separators). */
data class LexiconEntry(
    /** Stable row id from the shipped lexicon database. */
    val id: Long = 0L,
    val pinyinKey: String,
    val initials: String,
    val word: String,
    val baseFreq: Long,
)

/** Explicit provenance used by the ranking pipeline. */
enum class CandidateSource {
    EXACT_SINGLE_SYLLABLE,
    EXACT_MULTI_WORD,
    PURE_FULL_PINYIN_SENTENCE,
    CONSERVATIVE_CORRECTION,
    FULL_PINYIN_PREFIX,
    ABBREVIATION,
    USER_PHRASE,
}

/** A candidate offered for the current composing buffer. */
data class Candidate(
    val word: String,
    /** Prefix of the composing buffer this candidate consumes if chosen. */
    val pinyinConsumed: String,
    val score: Long,
    /** True for the greedy whole-buffer segmentation shown as the lead candidate. */
    val isSentence: Boolean = false,
    /** True when this candidate came from a conservative one-character typo repair. */
    val isCorrection: Boolean = false,
    /** Canonical full pinyin, shared by full-pinyin and initialism lookups for learning. */
    val canonicalPinyin: String = pinyinConsumed,
    /** True when the visible candidate was reached through initials rather than full pinyin. */
    val isAbbreviation: Boolean = false,
    /** Explicit source tier; UI should not infer ranking semantics from flags. */
    val source: CandidateSource = CandidateSource.FULL_PINYIN_PREFIX,
    /** Static lexicon row id(s) backing this candidate; no plaintext identity is needed. */
    val entryIds: List<Long> = emptyList(),
)

/** Plaintext-free v2 memory key: one static row or an ordered component sequence. */
data class CandidateMemoryV2(
    val entryIds: List<Long>,
    val frequency: Long,
    val lastUsed: Long,
    val lexiconVersion: String,
)

data class ChooseResult(val remainingPinyin: String)

/**
 * Manual segmentation separator the user can type to force a break between
 * syllables (e.g. "n'hao"), and that composing-display code inserts between
 * auto-detected syllables (PLAN M1.5 "拼音切分可视 + 手动分隔").
 */
const val PINYIN_SEPARATOR: Char = '\''

/**
 * Engine contract for turning a raw pinyin buffer into ranked candidates.
 * UI and IME service code must depend on this interface only -- never on
 * [TrieLexiconEngine] directly -- so the engine stays swappable (see DESIGN.md 3.3).
 */
interface PinyinEngine {
    fun input(pinyin: String): List<Candidate>

    /**
     * Cancellable variant used by the IME latest-only refresh pipeline. The
     * callback is checked at bounded Trie/Beam/correction checkpoints and must
     * not block.
     */
    fun input(pinyin: String, shouldCancel: () -> Boolean): List<Candidate> = input(pinyin)

    fun choose(candidate: Candidate): ChooseResult
    fun reset()

    /** Sensitive editor fields disable both learning writes and memory boosts. */
    fun setLearningEnabled(enabled: Boolean) {}

    /** Ends composing state while retaining learned ranking across input sessions. */
    fun endSession()

    /**
     * Breaks [pinyin] into the syllable-ish chunks the engine would use to
     * build its "sentence" candidate, for the composing bar to redisplay as
     * `chunks.joinToString("'")` (e.g. "nihao" -> "ni'hao"). Purely a display
     * aid -- callers must keep sending the raw, unsplit buffer to [input].
     */
    fun previewSegmentation(pinyin: String): List<String>
}
