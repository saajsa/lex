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
        assertEquals("Order of 30 Oct 2026. Not fetched yet. Tap to get it.", orderHint(avail))   // no cost on orders
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
        m.query = CaseQuery(filter = "eye", q = "mehta", folder = "2"); m.offset = 100
        assertEquals("eye", m.query.filter); assertEquals("mehta", m.query.q); assertEquals(100, m.offset)
    }

    @Test fun searchLooksAcrossEveryCase() {
        assertEquals("all", asked(CaseQuery(filter = "ongoing", q = "mehta", folder = "2")).filter)
        assertEquals("2", asked(CaseQuery(filter = "ongoing", q = "mehta", folder = "2")).folder)   // court, folder and tag still apply
        assertEquals("disposed", asked(CaseQuery(filter = "disposed", q = " ")).filter)          // no text: the chosen segment
        assertEquals("Searching all cases.", SEARCH_ALL_NOTE)
    }

    @Test fun edgeColours() {
        assertEquals(Edge.Green, rowEdge(case(mapOf("morning_check" to true)), today))
        assertEquals(Edge.Red, rowEdge(case(mapOf("morning_check" to true, "needs_eye" to true)), today))      // red first
        assertEquals(Edge.Red, rowEdge(case(mapOf("date_moved_on" to "2026-11-23", "decided_on" to "2026-11-20")), today))
        assertEquals(Edge.Ink, rowEdge(case(mapOf("morning_check" to true, "decided_on" to "2026-11-20")), today))   // disposed before watched
        assertEquals(Edge.Ink, rowEdge(case(mapOf("disposal" to "Dismissed")), today))
        assertEquals(Edge.Grey, rowEdge(case(emptyMap()), today))
        assertEquals(listOf(Sym.EdgeRed, Sym.EdgeInk, Sym.EdgeGreen, Sym.EdgeGrey), Edge.entries.map { it.sym })
    }

    @Test fun orderButtonCaptions() {
        val a = parseCourtOrder(mapOf("id" to 1.0, "state" to "on_file")); val b = parseCourtOrder(mapOf("id" to 2.0, "state" to "available"))
        assertEquals("Order", orderCaption(listOf(a), 0))
        assertEquals(listOf("Order 1", "Order 2"), listOf(0, 1).map { orderCaption(listOf(a, b), it) })
    }

    @Test fun orderButtonsFitTheirFifth() {
        // About a fifth of a phone's tile: wide enough for icon and caption side by side.
        assertEquals(OrderForm.Row, orderForm(96f, 40f))
        assertEquals(OrderForm.Row, orderForm(ORDER_ROW_MIN, 40f))
        // Narrower: the icon over the caption, when the whole caption fits (with 8dp to spare).
        assertEquals(OrderForm.Stacked, orderForm(60f, 40f))
        assertEquals(OrderForm.Stacked, orderForm(48f, 40f))
        // Narrower still: never cut "Order 1" mid-word; the icon and its number.
        assertEquals(OrderForm.NumberOnly, orderForm(47f, 40f))
        // The fixed column always reaches the row form ("⤓ Order 1"); 30% of a 360dp phone's timeline content (~231dp) would not.
        assertEquals(OrderForm.Row, orderForm(ORDER_COLUMN.toFloat(), 48f))
        assertTrue(231f * 0.3f < ORDER_ROW_MIN)
        assertEquals("1", captionNumber("Order 1")); assertEquals("12", captionNumber("Order 12")); assertEquals("", captionNumber("Order"))
    }

    /**
     * 13:20 crash: a BoxWithConstraints (a SubcomposeLayout) inside a tile measured with IntrinsicSize.Min threw "Asking for intrinsic
     * measurements of SubcomposeLayout layouts is not supported". The order buttons no longer subcompose, and the tiles that hold them
     * no longer ask for intrinsic height (their status edge is drawn).
     */
    @Test fun noSubcomposeUnderIntrinsicTiles() {
        val root = listOf("src/main/java/com/legumlex/client/feature/board", "app/src/main/java/com/legumlex/client/feature/board").map { java.io.File(it) }.first { it.exists() }
        listOf("BoardSymbols.kt", "BoardOrders.kt", "BoardCasesScreen.kt").forEach { assertFalse(it, java.io.File(root, it).readText().contains("BoxWithConstraints")) }
        val screen = java.io.File(root, "BoardCasesScreen.kt").readText()
        val row = screen.substringAfter("internal fun CaseRow(").substringBefore("\n}\n")
        assertFalse("CaseRow asks for intrinsic height", row.contains("IntrinsicSize"))
        val head = screen.substringAfter("// The pinned head").substringBefore("PageHeader(")
        assertFalse("the case head asks for intrinsic height", head.contains("IntrinsicSize"))
    }

    @Test fun segmentsFitWithShortLabels() {
        assertEquals(listOf("Ongoing", "Needs Eye", "Undated", "Disposed"), STATUS_SEGMENTS.map { SEGMENT_SHORT[it.first] })
        assertEquals("Needs Your Eye", STATUS_SEGMENTS[1].second)   // the long name, for screen readers
    }

    @Test fun rowAndWatchWords() {
        val c = case(mapOf("court_line" to "Civil Judge at Ludhiana", "folder" to mapOf("id" to 2.0, "name" to "Bank"), "tags" to listOf(mapOf("id" to 1.0, "name" to "Urgent")), "morning_check" to true))
        assertEquals("Civil Judge at Ludhiana · Bank · #Urgent", courtLine(c))
        assertTrue(rowSpeech(c, today).endsWith("Watched daily"))
        assertEquals("Watch Daily", WATCH_TITLE); assertEquals("Checked with eCourts every morning", WATCH_LINE)   // the switch row, no cost
        assertFalse(LEGEND.any { it.line.contains("credit", true) || it.line.contains("cost", true) })
    }

    @Test fun legendCoversEverySymbolOnce() {
        assertEquals(Sym.entries.size, LEGEND.toSet().size)
        LEGEND.forEach { assertTrue(it.label.isNotBlank() && it.line.isNotBlank() && it.line.endsWith(".")) }
        // Colour only for good and needs-you (the app's rule): green for confirmed and kept, red for moved and Needs Your Eye.
        assertEquals(setOf(Sym.CheckedToday, Sym.OrderKept, Sym.EdgeGreen), LEGEND.filter { it.tone == SymTone.Green }.toSet())
        assertEquals(setOf(Sym.Moved, Sym.NeedsEye, Sym.EdgeRed), LEGEND.filter { it.tone == SymTone.Red }.toSet())
        assertEquals(setOf(Sym.EdgeInk), LEGEND.filter { it.tone == SymTone.Ink }.toSet())
        // No "e" badge and no eye: the tint says watched (owner, 23 Nov 2026).
        assertFalse(LEGEND.any { it.label.equals("eCourts", true) || it.label.startsWith("Daily Check") })
        assertEquals("Watched daily: checked with eCourts every morning.", Sym.EdgeGreen.line)
        assertEquals("Needs your eye, or the date was moved.", Sym.EdgeRed.line)
        assertEquals("Disposed.", Sym.EdgeInk.line); assertEquals("Not watched.", Sym.EdgeGrey.line)
        assertFalse(LEGEND.any { it.label.contains("Tint") })   // no tint any more: the edge alone
        assertFalse(LEGEND.any { it.line.contains("ertified") })
    }

    @Test fun filingAndFilters() {
        assertEquals("#Urgent #Bank", tagsLine(parseCase(mapOf("id" to 1.0, "tags" to listOf(mapOf("id" to 1.0, "name" to "Urgent"), mapOf("id" to 2.0, "name" to "Bank"))))))
        assertEquals(0, activeFilters(CaseQuery(filter = "eye", q = "x")))   // the status segment and search are not "filters"
        assertEquals(3, activeFilters(CaseQuery(court = "k1", folder = "none", tag = 4)))
    }
}
