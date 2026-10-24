package com.legumlex.client.feature.lead

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.legumlex.client.data.api.ApiClient
import com.legumlex.client.data.auth.SessionRole
import com.legumlex.client.data.auth.TokenStore
import com.legumlex.client.feature.healthchecks.funnel.FunnelPrimaryButton
import com.legumlex.client.feature.scanner.rememberDocumentScannerLauncher
import com.legumlex.client.ui.components.Eyebrow
import com.legumlex.client.ui.components.LexCard
import com.legumlex.client.ui.components.WizardChoice
import com.legumlex.client.ui.components.WizardDropdown
import com.legumlex.client.ui.components.WizardFrame
import com.legumlex.client.ui.components.WizardHeading
import com.legumlex.client.ui.components.WizardRows
import com.legumlex.client.ui.components.WizardTextField
import com.legumlex.client.ui.components.WizardTick
import com.legumlex.client.ui.theme.Lex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import retrofit2.HttpException

/*
 * A lead opens a client account (owner, 2026-10-20): the website's
 * registration (lexon/register), step for step, over lead/onboarding. The
 * Google step is the lead's own sign-in; the documents are read as on the
 * website; Review is Details, Address, Contact and Confirm; the account ends
 * with the firm for approval, and the app opens as the client's when the firm
 * approves it. One the firm sends back is reopened here (owner, 2026-10-23):
 * the documents on file are kept, the reviewer's note is shown, and the
 * corrections are sent again.
 *
 * As on the website (owner, 2026-10-23): no password — the client signs in
 * with Google or with a code to the mobile confirmed on the Contact step,
 * which a registration cannot complete without; a document that fails the
 * basic checks (the wrong card, unreadable, someone else's) is refused, and
 * one on file that fails them is shown for replacing; what the documents say
 * about who the registrant is stays as read.
 */

data class OnbDoc(val type: String, val label: String, val description: String, val required: Boolean,
                  val uploaded: Boolean, val kept: Boolean, val name: String?, val read: Boolean?, val fields: List<Pair<String, String>>,
                  val problem: String?)

data class OnbStanding(val email: String, val problem: String, val types: List<Pair<String, String>>,
                       val entityType: String?, val documents: List<OnbDoc>, val allUploaded: Boolean,
                       val reopened: Boolean, val reviewNote: String)

data class OnbReview(val entityType: String, val email: String, val givenName: String, val familyName: String,
                     val details: Map<String, String>, val sources: Map<String, String>, val unread: List<String>,
                     val countries: List<Pair<Int, String>>, val panRead: String, val locked: Set<String>,
                     val recordVerified: Boolean, val verified: String?, val codes: Boolean, val consent: String)

/** The mobile's code, as the Contact step holds it. */
data class OnbPhone(val sent: Boolean = false, val to: String = "", val resendIn: Int = 0, val smsIn: Int = 0,
                    val verified: String? = null, val busy: Boolean = false, val error: String = "")

private fun Any?.s(): String = (this as? String).orEmpty()

@Suppress("UNCHECKED_CAST")
private fun standingOf(d: Map<String, Any?>) = OnbStanding(
    email = d["email"].s(),
    problem = d["problem"].s(),
    types = (d["entity_types"] as? List<Map<String, Any?>>).orEmpty().map { it["value"].s() to it["label"].s() },
    entityType = (d["entity_type"] as? String)?.takeIf { it.isNotBlank() },
    documents = (d["documents"] as? List<Map<String, Any?>>).orEmpty().map { x ->
        OnbDoc(x["type"].s(), x["label"].s(), x["description"].s(), x["required"] == true, x["uploaded"] == true,
            x["kept"] == true, x["name"] as? String, x["read"] as? Boolean,
            (x["fields"] as? List<Map<String, Any?>>).orEmpty().map { it["label"].s() to it["value"].s() },
            (x["problem"] as? String)?.takeIf { it.isNotBlank() })
    },
    allUploaded = d["all_uploaded"] == true,
    reopened = d["reopened"] == true,
    reviewNote = d["review_note"].s(),
)

/** The server's own words, from a refusal. */
private fun Throwable.said(fallback: String): String = when (this) {
    is HttpException -> runCatching { JSONObject(response()?.errorBody()?.string().orEmpty()).optString("message") }.getOrNull()
        .orEmpty().ifBlank { fallback }
    else -> fallback
}

class OnboardingRepository(private val context: Context) {
    private val api = ApiClient.get(context)

    @Suppress("UNCHECKED_CAST")
    suspend fun standing(): Result<OnbStanding> = runCatching { standingOf(api.leadOnboarding()["data"] as Map<String, Any?>) }
        .recoverCatching { throw IllegalStateException(it.said("Your registration could not be loaded.")) }

