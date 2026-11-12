package com.legumlex.client.feature.auth

import kotlin.math.atan2
import kotlin.math.hypot

/*
 * The Lex Intelligence button's long-press menu (REQ-013 013.17): an arc of petals above the button. This file is the part with no screen in
 * it: which petals show and where each goes (from the server's switches), and the arc's geometry.
 */

/** The functions Lex Intelligence offers on the arc, left to right. */
enum class Petal(val label: String) {
    OpenAccount("Open An Account"), RegisterCounsel("Register As Counsel"), DiscussMatter("Discuss A Matter"),
}

/** google_config's switches, read before sign-in: account_desk and counsel_desk (the desk takes the wish), sign_up (the newcomer's screens are open). */
data class DeskSwitches(val accountDesk: Boolean = false, val counselDesk: Boolean = false, val signUp: Boolean = false)

/** Reads the switches from google_config. A field the server does not send yet (account_desk before 013.17a) is off. */
fun deskSwitches(config: Map<String, Any?>?): DeskSwitches =
    DeskSwitches(config?.get("account_desk") == true, config?.get("counsel_desk") == true, config?.get("sign_up") == true)

const val MSG_OPEN_ACCOUNT = "I would like to open an account"
const val MSG_REGISTER_COUNSEL = "I would like to register as counsel"

/** Where a petal leads. */
sealed interface PetalRoute {
    /** Open the chat panel and send [message] as the first message. */
    data class Chat(val message: String) : PetalRoute
    /** The existing Onboard Now screen. */
    data object OnboardScreen : PetalRoute
    /** The existing counsel registration screen. */
    data object CounselScreen : PetalRoute
    /** The chat's Google sign-in step, with no guest message spent. */
    data object GoogleSignIn : PetalRoute
}

/** The route for [petal], or null when it has none (hidden): sign-up shut and the desk off. */
fun petalRoute(petal: Petal, s: DeskSwitches): PetalRoute? = when (petal) {
    Petal.OpenAccount -> if (s.accountDesk) PetalRoute.Chat(MSG_OPEN_ACCOUNT) else if (s.signUp) PetalRoute.OnboardScreen else null
    Petal.RegisterCounsel -> if (s.counselDesk) PetalRoute.Chat(MSG_REGISTER_COUNSEL) else if (s.signUp) PetalRoute.CounselScreen else null
    Petal.DiscussMatter -> PetalRoute.GoogleSignIn
}

/** The petals that have a route, in order. */
fun visiblePetals(s: DeskSwitches): List<Petal> = Petal.values().filter { petalRoute(it, s) != null }

/** What the chat panel does on opening because a petal asked for it. */
sealed interface PanelStart {
    data class Message(val text: String) : PanelStart
    data object SignIn : PanelStart
}

// ---- The arc (dp; angles in degrees, 0 = right, 90 = down, 270 = up, as the canvas draws) ----

/** The inner radius clears the orb and its AI badge (the badge's far corner is about 60 dp from the orb's centre at 1.7x). */
const val PETAL_INNER = 64f
/** The ring is 100 dp thick: an icon, two lines of label and at least 14 dp of room to every edge of the petal. */
const val PETAL_OUTER = 164f
/** The orb sits [ORB_GAP] below whatever is above it (the card's squares, or the Debug chip), in a box [ORB_BOX] high, the orb at its middle. */
const val ORB_GAP = 136f
const val ORB_BOX = 88f
/** The arc's top edge must stay this far below the squares and the Debug chip. */
const val PETAL_CLEARANCE = 12f
/** The angle between one petal's middle and the next: wide, so the outer petals reach a little below the orb's horizontal. */
const val PETAL_STEP = 70f
/** The even gap between neighbouring petals, and the radius of their corners (as near Mylex's 4/6/8/12 as a ring segment allows), in dp. */
const val PETAL_GAP = 6f
const val PETAL_CORNER = 6f
/** Room round the ring in its window for the overshoot, the lift and the shadow, and below the baseline for the shadow. */
const val PETAL_MARGIN = 12f
/** Room below the orb's centre in the window, for the outer petals that hang below the horizontal (164 dp x sin 15 degrees, about 42 dp) and the shadow. */
const val PETAL_BELOW = 64f

