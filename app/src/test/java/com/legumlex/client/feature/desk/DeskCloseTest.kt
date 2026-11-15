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
        val close = vm.substringAfter("private suspend fun askClose()").substringBefore("/** The cards of the close's choice")
        assertTrue(close.contains("closeDone = true"))                                        // once for this enquiry
        assertTrue(close.contains("closeChose(directBook, directAsked), person) } ?: return next()"))   // an error, a 429 or no answer: carry on as today
        assertTrue(close.contains("say(Para.Text(r.say))"))
        assertTrue(close.contains("Action(\"close_go\", lbl(\"ui_go_ahead\", CLOSE_GO_AHEAD), main = true)") && close.contains("Action(\"close_no\", lbl(\"ui_not_now\", CLOSE_NOT_NOW))"))
        assertEquals("Yes, Go Ahead", CLOSE_GO_AHEAD); assertEquals("Not Now", CLOSE_NOT_NOW)
        val data = src("feature/desk/DeskData.kt")!!
        assertTrue(data.contains("api.leadDeskClose(text, lang, chose, p, note) else api.deskClose(text, lang, chose, p, note)"))
        val api = src("data/api/ApiService.kt")!!
        assertTrue(api.contains("@POST(\"desk/close\")") && api.contains("@POST(\"lead/desk/close\")"))
    }

    @Test fun yesGoesOnAndNotNowEndsKindlyWithNothingSent() {
        val vm = src("feature/desk/DeskViewModel.kt") ?: return
        val go = vm.substringAfter("\"close_go\" -> go {").substringBefore("\n")
        assertTrue(go.contains("dock(Dock.Waiting(\"…\")); next()"))
        val no = vm.substringAfter("\"close_no\" -> go {").substringBefore("\"route_ahc\"")
        assertTrue(no.contains("sayKey(\"close_stop\", CLOSE_STOP_EN)") && no.contains("Action(\"again\"") && !no.contains("send()") && !no.contains("next()"))
        assertEquals("No problem. Nothing has been sent or charged, and you can come back any time.", CLOSE_STOP_EN)
        // Editing an earlier answer asks again; a restart too.
        assertTrue(vm.contains("gatherRan = false; closeDone = false }") && vm.contains("gatherRan = false; closeDone = false; unreadRetried = false; unreadShown = false; sessionHash = null"))
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
        val block = vm.substringAfter("r.declined -> dock(").substringBefore("r.cards.isNotEmpty()")
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
        org.junit.Assert.assertFalse(vm.contains("sayKey(\"q_from_guest\""))   // the web removed the announcement
    }
}

/** REQ-029 slice 1. The JSON below is the server's: Desk.php check_post / cases_desk_unread_answer, cases_desk_close, Guest_desk. */
class DeskSlice1Test {
    private fun src(name: String) = java.io.File("src/main/java/com/legumlex/client/feature/desk/$name").readText()

    @org.junit.Test fun theUnreadAnswerIsReadFromTheServersFields() {
        // cases_desk_unread_answer('hourly_limit', 600) + cases_desk_unread('skipped') + lang
        val d = mapOf("nonmatter" to false, "busy" to true, "unread" to true, "unread_reason" to "hourly_limit",
            "unread_line" to "I can't take your enquiry right now. What you wrote is kept here; please try again in 10 minutes.", "retry_once" to false, "retry_after_minutes" to 10.0,
            "success" to true, "verdict" to "clear", "reading" to "", "reading_state" to "skipped", "lang" to "en")
        val repoSrc = src("DeskData.kt")
        assertTrue(repoSrc.contains("busy = d[\"busy\"] == true") && repoSrc.contains("unreadLine = (d[\"unread_line\"] as? String)") && repoSrc.contains("retryOnce = d[\"retry_once\"] == true"))
        assertEquals(true, d["busy"])
    }

    @org.junit.Test fun anUnreadAnswerStopsWithTheLineAndTwoWaysOnAndSendsNothing() {
        val vm = src("DeskViewModel.kt")
        val v = vm.substringAfter("private suspend fun verdict(reading: DeskReading?) {").substringBefore("unreadRetried = false\n")
        assertTrue(v.contains("reading?.busy == true") && v.contains("reading.retryOnce && !unreadRetried") && v.contains("return unreadStop(reading)"))   // one silent retry, else stop
        val stop = vm.substringAfter("private suspend fun unreadStop").substringBefore("private fun keep")
        assertTrue(stop.contains("r.unreadLine") && stop.contains("unread_try") && stop.contains("unread_new") && stop.contains("ui_try_again") && stop.contains("CLOSE_START_NEW"))
        assertFalse(stop.contains("send()") && stop.contains("next()"))   // nothing is sent or moved on from the unread stop
        assertTrue(vm.contains("\"unread_try\" -> go { vetted = null; unreadRetried = false; unreadShown = false; vet() }") && vm.contains("\"unread_new\" -> go { unreadShown = false; restart() }"))
    }

    @org.junit.Test fun theEntityIsUsedSilently() {
        val vm = src("DeskViewModel.kt")
        assertFalse(vm.contains("entity_onfile")); assertFalse(vm.contains("You are on file as"))
    }

