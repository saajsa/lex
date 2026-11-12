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

enum class Step { QUESTION, CLARIFY, DOCS, ROUTE, ENTITY, COUNTRY, PHONE, DEADLINE }

/** A guest's allowance, as the website's (cases CASES_DESK_GUEST_*). */
const val GUEST_TURNS = 2

/** The line that asks a guest to sign in: "your matter" when they come straight to the sign-in (Discuss A Matter) and have asked nothing, "your question" once they have. */
fun signInLine(asked: Boolean): String =
    "To go on, please sign in with Google. Your ${if (asked) "question" else "matter"} then goes to the advocates, and they write back to you."


/** Said when the guest desk could not be reached or sent no reply: never an invented answer. */
const val GUEST_UNREACHABLE = "Lex Intelligence could not be reached. Please try again."

/** What follows it: send the same question again (not counted), or sign in with Google. */
internal val GUEST_RETRY_ACTIONS = listOf(Action("guest_retry", "Try Again", main = true), Action("guest_signin", "Sign In With Google"))
const val GUEST_CHARS = 140

private val ORDER = Step.values().toList()

sealed interface Para {
    /** A paragraph; **bold** marks what the page sets in <b>. */
    data class Text(val s: String, val flag: Boolean = false) : Para
    data class Bullets(val items: List<String>) : Para
    data class Facts(val rows: List<Pair<String, String>>) : Para
    /** "Taking your enquiry in…" and its four stages. */
    data class Stages(val active: Int, val done: Boolean) : Para
    data object Seal : Para
    /** Opening an account: the summary, with Change, Replace and Add while it is the question. */
    data class Summary(val groups: List<AcctGroup>, val live: Boolean = true) : Para
    /** "Your registration is with the firm." between what came before and what follows. */
    data class Divider(val s: String) : Para
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
    /** Not signed in: a short message (two, of 140 characters). */
    data object Guest : Dock
    /** The Google sign-in, in the conversation. */
    data object SignIn : Dock
    /** Opening an account: what answers the server's turn. */
    data class Acct(val turn: AcctTurn) : Dock
    /** A hearing or deadline coming up, in the client's own words: the words and a date (REQ-017). */
    data object Deadline : Dock
    /** The Review step (REQ-017 Phase 2): the desk's brief of the enquiry, and the client's own words it came from. */
    data class Review(val brief: DeskBrief, val original: String) : Dock
    /** A document going up and being read (up to a few minutes). */
    data class AcctReading(val label: String, val since: Long) : Dock
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
    /** Opening an account (lexon Enrolment), and the firm's note pinned above the dock. */
    val acct: Boolean = false,
    val acctNote: String = "",
    /** The conversation is a counsel registration (REQ-014): its own footer, its own closing. */
    val counsel: Boolean = false,
    /** A guest's desk: whether the server's desk takes counsel (google_config counsel_desk, readable before sign-in): the intro's Register As Counsel chip. */
    val guestCounselDesk: Boolean = false,
)

sealed interface DeskEvent {
    data object OpenMatters : DeskEvent
    data class OpenConsultation(val id: Int) : DeskEvent
    data class OpenUrl(val url: String) : DeskEvent
    /** Use The Form Instead: the registration wizard. */
    data object OpenForm : DeskEvent
    /** The counsel registration screen (the chip href app:lead_counsel). */
    data object OpenCounselForm : DeskEvent
    /** The desk opened the Counsel workspace: the person is now the client's token; the app opens as for any sign-in. */
    data object OpenedAsClient : DeskEvent
    /** Back To Home (LeadHome, or the client's Home). */
    data object Home : DeskEvent
    /** Sign In / My Matters: the client sign-in. */
    data object SignIn : DeskEvent
}

class DeskViewModel(app: Application, saved: androidx.lifecycle.SavedStateHandle) : AndroidViewModel(app) {
    /** Opened to open an account (the route's account=1): the enquiry is not asked first. */
    private val accountEntry = saved.get<String>("account") == "1"
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
    /** A hearing or deadline the client flags: null while unasked, ("", "") for none. */
    private var deadline: Pair<String, String>? = null
    /** The brief: made once for each exact text (BriefCache); decided when the client has sent it, sent their own words, or no brief came (REQ-017 Phase 2). */
    private val briefCache = BriefCache()
    private var briefDecided = false
    private var briefToSend: DeskBrief? = null
    private var briefToken = ""
    private var sessionHash: String? = null
    private var consultationId: Int? = null
    /** A saved enquiry waiting for its fee: where to reply, and what it costs. */
    private var savedEmail = ""
    private var savedPrice = ""
    /** The fee's invoice is open in the browser: look again on coming back. */
    private var awaitingInvoice = false
    private val offered = mutableSetOf<String>()
    private val said = mutableSetOf<String>()
    private var again = false
    private var gibs = 0
    private var vetRun = 0
    private var reread: Deferred<Unit>? = null
    private var sending = false
    private var nextId = 0L

