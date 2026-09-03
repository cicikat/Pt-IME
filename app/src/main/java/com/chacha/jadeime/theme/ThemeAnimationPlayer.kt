package com.chacha.jadeime.theme

/** Lightweight, allocation-free animation gate used by Compose renderers.
 * It only returns a normalized progress value; resource decoding and drawing stay
 * outside the input/commit path. */
class ThemeAnimationPlayer(private val spec: AnimationSpec, private val clockMs: () -> Long = { System.currentTimeMillis() }) {
    private var startedAt = 0L
    private var droppedFrames = 0
    fun start() { if (spec.enabled) startedAt = clockMs(); droppedFrames = 0 }
    fun progress(): Float {
        if (!spec.enabled || startedAt == 0L) return 0f
        val elapsed = (clockMs() - startedAt).coerceAtLeast(0L)
        return (elapsed.toFloat() / spec.durationMs).coerceIn(0f, 1f)
    }
    fun reportFrame(elapsedMs: Long): Boolean {
        if (elapsedMs > 34L) droppedFrames++ else droppedFrames = 0
        return droppedFrames < 3
    }
    val maxObjects: Int get() = spec.maxObjects.coerceIn(0, 24)
}
