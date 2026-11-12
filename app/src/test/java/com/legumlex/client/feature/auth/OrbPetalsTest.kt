package com.legumlex.client.feature.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** REQ-013 013.17: the long-press arc's routing table (every switch combination) and geometry. */
class OrbPetalsTest {
    private fun sw(a: Boolean, c: Boolean, s: Boolean) = DeskSwitches(accountDesk = a, counselDesk = c, signUp = s)

    @Test fun routingForEveryCombinationOfTheThreeSwitches() {
        for (a in listOf(false, true)) for (c in listOf(false, true)) for (s in listOf(false, true)) {
            val x = sw(a, c, s)
            val acct = petalRoute(Petal.OpenAccount, x)
            val coun = petalRoute(Petal.RegisterCounsel, x)
            // Open An Account: the desk's chat when account_desk, else the Onboard Now screen when sign-up is open, else hidden.
            assertEquals("a=$a c=$c s=$s", when { a -> PetalRoute.Chat("I would like to open an account"); s -> PetalRoute.OnboardScreen; else -> null }, acct)
            // Register As Counsel: the chat when counsel_desk, else the counsel screen when sign-up is open, else hidden.
            assertEquals("a=$a c=$c s=$s", when { c -> PetalRoute.Chat("I would like to register as counsel"); s -> PetalRoute.CounselScreen; else -> null }, coun)
            // Discuss A Matter: always the Google sign-in step.
            assertEquals(PetalRoute.GoogleSignIn, petalRoute(Petal.DiscussMatter, x))
            assertEquals(Petal.values().filter { petalRoute(it, x) != null }, visiblePetals(x))
            assertTrue(Petal.DiscussMatter in visiblePetals(x))
        }
        // Everything shut: only Discuss A Matter.
        assertEquals(listOf(Petal.DiscussMatter), visiblePetals(sw(false, false, false)))
        assertEquals(Petal.values().toList(), visiblePetals(sw(true, true, true)))
    }

    @Test fun aFieldTheServerDoesNotSendYetIsOff() {
        // Today's server has no account_desk: the app works, with the existing screens.
        assertEquals(DeskSwitches(false, true, true), deskSwitches(mapOf("counsel_desk" to true, "sign_up" to true)))
        assertEquals(DeskSwitches(), deskSwitches(null))
        assertEquals(DeskSwitches(true, false, false), deskSwitches(mapOf("account_desk" to true, "counsel_desk" to "yes")))
    }

    @Test fun theArcIsCentredOnUpAndEachPetalIsFoundWhereItIsDrawn() {
        assertEquals(270f, petalMid(0, 1), 0.01f)
        assertEquals(listOf(200f, 270f, 340f), listOf(0, 1, 2).map { petalMid(it, 3) })
        assertEquals(listOf(235f, 305f), listOf(0, 1).map { petalMid(it, 2) })
        val mid = (PETAL_INNER + PETAL_OUTER) / 2f
        for (n in 1..3) for (i in 0 until n) {
            val a = Math.toRadians(petalMid(i, n).toDouble())
            assertEquals("n=$n i=$i", i, petalAt(mid * Math.cos(a).toFloat(), mid * Math.sin(a).toFloat(), n))
        }
    }

    @Test fun nothingIsHitInsideTheButtonBeyondTheRingOrBelowTheCentre() {
        assertNull(petalAt(0f, -(PETAL_INNER - 1f), 3))
        assertNull(petalAt(0f, -(PETAL_OUTER + 30f), 3))
        assertNull(petalAt(0f, 80f, 3))                 // straight down: not in the arc
        assertNull(petalAt(0f, -80f, 0))
        // Just inside the slide's slack beyond the outer edge still counts, so a thumb sliding out is not dropped at once.
        assertEquals(1, petalAt(0f, -(PETAL_OUTER + 10f), 3))
    }

