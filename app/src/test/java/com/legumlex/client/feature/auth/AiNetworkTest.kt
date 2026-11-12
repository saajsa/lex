package com.legumlex.client.feature.auth

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/** REQ-013 013.20: the AI texture's layout and twinkle. A 360 dp phone: the area is 360 + 2 x 16 wide (it bleeds to the screen's edges). */
class AiNetworkTest {
    private val w = 392f
    private val h = ORB_GAP + ORB_BOX + 90f
    private val hx = w / 2f
    private val hy = ORB_GAP + ORB_BOX / 2f
    private fun net(seed: Long = NET_SEED, width: Float = w, height: Float = h) = buildNetwork(width, height, width / 2f, hy, seed = seed)

    @Test fun theLayoutIsTheSameEveryTimeForASeedAndASize() {
        val a = net(); val b = net()
        assertArrayEquals(a.nx, b.nx, 0f); assertArrayEquals(a.ny, b.ny, 0f)
        assertArrayEquals(a.ex, b.ex, 0f); assertArrayEquals(a.edgeAlpha, b.edgeAlpha, 0f)
        assertArrayEquals(a.twPeriod, b.twPeriod)
        // Another seed is another layout; it is a sensible size.
        assertFalse(a.nx.contentEquals(net(seed = 7L).nx))
        assertTrue("nodes ${a.nodes}", a.nodes in 40..150)
        assertTrue("edges ${a.edges}", a.edges in 40..450)
    }

    @Test fun nothingFallsOnTheCardTheChipTheDateOrTheButton() {
        val n = net()
        for (i in 0 until n.nodes) {
            // Inside the area (below the chip and the squares, which is y = 0, and above the date, y = h), and in its fade, never at its edge.
            assertTrue(n.ny[i] >= 6f && n.ny[i] <= h - 12f)
            assertTrue(n.nx[i] in 0f..w)
            // Off the button, with room for its spokes.
            assertTrue(hypot(n.nx[i] - hx, n.ny[i] - hy) >= NET_HUB_R + NET_HUB_CLEAR)
            // The dots are nothing by the date text: the fade has taken them.
            if (n.ny[i] > h - 12f) assertEquals(0f, n.nodeAlpha[i], 0f)
        }
        for (e in 0 until n.edges) {
            val x1 = n.ex[4 * e]; val y1 = n.ex[4 * e + 1]; val x2 = n.ex[4 * e + 2]; val y2 = n.ex[4 * e + 3]
            assertTrue(y1 in 6f..h - 12f && y2 in 6f..h - 12f)
            if (n.spoke[e]) {
                // A spoke ends exactly on the button's outer ring, not inside it.
                assertEquals(NET_HUB_R, hypot(x2 - hx, y2 - hy), 0.05f)
            } else {
                // A line never crosses the button.
                var best = Float.MAX_VALUE
                for (k in 0..40) { val t = k / 40f; best = minOf(best, hypot(x1 + t * (x2 - x1) - hx, y1 + t * (y2 - y1) - hy)) }
                assertTrue("line $e comes within $best", best >= NET_HUB_R + 3f)
            }
            assertTrue(hypot(x2 - x1, y2 - y1) <= NET_MAX_EDGE + NET_HUB_R + 1f)
        }
    }

    @Test fun aFewLinesRunIntoTheButtonsRing() {
        val n = net()
        val spokes = (0 until n.edges).count { n.spoke[it] }
        assertTrue("spokes $spokes", spokes in 3..5)
    }

