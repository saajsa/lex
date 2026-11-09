package com.legumlex.client.feature.workspace

import com.legumlex.client.data.api.ApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull

/*
 * The workspaces one sign-in holds (REQ-010, 10a): Counsel and the Client Portal. The server lists them (`workspaces` in the
 * sign-in and profile answers) and says whether to ask where to go (`choose`). The app asks at every opening and keeps the
 * answer for this run only: nothing is remembered between openings.
 */

/** One workspace as the server lists it. `status` is the server's own word (none, pending, rejected, active, suspended, removed). */
data class WorkspaceItem(
    val kind: String, val label: String, val status: String, val enter: Boolean, val note: String,
    val canAdd: Boolean, val canRemove: Boolean,
    /** The chooser's figures, when the server sends them (counsel: listed_today, credits; portal: matters, hearings_week). */
    val stats: Map<String, Int> = emptyMap(),
)

data class Workspaces(val items: List<WorkspaceItem>, val choose: Boolean, val counselOnly: Boolean) {
    fun item(kind: String) = items.firstOrNull { it.kind == kind }
    val counsel get() = item(KIND_COUNSEL)
    val portal get() = item(KIND_PORTAL)
}

const val KIND_COUNSEL = "counsel"
const val KIND_PORTAL = "portal"

@Suppress("UNCHECKED_CAST")
fun parseWorkspaceList(d: Map<String, Any?>?): Workspaces {
    if (d == null) return Workspaces(emptyList(), false, false)
    val items = (d["items"] as? List<Map<String, Any?>>).orEmpty().map {
        WorkspaceItem(
            kind = (it["kind"] as? String).orEmpty(), label = (it["label"] as? String).orEmpty(),
            status = (it["status"] as? String).orEmpty(), enter = it["enter"] == true, note = (it["note"] as? String).orEmpty(),
            canAdd = it["can_add"] == true, canRemove = it["can_remove"] == true,
            stats = (it["stats"] as? Map<String, Any?>).orEmpty().mapNotNull { (k, v) -> (v as? Number)?.toInt()?.let { n -> k to n } }.toMap(),
        )
    }
    return Workspaces(items, d["choose"] == true, d["counsel_only"] == true)
}

/** Which workspace this run is in. Unknown until the server has answered (neutral chrome, the server's board answer decides). */
enum class Mode { Unknown, Counsel, Portal }

object WorkspaceSession {
    private val _mode = MutableStateFlow(Mode.Unknown)
    val mode: StateFlow<Mode> = _mode.asStateFlow()

    private val _workspaces = MutableStateFlow<Workspaces?>(null)
    val workspaces: StateFlow<Workspaces?> = _workspaces.asStateFlow()

    /** The person's first name for the greeting, when the profile gave it. */
    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name.asStateFlow()

    /** A note to show once on arrival (a workspace added or removed while the app was open). */
    private val _note = MutableStateFlow("")
    val note: StateFlow<String> = _note.asStateFlow()
    fun say(text: String) { _note.value = text }
    fun noteShown() { _note.value = "" }

    /** The workspace a tapped push is for (cold start, before the list is known): the open that follows honours it instead of asking. */
    var pushHint: String? = null

    /** Also drops a push hint: a hint from before a sign-out must never decide the next sign-in. */
    fun clear() { signInData = null; pushHint = null; _mode.value = Mode.Unknown; _workspaces.value = null; _name.value = ""; _note.value = "" }

    fun set(m: Mode) { _mode.value = m }

    fun update(ws: Workspaces) { _workspaces.value = ws }

    /**
     * Ask the server what this sign-in holds and decide where the app opens. Returns true when the person must choose
     * (more than one workspace); otherwise the mode is settled here: counsel only means Counsel, everything else the Portal.
     * A server that cannot be reached leaves the mode unknown and opens the app as before (the board's answer decides).
     */
    /** The sign-in's own answer (it carries `workspaces`), kept for the opening that follows so no second, slower call is needed. */
    var signInData: Map<String, Any?>? = null

    /** A debug-only line in the log (tag LexWorkspace): what the opening was given and what it decided. No tokens, no personal data. */
    private fun trace(line: String) { if (com.legumlex.client.BuildConfig.DEBUG) android.util.Log.d("LexWorkspace", line) }

