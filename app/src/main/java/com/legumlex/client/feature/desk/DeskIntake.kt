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
    // The date in full, as the web reads it ("Tuesday, 15 December 2026"); one that will not parse is shown as sent.
    listOfNotNull(cleanDeadlineText(text).takeIf { it.isNotEmpty() }, date.takeIf { it.isNotBlank() }?.let { d -> runCatching { dateAnswer(LocalDate.parse(d)) }.getOrDefault(d) }).joinToString(" · ")

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
data class GatherResult(
    val question: String, val topic: String, val done: Boolean, val ran: Boolean = false, val tongue: DeskTongue = DeskTongue(), val directOffer: DirectOfferInfo? = null, val directOpen: DirectOpen? = null,
    /** mode=focus: what Lex understood (said once, with the question), the kinds of help to tick while the goal is unclear ("not sure" last), and the danger pause. */
    val reflect: String = "", val options: List<String> = emptyList(), val optionsHint: String = "", val emergency: String = "",
    /** After the last question, read the whole again only when this is true (an older server says nothing: as before). */
    val reread: Boolean = true,
    /** REQ-045: how the question is answered (chips or a calendar); null keeps today's typed answer. */
    val input: GatherInput? = null,
) {
    val more: Boolean get() = !done && question.isNotBlank()
    val hasOptions: Boolean get() = options.isNotEmpty()
}

/** The answer of desk/gather; null when it could not be asked (a failure never blocks sending). */
fun parseGather(d: Map<String, Any?>?): GatherResult? {
    if (d == null || d["success"] == false) return null
    return GatherResult(
        (d["question"] as? String).orEmpty().trim(), (d["topic"] as? String).orEmpty(), d["done"] == true, d["ran"] == true, parseTongue(d), parseDirectOffer(d["direct_offer"]), parseDirectOpen(d["direct_open"]),
        reflect = (d["reflect"] as? String).orEmpty().trim(), options = (d["options"] as? List<*>).orEmpty().mapNotNull { (it as? String)?.trim()?.takeIf { s -> s.isNotEmpty() } },
        optionsHint = (d["options_hint"] as? String).orEmpty().trim(), emergency = htmlToMarkup((d["emergency"] as? String).orEmpty().trim()), reread = d["reread"] != false,
        input = parseGatherInput(d["input"]),
    )
}

// ---- Lex asks with widgets (REQ-045 Plan v2) ----

/**
 * A gather question's input, as the server decided it (cases_desk_input_clean): yes_no and choice with the server's [options] (yes_no falls back to the book's
 * Yes / No / Not Sure when absent), or date with [whenDate] past | future and the server's [min] / [max]; [other] is the server's "Something Else, Type It".
 */
data class GatherInput(
    val kind: String, val options: List<String> = emptyList(), val whenDate: String = "", val min: LocalDate? = null, val max: LocalDate? = null,
    val other: String = "", val fillsDeadline: Boolean = false,
    /** A hearing to come that may already have been missed: the server's "I Missed It" and its past calendar. */
    val missed: MissedInput? = null,
)

/** The missed-hearing way (date input only): [label] the chip, [ask] the calendar's heading, [answer] with {date}, and the days [min]..[max]. */
data class MissedInput(val label: String, val ask: String, val answer: String, val min: LocalDate? = null, val max: LocalDate? = null)

/** The server's missed way, or null when it is absent or has no label. */
fun parseMissed(raw: Any?): MissedInput? {
    val m = raw as? Map<*, *> ?: return null
    fun str(k: String) = (m[k] as? String).orEmpty().trim()
    fun day(k: String) = (m[k] as? String)?.let { runCatching { LocalDate.parse(it.trim()) }.getOrNull() }
    val label = str("label").ifEmpty { return null }
    return MissedInput(label, str("ask").ifEmpty { "When was it?" }, str("answer").ifEmpty { MISSED_ANSWER_EN }, day("min"), day("max"))
}

const val MISSED_ANSWER_EN = "Missed the hearing on {date}"

// ---- The key dates of a bounced cheque (REQ-046; the web's askKeyDates) ----

