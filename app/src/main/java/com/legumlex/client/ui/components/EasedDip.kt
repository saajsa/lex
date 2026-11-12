package com.legumlex.client.ui.components

import androidx.compose.ui.graphics.Path

/*
 * The eased S that carries an edge down into a dip (and back out), shared by the Counsel bar (the dip in its top edge, where the Cause List
 * button sits) and the Sign In card (the dip in its bottom edge, where its buttons sit), so the two curves are the same.
 *
 * A shoulder runs [width] along the edge (signed: negative goes left) and [depth] across it (signed: into the shape). Going IN, it leaves the
 * edge level and arrives level at the dip's floor; going OUT it climbs back. The control points are the bar's own: 0.45 and 0.38 of the
 * width going in, 0.62 and 0.55 going out.
 */

/** The cubic's control points and end for a shoulder starting at ([x], [y]): [c1x, c1y, c2x, c2y, endX, endY]. */
internal fun easedDipControls(x: Float, y: Float, width: Float, depth: Float, out: Boolean): FloatArray =
    if (!out) floatArrayOf(x + width * 0.45f, y, x + width * 0.38f, y + depth, x + width, y + depth)
    else floatArrayOf(x + width * 0.62f, y, x + width * 0.55f, y + depth, x + width, y + depth)

/** Down into a dip: from the edge at ([x], [y]) to its floor, [width] along and [depth] across. */
fun Path.easedDipIn(x: Float, y: Float, width: Float, depth: Float) {
    val c = easedDipControls(x, y, width, depth, out = false)
    cubicTo(c[0], c[1], c[2], c[3], c[4], c[5])
}

/** Back out of a dip: from its floor at ([x], [y]) to the edge, [width] along and [depth] across (back toward the edge). */
fun Path.easedDipOut(x: Float, y: Float, width: Float, depth: Float) {
    val c = easedDipControls(x, y, width, depth, out = true)
    cubicTo(c[0], c[1], c[2], c[3], c[4], c[5])
}
