package com.legumlex.client.ui.illustrations

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.legumlex.client.ui.theme.AccentInk
import com.legumlex.client.ui.theme.StatusPending

// Geometric, line-art legal illustrations built on Compose Canvas — no raster
// assets. They inherit a stroke `color` so they read on both light and dark
// backgrounds, with an `accent` for the one highlighted element in each.

private class Frame(val ox: Float, val oy: Float, val s: Float) {
    fun at(fx: Float, fy: Float) = Offset(ox + fx * s, oy + fy * s)
}

private fun DrawScope.frame(): Frame {
    val s = size.minDimension
    return Frame((size.width - s) / 2f, (size.height - s) / 2f, s)
}

// ── Scales of justice — splash / brand hero ─────────────────────────────────
@Composable
fun ScalesIllustration(
    modifier: Modifier = Modifier,
    color: Color = AccentInk,
    accent: Color = StatusPending.solid,
) {
    Canvas(modifier = modifier) {
        val f = frame()
        val sw = f.s * 0.024f
        val thin = sw * 0.82f

        fun line(a: Offset, b: Offset, w: Float = sw) =
            drawLine(color, a, b, w, StrokeCap.Round)

        // Central post + finial
        line(f.at(0.5f, 0.16f), f.at(0.5f, 0.80f))
        drawCircle(color, f.s * 0.036f, f.at(0.5f, 0.115f), style = Stroke(sw))

        // Beam
        line(f.at(0.19f, 0.255f), f.at(0.81f, 0.255f))

        // Hanging pans (a bowl drawn as a quadratic arc + two strings)
        fun pan(cx: Float) {
            val rim = 0.455f
            val left = cx - 0.115f
            val right = cx + 0.115f
            line(f.at(cx, 0.265f), f.at(left, rim), thin)
            line(f.at(cx, 0.265f), f.at(right, rim), thin)
            val bowl = Path().apply {
                moveTo(f.at(left, rim).x, f.at(left, rim).y)
                quadraticTo(
                    f.at(cx, rim + 0.135f).x, f.at(cx, rim + 0.135f).y,
                    f.at(right, rim).x, f.at(right, rim).y,
                )
            }
            drawPath(bowl, color, style = Stroke(sw, cap = StrokeCap.Round))
        }
        pan(0.225f)
        pan(0.775f)

        // Triangular base
        line(f.at(0.5f, 0.80f), f.at(0.34f, 0.88f))
        line(f.at(0.5f, 0.80f), f.at(0.66f, 0.88f))
        line(f.at(0.30f, 0.88f), f.at(0.70f, 0.88f))

        // Accent: a subtle highlight dot on the finial
        drawCircle(accent, f.s * 0.013f, f.at(0.5f, 0.115f))
    }
}

// ── Case file — Welcome slide 1 ─────────────────────────────────────────────
@Composable
fun CaseFileIllustration(
    modifier: Modifier = Modifier,
    color: Color = AccentInk,
    accent: Color = StatusPending.solid,
) {
    Canvas(modifier = modifier) {
        val f = frame()
        val sw = f.s * 0.022f
        val stroke = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round)

        // Page outline with a folded top-right corner
        val foldX = 0.66f
        val foldY = 0.34f
        val page = Path().apply {
            moveTo(f.at(0.28f, 0.20f).x, f.at(0.28f, 0.20f).y)
            lineTo(f.at(foldX, 0.20f).x, f.at(foldX, 0.20f).y)
            lineTo(f.at(0.78f, foldY).x, f.at(0.78f, foldY).y)
            lineTo(f.at(0.78f, 0.86f).x, f.at(0.78f, 0.86f).y)
            lineTo(f.at(0.28f, 0.86f).x, f.at(0.28f, 0.86f).y)
            close()
        }
        drawPath(page, color, style = stroke)

        // Fold triangle
        val fold = Path().apply {
            moveTo(f.at(foldX, 0.20f).x, f.at(foldX, 0.20f).y)
            lineTo(f.at(foldX, foldY).x, f.at(foldX, foldY).y)
            lineTo(f.at(0.78f, foldY).x, f.at(0.78f, foldY).y)
        }
        drawPath(fold, color, style = stroke)

        // Text lines
        fun row(y: Float, x1: Float, x2: Float, c: Color = color) =
            drawLine(c, f.at(x1, y), f.at(x2, y), sw * 0.9f, StrokeCap.Round)
        row(0.46f, 0.36f, 0.70f, accent) // accent: the "case title" line
        row(0.56f, 0.36f, 0.70f)
        row(0.65f, 0.36f, 0.70f)
        row(0.74f, 0.36f, 0.58f)
    }
}

