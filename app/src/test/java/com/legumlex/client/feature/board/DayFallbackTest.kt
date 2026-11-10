package com.legumlex.client.feature.board

import com.legumlex.client.data.api.ApiService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.time.LocalDate
import java.time.ZoneId

/** When the day cannot load, the copy kept on the device is shown, with when it was saved. */
class DayFallbackTest {
    private val dayData = mapOf("data" to mapOf("date" to "2026-11-10", "items" to listOf(mapOf("id" to 5.0, "cnr" to "PBLD010012342026")), "counts" to mapOf("total" to 1.0, "listed" to 1.0)))

    /** An API whose boardDay answers with [answer] (a map) or throws it (a Throwable); no other call is made. */
    private fun repo(root: File, answer: () -> Any): BoardRepository {
        val none = Proxy.newProxyInstance(ApiService::class.java.classLoader, arrayOf(ApiService::class.java)) { _, m, _ -> error("unexpected ${m.name}") } as ApiService
        val api = object : ApiService by none {
            @Suppress("UNCHECKED_CAST")
            override suspend fun boardDay(date: String): Map<String, Any?> = answer().let { if (it is Throwable) throw it else it as Map<String, Any?> }
        }
        return BoardRepository(api, root)
    }

    @Test fun offlineShowsTheSavedCopy() = runBlocking {
        val root = Files.createTempDirectory("day").toFile()
        assertNull(repo(root) { IOException("offline") }.dayOrSaved("2026-11-10").getOrNull())   // nothing saved yet: the failure stands
        val ok = repo(root) { dayData }.dayOrSaved("2026-11-10").getOrThrow()
        assertNull(ok.savedAt); assertEquals(1, ok.day.items.size)
        val off = repo(root) { IOException("offline") }.dayOrSaved("2026-11-10").getOrThrow()
        assertNotNull(off.savedAt); assertEquals(1, off.day.items.size)
        assertEquals(5, off.day.items[0].id)
    }

    @Test fun aRefusalIsNotAnsweredFromTheCopy() = runBlocking {
        val root = Files.createTempDirectory("day").toFile()
        repo(root) { dayData }.dayOrSaved("2026-11-10")
        // A non-HTTP, non-IO failure (not offline, no server error code) is not "weak signal".
        assertTrue(repo(root) { IllegalStateException("x") }.dayOrSaved("2026-11-10").isFailure)
    }

    @Test fun savedLabel() {
        val zone = ZoneId.of("Asia/Kolkata")
        val at = LocalDate.of(2026, 11, 10).atTime(15, 20).atZone(zone).toInstant().toEpochMilli()
        assertEquals("Saved copy from 3:20 PM", BoardDates.savedLabel(at, LocalDate.of(2026, 11, 10), zone))
        assertTrue(BoardDates.savedLabel(at, LocalDate.of(2026, 11, 12), zone).endsWith(", 3:20 PM"))
    }
}

/** Dates follow eCourts: only an existing case with a CNR has its Next Date locked. */
class NextDateLockTest {
    private fun case(cnr: String, hasCnr: Boolean) = parseCase(mapOf("id" to 1.0, "cnr" to cnr, "has_cnr" to hasCnr))
    @Test fun lockedForACnrCase() { assertTrue(nextDateLocked(case("PBLD010012342026", true))) }
    @Test fun openForANewCase() { assertEquals(false, nextDateLocked(null)) }
    @Test fun openForAHandCase() { assertEquals(false, nextDateLocked(case("", false))) }
}
