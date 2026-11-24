package com.legumlex.client.feature.board

import com.legumlex.client.data.api.ApiService
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy
import java.nio.file.Files

/** REQ-038 038.2 listings and 038.3 orders, on the final contract (state, listings[], X-Orders-Left-Out). */
class CourtOrdersTest {
    private val onFile = mapOf("id" to 7.0, "label" to "Order of 12 Nov 2026", "date" to "2026-11-12", "kind" to "Order", "state" to "on_file", "cost" to 0.0, "pages" to 3.0, "size" to 52000.0, "fetched_at" to "2026-11-23 08:00:00")
    private val available = mapOf("id" to 8.0, "label" to null, "date" to "2026-10-30", "kind" to "Interim Order", "state" to "available", "cost" to 9.0, "pages" to null, "size" to null, "fetched_at" to null)
    private val signed = onFile + mapOf("id" to 9.0, "label" to "Order of 12 Nov 2026, No. 2", "state" to "signed", "pages" to 1.0)

    @Test fun parsesListingsAndOrders() {
        val c = parseCase(mapOf("id" to 5.0, "record_loaded" to true,
            "listings" to listOf(mapOf("date" to "2026-11-12", "purpose" to "Arguments", "business" to null, "judge" to "Ld. Shri R. Singh", "item" to null, "outcome" to null,
                "next_date" to "2026-11-28", "next_purpose" to "Evidence", "order" to mapOf("id" to 7.0, "state" to "on_file"), "orders" to listOf(onFile))),
            "orders" to listOf(onFile, available)))
        val l = c.record.listings.single()
        assertTrue(c.record.loaded); assertEquals(7, l.orders.single().id); assertNull(l.business); assertNull(l.item); assertNull(l.outcome)
        assertEquals(listOf(7, 8), c.record.orders.map { it.id })
        assertEquals("Listed for Arguments. Next: ${BoardDates.long(java.time.LocalDate.of(2026, 11, 28))}, Evidence.", hearingLine(l))
        // Tolerant: an older or partial answer is an empty, unloaded record; an unknown state reads as not fetched.
        assertFalse(parseCase(mapOf("id" to 5.0)).record.loaded)
        assertTrue(parseCase(mapOf("id" to 5.0, "hearings" to listOf(mapOf("date" to "x")))).record.listings.isEmpty())
        assertEquals("available", parseCourtOrder(mapOf("id" to 1.0, "state" to "weird")).state)
    }

    @Test fun statesInTheWebsWords() {
        val k = parseCourtOrder(onFile); val a = parseCourtOrder(available); val s = parseCourtOrder(signed)
        assertTrue(k.fetched && !k.sealed); assertTrue(s.fetched && s.sealed); assertFalse(a.fetched)
        assertTrue(parseCourtOrder(onFile + ("state" to "encrypted")).sealed)
        assertEquals("Order of 12 Nov 2026", orderLabel(k))
        assertEquals("Interim Order of 30 Oct 2026", orderLabel(a))   // no label: kind and date, as the server builds it
        assertEquals("Kept, 3 pages", orderState(k))
        assertEquals("Kept, 1 page, signed: opens on its own", orderState(s))
        assertEquals("Not fetched yet", orderState(a))   // no cost shown on orders (owner, 23 Nov 2026)
        assertEquals("Copies from eCourts. A fetched copy is kept: opening it again is free.", ORDERS_LINE)
        assertEquals("Hearings come from eCourts once the case has a CNR.", recordEmpty("hearings", hasCnr = false, loaded = false))
        assertEquals("Update From Court to load orders.", recordEmpty("orders", hasCnr = true, loaded = false))
        assertEquals("eCourts lists no orders for this case yet.", recordEmpty("orders", hasCnr = true, loaded = true))
        assertEquals("Order of 12 Nov 2026.pdf", downloadName("Order of 12 Nov 2026"))
    }

    @Test fun combinedFollowsTheServer() {
        val k = parseCourtOrder(onFile); val a = parseCourtOrder(available); val s = parseCourtOrder(signed)
        assertTrue(combinedOffered(listOf(k, a))); assertFalse(combinedOffered(listOf(s, a)))
        assertEquals(listOf(9, 12), leftOutIds("9, 12")); assertTrue(leftOutIds("").isEmpty()); assertTrue(leftOutIds(null).isEmpty())
        assertNull(leftOutNote(0)); assertEquals("1 kept order is not in this PDF: it opens on its own.", leftOutNote(1))
    }

    @Test fun otherOrdersAreThoseUnderNoHearing() {
        val r = parseRecord(mapOf("record_loaded" to true,
            "listings" to listOf(mapOf("date" to "2026-11-12", "orders" to listOf(onFile))),
            "orders" to listOf(onFile, available, onFile + mapOf("id" to 11.0, "date" to null))))
        assertEquals(listOf(8, 11), otherOrders(r).map { it.id })   // one dated with no listing that day, one with no date
        assertTrue(otherOrders(parseRecord(mapOf("listings" to listOf(mapOf("date" to "2026-11-12", "orders" to listOf(onFile))), "orders" to listOf(onFile)))).isEmpty())
    }

