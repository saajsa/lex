package com.legumlex.client.ui.brand

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.legumlex.client.R
import com.legumlex.client.ui.theme.AccentInk
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * The Legum Lex mark: two interlocking L forms, from the company's logo file
 * (cases/assets/images/legum-lex-logo.png), drawn on the geometry the website
 * draws it with (cases/views/public/site/partials/mark.php, viewBox 284×325).
 * The launcher icon, the status-bar icon and the splash use the same shapes
 * (res/drawable/ic_launcher_foreground.xml, ic_stat_notification.xml).
 */

private const val VW = 284f
private const val VH = 325f

// Each form: two rectangles, and its own box (for the assembly's offsets).
private val L1 = listOf(floatArrayOf(10f, 10f, 56f, 218f), floatArrayOf(10f, 180f, 125f, 48f))
private val L2 = listOf(floatArrayOf(195f, 10f, 79f, 305f), floatArrayOf(100f, 248f, 174f, 67f))
private const val L1W = 125f; private const val L1H = 218f
private const val L2W = 174f; private const val L2H = 305f

// The site's ease: cubic-bezier(.2,.8,.2,1).
private val Ease = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

/**
 * The mark, fitted inside a [size] square. With [animate], the two forms
 * slide in and settle as the site's header assembles them on arrival
 * (ll-mark-anim: the first from up and left, the second from down and right).
 */
@Composable
fun LegumMark(
    size: Dp,
    modifier: Modifier = Modifier,
    fillColor: Color = AccentInk,
    animate: Boolean = false,
) {
    val a1 = remember { Animatable(if (animate) 0f else 1f) }
    val a2 = remember { Animatable(if (animate) 0f else 1f) }
    if (animate) {
        LaunchedEffect(Unit) {
            launch { delay(100); a1.animateTo(1f, tween(900, easing = Ease)) }
            launch { delay(250); a2.animateTo(1f, tween(900, easing = Ease)) }
        }
    }
    val resolved = if (size == Dp.Unspecified) 64.dp else size
    Canvas(modifier = modifier.size(resolved)) {
        val s = minOf(this.size.width / VW, this.size.height / VH)
        val ox = (this.size.width - VW * s) / 2
        val oy = (this.size.height - VH * s) / 2
        // Each form as one shape, so where its two bars meet is not painted
        // twice while it fades in.
        fun form(rects: List<FloatArray>, p: Float, dx: Float, dy: Float) {
            val tx = (1 - p) * dx; val ty = (1 - p) * dy
            val path = Path()
            rects.forEach { r ->
                path.addRect(Rect(Offset(ox + (r[0] + tx) * s, oy + (r[1] + ty) * s), Size(r[2] * s, r[3] * s)))
            }
            drawPath(path, fillColor.copy(alpha = fillColor.alpha * p))
        }
        form(L1, a1.value, -0.28f * L1W, -0.18f * L1H)
        form(L2, a2.value, 0.24f * L2W, 0.16f * L2H)
    }
}

/** "LEGUM LEX", the company's wordmark (cases/assets/images/logo.png), in [color]. */
@Composable
fun LegumWordmark(height: Dp, modifier: Modifier = Modifier, color: Color = AccentInk) {
    Image(
        painter = painterResource(R.drawable.legum_lex_wordmark),
        contentDescription = "Legum Lex",
        colorFilter = ColorFilter.tint(color),
        contentScale = ContentScale.Fit,
        modifier = modifier.height(height),
    )
}
