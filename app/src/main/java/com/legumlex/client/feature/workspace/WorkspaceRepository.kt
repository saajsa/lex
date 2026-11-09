package com.legumlex.client.feature.workspace

import android.content.Context
import android.net.Uri
import com.legumlex.client.data.api.ApiClient
import com.legumlex.client.feature.counsel.CounselReview
import com.legumlex.client.feature.counsel.CounselStanding
import com.legumlex.client.feature.counsel.counselRefusal
import com.legumlex.client.feature.counsel.documentParts
import com.legumlex.client.feature.counsel.reviewOf
import com.legumlex.client.feature.counsel.standingOfCounsel
import com.legumlex.client.feature.lead.s

/** What Profile shows about the Counsel workspace, and what Add Counsel Workspace needs: the server's list, eligibility and any draft. */
data class WorkspaceState(val workspaces: Workspaces, val eligible: Boolean, val reason: String, val draft: CounselStanding?)

/**
 * Add / Remove Counsel Workspace (REQ-010), over customers_api workspace*. The server decides who may add, what the documents
 * must be and what removal means; the app carries the steps and shows its words.
 */
class WorkspaceRepository(private val context: Context) {
    private val api = ApiClient.get(context)

    @Suppress("UNCHECKED_CAST")
    suspend fun state(): Result<WorkspaceState> = runCatching {
        val d = api.workspaceList()["data"] as Map<String, Any?>
        val ws = parseWorkspaceList(d)
        WorkspaceSession.update(ws)
        WorkspaceState(ws, d["eligible"] == true, d["reason"].s(), heldDraft(d["draft"] as? Map<String, Any?>))
    }.recoverCatching { throw it.counselRefusal("Your workspaces could not be loaded just now. Please try again.") }

    @Suppress("UNCHECKED_CAST")
    suspend fun begin(): Result<CounselStanding> = runCatching { standingOfCounsel(api.workspaceBegin()["data"] as Map<String, Any?>) }
        .recoverCatching { throw it.counselRefusal("Adding the Counsel workspace could not be started. Please try again.") }

    @Suppress("UNCHECKED_CAST")
    suspend fun upload(type: String, uri: Uri, onProgress: (Float) -> Unit = {}): Result<Pair<CounselStanding, String>> = runCatching {
        val d = api.workspaceDocument(documentParts(context, type, uri, onProgress))["data"] as Map<String, Any?>
        standingOfCounsel(d["standing"] as Map<String, Any?>) to listOf(d["notice"].s(), d["warning"].s()).filter { it.isNotBlank() }.joinToString(" ")
    }.recoverCatching { throw it.counselRefusal("The file could not be uploaded. Please try again.") }

    @Suppress("UNCHECKED_CAST")
    suspend fun review(): Result<CounselReview> = runCatching { reviewOf(api.workspaceReview()["data"] as Map<String, Any?>) }
        .recoverCatching { throw it.counselRefusal("Your details could not be loaded. Please try again.") }

    @Suppress("UNCHECKED_CAST")
    suspend fun saveReview(fields: Map<String, String>): Result<CounselStanding> = runCatching { standingOfCounsel(api.workspaceReviewSave(fields)["data"] as Map<String, Any?>) }
        .recoverCatching { throw it.counselRefusal("Your details could not be saved. Please try again.") }

    /** Submit: the Counsel workspace opens at once. The answer is the new list and the server's words. */
    @Suppress("UNCHECKED_CAST")
    suspend fun submit(): Result<Pair<Workspaces, String>> = runCatching {
        val r = api.workspaceSubmit(mapOf("terms" to "1"))
        val ws = parseWorkspaceList(r["data"] as? Map<String, Any?>)
        WorkspaceSession.update(ws)
        ws to r["message"].s()
    }.recoverCatching { throw it.counselRefusal("Your Counsel workspace could not be added. Please try again.") }

    suspend fun discard(): Result<Unit> = runCatching { api.workspaceDiscard(); Unit }
        .recoverCatching { throw it.counselRefusal("That could not be set aside. Please try again.") }

    /** Remove the Counsel workspace. The server refuses a counsel-only account, with its words. */
    @Suppress("UNCHECKED_CAST")
    suspend fun remove(): Result<Pair<Workspaces, String>> = runCatching {
        val r = api.workspaceRemove()
        val ws = parseWorkspaceList(r["data"] as? Map<String, Any?>)
        WorkspaceSession.update(ws)
        ws to r["message"].s()
    }.recoverCatching { throw it.counselRefusal("The workspace could not be removed just now. Please try again.") }
}

/**
 * The draft the server holds for this contact, or null. GET workspace always sends a standing, so "no draft" arrives as
 * `started: false` with no documents; only a started one is a draft to pick up (otherwise begin must be called).
 */
@Suppress("UNCHECKED_CAST")
internal fun heldDraft(d: Map<String, Any?>?): CounselStanding? = d?.let { standingOfCounsel(it) }?.takeIf { it.started }
