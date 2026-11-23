package com.legumlex.client.feature.board

import com.legumlex.client.data.api.ApiService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.time.LocalDate

/** REQ-038: the case page's tabs, and 038.7 offline copies of the case list and case page. */
class OfflineAndTabsTest {
    /** The server's answer for each call: a body, or a failure to throw. */
    private var answer: () -> Any = { error("unset") }
    // A failure is handed to the suspend call's continuation: a proxy may not throw a checked IOException itself.
    @Suppress("UNCHECKED_CAST")
    private val api = Proxy.newProxyInstance(ApiService::class.java.classLoader, arrayOf(ApiService::class.java)) { _, _, args ->
        when (val a = answer()) {
            is Throwable -> { (args.last() as kotlin.coroutines.Continuation<Any?>).resumeWith(Result.failure(a)); kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED }
            else -> a
        }
    } as ApiService

    @Test fun whenTheCopyMayAnswer() {
        assertTrue(savedCopyAllowed(offline = true, http = null))
        assertTrue(savedCopyAllowed(offline = false, http = 503))
        assertFalse(savedCopyAllowed(offline = false, http = 403))
        assertFalse(savedCopyAllowed(offline = false, http = 404))
        assertFalse(savedCopyAllowed(offline = false, http = null))
    }

    @Test fun casePageFallsBackOnlyWhenUnreachable() = runBlocking {
        val repo = BoardRepository(api, Files.createTempDirectory("board").toFile())
        answer = { mapOf("data" to mapOf("id" to 9.0, "case_no" to "CS/1/2026")) }
        val (live, at) = repo.caseOrSaved(9).getOrThrow()
        assertEquals("CS/1/2026", live.caseNo); assertNull(at)
        answer = { java.io.IOException("no network") }
        val (kept, keptAt) = repo.caseOrSaved(9).getOrThrow()
        assertEquals("CS/1/2026", kept.caseNo); assertNotNull(keptAt)
        // Any failure that is not "unreachable" is the server's or the app's word, never answered from the copy
        // (403 and 404 by savedCopyAllowed above; an HttpException body needs org.json, a stub in unit tests).
        answer = { IllegalStateException("bad answer") }; assertTrue(repo.caseOrSaved(9).isFailure)
        answer = { java.io.IOException("no network") }; assertTrue(repo.caseOrSaved(10).isFailure)   // never opened: no copy
    }

    @Test fun casesListKeptPerQueryAndPage() = runBlocking {
        val repo = BoardRepository(api, Files.createTempDirectory("board").toFile())
        answer = { mapOf("data" to mapOf("total" to 1.0, "items" to listOf(mapOf("id" to 4.0)))) }
        repo.casePageOrSaved(CaseQuery(filter = "today"), 0).getOrThrow()
        answer = { java.io.IOException("no network") }
        val (p, at) = repo.casePageOrSaved(CaseQuery(filter = "today"), 0).getOrThrow()
        assertEquals(4, p.items.single().id); assertNotNull(at)
        assertTrue(repo.casePageOrSaved(CaseQuery(filter = "eye"), 0).isFailure)   // another query was never kept
        assertNotEquals(casesKey(CaseQuery(), 0), casesKey(CaseQuery(), 100))
        assertEquals(casesKey(CaseQuery(q = " Mehta "), 0), casesKey(CaseQuery(q = "mehta"), 0))
    }

    @Test fun tabsAndHead() {
        val today = LocalDate.of(2026, 11, 23)
        val c = parseCase(mapOf("id" to 1.0, "next_date" to "2026-11-28", "court" to "Civil Judge", "court_line" to "Civil Judge at Ludhiana"))
        assertEquals("Next: ${BoardDates.show("2026-11-28", today)} · Civil Judge at Ludhiana", caseHeadLine(c, today))
        assertEquals("", caseHeadLine(parseCase(mapOf("id" to 1.0)), today))
    }
}
