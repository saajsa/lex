package com.legumlex.client.feature.desk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** REQ-018: the enquiry's mobile number is proved with a WhatsApp code when the server says so; nothing changes when it does not. */
class DeskPhoneTest {
    private fun src(path: String) = java.io.File("src/main/java/com/legumlex/client/$path").takeIf { it.isFile }?.readText()

    @Test fun theDeskSaysWhetherVerificationIsRequiredAndOlderServersSayNothing() {
        assertEquals(PhoneVerify(true, false, ""), parsePhoneVerify(mapOf("required" to true, "verified" to false)))
        assertEquals(PhoneVerify(true, true, "+91 98xxx xx210"), parsePhoneVerify(mapOf("required" to true, "verified" to true, "number_masked" to "+91 98xxx xx210")))
        assertEquals(PhoneVerify(), parsePhoneVerify(null))                         // an older server, or the switch off: not required
        assertTrue(needsPhoneVerify(PhoneVerify(true, false), provedHere = false))
        assertFalse(needsPhoneVerify(PhoneVerify(true, true), false))               // verified on record: no step
        assertFalse(needsPhoneVerify(PhoneVerify(true, false), true))               // proved here
        assertFalse(needsPhoneVerify(PhoneVerify(), false))                         // not required
    }

    @Test fun aCodeRequestAnswersWhereItWentAndWhenTheButtonsUnlock() {
        val ok = parsePhoneOk(mapOf("session_hash" to "h", "verified" to false, "to" to "+91 98xxx xx210", "resend_in" to 60.0, "sms_in" to 30, "can_sms" to true)) as PhoneResult.Ok
        assertEquals("h", ok.sessionHash); assertFalse(ok.verified); assertEquals("+91 98xxx xx210", ok.to); assertEquals(60, ok.resendIn); assertEquals(30, ok.smsIn); assertTrue(ok.canSms)
        assertTrue((parsePhoneOk(mapOf("session_hash" to "h", "verified" to true)) as PhoneResult.Ok).verified)    // already verified: no code needed
        assertEquals("123", (parsePhoneOk(mapOf("session_hash" to "h", "verified" to true, "number" to "123")) as PhoneResult.Ok).number)
        assertEquals(PhoneResult.Failed, parsePhoneOk(null))
        assertEquals(60, unlockLeft(60, 0)); assertEquals(25, unlockLeft(60, 35)); assertEquals(0, unlockLeft(60, 90)); assertEquals(0, unlockLeft(30, 30))
    }

    @Test fun refusalsCarryTheCodeTheMessageAndTheWaitAndTheServersWordsAreShown() {
        val r = parsePhoneRefusal(mapOf("code" to "wait", "message" to "A code was sent a moment ago.", "retry_after_minutes" to 1.0, "session_hash" to "h"))
        assertEquals("wait", r.code); assertEquals(1, r.retryAfterMinutes); assertEquals("h", r.sessionHash)
        // English: the server's message. A book with the perr_<code> line: its own.
        assertEquals("A code was sent a moment ago.", phoneErrorText(LineBook.English, r))
        val book = LineBook("hi", mapOf("perr_wait" to "एक मिनट रुकिए।", "perr_generic" to "यह नहीं हुआ।"), null)
        assertEquals("एक मिनट रुकिए।", phoneErrorText(book, r))
        assertEquals("यह नहीं हुआ।", phoneErrorText(book, parsePhoneRefusal(mapOf("code" to "weird", "message" to ""))))      // no line, no message: the generic one
        assertEquals("generic", parsePhoneRefusal(emptyMap()).code)
        for (c in listOf("invalid_number", "busy", "wait", "number_limit", "session_limit", "switched_off", "wrong", "expired", "locked", "used", "session_locked", "nocode", "sms_unavailable"))
            assertEquals(c, parsePhoneRefusal(mapOf("code" to c)).code)
    }

    @Test fun theEnquiryRefusalForAnUnprovedNumberSendsTheVisitorBackToTheNumberStep() {
        assertTrue(isPhoneRefusal("phone_unverified")); assertTrue(isPhoneRefusal("phone_required")); assertFalse(isPhoneRefusal("rate_limited")); assertFalse(isPhoneRefusal(null))
        val vm = src("feature/desk/DeskViewModel.kt") ?: return
        val refused = vm.substringAfter("private suspend fun refused(").substringBefore("val msg = r.message")
        assertTrue(refused.contains("if (isPhoneRefusal(r.reason))") && refused.contains("tl(\"refuse_\" + r.reason)") && refused.contains("phone = null") && refused.contains("return next()"))
        assertEquals("Before I can send this, please confirm your mobile number.", PHONE_CONFIRM_FIRST_EN)
    }

