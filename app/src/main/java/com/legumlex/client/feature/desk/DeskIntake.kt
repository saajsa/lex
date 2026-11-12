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

// ---- The brief (REQ-017 Phase 2): what the desk will tell the advocates, checked by the client before it is sent ----

data class BriefPerson(val name: String, val role: String = "")
data class BriefDate(val asWritten: String, val about: String = "")

/** The desk's draft of the enquiry in closed fields. Nothing in it is advice or a conclusion; the client reads it, may change it, and may send their own words instead. */
data class DeskBrief(val whatHappened: String, val who: List<BriefPerson>, val wants: String, val dates: List<BriefDate>)

/** The answer to desk/brief: state "read" with a brief and its signed token, or anything else (no Review step). */
data class BriefResult(val state: String, val brief: DeskBrief?, val token: String) {
    /** The Review screen is shown only for a read brief with its token; every other state, a network error or a timeout goes on to send as before. */
    val reviewable: Boolean get() = state == "read" && brief != null && token.isNotEmpty()
}

val NO_BRIEF = BriefResult("skipped", null, "")

const val BRIEF_WHAT_MAX = 400
const val BRIEF_WANTS_MAX = 200
const val BRIEF_WHO_MAX = 5
const val BRIEF_DATES_MAX = 4

@Suppress("UNCHECKED_CAST")
fun parseBriefResult(d: Map<String, Any?>?): BriefResult {
    if (d == null) return NO_BRIEF
    val state = (d["state"] as? String).orEmpty().ifBlank { "skipped" }
    val b = d["brief"] as? Map<String, Any?>
    val brief = b?.let {
        DeskBrief(
            whatHappened = (it["what_happened"] as? String).orEmpty(),
            who = (it["who"] as? List<Map<String, Any?>>).orEmpty().map { w -> BriefPerson((w["name"] as? String).orEmpty(), (w["role"] as? String).orEmpty()) },
            wants = (it["wants"] as? String).orEmpty(),
            dates = (it["dates"] as? List<Map<String, Any?>>).orEmpty().map { x -> BriefDate((x["as_written"] as? String).orEmpty(), (x["about"] as? String).orEmpty()) },
        )
    }
    return BriefResult(state, brief, (d["brief_token"] as? String).orEmpty())
}

/** "name, role" and "as written, about": the line a person or a date is shown and edited as. */
fun BriefPerson.line() = if (role.isNotBlank()) "$name, $role" else name
fun BriefDate.line() = if (about.isNotBlank()) "$asWritten, $about" else asWritten

private fun splitFirstComma(s: String): Pair<String, String> {
    val i = s.indexOf(',')
    return if (i < 0) s.trim() to "" else s.substring(0, i).trim() to s.substring(i + 1).trim()
}

/** What the Edit screen turns into: the brief, or what to say under the fields. */
sealed interface BriefEdit {
    data class Ok(val brief: DeskBrief) : BriefEdit
    data class Problem(val message: String) : BriefEdit
}

/** The four edit fields (who and dates one per line) as a brief; the limits are the server's. The token stays the desk's, unchanged. */
fun parseBriefEdit(whatHappened: String, whoLines: String, wants: String, datesLines: String): BriefEdit {
    val what = cleanDeadlineText(whatHappened); val wan = cleanDeadlineText(wants)
    if (what.length > BRIEF_WHAT_MAX) return BriefEdit.Problem("Please keep What Happened to $BRIEF_WHAT_MAX characters.")
    if (wan.length > BRIEF_WANTS_MAX) return BriefEdit.Problem("Please keep What You Want to $BRIEF_WANTS_MAX characters.")
    val who = whoLines.lines().map { cleanDeadlineText(it) }.filter { it.isNotEmpty() }
    val dates = datesLines.lines().map { cleanDeadlineText(it) }.filter { it.isNotEmpty() }
    if (who.size > BRIEF_WHO_MAX) return BriefEdit.Problem("Please list at most $BRIEF_WHO_MAX people, one to a line.")
    if (dates.size > BRIEF_DATES_MAX) return BriefEdit.Problem("Please list at most $BRIEF_DATES_MAX dates, one to a line.")
    if (what.isEmpty() && wan.isEmpty() && who.isEmpty() && dates.isEmpty()) return BriefEdit.Problem("Please leave something here, or send your own words instead.")
    return BriefEdit.Ok(DeskBrief(
        what,
        who.map { splitFirstComma(it).let { (n, r) -> BriefPerson(n, r) } },
        wan,
        dates.map { splitFirstComma(it).let { (a, b) -> BriefDate(a, b) } },
    ))
}

private fun jsonString(s: String): String = buildString {
    append('"')
    for (c in s) when {
        c == '"' -> append("\\\"")
        c == '\\' -> append("\\\\")
        c == '\n' -> append("\\n")
        c == '\r' -> append("\\r")
        c == '\t' -> append("\\t")
        c < ' ' -> append("\\u%04x".format(c.code))
        else -> append(c)
    }
    append('"')
}

/** The brief as the server takes it: {what_happened, who:[{name,role}], wants, dates:[{as_written,about}]}. */
fun DeskBrief.toJson(): String =
    "{\"what_happened\":${jsonString(whatHappened)},\"who\":[" + who.joinToString(",") { "{\"name\":${jsonString(it.name)},\"role\":${jsonString(it.role)}}" } +
        "],\"wants\":${jsonString(wants)},\"dates\":[" + dates.joinToString(",") { "{\"as_written\":${jsonString(it.asWritten)},\"about\":${jsonString(it.about)}}" } + "]}"

/** The text fields of desk/enquiry, in the order sent. enquiry_text is always the client's own words, byte for byte; a brief adds brief and brief_token, none adds neither. */
fun enquiryFields(
    text: String, entity: String, phone: String, country: String?, countryOther: String, reading: String, sessionHash: String?,
    submissionKey: String, deadlineFlag: String, deadlineDate: String, brief: DeskBrief?, briefToken: String,
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
    if (brief != null && briefToken.isNotEmpty()) { add("brief" to brief.toJson()); add("brief_token" to briefToken) }
}

/** The brief drafts made so far, one per exact text: back and forward, or a retry, never ask the desk again; a changed text is asked again. */
class BriefCache {
    private var text: String? = null
    private var result: BriefResult? = null
    fun get(forText: String): BriefResult? = if (text == forText) result else null
    fun put(forText: String, r: BriefResult) { text = forText; result = r }
}

/** The ask_reasons that make a clear enquiry worth one question. */
val ASK_REASONS = setOf("time_limit_unknown", "forum_unknown", "missing_facts")

// ---- Lex's introduction (REQ-017 017.C): what a guest meets first ----

/** The first line of a guest's conversation, the owner's words. The card before the conversation starts says the same in two parts and gives way to this line. */
const val LEX_INTRO = "Hi, I am Lex, your AI assistant. I will listen, note the details, and pass your enquiry to an advocate. What would you like help with?"
const val LEX_INTRO_HEADING = "Hi, I Am Lex"
const val LEX_INTRO_BODY = "Your AI assistant. I will listen, note the details, and pass your enquiry to an advocate. What would you like help with?"
