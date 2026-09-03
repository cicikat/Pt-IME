package com.chacha.jadeime.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun entry(pinyinKey: String, initials: String, word: String, baseFreq: Long) =
    LexiconEntry(pinyinKey = pinyinKey, initials = initials, word = word, baseFreq = baseFreq)

/** Small fixture standing in for a slice of the real rime-ice lexicon.db. */
private val fixture = listOf(
    entry("ni", "n", "你", 1000),
    entry("hao", "h", "好", 900),
    entry("nihao", "nh", "你好", 5000),
    entry("wo", "w", "我", 2000),
    entry("men", "m", "们", 800),
    entry("women", "wm", "我们", 3000),
    entry("qu", "q", "去", 1500),
    entry("zhong", "z", "中", 1000),
    entry("guo", "g", "国", 900),
    entry("zhongguo", "zg", "中国", 4000),
    entry("cha", "c", "茶", 500),
    entry("cha", "c", "查", 2000),
)

private val shihuaiFixture = listOf(
    entry("shihuai", "sh", "释怀", 12_570),
    entry("shihuai", "sh", "使坏", 5_010),
    entry("shi", "s", "是", 31_422_712),
    entry("huai", "h", "坏", 500_000),
    entry("shehui", "sh", "社会", 500_000),
    entry("shi", "s", "释", 100_000),
    entry("huai", "h", "怀", 100_000),
)

private val xuanFixture = listOf(
    entry("xuan", "x", "选", 10_000),
    entry("xuan", "x", "玄", 9_000),
    entry("xuan", "x", "宣", 8_000),
    entry("xuan", "x", "旋", 7_000),
    entry("xuan", "x", "轩", 6_000),
    entry("xuan", "x", "悬", 5_000),
    entry("xuan", "x", "谖", 4_000),
    entry("xu", "x", "许", 100_000),
    entry("an", "a", "按", 100_000),
)

private val phraseFixture = listOf(
    entry("jue", "j", "角", 10_000),
    entry("se", "s", "色", 9_000),
    entry("jixu", "jx", "继续", 100_000),
)

private fun newEngine() = TrieLexiconEngine(staticEntries = fixture)

class TrieLexiconEngineTest {

    @Test
    fun `qwerty ue spelling reaches umlaut syllable without fragmenting into characters`() {
        val engine = TrieLexiconEngine(
            staticEntries = listOf(
                entry("lve", "l", "略", 301_716),
                entry("lve", "l", "掠", 81_961),
                entry("lu", "l", "路", 10_000_000),
                entry("e", "e", "饿", 1_000_000),
            ),
        )

        val candidates = engine.input("lue")

        assertEquals(listOf("略", "掠"), candidates.take(2).map { it.word })
        assertEquals("lue", candidates.first().pinyinConsumed)
        assertEquals("lve", candidates.first().canonicalPinyin)
        assertEquals("", engine.choose(candidates.first()).remainingPinyin)
        assertTrue(candidates.none { it.word == "路饿" })
        assertEquals(listOf("lue"), engine.previewSegmentation("lue"))
    }

    @Test
    fun `nue spelling reaches nve syllable`() {
        val engine = TrieLexiconEngine(
            staticEntries = listOf(
                entry("nve", "n", "虐", 30_741),
                entry("nu", "n", "怒", 1_000_000),
                entry("e", "e", "饿", 1_000_000),
            ),
        )

        assertEquals("虐", engine.input("nue").first().word)
    }

    @Test
    fun `legal single syllable exact candidates outrank split sentence`() {
        val candidates = TrieLexiconEngine(staticEntries = xuanFixture).input("xuan")

        assertEquals(listOf("选", "玄", "宣", "旋", "轩", "悬"), candidates.take(6).map { it.word })
        assertTrue(candidates.take(6).none { it.isSentence })
        assertTrue(candidates.none { it.word == "许按" })
    }

