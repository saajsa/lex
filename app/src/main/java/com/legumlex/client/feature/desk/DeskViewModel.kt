package com.legumlex.client.feature.desk

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/* ============================================================================
 *  The desk's conversation: reception.php's script for a client signed in to
 *  the portal, step for step and in its words (the advocate voice the site
 *  uses). The order is the page's next():
 *
 *    question → reading → [clarify] → [documents] → welcome → [route]
 *      → entity → [country] → phone → send → delivered
 *
 *  Sign-in is the page's portal branch: the app's own sign-in is the proof,
 *  so it is never asked. Every answer can be changed, which rewinds the
 *  conversation to it, as the page's "Change" does.
 * ========================================================================== */

enum class Step { QUESTION, CLARIFY, DOCS, ROUTE, ENTITY, COUNTRY, PHONE }

private val ORDER = Step.values().toList()

sealed interface Para {
    /** A paragraph; **bold** marks what the page sets in <b>. */
    data class Text(val s: String, val flag: Boolean = false) : Para
    data class Bullets(val items: List<String>) : Para
    data class Facts(val rows: List<Pair<String, String>>) : Para
    /** "Taking your enquiry in…" and its four stages. */
    data class Stages(val active: Int, val done: Boolean) : Para
    data object Seal : Para
}

enum class Who { DESK, ME, NOTE }

data class Line(
    val id: Long,
    val who: Who,
    val paras: List<Para>,
    val prompt: Step? = null,
    val step: Step? = null,
    val warn: Boolean = false,
    val files: List<String> = emptyList(),
)

data class Action(val id: String, val label: String, val main: Boolean = false)

sealed interface Dock {
    data class Waiting(val label: String) : Dock
    data object Question : Dock
    data object Clarify : Dock
    data object Docs : Dock
    data class Actions(val list: List<Action>) : Dock
    data class Entity(val hint: String) : Dock
    data object Country : Dock
    data object Phone : Dock
}

data class DeskUi(
    val lines: List<Line> = emptyList(),
    val dock: Dock = Dock.Waiting(""),
    val typing: Boolean = false,
    val error: String = "",
    val files: List<DeskFile> = emptyList(),
    val terms: DeskTerms = DeskTerms(),
    /** The funnel's terms have arrived; until then nothing is said about the fee. */
    val termsLoaded: Boolean = false,
    /** The conversation shows once the first question is sent, as the page's open(); before, the intro. */
    val opened: Boolean = false,
    /** What the question composer should hold when it comes back (a "Change"). */
    val prefill: String = "",
    val prefillTick: Int = 0,
    val locked: Boolean = false,
)

sealed interface DeskEvent {
    data object OpenMatters : DeskEvent
    data class OpenConsultation(val id: Int) : DeskEvent
}

class DeskViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = DeskRepository(app)
    private val _ui = MutableStateFlow(DeskUi())
    val ui: StateFlow<DeskUi> = _ui.asStateFlow()
    private val _events = MutableSharedFlow<DeskEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<DeskEvent> = _events

    // The page's S.
    private var text = ""
    private var more: String? = null
    private var vetted: String? = null
    private var ask = ""
    private var area = ""
    private var read: DeskReading? = null
    private var reading = ""
    private var qFiles = 0
    private var docs: Boolean? = null
    private var welcomed = false
    private var route: String? = null
    private var entity: String? = null
    private var country: String? = null
    private var countryOther = ""
    private var phone: String? = null
    private var sessionHash: String? = null
    private var consultationId: Int? = null
    private val offered = mutableSetOf<String>()
    private val said = mutableSetOf<String>()
    private var again = false
    private var gibs = 0
    private var vetRun = 0
    private var reread: Deferred<Unit>? = null
    private var sending = false
    private var nextId = 0L

    private val flow = Mutex()
    private val terms: Job = viewModelScope.launch {
        // One retry: the fee line must come from the funnel, never a default.
        val t = repo.terms() ?: run { delay(1_500); repo.terms() }
        t?.let { v -> _ui.update { it.copy(terms = v, termsLoaded = true) } }
    }

    init { go { next() } }

    private val t get() = _ui.value.terms
    private val ctx get() = t.context

    /** Run a turn of the conversation, one at a time, as the page's queue does. */
    private fun go(block: suspend () -> Unit) {
        viewModelScope.launch { flow.withLock { block() } }
    }

    // ---- Lines -------------------------------------------------------------

    private suspend fun say(vararg paras: Para, prompt: Step? = null, note: Boolean = false, warn: Boolean = false) {
        val chars = paras.sumOf { (it as? Para.Text)?.s?.length ?: 40 }
        _ui.update { it.copy(typing = true) }
        delay((350L + chars * 6L).coerceAtMost(1_100L))
        _ui.update { it.copy(typing = false, lines = it.lines + Line(++nextId, if (note) Who.NOTE else Who.DESK, paras.toList(), prompt = prompt, warn = warn)) }
    }

    private fun answer(step: Step, s: String, files: List<String> = emptyList()) {
        _ui.update { it.copy(lines = it.lines + Line(++nextId, Who.ME, listOf(Para.Text(s)), step = step, files = files), error = "") }
    }

    private fun asked(step: Step) = _ui.value.lines.any { it.prompt == step }
    private fun dock(d: Dock) = _ui.update { it.copy(dock = d, error = "") }
    private fun err(s: String) = _ui.update { it.copy(error = s) }

    // ---- The order ---------------------------------------------------------

    private suspend fun next() {
        when {
            text.isEmpty() -> askQuestion()
            vetted == null -> vet()
            vetted == "clarify" && more == null -> askClarify()
            docs == null -> askDocs()
            !welcomed -> welcome()
            route == null && needsRoute() -> askRoute()
            entity == null -> askEntity()
            entity == "nri" && country == null -> askCountry()
            phone == null -> askPhone()
            else -> send()
        }
    }

    private fun hour(): String {
        val h = LocalTime.now().hour
        return if (h < 12) "Good morning" else if (h < 17) "Good afternoon" else "Good evening"
    }

    private suspend fun askQuestion() {
        if (!asked(Step.QUESTION)) {
            if (again) {
                say(Para.Text("Of course. What else would you like to ask?"), prompt = Step.QUESTION)
            } else {
                say(Para.Text(hour() + ", and welcome to Legum Lex."))
                say(Para.Text("What would you like to ask? In your own words: what has happened, who is involved, and what you would like the advocate to do."), prompt = Step.QUESTION)
            }
        }
        dock(Dock.Question)
    }

    fun submitQuestion(raw: String) {
        val v = raw.trim()
        if (v.length < t.minChars) {
            err(if (v.isNotEmpty()) "A little more, please: a sentence or two about what has happened." else "Tell the advocate what happened, in your own words.")
            return
        }
        if (mashed(v)) { err("I could not quite make that out. Tell me, in plain words, what has happened."); return }
        go {
            _ui.update { it.copy(opened = true) }
            text = v
            qFiles = _ui.value.files.size
            answer(Step.QUESTION, v, _ui.value.files.map { it.name + " · " + mb(it.size) })
            dock(Dock.Waiting("…"))
            next()
        }
    }

    // ---- Reading the question (Landing::ajax_desk_check, via desk/check) ----

    private suspend fun vet() {
        val run = ++vetRun
        dock(Dock.Waiting("Reading what you have written…"))
        _ui.update { it.copy(typing = true) }
        val started = System.currentTimeMillis()
        val r = repo.check(text)
        // A beat of reading even when the answer is instant.
        delay((800 - (System.currentTimeMillis() - started)).coerceAtLeast(0))
        _ui.update { it.copy(typing = false) }
        if (run == vetRun) verdict(r)
    }

    private suspend fun verdict(r: DeskReading?) {
        var v = r?.verdict ?: "clear"
        area = r?.area.orEmpty()
        keep(r)
        // A second "gibberish" goes through: the check can be wrong, and it must
        // never be the reason someone cannot reach the firm.
        if (v == "gibberish" && ++gibs >= 2) v = "clear"
        if (v == "gibberish") {
            rewind(Step.QUESTION) { say(Para.Text("I am sorry, I could not quite make that out. Could you tell me, in a sentence or two, what has happened?"), note = true, warn = true) }
            return
        }
        if (v == "vague" || v == "not_legal") {
            vetted = "clarify"
            ask = if (v == "vague") r?.followUp?.takeIf { it.isNotBlank() } ?: ASK_VAGUE else ASK_NOT_LEGAL
        } else {
            vetted = "ok"
        }
        next()
    }

    private fun keep(r: DeskReading?) {
        if (r == null || r.token.isEmpty()) return
        read = r
        reading = r.token
    }

    private suspend fun askClarify() {
        if (text.length > 4900) { more = ""; return next() }
        if (!asked(Step.CLARIFY)) say(Para.Text(ask), prompt = Step.CLARIFY)
        dock(Dock.Clarify)
    }

    fun submitClarify(raw: String) {
        val v = raw.trim()
        if (v.length < 3) { err("Please add a few words, or send it as it is."); return }
        if (mashed(v)) { err("I could not quite make that out. A few plain words will do."); return }
        go {
            more = v
            answer(Step.CLARIFY, v)
            // The whole of it is read again: the answer usually holds what the
            // first reading lacked. A slow or failed check leaves the first.
            val run = vetRun
            reread = viewModelScope.async {
                val r = repo.check(fullText())
                if (run == vetRun && r != null) {
                    if (r.area.isNotEmpty() && r.area != "other") area = r.area
                    keep(r)
                }
            }
            dock(Dock.Waiting("…"))
            next()
        }
    }

    fun sendAsItIs() = go {
        more = ""
        answer(Step.CLARIFY, "I would rather send it as it is")
        dock(Dock.Waiting("…"))
        next()
    }

    private fun fullText() = if (!more.isNullOrEmpty()) text + "\n\n" + more else text

    // ---- Documents -----------------------------------------------------------

    private suspend fun askDocs() {
        reread?.await()
        terms.join()
        val list = read?.documents.orEmpty()
        if (list.isEmpty() || !t.takesFiles || qFiles > 0) { docs = true; return next() }
        if (!asked(Step.DOCS)) {
            say(
                Para.Text(thanks() + " If you have them to hand, these would help counsel read it properly:"),
                Para.Bullets(list),
                Para.Text("Attach what you have now, or carry on without them. They can be sent later."),
                *readingNotes().toTypedArray(),
                prompt = Step.DOCS,
            )
        }
        dock(Dock.Docs)
    }

    fun docsGo() = go {
        docs = true
        val f = _ui.value.files
        answer(Step.DOCS, if (f.isNotEmpty()) "Attached:" else "I will send them later", f.map { it.name + " · " + mb(it.size) })
        dock(Dock.Waiting("…"))
        next()
    }

    // ---- Who they are (cases_desk_context, read with the terms) ---------------

    private fun thanks(): String {
        if ("thanks" in said) return "Thank you."
        said += "thanks"
        return if (!more.isNullOrEmpty()) "Thank you, that helps."
        else AREA[area]?.let { "Thank you. That sounds like $it." } ?: "Thank you."
    }

    private fun readingNotes(): List<Para> {
        val r = read ?: return emptyList()
        if ("notes" in said) return emptyList()
        said += "notes"
        return buildList {
            if (r.urgency == "urgent") {
                add(Para.Text("**This sounds time-critical" + (if (r.reason.isNotEmpty()) ": " + r.reason else "") + ".** It will be marked urgent, so that the advocates see it first.", flag = true))
            }
            if (r.place == "elsewhere_india") {
                add(Para.Text("The advocates practise mainly before the courts of Punjab, Haryana and Chandigarh, and take matters from elsewhere in India too. If yours would be better with counsel on the spot, they will say so plainly."))
            }
        }
    }

    private suspend fun welcome() {
        welcomed = true
        terms.join()
        greetBack()
        next()
    }

    private suspend fun greetBack() {
        val c = ctx
        val first = c?.firstName.orEmpty()
        if (c == null) {
            say(Para.Text((if (first.isNotEmpty()) "Thank you, $first. " else "") + "As this is your first enquiry here, there are a few quick questions, so that the advocates have what they need to reply properly."),
                *readingNotes().toTypedArray())
            return
        }
        val facts = buildList {
            if (c.counsel.isNotEmpty()) add("Your counsel" to names(c.counsel))
            if (c.openCases > 0) add("Open matters" to "${c.openCases} ${if (c.openCases == 1) "matter" else "matters"} with the advocates")
            c.nextHearingDate?.let { d -> add("Next hearing" to day(d) + (c.nextHearingTitle?.takeIf { it.isNotBlank() }?.let { ", $it" } ?: "")) }
        }
        say(
            Para.Text((if (first.isNotEmpty()) "Welcome back, $first." else "Welcome back.") + (if (c.company.isNotEmpty()) " Good to hear from ${c.company} again." else "")),
            *(if (facts.isNotEmpty()) arrayOf<Para>(Para.Facts(facts)) else emptyArray()),
            Para.Text("This will go onto your file, so the counsel who know it will see it."),
            *readingNotes().toTypedArray(),
        )
    }

    private fun needsRoute() = read?.intent == "existing_matter" && (ctx?.openCases ?: 0) > 0

    private suspend fun askRoute() {
        if (!asked(Step.ROUTE)) {
            val r = read!!
            say(Para.Text("It sounds as though this may be about " + (if (r.related.isNotEmpty()) "**${r.related}**, a matter" else "a matter")
                + " already with the advocates. If so, the quickest way to its counsel is to write on it in My Matters."), prompt = Step.ROUTE)
        }
        dock(Dock.Actions(listOf(Action("matters", "Open My Matters", main = true), Action("new", "Send as a New Enquiry"))))
    }

    // ---- Entity, country, phone ------------------------------------------------

    private suspend fun askEntity() {
        val abroad = read?.place == "abroad"
        val c = ctx
        // On file already: said back to them, not asked. "Change" asks it.
        if (c != null && c.entity.isNotEmpty() && "entity" !in offered && !abroad) {
            offered += "entity"
            entity = c.entity
            say(Para.Text("You are on file as " + (if (c.entity == "business") "a business" + (if (c.company.isNotEmpty()) ", ${c.company}" else "") else "an individual")
                + ", so I will not ask you that again."), prompt = Step.ENTITY)
            answer(Step.ENTITY, if (c.entity == "business") "Business" else "Individual")
            return next()
        }
        if (!asked(Step.ENTITY)) {
            say(Para.Text("Are you asking as a business, an individual or an NRI?" + if (abroad) " You mentioned living outside India, so NRI may be the one." else ""), prompt = Step.ENTITY)
        }
        dock(Dock.Entity(if (abroad) "nri" else ""))
    }

    fun chooseEntity(v: String) = go {
        entity = v
        answer(Step.ENTITY, ENTITY_LABEL[v] ?: v)
        dock(Dock.Waiting("…"))
        next()
    }

    private suspend fun askCountry() {
        if (!asked(Step.COUNTRY)) {
            say(Para.Text("Where are you writing from? It decides the hour you can be called, and which consular route a Power of Attorney has to take."), prompt = Step.COUNTRY)
        }
        dock(Dock.Country)
    }

    fun chooseCountry(code: String) = go {
        country = code
        answer(Step.COUNTRY, t.countries.firstOrNull { it.code == code }?.name ?: code)
        dock(Dock.Waiting("…"))
        next()
    }

    fun otherCountry(raw: String) {
        val v = raw.trim()
        if (v.isEmpty()) { err("Please tell us the country or city."); return }
        if (!Regex("^[\\p{L}\\p{Zs}.,'’()-]+$").matches(v)) { err("Just the name of the country or city, please: letters only."); return }
        go {
            country = "OTHER"; countryOther = v
            answer(Step.COUNTRY, v)
            dock(Dock.Waiting("…"))
            next()
        }
    }

    private suspend fun askPhone() {
        val c = ctx
        if (c != null && c.phone.length == 10 && entity != "nri" && "phone" !in offered) {
            if (!asked(Step.PHONE)) {
                say(Para.Text("Shall the advocates call you on **${shown(c.phone)}**, the number on your file, if the matter needs a call?"), prompt = Step.PHONE)
            }
            dock(Dock.Actions(listOf(Action("phone_yes", "Yes, That Number", main = true), Action("phone_other", "A Different Number"))))
            return
        }
        if (!asked(Step.PHONE)) {
            say(Para.Text(if (entity == "nri")
                "What number can the advocates reach you on? Give it with your country code. They will dial it as written, or reply by email if the hour is wrong where you are."
                else "What number can the advocates reach you on? They call before they write, if the matter needs it."), prompt = Step.PHONE)
        }
        dock(Dock.Phone)
    }

    val nri get() = entity == "nri"
    val dial get() = t.countries.firstOrNull { it.code == country }?.dial

    /** What is wrong with a number, in words that say what to change; "" when nothing is. */
    fun phoneProblem(raw: String): String {
        var d = raw.filter { it.isDigit() }
        if (nri) {
            if (d.isEmpty()) return "Please give a number the advocates can reach you on, with your country code."
            if (d.length < 8) return "That looks too short for an international number. Please include the country code."
            if (d.length > 15) return "That is longer than any phone number can be. Please check it."
            return ""
        }
        if (Regex("^\\s*(\\+|00)(?!\\s*91)").containsMatchIn(raw)) return "That looks like an overseas number. If you are outside India, change your answer above to NRI."
        if (d.length > 10 && d.startsWith("91")) d = d.drop(2)
        d = d.trimStart('0')
        if (d.isEmpty()) return "Please give a mobile number the advocates can reach you on."
        if (d.length < 10) return "That is ${d.length} digit${if (d.length == 1) "" else "s"}. An Indian mobile number has ten."
        if (d.length > 10) return "That is ${d.length} digits. An Indian mobile number has ten."
        if (d.first() !in '6'..'9') return "Indian mobile numbers start with 6, 7, 8 or 9. Is this a landline?"
        return ""
    }

    fun givePhone(raw: String) {
        val p = phoneProblem(raw)
        if (p.isNotEmpty()) { err(p); return }
        go {
            if (nri) {
                phone = raw.trim()
                answer(Step.PHONE, phone!!)
            } else {
                var d = raw.filter { it.isDigit() }
                if (d.length > 10 && d.startsWith("91")) d = d.drop(2)
                d = d.trimStart('0')
                phone = d
                answer(Step.PHONE, shown(d))
            }
            next()
        }
    }

    fun noPhone() = go {
        phone = ""
        answer(Step.PHONE, "I would rather not give a number")
        next()
    }

    // ---- Actions (the page's dockActions) ---------------------------------------

    fun act(id: String) {
        when (id) {
            "matters" -> _events.tryEmit(DeskEvent.OpenMatters)
            "new" -> go { route = "new"; answer(Step.ROUTE, "Send it as a new enquiry"); dock(Dock.Waiting("…")); next() }
            "phone_yes" -> go { val c = ctx ?: return@go; phone = c.phone; answer(Step.PHONE, shown(c.phone)); next() }
            "phone_other" -> { offered += "phone"; dock(Dock.Phone) }
            "try" -> go { send() }
            "seen" -> _events.tryEmit(consultationId?.let { DeskEvent.OpenConsultation(it) } ?: DeskEvent.OpenMatters)
            "again" -> go { restart() }
        }
    }

    // ---- Attachments --------------------------------------------------------------

    fun addFiles(uris: List<Uri>) {
        viewModelScope.launch {
            _ui.update { it.copy(error = "") }
            for (u in uris) {
                val f = repo.read(u) ?: run { err("That file could not be read."); return@launch }
                val p = fileProblem(f)
                if (p.isNotEmpty()) { err(p); break }
                _ui.update { it.copy(files = it.files + f) }
            }
        }
    }

    fun removeFile(i: Int) = _ui.update { it.copy(files = it.files.filterIndexed { j, _ -> j != i }) }

    private fun fileProblem(f: DeskFile): String {
        val ext = f.name.substringAfterLast('.', "").lowercase()
        val have = _ui.value.files
        if (ext !in t.documentTypes) return "\"${f.name}\" is not a file type we can take (${t.documentTypes.joinToString(", ") { it.uppercase() }})."
        if (f.size > t.maxFileSize) return "\"${f.name}\" is larger than ${mb(t.maxFileSize)}."
        if (have.size >= t.maxFiles) return "Up to ${t.maxFiles} files can be attached."
        if (t.maxTotalSize > 0 && have.sumOf { it.size } + f.size > t.maxTotalSize) return "Your attachments would come to more than ${mb(t.maxTotalSize)} altogether."
        if (have.any { it.name == f.name && it.size == f.size }) return "\"${f.name}\" is already attached."
        return ""
    }

    // ---- Sending (lexon Funnel_orchestrator, via desk/enquiry) ------------------------

    private suspend fun send() {
        if (sending) return
        sending = true
        _ui.update { it.copy(locked = true) }
        dock(Dock.Waiting("Sending — please keep this open."))
        say(Para.Stages(0, false))
        val stagesId = _ui.value.lines.last().id
        val stager = viewModelScope.launch {
            for ((i, at) in listOf(0L, 900L, 2200L, 3800L).withIndex()) {
                delay(if (i == 0) 0 else at - listOf(0L, 900L, 2200L, 3800L)[i - 1])
                setStages(stagesId, i, false)
            }
            delay(9_000 - 3_800)
            dock(Dock.Waiting("Still going. The chamber is slow to answer just now — please keep this open."))
        }
        val r = repo.send(fullText(), entity.orEmpty(), phone.orEmpty(), country, countryOther, reading, sessionHash, _ui.value.files, t)
        stager.cancel()
        sending = false
        when (r) {
            is Sent.Delivered -> {
                setStages(stagesId, 3, true)
                consultationId = r.consultationId
                delivered(r.email)
            }
            is Sent.Saved -> {
                dropLine(stagesId)
                _ui.update { it.copy(locked = false) }
                say(Para.Text("**Your enquiry is saved.** The consultation fee of **${r.displayPrice}** sends it to the advocates. Nothing has been charged yet, and nothing is sent until it is."))
                dock(Dock.Actions(listOf(Action("again", "Ask Something Else"))))
            }
            is Sent.Refused -> {
                dropLine(stagesId)
                _ui.update { it.copy(locked = false) }
                sessionHash = r.sessionHash ?: sessionHash
                if (r.errors.isNotEmpty()) fieldErrors(r.errors)
                else failed("We could not send that. " + r.message.ifBlank { "Something went wrong at our end. Please try again." })
            }
            is Sent.Failed -> {
                dropLine(stagesId)
                _ui.update { it.copy(locked = false) }
                if (r.timedOut) {
                    // Not "try again": the enquiry may be on a desk already.
                    say(Para.Text("This is taking longer than it should. Your enquiry may already have reached the advocates."),
                        Para.Text("Before sending it again, look at your matters: if it is there, the advocates already have it and there is nothing more for you to do."), note = true, warn = true)
                    dock(Dock.Actions(listOf(Action("matters", "See Whether It Arrived", main = true), Action("try", "Send It Again"))))
                } else {
                    failed(if (r.message.isNotBlank()) "We could not send that. ${r.message}" else "We could not send that. Please check your connection and try again.")
                }
            }
        }
    }

    private fun setStages(id: Long, active: Int, done: Boolean) = _ui.update { u ->
        u.copy(lines = u.lines.map { if (it.id == id) it.copy(paras = listOf(Para.Stages(active, done))) else it })
    }

    private fun dropLine(id: Long) = _ui.update { u -> u.copy(lines = u.lines.filterNot { it.id == id }) }

    private suspend fun failed(msg: String) {
        say(Para.Text(msg), note = true, warn = true)
        dock(Dock.Actions(listOf(Action("try", "Try Again", main = true))))
    }

    /** A server complaint goes back to the question it is about. */
    private suspend fun fieldErrors(errors: Map<String, String>) {
        val owner = mapOf("enquiry_text" to Step.QUESTION, "attachments" to Step.QUESTION, "entity_type" to Step.ENTITY, "country" to Step.COUNTRY, "phone" to Step.PHONE)
        val target = ORDER.firstOrNull { k -> errors.keys.any { owner[it] == k } } ?: Step.PHONE
        rewind(target) { say(Para.Text(errors.values.joinToString(" ")), note = true, warn = true) }
    }

    private suspend fun delivered(email: String) {
        say(Para.Seal, Para.Text("**Your enquiry is with the advocates.**"),
            Para.Text("They will read what you have written, and usually reply within 30 minutes, to **${email.ifBlank { "your email" }}**."))
        dock(Dock.Actions(listOf(Action("seen", "See Your Matters", main = true), Action("again", "Ask Something Else"))))
    }

    private suspend fun restart() {
        text = ""; more = null; vetted = null; ask = ""; area = ""; read = null; reading = ""; qFiles = 0; docs = null
        route = null; entity = null; country = null; countryOther = ""; phone = null; sessionHash = null; consultationId = null
        offered.clear(); said.clear(); again = true; gibs = 0; vetRun++; reread = null
        _ui.update { it.copy(lines = emptyList(), files = emptyList(), locked = false, error = "", opened = true) }
        next()
    }

    // ---- Change (the page's rewind) ------------------------------------------------

    fun change(step: Step) {
        if (_ui.value.locked) return
        go { rewind(step) {} }
    }

    private suspend fun rewind(step: Step, then: suspend () -> Unit) {
        val i = ORDER.indexOf(step)
        val prior = text
        ORDER.drop(i).forEach { k ->
            when (k) {
                Step.QUESTION -> { text = ""; vetted = null; read = null; reading = ""; area = ""; vetRun++ }
                Step.CLARIFY -> { more = null; reread = null }
                Step.DOCS -> docs = null
                Step.ROUTE -> route = null
                Step.ENTITY -> { entity = null; if (k == step) offered += "entity" }
                Step.COUNTRY -> { country = null; countryOther = "" }
                Step.PHONE -> { phone = null; if (k == step) offered += "phone" }
            }
        }
        _ui.update { u ->
            val at = u.lines.indexOfFirst { it.who == Who.ME && it.step == step }
            u.copy(lines = if (at >= 0) u.lines.take(at) else u.lines,
                prefill = if (step == Step.QUESTION) prior else u.prefill,
                prefillTick = if (step == Step.QUESTION) u.prefillTick + 1 else u.prefillTick)
        }
        then()
        next()
    }

    // ---- Words --------------------------------------------------------------------

    companion object {
        val AREA = mapOf(
            "contract" to "a contract matter", "property" to "a property matter", "family" to "a family matter",
            "employment" to "an employment matter", "corporate" to "a company matter", "banking" to "a banking and finance matter",
            "consumer" to "a consumer matter", "criminal" to "a criminal matter", "civil" to "a civil dispute", "tax" to "a tax matter",
            "ip" to "an intellectual property matter",
        )
        val ENTITY_LABEL = mapOf("business" to "Business", "individual" to "Individual", "nri" to "NRI")
        const val ASK_VAGUE = "Could you tell me a little more? What has happened, who is involved, and what would you like the advocate to do?"
        const val ASK_NOT_LEGAL = "The advocates can help with the legal side of things: a dispute, a document, a notice, or a right you want to enforce. Is there a legal question here you could tell me about?"

        /** Plainly not words (the page's mashed()): deliberately narrow. */
        fun mashed(text: String): Boolean {
            val bare = text.replace(Regex("\\s"), "")
            if (Regex("(.)\\1{5,}").containsMatchIn(bare)) return true
            if (Regex("(qwert|asdf|zxcv|hjkl)", RegexOption.IGNORE_CASE).containsMatchIn(text)) return true
            val latin = Regex("[A-Za-z]{3,}").findAll(text).map { it.value }.toList()
            if (latin.size >= 4) {
                val noVowel = latin.count { it.length >= 5 && !Regex("[aeiouy]", RegexOption.IGNORE_CASE).containsMatchIn(it) }
                if (noVowel.toDouble() / latin.size >= 0.5) return true
            }
            val words = Regex("[\\p{L}\\p{M}\\p{N}]").findAll(text).count()
            return words < bare.length * 0.6
        }

        fun mb(bytes: Long): String {
            val m = bytes / 1_048_576.0
            return if (m >= 1) (if (m % 1.0 == 0.0) "${m.toInt()} MB" else "%.1f MB".format(m)) else "${(bytes / 1024.0).toInt().coerceAtLeast(1)} KB"
        }

        fun shown(ten: String) = "+91 " + ten.take(5) + " " + ten.drop(5)

        fun names(list: List<String>) = if (list.size < 2) list.firstOrNull().orEmpty() else list.dropLast(1).joinToString(", ") + " and " + list.last()

        fun day(iso: String): String {
            val d = runCatching { LocalDate.parse(iso.take(10)) }.getOrNull() ?: return ""
            val today = LocalDate.now()
            return when (d) {
                today -> "today"
                today.plusDays(1) -> "tomorrow"
                today.minusDays(1) -> "yesterday"
                else -> d.format(DateTimeFormatter.ofPattern(if (d.year == today.year) "d MMMM" else "d MMMM yyyy", Locale.ENGLISH))
            }
        }
    }
}