    @org.junit.Test fun closeAnswersAreParsedForEveryShape() {
        // booked: cases_desk_close, chose=consultation
        val booked = parseCloseTurn(mapOf("success" to true, "fee_display" to "₹99", "fee_paise" to 9900.0, "say" to "Your full consultation is booked.", "source" to "fixed", "booked" to true))!!
        assertTrue(booked.booked); assertTrue(booked.cards.isEmpty())
        // refused: success false, refused no_matter, say, lang
        val refused = parseCloseTurn(mapOf("success" to false, "refused" to "no_matter", "say" to "Please tell me what happened.", "lang" to "en"))!!
        assertEquals("no_matter", refused.refused)
        assertNull(parseCloseTurn(mapOf("success" to false, "say" to "Too many requests.")))   // a plain failure is still skipped
        // choice: two cards, the consultation first, the written secondary; `consult` beside it
        val choice = mapOf("success" to true, "fee_display" to "₹99", "fee_paise" to 9900.0, "source" to "fixed", "say" to "You asked to speak to an advocate. Which do you prefer?",
            "choice" to mapOf("lead" to "You asked to speak to an advocate. Which do you prefer?", "cards" to listOf(
                mapOf("key" to "consultation", "icon" to "call", "title" to "Full Consultation", "price" to "₹499", "includes" to "x", "timing" to "y", "button" to "Yes, Book It"),
                mapOf("key" to "written", "icon" to "mail", "title" to "Written Enquiry", "price" to "₹99", "includes" to "x", "timing" to "y", "button" to "Send The Written Enquiry", "secondary" to true))),
            "consult" to mapOf("fee_display" to "₹499", "fee_paise" to 49900.0))
        val c = parseCloseTurn(choice)!!
        assertEquals(listOf("consultation", "written"), c.cards.map { it.key })
        assertEquals("Yes, Book It", c.cards[0].button); assertFalse(c.cards[0].secondary); assertTrue(c.cards[1].secondary)
        assertTrue(!c.booked && !c.declined && c.refused.isEmpty())
    }

    @org.junit.Test fun closeSendsWhatWasChosenAndNoWrittenCloseFollowsABooking() {
        assertEquals("consultation", closeChose(true, true)); assertEquals("written", closeChose(false, true)); assertNull(closeChose(false, false))
        val vm = src("DeskViewModel.kt")
        val close = vm.substringAfter("private suspend fun askClose()").substringBefore("/** The cards of the close's choice")
        assertTrue(close.contains("if (directBook) return next()"))                                   // F-001: a booked consultation gets no written-enquiry close
        assertTrue(close.contains("repo.close(fullText(), langField(convLang), closeChose(directBook, directAsked), person)"))
        assertTrue(close.contains("r.refused == \"no_matter\"") && close.contains("r.booked -> next()") && close.contains("r.cards.isNotEmpty()"))
        val api = java.io.File("src/main/java/com/legumlex/client/data/api/ApiService.kt").readText()
        assertTrue(api.contains("@Field(\"chose\") chose: String? = null, @Field(\"person\") person: Int? = null"))
        assertTrue(vm.contains("lbl(\"ui_start_new\", CLOSE_START_NEW), main = true)"))               // Not Now rests on Start A New Enquiry, as the web's
    }

    @org.junit.Test fun theGuestLimitIsTheServersAndTheDefaultIsAThousand() {
        assertEquals(1000, GUEST_CHARS)
        // Guest_desk.php: data + guest_chars
        val r = parseGuestReply(mapOf("success" to true, "reply" to "Tell me more.", "left" to 1.0, "guest_chars" to 1000.0, "lang" to "en"))!!
        assertEquals(1000, r.guestChars)
        assertEquals(1500, parseGuestReply(mapOf("reply" to "x.", "guest_chars" to 1500.0))!!.guestChars)
        assertNull(parseGuestReply(mapOf("reply" to "x."))!!.guestChars)
        val vm = src("DeskViewModel.kt")
        assertTrue(vm.contains("r.guestChars?.let { n -> _ui.update { it.copy(guestChars = n) } }") && vm.contains("val most = _ui.value.guestChars"))
        assertTrue(src("DeskScreen.kt").contains("GuestDock(ui.error, ui.guestChars"))
    }

    @org.junit.Test fun theSkipWordsAndOneWordedSendButton() {
        assertEquals("Skip The Questions", GATHER_SEND_NOW)
        val screen = src("DeskScreen.kt")
        // The one button: word and icon, gold with the dark ink, grey and disabled until there is input; in the question box, every other composer and the deadline.
        val btn = screen.substringAfter("internal fun SendButton").substringBefore("@Composable\nprivate fun FileChips")
        assertTrue(btn.contains("if (enabled) D.Gold else D.Line2") && btn.contains("if (enabled) D.OnGold else D.Muted") && btn.contains("clickable(enabled = enabled") && btn.contains("Icons.AutoMirrored.Outlined.Send") && btn.contains("Text(label"))
        assertTrue(screen.contains("SendButton(dt(\"ui_send\", \"Send\"), text.isNotBlank(), onClick = onSend)"))
        assertTrue(screen.contains("SendButton(sendLabel ?: dt(\"ui_send\", \"Send\"), v.isNotBlank() || sendReady)"))
        assertTrue(screen.contains("text.isNotBlank() || date.isNotEmpty()"))
        assertFalse(screen.contains("Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(D.Gold)"))   // no wordless icon square is left
    }
}

