package com.legumlex.client.feature.consultations

import com.legumlex.client.feature.consultations.detail.GENERAL_NOT_ANSWERED
import com.legumlex.client.feature.consultations.detail.generalState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import com.legumlex.client.feature.consultations.detail.atTime
import org.junit.Test

/**
 * REQ-029: the written enquiry shows the web's own states with no time (R6). Shapes: customers_api Consultations.php:556-558 (enquiry_number, intake_state, assigned) on a list item,
 * and Mylex_model.php:1733-1760 for the words.
 */
class ConsultationWebStatesTest {
    private fun enquiry(vararg extra: Pair<String, Any?>) = parse(mapOf(
        "id" to "41", "consultation_type" to "legal_enquiry", "status" to "Open", "request_status" to "pending", "phase" to "consultation",
        "reference" to "ENQ/2026/12345", "enquiry_number" to "ENQ/2026/12345", "date_added" to "2026-11-15 10:00:00",
    ) + extra)

    @Test fun anEnquiryNobodyHoldsIsReceivedAndWaitsToBeGiven() {
        val s = generalState(enquiry("intake_state" to "received", "assigned" to false, "assigned_to" to ""))
        assertEquals("Enquiry ENQ/2026/12345 Received", s.title)
        assertEquals("It is waiting to be given to an advocate. You will be told who has it.", s.text)
    }

    @Test fun anAdvocateWhoHoldsItButHasNotAcknowledgedHasYourEnquiry() {
        val s = generalState(enquiry("intake_state" to "assigned", "assigned" to true, "assigned_to" to "Asha Verma", "assigned_staff_id" to 7))
        assertEquals("Asha Verma Has Your Enquiry", s.title)
        assertEquals("They will read it and reply to you.", s.text)
    }

    @Test fun anAcknowledgedEnquiryIsWithYourAdvocateAndNoTimeIsPromised() {
        val s = generalState(enquiry("intake_state" to "acknowledged", "assigned" to true, "assigned_to" to "Asha Verma", "acknowledged_at" to "2026-11-15 10:20:00", "request_status" to "approved"))
        assertEquals("With Your Advocate", s.title)
        assertEquals("An advocate is working on it. Nothing is needed from you right now.", s.text)
    }

    @Test fun anOtherPendingConsultationWaitsForTheFirm() {
        val s = generalState(parse(mapOf("id" to "9", "consultation_type" to "general", "status" to "Open", "request_status" to "pending", "phase" to "consultation")))
        assertEquals("Waiting For The Firm To Take This On", s.title)
        assertEquals("An advocate will be assigned and will pick it up.", s.text)
    }

    @Test fun theEmptyConversationSaysNotAnsweredYetAsThePortalDoes() {
        assertEquals("Not answered yet. It will appear here, and in Messages, as soon as your advocate replies.", GENERAL_NOT_ANSWERED)
    }

    @Test fun noWordingInTheClientAppPromisesAReplyTime() {
        val root = java.io.File("src/main/java")
        val hits = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.filter { Regex("usually repl", RegexOption.IGNORE_CASE).containsMatchIn(it.readText()) }.map { it.name }.toList()
        assertTrue("usually repl in $hits", hits.isEmpty())
        val general = java.io.File("src/main/java/com/legumlex/client/feature/consultations/detail/DetailGeneral.kt").readText()
        assertFalse(general.contains("30 minutes"))
    }

    @Test fun theAhcCardSaysUsualTurnaroundWithTheServersMinutesOrThirty() {
        val card = java.io.File("src/main/java/com/legumlex/client/ahc/components/CountdownCard.kt").readText()
        assertTrue(card.contains("\"Usual turnaround, \$slaMinutes minutes\"") && card.contains("AHC_TURNAROUND_FALLBACK_MINUTES = 30") && card.contains("slaMinutes: Int = AHC_TURNAROUND_FALLBACK_MINUTES"))
        assertFalse(card.contains("releases reviews"))
    }

    @Test fun runningLateShowsOnlyWhenTheServerSaysLate() {
        fun c(vararg p: Pair<String, Any?>) = parse(mapOf("id" to "9", "consultation_type" to "ahc", "status" to "In Progress", "request_status" to "approved", "phase" to "consultation", "acknowledged_at" to "2026-11-15 09:30:00") + p)
        assertEquals("The answer is running late. It has been flagged to the firm.", generalState(c("review_due_at" to "2026-11-15 10:00:00", "late" to true)).text)
        assertEquals("An advocate is working on it.", generalState(c("review_due_at" to "2026-11-15 10:00:00", "late" to false)).text)
        assertEquals("An advocate is working on it. Nothing is needed from you right now.", generalState(c()).text)
    }

    @Test fun theStartNewLabelIsTitleCaseAndRoutesHideTheFooter() {
        assertEquals("Start A New Enquiry", com.legumlex.client.feature.desk.startNewLabel("Start a New Enquiry"))
        assertEquals(setOf("ahc", "portal_docs", "clarify"), com.legumlex.client.feature.desk.ROUTE_KEYS)
    }

    @Test fun theReviewClockComesFromTheServer() {
        val src = java.io.File("src/main/java/com/legumlex/client/feature/consultations/detail/DetailAHC.kt").readText()
        assertTrue(src.contains("ahc?.reviewDueAt ?: c.createdAt?.plusMinutes") && !src.contains("AHC_SLA_SECONDS"))
        val map = java.io.File("src/main/java/com/legumlex/client/feature/consultations/detail/AhcUiStateMapper.kt").readText()
        assertTrue(map.contains("slaMinutes = ahcSlaMinutes(ahc)"))
        val repo = java.io.File("src/main/java/com/legumlex/client/feature/consultations/ConsultationsRepository.kt").readText()
        assertTrue(repo.contains("\"review\") as? Map<*, *>)?.get(\"sla_minutes\")") || repo.contains("get(\"sla_minutes\")"))
    }

    @Test fun anItemNotFromTodayCarriesItsDateAndALateReviewMovesTheNowStep() {
        val d = java.time.LocalDateTime.of(2026, 10, 16, 10, 30)
        assertEquals("16 Oct, 10:30 AM", d.atTime(java.time.LocalDate.of(2026, 11, 15)).replace("am", "AM").replace("pm", "PM"))
        assertEquals("10:30 AM", d.atTime(java.time.LocalDate.of(2026, 10, 16)).replace("am", "AM").replace("pm", "PM"))
        val screen = java.io.File("src/main/java/com/legumlex/client/ahc/AhcDetailScreen.kt").readText()
        assertTrue(screen.contains("if (state.isOverdue) StepState.DONE else StepState.NOW"))
        val rail = java.io.File("src/main/java/com/legumlex/client/feature/consultations/detail/DetailAHC.kt").readText()
        assertTrue(rail.contains("maxLines = 2,   // wraps beside the chip"))
    }

    @Test fun theHeaderCornerNoLongerRepeatsSubmittedAndReleasedCarriesItsDate() {
        val src = java.io.File("src/main/java/com/legumlex/client/feature/consultations/detail/DetailAHC.kt").readText()
        assertFalse(src.contains("\"Submitted\" + consultation.createdAt"))
        assertTrue(src.contains("\"Released\" + consultation.releasedAt?.let { \" \${it.atTime()}\" }"))
    }
}
