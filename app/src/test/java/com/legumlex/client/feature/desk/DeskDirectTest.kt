package com.legumlex.client.feature.desk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** REQ-017 017.30: known details skip steps, an informed advocate gets the sent animation, and a direct-talk enquiry ends in a waiting card that hands over. */
class DeskDirectTest {
    private fun src(path: String) = java.io.File("src/main/java/com/legumlex/client/$path").takeIf { it.isFile }?.readText()

    // ---- A. known details ----

    @Test fun knownDetailsParseAndAnOlderServerKnowsNothing() {
        val k = parseKnownDetails(mapOf("known_details" to mapOf("name" to true, "email" to true, "phone" to false, "entity" to true)))
        assertEquals(KnownDetails(name = true, email = true, phone = false, entity = true), k)
        assertEquals(KnownDetails(), parseKnownDetails(mapOf("known" to true)))      // no object: today's behaviour
        assertEquals(KnownDetails(), parseKnownDetails(null))
        assertEquals(KnownDetails(), parseKnownDetails(mapOf("known_details" to mapOf("phone" to "yes"))))   // only a real true counts
    }

    @Test fun aStepIsSkippedOnTheServersFlagAndNeverOnBeingSignedIn() {
        val vm = src("feature/desk/DeskViewModel.kt") ?: return
        val phone = vm.substringAfter("private suspend fun askPhone()").substringBefore("val c = ctx")
        assertTrue(phone.contains("if (t.known.phone) { phone = \"\"; return next() }"))
        val entity = vm.substringAfter("private suspend fun askEntity()").substringBefore("val abroad")
        assertTrue(entity.contains("if (t.known.entity)"))
        // Nothing in the skip looks at who is signed in.
        assertFalse(phone.contains("guest") || phone.contains("isLead") || phone.contains("SessionRole"))
        // Without the flag the old on-file confirm and the question remain.
        assertTrue(vm.contains("c.phone.length == 10 && entity != \"nri\" && \"phone\" !in offered"))
        assertTrue(src("feature/desk/DeskData.kt")!!.contains("known         = parseKnownDetails(c)"))
    }

    // ---- B. advocate informed ----

    @Test fun theCheckCarriesAStableConversationIdAndTheInformedFlag() {
        val data = src("feature/desk/DeskData.kt") ?: return
        assertTrue(data.contains("val conversation: String = java.util.UUID.randomUUID()"))           // once per desk session
        assertTrue(data.contains("api.leadDeskCheck(text, conversation, lang)") && data.contains("api.deskCheck(text, conversation = conversation, lang = lang)"))
        assertTrue(data.contains("advocateInformed = d[\"advocate_informed\"] == true"))
        val api = src("data/api/ApiService.kt")!!
        assertTrue(api.contains("@Field(\"conversation\") conversation: String? = null"))
        assertFalse(DeskReading("clear", "", "", "none", "", "unknown", "new_matter", "", emptyList(), "").advocateInformed)   // absent = false
    }

    @Test fun informedGetsTheAnimationAndOtherwiseThePersonSignedLine() {
        val vm = src("feature/desk/DeskViewModel.kt") ?: return
        val v = vm.substringAfter("if (reading?.wantsPerson == true && \"person\" !in said) {").substringBefore("adopt(").ifBlank { vm.substringAfter("if (reading?.wantsPerson == true").take(900) }
        val block = vm.substringAfter("if (reading?.wantsPerson == true && \"person\" !in said) {").take(900)
        assertTrue(block.contains("if (reading.advocateInformed)") && block.contains("tl(\"person_informed\")") && block.contains("PERSON_INFORMED") && block.contains("Para.Sent(tl(\"person_sent_caption\") ?: PERSON_SENT_CAPTION, "))
        assertTrue(block.contains("} else sayKey(\"person_signed\", PERSON_SIGNED)"))             // false or absent: the existing line, no animation
        assertEquals("I have messaged one of the advocates. As soon as they are free, I will hand over. Meanwhile, a few details will save time and let them come prepared.", PERSON_INFORMED)
    }

    // ---- C. the end of a direct-talk enquiry ----

    @Test fun theStatusRowParsesAndHandsOverOnlyWhenAcknowledgedAndNamed() {
        val s = parseDirectStatus(mapOf("intake_state" to "acknowledged", "assigned_to" to "Asha Rao", "acknowledged_at" to "2026-11-12 19:50:00", "direct_talk" to true))!!
        assertTrue(s.acknowledged && s.directTalk == true); assertTrue(handedOver(s))
        assertFalse(handedOver(parseDirectStatus(mapOf("intake_state" to "assigned", "assigned_to" to "Asha Rao"))))     // assigned is not yet taken
        assertFalse(handedOver(parseDirectStatus(mapOf("intake_state" to "acknowledged", "assigned_to" to ""))))          // no name to say
        assertFalse(handedOver(parseDirectStatus(mapOf("intake_state" to "received"))))
        assertFalse(handedOver(null)); assertNull(parseDirectStatus(null))
        // An older server: no direct_talk, no acknowledged_at.
        assertNull(parseDirectStatus(mapOf("intake_state" to "received"))!!.directTalk)
        assertTrue(parseDirectStatus(mapOf("acknowledged_at" to "2026-11-12 19:50:00"))!!.acknowledged)
    }