    @Suppress("UNCHECKED_CAST")
    suspend fun begin(type: String): Result<OnbStanding> = runCatching { standingOf(api.leadOnboardingBegin(type)["data"] as Map<String, Any?>) }
        .recoverCatching { throw IllegalStateException(it.said("That could not be started.")) }

    /** Upload one document: (the standing, a notice or warning to show). */
    @Suppress("UNCHECKED_CAST")
    suspend fun upload(type: String, uri: Uri): Result<Pair<OnbStanding, String>> = runCatching {
        val (name, bytes, mime) = withContext(Dispatchers.IO) {
            val cr = context.contentResolver
            val n = cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "$type.pdf"
            Triple(n, cr.openInputStream(uri)!!.use { it.readBytes() }, cr.getType(uri) ?: "application/pdf")
        }
        val parts = listOf(
            MultipartBody.Part.createFormData("document_type", type),
            MultipartBody.Part.createFormData("document", name, bytes.toRequestBody(mime.toMediaTypeOrNull())),
        )
        val d = api.leadOnboardingDocument(parts)["data"] as Map<String, Any?>
        standingOf(d["standing"] as Map<String, Any?>) to listOf(d["notice"].s(), d["warning"].s()).filter { it.isNotBlank() }.joinToString(" ")
    }.recoverCatching { throw IllegalStateException(it.said("The file could not be uploaded. Try again.")) }

    @Suppress("UNCHECKED_CAST")
    suspend fun remove(type: String): Result<OnbStanding> = runCatching { standingOf(api.leadOnboardingRemove(type)["data"] as Map<String, Any?>) }
        .recoverCatching { throw IllegalStateException(it.said("The document could not be removed. Try again.")) }

    /** The registration the firm sent back, opened again to be corrected. */
    @Suppress("UNCHECKED_CAST")
    suspend fun reopen(): Result<OnbStanding> = runCatching { standingOf(api.leadOnboardingReopen()["data"] as Map<String, Any?>) }
        .recoverCatching { throw IllegalStateException(it.said("Your registration could not be opened. Try again.")) }

    @Suppress("UNCHECKED_CAST")
    suspend fun review(): Result<OnbReview> = runCatching {
        val d = api.leadOnboardingReview()["data"] as Map<String, Any?>
        fun map(v: Any?) = (v as? Map<String, Any?>).orEmpty().mapValues { (_, x) -> x?.toString().orEmpty() }
        val phone = d["phone"] as? Map<String, Any?>
        OnbReview(d["entity_type"].s(), d["email"].s(), d["given_name"].s(), d["family_name"].s(),
            map(d["details"]), map(d["sources"]), (d["unread"] as? List<*>).orEmpty().mapNotNull { it as? String },
            (d["countries"] as? List<Map<String, Any?>>).orEmpty().map { ((it["id"] as? Number)?.toInt() ?: 0) to it["name"].s() },
            d["pan_read"].s(), (d["locked"] as? List<*>).orEmpty().mapNotNull { it as? String }.toSet(),
            phone?.get("record_verified") == true, ((phone?.get("verified") as? Map<String, Any?>)?.get("number") as? String),
            d["codes"] == true, d["consent"].s())
    }.recoverCatching { throw IllegalStateException(it.said("Your details could not be loaded.")) }

    /** Submit: Unit, or the refusal's field and words (Left: field, message). */
    suspend fun submit(fields: Map<String, String>): Result<Unit> = runCatching {
        api.leadOnboardingSubmit(fields)
        TokenStore(context.applicationContext).saveLeadStatus("awaiting_approval")
    }.recoverCatching {
        val field = (it as? HttpException)?.let { e -> runCatching { JSONObject(e.response()?.errorBody()?.string().orEmpty()).optString("field") }.getOrNull() }.orEmpty()
        throw OnbRefused(field, it.said("The registration could not be completed."))
    }

    /** The mobile's code: request (number) | status | sms | verify (code). The server's data, or its words. */
    @Suppress("UNCHECKED_CAST")
    suspend fun phone(step: String, fields: Map<String, String> = emptyMap()): Result<Map<String, Any?>> = runCatching {
        api.leadOnboardingPhone(step, fields)["data"] as? Map<String, Any?> ?: emptyMap()
    }.recoverCatching { throw IllegalStateException(it.said("That did not go through. Please try again.")) }

    suspend fun discard(): Result<Unit> = runCatching { api.leadOnboardingDiscard(); Unit }
        .recoverCatching { throw IllegalStateException(it.said("That could not be set aside. Try again.")) }
}

/** A refusal at submit: the field it is about, and the server's words. */
class OnbRefused(val field: String, message: String) : Exception(message)

private val Steps = listOf("Type", "Files", "Details", "Address", "Contact", "Confirm")