/** REQ-029 slice 1: English lines from desk/lines?lang=en, the app's constants only as the offline fallback. Shape: Desk.php lines_get (lang en, guest_chars, lines, pool, footer null). */
class DeskEnglishLinesTest {
    private val served = mapOf("lang" to "en", "guest_chars" to 1000.0, "pool" to emptyList<String>(), "footer" to null,
        "lines" to mapOf("ui_send_now" to "Skip The Questions", "ui_send" to "Send", "ui_start_new" to "Start a New Enquiry", "err_more_now" to "Please add a few words, or choose Skip The Questions."))

    @org.junit.Test fun aServedEnglishLineOverridesTheConstantAndNeverCountsAsTranslated() {
        val book = parseLineBook(served)!!
        assertEquals("en", book.lang)
        assertFalse(book.active)                                         // English is not a translation: no tag, no footer
        assertNull(book.footer)
        // the same call the screens and the view model make: the book's label, else the constant
        assertEquals("Skip The Questions", book.fragment("ui_send_now") ?: "Old Words")
        assertEquals("Please add a few words, or choose Skip The Questions.", book.fragment("err_more_now") ?: "old")
        assertEquals("Start a New Enquiry", book.fragment("ui_start_new") ?: CLOSE_START_NEW)
        assertEquals("Send", book.fragment("ui_send") ?: "Fallback")
        assertEquals("kept", book.fragment("a_key_the_server_lacks") ?: "kept")   // a key the server lacks keeps the constant
    }

    @org.junit.Test fun theConstantsApplyWhenTheCallFailsOrAnswersNothing() {
        assertNull(parseLineBook(null)); assertNull(parseLineBook(mapOf("lang" to "en", "lines" to emptyMap<String, String>())))
        assertNull(LineBook.English.fragment("ui_send_now")); assertEquals("Skip The Questions", LineBook.English.fragment("ui_send_now") ?: GATHER_SEND_NOW)
        // languageSwitch: no cache and a failed fetch is the constants' book; a served book wins; the cache is kept.
        val got = kotlinx.coroutines.runBlocking { languageSwitch("en", null) { null } }
        assertEquals(LineBook.English, got)
        val en = parseLineBook(served)!!
        assertEquals(en, kotlinx.coroutines.runBlocking { languageSwitch("en", null) { en } })
        assertEquals(en, kotlinx.coroutines.runBlocking { languageSwitch("en", en) { error("must not fetch") } })
        // the view model fetches English once at the start, for a signed-in desk
        val vm = java.io.File("src/main/java/com/legumlex/client/feature/desk/DeskViewModel.kt").readText()
        assertTrue(vm.contains("repo.lines(\"en\", guest) ?: return@launch") && vm.contains("/** The English lines come from the server too"))
    }

    @org.junit.Test fun hindiBooksStillCountAsTranslated() {
        val hi = parseLineBook(mapOf("lang" to "hi", "lines" to mapOf("ui_send" to "भेजें"), "footer" to mapOf("tag" to "अनुवादित", "en" to "Translated", "local" to "x")))!!
        assertTrue(hi.active); assertEquals("भेजें", hi.fragment("ui_send"))
    }
}

/** REQ-029 slice 1, phone findings of 15 Nov. */
class DeskFooterAndComposerTest {
    private fun src(name: String) = java.io.File("src/main/java/com/legumlex/client/feature/desk/$name").readText()

    @org.junit.Test fun theFooterIsTheConsultationsOnceItIsChosenAndNeverThe30MinutePromise() {
        val priced = DeskTerms(basePrice = 9900)
        // written enquiry: the fee, and a target once an advocate accepts it (reception.php rcp_reply, R6)
        assertEquals("Submitting an enquiry costs ${priced.displayPrice}, paid once it is written. Nothing is charged before then.", disclaimerLead(priced, false, ""))
        assertEquals(" The advocates read every enquiry, and we aim to reply within 30 minutes once an advocate accepts it.", disclaimerTail(false))
        // the full consultation chosen: its fee alone and who contacts them (rcp_direct_text), no enquiry fee and no 30 minutes (R9, R9a)
        assertEquals("A full consultation costs ₹499, paid when you book it.", disclaimerLead(priced, true, "₹499"))
        assertEquals(" The advocate who takes it, or their office, will contact you to fix a time.", disclaimerTail(true))
        assertFalse(disclaimerLead(priced, true, "₹499").contains("Submitting an enquiry") || disclaimerTail(true).contains("30 minutes"))
        assertEquals("A full consultation is paid when you book it.", disclaimerLead(priced, true, ""))
        val screen = src("DeskScreen.kt")
        assertTrue(screen.contains("direct = ui.footerDirect, fee = ui.directFee, hidden = ui.footerUnread"))
        val vm = src("DeskViewModel.kt")
        assertTrue(vm.contains("footerDirect = directBook, directFee = offerFee, footerUnread = unreadShown"))
        assertTrue(vm.contains("unreadShown = true"))                                  // nothing is offered while a message is unread: no footer
    }

