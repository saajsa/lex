package com.legumlex.client.feature.desk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** REQ-020 020.4: the offer, Yes or No, the booking and its payment, the wait (offered, accepted, unclaimed), and older servers. */
class DeskBookedTest {
    private fun src(path: String) = java.io.File("src/main/java/com/legumlex/client/$path").takeIf { it.isFile }?.readText()

    @Test fun theOfferParsesOnlyWhenAvailable() {
        assertEquals(DirectOfferInfo(150000, "₹1,500"), parseDirectOffer(mapOf("available" to true, "fee_paise" to 150000.0, "fee_display" to "₹1,500")))
        assertNull(parseDirectOffer(mapOf("available" to false, "fee_paise" to 150000))); assertNull(parseDirectOffer(null)); assertNull(parseDirectOffer("x"))
        assertEquals("₹500", parseDirectOffer(mapOf("available" to true, "fee_paise" to 50000))!!.feeDisplay)           // no display: built from the paise
        // desk/check (with advocate_informed) and desk/gather ({question:'', done:true, ran:false, direct_offer}) both carry it; older servers do not.
        val g = parseGather(mapOf("success" to true, "question" to "", "done" to true, "ran" to false, "direct_offer" to mapOf("available" to true, "fee_paise" to 50000, "fee_display" to "₹500")))!!
        assertEquals("₹500", g.directOffer?.feeDisplay); assertFalse(g.more)
        assertNull(parseGather(mapOf("success" to true, "question" to "Q?", "done" to false))!!.directOffer)
        assertNull(DeskReading("clear", "", "", "none", "", "unknown", "new_matter", "", emptyList(), "").directOffer)
    }

    @Test fun theBookingParsesWithAndWithoutAPayUrl() {
        val paid = parseDirectBooking(mapOf("consultation_id" to 17.0, "state" to "offered", "fee_paise" to 50000, "fee_display" to "₹500"))!!
        assertEquals(DirectPhase.Offered, directPhase(paid.state)); assertNull(paid.payUrl); assertEquals(17, paid.consultationId)      // already paid: no payment step
        val unpaid = parseDirectBooking(mapOf("consultation_id" to 17, "state" to "awaiting_payment", "invoice_id" to 9, "pay_url" to "https://x/invoice/9/abc", "total" to "500.00"))!!
        assertEquals("https://x/invoice/9/abc", unpaid.payUrl); assertEquals("9", unpaid.invoiceId); assertEquals("500.00", unpaid.total); assertFalse(unpaid.needsParticulars)
        assertTrue(parseDirectBooking(mapOf("state" to "awaiting_payment", "pay_url" to "https://x/lead_pay/h", "needs_particulars" to true))!!.needsParticulars)
        assertNull(parseDirectBooking(null)); assertNull(parseDirectBooking(emptyMap<String, Any?>()))
        // The answer to a sent enquiry carries it; an older server's does not.
        assertEquals("offered", deliveredFrom(mapOf("consultation_id" to 17, "direct" to mapOf("consultation_id" to 17, "state" to "offered")), "a@b.c").direct?.state)
        assertNull(deliveredFrom(mapOf("consultation_id" to 17), "a@b.c").direct)
    }

    @Test fun theRowsPhasesAndLabelsComeFromTheServer() {
        assertEquals(DirectPhase.AwaitingPayment, directPhase("awaiting_payment")); assertEquals(DirectPhase.Accepted, directPhase("accepted"))
        assertEquals(DirectPhase.Unclaimed, directPhase("unclaimed")); assertEquals(DirectPhase.Unknown, directPhase("something_new")); assertEquals(DirectPhase.Unknown, directPhase(null))
        val row = parseDirectRow(mapOf("direct" to true, "direct_state" to "accepted", "direct_label" to "Direct Consultation", "direct_due_by" to "2026-11-13 18:30:00",
            "direct_accepted_by_name" to "Asha Rao", "direct_overdue" to false, "intake_state" to "assigned", "assigned_to" to "Asha Rao"))!!
        assertEquals("Direct Consultation · Due 13 Nov, 18:30", directRowLine(row)); assertEquals("Asha Rao", row.acceptedByName)
        assertEquals("Direct Consultation · Overdue", directRowLine(row.copy(overdue = true)))
        assertEquals("Due 13 Nov, 18:30", directRowLine(row.copy(label = "")))             // unpaid: no label yet
        assertNull(directRowLine(row.copy(label = "", dueBy = "")))
        assertNull(parseDirectRow(mapOf("direct" to false))); assertNull(parseDirectRow(emptyMap())); assertNull(parseDirectRow(null))   // not booked, or an older server
        assertEquals("not a date", dueTimeText("not a date"))
    }