/** The question the key dates go under in the enquiry text (code's own English, as the web's KD_Q). */
const val KD_Q = "Key dates, if you know them"
const val KD_NONE_EN = "I do not have the dates to hand"

/** The screens of the step, in order: the three dates, then how the notice went, then its date. */
enum class KdAt { CHEQUE, MEMO, SENT, RECV, RECV_DATE }

/** What was given: each date (or null), no notice yet, and how the notice went (delivered | returned). */
data class KeyDates(
    val cheque: LocalDate? = null, val memo: LocalDate? = null, val sent: LocalDate? = null, val none: Boolean = false,
    val how: String = "", val recv: LocalDate? = null,
)

/** The days a key date can be: three years back to today. */
fun kdBounds(today: LocalDate): ClosedRange<LocalDate> = today.minusYears(3)..today

/** "4 November 2026" (PHP's j F Y, in English). */
fun kdDay(d: LocalDate): String = d.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", java.util.Locale.ENGLISH))

/** The answer line, exactly as the web builds it (parts joined by "; "); "" when nothing was given, and then nothing is added to the enquiry. */
fun keyDatesAnswer(v: KeyDates): String = buildList {
    v.cheque?.let { add("Cheque dated " + kdDay(it)) }
    v.memo?.let { add("bank return memo " + kdDay(it)) }
    if (v.none) add("no notice sent yet")
    v.sent?.let { add("notice sent " + kdDay(it)) }
    if (v.recv != null && v.how == "delivered") add("notice delivered " + kdDay(v.recv))
    if (v.recv != null && v.how == "returned") add("notice refused or unclaimed " + kdDay(v.recv))
    if (v.sent != null && v.recv == null) add("notice delivery not known")
}.joinToString("; ")

/** The screen after [at] once it is answered or passed with I Don't Know; null: the step is done. */
fun kdNext(at: KdAt, v: KeyDates): KdAt? = when (at) {
    KdAt.CHEQUE -> KdAt.MEMO
    KdAt.MEMO -> KdAt.SENT
    KdAt.SENT -> if (v.sent != null) KdAt.RECV else null
    KdAt.RECV, KdAt.RECV_DATE -> null
}

/** The missed calendar's days: the server's, else a year back to today. */
fun missedBounds(m: MissedInput, today: LocalDate): ClosedRange<LocalDate> {
    val lo = m.min ?: today.minusYears(1)
    val hi = m.max ?: today
    return if (lo <= hi) lo..hi else today.minusYears(1)..today
}

/** The missed answer, the date in full: "Missed the hearing on Friday, 22 November 2026". It is also the deadline line counsel sees (no deadline date). */
fun missedAnswer(m: MissedInput, d: LocalDate): String = m.answer.replace("{date}", dateAnswer(d))

const val INPUT_YES_NO = "yes_no"
const val INPUT_CHOICE = "choice"
const val INPUT_DATE = "date"
const val TYPE_IT_EN = "Something Else, Type It"

/** The server's input, or null (typed, as today) when absent, of an unknown kind, or not well formed. The server vets it too; this only refuses what cannot be shown. */
fun parseGatherInput(raw: Any?): GatherInput? {
    val m = raw as? Map<*, *> ?: return null
    val opts = (m["options"] as? List<*>).orEmpty().mapNotNull { (it as? String)?.trim()?.takeIf { s -> s.isNotEmpty() } }.distinct()
    val other = (m["other"] as? String).orEmpty().trim()
    fun day(k: String) = (m[k] as? String)?.let { runCatching { LocalDate.parse(it.trim()) }.getOrNull() }
    return when (val kind = (m["kind"] as? String)?.trim()) {
        INPUT_YES_NO -> GatherInput(kind, opts.takeIf { it.size == 3 }.orEmpty(), other = other)
        INPUT_CHOICE -> if (opts.size in 2..5) GatherInput(kind, opts, other = other) else null
        INPUT_DATE -> (m["when"] as? String)?.trim()?.takeIf { it == "past" || it == "future" }?.let {
            GatherInput(kind, whenDate = it, min = day("min"), max = day("max"), other = other, fillsDeadline = m["fills_deadline"] == true, missed = parseMissed(m["missed"]))
        }
        else -> null
    }
}

