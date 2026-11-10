package com.legumlex.client.feature.board

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertEquals(OrderVerdict.Failed, orderVerdict("failed"))
        assertEquals(OrderVerdict.Failed, orderVerdict("expired"))
        assertEquals(OrderVerdict.Flagged, orderVerdict("flagged"))
        // Pending, anything unknown, and no answer at all keep the order for the next check.
        assertEquals(OrderVerdict.Pending, orderVerdict("pending"))
        assertEquals(OrderVerdict.Pending, orderVerdict("something_new"))
        assertEquals(OrderVerdict.Pending, orderVerdict(null))
    }
}