    @Test fun onlyADirectTalkConversationEndsInTheWaitingCard() {
        assertTrue(isDirectTalk(true, null)); assertTrue(isDirectTalk(false, "I want an advocate")); assertFalse(isDirectTalk(false, null)); assertFalse(isDirectTalk(false, "  "))
        val vm = src("feature/desk/DeskViewModel.kt") ?: return
        val delivered = vm.substringAfter("private suspend fun delivered(").substringBefore("private suspend fun deliveredDirect")
        assertTrue(delivered.contains("if (isDirectTalk(informed, directTalk)) { deliveredDirect(enquiryNumber); return }"))
        val direct = vm.substringAfter("private suspend fun deliveredDirect(").substringBefore("private suspend fun restart")
        assertTrue(direct.contains("tl(\"person_final\")") && direct.contains("PERSON_FINAL"))                  // in place of the usual receipt
        assertTrue(direct.contains("Dock.Advocate(AdvocateWait.Waiting(\"\", ref))"))                              // the reference stays, small
        assertTrue(direct.contains("repo.directStatus(id)") && direct.contains("tl(\"person_handover\") ?: PERSON_HANDOVER"))
        assertTrue(direct.contains("AdvocateWait.HandedOver(handover, ref, OPEN_CONSULTATION)"))
        assertTrue(vm.contains("\"advocate_open\" -> _events.tryEmit(consultationId?.let { DeskEvent.OpenConsultation(it) }"))
        assertEquals("Your enquiry has been sent to the advocate.", PERSON_HANDOVER)         // no advocate name in the card line
        assertEquals("The final report has gone to the advocate. Waiting for them now; they usually attend within 30 minutes.", PERSON_FINAL)
        assertEquals(20_000L, DIRECT_POLL_MS)
    }

    @Test fun theUsualReceiptIsUnchangedForEveryOtherEnquiry() {
        val vm = src("feature/desk/DeskViewModel.kt") ?: return
        val delivered = vm.substringAfter("private suspend fun delivered(").substringBefore("private suspend fun deliveredDirect")
        assertTrue(delivered.contains("receiptLine(enquiryNumber, email)") && delivered.contains("Action(\"seen\", \"See Your Matters\""))
    }

    @Test fun theSentCaptionComesFromTheBookWithWebsEnglishAsFallback() {
        assertEquals("Sent to an advocate", PERSON_SENT_CAPTION)
        val book = LineBook("hx", mapOf("person_sent_caption" to "Vakeel ko bheja gaya"), null)
        assertEquals("Vakeel ko bheja gaya", book.line("person_sent_caption"))
        assertNull(LineBook.English.line("person_sent_caption"))      // no book: the English constant is used
        assertNull(LineBook("hi", mapOf("other" to "x"), null).line("person_sent_caption"))   // a book without the key: the English
    }

    @Test fun gatherAndRereadsCarryTheLanguageAndTheDocuments() {
        assertNull(langField("en")); assertNull(langField("")); assertEquals("hx", langField("hx")); assertEquals("pa", langField("pa"))
        assertNull(docsField(emptyList())); assertNull(docsField(listOf(" ", "")))
        assertEquals("Rent agreement|Bank statement", docsField(listOf(" Rent agreement ", "", "Bank statement")))
        val vm = src("feature/desk/DeskViewModel.kt") ?: return
        assertTrue(vm.contains("repo.gather(fullText(), askedSoFar, langField(convLang), docsField(read?.documents.orEmpty()))"))
        assertEquals(3, Regex("repo\\.check\\(.*?langField\\(convLang\\)\\)").findAll(vm).count())     // the first read, and both re-reads
        assertTrue(vm.contains("convLang = lang"))
        val api = src("data/api/ApiService.kt")!!
        assertTrue(api.contains("suspend fun deskGather(@Field(\"text\") text: String, @Field(\"asked\") asked: Int, @Field(\"lang\") lang: String? = null, @Field(\"docs\") docs: String? = null, @Field(\"conversation\") conversation: String? = null)"))
        assertTrue(api.contains("suspend fun leadDeskGather(@Field(\"text\") text: String, @Field(\"asked\") asked: Int, @Field(\"lang\") lang: String? = null, @Field(\"docs\") docs: String? = null, @Field(\"conversation\") conversation: String? = null)"))
        assertTrue(api.contains("@Field(\"lang\") lang: String? = null): Map<String, Any?>"))
        val data = src("feature/desk/DeskData.kt")!!
        assertTrue(data.contains("api.leadDeskCheck(text, conversation, lang)") && data.contains("api.leadDeskGather(text, asked, lang, docs, conversation)"))
    }
}