/** The days the calendar allows: the server's min and max, each falling back to the plan's bounds (past: up to today; future: today to two years ahead). */
fun inputBounds(i: GatherInput, today: LocalDate): ClosedRange<LocalDate> {
    val plan = dateBounds(i.whenDate, today)
    val lo = i.min ?: plan.start
    val hi = i.max ?: plan.endInclusive
    return if (lo <= hi) lo..hi else plan
}

/** As the web (reception.php dockGather): the server alone decides (fills_deadline) that the date answered is the enquiry's deadline; that step is then not asked. */
fun inputFillsDeadline(i: GatherInput): Boolean = i.kind == INPUT_DATE && i.fillsDeadline

/** The yes/no labels when the server sends none: the book's key and the English (the server's in_yes / in_no / in_not_sure lines). */
val YES_NO = listOf("in_yes" to "Yes", "in_no" to "No", "in_not_sure" to "Not Sure")

/** The days a date input allows: past, any day up to today; future, today to two years ahead. */
fun dateBounds(whenDate: String, today: LocalDate): ClosedRange<LocalDate> =
    if (whenDate == "past") LocalDate.of(1900, 1, 1)..today else today..today.plusYears(2)

/** A chosen date as the visitor's answer reads in the conversation and the enquiry: "Friday, 11 December 2026". */
fun dateAnswer(d: LocalDate): String = d.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", java.util.Locale.ENGLISH))

/**
 * The ticks, as the web keeps them (dockGather): "I'm not sure" (always the last) stands alone; ticking any other kind of help clears it; ticking it clears the rest.
 * Plain Kotlin, so the rule is tested off-device.
 */
fun toggleOption(picks: Set<Int>, index: Int, count: Int): Set<Int> {
    val unsure = count - 1
    val on = index !in picks
    return if (!on) picks - index else if (index == unsure) setOf(index) else (picks - unsure) + index
}

/** The answer sent for the ticks and what was written beside them: the ticked kinds in order, then the visitor's own words. */
fun optionsAnswer(options: List<String>, picks: Set<Int>, written: String): String {
    val picked = picks.sorted().mapNotNull { options.getOrNull(it) }
    val own = written.trim()
    return if (picked.isEmpty()) own else picked.joinToString("; ") + (if (own.isNotEmpty()) ". $own" else "")
}

/** The button of the tick list: "Choose One Or More" until something is ticked, then "Send 1 Choice" or "Send N Choices". */
fun sendChoicesLabel(n: Int, book: LineBook): String = when (n) {
    0 -> book.fragment("ui_choose_some") ?: "Choose One Or More"
    1 -> book.fragment("ui_send_choice") ?: "Send 1 Choice"
    else -> book.fragment("ui_send_choices", mapOf("n" to n.toString())) ?: "Send $n Choices"
}

const val OPT_CHOOSE_EN = "Choose one or more."
const val OPT_WITH_CHOICES_EN = "With your choices: {list}"
const val SAFE_CARRY_ON_EN = "I'm Safe Now, Carry On"

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
data class CloseCard(
    val key: String, val button: String, val title: String, val price: String, val secondary: Boolean,
    val includes: String = "", val timing: String = "", val intro: String = "",
)

/**
 * What Lex says before the enquiry is sent, and where it came from. Beside `say`: `declined` (no ask), `booked` (the full consultation was chosen: one line,
 * nothing more), `choice` (the two cards, the consultation first) and `refused` ("no_matter": the text holds no matter; `say` asks for it).
 */
data class CloseTurn(
    val say: String, val source: String, val feeDisplay: String, val feePaise: Int, val declined: Boolean = false,
    val booked: Boolean = false, val cards: List<CloseCard> = emptyList(), val refused: String = "",
    /** With chose=consultation and a note: the answer to a question in the note (who sees it, the fee, the time), what is left as their note, and whether they said it is urgent. */
    val bookingAnswer: String = "", val noteRest: String = "", val urgent: Boolean = false,
)