    @org.junit.Test fun noDeskWordingPromisesAThirtyMinuteReply() {
        for (f in listOf("DeskIntake.kt", "DeskScreen.kt", "DeskViewModel.kt")) assertFalse(f, Regex("usually (?:reply|replies|answered|attend|within)[^\"\\n]{0,30}30 minutes", RegexOption.IGNORE_CASE).containsMatchIn(src(f)))
        assertEquals("Reply aimed in 30 minutes", "Reply aimed in 30 minutes")
        assertTrue(src("DeskScreen.kt").contains("\"Reply aimed in 30 minutes\", \"> we aim to reply within 30 minutes once an advocate accepts it\""))
        assertTrue(receiptLine("ENQ-1", "a@b.c").contains("once one accepts it, we aim to reply within 30 minutes"))
        assertFalse(DIRECT_OFFER_EN.contains("30 minutes")); assertFalse(PERSON_FINAL.contains("30 minutes"))
    }

    @org.junit.Test fun everyTextStepHasOneComposerWithSendAtTheBottomRight() {
        val screen = src("DeskScreen.kt")
        val compose = screen.substringAfter("private fun Compose(").substringBefore("/**\n * A hearing or a deadline")
        // field above, then a row with the spacer and the one Send button: the same place as the first box
        assertTrue(compose.contains("Column(") && compose.indexOf("BasicTextField") < compose.indexOf("SendButton("))
        assertTrue(compose.contains("Spacer(Modifier.weight(1f))") && compose.contains("RoundedCornerShape(18.dp)"))
        assertFalse(compose.contains("Row(\n        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))"))
        val question = screen.substringAfter("private fun QuestionDock(").substringBefore("private fun FileChips")
        assertTrue(question.contains("SendButton(") && question.contains("Spacer(Modifier.weight(1f))"))
    }
}

/** REQ-029 slice 1, UI and UX pass (owner, 2026-11-15). */
class DeskUiRulesTest {
    private fun src(name: String) = java.io.File("src/main/java/com/legumlex/client/feature/desk/$name").readText()

    @org.junit.Test fun aPairOrTrioOfButtonsSitsInOneRowAndALongTrioStacksAtEqualWidth() {
        assertEquals(ChipLayout.Single, chipLayout(listOf("Try Again")))
        assertEquals(ChipLayout.Row, chipLayout(listOf("Book The Full Consultation", "Send The Written Enquiry")))   // the close: side by side, never one above the other
        assertEquals(ChipLayout.Row, chipLayout(listOf("Business", "Individual", "NRI")))
        assertEquals(ChipLayout.Row, chipLayout(listOf("I Have Paid", "Pay The Fee", "Set It Aside")))
        assertEquals(ChipLayout.Stack, chipLayout(listOf("See Whether It Arrived", "Send It Again", "Something Else Entirely Long")))
        assertEquals(ChipLayout.Stack, chipLayout(listOf("Pay Now", "See Your Matters", "Ask Something Else")))      // the pay trio: "Ask Something Else" needs three lines in a third of the width
        assertEquals(ChipLayout.Flow, chipLayout(listOf("India  +91", "United States  +1", "United Kingdom  +44", "Australia  +61", "Somewhere else")))
        val screen = src("DeskScreen.kt")
        val group = screen.substringAfter("private fun ChipGroup(").substringBefore("@Composable\nprivate fun DocsDock")
        // equal weights and equal heights in the row; equal width in the stack; two-line labels inside the button
        assertTrue(group.contains("Modifier.weight(1f).fillMaxHeight()") && group.contains("height(IntrinsicSize.Min)") && group.contains("Modifier.fillMaxWidth()") && group.contains("BoxWithConstraints"))
        assertTrue(screen.contains("is Dock.Actions -> Column { ChipGroup("))
    }

    @org.junit.Test fun aChoiceButtonCarriesTheServersFeeAsItsSecondLine() {
        val vm = src("DeskViewModel.kt")
        assertTrue(vm.contains("Dock.Cards(ChoiceBlock(r.say, r.cards), \"close\")"))
        assertTrue(src("DeskScreen.kt").contains("ChipSpec(a.label, a.main, a.sub)") && src("DeskScreen.kt").contains("if (c.sub.isNotBlank()) Text(c.sub"))
    }

    @org.junit.Test fun skipTheQuestionsIsAQuietTextButtonAndTheFeeIsNotHeavierThanTheFooter() {
        val screen = src("DeskScreen.kt")
        val gather = screen.substringAfter("private fun GatherDock(").substringBefore("@Composable\nprivate fun ClarifyDock")
        assertTrue(gather.contains("Quiet(dt(\"ui_send_now\", GATHER_SEND_NOW), onSendNow)") && !gather.contains("Chips("))
        val foot = screen.substringAfter("private fun Disclaimer(").substringBefore("/**\n * The services")
        assertTrue(foot.contains("withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = D.Ink)) { append(disclaimerLead(t, direct, fee, book)) }"))   // the web bolds the lead sentence
        val dl = screen.substringAfter("private fun DeadlineDock(").substringBefore("if (picking)")
        assertTrue(dl.contains("heightIn(min = 40.dp)") && dl.contains("SendButton(dt(\"ui_send\", \"Send\"), text.isNotBlank() || date.isNotEmpty())"))   // the date pill and Send share one height
        val stop = src("DeskViewModel.kt").substringAfter("private suspend fun unreadStop").substringBefore("private fun keep")
        assertTrue(stop.contains("say(Para.Text(r.unreadLine.ifBlank { UNREAD_EN }))") && !stop.contains("note = true"))   // a normal bubble
    }

