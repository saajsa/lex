package com.legumlex.client.feature.counsel

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.legumlex.client.data.api.ApiClient
import com.legumlex.client.feature.lead.OnbStanding
import com.legumlex.client.feature.lead.s
import com.legumlex.client.feature.lead.said
import com.legumlex.client.feature.lead.standingOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okio.buffer
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import retrofit2.HttpException

/*
 * Counsel registration (REQ-010): a lead who signed in with Google registers as counsel over customers_api lead/counsel*.
 * The server decides everything that matters (who may register, the staff rule, the flags, the review); the app only
 * carries the steps: Certificate, PAN, Review, Mobile, Confirm. The workspace opens at once on submit and the answer
 * carries the client token the app switches to.
 */

/** Whether the door was opened from a lead's own app (their sign-in stays; nothing is forgotten when they step back). */
object CounselFlow { @Volatile var keepSession: Boolean = false }

/** Who this is to the firm, and where the draft stands: outcome new | client | counsel | pending | rejected | staff. */
data class CounselTriage(val outcome: String, val message: String, val note: String, val standing: CounselStanding, val next: String = "")

/** The draft: the two documents, whether codes can be sent, and the mobile already confirmed (if any). */
data class CounselStanding(val onb: OnbStanding, val started: Boolean, val codes: Boolean, val verified: String?)

/** What was read from the documents (fixed) and what the advocate types. */
data class CounselReview(
    val fullName: String, val nameLocked: Boolean, val enrolment: String, val numberLocked: Boolean,
    val stateBar: String, val enrolledOn: String, val pan: String, val panLocked: Boolean,
    val state: String, val states: List<String>, val unread: List<String>,
)

/** The account that opened: the client token to switch to. */
data class CounselOpened(val token: String, val clientId: Int, val contactId: Int)

/** A refusal: the server's code, the field it is about and its words (409 begin carries the outcome as code outcome_<x>). */
class CounselRefused(val code: String, val field: String, message: String, val next: String = "") : Exception(message)

internal fun Throwable.counselRefusal(fallback: String): CounselRefused {
    if (this is CounselRefused) return this
    if (this is HttpException) {
        val j = runCatching { JSONObject(response()?.errorBody()?.string().orEmpty()) }.getOrNull()
        val msg = j?.optString("message").orEmpty().ifBlank { fallback }
        return CounselRefused(j?.optString("code").orEmpty(), j?.optString("field").orEmpty(), msg, j?.optString("next").orEmpty())
    }
    return CounselRefused("", "", if (this is java.io.IOException) offlineMessage() else fallback)
}

/** What the person sees when there is no connection. */
internal fun offlineMessage() = "There is no connection just now. Your details are kept; please try again when you are back online."

@Suppress("UNCHECKED_CAST")
internal fun standingOfCounsel(d: Map<String, Any?>): CounselStanding {
    val phone = d["phone"] as? Map<String, Any?>
    val verified = (phone?.get("verified") as? Map<String, Any?>)?.get("number") as? String
    return CounselStanding(standingOf(d), d["started"] == true, d["codes"] == true, verified)
}

@Suppress("UNCHECKED_CAST")
internal fun triageOf(d: Map<String, Any?>) = CounselTriage(d["outcome"].s(), d["message"].s(), d["note"].s(), standingOfCounsel(d), d["next"].s())

internal fun reviewOf(d: Map<String, Any?>) = CounselReview(
    d["full_name"].s(), d["name_locked"] == true, d["enrollment_number"].s(), d["number_locked"] == true,
    d["state_bar_council"].s(), d["enrollment_date"].s(), d["pan_number"].s(), d["pan_locked"] == true,
    d["state"].s(), (d["states"] as? List<*>).orEmpty().mapNotNull { it as? String }, (d["unread"] as? List<*>).orEmpty().mapNotNull { it as? String },
)

/** A body that says how much of itself has gone out (0 to 1), so a slow upload shows it is moving. */
internal class ProgressBody(private val inner: okhttp3.RequestBody, private val onProgress: (Float) -> Unit) : okhttp3.RequestBody() {
    override fun contentType() = inner.contentType()
    override fun contentLength() = inner.contentLength()
    override fun writeTo(sink: okio.BufferedSink) {
        val total = contentLength().coerceAtLeast(1L)
        var sent = 0L
        val counting = object : okio.ForwardingSink(sink) {
            override fun write(source: okio.Buffer, byteCount: Long) { super.write(source, byteCount); sent += byteCount; onProgress((sent.toFloat() / total).coerceAtMost(1f)) }
        }
        val buffered = counting.buffer()
        inner.writeTo(buffered)
        buffered.flush()
    }
}