    @Test fun theBookingIsSentOnlyOnYes() {
        assertEquals("1", enquiryFields("t", "individual", "9", null, "", "", null, "k", "", "", "", true).toMap()["direct"])
        assertFalse(enquiryFields("t", "individual", "9", null, "", "", null, "k", "", "").any { it.first == "direct" })
        val vm = src("feature/desk/DeskViewModel.kt") ?: return
        assertTrue(vm.contains("direct = directBook)"))
        assertTrue(vm.contains("\"direct_yes\" -> go { me(tl(\"direct_yes\") ?: DIRECT_YES); directBook = true;"))
        assertTrue(vm.contains("\"direct_no\" -> go { me(tl(\"direct_no\") ?: DIRECT_NO);") && !vm.substringAfter("\"direct_no\" ->").substringBefore("\"direct_pay\"").contains("directBook = true"))
        val data = src("feature/desk/DeskData.kt")!!
        assertTrue(data.contains("api.leadDeskDirect(consultationId, conversation) else api.deskDirect(consultationId, conversation)"))
        assertTrue(src("data/api/ApiService.kt")!!.contains("@POST(\"desk/direct\")") && src("data/api/ApiService.kt")!!.contains("@POST(\"lead/desk/direct\")"))
    }

    @Test fun theOfferIsAStepAskedOncePerConversationBeforeGathering() {
        val vm = src("feature/desk/DeskViewModel.kt") ?: return
        val order = vm.substringAfter("private suspend fun next()").substringBefore("private fun hour()")
        assertTrue(order.indexOf("vetted == null -> vet()") < order.indexOf("pendingOffer != null -> askDirectOffer()") && order.indexOf("pendingOffer != null -> askDirectOffer()") < order.indexOf("!gatherDone -> gather()"))
        assertTrue(vm.contains("if (reading?.directOffer != null && !directAsked) pendingOffer = reading.directOffer"))
        assertTrue(vm.contains("if (r?.directOffer != null && !directAsked) pendingOffer = r.directOffer"))
        val ask = vm.substringAfter("private suspend fun askDirectOffer()").substringBefore("private suspend fun deliveredBooked")
        assertTrue(ask.contains("tl(\"direct_offer\", mapOf(\"fee\" to o.feeDisplay))") && ask.contains("\"direct_yes\"") && ask.contains("\"direct_no\"") && ask.contains("directAsked = true"))
        assertEquals("An advocate can speak with you directly for ₹500. Pay now and one will take your enquiry, usually within 30 minutes. Shall I book it?", DIRECT_OFFER_EN.replace("{fee}", "₹500"))
        assertEquals("Yes, Book It", DIRECT_YES); assertEquals("No, Carry On", DIRECT_NO)
    }

    @Test fun payingUsesTheExistingScreensAndAnAlreadyPaidBookingHasNoPaymentStep() {
        val vm = src("feature/desk/DeskViewModel.kt") ?: return
        val booked = vm.substringAfter("private suspend fun deliveredBooked(").substringBefore("private fun watchBooked")
        assertTrue(booked.contains("awaiting && url != null ->"))
        assertTrue(booked.contains("_events.tryEmit(DeskEvent.OpenUrl(url))"))              // the existing OpenUrl: PayActivity / CheckoutActivity (invoice) or the lead's WebView page
        assertTrue(booked.contains("else -> dock(Dock.Advocate(AdvocateWait.Waiting(\"\", bookedRef)))"))     // offered: straight to the waiting card
        // The ordinary enquiry fee, when asked, goes through the existing pay flow and then books.
        assertTrue(vm.contains("delivered(savedEmail, null, bookedAfterPay())"))
        assertTrue(vm.contains("private suspend fun bookedAfterPay(): DirectBooking? {") && vm.contains("if (!directBook) return null"))
        assertTrue(vm.contains("if (directBook && direct != null) { deliveredBooked(direct, enquiryNumber); return }"))
        // Resume: a fresh pay_url from desk/direct, and a look at once on coming back.
        assertTrue(vm.contains("\"direct_pay\" -> go { payBookedAgain() }"))
        assertTrue(vm.contains("if (awaitingBooked) go {"))
    }

