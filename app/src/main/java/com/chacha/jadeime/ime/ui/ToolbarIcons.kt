package com.chacha.jadeime.ime.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Monochrome line-art toolbar glyphs (PLAN M1.7 "单色线性抽象 icon"), replacing the
 * earlier colorful emoji stand-ins. Hand-drawn with [Canvas] instead of pulling in
 * material-icons-extended -- that artifact ships thousands of unused icons and would
 * bloat the arm64-only debug APK for these few glyphs (CLAUDE.md "依赖能少则少").
 * All icons share the same stroke width/color so they read as one family.
 */

private val STROKE_WIDTH = 1.6.dp

@Composable
internal fun IconKeyboardSwitch(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = STROKE_WIDTH.toPx(), cap = StrokeCap.Round)
        val r = size.minDimension / 2f * 0.82f
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(color = color, radius = r, center = c, style = stroke)
        drawLine(color, Offset(c.x - r, c.y), Offset(c.x + r, c.y), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawOval(
            color = color,
            topLeft = Offset(c.x - r * 0.42f, c.y - r),
            size = Size(r * 0.84f, r * 2f),
            style = stroke,
        )
    }
}

/** A compact `!?#`-style mark for the toolbar's common-symbols entry. */
@Composable
internal fun IconSymbols(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = STROKE_WIDTH.toPx(), cap = StrokeCap.Round)
        val w = size.width
        val h = size.height
        // Exclamation mark
        drawLine(color, Offset(w * 0.2f, h * 0.2f), Offset(w * 0.2f, h * 0.58f), stroke.width, StrokeCap.Round)
        drawCircle(color, radius = stroke.width / 2f, center = Offset(w * 0.2f, h * 0.8f))
        // Question mark
        val question = Path().apply {
            moveTo(w * 0.42f, h * 0.32f)
            cubicTo(w * 0.42f, h * 0.08f, w * 0.72f, h * 0.08f, w * 0.72f, h * 0.32f)
            cubicTo(w * 0.72f, h * 0.49f, w * 0.56f, h * 0.5f, w * 0.56f, h * 0.64f)
        }
        drawPath(question, color, style = stroke)
        drawCircle(color, radius = stroke.width / 2f, center = Offset(w * 0.56f, h * 0.8f))
    }
}

@Composable
internal fun IconPalette(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = STROKE_WIDTH.toPx(), cap = StrokeCap.Round)
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.12f)
            cubicTo(w * 0.92f, h * 0.12f, w * 0.95f, h * 0.55f, w * 0.68f, h * 0.62f)
            cubicTo(w * 0.5f, h * 0.66f, w * 0.62f, h * 0.82f, w * 0.42f, h * 0.86f)
            cubicTo(w * 0.14f, h * 0.9f, w * 0.05f, h * 0.55f, w * 0.12f, h * 0.35f)
            cubicTo(w * 0.18f, h * 0.18f, w * 0.3f, h * 0.12f, w * 0.5f, h * 0.12f)
            close()
        }
        drawPath(path, color = color, style = stroke)
        val dotR = size.minDimension * 0.055f
        listOf(
            Offset(w * 0.34f, h * 0.34f),
            Offset(w * 0.58f, h * 0.3f),
            Offset(w * 0.72f, h * 0.46f),
        ).forEach { drawCircle(color = color, radius = dotR, center = it) }
    }
}

@Composable
internal fun IconEmoji(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = STROKE_WIDTH.toPx(), cap = StrokeCap.Round)
        val r = size.minDimension / 2f * 0.82f
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(color = color, radius = r, center = c, style = stroke)
        val eyeR = r * 0.09f
        drawCircle(color = color, radius = eyeR, center = Offset(c.x - r * 0.38f, c.y - r * 0.18f))
        drawCircle(color = color, radius = eyeR, center = Offset(c.x + r * 0.38f, c.y - r * 0.18f))
        drawArc(
            color = color,
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(c.x - r * 0.55f, c.y - r * 0.4f),
            size = Size(r * 1.1f, r * 1.1f),
            style = stroke,
        )
    }
}

@Composable
internal fun IconSettings(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = STROKE_WIDTH.toPx(), cap = StrokeCap.Round)
        val c = Offset(size.width / 2f, size.height / 2f)
        val rOuter = size.minDimension / 2f * 0.92f
        val rInner = rOuter * 0.62f
        val rTooth = rOuter * 1.04f
        drawCircle(color = color, radius = rInner, center = c, style = stroke)
        val teeth = 8
        for (i in 0 until teeth) {
            val angle = (2 * Math.PI * i / teeth).toFloat()
            val from = Offset(c.x + rInner * cos(angle), c.y + rInner * sin(angle))
            val to = Offset(c.x + rTooth * cos(angle), c.y + rTooth * sin(angle))
            drawLine(color, from, to, strokeWidth = stroke.width, cap = StrokeCap.Round)
        }
        drawCircle(color = color, radius = rInner * 0.32f, center = c, style = stroke)
    }
}

@Composable
internal fun IconChevronDown(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = STROKE_WIDTH.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.24f, h * 0.38f)
            lineTo(w * 0.5f, h * 0.64f)
            lineTo(w * 0.76f, h * 0.38f)
        }
        drawPath(path, color = color, style = stroke)
    }
}
