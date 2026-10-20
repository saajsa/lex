package com.legumlex.client.feature.desk

import com.legumlex.client.feature.consultations.apiError
import android.content.Context
import android.net.Uri
import com.legumlex.client.BuildConfig
import com.legumlex.client.data.api.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import retrofit2.HttpException

/* ============================================================================
 *  The Legum Lex desk (Consult), as a client signed in to the portal meets it
 *  (cases/views/public/partials/reception.php). Everything here is a call to
 *  the website's own server code through customers_api v1/desk, or the
 *  site's public notice tracker; nothing is decided on the phone that the
 *  page does not decide.
 * ========================================================================== */

data class DeskCountry(val code: String, val name: String, val dial: String)

/** What the desk knows of a client (cases_desk_context). */
data class DeskContext(
    val firstName: String,
    val company: String,
    val entity: String,            // business | individual | ""
    val counsel: List<String>,
    val phone: String,             // ten digits, or ""
    val openCases: Int,
    val nextHearingDate: String?,  // yyyy-mm-dd
    val nextHearingTitle: String?,
)

/** The enquiry funnel's terms (lexon Funnel::details), and the client. */
data class DeskTerms(
    val isActive: Boolean = true,
    val basePrice: Int = 0,        // paise
    val documentTypes: List<String> = emptyList(),
    val maxFileSize: Long = 0,
    val maxFiles: Int = 0,
    val maxTotalSize: Long = 0,
    val minChars: Int = 20,
    val maxChars: Int = 5000,
    val phoneRequired: Boolean = false,
    val countries: List<DeskCountry> = emptyList(),
    val firm: String = "",
    val context: DeskContext? = null,
) {
    val takesFiles get() = maxFiles > 0 && documentTypes.isNotEmpty()
    val priced get() = basePrice > 0
    val displayPrice get() = com.legumlex.client.feature.finance.formatMoney(basePrice / 100.0, "₹").removeSuffix(".00")
}

/** The desk's reading of a question (cases_desk_read). */
data class DeskReading(
    val verdict: String,           // clear | vague | not_legal | gibberish
    val followUp: String,
    val area: String,
    val urgency: String,
    val reason: String,
    val place: String,
    val intent: String,
    val related: String,
    val documents: List<String>,
    val token: String,             // signed; "" when nothing was learned
)

/** A file the client has chosen, read into memory as the page holds a File. */
class DeskFile(val name: String, val size: Long, val mime: String, val bytes: ByteArray)

sealed interface PayStart {
    /** No fee is due any more, or it has been paid: the enquiry is with the advocates. */
    data class Sent(val consultationId: Int?) : PayStart
    /** The fee's invoice, to be paid on its public page on the website. */
    data class Invoice(val url: String) : PayStart
}

sealed interface Sent {
    data class Delivered(val consultationId: Int?, val email: String) : Sent
    data class Saved(val displayPrice: String, val email: String, val sessionHash: String) : Sent
    data class Refused(val message: String, val errors: Map<String, String>, val sessionHash: String?) : Sent
    data class Failed(val message: String, val timedOut: Boolean) : Sent
}

/** The notice tracker's answer (Landing::ajax_track). */
data class Tracked(
    val isDemo: Boolean,
    val reference: String,
    val tracker: String?,
    val method: String?,
    val dispatched: String?,
    val delivered: String?,
    val deliveryStatus: String?,
    val isDispatched: Boolean,
    val isDelivered: Boolean,
    val waitingEnd: String?,
    val daysRemaining: Int?,
    val converted: Boolean,
)

class DeskRepository(private val context: Context) {
    private val api = ApiClient.get(context)
    private val resolver = context.applicationContext.contentResolver
    /** A lead's desk (lead/desk): the same calls, under the lead's token. */
    private val lead get() = com.legumlex.client.data.auth.SessionRole.isLead

    /** This install's own id, for the guest allowance (a day's two messages). */
    private val device: String by lazy {
        val p = context.applicationContext.getSharedPreferences("desk", Context.MODE_PRIVATE)
        p.getString("device", null) ?: java.util.UUID.randomUUID().toString().also { p.edit().putString("device", it).apply() }
    }