    @Test
    fun `new revision cancellation stops stale decode`() {
        val engine = TrieLexiconEngine(staticEntries = xuanFixture)
        var checks = 0

        val stale = engine.input("xuan") {
            checks += 1
            checks > 1
        }

        assertTrue(stale.isEmpty())
        assertTrue(checks > 1)
        assertEquals("选", engine.input("xuan").first().word)
    }

    @Test
    fun `full pinyin exact match wins by word length and score`() {
        val engine = newEngine()
        val candidates = engine.input("nihao")
        val top = candidates.first()
        assertEquals("你好", top.word)
        assertEquals("nihao", top.pinyinConsumed)
    }

    @Test
    fun `abbreviation lookup finds multi-char word by initials`() {
        val engine = newEngine()
        val candidates = engine.input("nh")
        assertTrue(candidates.any { it.word == "你好" && it.pinyinConsumed == "nh" })
    }

    @Test
    fun `exact multi-letter abbreviation is not buried by adjacent-key corrections`() {
        val engine = TrieLexiconEngine(
            staticEntries = listOf(
                entry("haode", "hd", "好的", 1_000),
                entry("he", "h", "和", 10_000_000),
                entry("haoxiang", "hx", "好像", 9_000_000),
                entry("jiandu", "jd", "监督", 8_000_000),
            ),
        )

        val candidates = engine.input("hd")

        assertEquals("好的", candidates.first().word)
        assertTrue(candidates.first().isAbbreviation)
        assertTrue(candidates.none { it.isCorrection })
    }

    @Test
    fun `single letter abbreviation surfaces but ranks below a full pinyin match`() {
        val engine = newEngine()
        // PLAN M1.5: 1-letter 简拼 used to be filtered out entirely (the root cause of
        // short 简拼 not producing anything); it must now surface, just downweighted.
        assertTrue(engine.input("n").any { it.word == "你" })
        val mixed = engine.input("ni")
        assertEquals("你", mixed.first().word) // full "ni" match still wins the top spot
    }

    @Test
    fun `mixed full pinyin and single-letter abbreviation segment without a manual separator`() {
        val engine = newEngine()
        // "ni" (full) + "h" (1-letter 简拼 for 好) typed back-to-back, no separator.
        val candidates = engine.input("nih")
        val sentence = candidates.firstOrNull { it.isSentence }
        assertEquals("你好", sentence?.word)
        assertEquals("nih", sentence?.pinyinConsumed)
    }

    @Test
    fun `full pinyin plus one initial and full pinyin suffix stays mixed input`() {
        val engine = TrieLexiconEngine(
            staticEntries = listOf(
                entry("yi", "y", "一", 10_000_000),
                entry("ju", "j", "句", 1_000_000),
                entry("wei", "w", "为", 5_000_000),
                // A high-frequency correction must not reinterpret the initial `j`.
                entry("ji", "j", "极", 20_000_000),
                entry("yin", "y", "因", 30_000_000),
            ),
        )

        val short = engine.input("yij")
        assertTrue(short.any { it.word == "一句" && it.isAbbreviation })
        assertTrue(short.none { it.isCorrection })

        val extended = engine.input("yijwei")
        assertTrue(extended.any { it.word == "一句为" && it.isAbbreviation })
        assertTrue(extended.none { it.isCorrection })
    }

    @Test
    fun `manual separator forces a segment boundary for abbreviation plus full pinyin`() {
        val engine = newEngine()
        val candidates = engine.input("n'hao")
        val sentence = candidates.firstOrNull { it.isSentence }
        assertEquals("你好", sentence?.word)
        assertEquals("n'hao", sentence?.pinyinConsumed)
    }

    @Test
    fun `a lone 1-letter abbreviation mid-buffer does not fragment segmentation into noise`() {
        val engine = newEngine()
        // "ma" has no dictionary entry in the fixture; the trailing "m" must NOT be
        // treated as a 1-letter 简拼 hit for 们 here, since two characters ("ma") are
        // still left to account for -- that would coin a bogus "你好们" sentence.
        val candidates = engine.input("nihaoma")
        assertTrue(candidates.none { it.isSentence && it.word == "你好们" })
    }

