package com.legumlex.client.feature.desk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** REQ-017 017.C: Lex's introduction, the owner's words, once. */
class LexIntroTest {
    private fun main(name: String) = java.io.File("src/main/java/com/legumlex/client/feature/desk/$name").takeIf { it.isFile }?.readText()

    @Test fun theIntroductionIsTheOwnersLine() {
        assertEquals("Hi, I am Lex, your AI assistant. I will listen, note the details, and pass your enquiry to an advocate. What would you like help with?", LEX_INTRO)
        // The card says the same in two parts: a heading, then the rest.
        assertEquals("Hi, I Am Lex", LEX_INTRO_HEADING)
        assertEquals("Your AI assistant. I will listen, note the details, and pass your enquiry to an advocate. What would you like help with?", LEX_INTRO_BODY)
        assertTrue(LEX_INTRO.endsWith(LEX_INTRO_BODY.removePrefix("Your AI assistant. ")))
        assertFalse(LEX_INTRO.contains("—") || LEX_INTRO_BODY.contains("—"))
    }

    @Test fun theOldGuestWordsAreGoneAndTheIntroductionIsNotShownTwice() {
        val vm = main("DeskViewModel.kt") ?: return
        val screen = main("DeskScreen.kt")!!
        for (old in listOf("Ask Lex Intelligence\"", "A short question about the advocates", "Ask me about the advocates, or how to reach them", "you sign in with Google.\"")) {
            assertFalse(old, vm.contains(old) || screen.contains(old))
        }
        // The first line of the conversation is the introduction, said once (when the guest's desk first speaks).
        assertEquals(1, Regex("say\\(Para.Text\\(LEX_INTRO\\)\\)").findAll(vm).count())
        assertTrue(vm.substringAfter("private suspend fun askGuest()").substringBefore("if (guestLeft").contains("if (!greeted)"))
        // The card is shown only before the conversation has begun (!ui.opened); after, the line is what is there. The intro text is never typed out in the card itself.
        assertTrue(screen.contains("if (!ui.opened) {"))
        assertEquals(0, Regex("Hi, I am Lex").findAll(screen).count())
        assertEquals(1, Regex("Hi, I am Lex").findAll(main("DeskIntake.kt")!!).count())
        // The sample screen shows the introduction as its first line, once.
        assertTrue(screen.contains("line(Who.DESK, Para.Text(LEX_INTRO))"))
        assertEquals(1, Regex("Para.Text\\(LEX_INTRO\\)").findAll(screen).count())
    }

    @Test fun theSignedInDeskKeepsItsOwnOpening() {
        val vm = main("DeskViewModel.kt") ?: return
        assertTrue(vm.contains("\", and welcome to Legum Lex.\")"))
        assertTrue(main("DeskScreen.kt")!!.contains("\"What has happened?\""))
    }
}
