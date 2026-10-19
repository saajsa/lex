package com.legumlex.client.ui.brand

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.legumlex.client.ui.theme.AccentInk

// Canonical Legum Lex LJ monogram. Single source of truth for the Compose
// surfaces — splash, login chrome, settings about. Drawn on the 60-unit
// design grid (matches drawable/ic_launcher_foreground.xml and the brand
// design doc); the Canvas scales it to the requested size. Client polarity
// is `fillColor = ink` over a snow surface.
@Composable
fun LegumMark(
    size: Dp,
    modifier: Modifier = Modifier,
    fillColor: Color = AccentInk,
) {
    val resolved = if (size == Dp.Unspecified) 64.dp else size
    Canvas(modifier = modifier.size(resolved)) {
        val unit = this.size.width / 60f   // 60-unit design grid
        val p = Path()
        p.addRect(rectOf(8f,  8f,  8f,  44f, unit))   // L stem
        p.addRect(rectOf(8f,  44f, 22f, 8f,  unit))   // L bar
        p.addRect(rectOf(36f, 8f,  16f, 8f,  unit))   // J top
        p.addRect(rectOf(44f, 8f,  8f,  32f, unit))   // J stem
        // J hook with rounded inner corner — traced directly from the design's
        // 60-unit subpath: M52 36 L52 44 Q52 52 44 52 L36 52 L36 44 L42 44 Q44 44 44 42 L44 36 Z
        p.moveTo(52f * unit, 36f * unit)
        p.lineTo(52f * unit, 44f * unit)
        p.quadraticBezierTo(52f * unit, 52f * unit, 44f * unit, 52f * unit)
        p.lineTo(36f * unit, 52f * unit)
        p.lineTo(36f * unit, 44f * unit)
        p.lineTo(42f * unit, 44f * unit)
        p.quadraticBezierTo(44f * unit, 44f * unit, 44f * unit, 42f * unit)
        p.lineTo(44f * unit, 36f * unit)
        p.close()

        drawPath(p, color = fillColor)
    }
}

private fun rectOf(x: Float, y: Float, w: Float, h: Float, unit: Float): Rect =
    Rect(offset = Offset(x * unit, y * unit), size = Size(w * unit, h * unit))
