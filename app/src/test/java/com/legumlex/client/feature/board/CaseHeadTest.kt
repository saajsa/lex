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

    @Test fun theNextDateElement() {
        assertEquals(NextElement("Next Hearing", "In 9 days", false), nextElement(case("2026-12-04", "Arguments"), today))
        assertEquals(NextElement("Next Hearing", "Tomorrow", true), nextElement(case("2026-11-26"), today))      // within 2 days: red
        assertEquals(NextElement("Next Hearing", "In 51 days", true), nextElement(case("2027-01-15", moved = "2026-11-25"), today))   // just moved: red
        assertEquals(NextElement("Last Listed", "No new date yet", true), nextElement(case("2026-11-08", "Appearance"), today))
        assertEquals(NextElement("No Next Date", "", false), nextElement(case(null), today))
        assertEquals(40, NEXT_BLOCK_DP)
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
        assertTrue(screen.contains("CaseHeading(it)") && screen.contains("NextDateElement(x, today)"))
        val heading = head.substringAfter("internal fun CaseHeading(").substringBefore("\n}\n")
        assertTrue(heading.indexOf("c.heading.uppercase()") < heading.indexOf("partiesText(c"))   // the number, then the title
    }
}
