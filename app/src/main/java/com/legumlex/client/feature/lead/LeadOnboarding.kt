package com.legumlex.client.feature.lead

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.legumlex.client.data.api.ApiClient
import com.legumlex.client.data.auth.SessionRole
import com.legumlex.client.data.auth.TokenStore
import com.legumlex.client.feature.healthchecks.funnel.FunnelPrimaryButton
import com.legumlex.client.feature.healthchecks.funnel.FunnelSecondaryButton
import com.legumlex.client.feature.scanner.rememberDocumentScannerLauncher
import com.legumlex.client.ui.components.Eyebrow
import com.legumlex.client.ui.components.LexCard
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
 * approves it.
 */

data class OnbDoc(val type: String, val label: String, val description: String, val required: Boolean,
                  val uploaded: Boolean, val name: String?, val read: Boolean?, val fields: List<Pair<String, String>>)

data class OnbStanding(val email: String, val problem: String, val types: List<Pair<String, String>>,
                       val entityType: String?, val documents: List<OnbDoc>, val allUploaded: Boolean)

data class OnbReview(val entityType: String, val email: String, val givenName: String, val familyName: String,
                     val details: Map<String, String>, val sources: Map<String, String>, val unread: List<String>,
                     val countries: List<Pair<Int, String>>)

private fun Any?.s(): String = (this as? String).orEmpty()

@Suppress("UNCHECKED_CAST")
private fun standingOf(d: Map<String, Any?>) = OnbStanding(
    email = d["email"].s(),
    problem = d["problem"].s(),
    types = (d["entity_types"] as? List<Map<String, Any?>>).orEmpty().map { it["value"].s() to it["label"].s() },
    entityType = (d["entity_type"] as? String)?.takeIf { it.isNotBlank() },
    documents = (d["documents"] as? List<Map<String, Any?>>).orEmpty().map { x ->
        OnbDoc(x["type"].s(), x["label"].s(), x["description"].s(), x["required"] == true, x["uploaded"] == true,
            x["name"] as? String, x["read"] as? Boolean,
            (x["fields"] as? List<Map<String, Any?>>).orEmpty().map { it["label"].s() to it["value"].s() })
    },
    allUploaded = d["all_uploaded"] == true,
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

    @Suppress("UNCHECKED_CAST")
    suspend fun review(): Result<OnbReview> = runCatching {
        val d = api.leadOnboardingReview()["data"] as Map<String, Any?>
        fun map(v: Any?) = (v as? Map<String, Any?>).orEmpty().mapValues { (_, x) -> x?.toString().orEmpty() }
        OnbReview(d["entity_type"].s(), d["email"].s(), d["given_name"].s(), d["family_name"].s(),
            map(d["details"]), map(d["sources"]), (d["unread"] as? List<*>).orEmpty().mapNotNull { it as? String },
            (d["countries"] as? List<Map<String, Any?>>).orEmpty().map { ((it["id"] as? Number)?.toInt() ?: 0) to it["name"].s() })
    }.recoverCatching { throw IllegalStateException(it.said("Your details could not be loaded.")) }

    suspend fun submit(fields: Map<String, String>): Result<Unit> = runCatching {
        api.leadOnboardingSubmit(fields)
        TokenStore(context.applicationContext).saveLeadStatus("awaiting_approval")
    }.recoverCatching { throw IllegalStateException(it.said("The registration could not be completed.")) }

    suspend fun discard() { runCatching { api.leadOnboardingDiscard() } }
}

private val Steps = listOf("Account Type", "Documents", "Details", "Address", "Contact", "Confirm")