    /** The profile answer, and what became of the call ("ok", "timeout", "HTTP 500", the exception's class). */
    @Suppress("UNCHECKED_CAST")
    private suspend fun fetchProfile(api: ApiService): Pair<Map<String, Any?>?, String> {
        // The profile answer can be slow while it works out the chooser's figures: wait, and ask once more before giving up.
        var why = "timeout"
        repeat(2) { n ->
            val r = withTimeoutOrNull(12_000) { runCatching { api.profile()["data"] as? Map<String, Any?> } }
            when {
                r == null -> why = "timeout"
                r.isSuccess && r.getOrNull() != null -> return r.getOrNull() to (if (n == 0) "ok" else "ok on retry")
                r.isSuccess -> why = "answer without data"
                else -> why = (r.exceptionOrNull() as? retrofit2.HttpException)?.let { "HTTP ${it.code()}" } ?: (r.exceptionOrNull()?.javaClass?.simpleName ?: "error")
            }
        }
        return null to why
    }

    /**
     * Ask the server what this sign-in holds and decide where the app opens. Returns true when the person must choose
     * (more than one workspace); otherwise the mode is settled here: counsel only means Counsel, everything else the Portal.
     * A server that cannot be reached leaves the mode unknown; the shell then asks again (see MainActivity), so a person
     * who holds both is still asked and never lands in Counsel unasked.
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun open(api: ApiService, fromSignIn: Map<String, Any?>? = null): Boolean {
        _mode.value = Mode.Unknown
        val held = signInData; signInData = null
        val given = (fromSignIn ?: held)?.takeIf { it["workspaces"] is Map<*, *> }
        var source = if (given != null) "sign-in" else "profile"
        var call = "not made"
        val data = given ?: fetchProfile(api).let { (d, why) -> call = why; d }
        val ws = (data?.get("workspaces") as? Map<String, Any?>)?.let { parseWorkspaceList(it) }
        val hint = pushHint
        if (ws == null) {
            // The hint stays for the next opening: nothing was decided.
            trace("open source=$source profile=$call workspaces=absent hint=${hint ?: "none"} consumed=no mode=Unknown")
            return false
        }
        _workspaces.value = ws
        (data["name"] as? String)?.trim()?.takeIf { it.isNotEmpty() }?.let { _name.value = it }
        // A tapped push already says where to go (when that workspace is held): no question.
        pushHint = null
        val pushed = workspaceForPush(hint, ws, Mode.Unknown) as? PushRoute.Open
        if (pushed != null) {
            _mode.value = pushed.mode
            trace("open source=$source profile=$call workspaces=present choose=${ws.choose} counsel_only=${ws.counselOnly} hint=$hint consumed=yes mode=${pushed.mode}")
            return false
        }
        val decided = openAs(ws)
        _mode.value = when (decided) { OpenAs.Counsel -> Mode.Counsel; OpenAs.Portal -> Mode.Portal; OpenAs.Choose -> Mode.Unknown }
        trace("open source=$source profile=$call workspaces=present choose=${ws.choose} counsel_only=${ws.counselOnly} hint=${hint ?: "none"} consumed=${if (hint != null) "dropped" else "no"} decision=$decided mode=${_mode.value}")
        return decided == OpenAs.Choose
    }
}

/** Where an opening goes, from the server's list: choose when it says so, Counsel for a counsel-only account, otherwise the Portal. */
enum class OpenAs { Choose, Counsel, Portal }

fun openAs(ws: Workspaces): OpenAs = when {
    ws.choose -> OpenAs.Choose
    ws.counselOnly -> OpenAs.Counsel
    else -> OpenAs.Portal
}

/** The greeting by the hour. */
fun greeting(hour: Int): String = when (hour) { in 5..11 -> "GOOD MORNING"; in 12..16 -> "GOOD AFTERNOON"; else -> "GOOD EVENING" }

/** What a fresh look at the server's list changed for this run (REQ-010, 010.10 unit 4): never a silent swap. */
sealed interface WorkspaceChange {
    data object None : WorkspaceChange
    /** The Counsel workspace can no longer be entered: someone in it moves to the Client Portal; someone in the Portal is only told. */
    data class CounselGone(val note: String, val moveToPortal: Boolean) : WorkspaceChange
    /** A Counsel workspace appeared: a note, and the app stays where it is. */
    data class CounselAdded(val note: String) : WorkspaceChange
}

const val GONE_NOTE = "Your Counsel workspace is no longer available. You are now in your Client Portal."
const val ADDED_NOTE = "A Counsel workspace has been added to your account. Choose Switch Workspace in Profile to open it."

fun workspaceChange(before: Workspaces?, after: Workspaces, mode: Mode): WorkspaceChange {
    if (before == null) return WorkspaceChange.None
    val had = before.counsel?.enter == true
    val has = after.counsel?.enter == true
    return when {
        had && !has -> WorkspaceChange.CounselGone(after.counsel?.note?.takeIf { it.isNotBlank() } ?: GONE_NOTE, mode == Mode.Counsel)
        !had && has -> WorkspaceChange.CounselAdded(ADDED_NOTE)
        else -> WorkspaceChange.None
    }
}

/** Look again at what this sign-in holds (the app came back to the front) and act on a change: a note, or a move to the Portal. */
suspend fun syncWorkspaces(context: android.content.Context) {
    val before = WorkspaceSession.workspaces.value
    val after = WorkspaceRepository(context).state().getOrNull()?.workspaces ?: return
    when (val c = workspaceChange(before, after, WorkspaceSession.mode.value)) {
        is WorkspaceChange.CounselGone -> {
            if (c.moveToPortal) { WorkspaceSession.set(Mode.Portal); com.legumlex.client.feature.board.BoardHub.hide() }
            WorkspaceSession.say(c.note)
        }
        is WorkspaceChange.CounselAdded -> WorkspaceSession.say(c.note)
        WorkspaceChange.None -> Unit
    }
}

/** What to do with a push, by the workspace it is for (`data.workspace`: client | counsel), REQ-010 unit 5. */
sealed interface PushRoute {
    /** Nothing about the workspace changes: open it where the app is. */
    data object Stay : PushRoute
    /** The person no longer holds that workspace: the push is dropped (or, if tapped later, only explained). */
    data object Drop : PushRoute
    /** Open it in that workspace; `told` says whether a note is needed (the app was in the other one). */
    data class Open(val mode: Mode, val told: Boolean) : PushRoute
}

fun workspaceForPush(tag: String?, held: Workspaces?, mode: Mode): PushRoute {
    if (tag.isNullOrBlank()) return PushRoute.Stay
    val target = if (tag == "counsel") Mode.Counsel else Mode.Portal
    if (held != null) {
        val item = if (target == Mode.Counsel) held.counsel else held.portal
        if (item == null || !item.enter) return PushRoute.Drop
    }
    return when (mode) {
        target -> PushRoute.Stay
        Mode.Unknown -> PushRoute.Open(target, told = false)
        else -> PushRoute.Open(target, told = true)
    }
}

const val PUSH_GONE_NOTE = "That update is for a workspace you no longer hold."
fun pushOpenedNote(mode: Mode) = if (mode == Mode.Counsel) "Opened in your Counsel workspace." else "Opened in your Client Portal."

/** The figures a card shows, as (number, words) in the design's order; empty when the server sent none. */
fun statLines(item: WorkspaceItem): List<Pair<String, String>> {
    fun n(v: Int) = java.text.NumberFormat.getIntegerInstance(java.util.Locale("en", "IN")).format(v)
    val st = item.stats
    return when (item.kind) {
        KIND_COUNSEL -> listOfNotNull(
            st["listed_today"]?.let { n(it) to "listed today" },
            st["credits"]?.let { n(it) to (if (it == 1) "credit" else "credits") },
        )
        else -> listOfNotNull(
            st["matters"]?.let { n(it) to (if (it == 1) "matter" else "matters") },
            st["hearings_week"]?.let { n(it) to (if (it == 1) "hearing this week" else "hearings this week") },
        )
    }
}

/** What the counsel door does for an existing client who signed in with Google (REQ-010). */
enum class DoorNext { AddCounsel, Open }

/**
 * An existing client goes straight into Add Counsel Workspace when the server says so (an additive `next` of "add_counsel" on
 * the answer), or, until it does, when the list shows the Counsel workspace can be added (can_add). Any other `next` or no way
 * to add it opens the app as usual (the chooser if there is a choice).
 */
fun doorClientNext(ws: Workspaces?, next: String?): DoorNext = when {
    next == "add_counsel" -> DoorNext.AddCounsel
    !next.isNullOrBlank() -> DoorNext.Open
    ws?.counsel?.canAdd == true -> DoorNext.AddCounsel
    else -> DoorNext.Open
}
