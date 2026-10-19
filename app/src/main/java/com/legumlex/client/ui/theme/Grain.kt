package com.legumlex.client.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.random.Random

/**
 * The paper grain of the owner's reference: a fine, faint speckle over every
 * screen, light and dark. One 128px tile of random dark and light specks,
 * made once and repeated; drawn above the content at a low alpha so it reads
 * as texture, never as noise on the text.
 */
private val grainTile: ImageBitmap by lazy {
    val n = 128
    val px = IntArray(n * n)
    val r = Random(7)
    for (i in px.indices) {
        val v = r.nextInt(100)
        px[i] = when {
            v < 18 -> 0x26000000            // a dark speck
            v < 30 -> 0x22FFFFFF            // a light speck
            else -> 0x00000000
        }
    }
    android.graphics.Bitmap.createBitmap(px, n, n, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}

@Composable
fun Modifier.grain(strength: Float = 0.55f): Modifier {
    val brush = ShaderBrush(ImageShader(grainTile, TileMode.Repeated, TileMode.Repeated))
    return this.drawWithContent {
        drawContent()
        drawRect(brush = brush, alpha = strength)
    }
}