@Composable
fun LeadOnboardingScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val repo = remember(context) { OnboardingRepository(context) }
    val scope = rememberCoroutineScope()

    var step by remember { mutableIntStateOf(0) }       // 0..5, 6 = sent
    var standing by remember { mutableStateOf<OnbStanding?>(null) }
    var review by remember { mutableStateOf<OnbReview?>(null) }
    var busy by remember { mutableStateOf("") }          // the document being read, or "submit"
    var error by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }
    var choice by remember { mutableStateOf<String?>(null) }
    val f = remember { mutableStateMapOf<String, String>() } // Review's fields

    LaunchedEffect(Unit) {
        repo.standing().onSuccess { s ->
            standing = s; choice = s.entityType
            if (s.entityType != null) step = 1
        }.onFailure { error = it.message.orEmpty() }
    }

    fun upload(type: String, uri: Uri) = scope.launch {
        busy = type; error = ""; notice = ""
        repo.upload(type, uri).onSuccess { (s, n) -> standing = s; notice = n }.onFailure { error = it.message.orEmpty() }
        busy = ""
    }
    var target by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) upload(target, uri) }
    val scan = rememberDocumentScannerLauncher { uri -> if (uri != null) upload(target, uri) }

    fun toReview() = scope.launch {
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
                val mob = d["mobile"].orEmpty()
                f["phonenumber"] = if (r.entityType == "nri") mob else mob.filter { it.isDigit() }.takeLast(10)
                val read = d["country_name"].orEmpty().lowercase()
                r.countries.firstOrNull { it.second.lowercase() == read }?.let { f["country"] = it.first.toString() }
            }
            step = 2
        }.onFailure { error = it.message.orEmpty() }
    }

    val type = review?.entityType ?: choice.orEmpty()
    val person = type == "individual" || type == "nri"
    val nri = type == "nri"
    val panLocked = review?.details?.get("pan_number").orEmpty().uppercase().matches(Regex("[A-Z]{5}[0-9]{4}[A-Z]"))

    /** What blocks the step from going on, or "". */
    fun problem(): String = when (step) {
        2 -> when {
            !person && f["company"].isNullOrBlank() -> "Registered Name is needed."
            f["firstname"].isNullOrBlank() -> "First Name is needed."
            !panLocked && !f["pan_number"].orEmpty().uppercase().matches(Regex("[A-Z]{5}[0-9]{4}[A-Z]")) -> "Enter the PAN: ten characters, like ABCDE1234F."
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
        4 -> if (nri) (if (!f["phonenumber"].orEmpty().matches(Regex("\\+?[0-9 ]{8,19}"))) "Enter your number with its country code, like +44 7700 900123." else "")
             else (if (!f["phonenumber"].orEmpty().matches(Regex("[0-9]{10}"))) "Enter the ten digits of your mobile number." else "")
        5 -> when {
            f["password"].orEmpty().length < 8 -> "The password needs at least eight characters."
            f["password"] != f["password_confirm"] -> "The two passwords do not match."
            f["terms"] != "1" -> "Tick to confirm before opening the account."
            else -> ""
        }
        else -> ""
    }

    fun submit() = scope.launch {
        busy = "submit"; error = ""
        val fields = buildMap {
            listOf("firstname", "lastname", "address", "city", "state", "zip", "phonenumber", "password", "password_confirm", "terms").forEach { put(it, f[it].orEmpty()) }
            if (!person) { put("company", f["company"].orEmpty()); put("gstin", f["gstin"].orEmpty().uppercase()) }
            if (!panLocked) put("pan_number", f["pan_number"].orEmpty().uppercase())
            if (nri) { put("nationality", f["nationality"].orEmpty()); put("country", f["country"].orEmpty()) }
        }
        repo.submit(fields).onSuccess { step = 6 }.onFailure { error = it.message.orEmpty() }
        busy = ""
    }

    BackHandler { if (step in 1..5 && busy.isEmpty()) { error = ""; step -= 1 } else onClose() }

    Column(Modifier.fillMaxSize().background(Lex.colors.background).imePadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (step < 6) Eyebrow("Step ${step + 1} of 6 · ${Steps[step]}")
            val s = standing
            when (step) {
                0 -> {
                    Title("Open An Account", "Your account with the firm is opened from your identity documents. The firm reviews every new account before it opens.")
                    if (s?.problem?.isNotBlank() == true) {
                        LexCard { Text(s.problem, style = MaterialTheme.typography.bodyMedium, color = Lex.colors.foreground, modifier = Modifier.padding(16.dp)) }
                    } else LexCard {
                        s?.types.orEmpty().forEachIndexed { i, (k, label) ->
                            if (i > 0) HorizontalDivider(color = Lex.colors.border)
                            Row(Modifier.fillMaxWidth().clickable { choice = k }.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = choice == k, onClick = { choice = k })
                                Text(label, style = MaterialTheme.typography.bodyLarge, color = Lex.colors.foreground)
                            }
                        }
                    }
                }
                1 -> {
                    Title("Your Documents", "Scan each one with the camera, or choose a PDF, JPG or PNG of up to 10 MB. Each is read as you send it.")
                    s?.documents.orEmpty().forEach { d ->
                        LexCard(title = d.label + if (d.required) "" else " (Optional)") {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (d.description.isNotBlank()) Text(d.description, style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground)
                                when {
                                    busy == d.type -> Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Lex.colors.mutedForeground)
                                        Spacer(Modifier.width(10.dp))
                                        Text("Reading…", style = MaterialTheme.typography.bodyMedium, color = Lex.colors.foreground)
                                    }
                                    d.uploaded -> {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(d.name.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = Lex.colors.foreground, modifier = Modifier.weight(1f))
                                            Text(if (d.read == true) "Read" else "Not Read", style = MaterialTheme.typography.labelMedium,
                                                color = if (d.read == true) Lex.colors.foreground else Lex.colors.mutedForeground)
                                        }
                                        d.fields.forEach { (k, v) ->
                                            Row { Text(k, Modifier.width(120.dp), style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground)
                                                Text(v, style = MaterialTheme.typography.bodySmall, color = Lex.colors.foreground) }
                                        }
                                        TextButton(onClick = { scope.launch { repo.remove(d.type).onSuccess { standing = it } } }, enabled = busy.isEmpty(),
                                            contentPadding = PaddingValues(0.dp)) { Text("Remove", color = Lex.colors.danger.softText) }
                                    }
                                    else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = { target = d.type; scan() }, enabled = busy.isEmpty()) { Text("Scan With Camera") }
                                        OutlinedButton(onClick = { target = d.type; picker.launch(arrayOf("application/pdf", "image/jpeg", "image/png")) }, enabled = busy.isEmpty()) { Text("Choose A File") }
                                    }
                                }
                            }
                        }
                    }
                    if (notice.isNotBlank()) Text(notice, style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground)
                }
                2 -> {
                    val r = review
                    Title("Check Your Details", "Your client account is opened with these details. Correct anything that was read wrongly.")
                    if (r != null && r.unread.isNotEmpty()) Text("Your ${r.unread.joinToString(" and ")} could not be read automatically, so some fields are empty. Type them as they appear on your documents; the firm checks them against the documents.",
                        style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground)
                    if (!person) Field(f, "company", "Registered Name", from(r, "company_name", "As on the registration certificate."))
                    Field(f, "firstname", if (person) "First Name" else "Contact First Name", if (person) from(r, "name", "From your Google account.") else "The person who will use this account.")
                    Field(f, "lastname", if (person) "Last Name" else "Contact Last Name", if (person) "As on your documents." else "Leave empty if there is none.")
                    if (panLocked) Locked("PAN", f["pan_number"].orEmpty(), from(r, "pan_number", ""))
                    else Field(f, "pan_number", "PAN", "Ten characters, like ABCDE1234F.", upper = true)
                    when {
                        type == "individual" -> Locked("Aadhaar", r?.details?.get("aadhaar_number")?.let { "XXXX XXXX " + it.filter(Char::isDigit).takeLast(4) } ?: "Not read",
                            if (r?.details?.get("aadhaar_number") != null) "Only the last four digits are shown." else "The firm reads it from your Aadhaar Card.")
                        nri -> {
                            Locked("Passport", r?.details?.get("passport_number")?.let { it.take(1) + "•".repeat((it.length - 3).coerceAtLeast(0)) + it.takeLast(2) } ?: "Not read",
                                if (r?.details?.get("passport_number") != null) from(r, "passport_number", "") else "The firm reads it from your passport.")
                            Field(f, "nationality", "Nationality", from(r, "nationality", "As on your passport, like Indian or British."))
                        }
                        else -> Field(f, "gstin", "GSTIN", from(r, "gstin", "Fifteen characters. Leave empty if not registered for GST."), upper = true)
                    }
                }
                3 -> {
                    Title(if (person) "Your Address" else "Registered Address", if (nri) "Where you live now, abroad." else "As on your documents.")
                    Field(f, "address", if (person) "Address" else "Registered Address", from(review, "address", "As on your documents."), lines = 3)
                    Field(f, "city", "City", "Town or city.")
                    Field(f, "state", if (nri) "State or Province" else "State", if (nri) "If your country has them." else "State or union territory.")
                    Field(f, "zip", if (nri) "Postal Code" else "Pincode", if (nri) "Postal or ZIP code." else "Six digits.", digits = !nri)
                    if (nri) CountryPicker(review?.countries.orEmpty(), f["country"]) { f["country"] = it }
                }
                4 -> {
                    Title("How We Reach You", "The firm writes to your Google address, and sends updates on your matters to your mobile.")
                    Locked("Email", standing?.email.orEmpty(), "From your Google sign-in. You sign in with it, and the firm writes to it.")
                    Field(f, "phonenumber", "Mobile Number", if (nri) "With the country code." else "Ten digits; +91 is added.", digits = !nri, phone = nri)
                }
                5 -> {
                    Title("Confirm", "Check the details. Then set a password for the client portal and open your account.")
                    LexCard {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOfNotNull(
                                "Account Type" to (standing?.types?.firstOrNull { it.first == type }?.second ?: type),
                                ("Registered Name" to f["company"].orEmpty()).takeIf { !person },
                                "Name" to listOf(f["firstname"], f["lastname"]).filter { !it.isNullOrBlank() }.joinToString(" "),
                                "PAN" to f["pan_number"].orEmpty().uppercase(),
                                "Address" to listOf(f["address"], f["city"], f["state"], f["zip"]).filter { !it.isNullOrBlank() }.joinToString(", "),
                                "Mobile" to (if (nri) f["phonenumber"].orEmpty() else "+91 " + f["phonenumber"].orEmpty()),
                                "Email" to standing?.email.orEmpty(),
                            ).forEach { (k, v) ->
                                Row { Text(k, Modifier.width(120.dp), style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground)
                                    Text(v, style = MaterialTheme.typography.bodySmall, color = Lex.colors.foreground) }
                            }
                        }
                    }
                    Field(f, "password", "Password", "At least eight characters.", secret = true)
                    Field(f, "password_confirm", "Confirm Password", "", secret = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = f["terms"] == "1", onCheckedChange = { f["terms"] = if (it) "1" else "" })
                        Text("I confirm these details are correct, the documents are genuine and mine or my organisation's, and I agree to the firm keeping them to verify my identity.",
                            style = MaterialTheme.typography.bodySmall, color = Lex.colors.foreground)
                    }
                }
                else -> {
                    Spacer(Modifier.height(24.dp))
                    Title("With The Firm For Approval", "Your registration and documents have been received. The firm reviews every new account before it opens; we will write to ${standing?.email.orEmpty()} when it does, and this app will then open as your client app.")
                }
            }
            if (error.isNotBlank()) Text(error, style = MaterialTheme.typography.bodySmall, color = Lex.colors.danger.softText)
        }
        HorizontalDivider(color = Lex.colors.border)
        Box(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            when (step) {
                0 -> FunnelPrimaryButton("Continue", modifier = Modifier.fillMaxWidth(),
                    enabled = choice != null && standing?.problem.isNullOrBlank() && busy.isEmpty(), onClick = {
                        val c = choice ?: return@FunnelPrimaryButton
                        if (c == standing?.entityType) { step = 1; return@FunnelPrimaryButton }
                        scope.launch {
                            busy = "begin"; error = ""
                            repo.begin(c).onSuccess { standing = it; review = null; f.clear(); step = 1 }.onFailure { error = it.message.orEmpty() }
                            busy = ""
                        }
                    })
                1 -> FunnelPrimaryButton("Go To Your Details", modifier = Modifier.fillMaxWidth(),
                    enabled = standing?.allUploaded == true && busy.isEmpty(), onClick = { toReview() })
                in 2..4 -> FunnelPrimaryButton("Continue", modifier = Modifier.fillMaxWidth(), onClick = {
                    val p = problem(); if (p.isNotEmpty()) error = p else { error = ""; step += 1 }
                })
                5 -> FunnelPrimaryButton(if (busy == "submit") "Opening…" else "Open My Account", modifier = Modifier.fillMaxWidth(),
                    enabled = busy.isEmpty(), onClick = { val p = problem(); if (p.isNotEmpty()) error = p else submit() })
                else -> FunnelSecondaryButton("Back To Home", modifier = Modifier.fillMaxWidth(), onClick = onClose)
            }
        }
    }
}

