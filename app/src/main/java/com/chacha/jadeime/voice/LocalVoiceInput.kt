package com.chacha.jadeime.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import com.k2fsa.sherpa.onnx.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.log10
import kotlin.math.sqrt

/** All native recognizer access is serialized; PCM exists only in bounded memory buffers. */
internal object LocalVoiceModel {
    val worker = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "jade-local-asr").apply { priority = Thread.NORM_PRIORITY - 1 }
    }
    private var recognizer: OnlineRecognizer? = null
    private var releaseTask: java.util.concurrent.ScheduledFuture<*>? = null

    fun acquire(context: Context): OnlineRecognizer {
        releaseTask?.cancel(false)
        return recognizer ?: OnlineRecognizer(context.assets, OnlineRecognizerConfig(
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = "asr/encoder-epoch-99-avg-1.int8.onnx",
                    decoder = "asr/decoder-epoch-99-avg-1.int8.onnx",
                    joiner = "asr/joiner-epoch-99-avg-1.int8.onnx",
                ),
                tokens = "asr/tokens.txt", modelType = "zipformer", numThreads = 2,
                debug = false,
            ),
            enableEndpoint = false,
        )).also { recognizer = it }
    }

    fun releaseWhenIdle() {
        releaseTask?.cancel(false)
        releaseTask = worker.schedule({ recognizer?.release(); recognizer = null }, 60, TimeUnit.SECONDS)
    }
}

internal class LocalVoiceInput(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var cancelled = false
    @Volatile private var finishing = false
    @Volatile private var recorder: AudioRecord? = null

    private fun deliver(action: () -> Unit) { main.post { if (!cancelled) action() } }

    @SuppressLint("MissingPermission") // Service checks permission before constructing this session.
    fun start(final: (String) -> Unit, error: (Int) -> Unit, partial: (String) -> Unit,
              ready: () -> Unit, rms: (Float) -> Unit, ended: () -> Unit) {
        LocalVoiceModel.worker.execute {
            var stream: OnlineStream? = null
            var audio: AudioRecord? = null
            try {
                if (cancelled) return@execute
                val model = LocalVoiceModel.acquire(context.applicationContext)
                if (cancelled || finishing) return@execute
                stream = model.createStream()
                val minBytes = AudioRecord.getMinBufferSize(16_000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                check(minBytes > 0)
                audio = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16_000,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBytes * 2, 32_000))
                check(audio.state == AudioRecord.STATE_INITIALIZED)
                recorder = audio
                if (cancelled || finishing) return@execute
                audio.startRecording()
                check(audio.recordingState == AudioRecord.RECORDSTATE_RECORDING)
                deliver(ready)
                val buffer = ShortArray(1600)
                var previousText = ""
                val started = android.os.SystemClock.elapsedRealtime()
                while (!cancelled && !finishing && android.os.SystemClock.elapsedRealtime() - started < 110_000) {
                    val count = audio.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                    if (cancelled || finishing) break
                    check(count > 0)
                    val samples = FloatArray(count) { buffer[it] / 32768f }
                    val energy = sqrt(samples.sumOf { (it * it).toDouble() } / count)
                    // Map measured dBFS to the UI's existing RMS scale (-2..10).
                    val level = ((20 * log10(energy.coerceAtLeast(0.00001)) + 60) / 50).toFloat().coerceIn(0f, 1f)
                    deliver { rms(level * 12 - 2) }
                    stream.acceptWaveform(samples, 16_000)
                    while (!cancelled && model.isReady(stream)) model.decode(stream)
                    val text = model.getResult(stream).text.trim()
                    if (text != previousText) { previousText = text; deliver { partial(text) } }
                }
                runCatching { audio.stop() }
                if (!cancelled) {
                    deliver(ended)
                    stream.acceptWaveform(FloatArray(8000), 16_000)
                    stream.inputFinished()
                    while (!cancelled && model.isReady(stream)) model.decode(stream)
                    val result = model.getResult(stream).text.trim()
                    deliver { if (result.isBlank()) error(7) else final(result) }
                }
            } catch (_: Exception) {
                deliver { error(100) }
            } catch (_: LinkageError) {
                deliver { error(100) }
            } finally {
                recorder = null
                runCatching { audio?.stop() }
                audio?.release()
                stream?.release()
                LocalVoiceModel.releaseWhenIdle()
            }
        }
    }

    fun stop() { finishing = true; runCatching { recorder?.stop() } }
    fun destroy() { cancelled = true; runCatching { recorder?.stop() } }
}
