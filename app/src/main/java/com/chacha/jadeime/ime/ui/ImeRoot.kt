package com.chacha.jadeime.ime.ui

import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import com.chacha.jadeime.data.ClipboardEntryRow
import com.chacha.jadeime.data.ClipboardRepository
import com.chacha.jadeime.data.PhraseRepository
import com.chacha.jadeime.emoji.EmojiCatalog
import com.chacha.jadeime.emoji.EmojiRepository
import com.chacha.jadeime.emoji.EmojiMappingRepository
import com.chacha.jadeime.engine.Candidate
import com.chacha.jadeime.engine.PINYIN_SEPARATOR
import com.chacha.jadeime.engine.PinyinEngine
import com.chacha.jadeime.ime.FieldConstraint
import com.chacha.jadeime.layout.LayoutRepository
import com.chacha.jadeime.phrase.KaomojiCatalog
import com.chacha.jadeime.theme.BuiltInThemes
import com.chacha.jadeime.theme.JadeTheme
import com.chacha.jadeime.theme.LocalJadeTheme
import com.chacha.jadeime.theme.ThemeRepository
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

internal enum class KeyboardPage { Letters, Numeric, Symbols }
private enum class ShiftState { Off, Locked }
internal enum class InputMode { Chinese, English }
private const val TAG = "JadeIme"
private const val SLOW_CANDIDATE_MS = 32L

// Only the classic full-stop punctuation set converts; symbols/currency stay ASCII,
// matching common Chinese IME convention (PLAN M1 "全半角标点映射").
private val CHINESE_PUNCTUATION = mapOf(
    "," to "，", "." to "。", "!" to "！", "?" to "？",
    ";" to "；", ":" to "：", "(" to "（", ")" to "）",
)

// Long-press alternatives follow the same convention as the main Chinese layout:
// use full-width Chinese punctuation when one exists, while symbols such as @/#
// remain ASCII because Chinese has no distinct equivalent.
private val CHINESE_LONG_PRESS_PUNCTUATION = mapOf(
    "," to "，", "." to "。", "!" to "！", "?" to "？",
    ";" to "；", ":" to "：", "(" to "（", ")" to "）",
    "[" to "【", "]" to "】", "{" to "｛", "}" to "｝",
    "<" to "＜", ">" to "＞",
    "-" to "－", "_" to "＿", "/" to "／", "\\" to "＼",
)

private val ENGLISH_LONG_PRESS_PUNCTUATION = mapOf(
    "“" to "\"", "”" to "\"", "‘" to "'", "’" to "'",
)

