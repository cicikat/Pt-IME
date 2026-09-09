package com.chacha.jadeime.voice

import android.content.Context
import java.util.Locale

/** Lifecycle adapter for the bundled bilingual recognizer. */
class VoiceInputController(private val context: Context) {
    private var local: LocalVoiceInput? = null
    @Suppress("UNUSED_PARAMETER") // The bilingual model handles both keyboard languages.
    fun start(locale: Locale, listener: (String) -> Unit, error: (Int) -> Unit,
              partial: (String) -> Unit = {}, ready: () -> Unit = {},
              rms: (Float) -> Unit = {}, ended: () -> Unit = {}) {
        destroy()
        local = LocalVoiceInput(context.applicationContext).also {
            it.start(listener, error, partial, ready, rms, ended)
        }
    }
    fun stop() { local?.stop() }
    fun destroy() { local?.destroy(); local = null }
}
