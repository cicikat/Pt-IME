package com.chacha.jadeime.voice

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import android.os.SystemClock
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

/** Debug-only device smoke test. Uses public fixture audio, never the microphone. */
class OfflineVoiceSmokeTest : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val results = Bundle()
        try {
            LocalVoiceModel.worker.submit {
                val start = SystemClock.elapsedRealtime()
                val model = LocalVoiceModel.acquire(targetContext)
                results.putLong("model_load_ms", SystemClock.elapsedRealtime() - start)
                val bytes = targetContext.assets.open("asr-test.pcm").use { it.readBytes() }
                val shorts = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                val samples = FloatArray(shorts.remaining()) { shorts.get() / 32768f }
                val stream = model.createStream()
                val decodeStart = SystemClock.elapsedRealtime()
                try {
                    for (offset in samples.indices step 1600) {
                        stream.acceptWaveform(samples.copyOfRange(offset, minOf(samples.size, offset + 1600)), 16000)
                        while (model.isReady(stream)) model.decode(stream)
                    }
                    stream.acceptWaveform(FloatArray(8000), 16000)
                    stream.inputFinished()
                    while (model.isReady(stream)) model.decode(stream)
                    val text = model.getResult(stream).text.trim()
                    check(text.isNotEmpty())
                    results.putInt("recognized_characters", text.length)
                    results.putLong("decode_ms", SystemClock.elapsedRealtime() - decodeStart)
                    results.putInt("audio_ms", samples.size / 16)
                } finally { stream.release(); LocalVoiceModel.releaseWhenIdle() }
            }.get(120, TimeUnit.SECONDS)
            results.putString("result", "PASS: bundled model decoded public fixture offline")
            finish(Activity.RESULT_OK, results)
        } catch (error: Exception) {
            results.putString("result", "FAIL: ${error.javaClass.simpleName}")
            finish(Activity.RESULT_CANCELED, results)
        }
    }
}