    @Test
    fun `previewSegmentation shows the auto-detected syllable boundaries`() {
        val engine = newEngine()
        // "nihao" is itself a dictionary entry, so it's the longest match, not "ni"+"hao";
        // "ma" has no entry and is left as an untranslated tail chunk.
        assertEquals(listOf("nihao", "ma"), engine.previewSegmentation("nihaoma"))
        assertEquals(listOf("ni", "h"), engine.previewSegmentation("nih"))
    }

    @Test
    fun `previewSegmentation passes through an existing manual separator as a boundary`() {
        val engine = newEngine()
        assertEquals(listOf("n", "hao"), engine.previewSegmentation("n'hao"))
    }

    @Test
    fun `adjacent-key typo is corrected via qwerty neighbor substitution`() {
        val engine = newEngine()
        // "wi" is a typo for "wo" (我) -- 'i' sits next to 'o' on qwerty.
        val candidates = engine.input("wi")
        val top = candidates.first()
        assertEquals("我", top.word)
        assertEquals("wi", top.pinyinConsumed)

        val result = engine.choose(top)
        assertEquals("", result.remainingPinyin)
    }

    @Test
    fun `correction does not fire when the raw buffer already has a confident match`() {
        val engine = newEngine()
        // "cha" matches directly (茶/查); correction must not inject noise on top.
        val words = engine.input("cha").map { it.word }.toSet()
        assertEquals(setOf("查", "茶"), words)
    }

    @Test
    fun `greedy segmentation concatenates the longest matches across the whole buffer`() {
        val engine = newEngine()
        val candidates = engine.input("womenqu")
        val sentence = candidates.firstOrNull { it.isSentence }
        assertEquals("我们去", sentence?.word)
        assertEquals("womenqu", sentence?.pinyinConsumed)
    }

    @Test
    fun `a single dictionary phrase does not produce a redundant sentence candidate`() {
        val engine = newEngine()
        val candidates = engine.input("zhongguo")
        assertFalse(candidates.any { it.isSentence })
        assertEquals("中国", candidates.first().word)
    }

    @Test
    fun `choose reports the unconsumed pinyin tail`() {
        val engine = newEngine()
        val candidates = engine.input("nihaoma")
        val nihao = candidates.first { it.word == "你好" }
        val result = engine.choose(nihao)
        assertEquals("ma", result.remainingPinyin)
    }

    @Test
    fun `base frequency ranks homophones without usage history`() {
        val engine = newEngine()
        val candidates = engine.input("cha")
        // Leading two are the full-pinyin ("cha") matches; a downweighted 1-letter
        // 简拼 ("c") duplicate of each now trails behind them (PLAN M1.5).
        val words = candidates.map { it.word }.take(2)
        assertEquals(listOf("查", "茶"), words)
    }

    @Test
    fun `one deliberate full-pinyin pick immediately moves candidate to first`() {
        val engine = newEngine()
        val cha = engine.input("cha").first { it.word == "茶" && it.pinyinConsumed == "cha" }
        engine.choose(cha)

        val reranked = engine.input("cha").map { it.word }.take(2)
        assertEquals(listOf("茶", "查"), reranked)
    }

    @Test
    fun `remembered candidate reached by initials is pinned to second`() {
        val engine = TrieLexiconEngine(staticEntries = shihuaiFixture)
        val shihuai = engine.input("shihuai").first { it.word == "使坏" }
        engine.choose(shihuai)

        val initials = engine.input("sh").map { it.word }
        assertEquals("使坏", initials[1])
    }

    @Test
    fun `exact full-pinyin phrases outrank high-frequency split sentences`() {
        val candidates = TrieLexiconEngine(staticEntries = shihuaiFixture).input("shihuai")

        assertEquals(listOf("释怀", "使坏"), candidates.take(2).map { it.word })
        assertTrue(candidates.drop(2).any { it.word == "是坏" && it.isSentence })
        assertFalse(candidates.first().isSentence)
    }