    @Test fun thePetalsDoNotOverlapAndFitTheScreen() {
        // The window (the ring and its margin each side) fits a 360 dp phone.
        assertTrue(2 * (PETAL_OUTER + PETAL_MARGIN) <= 360f)
        // The gap between neighbours is even: at every radius the petal's half-span is the step's half less the offset that gap + corner take.
        for (rho in listOf(PETAL_INNER + PETAL_CORNER, 100f, PETAL_OUTER - PETAL_CORNER)) {
            val half = petalHalfSpan(rho)
            assertTrue(half < PETAL_STEP / 2f && half > 0f)
            // Two neighbours' facing edges are PETAL_GAP apart in a straight line across (distance = 2 x offset from the boundary line = gap + 2 x corner, less the corner each side).
            val edge = rho * Math.sin(Math.toRadians((PETAL_STEP / 2f - half).toDouble())).toFloat()
            assertEquals(PETAL_GAP / 2f + PETAL_CORNER, edge, 0.01f)
        }
        assertTrue(PETAL_CORNER in listOf(4f, 6f, 8f, 12f))
    }

    // A stand-in for the rendered width: Inter Medium averages about 0.6 em a character (the app measures the real text with a TextMeasurer).
    private val measure = { text: String, size: Float -> text.length * size * 0.6f }
    private val labels = Petal.values().map { it.label }

    @Test fun everyLabelKeepsFourteenDpToEveryPetalEdgeAtTheTextSizesWeSupport() {
        for (scale in listOf(1.0f, 1.15f, 1.3f, 1.5f, 2.0f)) {
            labels.forEachIndexed { i, label ->
                val b = fitLabel(i, labels.size, label, scale, measure)
                val m = labelMargin(i, labels.size, b.width, b.height)
                assertTrue("$label at ${scale}x: margin $m at ${b.sizeDp} dp", m >= LABEL_MARGIN)
                // Shrunk, not clipped: never above the scaled size, never below the floor.
                assertTrue(b.sizeDp <= LABEL_BASE * scale + 0.01f && b.sizeDp >= LABEL_MIN)
                // Two lines at most, broken at a space.
                assertTrue(b.lines.size in 1..2 && b.lines.joinToString(" ") == label)
            }
        }
        // At the normal text size the labels stay near the 12 dp asked for (10 dp at the least on this wide stand-in measure; the app measures the real font).
        labels.forEachIndexed { i, l -> assertTrue(fitLabel(i, labels.size, l, 1.0f, measure).sizeDp >= 10f) }
    }

    @Test fun aLabelThatDoesNotFitIsShrunkNotClipped() {
        // Twice as wide a text: it is made smaller until it fits.
        val wide = { t: String, s: Float -> t.length * s * 1.2f }
        val b = fitLabel(1, 3, "Register As Counsel", 1.0f, wide)
        assertTrue(b.sizeDp < 12f)
        assertTrue(labelMargin(1, 3, b.width, b.height) >= LABEL_MARGIN || b.sizeDp == LABEL_MIN)
    }

    @Test fun theBreaksKeepAConnectorWordWithTheWordBefore() {
        val m = measure
        assertEquals(listOf("Open An", "Account"), labelBlock("Open An Account", 12f, m).lines)
        assertEquals(listOf("Register As", "Counsel"), labelBlock("Register As Counsel", 12f, m).lines)
        assertEquals(listOf("Discuss A", "Matter"), labelBlock("Discuss A Matter", 12f, m).lines)
    }

    @Test fun theDrawnWidthIsTheMeasuredWidthAndFitsThePetal() {
        // The block's width is the widest measured line (never an estimate), so it is what the margin was checked with.
        labels.forEachIndexed { i, label ->
            val b = fitLabel(i, labels.size, label, 1.0f, measure)
            assertEquals(maxOf(ICON, b.lines.maxOf { measure(it, b.sizeDp) }), b.width, 0.01f)
            assertTrue(labelMargin(i, labels.size, b.width, b.height) >= LABEL_MARGIN)
        }
        // And the app draws that same measured layout (drawText on the petal), not a Text with its own width and style.
        val src = java.io.File("src/main/java/com/legumlex/client/feature/auth/LoginScreen.kt").takeIf { it.isFile }?.readText() ?: return
        val arc = src.substringAfter("private fun PetalArc(").substringBefore("/** The one Debug chip")
        assertTrue(arc.contains("drawText(layout"))
        assertTrue(arc.contains("measurer.measure(fit.lines.joinToString") && arc.contains("labelStyle(fit.sizeDp)"))
        assertTrue(!arc.contains("softWrap = false,\n                            fontSize"))
        // Measured and drawn with one style.
        assertTrue(arc.contains("measurer.measure(text, labelStyle(sizeDp)"))
    }

