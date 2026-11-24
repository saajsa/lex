package com.legumlex.client.feature.desk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * REQ-029 slice 2. Answers shaped as desk_api_parity.php's fixtures (F01, F01 insisting, F02, a greeting, A04 consult wish) and the cards of cases_desk_choice().
 */
class DeskCardsTest {
    private fun src(name: String) = java.io.File("src/main/java/com/legumlex/client/feature/desk/$name").readText()

    private val cards = mapOf("lead" to "A full consultation with the advocates is ₹499. Once you pay, it is offered to our counsel straight away. Shall I book it?", "insist" to true, "cards" to listOf(
        mapOf("key" to "consultation", "icon" to "consult", "title" to "Full Consultation", "price" to "₹499", "includes" to "A talk with an advocate about your matter. Offered to our counsel straight away.",
            "timing" to "The advocate who takes it, or their office, will contact you to fix a time.", "button" to "Yes, Book It"),
        mapOf("key" to "written", "icon" to "written", "title" to "Written Enquiry", "price" to "₹99 in all", "includes" to "Your enquiry goes to our counsel. No other charges.",
            "timing" to "Once an advocate accepts it, we aim to reply within 30 minutes.", "button" to "Send The Written Enquiry", "intro" to "If you would like to send just an enquiry, it is only ₹99 in all.", "secondary" to true)))

    private fun answer(vararg extra: Pair<String, Any?>) = parseReading(mapOf("success" to true, "verdict" to "clear", "reading" to "", "reading_state" to "skipped", "lang" to "en") + extra)!!

    @Test fun aBareRequestForAPersonAsksForTheMatter() {      // F01
        val r = answer("nonmatter" to true, "message_kind" to "person_request", "legal_inquiry" to false, "need_matter" to true, "wants_person" to true, "reply" to "")
        assertEquals(NonMatterTurn.AskMatter, nonMatterTurn(r)); assertNull(r.insist)
    }

    @Test fun insistingGoesStraightToTheCardsWithNoAskForTheMatter() {      // F01 insisting: insist block, reply '', need_matter false
        val r = answer("nonmatter" to true, "message_kind" to "person_request", "legal_inquiry" to false, "need_matter" to false, "reply" to "", "insist" to cards)
        assertEquals(NonMatterTurn.Insist, nonMatterTurn(r))
        assertEquals(listOf("consultation", "written"), r.insist!!.cards.map { it.key })
        val c = r.insist!!.cards
        assertEquals("₹499", c[0].price); assertEquals("Yes, Book It", c[0].button); assertEquals("A talk with an advocate about your matter. Offered to our counsel straight away.", c[0].includes)
        assertTrue(c[1].secondary); assertEquals("If you would like to send just an enquiry, it is only ₹99 in all.", c[1].intro)
    }

    @Test fun aGreetingOrAServiceQuestionIsAnsweredAndTheBoxOpens() {
        val r = answer("nonmatter" to true, "message_kind" to "service_question", "legal_inquiry" to false, "need_matter" to false, "reply" to "I am Lex, the platform's AI assistant. What would you like help with?")
        assertEquals(NonMatterTurn.Answer, nonMatterTurn(r))
        assertEquals("I am Lex, the platform's AI assistant. What would you like help with?", r.reply)
    }

    @Test fun anExplicitConsultationWishWithAnAgreementGoesToTheCards() {      // A04 / 'consult wish, then an agreement'
        val r = answer("route" to "consultation", "route_line" to "", "insist" to cards, "need_matter" to false, "reply" to "")
        assertEquals(null, r.route)                                    // an empty route line is no route to show
        assertTrue(r.insist != null)
        val a = answer("route" to "consultation", "route_line" to "You can book a full consultation here.", "insist" to cards)
        assertEquals("consultation", a.route!!.route); assertTrue(a.insist != null)
    }

    @Test fun anEnquiryIsNotANonMatter() {
        for (r in listOf(answer("legal_inquiry" to true, "message_kind" to "legal_inquiry"), answer(), answer("legal_inquiry" to null, "message_kind" to ""))) assertEquals(NonMatterTurn.None, nonMatterTurn(r))
    }

