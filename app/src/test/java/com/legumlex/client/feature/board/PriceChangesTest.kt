package com.legumlex.client.feature.board

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** REQ-038 038.11: the pending cost changes on the Credits screen, as the web lists them. */
class PriceChangesTest {
    @Test fun parsesAndWords() {
        val d = mapOf("costs" to mapOf("add" to 4.0, "check" to 1.0), "price_changes" to listOf(
            mapOf("action" to "check", "label" to "Morning Check", "now" to 1.0, "next" to 2.0, "from" to "2027-01-01"),
            mapOf("action" to "add", "label" to "Update From Court Or Add By CNR", "now" to 4.0, "next" to 1.0, "from" to "2027-02-15"),
        ))
        val pcs = parseCredits(d).priceChanges
        assertEquals(listOf(PriceChange("check", "Morning Check", 1, 2, "2027-01-01"), PriceChange("add", "Update From Court Or Add By CNR", 4, 1, "2027-02-15")), pcs)
        assertEquals("Morning Check: 2 credits from Fri, 1 Jan 2027", priceChangeLine(pcs[0]))
        assertEquals("Update From Court Or Add By CNR: 1 credit from Mon, 15 Feb 2027", priceChangeLine(pcs[1]))
        assertEquals("X: 1,000 credits from soon", priceChangeLine(PriceChange("x", "X", 1, 1000, "soon")))
    }

    @Test fun noneWhenEmptyOrAbsent() {
        assertTrue(parseCredits(mapOf("price_changes" to emptyList<Any>())).priceChanges.isEmpty())
        assertTrue(parseCredits(emptyMap()).priceChanges.isEmpty())
    }
}
