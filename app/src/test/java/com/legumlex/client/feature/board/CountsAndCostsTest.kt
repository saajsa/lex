package com.legumlex.client.feature.board

import com.legumlex.client.feature.workspace.PAUSED_TEXT
import com.legumlex.client.feature.workspace.pausedText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** REQ-038: an unset cost (0) shows no figure; the paused screen speaks the server's words; counts grouped the Indian way. */
class CountsAndCostsTest {
    @Test fun unsetCostShowsNoFigure() {
        assertNull(shownCost(0)); assertNull(shownCost(null)); assertEquals(4, shownCost(4))
        assertEquals("Checked every morning.", dailyCheckLine(on = true, hasCnr = true, cost = 0))
        assertEquals("Checked every morning for 1 credit.", dailyCheckLine(on = true, hasCnr = true, cost = 1))
        assertEquals("Your cause list is ready by 7 AM. Each case checked costs credits.", stepThreeText(0))
        assertEquals("Your cause list is ready by 7 AM. Each case checked costs 2 credits.", stepThreeText(2))
    }

    @Test fun pausedScreenUsesTheServerMessage() {
        val said = "Your Counsel workspace is frozen while you hold a staff account or engagement with the firm."
        assertEquals(said, pausedText(said))
        assertEquals(PAUSED_TEXT, pausedText(null)); assertEquals(PAUSED_TEXT, pausedText(" "))
    }

    @Test fun countsAreGroupedIndianStyle() {
        assertEquals("1,234 cases listed", listedText(1234))
        assertEquals("12,34,567 dates need your eye", eyeText(1234567))
        assertEquals("1 date needs your eye", eyeText(1))
        val d = ImportDone(1200, 2, 100000)
        assertEquals("1,200 added, 2 updated. You now have 1,00,000 cases in your diary.", importDoneLine(d))
    }
}