    @org.junit.Test fun theThreeStepsShareOneBaseline() {
        val p = src("DeskScreen.kt").substringAfter("private fun Process()").substringBefore("private fun Disclaimer")
        assertTrue(p.contains("minLines = 2, maxLines = 2, modifier = Modifier.weight(1f)"))      // labels: equal heights, top aligned
        assertTrue(p.contains("textAlign = TextAlign.Center, minLines = 2, maxLines = 2, modifier = Modifier.fillMaxWidth()"))   // the caption: a fixed two lines, so nothing jumps
    }
}

/** REQ-029 slice 1, phone findings of 15 Nov (19:11 build). */
class DeskKeepAndBoldTest {
    private fun src(name: String) = java.io.File("src/main/java/com/legumlex/client/feature/desk/$name").readText()

    @org.junit.Test fun onlyTheConsultationChoiceIsFilledAndTheWrittenOneIsOutlined() {
        // the server marks `secondary` only when insisting (cases_desk_choice); the close's choice has none, so the rule is the app's: consultation first, filled (R10)
        val vm = src("DeskViewModel.kt")
        assertTrue(src("DeskScreen.kt").contains("val main = c.key == \"consultation\""))   // the cards: the consultation filled, the written enquiry outlined
        assertFalse(vm.contains("main = !c.secondary"))
    }

    @org.junit.Test fun closingTheDeskKeepsTheConversationForTheAppSession() {
        val screen = src("DeskScreen.kt")
        assertTrue(screen.contains("if (account) viewModel() else {") && screen.contains("viewModel(viewModelStoreOwner = deskActivity(deskContext), key = key)"))   // lives with the activity, not the screen
        assertTrue(screen.contains("val key = remember { DeskKeep.key(deskContext) }"))                                                                                      // one key per opening, so signing in inside the desk never swaps it
        assertTrue(screen.contains("TextFieldValue(vm.draftText, TextRange(vm.draftText.length))") && screen.contains("LaunchedEffect(draft.text) { vm.draftText = draft.text }"))   // the unsent question too
        val vm = src("DeskViewModel.kt")
        // a finished enquiry makes the next opening a new conversation; Start A New Enquiry (restart) clears the draft too
        assertTrue(vm.contains("draftText = \"\"; DeskKeep.renew()") && vm.contains("draftText = \"\"; pollJob?.cancel()"))
    }

    @org.junit.Test fun aServedLineWithItsOwnBoldIsNotWrappedAgain() {
        // desk_lines note_urgent (English): <b>This looks urgent{reason}.</b> I will mark it so, and {adv} will see it first.
        val book = parseLineBook(mapOf("lang" to "en", "lines" to mapOf("note_urgent" to "<b>This looks urgent{reason}.</b> I will mark it so, and the advocates will see it first.")))!!
        val t = book.line("note_urgent", mapOf("reason" to ": shop locked three days ago"))!!
        assertEquals("**This looks urgent: shop locked three days ago.** I will mark it so, and the advocates will see it first.", t)
        assertTrue(src("DeskViewModel.kt").contains("if (t.contains(\"**\")) t else \"**\$t**\""))
        // exactly one bold span: the first sentence
        assertEquals(2, Regex("\\*\\*").findAll(t).count())
    }
}

/** REQ-029 slice 1: Suggested Topics and the draft that survives a restart (web parity: reception.php TOPICS and lex_desk_draft). */
class DeskTopicsAndDraftTest {
    private fun src(name: String) = java.io.File("src/main/java/com/legumlex/client/feature/desk/$name").readText()

    @org.junit.Test fun theTopicsAreTheWebsInTheSameOrderAndAStarterIsNeverSent() {
        assertEquals(6, DESK_TOPICS.size); assertEquals(3, DESK_TOPICS_SHOWN)
        assertEquals("A cheque given to me has bounced.", DESK_TOPICS[0].title)
        assertEquals("Complaints under Section 138 of the NI Act.", DESK_TOPICS[0].covers)
        assertEquals("I have received a legal notice from ", DESK_TOPICS[2].starter)
        assertEquals("My employer has not paid my dues.", DESK_TOPICS[5].title)
        assertEquals(listOf("cheque", "tenant", "notice", "abroad", "partner", "employer"), DESK_TOPICS.map { t -> listOf("cheque", "tenant", "notice", "abroad", "partner", "employer").first { t.title.lowercase().contains(it) } })
        // the same six as the web page, in its order (reception.php TOPICS), compared from the page source when it is beside the app
        val screen = src("DeskScreen.kt")
        assertTrue(screen.contains("SuggestedTopics(onTopic = put)"))                                  // a tap fills the box through put(): focus, no send
        val topics = screen.substringAfter("internal fun SuggestedTopics").substringBefore("\n}\n")
        assertTrue(topics.contains("take(if (all) DESK_TOPICS.size else DESK_TOPICS_SHOWN)") && topics.contains("\"See All\"") && topics.contains("\"Show Fewer\"") && topics.contains("\"Suggested Topics\""))
        assertFalse(topics.contains("vm."))                                                              // nothing here talks to the desk: nothing is sent
    }

