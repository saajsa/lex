package com.legumlex.client.feature.counsel

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.legumlex.client.data.api.ApiClient
import com.legumlex.client.data.auth.AuthRepository
import com.legumlex.client.data.auth.TokenStore
import com.legumlex.client.feature.lead.DocCard
import com.legumlex.client.feature.lead.Field
import com.legumlex.client.feature.lead.Locked
import com.legumlex.client.feature.lead.MobileStep
import com.legumlex.client.feature.lead.OnbPhone
import com.legumlex.client.feature.lead.s
import com.legumlex.client.feature.scanner.rememberDocumentScannerLauncher
import com.legumlex.client.ui.components.WizardDropdown
import com.legumlex.client.ui.components.WizardFrame
import com.legumlex.client.ui.components.WizardHeading
import com.legumlex.client.ui.components.WizardRows
import com.legumlex.client.ui.components.WizardTick
import com.legumlex.client.ui.theme.Lex
import kotlinx.coroutines.launch

private val Steps = listOf("Certificate", "PAN", "Review", "Mobile", "Confirm")
private const val CERT = "bar_council_id"
private const val PAN = "pan"

/** What was typed, kept across a rotation as key, value, key, value. */
private val FieldsSaver = listSaver<SnapshotStateMap<String, String>, String>(
    save = { m -> m.flatMap { listOf(it.key, it.value) } },
    restore = { l -> mutableStateMapOf<String, String>().apply { l.chunked(2).forEach { (k, v) -> put(k, v) } } },
)

/**
 * Counsel registration, the form (REQ-010): Certificate, PAN, Review, Mobile, Confirm, over lead/counsel*. The documents are read
 * as they upload and what they say stays as read; the server's words are shown as they come. Close puts the Google sign-in
 * away; the draft is kept on the server.
 */
