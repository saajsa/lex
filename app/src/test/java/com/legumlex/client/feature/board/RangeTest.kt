package com.legumlex.client.feature.board

import com.legumlex.client.data.api.ApiService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Proxy
import java.time.LocalDate

/** REQ-038 038.4: the cause list for 7 or 14 days, as the web's Days Ahead. */
class RangeTest {
    private val body = mapOf("from" to "2026-11-23", "to" to "2026-11-29", "max_days" to 31.0, "days" to listOf(
        mapOf("date" to "2026-11-23", "items" to listOf(mapOf("id" to 5.0, "case_no" to "CS/1/2026")), "counts" to mapOf("total" to 1.0)),
        mapOf("date" to "2026-11-24", "items" to emptyList<Any>(), "counts" to mapOf("total" to 0.0)),
    ))

    @Test fun parsesAndWords() {
        val r = parseRange(body)
        assertEquals("2026-11-29", r.to); assertEquals(31, r.maxDays); assertEquals(2, r.days.size); assertEquals(5, r.days[0].items[0].id)
        assertEquals("1 listing in these days. The PDF leaves out the days with none.", rangeSummary(r))
        assertEquals("Nothing is listed in these days.", rangeSummary(r.copy(days = r.days.map { it.copy(items = emptyList()) })))
        val from = LocalDate.of(2026, 11, 23)
        assertEquals(LocalDate.of(2026, 11, 29), rangeTo(from, 7)); assertEquals(LocalDate.of(2026, 12, 6), rangeTo(from, 14))
        assertEquals("23 Nov to 29 Nov 2026", rangeLine(from, rangeTo(from, 7)))
        assertEquals("Today, Monday, 23 November 2026", rangeDayTitle("2026-11-23", from))
        assertEquals("Tuesday, 24 November 2026", rangeDayTitle("2026-11-24", from))
    }

    @Test fun asksForTheRange() = runBlocking {
        val calls = mutableListOf<Pair<String, List<Any?>>>()
        val api = Proxy.newProxyInstance(ApiService::class.java.classLoader, arrayOf(ApiService::class.java)) { _, m, a -> calls += m.name to a.orEmpty().dropLast(1); mapOf("data" to body) } as ApiService
        BoardRepository(api, null).range("2026-11-23", "2026-12-06")
        assertEquals("boardRange" to listOf<Any?>("2026-11-23", "2026-12-06"), calls.single())
    }
}
