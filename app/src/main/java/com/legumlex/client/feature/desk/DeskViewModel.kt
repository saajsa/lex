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

enum class Step { QUESTION, CLARIFY, GATHER, DOCS, ROUTE, ENTITY, COUNTRY, PHONE, DEADLINE }

/** A guest's allowance, as the website's (cases CASES_DESK_GUEST_*). */
const val GUEST_TURNS = 2

/** The line that asks a guest to sign in: "your matter" when they come straight to the sign-in (Discuss A Matter) and have asked nothing, "your question" once they have. */
fun signInLine(asked: Boolean): String =
    if (asked) "Please sign in with Google. Your question will then reach the advocates, who will get back to you."
    else "The advocates can help. Sign in with Google and tell them what happened."


/** Said when the guest desk could not be reached or sent no reply: never an invented answer. */
const val GUEST_UNREACHABLE = "Lex Intelligence could not be reached. Please try again."

/** What follows it: send the same question again (not counted), or sign in with Google. */
internal val GUEST_RETRY_ACTIONS = listOf(Action("guest_retry", "Try Again", main = true), Action("guest_signin", "Sign In With Google"))
/** The most characters a guest's first message may hold until the server says (`guest_chars`, owner 2026-11-14: 1,000). */
const val GUEST_CHARS = 1000

private val ORDER = Step.values().toList()

sealed interface Para {
    /** A paragraph; **bold** marks what the page sets in <b>. */
    data class Text(val s: String, val flag: Boolean = false) : Para
    data class Bullets(val items: List<String>) : Para
    data class Facts(val rows: List<Pair<String, String>>) : Para
    /** "Taking your enquiry in…" and its four stages. */
    data class Stages(
        val active: Int, val done: Boolean, val title: String? = null, val steps: List<String> = emptyList(),
        /** The completed forms (stage_done_title, stage_done_1..4); empty from an older server, which keeps today's text. */
        val doneTitle: String? = null, val doneSteps: List<String> = emptyList(),
        /** The held state of a booking waiting for its fee: the second step is the fee to pay, drawn as an empty ring, not running. */
        val held: Boolean = false,
    ) : Para
    data object Seal : Para
    /** "Message sent to an advocate" with its small animation, inside Lex's message; the caption and the time are the server's words (REQ-017 017.30). */
    data class Sent(val caption: String, val time: String? = null) : Para
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
    /** The disclaimer behind a translated fixed line's small "Translated" tag (REQ-017 017.31); null on English and on lines the model wrote. */
    val footer: DeskFooter? = null,
)

/** A button of the desk. [sub] is a second line inside it (the fee under a choice), from the server's own block. */
data class Action(val id: String, val label: String, val main: Boolean = false, val sub: String = "")

sealed interface Dock {
    data class Waiting(val label: String) : Dock
    data object Question : Dock
    data object Clarify : Dock
    /** One of Lex's follow-up questions before sending (REQ-017 017.20): an answer, or Send Now. */
    data object Gather : Dock
    /** A follow-up question answered with chips or a calendar (REQ-045), or typed after Something Else, Type It ([writing]). */
    data class Input(val question: String, val input: GatherInput, val writing: Boolean = false, val clarify: Boolean = false) : Dock
    data object Docs : Dock
    data class Actions(val list: List<Action>) : Dock
    data class Entity(val hint: String) : Dock
    data object Country : Dock
    data object Phone : Dock
    /** Not signed in: a short message (two, of 140 characters). */
    data object Guest : Dock
    /** The consultation-first choice (an advocate was asked for, or the close's choice): the cards, a box for a question, and Not Now. source: insist | close. */
    data class Cards(val block: ChoiceBlock, val source: String) : Dock
    /** After the consultation is chosen: one optional line about the matter, and Skip. */
    data object Matter : Dock
    /** One of Lex's questions with kinds of help to tick (the goal is unclear): the question, the options, which are ticked, and whether the visitor is writing their own beside them. */
    data class Options(val question: String, val options: List<String>, val picks: Set<Int>, val writing: Boolean) : Dock
    /** The Agreement Health Check is on offer (REQ-026): its button above the message box, which stays open for questions about the service. */
    data class Route(val route: RouteInfo) : Dock
    /** The WhatsApp code of the enquiry's number step (REQ-018): a six-digit box, Confirm, Send Code Again, Send By SMS Instead, Change Number. */
    data class Code(val info: PhoneCodeUi) : Dock
    /** The Google sign-in, in the conversation. */
    data object SignIn : Dock
    /** The quiet wait after a direct-talk enquiry is sent, and the hand-over to the advocate who has it (REQ-017 017.30). */
    data class Advocate(val state: AdvocateWait) : Dock
    /** Opening an account: what answers the server's turn. */
    data class Acct(val turn: AcctTurn) : Dock
    /** A hearing or deadline coming up, in the client's own words: the words and a date (REQ-017). */
    data object Deadline : Dock
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
    /** The guest box's limit: the server's `guest_chars` once it has answered, else GUEST_CHARS. */
    val guestChars: Int = GUEST_CHARS,
    /** The line under the box (the web's rcp-disclaim): the consultation's own once the full consultation is chosen (R9), none while a message is unread (R20a), else the written enquiry's. */
    val footerDirect: Boolean = false,
    val directFee: String = "",
    val footerUnread: Boolean = false,
    /** Counts up when the first box's message has gone: the box empties (a failed or refused send does not count). */
    val draftClearTick: Int = 0,
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
    /** The language's fixed labels (buttons, placeholders, screen-reader texts) the screens draw with; English when no book is in force. */
    val labels: LineBook = LineBook.English,
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
    /** The Agreement Health Check route's button: the app's own Agreement Check. */
    data object OpenAgreementCheck : DeskEvent
    /** The portal route's button (signed agreements): My Agreements. */
    data object OpenAgreements : DeskEvent
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
    /** REQ-045 (D7): the widget for [ask], only when [ask] is the reading's own follow_up. */
    private var askInput: GatherInput? = null
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
    private var sessionHash: String? = null
    private var consultationId: Int? = null
    /** A saved enquiry waiting for its fee: where to reply, and what it costs. */
    private var savedEmail = ""
    /** The `direct` the submit answered with when the enquiry fee is asked first (state awaiting_payment, via enquiry_payment): kept for after the payment. */
    private var savedDirect: DirectBooking? = null
    private var savedPrice = ""
    /** The fee's invoice is open in the browser: look again on coming back. */
    private var awaitingInvoice = false
    private val offered = mutableSetOf<String>()
    private val said = mutableSetOf<String>()
    private var again = false
    private var gibs = 0
    /** The server informed an advocate during this conversation (desk/check advocate_informed): its end is the direct-talk waiting state. */
    private var informed = false
    /** Lex's closing turn (REQ-023) has been taken for this enquiry (said, or skipped on an error). */
    private var closeDone = false

    // ---- A verified mobile number (REQ-018) ----
    /** The number was proved in this conversation (a code confirmed, or the server said it was already verified). */
    private var phoneProved = false
    /** The number step is asked again whatever is on file (a refusal at send, or Change Number). */
    private var forcePhone = false
    private var codeUi: PhoneCodeUi? = null

    private fun needsVerifyNow() = needsPhoneVerify(t.phoneVerify, phoneProved) && !phone.isNullOrEmpty()

    // ---- A booked Direct Consultation (REQ-020 020.4) ----
    /** The server offered a direct consultation (desk/check or desk/gather) and the visitor has not been asked yet. */
    private var pendingOffer: DirectOfferInfo? = null
    /** The person already has an open direct consultation (direct_open): told so, and carried to its wait. */
    private var pendingOpen: DirectOpen? = null
    private var openBooking: DirectOpen? = null
    /** Asked once per conversation, whatever the answer. */
    private var directAsked = false
    /** The visitor said Yes, Book It: the enquiry is sent with direct=1. */
    private var directBook = false
    private var bookedPoll: Job? = null
    private var awaitingBooked = false
    private var bookedRef = ""
    private val bookedSaid = mutableSetOf<String>()
    private var pollJob: Job? = null
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
    /** The unsent first question, kept with the conversation so closing the desk does not cost it (the web's lex_desk_draft). */
    private val draftPrefs = app.getSharedPreferences("lex_desk_draft", android.content.Context.MODE_PRIVATE)
    /** Per account (the draft of one person never opens for another); a guest's is its own. */
    private val draftKey: String = kotlinx.coroutines.runBlocking { com.legumlex.client.data.auth.TokenStore(app).accountKey() }
    /** Kept from 3 characters, with no expiry, until it is sent or a new enquiry starts; it survives the app closing (the web's lex_desk_draft). */
    var draftText: String = draftPrefs.getString(draftKey, "").orEmpty()
        set(v) {
            field = v
            draftPrefs.edit().apply { if (v.trim().length >= DESK_DRAFT_MIN) putString(draftKey, v) else remove(draftKey) }.apply()
        }
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
        dock(Dock.Actions(listOf(Action("terms", lbl("ui_try_again", "Try Again"), main = true))))
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

    private suspend fun say(vararg paras: Para, prompt: Step? = null, note: Boolean = false, warn: Boolean = false, footer: DeskFooter? = null) {
        val chars = paras.sumOf { (it as? Para.Text)?.s?.length ?: 40 }
        _ui.update { it.copy(typing = true) }
        delay((350L + chars * 6L).coerceAtMost(1_100L))
        _ui.update { it.copy(typing = false, lines = it.lines + Line(++nextId, if (note) Who.NOTE else Who.DESK, paras.toList(), prompt = prompt, warn = warn, footer = footer)) }
    }

    // ---- Language (REQ-017 017.26) ----------------------------------------------------

    /** The fixed lines in the reading's language: English (no lines) until a reading says otherwise, and again if /lines cannot be had. */
    private var book: LineBook = LineBook.English
    /** The language the last answer named (whether or not its lines could be fetched): sent on the follow-up calls so the server keeps it after one-word answers. */
    private var convLang = "en"
    private val books = mutableMapOf<String, LineBook>()

    /** The English lines come from the server too (desk/lines?lang=en); the constants in the app are only the offline fallback. A guest has the same from guest/desk/lines (no token). */
    init {
        viewModelScope.launch {
            val en = repo.lines("en", guest) ?: return@launch
            books["en"] = en
            if (book.lang == "en" && !book.active) { book = en; _ui.update { it.copy(labels = en) } }
        }
    }

    /** Take the language an answer names: from the first reading that is not English on, the fixed lines come from /lines (fetched once per language). */
    private suspend fun adopt(lang: String) {
        convLang = lang
        if (lang == book.lang) return
        book = languageSwitch(lang, books[lang]) { repo.lines(lang, guest)?.also { books[lang] = it } }
        _ui.update { it.copy(labels = book) }
        repo.lang = langField(lang).orEmpty()
    }