    @Test
    fun `consecutive static picks coin a user phrase`() {
        val engine = newEngine()
        val firstCha = engine.input("chacha").first { it.word == "茶" && it.pinyinConsumed == "cha" }
        engine.choose(firstCha)
        val secondCha = engine.input("cha").first { it.word == "茶" }
        engine.choose(secondCha)

        val phrase = engine.input("chacha").first { it.word == "茶茶" }
        assertEquals(CandidateSource.USER_PHRASE, phrase.source)
        assertFalse(phrase.isSentence)
    }

    @Test
    fun `ending a session keeps user ranking preference`() {
        val engine = newEngine()
        engine.choose(engine.input("cha").first { it.word == "茶" })
        assertEquals("茶", engine.input("cha").first().word)

        engine.endSession()

        assertEquals("茶", engine.input("cha").first().word)
    }

    @Test
    fun `opaque memory survives engine recreation without storing text`() {
        val updates = mutableListOf<CandidateMemoryV2>()
        val firstEngine = TrieLexiconEngine(staticEntries = shihuaiFixture, onMemoryChanged = updates::add)
        firstEngine.choose(firstEngine.input("shihuai").first { it.word == "使坏" })

        val stored = updates.single()
        assertEquals(1, stored.entryIds.size)
        assertTrue(stored.entryIds.first() > 0L)
        assertFalse(stored.lexiconVersion.contains("shihuai"))
        assertFalse(stored.lexiconVersion.contains("使坏"))

        val restored = TrieLexiconEngine(staticEntries = shihuaiFixture, initialMemory = listOf(stored))
        assertEquals("使坏", restored.input("shihuai").first().word)
    }

    @Test
    fun `角 and 色 become a phrase and abbreviation stays second`() {
        val updates = mutableListOf<CandidateMemoryV2>()
        val engine = TrieLexiconEngine(staticEntries = phraseFixture, onMemoryChanged = updates::add)

        engine.choose(engine.input("juese").first { it.word == "角" && it.pinyinConsumed == "jue" })
        engine.choose(engine.input("se").first { it.word == "色" && it.pinyinConsumed == "se" })

        assertEquals("角色", engine.input("juese").first().word)
        assertEquals(CandidateSource.USER_PHRASE, engine.input("juese").first().source)
        assertEquals("角色", engine.input("js")[1].word)

        val restored = TrieLexiconEngine(staticEntries = phraseFixture, initialMemory = updates)
        assertEquals("角色", restored.input("juese").first().word)
        assertEquals("角色", restored.input("js")[1].word)
    }

    @Test
    fun `sensitive field does not read or write v2 learning memory`() {
        val updates = mutableListOf<CandidateMemoryV2>()
        val engine = TrieLexiconEngine(staticEntries = phraseFixture, onMemoryChanged = updates::add)
        engine.setLearningEnabled(false)

        val leaf = engine.input("juese").first { it.word == "角" && it.pinyinConsumed == "jue" }
        engine.choose(leaf)
        engine.choose(engine.input("se").first { it.word == "色" && it.pinyinConsumed == "se" })

        assertTrue(updates.isEmpty())
        engine.setLearningEnabled(true)
        assertTrue(engine.input("juese").none { it.source == CandidateSource.USER_PHRASE })
    }

    @Test
    fun `beam decoding picks the best complete segmentation instead of the longest prefix`() {
        val engine = TrieLexiconEngine(
            staticEntries = listOf(
                entry("a", "a", "甲", 2_000),
                entry("bc", "bc", "乙", 2_000),
                entry("abc", "abc", "长", 2),
            ),
        )

        val top = engine.input("abc").first()
        assertEquals("甲乙", top.word)
        assertTrue(top.isSentence)
    }

