package com.legumlex.client.feature.desk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** REQ-045: Lex asks with widgets. The server's `input` on a gather answer (cases_desk_input_clean), tolerant of absence; the calendar's days; the deadline it fills. */
class DeskInputTest {
    private fun gather(input: Any?, changes: String? = null) =
        parseGather(mapOf("success" to true, "question" to "Q?", "input" to input) + (if (changes != null) mapOf("changes" to changes) else emptyMap()))!!

    private val other = "Something Else, Type It"

    @Test fun absentOrUnknownKeepsTheTypedAnswer() {
        assertNull(parseGather(mapOf("success" to true, "question" to "Q?"))!!.input)
        assertNull(gather(null).input)
        assertNull(gather("date").input)
        assertNull(gather(mapOf("kind" to "amount")).input)
        assertNull(gather(mapOf<String, Any>()).input)
    }

    @Test fun yesNoUsesTheServersLabels() {
        val i = gather(mapOf("kind" to "yes_no", "options" to listOf("Yes", "No", "Not Sure"), "values" to listOf("yes", "no", "not_sure"), "other" to other)).input!!
        assertEquals(listOf("Yes", "No", "Not Sure"), i.options)   // values ride beside them; the answer is the label's text
        assertEquals(other, i.other)
        // None sent: the book's (in_yes / in_no / in_not_sure) or the English.
        assertEquals(emptyList<String>(), gather(mapOf("kind" to "yes_no")).input!!.options)
        assertEquals(listOf("Yes", "No", "Not Sure"), YES_NO.map { it.second })
    }

    @Test fun choiceNeedsTwoToFiveOptions() {
        assertEquals(listOf("Landlord", "Tenant"), gather(mapOf("kind" to "choice", "options" to listOf(" Landlord ", "Tenant", "", 7, "Tenant"), "other" to other)).input!!.options)
        assertNull(gather(mapOf("kind" to "choice", "options" to listOf("Only"))).input)
        assertNull(gather(mapOf("kind" to "choice", "options" to (1..6).map { "Option $it" })).input)
        assertNull(gather(mapOf("kind" to "choice")).input)
    }

    @Test fun dateTakesTheServersBounds() {
        val today = LocalDate.of(2026, 11, 23)
        val fut = gather(mapOf("kind" to "date", "when" to "future", "min" to "2026-11-23", "max" to "2028-11-23", "other" to other)).input!!
        assertEquals(LocalDate.of(2026, 11, 23)..LocalDate.of(2028, 11, 23), inputBounds(fut, today))
        val past = gather(mapOf("kind" to "date", "when" to "past", "min" to null, "max" to "2026-11-23")).input!!
        assertNull(past.min)
        val pb = inputBounds(past, today)
        assertEquals(today, pb.endInclusive)
        assertTrue(LocalDate.of(1990, 5, 1) in pb)
        assertFalse(today.plusDays(1) in pb)
        // No min or max sent: the plan's own bounds; nonsense (min after max): the plan's too.
        assertEquals(today..today.plusYears(2), inputBounds(gather(mapOf("kind" to "date", "when" to "future")).input!!, today))
        assertEquals(today..today.plusYears(2), inputBounds(gather(mapOf("kind" to "date", "when" to "future", "min" to "2027-01-01", "max" to "2026-01-01")).input!!, today))
        assertNull(gather(mapOf("kind" to "date")).input)
        assertNull(gather(mapOf("kind" to "date", "when" to "soon")).input)
    }

    @Test fun onlyTheServerSaysTheDateIsTheDeadline() {
        val fut = mapOf("kind" to "date", "when" to "future", "min" to "2026-11-23", "max" to "2028-11-23")
        assertTrue(inputFillsDeadline(gather(fut + ("fills_deadline" to true)).input!!))
        // A future date on a timing turn without the flag is only an answer: the app never decides it.
        assertFalse(inputFillsDeadline(gather(fut, "timing").input!!))
        assertFalse(inputFillsDeadline(gather(fut + ("fills_deadline" to false)).input!!))
        assertFalse(inputFillsDeadline(gather(mapOf("kind" to "yes_no", "fills_deadline" to true)).input!!))
    }

    @Test fun theAnswerReadsAsADate() {
        assertEquals("Friday, 11 December 2026", dateAnswer(LocalDate.of(2026, 12, 11)))
        assertEquals(LocalDate.of(2026, 11, 23)..LocalDate.of(2028, 11, 23), dateBounds("future", LocalDate.of(2026, 11, 23)))
    }
}

/** REQ-045 D7: the same `input` on desk/check, beside follow_up, and never without it. */
class DeskClarifyInputTest {
    private fun reading(followUp: String, input: Any?) = parseReading(mapOf("success" to true, "verdict" to "vague", "follow_up" to followUp, "input" to input))!!