@Composable
@OptIn(ExperimentalCoroutinesApi::class)
internal fun ImeRoot(
    enterLabel: String,
    engine: PinyinEngine?,
    engineFailed: Boolean = false,
    onCommitText: (String) -> Unit,
    onPasteText: (String) -> Unit = onCommitText,
    onDeleteBackward: () -> Unit,
    onDeleteLongPress: () -> Unit = onDeleteBackward,
    onEnter: () -> Unit,
    // Positive = move right, negative = move left; called once per character step
    // (PLAN M2 "空格滑动移动光标"). Only wired up when there's no pending pinyin
    // composition, since that lives purely in the keyboard's own UI state and moving
    // the *app's* text cursor underneath it would be a confusing pairing.
    onMoveCursor: (Int) -> Unit,
    onOpenSkins: () -> Unit,
    onOpenMic: (InputMode) -> Unit,
    voiceState: VoiceUiState? = null,
    onStopVoice: () -> Unit = {},
    onCancelVoice: () -> Unit = {},
    onKeyboardActivity: () -> Unit = {},
    onOpenSettings: () -> Unit,
    onCollapseKeyboard: () -> Unit,
    // M2's emoji/kaomoji/phrase panel lives entirely inside ImeRoot (unlike the M5/M4
    // stubs, which still just Toast via the service) -- null (e.g. in @Preview) just
    // means an always-empty "最近"/"短语" tab, everything else still works.
    emojiRepository: EmojiRepository? = null,
    emojiMappingRepository: EmojiMappingRepository? = null,
    phraseRepository: PhraseRepository? = null,
    clipboardRepository: ClipboardRepository? = null,
    // PLAN M5 "设置页主题列表 + 预览 + 热切换"; null (e.g. in @Preview) just falls
    // back to the system light/dark built-ins.
    themeRepository: ThemeRepository? = null,
    // PLAN M5 "布局也 JSON 化"; null (e.g. in @Preview) always uses the built-in rows.
    layoutRepository: LayoutRepository? = null,
    symbolRepository: com.chacha.jadeime.ime.SymbolRepository? = null,
    // PLAN M2 "数字/URL/密码 inputType 自动切布局": JadeImeService derives this from the
    // focused field's EditorInfo. fieldGeneration bumps on every onStartInputView so
    // the effect below re-fires even when two consecutive fields share a constraint.
    fieldConstraint: FieldConstraint = FieldConstraint.None,
    fieldGeneration: Int = 0,
    // Only overridden by @Preview composables below to show off other states --
    // real usage (JadeImeService) always starts from the Chinese letters page.
    initialMode: InputMode = InputMode.Chinese,
    initialPage: KeyboardPage = KeyboardPage.Letters,
) {
    var page by rememberSaveable { mutableStateOf(initialPage) }
    // The toolbar symbols control is a true toggle: remember the page it replaced
    // so a second tap returns the user exactly to the prior keyboard.
    var pageBeforeSymbols by rememberSaveable { mutableStateOf(initialPage) }
    var shift by rememberSaveable { mutableStateOf(ShiftState.Off) }
    var mode by rememberSaveable { mutableStateOf(initialMode) }
    LaunchedEffect(fieldGeneration) {
        when (fieldConstraint) {
            FieldConstraint.ForceEnglish -> mode = InputMode.English
            FieldConstraint.ForceEnglishNumeric -> {
                mode = InputMode.English
                page = KeyboardPage.Symbols
            }
            FieldConstraint.None -> Unit
        }
    }
    var composingPinyin by rememberSaveable { mutableStateOf("") }
    var candidates by remember { mutableStateOf<List<Candidate>>(emptyList()) }
    var candidatesExpanded by remember { mutableStateOf(false) }
    var showEmojiPanel by remember { mutableStateOf(false) }
    var emojiRecent by remember { mutableStateOf(emojiRepository?.getRecent().orEmpty()) }
    var phrases by remember { mutableStateOf<List<String>>(emptyList()) }
    var clipboardEntries by remember { mutableStateOf<List<ClipboardEntryRow>>(emptyList()) }
    val scope = rememberCoroutineScope()
    // Trie decoding has to remain serialized because PinyinEngine keeps the current
    // composing buffer for choose(). More importantly, it must never run on the
    // IME's Compose/UI thread: input-method windows are especially easy for the
    // system to cancel when they miss several frames.
    val candidateDispatcher = remember { Dispatchers.Default.limitedParallelism(1) }
    var compositionRevision by remember { mutableStateOf(0L) }
    // Keep the previous frame while decoding, but only the matching revision is actionable.
    var candidateRevision by remember { mutableStateOf(-1L) }
    // A serial dispatcher does not remove stale work from its queue. Keep the
    // latest Job so fast typing cancels queued and cooperative in-flight work.
    var candidateWorkJob by remember { mutableStateOf<Job?>(null) }

    suspend fun refreshClipboard() {
        clipboardEntries = clipboardRepository?.getAll().orEmpty()
    }

    // Custom phrases and clipboard history are only ever edited from the Settings
    // screen or an OS-level copy (not this composition) -- refetch each time the
    // panel opens instead of trying to keep a live subscription across
    // process/Activity boundaries for lists that change rarely.
    LaunchedEffect(showEmojiPanel, phraseRepository, clipboardRepository) {
        if (showEmojiPanel) {
            phrases = phraseRepository?.getAll()?.map { it.content }.orEmpty()
            refreshClipboard()
        }
    }
    // PLAN M5 "热切换": selectedId is a StateFlow, so a theme picked in Settings
    // reaches an already-open keyboard window immediately, no reopen needed.
    val selectedThemeId = themeRepository?.selectedId?.collectAsState()?.value
    val appearance = themeRepository?.appearance?.collectAsState()?.value
    val themeRevision = themeRepository?.revision?.collectAsState()?.value
    val isDark = isSystemInDarkTheme()
    val fallbackTheme = if (isDark) BuiltInThemes.dark else BuiltInThemes.light
    val theme by produceState(fallbackTheme, selectedThemeId, isDark, themeRepository, appearance, themeRevision) {
        value = withContext(Dispatchers.IO) { themeRepository?.resolveActive(isDark) ?: fallbackTheme }
    }
    // The previous fixed 252dp surface left the four letter rows feeling cramped,
    // especially once the candidate row was visible. Keep the value theme-driven
    // so compact layouts remain possible without changing the gesture geometry.
    val keyboardHeight = (268f * theme.keyboardHeightScale.coerceIn(0.85f, 1.25f)).dp
    // PLAN M5 "布局也 JSON 化": filesDir/layouts/*.json overrides the built-in rows
    // when present; layoutRepository null (e.g. @Preview) just means "always default".
    // produceState retains its previous value when a key changes. A single state
    // keyed by page would therefore draw the old page's keys for one frame.
    // Keep each layout in its own slot and select synchronously during composition.
    var letterRows by remember(layoutRepository) { mutableStateOf(KeyboardLayouts.letters) }
    var numericRows by remember(layoutRepository) { mutableStateOf(KeyboardLayouts.numeric) }
    LaunchedEffect(layoutRepository) {
        letterRows = withContext(Dispatchers.IO) { layoutRepository?.loadLetters() ?: KeyboardLayouts.letters }
        numericRows = withContext(Dispatchers.IO) { layoutRepository?.loadNumeric() ?: KeyboardLayouts.numeric }
    }
    val rows = when (page) {
        KeyboardPage.Letters -> letterRows
        KeyboardPage.Numeric -> numericRows
        // The symbol page is rendered by SymbolsPanel, not these legacy key rows.
        KeyboardPage.Symbols -> emptyList()
    }

    // Pinyin never leaves the keyboard's own UI anymore (PLAN M1.6 "composing 拼音移入键盘内") --
    // only a chosen word ever reaches the app via onCommitText.
    var composingCursor by rememberSaveable { mutableStateOf(0) }

    fun updateComposing(pinyin: String, cursor: Int = pinyin.length) {
        candidateWorkJob?.cancel()
        composingPinyin = pinyin
        composingCursor = cursor.coerceIn(0, pinyin.length)
        val revision = ++compositionRevision
        candidateRevision = -1L
        if (pinyin.isEmpty()) {
            candidates = emptyList()
            candidatesExpanded = false
            candidateWorkJob = scope.launch(candidateDispatcher) { engine?.reset() }
            return
        }
        val activeEngine = engine ?: run {
            return
        }
        candidateWorkJob = scope.launch(candidateDispatcher) {
            val startedAt = SystemClock.elapsedRealtime()
            val context = currentCoroutineContext()
            val job = context[Job]
            val mapped = emojiMappingRepository?.all()?.get(pinyin.lowercase())
            val nextCandidates = buildList {
                if (mapped != null) add(Candidate(mapped, pinyin, Long.MAX_VALUE, canonicalPinyin = pinyin))
                addAll(activeEngine.input(pinyin) { job?.isActive != true })
            }
            val cancelled = job?.isActive != true
            val elapsedMs = SystemClock.elapsedRealtime() - startedAt
            if (elapsedMs >= SLOW_CANDIDATE_MS) {
                // Timing only: revision/count/cancel are safe; pinyin and
                // candidate text must never enter logs.
                Log.d(
                    TAG,
                    "candidate decode revision=$revision candidates=${nextCandidates.size} " +
                        "elapsedMs=$elapsedMs cancelled=$cancelled",
                )
            }
            withContext(Dispatchers.Main.immediate) {
                // A later keystroke, deletion, or mode change already superseded
                // this result. Showing it would make candidate taps act on stale text.
                if (!cancelled && revision == compositionRevision && pinyin == composingPinyin) {
                    candidates = nextCandidates
                    candidateRevision = revision
                }
            }
        }
    }

    LaunchedEffect(engine) {
        // Input typed during cold loading must be decoded without another keypress.
        if (engine != null && composingPinyin.isNotEmpty()) updateComposing(composingPinyin, composingCursor)
    }

    LaunchedEffect(fieldGeneration) { updateComposing("") }

    fun insertIntoComposing(text: String) {
        updateComposing(
            composingPinyin.substring(0, composingCursor) + text + composingPinyin.substring(composingCursor),
            composingCursor + text.length,
        )
    }

    // Abandons whatever pinyin is mid-composing by committing it verbatim as
    // Latin letters, per PLAN M1 "中文态下直接敲出的英文字母流原样上屏".
    fun flushComposingAsLiteral() {
        if (composingPinyin.isEmpty()) return
        candidateWorkJob?.cancel()
        onCommitText(composingPinyin)
        composingPinyin = ""
        composingCursor = 0
        candidates = emptyList()
        candidateRevision = -1L
        candidatesExpanded = false
        compositionRevision++
        candidateWorkJob = scope.launch(candidateDispatcher) { engine?.reset() }
    }

    fun chooseCandidate(candidate: Candidate) {
        if (candidateRevision != compositionRevision) return
        val activeEngine = engine ?: return
        candidateWorkJob?.cancel()
        val pinyinAtPick = composingPinyin
        val revisionAtPick = compositionRevision
        candidates = emptyList()
        candidateRevision = -1L
        candidatesExpanded = false
        candidateWorkJob = scope.launch(candidateDispatcher) {
            val result = activeEngine.choose(candidate)
            withContext(Dispatchers.Main.immediate) {
                // Candidate taps are valid only for the exact buffer that produced
                // the visible row. Ignore a late tap/result after newer typing.
                if (revisionAtPick != compositionRevision || pinyinAtPick != composingPinyin) return@withContext
                onCommitText(candidate.word)
                updateComposing(result.remainingPinyin)
            }
        }
    }

    /**
     * Resolve a composing buffer off the UI thread, then run an explicit action
     * (space/punctuation) after the chosen text. This also covers a fast key
     * sequence where the candidate result has not reached Compose yet.
     */
    fun commitComposingForAction(afterCommit: () -> Unit) {
        val pinyinAtAction = composingPinyin
        if (pinyinAtAction.isEmpty()) {
            afterCommit()
            return
        }
        val activeEngine = engine
        val revisionAtAction = compositionRevision
        candidateWorkJob?.cancel()
        candidates = emptyList()
        candidateRevision = -1L
        candidatesExpanded = false
        if (activeEngine == null) {
            flushComposingAsLiteral()
            afterCommit()
            return
        }
        candidateWorkJob = scope.launch(candidateDispatcher) {
            val leading = activeEngine.input(pinyinAtAction).firstOrNull()
            val remaining = leading?.let { activeEngine.choose(it).remainingPinyin }.orEmpty()
            withContext(Dispatchers.Main.immediate) {
                if (revisionAtAction != compositionRevision || pinyinAtAction != composingPinyin) return@withContext
                onCommitText(leading?.word ?: pinyinAtAction)
                if (remaining.isNotEmpty()) onCommitText(remaining)
                composingPinyin = ""
                composingCursor = 0
                candidates = emptyList()
                candidateRevision = -1L
                candidatesExpanded = false
                compositionRevision++
                candidateWorkJob = scope.launch(candidateDispatcher) { activeEngine.reset() }
                afterCommit()
            }
        }
    }

    fun switchToEnglish() {
        flushComposingAsLiteral()
        mode = InputMode.English
        page = KeyboardPage.Letters
        shift = ShiftState.Off
    }

    fun switchToChinese() {
        mode = InputMode.Chinese
        page = KeyboardPage.Letters
        shift = ShiftState.Off
    }

    fun toggleSymbolsPage() {
        if (page == KeyboardPage.Symbols) {
            page = KeyboardPage.Letters
        } else {
            pageBeforeSymbols = page
            page = KeyboardPage.Symbols
            shift = ShiftState.Off
        }
    }

    val keyboardFont = com.chacha.jadeime.theme.rememberKeyboardFont(theme.fontPath)
    CompositionLocalProvider(LocalJadeTheme provides theme) {
    MaterialTheme {
      androidx.compose.material3.ProvideTextStyle(androidx.compose.material3.LocalTextStyle.current.copy(fontFamily = keyboardFont)) {
        Box(modifier = Modifier.fillMaxWidth().background(theme.background).pointerInput(onKeyboardActivity) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                onKeyboardActivity()
                do {
                    val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                    if (event.changes.none { it.pressed }) break
                } while (true)
            }
        }) {
            val backgroundPath = theme.bgImage
            if (backgroundPath != null) {
                com.chacha.jadeime.theme.KeyboardBackground(
                    path = backgroundPath,
                    blur = theme.bgBlur,
                    crop = theme.backgroundCrop,
                    dim = theme.bgDim,
                    modifier = Modifier.fillMaxWidth().height(keyboardHeight),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(keyboardHeight)
                    .background(if (theme.bgImage == null) theme.background else Color.Transparent)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(theme.keyGap),
            ) {
              if (voiceState != null) {
                VoicePanel(voiceState, theme, onStopVoice, onCancelVoice, onOpenSettings)
              } else {
                if (!showEmojiPanel && page != KeyboardPage.Symbols && mode == InputMode.Chinese && composingPinyin.isNotEmpty()) {
                    ComposingBar(
                        // Keep composing feedback strictly local and O(1). Calling
                        // previewSegmentation() here walked the large Trie again on
                        // every Compose recomposition, which made the IME visibly
                        // stall after only a couple of keys on a real phone.
                        display = composingPinyin,
                        rawPinyin = composingPinyin,
                        cursor = composingCursor,
                        candidates = candidates,
                        expanded = candidatesExpanded,
                        candidatesReady = candidateRevision == compositionRevision,
                        status = if (engineFailed) "词库加载失败，请重启输入法" else if (engine == null) "词库加载中…" else "正在更新…",
                        onPick = ::chooseCandidate,
                        onToggleExpand = { candidatesExpanded = !candidatesExpanded },
                        onMoveCursor = { composingCursor = it.coerceIn(0, composingPinyin.length) },
                        theme = theme,
                        modifier = Modifier.height(44.dp),
                    )
                } else {
                    ToolbarRow(
                        theme = theme,
                        onOpenSymbols = { if (showEmojiPanel) { showEmojiPanel = false; page = KeyboardPage.Symbols } else toggleSymbolsPage() },
                        onOpenSkins = onOpenSkins,
                        onOpenEmoji = {
                            emojiRecent = emojiRepository?.getRecent().orEmpty()
                            showEmojiPanel = !showEmojiPanel
                            if (!showEmojiPanel) page = KeyboardPage.Letters
                        },
                        symbolsSelected = page == KeyboardPage.Symbols && !showEmojiPanel,
                        emojiSelected = showEmojiPanel,
                        onOpenSettings = onOpenSettings,
                        onCollapse = onCollapseKeyboard,
                        modifier = Modifier.height(44.dp),
                    )
                }
                if (showEmojiPanel) {
                EmojiPanel(
                    categories = EmojiCatalog.categories,
                    recent = emojiRecent,
                    kaomojis = KaomojiCatalog.items,
                    phrases = phrases,
                    theme = theme,
                    onPick = { picked, isEmoji ->
                        // A pending pinyin composition has nothing to do with an emoji/
                        // kaomoji/phrase pick, so it's abandoned as literal Latin first,
                        // same as any other "leave the pinyin state" action.
                        flushComposingAsLiteral()
                        onCommitText(picked)
                        // Only single-glyph emoji get MRU tracking -- kaomoji are a fixed
                        // catalog and phrases already live in a user-curated short list,
                        // neither needs a second "recent" ranking on top.
                        if (isEmoji) {
                            emojiRepository?.recordUsage(picked)
                        }
                    },
                    clipboardEntries = clipboardEntries,
                    onPasteClipboard = { content ->
                        flushComposingAsLiteral()
                        onPasteText(content)
                    },
                    onToggleClipboardPin = { row ->
                        scope.launch {
                            clipboardRepository?.togglePin(row)
                            refreshClipboard()
                        }
                    },
                    onDeleteClipboardEntry = { row ->
                        scope.launch {
                            clipboardRepository?.delete(row)
                            refreshClipboard()
                        }
                    },
                    onClearClipboard = {
                        scope.launch {
                            clipboardRepository?.clearUnpinned()
                            refreshClipboard()
                        }
                    },
                    onDelete = onDeleteBackward,
                    modifier = Modifier.weight(1f),
                )
                } else if (page == KeyboardPage.Symbols) {
                    SymbolsPanel(theme = theme, onPick = { symbol -> flushComposingAsLiteral(); onCommitText(symbol) }, onDelete = onDeleteBackward, modifier = Modifier.weight(1f), repository = symbolRepository)
                } else rows.forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(theme.keyGap),
                    ) {
                        row.forEach { key ->
                            val label = key.displayLabel(shift, enterLabel, mode)
                            JadeKey(
                                key = key,
                                label = label,
                                active = key.action == KeyAction.Shift && shift != ShiftState.Off,
                                shiftLocked = key.action == KeyAction.Shift && shift == ShiftState.Locked,
                                theme = theme,
                                modifier = Modifier.weight(key.weight),
                                onActivate = {
                                    when (key.action) {
                                        KeyAction.Text -> {
                                            val raw = requireNotNull(key.value)
                                            when {
                                                mode == InputMode.Chinese &&
                                                    page == KeyboardPage.Letters &&
                                                    raw.all(Char::isLetter) -> {
                                                    insertIntoComposing(raw)
                                                }
                                                // Full-width punctuation only in Chinese mode's
                                                // letters page with shift off -- numeric/symbols
                                                // page and an active shift both fall through to
                                                // plain ASCII (PLAN M1.7 "标点全半角跟模式走").
                                                mode == InputMode.Chinese &&
                                                    page == KeyboardPage.Letters &&
                                                    shift == ShiftState.Off -> {
                                                    commitComposingForAction {
                                                        onCommitText(CHINESE_PUNCTUATION[raw] ?: raw)
                                                    }
                                                }
                                                else -> {
                                                    val output = if (
                                                        page == KeyboardPage.Letters &&
                                                        shift != ShiftState.Off
                                                    ) {
                                                        raw.uppercase(Locale.ROOT)
                                                    } else {
                                                        raw
                                                    }
                                                    onCommitText(output)
                                                }
                                            }
                                        }
                                        KeyAction.Shift -> {
                                            if (mode == InputMode.Chinese) {
                                                switchToEnglish()
                                            } else {
                                                switchToChinese()
                                            }
                                        }
                                        KeyAction.Backspace -> {
                                            if (composingPinyin.isNotEmpty()) {
                                                if (composingCursor > 0) {
                                                    updateComposing(
                                                        composingPinyin.removeRange(composingCursor - 1, composingCursor),
                                                        composingCursor - 1,
                                                    )
                                                }
                                            } else {
                                                onDeleteBackward()
                                            }
                                        }
                                        KeyAction.Space -> {
                                            when {
                                                mode == InputMode.Chinese && composingPinyin.isNotEmpty() -> {
                                                    commitComposingForAction { onCommitText(" ") }
                                                }
                                                else -> onCommitText(" ")
                                            }
                                        }
                                        KeyAction.Enter -> {
                                            // A pinyin buffer is committed verbatim as an escape
                                            // hatch. It does not select the first candidate or
                                            // submit a Done/newline action.
                                            if (composingPinyin.isNotEmpty()) flushComposingAsLiteral() else onEnter()
                                        }
                                        KeyAction.Symbols -> {
                                            flushComposingAsLiteral()
                                            pageBeforeSymbols = page
                                            page = KeyboardPage.Symbols
                                            shift = ShiftState.Off
                                        }
                                        KeyAction.Numeric -> {
                                            flushComposingAsLiteral()
                                            page = KeyboardPage.Numeric
                                            shift = ShiftState.Off
                                        }
                                        KeyAction.Letters -> {
                                            flushComposingAsLiteral()
                                            page = KeyboardPage.Letters
                                        }
                                        KeyAction.LangToggle -> Unit
                                        KeyAction.Separator -> {
                                            if (mode == InputMode.Chinese && composingPinyin.isNotEmpty()) {
                                                insertIntoComposing(PINYIN_SEPARATOR.toString())
                                            } else {
                                                onCommitText("'")
                                            }
                                        }
                                        KeyAction.Spacer -> Unit
                                    }
                                },
                                onDoubleTap = null,
                                onLongPress = if (key.action == KeyAction.Shift) {
                                    {
                                        if (mode == InputMode.English) {
                                            shift = if (shift == ShiftState.Locked) ShiftState.Off else ShiftState.Locked
                                        }
                                    }
                                } else if (key.action == KeyAction.Backspace) {
                                    onDeleteLongPress
                                } else {
                                    null
                                },
                                onLongPressAlt = { corner ->
                                    flushComposingAsLiteral()
                                    val output = if (mode == InputMode.Chinese) {
                                        when (corner) {
                                            "“" -> "“”"
                                            "‘" -> "‘’"
                                            else -> CHINESE_LONG_PRESS_PUNCTUATION[corner] ?: corner
                                        }
                                    } else {
                                        ENGLISH_LONG_PRESS_PUNCTUATION[corner] ?: corner
                                    }
                                    onCommitText(output)
                                },
                                // Long-press space opens the in-keyboard dictation panel.
                                onSpaceLongPress = if (key.action == KeyAction.Space) ({ flushComposingAsLiteral(); onOpenMic(mode) }) else null,
                                onMoveCursor = if (
                                    key.action == KeyAction.Space &&
                                    !(mode == InputMode.Chinese && composingPinyin.isNotEmpty())
                                ) {
                                    onMoveCursor
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }
              }
            }

            // Rendered here (a sibling drawn *after* the Column, not nested inside
            // ComposingBar) so it paints on top of the keyboard rows below it --
            // Compose has no clipping/z-index across separate subtrees, only draw
            // order, and the rows compose after ComposingBar inside the Column.
            if (voiceState == null && !showEmojiPanel && page != KeyboardPage.Symbols && mode == InputMode.Chinese && candidatesExpanded && composingPinyin.isNotEmpty()) {
                ExpandedCandidatesPanel(
                    candidates = candidates,
                    enabled = candidateRevision == compositionRevision,
                    onPick = ::chooseCandidate,
                    theme = theme,
                )
            }

        }
      }
    }
    }
}

@Composable
private fun CandidateBar(
    candidates: List<Candidate>,
    enabled: Boolean,
    onPick: (Candidate) -> Unit,
    theme: JadeTheme,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) {
        itemsIndexed(candidates) { index, candidate ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .then(if (enabled) Modifier.pointerInput(candidate) {
                        detectTapGestures(onTap = { onPick(candidate) })
                    } else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (candidate.isCorrection) "纠 ${candidate.word}" else candidate.word,
                    fontSize = 18.sp,
                    fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                    color = theme.text,
                )
            }
        }
    }
}

