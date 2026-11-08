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

    /** The result options, as the server last listed them. */
    /** The credits chip in the Counsel header: balance and whether it is low, as board/today last said. */
    data class Chip(val balance: Int, val low: Boolean)
    private val _chip = MutableStateFlow<Chip?>(null)
    val chip: StateFlow<Chip?> = _chip.asStateFlow()
    fun setChip(balance: Int, low: Boolean) { _chip.value = Chip(balance, low) }

    /** The day chosen on the Calendar (yyyy-MM-dd): the bar's Print prints it; null until a day is chosen (today then). */
    private val _chosen = MutableStateFlow<String?>(null)
    val chosen: StateFlow<String?> = _chosen.asStateFlow()
    fun choose(date: String?) { _chosen.value = date }

    val statuses: List<String> get() = (_access.value as? BoardAccess.Yes)?.summary?.statuses.orEmpty()

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
        val perms = ProfileHub.profile.value?.permissions?.map { it.lowercase() }
        if (perms != null && perms.none { "board" in it }) { _access.value = BoardAccess.No; return }
        BoardRepository(context).summary()
            .onSuccess { set(it) }
            .onFailure { e ->
                val http = e.apiError().http
                if (http == 404 || http == 403) hide()
            }
    }
}

/** The Counsel workspace is on when the server says so (board/summary `counsel`): a separate chrome and home. */
fun isCounsel(access: BoardAccess): Boolean = (access as? BoardAccess.Yes)?.summary?.counsel == true