@Composable
fun LeadOnboardingScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val repo = remember(context) { OnboardingRepository(context) }
    val scope = rememberCoroutineScope()

    // The step, the choice and what was typed survive a rotation; the rest is the server's.
    var step by rememberSaveable { mutableIntStateOf(0) }       // 0..5, 6 = sent
    var standing by remember { mutableStateOf<OnbStanding?>(null) }
    var review by remember { mutableStateOf<OnbReview?>(null) }
    var busy by remember { mutableStateOf("") }          // the document being read, or "submit"
    var busySince by remember { mutableLongStateOf(0L) }
    var error by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }
    var choice by rememberSaveable { mutableStateOf<String?>(null) }
    val f = rememberSaveable(saver = FieldsSaver) { mutableStateMapOf<String, String>() } // Review's fields
    var phone by remember { mutableStateOf(OnbPhone()) }
    var confirmRemove by remember { mutableStateOf<OnbDoc?>(null) }
    val sentBack by SessionRole.status.collectAsState()

    LaunchedEffect(Unit) {
        repo.standing().onSuccess { s0 ->
            // Sent back by the firm: open that registration again, documents kept.
            val s = if (sentBack == "rejected" && !s0.reopened) repo.reopen().getOrElse { error = it.message.orEmpty(); s0 } else s0
            standing = s; if (choice == null) choice = s.entityType
            // A draft under way picks up at its documents (only on a first open, not after a rotation).
            if (s.entityType != null && step == 0 && !s.reopened) step = 1
        }.onFailure { error = it.message.orEmpty() }
    }

    fun upload(type: String, uri: Uri) = scope.launch {
        busy = type; busySince = System.currentTimeMillis(); error = ""; notice = ""
        repo.upload(type, uri).onSuccess { (s, n) -> standing = s; review = null; notice = n }.onFailure { error = it.message.orEmpty() }
        busy = ""
    }
    var target by rememberSaveable { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) upload(target, uri) }
    val scan = rememberDocumentScannerLauncher { uri -> if (uri != null) upload(target, uri) }

    /** The readings for Details on; [go] moves there (false: reloading them after a rotation). */
    fun toReview(go: Boolean = true) = scope.launch {
        error = ""
        repo.review().onSuccess { r ->
            review = r
            val d = r.details
            if (f.isEmpty()) {
                f["company"] = d["company_name"].orEmpty()
                f["firstname"] = d["firstname"].orEmpty().ifBlank { r.givenName }
                f["lastname"] = d["lastname"].orEmpty().ifBlank { r.familyName }
                f["pan_number"] = d["pan_number"].orEmpty().uppercase()
                f["gstin"] = d["gstin"].orEmpty()
                f["nationality"] = d["nationality"].orEmpty()
                f["address"] = d["address"].orEmpty()
                f["city"] = d["city"].orEmpty(); f["state"] = d["state"].orEmpty(); f["zip"] = d["pincode"].orEmpty()
                val mob = r.verified ?: d["mobile"].orEmpty().ifBlank { d["phonenumber"].orEmpty() }
                f["phonenumber"] = if (r.entityType == "nri") mob else mob.filter { it.isDigit() }.takeLast(10)
                val read = d["country_name"].orEmpty().lowercase()
                r.countries.firstOrNull { it.second.lowercase() == read }?.let { f["country"] = it.first.toString() }
            }
            phone = phone.copy(verified = r.verified)
            if (go) step = 2
        }.onFailure { error = it.message.orEmpty() }
    }
    // After a rotation the step and the typed fields are kept; the readings are asked for again.
    LaunchedEffect(step, standing != null) { if (step in 2..5 && review == null && standing?.allUploaded == true) toReview(go = false) }

    val reopened = standing?.reopened == true
    val type = review?.entityType ?: choice.orEmpty()
    val person = type == "individual" || type == "nri"
    val nri = type == "nri"
    val lk = review?.locked.orEmpty()
    // The mobile as the server keeps it (digits, country code first), from what is typed.
    fun digits(v: String) = if (nri) v.filter { it.isDigit() } else "91" + v.filter { it.isDigit() }.takeLast(10)
    val keepRecord = reopened && review?.recordVerified == true
    val confirmed = keepRecord || (phone.verified != null && phone.verified!!.filter { it.isDigit() } == digits(f["phonenumber"].orEmpty()))
    val anyProblem = standing?.documents.orEmpty().any { it.problem != null }

    /** What blocks the step from going on, or "". */
    fun problem(): String = when (step) {
        2 -> when {
            !person && "company" !in lk && f["company"].isNullOrBlank() -> "Registered Name is needed."
            "firstname" !in lk && f["firstname"].isNullOrBlank() -> "First Name is needed."
            else -> ""
        }
        3 -> when {
            f["address"].isNullOrBlank() -> "Address is needed."
            f["city"].isNullOrBlank() -> "City is needed."
            !nri && f["state"].isNullOrBlank() -> "State is needed."
            !nri && !f["zip"].orEmpty().matches(Regex("\\d{6}")) -> "Enter the six digits of the pincode."
            nri && f["country"].isNullOrBlank() -> "Choose the country you live in."
            else -> ""
        }
        4 -> if (confirmed) "" else "Please confirm your mobile number with the code the firm sends to it."
        5 -> if (f["terms"] != "1") (if (reopened) "Tick to confirm before sending." else "Tick to confirm before opening the account.") else ""
        else -> ""
    }

    fun submit() = scope.launch {
        busy = "submit"; error = ""
        // What identifies the registrant is the documents'; the server takes it from them.
        val fields = buildMap {
            listOf("firstname", "lastname").filter { it !in lk }.forEach { put(it, f[it].orEmpty()) }
            listOf("address", "city", "state", "zip", "terms").forEach { put(it, f[it].orEmpty()) }
            if (!keepRecord) put("phonenumber", f["phonenumber"].orEmpty())
            if (!person && "company" !in lk) put("company", f["company"].orEmpty())
            if (nri) { if ("nationality" !in lk) put("nationality", f["nationality"].orEmpty()); put("country", f["country"].orEmpty()) }
        }
        repo.submit(fields).onSuccess { step = 6 }.onFailure { e ->
            error = e.message.orEmpty()
            // To the step that fixes it.
            val field = (e as? OnbRefused)?.field.orEmpty()
            when {
                field == "phonenumber" || field == "phone_verify" -> { if (field == "phone_verify") phone = phone.copy(verified = null); step = 4 }
                field == "country" -> step = 3
                field.startsWith("document") || field == "pan_number" -> scope.launch { repo.standing().onSuccess { standing = it }; step = 1 }
            }
        }
        busy = ""
    }

    /** Next, per step: Type begins (or keeps) the draft, Files reads the details, the rest check theirs. */
    fun next() {
        when (step) {
            0 -> {
                val c = choice ?: return
                if (c == standing?.entityType) { step = 1; return }
                scope.launch {
                    busy = "begin"; error = ""
                    repo.begin(c).onSuccess { standing = it; review = null; f.clear(); step = 1 }.onFailure { error = it.message.orEmpty() }
                    busy = ""
                }
            }
            1 -> toReview()
            else -> { val p = problem(); if (p.isNotEmpty()) error = p else { error = ""; step += 1 } }
        }
    }

    if (step >= 6) {
        Sent(reopened, standing?.email.orEmpty(), onClose)
        return
    }

    WizardFrame(
        title = if (reopened) "Correct Your Registration" else "Become A Client",
        steps = Steps,
        step = step,
        ready = busy.isEmpty() && when (step) {
            0 -> choice != null && standing?.problem.isNullOrBlank()
            1 -> standing?.allUploaded == true && !anyProblem
            else -> true
        },
        saving = busy == "submit" || busy == "begin",
        savingLabel = if (busy == "begin") "Starting…" else if (reopened) "Sending…" else "Opening…",
        saveLabel = if (reopened) "Send Again" else "Open My Account",
        error = error,
        onClose = onClose,
        onStep = { i -> if (busy.isEmpty()) { error = ""; step = i } },
        onNext = if (step < 5) ({ next() }) else null,
        onSave = { val p = problem(); if (p.isNotEmpty()) error = p else submit() },
    ) {
        val s = standing
        when (step) {
            0 -> if (s?.reopened == true) {
                WizardHeading("What The Firm Asks", "The firm checked your registration and sent it back to be corrected.")
                LexCard {
                    Text(s.reviewNote.ifBlank { "Check your details and documents, and correct what is wrong." },
                        style = MaterialTheme.typography.bodyLarge, color = Lex.colors.foreground, modifier = Modifier.padding(16.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text("Your documents on file are kept; replace only what the firm asks for. Account type: ${s.types.firstOrNull { it.first == s.entityType }?.second ?: s.entityType.orEmpty()}.",
                    style = MaterialTheme.typography.bodyMedium, color = Lex.colors.mutedForeground)
            } else {
                WizardHeading("What kind of account?", "Your account with the firm is opened from your identity documents. The documents asked for depend on the kind.")
                if (s?.problem?.isNotBlank() == true) {
                    LexCard { Text(s.problem, style = MaterialTheme.typography.bodyMedium, color = Lex.colors.foreground, modifier = Modifier.padding(16.dp)) }
                } else s?.types.orEmpty().forEach { (k, label) ->
                    WizardChoice(label, on = choice == k, sub = TypeSubs[k].orEmpty()) { choice = k }
                }
            }
            1 -> {
                WizardHeading("Your documents", "Scan each one with the camera, or choose a PDF, JPG or PNG of up to 10 MB. Each is read as you send it.")
                s?.documents.orEmpty().forEach { d ->
                    DocCard(d, busy, busySince, reopened,
                        onScan = { target = d.type; scan() },
                        onPick = { target = d.type; picker.launch(arrayOf("application/pdf", "image/jpeg", "image/png")) },
                        onRemove = { confirmRemove = d })
                    Spacer(Modifier.height(12.dp))
                }
                if (notice.isNotBlank()) Text(notice, style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground)
                if (s != null && !s.reopened) StartOver(enabled = busy.isEmpty()) {
                    scope.launch {
                        repo.discard().onSuccess {
                            standing = standing?.copy(entityType = null, documents = emptyList(), allUploaded = false)
                            choice = null; review = null; f.clear(); notice = ""; error = ""; step = 0
                        }.onFailure { error = it.message.orEmpty() }
                    }
                }
            }
            2 -> {
                val r = review
                WizardHeading("Check your details", "Your client account is opened with these details. Correct anything that was read wrongly.")
                if (r != null && r.unread.isNotEmpty()) {
                    Text("Your ${r.unread.joinToString(" and ")} could not be read automatically, so some fields are empty. Type them as they appear on your documents; the firm checks them against the documents.",
                        style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground)
                    Spacer(Modifier.height(16.dp))
                }
                if (!person) {
                    if ("company" in lk) Locked("Registered Name", f["company"].orEmpty(), from(r, "company_name", "") + CLEARER)
                    else Field(f, "company", "Registered Name", "As on the registration certificate.")
                }
                if ("firstname" in lk) {
                    Locked("First Name", f["firstname"].orEmpty(), from(r, "name", "") + CLEARER)
                    Locked("Last Name", f["lastname"].orEmpty(), "As on your documents.")
                } else {
                    Field(f, "firstname", if (person) "First Name" else "Contact First Name", if (person) "As on your documents." else "The person who will use this account.")
                    Field(f, "lastname", if (person) "Last Name" else "Contact Last Name", if (person) "As on your documents." else "If you use one name only, please repeat it here.")
                }
                Locked("PAN", r?.panRead.orEmpty().ifBlank { f["pan_number"].orEmpty() }, from(r, "pan_number", "") + CLEARER)
                when {
                    type == "individual" || type == "proprietorship" -> r?.details?.get("aadhaar_number")?.takeIf { it.isNotBlank() }?.let {
                        Locked("Aadhaar", it, "Only the last four digits are shown.")
                    }
                    nri -> {
                        r?.details?.get("passport_number")?.takeIf { it.isNotBlank() }?.let { Locked("Passport", it, from(r, "passport_number", "")) }
                        if ("nationality" in lk) Locked("Nationality", f["nationality"].orEmpty(), from(r, "nationality", ""))
                        else Field(f, "nationality", "Nationality", "As on your passport, like Indian or British.")
                    }
                    else -> r?.details?.get("gstin")?.takeIf { it.isNotBlank() }?.let { Locked("GSTIN", it, from(r, "gstin", "")) }
                }
            }
            3 -> {
                WizardHeading(if (person) "Your address" else "Registered address", if (nri) "Where you live now, abroad." else "As on your documents.")
                Field(f, "address", if (person) "Address" else "Registered Address", from(review, "address", "As on your documents."), lines = 3)
                Field(f, "city", "City", "Town or city.")
                Field(f, "state", if (nri) "State or Province" else "State", if (nri) "If your country has them." else "State or union territory.")
                Field(f, "zip", if (nri) "Postal Code" else "Pincode", if (nri) "Postal or ZIP code." else "Six digits.", digits = !nri)
                if (nri) WizardDropdown("Country of Residence", review?.countries.orEmpty().map { it.first.toString() to it.second },
                    f["country"].orEmpty(), placeholder = "Choose your country") { f["country"] = it }
            }
            4 -> {
                WizardHeading("How we reach you", "The firm writes to your Google address, and sends updates on your matters to your mobile.")
                Locked("Email", standing?.email.orEmpty(), "From your Google sign-in. You sign in with it, and the firm writes to it.")
                if (keepRecord) Locked("Mobile Number", review?.details?.get("phonenumber").orEmpty(), "Your mobile on record, already confirmed. To change it, please write to the firm.")
                else MobileStep(f, phone, nri, review?.codes == true, confirmed,
                    onNumber = { phone = phone.copy(sent = false, error = "") },
                    send = { resend ->
                        phone = phone.copy(busy = true, error = "")
                        scope.launch {
                            repo.phone("request", mapOf("number" to if (nri) f["phonenumber"].orEmpty() else f["phonenumber"].orEmpty().filter { it.isDigit() }))
                                .onSuccess { d ->
                                    if (d["ok"] == true) phone = phone.copy(sent = true, to = d["to"].s(), resendIn = (d["resend_in"] as? Number)?.toInt() ?: 60,
                                        smsIn = (d["sms_in"] as? Number)?.toInt() ?: 30, busy = false, error = if (resend) "A new code is on its way." else "")
                                    else phone = phone.copy(busy = false, error = d["error"].s())
                                }.onFailure { phone = phone.copy(busy = false, error = it.message.orEmpty()) }
                        }
                    },
                    sms = {
                        scope.launch {
                            repo.phone("sms").onSuccess { phone = phone.copy(smsIn = Int.MAX_VALUE, error = "The code is on its way by text message.") }
                                .onFailure { phone = phone.copy(error = it.message.orEmpty()) }
                        }
                    },
                    verify = { code ->
                        phone = phone.copy(busy = true, error = "")
                        scope.launch {
                            repo.phone("verify", mapOf("code" to code)).onSuccess { d ->
                                phone = phone.copy(busy = false, sent = false, verified = (d["number"] as? String) ?: ("+" + digits(f["phonenumber"].orEmpty())))
                            }.onFailure { phone = phone.copy(busy = false, error = it.message.orEmpty()) }
                        }
                    },
                    change = { phone = phone.copy(sent = false, verified = null, error = "") },
                    tick = { phone = phone.copy(resendIn = (phone.resendIn - 1).coerceAtLeast(0), smsIn = if (phone.smsIn == Int.MAX_VALUE) phone.smsIn else (phone.smsIn - 1).coerceAtLeast(0)) },
                )
            }
            else -> {
                WizardHeading(if (reopened) "Check and send" else "Check and open",
                    if (reopened) "Check the details. Then send your corrected registration back to the firm."
                    else "Check the details. Then open your account: you sign in with Google, or with a code sent to your mobile.")
                WizardRows(listOfNotNull(
                    "Account Type" to (standing?.types?.firstOrNull { it.first == type }?.second ?: type),
                    ("Registered Name" to f["company"].orEmpty()).takeIf { !person },
                    "Name" to listOf(f["firstname"], f["lastname"]).filter { !it.isNullOrBlank() }.joinToString(" "),
                    "PAN" to (review?.panRead.orEmpty().ifBlank { f["pan_number"].orEmpty().uppercase() }),
                    "Address" to listOf(f["address"], f["city"], f["state"], f["zip"]).filter { !it.isNullOrBlank() }.joinToString(", "),
                    "Mobile" to (if (keepRecord) review?.details?.get("phonenumber").orEmpty() else (phone.verified ?: f["phonenumber"].orEmpty()) + " · Confirmed"),
                    "Email" to standing?.email.orEmpty(),
                ))
                Spacer(Modifier.height(16.dp))
                WizardTick(f["terms"] == "1", review?.consent.orEmpty().ifBlank { "I confirm these details are correct, that the documents are genuine and mine, and I agree to the firm and its service providers using them to verify my identity." }) {
                    f["terms"] = if (it) "1" else ""
                }
            }
        }
    }
    confirmRemove?.let { d ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Remove Your ${d.label}?") },
            text = { Text("You will then upload it again." + if (d.kept) " The one on file goes when your corrected registration is sent." else "") },
            confirmButton = { TextButton(onClick = {
                confirmRemove = null
                scope.launch { error = ""; repo.remove(d.type).onSuccess { standing = it; review = null }.onFailure { error = it.message.orEmpty() } }
            }) { Text("Remove", color = Lex.colors.danger.softText) } },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Keep It", color = Lex.colors.foreground) } },
            containerColor = Lex.colors.card,
        )
    }
}

/** One line under each kind of account, saying who it is for. */
private val TypeSubs = mapOf(
    "individual" to "You, living in India.",
    "nri" to "You, living abroad: your passport in place of Aadhaar.",
    "proprietorship" to "A business you own on your own.",
    "partnership" to "A firm with a partnership deed.",
    "llp" to "A limited liability partnership.",
    "pvt_ltd" to "A private limited company.",
    "pub_ltd" to "A public limited company.",
    "trust" to "A trust, society or NGO.",
    "company" to "Any other company.",
)

/** One document: what it is, then Scan / Choose, its reading, or what is on file. */
@Composable
private fun DocCard(d: OnbDoc, busy: String, busySince: Long, reopened: Boolean,
                    onScan: () -> Unit, onPick: () -> Unit, onRemove: () -> Unit) {
    val bad = d.problem != null
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Lex.colors.card)
            .border(1.dp, if (bad) Lex.colors.danger.solid else Lex.colors.border, RoundedCornerShape(12.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(d.label + if (d.required) "" else " (Optional)", style = MaterialTheme.typography.titleSmall, color = Lex.colors.foreground)
                if (d.description.isNotBlank() && d.description != d.label)
                    Text(d.description, style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground)
            }
            val tag = when {
                busy == d.type -> null
                bad -> "Needs Replacing"
                d.kept -> "On File"
                d.uploaded -> (if (reopened) "New · " else "") + (if (d.read == true) "Read" else "Not Read")
                else -> null
            }
            tag?.let {
                Text(it, style = MaterialTheme.typography.labelMedium,
                    color = if (bad) Lex.colors.danger.softText else if (d.uploaded && d.read == true || d.kept) Lex.colors.foreground else Lex.colors.mutedForeground,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Lex.colors.muted).padding(horizontal = 8.dp, vertical = 3.dp))
            }
        }
        when {
            busy == d.type -> Reading(busySince)
            d.kept || d.uploaded -> {
                if (d.kept) Text("On file from your registration.", style = MaterialTheme.typography.bodyMedium, color = Lex.colors.foreground)
                else if (!d.name.isNullOrBlank()) Text(d.name, style = MaterialTheme.typography.bodyMedium, color = Lex.colors.foreground)
                d.problem?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Lex.colors.danger.softText) }
                d.fields.forEach { (k, v) ->
                    Row { Text(k, Modifier.width(120.dp), style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground)
                        Text(v, style = MaterialTheme.typography.bodySmall, color = Lex.colors.foreground) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (d.kept || bad) {
                        DocButton("Scan Again", Icons.Outlined.DocumentScanner, busy.isEmpty(), onScan)
                        DocButton("Replace", Icons.Outlined.UploadFile, busy.isEmpty(), onPick)
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onRemove, enabled = busy.isEmpty()) { Text("Remove", color = Lex.colors.danger.softText) }
                }
            }
            else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DocButton("Scan", Icons.Outlined.DocumentScanner, busy.isEmpty(), onScan)
                DocButton("Choose File", Icons.Outlined.UploadFile, busy.isEmpty(), onPick)
            }
        }
    }
}

