package com.chacha.jadeime.voice

import android.content.Context
import android.content.Intent
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

class VoiceInputController(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null
    fun start(locale: Locale, listener: (String) -> Unit, error: (Int) -> Unit, partial: (String) -> Unit = {}) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) { error(0); return }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: android.os.Bundle) { results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(listener) ?: error(SpeechRecognizer.ERROR_NO_MATCH) }
                override fun onError(code: Int) { error(code); destroy() }
                override fun onReadyForSpeech(p: android.os.Bundle?) {} override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(v: Float) {} override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() {} override fun onPartialResults(b: android.os.Bundle?) { b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(partial) }
                override fun onEvent(t: Int, b: android.os.Bundle?) {}
            })
            startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            })
        }
    }
    fun stop() { recognizer?.stopListening() }
    fun destroy() { recognizer?.destroy(); recognizer = null }
}