    /** The fixed line [key] in this language, filled; null in English or when the book lacks it (the English line is then said). */
    private fun tl(key: String, vars: Map<String, String> = emptyMap()): String? = book.line(key, vars)

    /** A button, chip, wait or error label (REQ-017 017.31): the book's ui_/wait_/err_ key as given, else the English. */
    private fun lbl(key: String, english: String): String = book.fragment(key) ?: english

    /** The footer for a line the app said from the book. */
    private fun ft(translated: Boolean): DeskFooter? = if (translated) book.footer else null

    /** Say a fixed line: from the book when there is one (with its footer), else the English. */
    private suspend fun sayKey(key: String, english: String, vars: Map<String, String> = emptyMap(), prompt: Step? = null, note: Boolean = false, warn: Boolean = false) {
        val t = tl(key, vars)
        say(Para.Text(t ?: english), prompt = prompt, note = note, warn = warn, footer = ft(t != null))
    }


    private fun answer(step: Step, s: String, files: List<String> = emptyList()) {
        _ui.update { it.copy(lines = it.lines + Line(++nextId, Who.ME, listOf(Para.Text(s)), step = step, files = files), error = "") }
    }

    private fun asked(step: Step) = _ui.value.lines.any { it.prompt == step }
    /** Runs [block] (a call to the desk's AI) and, if it is still waiting after SLOW_AFTER_MS, says so on the pending line: [label], in the dock's own style. */
    private suspend fun <T> withSlowNote(label: String, block: suspend () -> T): T = kotlinx.coroutines.coroutineScope {
        val note = launch {
            delay(SLOW_AFTER_MS)
            if (_ui.value.dock is Dock.Waiting) _ui.update { it.copy(dock = Dock.Waiting(label)) }
        }
        try { block() } finally { note.cancel() }
    }

    private fun dock(d: Dock) = _ui.update { it.copy(dock = d, error = "", footerDirect = directBook, directFee = offerFee, footerUnread = unreadShown || routeOffered in ROUTE_KEYS) }

    /** The full consultation's fee as the server last said it (the offer, or the choice's card), for the line under the box. */
    private var offerFee = ""
    private var unreadShown = false
    private fun err(s: String) = _ui.update { it.copy(error = s) }

    // ---- The order ---------------------------------------------------------

    private suspend fun next() {
        when {
            guest -> askGuest()
            text.isEmpty() -> askQuestion()
            vetted == null -> vet()
            pendingOffer != null -> askDirectOffer()
            pendingOpen != null -> askDirectOpen()
            vetted == "clarify" && more == null -> askClarify()
            !gatherDone -> gather()
            !closeDone -> askClose()
            docs == null -> askDocs()
            !welcomed -> welcome()
            route == null && needsRoute() -> askRoute()
            entity == null -> askEntity()
            entity == "nri" && country == null -> askCountry()
            phone == null -> askPhone()
            needsVerifyNow() -> verifyPhone()
            deadline == null -> askDeadline()
            else -> send()
        }
    }

    private fun hour(): String {
        val h = LocalTime.now().hour
        return if (h < 12) "Good morning" else if (h < 17) "Good afternoon" else "Good evening"
    }

    /** The guest conversation is read once, after sign-in (the web's askResume). */
    private var resumed = false

    /**
     * Lex's first turn after sign-in, from desk/resume: a story goes straight on; a question is answered and the matter asked for; a service route says its line.
     * Returns true when it has taken the turn (the conversation goes on from there); false when it could not be read, and the plain rule below decides.
     */
    private suspend fun resumeFromGuest(): Boolean {
        resumed = true
        dock(Dock.Waiting("…"))
        val r = withSlowNote(lbl("wait_moment", ONE_MOMENT)) { repo.resume(guestTurns, langField(convLang)) } ?: return false
        adopt(r.lang)
        when {
            // They insisted on an advocate in the guest chat: the consultation first (slice 2 draws the cards); the matter is still asked for here.
            r.insist != null -> { directTalk = guestTurns.firstOrNull { it.first == "guest" }?.second; say(Para.Text(r.insist.lead), prompt = Step.QUESTION); dock(Dock.Question); return true }
            // A service (an agreement review, a copy, help with an agreement): Lex's line; the service's own button comes with the routes.
            r.kind == "route" && r.route != null -> { routeOffer(r.route, fromCheck = false, prompt = Step.QUESTION); return true }
            r.kind == "route" && r.reply.isNotBlank() -> { say(Para.Text(r.reply), prompt = Step.QUESTION); dock(Dock.Question); return true }
            // A question or an intent: answered, and the matter is asked for. Only a story becomes the enquiry.
            r.kind == "question" && r.reply.isNotBlank() -> { say(Para.Text(r.reply), prompt = Step.QUESTION); dock(Dock.Question); return true }
            else -> return false
        }
    }

