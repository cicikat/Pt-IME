package com.chacha.jadeime.engine

import java.security.MessageDigest
import kotlin.math.ln
import kotlin.math.roundToLong

/**
 * Pure-Kotlin, JVM-testable pinyin engine. No Android/Room dependency here --
 * loading [LexiconEntry] rows off disk is the caller's job (see the `data`
 * layer), this class only holds the in-memory Trie and scoring logic
 * (CLAUDE.md: "不许把 Trie 实现耦合进 UI", kept out of
 * the Android layer entirely too).
 *
 * Lookup strategy:
 * - full pinyin: every dictionary entry whose `pinyinKey` is itself a prefix of
 *   the typed buffer (walking a trie char-by-char naturally finds these).
 * - 简拼 abbreviation: same walk over a second trie keyed by `initials`. Matches
 *   of length >=2 are trusted at full weight; a bare 1-letter hit is kept but
 *   downweighted (PLAN M1.5: previously filtered out entirely, which was the
 *   root cause of short 简拼 not producing anything).
 * - whole-buffer "sentence" candidates: a small beam search considers every
 *   valid segmentation. This prevents a locally longest word from forcing a
 *   bad sentence candidate ahead of a better global path.
 * - a manual [PINYIN_SEPARATOR] the user types forces a hard segment boundary
 *   that matching never crosses (PLAN M1.5 "手动分隔").
 * - conservative one-character correction: adjacent-key replacement for a word,
 *   plus vowel insertion/deletion for a full-pinyin sentence when the raw buffer
 *   has no full-pinyin path at all. Corrected candidates are explicitly marked.
 */