    @Test fun theAskForTheMatterIsNeverTheSameWordsTwiceInARow() {
        assertEquals("person_need_matter", matterAskKey(0).first); assertEquals("ask_matter_short", matterAskKey(1).first); assertEquals("ask_matter_again", matterAskKey(2).first); assertEquals("ask_matter_again", matterAskKey(9).first)
        assertEquals("Please tell me what happened, in your own words.", ASK_MATTER_SHORT_EN)
    }

    @Test fun aChoiceTypedInWordsPicksTheCardAndAQuestionDoesNot() {
        assertEquals("consultation", cardPickFromText("yes, book it")); assertEquals("consultation", cardPickFromText("the full consultation please"))
        assertEquals("written", cardPickFromText("the written one")); assertEquals("written", cardPickFromText("just an enquiry"))
        assertEquals("", cardPickFromText("Who will see this?")); assertEquals("", cardPickFromText("how much is it, and when will someone call me, and can I pay later or by card, and what if it is late?"))
    }

    @Test fun theChecksCarryTheWebsDecisionsFlags() {
        val vm = src("DeskViewModel.kt")
        assertTrue(vm.contains("repo.check(text, langField(convLang), insist = insisting(), consultWish = consultWish,"))
        assertTrue(vm.contains("private fun insisting() = \"person\" in said || directTalk != null || matterAsks > 0 || cardsSeen"))
        assertTrue(src("DeskData.kt").contains("deskV = 2, insist = ins, consultWish = cw"))
        // under the cards a typed line is read with both flags
        assertTrue(vm.contains("repo.check(v, langField(convLang), insist = true, consultWish = true)"))
    }

    @Test fun theVerdictFollowsTheWebsOrder() {
        val vm = src("DeskViewModel.kt")
        val v = vm.substringAfter("private suspend fun verdict(reading: DeskReading?) {").substringBefore("var v = r?.verdict")
        val order = listOf("reading.route?.route == \"consultation\"", "nonMatterTurn(reading)", "if (reading?.insist != null) {", "if (reading?.needMatter == true) {").map { v.indexOf(it) }
        assertTrue(order.all { it >= 0 } && order == order.sorted())
        assertTrue(v.contains("NonMatterTurn.AskMatter -> { keep(reading); said += \"person\"; backToQuestion(nextMatterAsk(), true); return }"))
        assertTrue(v.contains("NonMatterTurn.Answer -> { keep(reading); backToQuestion(reading.reply, false); return }"))
    }

    @Test fun theCardsBookTheConsultationFirstAndNotNowRests() {
        val vm = src("DeskViewModel.kt")
        assertTrue(vm.contains("if (key == \"consultation\") { directBook = true; sayKey(\"ask_matter_optional\", ASK_MATTER_OPTIONAL_EN); dock(Dock.Matter) }"))
        assertTrue(vm.contains("else { directBook = false; text = \"\"; vetted = null; say(Para.Text(tl(\"ask_matter_short\") ?: ASK_MATTER_SHORT_EN)); next() }"))
        // the booking goes straight on: no reading, no closing turn, no gathering
        val fin = vm.substringAfter("private suspend fun finishInsist").substringBefore("/** Something typed under the cards")
        assertTrue(fin.contains("vetted = \"ok\"; closeDone = true; docs = true; more = null; gatherDone = true; gatherRan = true"))
        assertTrue(fin.contains("(I would like to speak to an advocate.)"))
        assertTrue(vm.contains("fun cardsNotNow() = go {") && vm.contains("tl(\"insist_declined\") ?: INSIST_DECLINED_EN"))
        val screen = src("DeskScreen.kt")
        val ui = screen.substringAfter("private fun CardsDock(").substringBefore("/** After the consultation is chosen")
        assertTrue(src("DeskScreen.kt").contains("val main = c.key == \"consultation\"") && ui.contains("Compose(dt(\"ph_ask_question\"") && ui.contains("Quiet(dt(\"ui_not_now\", \"Not Now\"), onNotNow)"))
        assertTrue(screen.contains("is Dock.Cards -> CardsDock(ui.error, vm::cardText, vm::cardsNotNow)"))
    }