    private suspend fun askQuestion() {
        if (!asked(Step.QUESTION)) {
            if (!resumed && guestTurns.any { it.first == "guest" && it.second.isNotBlank() }) {
                // The web greets first (Welcome back, or Thank you for choosing Legum Lex), then reads the guest conversation.
                terms.join()
                if (_ui.value.termsLoaded) greetBack(withNotes = false)
                if (resumeFromGuest()) return
            }
            val story = guestStory(guestTurns)
            if (story != null) {
                // Signed in after writing as a guest (web parity): their own words are the enquiry; Lex does not ask again.
                text = story
                qFiles = _ui.value.files.size
                // No announcement here (the web removed it): the next turn simply goes on.
                _ui.update { it.copy(opened = true) }
                guestTurns.clear()
                dock(Dock.Waiting("…"))
                next()
                return
            }
            if (guestTurns.isNotEmpty()) {
                sayKey("q_guest", "Tell me what happened, in your own words.", prompt = Step.QUESTION)
            } else if (again) {
                sayKey("q_again", "What would you like to ask?", prompt = Step.QUESTION)
            } else {
                // One welcome, at the start, and then the question (as the website's): "Welcome back" with what is on file when the firm knows them,
                // else the time of day's welcome. The reading's own notes (an urgent matter) come later, once there is a reading.
                terms.join()
                if (_ui.value.termsLoaded) greetBack(withNotes = false) else say(Para.Text(hour() + ", and welcome to Legum Lex."))
                sayKey("q_welcomed", ASK_HELP, prompt = Step.QUESTION)
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
        if (v.length < 2) { err(lbl("err_few_words", "Write a few words first.")); return }
        val most = _ui.value.guestChars
        if (v.length > most) { err("At most $most characters, please."); return }
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
        val r = withSlowNote(lbl("wait_moment", ONE_MOMENT)) { repo.guest(guestTurns) }
        if (r == null) {
            // The desk was not reached (or sent nothing): no invented answer, and the try is not one of the two messages.
            guestTurns.removeAt(guestTurns.lastIndex)
            if (answering) { guestFree--; guestAnswering = true }
            guestRetry = v
            sayKey("api_unavailable", GUEST_UNREACHABLE, note = true, warn = true)
            dock(Dock.Actions(listOf(Action("guest_retry", lbl("ui_try_again", "Try Again"), main = true), Action("guest_signin", lbl("ui_signin_google", "Sign In With Google")))))
            return
        }
        adopt(r.tongue.lang)
        r.guestChars?.let { n -> _ui.update { it.copy(guestChars = n) } }
        if (r.person) directTalk = v
        val reply = r.reply
        val toSignIn = r.signIn || (guestLeft <= 0 && r.clarify.not() && r.forms.isEmpty())
        if (r.account) acctWish = true
        // A clear counsel wish: after signing in the conversation starts as counsel's.
        if (r.counsel) { acctWish = true; counselWish = true }
        guestTurns += "desk" to reply
        if (toSignIn && reply.contains("sign in", ignoreCase = true)) signinSaid = true
        say(Para.Text(reply), footer = r.tongue.lineFooter)
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
            val asked = guestTurns.any { it.first == "guest" }
            sayKey(if (asked) "signin_go" else "signin_default", signInLine(asked = asked))
        }
        dock(Dock.SignIn)
    }

    /** Signed in (a lead or a client): the desk goes on, with the firm's terms. */
    /** The first name the sign-in gave, for the greeting when the firm has no record of them yet. */
    private var signedFirst = ""

    fun signedIn(email: String = "", name: String = "") {
        signedFirst = name.trim().substringBefore(' ')
        // The web's answer('signin'): the visitor's own bubble, "Signed in as <email>", before anything else is said.
        if (email.isNotBlank()) go { me("Signed in as $email") }
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
        if (mashed(v)) { err(lbl("err_mashed", "That could not be understood. Please describe, in plain words, what has happened.")); return }
        go {
            draftText = ""
            _ui.update { it.copy(opened = true, draftClearTick = it.draftClearTick + 1) }
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
        dock(Dock.Waiting(lbl("wait_reading", "Reading what you have written…")))
        _ui.update { it.copy(typing = true) }
        val started = System.currentTimeMillis()
        val r = withSlowNote(lbl("wait_moment", ONE_MOMENT)) { repo.check(text, langField(convLang), insist = insisting(), consultWish = consultWish, routeCtx = routeOffered.takeIf { it == "ahc" }, routeLast = routeLast.takeIf { routeOffered == "ahc" }) }
        // A beat of reading even when the answer is instant.
        delay((800 - (System.currentTimeMillis() - started)).coerceAtLeast(0))
        _ui.update { it.copy(typing = false) }
        if (run == vetRun) verdict(r)
    }

    // ---- The non-matter branch and the cards (REQ-029 slice 2) ---------------------------------------------

    /** Which service the desk is offering now (ahc, portal_docs or clarify), and Lex's line for it: a follow-up while it is on screen keeps the route (route_ctx, route_last). */
    private var routeOffered = ""
    private var routeLast = ""

    /** A service route's line and its way in (the web's routeOffer / dockRoute). */
    private suspend fun routeOffer(route: RouteInfo, fromCheck: Boolean, prompt: Step? = null) {
        routeOffered = route.route; routeLast = route.line
        if (route.route == "clarify") {
            if (fromCheck) { backToQuestion(route.line, false); return }
            say(Para.Text(route.line), prompt = prompt); releaseQuestionBubble(); resetStory(); dock(Dock.Question); return
        }
        if (fromCheck) { releaseQuestionBubble(); resetStory() }
        val signedIn = !guest && !com.legumlex.client.data.auth.SessionRole.isLead
        say(Para.Text(if (route.route == "portal_docs" && signedIn && route.lineIn.isNotBlank()) route.lineIn else route.line), prompt = prompt)
        if (route.route == "ahc") { dock(Dock.Route(route)); return }
        // signed agreements: My Agreements for someone in the portal (a lead has no portal, so only the line); never a link to a file.
        val open = if (signedIn) listOf(Action("route_portal", route.buttonIn.ifBlank { "Open My Agreements" }, main = true)) else emptyList()
        dock(Dock.Actions(open + Action("route_else", lbl("ui_ask_else", "Ask Something Else"), main = open.isEmpty())))
    }

    /** How many times the matter has been asked for in this conversation: never the same words twice in a row (matterAsk). */
    private var matterAsks = 0
    /** The visitor has been shown the consultation cards, or asked for an advocate: later reads say so (insist=1, consult_wish=1). */
    private var cardsSeen = false
    private var consultWish = false
    private var cardsAgain = false
    /** What they wrote when the cards came up: the booking's own request if they book. */
    private var insistAsked = ""

    private fun routeButtonLabel() = ((_ui.value.dock as? Dock.Route)?.route?.button ?: "").ifBlank { lbl("ui_open_ahc", "Open Agreement Health Check") }

    private fun insisting() = "person" in said || directTalk != null || matterAsks > 0 || cardsSeen

    private fun nextMatterAsk(): String { val (key, en) = matterAskKey(matterAsks); matterAsks++; return tl(key) ?: en }

    /** Their message stays in the thread but is no longer "the question"; nothing it gathered carries to the next story (web resetStory). */
    private fun releaseQuestionBubble() = _ui.update { u ->
        val i = u.lines.indexOfLast { it.who == Who.ME && it.step == Step.QUESTION }
        if (i < 0) u else u.copy(lines = u.lines.toMutableList().also { it[i] = it[i].copy(step = null) })
    }

    private fun resetStory() {
        text = ""; vetted = null; qFiles = 0; read = null; reading = ""; area = ""; more = null; ask = ""; askInput = null
        gatherLog = emptyList(); gatherPending = null; gatherAsked = 0; gatherDone = false; gatherRan = false; closeDone = false; docs = null; vetRun++
        if (deadlineFromGather) { deadline = null; deadlineFromGather = false }
    }

    /** Lex answers, or asks for the matter, and the box opens again; the text is cleared: nothing here is the enquiry. */
    private suspend fun backToQuestion(line: String, keepTalk: Boolean) {
        if (keepTalk && directTalk == null) directTalk = text
        releaseQuestionBubble(); resetStory()
        say(Para.Text(line))
        next()
    }

    /** Someone asking for an advocate: the full consultation first, the written enquiry as the alternative (REQ-023 R10). */
    private suspend fun insistOffer(block: ChoiceBlock) {
        said += "person"; cardsSeen = true; consultWish = true
        block.cards.firstOrNull { it.key == "consultation" }?.price?.takeIf { it.isNotBlank() }?.let { offerFee = it }
        if (directTalk == null) directTalk = text.ifBlank { null }
        insistAsked = text
        releaseQuestionBubble(); resetStory(); cardsAgain = false
        if (block.lead.isNotBlank()) say(Para.Text(block.lead))
        dock(Dock.Cards(block, "insist"))
    }

    /** A card is picked (a button, or its words typed). */
    fun pickCard(key: String) = go { pickCardNow(key) }

    private suspend fun pickCardNow(key: String) {
        val cards = (_ui.value.dock as? Dock.Cards) ?: return
        val card = cards.block.cards.firstOrNull { it.key == key } ?: return
        me(card.button)
        directAsked = true
        if (cards.source == "close") { directBook = key == "consultation"; dock(Dock.Waiting("…")); next(); return }
        if (key == "consultation") { directBook = true; sayKey("ask_matter_optional", ASK_MATTER_OPTIONAL_EN); dock(Dock.Matter) }
        else { directBook = false; text = ""; vetted = null; say(Para.Text(tl("ask_matter_short") ?: ASK_MATTER_SHORT_EN)); next() }
    }

    /** The one optional line after choosing the consultation, or Skip: the booking's text is their own request, with the line after it. */
    fun matterGo(raw: String) {
        val v = raw.trim()
        if (v.length < 2) { err(lbl("err_few_words", "Write a few words first.")); return }
        go { me(v); finishInsist(v) }
    }
    fun matterSkip() = go { me(lbl("ui_skip", "Skip")); finishInsist("") }

    private suspend fun finishInsist(extraTyped: String) {
        val base = (guestStory(guestTurns) ?: directTalk ?: insistAsked).trim()
        var extra = extraTyped
        // The server answers a question in the optional line from the catalogue (who sees it, the fee, how the time is fixed), keeps the rest as their note, and says when it is urgent.
        if (extraTyped.isNotBlank()) {
            dock(Dock.Waiting("…"))
            val r = withSlowNote(lbl("wait_moment", ONE_MOMENT)) { repo.close(base.ifBlank { extraTyped }, langField(convLang), chose = "consultation", person = true, note = extraTyped) }
            if (r != null && r.booked) {
                if (r.bookingAnswer.isNotBlank()) say(Para.Text(r.bookingAnswer))
                extra = r.noteRest
                if (r.urgent) sayKey("urgent_marked", "I have marked it urgent for the advocate.")
            }
        }
        var t = base + (if (extra.isNotBlank()) "\n\n" + extra else "")
        if (t.length < 20) t = (t + " (I would like to speak to an advocate.)").trim()
        // The reading and the closing turn are not needed for a booking: it goes straight on to the details.
        text = t; vetted = "ok"; closeDone = true; docs = true; more = null; gatherDone = true; gatherRan = true
        dock(Dock.Waiting("…")); next()
    }

    /** Something typed under the cards (cardAsk): a choice in words picks the card; a question is answered and the cards come again once; a story leaves the cards. */
    fun cardText(raw: String) {
        val v = raw.trim()
        if (v.length < 2) return
        go {
            val c = (_ui.value.dock as? Dock.Cards) ?: return@go
            me(v)
            val pick = cardPickFromText(v)
            if (pick.isNotEmpty() && c.block.cards.any { it.key == pick }) { pickCardNow(pick); return@go }
            dock(Dock.Waiting("…"))
            val r = withSlowNote(lbl("wait_moment", ONE_MOMENT)) { repo.check(v, langField(convLang), insist = true, consultWish = true) }
            when {
                r?.insist != null -> { say(Para.Text(r.insist.lead)); if (!cardsAgain) { cardsAgain = true; dock(Dock.Cards(r.insist, c.source)) } else dock(c) }
                r?.busy == true -> { say(Para.Text(r.unreadLine.ifBlank { UNREAD_EN })); dock(c) }
                r != null && (r.legalInquiry == false || r.nonmatter || r.messageKind == "person_request") -> { if (r.reply.isNotBlank()) say(Para.Text(r.reply)); dock(c) }
                r == null -> dock(c)
                // A matter: the cards are set aside and it goes on as an enquiry; the choice is made at the close.
                c.source == "insist" -> { text = v; qFiles = 0; directBook = false; directAsked = false; verdict(r) }
                else -> { text = text + "\n\n" + v; closeDone = false; next() }
            }
        }
    }

    /** Not Now under the cards: it rests there, with the declined line and only Start A New Enquiry. */
    fun cardsNotNow() = go {
        me(lbl("ui_not_now", CLOSE_NOT_NOW))
        say(Para.Text(tl("insist_declined") ?: INSIST_DECLINED_EN))
        dock(Dock.Actions(listOf(Action("again", lbl("ui_start_new", CLOSE_START_NEW), main = true))))
    }

    /** One silent second reading when the server says the reading failed and a retry may work (retry_once); reset by any reading that was made. */
    private var unreadRetried = false

    private suspend fun verdict(reading: DeskReading?) {
        // R20a (REQ-023 F-017): the server did not read the message (a limit, or the reader is down). Nothing is offered, charged or sent: once more without a word when it
        // says a retry may work, else one honest line with the words kept, and Try Again or Start A New Enquiry.
        if (reading?.busy == true) {
            if (reading.retryOnce && !unreadRetried) { unreadRetried = true; vetted = null; return vet() }
            return unreadStop(reading)
        }
        unreadRetried = false
        // A reading that failed or was skipped is no finding (REQ-017): neither its urgency nor its area is used or shown, and sending goes on.
        val r = reading?.takeIf { deskReadingUsable(it.readingState) }
        // The reader offers an account; it never assumes one.
        if (t.accountDesk && r?.intent == "open_account" && r.urgency == "none") { offer(); return }
        adopt(r?.tongue?.lang ?: "en")
        // From the raw reading: a request for a person may come with a reading that was skipped, which is no finding but is still the visitor's words.
        if (reading?.directOffer != null && !directAsked) pendingOffer = reading.directOffer
        if (reading?.directOpen != null && !directAsked) pendingOpen = reading.directOpen
        // A service route (REQ-026): an agreement review, a signed copy, "help with an agreement". The route stays on offer only while the visitor is still on it.
        routeOffered = reading?.route?.route?.takeIf { it in setOf("ahc", "portal_docs", "clarify") }.orEmpty()
        if (reading?.route != null && routeOffered.isNotEmpty()) { keep(reading); routeOffer(reading.route, fromCheck = true); return }
        if (reading != null && (reading.wantsPerson || reading.messageKind == "person_request" || reading.needMatter || reading.insist != null)) consultWish = true
        // An explicit wish to speak to an advocate (a route of consultation): the consultation first, the cards, whatever the reading says about an agreement.
        if (reading?.insist != null && reading.route?.route == "consultation") { keep(reading); insistOffer(reading.insist); return }
        // Not an enquiry (only a request for a person, a service question, account help or a greeting): never the story, an offer, a fee or a send.
        if (reading != null) when (nonMatterTurn(reading)) {
            NonMatterTurn.Insist -> { keep(reading); insistOffer(reading.insist!!); return }
            NonMatterTurn.Answer -> { keep(reading); backToQuestion(reading.reply, false); return }
            NonMatterTurn.AskMatter -> { keep(reading); said += "person"; backToQuestion(nextMatterAsk(), true); return }
            NonMatterTurn.None -> Unit
        }
        if (reading?.insist != null) { keep(reading); insistOffer(reading.insist); return }
        if (reading?.needMatter == true) {
            said += "person"; keep(reading)
            vetted = "clarify"; ask = tl("person_need_matter") ?: PERSON_NEED_MATTER_EN; askInput = null
            next(); return
        }
        if (reading?.wantsPerson == true && "person" !in said) {
            said += "person"
            if (reading.advocateInformed) {
                // The server really notified an advocate: Lex says so, with the small "sent" animation (caption person_sent_caption and the time), and gathering goes on.
                informed = true
                val line = tl("person_informed")
                say(Para.Text(line ?: PERSON_INFORMED), Para.Sent(tl("person_sent_caption") ?: PERSON_SENT_CAPTION, LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))), footer = ft(line != null))
            } else sayKey("person_signed", PERSON_SIGNED)
        }
        var v = r?.verdict ?: "clear"
        area = r?.area.orEmpty()
        keep(r)
        // A second "gibberish" goes through: the check can be wrong, and it must
        // never be the reason someone cannot reach the firm.
        if (v == "gibberish" && ++gibs >= 2) v = "clear"
        if (v == "gibberish") {
            rewind(Step.QUESTION) { sayKey("gibberish", "I could not follow that. Could you say what happened in a line or two?", note = true, warn = true) }
            return
        }
        if (v == "vague" || v == "not_legal") {
            vetted = "clarify"
            ask = if (v == "vague") r?.followUp?.takeIf { it.isNotBlank() } ?: ASK_VAGUE else ASK_NOT_LEGAL
            askInput = if (v == "vague" && r?.followUp?.isNotBlank() == true) r.input else null
            askFooter = r?.tongue?.lineFooter
        } else if (v == "clear" && r != null && r.askReason in ASK_REASONS && r.followUp.isNotBlank()) {
            // One question on a clear enquiry too, when its answer would change what the advocate does next (the time limit, the forum). It is
            // asked once, in the same way, and "Send it as it is" stays; the re-read after the answer never asks again.
            vetted = "clarify"
            ask = r.followUp
            askInput = r.input
            askFooter = r.tongue.lineFooter
        } else {
            vetted = "ok"
        }
        next()
    }