class TrieLexiconEngine(
    staticEntries: List<LexiconEntry>,
    initialMemory: List<CandidateMemoryV2> = emptyList(),
    private val onMemoryChanged: (CandidateMemoryV2) -> Unit = {},
) : PinyinEngine {

    /**
     * Test fixtures from before the v2 schema may omit ids. Assigning a stable
     * fixture-local id keeps the engine usable in JVM tests while production
     * rows always retain the Room primary key.
     */
    private val staticEntries = staticEntries.mapIndexed { index, entry ->
        if (entry.id > 0L) entry else entry.copy(id = index.toLong() + 1L)
    }
    private val byPinyinKey = PrefixTrie { it.pinyinKey }
    private val byInitials = PrefixTrie { it.initials }
    private val entriesById = this.staticEntries.associateBy { it.id }
    private val lexiconVersion = computeLexiconVersion(this.staticEntries)
    private val memory = HashMap<List<Long>, CandidateMemoryV2>()
    private val validSingleSyllables = PINYIN_SYLLABLES.intersect(this.staticEntries
        .asSequence()
        .filter { it.word.length == 1 }
        .map { it.pinyinKey }
        .toSet())
    private var memoryClock = initialMemory.maxOfOrNull { it.lastUsed } ?: 0L
    private var composing: String = ""
    private var transactionPinyin: String = ""
    private val selectedEntryIds = ArrayList<Long>()
    private var learningEnabled = true

    init {
        for (entry in this.staticEntries) add(entry)
        for (stored in initialMemory) {
            val key = stored.entryIds.toList()
            if (
                stored.lexiconVersion == lexiconVersion &&
                key.isNotEmpty() &&
                key.all { entriesById.containsKey(it) }
            ) {
                memory[key] = stored.copy(entryIds = key)
            }
        }
    }

    override fun input(pinyin: String): List<Candidate> = input(pinyin) { false }

    override fun input(pinyin: String, shouldCancel: () -> Boolean): List<Candidate> {
        val previousComposing = composing
        if (pinyin.isNotEmpty()) {
            if (previousComposing.isEmpty() || pinyin != previousComposing) {
                // A changed raw buffer means the user edited/canceled the prior
                // selection chain. Only the exact remaining suffix continues it.
                if (pinyin != previousComposing) {
                    selectedEntryIds.clear()
                    transactionPinyin = pinyin
                }
            }
        }
        composing = pinyin
        if (pinyin.isEmpty() || shouldCancel()) return emptyList()

        // rime-ice spells ü with `v` (lve/nve), while users commonly type
        // `lue`/`nue` on a qwerty keyboard. The substitution is length-preserving,
        // so candidates can still consume and edit the original composing text.
        val lookupPinyin = normalizeUmlautPinyin(pinyin)

        val results = LinkedHashMap<Pair<String, String>, Candidate>()
        for (entry in byPinyinKey.entriesWithKeyPrefixOf(lookupPinyin, shouldCancel, DIRECT_CANDIDATE_LIMIT)) {
            if (shouldCancel()) return emptyList()
            offer(
                results,
                Candidate(
                    word = entry.word,
                    pinyinConsumed = entry.pinyinKey,
                    score = scoreOf(entry),
                    canonicalPinyin = entry.pinyinKey,
                    source = sourceForFullEntry(entry, lookupPinyin),
                    entryIds = listOf(entry.id),
                ),
            )
        }
        for (entry in byInitials.entriesWithKeyPrefixOf(lookupPinyin, shouldCancel, DIRECT_CANDIDATE_LIMIT)) {
            if (shouldCancel()) return emptyList()
            val singleLetter = entry.initials.length <= 1
            val score = if (singleLetter) downweightSingleLetter(scoreOf(entry)) else scoreOf(entry)
            offer(
                results,
                Candidate(
                    word = entry.word,
                    pinyinConsumed = entry.initials,
                    score = score,
                    canonicalPinyin = entry.pinyinKey,
                    isAbbreviation = true,
                    source = CandidateSource.ABBREVIATION,
                    entryIds = listOf(entry.id),
                ),
            )
        }

        val decodedSentences = decodeSentences(lookupPinyin, shouldCancel = shouldCancel)
        if (shouldCancel()) return emptyList()
        val hasCompleteFullPinyinPath =
            byPinyinKey.hasExactKey(lookupPinyin) || decodedSentences.any { !it.isAbbreviation }
        val hasCompleteAbbreviationPath =
            lookupPinyin.length >= 2 && byInitials.hasExactKey(lookupPinyin)
        val hasCompleteSingleInitialMix =
            hasFullPinyinSingleInitialMix(lookupPinyin, shouldCancel)
        val legalSingleSyllable = lookupPinyin in validSingleSyllables && byPinyinKey.hasExactKey(lookupPinyin)

        // A legal one-syllable reading such as xuan must not be reinterpreted
        // as xu+an. The exact homophone list is the useful surface here; the
        // split sentence remains available for genuinely multi-syllable input.
        if (!legalSingleSyllable) {
            for (sentence in decodedSentences) {
                if (shouldCancel()) return emptyList()
                offer(results, sentence)
            }
        }

        // User phrases are reconstructed from static ids. They are added before
        // sorting so an exact remembered phrase can take its explicit top tier.
        for (phrase in rememberedPhraseCandidates(lookupPinyin, shouldCancel)) {
            if (shouldCancel()) return emptyList()
            offer(results, phrase)
        }

        // A valid full-pinyin path or an explicit multi-letter initialism is more
        // trustworthy than a repair. This keeps `hd` -> 好的 from being buried by
        // neighboring-key guesses while still rescuing inputs with no direct path,
        // e.g. wo-je-de-ke-yi -> wo-jue-de-ke-yi.
        if (!hasCompleteFullPinyinPath && !hasCompleteAbbreviationPath && !hasCompleteSingleInitialMix) {
            for (candidate in sentenceCorrectionCandidates(lookupPinyin, shouldCancel)) {
                if (shouldCancel()) return emptyList()
                offer(results, candidate)
            }
            for (candidate in correctionCandidates(lookupPinyin, shouldCancel)) {
                if (shouldCancel()) return emptyList()
                offer(results, candidate)
            }
        }

        if (shouldCancel()) return emptyList()
        val ranked = results.values.sortedWith(candidateComparator(lookupPinyin)).toMutableList()
        promoteRememberedAbbreviation(ranked, lookupPinyin)
        return ranked.map { candidate ->
            candidate.copy(pinyinConsumed = pinyin.take(candidate.pinyinConsumed.length))
        }
    }

    override fun choose(candidate: Candidate): ChooseResult {
        if (
            learningEnabled &&
            candidate.source != CandidateSource.CONSERVATIVE_CORRECTION &&
            candidate.entryIds.isNotEmpty()
        ) {
            if (candidate.source == CandidateSource.USER_PHRASE || candidate.entryIds.size == 1) {
                remember(candidate.entryIds)
            }
            if (candidate.source != CandidateSource.USER_PHRASE) {
                selectedEntryIds += candidate.entryIds
            }
        }

        val remaining = composing.removePrefix(candidate.pinyinConsumed)
        composing = remaining
        if (
            remaining.isEmpty() &&
            transactionPinyin.isNotEmpty() &&
            selectedEntryIds.size >= MIN_USER_PHRASE_COMPONENTS &&
            candidate.source != CandidateSource.USER_PHRASE
        ) {
            val phraseIds = selectedEntryIds.toList()
            if (phraseIds.all { entriesById.containsKey(it) }) remember(phraseIds)
            selectedEntryIds.clear()
            transactionPinyin = ""
        }
        return ChooseResult(remaining)
    }

    private fun remember(entryIds: List<Long>) {
        val key = entryIds.toList()
        if (key.isEmpty() || key.any { !entriesById.containsKey(it) }) return
        val previous = memory[key]
        val updated = CandidateMemoryV2(
            entryIds = key,
            frequency = (previous?.frequency ?: 0L) + 1L,
            lastUsed = ++memoryClock,
            lexiconVersion = lexiconVersion,
        )
        memory[key] = updated
        runCatching { onMemoryChanged(updated) }
    }

    override fun reset() {
        composing = ""
        transactionPinyin = ""
        selectedEntryIds.clear()
    }

    override fun setLearningEnabled(enabled: Boolean) {
        learningEnabled = enabled
        transactionPinyin = ""
        selectedEntryIds.clear()
    }

    override fun endSession() {
        composing = ""
        transactionPinyin = ""
        selectedEntryIds.clear()
    }

    override fun previewSegmentation(pinyin: String): List<String> {
        if (pinyin.isEmpty()) return emptyList()
        val lookupPinyin = normalizeUmlautPinyin(pinyin)
        val chunks = mutableListOf<String>()
        var pos = 0
        while (pos < lookupPinyin.length) {
            if (lookupPinyin[pos] == PINYIN_SEPARATOR) {
                pos += 1
                continue
            }
            val scoped = scopedRemainder(lookupPinyin, pos)
            val match = bestSegmentMatch(scoped)
            if (match == null) {
                chunks += pinyin.substring(pos, pos + scoped.length)
                pos += scoped.length
            } else {
                chunks += pinyin.substring(pos, pos + match.matchedKey.length)
                pos += match.matchedKey.length
            }
        }
        return chunks
    }

    private fun normalizeUmlautPinyin(pinyin: String): String =
        pinyin.replace("lue", "lve").replace("nue", "nve")

    private fun offer(results: MutableMap<Pair<String, String>, Candidate>, candidate: Candidate) {
        val key = candidate.word to candidate.pinyinConsumed
        val existing = results[key]
        if (
            existing == null ||
            sourcePriority(candidate) < sourcePriority(existing) ||
            (sourcePriority(candidate) == sourcePriority(existing) && candidate.score > existing.score)
        ) {
            results[key] = candidate
        }
    }

    /** Keep a real lexicon phrase when Beam also reconstructs the same text from characters. */
    private fun sourcePriority(candidate: Candidate): Int = when {
        candidate.source == CandidateSource.USER_PHRASE -> 0
        candidate.source == CandidateSource.CONSERVATIVE_CORRECTION -> 4
        candidate.source == CandidateSource.ABBREVIATION -> 3
        candidate.source == CandidateSource.PURE_FULL_PINYIN_SENTENCE -> 2
        else -> 1
    }

    private fun scoreOf(entry: LexiconEntry): Long {
        return (ln(entry.baseFreq.toDouble() + 1.0) * BASE_FREQUENCY_SCALE).roundToLong()
    }

    /**
     * Confidence tiers come before raw frequency. In particular an exact lexicon
     * phrase for the whole full-pinyin buffer must beat a high-frequency split
     * sentence ("shihuai" -> "释怀", not "是坏"). Inside the same tier, one
     * deliberate selection immediately wins through the user-memory fields.
     */
    /**
     * Comparator deliberately stays structural: no SHA, hex formatting, or
     * candidate-text construction is allowed on this hot path. Memory lookup is
     * a direct immutable component-id list lookup prepared by the candidate.
     */
    private fun candidateComparator(input: String): Comparator<Candidate> = Comparator { left, right ->
        compareCandidates(left, right, input)
    }

    private fun compareCandidates(left: Candidate, right: Candidate, input: String): Int {
        val consumed = right.pinyinConsumed.length.compareTo(left.pinyinConsumed.length)
        if (consumed != 0) return consumed

        val tier = confidenceTier(left, input).compareTo(confidenceTier(right, input))
        if (tier != 0) return tier

        // 简拼 has a dedicated second-place policy below. Applying normal
        // memory boost here would incorrectly push it to first.
        val leftMemory = if (left.isAbbreviation) null else memoryFor(left)
        val rightMemory = if (right.isAbbreviation) null else memoryFor(right)
        val frequency = (rightMemory?.frequency ?: 0L).compareTo(leftMemory?.frequency ?: 0L)
        if (frequency != 0) return frequency
        val lastUsed = (rightMemory?.lastUsed ?: 0L).compareTo(leftMemory?.lastUsed ?: 0L)
        if (lastUsed != 0) return lastUsed
        val score = right.score.compareTo(left.score)
        if (score != 0) return score
        return left.word.compareTo(right.word)
    }

    private fun confidenceTier(candidate: Candidate, input: String): Int {
        val exact = candidate.pinyinConsumed == input
        if (!exact) return 6
        return when (candidate.source) {
            CandidateSource.USER_PHRASE -> if (candidate.isAbbreviation) 5 else 0
            CandidateSource.EXACT_MULTI_WORD -> 1
            CandidateSource.EXACT_SINGLE_SYLLABLE -> 1
            CandidateSource.PURE_FULL_PINYIN_SENTENCE -> 2
            CandidateSource.CONSERVATIVE_CORRECTION -> 3
            CandidateSource.FULL_PINYIN_PREFIX -> 4
            CandidateSource.ABBREVIATION -> 5
        }
    }

    /**
     * Initialisms are extremely ambiguous, so a remembered item is pinned to
     * second rather than allowed to erase the strongest dictionary default.
     */
    private fun promoteRememberedAbbreviation(ranked: MutableList<Candidate>, input: String) {
        val rememberedIndex = ranked.indices
            .filter { index ->
                val candidate = ranked[index]
                candidate.isAbbreviation &&
                    candidate.pinyinConsumed == input &&
                    (memoryFor(candidate)?.frequency ?: 0L) > 0L
            }
            .maxByOrNull { index -> memoryFor(ranked[index])?.lastUsed ?: 0L }
            ?: return
        if (rememberedIndex > 1) {
            val remembered = ranked.removeAt(rememberedIndex)
            ranked.add(1.coerceAtMost(ranked.size), remembered)
        }
    }

    private fun memoryFor(candidate: Candidate): CandidateMemoryV2? =
        if (learningEnabled) candidate.entryIds.takeIf { it.isNotEmpty() }?.let { memory[it] } else null

    private fun sourceForFullEntry(entry: LexiconEntry, input: String): CandidateSource = when {
        entry.pinyinKey != input -> CandidateSource.FULL_PINYIN_PREFIX
        entry.word.length == 1 && input in validSingleSyllables -> CandidateSource.EXACT_SINGLE_SYLLABLE
        entry.word.length == 1 -> CandidateSource.FULL_PINYIN_PREFIX
        else -> CandidateSource.EXACT_MULTI_WORD
    }

    private fun rememberedPhraseCandidates(
        input: String,
        shouldCancel: () -> Boolean,
    ): List<Candidate> {
        if (!learningEnabled) return emptyList()
        val phrases = ArrayList<Candidate>(MAX_REMEMBERED_PHRASES)
        for ((ids, remembered) in memory) {
            if (shouldCancel()) return emptyList()
            if (ids.size < MIN_USER_PHRASE_COMPONENTS || remembered.lexiconVersion != lexiconVersion) continue
            val entries = ids.mapNotNull(entriesById::get)
            if (entries.size != ids.size) continue
            val word = entries.joinToString(separator = "") { it.word }
            val canonicalPinyin = entries.joinToString(separator = "") { it.pinyinKey }
            val initials = entries.joinToString(separator = "") { it.initials }
            if (canonicalPinyin.isNotEmpty() && input.startsWith(canonicalPinyin)) {
                phrases += Candidate(
                    word = word,
                    pinyinConsumed = canonicalPinyin,
                    score = rememberedScore(remembered),
                    canonicalPinyin = canonicalPinyin,
                    source = CandidateSource.USER_PHRASE,
                    entryIds = ids,
                )
            }
            if (initials.isNotEmpty() && input.startsWith(initials)) {
                phrases += Candidate(
                    word = word,
                    pinyinConsumed = initials,
                    score = rememberedScore(remembered),
                    canonicalPinyin = canonicalPinyin,
                    isAbbreviation = true,
                    source = CandidateSource.USER_PHRASE,
                    entryIds = ids,
                )
            }
            if (phrases.size >= MAX_REMEMBERED_PHRASES) break
        }
        return phrases
    }

    private fun rememberedScore(memory: CandidateMemoryV2): Long =
        BASE_FREQUENCY_SCALE.toLong() + memory.frequency.coerceAtMost(1_000L)

    /**
     * Dictionary frequencies for a single character and a multi-character word
     * are not directly comparable: “可” and “一” are individually very common,
     * but that must not make “可一” beat the established word “可以” in a sentence.
     * This only affects Beam paths; single-character candidates keep their normal
     * homophone ranking in the candidate bar.
     */
    private fun segmentScoreOf(entry: LexiconEntry): Long =
        scoreOf(entry) - if (entry.word.length == 1) SINGLE_CHARACTER_SEGMENT_PENALTY else 0L

    private fun downweightSingleLetter(score: Long): Long = (score * SINGLE_LETTER_ABBREV_WEIGHT).roundToLong()

    private fun add(entry: LexiconEntry) {
        byPinyinKey.insert(entry, entry.pinyinKey)
        if (entry.initials.isNotEmpty()) byInitials.insert(entry, entry.initials)
    }

    private class SegmentMatch(
        val entry: LexiconEntry,
        val matchedKey: String,
        val score: Long,
        /** A compact initialism is useful, but less certain than a full-pinyin match. */
        val isAbbreviation: Boolean = false,
    )

    private data class DecodeState(
        val word: String,
        val canonicalPinyin: String,
        val entryIds: List<Long>,
        val totalScore: Long,
        val segments: Int,
        val abbreviationSegments: Int,
        val syllables: Int = 0,
        val extraSyllables: Int = 0,
    ) {
        fun append(match: SegmentMatch, minimumSyllables: Int): DecodeState = DecodeState(
            word = word + match.entry.word,
            canonicalPinyin = canonicalPinyin + match.entry.pinyinKey,
            entryIds = entryIds + match.entry.id,
            totalScore = totalScore + match.score,
            segments = segments + 1,
            abbreviationSegments = abbreviationSegments + if (match.isAbbreviation) 1 else 0,
            syllables = syllables + match.entry.initials.length,
            extraSyllables = if (minimumSyllables == Int.MAX_VALUE) 0 else
                (syllables + match.entry.initials.length - minimumSyllables).coerceAtLeast(0),
        )

        fun candidate(pinyin: String): Candidate {
            val averageScore = totalScore / segments.coerceAtLeast(1)
            return Candidate(
                word = word,
                pinyinConsumed = pinyin,
                score = rankingScore(),
                isSentence = segments > 1,
                canonicalPinyin = canonicalPinyin,
                isAbbreviation = abbreviationSegments > 0,
                source = if (abbreviationSegments > 0) {
                    CandidateSource.ABBREVIATION
                } else {
                    CandidateSource.PURE_FULL_PINYIN_SENTENCE
                },
                entryIds = entryIds,
            )
        }

        /**
         * Beam pruning must use the same objective as final candidate ordering.
         *
         * Summing per-word frequency made a path with many tiny 简拼 matches look
         * better halfway through a long full-pinyin buffer, even though its final
         * average score was worse.  It could then evict the intended full-pinyin
         * path before that path reached the end of the buffer.
         */
        fun rankingScore(): Long =
            totalScore / segments.coerceAtLeast(1) -
                (segments - 1) * SEGMENTATION_PENALTY -
                abbreviationSegments * ABBREVIATION_SEGMENT_PENALTY -
                if (abbreviationSegments == 0) extraSyllables * EXTRA_SYLLABLE_PENALTY else 0L
    }

    /** The substring of [pinyin] from [pos] up to (not including) the next [PINYIN_SEPARATOR], or the end. */
    private fun scopedRemainder(pinyin: String, pos: Int): String {
        val remaining = pinyin.substring(pos)
        val boundary = remaining.indexOf(PINYIN_SEPARATOR).let { if (it < 0) remaining.length else it }
        return remaining.substring(0, boundary)
    }

    /** Returns complete, globally scored paths through the current pinyin buffer. */
    private fun decodeSentences(
        pinyin: String,
        allowAbbreviations: Boolean = true,
        shouldCancel: () -> Boolean = { false },
    ): List<Candidate> {
        if (pinyin.isEmpty() || shouldCancel()) return emptyList()
        val minimumSyllables = minimumSyllableCounts(pinyin, shouldCancel)
        val beams = Array(pinyin.length + 1) { mutableListOf<DecodeState>() }
        beams[0] += DecodeState(
            word = "",
            canonicalPinyin = "",
            entryIds = emptyList(),
            totalScore = 0L,
            segments = 0,
            abbreviationSegments = 0,
        )

        for (pos in pinyin.indices) {
            if (shouldCancel()) return emptyList()
            val states = beams[pos]
            if (states.isEmpty()) continue
            trimBeam(states)
            if (pinyin[pos] == PINYIN_SEPARATOR) {
                beams[pos + 1] += states
                continue
            }
            for (state in states) {
                if (shouldCancel()) return emptyList()
                for (match in segmentMatches(pinyin, pos, allowAbbreviations, shouldCancel)) {
                    if (shouldCancel()) return emptyList()
                    val next = pos + match.matchedKey.length
                    beams[next] += state.append(match, minimumSyllables[next])
                }
            }
        }

        return beams[pinyin.length]
            .asSequence()
            .filter { it.segments > 1 }
            .map { it.candidate(pinyin) }
            .sortedByDescending { it.score }
            .take(SENTENCE_CANDIDATE_LIMIT)
            .toList()
    }

    private fun trimBeam(states: MutableList<DecodeState>) {
        if (states.size <= BEAM_WIDTH) return
        states.sortByDescending(DecodeState::rankingScore)
        states.subList(BEAM_WIDTH, states.size).clear()
    }

    /** Structural baseline independent of word frequencies. Keep all legal
     * readings, but charge extra syllables (mao -> ma + o) during pruning too.
     * Explicit separators are hard boundaries, so xi'an remains intentional.
     */
    private fun minimumSyllableCounts(pinyin: String, shouldCancel: () -> Boolean): IntArray {
        val counts = IntArray(pinyin.length + 1) { Int.MAX_VALUE }
        counts[0] = 0
        for (pos in pinyin.indices) {
            if (shouldCancel()) return counts
            if (counts[pos] == Int.MAX_VALUE) continue
            if (pinyin[pos] == PINYIN_SEPARATOR) {
                counts[pos + 1] = minOf(counts[pos + 1], counts[pos])
                continue
            }
            for (end in pos + 1..minOf(pos + 6, pinyin.length)) {
                if (pinyin.substring(pos, end) in PINYIN_SYLLABLES) {
                    counts[end] = minOf(counts[end], counts[pos] + 1)
                }
            }
        }
        return counts
    }

    private fun segmentMatches(
        pinyin: String,
        pos: Int,
        allowAbbreviations: Boolean = true,
        shouldCancel: () -> Boolean = { false },
    ): List<SegmentMatch> {
        val scoped = scopedRemainder(pinyin, pos)
        if (scoped.isEmpty()) return emptyList()
        val matches = LinkedHashMap<Pair<String, String>, SegmentMatch>()
        fun offer(match: SegmentMatch) {
            val key = match.entry.word to match.matchedKey
            val old = matches[key]
            if (old == null || match.score > old.score) matches[key] = match
        }

        for (entry in byPinyinKey.entriesWithKeyPrefixOf(scoped, shouldCancel, MATCH_LIMIT_PER_POSITION)) {
            if (shouldCancel()) return emptyList()
            offer(SegmentMatch(entry, entry.pinyinKey, segmentScoreOf(entry)))
        }
        if (allowAbbreviations) {
            val allowSingleLetter = scoped.length == 1 ||
                hasCompleteFullPinyinSegmentation(pinyin, pos + 1, shouldCancel)
            for (entry in byInitials.entriesWithKeyPrefixOf(scoped, shouldCancel, MATCH_LIMIT_PER_POSITION)) {
                if (shouldCancel()) return emptyList()
                val singleLetter = entry.initials.length <= 1
                if (singleLetter && !allowSingleLetter) continue
                val segmentScore = segmentScoreOf(entry)
                val score = if (singleLetter) downweightSingleLetter(segmentScore) else segmentScore
                offer(SegmentMatch(entry, entry.initials, score, isAbbreviation = true))
            }
        }
        return matches.values.sortedByDescending { it.score }.take(MATCH_LIMIT_PER_POSITION)
    }

    /**
     * Recognizes a conservative mixed-input shape: one or more complete full-pinyin
     * segments, one single-letter initial, then an optional complete full-pinyin
     * suffix. Examples are `yi+j` and `yi+j+wei`. Keeping the initial bounded to
     * exactly one position avoids treating arbitrary initialism chains as proof
     * that typo correction should be disabled.
     */
    private fun hasFullPinyinSingleInitialMix(
        pinyin: String,
        shouldCancel: () -> Boolean = { false },
    ): Boolean {
        if (pinyin.contains(PINYIN_SEPARATOR) || pinyin.length < 3) return false
        for (initialPos in 1 until pinyin.length) {
            if (shouldCancel()) return false
            if (!byInitials.hasExactKey(pinyin[initialPos].toString())) continue
            if (!hasCompleteFullPinyinSegmentation(pinyin, 0, shouldCancel, initialPos)) continue
            if (initialPos + 1 == pinyin.length ||
                hasCompleteFullPinyinSegmentation(pinyin, initialPos + 1, shouldCancel)
            ) return true
        }
        return false
    }

    private fun hasCompleteFullPinyinSegmentation(
        pinyin: String,
        start: Int,
        shouldCancel: () -> Boolean = { false },
        endExclusive: Int = pinyin.length,
    ): Boolean {
        if (start == endExclusive) return true
        if (start !in 0 until endExclusive || endExclusive > pinyin.length) return false
        val reachable = BooleanArray(endExclusive + 1)
        reachable[start] = true
        for (pos in start until endExclusive) {
            if (shouldCancel()) return false
            if (!reachable[pos]) continue
            val scoped = pinyin.substring(pos, endExclusive)
            for (entry in byPinyinKey.entriesWithKeyPrefixOf(scoped, shouldCancel, MATCH_LIMIT_PER_POSITION)) {
                val next = pos + entry.pinyinKey.length
                if (next <= endExclusive) reachable[next] = true
            }
        }
        return reachable[endExclusive]
    }

    /**
     * Best match at one segmentation step: longest full-pinyin hit wins; a 简拼
     * hit is used instead when it reaches further. A bare 1-letter 简拼 is only
     * trusted when it exactly accounts for everything left in [scoped] -- mid-buffer
     * it's too ambiguous a guess and would fragment the segmentation into noise
     * (e.g. the trailing "ma" of "nihaoma" must NOT get treated as a 1-letter hit).
     */
    private fun bestSegmentMatch(scoped: String): SegmentMatch? {
        if (scoped.isEmpty()) return null

        val full = byPinyinKey.entriesWithKeyPrefixOf(scoped)
            .maxWithOrNull(compareBy<LexiconEntry> { it.pinyinKey.length }.thenBy { segmentScoreOf(it) })
            ?.let { SegmentMatch(it, it.pinyinKey, segmentScoreOf(it)) }

        val abbrevEntry = byInitials.entriesWithKeyPrefixOf(scoped)
            .maxWithOrNull(compareBy<LexiconEntry> { it.initials.length }.thenBy { segmentScoreOf(it) })
        val abbrev = abbrevEntry
            ?.takeUnless { it.initials.length <= 1 && scoped.length > 1 }
            ?.let {
                val singleLetter = it.initials.length <= 1
                val segmentScore = segmentScoreOf(it)
                val weighted = if (singleLetter) downweightSingleLetter(segmentScore) else segmentScore
                SegmentMatch(it, it.initials, weighted, isAbbreviation = true)
            }

        return when {
            full == null -> abbrev
            abbrev == null -> full
            abbrev.matchedKey.length > full.matchedKey.length -> abbrev
            else -> full // tie on consumed length: full pinyin is the more certain read
        }
    }

    /**
     * Adjacent-qwerty-key typo correction (PLAN M1.5 "邻键纠错"). Tries
     * substituting each position with its qwerty neighbors and re-running the
     * normal lookups on the result. Results stay below direct matches through a
     * score penalty instead of disappearing whenever the raw string is valid.
     * [Candidate.pinyinConsumed] is taken from the *original* (mistyped) buffer --
     * substitution never changes the buffer's length, so the original's prefix of
     * the same length is exactly what a pick must remove from `composing`.
     */
    private fun correctionCandidates(
        pinyin: String,
        shouldCancel: () -> Boolean = { false },
    ): List<Candidate> {
        if (pinyin.contains(PINYIN_SEPARATOR)) return emptyList()
        val results = LinkedHashMap<Pair<String, String>, Candidate>()
        for (pos in pinyin.indices) {
            if (shouldCancel()) return emptyList()
            val neighbors = QWERTY_NEIGHBORS[pinyin[pos]] ?: continue
            for (replacement in neighbors) {
                if (shouldCancel()) return emptyList()
                val variant = pinyin.substring(0, pos) + replacement + pinyin.substring(pos + 1)
                for (entry in byPinyinKey.entriesWithKeyPrefixOf(variant, shouldCancel, MATCH_LIMIT_PER_POSITION)) {
                    if (shouldCancel()) return emptyList()
                    val consumed = pinyin.substring(0, entry.pinyinKey.length)
                    val score = (scoreOf(entry) * CORRECTION_WEIGHT).roundToLong()
                    offer(
                        results,
                        Candidate(
                            word = entry.word,
                            pinyinConsumed = consumed,
                            score = score,
                            isCorrection = true,
                            canonicalPinyin = entry.pinyinKey,
                            source = CandidateSource.CONSERVATIVE_CORRECTION,
                            entryIds = listOf(entry.id),
                        ),
                    )
                }
                for (entry in byInitials.entriesWithKeyPrefixOf(variant, shouldCancel, MATCH_LIMIT_PER_POSITION)) {
                    if (shouldCancel()) return emptyList()
                    if (entry.initials.length <= 1) continue // too noisy stacked on top of a typo guess
                    val consumed = pinyin.substring(0, entry.initials.length)
                    val score = (scoreOf(entry) * CORRECTION_WEIGHT).roundToLong()
                    offer(
                        results,
                        Candidate(
                            word = entry.word,
                            pinyinConsumed = consumed,
                            score = score,
                            isCorrection = true,
                            canonicalPinyin = entry.pinyinKey,
                            isAbbreviation = true,
                            source = CandidateSource.CONSERVATIVE_CORRECTION,
                            entryIds = listOf(entry.id),
                        ),
                    )
                }
            }
        }
        return results.values.toList()
    }

    /**
     * Corrects one missing or extra vowel in a longer pinyin stream, then requires
     * the repaired stream to decode entirely through full-pinyin entries.  This is
     * intentionally not a broad spelling corrector: it avoids treating a normal
     * full-pinyin or 简拼 input as something that should be changed.
     */
    private fun sentenceCorrectionCandidates(
        pinyin: String,
        shouldCancel: () -> Boolean = { false },
    ): List<Candidate> {
        if (pinyin.contains(PINYIN_SEPARATOR) || pinyin.length !in MIN_SENTENCE_CORRECTION_LENGTH..MAX_SENTENCE_CORRECTION_LENGTH) {
            return emptyList()
        }

        val results = LinkedHashMap<Pair<String, String>, Candidate>()
        for (variant in singleVowelEditVariants(pinyin, shouldCancel)) {
            if (shouldCancel()) return emptyList()
            for (sentence in decodeSentences(variant, allowAbbreviations = false, shouldCancel = shouldCancel)) {
                if (shouldCancel()) return emptyList()
                offer(
                    results,
                    Candidate(
                        word = sentence.word,
                        // The candidate still consumes the user's original buffer;
                        // the inserted/deleted vowel only exists during lookup.
                        pinyinConsumed = pinyin,
                        score = sentence.score - SENTENCE_CORRECTION_PENALTY,
                        isSentence = true,
                        isCorrection = true,
                        canonicalPinyin = sentence.canonicalPinyin,
                        source = CandidateSource.CONSERVATIVE_CORRECTION,
                        entryIds = sentence.entryIds,
                    ),
                )
            }
        }
        return results.values.sortedByDescending { it.score }.take(SENTENCE_CANDIDATE_LIMIT)
    }

    /** At most one edit, limited to vowels and plausible insertion sites. */
    private fun singleVowelEditVariants(
        pinyin: String,
        shouldCancel: () -> Boolean = { false },
    ): Set<String> {
        val variants = LinkedHashSet<String>()
        for (pos in 1 until pinyin.length) {
            if (shouldCancel()) return emptySet()
            if (pinyin[pos - 1] in PINYIN_CONSONANTS) {
                for (vowel in PINYIN_VOWELS) {
                    variants += pinyin.substring(0, pos) + vowel + pinyin.substring(pos)
                }
            }
        }
        for (pos in pinyin.indices) {
            if (shouldCancel()) return emptySet()
            if (pinyin[pos] in PINYIN_VOWELS) {
                variants += pinyin.removeRange(pos, pos + 1)
            }
        }
        return variants
    }

    /** Finds every indexed entry whose key is itself a prefix of a query string. */
    private class PrefixTrie(private val keyOf: (LexiconEntry) -> String) {
        private class Node {
            val children = HashMap<Char, Node>()
            val entries = mutableListOf<LexiconEntry>()
        }

        private val root = Node()

        fun insert(entry: LexiconEntry, key: String) {
            if (key.isEmpty()) return
            var node = root
            for (c in key) node = node.children.getOrPut(c) { Node() }
            node.entries += entry
        }

        fun entriesWithKeyPrefixOf(
            query: String,
            shouldCancel: () -> Boolean = { false },
            limit: Int = Int.MAX_VALUE,
        ): List<LexiconEntry> {
            val result = mutableListOf<LexiconEntry>()
            var node = root
            for (c in query) {
                if (shouldCancel()) return emptyList()
                node = node.children[c] ?: break
                result += node.entries
            }
            if (result.size <= limit) return result

            // Preserve every exact terminal hit, then keep only the most
            // frequent prefix rows. This bounds single-letter initials (the
            // `x` bucket is large) without evicting exact xuan homophones.
            val exact = result.filter { keyOf(it) == query }
                .sortedWith(compareByDescending<LexiconEntry> { it.baseFreq }.thenBy { it.id })
            if (exact.size >= limit) return exact.take(limit)
            val remaining = result.filter { keyOf(it) != query }
                .sortedWith(compareByDescending<LexiconEntry> { it.baseFreq }.thenBy { it.id })
                .take(limit - exact.size)
            return exact + remaining
        }

        fun hasExactKey(query: String): Boolean {
            var node = root
            for (c in query) node = node.children[c] ?: return false
            return node.entries.any { keyOf(it) == query }
        }
    }

    private companion object {
        /** Standard Mandarin toneless syllables used to guard single-syllable ranking. */
        val PINYIN_SYLLABLES: Set<String> = """
            a ai an ang ao ba bai ban bang bao bei ben beng bi bian biao bie bin bing bo bu
            ca cai can cang cao ce cei cen ceng cha chai chan chang chao che chen cheng chi chong chou chu chua chuai chuan chuang chui chun chuo ci cong cou cu cua cuan cui cun cuo
            da dai dan dang dao de dei dei den deng di dian diao die ding diu dong dou du dua duan dui dun duo
            e ei en eng er fa fan fang fei fen feng fo fou fu ga gai gan gang gao ge gei gen geng gong gou gu gua guai guan guang gui gun guo
            ha hai han hang hao he hei hen heng hong hou hu hua huai huan huang hui hun huo
            ji jia jian jiang jiao jie jin jing jiong jiou jiu ju juan jue jun ka kai kan kang kao ke kei ken keng kong kou ku kua kuai kuan kuang kui kun kuo
            la lai lan lang lao le lei leng li lia lian liang liao lie lin ling liu long lou lu lua luan luo lv lve luan
            ma mai man mang mao me mei men meng mi mian miao mie min ming miu mo mou mu na nai nan nang nao ne nei nen neng ni nian niang niao nie nin ning niu nong nou nu nua nuan nuo nv nve
            o ou pa pai pan pang pao pei pen peng pi pian piao pie pin ping po pou pu qi qia qian qiang qiao qie qin qing qiong qiu qu quan que qun
            ra ran rang rao re rei ren reng ri rong rou ru rua ruan rui run ruo sa sai san sang sao se sei sen seng sha shai shan shang shao she shei shen sheng shi shou shu shua shuai shuan shuang shui shun shuo si song sou su sua suan sui sun suo
            ta tai tan tang tao te tei teng ti tian tiao tie ting tong tou tu tua tuan tui tun tuo wa wai wan wang wei wen weng wo wu xi xia xian xiang xiao xie xin xing xiong xiu xu xuan xue xun ya yai yan yang yao ye yi yin ying yo yong you yu yuan yue yun za zai zan zang zao ze zei zen zeng zha zhai zhan zhang zhao zhe zhei zhen zheng zhi zhong zhou zhu zhua zhuai zhuan zhuang zhui zhun zhuo zi zong zou zu zuan zui zun zuo
        """.trimIndent().split(Regex("\\s+")).toSet()
        const val SINGLE_LETTER_ABBREV_WEIGHT = 0.35
        const val CORRECTION_WEIGHT = 0.5
        const val SINGLE_CHARACTER_SEGMENT_PENALTY = 4_000L
        const val SENTENCE_CORRECTION_PENALTY = 1_200L
        const val MIN_SENTENCE_CORRECTION_LENGTH = 5
        const val MAX_SENTENCE_CORRECTION_LENGTH = 24
        const val PINYIN_VOWELS = "aeiouv"
        const val PINYIN_CONSONANTS = "bpmfdtnlgkhjqxrzcsyw"
        const val BASE_FREQUENCY_SCALE = 1_000.0
        const val SEGMENTATION_PENALTY = 350L
        const val EXTRA_SYLLABLE_PENALTY = 3_000L
        // A full pinyin stream must not be reinterpreted as a chain of short
        // initialisms merely because that chain happens to consume every letter.
        // This is deliberately much larger than the normal word-break penalty;
        // when a user really types 简拼 it remains available, but a viable full
        // path always wins the first candidate.
        const val ABBREVIATION_SEGMENT_PENALTY = 4_000L
        const val BEAM_WIDTH = 12
        const val MATCH_LIMIT_PER_POSITION = 24
        const val SENTENCE_CANDIDATE_LIMIT = 8
        const val DIRECT_CANDIDATE_LIMIT = 64
        const val MAX_REMEMBERED_PHRASES = 256
        const val MIN_USER_PHRASE_COMPONENTS = 2

        private fun computeLexiconVersion(entries: List<LexiconEntry>): String {
            val digest = MessageDigest.getInstance("SHA-256")
            for (entry in entries.sortedBy { it.id }) {
                digest.update(entry.id.toString().toByteArray(Charsets.UTF_8))
                digest.update(0.toByte())
                digest.update(entry.pinyinKey.toByteArray(Charsets.UTF_8))
                digest.update(0.toByte())
                digest.update(entry.initials.toByteArray(Charsets.UTF_8))
                digest.update(0.toByte())
                digest.update(entry.word.toByteArray(Charsets.UTF_8))
                digest.update(0.toByte())
            }
            return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        }

        /** Standard qwerty layout adjacency (self excluded), for 邻键纠错. */
        val QWERTY_NEIGHBORS: Map<Char, List<Char>> = mapOf(
            'q' to listOf('w', 'a'),
            'w' to listOf('q', 'e', 'a', 's'),
            'e' to listOf('w', 'r', 's', 'd'),
            'r' to listOf('e', 't', 'd', 'f'),
            't' to listOf('r', 'y', 'f', 'g'),
            'y' to listOf('t', 'u', 'g', 'h'),
            'u' to listOf('y', 'i', 'h', 'j'),
            'i' to listOf('u', 'o', 'j', 'k'),
            'o' to listOf('i', 'p', 'k', 'l'),
            'p' to listOf('o', 'l'),
            'a' to listOf('q', 'w', 's', 'z'),
            's' to listOf('a', 'w', 'e', 'd', 'z', 'x'),
            'd' to listOf('s', 'e', 'r', 'f', 'x', 'c'),
            'f' to listOf('d', 'r', 't', 'g', 'c', 'v'),
            'g' to listOf('f', 't', 'y', 'h', 'v', 'b'),
            'h' to listOf('g', 'y', 'u', 'j', 'b', 'n'),
            'j' to listOf('h', 'u', 'i', 'k', 'n', 'm'),
            'k' to listOf('j', 'i', 'o', 'l', 'm'),
            'l' to listOf('k', 'o', 'p'),
            'z' to listOf('a', 's', 'x'),
            'x' to listOf('z', 's', 'd', 'c'),
            'c' to listOf('x', 'd', 'f', 'v'),
            'v' to listOf('c', 'f', 'g', 'b'),
            'b' to listOf('v', 'g', 'h', 'n'),
            'n' to listOf('b', 'h', 'j', 'm'),
            'm' to listOf('n', 'j', 'k'),
        )
    }
}
