package com.legumlex.client.feature.desk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** REQ-013 013.16: the guest desk never invents an answer when it cannot be reached. */
class GuestDeskOfflineTest {
    @Test fun theUnreachableLineIsTheDesksErrorAndOffersTryAgainAndGoogle() {
        assertEquals("Lex Intelligence could not be reached. Please try again.", GUEST_UNREACHABLE)
        assertEquals(listOf("guest_retry", "guest_signin"), GUEST_RETRY_ACTIONS.map { it.id })
        assertEquals(listOf("Try Again", "Sign In With Google"), GUEST_RETRY_ACTIONS.map { it.label })
        assertTrue(GUEST_RETRY_ACTIONS[0].main)
    }

    @Test fun noInventedReplyIsLeftAndAFailedTryIsNotCounted() {
        val src = java.io.File("src/main/java/com/legumlex/client/feature/desk/DeskViewModel.kt").takeIf { it.isFile }?.readText() ?: return
        assertFalse(src.contains("The advocates can help with that"))
        val send = src.substringAfter("private suspend fun sendGuest").substringBefore("val reply = r.reply")
        assertTrue(send.contains("if (r == null)"))
        // The failed question leaves the two-message count (the turn is dropped, an answer's free pass is given back) and can be re-sent.
        assertTrue(send.contains("guestTurns.removeAt(guestTurns.lastIndex)"))
        assertTrue(send.contains("if (answering) { guestFree--; guestAnswering = true }"))
        assertTrue(src.contains("\"guest_retry\" -> go"))
    }

    @Test fun aReplyWithNoTextIsNoReply() {
        assertEquals(null, parseGuestReply(mapOf("reply" to "")))
        assertEquals("Hello", parseGuestReply(mapOf("reply" to "Hello"))?.reply)
    }

    @Test fun theSignInLineSaysMatterWhenNothingWasAskedAndQuestionWhenSomethingWas() {
        assertEquals("To go on, please sign in with Google. Your matter then goes to the advocates, and they write back to you.", signInLine(asked = false))
        assertEquals("To go on, please sign in with Google. Your question then goes to the advocates, and they write back to you.", signInLine(asked = true))
        val src = java.io.File("src/main/java/com/legumlex/client/feature/desk/DeskViewModel.kt").takeIf { it.isFile }?.readText() ?: return
        assertTrue(src.contains("signInLine(asked = guestTurns.any { it.first == \"guest\" })"))
    }
}