    /** The unread answer's line (the server's own words), the text kept in the thread, and two ways on. Nothing is sent from here. */
    private suspend fun unreadStop(r: DeskReading) {
        vetted = null; read = null; reading = ""
        unreadShown = true
        // A normal Lex bubble, as the web's (unreadStop says it in a plain bubble), not a muted note.
        say(Para.Text(r.unreadLine.ifBlank { UNREAD_EN }))
        dock(Dock.Actions(listOf(
            Action("unread_try", lbl("ui_try_again", "Try Again"), main = true),
            Action("unread_new", lbl("ui_start_new", CLOSE_START_NEW)),
        )))
    }

    private fun keep(r: DeskReading?) {
        if (r == null || r.token.isEmpty()) return
        read = r
        reading = r.token
    }

    /** The footer of the clarifying question when the server said it was a fixed, translated line. */
    private var askFooter: DeskFooter? = null

    private suspend fun askClarify() {
        if (text.length > 4900) { more = ""; return next() }
        if (!asked(Step.CLARIFY)) say(Para.Text(ask), prompt = Step.CLARIFY, footer = askFooter)
        askInput?.let { dock(Dock.Input(ask, it, clarify = true)) } ?: dock(Dock.Clarify)
    }

    fun submitClarify(raw: String) {
        val v = raw.trim()
        if (v.length < 3) { err(lbl("err_more_asis", "Please add a few words, or send it as it is.")); return }
        if (mashed(v)) { err(lbl("err_mashed", "That could not be understood. Please use a few plain words.")); return }
        go { clarified(v) }
    }

    /** The clarify answer, typed or tapped: it goes into the enquiry and the whole is read again. */
    private suspend fun clarified(v: String) {
        more = v
        answer(Step.CLARIFY, v)
        // The whole of it is read again: the answer usually holds what the
        // first reading lacked. A slow or failed check leaves the first.
        val run = vetRun
        reread = viewModelScope.async {
            val r = repo.check(fullText(), langField(convLang))
            if (run == vetRun && r != null) {
                if (r.area.isNotEmpty() && r.area != "other") area = r.area
                keep(r)
            }
        }
        dock(Dock.Waiting("…"))
        next()
    }

    fun sendAsItIs() = go {
        more = ""
        answer(Step.CLARIFY, tl("ans_as_is") ?: ANS_AS_IS)
        dock(Dock.Waiting("…"))
        next()
    }

    /** What a guest said when they asked for a person: it goes into the enquiry in their own words, as the website carries it. */
    private var directTalk: String? = null

    private fun fullText() =
        (if (!more.isNullOrEmpty()) text + gatherLabel(ask, more.orEmpty()) else text) + gatherLog.joinToString("") { gatherLabel(it.first, it.second) } +
            (directTalk?.takeIf { it.isNotBlank() }?.let { "\n\nAsked in the chat: $it" } ?: "")

    // ---- Lex's follow-up questions (REQ-017 017.20; desk/gather) --------------------------------

    /** The questions Lex has asked, with their answers; the one now waiting; how many were asked in all (the reading's own counts as the first). */
    private var gatherLog: List<Pair<String, String>> = emptyList()
    private var gatherPending: String? = null
    private var gatherAsked = 0
    private var gatherDone = false
    /** Any gather call in this journey said it ran: then Lex has already asked about hearings and deadlines, and the separate step is skipped. */
    private var gatherRan = false

    private val readingAsked get() = if (!more.isNullOrEmpty()) 1 else 0
    private val readingSkipped get() = more == ""

    /** Ask the desk for the next question; none, done, three asked, a skipped follow-up, or a failure: on to the papers (and the text is read once more if answers were added). */
    private suspend fun gather() {
        val askedSoFar = readingAsked + gatherAsked
        if (!mayAskMore(readingAsked > 0, readingSkipped, askedSoFar)) return finishGather()
        dock(Dock.Waiting("…"))
        val r = withSlowNote(lbl("wait_moment", ONE_MOMENT)) { repo.gather(fullText(), askedSoFar, langField(convLang), docsField(read?.documents.orEmpty())) }
        if (r?.ran == true) gatherRan = true
        r?.let { adopt(it.tongue.lang) }
        if (r != null) lastReread = r.reread
        // The danger pause comes first: no booking is pushed while someone may be in danger.
        if (r != null && r.emergency.isNotBlank()) { dangerPause(r.emergency); return }
        if (r?.directOffer != null && !directAsked && !danger) pendingOffer = r.directOffer
        if (r?.directOpen != null && !directAsked && !danger) pendingOpen = r.directOpen
        // Lex shows it understood, once, in its own bubble with the question (if there is one).
        val reflect = if (r != null && r.reflect.isNotBlank() && !reflected) { reflected = true; r.reflect } else ""
        if (r == null || !r.more) {
            if (reflect.isNotBlank()) say(Para.Text(reflect), prompt = null, footer = r?.tongue?.lineFooter)
            return finishGather()
        }
        gatherPending = r.question
        gatherAsked++
        say(Para.Text(if (reflect.isNotBlank()) reflect + "\n\n" + r.question else r.question), prompt = Step.GATHER, footer = r.tongue.lineFooter)
        picks = emptySet()
        if (r.hasOptions) dock(Dock.Options(r.question, r.options, picks, writing = false))
        else if (r.input != null) dock(Dock.Input(r.question, r.input))
        else dock(Dock.Gather)
    }

    /** Whether Lex has said what it understood in this conversation, and whether the last answer wants the whole read again. */
    private var reflected = false
    private var lastReread = true
    private var danger = false
    private var picks: Set<Int> = emptySet()

    /** Immediate danger: the emergency line alone, and one button; nothing else (no papers, no offer) until they say they are safe. */
    private suspend fun dangerPause(line: String) {
        danger = true
        say(Para.Text(line))
        dock(Dock.Actions(listOf(Action("safe_now", lbl("ui_safe_carry_on", SAFE_CARRY_ON_EN), main = true))))
    }

    // ---- The tick list (F-029): Choose one or more, Send N Choices, Write My Own Instead, Back To The Choices ----

    fun togglePick(i: Int) {
        val d = _ui.value.dock as? Dock.Options ?: return
        picks = toggleOption(picks, i, d.options.size)
        dock(d.copy(picks = picks))
    }

    fun sendPicks() = go {
        val d = _ui.value.dock as? Dock.Options ?: return@go
        if (picks.isEmpty()) return@go
        recordGatherAnswer(d.question, optionsAnswer(d.options, picks, ""))
    }

    fun writeOwn() { val d = _ui.value.dock as? Dock.Options ?: return; dock(d.copy(writing = true)) }

    // ---- Lex asks with widgets (REQ-045): a chip or a date is the answer; Something Else, Type It opens the box ----

    /** Whether the enquiry's deadline came from a date answered here (so changing the answers clears it too). */
    private var deadlineFromGather = false

    /** A chip or a date: the answer is the text the conversation and the enquiry show ("Yes", the label, "Friday, 11 December 2026"); [isoDate] for a date. */
    fun pickInput(text: String, isoDate: String = "") = go {
        val d = _ui.value.dock as? Dock.Input ?: return@go
        if (isoDate.isNotEmpty() && inputFillsDeadline(d.input)) { deadline = "" to isoDate; deadlineFromGather = true }
        if (d.clarify) clarified(text) else recordGatherAnswer(d.question, text)
    }

    fun typeInstead() { val d = _ui.value.dock as? Dock.Input ?: return; dock(d.copy(writing = true)) }
    fun backToInput() { val d = _ui.value.dock as? Dock.Input ?: return; dock(d.copy(writing = false)) }
    fun backToChoices() { val d = _ui.value.dock as? Dock.Options ?: return; dock(d.copy(writing = false)) }

    /** An answer, from the ticks or the box: it goes into the enquiry, the ticks are spent, and the next question is asked. */
    private suspend fun recordGatherAnswer(q: String, v: String) {
        gatherLog = gatherLog + (q to v)
        gatherPending = null; picks = emptySet()
        answer(Step.GATHER, v)
        dock(Dock.Waiting("…"))
        next()
    }

    private suspend fun finishGather() {
        gatherDone = true
        gatherPending = null
        if (gatherLog.isNotEmpty() && lastReread) {
            // The whole of it is read once more, as with the reading's own question: the answers hold what the first reading lacked.
            val run = vetRun
            reread = viewModelScope.async {
                val r = repo.check(fullText(), langField(convLang))
                if (run == vetRun && r != null && deskReadingUsable(r.readingState)) {
                    if (r.area.isNotEmpty() && r.area != "other") area = r.area
                    keep(r)
                }
            }
        }
        next()
    }

    fun submitGather(raw: String) {
        val opt = _ui.value.dock as? Dock.Options
        val q = gatherPending ?: return
        // With ticks kept (Write My Own Instead), the ticks and the words go together; the ticks alone are something to send too.
        val v = if (opt != null) optionsAnswer(opt.options, picks, raw) else raw.trim()
        if (v.length < 2) { err(lbl("err_more_now", "Please add a few words, or choose Skip The Questions.")); return }
        if (mashed(raw.trim().ifEmpty { v })) { err(lbl("err_mashed", "That could not be understood. Please use a few plain words.")); return }
        go { recordGatherAnswer(q, v) }
    }

    /** Send Now: no more questions. */
    fun sendNow() = go {
        // No bubble for it (it is a button, not something the visitor said, and there is nothing to change): the questions simply stop.
        gatherPending = null
        gatherDone = false
        finishGather()
    }

    // ---- Documents -----------------------------------------------------------

