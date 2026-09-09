package com.chacha.jadeime.voice
import com.chacha.jadeime.ime.ui.InputMode
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale
class VoiceLocaleResolverTest { @Test fun followsInputMode() { assertEquals(Locale.US, VoiceLocaleResolver.resolve(InputMode.English)); assertEquals(Locale.SIMPLIFIED_CHINESE, VoiceLocaleResolver.resolve(InputMode.Chinese)) } }