@Composable
private fun DocButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, Lex.colors.input, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) Lex.colors.foreground else Lex.colors.faint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) Lex.colors.foreground else Lex.colors.faint)
    }
}

/** After sending: the tick, what happens now, and the way home. */
@Composable
private fun Sent(reopened: Boolean, email: String, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    Column(Modifier.fillMaxSize().background(Lex.colors.background).padding(24.dp), verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(Lex.colors.primary), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Check, contentDescription = null, tint = Lex.colors.primaryForeground, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(24.dp))
        WizardHeading(if (reopened) "Sent again" else "With the firm for approval",
            if (reopened) "Your corrected registration is with the firm. The firm will write to $email when your account opens, and this app will then open as your client app."
            else "Your registration and documents have been received. The firm reviews every new account before it opens; it will write to $email when it does, and this app will then open as your client app. You sign in with Google, or with a code sent to your mobile.")
        FunnelPrimaryButton("Back To Home", modifier = Modifier.fillMaxWidth(), onClick = onClose)
    }
}

private const val CLEARER = " Wrong? Upload a clearer copy."

/**
 * The mobile, confirmed by a code before the registration can complete:
 * the number and Send Code, then the code (six digits, sent on its own when
 * complete), Send By SMS, Send A New Code with its countdown, Change Number.
 */
