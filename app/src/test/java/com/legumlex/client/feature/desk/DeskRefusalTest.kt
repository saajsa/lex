package com.legumlex.client.feature.desk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** REQ-017: a refused enquiry says the server's line alone, and Try Again is offered only where a retry can help. */
class DeskRefusalTest {
    private fun src(path: String) = java.io.File("src/main/java/com/legumlex/client/$path").takeIf { it.isFile }?.readText()

    @Test fun theRefusalFieldsParse() {
        val footer = mapOf("lang" to "hi", "en" to "Translated.", "local" to "अनुवादित। x")
        val f = parseRefusal(mapOf("message" to " Ek ghante mein teen baar. ", "reason" to "rate_limited", "retry_after_minutes" to 42.0, "lang" to "hi", "translated" to true, "footer" to footer))
        assertEquals("Ek ghante mein teen baar.", f.message); assertEquals("rate_limited", f.reason); assertEquals(42, f.retryAfterMinutes)
        assertEquals("hi", f.tongue.lang); assertEquals("Translated.", f.tongue.lineFooter?.en)
        // Translated false: the line is the server's English, with no footer.
        assertNull(parseRefusal(mapOf("message" to "m", "reason" to "check_details", "lang" to "hi", "translated" to false, "footer" to footer)).tongue.lineFooter)
    }

    @Test fun anOldServerSendsNoReason() {
        val f = parseRefusal(mapOf("message" to "Please try again."))
        assertNull(f.reason); assertEquals(0, f.retryAfterMinutes); assertEquals("en", f.tongue.lang)
        assertNull(parseRefusal(null).reason); assertEquals("", parseRefusal(null).message)
        assertNull(parseRefusal(mapOf("reason" to "  ")).reason)
    }

    @Test fun tryAgainIsOfferedPerReason() {
        for (r in listOf("rate_limited", "already_sent", "awaiting_payment")) assertFalse(r, retryHelps(r))
        for (r in listOf("session_not_found", "not_accepted", "sign_in_again", "check_details", "check_files", "something_new", null)) assertTrue(r.toString(), retryHelps(r))
    }

    @Test fun theLanguageIsPostedAndTheServersLineIsSaidAlone() {
        val fields = enquiryFields("t", "individual", "9876543210", null, "", "", null, "k", "", "", "hx").toMap()
        assertEquals("hx", fields["lang"])
        assertFalse(enquiryFields("t", "individual", "9876543210", null, "", "", null, "k", "", "").any { it.first == "lang" })   // English: not sent
        val vm = src("feature/desk/DeskViewModel.kt") ?: return
        val refused = vm.substringAfter("private suspend fun refused(").substringBefore("// ---- The fee")
        // The server's line, with its footer when translated; no app prefix in front of it.
        assertTrue(refused.contains("say(Para.Text(msg), note = true, warn = true, footer = r.tongue.lineFooter)"))
        assertFalse(refused.contains("That did not go through. \""))
        assertTrue(refused.contains("retryHelps(r.reason) -> dock(Dock.Actions(listOf(Action(\"try\", \"Try Again\"") && refused.contains("else -> dock(Dock.Actions(listOf(Action(\"matters\""))
        // An older server (no reason) keeps today's path.
        assertTrue(vm.contains("if (r.reason != null && !(r.errors.isNotEmpty() && r.reason in setOf(\"check_details\", \"check_files\"))) refused(r)"))
        assertTrue(vm.contains("lang = book.lang.takeIf { it != \"en\" }.orEmpty()"))
        val data = src("feature/desk/DeskData.kt")!!
        assertTrue(data.contains("Sent.Refused(f.message, errors,") && data.contains("deadlineDate, lang, direct, conversation)"))
    }

    @Test fun aTranslatedRefusalCardCarriesTheFooterOrTag() {
        val screen = src("feature/desk/DeskScreen.kt") ?: return
        val note = screen.substringAfter("private fun NoteLine(").substringBefore("\n}\n")
        assertTrue(note.contains("line.footer?.let { DeskFooterText(it, line.footerTag,"))
        // The refusal says its line with the footer rule of Lex's lines (full once, then the tag).
        val vm = src("feature/desk/DeskViewModel.kt")!!
        assertTrue(vm.substringAfter("private suspend fun refused(").contains("footer = r.tongue.lineFooter") && vm.contains("footerOnce.next(footer)"))
    }
}
