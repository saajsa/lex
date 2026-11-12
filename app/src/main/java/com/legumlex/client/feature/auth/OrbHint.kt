package com.legumlex.client.feature.auth

import com.legumlex.client.feature.desk.KeyValueStore

/*
 * The first-time hint beside the Lex button (REQ-013): "How can I help you?" typed out, then "Press and hold for more" when there is an arc to hold for.
 * It shows to someone who has not yet touched the button, and stops after the sign-in screen has been seen HINT_MAX_VISITS times without a touch, so it
 * does not nag. This file is the rule, with no screen in it.
 */

const val HINT_MAX_VISITS = 3
const val HINT_FIRST = "How can I help you?"
const val HINT_SECOND = "Press and hold for more"

class OrbHint(private val store: KeyValueStore) {
    /** The sign-in screen has been entered: counts the visit, and says whether the hint shows on it. */
    fun visit(): Boolean {
        if (touched()) return false
        val n = (store.get(VISITS)?.toIntOrNull() ?: 0) + 1
        store.put(VISITS, n.toString())
        return n <= HINT_MAX_VISITS
    }

    /** Whether it shows now (a rotation, say), without counting a visit. */
    fun shouldShow(): Boolean = !touched() && (store.get(VISITS)?.toIntOrNull() ?: 0) <= HINT_MAX_VISITS

    /** The button was touched (a tap or a long press), or the hint itself was tapped: never again. */
    fun dismiss() { store.put(TOUCHED, "1") }

    /** For a re-test (the Debug menu): as a fresh install. */
    fun reset() { store.remove(TOUCHED); store.remove(VISITS) }

    private fun touched() = store.get(TOUCHED) == "1"

    companion object { const val TOUCHED = "touched"; const val VISITS = "visits" }
}

/** The lines the hint types, in order: the second only where the button has an arc to hold for. */
fun hintLines(hasPetals: Boolean): List<String> = if (hasPetals) listOf(HINT_FIRST, HINT_SECOND) else listOf(HINT_FIRST)

/** The text typed so far when [typed] characters of the lines have been written (a break between lines is free). */
fun hintTyped(lines: List<String>, typed: Int): List<String> {
    var left = typed.coerceAtLeast(0)
    return lines.map { l -> val n = minOf(left, l.length); left -= n; l.take(n) }
}

/** The second line is the same ink at this strength (it stays above 0.8, so it still passes the ink rule on the dark fill). */
const val HINT_SECOND_ALPHA = 0.85f
/** The hint sits in the band above the button: this far (dp) above the band's foot, the pointer's height, clear of the button's badge. */
const val HINT_LIFT = 14f
const val HINT_POINTER = 6f