    @Test fun theStepIsInTheFlowAfterTheNumberAndBeforeTheDeadlineWithTheSameSessionOnTheSubmit() {
        val vm = src("feature/desk/DeskViewModel.kt") ?: return
        val order = vm.substringAfter("private suspend fun next()").substringBefore("private fun hour()")
        assertTrue(order.indexOf("phone == null -> askPhone()") < order.indexOf("needsVerifyNow() -> verifyPhone()") && order.indexOf("needsVerifyNow() -> verifyPhone()") < order.indexOf("deadline == null -> askDeadline()"))
        assertTrue(vm.contains("private fun needsVerifyNow() = needsPhoneVerify(t.phoneVerify, phoneProved) && !phone.isNullOrEmpty()"))
        val verify = vm.substringAfter("private suspend fun verifyPhone()").substringBefore("/** Confirm: the six digits.")
        assertTrue(verify.contains("repo.phoneCode(phone.orEmpty(), entity.orEmpty(), sessionHash)") && verify.contains("if (r.sessionHash.isNotBlank()) sessionHash = r.sessionHash"))
        assertTrue(verify.contains("if (r.verified) { phoneProved = true; forcePhone = false; return next() }"))   // already verified: no code step
        assertTrue(verify.contains("tl(\"phone_code_sent\"") && verify.contains("PHONE_CODE_SENT_EN") && verify.contains("Dock.Code("))
        // The enquiry is sent with the session the number was proved on.
        assertTrue(vm.contains("repo.send(fullText(), entity.orEmpty(), phone.orEmpty(), country, countryOther, reading, sessionHash,"))
        assertTrue(vm.contains("phone_confirmed") && vm.contains("PHONE_CONFIRMED_EN"))
    }

    @Test fun theCodeStepHasConfirmResendSmsAndChangeNumber() {
        val vm = src("feature/desk/DeskViewModel.kt") ?: return
        val code = vm.substringAfter("fun submitCode(").substringBefore("/** Send Code Again")
        assertTrue(code.contains("c.length != 6") && code.contains("lbl(\"perr_wrong\"") && code.contains("repo.phoneVerify(c, hash)"))
        assertTrue(vm.contains("fun codeAgain() = go { verifyPhone() }") && vm.contains("repo.phoneSms(hash)"))
        assertTrue(vm.substringAfter("fun changeNumber()").contains("phone = null; phoneProved = false; codeUi = null; forcePhone = true; offered += \"phone\""))
        assertTrue(vm.contains("if (t.known.phone && !forcePhone)"))
        val screen = src("feature/desk/DeskScreen.kt") ?: return
        val dock = screen.substringAfter("private fun CodeDock(").substringBefore("/** A guest")
        for (k in listOf("ph_code", "sr_code", "ui_confirm", "ui_resend", "ui_sms_instead", "ui_change_number")) assertTrue(k, dock.contains("\"$k\""))
        assertTrue(dock.contains("unlockLeft(info.resendIn, elapsed)") && dock.contains("unlockLeft(info.smsIn, elapsed)") && dock.contains("if (info.canSms)"))   // SMS only when the server offers it
        assertTrue(dock.contains("filter { c -> c.isDigit() }.take(6)"))
    }

    @Test fun noNumberOptionWhileVerificationIsRequiredAndTheLeadDeskUsesItsOwnRoutes() {
        val screen = src("feature/desk/DeskScreen.kt") ?: return
        assertTrue(screen.contains("ui.terms.phoneRequired || (ui.terms.phoneVerify.required && !ui.terms.phoneVerify.verified)"))
        val data = src("feature/desk/DeskData.kt")!!
        assertTrue(data.contains("api.leadDeskPhoneCode(number, entityType.ifBlank { null }, sessionHash, langField(lang)) else api.deskPhoneCode("))
        assertTrue(data.contains("api.leadDeskPhoneSms(sessionHash, langField(lang)) else api.deskPhoneSms(") && data.contains("api.leadDeskPhoneVerify(code, sessionHash, langField(lang)) else api.deskPhoneVerify("))
        val api = src("data/api/ApiService.kt")!!
        for (r in listOf("desk/phone_code", "desk/phone_sms", "desk/phone_verify", "lead/desk/phone_code", "lead/desk/phone_sms", "lead/desk/phone_verify")) assertTrue(r, api.contains("@POST(\"$r\")"))
    }

    @Test fun theAccountFlowAndTheLastLabelsCarryTheLanguage() {
        val data = src("feature/desk/DeskData.kt") ?: return
        assertTrue(data.contains("api.leadOnboardingTurn(if (open) 1 else 0, if (counsel) \"counsel\" else null, langField(lang))"))
        assertTrue(data.contains("langField(lang)?.let { put(\"lang\", it) }") && data.contains("MultipartBody.Part.createFormData(\"lang\", it)") && data.contains("langField(lang)?.let { mapOf(\"lang\" to it) }"))
        assertTrue(src("feature/desk/DeskViewModel.kt")!!.contains("repo.lang = langField(lang).orEmpty()"))
        val vm = src("feature/desk/DeskViewModel.kt")!!; val screen = src("feature/desk/DeskScreen.kt")!!
        for (k in listOf("ui_paid", "ui_pay_fee", "ui_yes_number")) assertTrue(k, vm.contains("\"$k\""))
        for (k in listOf("ui_continue_google", "ui_close", "ui_clear")) assertTrue(k, screen.contains("\"$k\""))   // REQ-045: the calendar is inline, so no Pick A Date pill or Choose / Cancel dialog
    }
}
