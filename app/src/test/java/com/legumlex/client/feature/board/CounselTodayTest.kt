package com.legumlex.client.feature.board

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The Counsel home: board/today parsed as the contract sends it, and the switch the server's `counsel` flag drives. */
class CounselTodayTest {

    private val full: Map<String, Any?> = mapOf(
        "counsel" to true, "workspace" to "counsel", "empty" to false,
        "date" to "2026-11-09", "weekday" to "Monday", "date_label" to "Monday, 9 November 2026",
        "listed_today" to 5.0, "listed_tomorrow" to 2.0, "cases_tracked" to 7.0, "on_check" to 7.0,
        "credits" to mapOf("balance" to 100.0, "cost" to 1.0, "per_morning" to 7.0, "days_left" to 14.0, "days_label" to "About 14 days of checks", "low" to false, "expiring" to null),
        "not_checked_today" to 0.0, "short" to null, "checked_at" to "2026-11-09 07:05:00",
        "courts" to listOf(
            mapOf("court" to "Civil Judge (Jr. Div.) Ludhiana", "bench" to "Ld. Ms. Anjali Sharma", "room" to "Court No. 12", "count" to 2.0),
            mapOf("court" to "Judicial Magistrate First Class Jalandhar", "bench" to "Ld. Court (Court No. 9)", "room" to "Court No. 9", "count" to 2.0),
        ),
        "courts_n" to 3.0,
        "needs_eye" to listOf(
            mapOf("id" to 4.0, "cnr" to "PBLD010045672025", "case_no" to "CRM/456/2025", "title" to "State of Punjab v. Gurdeep Singh & Ors.", "ecourts_date" to "2026-11-09", "earlier_date" to "2026-11-12"),
        ),
    )

    @Test fun parsesTheDay() {
        val t = parseToday(full)
        assertFalse(t.empty)
        assertEquals("Monday", t.weekday)
        assertEquals(5, t.listedToday)
        assertEquals(2, t.listedTomorrow)
        assertEquals(7, t.casesTracked)
        assertEquals(3, t.courtsN)
        assertEquals("2026-11-09 07:05:00", t.checkedAt)
        assertNull(t.short)
        assertEquals(2, t.courts.size)
        assertEquals("Ld. Ms. Anjali Sharma", t.courts[0].bench)
        assertEquals(100, t.credits.balance)
        assertEquals(14, t.credits.daysLeft)
        assertEquals("About 14 days of checks", t.credits.daysLabel)
        assertFalse(t.credits.low)
    }

    @Test fun parsesNeedsYourEye() {
        val e = parseToday(full).needsEye.single()
        assertEquals(4, e.id)
        assertEquals("CRM/456/2025", e.heading)
        assertEquals("2026-11-09", e.ecourtsDate)
        assertEquals("2026-11-12", e.earlierDate)
        assertEquals("eCourts shows 9 Nov; your diary had 12 Nov", eyeLine(e, LocalDate.of(2026, 11, 9)))
    }

    @Test fun parsesShortCreditsAndNullDays() {
        val t = parseToday(full + mapOf(
            "short" to mapOf("checked" to 3.0, "of" to 7.0, "credits_had" to 3.0, "unchecked" to 4.0),
            "credits" to mapOf("balance" to 0.0, "cost" to 1.0, "per_morning" to 7.0, "days_left" to null, "days_label" to "No case is on the daily check", "low" to true),
        ))
        val s = t.short!!
        assertEquals(3, s.checked); assertEquals(7, s.of); assertEquals(4, s.unchecked)
        assertNull(t.credits.daysLeft)
        assertTrue(t.credits.low)
    }

    @Test fun emptyStateAndMissingFields() {
        val t = parseToday(mapOf("counsel" to true, "empty" to true, "date" to "2026-11-09", "weekday" to "Monday"))
        assertTrue(t.empty)
        assertEquals(0, t.listedToday)
        assertTrue(t.courts.isEmpty())
        assertTrue(t.needsEye.isEmpty())
        assertNull(t.checkedAt)
    }