/** How far the arc's top edge is below what is above the orb: the gap, plus the orb's half box, less the ring's outer radius. */
fun arcClearance(gap: Float = ORB_GAP, box: Float = ORB_BOX, outer: Float = PETAL_OUTER): Float = gap + box / 2f - outer

/** Half the angle a petal spans at radius [rho] dp: its step's half, less what the gap and the corner radius take off that edge (a parallel offset of the radial edge). */
fun petalHalfSpan(rho: Float, edgeInset: Float = PETAL_GAP / 2f + PETAL_CORNER): Float =
    PETAL_STEP / 2f - Math.toDegrees(Math.asin((edgeInset / rho).toDouble())).toFloat()

/** The motion of the arc and the orb, in ms. Reduced motion (the system's animations off) is no motion at all and no idle breathing. */
data class PetalMotion(val openStaggerMs: Int, val foldStaggerMs: Int, val foldMs: Int, val pulseMs: Int, val pressScale: Float, val idle: Boolean)

fun petalMotion(reduced: Boolean): PetalMotion =
    if (reduced) PetalMotion(0, 0, 0, 0, 1f, false) else PetalMotion(40, 30, 150, 110, 0.94f, true)

/** The foot block (the date, the day, the rule, the maxim) at its smaller size, in dp, and what the orb's zone takes above it. */
object FootGeometry {
    const val WEEKDAY_LINE = 24f
    const val DAY_LINE = 28f
    const val RULE_GAPS = 6f + 1f + 6f
    const val MAXIM_LINE = 18f
    const val MEANING_LINE = 14f
    const val GAP_ABOVE = 6f
    /** One line each for the maxim and its meaning (a longer one wraps below this). */
    const val BLOCK = WEEKDAY_LINE + DAY_LINE + RULE_GAPS + MAXIM_LINE + 2f + MEANING_LINE + GAP_ABOVE
    /** The orb's zone: its gap, its box, and the foot block. */
    const val ORB_AND_FOOT = ORB_GAP + ORB_BOX + BLOCK
}

/** The middle angle of petal [i] of [n], the set centred on straight up. */
fun petalMid(i: Int, n: Int): Float = 270f + (i - (n - 1) / 2f) * PETAL_STEP

/** The petal under a point [dx], [dy] dp from the button's centre (y down), or null (inside the button, or beyond the ring). */
fun petalAt(dx: Float, dy: Float, n: Int): Int? {
    val r = hypot(dx, dy)
    if (n <= 0 || r < PETAL_INNER || r > PETAL_OUTER + 24f) return null
    val ang = ((Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()) + 360f) % 360f
    for (i in 0 until n) {
        val diff = Math.abs(((ang - petalMid(i, n) + 540f) % 360f) - 180f)
        if (diff <= PETAL_STEP / 2f) return i
    }
    return null
}

// ---- The labels, placed by measuring the rendered text ----

const val ICON = 20f
const val ICON_GAP = 4f
/** At least this much room (dp) from the label block to every edge of its petal: both sides, the inner arc and the outer arc. */
const val LABEL_MARGIN = 14f
const val LABEL_BASE = 12f
const val LABEL_MIN = 9f
const val LABEL_LINE_RATIO = 1.17f

/** An icon over up to two lines of label, at [sizeDp] a line: its size in dp, and the lines. */
data class LabelBlock(val sizeDp: Float, val lines: List<String>, val width: Float, val height: Float)

/** The block for [label] at [sizeDp], broken where the two lines come out narrowest as [measure] (text, size in dp, giving the width in dp) says. */
fun labelBlock(label: String, sizeDp: Float, measure: (String, Float) -> Float): LabelBlock {
    val words = label.split(" ")
    val cuts = (1 until words.size).map { listOf(words.take(it).joinToString(" "), words.drop(it).joinToString(" ")) to it }
    // A connector word (As, An, A) stays with the word before it: "Register As / Counsel", not "Register / As Counsel".
    val kept = cuts.filter { (_, at) -> words[at].length > 2 }.ifEmpty { cuts }
    val lines = if (words.size < 2) listOf(label) else kept.minByOrNull { (pair, _) -> pair.maxOf { measure(it, sizeDp) } }!!.first
    val w = maxOf(ICON, lines.maxOf { measure(it, sizeDp) })
    return LabelBlock(sizeDp, lines, w, ICON + ICON_GAP + lines.size * sizeDp * LABEL_LINE_RATIO)
}

