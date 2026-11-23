package com.legumlex.client.feature.board

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** REQ-038 (owner, 23 Nov 2026): the counsel symbols, their mapping and the legend. */
class BoardSymbolsTest {
    private val today = LocalDate.of(2026, 11, 23)
    private fun case(extra: Map<String, Any?>) = parseCase(mapOf("id" to 1.0, "next_date" to "2026-12-04") + extra)

    @Test fun nextDateMarks() {
        assertEquals(Sym.Undated, dateMark(parseCase(mapOf("id" to 1.0)), today))
        assertEquals(Sym.NeedsEye, dateMark(case(mapOf("needs_eye" to true, "check" to mapOf("result" to "not_listed", "for" to "2026-11-23"))), today))
        assertEquals(Sym.CheckedToday, dateMark(case(mapOf("check" to mapOf("result" to "not_listed", "for" to "2026-11-23", "at" to "2026-11-23 07:05:00"))), today))
        assertEquals(Sym.Checked, dateMark(case(mapOf("check" to mapOf("result" to "not_listed", "for" to "2026-11-20"))), today))
        assertNull(dateMark(case(emptyMap()), today))   // never checked or read: no mark
        assertEquals(Sym.Checked, dateMark(case(mapOf("read_at" to "2026-11-23 10:15:00")), today))   // Update From Court read it
        assertEquals(Sym.CheckedToday, dateMark(case(mapOf("read_at" to "2026-11-23 10:15:00", "check" to mapOf("result" to "not_listed", "for" to "2026-11-23"))), today))
        assertNull(dateMark(case(mapOf("read_at" to null)), today))
        val moved = case(mapOf("check" to mapOf("result" to "not_listed", "for" to "2026-11-23"),
            "history" to listOf(mapOf("date" to "2026-12-04", "source" to "ecourts_check", "at" to "2026-11-23 07:05:00", "superseded" to false))))
        assertEquals(Sym.Moved, dateMark(moved, today))
        assertTrue(movedToday(moved, today.plusDays(1)))    // yesterday's move still shows
        assertFalse(movedToday(moved, today.plusDays(2)))
        // A list item carries date_moved_on instead of the history.
        assertEquals(Sym.Moved, dateMark(case(mapOf("date_moved_on" to "2026-11-22")), today))
        assertNull(dateMark(case(mapOf("date_moved_on" to "2026-11-21")), today))
        assertNull(dateMark(case(mapOf("date_moved_on" to null)), today))
    }

    @Test fun orderMarksAndHints() {
        val kept = parseCourtOrder(mapOf("id" to 7.0, "label" to "Order of 12 Nov 2026", "state" to "on_file", "pages" to 2.0))
        val avail = parseCourtOrder(mapOf("id" to 8.0, "label" to "Order of 30 Oct 2026", "state" to "available", "cost" to 9.0))
        val signed = parseCourtOrder(mapOf("id" to 9.0, "label" to "Order of 1 Oct 2026", "state" to "signed"))
        assertEquals(Sym.OrderKept, orderMark(kept)); assertEquals(Sym.OrderAvailable, orderMark(avail)); assertEquals(Sym.OrderSealed, orderMark(signed))
        assertEquals("Order of 12 Nov 2026. Kept, tap to open.", orderHint(kept))
        assertEquals("Order of 30 Oct 2026. Not fetched yet, 9 credits. Tap to get it.", orderHint(avail))
        assertEquals("Order of 1 Oct 2026. Signed, opens on its own.", orderHint(signed))
    }

    @Test fun sameDayOrdersAreNumbered() {
        val a = parseCourtOrder(mapOf("id" to 233.0, "label" to "Interim Order of 8 Nov 2026", "state" to "available", "cost" to 9.0))
        val b = parseCourtOrder(mapOf("id" to 234.0, "label" to "Interim Order of 8 Nov 2026 (2)", "state" to "available", "cost" to 9.0))
        assertEquals(listOf(1, 2), listOf(a, b).indices.map { orderIndex(listOf(a, b), it) })
        assertNull(orderIndex(listOf(a), 0))   // one order: no number
    }

    @Test fun listStateOutlivesTheScreen() {
        val m = CasesListModel()
        assertEquals("ongoing", m.query.filter)   // Cases opens on Ongoing
        m.query = CaseQuery(filter = "eye", q = "mehta", folder = "2"); m.offset = 100; m.open = setOf(7)
        assertEquals("eye", m.query.filter); assertEquals("mehta", m.query.q); assertEquals(100, m.offset); assertEquals(setOf(7), m.open)
    }

    @Test fun searchLooksAcrossEveryCase() {
        assertEquals("all", asked(CaseQuery(filter = "ongoing", q = "mehta", folder = "2")).filter)
        assertEquals("2", asked(CaseQuery(filter = "ongoing", q = "mehta", folder = "2")).folder)   // court, folder and tag still apply
        assertEquals("disposed", asked(CaseQuery(filter = "disposed", q = " ")).filter)          // no text: the chosen segment
        assertEquals("Searching all cases.", SEARCH_ALL_NOTE)
    }

    @Test fun legendCoversEverySymbolOnce() {
        assertEquals(Sym.entries.size, LEGEND.toSet().size)
        LEGEND.forEach { assertTrue(it.label.isNotBlank() && it.line.isNotBlank() && it.line.endsWith(".")) }
        // Colour only for good and needs-you (the app's rule): green for confirmed and kept, red for moved and Needs Your Eye.
        assertEquals(setOf(Sym.CheckedToday, Sym.OrderKept), LEGEND.filter { it.tone == SymTone.Green }.toSet())
        assertEquals(setOf(Sym.Moved, Sym.NeedsEye), LEGEND.filter { it.tone == SymTone.Red }.toSet())
        assertFalse(LEGEND.any { it.line.contains("ertified") })
    }

    @Test fun filingAndFilters() {
        assertEquals("#Urgent #Bank", tagsLine(parseCase(mapOf("id" to 1.0, "tags" to listOf(mapOf("id" to 1.0, "name" to "Urgent"), mapOf("id" to 2.0, "name" to "Bank"))))))
        assertEquals(0, activeFilters(CaseQuery(filter = "eye", q = "x")))   // the status segment and search are not "filters"
        assertEquals(3, activeFilters(CaseQuery(court = "k1", folder = "none", tag = 4)))
    }
}
