package com.legumlex.client.data.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/*
 * Who is signed in (owner, 2026-10-20): a client, or a lead. Anyone may sign
 * in with Google; someone the firm has no open account for is a lead, with a
 * smaller app (the desk, the Agreement Check, their enquiries). A lead's token
 * opens none of the client endpoints, so nothing that calls one may run while
 * a lead is signed in: the bell, the profile, push.
 *
 * Read from TokenStore at launch (the splash) and set at each sign-in.
 */
object SessionRole {
    private val _lead = MutableStateFlow(false)
    val lead: StateFlow<Boolean> = _lead

    /** lead | awaiting_approval | rejected (sent back to be corrected), for a lead; empty for a client. */
    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status

    val isLead: Boolean get() = _lead.value

    /** Why the firm sent the registration back (status rejected), in the reviewer's words. */
    val reviewNote = MutableStateFlow("")

    /** The lead's name, as Google gave it (for the greeting). */
    val name = MutableStateFlow("")

    /** A new registration can be started (sign-up open, or one under way); lead/me. */
    val canRegister = MutableStateFlow(true)

    /** The desk opens accounts by conversation (lexon_desk_onboarding); lead/me. */
    val accountDesk = MutableStateFlow(false)

    fun set(role: String?, status: String? = null) {
        _lead.value = role == "lead"
        _status.value = if (role == "lead") status.orEmpty().ifBlank { "lead" } else ""
    }
}
