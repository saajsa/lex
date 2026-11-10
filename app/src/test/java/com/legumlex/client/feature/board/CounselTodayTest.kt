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

    @Test fun courtLineLeadsWithTheBenchElseTheRoom() {
        val b = parseCase(mapOf("id" to 4.0, "court" to "Civil Judge (Jr. Div.) Ludhiana", "bench" to "Ld. Ms. Anjali Sharma", "room" to "Court 3"))
        assertEquals("Civil Judge (Jr. Div.) Ludhiana · Ld. Ms. Anjali Sharma", b.courtLine)
        val r = parseCase(mapOf("id" to 5.0, "court" to "Civil Judge", "room" to "Court 3"))
        assertEquals("Civil Judge · Court 3", r.courtLine)
        assertTrue(r.hasCnr)
        assertTrue(parseCase(mapOf("id" to 2.0, "needs_eye" to true)).needsEye)
    }

    // ---- the calendar flow ----------------------------------------------------------

    @Test fun theWeekRunsSundayToSaturday() {
        val mon = LocalDate.of(2026, 11, 9)
        assertEquals(LocalDate.of(2026, 11, 8), weekStart(mon))
        assertEquals(LocalDate.of(2026, 11, 8), weekStart(LocalDate.of(2026, 11, 8)))   // a Sunday is its own week's start
        assertEquals(LocalDate.of(2026, 11, 8), weekStart(LocalDate.of(2026, 11, 14)))   // the Saturday
        val days = weekDays(mon)
        assertEquals(7, days.size)
        assertEquals(LocalDate.of(2026, 11, 8), days.first())
        assertEquals(LocalDate.of(2026, 11, 14), days.last())
        assertTrue(mon in days)
    }

    @Test fun aSwipeMovesTheChosenDayByAWeekAcrossMonthsAndYears() {
        assertEquals(LocalDate.of(2026, 11, 16), shiftWeek(LocalDate.of(2026, 11, 9), 1))
        assertEquals(LocalDate.of(2026, 11, 2), shiftWeek(LocalDate.of(2026, 11, 9), -1))
        assertEquals(LocalDate.of(2027, 1, 1), shiftWeek(LocalDate.of(2026, 12, 25), 1))
        assertEquals(LocalDate.of(2026, 12, 27), weekStart(LocalDate.of(2027, 1, 1)))   // the week crosses the year
    }

    @Test fun theMonthsAcrossTheTopAndTheSheetHeading() {
        assertEquals(Triple("October", "November", "December"), monthsAround(LocalDate.of(2026, 11, 9)))
        assertEquals(Triple("December", "January", "February"), monthsAround(LocalDate.of(2027, 1, 20)))
        val today = LocalDate.of(2026, 11, 8)
        assertEquals("Today", sheetHeading(today, today))
        assertEquals("Monday, 9 Nov", sheetHeading(LocalDate.of(2026, 11, 9), today))
        assertEquals("3 cases · eCourts checked 7:05 AM", daySubline(3, true, "7:05 AM"))
        assertEquals("2 cases", daySubline(2, false, "7:05 AM"))
        assertEquals("1 case", daySubline(1, true, null))
    }

    @Test fun partiesComeFromTheServerOrFromTheTitle() {
        val named = parseCase(mapOf("id" to 1.0, "petitioner" to "Sunita Devi", "respondent" to "Municipal Corporation Ludhiana", "title" to "x"))
        assertEquals("Sunita Devi" to "Municipal Corporation Ludhiana", parties(named))
        assertEquals("State of Punjab" to "Gurdeep Singh & Ors.", parties(parseCase(mapOf("id" to 2.0, "title" to "State of Punjab v. Gurdeep Singh & Ors."))))
        assertEquals("A" to "B", parties(parseCase(mapOf("id" to 3.0, "title" to "A vs B"))))
        assertNull(parties(parseCase(mapOf("id" to 4.0, "title" to "A matter with no parties"))))
    }

    @Test fun theServersCourtAndJudgeLinesAreUsedWhenPresent() {
        val c = parseCase(mapOf("id" to 5.0, "court" to "Civil Judge (Jr. Div.) Ludhiana", "room" to "Court 3", "bench" to "Ld. Ms. Anjali Sharma",
            "court_line" to "Civil Judge (Jr. Div.) at Ludhiana", "judge_line" to "Ld. Ms. Anjali Sharma"))
        assertEquals("Civil Judge (Jr. Div.) at Ludhiana", c.courtShown)
        assertEquals("Ld. Ms. Anjali Sharma", c.judgeShown)
        assertEquals("Civil Judge (Jr. Div.) at Ludhiana · Ld. Ms. Anjali Sharma", c.courtLine)
        // Without them (an older server) the court and the bench or room are used.
        val old = parseCase(mapOf("id" to 6.0, "court" to "Civil Judge", "room" to "Court 3"))
        assertEquals("Civil Judge", old.courtShown)
        assertEquals("Court 3", old.judgeShown)
        // No room, "Ld. Court" when no judge is known.
        assertEquals("Ld. Court", parseCase(mapOf("id" to 7.0, "judge_line" to "Ld. Court")).judgeShown)
    }

    @Test fun theLinesAreWordedFromTheCounts() {
        assertEquals("11 cases tracked · 99 days of checks left", checksLine(11, 99))
        assertEquals("1 case tracked · 1 day of checks left", checksLine(1, 1))
        assertEquals("3 cases tracked · less than a day of checks left", checksLine(3, 0))
        assertEquals("3 cases tracked", checksLine(3, null))
        assertEquals("1 case listed", listedText(1))
        assertEquals("0 cases listed", listedText(0))
        assertEquals("1 date needs your eye", eyeText(1))
        assertEquals("2 dates need your eye", eyeText(2))
    }

    @Test fun readsTheDayCountsIncludingZeros() {
        val m = parseDays(mapOf("from" to "2026-11-06", "n" to 3.0, "days" to listOf(
            mapOf("date" to "2026-11-06", "listed" to 0.0), mapOf("date" to "2026-11-07", "listed" to 2.0), mapOf("date" to "2026-11-08", "listed" to "3"))))
        assertEquals(0, m["2026-11-06"])
        assertEquals(2, m["2026-11-07"])
        assertEquals(3, m["2026-11-08"])
        assertTrue(parseDays(mapOf("days" to null)).isEmpty())
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

    @Test fun extractsTheCnrFromAScannedCode() {
        assertEquals("PBLD010012342026", extractCnr("PBLD010012342026"))
        assertEquals("PBLD010012342026", extractCnr("pbld010012342026"))
        assertEquals("PBLD010012342026", extractCnr("https://services.ecourts.gov.in/ecourtindia_v6/?cino=PBLD010012342026&state=PB"))
        assertEquals("PBLD010012342026", extractCnr("  CNR: PBLD010012342026 \n"))
        assertNull(extractCnr("hello world"))
        assertNull(extractCnr("PBLD0100"))
        assertNull(extractCnr(""))
        assertNull(extractCnr(null))
    }
}
