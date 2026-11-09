package com.legumlex.client.feature.board

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EnrolmentReviewTest {
    private fun home(profile: Map<String, Any?>) = parseHome(mapOf("profile" to profile, "score" to mapOf("next" to null), "tiles" to emptyMap<String, Any?>()))

    @Test fun pending_shows_the_line() {
        val h = home(mapOf("name" to "Arjun Mehta", "display_name" to "Arjun Mehta", "review" to "pending"))
        assertEquals("pending", h.profile.review)
        assertEquals("Your enrolment is with our reviewers.", enrolmentLine(h.profile))
    }

    @Test fun verified_rejected_and_absent_say_nothing_here() {
        assertNull(enrolmentLine(home(mapOf("review" to "verified")).profile))
        assertNull(enrolmentLine(home(mapOf("review" to "rejected")).profile))
        assertNull(enrolmentLine(home(mapOf("name" to "X")).profile))
    }

    @Test fun an_unknown_word_is_not_trusted() {
        assertEquals("", home(mapOf("review" to "maybe")).profile.review)
    }

    @Test fun the_name_is_taken_exactly_as_the_server_sends_it() {
        assertEquals("Arjun Mehta", home(mapOf("display_name" to "Arjun Mehta")).profile.displayName)
        assertEquals("Adv. Arjun Mehta", home(mapOf("display_name" to "Adv. Arjun Mehta")).profile.displayName)
    }

    @Test fun the_line_has_no_em_dash() {
        assertEquals(false, ENROLMENT_PENDING_LINE.contains("—"))
    }
}
