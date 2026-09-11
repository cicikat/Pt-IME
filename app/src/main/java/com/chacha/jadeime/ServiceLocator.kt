package com.chacha.jadeime

import android.content.Context
import android.util.Log
import com.chacha.jadeime.data.ClipboardRepository
import com.chacha.jadeime.data.DraftRepository
import com.chacha.jadeime.data.DraftSyncRepository
import com.chacha.jadeime.data.PhraseRepository
import com.chacha.jadeime.data.PinyinRepository
import com.chacha.jadeime.emoji.EmojiRepository
import com.chacha.jadeime.emoji.EmojiMappingRepository
import com.chacha.jadeime.engine.TrieLexiconEngine
import com.chacha.jadeime.layout.LayoutRepository
import com.chacha.jadeime.theme.ThemeRepository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "ServiceLocator"

/**
 * Hand-written service locator (CLAUDE.md: no Hilt for a single-module personal
 * project). Owns the process-lifetime pinyin engine: building a ~20万-entry Trie
 * isn't free, so it's kicked off once from [JadeApplication.onCreate] rather than
 * from the IME window (CLAUDE.md known pitfall: don't do heavy work in the IME).
 */
object ServiceLocator {
    // A fire-and-forget coroutine that throws with no handler installed takes
    // down the whole process (this bit us once: a Room schema mismatch loading
    // the lexicon crashed the IME's shared process on every launch). Chinese
    // input degrading to "no candidates" beats the app being unusable.
    private val exceptionHandler = CoroutineExceptionHandler { _, error ->
        Log.e(TAG, "background task failed, engine may be unavailable", error)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + exceptionHandler)
    private lateinit var repository: PinyinRepository
    lateinit var emojiRepository: EmojiRepository
        private set
    lateinit var emojiMappingRepository: EmojiMappingRepository
        private set
    lateinit var phraseRepository: PhraseRepository
        private set
    lateinit var clipboardRepository: ClipboardRepository
        private set
    lateinit var themeRepository: ThemeRepository
        private set
    lateinit var layoutRepository: LayoutRepository
        private set
    lateinit var symbolRepository: com.chacha.jadeime.ime.SymbolRepository
        private set
    lateinit var draftRepository: DraftRepository
        private set
    lateinit var draftPrivacy: com.chacha.jadeime.data.DraftPrivacySettings
        private set
    lateinit var draftSyncRepository: DraftSyncRepository
        private set

    private val _engine = MutableStateFlow<TrieLexiconEngine?>(null)
    val engine: StateFlow<TrieLexiconEngine?> = _engine.asStateFlow()
    private val _engineFailed = MutableStateFlow(false)
    val engineFailed: StateFlow<Boolean> = _engineFailed.asStateFlow()
    @Volatile private var engineLearningEnabled = true

    fun init(context: Context) {
        if (::repository.isInitialized) return
        repository = PinyinRepository(context.applicationContext)
        emojiRepository = EmojiRepository(context.applicationContext)
        emojiMappingRepository = EmojiMappingRepository(context.applicationContext)
        phraseRepository = PhraseRepository(context.applicationContext)
        clipboardRepository = ClipboardRepository(context.applicationContext)
        themeRepository = ThemeRepository(context.applicationContext)
        layoutRepository = LayoutRepository(context.applicationContext)
        symbolRepository = com.chacha.jadeime.ime.SymbolRepository(context.applicationContext)
        draftRepository = DraftRepository(context.applicationContext)
        draftPrivacy = com.chacha.jadeime.data.DraftPrivacySettings(context.applicationContext)
        draftSyncRepository = DraftSyncRepository(context.applicationContext)
        scope.launch(Dispatchers.IO) {
            for (write in draftWrites) runCatching { write() }
        }
        scope.launch(Dispatchers.IO) {
            while (true) {
                kotlinx.coroutines.delay(2_000)
                runCatching { draftSyncRepository.syncIfDue() }
            }
        }
        scope.launch(Dispatchers.IO) {
            val started = android.os.SystemClock.elapsedRealtime()
            try {
                _engine.value = repository.loadEngine().also {
                    it.setLearningEnabled(engineLearningEnabled)
                }
                Log.d(TAG, "engine ready elapsedMs=${android.os.SystemClock.elapsedRealtime() - started}")
            } catch (error: Exception) {
                _engineFailed.value = true
                Log.e(TAG, "failed to load pinyin engine", error)
            }
        }
    }

    /** Fire-and-forget clipboard write -- the OS clipboard-changed callback isn't a
     * suspend context, so this is the seam that hops onto a background dispatcher. */
    fun recordClipboard(content: String) {
        if (!::clipboardRepository.isInitialized) return
        scope.launch(Dispatchers.IO) {
            try {
                clipboardRepository.record(content)
            } catch (error: Exception) {
                Log.e(TAG, "failed to record clipboard entry", error)
            }
        }
    }

    private val draftSession = com.chacha.jadeime.data.DraftSession()
    private val draftWrites = kotlinx.coroutines.channels.Channel<suspend () -> Unit>(kotlinx.coroutines.channels.Channel.UNLIMITED)

    fun draftActivity(appPackage: String) { draftSession.activity(appPackage, android.os.SystemClock.elapsedRealtime()) }
    fun breakDraftSession() { draftSession.breakSession() }

    fun recordDraft(text: String, appPackage: String, source: String) {
        if (!::draftRepository.isInitialized) return
        if (!draftPrivacy.enabled) { breakDraftSession(); return }
        val session = draftSession.activity(appPackage, android.os.SystemClock.elapsedRealtime())
        val now = System.currentTimeMillis()
        val redacted = com.chacha.jadeime.data.DraftRedactor.redact(text)
        draftWrites.trySend { if (draftPrivacy.enabled) {
            draftRepository.record(redacted, appPackage, source, session, now)
            draftSyncRepository.noteEdit()
        } }
    }

    fun recordDraftEdit(kind: String, text: String, outcome: String, appPackage: String) {
        if (!::draftRepository.isInitialized || !draftPrivacy.enabled) return
        val session = draftSession.activity(appPackage, android.os.SystemClock.elapsedRealtime())
        val now = System.currentTimeMillis()
        val redacted = com.chacha.jadeime.data.DraftRedactor.redact(text)
        draftWrites.trySend { if (draftPrivacy.enabled) {
            draftRepository.recordEdit(kind, redacted, outcome, appPackage, session, now)
            draftSyncRepository.noteEdit()
        } }
    }

    suspend fun clearDrafts() {
        val done = kotlinx.coroutines.CompletableDeferred<Unit>()
        draftWrites.send {
            try { draftRepository.clear(); done.complete(Unit) }
            catch (error: Exception) { done.completeExceptionally(error) }
        }
        done.await()
    }

    /** Ends composing state; learned ranking remains in opaque, plaintext-free storage. */
    fun endEngineSession() {
        val current = _engine.value ?: return
        current.endSession()
    }

    /** Keeps password/email fields out of both ranking reads and memory writes. */
    fun setEngineLearningEnabled(enabled: Boolean) {
        engineLearningEnabled = enabled
        _engine.value?.setLearningEnabled(enabled)
    }
}
