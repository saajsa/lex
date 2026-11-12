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

/** What is wrong with a deadline answer (the web's own rules); "" when nothing is. Both may not be empty; the date, when given, is YYYY-MM-DD between 2000 and 2100. */
fun deadlineProblem(text: String, date: String): String {
    val t = cleanDeadlineText(text)
    if (t.isEmpty() && date.isBlank()) return "Write a few words, or pick a date, or choose No, Nothing Yet."
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

/** The receipt line after sending: no advocate is named, since none has the enquiry yet. */
fun receiptLine(enquiryNumber: String?): String {
    val n = enquiryNumber?.trim().orEmpty()
    return (if (n.isNotEmpty()) "Enquiry $n received." else "Your enquiry has been received.") +
        " It will be given to an advocate, and you will be told who has it."
}

// ---- What is sent with an enquiry ----

/** The text fields of desk/enquiry, in the order sent. enquiry_text is always the client's own words, byte for byte. */
fun enquiryFields(
    text: String, entity: String, phone: String, country: String?, countryOther: String, reading: String, sessionHash: String?,
    submissionKey: String, deadlineFlag: String, deadlineDate: String,
): List<Pair<String, String>> = buildList {
    add("enquiry_text" to text)
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
const val GATHER_SEND_NOW = "Send Now"

/** The desk's next question (desk/gather): "" means nothing more, and [done] says it has no more to ask. */
data class GatherResult(val question: String, val topic: String, val done: Boolean, val ran: Boolean = false) {
    val more: Boolean get() = !done && question.isNotBlank()
}

/** The answer of desk/gather; null when it could not be asked (a failure never blocks sending). */
fun parseGather(d: Map<String, Any?>?): GatherResult? {
    if (d == null || d["success"] == false) return null
    return GatherResult((d["question"] as? String).orEmpty().trim(), (d["topic"] as? String).orEmpty(), d["done"] == true, d["ran"] == true)
}

/** The separate hearing-or-deadline step is skipped when Lex's gathering ran (any call said ran), and kept when it failed, timed out, or never ran. */
fun skipDeadlineStep(gatherRan: Boolean): Boolean = gatherRan

/** A question and its answer, as appended to the enquiry text. */
fun gatherLabel(question: String, answer: String): String = "\n\nLex asked: $question\nThey answered: $answer"

/** Whether to ask one more: a clarifying question skipped ("as it is") ends the asking, and three in all is the limit. */
fun mayAskMore(readingFollowUpAnswered: Boolean, readingFollowUpSkipped: Boolean, askedSoFar: Int): Boolean =
    !readingFollowUpSkipped && askedSoFar < GATHER_MAX

/** The question after the welcome: one line, no second greeting. */
const val ASK_HELP = "What would you like help with?"