    private val flow = Mutex()

    // Opening an account (declared before init, which may start it).
    private var turn: AcctTurn? = null
    private var acctWish = false
    private var pendingConfirm: AcctChip? = null
    private var uploading: Job? = null
    private var acctStarted = false
    /** The conversation is counsel's (the turns ask with intent=counsel), the guest asked for it, and the workspace has opened. */
    private var counselMode = false
    private var counselWish = false
    private var openedAsClient = false
    /** The clarifying question was the last thing a guest was asked: their answer is not one of the device's two messages. */
    private var guestAnswering = false
    private var guestFree = 0
    private var guestForms: List<Pair<String, String>> = emptyList()

    /*
     * A guest (owner, 2026-10-20): not signed in, as the website's visitor is.
     * Two short messages, answered by the desk (guest/desk), then the Google
     * sign-in in the conversation; the desk then goes on as for anyone signed
     * in. The firm's terms are a signed-in call, so they wait for the sign-in.
     */
    var guest: Boolean = kotlinx.coroutines.runBlocking {
        com.legumlex.client.data.auth.TokenStore(app).read().isNullOrBlank()
    }
        private set
    private val guestTurns = mutableListOf<Pair<String, String>>()
    private var greeted = false
    private var signinSaid = false

    private var terms: Job = if (guest) Job().apply { complete() } else loadTerms()

    init {
        if (guest) viewModelScope.launch {
            val on = runCatching { com.legumlex.client.data.api.ApiClient.get(getApplication()).googleConfig()["counsel_desk"] == true }.getOrDefault(false)
            _ui.update { it.copy(guestCounselDesk = on) }
        }
    }

    private fun loadTerms(): Job = viewModelScope.launch {
        // One quiet retry: the fee line must come from the funnel, never a default.
        val t = repo.terms() ?: run { delay(1_500); repo.terms() }
        t?.let { v -> _ui.update { it.copy(terms = v, termsLoaded = true) } }
    }

    /**
     * The desk goes no further than the question until the firm's terms (the
     * fee, the file rules) have arrived: without them it would quote nothing,
     * or the wrong thing. On failure it says so and offers Try Again.
     */
    private suspend fun termsReady(): Boolean {
        terms.join()
        if (_ui.value.termsLoaded) return true
        say(Para.Text("The desk could not connect to the firm. Please check your connection and try again."), note = true, warn = true)
        dock(Dock.Actions(listOf(Action("terms", "Try Again", main = true))))
        return false
    }

