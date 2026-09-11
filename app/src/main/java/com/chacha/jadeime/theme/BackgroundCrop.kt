package com.chacha.jadeime.theme

/** Position is a fraction of the overflow, so every viewport stays fully covered. */
data class BackgroundCrop(val x: Float = .5f, val y: Float = .5f, val zoom: Float = 1f) {
    fun bounded() = BackgroundCrop(x.finite(.5f).coerceIn(0f, 1f), y.finite(.5f).coerceIn(0f, 1f), zoom.finite(1f).coerceIn(1f, 4f))
    fun placement(imageWidth: Float, imageHeight: Float, width: Float, height: Float): ImagePlacement {
        if (imageWidth <= 0 || imageHeight <= 0 || width <= 0 || height <= 0) return ImagePlacement(0f, 0f, 0f, 0f)
        val crop = bounded()
        val scale = maxOf(width / imageWidth, height / imageHeight) * crop.zoom
        val w = imageWidth * scale
        val h = imageHeight * scale
        return ImagePlacement(-(w - width) * crop.x, -(h - height) * crop.y, w, h)
    }
    fun dragged(dx: Float, dy: Float, imageWidth: Float, imageHeight: Float, width: Float, height: Float): BackgroundCrop {
        val p = placement(imageWidth, imageHeight, width, height)
        return copy(x = if (p.width - width > 1f) x - dx / (p.width - width) else x,
            y = if (p.height - height > 1f) y - dy / (p.height - height) else y).bounded()
    }
}

data class ImagePlacement(val left: Float, val top: Float, val width: Float, val height: Float)
private fun Float.finite(fallback: Float) = if (isFinite()) this else fallback
