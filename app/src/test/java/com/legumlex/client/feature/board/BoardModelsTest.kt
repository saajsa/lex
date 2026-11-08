package com.legumlex.client.feature.board

import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The server's shapes (REQ-008 section 8), read as the app reads them: Moshi maps in, plain classes out. */
class BoardModelsTest {
    private val adapter = Moshi.Builder().build().adapter(Map::class.java)

    @Suppress("UNCHECKED_CAST")
    private fun read(json: String): Map<String, Any?> = adapter.fromJson(json) as Map<String, Any?>

    private val case1 = """{"id":550,"cnr":"PBLD010012342026","case_no":"CS/123/2026","case_type":"Civil Suit","title":"Rajinder Kumar v. Harpreet Singh",
        "petitioner":"Rajinder Kumar","respondent":"Harpreet Singh","court":"Civil Judge Ludhiana","court_key":"PBLD01","room":"Court No. 12","stage":"Evidence",
        "next_date":"2026-11-08","last_date":null,"note":"","source":"import","junior":{"id":90,"name":"Junior A","by":"court"},"case_junior_id":null,
        "morning_check":true,"check":{"result":"listed_today","for":"2026-11-08","at":"2026-11-08 07:30:00"},"refresh_asked_at":null,
        "entry":{"id":3,"item_no":"14","status":"Adjourned","next_date":"2026-12-10","remarks":"x"},"item_no":"14"}"""
    private val case2 = """{"id":551,"cnr":"PBLD010045672025","case_no":"","title":"","court":"","room":"","stage":"","next_date":null,"junior":null,
        "morning_check":false,"check":null,"entry":null,"item_no":""}"""

    @Test fun caseWithEverything() {
        val c = parseCase(read(case1))
        assertEquals(550, c.id)
        assertEquals("CS/123/2026", c.heading)
        assertEquals("2026-11-08", c.nextDate)
        assertNull(c.lastDate)
        assertEquals(JuniorRef(90, "Junior A", "court"), c.junior)
        assertNull(c.caseJuniorId)
        assertTrue(c.morningCheck)
        assertEquals("Listed Today", c.check?.label)
        assertEquals("Adjourned", c.entry?.status)
        assertEquals("14", c.itemNo)
    }

    @Test fun caseWithNothing() {
        val c = parseCase(read(case2))
        assertEquals("PBLD010045672025", c.heading)
        assertNull(c.junior); assertNull(c.check); assertNull(c.entry)
        assertFalse(c.morningCheck)
    }

    @Test fun checkAnswersComeFromTheServer() {
        assertEquals("Listed Tomorrow", MorningCheck("listed_tomorrow", null, null).label)
        assertEquals("Not Listed", MorningCheck("not_listed", null, null).label)
    }

    private val dayJson = """{"date":"2026-11-08","items":[$case1,$case2],
        "by_court":[{"court":"Civil Judge Ludhiana","count":1,"rooms":[{"room":"Court No. 12","items":[$case1]}]},
                    {"court":"Court Not Known","count":1,"rooms":[{"room":"Room Not Known","items":[$case2]}]}],
        "by_junior":[{"junior":null,"items":[$case2]},{"junior":{"id":90,"name":"Junior A"},"items":[$case1]}],
        "counts":{"total":2,"with_result":1,"listed":1}}"""

    @Test fun dayKeepsTheServersGrouping() {
        val d = parseDay(read(dayJson))
        assertEquals("2026-11-08", d.date)
        assertEquals(2, d.items.size)
        assertEquals(2, d.total); assertEquals(1, d.withResult); assertEquals(1, d.listed)
        // Court, then room, then item.
        assertEquals(listOf("Civil Judge Ludhiana", "Court Not Known"), d.byCourt.map { it.court })
        assertEquals("Court No. 12", d.byCourt[0].rooms[0].room)
        assertEquals(550, d.byCourt[0].rooms[0].items[0].id)
        assertEquals("Room Not Known", d.byCourt[1].rooms[0].room)
        // By junior: the unassigned group has no junior.
        assertNull(d.byJunior[0].junior)
        assertEquals("Junior A", d.byJunior[1].junior?.name)
        assertEquals(d.total, d.byCourt.sumOf { it.count })
        assertEquals(d.total, d.byJunior.sumOf { it.items.size })
    }

