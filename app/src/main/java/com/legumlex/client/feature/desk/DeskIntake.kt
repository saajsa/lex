package com.legumlex.client.feature.desk

import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeParseException

/*
 * REQ-017 Phase 1 (the desk's intake), the parts with no screen in them: the submission key that makes a retry one enquiry, the deadline step's
 * validation, what a reading may be used for, and the receipt.
 */

/** A small string store (SharedPreferences in the app, a map in the tests). */
interface KeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
}

/**
 * The submission key: made once for an enquiry as composed, and sent on every attempt to send it (a retry after a timeout, a restart, a second tap),
 * so the server can tell a retry from a new enquiry. It is kept with the enquiry's fingerprint: the same enquiry gets the same key for as long as it
 * is kept (a day), a different one gets a new key, and it is cleared once the enquiry is sent or another is begun.
 */
class SubmissionKeys(
    private val store: KeyValueStore,
    private val newKey: () -> String = { java.util.UUID.randomUUID().toString().replace("-", "") },
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    fun forEnquiry(fingerprint: String): String {
        val kept = store.get(KEY); val fp = store.get(FP); val at = store.get(AT)?.toLongOrNull() ?: 0L
        if (!kept.isNullOrEmpty() && fp == fingerprint && now() - at < KEEP_MS) return kept
        val k = newKey()
        store.put(KEY, k); store.put(FP, fingerprint); store.put(AT, now().toString())
        return k
    }

    fun clear() { store.remove(KEY); store.remove(FP); store.remove(AT) }

    companion object {
        const val KEY = "submission_key"; const val FP = "submission_fp"; const val AT = "submission_at"
        const val KEEP_MS = 24L * 60 * 60 * 1000

        /** The enquiry as it will be sent: its words and its files' names and sizes. */
        fun fingerprint(text: String, files: List<Pair<String, Long>>): String {
            val md = MessageDigest.getInstance("SHA-256")
            val bytes = md.digest((text + "\u0000" + files.joinToString("|") { "${it.first}:${it.second}" }).toByteArray())
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}

const val DEADLINE_MAX = 240

/** The words, as the server keeps them: runs of whitespace made one, trimmed. */
fun cleanDeadlineText(raw: String): String = raw.replace(Regex("\\s+"), " ").trim()

/** The empty-answer message of deadlineProblem (the one the book's err_flag translates). */
const val DEADLINE_EMPTY_EN = "Write a few words, or pick a date, or choose No, Nothing Yet."

/** What is wrong with a deadline answer (the web's own rules); "" when nothing is. Both may not be empty; the date, when given, is YYYY-MM-DD between 2000 and 2100. */
fun deadlineProblem(text: String, date: String): String {
    val t = cleanDeadlineText(text)
    if (t.isEmpty() && date.isBlank()) return DEADLINE_EMPTY_EN
    if (t.length > DEADLINE_MAX) return "Please keep this to $DEADLINE_MAX characters."
    if (date.isNotBlank()) {
        val d = try { LocalDate.parse(date.trim()) } catch (_: DateTimeParseException) { null }
        if (d == null || d.year < 2000 || d.year > 2100) return "Please give the date as day, month and year."
    }
    return ""
}

/** The deadline answer as the conversation echoes it: the words, then the date as day.month.year. */
fun deadlineShown(text: String, date: String): String =
    listOfNotNull(cleanDeadlineText(text).takeIf { it.isNotEmpty() }, date.takeIf { it.isNotBlank() }?.split("-")?.reversed()?.joinToString(".")).joinToString(" · ")

/**
 * Whether the desk's reading may be used: the server says reading_state "read". Anything else ("failed", "skipped") is not a finding, so its urgency
 * and area are not shown or used; sending goes on regardless. An older server sends no state: its reading is used as before.
 */
fun deskReadingUsable(state: String?): Boolean = state == null || state == "read"

/** The receipt after sending, as one paragraph (markup): no advocate is named, since none has the enquiry yet. */
fun receiptLine(enquiryNumber: String?, email: String = ""): String {
    val n = enquiryNumber?.trim().orEmpty()
    return (if (n.isNotEmpty()) "**Enquiry $n received.**" else "**Enquiry received.**") +
        " It goes to one of the advocates; once one accepts it, we aim to reply within 30 minutes, to **${email.trim().ifBlank { "your email" }}**."
}

// ---- What is sent with an enquiry ----

/** The text fields of desk/enquiry, in the order sent. enquiry_text is always the client's own words, byte for byte. */
fun enquiryFields(
    text: String, entity: String, phone: String, country: String?, countryOther: String, reading: String, sessionHash: String?,
    submissionKey: String, deadlineFlag: String, deadlineDate: String, lang: String = "", direct: Boolean = false, conversation: String = "",
): List<Pair<String, String>> = buildList {
    add("enquiry_text" to text)
    if (lang.isNotBlank()) add("lang" to lang)
    if (direct) add("direct" to "1")
    if (conversation.isNotBlank()) add("conversation" to conversation)
    add("entity_type" to entity)
    add("phone" to phone)
    if (country != null) {
        add("country" to country)
        if (country == "OTHER") add("country_other" to countryOther)
    }
    if (reading.isNotEmpty()) add("desk_reading" to reading)
    if (!sessionHash.isNullOrEmpty()) add("session_hash" to sessionHash)
    if (submissionKey.isNotEmpty()) add("submission_key" to submissionKey)
    if (deadlineFlag.isNotEmpty()) add("deadline_flag" to deadlineFlag)
    if (deadlineDate.isNotEmpty()) add("deadline_date" to deadlineDate)
}

/** The ask_reasons that make a clear enquiry worth one question. */
val ASK_REASONS = setOf("time_limit_unknown", "forum_unknown", "missing_facts")

// ---- Lex's introduction (REQ-017 017.C): what a guest meets first ----

/** The first line of a guest's conversation, the owner's words. The card before the conversation starts says the same in two parts and gives way to this line. */
const val LEX_INTRO = "Hi, I am Lex, your AI assistant. I will listen, note the details, and pass your enquiry to an advocate. What would you like help with?"
const val LEX_INTRO_HEADING = "Hi, I Am Lex"
const val LEX_INTRO_BODY = "Your AI assistant. I will listen, note the details, and pass your enquiry to an advocate. What would you like help with?"

// ---- Waiting on the desk's AI calls (REQ-017): the server gives each a fixed budget and falls back itself; the app waits long enough and shows progress ----

/** How long the app waits for the reading, the re-read and the guest chat (the server answers within about 14 s). */
const val DESK_READ_WAIT_MS = 20_000L
/** The HTTP read timeouts (seconds) those calls carry, a little over the waits so the wait, not the socket, is what ends them. */
const val DESK_READ_HTTP_S = 22
/** After this long with no reply, the pending line says it is still working. */
const val SLOW_AFTER_MS = 3_000L
const val ONE_MOMENT = "One moment…"

// ---- The desk's layout rules (REQ-017 UI pass) ----

/** The side margin of everything in the conversation and its docks: Lex's lines, the visitor's bubbles, the cards and the buttons. */
const val DESK_SIDE_MARGIN = 16f
/** Between rows of buttons, and above a group of them. */
const val CHIP_GAP = 8f
const val CHIP_GROUP_TOP = 12f

/** Room under the last thing in the conversation and the Review page, so the last line and the last button are never flush with, or under, what sits below them. */
const val DESK_BOTTOM_ROOM = 24f

// ---- Lex's follow-up questions before an enquiry is sent (REQ-017 017.20) ----

/** At most three questions in all, the reading's own follow-up counting as the first. */
const val GATHER_MAX = 3
const val GATHER_SEND_NOW = "Skip The Questions"

/** The desk's next question (desk/gather): "" means nothing more, and [done] says it has no more to ask. */
data class GatherResult(val question: String, val topic: String, val done: Boolean, val ran: Boolean = false, val tongue: DeskTongue = DeskTongue(), val directOffer: DirectOfferInfo? = null, val directOpen: DirectOpen? = null) {
    val more: Boolean get() = !done && question.isNotBlank()
}

/** The answer of desk/gather; null when it could not be asked (a failure never blocks sending). */
fun parseGather(d: Map<String, Any?>?): GatherResult? {
    if (d == null || d["success"] == false) return null
    return GatherResult((d["question"] as? String).orEmpty().trim(), (d["topic"] as? String).orEmpty(), d["done"] == true, d["ran"] == true, parseTongue(d), parseDirectOffer(d["direct_offer"]), parseDirectOpen(d["direct_open"]))
}

/** The separate hearing-or-deadline step is skipped when Lex's gathering ran (any call said ran), and kept when it failed, timed out, or never ran. */
fun skipDeadlineStep(gatherRan: Boolean): Boolean = gatherRan

/** A question and its answer, as appended to the enquiry text. */
fun gatherLabel(question: String, answer: String): String = "\n\nLex asked: $question\nThey answered: $answer"

/** Whether to ask one more: a clarifying question skipped ("as it is") ends the asking, and three in all is the limit. */
fun mayAskMore(readingFollowUpAnswered: Boolean, readingFollowUpSkipped: Boolean, askedSoFar: Int): Boolean =
    !readingFollowUpSkipped && askedSoFar < GATHER_MAX

/** The question after the welcome: one line, no second greeting. */
const val ASK_HELP = "How can I help?"

// ---- The visitor's own answers, as the bubbles say them (the English; /lines carries them as ans_*) ----
const val ANS_AS_IS = "Send it as it is."
const val ANS_DOCS_LATER = "I will send them later."
const val ANS_ATTACHED = "Attached:"
const val ANS_NO_PHONE = "I would rather not give a number."
const val ANS_NO_DEADLINE = "Nothing yet."
const val ANS_NEW_ENQUIRY = "Send it as a new enquiry."
const val CHIP_FLAG_YES = "Yes, There Is One"
const val CHIP_FLAG_NO = "No, Nothing Yet"

// ---- A refused enquiry (desk/enquiry 422): the server's reason, and whether trying again can help ----

/** The additive fields of a refusal: `reason`, `retry_after_minutes` (with rate_limited), and the translated-line markers. Null `reason` from an older server. */
data class Refusal(val message: String, val reason: String?, val retryAfterMinutes: Int, val tongue: DeskTongue)

fun parseRefusal(m: Map<String, Any?>?): Refusal {
    val body = m ?: emptyMap()
    return Refusal(
        message = (body["message"] as? String).orEmpty().trim(),
        reason = (body["reason"] as? String)?.trim()?.takeIf { it.isNotEmpty() },
        retryAfterMinutes = (body["retry_after_minutes"] as? Number)?.toInt() ?: 0,
        tongue = parseTongue(body),
    )
}

/** Whether "Try Again" is offered: not when the hourly limit or an enquiry already sent or awaiting its fee makes a retry pointless. Every other reason, and no reason, keep it. */
fun retryHelps(reason: String?): Boolean = reason !in setOf("rate_limited", "already_sent", "awaiting_payment")

// ---- The message-to-the-advocate animation and the waiting state (REQ-017 017.30, prepared before the server fields) ----

/** How long the "message being sent to the advocate" animation runs, once. */
const val SEND_ANIM_MS = 1_000
/** The animation's track is at most this much of the width, left-aligned under Lex's line. */
const val SEND_TRACK_FRACTION = 0.4f

/** The system's animator scale (Settings > Developer options, or "Remove animations"): 0 means animations are off, so the final state is drawn at once. */
fun animationsOn(animatorScale: Float): Boolean = animatorScale > 0f

/** How far along the hairline the message glyph is at [t] (0..1 over the animation), and whether the advocate's tick shows: only at the very end. */
fun sendProgress(t: Float): Float = t.coerceIn(0f, 1f)
fun sendDone(t: Float): Boolean = t >= 1f

/** The waiting state after a direct-talk enquiry is sent: waiting, then handed over to a named advocate. The words are the server's or the caller's own. */
sealed interface AdvocateWait {
    data class Waiting(val title: String, val line: String) : AdvocateWait
    data class HandedOver(val title: String, val line: String, val button: String) : AdvocateWait
}

// ---- A direct-talk conversation (REQ-017 017.30) ----

/** web's English lines, used when no book is in force (the same words as desk_lines.json). */
const val PERSON_INFORMED = "I have messaged one of the advocates. As soon as they are free, I will hand over. Meanwhile, a few details will save time and let them come prepared."
const val PERSON_FINAL = "The final report has gone to the advocate. Waiting for them now; they or their office will contact you to fix a time."
const val PERSON_HANDOVER = "Your enquiry has been sent to the advocate."
const val PERSON_SENT_CAPTION = "Sent to an advocate"
const val OPEN_CONSULTATION = "Open Consultation"
/** How often the waiting state asks for the consultation's status. */
const val DIRECT_POLL_MS = 20_000L

/** The consultation row's fields that the waiting state reads: where it is in intake, who has it, and whether it was a direct-talk request. */
data class DirectStatus(val intakeState: String?, val assignedTo: String, val acknowledged: Boolean, val directTalk: Boolean?)

fun parseDirectStatus(m: Map<String, Any?>?): DirectStatus? {
    if (m == null) return null
    val state = (m["intake_state"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
    val ackAt = (m["acknowledged_at"] as? String)?.trim().orEmpty()
    return DirectStatus(
        intakeState = state,
        assignedTo = (m["assigned_to"] as? String)?.trim().orEmpty(),
        acknowledged = state == "acknowledged" || ackAt.isNotEmpty(),
        directTalk = if (m.containsKey("direct_talk")) m["direct_talk"] as? Boolean else null,
    )
}

/** Hand over only when the advocate has acknowledged it and the row names them. */
fun handedOver(s: DirectStatus?): Boolean = s != null && s.acknowledged && s.assignedTo.isNotBlank()

/** Whether a conversation is a direct-talk one: the server really informed an advocate, or a guest's request was carried into the enquiry. */
fun isDirectTalk(advocateInformed: Boolean, guestRequest: String?): Boolean = advocateInformed || !guestRequest.isNullOrBlank()

// ---- Fields the follow-up calls carry (REQ-017) ----

/** The conversation's language as a request field: omitted for English and before any reading has named one. */
fun langField(lang: String): String? = lang.takeIf { it in DESK_LANGS }

/** The reading's documents joined with "|" for desk/gather; omitted when there are none. */
fun docsField(documents: List<String>): String? = documents.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("|").ifEmpty { null }

// ---- A booked Direct Consultation (REQ-020 020.4) ----

/** What desk/check or desk/gather offers: an advocate to speak with directly, for a fee. Null when absent or not available. */
data class DirectOfferInfo(val feePaise: Int, val feeDisplay: String)

fun parseDirectOffer(m: Any?): DirectOfferInfo? {
    val o = m as? Map<*, *> ?: return null
    if (o["available"] != true) return null
    val paise = (o["fee_paise"] as? Number)?.toInt() ?: 0
    val shown = (o["fee_display"] as? String)?.trim().orEmpty().ifBlank { if (paise > 0) "₹" + (paise / 100) else "" }
    return DirectOfferInfo(paise, shown)
}

/** The `direct` of desk/enquiry and desk/direct: the booking, its state, and where its fee is paid (no pay_url when it is already paid). */
data class DirectBooking(
    val consultationId: Int?, val state: String, val feePaise: Int, val feeDisplay: String,
    val invoiceId: String?, val payUrl: String?, val total: String?, val needsParticulars: Boolean,
    /** The person already had an open direct consultation (this is it, not a new one). */
    val existing: Boolean = false,
    /** "enquiry_payment": the ordinary enquiry payment is the booking payment (no separate fee page). */
    val via: String? = null,
    /** Already paid (desk/direct on a paid enquiry: state offered, pay_url empty). */
    val paid: Boolean = false,
)

fun parseDirectBooking(m: Any?): DirectBooking? {
    val o = m as? Map<*, *> ?: return null
    val state = (o["state"] as? String)?.trim().orEmpty()
    if (state.isEmpty() && o["consultation_id"] == null) return null
    return DirectBooking(
        consultationId = (o["consultation_id"] as? Number)?.toInt()?.takeIf { it > 0 }, state = state,
        feePaise = (o["fee_paise"] as? Number)?.toInt() ?: 0, feeDisplay = (o["fee_display"] as? String)?.trim().orEmpty(),
        invoiceId = o["invoice_id"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }, payUrl = (o["pay_url"] as? String)?.takeIf { it.isNotBlank() },
        total = o["total"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }, needsParticulars = o["needs_particulars"] == true,
        existing = o["existing"] == true, via = (o["via"] as? String)?.trim()?.takeIf { it.isNotEmpty() }, paid = o["paid"] == true,
    )
}

/** Where a booked consultation is: from the consultation row's direct_state. */
enum class DirectPhase { AwaitingPayment, Offered, Accepted, Unclaimed, Unknown }

fun directPhase(state: String?): DirectPhase = when (state?.trim()) {
    "awaiting_payment" -> DirectPhase.AwaitingPayment
    "offered" -> DirectPhase.Offered
    "accepted" -> DirectPhase.Accepted
    "unclaimed" -> DirectPhase.Unclaimed
    else -> DirectPhase.Unknown
}

/** web's English for the lines of a booked consultation (the same words as desk_lines.json). */
const val DIRECT_OFFER_EN = "A full consultation with our counsel is {fee}. Once you pay, it is offered to our counsel straight away, and the advocate who takes it, or their office, will contact you to fix a time. Shall I book it?"
const val DIRECT_YES = "Yes, Book It"
const val DIRECT_NO = "No, Carry On"
const val DIRECT_PAY_WAIT = "Payment received. I have asked the advocates to take it; waiting for one to accept."
const val DIRECT_ACCEPTED = "Your enquiry has been sent to the advocate."
const val DIRECT_UNCLAIMED = "No advocate has accepted yet. The principal advocate is assigning one now. You remain booked."
const val DIRECT_DUE_NOTE = "Due by {time}"

/** "13 Nov, 18:30" from a server datetime ("Y-m-d H:i:s"); the text as given when it cannot be read. */
fun dueTimeText(raw: String): String {
    val t = runCatching { java.time.LocalDateTime.parse(raw.trim().take(19).replace(' ', 'T')) }.getOrNull() ?: return raw
    return "${t.dayOfMonth} ${t.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)}, %02d:%02d".format(t.hour, t.minute)
}

/** The consultation row's direct fields as the waiting state reads them. */
data class DirectRow(
    val phase: DirectPhase, val label: String, val dueBy: String, val acceptedByName: String, val overdue: Boolean,
    val intakeState: String?, val assignedTo: String,
    /** The enquiry's number on the row (ENQ/2026/…), for the waiting card's reference. */
    val enquiryNumber: String = "",
)

fun parseDirectRow(m: Map<String, Any?>?): DirectRow? {
    if (m == null || m["direct"] != true) return null
    return DirectRow(
        phase = directPhase(m["direct_state"] as? String), label = (m["direct_label"] as? String)?.trim().orEmpty(),
        dueBy = (m["direct_due_by"] as? String)?.trim().orEmpty(), acceptedByName = (m["direct_accepted_by_name"] as? String)?.trim().orEmpty(),
        overdue = m["direct_overdue"] == true, intakeState = (m["intake_state"] as? String)?.trim()?.takeIf { it.isNotEmpty() },
        assignedTo = (m["assigned_to"] as? String)?.trim().orEmpty(), enquiryNumber = (m["enquiry_number"] as? String)?.trim().orEmpty(),
    )
}

/** The label (the server's, once paid) and the due time or Overdue; null when there is nothing to say. */
fun directRowLine(d: DirectRow): String? = buildList {
    d.label.takeIf { it.isNotBlank() }?.let { add(it) }
    if (d.overdue) add("Overdue") else if (d.dueBy.isNotBlank()) add("Due " + dueTimeText(d.dueBy))
}.joinToString(" · ").ifBlank { null }

/** desk/check or desk/gather's `direct_open` (instead of direct_offer): the person already has an open direct consultation. */
data class DirectOpen(val consultationId: Int, val state: String, val feePaise: Int, val feeDisplay: String, val payUrl: String?, val invoiceId: String?)

fun parseDirectOpen(m: Any?): DirectOpen? {
    val o = m as? Map<*, *> ?: return null
    val id = (o["consultation_id"] as? Number)?.toInt()?.takeIf { it > 0 } ?: return null
    return DirectOpen(id, (o["state"] as? String)?.trim().orEmpty(), (o["fee_paise"] as? Number)?.toInt() ?: 0, (o["fee_display"] as? String)?.trim().orEmpty(),
        (o["pay_url"] as? String)?.takeIf { it.isNotBlank() }, o["invoice_id"]?.toString()?.takeIf { it.isNotBlank() && it != "null" })
}

const val DIRECT_PAY_PROMPT = "Your enquiry is in. Pay {fee} to book the consultation."
const val DIRECT_PAY_BTN = "Pay Now"
const val DIRECT_HAVE_PAY = "You already have a direct consultation booked. Pay {fee} to confirm it."
const val DIRECT_HAVE_WAIT = "You already have a direct consultation booked. An advocate will take it shortly."
const val DIRECT_GO = "Carry On"

/** The note before a fee is paid on a page outside the app's checkout (a lead's fee page); an invoice paid in the app says nothing. */
const val PAY_INVOICE_NOTE = "The fee is an invoice from the firm. Once it is paid, your enquiry goes to the advocates."

/** The reference under the waiting card: the enquiry's number, else the consultation number ("#17"), else nothing. */
fun bookedReference(enquiryNumber: String?, consultationId: Int?): String =
    enquiryNumber?.trim().orEmpty().ifBlank { consultationId?.takeIf { it > 0 }?.let { "#$it" }.orEmpty() }

// ---- A verified mobile number (REQ-018) ----

/** GET desk phone_verify: {required, verified, number_masked}. */
data class PhoneVerify(val required: Boolean = false, val verified: Boolean = false, val numberMasked: String = "")

fun parsePhoneVerify(m: Any?): PhoneVerify {
    val o = m as? Map<*, *> ?: return PhoneVerify()
    return PhoneVerify(o["required"] == true, o["verified"] == true, (o["number_masked"] as? String).orEmpty())
}

/** The number step needs proving: required, none on record verified, and not proved in this conversation. */
fun needsPhoneVerify(v: PhoneVerify, provedHere: Boolean): Boolean = v.required && !v.verified && !provedHere

/** What a code request, an SMS request or a check answered. */
sealed interface PhoneResult {
    /** 200: the session the code is bound to; for a request also where it went and when the buttons unlock. */
    data class Ok(val sessionHash: String, val verified: Boolean, val to: String, val resendIn: Int, val smsIn: Int, val canSms: Boolean, val number: String) : PhoneResult
    /** 422: a code (invalid_number, busy, wait, number_limit, session_limit, switched_off, wrong, expired, locked, used, session_locked, nocode, sms_unavailable) and the server's message. */
    data class Refused(val code: String, val message: String, val retryAfterMinutes: Int, val sessionHash: String?) : PhoneResult
    data object Failed : PhoneResult
}

fun parsePhoneOk(d: Map<String, Any?>?): PhoneResult {
    if (d == null) return PhoneResult.Failed
    return PhoneResult.Ok(
        sessionHash = (d["session_hash"] as? String).orEmpty(), verified = d["verified"] == true, to = (d["to"] as? String).orEmpty(),
        resendIn = ((d["resend_in"] as? Number)?.toInt() ?: 60).coerceAtLeast(0), smsIn = ((d["sms_in"] as? Number)?.toInt() ?: 30).coerceAtLeast(0),
        canSms = d["can_sms"] == true, number = (d["number"] as? String).orEmpty(),
    )
}

fun parsePhoneRefusal(b: Map<String, Any?>): PhoneResult.Refused = PhoneResult.Refused(
    code = (b["code"] as? String)?.trim().orEmpty().ifEmpty { "generic" }, message = (b["message"] as? String)?.trim().orEmpty(),
    retryAfterMinutes = (b["retry_after_minutes"] as? Number)?.toInt() ?: 0, sessionHash = (b["session_hash"] as? String)?.takeIf { it.isNotBlank() },
)

/** The book's own line for a refusal code (perr_<code>) when it has one; else the server's message; else the generic line. */
fun phoneErrorText(book: LineBook, r: PhoneResult.Refused): String =
    book.fragment("perr_" + r.code) ?: r.message.ifBlank { book.fragment("perr_generic") ?: "That did not work. Please try again." }

/** Seconds left before a button unlocks, from when the code was sent. */
fun unlockLeft(unlockIn: Int, elapsedSeconds: Int): Int = (unlockIn - elapsedSeconds).coerceAtLeast(0)

/** desk/enquiry refused because the number is not proved: the visitor is sent back to the number step and the enquiry is sent again once it is. */
fun isPhoneRefusal(reason: String?): Boolean = reason == "phone_unverified" || reason == "phone_required"

const val PHONE_CODE_SENT_EN = "I have sent a 6-digit code to {to} on WhatsApp. Please type it here to confirm your number."
const val PHONE_CONFIRMED_EN = "Thank you, your number is confirmed."
const val PHONE_CONFIRM_FIRST_EN = "Before I can send this, please confirm your mobile number."
const val PHONE_REQUIRED_EN = "Please give a mobile number the advocates can reach you on."

/** The code step's dock state: where the code went, when its buttons unlock, and when it was sent (for the countdowns). */
data class PhoneCodeUi(val to: String, val resendIn: Int, val smsIn: Int, val canSms: Boolean, val sentAtMs: Long)

// ---- Lex's closing turn (REQ-023) ----

/** One of the close's two cards (`choice.cards`): which product (key consultation|written), and the button's own words. */
data class CloseCard(val key: String, val button: String, val title: String, val price: String, val secondary: Boolean)

/**
 * What Lex says before the enquiry is sent, and where it came from. Beside `say`: `declined` (no ask), `booked` (the full consultation was chosen: one line,
 * nothing more), `choice` (the two cards, the consultation first) and `refused` ("no_matter": the text holds no matter; `say` asks for it).
 */
data class CloseTurn(
    val say: String, val source: String, val feeDisplay: String, val feePaise: Int, val declined: Boolean = false,
    val booked: Boolean = false, val cards: List<CloseCard> = emptyList(), val refused: String = "",
)

/** Null when the answer says nothing to show (the turn is skipped and the desk goes on as before). */
fun parseCloseTurn(d: Map<String, Any?>?): CloseTurn? {
    if (d == null) return null
    val refused = (d["refused"] as? String).orEmpty().trim()
    // A refusal answers success:false with its own say: it must not be skipped (the server found no matter in the text).
    if (d["success"] == false && refused.isEmpty()) return null
    val say = (d["say"] as? String)?.trim().orEmpty()
    if (say.isEmpty()) return null
    val cards = ((d["choice"] as? Map<*, *>)?.get("cards") as? List<*>).orEmpty().mapNotNull { c ->
        val m = c as? Map<*, *> ?: return@mapNotNull null
        val key = (m["key"] as? String).orEmpty().trim()
        val button = (m["button"] as? String).orEmpty().trim()
        if (key !in setOf("consultation", "written") || button.isEmpty()) null
        else CloseCard(key, button, (m["title"] as? String).orEmpty().trim(), (m["price"] as? String).orEmpty().trim(), m["secondary"] == true)
    }
    return CloseTurn(
        htmlToMarkup(say), (d["source"] as? String)?.trim().orEmpty(), (d["fee_display"] as? String)?.trim().orEmpty(), (d["fee_paise"] as? Number)?.toInt() ?: 0,
        declined = d["declined"] == true, booked = d["booked"] == true, cards = cards, refused = refused,
    )
}

/** What the visitor chose so far, as desk/close wants it: "consultation" (booked), "written" (said no to the booking), or nothing yet. */
fun closeChose(booked: Boolean, offerAnswered: Boolean): String? = if (booked) "consultation" else if (offerAnswered) "written" else null

const val CLOSE_GO_AHEAD = "Yes, Go Ahead"
const val CLOSE_NOT_NOW = "Not Now"
const val CLOSE_STOP_EN = "No problem. Nothing has been sent or charged, and you can come back any time."

const val CLOSE_START_NEW = "Start A New Enquiry"

const val Q_FROM_GUEST = "Thank you. I have what you wrote above, so I will ask only what our counsel still need."
const val GUEST_STORY_MIN = 20

/** The guest's own messages, joined, when they wrote at least GUEST_STORY_MIN characters in all; else null, also when it is keyboard-mash text (the question is asked as before). */
fun guestStory(turns: List<Pair<String, String>>): String? =
    turns.filter { it.first == "guest" }.map { it.second.trim() }.filter { it.isNotEmpty() }.joinToString(" ").takeIf { it.length >= GUEST_STORY_MIN && !DeskViewModel.mashed(it) }

/** The unread answer's own line is shown; this is only for an answer without one. */
const val UNREAD_EN = "I can't take your enquiry right now. What you wrote is kept here; please try again later."

// The line under the box (the web's reception.php rcp-disclaim; the server does not serve it, so these are the web's own words).
/** The bold lead: the consultation's fee once the full consultation is chosen (R9, R9a), else the written enquiry's. */
fun disclaimerLead(t: DeskTerms, direct: Boolean, fee: String): String =
    if (direct) (if (fee.isNotBlank()) "A full consultation costs $fee, paid when you book it." else "A full consultation is paid when you book it.")
    else if (t.priced) "Submitting an enquiry costs ${t.displayPrice}, paid once it is written. Nothing is charged before then." else "Submitting an enquiry is free."

/** The rest: how the consultation is arranged, or the written enquiry's reply target (R6: a target once an advocate accepts it, never a promise). */
fun disclaimerTail(direct: Boolean): String =
    if (direct) " The advocate who takes it, or their office, will contact you to fix a time."
    else " The advocates read every enquiry, and we aim to reply within 30 minutes once an advocate accepts it."

// ---- Suggested Topics (the web's reception.php TOPICS, in the same order; the page holds them in its script, so the server serves no list) ----

/** One suggested topic: the line, what it covers, and the starter text a tap puts in the question box (it is never sent). */
data class DeskTopic(val title: String, val covers: String, val starter: String)

val DESK_TOPICS = listOf(
    DeskTopic("A cheque given to me has bounced.", "Complaints under Section 138 of the NI Act.", "A cheque given to me has bounced. "),
    DeskTopic("My tenant will not pay rent or leave.", "Rent, eviction and security deposits.", "My tenant will not pay rent or leave. "),
    DeskTopic("I have received a legal notice.", "Understanding it, and replying in time.", "I have received a legal notice from "),
    DeskTopic("I live abroad and have property in India.", "Sale, possession and Power of Attorney.", "I live abroad and have property in India. "),
    DeskTopic("A business partner broke our agreement.", "Contracts, recovery and disputes.", "A business partner has broken our agreement. "),
    DeskTopic("My employer has not paid my dues.", "Salary, gratuity and termination.", "My employer has not paid my dues. "),
)
/** How many the web shows before "See all". */
const val DESK_TOPICS_SHOWN = 3
/** The draft is kept from this many characters (the web's rule). */
const val DESK_DRAFT_MIN = 3

// ---- The welcome, as the web says it (reception.php greetBack, F-018 / R23a) ----

const val GREET_NEW_NOENAME_EN = "Thank you for choosing Legum Lex."

/** "Welcome back, {first}." (served welcome_back_name / welcome_back): the first name only, with nothing else about the account and no note about the file. */
fun welcomeLine(first: String, book: LineBook): String =
    if (first.isNotBlank()) book.line("welcome_back_name", mapOf("first" to first)) ?: "Welcome back, $first."
    else book.line("welcome_back") ?: "Welcome back."

/** The facts card: advocates, open matters, next hearing (labels lab_counsel / lab_open / lab_hearing; the count by matter_one / matter_many). */
fun welcomeFacts(c: DeskContext, day: (String) -> String, label: (String, String) -> String, matters: (Int) -> String?): List<Pair<String, String>> = buildList {
    if (c.counsel.isNotEmpty()) add(label("lab_counsel", "Your advocates") to DeskViewModel.names(c.counsel))
    if (c.openCases > 0) add(label("lab_open", "Open matters") to (matters(c.openCases) ?: "${c.openCases} ${if (c.openCases == 1) "matter" else "matters"}"))
    c.nextHearingDate?.let { d -> add(label("lab_hearing", "Next hearing") to day(d) + (c.nextHearingTitle?.takeIf { it.isNotBlank() }?.let { ", $it" } ?: "")) }
}