    @Test fun checkedLineReadsTheServersStamp() {
        val today = LocalDate.of(2026, 11, 9)
        assertEquals("7:05 AM", BoardDates.checkedLabel("2026-11-09 07:05:00", today))
        assertEquals("7:05 AM", BoardDates.checkedLabel("2026-11-09T07:05:00", today))
        assertEquals("8 Nov, 6:30 PM", BoardDates.checkedLabel("2026-11-08 18:30:00", today))
        assertNull(BoardDates.checkedLabel(null, today))
        assertNull(BoardDates.checkedLabel("not a time", today))
    }

    @Test fun greetingFollowsTheHour() {
        assertEquals("Good morning", BoardDates.greeting(7))
        assertEquals("Good afternoon", BoardDates.greeting(12))
        assertEquals("Good evening", BoardDates.greeting(18))
        assertEquals("9 November 2026", BoardDates.full(LocalDate.of(2026, 11, 9)))
    }

    // ---- the workspace switch ----------------------------------------------------

    private fun summary(counsel: Boolean) = parseSummary(mapOf("today" to "2026-11-09", "cases" to 7.0, "credits" to 100.0, "counsel" to counsel))

    @Test fun summaryCarriesTheCounselFlag() {
        assertTrue(summary(true).counsel)
        assertFalse(summary(false).counsel)
        assertFalse(parseSummary(mapOf("today" to "2026-11-09")).counsel)
    }

    @Test fun workspaceSwitchFollowsTheServer() {
        assertTrue(isCounsel(BoardAccess.Yes(summary(true))))
        assertFalse(isCounsel(BoardAccess.Yes(summary(false))))
        assertFalse(isCounsel(BoardAccess.Unknown))
        assertFalse(isCounsel(BoardAccess.No))
    }

    @Test fun creditsChipIsKeptAndClearedWithTheSignIn() {
        BoardHub.setChip(3, true)
        assertEquals(BoardHub.Chip(3, true), BoardHub.chip.value)
        BoardHub.clear()
        assertNull(BoardHub.chip.value)
    }

    // ---- the case list under Today (Cases merged into Home) ------------------------

    private fun caseOn(next: String?) = parseCase(mapOf("id" to 1.0, "cnr" to "PBLD010012342026", "next_date" to next))

    @Test fun caseFiltersByDay() {
        val today = LocalDate.of(2026, 11, 9)
        assertTrue(CaseFilter.All.keeps(caseOn(null), today))
        assertTrue(CaseFilter.Today.keeps(caseOn("2026-11-09"), today))
        assertFalse(CaseFilter.Today.keeps(caseOn("2026-11-10"), today))
        assertTrue(CaseFilter.Tomorrow.keeps(caseOn("2026-11-10"), today))
        assertFalse(CaseFilter.Tomorrow.keeps(caseOn(null), today))
    }

    @Test fun caseFiltersNeedsEyeAndOff() {
        val today = LocalDate.of(2026, 11, 9)
        val eye = parseCase(mapOf("id" to 2.0, "needs_eye" to true, "morning_check" to true, "ecourts_date" to "2026-12-01"))
        val off = parseCase(mapOf("id" to 3.0, "needs_eye" to false, "morning_check" to false))
        assertTrue(CaseFilter.NeedsEye.keeps(eye, today))
        assertFalse(CaseFilter.NeedsEye.keeps(off, today))
        assertTrue(CaseFilter.Off.keeps(off, today))
        assertFalse(CaseFilter.Off.keeps(eye, today))
        assertEquals("2026-12-01", eye.ecourtsDate)
    }

    @Test fun courtLineLeadsWithTheBenchElseTheRoom() {
        val b = parseCase(mapOf("id" to 4.0, "court" to "Civil Judge (Jr. Div.) Ludhiana", "bench" to "Ld. Ms. Anjali Sharma", "room" to "Court 3"))
        assertEquals("Civil Judge (Jr. Div.) Ludhiana · Ld. Ms. Anjali Sharma", b.courtLine)
        val r = parseCase(mapOf("id" to 5.0, "court" to "Civil Judge", "room" to "Court 3"))
        assertEquals("Civil Judge · Court 3", r.courtLine)
        assertTrue(r.hasCnr)
    }