    @Test fun emptyDay() {
        val d = parseDay(read("""{"date":"2026-11-09","items":[],"by_court":[],"by_junior":[],"counts":{"total":0,"with_result":0,"listed":0}}"""))
        assertTrue(d.items.isEmpty() && d.byCourt.isEmpty() && d.byJunior.isEmpty())
        assertEquals(0, d.total)
    }

    @Test fun dayRoundTripsThroughTheCacheFile() {
        // The weak-signal cache stores the day's own JSON and reads it back the same.
        val raw = read(dayJson)
        val again = read(adapter.toJson(raw))
        assertEquals(parseDay(raw), parseDay(again))
    }

    @Test fun summaryAndStatusesComeFromTheServer() {
        val s = parseSummary(read("""{"today":"2026-11-08","cases":7,"listed_today":5,"listed_tomorrow":2,"results_pending":4,"results_entered":1,"juniors":2,"unassigned_today":3,"credits":100,"statuses":["Heard","Adjourned"]}"""))
        assertEquals(7, s.cases); assertEquals(100, s.credits)
        assertEquals(listOf("Heard", "Adjourned"), s.statuses)
    }

    @Test fun importPreview() {
        val p = parsePreview(read("""{"import_id":"abc","kind":"mycases","found":7,"new":6,"existing":1,"duplicates":0,"error_count":1,"errors":[{"row":3,"message":"Bad CNR"}],
            "sample":[{"cnr":"PBLD010012342026","case_no":"CS/1/2026","court":"X","next_date":"2026-11-08","is_new":true}]}"""))
        assertEquals("abc", p.importId); assertEquals(6, p.new); assertEquals(1, p.existing)
        assertEquals(ImportRowError(3, "Bad CNR"), p.errors[0])
        assertTrue(p.sample[0].isNew)
        assertEquals(ImportDone(7, 0, 7), parseImportDone(read("""{"added":7,"updated":0,"total":7}""")))
    }

    @Test fun juniorsAndCourts() {
        val j = parseJuniors(read("""{"juniors":[{"id":90,"name":"A","cases":2,"courts":["PBLD01"]}],"courts":[{"court_key":"PBLD01","court":"C","cases":2,"junior":{"id":90,"name":"A"}},{"court_key":"X","court":"D","cases":1,"junior":null}]}"""))
        assertEquals(listOf("PBLD01"), j.juniors[0].courts)
        assertEquals(90, j.courts[0].junior?.id)
        assertNull(j.courts[1].junior)
        // An assignment that answers with an empty list for data is not an error to parse.
        assertNotNull(parseJuniors(emptyMap()))
    }

    @Test fun credits() {
        val c = parseCredits(read("""{"balance":100,"expiring":[{"credits":5,"expires_at":"2026-12-01"}],"packs":[{"key":"starter","label":"Starter","credits":500,"price":590}],
            "costs":{"add":4,"check":4},"history":[{"id":1,"when":"2026-11-08 00:15:25","kind":"trial","label":"Free Trial","delta":100,"balance_after":100,"reference":"x","expires_at":"2027-11-08"}],
            "purchases":[{"id":2,"pack":"starter","credits":500,"status":"pending","invoice_id":9,"created_at":"2026-11-08 01:00:00","paid_at":null,"pay_link":"https://x/invoice/9/abc"}],"credit_months":12}"""))
        assertEquals(100, c.balance); assertEquals(4, c.costAdd); assertEquals(4, c.costCheck)
        assertEquals(590.0, c.packs[0].price, 0.0)
        assertEquals("Free Trial", c.history[0].label)
        assertEquals("https://x/invoice/9/abc", c.purchases[0].payLink)
        assertEquals(12, c.creditMonths)
    }
}
