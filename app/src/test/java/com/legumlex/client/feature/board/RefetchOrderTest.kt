package com.legumlex.client.feature.board

import com.legumlex.client.data.api.ApiService
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy
import java.nio.file.Files

/** REQ-051 D4: Fetch Again From Court on a kept order: its price, the web's words, and the new copy replacing this device's one. */
class RefetchOrderTest {
    private val kept = mapOf("id" to 7.0, "label" to "Order of 12 Nov 2026", "state" to "on_file", "cost" to 0.0, "pages" to 2.0)

    @Test fun thePriceComesFromCostsOrder() {
        val c = parseCredits(mapOf("balance" to 50.0, "costs" to mapOf("add" to 4.0, "check" to 4.0, "order" to 9.0)))
        assertEquals(9, c.costOrder)
        assertEquals(0, parseCredits(mapOf("balance" to 50.0, "costs" to mapOf("add" to 4.0))).costOrder)
        // A kept order lists cost 0, so its own cost is never the price.
        assertEquals(0, parseCourtOrder(kept).cost)
    }

    @Test fun theWebsWords() {
        assertEquals(
            "We ask the court for this order's copy again and replace the copy you have. Use it when the court may have changed or corrected the order. " +
                "This uses 9 credits, taken only if the new copy arrives; if it does not, you keep the copy you have.",
            refetchLine(9),
        )
        assertTrue(refetchLine(null).contains("This uses credits,"))
        assertEquals("Fetch this order again from the court for 1 credit?", refetchQuestion(1))
        assertEquals("Fetch this order again from the court?", refetchQuestion(null))
        assertEquals("New copy kept. 9 credits used. Balance: 41 credits.", refetchDoneLine(OrderFetch(9, 41, parseCourtOrder(kept))))
        listOf(refetchLine(9), refetchQuestion(9)).forEach { assertFalse(it.contains("—")); assertFalse(it.contains("ertified")) }
    }

    @Test fun theLegendSaysHowToFetchAgain() {
        assertTrue(LEGEND.single { it == Sym.OrderKept }.line.endsWith("Press and hold a kept order to fetch it again from the court."))
    }

    @Test fun theNewCopyReplacesThisDevicesCopy() = runBlocking {
        val calls = mutableListOf<String>()
        var body = "%PDF-1.4 old"
        val api = Proxy.newProxyInstance(ApiService::class.java.classLoader, arrayOf(ApiService::class.java)) { _, m, a ->
            calls += m.name + a.orEmpty().dropLast(1)
            when (m.name) {
                "boardOrderFile" -> body.toResponseBody()
                "boardOrderRefetch" -> mapOf("data" to mapOf("charged" to 9.0, "balance" to 41.0, "order" to kept))
                else -> error(m.name)
            }
        } as ApiService
        val repo = BoardRepository(api, Files.createTempDirectory("board").toFile())
        assertEquals("%PDF-1.4 old", repo.orderFile(5, 7).getOrThrow().readText())
        body = "%PDF-1.4 new"
        val r = repo.orderRefetch(5, 7).getOrThrow()
        assertEquals(9, r.charged); assertEquals(41, r.balance)
        assertEquals("boardOrderRefetch[5, 7]", calls.last())
        assertFalse(repo.orderCacheFile(5, 7)!!.exists())
        assertEquals("%PDF-1.4 new", repo.orderFile(5, 7).getOrThrow().readText())
        assertEquals(2, calls.count { it.startsWith("boardOrderFile") })
    }

    @Test fun aRefusalKeepsThisDevicesCopy() = runBlocking {
        val api = Proxy.newProxyInstance(ApiService::class.java.classLoader, arrayOf(ApiService::class.java)) { _, m, _ ->
            when (m.name) {
                "boardOrderFile" -> "%PDF-1.4 old".toResponseBody()
                "boardOrderRefetch" -> throw retrofit2.HttpException(retrofit2.Response.error<Any>(402,
                    """{"status":false,"code":"insufficient_credits","message":"Not enough credits.","cost":9,"balance":2}""".toResponseBody()))
                else -> error(m.name)
            }
        } as ApiService
        val repo = BoardRepository(api, Files.createTempDirectory("board").toFile())
        repo.orderFile(5, 7).getOrThrow()
        val e = repo.orderRefetch(5, 7).exceptionOrNull()!!
        // The refusal reaches the wizard as the server sent it (its code and message are read by boardCode/boardMessage, Android's JSON).
        assertEquals(402, (e as retrofit2.HttpException).code())
        assertTrue(repo.orderCacheFile(5, 7)!!.exists())
    }
}
