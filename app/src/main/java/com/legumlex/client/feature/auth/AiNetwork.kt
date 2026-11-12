package com.legumlex.client.feature.auth

import java.util.Random
import kotlin.math.hypot

/*
 * The AI texture behind the Lex Intelligence button (REQ-013 013.20): faint dots joined by hairlines, like a neural net, densest round the button
 * and thinning outward. This file is the layout and the twinkle, with no screen in it, so they can be tested: the layout is deterministic for a seed
 * and a size (never jumps between launches), keeps off the button, and fades to nothing at the top and the foot of its area.
 */

/** The dots and lines of one layout, in dp from the area's top-left. Arrays, so drawing allocates nothing. */
class AiNetwork(
    val w: Float, val h: Float, val hubX: Float, val hubY: Float, val hubR: Float,
    val nx: FloatArray, val ny: FloatArray, val nodeAlpha: FloatArray, val nodeSize: FloatArray,
    /** A node's twinkle: its period in seconds (0 = it does not twinkle) and phase (0..1). */
    val twPeriod: IntArray, val twPhase: FloatArray,
    /** An edge's ends (x1, y1, x2, y2 per edge), its alpha, and whether it is a spoke into the button's ring. */
    val ex: FloatArray, val edgeAlpha: FloatArray, val spoke: BooleanArray,
) {
    val nodes get() = nx.size
    val edges get() = edgeAlpha.size
}

const val NET_SEED = 20261111L
/** The button's outer radius (dp): the orb at 1.7x is 75 dp across. */
const val NET_HUB_R = 37.4f
/** Nothing is placed closer to the button than its radius plus this, so the spokes into its ring have room to read. */
const val NET_HUB_CLEAR = 16f
/** Fades: at the area's top over this many dp, and at its foot (before the date text) over this many. */
const val NET_FADE_TOP = 10f
const val NET_FADE_BOTTOM = 34f
const val NET_NEAR = 180f
const val NET_MAX_EDGE = 70f
/** The master period (seconds): every twinkle period (2, 3 or 4 s) divides it, so the loop is seamless. */
const val NET_MASTER_S = 12

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)

/** The least spacing (dp) between dots at [r] dp from the button: close near it, sparse out at the edges. */
fun netSpacing(r: Float): Float = lerp(20f, 52f, (r - 60f) / 220f)

private fun fade(y: Float, h: Float): Float =
    minOf((y / NET_FADE_TOP).coerceIn(0f, 1f), ((h - y) / NET_FADE_BOTTOM).coerceIn(0f, 1f))

private fun distToSegment(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
    val dx = x2 - x1; val dy = y2 - y1
    val len2 = dx * dx + dy * dy
    val t = if (len2 == 0f) 0f else (((px - x1) * dx + (py - y1) * dy) / len2).coerceIn(0f, 1f)
    return hypot(px - (x1 + t * dx), py - (y1 + t * dy))
}