    private suspend fun askDocs() {
        reread?.await()
        if (!termsReady()) return
        val list = read?.documents.orEmpty()
        if (list.isEmpty() || !t.takesFiles || qFiles > 0) { docs = true; return next() }
        if (!asked(Step.DOCS)) {
            val intro = tl("docs_intro")
            val notes = readingNotes()
            say(
                Para.Text(intro ?: "These papers help the advocate, if you have them. Attach them now or carry on; you can send them later."),
                Para.Bullets(list),
                *notes.toTypedArray(),
                prompt = Step.DOCS,
                footer = ft(intro != null || notesTranslated),
            )
        }
        dock(Dock.Docs)
    }

    fun docsGo() = go {
        docs = true
        val f = _ui.value.files
        answer(Step.DOCS, if (f.isNotEmpty()) (tl("ans_attached") ?: ANS_ATTACHED) else (tl("ans_docs_later") ?: ANS_DOCS_LATER), f.map { it.name + " · " + mb(it.size) })
        dock(Dock.Waiting("…"))
        next()
    }

    // ---- Who they are (cases_desk_context, read with the terms) ---------------

    /** Whether the notes just made came from the book (their line carries the footer). */
    private var notesTranslated = false

    private fun readingNotes(): List<Para> {
        notesTranslated = false
        val r = read ?: return emptyList()
        if ("notes" in said) return emptyList()
        said += "notes"
        return buildList {
            if (r.urgency == "urgent") {
                val t = tl("note_urgent", mapOf("reason" to if (r.reason.isNotEmpty()) ": " + r.reason else ""))
                // The served line carries its own bold (the first sentence): wrapping it again would turn the bold off and on, leaving one word bold.
                if (t != null) { notesTranslated = true; add(Para.Text(if (t.contains("**")) t else "**$t**", flag = true)) } else
                add(Para.Text("**This looks urgent" + (if (r.reason.isNotEmpty()) ": " + r.reason else "") + ".** I will mark it so, and the advocates will see it first.", flag = true))
            }
            if (r.place == "elsewhere_india") {
                val t = tl("note_elsewhere")
                if (t != null) { notesTranslated = true; add(Para.Text(t)) } else
                add(Para.Text("The advocates mostly appear in the courts of Punjab, Haryana and Chandigarh, but take matters from elsewhere too. If someone nearer the court would suit you better, they will say so."))
            }
        }
    }

    /** The welcome is said at the start; here only what the reading found (an urgent matter, one outside the home courts) is said, once. */
    private suspend fun welcome() {
        if (!termsReady()) return
        welcomed = true
        val notes = readingNotes()
        if (notes.isNotEmpty()) say(*notes.toTypedArray(), footer = ft(notesTranslated))
        next()
    }

    private suspend fun greetBack(withNotes: Boolean = true) {
        val c = ctx
        val first = c?.firstName.orEmpty().ifBlank { signedFirst }
        // The web's welcome (reception.php greetBack, F-018): someone we do not know yet is thanked; someone on file is welcomed back by first name only.
        if (c == null) {
            say(Para.Text(if (first.isNotBlank()) tl("greet_new", mapOf("first" to first)) ?: "Thank you for choosing Legum Lex, $first." else tl("greet_new_noname") ?: GREET_NEW_NOENAME_EN), *(if (withNotes) readingNotes().toTypedArray() else emptyArray()))
            return
        }
        val facts = welcomeFacts(c, { d -> day(d) }, { key, en -> lbl(key, en) }, { n -> matterCount(book, n) })
        say(
            Para.Text(welcomeLine(first, book)),
            *(if (facts.isNotEmpty()) arrayOf<Para>(Para.Facts(facts)) else emptyArray()),
            // F-018: nothing is said about the company on record or about the file; "Last enquiry" and unread replies need what this API does not carry (the funnel's sessions).
            *(if (withNotes) readingNotes().toTypedArray() else emptyArray()),
        )
    }

    private fun needsRoute() = read?.intent == "existing_matter" && (ctx?.openCases ?: 0) > 0

    private suspend fun askRoute() {
        if (!asked(Step.ROUTE)) {
            val r = read!!
            sayKey(if (r.related.isNotEmpty()) "route_with" else "route_without",
                "This looks like " + (if (r.related.isNotEmpty()) "**${r.related}**, already" else "a matter already") + " with the advocates. Writing on it in My Matters is the quickest way to reach them.",
                mapOf("related" to r.related), prompt = Step.ROUTE)
        }
        dock(Dock.Actions(listOf(Action("matters", lbl("ui_open_matters", "Open My Matters"), main = true), Action("new", lbl("ui_send_new", "Send as a New Enquiry")))))
    }

    // ---- Entity, country, phone ------------------------------------------------

