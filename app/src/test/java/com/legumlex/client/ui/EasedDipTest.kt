package com.legumlex.client.ui

import com.legumlex.client.feature.auth.SignInAction
import com.legumlex.client.feature.auth.edgeActions
import com.legumlex.client.ui.components.easedDipControls
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** REQ-013 013.11: the dip curve shared by the Counsel bar and the Sign In card. */
class EasedDipTest {
    private val eps = 0.001f

    @Test fun theBarsDipIsReproducedExactly() {
        // The bar's own curve (CounselBarShape): hw 62 dp, depth 34 dp, centred at cx.
        val cx = 200f; val hw = 62f; val d = 34f
        // Going in: cubicTo(cx - hw * 0.55, 0, cx - hw * 0.62, d, cx, d), starting at (cx - hw, 0).
        assertArrayEquals(floatArrayOf(cx - hw * 0.55f, 0f, cx - hw * 0.62f, d, cx, d), easedDipControls(cx - hw, 0f, hw, d, out = false), eps)
        // Coming out: cubicTo(cx + hw * 0.62, d, cx + hw * 0.55, 0, cx + hw, 0), starting at (cx, d).
        assertArrayEquals(floatArrayOf(cx + hw * 0.62f, d, cx + hw * 0.55f, 0f, cx + hw, 0f), easedDipControls(cx, d, hw, -d, out = true), eps)
    }

    @Test fun theCardsDipRunsTheOtherWayWithTheSameCurve() {
        // Leftward along the card's bottom edge (negative width), up into the card (negative depth).
        val c = easedDipControls(300f, 100f, -44f, -34f, out = false)
        assertArrayEquals(floatArrayOf(300f - 44 * 0.45f, 100f, 300f - 44 * 0.38f, 66f, 256f, 66f), c, eps)
        // The curve leaves and arrives level: the first control is on the edge's line, the second on the floor's.
        assertEquals(100f, c[1], eps); assertEquals(c[5], c[3], eps)
    }

    @Test fun theDipFitsItsButtons() {
        // 48 dp squares 20 dp apart: two take 116 dp, one 48.
        assertEquals(116f, com.legumlex.client.feature.auth.dipRowWidth(2), eps)
        assertEquals(48f, com.legumlex.client.feature.auth.dipRowWidth(1), eps)
        // The flat run is the row less what the end buttons tuck into the shoulders (12 each); narrower with two; none, none.
        assertEquals(92f, com.legumlex.client.feature.auth.dipFlatRun(2), eps)
        assertEquals(24f, com.legumlex.client.feature.auth.dipFlatRun(1), eps)
        assertEquals(0f, com.legumlex.client.feature.auth.dipFlatRun(0), eps)
        assertTrue(com.legumlex.client.feature.auth.dipFlatRun(2) > com.legumlex.client.feature.auth.dipFlatRun(1))
    }

    @Test fun theDipClearsTheCardsLeftOnANarrowPhone() {
        val g = com.legumlex.client.feature.auth.DipGeometry
        // A 360 dp phone: the card is 328 dp (16 dp gutters). The dip's left end is where its flat run and shoulders start.
        val card = 328f
        for (n in listOf(1, 2)) {
            val dip = com.legumlex.client.feature.auth.dipFlatRun(n) + 2 * g.SHOULDER
            val leftEnd = card - g.END - dip
            // Well clear of the card's rounded corner (12 dp), and the buttons sit inside the card.
            assertTrue("n=$n left end $leftEnd", leftEnd >= 12f + 16f)
            assertTrue(com.legumlex.client.feature.auth.dipRowWidth(n) + g.END + g.SHOULDER - g.TUCK <= card)
        }
    }

    @Test fun theSquaresAreThePrintButtonScaledTo48() {
        val g = com.legumlex.client.feature.auth.DipGeometry
        assertEquals(48f, g.BUTTON, eps)
        // 56 -> 48 is 6/7: the dip's depth (34), the overhang (26) and the corner (18) keep the same proportions.
        assertEquals(34f * 48 / 56, g.DEPTH, 0.5f)
        assertEquals(26f * 48 / 56, g.OVERHANG, 0.5f)
        assertEquals(18f * 48 / 56, g.RADIUS, 0.5f)
        // The part of a button inside the dip leaves the print button's 4 dp gap, scaled.
        assertEquals((34f - 30f) * 48 / 56, g.DEPTH - (g.BUTTON - g.OVERHANG), 0.6f)
    }

    @Test fun theDipFollowsTheViews() {
        assertEquals(2, edgeActions(codeStep = false, emailView = false).size)
        // The Email view narrows the dip to fit one; the code step has none (a plain card).
        assertEquals(listOf(SignInAction.Google), edgeActions(codeStep = false, emailView = true))
        assertEquals(0, edgeActions(codeStep = true, emailView = false).size)
    }
}
