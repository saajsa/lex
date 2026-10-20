package com.legumlex.client.feature.lead

import android.content.Context
import com.legumlex.client.BuildConfig
import com.legumlex.client.data.api.ApiClient
import com.legumlex.client.feature.consultations.Reply
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/*
 * A lead's enquiries (owner, 2026-10-20): the website's own My Matters and
 * thread (lexon/api/funnel/my_sessions, thread, thread/{hash}/reply), which
 * take the lead's token as the identity they would take from Google.
 */

/** One of the lead's matters, as the website's My Matters lists it. */
data class LeadEnquiry(
    val sessionHash: String,
    val kind: String,            // enquiry | ahc
    val title: String,
    val reference: String,
    val summary: String,
    val label: String,           // "With Your Advocate"…
    val state: String,           // in_progress | …
    val detail: String,
    val unread: Int,
    val replies: Int,
    val lastActivity: LocalDateTime?,
)

data class LeadThread(
    val title: String,
    val reference: String,
    val summary: String,
    val label: String,
    val advocate: String,
    val messages: List<Reply>,
    val canReply: Boolean,
    val closedReason: String?,
)

private val Stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
private fun stamp(v: Any?): LocalDateTime? = (v as? String)?.let { runCatching { LocalDateTime.parse(it, Stamp) }.getOrNull() }
private fun Any?.int(): Int = when (this) { is Number -> toInt(); is String -> toIntOrNull() ?: 0; else -> 0 }
private fun Any?.str(): String = (this as? String).orEmpty()

class LeadRepository(context: Context) {
    private val api = ApiClient.get(context)
    private val site = BuildConfig.API_BASE_URL.substringBefore("customers_api/")

    @Suppress("UNCHECKED_CAST")
    suspend fun enquiries(): Result<List<LeadEnquiry>> = runCatching {
        val r = api.funnelMySessions(site + "lexon/api/funnel/my_sessions")
        if (r["success"] != true) error(r["error"].str().ifBlank { "Your enquiries could not be loaded." })
        (r["sessions"] as? List<Map<String, Any?>>).orEmpty().map { s ->
            LeadEnquiry(
                sessionHash  = s["session_hash"].str(),
                kind         = s["kind"].str(),
                title        = s["document_name"].str().ifBlank { "Legal enquiry" },
                reference    = s["reference"].str(),
                summary      = s["summary"].str(),
                label        = s["label"].str(),
                state        = s["state"].str(),
                detail       = s["detail"].str(),
                unread       = s["unread"].int(),
                replies      = s["reply_count"].int(),
                lastActivity = stamp(s["last_activity"]) ?: stamp(s["started_at"]),
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun thread(hash: String): Result<LeadThread> = runCatching {
        val r = api.funnelThread(site + "lexon/api/funnel/thread/$hash")
        if (r["success"] != true) error(r["error"].str().ifBlank { "This enquiry could not be loaded." })
        val m = r["matter"] as? Map<String, Any?> ?: emptyMap()
        val advocate = r["advocate"].str()
        LeadThread(
            title        = m["title"].str().ifBlank { "Legal enquiry" },
            reference    = m["reference"].str(),
            summary      = m["summary"].str(),
            label        = m["label"].str(),
            advocate     = advocate,
            messages     = (r["messages"] as? List<Map<String, Any?>>).orEmpty().mapIndexed { i, x ->
                val mine = x["from"] == "you"
                Reply(
                    id          = "l$i",
                    message     = x["message"].str(),
                    fromClient  = mine,
                    authorName  = x["author"].str().ifBlank { if (mine) "You" else advocate },
                    authorImage = null,
                    createdAt   = stamp(x["date_added"]),
                    readAt      = stamp(x["read_at"]),
                )
            },
            canReply     = r["can_reply"] == true,
            closedReason = (r["closed_reason"] as? String)?.takeIf { it.isNotBlank() },
        )
    }

    suspend fun reply(hash: String, message: String): Result<Unit> = runCatching {
        val r = api.funnelReply(site + "lexon/api/funnel/thread/$hash/reply", message)
        if (r["success"] != true) error(r["error"].str().ifBlank { "Your reply could not be sent." })
    }
}