@Composable
fun CounselWizardScreen(onClose: () -> Unit, onOpened: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { CounselRepository(context) }
    val auth = remember { AuthRepository(ApiClient.get(context), TokenStore(context.applicationContext), context.cacheDir) }

    var step by rememberSaveable { mutableIntStateOf(0) }
    var standing by remember { mutableStateOf<CounselStanding?>(null) }
    var review by remember { mutableStateOf<CounselReview?>(null) }
    var busy by remember { mutableStateOf("") }
    var busySince by remember { mutableLongStateOf(0L) }
    var error by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }
    val f = rememberSaveable(saver = FieldsSaver) { mutableStateMapOf<String, String>() }
    var terms by rememberSaveable { mutableStateOf(false) }
    var phone by remember { mutableStateOf(OnbPhone()) }
    var target by rememberSaveable { mutableStateOf(CERT) }
    // The picked file is kept, so a failed upload is retried without choosing it again.
    var progress by remember { mutableStateOf<Float?>(null) }
    var kept by remember { mutableStateOf<Pair<String, Uri>?>(null) }

    LaunchedEffect(Unit) {
        repo.triage().onSuccess { t ->
            standing = t.standing
            val docs = t.standing.onb.documents
            // A draft under way picks up where it stopped.
            if (step == 0) step = when {
                docs.firstOrNull { it.type == CERT }?.uploaded != true -> 0
                docs.firstOrNull { it.type == PAN }?.uploaded != true -> 1
                else -> 1
            }
            t.standing.verified?.let { phone = phone.copy(verified = it) }
        }.onFailure { error = it.message.orEmpty() }
    }

    fun doc(type: String) = standing?.onb?.documents.orEmpty().firstOrNull { it.type == type }

    fun upload(type: String, uri: Uri) = scope.launch {
        busy = type; busySince = System.currentTimeMillis(); error = ""; notice = ""; progress = 0f; kept = type to uri
        repo.upload(type, uri) { progress = it }.onSuccess { (s, n) -> standing = s; review = null; notice = n; kept = null }.onFailure { error = it.message.orEmpty() }
        progress = null; busy = ""
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) upload(target, uri) }
    val scan = rememberDocumentScannerLauncher { uri -> if (uri != null) upload(target, uri) }
    var removing by remember { mutableStateOf<String?>(null) }

    fun toReview() = scope.launch {
        busy = "review"; error = ""
        repo.review().onSuccess { r ->
            review = r
            if (f.isEmpty()) {
                f["full_name"] = r.fullName; f["enrollment_number"] = r.enrolment; f["state_bar_council"] = r.stateBar
                f["pan_number"] = r.pan; f["state"] = r.state
            }
            step = 2
        }.onFailure { error = it.message.orEmpty() }
        busy = ""
    }
    // After a rotation the typed fields are kept; the readings are asked for again.
    LaunchedEffect(step, standing != null) { if (step in 2..4 && review == null && standing?.onb?.allUploaded == true) {
        repo.review().onSuccess { review = it; if (f.isEmpty()) { f["full_name"] = it.fullName; f["enrollment_number"] = it.enrolment; f["state_bar_council"] = it.stateBar; f["pan_number"] = it.pan; f["state"] = it.state } }
    } }

    fun saveReview() = scope.launch {
        busy = "save"; error = ""
        repo.saveReview(mapOf(
            "full_name" to f["full_name"].orEmpty(), "enrollment_number" to f["enrollment_number"].orEmpty(),
            "state_bar_council" to f["state_bar_council"].orEmpty(), "pan_number" to f["pan_number"].orEmpty(), "state" to f["state"].orEmpty(),
        )).onSuccess { standing = it; step = 3 }.onFailure { error = it.message.orEmpty() }
        busy = ""
    }

    val mobile = f["phonenumber"].orEmpty().filter { it.isDigit() }.takeLast(10)
    fun digits() = "91$mobile"
    val confirmed = phone.verified != null && phone.verified!!.filter { it.isDigit() } == digits()

    fun problem(): String = when (step) {
        0 -> if (doc(CERT)?.uploaded != true || doc(CERT)?.problem != null) "Please add your Bar Council certificate or ID card." else ""
        1 -> if (doc(PAN)?.uploaded != true || doc(PAN)?.problem != null) "Please add your PAN card." else ""
        2 -> when {
            f["full_name"].isNullOrBlank() -> "Your full name is needed."
            f["enrollment_number"].isNullOrBlank() -> "Your Bar Council enrolment number is needed."
            f["state"].isNullOrBlank() -> "Choose the state you practise in."
            else -> ""
        }
        3 -> if (confirmed) "" else "Please confirm your mobile number with the code the firm sends to it."
        4 -> if (!terms) "Tick to confirm before opening your Counsel workspace." else ""
        else -> ""
    }

    fun submit() = scope.launch {
        busy = "submit"; error = ""
        repo.submit(mobile).onSuccess { o ->
            // The workspace is open: the lead's sign-in gives way to the account's.
            auth.switchedTo(o.token, o.clientId, o.contactId)
            onOpened()
        }.onFailure { e ->
            error = e.message.orEmpty()
            val field = (e as? CounselRefused)?.field.orEmpty()
            when {
                field == "phonenumber" || field == "phone_verify" || field == "phone_taken" -> { if (field == "phone_verify") phone = phone.copy(verified = null); step = 3 }
                field == "pan_number" || field == "pan_taken" -> step = 2
                field == "enrollment_number" || field == "bar_number" || field == "bar_number_taken" || field == "full_name" || field == "state" -> step = 2
            }
        }
        busy = ""
    }

    fun next() {
        when (step) {
            1 -> { val p = problem(); if (p.isNotEmpty()) error = p else toReview() }
            2 -> { val p = problem(); if (p.isNotEmpty()) error = p else saveReview() }
            4 -> { val p = problem(); if (p.isNotEmpty()) error = p else submit() }
            else -> { val p = problem(); if (p.isNotEmpty()) error = p else { error = ""; step += 1 } }
        }
    }

    WizardFrame(
        title = "Register As Counsel", steps = Steps, step = step,
        ready = busy.isEmpty() && (step != 3 || confirmed), saving = busy == "submit" || busy == "save" || busy == "review",
        saveLabel = "Open Counsel Workspace", savingLabel = if (busy == "submit") "Opening…" else "Checking…",
        error = error,
        onClose = { scope.launch { if (!CounselFlow.keepSession) auth.forget(); onClose() } },
        onStep = { i -> if (busy.isEmpty()) { error = ""; step = i } },
        onNext = if (step < Steps.lastIndex) ({ next() }) else null,
        onSave = { next() },
    ) {
        when (step) {
            0 -> {
                WizardHeading("Your Bar Council certificate or ID card", "Scan it with the camera, or choose a PDF, JPG or PNG of up to 10 MB. It is read as you send it.")
                doc(CERT)?.let { d -> DocCard(d, busy, busySince, false, onScan = { target = CERT; scan() }, onPick = { target = CERT; picker.launch(arrayOf("application/pdf", "image/jpeg", "image/png")) }, onRemove = { removing = CERT }, progress = progress) }
                if (notice.isNotBlank()) Text(notice, style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground)
                kept?.takeIf { busy.isEmpty() && error.isNotBlank() && it.first == (if (step == 0) CERT else PAN) }?.let { (t, u) -> androidx.compose.material3.TextButton({ upload(t, u) }) { Text("Try Again With The Same File", color = Lex.colors.foreground) } }
            }
            1 -> {
                WizardHeading("Your PAN card", "One account per advocate. It is read as you send it.")
                doc(PAN)?.let { d -> DocCard(d, busy, busySince, false, onScan = { target = PAN; scan() }, onPick = { target = PAN; picker.launch(arrayOf("application/pdf", "image/jpeg", "image/png")) }, onRemove = { removing = PAN }, progress = progress) }
                if (notice.isNotBlank()) Text(notice, style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground)
                kept?.takeIf { busy.isEmpty() && error.isNotBlank() && it.first == (if (step == 0) CERT else PAN) }?.let { (t, u) -> androidx.compose.material3.TextButton({ upload(t, u) }) { Text("Try Again With The Same File", color = Lex.colors.foreground) } }
            }
            2 -> {
                val r = review
                WizardHeading("Check your details", "What the documents say stays as read. Type what could not be read.")
                if (r != null && r.unread.isNotEmpty()) Text("Your ${r.unread.joinToString(" and ")} could not be read automatically. Type it as it appears on your document; the firm checks it against the document.",
                    style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground)
                Spacer(Modifier.height(12.dp))
                if (r?.nameLocked == true) Locked("Full Name", f["full_name"].orEmpty(), "As on your certificate.") else Field(f, "full_name", "Full Name", "As on your certificate.")
                if (r?.numberLocked == true) Locked("Enrolment Number", f["enrollment_number"].orEmpty(), "Read from your certificate.")
                else Field(f, "enrollment_number", "Enrolment Number", "Written like P&H/123/2015: state, number, year.", upper = true)
                Field(f, "state_bar_council", "State Bar Council", "The council that enrolled you.")
                if (r?.panLocked == true) Locked("PAN", f["pan_number"].orEmpty(), "Read from your PAN card.") else Field(f, "pan_number", "PAN", "Ten characters, like ABCDE1234F.", upper = true)
                WizardDropdown("State You Practise In", (r?.states.orEmpty()).map { it to it }, f["state"].orEmpty(), placeholder = "Choose your state") { f["state"] = it }
            }
            3 -> {
                WizardHeading("Confirm your mobile", "A code is sent to it. You can sign in with it afterwards.")
                MobileStep(f, phone, nri = false, codes = standing?.codes == true, confirmed = confirmed,
                    onNumber = { phone = phone.copy(sent = false, error = "") },
                    send = { resend ->
                        phone = phone.copy(busy = true, error = "")
                        scope.launch {
                            repo.phone("request", mapOf("number" to mobile)).onSuccess { d ->
                                if (d["ok"] == true) phone = phone.copy(sent = true, to = d["to"].s(), resendIn = (d["resend_in"] as? Number)?.toInt() ?: 60,
                                    smsIn = (d["sms_in"] as? Number)?.toInt() ?: 30, busy = false, error = if (resend) "A new code is on its way." else "")
                                else phone = phone.copy(busy = false, error = d["error"].s())
                            }.onFailure { phone = phone.copy(busy = false, error = it.message.orEmpty()) }
                        }
                    },
                    sms = { scope.launch { repo.phone("sms").onSuccess { phone = phone.copy(smsIn = Int.MAX_VALUE, error = "The code is on its way by text message.") }.onFailure { phone = phone.copy(error = it.message.orEmpty()) } } },
                    verify = { code ->
                        phone = phone.copy(busy = true, error = "")
                        scope.launch {
                            repo.phone("verify", mapOf("code" to code)).onSuccess { d ->
                                phone = phone.copy(busy = false, sent = false, verified = (d["number"] as? String) ?: ("+" + digits()))
                            }.onFailure { phone = phone.copy(busy = false, error = it.message.orEmpty()) }
                        }
                    },
                    change = { phone = phone.copy(sent = false, verified = null, error = "") },
                    tick = { phone = phone.copy(resendIn = (phone.resendIn - 1).coerceAtLeast(0), smsIn = if (phone.smsIn == Int.MAX_VALUE) phone.smsIn else (phone.smsIn - 1).coerceAtLeast(0)) },
                )
            }
            else -> {
                WizardHeading("Check and open", "Your Counsel workspace opens as soon as you send this. Trial credits come with it, once per enrolment number. The firm then verifies your enrolment.")
                WizardRows(listOfNotNull(
                    "Name" to f["full_name"].orEmpty(), "Enrolment Number" to f["enrollment_number"].orEmpty(),
                    f["state_bar_council"].orEmpty().takeIf { it.isNotBlank() }?.let { "State Bar Council" to it },
                    "PAN" to f["pan_number"].orEmpty(), "State" to f["state"].orEmpty(), "Mobile" to (phone.verified ?: "+" + digits()),
                ))
                Spacer(Modifier.height(14.dp))
                WizardTick(terms, "I confirm these details are correct and the documents are genuine and mine. I understand the firm verifies my enrolment.") { terms = it }
            }
        }
    }

    removing?.let { t ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove This Document?") },
            text = { Text("You will upload it again.") },
            confirmButton = { androidx.compose.material3.TextButton({ val x = t; removing = null; scope.launch { repo.remove(x).onSuccess { standing = it; review = null }.onFailure { error = it.message.orEmpty() } } }) { Text("Remove", color = Lex.colors.danger.softText) } },
            dismissButton = { androidx.compose.material3.TextButton({ removing = null }) { Text("Cancel", color = Lex.colors.foreground) } },
            containerColor = Lex.colors.card,
        )
    }
}
