package com.legumlex.client.feature.desk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** REQ-017 017.21: the signed-in desk's opening, as the website's. */
class DeskOpenerTest {
    private val vm: String? = java.io.File("src/main/java/com/legumlex/client/feature/desk/DeskViewModel.kt").takeIf { it.isFile }?.readText()
    private val screen: String? = java.io.File("src/main/java/com/legumlex/client/feature/desk/DeskScreen.kt").takeIf { it.isFile }?.readText()

    @Test fun theOpenerIsOneWelcomeThenWhatWouldYouLikeHelpWith() {
        assertEquals("What would you like help with?", ASK_HELP)
        val s = vm ?: return
        val ask = s.substringAfter("private suspend fun askQuestion()").substringBefore("dock(Dock.Question)")
        assertTrue(ask.contains("say(Para.Text(ASK_HELP), prompt = Step.QUESTION)"))
        // The old second line is gone.
        assertFalse(s.contains("What would you like to ask? In your own words"))
        // One welcome only: Welcome back with what is on file when the firm knows them, else the time of day's.
        assertEquals(1, Regex("greetBack\\(withNotes = false\\)").findAll(ask).count())
        assertEquals(1, Regex("and welcome to Legum Lex").findAll(ask).count())
    }

    @Test fun theWelcomeBackCardComesBeforeTheFirstQuestion() {
        val s = vm ?: return
        val ask = s.substringAfter("private suspend fun askQuestion()").substringBefore("dock(Dock.Question)")
        assertTrue(ask.indexOf("greetBack(withNotes = false)") < ask.indexOf("say(Para.Text(ASK_HELP)"))
        // Later (after the papers) only what the reading found is said, once; the card is not said again.
        val later = s.substringAfter("private suspend fun welcome()").substringBefore("private suspend fun greetBack")
        assertFalse(later.contains("greetBack("))
        assertTrue(later.contains("readingNotes()") && later.contains("if (notes.isNotEmpty())"))
    }

    @Test fun noAnswerBubbleWhenLexDidNotAsk() {
        val s = vm ?: return
        val entity = s.substringAfter("private suspend fun askEntity()").substringBefore("if (!asked(Step.ENTITY))")
        assertTrue(entity.contains("so I will not ask you that again."))
        assertFalse(entity.contains("answer(Step.ENTITY"))
        // The same for a phone number on file? No: that one is asked ("Shall the advocates call you on...?") and its answer is shown.
        assertTrue(s.contains("answer(Step.PHONE, shown(c.phone))"))
    }

    @Test fun changeIsInTheSecondaryInkOnThePage() {
        val sc = screen ?: return
        val me = sc.substringAfter("private fun MeLine(").substringBefore("private fun NoteLine(")
        assertTrue(me.contains("D.ink(DeskSurface.Page, DeskRole.Secondary)"))
        assertFalse(me.contains("alpha = 0.58f"))
        // Secondary on the page passes 4.5:1 in both themes.
        for (c in listOf(com.legumlex.client.ui.theme.LightLexColors, com.legumlex.client.ui.theme.DarkLexColors)) {
            assertTrue(contrastRatio(deskInk(c, DeskSurface.Page, DeskRole.Secondary), deskFill(c, DeskSurface.Page)) >= 4.5)
        }
    }
}