@Composable
private fun MobileStep(f: MutableMap<String, String>, p: OnbPhone, nri: Boolean, codes: Boolean, confirmed: Boolean,
                       onNumber: () -> Unit, send: (resend: Boolean) -> Unit, sms: () -> Unit, verify: (String) -> Unit,
                       change: () -> Unit, tick: () -> Unit) {
    if (confirmed) {
        Locked("Mobile Number", p.verified ?: f["phonenumber"].orEmpty(), "Confirmed. You can sign in with a code sent to it.")
        TextButton(onClick = { change() }, contentPadding = PaddingValues(0.dp)) { Text("Change Number", color = Lex.colors.foreground) }
        return
    }
    if (!p.sent) {
        WizardTextField("Mobile Number", f["phonenumber"].orEmpty(),
            { v -> f["phonenumber"] = if (nri) v.filter { it.isDigit() || it == '+' || it == ' ' } else v.filter { it.isDigit() }.take(10); onNumber() },
            help = if (nri) "The whole number with its country code, starting with +." else "Ten digits, starting with 6, 7, 8 or 9. The firm sends a code to confirm it.",
            enabled = !p.busy, prefix = if (!nri) "+91" else "",
            keyboardType = if (nri) KeyboardType.Phone else KeyboardType.Number)
        if (!codes) Text("Mobile numbers cannot be confirmed just now, so your registration cannot be completed yet. Everything you have given is saved; please try again a little later.",
            style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground)
        else FunnelPrimaryButton(if (p.busy) "Sending…" else "Send Code", modifier = Modifier.fillMaxWidth(),
            enabled = !p.busy && f["phonenumber"].orEmpty().isNotBlank(), onClick = { send(false) })
    } else {
        LaunchedEffect(p.to) { while (true) { kotlinx.coroutines.delay(1000); tick() } }
        var code by remember(p.to) { mutableStateOf("") }
        Text("A six-digit code was sent to ${p.to} by WhatsApp or text message. Please type it below.",
            style = MaterialTheme.typography.bodyMedium, color = Lex.colors.foreground)
        Spacer(Modifier.height(14.dp))
        WizardTextField("Code", code, { v -> code = v.filter { it.isDigit() }.take(6); if (code.length == 6 && !p.busy) verify(code) },
            enabled = !p.busy, placeholder = "Six digits", keyboardType = KeyboardType.NumberPassword)
        FunnelPrimaryButton(if (p.busy) "Confirming…" else "Confirm", modifier = Modifier.fillMaxWidth(),
            enabled = code.length == 6 && !p.busy, onClick = { verify(code) })
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (p.smsIn != Int.MAX_VALUE) TextButton(onClick = sms, enabled = p.smsIn == 0 && !p.busy) {
                Text(if (p.smsIn > 0) "Send By SMS In %d:%02d".format(p.smsIn / 60, p.smsIn % 60) else "Send By SMS",
                    color = if (p.smsIn == 0) Lex.colors.foreground else Lex.colors.faint)
            }
            TextButton(onClick = { send(true) }, enabled = p.resendIn == 0 && !p.busy) {
                Text(if (p.resendIn > 0) "Send A New Code In %d:%02d".format(p.resendIn / 60, p.resendIn % 60) else "Send A New Code",
                    color = if (p.resendIn == 0) Lex.colors.foreground else Lex.colors.faint)
            }
        }
        TextButton(onClick = change, contentPadding = PaddingValues(0.dp)) { Text("Change Number", color = Lex.colors.mutedForeground) }
    }
    if (p.error.isNotBlank()) Text(p.error, style = MaterialTheme.typography.bodySmall,
        color = if (p.error.startsWith("A new code") || p.error.startsWith("The code is on")) Lex.colors.mutedForeground else Lex.colors.danger.softText)
}

