package com.chacha.jadeime.voice
import com.chacha.jadeime.ime.ui.InputMode
import java.util.Locale
object VoiceLocaleResolver { fun resolve(mode: InputMode): Locale = if (mode == InputMode.English) Locale.US else Locale.SIMPLIFIED_CHINESE }
