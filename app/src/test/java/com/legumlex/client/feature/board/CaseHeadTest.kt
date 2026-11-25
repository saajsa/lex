package com.legumlex.client.feature.board

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** REQ-038, owner 24 Nov 2026: the case page's head (number, then title), the next date element, the hearing rows' meta, and the switch. */
class CaseHeadTest {
    private val today = LocalDate.of(2026, 11, 25)
    private fun case(next: String?, stage: String = "", moved: String? = null) = parseCase(mapOf("id" to 1.0, "next_date" to next, "stage" to stage, "date_moved_on" to moved))
    private fun src(path: String) = java.io.File("src/main/java/com/legumlex/client/feature/board/$path").takeIf { it.isFile }?.readText()

    @Test fun theTitleStepsDownAndOnlyThenEllipsizes() {
        assertEquals(30 to 2, TITLE_STEPS.first())          // full size over 2 lines
        assertEquals(4, TITLE_STEPS.last().second)          // the smallest allows 4 lines
        assertTrue(TITLE_STEPS.zipWithNext().all { (a, b) -> b.first < a.first && b.second >= a.second })
        assertEquals(0, nextTitleStep(0, overflowed = false))
        assertEquals(1, nextTitleStep(0, overflowed = true))
        assertEquals(TITLE_STEPS.lastIndex, nextTitleStep(TITLE_STEPS.lastIndex, overflowed = true))   // stays at the smallest
    }

    @Test fun theTopHearingRowCarriesTheNextDatesState() {
        val none = parseRecord(mapOf("listings" to emptyList<Any>(), "orders" to emptyList<Any>()))
        // A date to come: UPCOMING, its days to go as the meta; red within 2 days or when just moved.
        val far = timelineRows(none, "2026-12-04", today).first()
        assertTrue(far.next); assertEquals("UPCOMING", far.eyebrow); assertEquals("In 9 days", far.meta); assertEquals("", far.metaLabel); assertFalse(far.red)
        assertTrue(timelineRows(none, "2026-11-26", today).first().let { it.red && it.meta == "Tomorrow" })
        assertTrue(timelineRows(none, "2026-11-25", today).first().let { it.red && it.meta == "Today" })
        assertTrue(timelineRows(none, "2027-01-15", today, moved = true).first().red)
        // Passed: LISTED, no new date yet, red.
        val past = timelineRows(none, "2026-11-08", today).first()
        assertTrue(past.next && past.red); assertEquals("LISTED", past.eyebrow); assertEquals("No new date yet", past.meta)
        // A recorded listing on the next date is itself the next row; its own meta is kept.
        val listed = parseRecord(mapOf("listings" to listOf(mapOf("date" to "2026-12-04", "purpose" to "Arguments"))))
        val rows = timelineRows(listed, "2026-12-04", today)
        assertEquals(1, rows.count { it.next }); assertEquals("In 9 days", rows.first { it.next }.meta)
        // No next date: no row is marked.
        assertTrue(timelineRows(listed, null, today).none { it.next })
    }

    @Test fun hearingMetaIsALabelledLineWithNoArrow() {
        assertEquals("NEXT  4 Dec 2026 · Arguments", metaText("Next", "4 Dec 2026 · Arguments", androidx.compose.ui.graphics.Color.Gray).text)
        assertEquals("No new date yet", metaText("", "No new date yet", androidx.compose.ui.graphics.Color.Gray).text)
        val row = src("BoardOrders.kt")?.substringAfter("internal fun TimelineRowView(")?.substringBefore("\n}\n") ?: return
        assertFalse(row.contains("ArrowForward"))
        assertTrue(row.contains("Modifier.width(TL_DATE_COL.dp)") && row.contains("Spacer(Modifier.width(TL_TEXT_GAP.dp))"))
    }

    @Test fun theHeadAndTheSwitch() {
        assertEquals(40 to 24, TOGGLE_W to TOGGLE_H)
        val head = src("BoardHead.kt") ?: return
        val toggle = head.substringAfter("internal fun LexToggle(").substringBefore("\n}\n")
        assertTrue(toggle.contains("role = Role.Switch") && toggle.contains("minimumInteractiveComponentSize()") && toggle.contains("if (reduced) snap() else spring("))
        val screen = src("BoardCasesScreen.kt")!!
        assertTrue(screen.contains("LexToggle(on, enabled, WATCH_TITLE, onToggle)") && !screen.contains("material3.Switch("))
        assertFalse(screen.contains("PageHeader(eyebrow = \"Case\""))       // no "Case" eyebrow
        assertTrue(screen.contains("CaseHeading(it)"))
        // The next date element is gone; its share and refresh are on the Hearings header, its mark on the top row.
        assertFalse(screen.contains("NextDateElement") || head.contains("NextDateElement") || head.contains("fun nextElement"))
        assertTrue(screen.contains("mark = dateMark(x, today)?.takeIf { it != Sym.Undated }, moved = movedToday(x, today),") && screen.contains("actions = {"))
        val section = src("BoardOrders.kt")!!.substringAfter("internal fun CaseRecordSection(").substringBefore("\n}\n")
        assertTrue(section.contains("Eyebrow(\"Hearings\", Modifier.weight(1f))") && section.contains("actions?.invoke(this)"))
        val heading = head.substringAfter("internal fun CaseHeading(").substringBefore("\n}\n")
        assertTrue(heading.indexOf("c.heading.uppercase()") < heading.indexOf("partiesText(c"))   // the number, then the title
    }

    // REQ-051 polish: the title line is dropped when it reads as the number line. The shape is dev case 16791 as customers_api sends it:
    // no case number, no parties, and title = the CNR (Board_model::short_title falls back to cino).
    private fun stub(vararg kv: Pair<String, Any?>) = parseCase(mapOf("id" to 16791.0, "cnr" to "ZZMM010000932026", "case_no" to "",
        "title" to "ZZMM010000932026", "petitioner" to "", "respondent" to "") + kv)

    @Test fun theCnrShowsOnceInTheHeader() {
        assertTrue(titleRepeatsNumber(stub()))
        assertTrue(titleRepeatsNumber(stub("title" to "")))                     // no title at all: the CNR fallback
        assertTrue(titleRepeatsNumber(stub("title" to " zzmm 0100 0093 2026 ")))  // case and spaces ignored
        assertTrue(titleRepeatsNumber(stub("petitioner" to "A", "title" to "")))  // one party alone names nothing
        assertFalse(titleRepeatsNumber(stub("case_no" to "CS/93/2026")))         // a number: the CNR title is a different line
        assertFalse(titleRepeatsNumber(stub("title" to "A v. B")))
        assertFalse(titleRepeatsNumber(stub("petitioner" to "A", "respondent" to "B")))
        assertTrue(titleRepeatsNumber(stub("case_no" to "Writ 55", "title" to "WRIT 55")))
        assertTrue(sameText("A B", "ab")); assertFalse(sameText("AB", "ABC"))
    }

    @Test fun theCalendarShowsTheCnrOnce() {
        assertEquals("CNR", timelineNumberLine(stub()))
        assertEquals("ZZMM010000932026", partiesText(stub(), androidx.compose.ui.graphics.Color.Gray).text)
        assertEquals("CS/93/2026", timelineNumberLine(stub("case_no" to "CS/93/2026")))
        assertEquals("ZZMM010000932026", timelineNumberLine(stub("title" to "A v. B")))   // a title but no number: the CNR line, as before
        assertEquals("", timelineNumberLine(stub("case_no" to "Writ 55", "title" to "Writ 55")))
    }
}