    @Test
    fun `full pinyin sentence survives competing initialism paths`() {
        // Regression for zhe-shou-gan-zhen-bu-cuo being decoded as
        // zh + e + sh + ougan + zh + en + bucuo.  Before the fix, the beam was
        // pruned by accumulated (rather than average) score and could surface
        // “只会饿时候欧感只会嗯不错” before “这手感真不错”.
        val fullSentence = listOf(
            entry("zhe", "z", "这", 17_648_808),
            entry("shougan", "sg", "手感", 105_960),
            entry("zhenbucuo", "zbc", "真不错", 21_270),
        )
        val initialismDecoys = (1..12).map { index ->
            entry("zhihui$index", "zh", "只会$index", 931_805)
        }
        val engine = TrieLexiconEngine(
            staticEntries = fullSentence + initialismDecoys + listOf(
                entry("e", "e", "饿", 1_000_000),
                entry("shihou", "sh", "时候", 502_960),
                entry("ougan", "og", "欧感", 100_000),
                entry("en", "e", "嗯", 9_179_528),
                entry("bucuo", "bc", "不错", 500_601),
            ),
        )

        val top = engine.input("zheshouganzhenbucuo").first()

        assertEquals("这手感真不错", top.word)
        assertEquals("zheshouganzhenbucuo", top.pinyinConsumed)
        assertTrue(top.isSentence)
    }

    @Test
    fun `missing vowel repairs to a full pinyin sentence before initialism fallback`() {
        val engine = TrieLexiconEngine(
            staticEntries = listOf(
                entry("wo", "w", "我", 29_569_261),
                entry("juede", "jd", "觉得", 500_907),
                entry("keyi", "ky", "可以", 505_814),
                // The old fallback can consume `je` as an initialism, which is
                // locally valid but makes “我金额的可一” instead of the intent.
                entry("jine", "je", "金额", 500_000),
                entry("de", "d", "的", 30_000_000),
                entry("ke", "k", "可", 10_000_000),
                entry("yi", "y", "一", 30_000_000),
            ),
        )

        val top = engine.input("wojedekeyi").first()

        assertEquals("我觉得可以", top.word)
        assertEquals("wojedekeyi", top.pinyinConsumed)
        assertTrue(top.isSentence)
        assertTrue(top.isCorrection)
        assertEquals("", engine.choose(top).remainingPinyin)
    }

    @Test
    fun `extra vowel repairs to a full pinyin sentence`() {
        val engine = TrieLexiconEngine(
            staticEntries = listOf(
                entry("wo", "w", "我", 29_569_261),
                entry("juede", "jd", "觉得", 500_907),
                entry("keyi", "ky", "可以", 505_814),
            ),
        )

        val top = engine.input("wojueedekeyi").first()

        assertEquals("我觉得可以", top.word)
        assertTrue(top.isCorrection)
    }

    @Test
    fun `complete full pinyin is never marked as a correction`() {
        val engine = TrieLexiconEngine(
            staticEntries = listOf(
                entry("wo", "w", "我", 29_569_261),
                entry("juede", "jd", "觉得", 500_907),
                entry("keyi", "ky", "可以", 505_814),
            ),
        )

        val top = engine.input("wojuedekeyi").first()

        assertEquals("我觉得可以", top.word)
        assertFalse(top.isCorrection)
    }

    @Test
    fun `dictionary phrase outranks a split into high frequency single characters`() {
        val engine = TrieLexiconEngine(
            staticEntries = listOf(
                entry("ke", "k", "可", 10_000_000),
                entry("yi", "y", "一", 30_000_000),
                entry("keyi", "ky", "可以", 505_814),
            ),
        )

        val top = engine.input("keyi").first()

        assertEquals("可以", top.word)
    }

    @Test
    fun `empty input yields no candidates`() {
        val engine = newEngine()
        assertTrue(engine.input("").isEmpty())
    }

    @Test
    fun `unmatched input yields no candidates instead of throwing`() {
        val engine = newEngine()
        assertNull(engine.input("xyz123").firstOrNull())
    }
}
