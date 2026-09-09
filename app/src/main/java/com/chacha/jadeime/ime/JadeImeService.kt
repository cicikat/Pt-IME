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
                ImeRoot(
                    enterLabel = enterLabel,
                    engine = engine,
                    onCommitText = ::commitText,
                    onDeleteBackward = ::deleteBackward,
                    onDeleteLongPress = ::deleteLongPress,
                    onEnter = ::performEnter,
                    onMoveCursor = ::moveCursor,
                    // Theme and general settings have distinct entry points so the
                    // toolbar reaches the skin picker directly instead of a mixed page.
                    onOpenSkins = ::openThemeSettings,
                    onOpenMic = ::startVoice,
                    onOpenSettings = ::openSettings,
                    onCollapseKeyboard = { requestHideSelf(0) },
                    emojiRepository = ServiceLocator.emojiRepository,
                    emojiMappingRepository = ServiceLocator.emojiMappingRepository,
                    phraseRepository = ServiceLocator.phraseRepository,
                    clipboardRepository = ServiceLocator.clipboardRepository,
                    themeRepository = ServiceLocator.themeRepository,
                    layoutRepository = ServiceLocator.layoutRepository,
                    fieldConstraint = fieldConstraint,
                    fieldGeneration = fieldGeneration,
                )
            }
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        enterLabel = info?.actionLabel?.toString()
            ?: actionLabel((info?.imeOptions ?: 0) and IME_MASK_ACTION)
        fieldConstraint = info.deriveFieldConstraint()
        deleteSnapshot = null
        ServiceLocator.setEngineLearningEnabled(!info.isSensitiveField())
        fieldGeneration++
        viewTreeOwners?.resume()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        viewTreeOwners?.pause()
        ServiceLocator.endEngineSession()
        deleteSnapshot = null
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        voice?.destroy()
        clipboardManager.removePrimaryClipChangedListener(clipboardListener)
        viewTreeOwners?.destroy()
        viewTreeOwners = null
        super.onDestroy()
    }

    private fun startVoice(mode: com.chacha.jadeime.ime.ui.InputMode) {
        if (currentInputConnection == null || getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
            Toast.makeText(this, "当前输入框不可用", Toast.LENGTH_SHORT).show(); return
        }
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "请先允许录音权限", Toast.LENGTH_SHORT).show(); return
        }
        voice?.destroy()
        voice = VoiceInputController(this).also { c ->
            val locale = com.chacha.jadeime.voice.VoiceLocaleResolver.resolve(mode)
            c.start(locale, { text -> commitText(text); ServiceLocator.recordDraft(text, currentInputEditorInfo.packageName.orEmpty(), "voice"); voice?.destroy(); voice = null }, { Toast.makeText(this, "语音识别失败", Toast.LENGTH_SHORT).show(); voice?.destroy(); voice = null })
        }
    }

    private fun commitText(text: String) {
        currentInputConnection?.commitText(text, 1)
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (currentInputConnection != null && !keyguard.isKeyguardLocked && !currentInputEditorInfo.isSensitiveField() && text.isNotEmpty()) {
            ServiceLocator.recordDraft(text, currentInputEditorInfo.packageName.orEmpty(), "keyboard")
        }
    }

    private fun deleteBackward() {
        if (restoreSnapshotIfAvailable()) return
        sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_DEL)
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
        ic.deleteSurroundingText(before.length, after.length)
    }
    private fun restoreSnapshotIfAvailable(): Boolean {
        val snap = deleteSnapshot ?: return false
        if (currentInputEditorInfo.isSensitiveField()) { deleteSnapshot = null; return false }
        val connection = currentInputConnection ?: return false
        connection.commitText(snap, 1)
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