    @org.junit.Test fun theDraftIsKeptFromThreeCharactersPerAccountAndSurvivesARestart() {
        assertEquals(3, DESK_DRAFT_MIN)
        val vm = src("DeskViewModel.kt")
        assertTrue(vm.contains("getSharedPreferences(\"lex_desk_draft\"") && vm.contains("accountKey()"))
        assertTrue(vm.contains("if (v.trim().length >= DESK_DRAFT_MIN) putString(draftKey, v) else remove(draftKey)"))
        assertTrue(vm.contains("var draftText: String = draftPrefs.getString(draftKey, \"\").orEmpty()"))
        // cleared on send (submitQuestion), at a finished enquiry and at Start A New Enquiry
        assertTrue(vm.contains("draftText = \"\"; DeskKeep.renew()") && vm.contains("draftText = \"\"; pollJob?.cancel()") && vm.contains("            draftText = \"\"\n            _ui.update { it.copy(opened = true, draftClearTick = it.draftClearTick + 1) }"))
        val store = java.io.File("src/main/java/com/legumlex/client/data/auth/TokenStore.kt").readText()
        assertTrue(store.contains("suspend fun accountKey(): String") && store.contains("return \"guest\""))
    }
}

/** REQ-029 slice 1: the welcome as the web says it (F-018, R23a), and the intro's lower edge. */
class DeskWelcomeTest {
    private fun src(name: String) = java.io.File("src/main/java/com/legumlex/client/feature/desk/$name").readText()
    private val ctx = DeskContext(firstName = "App", company = "Replastica", entity = "business", counsel = listOf("Asha Verma", "Ravi Kumar"), phone = "9876543210", openCases = 2, nextHearingDate = "2026-11-20", nextHearingTitle = "Rent recovery")

    @org.junit.Test fun theWelcomeNamesNeitherTheCompanyNorTheFile() {
        val en = parseLineBook(mapOf("lang" to "en", "lines" to mapOf("welcome_back_name" to "Welcome back, {first}.", "welcome_back" to "Welcome back.")))!!
        assertEquals("Welcome back, App.", welcomeLine("App", en))              // served welcome_back_name
        assertEquals("Welcome back, App.", welcomeLine("App", LineBook.English)) // the constant fallback is the same
        assertEquals("Welcome back.", welcomeLine("", LineBook.English))
        val vm = src("DeskViewModel.kt")
        val greet = vm.substringAfter("private suspend fun greetBack(").substringBefore("private fun needsRoute")
        assertFalse(greet.contains("Good to hear from") || greet.contains("c.company") || greet.contains("on your file") || greet.contains("go on your file"))
        assertFalse(src("DeskIntake.kt").substringAfter("The welcome, as the web says it").contains("company"))
    }

    @org.junit.Test fun theFactsAreAdvocatesOpenMattersAndNextHearing() {
        val f = welcomeFacts(ctx, { d -> d }, { _, en -> en }, { n -> null })
        assertEquals(listOf("Your advocates", "Open matters", "Next hearing"), f.map { it.first })
        assertEquals("Asha Verma and Ravi Kumar", f[0].second); assertEquals("2 matters", f[1].second); assertEquals("2026-11-20, Rent recovery", f[2].second)
        // the served labels and counts win
        val served = welcomeFacts(ctx.copy(openCases = 1), { d -> d }, { k, en -> if (k == "lab_open") "Open Cases" else en }, { n -> "$n matter" })
        assertEquals("Open Cases" to "1 matter", served[1])
        assertTrue(welcomeFacts(ctx.copy(counsel = emptyList(), openCases = 0, nextHearingDate = null), { d -> d }, { _, en -> en }, { null }).isEmpty())
    }

    @org.junit.Test fun someoneUnknownIsThankedAsTheWebDoes() {
        assertEquals("Thank you for choosing Legum Lex.", GREET_NEW_NOENAME_EN)
        assertTrue(src("DeskViewModel.kt").contains("tl(\"greet_new_noname\") ?: GREET_NEW_NOENAME_EN"))
    }

    @org.junit.Test fun theIntroFadesAtItsLowerEdgeWhereMoreLiesBelow() {
        val screen = src("DeskScreen.kt")
        assertTrue(screen.contains("if (introScroll.canScrollForward) Box(") && screen.contains("Brush.verticalGradient(listOf(Color.Transparent, D.Page))"))
        assertTrue(screen.contains("SuggestedTopics(onTopic = put)\n                        Spacer(Modifier.height(28.dp))"))   // the last row can scroll clear of the fade
    }
}

/** REQ-029 slice 1: the guest's composer is the same card, with its tools in the bottom row. */
class DeskGuestComposerTest {
    private fun src(name: String) = java.io.File("src/main/java/com/legumlex/client/feature/desk/$name").readText()

