package com.chacha.jadeime.ime

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.view.inputmethod.EditorInfo.IME_ACTION_DONE
import android.view.inputmethod.EditorInfo.IME_ACTION_GO
import android.view.inputmethod.EditorInfo.IME_ACTION_NEXT
import android.view.inputmethod.EditorInfo.IME_ACTION_PREVIOUS
import android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
import android.view.inputmethod.EditorInfo.IME_ACTION_SEND
import android.view.inputmethod.EditorInfo.IME_MASK_ACTION
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import android.app.KeyguardManager
import com.chacha.jadeime.voice.VoiceInputController
import java.util.Locale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.chacha.jadeime.ServiceLocator
import com.chacha.jadeime.ime.ui.ImeRoot
import com.chacha.jadeime.settings.SettingsActivity
import com.chacha.jadeime.settings.SettingsPage

/** Android entry point for the JadeBoard input method. */
class JadeImeService : InputMethodService() {
    private var voice: VoiceInputController? = null
    private var voiceState by mutableStateOf<com.chacha.jadeime.ime.ui.VoiceUiState?>(null)
    private var voiceGeneration = 0
    private val voiceHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var voiceTimeout: Runnable? = null

    private var viewTreeOwners: ImeViewTreeOwners? = null
    private var enterLabel by mutableStateOf("↵")
    private var fieldConstraint by mutableStateOf(FieldConstraint.None)
    // Bumped on every onStartInputView so ImeRoot's LaunchedEffect re-fires even when
    // two consecutive fields share the same constraint (e.g. tabbing between two
    // password fields) -- the constraint value alone wouldn't change in that case.
    private var fieldGeneration by mutableStateOf(0)
    private val clipboardManager: ClipboardManager by lazy {
        getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    // Registered for the service's whole process-lifetime, not just while the
    // keyboard window is shown -- clipboard changes (e.g. copying in another app
    // right before switching to this one) should already be in history by the
    // time the user opens the panel. PLAN M2 "剪贴板：监听 + 历史面板"; the
    // "敏感字段豁免" clause is why the currently focused field is checked here
    // rather than blanket-recording every clip.
    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        if (currentInputEditorInfo.isSensitiveField()) return@OnPrimaryClipChangedListener
        val text = clipboardManager.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(this)
            ?.toString()
        if (!text.isNullOrBlank()) {
            ServiceLocator.recordClipboard(text)
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Defensive: JadeApplication.onCreate() already does this, but the IME
        // process could in principle be entered without it running first.
        ServiceLocator.init(applicationContext)
        clipboardManager.addPrimaryClipChangedListener(clipboardListener)
    }

    override fun onCreateInputView(): View {
        viewTreeOwners?.destroy()
        val owners = ImeViewTreeOwners().also {
            it.create()
            viewTreeOwners = it
        }

        // Some OEM ROMs (observed on ColorOS) wrap the IME window's content in
        // extra decor (e.g. an "android:id/parentPanel" container) above whatever
        // view onCreateInputView() returns. Compose's WindowRecomposer looks up
        // the ViewTreeLifecycleOwner starting from the window's actual decorView
        // and walks *down* from there -- tagging only the returned ComposeView
        // (a descendant) is invisible to that lookup on such ROMs, so the owners
        // must be attached to the real window decorView too (FlorisBoard's fix
        // for the same issue).
        window?.window?.decorView?.let { decorView ->
            decorView.setViewTreeLifecycleOwner(owners)
            decorView.setViewTreeViewModelStoreOwner(owners)
            decorView.setViewTreeSavedStateRegistryOwner(owners)
        }

        return ComposeView(this).apply {
            setViewTreeLifecycleOwner(owners)
            setViewTreeViewModelStoreOwner(owners)
            setViewTreeSavedStateRegistryOwner(owners)
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
            )
            setContent {
                val engine by ServiceLocator.engine.collectAsState()
                val engineFailed by ServiceLocator.engineFailed.collectAsState()
                ImeRoot(
                    enterLabel = enterLabel,
                    engine = engine,
                    engineFailed = engineFailed,
                    onCommitText = ::commitText,
                    onPasteText = { currentInputConnection?.commitText(it, 1) },
                    onDeleteBackward = ::deleteBackward,
                    onComposingDelete = { recordDraftEdit("compose_delete", it, "applied") },
                    onDeleteLongPress = ::deleteLongPress,
                    onEnter = ::performEnter,
                    onMoveCursor = ::moveCursor,
                    // Theme and general settings have distinct entry points so the
                    // toolbar reaches the skin picker directly instead of a mixed page.
                    onOpenSkins = ::openThemeSettings,
                    onOpenMic = ::startVoice,
                    voiceState = voiceState,
                    onStopVoice = ::stopVoice,
                    onCancelVoice = ::cancelVoice,
                    onKeyboardActivity = ::keyboardActivity,
                    onOpenSettings = ::openSettings,
                    onCollapseKeyboard = { requestHideSelf(0) },
                    emojiRepository = ServiceLocator.emojiRepository,
                    emojiMappingRepository = ServiceLocator.emojiMappingRepository,
                    phraseRepository = ServiceLocator.phraseRepository,
                    clipboardRepository = ServiceLocator.clipboardRepository,
                    themeRepository = ServiceLocator.themeRepository,
                    layoutRepository = ServiceLocator.layoutRepository,
                    symbolRepository = ServiceLocator.symbolRepository,
                    fieldConstraint = fieldConstraint,
                    fieldGeneration = fieldGeneration,
                )
            }
        }
    }

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        cancelVoice()
        keyboardActivity()
    }

    override fun onFinishInput() {
        cancelVoice()
        super.onFinishInput()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        cancelVoice()
        keyboardActivity()
        enterLabel = info?.actionLabel?.toString()
            ?: actionLabel((info?.imeOptions ?: 0) and IME_MASK_ACTION)
        fieldConstraint = info.deriveFieldConstraint()
        deleteSnapshot = null
        ServiceLocator.setEngineLearningEnabled(!info.isSensitiveField())
        fieldGeneration++
        viewTreeOwners?.resume()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        cancelVoice()
        viewTreeOwners?.pause()
        ServiceLocator.endEngineSession()
        deleteSnapshot = null
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        cancelVoice()
        clipboardManager.removePrimaryClipChangedListener(clipboardListener)
        viewTreeOwners?.destroy()
        viewTreeOwners = null
        super.onDestroy()
    }

    private fun keyboardActivity() {
        if (currentInputEditorInfo.isSensitiveField()) ServiceLocator.breakDraftSession()
        else currentInputEditorInfo?.packageName?.let { ServiceLocator.draftActivity(it) }
    }

    private fun cancelVoice() {
        voiceGeneration++
        voiceTimeout?.let { voiceHandler.removeCallbacks(it) }
        voiceTimeout = null
        voice?.destroy()
        voice = null
        voiceState = null
    }

    private fun voiceFailure(message: String) {
        cancelVoice()
        voiceState = com.chacha.jadeime.ime.ui.VoiceUiState(message = message, busy = false)
    }

    private fun armVoiceTimeout(generation: Int, millis: Long) {
        voiceTimeout?.let { voiceHandler.removeCallbacks(it) }
        voiceTimeout = Runnable { if (generation == voiceGeneration) voiceFailure("语音服务无响应，请重试或检查系统语音服务") }
            .also { voiceHandler.postDelayed(it, millis) }
    }

    private fun stopVoice() {
        voiceState = voiceState?.copy(message = "正在识别…", listening = false, level = 0f)
        armVoiceTimeout(voiceGeneration, 15_000)
        runCatching { voice?.stop() }.onFailure { voiceFailure("无法停止录音，请重试") }
    }

    private fun startVoice(mode: com.chacha.jadeime.ime.ui.InputMode) {
        cancelVoice()
        if (currentInputConnection == null || getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
            voiceFailure("当前输入框不可用"); return
        }
        if (currentInputEditorInfo.isSensitiveField()) {
            voiceFailure("敏感输入框不启用语音"); return
        }
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            voiceFailure("未开启麦克风：请在设置中允许录音权限"); return
        }
        voiceState = com.chacha.jadeime.ime.ui.VoiceUiState(message = "正在加载离线语音模型…")
        val generation = voiceGeneration
        val controller = VoiceInputController(this)
        voice = controller
        armVoiceTimeout(generation, 60_000)
        runCatching {
            controller.start(
                com.chacha.jadeime.voice.VoiceLocaleResolver.resolve(mode),
                listener = { text ->
                    if (generation == voiceGeneration) {
                        if (!currentInputEditorInfo.isSensitiveField()) commitInput(text, "voice")
                        cancelVoice()
                    }
                },
                error = { code -> if (generation == voiceGeneration) voiceFailure(when (code) {
                    0 -> "系统没有可用的语音识别服务"
                    6, 7 -> "没有识别到说话，请重试"
                    9 -> "麦克风权限不可用，请检查设置"
                    1, 2 -> "语音服务网络连接失败"
                    8 -> "语音服务忙，请稍后重试"
                    100 -> "离线模型或录音启动失败，请关闭语音后重试"
                    else -> "语音识别失败（错误 $code），请重试"
                }) },
                partial = { text -> if (generation == voiceGeneration) voiceState = voiceState?.copy(partial = text) },
                ready = { if (generation == voiceGeneration) {
                    voiceState = voiceState?.copy(message = "麦克风已开启 · 正在聆听", listening = true)
                    armVoiceTimeout(generation, 120_000)
                } },
                rms = { value -> if (generation == voiceGeneration && voiceState?.listening == true) {
                    voiceState = voiceState?.copy(level = ((value + 2f) / 12f).coerceIn(0f, 1f))
                } },
                ended = { if (generation == voiceGeneration) {
                    voiceState = voiceState?.copy(message = "录音结束 · 正在识别…", listening = false, level = 0f)
                    armVoiceTimeout(generation, 15_000)
                } },
            )
        }.onFailure { if (generation == voiceGeneration) voiceFailure("无法启动麦克风或系统语音服务") }
    }

    private fun commitText(text: String) = commitInput(text, "keyboard")

    private fun commitInput(text: String, source: String) {
        val connection = currentInputConnection ?: return
        if (!connection.commitText(text, 1)) return
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (!keyguard.isKeyguardLocked && !currentInputEditorInfo.isSensitiveField() && text.isNotEmpty()) {
            ServiceLocator.recordDraft(text, currentInputEditorInfo?.packageName.orEmpty(), source)
        }
    }

    private fun deleteBackward() {
        if (restoreSnapshotIfAvailable()) return
        val ic = currentInputConnection ?: return
        val text = if (canObserveDraft()) {
            ic.getSelectedText(0)?.toString()?.take(4096)?.takeIf { it.isNotEmpty() }
                ?: ic.getTextBeforeCursor(2, 0)?.toString().orEmpty().let { before ->
                    if (before.isEmpty()) "" else String(Character.toChars(before.codePointBefore(before.length)))
                }
        } else ""
        sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_DEL)
        recordDraftEdit("delete_backward", text, "requested")
    }

    private fun canObserveDraft(): Boolean = ServiceLocator.draftPrivacy.enabled &&
        !getSystemService(KeyguardManager::class.java).isKeyguardLocked && !currentInputEditorInfo.isSensitiveField()

    private fun recordDraftEdit(kind: String, text: String, outcome: String) {
        if (canObserveDraft()) ServiceLocator.recordDraftEdit(kind, text, outcome, currentInputEditorInfo?.packageName.orEmpty())
    }

    private var deleteSnapshot: CharSequence? = null
    private var deleteSnapshotCursor = 0
    private fun deleteLongPress() {
        if (currentInputEditorInfo.isSensitiveField()) return
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(4096, 0) ?: return
        val after = ic.getTextAfterCursor(4096, 0) ?: return
        deleteSnapshot = before.toString() + after.toString()
        deleteSnapshotCursor = before.length
        val applied = ic.deleteSurroundingText(before.length, after.length)
        recordDraftEdit("clear", (before.toString() + after.toString()).takeLast(4096), if (applied) "applied" else "requested")
    }
    private fun restoreSnapshotIfAvailable(): Boolean {
        val snap = deleteSnapshot ?: return false
        if (currentInputEditorInfo.isSensitiveField()) { deleteSnapshot = null; return false }
        val connection = currentInputConnection ?: return false
        if (!connection.commitText(snap, 1)) return false
        recordDraftEdit("restore", snap.toString().takeLast(4096), "applied")
        val cursor = deleteSnapshotCursor.coerceIn(0, snap.length)
        connection.setSelection(cursor, cursor)
        deleteSnapshot = null
        return true
    }

    private fun moveCursor(direction: Int) {
        val keyCode = if (direction > 0) {
            android.view.KeyEvent.KEYCODE_DPAD_RIGHT
        } else {
            android.view.KeyEvent.KEYCODE_DPAD_LEFT
        }
        sendDownUpKeyEvents(keyCode)
    }

    private fun performEnter() {
        if (!sendDefaultEditorAction(true)) {
            commitText("\n")
        }
    }

    private fun openSettings() {
        startActivity(SettingsActivity.intent(this, SettingsPage.General).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun openThemeSettings() {
        startActivity(SettingsActivity.intent(this, SettingsPage.Theme).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // 皮肤 (M5) / emoji panel (M2) / 语音 (M4) aren't built yet -- toolbar/keyboard
    // entries exist already but stay stubbed until those milestones land. PLAN
    // M1.6 asks the stub to say *when* it lands instead of a silent no-op, so a
    // tap doesn't read as the button being broken.
    private fun showComingSoon(feature: String, milestone: String) {
        Toast.makeText(this, "$feature 功能 $milestone 后可用", Toast.LENGTH_SHORT).show()
    }

    private fun actionLabel(action: Int): String = when (action) {
        IME_ACTION_GO -> "Go"
        IME_ACTION_SEARCH -> "⌕"
        IME_ACTION_SEND -> "Send"
        IME_ACTION_NEXT -> "Next"
        IME_ACTION_DONE -> "Done"
        IME_ACTION_PREVIOUS -> "Prev"
        else -> "↵"
    }
}