    @Test fun readOfferedOnlyWhereItLoadsSomething() {
        assertTrue(offerRead(hasCnr = true, loaded = false))
        assertFalse(offerRead(hasCnr = true, loaded = true))    // eCourts lists none: a read would not help
        assertFalse(offerRead(hasCnr = false, loaded = false))  // no CNR: nothing to read
    }

    @Test fun neverCertified() {
        val root = listOf("src/main/java/com/legumlex/client/feature/board", "app/src/main/java/com/legumlex/client/feature/board").map(::File).first { it.exists() }
        listOf("BoardOrders.kt", "BoardCasesScreen.kt").forEach { assertFalse(it, File(root, it).readText().contains("ertified")) }
    }

    @Test fun keptCopyAndCombinedHeader() = runBlocking {
        val calls = mutableListOf<String>()
        val api = Proxy.newProxyInstance(ApiService::class.java.classLoader, arrayOf(ApiService::class.java)) { _, m, a ->
            calls += m.name + a.orEmpty().dropLast(1)
            when (m.name) {
                "boardOrderFile" -> "%PDF-1.4 kept".toResponseBody()
                "boardOrderFetch" -> mapOf("data" to mapOf("charged" to 9.0, "balance" to 111.0, "order" to onFile))
                "boardOrdersCombined" -> retrofit2.Response.success("%PDF-1.4 all".toResponseBody(), okhttp3.Headers.headersOf("X-Orders-Left-Out", "9"))
                else -> error(m.name)
            }
        } as ApiService
        val repo = BoardRepository(api, Files.createTempDirectory("board").toFile())
        val f1 = repo.orderFile(5, 7).getOrThrow(); val f2 = repo.orderFile(5, 7).getOrThrow()
        assertEquals("%PDF-1.4 kept", f2.readText()); assertEquals(f1, f2)
        assertEquals(1, calls.count { it.startsWith("boardOrderFile") })   // the second open is free and works offline
        assertTrue(f1.path.replace('\\', '/').contains("/board/orders/"))
        val (all, left) = repo.combined(5).getOrThrow()
        assertEquals("%PDF-1.4 all", all.readText()); assertEquals(listOf(9), left)
        val r = repo.orderFetch(5, 8).getOrThrow()
        assertEquals(9, r.charged); assertEquals(111, r.balance); assertEquals("on_file", r.order.state)
        assertEquals("boardOrderFetch[5, 8]", calls.last())
    }
}

/** The owner's reference (23 Nov 2026): hearings as a date-led timeline, the next date leading as UPCOMING. */
class TimelineTest {
    private val today = java.time.LocalDate.of(2026, 11, 23)
    private val rec = parseRecord(mapOf("record_loaded" to true,
        "listings" to listOf(
            mapOf("date" to "2026-11-08", "purpose" to "Evidence", "judge" to "Ms. Anita Sharma", "next_date" to "2026-12-04", "next_purpose" to "Arguments", "orders" to emptyList<Any>()),
            mapOf("date" to "2026-10-24", "purpose" to "", "judge" to "", "next_date" to null, "next_purpose" to "", "orders" to emptyList<Any>()),
        ),
        "orders" to listOf(mapOf("id" to 9.0, "date" to null, "state" to "available"))))

    @Test fun rows() {
        val rows = timelineRows(rec, "2026-12-04", today)
        assertEquals(listOf("UPCOMING", "HEARD · MS. ANITA SHARMA", "HEARD", "OTHER ORDERS"), rows.map { it.eyebrow })
        assertEquals("Arguments", rows[0].title); assertTrue(rows[0].upcoming); assertTrue(rows[0].orders.isEmpty())
        assertEquals("Evidence", rows[1].title); assertEquals("4 Dec 2026 · Arguments", rows[1].meta); assertEquals("Next", rows[1].metaLabel)
        assertEquals("Hearing", rows[2].title); assertNull(rows[2].meta)                // no purpose: "Hearing"
        assertEquals(listOf(9), rows[3].orders.map { it.id })
    }

    @Test fun upcomingOnlyAfterTheLastHearing() {
        assertEquals("HEARD · MS. ANITA SHARMA", timelineRows(rec, "2026-11-08", today).first().eyebrow)   // the same date: no extra row
        assertEquals("HEARD · MS. ANITA SHARMA", timelineRows(rec, null, today).first().eyebrow)
        val only = timelineRows(parseRecord(emptyMap()), "2026-12-04", today)
        assertEquals(1, only.size); assertEquals("Hearing", only[0].title)                     // no hearings yet: the next date alone
        assertEquals("LISTED", timelineRows(parseRecord(mapOf("listings" to listOf(mapOf("date" to "2026-11-23")))), null, today)[0].eyebrow)
    }
}
