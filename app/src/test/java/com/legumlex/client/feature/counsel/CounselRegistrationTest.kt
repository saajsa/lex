package com.legumlex.client.feature.counsel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CounselRegistrationTest {
    @Test fun triage_carries_the_servers_outcome_message_and_note() {
        val t = triageOf(mapOf("outcome" to "rejected", "message" to "Please correct your details.", "note" to "PAN unclear"))
        assertEquals("rejected", t.outcome)
        assertEquals("Please correct your details.", t.message)
        assertEquals("PAN unclear", t.note)
        assertFalse(t.standing.started)
        assertFalse(t.standing.codes)
        assertNull(t.standing.verified)
    }

    @Test fun every_outcome_is_passed_through_untouched() {
        for (o in listOf("new", "client", "counsel", "pending", "rejected", "staff")) {
            assertEquals(o, triageOf(mapOf("outcome" to o, "message" to "m")).outcome)
        }
    }

    @Test fun standing_reads_documents_started_codes_and_verified_mobile() {
        val s = standingOfCounsel(mapOf(
            "started" to true, "codes" to true, "all_uploaded" to false,
            "documents" to listOf(mapOf("type" to "bar_council_id", "label" to "Certificate", "uploaded" to true, "read" to true)),
            "phone" to mapOf("verified" to mapOf("number" to "+919876543210")),
        ))
        assertTrue(s.started); assertTrue(s.codes)
        assertEquals("+919876543210", s.verified)
        assertEquals("bar_council_id", s.onb.documents.single().type)
        assertTrue(s.onb.documents.single().uploaded)
    }

    @Test fun review_keeps_read_values_locked_and_lists_unread() {
        val r = reviewOf(mapOf(
            "full_name" to "A B", "name_locked" to true, "enrollment_number" to "P/1/2015", "number_locked" to false,
            "pan_number" to "ABCDE1234F", "pan_locked" to true, "state" to "Punjab",
            "states" to listOf("Punjab", "Haryana"), "unread" to listOf("enrolment number"),
        ))
        assertTrue(r.nameLocked); assertTrue(r.panLocked); assertFalse(r.numberLocked)
        assertEquals(listOf("Punjab", "Haryana"), r.states)
        assertEquals(listOf("enrolment number"), r.unread)
    }

    @Test fun review_missing_fields_are_empty_and_unlocked() {
        val r = reviewOf(emptyMap())
        assertEquals("", r.fullName); assertFalse(r.nameLocked); assertFalse(r.panLocked)
        assertTrue(r.states.isEmpty()); assertTrue(r.unread.isEmpty())
    }

    @Test fun a_refusal_is_kept_as_it_is() {
        val e = CounselRefused("plan_limit", "phone_taken", "Taken.")
        assertSame(e, e.counselRefusal("fallback"))
        assertEquals("phone_taken", e.field)
        assertEquals("Taken.", e.message)
    }

    @Test fun no_connection_says_so_and_keeps_details() {
        val r = java.io.IOException("x").counselRefusal("fallback")
        assertEquals(offlineMessage(), r.message)
        assertTrue(r.message!!.contains("no connection"))
        assertFalse(r.message!!.contains("—"))
    }

    @Test fun anything_else_uses_the_fallback() {
        assertEquals("fallback", IllegalStateException("x").counselRefusal("fallback").message)
    }

    @Test fun the_servers_next_is_read_from_the_triage_and_the_refusal() {
        assertEquals("add_counsel", triageOf(mapOf("outcome" to "client", "message" to "m", "next" to "add_counsel")).next)
        assertEquals("", triageOf(mapOf("outcome" to "new", "message" to "m")).next)
        assertEquals("add_counsel", CounselRefused("outcome_client", "", "m", "add_counsel").next)
    }
}