/** What was typed, kept across a rotation as key, value, key, value… */
private val FieldsSaver = listSaver<SnapshotStateMap<String, String>, String>(
    save = { m -> m.flatMap { listOf(it.key, it.value) } },
    restore = { l -> mutableStateMapOf<String, String>().apply { l.chunked(2).forEach { (k, v) -> put(k, v) } } },
)

/** A document being uploaded and read: the whole call (up to two minutes). */
@Composable
private fun Reading(since: Long) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(since) { while (true) { kotlinx.coroutines.delay(1000); now = System.currentTimeMillis() } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Lex.colors.mutedForeground)
        Spacer(Modifier.width(10.dp))
        Text(if (now - since < 20_000) "Reading…" else "Still reading your document…",
            style = MaterialTheme.typography.bodyMedium, color = Lex.colors.foreground)
    }
}

/** Set the registration aside; the documents uploaded for it are deleted. */
@Composable
private fun StartOver(enabled: Boolean, onConfirm: () -> Unit) {
    var ask by remember { mutableStateOf(false) }
    TextButton(onClick = { ask = true }, enabled = enabled, contentPadding = PaddingValues(0.dp)) {
        Text("Start Over", color = Lex.colors.foreground)
    }
    if (ask) AlertDialog(
        onDismissRequest = { ask = false },
        title = { Text("Start Over?") },
        text = { Text("The documents you uploaded are deleted, and you choose the kind of account again.") },
        confirmButton = { TextButton(onClick = { ask = false; onConfirm() }) { Text("Start Over", color = Lex.colors.danger.softText) } },
        dismissButton = { TextButton(onClick = { ask = false }) { Text("Cancel", color = Lex.colors.foreground) } },
        containerColor = Lex.colors.card,
    )
}

private fun from(r: OnbReview?, key: String, otherwise: String): String =
    r?.sources?.get(key)?.takeIf { it.isNotBlank() }?.let { "Read from your $it." } ?: otherwise

@Composable
private fun Field(f: MutableMap<String, String>, key: String, label: String, help: String, upper: Boolean = false,
                  lines: Int = 1, digits: Boolean = false, phone: Boolean = false) {
    WizardTextField(label, f[key].orEmpty(), { v -> f[key] = if (upper) v.uppercase() else v }, help = help, lines = lines,
        keyboardType = when { digits -> KeyboardType.Number; phone -> KeyboardType.Phone; else -> KeyboardType.Text },
        prefix = if (key == "phonenumber" && digits) "+91" else "")
}

@Composable
private fun Locked(label: String, value: String, help: String) = WizardTextField(label, value, help = help, locked = true)
