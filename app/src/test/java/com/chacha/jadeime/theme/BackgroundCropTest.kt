package com.chacha.jadeime.theme

import org.junit.Assert.*
import org.junit.Test

class BackgroundCropTest {
    @Test fun `portrait landscape and zoom always cover keyboard without exposed edges`() {
        for ((iw, ih) in listOf(1080f to 1920f, 1920f to 1080f, 100f to 100f)) {
            for ((w, h) in listOf(400f to 268f, 800f to 268f)) {
                for (zoom in listOf(1f, 2f, 4f)) for (x in listOf(0f, .5f, 1f)) for (y in listOf(0f, .5f, 1f)) {
                    val p = BackgroundCrop(x, y, zoom).placement(iw, ih, w, h)
                    assertTrue(p.left <= .001f && p.top <= .001f)
                    assertTrue(p.left + p.width >= w - .001f && p.top + p.height >= h - .001f)
                }
            }
        }
    }
    @Test fun `extreme dragging clamps and invalid transforms recover`() {
        val crop = BackgroundCrop().dragged(100000f, -100000f, 1000f, 1000f, 400f, 268f)
        assertEquals(.5f, crop.x, .001f)
        assertEquals(1f, crop.y, .001f)
        assertEquals(BackgroundCrop(), BackgroundCrop(Float.NaN, Float.POSITIVE_INFINITY, Float.NaN).bounded())
        assertEquals(BackgroundCrop(0f, 1f, 1f), BackgroundCrop(-100f, 100f, -3f).bounded())
    }
}
