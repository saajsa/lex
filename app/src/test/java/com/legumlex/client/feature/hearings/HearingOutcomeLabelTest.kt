package com.legumlex.client.feature.hearings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** REQ-016 016.3: the client app shows a recorded hearing as "Completed · <outcome>"; an older server (no outcome) shows the status as before. */
class HearingOutcomeLabelTest {
    @Test fun labelFollowsTheOutcome() {
        assertEquals("Completed · Adjourned", hearingStatusLabel("Completed", "Adjourned"))
        assertEquals("Completed · No Appearance", hearingStatusLabel("Completed", "No Appearance"))
        assertEquals("Completed", hearingStatusLabel("Completed", null))
        assertEquals("Scheduled", hearingStatusLabel("Scheduled", null))
        assertEquals("Scheduled", hearingStatusLabel("Scheduled", "Adjourned"))
        assertEquals("adjourned", hearingMark("Completed", "Adjourned"))
        assertEquals("completed", hearingMark("Completed", "Heard"))
    }

    @Test fun outcomeIsOptionalAndReachesTheModels() {
        val withOutcome = parseHearing(mapOf("id" to "9", "status" to "Completed", "outcome" to "Postponed",
            "history" to listOf(mapOf("id" to "8", "status" to "Completed", "outcome" to "Adjourned"))))
        assertEquals("Completed · Postponed", withOutcome.statusLabel)
        assertEquals("Completed · Adjourned", withOutcome.history[0].statusLabel)
        val old = parseHearing(mapOf("id" to "9", "status" to "Adjourned"))
        assertNull(old.outcome); assertEquals("Adjourned", old.statusLabel)
        assertNull(parseHearing(mapOf("id" to "1", "status" to "Completed", "outcome" to "")).outcome)
    }
}