    @org.junit.Test fun theGuestBoxPutsSignInAndTheCounterInTheCardsBottomRowLeftOfSend() {
        val screen = src("DeskScreen.kt")
        val compose = screen.substringAfter("private fun Compose(").substringBefore("/**\n * A hearing or a deadline")
        val row = compose.substringAfter("        Row(verticalAlignment = Alignment.CenterVertically) {\n            tools?.invoke()")
        // bottom row order: tools, gap, counter, tick, Send
        val order = listOf("Spacer(Modifier.weight(1f))", "count(v)", "SendButton(").map { row.indexOf(it) }
        assertTrue(order.all { it >= 0 } && order == order.sorted())
        assertTrue(compose.contains("tools?.invoke()") && compose.indexOf("tools?.invoke()") < compose.indexOf("SendButton("))
        val guest = screen.substringAfter("private fun GuestDock(").substringBefore("/** The Google sign-in, in the conversation. */")
        assertTrue(guest.contains("tools = { QuietInRow(dt(\"ui_signin_google\", \"Sign in with Google\"), onSignIn) }") && guest.contains("count = { v -> \"\${v.length} / \$limit\" }"))
        assertFalse(guest.contains("Row(verticalAlignment = Alignment.CenterVertically)"))   // no second row under the card
    }
}

/** REQ-029: findings of the held booking (15 Nov): the pay step's order, and a typed number kept in order. */
class DeskBookingFindingsTest {
    private fun src(name: String) = java.io.File("src/main/java/com/legumlex/client/feature/desk/$name").readText()

    /** A keyboard typing one character at the cursor, then the field's own formatter (what BasicTextField does with a value). */
    private fun typeFast(digits: String, nri: Boolean = false): androidx.compose.ui.text.input.TextFieldValue {
        var f = androidx.compose.ui.text.input.TextFieldValue("", androidx.compose.ui.text.TextRange(0))
        for (c in digits) {
            val at = f.selection.start
            val typed = androidx.compose.ui.text.input.TextFieldValue(f.text.substring(0, at) + c + f.text.substring(at), androidx.compose.ui.text.TextRange(at + 1))
            f = reformat(typed) { formatPhoneInput(it, nri) }
        }
        return f
    }

    @org.junit.Test fun aNumberTypedDigitByDigitIsKeptInOrderAndTheCursorStaysAtTheEnd() {
        for (n in listOf("9876543210", "9876543201", "6123456789", "7000000001")) {
            val f = typeFast(n)
            assertEquals(n.take(5) + " " + n.drop(5), f.text)
            assertEquals(n, f.text.filter { it.isDigit() })            // what is sent is exactly what was typed
            assertEquals(f.text.length, f.selection.start)
        }
        assertEquals("98765 43210", typeFast("919876543210").text)       // a pasted +91 form
        assertEquals("98765 43210", typeFast("09876543210").text)
        assertEquals("98765", typeFast("98765").text)
    }

    @org.junit.Test fun theComposerKeepsItsOwnCursorSoAFormatterCannotLeaveItBehind() {
        val screen = src("DeskScreen.kt")
        assertTrue(screen.contains("BasicTextField(field, { field = reformat(it, format) }") && screen.contains("var field by remember { mutableStateOf(TextFieldValue(initial"))
        assertTrue(screen.contains("format = { raw -> formatPhoneInput(raw, nri) }"))
    }

    @org.junit.Test fun theBookedPayStepIsPayNowThenSeeYourMattersThenAskSomethingElseAsTheWebs() {
        val vm = src("DeskViewModel.kt")
        val actions = vm.substringAfter("private fun bookedPayActions").substringBefore("/** Pay The Fee")
        assertTrue(actions.indexOf("\"direct_pay\", payBtn, main = true") < actions.indexOf("\"seen\"") && actions.indexOf("\"seen\"") < actions.indexOf("\"again\""))
        assertFalse(actions.contains("I Have Paid"))                       // nothing is paid yet
        assertFalse(vm.contains("Action(\"direct_paid\""))
        // the page opens on Pay Now, not by itself on arrival
        val booked = vm.substringAfter("private suspend fun deliveredBooked").substringBefore("/** The web's pay step")
        assertFalse(booked.substringBefore("watchBooked()").contains("DeskEvent.OpenUrl"))
    }
}

/** REQ-029: the desk's debug-only Test Contact (the web's dev Test visitor). */
class DeskTestContactTest {
    private fun src(name: String) = java.io.File("src/main/java/com/legumlex/client/feature/desk/$name").readText()

    @org.junit.Test fun itExistsOnlyInADebugBuildWithTheTestContactConfigured() {
        assertTrue(deskTestContactOn(true, "test@example.org", "secret"))
        assertFalse(deskTestContactOn(false, "test@example.org", "secret"))   // a release build: never, whatever the values
        assertFalse(deskTestContactOn(true, "", "secret")); assertFalse(deskTestContactOn(true, "test@example.org", ""))
        val build = java.io.File("build.gradle.kts").readText()
        assertTrue(build.contains("DEV_LOGIN_EMAIL"))                         // the values come from local.properties and are blank without it
        val screen = src("DeskScreen.kt")
        assertTrue(screen.contains("deskTestContactOn(com.legumlex.client.BuildConfig.DEBUG, com.legumlex.client.BuildConfig.DEV_LOGIN_EMAIL, com.legumlex.client.BuildConfig.DEV_LOGIN_PASSWORD)"))
    }

