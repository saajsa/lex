package com.legumlex.client.feature.desk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** REQ-029 slice 4. Shapes are cases_desk_resume's own (cases_desk_helper.php ~3370-3440) as customers_api Desk.php resume_post answers them in `data`. */
class DeskResumeTest {
    private fun src(name: String) = java.io.File("src/main/java/com/legumlex/client/feature/desk/$name").readText()

    @Test fun aStoryGoesStraightOn() {
        val r = parseResume(mapOf("success" to true, "kind" to "story", "reply" to "", "lang" to "en"))!!
        assertEquals("story", r.kind); assertEquals("", r.reply); assertNull(r.insist); assertNull(r.route)
    }

    @Test fun aQuestionIsAnsweredAndTheMatterAskedFor() {
        val r = parseResume(mapOf("success" to true, "kind" to "question", "reply" to "To reach an advocate, tell me briefly what you need them for.", "lang" to "en"))!!
        assertEquals("question", r.kind); assertEquals("To reach an advocate, tell me briefly what you need them for.", r.reply); assertFalse(r.nonmatter)
        val bare = parseResume(mapOf("success" to true, "kind" to "question", "nonmatter" to true, "lang" to "en", "reply" to "Please tell me what happened, in your own words."))!!
        assertTrue(bare.nonmatter)
    }

    @Test fun anInsistedAdvocateCarriesTheConsultationFirstCards() {
        val r = parseResume(mapOf("success" to true, "kind" to "question", "reply" to "", "nonmatter" to false, "lang" to "en", "insist" to mapOf("lead" to "A full consultation with the advocates is ₹499. Shall I book it?", "insist" to true, "cards" to listOf(
            mapOf("key" to "consultation", "icon" to "consult", "title" to "Full Consultation", "price" to "₹499", "includes" to "x", "timing" to "y", "button" to "Yes, Book It"),
            mapOf("key" to "written", "icon" to "written", "title" to "Written Enquiry", "price" to "₹99 in all", "includes" to "x", "timing" to "y", "button" to "Send The Written Enquiry", "intro" to "If you would like to send just an enquiry, it is only ₹99 in all.", "secondary" to true)))))!!
        assertEquals(listOf("consultation", "written"), r.insist!!.cards.map { it.key })
        assertEquals("Yes, Book It", r.insist!!.cards[0].button); assertTrue(r.insist!!.cards[1].secondary)
    }

    @Test fun aServiceRouteCarriesItsFields() {
        val r = parseResume(mapOf("success" to true, "kind" to "route", "reply" to "Yes. Our Agreement Health Check is the service for reviewing an agreement before you sign.", "lang" to "en",
            "route" to "ahc", "route_line" to "Yes. Our Agreement Health Check is the service for reviewing an agreement before you sign.", "route_when" to "tomorrow", "route_button" to "Open Agreement Health Check"))!!
        assertEquals("route", r.kind); assertEquals("ahc", r.route!!.route); assertEquals("tomorrow", r.route!!.whenText); assertEquals("Open Agreement Health Check", r.route!!.button)
        val docs = parseRoute(mapOf("route" to "portal_docs", "route_line" to "Sign in.", "route_button" to "Sign In To The Portal", "route_line_in" to "Open it.", "route_button_in" to "Open My Agreements", "route_href_in" to "/mylex/agreements"))!!
        assertEquals("Open My Agreements", docs.buttonIn); assertEquals("/mylex/agreements", docs.hrefIn)
        assertNull(parseRoute(mapOf("route" to "", "route_line" to "")))
    }

    @Test fun aFailedAnswerFallsBackToThePlainRule() {
        assertNull(parseResume(mapOf("success" to false, "code" to "rate_limited"))); assertNull(parseResume(null))
        assertNull(parseResume(mapOf("success" to true, "kind" to "other")))
        val vm = src("DeskViewModel.kt")
        assertTrue(vm.contains("repo.resume(guestTurns, langField(convLang)) } ?: return false"))   // null: the story rule below decides
        assertTrue(vm.contains("if (resumeFromGuest()) return") && vm.contains("if (!resumed && guestTurns.any { it.first == \"guest\" && it.second.isNotBlank() }) {"))
        assertTrue(src("DeskData.kt").contains("api.leadDeskResume(json, lang, conversation) else api.deskResume(json, lang, conversation)"))
    }

    @Test fun theGuestConversationIsSentAsTheServerReadsIt() {
        assertEquals("[{\"role\":\"guest\",\"text\":\"My landlord kept my \\\"deposit\\\".\"},{\"role\":\"desk\",\"text\":\"Sorry.\\nTell me more.\"}]",
            turnsJson(listOf("guest" to "My landlord kept my \"deposit\".", "desk" to "Sorry.\nTell me more.")))
        assertEquals("[]", turnsJson(emptyList()))
    }
}