@Composable
private fun ComposingBar(
    display: String,
    rawPinyin: String,
    cursor: Int,
    candidates: List<Candidate>,
    expanded: Boolean,
    candidatesReady: Boolean,
    status: String,
    onPick: (Candidate) -> Unit,
    onToggleExpand: () -> Unit,
    onMoveCursor: (Int) -> Unit,
    theme: JadeTheme,
    modifier: Modifier = Modifier,
) {
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val displayCursor = displayOffsetForRawCursor(display, rawPinyin, cursor)
    // Text's layout callback arrives one frame behind a newly typed character.
    // During that frame [displayCursor] belongs to the new string while
    // [textLayout] still belongs to the old one; getCursorRect() throws when the
    // offset exceeds the old text length and crashes the whole IME process.
    val cursorRect = textLayout?.let { layout ->
        layout.getCursorRect(displayCursor.coerceIn(0, layout.layoutInput.text.length))
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Pinyin "小块" pinned to the keyboard's own top-left corner (PLAN M1.6) --
        // this is purely local UI state, never pushed into the app's text field.
        Surface(
            modifier = Modifier.padding(start = 6.dp, end = 4.dp),
            color = theme.keySpecial,
            shape = RoundedCornerShape(6.dp),
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = display,
                    fontSize = 13.sp,
                    color = theme.text.copy(alpha = 0.8f),
                    onTextLayout = { textLayout = it },
                    modifier = Modifier.pointerInput(display, rawPinyin, textLayout) {
                        // Long-press selects the initial position, then a held finger
                        // can drag the insertion cursor freely before release.
                        detectDragGesturesAfterLongPress(
                            onDragStart = { position ->
                                textLayout?.getOffsetForPosition(position)?.let { displayOffset ->
                                    onMoveCursor(rawCursorForDisplayOffset(display, rawPinyin, displayOffset))
                                }
                            },
                            onDrag = { change, _ ->
                                textLayout?.getOffsetForPosition(change.position)?.let { displayOffset ->
                                    onMoveCursor(rawCursorForDisplayOffset(display, rawPinyin, displayOffset))
                                }
                                change.consume()
                            },
                        )
                    },
                )
                if (cursorRect != null) {
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(cursorRect.left.toInt(), 0) }
                            .width(1.dp)
                            .height(18.dp)
                            .background(theme.text),
                    )
                }
            }
        }
        if (candidates.isEmpty() && !candidatesReady) Text(status, color = theme.text, fontSize = 12.sp, modifier = Modifier.weight(1f))
        else CandidateBar(
            candidates = candidates,
            enabled = candidatesReady,
            onPick = onPick,
            theme = theme,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        Box(
            modifier = Modifier
                .size(28.dp)
                .then(if (candidatesReady) Modifier.pointerInput(onToggleExpand) {
                    detectTapGestures(onTap = { onToggleExpand() })
                } else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (expanded) "⌃" else "⌄",
                fontSize = 16.sp,
                color = theme.text,
            )
        }
    }
}

