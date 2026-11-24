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

    @Test fun theCaseNumberIsSetLikeADateBlock() {
        assertEquals(CaseNoBlock("CS", "777", "2021"), caseNoBlock("CS/777/2021", "CNR"))
        assertEquals(CaseNoBlock("CS", "04007", "2026"), caseNoBlock("CS/04007/2026", "CNR"))
        assertEquals(CaseNoBlock("PHONE", "2", "2026"), caseNoBlock(" PHONE / 2 / 2026 ", "CNR"))
        assertEquals(CaseNoBlock("", "Writ 55", ""), caseNoBlock("Writ 55", "CNR"))                    // another form: whole, no type or year
        assertEquals(CaseNoBlock("", "DLHC010012342026", ""), caseNoBlock("", "DLHC010012342026"))     // no number yet: the CNR
    }

    @Test fun longNumbersShrinkNeverWrap() {
        assertEquals(24, caseNoFontSp(3))   // the hearing day's size
        assertTrue(caseNoFontSp(4) < 24 && caseNoFontSp(5) < caseNoFontSp(4) && caseNoFontSp(8) < caseNoFontSp(6))
        assertEquals(9, caseNoFontSp(16))
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
        // The hearing row's own geometry: its column width, rule and gap; a rule each side; no toggle icon.
        assertTrue(row.contains("Modifier.width(TL_DATE_COL.dp).align(Alignment.CenterVertically)") && row.contains("CARD_INSET + TL_RULE_X") && row.contains("Spacer(Modifier.width(TL_TEXT_GAP.dp))"))
        assertEquals(2, Regex("""Modifier\.width\(TL_DATE_COL\.dp\)""").findAll(row).count())
        assertFalse(row.contains("ExpandMore") || row.contains("rotationZ"))
        val tl = java.io.File("src/main/java/com/legumlex/client/feature/board/BoardOrders.kt").readText().substringAfter("internal fun TimelineRowView(").substringBefore("\n}\n")
        assertTrue(tl.contains("TL_RULE_X.dp.toPx()") && tl.contains("Modifier.width(TL_DATE_COL.dp)") && tl.contains("Spacer(Modifier.width(TL_TEXT_GAP.dp))"))
        assertTrue(row.contains("LegumPrimaryButton(\"Open Case\", onClick)"))
        assertFalse(row.contains("TimelineRowView"))       // the last hearing is no longer listed in the open state
        assertFalse(row.contains("IntrinsicSize"))
        assertTrue(row.contains("maxLines = 2,\n                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis"))   // the court line wraps, then ellipsis
        assertTrue(row.contains("val red = next != null && (next.isBefore(today) || movedToday(c, today))"))   // the right block's red: passed or moved
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

/** Owner, 24 Nov 2026 ("fix the case page the same way"): a passed next date is never Next or Upcoming, anywhere in the counsel workspace. */
class PassedNextDateTest {
    private val today = LocalDate.of(2026, 11, 25)
    private fun case(next: String?, stage: String = "") = parseCase(mapOf("id" to 1.0, "next_date" to next, "stage" to stage))

    @Test fun oneWordingForAPassedDate() {
        assertEquals("Listed 8 Nov · no new date yet", listedLine(LocalDate.of(2026, 11, 8), today))
        assertEquals("Listed 8 Nov · Appearance · no new date yet", listedLine(LocalDate.of(2026, 11, 8), today, "Appearance"))
        assertEquals(LocalDate.of(2026, 11, 8), passedNext("2026-11-08", today))
        assertNull(passedNext("2026-11-25", today))   // today is not passed
        assertNull(passedNext(null, today))
    }

    @Test fun theCaseHeaderLine() {
        assertEquals("Listed 8 Nov · no new date yet", nextLine(case("2026-11-08"), today))
        assertEquals("Next: 25 Nov", nextLine(case("2026-11-25"), today))
        assertEquals("Next: 3 Dec", nextLine(case("2026-12-03"), today))
        assertEquals("No Next Date", nextLine(case(null), today))
        // The list's open card shares the wording.
        assertEquals("Listed 8 Nov · Appearance · no new date yet" to true, openNextLine(case("2026-11-08", "Appearance"), today))
    }

    @Test fun theTimelineNeverSaysUpcomingForAPassedDate() {
        val r = parseRecord(mapOf("listings" to emptyList<Any>(), "orders" to emptyList<Any>()))
        val past = timelineRows(r, "2026-11-08", today).first()
        assertEquals("LISTED", past.eyebrow); assertFalse(past.upcoming); assertEquals("No new date yet", past.meta)
        val todayRow = timelineRows(r, "2026-11-25", today).first()
        assertEquals("UPCOMING", todayRow.eyebrow); assertTrue(todayRow.upcoming)
    }

    @Test fun theAddByCnrReview() {
        val p = CnrPreview("CNR1", "CS/1/2026", "", "", "Court", "", "", "2026-11-08", "")
        assertEquals("None yet. Last listed ${BoardDates.long(LocalDate.of(2026, 11, 8))}.", cnrFacts(p, today).toMap()["Next Date"])
        assertEquals(BoardDates.long(LocalDate.of(2026, 12, 3)), cnrFacts(p.copy(nextDate = "2026-12-03"), today).toMap()["Next Date"])
    }
}
