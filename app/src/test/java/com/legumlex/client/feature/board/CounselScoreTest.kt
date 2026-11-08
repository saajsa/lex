package com.legumlex.client.feature.board

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CounselScoreTest {
    private fun sp(key: String, label: String, side: String, n: Int) = mapOf("key" to key, "label" to label, "side" to side, "count" to n.toDouble())

    private val home = mapOf(
        "profile" to mapOf(
            "name" to "Loop App Advocate", "display_name" to "Adv. Loop App Advocate", "initials" to "LAA", "bar_council" to "Punjab", "enrolment_no" to "ZZ/9001/2015",
            "years_at_bar" to 11.0, "chambers" to "214, District Courts, Ludhiana, 141001", "email" to "loop@example.org", "mobile" to "+919000000098",
            "courts" to 4.0, "districts" to listOf("Ludhiana", "Jalandhar"), "districts_n" to 2.0, "days_on_platform" to 0.0,
        ),
        "score" to mapOf(
            "criminal" to 1.5, "civil" to 8.5, "ni" to 1.0, "lead" to "civil",
            "specialties" to listOf(
                sp("bail", "Bail", "criminal", 0), sp("crl_misc", "Crl. Misc.", "criminal", 1), sp("crl_appeals", "Criminal Appeals", "criminal", 0), sp("trials", "Trials", "criminal", 0),
                sp("suits", "Suits", "civil", 7), sp("civil_appeals", "Civil Appeals", "civil", 1), sp("execution", "Execution", "civil", 0), sp("family", "Family", "civil", 0),
                sp("other_civil", "Other Civil", "civil", 0), sp("ni", "NI Act", "both", 1), sp("unknown", "Not Known", "none", 1),
            ),
            "next" to mapOf("criminal" to 10.0, "civil" to 10.0),
        ),
        "tiles" to mapOf(
            "diary" to mapOf("ongoing" to 11.0, "criminal" to 1.0, "civil" to 8.0, "ni" to 1.0, "unknown" to 1.0),
            "this_month" to mapOf("month" to "November 2026", "hearings" to 14.0, "busiest" to mapOf("date" to "2026-11-08", "count" to 3.0)),
            "dates_caught" to mapOf("count" to 2.0),
            "disposed" to mapOf("this_year" to 0.0, "oldest_running_year" to 2021.0),
        ),
    )

    @Test fun readsTheHome() {
        val h = parseHome(home)
        assertEquals("Adv. Loop App Advocate", h.profile.displayName)
        assertEquals(11, h.profile.yearsAtBar)
        assertEquals(listOf("Ludhiana", "Jalandhar"), h.profile.districts)
        assertEquals(8.5, h.score.civil, 0.0)
        assertEquals("civil", h.score.lead)
        assertEquals(11, h.score.specialties.size)
        assertEquals(10, h.score.nextCivil)
        assertEquals(14, h.tiles.hearings)
        assertEquals("2026-11-08", h.tiles.busiestDate)
        assertEquals(2021, h.tiles.oldestRunningYear)
    }

    @Test fun aMissingYearsAndNoNextStepAreKept() {
        val h = parseHome(mapOf("profile" to mapOf("name" to "X"), "score" to mapOf("criminal" to 120.0, "civil" to 0.0, "next" to null), "tiles" to emptyMap<String, Any?>()))
        assertNull(h.profile.yearsAtBar)
        assertNull(h.score.nextCriminal)
        assertNull(h.tiles.oldestRunningYear)
    }

    @Test fun scoresShowHalvesAndWholeNumbers() {
        assertEquals("8.5", scoreText(8.5))
        assertEquals("7", scoreText(7.0))
        assertEquals("0", scoreText(0.0))
        assertEquals("1.5", scoreText(1.5))
        assertEquals("15.5", scoreText(15.5))
        assertEquals("0.5", scoreText(0.5))
    }

    @Test fun stepsAreReachedAtTheirOwnValue() {
        assertEquals(0, stepsReached(9.5))
        assertEquals(1, stepsReached(10.0))
        assertEquals(1, stepsReached(24.5))
        assertEquals(2, stepsReached(25.0))
        assertEquals(4, stepsReached(100.0))
    }

    @Test fun theRingHasOneArcForEachSpecialtyWithCasesInTheWebsOrder() {
        val arcs = ringArcs(parseHome(home).score.specialties)
        assertEquals(listOf("ni", "suits", "civil_appeals", "crl_misc", "unknown"), arcs.map { it.key })
        // 11 ongoing cases: a share of 7/11 for Suits, less the small gaps.
        val suits = arcs.first { it.key == "suits" }
        assertEquals(7f / 11f * 360f - 2.4f, suits.sweep, 0.01f)
        // The arcs never overlap and stay inside the circle.
        arcs.zipWithNext().forEach { (a, b) -> assertTrue(a.start + a.sweep <= b.start) }
        assertTrue(arcs.last().start + arcs.last().sweep <= 360f)
    }

    @Test fun oneSpecialtyOnlyIsAWholeRing() {
        val arcs = ringArcs(listOf(Specialty("suits", "Suits", "civil", 4), Specialty("bail", "Bail", "criminal", 0)))
        assertEquals(1, arcs.size)
        assertEquals(359.9f, arcs[0].sweep, 0.001f)
        assertTrue(ringArcs(listOf(Specialty("suits", "Suits", "civil", 0))).isEmpty())
    }

    @Test fun aTapOnTheRingFindsTheArcAndAnythingElseFindsNone() {
        val arcs = ringArcs(parseHome(home).score.specialties)
        // The first arc (NI Act) starts at the top: a point just clockwise of 12 o'clock on the ring.
        val ni = arcAt(arcs, 5f, -128f, 128f, 22f)
        assertNotNull(ni); assertEquals("ni", ni!!.key)
        // The suits arc is the big one on the right: 3 o'clock is within it.
        assertEquals("suits", arcAt(arcs, 128f, 0f, 128f, 22f)?.key)
        // The centre and far outside are not arcs.
        assertNull(arcAt(arcs, 0f, 0f, 128f, 22f))
        assertNull(arcAt(arcs, 300f, 0f, 128f, 22f))
    }

    @Test fun anArcSaysItsNameOnly() {
        assertEquals("Suits", sliceWord(Specialty("suits", "Suits", "civil", 7)))
        assertEquals("Not Known", sliceWord(Specialty("unknown", "Not Known", "none", 2)))
        assertEquals("CIVIL", sideCaption("civil"))
        assertEquals("BOTH SIDES", sideCaption("both"))
    }

    @Test fun theColoursAreTheWebs() {
        assertEquals(0xFFB3261E, SpecialtyColours["bail"])
        assertEquals(0xFF1F3A6E, SpecialtyColours["suits"])
        assertEquals(0xFFF5C518, SpecialtyColours["ni"])
        assertEquals(0xFFBDB9B0, SpecialtyColours["unknown"])
        assertEquals(RingOrder.toSet(), SpecialtyColours.keys)
    }

    @Test fun theTilesAndTheProfileUseTheWebsWords() {
        val h = parseHome(home)
        assertEquals("Civil 8 · Criminal 1 · NI Act 1 · 1 not known", diaryLine(h.tiles))
        assertEquals("Busiest: Sun 8 Nov, 3", busiestLine(h.tiles))
        assertEquals("Oldest running: filed 2021", oldestLine(h.tiles))
        assertEquals("ZZ/9001/2015 · Punjab", barLine(h.profile))
        assertEquals("4 courts · Ludhiana · Jalandhar", courtsLine(h.profile))
        assertEquals("Nothing listed yet", busiestLine(h.tiles.copy(busiestDate = null)))
        assertEquals("No running case yet", oldestLine(h.tiles.copy(oldestRunningYear = null)))
        assertEquals("1 court", courtsLine(h.profile.copy(courts = 1, districts = emptyList())))
    }
}