// ── Calendar with a marked hearing date — Welcome slide 2 ────────────────────
@Composable
fun CalendarIllustration(
    modifier: Modifier = Modifier,
    color: Color = AccentInk,
    accent: Color = StatusPending.solid,
) {
    Canvas(modifier = modifier) {
        val f = frame()
        val sw = f.s * 0.022f
        val stroke = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round)

        // Body
        val body = Path().apply {
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    left = f.at(0.20f, 0.26f).x, top = f.at(0.20f, 0.26f).y,
                    right = f.at(0.80f, 0.26f).x, bottom = f.at(0.80f, 0.84f).y,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(f.s * 0.04f),
                )
            )
        }
        drawPath(body, color, style = stroke)

        // Binding tabs
        drawLine(color, f.at(0.36f, 0.18f), f.at(0.36f, 0.30f), sw, StrokeCap.Round)
        drawLine(color, f.at(0.64f, 0.18f), f.at(0.64f, 0.30f), sw, StrokeCap.Round)

        // Header divider
        drawLine(color, f.at(0.20f, 0.40f), f.at(0.80f, 0.40f), sw, StrokeCap.Round)

        // Day grid — 4 columns × 3 rows
        val cols = listOf(0.31f, 0.44f, 0.57f, 0.69f)
        val rows = listOf(0.52f, 0.63f, 0.74f)
        rows.forEachIndexed { ri, ry ->
            cols.forEachIndexed { ci, cx ->
                val marked = ri == 0 && ci == 2
                if (marked) {
                    drawCircle(accent, f.s * 0.038f, f.at(cx, ry))
                } else {
                    drawCircle(color, f.s * 0.020f, f.at(cx, ry))
                }
            }
        }
    }
}

// ── Shield with a check — Welcome slide 3 ───────────────────────────────────
@Composable
fun ShieldIllustration(
    modifier: Modifier = Modifier,
    color: Color = AccentInk,
    accent: Color = StatusPending.solid,
) {
    Canvas(modifier = modifier) {
        val f = frame()
        val sw = f.s * 0.024f
        val stroke = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round)

        val shield = Path().apply {
            moveTo(f.at(0.50f, 0.15f).x, f.at(0.50f, 0.15f).y)
            lineTo(f.at(0.79f, 0.26f).x, f.at(0.79f, 0.26f).y)
            lineTo(f.at(0.79f, 0.52f).x, f.at(0.79f, 0.52f).y)
            quadraticTo(
                f.at(0.79f, 0.74f).x, f.at(0.79f, 0.74f).y,
                f.at(0.50f, 0.86f).x, f.at(0.50f, 0.86f).y,
            )
            quadraticTo(
                f.at(0.21f, 0.74f).x, f.at(0.21f, 0.74f).y,
                f.at(0.21f, 0.52f).x, f.at(0.21f, 0.52f).y,
            )
            lineTo(f.at(0.21f, 0.26f).x, f.at(0.21f, 0.26f).y)
            close()
        }
        drawPath(shield, color, style = stroke)

        // Check mark (accent)
        val check = Path().apply {
            moveTo(f.at(0.39f, 0.49f).x, f.at(0.39f, 0.49f).y)
            lineTo(f.at(0.47f, 0.58f).x, f.at(0.47f, 0.58f).y)
            lineTo(f.at(0.63f, 0.39f).x, f.at(0.63f, 0.39f).y)
        }
        drawPath(check, accent, style = Stroke(sw * 1.25f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