/** Null when the answer says nothing to show (the turn is skipped and the desk goes on as before). */
fun parseCloseTurn(d: Map<String, Any?>?): CloseTurn? {
    if (d == null) return null
    val refused = (d["refused"] as? String).orEmpty().trim()
    // A refusal answers success:false with its own say: it must not be skipped (the server found no matter in the text).
    if (d["success"] == false && refused.isEmpty()) return null
    val say = (d["say"] as? String)?.trim().orEmpty()
    if (say.isEmpty()) return null
    val cards = parseChoiceBlock(d["choice"])?.cards.orEmpty()
    return CloseTurn(
        htmlToMarkup(say), (d["source"] as? String)?.trim().orEmpty(), (d["fee_display"] as? String)?.trim().orEmpty(), (d["fee_paise"] as? Number)?.toInt() ?: 0,
        declined = d["declined"] == true, booked = d["booked"] == true, cards = cards, refused = refused,
        bookingAnswer = htmlToMarkup((d["booking_answer"] as? String).orEmpty().trim()), noteRest = (d["note_rest"] as? String).orEmpty().trim(), urgent = d["urgent"] == true,
    )
}

/** What the visitor chose so far, as desk/close wants it: "consultation" (booked), "written" (said no to the booking), or nothing yet. */
fun closeChose(booked: Boolean, offerAnswered: Boolean): String? = if (booked) "consultation" else if (offerAnswered) "written" else null

const val CLOSE_GO_AHEAD = "Yes, Go Ahead"
const val CLOSE_NOT_NOW = "Not Now"
const val CLOSE_STOP_EN = "No problem. Nothing has been sent or charged, and you can come back any time."

const val CLOSE_START_NEW = "Start A New Enquiry"

/** The service routes under which the enquiry footer (fee, 30 minutes) is not shown (web syncFooter). */
val ROUTE_KEYS = setOf("ahc", "portal_docs", "clarify")

const val Q_FROM_GUEST = "Thank you. I have what you wrote above, so I will ask only what our counsel still need."
const val GUEST_STORY_MIN = 20

/** The guest's own messages, joined, when they wrote at least GUEST_STORY_MIN characters in all; else null, also when it is keyboard-mash text (the question is asked as before). */
fun guestStory(turns: List<Pair<String, String>>): String? =
    turns.filter { it.first == "guest" }.map { it.second.trim() }.filter { it.isNotEmpty() }.joinToString(" ").takeIf { it.length >= GUEST_STORY_MIN && !DeskViewModel.mashed(it) }

/** The unread answer's own line is shown; this is only for an answer without one. */
const val UNREAD_EN = "I can't take your enquiry right now. What you wrote is kept here; please try again later."

// The line under the box (the web's reception.php rcp-disclaim; the server does not serve it, so these are the web's own words).
/** The bold lead: the consultation's fee once the full consultation is chosen (R9, R9a), else the written enquiry's. */
fun disclaimerLead(t: DeskTerms, direct: Boolean, fee: String, book: LineBook = LineBook.English): String =
    // The words are the server's (desk_lines foot_*); the constants are the offline fallback.
    if (direct) (if (fee.isNotBlank()) book.line("foot_direct", mapOf("fee2" to fee)) ?: "A full consultation costs $fee, paid when you book it." else "A full consultation is paid when you book it.")
    else if (t.priced) book.line("foot_fee_paid", mapOf("fee" to t.displayPrice)) ?: "Submitting an enquiry costs ${t.displayPrice}, paid once it is written. Nothing is charged before then."
    else book.line("foot_fee_free") ?: "Submitting an enquiry is free."

/** The rest: how the consultation is arranged, or the written enquiry's reply target (R6: a target once an advocate accepts it, never a promise). */
fun disclaimerTail(direct: Boolean, book: LineBook = LineBook.English): String =
    if (direct) " " + (book.line("foot_direct_contact") ?: "The advocate who takes it, or their office, will contact you to fix a time.")
    else " " + (book.line("foot_readers_advocate", mapOf("reply" to (book.fragment("foot_reply") ?: "aim to reply within 30 minutes once an advocate accepts it"))) ?: "The advocates read every enquiry, and we aim to reply within 30 minutes once an advocate accepts it.")

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

