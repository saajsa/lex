package com.legumlex.client.feature.board

import com.legumlex.client.data.api.ApiService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

/** REQ-038 038.1 alerts and 038.6 Share Summary. */
class AlertsAndShareTest {
    private val calls = mutableListOf<String>()
    private fun api(body: Map<String, Any?>): ApiService =
        Proxy.newProxyInstance(ApiService::class.java.classLoader, arrayOf(ApiService::class.java)) { _, m, _ -> calls += m.name; body } as ApiService

    @Test fun routesAndDeepLinks() {
        assertEquals(AlertTarget.Today, alertTarget("board"))
        assertEquals(AlertTarget.Case(42), alertTarget("board/cases/42"))
        assertEquals(AlertTarget.Today, alertTarget("board/cases/x"))
        assertEquals(AlertTarget.Today, alertTarget(null))
        assertEquals("legumlex://counsel/today", counselDeepLink("board"))
        assertEquals("legumlex://counsel/case/42", counselDeepLink("board/cases/42"))
    }

    @Test fun whichPushesAreAlerts() {
        assertTrue(isCounselAlert(mapOf("workspace" to "counsel", "alert_id" to "digest:2026-11-23", "kind" to "digest")))
        assertTrue(isCounselAlert(mapOf("workspace" to "counsel", "alert_id" to "moved:12:2026-11-24")))
        assertFalse(isCounselAlert(mapOf("workspace" to "counsel")))
        assertFalse(isCounselAlert(mapOf("workspace" to "client", "alert_id" to "x")))
    }

    @Test fun parsesAlerts() {
        val a = parseAlerts(mapOf("items" to listOf(
            mapOf("id" to 3.0, "kind" to "moved", "ref" to "moved:12:2026-11-24", "title" to "Date Moved", "body" to "CS/1/2026 is now on 24 Nov.", "data" to mapOf("route" to "board/cases/12"), "at" to "2026-11-23 07:05:00"),
            mapOf("id" to 2.0, "kind" to "digest", "ref" to "digest:2026-11-23", "title" to "Today's List", "body" to "", "data" to emptyList<Any>(), "at" to "2026-11-23 07:00:00"),
        )))
        assertEquals(listOf(3, 2), a.map { it.id })
        assertEquals("board/cases/12", a[0].route); assertEquals("board", a[1].route)   // PHP's empty data [] reads as no route
    }

    @Test fun shareTextFromTheCaseElseTheServer() = runBlocking {
        val repo = BoardRepository(api(mapOf("data" to mapOf("text" to "From the server"))), null)
        val c = parseCase(mapOf("id" to 9.0, "share_text" to "From the case"))
        assertEquals("From the case", repo.shareText(c).getOrThrow()); assertTrue(calls.isEmpty())
        assertEquals("From the server", repo.shareText(c.copy(shareText = "")).getOrThrow()); assertEquals(listOf("boardCaseShare"), calls)
        assertEquals("No link and no note of yours goes with it.", SHARE_NOTE)
    }
}