    private suspend fun askEntity() {
        // The server has it on file (context.known_details.entity): not asked, and nothing said; the server uses the record when none is sent.
        if (t.known.entity) { entity = ctx?.entity.orEmpty(); return next() }
        val abroad = read?.place == "abroad"
        val c = ctx
        // On file already: said back to them, not asked. "Change" asks it.
        if (c != null && c.entity.isNotEmpty() && "entity" !in offered && !abroad) {
            // R23a (owner, 2026-11-14): what the records already hold is used silently. Lex does not say it can see the account type, and does not ask.
            offered += "entity"
            entity = c.entity
            // Lex did not ask, so there is no answer bubble to show (nothing for the visitor to "change").
            return next()
        }
        if (!asked(Step.ENTITY)) {
            val ask = tl("entity_ask")
            if (ask != null) say(Para.Text(ask + (if (abroad) " " + (tl("entity_abroad") ?: "") else "")), prompt = Step.ENTITY, footer = ft(true))
            else say(Para.Text("Are you asking as a business, an individual or an NRI?" + if (abroad) " You live abroad, so NRI may fit." else ""), prompt = Step.ENTITY)
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
            sayKey("country_ask", "Which country are you writing from? It sets call times and how a Power of Attorney is sent.", prompt = Step.COUNTRY)
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
        if (v.isEmpty()) { err(lbl("err_country", "Please enter the country or city.")); return }
        if (!Regex("^[\\p{L}\\p{Zs}.,'’()-]+$").matches(v)) { err(lbl("err_country_letters", "Please enter the country or city name, using letters only.")); return }
        go {
            country = "OTHER"; countryOther = v
            answer(Step.COUNTRY, v)
            dock(Dock.Waiting("…"))
            next()
        }
    }

    private suspend fun askPhone() {
        // The number is on file (context.known_details.phone): no question and no on-file confirm; the server uses the number on record when none is sent.
        if (t.known.phone && !forcePhone) { phone = ""; return next() }
        val c = ctx
        if (c != null && c.phone.length == 10 && entity != "nri" && "phone" !in offered) {
            if (!asked(Step.PHONE)) {
                sayKey("phone_onfile", "May the advocates call you on **${shown(c.phone)}**, the number on file, if needed?", mapOf("num" to shown(c.phone)), prompt = Step.PHONE)
            }
            dock(Dock.Actions(listOf(Action("phone_yes", lbl("ui_yes_number", "Yes, That Number"), main = true), Action("phone_other", lbl("ui_diff_number", "A Different Number")))))
            return
        }
        if (!asked(Step.PHONE)) {
            sayKey(if (entity == "nri") "phone_nri" else "phone_normal", if (entity == "nri")
                "Which number can the advocates reach you on, with the country code? If the time is wrong there, they will email."
                else "Which number can the advocates reach you on? They may call before writing.", prompt = Step.PHONE)
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

    // ---- A hearing or a deadline, in the client's own words (REQ-017; as the website's step) ----

    private suspend fun askDeadline() {
        // Gathering ran: it has asked about hearings and deadlines already, so this step is skipped (nothing is said or shown).
        if (skipDeadlineStep(gatherRan)) { deadline = "" to ""; return next() }
        if (!asked(Step.DEADLINE)) {
            sayKey("flag_ask", "Any hearing or deadline coming up? Tell me in your own words, with the date if you have it.", prompt = Step.DEADLINE)
        }
        dock(Dock.Actions(listOf(Action("deadline_yes", tl("chip_flag_yes") ?: CHIP_FLAG_YES, main = true), Action("deadline_no", tl("chip_flag_no") ?: CHIP_FLAG_NO))))
    }

    /** The words and the date (YYYY-MM-DD or empty); a problem is said under the box and nothing moves on. */
    fun giveDeadline(text: String, date: String) {
        val p = deadlineProblem(text, date)
        if (p.isNotEmpty()) { err(if (p == DEADLINE_EMPTY_EN) lbl("err_flag", p) else p); return }
        go {
            deadline = cleanDeadlineText(text) to date.trim()
            answer(Step.DEADLINE, deadlineShown(text, date))
            dock(Dock.Waiting("…"))
            next()
        }
    }

    /** The number is asked, then proved with a WhatsApp code when the server says verification is required and nothing verified is on record. */
    private suspend fun verifyPhone() {
        dock(Dock.Waiting("…"))
        when (val r = repo.phoneCode(phone.orEmpty(), entity.orEmpty(), sessionHash)) {
            is PhoneResult.Ok -> {
                if (r.sessionHash.isNotBlank()) sessionHash = r.sessionHash
                if (r.verified) { phoneProved = true; forcePhone = false; return next() }
                val line = tl("phone_code_sent", mapOf("to" to r.to))
                say(Para.Text(line ?: PHONE_CODE_SENT_EN.replace("{to}", r.to)), footer = ft(line != null))
                codeUi = PhoneCodeUi(r.to, r.resendIn, r.smsIn, r.canSms, System.currentTimeMillis())
                dock(Dock.Code(codeUi!!))
            }
            is PhoneResult.Refused -> {
                r.sessionHash?.let { sessionHash = it }
                say(Para.Text(phoneErrorText(book, r)), note = true, warn = true, footer = ft(book.has("perr_" + r.code)))
                dock(Dock.Actions(listOf(Action("phone_retry", lbl("ui_try_again", "Try Again"), main = true), Action("phone_change", lbl("ui_change_number", "Change Number")))))
            }
            PhoneResult.Failed -> {
                sayKey("perr_generic", "That did not work. Please try again.", note = true, warn = true)
                dock(Dock.Actions(listOf(Action("phone_retry", lbl("ui_try_again", "Try Again"), main = true), Action("phone_change", lbl("ui_change_number", "Change Number")))))
            }
        }
    }

    /** Confirm: the six digits. The server's refusal (wrong, expired, locked, used, session_locked) keeps the box and says why. */
    fun submitCode(raw: String) {
        val c = raw.filter { it.isDigit() }
        if (c.length != 6) { err(lbl("perr_wrong", "That code is not right. Please check it and try again.")); return }
        val hash = sessionHash ?: return
        go {
            val back = codeUi
            dock(Dock.Waiting("…"))
            when (val r = repo.phoneVerify(c, hash)) {
                is PhoneResult.Ok -> {
                    phoneProved = true; forcePhone = false; codeUi = null
                    sayKey("phone_confirmed", PHONE_CONFIRMED_EN, note = true)
                    next()
                }
                is PhoneResult.Refused -> { if (back != null) dock(Dock.Code(back)); err(phoneErrorText(book, r)) }
                PhoneResult.Failed -> { if (back != null) dock(Dock.Code(back)); err(lbl("perr_generic", "That did not work. Please try again.")) }
            }
        }
    }

    /** Send Code Again: a new code for the same number. */
    fun codeAgain() = go { verifyPhone() }

    /** Send By SMS Instead (Indian numbers; unlocked by the server's sms_in). */
    fun codeSms() {
        val hash = sessionHash ?: return
        go {
            val back = codeUi
            when (val r = repo.phoneSms(hash)) {
                is PhoneResult.Refused -> { if (back != null) dock(Dock.Code(back)); err(phoneErrorText(book, r)) }
                PhoneResult.Failed -> err(lbl("perr_generic", "That did not work. Please try again."))
                else -> Unit
            }
        }
    }

    /** Change Number: the number step again, whatever is on file; the next number is proved afresh. */
    fun changeNumber() = go {
        phone = null; phoneProved = false; codeUi = null; forcePhone = true; offered += "phone"
        dock(Dock.Waiting("…")); next()
    }

    /** After gathering, before the details: Lex's closing turn. An error, a rate limit or no answer skips it and the desk goes on as before. */
    private suspend fun askClose() {
        closeDone = true
        // Someone who may be in danger is not sold a close.
        if (danger) return next()
        // The full consultation is already chosen: no written-enquiry close (R9, F-001). The booking goes on to the details and the send.
        if (directBook) return next()
        dock(Dock.Waiting("…"))
        val person = "person" in said || directTalk != null
        val r = withSlowNote(lbl("wait_moment", ONE_MOMENT)) { repo.close(fullText(), langField(convLang), closeChose(directBook, directAsked), person) } ?: return next()
        // The text holds no matter (only a request for a person, a service question, a greeting): the server found nothing to close on. The matter is asked for again.
        if (r.refused == "no_matter") {
            rewind(Step.QUESTION) { say(Para.Text(r.say)) }
            return
        }
        say(Para.Text(r.say))
        when {
            // Chose the full consultation: one line, nothing more is asked.
            r.booked -> next()
            // Already said no or later: no new ask; carry on or start over.
            r.declined -> dock(Dock.Actions(listOf(
                Action("close_carry", tl("direct_go") ?: DIRECT_GO, main = true),
                Action("again", lbl("ui_start_new", CLOSE_START_NEW)),
            )))
            // They asked for a person and the full consultation is on: the server's two cards, the consultation first. Each button carries the card's own words.
            r.cards.isNotEmpty() -> dock(Dock.Cards(ChoiceBlock(r.say, r.cards), "close"))
            else -> dock(Dock.Actions(listOf(
                Action("close_go", lbl("ui_go_ahead", CLOSE_GO_AHEAD), main = true),
                Action("close_no", lbl("ui_not_now", CLOSE_NOT_NOW)),
            )))
        }
    }

    fun noPhone() = go {
        phone = ""
        answer(Step.PHONE, tl("ans_no_phone") ?: ANS_NO_PHONE)
        next()
    }

    // ---- Actions (the page's dockActions) ---------------------------------------

    fun act(id: String) {
        when (id) {
            "matters" -> _events.tryEmit(DeskEvent.OpenMatters)
            "new" -> go { route = "new"; answer(Step.ROUTE, tl("ans_new_enquiry") ?: ANS_NEW_ENQUIRY); dock(Dock.Waiting("…")); next() }
            "phone_yes" -> go { val c = ctx ?: return@go; phone = c.phone; answer(Step.PHONE, shown(c.phone)); next() }
            "phone_other" -> { offered += "phone"; dock(Dock.Phone) }
            "deadline_yes" -> dock(Dock.Deadline)
            "deadline_no" -> go { deadline = "" to ""; answer(Step.DEADLINE, tl("ans_no_deadline") ?: ANS_NO_DEADLINE); dock(Dock.Waiting("…")); next() }
            "try" -> go { send() }
            // Lex's closing turn: Yes carries on to the details; Not Now ends kindly with nothing sent or charged.
            "close_go" -> go { me(lbl("ui_go_ahead", CLOSE_GO_AHEAD)); dock(Dock.Waiting("…")); next() }
            "close_carry" -> go { me(tl("direct_go") ?: DIRECT_GO); dock(Dock.Waiting("…")); next() }
            "close_no" -> go {
                me(lbl("ui_not_now", CLOSE_NOT_NOW))
                sayKey("close_stop", CLOSE_STOP_EN)
                dock(Dock.Actions(listOf(Action("again", lbl("ui_start_new", CLOSE_START_NEW), main = true))))
            }
            // The unread screen: Try Again reads the same words once more (nothing was sent); Start A New Enquiry begins afresh.
            // The Agreement Health Check route: its button opens the app's own Agreement Check (nothing is sent from here, no enquiry, no fee).
            "route_ahc" -> go { me(routeButtonLabel()); _events.tryEmit(DeskEvent.OpenAgreementCheck) }
            "route_portal" -> go { me((_ui.value.dock as? Dock.Actions)?.list?.firstOrNull { it.id == "route_portal" }?.label ?: "Open My Agreements"); _events.tryEmit(DeskEvent.OpenAgreements) }
            "route_else" -> go { me(lbl("ui_ask_else", "Ask Something Else")); routeOffered = ""; releaseQuestionBubble(); resetStory(); next() }
            "safe_now" -> go { me(lbl("ui_safe_carry_on", SAFE_CARRY_ON_EN)); dock(Dock.Waiting("…")); finishGather() }
            "unread_try" -> go { vetted = null; unreadRetried = false; unreadShown = false; vet() }
            "unread_new" -> go { unreadShown = false; restart() }
            "phone_retry" -> go { verifyPhone() }
            "phone_change" -> changeNumber()
            // A booked Direct Consultation (REQ-020): Yes books it, No carries on as an ordinary enquiry; the fee is paid on its page.
            "direct_yes" -> go { me(tl("direct_yes") ?: DIRECT_YES); directBook = true; dock(Dock.Waiting("…")); next() }
            "direct_no" -> go { me(tl("direct_no") ?: DIRECT_NO); dock(Dock.Waiting("…")); next() }
            "direct_pay" -> go { payBookedAgain() }
            // The person already has an open direct consultation: Pay Now opens its page, Carry On goes to its wait.
            "direct_open_pay" -> go {
                val o = openBooking ?: return@go
                val url = o.payUrl ?: repo.direct(o.consultationId)?.payUrl
                consultationId = o.consultationId
                if (url != null) { awaitingBooked = true; _events.tryEmit(DeskEvent.OpenUrl(url)); bookedRef = ""; bookedSaid.clear()
                    dock(Dock.Actions(bookedPayActions(tl("direct_pay_btn") ?: DIRECT_PAY_BTN))); watchBooked() }
                else payBookedAgain()
            }
            // Carry On: this conversation goes on as an ordinary enquiry (the rest of the questions, an ordinary submit, no direct=1); the open booking stays in the list.
            "direct_go" -> go { me(tl("direct_go") ?: DIRECT_GO); dock(Dock.Waiting("…")); next() }
            "direct_paid" -> go { val id = consultationId ?: return@go; repo.directRow(id)?.let { advanceBooked(it) } }
            // A guest's question the desk could not take: send the same one again, or go to the Google sign-in.
            "guest_retry" -> go { val q = guestRetry ?: return@go; guestRetry = null; sendGuest(q, retry = true) }
            "guest_signin" -> go { signInDock() }
            "advocate_open" -> _events.tryEmit(consultationId?.let { DeskEvent.OpenConsultation(it) } ?: DeskEvent.OpenMatters)
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
        dock(Dock.Actions(listOf(Action("acct_offer_yes", "Open My Account", main = true), Action("acct_offer_no", lbl("ui_have_question", "No, I Have A Question")))))
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
            dock(Dock.Actions(listOf(Action("acct_retry", lbl("ui_try_again", "Try Again"), main = true))))
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
        dock(Dock.Waiting(lbl("wait_sending", "Sending — please keep this open.")))
        // Three steps (F-018: "Added to your file" is no longer said): the served stage_1, stage_2 and stage_4, and their completed forms.
        val running = STAGE_RUNNING_KEYS.map { tl(it) }
        val doneSteps = STAGE_DONE_KEYS.map { tl(it) }
        say(Para.Stages(0, false, tl("stage_title"), if (running.all { it != null }) running.filterNotNull() else emptyList(),
            tl("stage_done_title"), if (doneSteps.all { it != null }) doneSteps.filterNotNull() else emptyList()), footer = ft(book.has("stage_title")))
        val stagesId = _ui.value.lines.last().id
        val stager = viewModelScope.launch {
            for ((i, at) in STAGE_AT_MS.withIndex()) {
                delay(if (i == 0) 0 else at - STAGE_AT_MS[i - 1])
                setStages(stagesId, i, false)
            }
            delay(9_000 - STAGE_AT_MS.last())
            dock(Dock.Waiting(tl("wait_slow") ?: "Still working; the chamber is slow just now. Please keep this open."))
        }
        // The key is made once for this enquiry as composed and sent on every attempt: a retry after a timeout or a restart is the same enquiry.
        val key = repo.submissionKeys.forEnquiry(SubmissionKeys.fingerprint(fullText(), _ui.value.files.map { it.name to it.size }))
        val flag = deadline
        val r = repo.send(fullText(), entity.orEmpty(), phone.orEmpty(), country, countryOther, reading, sessionHash, _ui.value.files, t,
            submissionKey = key, deadlineFlag = cleanDeadlineText(flag?.first.orEmpty()), deadlineDate = flag?.second.orEmpty(), lang = book.lang.takeIf { it != "en" }.orEmpty(), direct = directBook)
        stager.cancel()
        sending = false
        when (r) {
            is Sent.Delivered -> {
                // A booked consultation held for its fee (F-032): nothing has reached the advocates, so no delivered ticks and no seal; the web's held state, then the pay prompt.
                if (isHeldBooking(r.intakeState, r.direct)) holdStages(stagesId, r.direct?.feeDisplay.orEmpty()) else setStages(stagesId, STAGE_AT_MS.size, true)
                consultationId = r.consultationId
                delivered(r.email, r.enquiryNumber, r.direct)
            }
            is Sent.Saved -> {
                dropLine(stagesId)
                _ui.update { it.copy(locked = false) }
                sessionHash = r.sessionHash.ifBlank { sessionHash }
                savedEmail = r.email; savedPrice = r.displayPrice; savedDirect = r.direct
                val sv = tl("saved"); val pf = tl("pay_fee", mapOf("fee" to r.displayPrice))
                if (sv != null && pf != null) say(Para.Text("**$sv** $pf"), footer = ft(true))
                else say(Para.Text("**Your enquiry is saved.** The consultation fee of **${r.displayPrice}** sends it to the advocates. Nothing is charged until then."))
                payActions()
            }
            is Sent.Refused -> {
                dropLine(stagesId)
                _ui.update { it.copy(locked = false) }
                sessionHash = r.sessionHash ?: sessionHash
                if (r.reason != null && !(r.errors.isNotEmpty() && r.reason in setOf("check_details", "check_files"))) refused(r)
                else if (r.errors.isNotEmpty()) fieldErrors(r.errors)
                else failed("That did not go through. " + r.message.ifBlank { "Please try again." }, tl("err_generic")?.let { it + if (r.message.isNotBlank()) " " + r.message else "" })
            }
            is Sent.Failed -> {
                dropLine(stagesId)
                _ui.update { it.copy(locked = false) }
                if (r.timedOut) {
                    // Not "try again": the enquiry may be on a desk already.
                    val ta = tl("timeout_a")
                    say(Para.Text(ta ?: "This is taking long. It may already have reached the advocates; please check My Matters before sending again."), note = true, warn = true, footer = ft(ta != null))
                    dock(Dock.Actions(listOf(Action("matters", lbl("ui_see_arrived", "See Whether It Arrived"), main = true), Action("try", lbl("ui_send_again", "Send It Again")))))
                } else {
                    failed(if (r.message.isNotBlank()) "That did not go through. ${r.message}" else "That did not go through. Please check your connection and try again.",
                        if (r.message.isNotBlank()) tl("err_generic")?.let { it + " " + r.message } else tl("send_failed"))
                }
            }
        }
    }

    /**
     * A refusal that names its reason: the server's line alone (it is already in the conversation's language, with its tag when translated), never the app's
     * own "That did not go through." in front of it. Where a retry cannot help (the hourly limit, already sent, awaiting its fee) there is no Try Again; what
     * the visitor wrote stays on screen.
     */
    private suspend fun refused(r: Sent.Refused) {
        // The server will not send it until the number is proved: back to the number step; once proved, the enquiry goes on as it was.
        if (isPhoneRefusal(r.reason)) {
            val line = tl("refuse_" + r.reason)
            val en = r.message.ifBlank { if (r.reason == "phone_unverified") PHONE_CONFIRM_FIRST_EN else PHONE_REQUIRED_EN }
            say(Para.Text(line ?: en), note = true, warn = true, footer = ft(line != null))
            phoneProved = false; forcePhone = true; offered += "phone"; phone = null
            return next()
        }
        val msg = r.message.ifBlank { tl("err_generic") ?: "That did not go through." }
        say(Para.Text(msg), note = true, warn = true, footer = r.tongue.lineFooter)
        when {
            retryHelps(r.reason) -> dock(Dock.Actions(listOf(Action("try", lbl("ui_try_again", "Try Again"), main = true))))
            r.reason == "awaiting_payment" && sessionHash != null -> payActions()
            else -> dock(Dock.Actions(listOf(Action("matters", lbl("ui_see_matters", "See Your Matters"), main = true))))
        }
    }

    // ---- The fee (lexon Funnel_orchestrator, via desk/pay) --------------------------

    private fun payActions() = dock(Dock.Actions(listOf(
        Action("pay", book.fragment("ui_pay_send") ?: "Pay ${savedPrice.ifBlank { "the Fee" }} and Send", main = true),
        Action("discard", lbl("ui_discard_it", "Set It Aside")),
    )))

    private suspend fun pay() {
        val hash = sessionHash ?: return
        dock(Dock.Waiting(lbl("wait_payment", "Opening the invoice…")))
        repo.pay(hash)
            .onSuccess { p ->
                when (p) {
                    is PayStart.Sent -> { consultationId = p.consultationId; delivered(savedEmail, null, bookedAfterPay()) }
                    is PayStart.Invoice -> {
                        awaitingInvoice = true
                        _events.tryEmit(DeskEvent.OpenUrl(p.url))
                        // Said only when the fee is paid on a page the person has to leave for; an invoice opens in the app's own checkout at once, and says nothing.
                        if (!said.contains("invoice") && com.legumlex.client.feature.finance.CheckoutActivity.invoiceIn(p.url) == null) {
                            said += "invoice"
                            val line = tl("pay_invoice_note")
                            // In a translated conversation with no such line in the book, nothing is said rather than English.
                            if (line != null) say(Para.Text(line), footer = ft(true))
                            else if (!book.active) say(Para.Text(PAY_INVOICE_NOTE))
                        }
                        dock(Dock.Actions(listOf(Action("paid", lbl("ui_paid", "I Have Paid"), main = true), Action("pay", lbl("ui_pay_fee", "Pay The Fee")), Action("discard", lbl("ui_discard_it", "Set It Aside")))))
                    }
                }
            }
            .onFailure { say(Para.Text(it.message ?: "The invoice could not be opened. Please try again."), note = true, warn = true); payActions() }
    }

    /** Back from the browser: if the invoice is paid, the enquiry has gone. */
    fun resumed() {
        if (awaitingInvoice) go { checkPaid(asked = false) }
        // Back from a booked consultation's fee page: look now rather than at the next poll.
        if (awaitingBooked) go { val id = consultationId ?: return@go; repo.directRow(id)?.let { advanceBooked(it) } }
    }

    private suspend fun checkPaid(asked: Boolean) {
        val hash = sessionHash ?: return
        if (asked) dock(Dock.Waiting(lbl("wait_confirming", "Checking the payment…")))
        repo.pay(hash).onFailure { e ->
            if (asked) {
                say(Para.Text(e.message ?: "The payment could not be checked. Please try again."), note = true, warn = true)
                dock(Dock.Actions(listOf(Action("paid", lbl("ui_paid", "I Have Paid"), main = true), Action("pay", lbl("ui_pay_fee", "Pay The Fee")), Action("discard", lbl("ui_discard_it", "Set It Aside")))))
            }
        }.onSuccess { p ->
            if (p is PayStart.Sent) {
                awaitingInvoice = false
                consultationId = p.consultationId
                delivered(savedEmail, null, bookedAfterPay())
            } else if (asked) {
                say(Para.Text("The payment has not yet been confirmed. If you have just paid, please wait a minute and try again."), note = true)
                dock(Dock.Actions(listOf(Action("paid", lbl("ui_paid", "I Have Paid"), main = true), Action("pay", lbl("ui_pay_fee", "Pay The Fee")), Action("discard", lbl("ui_discard_it", "Set It Aside")))))
            }
        }
    }

    private suspend fun discard() {
        val hash = sessionHash ?: return
        dock(Dock.Waiting(lbl("wait_discarding", "…")))
        repo.discard(hash)
            .onSuccess {
                awaitingInvoice = false
                say(Para.Text("Your enquiry has been set aside. Nothing was charged."))
                dock(Dock.Actions(listOf(Action("again", lbl("ui_ask_else", "Ask Something Else"), main = true))))
            }
            .onFailure { e ->
                say(Para.Text(e.message ?: "The enquiry could not be set aside. Please try again."), note = true, warn = true)
                payActions()
            }
    }

    private fun setStages(id: Long, active: Int, done: Boolean) = _ui.update { u ->
        u.copy(lines = u.lines.map { if (it.id == id) it.copy(paras = listOf((it.paras.firstOrNull() as? Para.Stages ?: Para.Stages(active, done)).copy(active = active, done = done))) else it })
    }

    /** The held state in place of the stepper: "Booked. Waiting for the fee.", the details saved (ticked) and the fee to pay (not ticked). Served stage_held_*. */
    private fun holdStages(id: Long, fee: String) = _ui.update { u ->
        val title = tl("stage_held_title") ?: STAGE_HELD_TITLE_EN
        val one = tl("stage_held_1") ?: STAGE_HELD_1_EN
        val two = tl("stage_held_2", mapOf("fee" to fee)) ?: STAGE_HELD_2_EN.replace("{fee}", fee)
        u.copy(lines = u.lines.map { if (it.id == id) it.copy(paras = listOf(Para.Stages(1, false, title, listOf(one, two), held = true))) else it })
    }

    private fun dropLine(id: Long) = _ui.update { u -> u.copy(lines = u.lines.filterNot { it.id == id }) }

    /** [msg] in English, or [translated] when the book had the line (then it carries the footer). */
    private suspend fun failed(msg: String, translated: String? = null) {
        say(Para.Text(translated ?: msg), note = true, warn = true, footer = ft(translated != null))
        dock(Dock.Actions(listOf(Action("try", lbl("ui_try_again", "Try Again"), main = true))))
    }

    /** A server complaint goes back to the question it is about. */
    private suspend fun fieldErrors(errors: Map<String, String>) {
        val owner = mapOf("enquiry_text" to Step.QUESTION, "attachments" to Step.QUESTION, "entity_type" to Step.ENTITY, "country" to Step.COUNTRY, "phone" to Step.PHONE,
            "deadline_flag" to Step.DEADLINE, "deadline_date" to Step.DEADLINE)
        val target = ORDER.firstOrNull { k -> errors.keys.any { owner[it] == k } } ?: Step.PHONE
        rewind(target) { say(Para.Text(errors.values.joinToString(" ")), note = true, warn = true) }
    }

    /** After the ordinary enquiry fee was paid on a Yes, Book It: the booking (desk/direct), or null when none is available (the ordinary receipt then). */
    private suspend fun bookedAfterPay(): DirectBooking? {
        if (!directBook) return null
        val id = consultationId
        // The server's booking now; else what the submit said; else a quiet booking on the enquiry payment: a Yes, Book It is never ended with the ordinary
        // (or the direct-talk) receipt, because the one fee was the booking.
        return (id?.let { repo.direct(it) }) ?: savedDirect?.copy(consultationId = id ?: savedDirect?.consultationId)
            ?: DirectBooking(id, "awaiting_payment", 0, "", null, null, null, false, via = "enquiry_payment")
    }

    private suspend fun delivered(email: String, enquiryNumber: String? = null, direct: DirectBooking? = null) {
        // The enquiry is sent: the next opening of the desk is a new conversation.
        draftText = ""; DeskKeep.renew()
        // Sent: the key has done its work, and the next enquiry is a new one.
        repo.submissionKeys.clear()
        if (directBook && direct != null) { deliveredBooked(direct, enquiryNumber); return }
        if (isDirectTalk(informed, directTalk)) { deliveredDirect(enquiryNumber); return }
        // The receipt names no advocate: none has it yet, and the client is told who does when it is assigned.
        val full = tl(if (!enquiryNumber.isNullOrBlank()) "received_no" else "received_plain", mapOf("n" to enquiryNumber.orEmpty(), "email" to email.ifBlank { "your email" }))
        if (full != null) say(Para.Seal, Para.Text(full), footer = ft(true))
        else say(Para.Seal, Para.Text(receiptLine(enquiryNumber, email)))
        dock(Dock.Actions(listOf(Action("seen", lbl("ui_see_matters", "See Your Matters"), main = true), Action("again", lbl("ui_ask_else", "Ask Something Else")))))
    }

    /** After a direct-talk enquiry: the final-report line in place of the usual receipt, then the quiet waiting card, which hands over when the advocate acknowledges. */
    /** Said once per conversation: the visitor is offered a booked consultation with an advocate directly. */
    private suspend fun askDirectOffer() {
        val o = pendingOffer ?: return next()
        pendingOffer = null; directAsked = true; if (o.feeDisplay.isNotBlank()) offerFee = o.feeDisplay
        val line = tl("direct_offer", mapOf("fee" to o.feeDisplay))
        say(Para.Text(line ?: DIRECT_OFFER_EN.replace("{fee}", o.feeDisplay)), footer = ft(line != null))
        dock(Dock.Actions(listOf(Action("direct_yes", tl("direct_yes") ?: DIRECT_YES, main = true), Action("direct_no", tl("direct_no") ?: DIRECT_NO))))
    }

    /** Said once: an open direct consultation already exists. Awaiting payment: Pay Now (its page) or Carry On; Carry On continues this conversation as an ordinary enquiry. */
    private suspend fun askDirectOpen() {
        val o = pendingOpen ?: return next()
        pendingOpen = null; directAsked = true; openBooking = o
        val awaiting = directPhase(o.state) == DirectPhase.AwaitingPayment
        val vars = mapOf("fee" to o.feeDisplay)
        val key = if (awaiting) "direct_have_pay" else "direct_have_wait"
        val line = tl(key, vars)
        say(Para.Text(line ?: (if (awaiting) DIRECT_HAVE_PAY else DIRECT_HAVE_WAIT).replace("{fee}", o.feeDisplay)), footer = ft(line != null))
        val go = Action("direct_go", tl("direct_go") ?: DIRECT_GO, main = !awaiting)
        dock(Dock.Actions(if (awaiting) listOf(Action("direct_open_pay", tl("direct_pay_btn") ?: DIRECT_PAY_BTN, main = true), go) else listOf(go)))
    }

    /**
     * The enquiry went in as a booked consultation. Its fee is the ordinary enquiry fee: already paid (state offered, no pay_url) there is no payment step;
     * otherwise the fee invoice (or a lead's particulars page) opens through the existing pay screens. Then the wait: offered, accepted, unclaimed.
     */
    private suspend fun deliveredBooked(b: DirectBooking, enquiryNumber: String?) {
        consultationId = b.consultationId ?: consultationId
        // The card's small line: the enquiry's number, else the consultation's own (#17) until the row gives the number.
        bookedRef = bookedReference(enquiryNumber, consultationId)
        bookedSaid.clear()
        val url = b.payUrl
        val payBtn = tl("direct_pay_btn") ?: DIRECT_PAY_BTN
        val awaiting = directPhase(b.state) == DirectPhase.AwaitingPayment && !b.paid
        // The person already had an open one: told so (pay or wait), not booked again.
        if (b.existing) {
            val key = if (awaiting) "direct_have_pay" else "direct_have_wait"
            val line = tl(key, mapOf("fee" to b.feeDisplay))
            say(Para.Text(line ?: (if (awaiting) DIRECT_HAVE_PAY else DIRECT_HAVE_WAIT).replace("{fee}", b.feeDisplay)), footer = ft(line != null))
        } else if (awaiting && url != null) {
            val line = tl("direct_pay_prompt", mapOf("fee" to b.feeDisplay))
            say(Para.Text(line ?: DIRECT_PAY_PROMPT.replace("{fee}", b.feeDisplay)), footer = ft(line != null))
        }
        when {
            awaiting && url != null -> {
                // The page opens when Pay Now is pressed (the web opens it on a click, never by itself).
                awaitingBooked = true
                dock(Dock.Actions(bookedPayActions(payBtn)))
            }
            // The ordinary enquiry payment is the booking payment: nothing separate to pay; the poll shows it offered once paid.
            awaiting && b.via == "enquiry_payment" -> dock(Dock.Advocate(AdvocateWait.Waiting("", bookedRef)))
            // Awaiting payment with nothing to open: never a silent wait; Pay Now asks the server for the page again.
            awaiting -> { awaitingBooked = true; dock(Dock.Actions(listOf(Action("direct_pay", payBtn, main = true)))) }
            else -> dock(Dock.Advocate(AdvocateWait.Waiting("", bookedRef)))
        }
        watchBooked()
    }

    /** The web's pay step for a booked consultation (reception.php directBooked): Pay Now (main), then See Your Matters and Ask Something Else. The fee is not paid yet, so no "I Have Paid". */
    private fun bookedPayActions(payBtn: String) = listOf(
        Action("direct_pay", payBtn, main = true),
        Action("seen", lbl("ui_see_matters", "See Your Matters")),
        Action("again", lbl("ui_ask_else", "Ask Something Else")),
    )

    /** Pay The Fee (or Try Again): ask desk/direct for the fee page; with none, say so (the server's line, else "That did not go through.") with Try Again. */
    private suspend fun payBookedAgain() {
        val id = consultationId ?: return
        val b = repo.direct(id)
        val url = b?.payUrl
        when {
            url != null -> {
                awaitingBooked = true
                _events.tryEmit(DeskEvent.OpenUrl(url))
                dock(Dock.Actions(bookedPayActions(tl("direct_pay_btn") ?: DIRECT_PAY_BTN)))
            }
            // Paid meanwhile: the wait carries on from the row.
            b != null && directPhase(b.state) != DirectPhase.AwaitingPayment -> repo.directRow(id)?.let { advanceBooked(it) }
            else -> {
                sayKey("err_generic", "That did not go through.", note = true, warn = true)
                dock(Dock.Actions(listOf(Action("direct_pay", lbl("ui_try_again", "Try Again"), main = true))))
            }
        }
    }

    private fun watchBooked() {
        val id = consultationId ?: return
        bookedPoll?.cancel()
        bookedPoll = viewModelScope.launch {
            while (true) {
                val row = repo.directRow(id)
                if (row != null && flow.withLock { advanceBooked(row) }) break
                delay(DIRECT_POLL_MS)
            }
        }
    }

    /** One look at the booked consultation: says what changed once, moves the dock on, and returns true when an advocate has it (nothing more to wait for). */
    private suspend fun advanceBooked(row: DirectRow): Boolean {
        if (row.enquiryNumber.isNotBlank() && bookedRef.startsWith("#") || (bookedRef.isBlank() && row.enquiryNumber.isNotBlank())) bookedRef = row.enquiryNumber
        fun lineOrNull(key: String, en: String, vars: Map<String, String> = emptyMap()): Pair<String, Boolean> {
            val t = tl(key, vars); return (t ?: en.replace(Regex("\\{(\\w+)\\}")) { vars[it.groupValues[1]].orEmpty() }) to (t != null)
        }
        when (row.phase) {
            DirectPhase.Offered, DirectPhase.Unclaimed -> {
                awaitingBooked = false
                if ("paid" !in bookedSaid) {
                    bookedSaid += "paid"
                    val (wait, t1) = lineOrNull("direct_pay_wait", DIRECT_PAY_WAIT)
                    val paras = mutableListOf<Para>(Para.Text(wait))
                    var translated = t1
                    if (row.dueBy.isNotBlank()) { val (due, t2) = lineOrNull("direct_due_note", DIRECT_DUE_NOTE, mapOf("time" to dueTimeText(row.dueBy))); paras += Para.Text(due); translated = translated || t2 }
                    say(*paras.toTypedArray(), footer = ft(translated))
                    dock(Dock.Advocate(AdvocateWait.Waiting("", bookedRef)))
                }
                if (row.phase == DirectPhase.Unclaimed && "unclaimed" !in bookedSaid) {
                    bookedSaid += "unclaimed"
                    val (u, t) = lineOrNull("direct_unclaimed", DIRECT_UNCLAIMED)
                    say(Para.Text(u), footer = ft(t))
                }
                return false
            }
            DirectPhase.Accepted -> {
                awaitingBooked = false
                val (acc, _) = lineOrNull("direct_accepted", DIRECT_ACCEPTED)
                _ui.update { it.copy(dock = Dock.Advocate(AdvocateWait.HandedOver(acc, bookedRef, OPEN_CONSULTATION))) }
                return true
            }
            else -> return false
        }
    }

    private suspend fun deliveredDirect(enquiryNumber: String?) {
        val line = tl("person_final")
        say(Para.Seal, Para.Text(line ?: PERSON_FINAL), footer = ft(line != null))
        // The enquiry's reference stays visible, small, on the card's second line (person_final drops it).
        val ref = enquiryNumber?.trim().orEmpty()
        dock(Dock.Advocate(AdvocateWait.Waiting("", ref)))
        val id = consultationId ?: return
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                delay(DIRECT_POLL_MS)
                val s = repo.directStatus(id)
                if (handedOver(s)) {
                    val handover = tl("person_handover") ?: PERSON_HANDOVER
                    _ui.update { it.copy(dock = Dock.Advocate(AdvocateWait.HandedOver(handover, ref, OPEN_CONSULTATION))) }
                    break
                }
            }
        }
    }

    private suspend fun restart() {
        reflected = false; lastReread = true; danger = false; picks = emptySet(); matterAsks = 0; cardsSeen = false; consultWish = false; cardsAgain = false; insistAsked = ""
        draftText = ""; pollJob?.cancel(); bookedPoll?.cancel(); informed = false; phoneProved = false; forcePhone = false; codeUi = null; pendingOffer = null; pendingOpen = null; openBooking = null; directAsked = false; directBook = false; awaitingBooked = false; bookedSaid.clear()
        text = ""; more = null; vetted = null; ask = ""; askInput = null; area = ""; read = null; reading = ""; qFiles = 0; docs = null
        route = null; entity = null; country = null; countryOther = ""; phone = null; deadline = null; deadlineFromGather = false; gatherLog = emptyList(); gatherPending = null; gatherAsked = 0; gatherDone = false; gatherRan = false; closeDone = false; unreadRetried = false; unreadShown = false; sessionHash = null; consultationId = null
        repo.submissionKeys.clear()
        savedEmail = ""; savedPrice = ""; awaitingInvoice = false
        directTalk = null
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
                Step.GATHER -> { if (deadlineFromGather) { deadline = null; deadlineFromGather = false }; gatherLog = emptyList(); gatherPending = null; gatherAsked = 0; gatherDone = false; gatherRan = false; closeDone = false }
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
        const val PERSON_SIGNED = "The advocates are busy. I have noted you want to talk directly. A line on the matter helps them prepare; carry on when ready."
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