    @Test fun theWaitSaysOfferedAcceptedAndUnclaimed() {
        val vm = src("feature/desk/DeskViewModel.kt") ?: return
        val adv = vm.substringAfter("private suspend fun advanceBooked(").substringBefore("private suspend fun deliveredDirect(")
        assertTrue(adv.contains("\"direct_pay_wait\"") && adv.contains("\"direct_due_note\"") && adv.contains("mapOf(\"time\" to dueTimeText(row.dueBy))"))
        assertTrue(adv.contains("\"direct_unclaimed\"") && adv.contains("\"unclaimed\" !in bookedSaid"))        // said once, still booked
        assertTrue(adv.contains("\"direct_accepted\"") && adv.contains("AdvocateWait.HandedOver(acc, bookedRef, OPEN_CONSULTATION)") && adv.contains("return true"))
        assertTrue(vm.contains("if (row != null && flow.withLock { advanceBooked(row) }) break") && vm.contains("delay(DIRECT_POLL_MS)"))
        assertEquals("Payment received. I have asked the advocates to take it; waiting for one to accept.", DIRECT_PAY_WAIT)
        assertEquals("Your enquiry has been sent to the advocate.", DIRECT_ACCEPTED)       // no advocate name in the card line
        assertFalse(adv.contains("mapOf(\"name\" to name)"))
        assertEquals("No advocate has accepted yet. The principal advocate is assigning one now. You remain booked.", DIRECT_UNCLAIMED)
        assertEquals("Due by 13 Nov, 18:30", DIRECT_DUE_NOTE.replace("{time}", "13 Nov, 18:30"))
    }

    @Test fun theConsultationListAndDetailShowTheLabelAndDueTime() {
        val m = src("feature/consultations/ConsultationModels.kt") ?: return
        assertTrue(m.contains("fun Consultation.directLine(): String? = direct?.let { com.legumlex.client.feature.desk.directRowLine(it) }"))
        assertTrue(src("feature/consultations/ConsultationsRepository.kt")!!.contains("direct               = com.legumlex.client.feature.desk.parseDirectRow(m)"))
        assertTrue(src("feature/consultations/ConsultationsScreen.kt")!!.contains("c.directLine() != null -> c.directLine()"))
        assertTrue(src("feature/consultations/detail/ConsultationSpecSheet.kt")!!.contains("c.directLine()?.let { SpecRow(Icons.Outlined.Bolt, \"Booking\", it) }"))
    }

    @Test fun awaitingPaymentWithoutAPayUrlIsNeverASilentWait() {
        val vm = src("feature/desk/DeskViewModel.kt") ?: return
        val booked = vm.substringAfter("private suspend fun deliveredBooked(").substringBefore("private suspend fun payBookedAgain")
        assertTrue(booked.contains("awaiting -> { awaitingBooked = true; dock(Dock.Actions(listOf(Action(\"direct_pay\", payBtn, main = true)))) }"))
        val again = vm.substringAfter("private suspend fun payBookedAgain()").substringBefore("private fun watchBooked")
        assertTrue(again.contains("repo.direct(id)") && again.contains("sayKey(\"err_generic\", \"That did not go through.\"") && again.contains("Action(\"direct_pay\", \"Try Again\", main = true)"))
        assertTrue(again.contains("directPhase(b.state) != DirectPhase.AwaitingPayment"))     // paid meanwhile: on to the wait
        assertTrue(vm.contains("\"direct_pay\" -> go { payBookedAgain() }"))
    }

    @Test fun theSameConversationIdGoesOnEveryDeskCall() {
        val api = src("data/api/ApiService.kt") ?: return
        for (fn in listOf("deskCheck", "leadDeskCheck", "deskGather", "leadDeskGather", "deskDirect", "leadDeskDirect"))
            assertTrue(fn, Regex("suspend fun $fn\\(.*@Field\\(\"conversation\"\\) conversation: String\\? = null").containsMatchIn(api))
        val data = src("feature/desk/DeskData.kt")!!
        assertTrue(data.contains("api.leadDeskCheck(text, conversation, lang)") && data.contains("api.leadDeskGather(text, asked, lang, docs, conversation)"))
        assertTrue(data.contains("deadlineDate, lang, direct, conversation)"))                      // the enquiry submit (direct=1 or not)
        assertEquals("abc", enquiryFields("t", "individual", "9", null, "", "", null, "k", "", "", "", true, "abc").toMap()["conversation"])
        assertFalse(enquiryFields("t", "individual", "9", null, "", "", null, "k", "", "").any { it.first == "conversation" })
    }

    // ---- Addendum: direct_open, one fee through the enquiry payment, the new keys ----