    /**
     * The desk's answer to a guest (guest/desk): (reply, sign in now). Null
     * when it could not be asked; the desk then asks them to sign in.
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun guest(turns: List<Pair<String, String>>): Pair<String, Boolean>? = withTimeoutOrNull(15_000) {
        runCatching {
            val json = org.json.JSONArray(turns.map { (role, text) -> org.json.JSONObject().put("role", role).put("text", text) }).toString()
            val d = api.guestDesk(json, device)["data"] as? Map<String, Any?> ?: return@runCatching null
            val reply = (d["reply"] as? String).orEmpty()
            if (reply.isBlank()) null else reply to (d["sign_in"] == true || (d["left"] as? Number)?.toInt() == 0)
        }.getOrNull()
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun terms(): DeskTerms? = runCatching {
        val d = (if (lead) api.leadDesk() else api.desk())["data"] as? Map<String, Any?> ?: return null
        val c = d["context"] as? Map<String, Any?>
        val hearing = c?.get("next_hearing") as? Map<String, Any?>
        DeskTerms(
            isActive      = d["is_active"] as? Boolean ?: true,
            basePrice     = d["base_price"].int(),
            documentTypes = (d["document_types"] as? List<*>)?.map { it.toString().lowercase() } ?: emptyList(),
            maxFileSize   = d["max_file_size"].long(),
            maxFiles      = d["max_files"].int(),
            maxTotalSize  = d["max_total_size"].long(),
            minChars      = d["min_chars"].int().takeIf { it > 0 } ?: 20,
            maxChars      = d["max_chars"].int().takeIf { it > 0 } ?: 5000,
            phoneRequired = d["phone_required"] as? Boolean ?: false,
            countries     = (d["countries"] as? List<Map<String, Any?>>)?.map {
                DeskCountry(it["code"].toString(), it["name"].toString(), it["dial"].toString())
            } ?: emptyList(),
            firm          = (d["firm"] as? String).orEmpty(),
            context       = c?.takeIf { it["known"] == true }?.let {
                DeskContext(
                    firstName = (it["first_name"] as? String).orEmpty(),
                    company   = (it["company"] as? String).orEmpty(),
                    entity    = (it["entity"] as? String).orEmpty(),
                    counsel   = (it["counsel"] as? List<*>)?.mapNotNull { n -> (n as? String)?.takeIf { s -> s.isNotBlank() } } ?: emptyList(),
                    phone     = (it["phone"] as? String).orEmpty(),
                    openCases = it["open_cases"].int(),
                    nextHearingDate  = hearing?.get("date") as? String,
                    nextHearingTitle = hearing?.get("title") as? String,
                )
            },
        )
    }.getOrNull()

    /** The reading, or null: the desk fails open, as the page does (9 seconds). */
    @Suppress("UNCHECKED_CAST")
    suspend fun check(text: String): DeskReading? = withTimeoutOrNull(9_000) {
        runCatching {
            val d = (if (lead) api.leadDeskCheck(text) else api.deskCheck(text))["data"] as? Map<String, Any?> ?: return@runCatching null
            if (d["success"] != true) return@runCatching null
            DeskReading(
                verdict  = (d["verdict"] as? String) ?: "clear",
                followUp = (d["follow_up"] as? String).orEmpty(),
                area     = (d["area"] as? String).orEmpty(),
                urgency  = (d["urgency"] as? String) ?: "none",
                reason   = (d["urgency_reason"] as? String).orEmpty(),
                place    = (d["place"] as? String) ?: "unknown",
                intent   = (d["intent"] as? String) ?: "new_matter",
                related  = (d["related"] as? String).orEmpty(),
                documents = (d["documents"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                token    = (d["reading"] as? String).orEmpty(),
            )
        }.getOrNull()
    }

    /** A chosen file, read as the page reads one: its name, size and bytes. */
    suspend fun read(uri: Uri): DeskFile? = withContext(Dispatchers.IO) {
        runCatching {
            var name = "attachment"
            var size = -1L
            resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getString(0)?.let { name = it }
                    if (!c.isNull(1)) size = c.getLong(1)
                }
            }
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
            DeskFile(name, if (size >= 0) size else bytes.size.toLong(), resolver.getType(uri) ?: "application/octet-stream", bytes)
        }.getOrNull()
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun send(
        text: String, entity: String, phone: String, country: String?, countryOther: String,
        reading: String, sessionHash: String?, files: List<DeskFile>, terms: DeskTerms,
    ): Sent {
        val parts = buildList {
            add(MultipartBody.Part.createFormData("enquiry_text", text))
            add(MultipartBody.Part.createFormData("entity_type", entity))
            add(MultipartBody.Part.createFormData("phone", phone))
            if (country != null) {
                add(MultipartBody.Part.createFormData("country", country))
                if (country == "OTHER") add(MultipartBody.Part.createFormData("country_other", countryOther))
            }
            if (reading.isNotEmpty()) add(MultipartBody.Part.createFormData("desk_reading", reading))
            if (!sessionHash.isNullOrEmpty()) add(MultipartBody.Part.createFormData("session_hash", sessionHash))
            files.forEach { f ->
                add(MultipartBody.Part.createFormData("attachments[]", f.name, f.bytes.toRequestBody(f.mime.toMediaTypeOrNull())))
            }
        }
        // The page's own allowance: 45 seconds, and three more for each megabyte, up to three minutes.
        val ms = minOf(180_000L, 45_000L + ((files.sumOf { it.size } + 1_048_575) / 1_048_576) * 3_000)
        return try {
            val d = withTimeoutOrNull(ms) { (if (lead) api.leadDeskEnquiry(parts) else api.deskEnquiry(parts))["data"] as? Map<String, Any?> }
                ?: return Sent.Failed("", timedOut = true)
            val email = (d["email"] as? String).orEmpty()
            if (d["require_payment"] == true) Sent.Saved(terms.displayPrice, email, d["session_hash"]?.toString().orEmpty())
            else Sent.Delivered(d["consultation_id"].int().takeIf { it > 0 }, email)
        } catch (e: HttpException) {
            val body = runCatching { JSONObject(e.response()?.errorBody()?.string().orEmpty()) }.getOrNull()
            val errors = buildMap {
                body?.optJSONObject("errors")?.let { o -> o.keys().forEach { k -> put(k, o.optString(k)) } }
            }
            if (e.code() == 422) Sent.Refused(body?.optString("message").orEmpty(), errors, body?.optString("session_hash")?.takeIf { it.isNotBlank() })
            else Sent.Failed(body?.optString("message").orEmpty(), timedOut = false)
        } catch (e: java.io.InterruptedIOException) {
            Sent.Failed("", timedOut = true)
        } catch (e: Exception) {
            Sent.Failed("", timedOut = false)
        }
    }

    /**
     * The enquiry's fee (desk/pay): an invoice, paid on the website (the app
     * takes no payments itself); or, the fee paid or removed meanwhile, the
     * enquiry already sent. Asked again on return from the browser.
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun pay(sessionHash: String): Result<PayStart> = runCatching {
        val d = (if (lead) api.leadDeskPay(sessionHash) else api.deskPay(sessionHash))["data"] as? Map<String, Any?> ?: error("No answer")
        val url = (d["pay_url"] as? String).orEmpty()
        if (url.isNotBlank()) PayStart.Invoice(url) else PayStart.Sent(d["consultation_id"].int().takeIf { it > 0 })
    }.recoverCatching { e -> throw IllegalStateException(serverMessage(e) ?: "The invoice could not be opened. Please try again.") }

    suspend fun discard(sessionHash: String): Result<Unit> = runCatching { if (lead) api.leadDeskDiscard(sessionHash) else api.deskDiscard(sessionHash); Unit }
        .recoverCatching { e -> throw IllegalStateException(serverMessage(e) ?: "The enquiry could not be set aside. Please try again.") }

    // The shared reader caches the body (it can be read once only).
    private fun serverMessage(e: Throwable): String? = e.apiError().message

    /** The Legum Lex site's tracker, at its own address beside the API's. */
    @Suppress("UNCHECKED_CAST")
    suspend fun track(reference: String): Result<Tracked> = runCatching {
        val site = BuildConfig.API_BASE_URL.substringBefore("customers_api/")
        val r = api.trackNotice(site + "cases/landing/ajax_track", reference)
        if (r["success"] != true) throw TrackMiss((r["message"] as? String) ?: "That reference was not found.")
        val d = r["data"] as? Map<String, Any?> ?: throw TrackMiss("That reference was not found.")
        Tracked(
            isDemo = r["is_demo"] == true,
            reference = (d["reference"] as? String) ?: reference,
            tracker = d["tracker"] as? String,
            method = d["method"] as? String,
            dispatched = d["dispatched"] as? String,
            delivered = d["delivered"] as? String,
            deliveryStatus = d["delivery_status"] as? String,
            isDispatched = d["is_dispatched"] == true,
            isDelivered = d["is_delivered"] == true,
            waitingEnd = d["waiting_end"] as? String,
            daysRemaining = (d["days_remaining"] as? Number)?.toInt(),
            converted = d["converted"] == true,
        )
    }
}

class TrackMiss(message: String) : Exception(message)

private fun Any?.int(): Int = when (this) { is Number -> toInt(); is String -> toIntOrNull() ?: 0; else -> 0 }
private fun Any?.long(): Long = when (this) { is Number -> toLong(); is String -> toLongOrNull() ?: 0L; else -> 0L }
