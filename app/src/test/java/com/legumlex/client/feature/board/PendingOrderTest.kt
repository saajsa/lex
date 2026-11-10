package com.legumlex.client.feature.board

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** The pending credits order: kept on the device across restarts, cleared when settled, and read from the server's own status. */
class PendingOrderTest {
    private fun root(): File = Files.createTempDirectory("pending").toFile()

    @Test fun survivesANewStore() {
        val r = root()
        PendingOrderStore(r).put(PendingOrder("order_abc", "starter", 1_700_000_000_000))
        // A new store over the same folder is the app after the process died.
        assertEquals(PendingOrder("order_abc", "starter", 1_700_000_000_000), PendingOrderStore(r).get())
    }

    @Test fun clearForgetsIt() {
        val r = root()
        val s = PendingOrderStore(r)
        s.put(PendingOrder("o1", "p", 1))
        s.clear()
        assertNull(s.get())
        assertNull(PendingOrderStore(r).get())
    }

    @Test fun signOutWipesIt() {
        // Sign-out deletes cache/board; the order goes with it.
        val r = root()
        PendingOrderStore(r).put(PendingOrder("o1", "p", 1))
        File(r, "board").deleteRecursively()
        assertNull(PendingOrderStore(r).get())
    }

    @Test fun noCacheFolderIsHarmless() {
        val s = PendingOrderStore(null)
        s.put(PendingOrder("o1", "p", 1))
        assertNull(s.get())
        s.clear()
    }

    @Test fun aNewPurchaseReplacesTheOldOrder() {
        val r = root()
        val s = PendingOrderStore(r)
        s.put(PendingOrder("o1", "a", 1)); s.put(PendingOrder("o2", "b", 2))
        assertEquals("o2", s.get()?.order)
    }

    @Test fun serverStatusDecidesTheVerdict() {
        assertEquals(OrderVerdict.Paid, orderVerdict("paid"))
        // Expired and cancelled: nothing was charged, the order is done.
        assertEquals(OrderVerdict.Failed, orderVerdict("expired"))
        assertEquals(OrderVerdict.Failed, orderVerdict("cancelled"))
        // Failed stays payable on the server and a later capture is honoured: it is kept, not "nothing was charged".
        assertEquals(OrderVerdict.NotCompleted, orderVerdict("failed"))
        assertEquals(OrderVerdict.Flagged, orderVerdict("flagged"))
        // Pending, anything unknown, and no answer at all keep the order for the next check.
        assertEquals(OrderVerdict.Pending, orderVerdict("pending"))
        assertEquals(OrderVerdict.Pending, orderVerdict("something_new"))
        assertEquals(OrderVerdict.Pending, orderVerdict(null))
    }

    @Test fun onlyExpiredSaysNothingWasCharged() {
        assertTrue(EXPIRED_NOTE.contains("nothing was charged"))
        assertFalse(FAILED_NOTE.contains("nothing was charged"))
        assertEquals("Payment not completed. If you did pay, it will be matched and your credits added.", FAILED_NOTE)
    }

    @Test fun aFailedOrderIsKeptForAWhileThenLetGo() {
        val start = 1_000_000_000L
        assertFalse(failedOrderGivenUp(start, start + 60_000))
        assertFalse(failedOrderGivenUp(start, start + FAILED_KEEP_MILLIS))
        assertTrue(failedOrderGivenUp(start, start + FAILED_KEEP_MILLIS + 1))
        // An order with no start time cannot be kept for "a while".
        assertTrue(failedOrderGivenUp(0L, start))
    }

    @Test fun anUnknownOrderResolvesButAFailedAskDoesNot() {
        assertTrue(orderGone(404))
        assertFalse(orderGone(500))
        assertFalse(orderGone(403))
        assertFalse(orderGone(null))
    }
}