/** The multipart body of one picked document: its type, its name and its bytes (read off the main thread). */
internal suspend fun documentParts(context: Context, type: String, uri: Uri, onProgress: (Float) -> Unit = {}): List<MultipartBody.Part> {
    val (name, bytes, mime) = withContext(Dispatchers.IO) {
        val cr = context.contentResolver
        val n = cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "$type.pdf"
        Triple(n, cr.openInputStream(uri)!!.use { it.readBytes() }, cr.getType(uri) ?: "application/pdf")
    }
    return listOf(
        MultipartBody.Part.createFormData("document_type", type),
        MultipartBody.Part.createFormData("document", name, ProgressBody(bytes.toRequestBody(mime.toMediaTypeOrNull()), onProgress)),
    )
}

class CounselRepository(private val context: Context) {
    private val api = ApiClient.get(context)

    @Suppress("UNCHECKED_CAST")
    suspend fun triage(): Result<CounselTriage> = runCatching {
        triageOf(api.counselMe()["data"] as Map<String, Any?>)
    }.recoverCatching { throw it.counselRefusal("Registration could not be checked just now. Please try again.") }

    /** Start a new draft, or reopen a rejected registration for correction. */
    @Suppress("UNCHECKED_CAST")
    suspend fun begin(): Result<CounselStanding> = runCatching { standingOfCounsel(api.counselBegin()["data"] as Map<String, Any?>) }
        .recoverCatching { throw it.counselRefusal("Registration could not be started. Please try again.") }

    /** Upload one document: the standing, and a notice or warning to show. */
    @Suppress("UNCHECKED_CAST")
    suspend fun upload(type: String, uri: Uri, onProgress: (Float) -> Unit = {}): Result<Pair<CounselStanding, String>> = runCatching {
        val parts = documentParts(context, type, uri, onProgress)
        val d = api.counselDocument(parts)["data"] as Map<String, Any?>
        standingOfCounsel(d["standing"] as Map<String, Any?>) to listOf(d["notice"].s(), d["warning"].s()).filter { it.isNotBlank() }.joinToString(" ")
    }.recoverCatching { throw it.counselRefusal("The file could not be uploaded. Please try again.") }

    @Suppress("UNCHECKED_CAST")
    suspend fun remove(type: String): Result<CounselStanding> = runCatching { standingOfCounsel(api.counselRemove(type)["data"] as Map<String, Any?>) }
        .recoverCatching { throw it.counselRefusal("The document could not be removed. Please try again.") }

    @Suppress("UNCHECKED_CAST")
    suspend fun review(): Result<CounselReview> = runCatching {
        reviewOf(api.counselReview()["data"] as Map<String, Any?>)
    }.recoverCatching { throw it.counselRefusal("Your details could not be loaded. Please try again.") }

    /** Review's answers: full_name, enrollment_number, state_bar_council, pan_number, state. A refusal names its field. */
    @Suppress("UNCHECKED_CAST")
    suspend fun saveReview(fields: Map<String, String>): Result<CounselStanding> = runCatching { standingOfCounsel(api.counselReviewSave(fields)["data"] as Map<String, Any?>) }
        .recoverCatching { throw it.counselRefusal("Your details could not be saved. Please try again.") }

    /** The mobile's code: request (number) | status | sms | verify (code). The server's data, or its words. */
    @Suppress("UNCHECKED_CAST")
    suspend fun phone(step: String, fields: Map<String, String> = emptyMap()): Result<Map<String, Any?>> = runCatching {
        api.counselPhone(step, fields)["data"] as? Map<String, Any?> ?: emptyMap()
    }.recoverCatching { throw it.counselRefusal("That did not go through. Please try again.") }

    /** Submit: the workspace opens at once; the answer carries the client token. */
    @Suppress("UNCHECKED_CAST")
    suspend fun submit(phonenumber: String): Result<CounselOpened> = runCatching {
        val d = api.counselSubmit(mapOf("terms" to "1", "phonenumber" to phonenumber))["data"] as Map<String, Any?>
        val token = d["token"] as? String
        val client = (d["client_id"] as? Number)?.toInt() ?: d["client_id"]?.toString()?.toIntOrNull()
        val contact = (d["contact_id"] as? Number)?.toInt() ?: d["contact_id"]?.toString()?.toIntOrNull()
        if (token.isNullOrBlank() || client == null || contact == null) throw CounselRefused("pending", "", "Your registration is with the firm. We will write to you when your account opens.")
        CounselOpened(token, client, contact)
    }.recoverCatching { throw it.counselRefusal("Your registration could not be sent. Please try again.") }

    suspend fun discard(): Result<Unit> = runCatching { api.counselDiscard(); Unit }
        .recoverCatching { throw it.counselRefusal("That could not be set aside. Please try again.") }
}