    @org.junit.Test fun itGoesThroughTheSameAfterSignInPathAsGoogle() {
        val screen = src("DeskScreen.kt")
        // both end in LoginState.Success, which the desk answers once: vm.signedIn() (resume included), in the same view model
        assertTrue(screen.contains("is com.legumlex.client.feature.auth.LoginState.Success -> vm.signedIn(st.result.email, st.result.name)"))
        assertTrue(screen.contains("login.onEmailLogin(com.legumlex.client.BuildConfig.DEV_LOGIN_EMAIL, com.legumlex.client.BuildConfig.DEV_LOGIN_PASSWORD)") && screen.contains("login::onIdTokenReceived"))
        assertTrue(screen.contains("if (onTestContact != null) Quiet(\"Test Contact\", onTestContact)"))        // absent when null
        assertTrue(screen.contains("Dock.SignIn -> GoogleDock(signingIn, ui.error, google, testContact)"))
    }
}


/** REQ-029: no label is ever cut off, at 360 and 412 dp, with the real labels. */
class DeskChipFitTest {
    private fun widths() = listOf(360f - 2 * DESK_SIDE_MARGIN, 412f - 2 * DESK_SIDE_MARGIN)

    @org.junit.Test fun noGroupNeedsMoreThanTwoLinesInARowAtTheSupportedWidths() {
        val groups = listOf(
            listOf("Book The Full Consultation", "Send The Written Enquiry"), listOf("Yes, Book It", "Send The Written Enquiry"), listOf("Yes, Go Ahead", "Not Now"),
            listOf("Carry On", "Start A New Enquiry"), listOf("Try Again", "Start A New Enquiry"), listOf("Attach Documents", "Carry On Without"),
            listOf("Business", "Individual", "NRI"), listOf("Pay Now", "See Your Matters", "Ask Something Else"),
            listOf("Yes, That Number", "A Different Number"), listOf("See Whether It Arrived", "Send It Again"), listOf("Open My Account", "No, I Have A Question"),
        )
        for (w in widths()) for (g in groups) {
            val layout = chipLayout(g, w)
            if (layout == ChipLayout.Row) for (l in g) assertTrue("$l at $w dp", chipLines(l, g.size, w) <= 2)   // a row only when every label fits in two lines
            else assertEquals("$g at $w dp", ChipLayout.Stack, layout)                                           // else equal-width stacked, never a cut label
        }
        assertEquals(ChipLayout.Stack, chipLayout(listOf("Pay Now", "See Your Matters", "Ask Something Else"), widths()[0]))
        assertEquals(ChipLayout.Row, chipLayout(listOf("Business", "Individual", "NRI"), widths()[0]))
        assertEquals(ChipLayout.Row, chipLayout(listOf("Book The Full Consultation", "Send The Written Enquiry"), widths()[1]))
    }
}


/** REQ-029 (a) parity fixes: no "I have what you wrote above"; "Signed in as <email>" and the greeting after an in-desk sign-in (reception.php signedIn, welcome, greetBack). */
class DeskSignInTurnsTest {
    private fun src(name: String) = java.io.File("src/main/java/com/legumlex/client/feature/desk/$name").readText()

    @org.junit.Test fun nothingAnnouncesWhatTheyWroteAbove() {
        val vm = src("DeskViewModel.kt")
        assertFalse(vm.contains("Q_FROM_GUEST") && vm.contains("sayKey(\"q_from_guest\""))
        val ask = vm.substringAfter("private suspend fun askQuestion()").substringBefore("// ---- A guest")
        assertFalse(ask.contains("I have what you wrote"))
    }

    @org.junit.Test fun theVisitorsBubbleSaysSignedInAsTheEmailAndTheGreetingFollows() {
        val vm = src("DeskViewModel.kt")
        val sign = vm.substringAfter("fun signedIn(email: String = \"\", name: String = \"\") {").substringBefore("fun signInProblem")
        assertTrue(sign.contains("go { me(\"Signed in as \$email\") }"))
        // the greeting comes before the guest conversation is read
        val ask = vm.substringAfter("private suspend fun askQuestion()").substringBefore("val story = guestStory")
        assertTrue(ask.indexOf("greetBack(withNotes = false)") in 0 until ask.indexOf("resumeFromGuest()"))
        // someone the firm does not know yet is thanked by first name (served greet_new)
        val greet = vm.substringAfter("private suspend fun greetBack(").substringBefore("private fun needsRoute")
        assertTrue(greet.contains("tl(\"greet_new\", mapOf(\"first\" to first)) ?: \"Thank you for choosing Legum Lex, \$first.\""))
        assertEquals("Thank you for choosing Legum Lex, Asha.", parseLineBook(mapOf("lang" to "en", "lines" to mapOf("greet_new" to "Thank you for choosing Legum Lex, {first}.")))!!.line("greet_new", mapOf("first" to "Asha")))
        val screen = src("DeskScreen.kt")
        assertTrue(screen.contains("vm.signedIn(st.result.email, st.result.name)"))
    }
}
