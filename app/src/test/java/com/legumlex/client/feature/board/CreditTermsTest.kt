package com.legumlex.client.feature.board

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** REQ-052: How Credits Work comes from the server (credits.how_credits_work); the app adds only its icons, and hides the section without it. */
class CreditTermsTest {
    private val terms = listOf(
        mapOf("key" to "result", "icon" to "ki-handcart", "title" to "You Pay For A Result", "text" to "Credits pay for a result."),
        mapOf("key" to "record", "icon" to "ki-book-open", "title" to "The Court's Own Record", "text" to "The result is the court's own record."),
        mapOf("key" to "success", "icon" to "ki-check-circle", "title" to "Charged Only When It Arrives", "text" to "A charge is taken only when the result arrives."),
        mapOf("key" to "worth", "icon" to "ki-bill", "title" to "What Credits Are Worth", "text" to "Paid credits never expire."),
    )

    @Test fun theServersPointsInOrder() {
        val c = parseCredits(mapOf("balance" to 10.0, "how_credits_work" to terms))
        assertEquals(listOf("result", "record", "success", "worth"), c.howCreditsWork.map { it.key })
        assertEquals("The Court's Own Record", c.howCreditsWork[1].title)
        assertEquals("Paid credits never expire.", c.howCreditsWork[3].text)
    }

    @Test fun anOlderServerHidesTheSection() {
        assertTrue(parseCredits(mapOf("balance" to 10.0)).howCreditsWork.isEmpty())
        // A point with no words is not shown.
        assertEquals(1, parseCredits(mapOf("balance" to 10.0, "how_credits_work" to listOf(terms[0], mapOf("key" to "x", "title" to "", "text" to "")))).howCreditsWork.size)
    }

    @Test fun eachPointHasItsOwnIcon() {
        val icons = listOf("result", "record", "success", "worth").map(::creditTermIcon)
        assertEquals(4, icons.toSet().size)
        assertFalse(creditTermIcon("other") in icons)
    }

    @Test fun theWordsAreNotCopiedIntoTheApp() {
        val f = java.io.File("src/main/java/com/legumlex/client/feature/board/BoardCreditsScreen.kt").takeIf { it.isFile } ?: return
        val src = f.readText()
        terms.forEach { assertFalse(it["title"]!!, src.contains(it["title"]!!)) }
    }
}