    @Test fun caseReadsHistoryAndCnrState() {
        val c = parseCase(mapOf(
            "id" to 6.0, "has_cnr" to false,
            "history" to listOf(
                mapOf("date" to "2026-12-01", "source" to "ecourts", "label" to "From eCourts", "at" to "2026-11-08 07:05:00"),
                mapOf("date" to "2026-11-20", "source" to "import", "label" to "Imported", "at" to null),
            ),
        ))
        assertFalse(c.hasCnr)
        assertEquals(2, c.history.size)
        assertEquals("From eCourts", c.history[0].label)
        assertEquals("2026-11-20", c.history[1].date)
        assertNull(c.history[1].at)
        assertTrue(parseCase(mapOf("id" to 7.0)).history.isEmpty())
    }

    @Test fun dayReadsTheFormatLine() {
        val d = parseDay(mapOf("date" to "2026-11-08", "format" to mapOf("summary" to "A4, landscape, 5 columns", "paper" to "A4", "orientation" to "landscape", "matched" to true)))
        assertEquals("A4, landscape, 5 columns", d.format?.summary)
        assertTrue(d.format?.matched == true)
        assertNull(parseDay(mapOf("date" to "2026-11-08")).format)
    }

    // ---- pay first, spend stats, mismatches --------------------------------------

    @Test fun parsesCheckoutAndOrder() {
        val c = parseCheckout(mapOf("order" to "ord_1", "pay_url" to "https://x/board/pay/t", "amount" to 590.0, "pack" to mapOf("key" to "starter", "label" to "Starter", "credits" to 500.0)))
        assertEquals("ord_1", c.order); assertEquals("https://x/board/pay/t", c.payUrl); assertEquals(500, c.credits)
        val o = parseOrder(mapOf("order" to "ord_1", "status" to "paid", "credits" to 500.0, "balance" to 600.0))
        assertEquals("paid", o.status); assertEquals(500, o.credits); assertEquals(600, o.balance)
        assertNull(parseOrder(mapOf("order" to "o", "status" to "pending")).balance)
    }

    @Test fun parsesSpendStatsAndTheCheckedLine() {
        val d = parseCredits(mapOf(
            "balance" to 600.0, "costs" to mapOf("add" to 4.0, "check" to 1.0), "credit_months" to 12.0,
            "spent" to mapOf("today" to 7.0, "month" to 31.0, "all" to 120.0),
            "checked" to mapOf("at" to "2026-11-09 07:05:00", "cases" to 7.0, "credits" to 7.0, "on_check" to 7.0),
        ))
        assertEquals(SpentStats(7, 31, 120), d.spent)
        assertEquals(CheckedStats("2026-11-09 07:05:00", 7, 7, 7), d.checked)
        assertEquals(SpentStats(0, 0, 0), parseCredits(mapOf("balance" to 1.0)).spent)
    }

    @Test fun importMismatchesAreParsed() {
        val done = parseImportDone(mapOf(
            "added" to 2.0, "updated" to 0.0, "total" to 7.0,
            "mismatches" to listOf(mapOf("id" to 9.0, "cnr" to "PBLD010045672025", "case_no" to "CRM/456/2025", "title" to "State v. X", "ecourts_date" to "2026-11-10", "your_date" to "2026-11-08")),
        ))
        val m = done.mismatches.single()
        assertEquals(9, m.id); assertEquals("CRM/456/2025", m.heading); assertEquals("2026-11-10", m.ecourtsDate); assertEquals("2026-11-08", m.yourDate)
        assertTrue(parseImportDone(mapOf("added" to 1.0)).mismatches.isEmpty())
    }

    @Test fun packLastsReadsLikeTheWeb() {
        assertEquals("About 71 days at 7 cases", packLasts(500, 7, 1, 12))
        assertEquals("About 1 day at 1 case", packLasts(1, 0, 1, 12))
        assertEquals("Covers 15 cases for 12 months", packLasts(5500, 7, 1, 12))
    }
}
