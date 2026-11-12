package com.legumlex.client.feature.consultations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** REQ-017 Phase 1: an enquiry's intake state, as the server sends it, and no advocate before it is assigned. */
class IntakeStateTest {
    private fun row(vararg kv: Pair<String, Any?>) = parse(mapOf<String, Any?>("id" to "9", "consultation_type" to "legal_enquiry") + kv)

    @Test fun theStateMapping() {
        assertEquals(IntakeState.RECEIVED, IntakeState.from("received"))
        assertEquals(IntakeState.ASSIGNED, IntakeState.from(" Assigned "))
        assertEquals(IntakeState.ACKNOWLEDGED, IntakeState.from("acknowledged"))
        assertNull(IntakeState.from(null)); assertNull(IntakeState.from("")); assertNull(IntakeState.from("something new"))
    }

    @Test fun theNewFieldsParseAndAreAbsentFromAnOlderServer() {
        val r = row("enquiry_number" to "ENQ/2026/12345", "intake_state" to "received", "assigned" to false, "reference" to "REF-1",
            "funnel_view" to mapOf("label" to "Enquiry ENQ/2026/12345 Received"))
        assertEquals("ENQ/2026/12345", r.enquiryNumber); assertEquals("received", r.intakeState); assertEquals(false, r.assigned); assertEquals("REF-1", r.reference)
        val old = row()
        assertNull(old.enquiryNumber); assertNull(old.intakeState); assertNull(old.assigned); assertNull(old.reference)
        assertNull(old.intakeLabel())                      // not an enquiry: no intake label
    }

    @Test fun theServersLabelIsShownAsGiven() {
        assertEquals("Enquiry ENQ/2026/12345 Received", row("intake_state" to "received", "enquiry_number" to "ENQ/2026/12345", "funnel_view" to mapOf("label" to "Enquiry ENQ/2026/12345 Received")).intakeLabel())
        assertEquals("Assigned To Adv. Rao", row("intake_state" to "assigned", "assigned" to true, "assigned_to" to "Adv. Rao", "funnel_view" to mapOf("label" to "Assigned To Adv. Rao")).intakeLabel())
        assertEquals("Acknowledged", row("intake_state" to "acknowledged", "funnel_view" to mapOf("label" to "Acknowledged")).intakeLabel())
        // No label sent: the same words are built from the state.
        assertEquals("Enquiry ENQ/2026/12345 Received", row("intake_state" to "received", "enquiry_number" to "ENQ/2026/12345").intakeLabel())
        assertEquals("Assigned To Adv. Rao", row("intake_state" to "assigned", "assigned_to" to "Adv. Rao").intakeLabel())
    }

    @Test fun noAdvocateIsNamedBeforeAssignment() {
        assertNull(row("intake_state" to "received", "assigned" to false, "assigned_to" to "Adv. Rao").advocateName())     // even if one slipped through
        assertNull(row("assigned" to false, "assigned_to" to "Adv. Rao").advocateName())
        assertEquals("Adv. Rao", row("intake_state" to "assigned", "assigned" to true, "assigned_to" to "Adv. Rao").advocateName())
        assertEquals("Adv. Rao", row("intake_state" to "acknowledged", "assigned_to" to "Adv. Rao").advocateName())
        assertEquals("Adv. Rao", row("assigned_to" to "Adv. Rao").advocateName())                                            // an older server: as before
        assertNull(row("assigned_to" to "").advocateName())
    }

    @Test fun appointmentBookedOnlyWhenConfirmed() {
        val booked = row("intake_state" to "assigned", "assigned_to" to "Adv. Rao", "funnel_view" to mapOf("label" to "Appointment Booked"))
        assertEquals("Appointment Booked", booked.intakeLabel())                       // as given (the server only says it when confirmed)
        assertEquals("Appointment Booked", booked.intakeLabel(appointmentConfirmed = true))
        assertEquals("Assigned To Adv. Rao", booked.intakeLabel(appointmentConfirmed = false))   // known unconfirmed: not shown
    }
}