/** Maps a position in the segmented display (for example `ni'hao`) to raw pinyin. */
internal fun rawCursorForDisplayOffset(display: String, raw: String, displayOffset: Int): Int {
    var rawOffset = 0
    for (index in 0 until displayOffset.coerceIn(0, display.length)) {
        if (rawOffset < raw.length && display[index] == raw[rawOffset]) rawOffset++
    }
    return rawOffset
}

/** Maps the raw editing cursor back to the segmented display used by the composing bar. */
internal fun displayOffsetForRawCursor(display: String, raw: String, rawCursor: Int): Int {
    val target = rawCursor.coerceIn(0, raw.length)
    var consumed = 0
    for (index in display.indices) {
        if (consumed == target) return index
        if (consumed < raw.length && display[index] == raw[consumed]) consumed++
    }
    return display.length
}

@Composable
private fun ExpandedCandidatesPanel(
    candidates: List<Candidate>,
    enabled: Boolean,
    onPick: (Candidate) -> Unit,
    theme: JadeTheme,
) {
    // Anchored under the composing bar, covers the rest of the keyboard so long
    // candidate lists can be scanned by scroll instead of only right-swiping
    // the single-row bar (PLAN M1.6 "候选栏展开").
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = 44.dp)
            .height(204.dp)
            .zIndex(3f)
            .shadow(4.dp),
        color = theme.background,
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(candidates) { candidate ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(theme.keyDefault, RoundedCornerShape(6.dp))
                        .then(if (enabled) Modifier.pointerInput(candidate) {
                            detectTapGestures(onTap = { onPick(candidate) })
                        } else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (candidate.isCorrection) "纠 ${candidate.word}" else candidate.word,
                        fontSize = 16.sp,
                        color = theme.text,
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolbarRow(
    theme: JadeTheme,
    onOpenSymbols: () -> Unit,
    onOpenSkins: () -> Unit,
    onOpenEmoji: () -> Unit,
    onOpenSettings: () -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
    symbolsSelected: Boolean = false,
    emojiSelected: Boolean = false,
) {
    // Five entries spread evenly across the full row (PLAN M1.7 "五个入口平均分布整行"),
    // each a monochrome line icon instead of a colorful emoji glyph.
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        ToolbarIcon(theme, onOpenSymbols, symbolsSelected) { IconSymbols(it, Modifier.size(20.dp)) }
        ToolbarIcon(theme, onOpenSkins) { IconPalette(it, Modifier.size(20.dp)) }
        ToolbarIcon(theme, onOpenEmoji, emojiSelected) { IconEmoji(it, Modifier.size(20.dp)) }
        ToolbarIcon(theme, onOpenSettings) { IconSettings(it, Modifier.size(20.dp)) }
        ToolbarIcon(theme, onCollapse) { IconChevronDown(it, Modifier.size(20.dp)) }
    }
}

@Composable
private fun ToolbarIcon(
    theme: JadeTheme,
    onClick: () -> Unit,
    selected: Boolean = false,
    icon: @Composable (Color) -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .pointerInput(onClick) { detectTapGestures(onTap = { onClick() }) },
        contentAlignment = Alignment.Center,
    ) {
        icon(theme.text)
        if (selected) Box(Modifier.align(Alignment.BottomCenter).size(6.dp).background(theme.text.copy(alpha = .85f), RoundedCornerShape(2.dp)))
    }
}