    @Test fun anOpenDirectConsultationParsesAndOlderServersSendNone() {
        val o = parseDirectOpen(mapOf("consultation_id" to 17.0, "state" to "awaiting_payment", "existing" to true, "fee_paise" to 50000, "fee_display" to "₹500", "pay_url" to "https://x/invoice/9/a", "invoice_id" to 9))!!
        assertEquals(DirectOpen(17, "awaiting_payment", 50000, "₹500", "https://x/invoice/9/a", "9"), o)
        assertNull(parseDirectOpen(mapOf("state" to "offered"))); assertNull(parseDirectOpen(null))
        assertNull(parseDirectOpen(mapOf("consultation_id" to 17, "state" to "offered"))!!.payUrl)
        val g = parseGather(mapOf("success" to true, "question" to "", "done" to true, "ran" to false, "direct_open" to mapOf("consultation_id" to 17, "state" to "offered")))!!
        assertEquals(17, g.directOpen?.consultationId); assertNull(g.directOffer)
        assertNull(parseGather(mapOf("success" to true, "question" to "Q?", "done" to false))!!.directOpen)
    }

    @Test fun theBookingCarriesExistingViaAndPaid() {
        val b = parseDirectBooking(mapOf("consultation_id" to 17, "state" to "awaiting_payment", "fee_paise" to 50000, "via" to "enquiry_payment"))!!
        assertEquals("enquiry_payment", b.via); assertFalse(b.existing); assertFalse(b.paid); assertNull(b.payUrl)
        val paid = parseDirectBooking(mapOf("consultation_id" to 17, "state" to "offered", "paid" to true, "pay_url" to ""))!!
        assertTrue(paid.paid); assertNull(paid.payUrl)                                         // an empty pay_url is none
        assertTrue(parseDirectBooking(mapOf("consultation_id" to 17, "state" to "offered", "existing" to true))!!.existing)
        assertNull(parseDirectBooking(mapOf("consultation_id" to 17, "state" to "offered"))!!.via)   // older server
    }

    @Test fun theOpenOneIsToldOnceAndCarriedToItsWait() {
        val vm = src("feature/desk/DeskViewModel.kt") ?: return
        val order = vm.substringAfter("private suspend fun next()").substringBefore("private fun hour()")
        assertTrue(order.contains("pendingOpen != null -> askDirectOpen()"))
        assertTrue(vm.contains("if (reading?.directOpen != null && !directAsked) pendingOpen = reading.directOpen") && vm.contains("if (r?.directOpen != null && !directAsked) pendingOpen = r.directOpen"))
        val open = vm.substringAfter("private suspend fun askDirectOpen()").substringBefore("private fun carryOnToWait")
        assertTrue(open.contains("\"direct_have_pay\"") && open.contains("\"direct_have_wait\"") && open.contains("Action(\"direct_go\"") && open.contains("Action(\"direct_open_pay\""))
        assertTrue(open.contains("DirectPhase.AwaitingPayment"))                                // Pay Now only while awaiting payment
        // Carry On continues THIS conversation as an ordinary enquiry: the next question, an ordinary submit (no direct=1), no jump to the waiting card.
        val go = vm.substringAfter("\"direct_go\" -> go {").substringBefore("\n")
        assertTrue(go.contains("dock(Dock.Waiting(\"…\")); next()") && !go.contains("directBook = true") && !go.contains("watchBooked") && !go.contains("Dock.Advocate"))
        assertFalse(vm.contains("carryOnToWait"))
        // The open booking's id is taken only when Pay Now is chosen (an ordinary submit then sets its own).
        assertFalse(open.contains("consultationId = o.consultationId"))
        assertTrue(vm.substringAfter("\"direct_open_pay\" -> go {").substringBefore("\"direct_go\"").contains("consultationId = o.consultationId"))
        // The same handling when a submit or desk/direct says existing.
        assertTrue(vm.substringAfter("private suspend fun deliveredBooked(").contains("if (b.existing) {"))
        assertEquals("You already have a direct consultation booked. Pay ₹500 to confirm it.", DIRECT_HAVE_PAY.replace("{fee}", "₹500"))
        assertEquals("You already have a direct consultation booked. An advocate will take it shortly.", DIRECT_HAVE_WAIT)
        assertEquals("Carry On", DIRECT_GO); assertEquals("Pay Now", DIRECT_PAY_BTN)
    }

    @Test fun thePricedEnquiryPaysOnceAndAPaidOneHasNoPaymentStep() {
        val vm = src("feature/desk/DeskViewModel.kt") ?: return
        val booked = vm.substringAfter("private suspend fun deliveredBooked(").substringBefore("private fun watchBooked")
        // The ordinary enquiry payment is the booking payment: no second fee page, a quiet wait that the poll turns into "offered".
        assertTrue(booked.contains("awaiting && b.via == \"enquiry_payment\" -> dock(Dock.Advocate(AdvocateWait.Waiting(\"\", bookedRef)))"))
        assertTrue(booked.contains("val awaiting = directPhase(b.state) == DirectPhase.AwaitingPayment && !b.paid"))     // a paid one is not awaiting
        assertTrue(booked.contains("tl(\"direct_pay_prompt\"") && booked.contains("DIRECT_PAY_PROMPT"))
        assertEquals("Your enquiry is in. Pay ₹500 to book the consultation.", DIRECT_PAY_PROMPT.replace("{fee}", "₹500"))
        assertTrue(vm.contains("tl(\"direct_pay_btn\") ?: DIRECT_PAY_BTN"))
    }