    @Test fun theClosesChoiceIsTheSameCards() {
        assertTrue(src("DeskViewModel.kt").contains("r.cards.isNotEmpty() -> dock(Dock.Cards(ChoiceBlock(r.say, r.cards), \"close\"))"))
        val close = parseCloseTurn(mapOf("success" to true, "fee_display" to "₹99", "fee_paise" to 9900.0, "source" to "fixed", "say" to "You asked to speak to someone; you can choose.", "choice" to cards))!!
        assertEquals("A talk with an advocate about your matter. Offered to our counsel straight away.", close.cards[0].includes)
    }
}

/** REQ-029 (b) follow-up: desk/close with a note (booking_answer, note_rest, urgent), and the footers served as desk_lines foot_*. */
class DeskBookingNoteAndFootersTest {
    private fun src(name: String) = java.io.File("src/main/java/com/legumlex/client/feature/desk/$name").readText()

    @Test fun theNoteIsAnsweredFromTheCatalogueAndTheRestKept() {
        // cases_desk_close, chose=consultation + note
        val t = parseCloseTurn(mapOf("success" to true, "fee_display" to "₹99", "fee_paise" to 9900.0, "say" to "I will book the full consultation, ₹499.", "source" to "fixed", "booked" to true,
            "booking_answer" to "A full consultation is ₹499, paid when you book it.", "note_rest" to "Evenings suit me.", "urgent" to true))!!
        assertTrue(t.booked); assertEquals("A full consultation is ₹499, paid when you book it.", t.bookingAnswer); assertEquals("Evenings suit me.", t.noteRest); assertTrue(t.urgent)
        val plain = parseCloseTurn(mapOf("success" to true, "say" to "I will book the full consultation, ₹499.", "booked" to true, "booking_answer" to "", "note_rest" to "", "urgent" to false))!!
        assertEquals("", plain.bookingAnswer); assertFalse(plain.urgent)
        val vm = src("DeskViewModel.kt")
        val fin = vm.substringAfter("private suspend fun finishInsist").substringBefore("/** Something typed under the cards")
        assertTrue(fin.contains("chose = \"consultation\", person = true, note = extraTyped") && fin.contains("extra = r.noteRest") && fin.contains("sayKey(\"urgent_marked\""))
        assertTrue(src("DeskData.kt").contains("api.leadDeskClose(text, lang, chose, p, note) else api.deskClose(text, lang, chose, p, note)"))
    }

    @Test fun theFootersAreTheServersWordsWithTheConstantsAsTheFallback() {
        val book = parseLineBook(mapOf("lang" to "en", "lines" to mapOf(
            "foot_fee_paid" to "Submitting an enquiry costs {fee}, paid once it is written. Nothing is charged before then.", "foot_fee_free" to "Submitting an enquiry is free.",
            "foot_readers_advocate" to "The advocates read every enquiry, and we {reply}.", "foot_reply" to "aim to reply within 30 minutes once an advocate accepts it",
            "foot_direct" to "A full consultation costs {fee2}, paid when you book it.", "foot_direct_contact" to "The advocate who takes it, or their office, will contact you to fix a time.")))!!
        val priced = DeskTerms(basePrice = 9900)
        assertEquals("Submitting an enquiry costs ${priced.displayPrice}, paid once it is written. Nothing is charged before then.", disclaimerLead(priced, false, "", book))
        assertEquals(" The advocates read every enquiry, and we aim to reply within 30 minutes once an advocate accepts it.", disclaimerTail(false, book))
        assertEquals("A full consultation costs ₹499, paid when you book it.", disclaimerLead(priced, true, "₹499", book))
        assertEquals(" The advocate who takes it, or their office, will contact you to fix a time.", disclaimerTail(true, book))
        // no served lines: the same words from the constants
        assertEquals(disclaimerLead(priced, true, "₹499", book), disclaimerLead(priced, true, "₹499"))
        assertEquals(disclaimerTail(false, book), disclaimerTail(false))
        assertTrue(src("DeskScreen.kt").contains("disclaimerLead(t, direct, fee, book)") && src("DeskScreen.kt").contains("disclaimerTail(direct, book)"))
    }
}