/** The middle of petal [i] of [n] (the centroid of its ring segment), in dp from the orb's centre, y down. */
fun petalCentroid(i: Int, n: Int): Pair<Float, Float> {
    val th = Math.toRadians(PETAL_STEP.toDouble())
    val r = (2.0 / 3.0) * (Math.pow(PETAL_OUTER.toDouble(), 3.0) - Math.pow(PETAL_INNER.toDouble(), 3.0)) /
        (Math.pow(PETAL_OUTER.toDouble(), 2.0) - Math.pow(PETAL_INNER.toDouble(), 2.0)) * Math.sin(th / 2) / (th / 2)
    val a = Math.toRadians(petalMid(i, n).toDouble())
    return (r * Math.cos(a)).toFloat() to (r * Math.sin(a)).toFloat()
}

/**
 * The least room (dp) between a block of [w] x [h] dp centred in petal [i] of [n] and the petal's visible edges: the inner arc, the outer arc,
 * and the two sides (each a half gap inside the step's own edge).
 */
fun labelMargin(i: Int, n: Int, w: Float, h: Float): Float {
    val (cx, cy) = petalCentroid(i, n)
    val hw = w / 2f; val hh = h / 2f
    val corners = listOf(-1f, 1f).flatMap { sx -> listOf(-1f, 1f).map { sy -> (cx + sx * hw) to (cy + sy * hh) } }
    val outer = PETAL_OUTER - corners.maxOf { hypot(it.first, it.second) }
    val qx = if (cx - hw <= 0f && 0f <= cx + hw) 0f else if (cx - hw > 0f) cx - hw else cx + hw
    val qy = if (cy - hh <= 0f && 0f <= cy + hh) 0f else if (cy - hh > 0f) cy - hh else cy + hh
    var best = minOf(outer, hypot(qx, qy) - PETAL_INNER)
    val mid = Math.toRadians(petalMid(i, n).toDouble())
    for (side in listOf(-1.0, 1.0)) {
        val a = mid + side * Math.toRadians(PETAL_STEP / 2.0)
        var nx = -Math.sin(a); var ny = Math.cos(a)
        if (nx * Math.cos(mid) + ny * Math.sin(mid) < 0) { nx = -nx; ny = -ny }
        for ((x, y) in corners) best = minOf(best, (x * nx + y * ny - PETAL_GAP / 2.0).toFloat())
    }
    return best
}

/** The largest label (12 dp at the system's text size, scaled with it, but not below [LABEL_MIN]) with [LABEL_MARGIN] to every petal edge: shrunk, never clipped. */
fun fitLabel(i: Int, n: Int, label: String, fontScale: Float, measure: (String, Float) -> Float): LabelBlock {
    var size = LABEL_BASE * fontScale
    while (true) {
        val b = labelBlock(label, size, measure)
        if (labelMargin(i, n, b.width, b.height) >= LABEL_MARGIN || size <= LABEL_MIN) return b
        size = maxOf(LABEL_MIN, size - 0.5f)
    }
}

// ---- The idle ripple ----

const val RIPPLE_RINGS = 3
const val RIPPLE_CYCLE_MS = 2800
/** The ripple's faintest and strongest hairline (ink at this alpha), and its one still ring for reduced motion. */
const val RIPPLE_MAX_ALPHA = 0.19f
const val RIPPLE_STILL_ALPHA = 0.08f
/** The ripple's hairline, in dp. */
const val RIPPLE_STROKE = 1.25f

/** A ring's alpha at [p] (0 at the orb, 1 at the arc's reach): it comes in over the first tenth and fades out to nothing. */
fun rippleAlpha(p: Float): Float = RIPPLE_MAX_ALPHA * minOf(1f, p / 0.1f) * (1f - p) * (1f - p)