    // ---- Phone-run fixes ----

    @Test fun aBookingPaidThroughTheEnquiryPaymentEndsInTheDirectLinesNeverTheTalkOnes() {
        val vm = src("feature/desk/DeskViewModel.kt") ?: return
        val after = vm.substringAfter("private suspend fun bookedAfterPay()").substringBefore("private suspend fun delivered(")
        // The server's booking, else what the submit said, else a quiet booking on the enquiry payment: never null for a Yes, Book It.
        assertTrue(after.contains("repo.direct(it)") && after.contains("savedDirect?.copy(") && after.contains("via = \"enquiry_payment\""))
        assertTrue(vm.contains("savedDirect = r.direct"))
        val delivered = vm.substringAfter("private suspend fun delivered(").substringBefore("private suspend fun askDirectOffer")
        assertTrue(delivered.indexOf("if (directBook && direct != null)") < delivered.indexOf("isDirectTalk(informed, directTalk)"))
        // The saved answer carries the booking.
        val saved = (deliveredFrom(mapOf("consultation_id" to 1), "a@b.c")).direct
        assertNull(saved)
        assertTrue(src("feature/desk/DeskData.kt")!!.contains("Sent.Saved(terms.displayPrice, email, d[\"session_hash\"]?.toString().orEmpty(), parseDirectBooking(d[\"direct\"]))"))
        assertEquals("enquiry_payment", parseDirectBooking(mapOf("consultation_id" to 5, "state" to "awaiting_payment", "via" to "enquiry_payment"))!!.via)
    }

    @Test fun theInvoiceNoteIsNotEnglishInATranslatedConversationNorShownWhenPaidInTheApp() {
        val vm = src("feature/desk/DeskViewModel.kt") ?: return
        val pay = vm.substringAfter("private suspend fun pay()").substringBefore("/** Back from the browser")
        assertTrue(pay.contains("CheckoutActivity.invoiceIn(p.url) == null"))                 // an invoice opens in the app's checkout: nothing said
        assertTrue(pay.contains("tl(\"pay_invoice_note\")") && pay.contains("else if (!book.active) say(Para.Text(PAY_INVOICE_NOTE))"))
        assertFalse(pay.contains("say(Para.Text(\"The fee is an invoice"))                    // no fixed English
        assertEquals("The fee is an invoice from the firm. Once it is paid, your enquiry goes to the advocates.", PAY_INVOICE_NOTE)
    }

    @Test fun theConversationFollowsTheEndOfALineStillBeingRevealedUntilTheVisitorScrollsUp() {
        val s = src("feature/desk/DeskScreen.kt") ?: return
        val conv = s.substringAfter("private fun Conversation(").substringBefore("@Composable\nprivate fun DeskMark")
        assertTrue(conv.contains("snapshotFlow { list.canScrollForward }") && conv.contains("if (!more) stuck = true else if (stuck && lineCount > 0) list.scrollBy(1_000_000f)"))
        assertTrue(conv.contains("source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput && available.y > 0f) stuck = false"))
        assertTrue(conv.contains(".nestedScroll(follow)"))
    }

    @Test fun theWaitingCardAfterABookingShowsAReference() {
        assertEquals("ENQ/2026/22255", bookedReference(" ENQ/2026/22255 ", 17)); assertEquals("#17", bookedReference(null, 17)); assertEquals("#17", bookedReference(" ", 17))
        assertEquals("", bookedReference(null, null)); assertEquals("", bookedReference(null, 0))
        assertEquals("ENQ/2026/22255", parseDirectRow(mapOf("direct" to true, "direct_state" to "offered", "enquiry_number" to "ENQ/2026/22255"))!!.enquiryNumber)
        val vm = src("feature/desk/DeskViewModel.kt") ?: return
        assertTrue(vm.contains("bookedRef = bookedReference(enquiryNumber, consultationId)"))
        // Once the row gives the number it replaces the "#17" placeholder before the card is drawn.
        assertTrue(vm.substringAfter("private suspend fun advanceBooked(").contains("bookedRef = row.enquiryNumber"))
    }
}