/** REQ-029 (b) phone faults: the box empties on a successful send; tall docks never push the box and Send out of reach. */
class DeskInputFaultsTest {
    private fun src(name: String) = java.io.File("src/main/java/com/legumlex/client/feature/desk/$name").readText()

    @Test fun theFirstBoxEmptiesWhenTheMessageHasGoneAndKeepsItsTextWhenTheSendIsRefused() {
        val vm = src("DeskViewModel.kt")
        val submit = vm.substringAfter("fun submitQuestion(raw: String) {").substringBefore("// ---- Reading the question")
        // validation first (too short, mashed: return with the text kept), the clear only inside the sent branch
        assertTrue(submit.indexOf("return") in 0 until submit.indexOf("draftClearTick = it.draftClearTick + 1"))
        assertTrue(submit.contains("go {\n            draftText = \"\"\n            _ui.update { it.copy(opened = true, draftClearTick = it.draftClearTick + 1) }"))
        val screen = src("DeskScreen.kt")
        assertTrue(screen.contains("LaunchedEffect(ui.draftClearTick) { if (ui.draftClearTick > 0) draft = TextFieldValue(\"\") }"))
        assertEquals(0, DeskUi().draftClearTick)
    }

    @Test fun theCardsScrollWithTheConversationAndOnlyTheBoxSitsInTheDock() {
        val screen = src("DeskScreen.kt")
        assertTrue(screen.contains("if (cards != null) item(key = \"cards\") { CardsList(cards, onPick) }"))
        assertTrue(screen.contains("Conversation(ui, vm::change, vm::acctChip, Modifier.weight(1f), onPick = vm::pickCard)"))
        val dock = screen.substringAfter("private fun CardsDock(").substringBefore("/** After the consultation is chosen")
        assertTrue(dock.contains("Compose(") && dock.contains("Quiet(") && !dock.contains("block.cards"))      // the dock is the box and Not Now: short
        assertTrue(screen.contains("is Dock.Cards -> CardsDock(ui.error, vm::cardText, vm::cardsNotNow)"))
    }

    @Test fun noDockCanTakeMoreThanSixtyPercentOfTheRoomAndTheKeyboardIsHandled() {
        val screen = src("DeskScreen.kt")
        assertTrue(screen.contains("val dockMax = maxHeight * 0.6f") && screen.contains("Box(Modifier.heightIn(max = dockMax).then(if (tickList) Modifier else Modifier.verticalScroll(rememberScrollState()))"))
        assertTrue(screen.contains("Column(Modifier.fillMaxSize().background(D.Page).imePadding()) {"))   // the room shrinks with the keyboard
        val manifest = java.io.File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:windowSoftInputMode=\"adjustResize\""))
    }
}

/** REQ-029 F-032 / F-018: the send stepper has three steps, and a booking held for its fee shows the held state, not Delivered. */
class DeskHeldStateTest {
    private fun src(name: String) = java.io.File("src/main/java/com/legumlex/client/feature/desk/$name").readText()

    @Test fun theStepperIsThreeStepsAndAddedToYourFileIsGone() {
        assertEquals(listOf("stage_1", "stage_2", "stage_4"), STAGE_RUNNING_KEYS)
        assertEquals(listOf("stage_done_1", "stage_done_2", "stage_done_4"), STAGE_DONE_KEYS)
        assertFalse(STAGE_RUNNING_KEYS.contains("stage_3") || STAGE_DONE_KEYS.contains("stage_done_3"))
        val screen = src("DeskScreen.kt")
        assertTrue(screen.contains("private val STAGES = listOf(\"Securing your enquiry\", \"Sending it to the chamber\", \"Letting the advocates know\")"))
        assertFalse(screen.contains("Adding it to your file"))
    }

