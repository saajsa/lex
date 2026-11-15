package com.legumlex.client.feature.desk

import com.legumlex.client.feature.consultations.CONSULTATION_OFFER_LINE
import com.legumlex.client.feature.consultations.consultationOfferLabel
import com.legumlex.client.feature.consultations.parseConsultationOffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** REQ-023: Lex's closing turn after gathering, the consultation Lex's counsel recommends, and a direct offer that may be null. */
class DeskCloseTest {
    private fun src(path: String) = java.io.File("src/main/java/com/legumlex/client/$path").takeIf { it.isFile }?.readText()

    @Test fun theClosingTurnParsesAndAnErrorSkipsIt() {
        val t = parseCloseTurn(mapOf("success" to true, "say" to "For <b>₹99</b>, your matter goes straight to our counsel. Shall I send it?", "source" to "fixed", "fee_display" to "₹99", "fee_paise" to 9900.0))!!
        assertEquals("For **₹99**, your matter goes straight to our counsel. Shall I send it?", t.say)       // <b> becomes the app's bold
        assertEquals("fixed", t.source); assertEquals("₹99", t.feeDisplay); assertEquals(9900, t.feePaise)
        assertEquals("ai", parseCloseTurn(mapOf("success" to true, "say" to "Hello.", "source" to "ai"))!!.source)
        assertNull(parseCloseTurn(null)); assertNull(parseCloseTurn(mapOf("success" to false, "say" to "x"))); assertNull(parseCloseTurn(mapOf("success" to true, "say" to "  ")))
    }

    @Test fun theTurnSitsAfterGatheringAndBeforeTheDetailsAndIsSkippedOnAnError() {
        val vm = src("feature/desk/DeskViewModel.kt") ?: return
        val order = vm.substringAfter("private suspend fun next()").substringBefore("private fun hour()")
        assertTrue(order.indexOf("!gatherDone -> gather()") < order.indexOf("!closeDone -> askClose()") && order.indexOf("!closeDone -> askClose()") < order.indexOf("docs == null -> askDocs()"))
        val close = vm.substringAfter("private suspend fun askClose()").substringBefore("fun noPhone()")
        assertTrue(close.contains("closeDone = true"))                                        // once for this enquiry
        assertTrue(close.contains("repo.close(fullText(), langField(convLang)) } ?: return next()"))   // an error, a 429 or no answer: carry on as today
        assertTrue(close.contains("say(Para.Text(r.say))"))
        assertTrue(close.contains("Action(\"close_go\", lbl(\"ui_go_ahead\", CLOSE_GO_AHEAD), main = true)") && close.contains("Action(\"close_no\", lbl(\"ui_not_now\", CLOSE_NOT_NOW))"))
        assertEquals("Yes, Go Ahead", CLOSE_GO_AHEAD); assertEquals("Not Now", CLOSE_NOT_NOW)
        val data = src("feature/desk/DeskData.kt")!!
        assertTrue(data.contains("api.leadDeskClose(text, lang) else api.deskClose(text, lang)"))
        val api = src("data/api/ApiService.kt")!!
        assertTrue(api.contains("@POST(\"desk/close\")") && api.contains("@POST(\"lead/desk/close\")"))
    }

    @Test fun yesGoesOnAndNotNowEndsKindlyWithNothingSent() {
        val vm = src("feature/desk/DeskViewModel.kt") ?: return
        val go = vm.substringAfter("\"close_go\" -> go {").substringBefore("\n")
        assertTrue(go.contains("dock(Dock.Waiting(\"…\")); next()"))
        val no = vm.substringAfter("\"close_no\" -> go {").substringBefore("\"phone_retry\"")
        assertTrue(no.contains("sayKey(\"close_stop\", CLOSE_STOP_EN)") && no.contains("Action(\"again\"") && !no.contains("send()") && !no.contains("next()"))
        assertEquals("No problem. Nothing has been sent or charged, and you can come back any time.", CLOSE_STOP_EN)
        // Editing an earlier answer asks again; a restart too.
        assertTrue(vm.contains("gatherRan = false; closeDone = false }") && vm.contains("gatherRan = false; closeDone = false; sessionHash = null"))
    }

    @Test fun theRecommendedConsultationIsBookableOnlyWhenTheServerSaysSo() {
        val o = parseConsultationOffer(mapOf("fee_paise" to 49900.0, "fee_display" to "₹499", "reply_id" to 31, "can_book" to true))!!
        assertTrue(o.canBook); assertEquals("₹499", o.feeDisplay); assertEquals("31", o.replyId); assertEquals(49900, o.feePaise)
        assertEquals("Book A Consultation (₹499)", consultationOfferLabel(o))
        assertEquals("Book A Consultation", consultationOfferLabel(o.copy(feeDisplay = "")))
        assertFalse(parseConsultationOffer(mapOf("fee_paise" to 49900, "can_book" to false))!!.canBook)
        assertNull(parseConsultationOffer(null))                                           // null: no recommendation (or an older server)
        assertEquals("₹499", parseConsultationOffer(mapOf("fee_paise" to 49900, "can_book" to true))!!.feeDisplay)
        assertFalse(CONSULTATION_OFFER_LINE.contains("Nyay", true) || CONSULTATION_OFFER_LINE.contains("advocate", true))    // "our counsel", no names
        val g = src("feature/consultations/detail/DetailGeneral.kt") ?: return
        assertTrue(g.contains("consultation.consultationOffer?.takeIf { it.canBook }?.let { offer ->"))
        val vm = src("feature/consultations/ConsultationDetailViewModel.kt")!!
        assertTrue(vm.contains("repo.bookConsultation(consultationId)") && vm.contains("b.payUrl != null -> _payUrls.tryEmit(b.payUrl)"))
        assertTrue(src("feature/consultations/ConsultationDetailScreen.kt")!!.contains("vm.payUrls.collect { url -> com.legumlex.client.feature.finance.PayActivity.open(context, url) }"))
        val repo = src("feature/consultations/ConsultationsRepository.kt")!!
        assertTrue(repo.contains("api.leadDeskDirect(n) else api.deskDirect(n)") && repo.contains("consultationOffer    = parseConsultationOffer(m[\"consultation_offer\"])"))
    }

