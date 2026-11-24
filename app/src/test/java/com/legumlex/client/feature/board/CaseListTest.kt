package com.legumlex.client.feature.board

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** REQ-038, owner 24 Nov 2026: the Cases list card in three columns, the last-heard badge, a card that toggles, Open Case; smaller order pills. */
class CaseListTest {
    private val today = LocalDate.of(2026, 11, 24)

    @Test fun theCaseNumberSplitsIntoNumberAndYear() {
        assertEquals("CS 1234" to "2026", caseNoParts("CS/1234/2026", "CNR"))
        assertEquals("CS 04007" to "2026", caseNoParts("CS/04007/2026", "CNR"))
        assertEquals("PHONE 2" to "2026", caseNoParts(" PHONE / 2 / 2026 ", "CNR"))
        assertEquals("Writ 55" to "", caseNoParts("Writ 55", "CNR"))          // not in the TYPE/NO/YEAR form: whole, no year line
        assertEquals("DLHC010012342026" to "", caseNoParts("", "DLHC010012342026"))   // no number yet: the CNR
    }

    @Test fun theLastHeardBadge() {
        assertEquals("Last heard 12 Nov", lastHeardBadge("2026-11-12", today))
        assertTrue(lastHeardBadge("2025-12-03", today)!!.startsWith("Last heard 3 Dec"))
        assertTrue(lastHeardBadge("2025-12-03", today)!!.contains("2025"))
        assertNull(lastHeardBadge(null, today))
        assertNull(lastHeardBadge("", today))
    }

    @Test fun theCardTogglesAndOpenCaseGoesToTheCase() {
        val f = listOf("src/main/java/com/legumlex/client/feature/board/BoardCasesScreen.kt").map { java.io.File(it) }.firstOrNull { it.isFile } ?: return
        val s = f.readText()
        val row = s.substringAfter("internal fun CaseRow(").substringBefore("\n}\n")
        assertTrue(row.contains("clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onToggle ?: onClick)"))   // the whole card toggles (Days Ahead: opens)
        assertTrue(row.contains("LegumPrimaryButton(\"Open Case\", onClick)"))
        assertFalse(row.contains("TimelineRowView"))       // the last hearing is no longer listed in the open state
        assertFalse(row.contains("IntrinsicSize"))
        assertTrue(row.contains("maxLines = 2,\n                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis"))   // the court line wraps, then ellipsis
        assertTrue(row.contains("rotationZ = turn"))       // the chevron turns when open
        assertTrue(s.contains("CaseRow(c, today, { onOpenCase(c.id) }, expanded = ex, onToggle = { open = if (ex) open - c.id else open + c.id })"))
        assertTrue(row.contains("openNextLine(c, today)") && row.contains("\"No next date yet\""))
        // No dead code left from the old open state.
        assertTrue(s.contains("fun BoardCasesListScreen(onOpenCase: (Int) -> Unit) {"))
        assertFalse(s.contains("SnapshotStateMap<Int, CaseRecord>"))
    }

    private fun case(next: String?, stage: String = "", moved: String? = null) =
        parseCase(mapOf("id" to 1.0, "next_date" to next, "stage" to stage, "date_moved_on" to moved))

    @Test fun daysToGo() {
        assertEquals("today", daysToGo(today, today))
        assertEquals("tomorrow", daysToGo(today.plusDays(1), today))
        assertEquals("in 9 days", daysToGo(today.plusDays(9), today))
        assertEquals("in 2 days", daysToGo(today.plusDays(2), today))
    }

    @Test fun theOpenLineAndWhenItIsRed() {
        assertEquals("Next: 3 Dec · Arguments · in 9 days" to false, openNextLine(case("2026-12-03", "Arguments"), today))
        assertEquals("Next: 3 Dec · in 9 days" to false, openNextLine(case("2026-12-03"), today))   // no purpose known: left out
        // Within 2 days: red.
        assertTrue(openNextLine(case("2026-11-26", "Evidence"), today)!!.second)
        assertTrue(openNextLine(case("2026-11-24"), today)!!.second)
        assertFalse(openNextLine(case("2026-11-27"), today)!!.second)
        // Moved by eCourts today or yesterday: red, however far off.
        assertTrue(openNextLine(case("2027-01-15", moved = "2026-11-24"), today)!!.second)
        assertTrue(openNextLine(case("2027-01-15", moved = "2026-11-23"), today)!!.second)
        assertFalse(openNextLine(case("2027-01-15", moved = "2026-11-10"), today)!!.second)
        // A date gone by is not "next": listed, with no new date yet, and red.
        assertEquals("Listed 8 Nov · Appearance · no new date yet" to true, openNextLine(case("2026-11-08", "Appearance"), today))
        assertEquals("Listed 23 Nov · no new date yet" to true, openNextLine(case("2026-11-23"), today))
        assertFalse(openNextLine(case("2026-11-08"), today)!!.first.contains("Next") || openNextLine(case("2026-11-08"), today)!!.first.contains("ago"))
        // No next date: none (the card says "No next date yet").
        assertNull(openNextLine(case(null), today))
    }
}
