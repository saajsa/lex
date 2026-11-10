package com.legumlex.client.feature.auth

import com.legumlex.client.feature.board.BoardDates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** REQ-013 013.3: the date and the maxim at the foot of Sign In. */
class SignInDateTest {
    @Test fun ordinalSuffixes() {
        val want = mapOf(1 to "1st", 2 to "2nd", 3 to "3rd", 4 to "4th", 10 to "10th", 11 to "11th", 12 to "12th", 13 to "13th",
            21 to "21st", 22 to "22nd", 23 to "23rd", 24 to "24th", 30 to "30th", 31 to "31st")
        want.forEach { (n, s) -> assertEquals(s, ordinalDay(n)) }
    }

    @Test fun theDateWordsAreTheReferencesThree() {
        val w = dateWords(LocalDate.of(2026, 11, 10))
        assertEquals(DateWords("Tue", "10th", "November"), w)
    }

    @Test fun todayIsIndiaNotThePhonesZone() {
        // 18:29 UTC on 10 Nov is 23:59 IST on 10 Nov; one minute later it is the 11th in India.
        assertEquals(LocalDate.of(2026, 11, 10), BoardDates.todayAt(Instant.parse("2026-11-10T18:29:00Z")))
        assertEquals(LocalDate.of(2026, 11, 11), BoardDates.todayAt(Instant.parse("2026-11-10T18:30:00Z")))
        assertEquals("11th", dateWords(BoardDates.todayAt(Instant.parse("2026-11-10T18:30:00Z"))).day)
    }

    @Test fun theMaximIsStableForADateAndMovesWithTheDay() {
        val d = LocalDate.of(2026, 11, 10)
        assertEquals(SignInMaxims.forDate(d), SignInMaxims.forDate(d))
        assertNotNull(SignInMaxims.forDate(d))
        assertTrue(SignInMaxims.all.contains(SignInMaxims.forDate(LocalDate.of(2026, 12, 31))))
        // Consecutive days take consecutive entries of the list.
        val a = SignInMaxims.all.indexOf(SignInMaxims.forDate(d))
        val b = SignInMaxims.all.indexOf(SignInMaxims.forDate(d.plusDays(1)))
        assertEquals((a + 1) % SignInMaxims.all.size, b)
    }

    @Test fun theMaximListIsThe31AndWellFormed() {
        assertEquals(31, SignInMaxims.all.size)
        SignInMaxims.all.forEachIndexed { i, m ->
            assertTrue("maxim ${i + 1} must end with a full stop: ${m.latin}", m.latin.endsWith("."))
            assertTrue("maxim ${i + 1} meaning has ${m.meaning.split(" ").size} words (8 at most): ${m.meaning}", m.meaning.split(" ").size <= 8)
            assertTrue("maxim ${i + 1} meaning must end with a full stop", m.meaning.endsWith("."))
        }
        assertEquals(31, SignInMaxims.all.map { it.latin }.toSet().size)
    }

    @Test fun everyDayOfALeapYearHasAMaxim() {
        var d = LocalDate.of(2028, 1, 1)
        while (d.year == 2028) { assertNotNull(SignInMaxims.forDate(d)); d = d.plusDays(1) }
    }
}