    @Test fun aNullDirectOfferShowsNothing() {
        assertNull(parseDirectOffer(null)); assertNull(parseDirectOffer(mapOf("available" to false))); assertNull(parseDirectOffer(mapOf("fee_paise" to 49900)))
        assertNull(parseGather(mapOf("success" to true, "question" to "", "done" to true, "direct_offer" to null))!!.directOffer)
        val r = DeskReading("clear", "", "", "none", "", "unknown", "new_matter", "", emptyList(), "")
        assertNull(r.directOffer)
        val vm = src("feature/desk/DeskViewModel.kt") ?: return
        assertTrue(vm.contains("if (reading?.directOffer != null && !directAsked) pendingOffer = reading.directOffer"))      // only a present offer is asked
    }

    @Test fun theOfferIsShownInTheConsultationsLanguageWithEnglishAsFallback() {
        val book = com.legumlex.client.feature.desk.LineBook("hi", mapOf("consult_offer_line" to "हमारे वकील इस मामले के लिए पूरी कंसल्टेशन की सलाह देते हैं।", "ui_book_consultation" to "कंसल्टेशन बुक करें"), null)
        val o = parseConsultationOffer(mapOf("fee_paise" to 49900, "fee_display" to "₹499", "can_book" to true))!!
        assertEquals("कंसल्टेशन बुक करें (₹499)", consultationOfferLabel(o, book))
        assertEquals("हमारे वकील इस मामले के लिए पूरी कंसल्टेशन की सलाह देते हैं।", com.legumlex.client.feature.consultations.consultationOfferLine(book))
        // No book (English, an older server, or the lines could not be had): the English as before.
        assertEquals("Book A Consultation (₹499)", consultationOfferLabel(o)); assertEquals(CONSULTATION_OFFER_LINE, com.legumlex.client.feature.consultations.consultationOfferLine())
        assertEquals("hi", com.legumlex.client.feature.consultations.consultationLang("hi")); assertNull(com.legumlex.client.feature.consultations.consultationLang("en")); assertNull(com.legumlex.client.feature.consultations.consultationLang(null))
        val vm = src("feature/consultations/ConsultationDetailViewModel.kt") ?: return
        assertTrue(vm.contains("fun offerLanguage(lang: String?)") && vm.contains("repo.lines(l)?.let { _offerBook.value = it }"))
        assertTrue(src("feature/consultations/ConsultationsRepository.kt")!!.contains("lang                 = (m[\"lang\"] as? String)"))
        assertTrue(src("feature/consultations/detail/DetailGeneral.kt")!!.contains("vm.offerLanguage(consultation.lang)"))
    }
}

class DeskCloseDeclinedTest {
    @org.junit.Test fun aDeclinedTurnIsReadAndOffersOnlyCarryOnAndANewEnquiry() {
        val t = parseCloseTurn(mapOf("success" to true, "say" to "Understood.", "declined" to true))!!
        org.junit.Assert.assertTrue(t.declined)
        org.junit.Assert.assertFalse(parseCloseTurn(mapOf("success" to true, "say" to "Hi"))!!.declined)
        val vm = java.io.File("src/main/java/com/legumlex/client/feature/desk/DeskViewModel.kt").readText()
        val block = vm.substringAfter("if (r.declined) {").substringBefore("return\n        }")
        org.junit.Assert.assertTrue(block.contains("close_carry") && block.contains("\"again\""))
        org.junit.Assert.assertFalse(block.contains("close_no"))
    }
}

class DeskGuestStoryTest {
    @org.junit.Test fun theGuestsWordsBecomeTheEnquiryOnlyWhenLongEnough() {
        val t = listOf("guest" to "My landlord kept my deposit.", "desk" to "Sorry to hear.", "guest" to "He refuses to return it.")
        org.junit.Assert.assertEquals("My landlord kept my deposit. He refuses to return it.", guestStory(t))
        org.junit.Assert.assertNull(guestStory(listOf("guest" to "Help me", "desk" to "A long reply from the desk that must not count")))
        org.junit.Assert.assertNull(guestStory(emptyList()))
        org.junit.Assert.assertNull(guestStory(listOf("guest" to "asdfghjklqwertyuiopzxcvbnm asdfghjkl")))
        val vm = java.io.File("src/main/java/com/legumlex/client/feature/desk/DeskViewModel.kt").readText()
        org.junit.Assert.assertTrue(vm.contains("sayKey(\"q_from_guest\", Q_FROM_GUEST"))
    }
}