/** The number as it is shown while typed: five and five, a leading 91 or 0 dropped (an overseas number or a "+" is left as typed). */
fun formatPhoneInput(raw: String, nri: Boolean): String =
    if (nri || raw.trimStart().startsWith("+")) raw else {
        var d = raw.filter { it.isDigit() }
        if (d.length > 10 && d.startsWith("91")) d = d.drop(2)
        d = d.trimStart('0').take(10)
        if (d.length > 5) d.take(5) + " " + d.drop(5) else d
    }

// ---- The non-matter branch and the cards (REQ-029 slice 2; reception.php matterAsk, insistOffer, insistMatter, dockChoice, cardAsk) ----

const val PERSON_NEED_MATTER_EN = "I can make a counsel available, but I still really need to know your matter. Please tell me what happened, in your own words."
const val ASK_MATTER_SHORT_EN = "Please tell me what happened, in your own words."
const val ASK_MATTER_AGAIN_EN = "Whenever you are ready, tell me what happened and I will take it from there."
const val ASK_MATTER_OPTIONAL_EN = "If you like, add a line about your matter for the advocate, and the time that suits you for the call."
const val INSIST_DECLINED_EN = "That is fine. Nothing has been booked or charged, and you can come back any time."
const val CARD_ASK_PLACEHOLDER_EN = "Ask a question…"

/** The ask for the matter is never the same words twice in a row: the owner's full line, a short one, then a patient one (matterAsk). */
fun matterAskKey(asks: Int): Pair<String, String> = when (asks) {
    0 -> "person_need_matter" to PERSON_NEED_MATTER_EN
    1 -> "ask_matter_short" to ASK_MATTER_SHORT_EN
    else -> "ask_matter_again" to ASK_MATTER_AGAIN_EN
}

/** What a typed line under the cards means before anything is read (cardAsk): a short choice in words picks the card; anything else is a question or a story. */
fun cardPickFromText(text: String): String {
    val t = text.trim()
    val short = t.split(Regex("\\s+")).size <= 12 && '?' !in t
    if (!short) return ""
    return when {
        Regex("\\b(?:written|just (?:an |the )?enquiry|only (?:an |the )?enquiry)\\b", RegexOption.IGNORE_CASE).containsMatchIn(t) -> "written"
        Regex("^\\s*(?:yes|ok(?:ay)?|sure|book|please book|i(?:'ll| will) book)\\b|\\b(?:book it|full consultation|the consultation)\\b", RegexOption.IGNORE_CASE).containsMatchIn(t) -> "consultation"
        else -> ""
    }
}

/** What the reading says to do with a message that is not an enquiry (verdict): the cards, the answer, or the ask for the matter. */
enum class NonMatterTurn { Insist, Answer, AskMatter, None }

fun nonMatterTurn(r: DeskReading): NonMatterTurn {
    val not = r.legalInquiry == false || r.messageKind == "person_request" || r.nonmatter
    if (!not) return NonMatterTurn.None
    if (r.insist != null) return NonMatterTurn.Insist
    val ask = r.messageKind == "person_request" || r.needMatter || r.reply.isBlank()
    return if (ask) NonMatterTurn.AskMatter else NonMatterTurn.Answer
}

// ---- The send stepper (F-018, F-032): three steps, and the held state for a booking waiting for its fee ----

val STAGE_RUNNING_KEYS = listOf("stage_1", "stage_2", "stage_4")
val STAGE_DONE_KEYS = listOf("stage_done_1", "stage_done_2", "stage_done_4")
val STAGE_AT_MS = listOf(0L, 900L, 2200L)
const val STAGE_HELD_TITLE_EN = "Booked. Waiting for the fee."
const val STAGE_HELD_1_EN = "Your details are saved"
const val STAGE_HELD_2_EN = "Pay {fee} to send it to our counsel"

/** A booked consultation whose fee is unpaid: the server holds it (data.intake_state, or direct.state, awaiting_payment). Nothing has reached the advocates. */
fun isHeldBooking(intakeState: String?, direct: DirectBooking?): Boolean = intakeState == "awaiting_payment" || direct?.state == "awaiting_payment"