    init { go { if (accountEntry) { acctStarted = true; enterAccount(true) } else next() } }

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
            guest -> askGuest()
            text.isEmpty() -> askQuestion()
            vetted == null -> vet()
            vetted == "clarify" && more == null -> askClarify()
            docs == null -> askDocs()
            !welcomed -> welcome()
            route == null && needsRoute() -> askRoute()
            entity == null -> askEntity()
            entity == "nri" && country == null -> askCountry()
            phone == null -> askPhone()
            deadline == null -> askDeadline()
            !briefDecided -> makeBrief()
            else -> send()
        }
    }

    private fun hour(): String {
        val h = LocalTime.now().hour
        return if (h < 12) "Good morning" else if (h < 17) "Good afternoon" else "Good evening"
    }

    private suspend fun askQuestion() {
        if (!asked(Step.QUESTION)) {
            if (guestTurns.isNotEmpty()) {
                say(Para.Text("Thank you. Now, in your own words: what has happened, who is involved, and what you would like the advocate to do?"), prompt = Step.QUESTION)
            } else if (again) {
                say(Para.Text("What else would you like to ask?"), prompt = Step.QUESTION)
            } else {
                say(Para.Text(hour() + ", and welcome to Legum Lex."))
                say(Para.Text("What would you like to ask? In your own words: what has happened, who is involved, and what you would like the advocate to do."), prompt = Step.QUESTION)
            }
        }
        dock(Dock.Question)
    }

    // ---- A guest (guest/desk) -------------------------------------------------

    private val guestLeft get() = GUEST_TURNS - (guestTurns.count { it.first == "guest" } - guestFree)

    private suspend fun askGuest() {
        if (!greeted) {
            greeted = true
            // Lex's introduction, the owner's words: the first thing a guest is told. The card before the conversation gives way to it.
            say(Para.Text(LEX_INTRO))
        }
        if (guestLeft <= 0) signInDock() else dock(Dock.Guest)
    }

    fun submitGuest(raw: String) {
        val v = raw.trim()
        if (v.length < 2) { err("Write a few words first."); return }
        if (v.length > GUEST_CHARS) { err("At most $GUEST_CHARS characters, please."); return }
        go { sendGuest(v, retry = false) }
    }

    /** The guest's question that could not be sent, for Try Again. */
    private var guestRetry: String? = null

    private suspend fun sendGuest(v: String, retry: Boolean) {
        _ui.update { it.copy(opened = true) }
        guestTurns += "guest" to v
        // An answer to the clarifying question (Client or Advocate) is not one of the two messages.
        val answering = guestAnswering
        if (answering) guestFree++
        guestAnswering = false
        if (retry) _ui.update { it.copy(error = "") }
        else _ui.update { it.copy(lines = it.lines + Line(++nextId, Who.ME, listOf(Para.Text(v))), error = "") }
        dock(Dock.Waiting("…"))
        val r = repo.guest(guestTurns)
        if (r == null) {
            // The desk was not reached (or sent nothing): no invented answer, and the try is not one of the two messages.
            guestTurns.removeAt(guestTurns.lastIndex)
            if (answering) { guestFree--; guestAnswering = true }
            guestRetry = v
            say(Para.Text(GUEST_UNREACHABLE), note = true, warn = true)
            dock(Dock.Actions(GUEST_RETRY_ACTIONS))
            return
        }
        val reply = r.reply
        val toSignIn = r.signIn || (guestLeft <= 0 && r.clarify.not() && r.forms.isEmpty())
        if (r.account) acctWish = true
        // A clear counsel wish: after signing in the conversation starts as counsel's.
        if (r.counsel) { acctWish = true; counselWish = true }
        guestTurns += "desk" to reply
        if (toSignIn && reply.contains("sign in", ignoreCase = true)) signinSaid = true
        say(Para.Text(reply))
        when {
            r.forms.isNotEmpty() -> { guestForms = r.forms; dock(Dock.Actions(r.forms.mapIndexed { i, f -> Action("guest_form_$i", f.first, main = i == 0) })) }
            // The clarifying question keeps the text box for the answer.
            r.clarify -> { guestAnswering = true; dock(Dock.Guest) }
            toSignIn -> signInDock()
            else -> dock(Dock.Guest)
        }
    }

    /** "Sign in with Google" from the guest's box, before the two are used. */
    fun guestSignIn() = go { _ui.update { it.copy(opened = true) }; signInDock() }

    private suspend fun signInDock() {
        if (!signinSaid) {
            signinSaid = true
            say(Para.Text(signInLine(asked = guestTurns.any { it.first == "guest" })))
        }
        dock(Dock.SignIn)
    }

    /** Signed in (a lead or a client): the desk goes on, with the firm's terms. */
    fun signedIn() {
        guest = false
        terms = loadTerms()
        // Asked for an account before signing in: straight to it.
        if (acctWish) {
            acctWish = false
            go {
                // A Google address that is already a client's: their account is open.
                if (!com.legumlex.client.data.auth.SessionRole.isLead) {
                    say(Para.Text(
                        if (counselWish) "Your account with the firm is already open. To add a Counsel workspace, open Profile and choose Add Counsel Workspace."
                        else "Your account with the firm is already open. To open another account, such as one for a business, please write to the firm from My Matters.",
                    ))
                    dock(Dock.Actions(listOf(Action("acct_home", "Back To Home", main = true))))
                } else if (counselWish) enterCounsel(false, said = true) else enterAccount(false)
            }
            return
        }
        go { next() }
    }

    fun signInProblem(message: String) = err(message)

    fun submitQuestion(raw: String) {
        val v = raw.trim()
        // A short ask for an account is known by its words (Enrolment::wish).
        if ((t.accountDesk || t.counselDesk) && v.length < 20) {
            when (deskWish(v, t.wish, t.wishCounsel, t.wishClient, t.counselDesk, t.clientSignUp)) {
                WishKind.Client -> if (t.accountDesk) { go { _ui.update { it.copy(opened = true) }; me(v); offer() }; return }
                WishKind.Counsel -> { go { _ui.update { it.copy(opened = true) }; me(v); fixed(t.counselOffer) }; return }
                WishKind.Unsure -> { go { _ui.update { it.copy(opened = true) }; me(v); fixed(t.clarify.firstOrNull()) }; return }
                WishKind.CounselForm -> t.counselForm?.let { f -> go { _ui.update { it.copy(opened = true) }; me(v); fixed(f) }; return }
                WishKind.None -> Unit
            }
        }
        if (v.length < t.minChars) {
            err(if (v.isNotEmpty()) "Please add a sentence or two describing what has happened." else "Tell the advocate what happened, in your own words.")
            return
        }
        if (mashed(v)) { err("That could not be understood. Please describe, in plain words, what has happened."); return }
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

    private suspend fun verdict(reading: DeskReading?) {
        // A reading that failed or was skipped is no finding (REQ-017): neither its urgency nor its area is used or shown, and sending goes on.
        val r = reading?.takeIf { deskReadingUsable(it.readingState) }
        // The reader offers an account; it never assumes one.
        if (t.accountDesk && r?.intent == "open_account" && r.urgency == "none") { offer(); return }
        var v = r?.verdict ?: "clear"
        area = r?.area.orEmpty()
        keep(r)
        // A second "gibberish" goes through: the check can be wrong, and it must
        // never be the reason someone cannot reach the firm.
        if (v == "gibberish" && ++gibs >= 2) v = "clear"
        if (v == "gibberish") {
            rewind(Step.QUESTION) { say(Para.Text("That could not be understood. Please describe, in a sentence or two, what has happened."), note = true, warn = true) }
            return
        }
        if (v == "vague" || v == "not_legal") {
            vetted = "clarify"
            ask = if (v == "vague") r?.followUp?.takeIf { it.isNotBlank() } ?: ASK_VAGUE else ASK_NOT_LEGAL
        } else if (v == "clear" && r != null && r.askReason in ASK_REASONS && r.followUp.isNotBlank()) {
            // One question on a clear enquiry too, when its answer would change what the advocate does next (the time limit, the forum). It is
            // asked once, in the same way, and "Send it as it is" stays; the re-read after the answer never asks again.
            vetted = "clarify"
            ask = r.followUp
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
        if (mashed(v)) { err("That could not be understood. Please use a few plain words."); return }
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
        if (!termsReady()) return
        val list = read?.documents.orEmpty()
        if (list.isEmpty() || !t.takesFiles || qFiles > 0) { docs = true; return next() }
        if (!asked(Step.DOCS)) {
            say(
                Para.Text(thanks() + " If you have them to hand, these would help counsel read it properly:"),
                Para.Bullets(list),
                Para.Text("Attach the documents you have now, or continue without them. They may be sent later."),
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
        if (!termsReady()) return
        welcomed = true
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
            Para.Text("This will be added to your file, for the advocates who know your matters."),
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
        if (v.isEmpty()) { err("Please enter the country or city."); return }
        if (!Regex("^[\\p{L}\\p{Zs}.,'’()-]+$").matches(v)) { err("Please enter the country or city name, using letters only."); return }
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

    // ---- The Review step (REQ-017 Phase 2) -------------------------------------------------------

    /** The desk's brief of the whole enquiry, asked once for this exact text. Anything but a read brief, an error or a timeout: no Review, and it goes as before. */
    private suspend fun makeBrief() {
        val whole = fullText()
        var r = briefCache.get(whole)
        if (r == null) {
            dock(Dock.Waiting("Getting what you wrote ready for the advocates…"))
            r = repo.brief(whole)
            briefCache.put(whole, r)
        }
        if (!r.reviewable) { briefDecided = true; briefToSend = null; briefToken = ""; return next() }
        say(Para.Text("Here is what I will tell the advocates, in your own words. Please check it, change anything that is wrong, and send it."))
        dock(Dock.Review(r.brief!!, whole))
    }

    /** Send with the brief as it stands (after any edit); the token is the desk's own, unchanged. */
    fun sendBrief(brief: DeskBrief) = go {
        val r = briefCache.get(fullText()) ?: return@go
        briefToSend = brief; briefToken = r.token; briefDecided = true
        me("Send To The Firm")
        dock(Dock.Waiting("…"))
        next()
    }

    /** Send the client's own words alone: no brief, no token. */
    fun sendOwnWords() = go {
        briefToSend = null; briefToken = ""; briefDecided = true
        me("Send My Own Words Instead")
        dock(Dock.Waiting("…"))
        next()
    }

    // ---- A hearing or a deadline, in the client's own words (REQ-017; as the website's step) ----

    private suspend fun askDeadline() {
        if (!asked(Step.DEADLINE)) {
            say(Para.Text("Is there a hearing or a deadline coming up that the advocates should know about? Tell us in your own words, with the date if you have one."), prompt = Step.DEADLINE)
        }
        dock(Dock.Actions(listOf(Action("deadline_yes", "Yes, There Is One", main = true), Action("deadline_no", "No, Nothing Yet"))))
    }

    /** The words and the date (YYYY-MM-DD or empty); a problem is said under the box and nothing moves on. */
    fun giveDeadline(text: String, date: String) {
        val p = deadlineProblem(text, date)
        if (p.isNotEmpty()) { err(p); return }
        go {
            deadline = cleanDeadlineText(text) to date.trim()
            answer(Step.DEADLINE, deadlineShown(text, date))
            dock(Dock.Waiting("…"))
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
            "deadline_yes" -> dock(Dock.Deadline)
            "deadline_no" -> go { deadline = "" to ""; answer(Step.DEADLINE, "Nothing yet"); dock(Dock.Waiting("…")); next() }
            "try" -> go { send() }
            // A guest's question the desk could not take: send the same one again, or go to the Google sign-in.
            "guest_retry" -> go { val q = guestRetry ?: return@go; guestRetry = null; sendGuest(q, retry = true) }
            "guest_signin" -> go { signInDock() }
            "seen" -> _events.tryEmit(consultationId?.let { DeskEvent.OpenConsultation(it) } ?: DeskEvent.OpenMatters)
            "again" -> go { restart() }
            "pay" -> go { pay() }
            "paid" -> go { checkPaid(asked = true) }
            "discard" -> go { discard() }
            "terms" -> go { dock(Dock.Waiting("…")); if (!terms.isActive) terms = loadTerms(); next() }
            // A guest's two forms (still unclear after two asks): Open An Account goes on as the client's (Google first); the counsel form is its own screen.
            "guest_form_0", "guest_form_1" -> go {
                val f = guestForms.getOrNull(id.removePrefix("guest_form_").toInt()) ?: return@go
                me(f.first)
                when (chipTarget(f.second)) {
                    ChipTarget.CounselForm -> _events.tryEmit(DeskEvent.OpenCounselForm)
                    else -> enterAccount(false, said = true)
                }
            }
            "acct_offer_yes" -> go { me("Open My Account"); enterAccount(false, said = true) }
            "acct_offer_no" -> go { me("No, I Have A Question"); leaveAccount() }
            "acct_retry" -> go { me("Try Again"); fetchTurn(false) }
            "acct_home" -> _events.tryEmit(if (openedAsClient) DeskEvent.OpenedAsClient else DeskEvent.Home)
            "acct_ok" -> pendingConfirm?.let { c -> pendingConfirm = null; c.confirm?.let { acctAnswer(it.ok.id, bubble = it.ok.label) } }
            "acct_cancel" -> go { me(pendingConfirm?.confirm?.cancel ?: "Keep Going"); pendingConfirm = null; turn?.let { dock(Dock.Acct(it)) } }
        }
    }

    // ---- Opening an account (lexon Enrolment, via lead/onboarding/turn) ---------------
    //
    // The server computes every turn; this draws it. Reached by the chip, the
    // route's account=1 (LeadHome), an offer (the phrase check or the
    // reader), or a guest's ask before signing in.


    /** Straight into opening an account (the intro's chip, LeadHome). */
    fun startAccount(explicit: Boolean = true) {
        if (acctStarted && explicit) return
        acctStarted = true
        go { enterAccount(explicit) }
    }

    /** Straight into registering as counsel (the intro's chip). */
    fun startCounsel(explicit: Boolean = true) {
        if (acctStarted && explicit) return
        acctStarted = true
        go { enterCounsel(explicit) }
    }

    private suspend fun enterCounsel(explicit: Boolean, said: Boolean = false) {
        counselMode = true
        _ui.update { it.copy(opened = true, acct = true, counsel = true) }
        if (explicit && !said) me("I would like to register as counsel")
        if (guest) {
            // Signed out: Google first; the counsel conversation starts when the sign-in is done.
            acctWish = true
            counselWish = true
            signinSaid = true
            say(Para.Text("I can help you register as counsel. Please sign in with Google first; then I will ask for your Bar Council certificate and your PAN Card."))
            dock(Dock.SignIn)
            return
        }
        fetchTurn(false)
    }

    /** A fixed turn the server sent in the desk config (the counsel offer, the clarifying question, the forms): its lines, then its chips. */
    private suspend fun fixed(turn: AcctTurn?) {
        if (turn == null) { say(Para.Text("That did not go through.")); dock(Dock.Question); return }
        turn.say.forEach { say(Para.Text(it)) }
        dock(Dock.Acct(turn))
    }

    private suspend fun enterAccount(explicit: Boolean, said: Boolean = false) {
        counselMode = false
        _ui.update { it.copy(opened = true, acct = true, counsel = false) }
        if (explicit && !said) me("I would like to open an account")
        if (guest) {
            // Signed out (the sign-in screen's Open An Account): Google first, said as the website says it.
            acctWish = true
            signinSaid = true
            say(Para.Text("I can help you open an account with the firm. Please sign in with Google first; then I will ask for your identity documents."))
            dock(Dock.SignIn)
            return
        }
        fetchTurn(!explicit && !said)
    }

    private suspend fun fetchTurn(open: Boolean) {
        dock(Dock.Waiting("…"))
        _ui.update { it.copy(typing = true) }
        val t = repo.acctTurn(open, counselMode)
        _ui.update { it.copy(typing = false) }
        render(t, null)
    }

    private suspend fun offer() {
        say(Para.Text(t.offer.ifBlank { "It sounds as though you would like to open an account with the firm. Shall we do that now?" }))
        dock(Dock.Actions(listOf(Action("acct_offer_yes", "Open My Account", main = true), Action("acct_offer_no", "No, I Have A Question"))))
    }

    /** Back to the enquiry (Ask A Question, Not Now, No, I Have A Question). */
    private suspend fun leaveAccount() {
        uploading?.cancel()
        _ui.update { it.copy(acct = false, acctNote = "", counsel = false) }
        counselMode = false; counselWish = false
        text = ""; vetted = null; read = null; reading = ""
        say(Para.Text("Of course. What would you like to ask?"), prompt = Step.QUESTION)
        next()
    }

    private fun me(s: String) {
        _ui.update { it.copy(lines = it.lines + Line(++nextId, Who.ME, listOf(Para.Text(s))), error = "") }
    }

    private suspend fun render(t: AcctTurn?, ok: Boolean?) {
        if (t == null) {
            say(Para.Text("That did not go through."), note = true, warn = true)
            dock(Dock.Actions(listOf(Action("acct_retry", "Try Again", main = true))))
            return
        }
        val prev = turn
        turn = t
        // An earlier summary can no longer be answered.
        _ui.update { u -> u.copy(lines = u.lines.map { l -> if (l.paras.any { it is Para.Summary }) l.copy(paras = l.paras.map { p -> if (p is Para.Summary) p.copy(live = false) else p }) else l }) }
        val repeat = ok == false && prev?.step == t.step && !t.stale
        if (!repeat) t.say.forEach { say(Para.Text(it)) }
        val ownError = t.dock is AcctDock.Card || t.dock is AcctDock.Code
        if (t.error.isNotBlank() && !ownError && (repeat || t.error !in t.say)) say(Para.Text(t.error), note = true, warn = true)
        _ui.update { it.copy(acctNote = t.note) }
        when (val d = t.dock) {
            is AcctDock.Confirm -> if (d.rows.isNotEmpty()) say(Para.Facts(d.rows.map { it.label to it.value }))
            is AcctDock.Summary -> say(Para.Summary(d.groups))
            else -> Unit
        }
        if (t.sent) {
            say(Para.Divider(t.divider))
            // A client's registration waits for the firm; a counsel's workspace is open at once (the token was switched to the client's).
            if (!openedAsClient && t.track != "counsel") com.legumlex.client.data.auth.TokenStore(getApplication()).saveLeadStatus("awaiting_approval")
        }
        dock(Dock.Acct(t))
    }

    fun acctChip(c: AcctChip) {
        if (c.confirm != null) {
            go { me(c.label); say(Para.Text(c.confirm.say)); pendingConfirm = c
                dock(Dock.Actions(listOf(Action("acct_ok", c.confirm.ok.label, main = true), Action("acct_cancel", c.confirm.cancel)))) }
            return
        }
        c.href?.let { h ->
            when (val target = chipTarget(h)) {
                ChipTarget.ClientForm -> _events.tryEmit(DeskEvent.OpenForm)
                ChipTarget.CounselForm -> _events.tryEmit(DeskEvent.OpenCounselForm)
                is ChipTarget.Url -> _events.tryEmit(DeskEvent.OpenUrl(target.url))
            }
            return
        }
        when (c.id) {
            "check_again", "try_again" -> go { me(c.label); fetchTurn(false) }
            "ask", "decline", "not_now" -> go { me(c.label); leaveAccount() }
            "home" -> _events.tryEmit(if (openedAsClient) DeskEvent.OpenedAsClient else DeskEvent.Home)
            "sign_in" -> _events.tryEmit(DeskEvent.SignIn)
            "open_account" -> go { me(c.label); fetchTurn(false) }
            // The offers and the clarifying question are the server's fixed turns, answered here.
            "open_counsel", "clarify:counsel" -> go { me(c.label); enterCounsel(false, said = true) }
            "clarify:client" -> go { me(c.label); enterAccount(false, said = true) }
            "google" -> go { signInDock() }
            "upload_again" -> go { me(c.label); turn?.let { dock(Dock.Acct(it.copy(chips = emptyList()))) } }
            else -> acctAnswer(c.id, bubble = c.label)
        }
    }

    fun acctAnswer(id: String, fields: Map<String, String> = emptyMap(), code: String = "", bubble: String = "") = go {
        val step = turn?.step ?: return@go
        if (bubble.isNotEmpty()) me(bubble)
        dock(Dock.Waiting("…"))
        _ui.update { it.copy(typing = true) }
        val r = repo.acctAnswer(step, id, fields, code)
        _ui.update { it.copy(typing = false) }
        render(r?.second, r?.first)
    }

    /** A card's fields, said back as "Label: value" (the code is never shown). */
    fun acctFields(fields: Map<String, String>, labels: Map<String, String>, button: String) {
        val shown = fields.filterValues { it.isNotBlank() }.map { (k, v) -> (labels[k] ?: k) + ": " + v }.joinToString("\n")
        acctAnswer(if (button == "Save") "save" else "continue", fields, bubble = shown.ifBlank { button })
    }

    fun acctCode(code: String) = acctAnswer("code", code = code, bubble = "Code entered")

    fun acctUpload(uri: Uri) {
        val t = turn ?: return
        val d = t.dock as? AcctDock.Upload ?: return
        uploading = viewModelScope.launch {
            flow.withLock {
                val name = runCatching {
                    getApplication<Application>().contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                }.getOrNull() ?: d.label
                me(name)
                dock(Dock.AcctReading(d.label, System.currentTimeMillis()))
                val next = repo.acctUpload(d.type, uri)
                render(next, if (next != null && next.error.isNotBlank()) false else null)
            }
        }
    }

    /** Stop Waiting: the reading is given up; its late result is discarded on the server. */
    fun acctStop() {
        uploading?.cancel()
        uploading = null
        acctAnswer("stop", bubble = "Stop Waiting")
    }

    fun acctSubmit() = go {
        val t = turn ?: return@go
        val d = t.dock as? AcctDock.Final ?: return@go
        me(d.button)
        dock(Dock.Waiting(if (d.button == "Send Again") "Sending it again…" else if (counselMode) "Opening your Counsel workspace…" else "Opening your account…"))
        val sub = repo.acctSubmit(t.step)
        // A counsel registration opens the workspace at once: the lead's token gives way to the client's, as lead/counsel/submit does.
        sub?.opened?.let { o ->
            val app = getApplication<Application>()
            com.legumlex.client.data.auth.AuthRepository(com.legumlex.client.data.api.ApiClient.get(app), com.legumlex.client.data.auth.TokenStore(app), app.cacheDir).switchedTo(o.token, o.clientId, o.contactId)
            openedAsClient = true
        }
        val next = sub?.turn
        render(next, if (next?.sent == true || sub?.opened != null) true else false)
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
        // The key is made once for this enquiry as composed and sent on every attempt: a retry after a timeout or a restart is the same enquiry.
        val key = repo.submissionKeys.forEnquiry(SubmissionKeys.fingerprint(fullText(), _ui.value.files.map { it.name to it.size }))
        val flag = deadline
        val r = repo.send(fullText(), entity.orEmpty(), phone.orEmpty(), country, countryOther, reading, sessionHash, _ui.value.files, t,
            submissionKey = key, deadlineFlag = cleanDeadlineText(flag?.first.orEmpty()), deadlineDate = flag?.second.orEmpty(),
            brief = briefToSend, briefToken = briefToken)
        stager.cancel()
        sending = false
        when (r) {
            is Sent.Delivered -> {
                setStages(stagesId, 3, true)
                consultationId = r.consultationId
                delivered(r.email, r.enquiryNumber)
            }
            is Sent.Saved -> {
                dropLine(stagesId)
                _ui.update { it.copy(locked = false) }
                sessionHash = r.sessionHash.ifBlank { sessionHash }
                savedEmail = r.email; savedPrice = r.displayPrice
                say(Para.Text("**Your enquiry is saved.** The consultation fee of **${r.displayPrice}** sends it to the advocates. Nothing has been charged yet, and nothing is sent until it is."))
                payActions()
            }
            is Sent.Refused -> {
                dropLine(stagesId)
                _ui.update { it.copy(locked = false) }
                sessionHash = r.sessionHash ?: sessionHash
                if (r.errors.isNotEmpty()) fieldErrors(r.errors)
                else failed("Your message could not be sent. " + r.message.ifBlank { "The request could not be completed. Please try again." })
            }
            is Sent.Failed -> {
                dropLine(stagesId)
                _ui.update { it.copy(locked = false) }
                if (r.timedOut) {
                    // Not "try again": the enquiry may be on a desk already.
                    say(Para.Text("This is taking longer than it should. Your enquiry may already have reached the advocates."),
                        Para.Text("Before sending it again, please check your matters. If it appears there, the advocates have received it and no further action is required."), note = true, warn = true)
                    dock(Dock.Actions(listOf(Action("matters", "See Whether It Arrived", main = true), Action("try", "Send It Again"))))
                } else {
                    failed(if (r.message.isNotBlank()) "Your message could not be sent. ${r.message}" else "Your message could not be sent. Please check your connection and try again.")
                }
            }
        }
    }

    // ---- The fee (lexon Funnel_orchestrator, via desk/pay) --------------------------

    private fun payActions() = dock(Dock.Actions(listOf(
        Action("pay", "Pay ${savedPrice.ifBlank { "the Fee" }} and Send", main = true),
        Action("discard", "Set It Aside"),
    )))

    private suspend fun pay() {
        val hash = sessionHash ?: return
        dock(Dock.Waiting("Opening the invoice…"))
        repo.pay(hash)
            .onSuccess { p ->
                when (p) {
                    is PayStart.Sent -> { consultationId = p.consultationId; delivered(savedEmail) }
                    is PayStart.Invoice -> {
                        awaitingInvoice = true
                        _events.tryEmit(DeskEvent.OpenUrl(p.url))
                        if (!said.contains("invoice")) {
                            said += "invoice"
                            say(Para.Text("The fee is an invoice from the firm. Once it is paid, your enquiry goes to the advocates."))
                        }
                        dock(Dock.Actions(listOf(Action("paid", "I Have Paid", main = true), Action("pay", "Pay The Fee"), Action("discard", "Set It Aside"))))
                    }
                }
            }
            .onFailure { say(Para.Text(it.message ?: "The invoice could not be opened. Please try again."), note = true, warn = true); payActions() }
    }

    /** Back from the browser: if the invoice is paid, the enquiry has gone. */
    fun resumed() { if (awaitingInvoice) go { checkPaid(asked = false) } }

    private suspend fun checkPaid(asked: Boolean) {
        val hash = sessionHash ?: return
        if (asked) dock(Dock.Waiting("Checking the payment…"))
        repo.pay(hash).onFailure { e ->
            if (asked) {
                say(Para.Text(e.message ?: "The payment could not be checked. Please try again."), note = true, warn = true)
                dock(Dock.Actions(listOf(Action("paid", "I Have Paid", main = true), Action("pay", "Pay The Fee"), Action("discard", "Set It Aside"))))
            }
        }.onSuccess { p ->
            if (p is PayStart.Sent) {
                awaitingInvoice = false
                consultationId = p.consultationId
                delivered(savedEmail)
            } else if (asked) {
                say(Para.Text("The payment has not yet been confirmed. If you have just paid, please wait a minute and try again."), note = true)
                dock(Dock.Actions(listOf(Action("paid", "I Have Paid", main = true), Action("pay", "Pay The Fee"), Action("discard", "Set It Aside"))))
            }
        }
    }

    private suspend fun discard() {
        val hash = sessionHash ?: return
        dock(Dock.Waiting("…"))
        repo.discard(hash)
            .onSuccess {
                awaitingInvoice = false
                say(Para.Text("Your enquiry has been set aside. Nothing was charged."))
                dock(Dock.Actions(listOf(Action("again", "Ask Something Else", main = true))))
            }
            .onFailure { e ->
                say(Para.Text(e.message ?: "The enquiry could not be set aside. Please try again."), note = true, warn = true)
                payActions()
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
        val owner = mapOf("enquiry_text" to Step.QUESTION, "attachments" to Step.QUESTION, "entity_type" to Step.ENTITY, "country" to Step.COUNTRY, "phone" to Step.PHONE,
            "deadline_flag" to Step.DEADLINE, "deadline_date" to Step.DEADLINE)
        val target = ORDER.firstOrNull { k -> errors.keys.any { owner[it] == k } } ?: Step.PHONE
        rewind(target) { say(Para.Text(errors.values.joinToString(" ")), note = true, warn = true) }
    }

    private suspend fun delivered(email: String, enquiryNumber: String? = null) {
        // Sent: the key has done its work, and the next enquiry is a new one.
        repo.submissionKeys.clear()
        // The receipt names no advocate: none has it yet, and the client is told who does when it is assigned.
        say(Para.Seal, Para.Text("**${receiptLine(enquiryNumber)}**"),
            Para.Text("The advocates read what you have written, and usually reply within 30 minutes, to **${email.ifBlank { "your email" }}**."))
        dock(Dock.Actions(listOf(Action("seen", "See Your Matters", main = true), Action("again", "Ask Something Else"))))
    }

    private suspend fun restart() {
        text = ""; more = null; vetted = null; ask = ""; area = ""; read = null; reading = ""; qFiles = 0; docs = null
        route = null; entity = null; country = null; countryOther = ""; phone = null; deadline = null; briefDecided = false; briefToSend = null; briefToken = ""; sessionHash = null; consultationId = null
        repo.submissionKeys.clear()
        savedEmail = ""; savedPrice = ""; awaitingInvoice = false
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
        // Whatever was changed, the brief is decided again (from the cache if the text is the same, from the desk if it is not).
        briefDecided = false; briefToSend = null; briefToken = ""
        ORDER.drop(i).forEach { k ->
            when (k) {
                Step.QUESTION -> { text = ""; vetted = null; read = null; reading = ""; area = ""; vetRun++ }
                Step.CLARIFY -> { more = null; reread = null }
                Step.DOCS -> docs = null
                Step.ROUTE -> route = null
                Step.ENTITY -> { entity = null; if (k == step) offered += "entity" }
                Step.COUNTRY -> { country = null; countryOther = "" }
                Step.PHONE -> { phone = null; if (k == step) offered += "phone" }
                Step.DEADLINE -> deadline = null
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
