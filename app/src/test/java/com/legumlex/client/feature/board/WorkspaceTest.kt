package com.legumlex.client.feature.board

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceTest {
    @Test fun `a remembered counsel gets the counsel chrome while the server has not answered`() {
        assertTrue(counselChrome(BoardAccess.Unknown, true))
        assertFalse(counselChrome(BoardAccess.Unknown, false))
        assertFalse(counselChrome(BoardAccess.No, true))
    }

    @Test fun `first launch is neutral until the server answers`() {
        assertTrue(workspaceUnknown(BoardAccess.Unknown, false))
        assertFalse(workspaceUnknown(BoardAccess.Unknown, true))
        assertFalse(workspaceUnknown(BoardAccess.No, false))
    }

    @Test fun `remembered counsel plus one refusal stays counsel until the retry answers`() {
        assertTrue(needsRetry(true, Answer.Refused))
        assertEquals(Verdict.Keep, decideAccess(true, listOf(Answer.Refused)))
        assertEquals(Verdict.Yes, decideAccess(true, listOf(Answer.Refused, Answer.Ok)))
        assertEquals(Verdict.Keep, decideAccess(true, listOf(Answer.Refused, Answer.Failed)))
    }

    @Test fun `two refusals send a remembered counsel to the client workspace`() {
        // two 404s, or a 403 confirmed by a retry 403: both are two refusals
        assertEquals(Verdict.No, decideAccess(true, listOf(Answer.Refused, Answer.Refused)))
    }

    @Test fun `an account never known as counsel is refused on the first refusal`() {
        assertFalse(needsRetry(false, Answer.Refused))
        assertEquals(Verdict.No, decideAccess(false, listOf(Answer.Refused)))
        assertEquals(Verdict.Yes, decideAccess(false, listOf(Answer.Ok)))
    }

    @Test fun `a failure to reach the server decides nothing`() {
        assertEquals(Verdict.Keep, decideAccess(false, listOf(Answer.Failed)))
        assertEquals(Verdict.Keep, decideAccess(true, listOf(Answer.Failed)))
        assertEquals(Verdict.Keep, decideAccess(false, emptyList()))
    }

    @Test fun `the chambers request is trimmed and cut to the server limits`() {
        val f = chambersFields("  214, District Courts ", " Ludhiana", "141001 ")
        assertEquals(mapOf("address" to "214, District Courts", "city" to "Ludhiana", "zip" to "141001"), f)
        val long = chambersFields("a".repeat(300), "c".repeat(200), "9".repeat(40))
        assertEquals(191, long["address"]!!.length)
        assertEquals(100, long["city"]!!.length)
        assertEquals(15, long["zip"]!!.length)
        assertEquals(setOf("address", "city", "zip"), f.keys)
    }
}