private fun from(r: OnbReview?, key: String, otherwise: String): String =
    r?.sources?.get(key)?.takeIf { it.isNotBlank() }?.let { "Read from your $it." } ?: otherwise

@Composable
private fun Title(title: String, sub: String) {
    Text(title, style = MaterialTheme.typography.headlineSmall, color = Lex.colors.foreground)
    Text(sub, style = MaterialTheme.typography.bodyMedium, color = Lex.colors.mutedForeground)
}

@Composable
private fun Field(f: MutableMap<String, String>, key: String, label: String, help: String, upper: Boolean = false,
                  lines: Int = 1, digits: Boolean = false, phone: Boolean = false, secret: Boolean = false) {
    OutlinedTextField(
        value = f[key].orEmpty(),
        onValueChange = { v -> f[key] = if (upper) v.uppercase() else v },
        label = { Text(label) },
        supportingText = if (help.isNotBlank()) ({ Text(help) }) else null,
        singleLine = lines == 1, minLines = lines,
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = when { secret -> KeyboardType.Password; digits -> KeyboardType.Number; phone -> KeyboardType.Phone; else -> KeyboardType.Text }),
        prefix = if (key == "phonenumber" && digits) ({ Text("+91 ") }) else null,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Locked(label: String, value: String, help: String) {
    OutlinedTextField(value = value, onValueChange = {}, readOnly = true, enabled = false, label = { Text(label) },
        supportingText = if (help.isNotBlank()) ({ Text(help) }) else null, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun CountryPicker(countries: List<Pair<Int, String>>, selected: String?, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val name = countries.firstOrNull { it.first.toString() == selected }?.second
    Box {
        OutlinedTextField(value = name ?: "", onValueChange = {}, readOnly = true, label = { Text("Country of Residence") },
            placeholder = { Text("Choose your country…") }, modifier = Modifier.fillMaxWidth(),
            trailingIcon = { TextButton(onClick = { open = true }) { Text("Choose") } })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            countries.forEach { (id, n) -> DropdownMenuItem(text = { Text(n) }, onClick = { onPick(id.toString()); open = false }) }
        }
    }
}
