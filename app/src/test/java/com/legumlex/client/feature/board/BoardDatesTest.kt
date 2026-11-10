package com.legumlex.client.feature.board

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class BoardDatesTest {

    /** 20:00 UTC on 7 Nov is 01:30 on 8 Nov in India: the board's today is the 8th, whatever the phone says. */
    @Test fun todayIsIndiasToday() {
        val clock = Clock.fixed(Instant.parse("2026-11-07T20:00:00Z"), ZoneOffset.UTC)
        assertEquals(LocalDate.of(2026, 11, 8), BoardDates.today(clock))
    }

    @Test fun todayJustBeforeIndiaMidnight() {
        // 18:29 UTC is 23:59 IST on the 7th; 18:30 UTC is midnight, the 8th.
        assertEquals(LocalDate.of(2026, 11, 7), BoardDates.todayAt(Instant.parse("2026-11-07T18:29:59Z")))
        assertEquals(LocalDate.of(2026, 11, 8), BoardDates.todayAt(Instant.parse("2026-11-07T18:30:00Z")))
    }

    @Test fun phoneZoneDoesNotMatter() {
        val inNewYork = Clock.fixed(Instant.parse("2026-11-07T20:00:00Z"), java.time.ZoneId.of("America/New_York"))
        assertEquals(LocalDate.of(2026, 11, 8), BoardDates.today(inNewYork))
    }

    @Test fun stripIsAnchoredOnToday() {
        val today = LocalDate.of(2026, 11, 8)
        val strip = BoardDates.strip(today, today)
        assertEquals(BoardDates.STRIP_BACK + BoardDates.STRIP_AHEAD + 1, strip.size)
        assertEquals(today.minusDays(7), strip.first())
        assertEquals(today.plusDays(20), strip.last())
        assertTrue(today in strip)
        // Choosing another day inside the window does not move the strip.
        assertEquals(strip, BoardDates.strip(today.plusDays(3), today))
    }

    @Test fun stripFollowsADateOutsideTheWindow() {
        val today = LocalDate.of(2026, 11, 8)
        val far = LocalDate.of(2027, 2, 1)
        val strip = BoardDates.strip(far, today)
        assertTrue(far in strip)
        assertEquals(far.minusDays(7), strip.first())
    }

    @Test fun headings() {
        val today = LocalDate.of(2026, 11, 8)
        assertEquals("Today", BoardDates.heading(today, today))
        assertEquals("Tomorrow", BoardDates.heading(today.plusDays(1), today))
        assertEquals("Yesterday", BoardDates.heading(today.minusDays(1), today))
        assertEquals("Wed, 11 Nov 2026", BoardDates.heading(LocalDate.of(2026, 11, 11), today))
    }

    @Test fun parsingAndWriting() {
        assertEquals(LocalDate.of(2026, 11, 8), BoardDates.parse("2026-11-08"))
        assertEquals(LocalDate.of(2026, 11, 8), BoardDates.parse("2026-11-08 10:30:00"))
        assertNull(BoardDates.parse(""))
        assertNull(BoardDates.parse(null))
        assertNull(BoardDates.parse("soon"))
        assertEquals("2026-11-08", BoardDates.api(LocalDate.of(2026, 11, 8)))
        assertEquals("08-11-2026", BoardDates.dmy(LocalDate.of(2026, 11, 8)))
    }

    @Test fun shortShowsTheYearOnlyWhenNotThisYear() {
        val today = LocalDate.of(2026, 11, 8)
        assertEquals("10 Dec", BoardDates.short(LocalDate.of(2026, 12, 10), today))
        assertEquals("10 Jan 2027", BoardDates.short(LocalDate.of(2027, 1, 10), today))
        assertEquals("", BoardDates.show(null, today))
    }

    @Test fun creditsAreGroupedTheIndianWay() {
        assertEquals("1 credit", creditsLabel(1))
        assertEquals("0 credits", creditsLabel(0))
        assertEquals("2,100 credits", creditsLabel(2100))
        assertEquals("100", creditsText(100))
        assertEquals("2,100", creditsText(2100))
        assertEquals("5,500", creditsText(5500))
        assertEquals("1,00,000", creditsText(100000))
    }
}