    @Test fun aBookingWaitingForItsFeeIsHeldFromEitherField() {
        val held = parseDirectBooking(mapOf("consultation_id" to 9.0, "state" to "awaiting_payment", "fee_display" to "₹499", "fee_paise" to 49900.0, "pay_url" to "https://example.org/pay", "paid" to false))
        assertTrue(isHeldBooking("awaiting_payment", null)); assertTrue(isHeldBooking(null, held)); assertTrue(isHeldBooking("awaiting_payment", held))
        assertFalse(isHeldBooking("received", null)); assertFalse(isHeldBooking(null, null)); assertFalse(isHeldBooking("acknowledged", held?.copy(state = "offered")))
        // desk/enquiry for a booked consultation (cases helper): require_payment false, consultation_id, direct{}, intake_state awaiting_payment, enquiry_number null
        val sent = deliveredFrom(mapOf("success" to true, "require_payment" to false, "consultation_id" to 9.0, "intake_state" to "awaiting_payment", "enquiry_number" to null,
            "direct" to mapOf("consultation_id" to 9.0, "state" to "awaiting_payment", "fee_display" to "₹499", "pay_url" to "https://example.org/pay")), "a@b.c")
        assertTrue(isHeldBooking(sent.intakeState, sent.direct)); assertEquals("₹499", sent.direct!!.feeDisplay)
    }

    @Test fun theHeldStateReplacesTheDeliveredTicksAndHasNoSeal() {
        val vm = src("DeskViewModel.kt")
        assertTrue(vm.contains("if (isHeldBooking(r.intakeState, r.direct)) holdStages(stagesId, r.direct?.feeDisplay.orEmpty()) else setStages(stagesId, STAGE_AT_MS.size, true)"))
        val hold = vm.substringAfter("private fun holdStages").substringBefore("private fun dropLine")
        assertTrue(hold.contains("tl(\"stage_held_title\") ?: STAGE_HELD_TITLE_EN") && hold.contains("tl(\"stage_held_1\")") && hold.contains("tl(\"stage_held_2\", mapOf(\"fee\" to fee))") && hold.contains("held = true"))
        assertEquals("Booked. Waiting for the fee.", STAGE_HELD_TITLE_EN); assertEquals("Your details are saved", STAGE_HELD_1_EN); assertEquals("Pay ₹499 to send it to our counsel", STAGE_HELD_2_EN.replace("{fee}", "₹499"))
        val booked = vm.substringAfter("private suspend fun deliveredBooked").substringBefore("/** The web's pay step")
        assertFalse(booked.contains("Para.Seal"))                        // the booked step draws no seal
        // the first step is ticked, the second is a plain ring: not running, not ticked
        val screen = src("DeskScreen.kt")
        assertTrue(screen.contains("val active = !p.done && i == p.active && !p.held"))
        // the pay prompt follows with Pay Now as the main button (the web's directBooked)
        assertTrue(vm.contains("Action(\"direct_pay\", payBtn, main = true)"))
    }
}

/** REQ-029 (c): service routes (REQ-026) and the signed-out box. Shapes as desk_api_parity.php's C03, A02, A03 and the route follow-up. */
class DeskRoutesTest {
    private fun src(name: String) = java.io.File("src/main/java/com/legumlex/client/feature/desk/$name").readText()
    private fun answer(vararg extra: Pair<String, Any?>) = parseReading(mapOf("success" to true, "verdict" to "clear", "reading" to "", "reading_state" to "skipped", "lang" to "en") + extra)!!

    @Test fun anAgreementReviewIsRoutedToTheAgreementHealthCheckWithItsButton() {      // C03
        val r = answer("route" to "ahc", "route_line" to "Yes. Our Agreement Health Check is the service for reviewing an agreement before you sign. You mentioned that you are due to sign tomorrow. I can't confirm a review by tomorrow from here.",
            "route_when" to "tomorrow", "route_button" to "Open Agreement Health Check", "message_kind" to "service_question", "nonmatter" to false)
        assertEquals("ahc", r.route!!.route); assertEquals("Open Agreement Health Check", r.route!!.button); assertEquals("tomorrow", r.route!!.whenText)
    }

    @Test fun aSignedAgreementCopyGoesToMyAgreementsInThePortal() {      // A02
        val r = answer("route" to "portal_docs", "legal_issue" to "no", "message_kind" to "account_support", "route_line" to "You can find your signed agreements in the portal, once you sign in.",
            "route_button" to "Sign In To The Portal", "route_line_in" to "Your signed agreements are in My Agreements.", "route_button_in" to "Open My Agreements", "route_href_in" to "/mylex/agreements")
        assertEquals("portal_docs", r.route!!.route); assertEquals("Open My Agreements", r.route!!.buttonIn); assertEquals("Your signed agreements are in My Agreements.", r.route!!.lineIn)
    }