    @Test fun theReadingsFollowUpCarriesTheInput() {
        val i = reading("When is the hearing?", mapOf("kind" to "date", "when" to "future", "min" to "2026-11-23", "max" to "2028-11-23", "fills_deadline" to true, "other" to "Something Else, Type It")).input!!
        assertEquals(INPUT_DATE, i.kind)
        assertTrue(inputFillsDeadline(i))
        assertEquals(listOf("Yes", "No", "Not Sure"), reading("Has a notice come?", mapOf("kind" to "yes_no", "options" to listOf("Yes", "No", "Not Sure"))).input!!.options)
    }

    @Test fun noFollowUpNoInput() {
        assertNull(reading("", mapOf("kind" to "yes_no")).input)
        assertNull(reading("  ", mapOf("kind" to "yes_no")).input)
        assertNull(reading("Has a notice come?", null).input)
        assertNull(parseReading(mapOf("success" to true, "follow_up" to "Q?"))!!.input)
    }
}

/** REQ-045 (owner, 24 Nov): a hearing to come may have been missed: the server's I Missed It, a past calendar, and the words that go to counsel. */
class DeskMissedTest {
    private val today = LocalDate.of(2026, 11, 24)
    private fun date(missed: Any?) = parseGatherInput(mapOf("kind" to "date", "when" to "future", "min" to "2026-11-24", "max" to "2028-11-24", "missed" to missed))!!

    @Test fun theMissedWayIsReadFromTheServer() {
        val m = date(mapOf("label" to "I Missed It", "ask" to "When was it?", "answer" to "Missed the hearing on {date}", "min" to "2025-11-24", "max" to "2026-11-24")).missed!!
        assertEquals("I Missed It", m.label)
        assertEquals("When was it?", m.ask)
        assertEquals(LocalDate.of(2025, 11, 24)..today, missedBounds(m, today))
        assertEquals("Missed the hearing on Sunday, 22 November 2026", missedAnswer(m, LocalDate.of(2026, 11, 22)))
    }

    @Test fun absentOrLabelLessMeansNone() {
        assertNull(date(null).missed)
        assertNull(date(mapOf("ask" to "When was it?")).missed)
        assertNull(date("I Missed It").missed)
    }

    @Test fun missingPartsFallBack() {
        val m = date(mapOf("label" to "I Missed It")).missed!!
        assertEquals(today.minusYears(1)..today, missedBounds(m, today))
        assertEquals("Missed the hearing on Friday, 20 November 2026", missedAnswer(m, LocalDate.of(2026, 11, 20)))
        assertEquals(today.minusYears(1)..today, missedBounds(MissedInput("I Missed It", "", MISSED_ANSWER_EN, LocalDate.of(2027, 1, 1), LocalDate.of(2026, 1, 1)), today))
    }
}

/** REQ-046: the optional key dates of a bounced cheque, the answer line exactly as the web's, and the order of its screens. */
class DeskKeyDatesTest {
    private fun d(s: String) = LocalDate.parse(s)

    @Test fun theReadingSaysWhenToAsk() {
        assertTrue(parseReading(mapOf("success" to true, "cheque_dates" to true))!!.chequeDates)
        assertFalse(parseReading(mapOf("success" to true))!!.chequeDates)
        assertFalse(parseReading(mapOf("success" to true, "cheque_dates" to "true"))!!.chequeDates)
    }

    @Test fun theAnswerLineIsTheWebs() {
        val all = KeyDates(cheque = d("2026-09-04"), memo = d("2026-10-01"), sent = d("2026-10-20"), how = "delivered", recv = d("2026-11-04"))
        assertEquals("Cheque dated 4 September 2026; bank return memo 1 October 2026; notice sent 20 October 2026; notice delivered 4 November 2026", keyDatesAnswer(all))
        assertEquals("notice sent 20 October 2026; notice refused or unclaimed 4 November 2026", keyDatesAnswer(KeyDates(sent = d("2026-10-20"), how = "returned", recv = d("2026-11-04"))))
        assertEquals("Cheque dated 4 September 2026; notice sent 20 October 2026; notice delivery not known", keyDatesAnswer(KeyDates(cheque = d("2026-09-04"), sent = d("2026-10-20"))))
        assertEquals("bank return memo 1 October 2026; no notice sent yet", keyDatesAnswer(KeyDates(memo = d("2026-10-01"), none = true)))
        assertEquals("", keyDatesAnswer(KeyDates()))
        assertEquals("\n\nLex asked: Key dates, if you know them\nThey answered: Cheque dated 4 November 2026", gatherLabel(KD_Q, keyDatesAnswer(KeyDates(cheque = d("2026-11-04")))))
    }

    @Test fun theScreensFollowTheWeb() {
        assertEquals(KdAt.MEMO, kdNext(KdAt.CHEQUE, KeyDates()))
        assertEquals(KdAt.SENT, kdNext(KdAt.MEMO, KeyDates()))
        assertEquals(KdAt.RECV, kdNext(KdAt.SENT, KeyDates(sent = d("2026-10-20"))))
        assertNull(kdNext(KdAt.SENT, KeyDates()))   // no notice date: the step ends
        assertNull(kdNext(KdAt.RECV_DATE, KeyDates()))
        val today = LocalDate.of(2026, 11, 24)
        assertEquals(LocalDate.of(2023, 11, 24)..today, kdBounds(today))
    }
}