    @Test fun theDensityFallsWithDistanceFromTheButton() {
        val n = net()
        // Dots per 1000 dp2 of the area, in three bands of distance from the button (the area's own part of each band, found by sampling).
        fun density(lo: Float, hi: Float): Float {
            var area = 0f; var y = 3f
            while (y < h) { var x = 2f; while (x < w) { if (hypot(x - hx, y - hy) in lo..hi) area += 16f; x += 4f }; y += 4f }
            val count = (0 until n.nodes).count { hypot(n.nx[it] - hx, n.ny[it] - hy) in lo..hi }
            return count / area * 1000f
        }
        val d1 = density(0f, 100f); val d2 = density(100f, 180f); val d3 = density(180f, 400f)
        assertTrue("$d1 > $d2 > $d3", d1 > d2 && d2 > d3)
        assertTrue(netSpacing(60f) < netSpacing(160f) && netSpacing(160f) < netSpacing(300f))
        // And the dots and lines are a little stronger and bigger near the button than far from it.
        fun mean(sel: (Int) -> Boolean, v: FloatArray) = (0 until n.nodes).filter(sel).map { v[it] }.average()
        val near = { i: Int -> hypot(n.nx[i] - hx, n.ny[i] - hy) < 110f && n.ny[i] > 20f && n.ny[i] < h - 40f }
        val far = { i: Int -> hypot(n.nx[i] - hx, n.ny[i] - hy) > 200f && n.ny[i] > 20f && n.ny[i] < h - 40f }
        assertTrue(mean(near, n.nodeAlpha) > mean(far, n.nodeAlpha))
        assertTrue(mean(near, n.nodeSize) > mean(far, n.nodeSize))
    }

    @Test fun theLookStaysQuiet() {
        val n = net()
        for (i in 0 until n.nodes) { assertTrue(n.nodeAlpha[i] in 0f..0.16f); assertTrue(n.nodeSize[i] in 1.5f..2.5f) }
        for (e in 0 until n.edges) assertTrue(n.edgeAlpha[e] in 0f..0.10f)
    }

    @Test fun theTwinkleIsSlowSeamlessAndStillWithReducedMotion() {
        for (p in listOf(2, 3, 4)) for (ph in listOf(0f, 0.3f, 0.77f)) {
            assertEquals(twinkle(0f, p, ph, false), twinkle(NET_MASTER_S.toFloat(), p, ph, false), 0.0001f)   // the loop joins up
            for (t in 0..120) {
                val m = twinkle(t / 10f, p, ph, false)
                assertTrue(m in 1f..1.9001f)
                // Reduced motion: a static alpha, whatever the time.
                assertEquals(1f, twinkle(t / 10f, p, ph, true), 0f)
            }
        }
        assertEquals(1f, twinkle(5f, 0, 0.4f, false), 0f)       // a dot that does not twinkle
        // Only a handful twinkle at a time: about a quarter of the dots, and each is bright for under half of its cycle.
        val n = net()
        val share = n.twPeriod.count { it > 0 }.toFloat() / n.nodes
        assertTrue("share $share", share in 0.1f..0.5f)
        assertTrue(n.twPeriod.all { it in listOf(0, 2, 3, 4) } && NET_MASTER_S % 2 == 0 && NET_MASTER_S % 3 == 0 && NET_MASTER_S % 4 == 0)
    }

    @Test fun itDrawsInOneCanvasDrivenByOneTransitionAndIsDimmedWithTheArc() {
        val src = java.io.File("src/main/java/com/legumlex/client/feature/auth/LoginScreen.kt").takeIf { it.isFile }?.readText() ?: return
        val tex = src.substringAfter("private fun AiTexture(").substringBefore("/** How the arc is left")
        assertEquals(1, Regex("rememberInfiniteTransition").findAll(tex).count())
        assertTrue(tex.contains("drawWithCache") && tex.contains("buildNetwork("))
        assertTrue(tex.contains("if (reduced) null else"))
        // Behind the orb, hidden with the chat, dimmed while the arc is open.
        assertTrue(src.contains("if (chat) 0f else if (arcOpen) 0.25f else 1f"))
        assertTrue(src.indexOf("AiTexture(Modifier.matchParentSize()") < src.indexOf("LexOrb(enabled = !isLoading"))
    }
}