    @Test fun helpWithAnAgreementIsOneClarifyingQuestionWithFreeText() {      // A03
        val r = answer("route" to "clarify", "route_line" to "What would you like help with: reviewing the document, finding a copy, or something that has happened under it?")
        assertEquals("clarify", r.route!!.route); assertTrue(r.route!!.line.startsWith("What would you like help with"))
    }

    @Test fun aRealMatterHasNoRouteAndAnEmptyRouteIsNone() {
        assertNull(answer("route" to "intake", "route_line" to "").route); assertNull(answer().route); assertNull(answer("route" to "", "route_line" to "").route)
    }

    @Test fun theVerdictTakesTheRouteFirstAndTheFollowUpKeepsIt() {
        val vm = src("DeskViewModel.kt")
        val v = vm.substringAfter("private suspend fun verdict(reading: DeskReading?) {").substringBefore("var v = r?.verdict")
        assertTrue(v.indexOf("routeOffer(reading.route, fromCheck = true)") in 0 until v.indexOf("nonMatterTurn(reading)"))
        // while the AHC is on offer, what they write goes with route_ctx=ahc and the last line, so a question about the service is answered and the route kept
        assertTrue(vm.contains("routeCtx = routeOffered.takeIf { it == \"ahc\" }, routeLast = routeLast.takeIf { routeOffered == \"ahc\" }"))
        assertTrue(src("DeskData.kt").contains("api.leadDeskCheck(text, conversation, lang, 2, ins, cw, routeCtx, routeLast)"))
        val offer = vm.substringAfter("private suspend fun routeOffer").substringBefore("/** How many times the matter")
        assertTrue(offer.contains("if (route.route == \"ahc\") { dock(Dock.Route(route)); return }"))      // the box stays open with the button above it
        assertTrue(offer.contains("Action(\"route_portal\"") && offer.contains("Action(\"route_else\""))     // the portal route: its button and Ask Something Else
        assertTrue(offer.contains("backToQuestion(route.line, false)"))                                      // clarify: the line, the box
    }

    @Test fun theButtonsOpenTheAppsOwnScreensAndSendNothing() {
        val vm = src("DeskViewModel.kt")
        assertTrue(vm.contains("\"route_ahc\" -> go { me(routeButtonLabel()); _events.tryEmit(DeskEvent.OpenAgreementCheck) }"))
        assertTrue(vm.contains("_events.tryEmit(DeskEvent.OpenAgreements)"))
        val screen = src("DeskScreen.kt")
        assertTrue(screen.contains("DeskEvent.OpenAgreementCheck -> onAgreement()") && screen.contains("DeskEvent.OpenAgreements -> onAgreements()") && screen.contains("is Dock.Route -> RouteDock("))
        val nav = java.io.File("src/main/java/com/legumlex/client/ui/navigation/NavGraph.kt").readText()
        assertTrue(nav.contains("onAgreements = { navController.navigate(Route.Registry.path) }") && nav.contains("onAgreement = { navController.navigate(if (lead()) Route.LeadAhc.path else Route.AhcFunnel.path) }"))
        assertFalse(src("DeskViewModel.kt").substringAfter("\"route_ahc\" ->").substringBefore("\"route_portal\"").contains("repo."))   // opening the form creates no enquiry
    }

    @Test fun theResumeRouteUsesTheSameOffer() {
        assertTrue(src("DeskViewModel.kt").contains("r.kind == \"route\" && r.route != null -> { routeOffer(r.route, fromCheck = false, prompt = Step.QUESTION); return true }"))
    }