fun buildNetwork(w: Float, h: Float, hubX: Float, hubY: Float, hubR: Float = NET_HUB_R, seed: Long = NET_SEED): AiNetwork {
    val rnd = Random(seed)
    val xs = ArrayList<Float>(); val ys = ArrayList<Float>()
    // Dart throwing: a seeded candidate is kept if it is off the button, clear of the others by the spacing for its distance, and (thinning
    // outward) survives a draw that gets harder with distance.
    repeat(3000) {
        if (xs.size >= 150) return@repeat
        val x = rnd.nextFloat() * w; val y = 6f + rnd.nextFloat() * (h - 6f - 12f)
        val r = hypot(x - hubX, y - hubY)
        val keep = rnd.nextFloat() < lerp(1f, 0.35f, (r - NET_NEAR) / 200f)
        if (!keep || r < hubR + NET_HUB_CLEAR) return@repeat
        val gap = netSpacing(r)
        for (j in xs.indices) if (hypot(x - xs[j], y - ys[j]) < gap) return@repeat
        xs.add(x); ys.add(y)
    }
    val n = xs.size
    val nx = FloatArray(n) { xs[it] }; val ny = FloatArray(n) { ys[it] }
    val nodeAlpha = FloatArray(n); val nodeSize = FloatArray(n); val twPeriod = IntArray(n); val twPhase = FloatArray(n)
    val periods = intArrayOf(2, 3, 4)
    for (i in 0 until n) {
        val r = hypot(nx[i] - hubX, ny[i] - hubY)
        val near = 1f - ((r - 60f) / (NET_NEAR + 140f)).coerceIn(0f, 1f)
        nodeAlpha[i] = lerp(0.10f, 0.16f, near) * fade(ny[i], h)
        nodeSize[i] = lerp(1.6f, 2.4f, near)
        // The draws are always made, so the layout of the rest does not depend on which dots twinkle.
        val tw = rnd.nextFloat() < 0.28f; val p = periods[rnd.nextInt(3)]; val ph = rnd.nextFloat()
        twPeriod[i] = if (tw) p else 0; twPhase[i] = ph
    }
    // Lines: each dot to its 2 nearest (3 near the button) within the longest line, never through the button.
    val ex = ArrayList<Float>(); val ea = ArrayList<Float>(); val sp = ArrayList<Boolean>()
    val seen = HashSet<Long>()
    fun edgeAlphaAt(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val mx = (x1 + x2) / 2f; val my = (y1 + y2) / 2f
        val r = hypot(mx - hubX, my - hubY)
        return lerp(0.09f, 0.06f, (r - NET_NEAR) / 180f) * fade(my, h)
    }
    for (i in 0 until n) {
        val r = hypot(nx[i] - hubX, ny[i] - hubY)
        val order = (0 until n).filter { it != i }.sortedBy { hypot(nx[it] - nx[i], ny[it] - ny[i]) }
        var made = 0
        for (j in order) {
            if (made >= (if (r < NET_NEAR) 3 else 2)) break
            if (hypot(nx[j] - nx[i], ny[j] - ny[i]) > NET_MAX_EDGE) break
            if (distToSegment(hubX, hubY, nx[i], ny[i], nx[j], ny[j]) < hubR + 4f) continue
            val key = minOf(i, j).toLong() * 100000L + maxOf(i, j)
            made++
            if (!seen.add(key)) continue
            ex.add(nx[i]); ex.add(ny[i]); ex.add(nx[j]); ex.add(ny[j]); ea.add(edgeAlphaAt(nx[i], ny[i], nx[j], ny[j])); sp.add(false)
        }
    }
    // Spokes: a few dots round the button, well apart in angle, each joined to the button's outer ring, so the button reads as the hub.
    val near = (0 until n).filter { hypot(nx[it] - hubX, ny[it] - hubY) < hubR + 70f }.sortedBy { hypot(nx[it] - hubX, ny[it] - hubY) }
    val taken = ArrayList<Double>()
    for (i in near) {
        if (taken.size >= 5) break
        val ang = Math.atan2((ny[i] - hubY).toDouble(), (nx[i] - hubX).toDouble())
        if (taken.any { Math.abs(Math.atan2(Math.sin(ang - it), Math.cos(ang - it))) < Math.toRadians(50.0) }) continue
        taken.add(ang)
        val r = hypot(nx[i] - hubX, ny[i] - hubY)
        val rx = hubX + (nx[i] - hubX) / r * hubR; val ry = hubY + (ny[i] - hubY) / r * hubR
        ex.add(nx[i]); ex.add(ny[i]); ex.add(rx); ex.add(ry); ea.add(0.10f * fade(ny[i], h)); sp.add(true)
    }
    return AiNetwork(w, h, hubX, hubY, hubR, nx, ny, nodeAlpha, nodeSize, twPeriod, twPhase,
        ex.toFloatArray(), ea.toFloatArray(), sp.toBooleanArray())
}

/** A dot's brightness multiplier at [seconds] (0 to [NET_MASTER_S]): 1, or up to 1.9 for a moment if it twinkles. Always 1 with reduced motion. */
fun twinkle(seconds: Float, period: Int, phase: Float, reduced: Boolean): Float {
    if (reduced || period == 0) return 1f
    val x = ((seconds / period) + phase) % 1f
    if (x >= 0.5f) return 1f
    val s = Math.sin(Math.PI * x / 0.5).toFloat()
    return 1f + 0.9f * s * s
}