@Composable
internal fun JadeKey(
    key: KeySpec,
    label: String,
    active: Boolean,
    theme: JadeTheme,
    modifier: Modifier,
    onActivate: () -> Unit,
    onDoubleTap: (() -> Unit)?,
    onLongPress: (() -> Unit)?,
    onLongPressAlt: (String) -> Unit,
    onSpaceLongPress: (() -> Unit)? = null,
    onMoveCursor: ((Int) -> Unit)? = null,
    // Distinguishes the shift key's two "on" states (PLAN M1.7 "单击=浅灰、锁定=常亮+下标");
    // [active] alone only says "on", not which flavor of on.
    shiftLocked: Boolean = false,
) {
    if (key.action == KeyAction.Spacer) {
        Box(modifier = modifier.fillMaxSize())
        return
    }

    var pressed by remember { mutableStateOf(false) }
    var longPressedCorner by remember { mutableStateOf(false) }
    val view = LocalView.current
    val viewConfiguration = LocalViewConfiguration.current
    val background = when {
        pressed -> theme.keyPressed
        shiftLocked -> theme.keyActive
        active -> theme.shiftOnce
        key.action in setOf(
            KeyAction.Shift,
            KeyAction.Backspace,
            KeyAction.Symbols,
            KeyAction.Numeric,
            KeyAction.Letters,
            KeyAction.LangToggle,
            KeyAction.Separator,
        ) -> theme.keySpecial
        else -> theme.keyDefault
    }

    val gestureModifier = if (key.action == KeyAction.Backspace) {
        Modifier.pointerInput(onActivate, viewConfiguration.longPressTimeoutMillis) {
            detectTapGestures(
                onPress = {
                    pressed = true
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    try {
                        coroutineScope {
                            var repeated = false
                            val repeatJob = launch {
                                delay(viewConfiguration.longPressTimeoutMillis.toLong())
                                repeated = true
                                onLongPress?.invoke()
                                while (isActive) {
                                    onActivate()
                                    delay(55)
                                }
                            }
                            val released = tryAwaitRelease()
                            repeatJob.cancelAndJoin()
                            if (released && !repeated) onActivate()
                        }
                    } finally {
                        pressed = false
                    }
                },
            )
        }
    } else if (key.action == KeyAction.Space) {
        val currentActivate by rememberUpdatedState(onActivate)
        val currentVoice by rememberUpdatedState(onSpaceLongPress)
        val currentMove by rememberUpdatedState(onMoveCursor)
        // Hand-rolled instead of composing detectTapGestures + detectDragGestures:
        // both start from the same awaitFirstDown(), and running them as independent
        // detectors racing on one pointer stream is exactly the kind of "who
        // consumes first" ambiguity that produces flaky double-fires. One gesture
        // loop that decides tap vs. long-press vs. drag once, at the end, is
        // unambiguous (PLAN M2 "空格滑动移动光标"). Note: AwaitPointerEventScope is
        // @RestrictsSuspension, so the long-press timeout can't be a sibling
        // coroutineScope{launch{}} (that's what the Backspace-repeat gesture above
        // does, but from an *unrestricted* PressGestureScope) -- withTimeoutOrNull
        // is a plain suspend fun with no receiver of its own, so it's fair game here.
        Modifier.pointerInput(viewConfiguration) {
            val touchSlop = viewConfiguration.touchSlop
            val stepPx = touchSlop * 3f
            awaitEachGesture {
                val down = awaitFirstDown()
                pressed = true
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                var dragging = false
                var released = false
                var lastStepX = down.position.x

                val timedOut = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    while (!dragging && !released) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) {
                            change?.consume()
                            released = true
                        } else if (currentMove != null && kotlin.math.abs(change.position.x - lastStepX) > touchSlop) {
                            dragging = true
                        }
                    }
                } == null

                var longPressFired = false
                if (timedOut && !dragging && !released && currentVoice != null) {
                    longPressFired = true
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    currentVoice?.invoke()
                }

                if (dragging) {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        change.consume()
                        var delta = change.position.x - lastStepX
                        while (delta > stepPx) {
                            currentMove?.invoke(1)
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            lastStepX += stepPx
                            delta -= stepPx
                        }
                        while (delta < -stepPx) {
                            currentMove?.invoke(-1)
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            lastStepX -= stepPx
                            delta += stepPx
                        }
                    }
                } else if (!released) {
                    // Long press already resolved (fired or no handler) -- just
                    // drain the rest of the gesture until release.
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                    }
                }

                pressed = false
                if (!dragging && !longPressFired) currentActivate()
            }
        }
    } else {
        val corner = key.corner
        Modifier.pointerInput(onActivate, onDoubleTap, onLongPress, corner, onSpaceLongPress) {
            detectTapGestures(
                onDoubleTap = onDoubleTap?.let { callback -> { callback() } },
                onLongPress = if (corner != null) {
                    {
                        longPressedCorner = true
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onLongPressAlt(corner)
                    }
                } else if (onSpaceLongPress != null) {
                    {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onSpaceLongPress()
                    }
                } else if (onLongPress != null) {
                    {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onLongPress()
                    }
                } else {
                    null
                },
                onPress = {
                    pressed = true
                    longPressedCorner = false
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    try {
                        tryAwaitRelease()
                    } finally {
                        pressed = false
                        longPressedCorner = false
                    }
                },
                onTap = { onActivate() },
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(if (pressed) 1f else 0f),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = if (pressed) 1.dp else 0.dp)
                .then(gestureModifier),
            shape = RoundedCornerShape(theme.keyCornerRadius),
            color = background.copy(alpha = background.alpha * theme.regionAlpha.keys),
            shadowElevation = if (pressed || theme.regionAlpha.keys < 1f) 0.dp else 2.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = label,
                    color = theme.text,
                    fontSize = if (label.length > 3) 12.sp else 19.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    textDecoration = if (shiftLocked) TextDecoration.Underline else TextDecoration.None,
                )
            }
        }

        // Corner glyph (PLAN M1.6 "键帽角标小字符"): digits on the q-row, common
        // symbols on the a/z rows, reachable by long-press.
        if (key.corner != null) {
            Text(
                text = key.corner,
                color = theme.text.copy(alpha = 0.5f),
                fontSize = 9.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 3.dp),
            )
        }

        if (pressed && (key.showsPopup || key.corner != null)) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-52).dp)
                    .size(width = 48.dp, height = 54.dp)
                    .shadow(5.dp, RoundedCornerShape(9.dp)),
                color = theme.popupBackground,
                shape = RoundedCornerShape(9.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (longPressedCorner) key.corner ?: label else label,
                        color = theme.text,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

private fun KeySpec.displayLabel(shift: ShiftState, enterLabel: String, mode: InputMode): String = when (action) {
    KeyAction.Shift -> if (shift == ShiftState.Locked) "⇪" else "⇧"
    KeyAction.Enter -> enterLabel
    KeyAction.Space -> if (mode == InputMode.Chinese) "中文" else "English"
    KeyAction.LangToggle -> if (mode == InputMode.Chinese) "中" else "En"
    KeyAction.Text -> if (
        mode == InputMode.English &&
        shift != ShiftState.Off &&
        value?.all(Char::isLetter) == true
    ) {
        label.uppercase(Locale.ROOT)
    } else {
        label
    }
    else -> label
}

// PLAN M1.7 "核心组件挂 @Preview": lets the keyboard be eyeballed in Android Studio's
// design pane without a device/data cable. engine = null falls back to whatever
// PinyinEngine? default the call sites tolerate -- ImeRoot already renders an empty
// candidate row when there's no engine, which is fine for a static layout preview.
private fun previewNoOp() {}

@Preview(name = "浅色 · 中文", showBackground = true, widthDp = 400)
@Composable
private fun ImeRootPreviewChineseLight() {
    ImeRoot(
        enterLabel = "↵",
        engine = null,
        onCommitText = {},
        onMoveCursor = {},
        onDeleteBackward = ::previewNoOp,
        onEnter = ::previewNoOp,
        onOpenSkins = ::previewNoOp,
        onOpenMic = { _ -> previewNoOp() },
        onOpenSettings = ::previewNoOp,
        onCollapseKeyboard = ::previewNoOp,
    )
}

@Preview(
    name = "深色 · 中文",
    showBackground = true,
    widthDp = 400,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ImeRootPreviewChineseDark() {
    ImeRoot(
        enterLabel = "↵",
        engine = null,
        onCommitText = {},
        onMoveCursor = {},
        onDeleteBackward = ::previewNoOp,
        onEnter = ::previewNoOp,
        onOpenSkins = ::previewNoOp,
        onOpenMic = { _ -> previewNoOp() },
        onOpenSettings = ::previewNoOp,
        onCollapseKeyboard = ::previewNoOp,
    )
}

@Preview(name = "浅色 · 英文", showBackground = true, widthDp = 400)
@Composable
private fun ImeRootPreviewEnglish() {
    ImeRoot(
        enterLabel = "↵",
        engine = null,
        onCommitText = {},
        onMoveCursor = {},
        onDeleteBackward = ::previewNoOp,
        onEnter = ::previewNoOp,
        onOpenSkins = ::previewNoOp,
        onOpenMic = { _ -> previewNoOp() },
        onOpenSettings = ::previewNoOp,
        onCollapseKeyboard = ::previewNoOp,
        initialMode = InputMode.English,
    )
}

@Preview(name = "浅色 · 符号页", showBackground = true, widthDp = 400)
@Composable
private fun ImeRootPreviewSymbols() {
    ImeRoot(
        enterLabel = "↵",
        engine = null,
        onCommitText = {},
        onMoveCursor = {},
        onDeleteBackward = ::previewNoOp,
        onEnter = ::previewNoOp,
        onOpenSkins = ::previewNoOp,
        onOpenMic = { _ -> previewNoOp() },
        onOpenSettings = ::previewNoOp,
        onCollapseKeyboard = ::previewNoOp,
        initialPage = KeyboardPage.Symbols,
    )
}