    @Test fun theSignedOutBoxUsesDeskV2AndTheTokenFreeLines() {
        val data = src("DeskData.kt")
        assertTrue(data.contains("api.guestDesk(json, device, 2)") && data.contains("if (guest) api.guestDeskLines(lang)"))
        val api = java.io.File("src/main/java/com/legumlex/client/data/api/ApiService.kt").readText()
        assertTrue(api.contains("@GET(\"guest/desk/lines\")") && api.contains("@Field(\"desk_v\") deskV: Int? = null): Map<String, Any?>"))
        val vm = src("DeskViewModel.kt")
        assertTrue(vm.contains("repo.lines(\"en\", guest) ?: return@launch") && vm.contains("repo.lines(lang, guest)"))
        // a guest route answer is {success, reply (the route line), sign_in false, route…}: the reply is shown as before
        val g = parseGuestReply(mapOf("success" to true, "reply" to "Yes. Our Agreement Health Check is the service for reviewing an agreement before you sign.", "sign_in" to false, "left" to 1.0, "lang" to "en", "route" to "ahc", "route_line" to "Yes.", "route_button" to "Open Agreement Health Check"))!!
        assertFalse(g.signIn); assertTrue(g.reply.startsWith("Yes. Our Agreement"))
    }
}

/** REQ-029 (d): focus questions (desk/gather mode=focus). Shapes: cases_desk_focus() and desk_api_parity.php's "unclear goal" and "a stated goal" cases. */
class DeskFocusTest {
    private fun src(name: String) = java.io.File("src/main/java/com/legumlex/client/feature/desk/$name").readText()

    private val unclear = mapOf("success" to true, "reflect" to "I've noted the Rs 50,000 deposit and the two months of waiting.", "question" to "What would you most like help with?", "topic" to "focus",
        "options" to listOf("Getting the deposit back", "A legal notice to the landlord", "Knowing what the agreement allows him to keep", "I'm not sure; I'd like the advocate to advise me"),
        "options_hint" to "Tick any that fit, or write your own.", "changes" to "first_response", "safety" to false, "danger" to false, "emergency" to "", "done" to false, "ran" to true, "reread" to false, "lang" to "en")

    @Test fun anUnclearGoalHasReflectOneQuestionAndTheOptionsWithNotSureLast() {
        val r = parseGather(unclear)!!
        assertTrue(r.more && r.hasOptions); assertEquals("What would you most like help with?", r.question)
        assertEquals("I've noted the Rs 50,000 deposit and the two months of waiting.", r.reflect)
        assertEquals(4, r.options.size); assertTrue(r.options.last().startsWith("I'm not sure")); assertEquals("Tick any that fit, or write your own.", r.optionsHint); assertFalse(r.reread)
    }

    @Test fun aStatedGoalHasNoOptionsAndTheQuestionsMayBeDone() {
        val r = parseGather(mapOf("success" to true, "reflect" to "", "question" to "", "topic" to "focus", "options" to emptyList<String>(), "done" to true, "ran" to true, "reread" to true))!!
        assertFalse(r.more); assertFalse(r.hasOptions); assertTrue(r.reread)
        assertTrue(parseGather(mapOf("success" to true, "question" to "Who sent the notice?", "done" to false))!!.reread)         // an older server: as before
    }

    @Test fun dangerPausesWithTheEmergencyLineAlone() {
        val r = parseGather(mapOf("success" to true, "reflect" to "", "question" to "", "topic" to "focus", "options" to emptyList<String>(), "changes" to "safety", "safety" to true, "danger" to true,
            "emergency" to "Please call 112 now. Women can also call the women's helpline, 181. Once you are safe, we can carry on.", "done" to true, "ran" to true))!!
        assertTrue(r.emergency.startsWith("Please call 112 now")); assertFalse(r.more)
        val vm = src("DeskViewModel.kt")
        assertTrue(vm.contains("if (r != null && r.emergency.isNotBlank()) { dangerPause(r.emergency); return }"))
        assertTrue(vm.contains("\"safe_now\" -> go { me(lbl(\"ui_safe_carry_on\", SAFE_CARRY_ON_EN)); dock(Dock.Waiting(\"…\")); finishGather() }"))
        assertTrue(vm.contains("if (danger) return next()"))                                        // no close for someone who may be in danger
        assertTrue(vm.contains("!directAsked && !danger"))                                          // no booking pushed during danger
        assertEquals("I'm Safe Now, Carry On", SAFE_CARRY_ON_EN)
    }

