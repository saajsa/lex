package com.legumlex.client.feature.board

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// -----------------------------------------------------------------------------
// The Home's specialty ring, as numbers: how the arcs are laid out, how a score is shown, what a tap
// on an arc says. No drawing here, so it can be tested. The colours and the order are the web's.
// -----------------------------------------------------------------------------

/** The ring's colours by specialty (the web's, as given): criminal in warm shades, civil in cool ones, NI Act gold. */
internal val SpecialtyColours: Map<String, Long> = mapOf(
    "bail" to 0xFFB3261E, "crl_misc" to 0xFFE8735A, "crl_appeals" to 0xFFA84A1C, "trials" to 0xFF7B2D5E,
    "suits" to 0xFF1F3A6E, "civil_appeals" to 0xFF16787F, "execution" to 0xFF5B6B82, "family" to 0xFF6F9A74, "other_civil" to 0xFF4F9FD0,
    "ni" to 0xFFF5C518, "unknown" to 0xFFBDB9B0,
)

/** The order the arcs run round the ring, clockwise from the top. */
internal val RingOrder = listOf("ni", "suits", "civil_appeals", "execution", "family", "other_civil", "bail", "crl_misc", "crl_appeals", "trials", "unknown")

/** The steps a side's score climbs toward. */
internal val ScoreSteps = listOf(10, 25, 50, 100)

/** One arc: the specialty, its start and sweep in degrees clockwise from the top. */
data class RingArc(val key: String, val label: String, val side: String, val count: Int, val start: Float, val sweep: Float)

private const val Gap = 2.4f

/** The arcs for the specialties that have cases, each as long as its share of all the cases, with a small gap between them. */
internal fun ringArcs(specialties: List<Specialty>): List<RingArc> {
    val by = specialties.associateBy { it.key }
    val total = RingOrder.sumOf { by[it]?.count ?: 0 }
    if (total <= 0) return emptyList()
    var at = 0f
    val out = mutableListOf<RingArc>()
    for (k in RingOrder) {
        val sp = by[k] ?: continue
        if (sp.count < 1) continue
        val span = sp.count.toFloat() / total * 360f
        val whole = total == sp.count
        val a0 = at + if (whole) 0f else Gap / 2
        val a1 = at + span - if (whole) 0f else Gap / 2
        out += RingArc(k, sp.label, sp.side, sp.count, a0, if (whole) 359.9f else a1 - a0)
        at += span
    }
    return out
}

/** A score as the web shows it: "8.5", "7", "0". */
internal fun scoreText(v: Double): String {
    val r = Math.round(v * 10.0) / 10.0
    return if (r == Math.floor(r)) r.toLong().toString() else String.format(Locale.ENGLISH, "%.1f", r)
}

/** How many of the steps (10, 25, 50, 100) a score has reached. */
internal fun stepsReached(score: Double): Int = ScoreSteps.count { score >= it }

/** The tooltip on an arc: the specialty's name only ("Suits"); the count and points show in the centre and the legend. */
internal fun sliceWord(sp: Specialty): String = sp.label

/** The small word above the centre number at rest: the leading side, or EVEN when the two sides score the same. */
internal fun restCaption(sc: HomeScore): String =
    if (Math.round(sc.civil * 10) == Math.round(sc.criminal * 10)) "EVEN" else if (sc.lead == "civil") "CIVIL" else "CRIMINAL"

/** The centre number at rest: the leading side's score (an even score shows either, they are the same). */
internal fun restScore(sc: HomeScore): Double = if (sc.lead == "civil") sc.civil else sc.criminal

/** How full each step bar is, 0 to 1: segments 0-10, 10-25, 25-50, 50-100; full once reached, the current one by its fraction, later ones empty. */
internal fun stepFill(score: Double): List<Float> {
    val edges = listOf(0) + ScoreSteps
    return ScoreSteps.indices.map { i ->
        val lo = edges[i]; val hi = edges[i + 1]
        ((score - lo) / (hi - lo)).toFloat().coerceIn(0f, 1f)
    }
}

/** The small word above the centre number when an arc is chosen. */
internal fun sideCaption(side: String): String = when (side) {
    "criminal" -> "CRIMINAL"
    "civil" -> "CIVIL"
    "both" -> "BOTH SIDES"
    else -> "NEITHER SIDE"
}

/** The arc under a point (offset from the ring's centre, in the ring's own units), or null: its band is [radius - half, radius + half]. */
internal fun arcAt(arcs: List<RingArc>, dx: Float, dy: Float, radius: Float, half: Float): RingArc? {
    val r = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
    if (r < radius - half || r > radius + half) return null
    // Degrees clockwise from the top.
    var deg = Math.toDegrees(Math.atan2(dx.toDouble(), -dy.toDouble())).toFloat()
    if (deg < 0f) deg += 360f
    return arcs.firstOrNull { deg >= it.start - Gap / 2 && deg <= it.start + it.sweep + Gap / 2 }
}

/** "Civil 8 · Criminal 1 · NI Act 1 · 1 not known", the Diary tile's line. */
internal fun diaryLine(t: HomeTiles): String =
    "Civil ${t.civil} · Criminal ${t.criminal}" + (if (t.ni > 0) " · NI Act ${t.ni}" else "") + (if (t.unknown > 0) " · ${t.unknown} not known" else "")

/** "Busiest: Sun 8 Nov, 3", or "Nothing listed yet". */
internal fun busiestLine(t: HomeTiles): String {
    val d = runCatching { LocalDate.parse(t.busiestDate) }.getOrNull() ?: return "Nothing listed yet"
    return "Busiest: " + d.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)) + ", " + t.busiestCount
}

internal fun oldestLine(t: HomeTiles): String = t.oldestRunningYear?.let { "Oldest running: filed $it" } ?: "No running case yet"

/** "ZZ/9001/2015 · Punjab": the enrolment number and the Bar Council, whichever are given. */
internal fun barLine(p: HomeProfile): String = listOf(p.enrolmentNo, p.barCouncil).filter { it.isNotBlank() }.joinToString(" · ")

/** "4 courts · Ludhiana · Jalandhar". */
internal fun courtsLine(p: HomeProfile): String =
    (listOf("${p.courts} ${if (p.courts == 1) "court" else "courts"}") + p.districts).joinToString(" · ")
