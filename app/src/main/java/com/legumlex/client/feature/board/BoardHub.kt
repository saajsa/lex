package com.legumlex.client.feature.board

import android.content.Context
import com.legumlex.client.feature.consultations.apiError
import com.legumlex.client.feature.profile.ProfileHub
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Whether this sign-in has the Daily Board, as the server says. */
sealed interface BoardAccess {
    /** Not asked yet, or the server could not be reached. */
    data object Unknown : BoardAccess

    /** Not for this account, or the server's switch is off (404 board_off, 403): the feature stays hidden. */
    data object No : BoardAccess

    data class Yes(val summary: BoardSummary) : BoardAccess
}

/**
 * Who has the Daily Board, app-wide: Home's tile and More's row show only when
 * the server answers the board's summary. The server decides (its switch, the
 * contact's permission, a board profile), so a 404 or 403 hides the feature and
 * nothing else changes. A plain singleton, as the app's other hubs are; cleared
 * at sign-out and when the person switches accounts.
 */
object BoardHub {
    private val _access = MutableStateFlow<BoardAccess>(BoardAccess.Unknown)
    val access: StateFlow<BoardAccess> = _access.asStateFlow()

    /** The credits chip in the Counsel header: balance and whether it is low, as board/today last said. */
    data class Chip(val balance: Int, val low: Boolean)
    private val _chip = MutableStateFlow<Chip?>(null)
    val chip: StateFlow<Chip?> = _chip.asStateFlow()
    fun setChip(balance: Int, low: Boolean) { _chip.value = Chip(balance, low) }

    /** The day chosen on the Calendar (yyyy-MM-dd): the bar's Print prints it; null until a day is chosen (today then). */
    private val _chosen = MutableStateFlow<String?>(null)
    val chosen: StateFlow<String?> = _chosen.asStateFlow()
    fun choose(date: String?) { _chosen.value = date }

    /** Whether this run is known to be in the Counsel workspace (the chooser's answer, or the server's counsel-only). */
    private fun expectCounsel() = com.legumlex.client.feature.workspace.WorkspaceSession.mode.value == com.legumlex.client.feature.workspace.Mode.Counsel

    fun clear() { _access.value = BoardAccess.Unknown; _chip.value = null; _chosen.value = null }

    fun set(summary: BoardSummary) { _access.value = BoardAccess.Yes(summary) }

    /** The server refused the board (404 board_off, 403): hide it. */
    fun hide() { _access.value = BoardAccess.No }

    /**
     * Ask the server. A contact whose profile lists no board permission is not
     * asked at all (every other client). A server that cannot be reached leaves
     * what was known as it was.
     */
    suspend fun refresh(context: Context) {
        // In the Client Portal the board is not part of the workspace: nothing is asked.
        if (com.legumlex.client.feature.workspace.WorkspaceSession.mode.value == com.legumlex.client.feature.workspace.Mode.Portal) { hide(); return }
        val repo = BoardRepository(context)
        val first = repo.summary()
        var r = first
        val answers = mutableListOf(answerOf(first))
        // A known counsel is not turned away by one refusal: ask once more before believing it.
        if (needsRetry(expectCounsel(), answers[0])) { kotlinx.coroutines.delay(1_500); r = repo.summary(); answers += answerOf(r) }
        when (decideAccess(expectCounsel(), answers)) {
            Verdict.Yes -> r.onSuccess { set(it) }
            Verdict.No -> {
                // A person in the Counsel workspace whom the server now refuses moves to the Client Portal, with the server's words.
                val wasCounsel = expectCounsel()
                if (wasCounsel) {
                    val said = r.exceptionOrNull()?.apiError()?.message
                    com.legumlex.client.feature.workspace.WorkspaceSession.set(com.legumlex.client.feature.workspace.Mode.Portal)
                    com.legumlex.client.feature.workspace.WorkspaceSession.say(said ?: com.legumlex.client.feature.workspace.GONE_NOTE)
                }
                hide()
            }
            Verdict.Keep -> Unit
        }
    }

    private fun answerOf(r: Result<BoardSummary>): Answer {
        if (r.isSuccess) return Answer.Ok
        val http = r.exceptionOrNull()?.apiError()?.http
        return if (http == 404 || http == 403) Answer.Refused else Answer.Failed
    }
}

enum class Answer { Ok, Refused, Failed }
enum class Verdict { Yes, No, Keep }

/** Whether a refusal is asked again before it is believed: only for an account known as counsel. */
fun needsRetry(expectCounsel: Boolean, first: Answer): Boolean = expectCounsel && first == Answer.Refused

/**
 * What the server's board/summary answers decide. Only a refusal (404 or 403) can decide No: a failure to reach the server
 * decides nothing. An account never known as counsel is refused on the first refusal; a remembered counsel only when the
 * retry refuses too (two refusals); an answer that is not a refusal (success or failure) after a first refusal rescues it.
 */
fun decideAccess(expectCounsel: Boolean, answers: List<Answer>): Verdict {
    val last = answers.lastOrNull() ?: return Verdict.Keep
    return when {
        last == Answer.Ok -> Verdict.Yes
        last == Answer.Failed -> Verdict.Keep
        expectCounsel && answers.size < 2 -> Verdict.Keep
        else -> Verdict.No
    }
}

/** The Counsel workspace is on when the server says so (board/summary `counsel`): a separate chrome and home. */
fun isCounsel(access: BoardAccess): Boolean = (access as? BoardAccess.Yes)?.summary?.counsel == true

/** The chrome is the Counsel's: the server said so, or it did last time and has not yet answered this time. */
fun counselChrome(access: BoardAccess, expectCounsel: Boolean): Boolean =
    isCounsel(access) || (access is BoardAccess.Unknown && expectCounsel)

/** Nothing is known yet (no answer, nothing remembered): neutral chrome, no client label. */
fun workspaceUnknown(access: BoardAccess, expectCounsel: Boolean): Boolean =
    access is BoardAccess.Unknown && !expectCounsel

/** The chrome for this run: the chooser's answer first; without one, the server's board answer decides. */
fun counselChromeFor(access: BoardAccess, mode: com.legumlex.client.feature.workspace.Mode): Boolean = when (mode) {
    com.legumlex.client.feature.workspace.Mode.Portal -> false
    com.legumlex.client.feature.workspace.Mode.Counsel -> counselChrome(access, true)
    com.legumlex.client.feature.workspace.Mode.Unknown -> counselChrome(access, false)
}

/** Neutral chrome only while nothing is known: no choice made and no board answer yet. */
fun workspaceUnknownFor(access: BoardAccess, mode: com.legumlex.client.feature.workspace.Mode): Boolean =
    mode == com.legumlex.client.feature.workspace.Mode.Unknown && workspaceUnknown(access, false)