    @Test fun notSureStandsAloneAndAnyOtherKindClearsIt() {
        val n = 4; val unsure = 3
        assertEquals(setOf(0), toggleOption(emptySet(), 0, n)); assertEquals(setOf(0, 2), toggleOption(setOf(0), 2, n))
        assertEquals(setOf(3), toggleOption(setOf(0, 2), unsure, n))                    // not sure clears the rest
        assertEquals(setOf(1), toggleOption(setOf(3), 1, n))                            // another kind clears not sure
        assertEquals(setOf(2), toggleOption(setOf(0, 2), 0, n))                         // ticking again unticks
    }

    @Test fun theAnswerIsTheTickedKindsInOrderThenTheirOwnWords() {
        val o = listOf("Getting the deposit back", "A legal notice to the landlord", "I'm not sure")
        assertEquals("Getting the deposit back; A legal notice to the landlord", optionsAnswer(o, setOf(1, 0), ""))
        assertEquals("Getting the deposit back. And the interest too.", optionsAnswer(o, setOf(0), "  And the interest too. "))
        assertEquals("Only my words", optionsAnswer(o, emptySet(), "Only my words"))
    }

    @Test fun theButtonReadsChooseOneOrMoreThenSendNChoices() {
        assertEquals("Choose One Or More", sendChoicesLabel(0, LineBook.English)); assertEquals("Send 1 Choice", sendChoicesLabel(1, LineBook.English)); assertEquals("Send 3 Choices", sendChoicesLabel(3, LineBook.English))
        val served = parseLineBook(mapOf("lang" to "en", "lines" to mapOf("ui_choose_some" to "Choose One Or More", "ui_send_choice" to "Send 1 Choice", "ui_send_choices" to "Send {n} Choices")))!!
        assertEquals("Send 2 Choices", sendChoicesLabel(2, served))
    }

    @Test fun theLayoutKeepsTheQuestionTheButtonAndTheLinksInReach() {
        val screen = src("DeskScreen.kt")
        val ui = screen.substringAfter("private fun OptionsDock(").substringBefore("\n}\n\n", screen.substringAfter("private fun OptionsDock("))
        assertTrue(ui.contains("Text(d.question") && ui.contains("dt(\"opt_choose\", OPT_CHOOSE_EN)") && ui.contains("Role.Checkbox") && ui.contains("Modifier.weight(1f, fill = false)"))   // only the rows scroll
        assertTrue(ui.contains("sendChoicesLabel(d.picks.size, book)") && ui.contains("clickable(enabled = ready, onClick = onSend)"))
        assertTrue(ui.contains("dt(\"ui_write_own\", \"Write My Own Instead\")") && ui.contains("dt(\"ui_skip_questions\", GATHER_SEND_NOW)") && ui.contains("dt(\"ui_back_choices\", \"Back To The Choices\")"))
        assertTrue(ui.contains("OPT_WITH_CHOICES_EN") && ui.contains("sendReady = d.picks.isNotEmpty()"))                  // the kept ticks are something to send
        assertTrue(screen.contains("val tickList = (ui.dock as? Dock.Options)?.writing == false"))                          // the dock itself does not scroll as a whole
        assertTrue(screen.contains("is Dock.Options -> OptionsDock(d, ui.error, vm::togglePick, vm::sendPicks, vm::writeOwn, vm::backToChoices, vm::sendNow, vm::submitGather)"))
    }

    @Test fun focusIsRequestedAndTheRereadFollowsTheServer() {
        val data = src("DeskData.kt")
        assertTrue(data.contains("api.leadDeskGather(text, asked, lang, docs, conversation, \"focus\", topics) else api.deskGather(text, asked, lang, docs, conversation, \"focus\", topics)"))
        val vm = src("DeskViewModel.kt")
        assertTrue(vm.contains("if (gatherLog.isNotEmpty() && lastReread) {") && vm.contains("if (r != null) lastReread = r.reread"))
        assertTrue(vm.contains("val reflect = if (r != null && r.reflect.isNotBlank() && !reflected) { reflected = true; r.reflect } else \"\""))
        assertTrue(vm.contains("if (r.hasOptions) dock(Dock.Options(r.question, r.options, picks, writing = false))\n        else if (r.input != null) dock(Dock.Input(r.question, r.input))\n        else dock(Dock.Gather)"))
    }
}