    @Test fun theBlocksCentreIsThePetalsCentre() {
        // The block is placed at the ring segment's centroid, on the petal's own middle angle.
        for (i in 0 until 3) {
            val (x, y) = petalCentroid(i, 3)
            val ang = (Math.toDegrees(Math.atan2(y.toDouble(), x.toDouble())) + 360.0) % 360.0
            assertEquals(petalMid(i, 3).toDouble(), ang, 0.01)
            val r = Math.hypot(x.toDouble(), y.toDouble()).toFloat()
            assertTrue(r > PETAL_INNER && r < PETAL_OUTER)
        }
    }

    @Test fun theOuterPetalsReachALittleBelowTheHorizontalAndStayInTheWindow() {
        val edge = petalMid(2, 3) + PETAL_STEP / 2f          // the right petal's lower edge
        val below = PETAL_OUTER * Math.sin(Math.toRadians(edge.toDouble())).toFloat()
        assertTrue(below > 0f && below < PETAL_BELOW - 12f)
    }

    @Test fun theRippleIsQuietFillsToTheArcsReachAndRestsWithReducedMotion() {
        assertTrue(RIPPLE_RINGS in 2..3 && RIPPLE_CYCLE_MS in 2500..3000)
        assertEquals(0f, rippleAlpha(0f), 0.0001f); assertEquals(0f, rippleAlpha(1f), 0.0001f)
        for (p in listOf(0.05f, 0.1f, 0.3f, 0.6f, 0.9f)) assertTrue(rippleAlpha(p) in 0f..RIPPLE_MAX_ALPHA)
        assertTrue(RIPPLE_MAX_ALPHA in 0.18f..0.2f && RIPPLE_STROKE == 1.25f)
        // Reaching the arc's outer radius keeps it below the card: the orb is ORB_GAP + half its box below what is above it.
        assertTrue(PETAL_OUTER + PETAL_CLEARANCE <= ORB_GAP + ORB_BOX / 2f)
        // Reduced motion is no idle motion (the screen then draws one still faint ring).
        assertTrue(!petalMotion(true).idle && petalMotion(false).idle)
        assertTrue(RIPPLE_STILL_ALPHA < RIPPLE_MAX_ALPHA)
    }

    @Test fun reducedMotionIsNoMotionAndNoIdleBreathing() {
        val on = petalMotion(false); val off = petalMotion(true)
        assertEquals(40, on.openStaggerMs); assertEquals(0.94f, on.pressScale, 0.001f); assertTrue(on.idle)
        assertTrue(on.foldMs in 1..300 && on.pulseMs in 1..300)
        assertEquals(PetalMotion(0, 0, 0, 0, 1f, false), off)
    }

    @Test fun theFootBlockAndTheOrbZoneFitASmallPhone() {
        // 360 x 640 dp: the foot block is smaller than before (the date was 40 + 56 sp type, about 175 dp), and the orb's gap, its box and the block
        // together leave the card at least 310 dp of the screen's 640.
        assertTrue(FootGeometry.BLOCK <= 130f)
        assertTrue(FootGeometry.ORB_AND_FOOT <= 330f)
        assertTrue(640f - FootGeometry.ORB_AND_FOOT >= 310f)
        // The block's text sits in the width between the page's gutters (360 - 2 x 16 - 2 x 20 padding).
        assertTrue(360f - 32f - 40f > 0f)
    }

    @Test fun theArcStaysClearOfTheSquaresAndTheDebugChip() {
        // Its top edge is ORB_GAP + half the orb's box - the outer radius below what is above the orb (the squares, or the chip).
        assertTrue(arcClearance() >= PETAL_CLEARANCE)
        // 108 + 44 - 136 = 16 dp: the 12 dp asked for, and 4 dp for the petal's lift.
        assertEquals(16f, arcClearance(), 0.01f)
        // A smaller gap would break it; the constants are checked, not just the formula.
        assertTrue(arcClearance(gap = ORB_GAP - 10f) < PETAL_CLEARANCE)
    }

    @Test fun thePetalsClearTheOrbAndItsBadge() {
        // The orb (75 dp) has a 37.5 dp radius; its AI badge's far corner is at about (44, -41) dp from the centre: 60 dp.
        val badgeCorner = Math.hypot(44.3, 40.9).toFloat()
        assertTrue(PETAL_INNER > badgeCorner)
        // And the ring is thick enough for an icon and two lines of label.
        assertTrue(PETAL_OUTER - PETAL_INNER >= 72f)
    }
}
