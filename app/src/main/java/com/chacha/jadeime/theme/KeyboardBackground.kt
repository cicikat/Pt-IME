package com.chacha.jadeime.theme

import android.content.Context
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal fun decodeBackground(path: String): Drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(File(path))) { decoder, info, _ ->
    val maxSide = if (info.isAnimated) 1024 else 1600
    val scale = minOf(1f, maxSide.toFloat() / maxOf(info.size.width, info.size.height))
    decoder.setTargetSize(maxOf(1, (info.size.width * scale).toInt()), maxOf(1, (info.size.height * scale).toInt()))
}

@Composable
fun KeyboardBackground(
    path: String,
    crop: BackgroundCrop,
    dim: Float,
    modifier: Modifier = Modifier,
    blur: Int = 0,
    onSize: (Int, Int) -> Unit = { _, _ -> },
) {
    var drawable by remember(path) { mutableStateOf<Drawable?>(null) }
    LaunchedEffect(path) {
        drawable = withContext(Dispatchers.IO) { runCatching { decodeBackground(path) }.getOrNull() }
    }
    val latestOnSize by rememberUpdatedState(onSize)
    LaunchedEffect(drawable) { drawable?.let { latestOnSize(it.intrinsicWidth, it.intrinsicHeight) } }
    Box(modifier.clipToBounds()) {
        AndroidView(
            factory = { CroppedBackgroundView(it) },
            modifier = Modifier.fillMaxSize().blur(blur.dp),
            update = { it.setImage(drawable, crop) },
            onRelease = { it.setImage(null, crop) },
        )
        if (dim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim.coerceIn(0f, 1f))))
    }
}

/** Drawing is clipped at the View too; animation runs only while the keyboard is visible. */
private class CroppedBackgroundView(context: Context) : View(context) {
    private var image: Drawable? = null
    private var crop = BackgroundCrop()
    fun setImage(value: Drawable?, position: BackgroundCrop) {
        if (image !== value) {
            (image as? AnimatedImageDrawable)?.stop()
            image?.callback = null
            image = value
            value?.callback = this
            updatePlayback()
        }
        crop = position
        invalidate()
    }
    override fun verifyDrawable(who: Drawable) = who === image || super.verifyDrawable(who)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val drawable = image ?: return
        val p = crop.placement(drawable.intrinsicWidth.toFloat(), drawable.intrinsicHeight.toFloat(), width.toFloat(), height.toFloat())
        if (p.width <= 0 || p.height <= 0) return
        val checkpoint = canvas.save()
        canvas.clipRect(0, 0, width, height)
        canvas.translate(p.left, p.top)
        canvas.scale(p.width / drawable.intrinsicWidth, p.height / drawable.intrinsicHeight)
        drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
        drawable.draw(canvas)
        canvas.restoreToCount(checkpoint)
    }
    private fun updatePlayback() {
        (image as? AnimatedImageDrawable)?.let {
            if (isAttachedToWindow && windowVisibility == VISIBLE && isShown) it.start() else it.stop()
        }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); updatePlayback() }
    override fun onDetachedFromWindow() { (image as? AnimatedImageDrawable)?.stop(); super.onDetachedFromWindow() }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); updatePlayback() }
    override fun onVisibilityAggregated(isVisible: Boolean) { super.onVisibilityAggregated(isVisible); updatePlayback() }
}
