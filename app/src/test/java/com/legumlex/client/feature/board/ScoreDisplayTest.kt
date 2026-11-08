package com.legumlex.client.feature.board

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreDisplayTest {
    private fun score(civil: Double, criminal: Double, lead: String) =
        HomeScore(criminal, civil, 0, lead, emptyList(), null, null)

    @Test fun `the tooltip is the specialty name only`() {
        assertEquals("Suits", sliceWord(Specialty("suits", "Suits", "civil", 7)))
        assertEquals("NI Act", sliceWord(Specialty("ni", "NI Act", "both", 3)))
    }

    @Test fun `at rest only the leading side shows, an even score reads EVEN`() {
        assertEquals("CIVIL", restCaption(score(8.5, 1.5, "civil")))
        assertEquals(8.5, restScore(score(8.5, 1.5, "civil")), 0.0)
        assertEquals("CRIMINAL", restCaption(score(1.0, 4.0, "criminal")))
        assertEquals("EVEN", restCaption(score(3.0, 3.0, "civil")))
        assertEquals("EVEN", restCaption(score(0.0, 0.0, "criminal")))
    }

    @Test fun `bars fill progressively`() {
        assertEquals(listOf(0.85f, 0f, 0f, 0f), stepFill(8.5))
        assertEquals(listOf(1f, 0f, 0f, 0f), stepFill(10.0))
        assertEquals(listOf(1f, 1f, 0.5f, 0f), stepFill(37.5))
        assertEquals(listOf(1f, 1f, 1f, 1f), stepFill(120.0))
        assertEquals(listOf(0f, 0f, 0f, 0f), stepFill(0.0))
    }
}
