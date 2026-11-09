package com.legumlex.client.feature.workspace

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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import com.legumlex.client.feature.counsel.CounselReview
import com.legumlex.client.feature.counsel.CounselStanding
import com.legumlex.client.feature.lead.DocCard
import com.legumlex.client.feature.lead.Field
import com.legumlex.client.feature.lead.Locked
import com.legumlex.client.feature.scanner.rememberDocumentScannerLauncher
import com.legumlex.client.ui.components.WizardDropdown
import com.legumlex.client.ui.components.WizardFrame
import com.legumlex.client.ui.components.WizardHeading
import com.legumlex.client.ui.components.WizardRows
import com.legumlex.client.ui.components.WizardTick
import com.legumlex.client.ui.theme.Lex
import kotlinx.coroutines.launch

private val Steps = listOf("Certificate", "Details", "Confirm")
private const val CERT = "bar_council_id"

private val FieldsSaver = listSaver<SnapshotStateMap<String, String>, String>(
    save = { m -> m.flatMap { listOf(it.key, it.value) } },
    restore = { l -> mutableStateMapOf<String, String>().apply { l.chunked(2).forEach { (k, v) -> put(k, v) } } },
)

/**
 * Add Counsel Workspace (REQ-010, canvas 10b), from Profile: the certificate, the details and the confirmation, over the
 * workspace endpoints. Identity, PAN and mobile are already on the account, so only the Bar Council certificate is asked.
 * The workspace opens as soon as it is sent; the server's words are shown as they come.
 */
@Composable
fun AddCounselScreen(onClose: () -> Unit, onAdded: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { WorkspaceRepository(context) }

    var step by rememberSaveable { mutableIntStateOf(0) }
    var standing by remember { mutableStateOf<CounselStanding?>(null) }
    var review by remember { mutableStateOf<CounselReview?>(null) }
    var busy by remember { mutableStateOf("") }
    var busySince by remember { mutableLongStateOf(0L) }
    var error by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf<Float?>(null) }
    var kept by remember { mutableStateOf<Pair<String, Uri>?>(null) }
    var starting by remember { mutableStateOf(true) }
    val f = rememberSaveable(saver = FieldsSaver) { mutableStateMapOf<String, String>() }
    var terms by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // A draft already held is picked up; otherwise one is started.
        val asked = repo.state()
        val held = asked.getOrNull()?.draft
        if (held != null) standing = held
        else if (asked.isFailure) error = asked.exceptionOrNull()?.message.orEmpty()
        else repo.begin().onSuccess { standing = it }.onFailure { error = it.message.orEmpty() }
        starting = false
    }

    fun doc() = standing?.onb?.documents.orEmpty().firstOrNull { it.type == CERT } ?: standing?.onb?.documents.orEmpty().firstOrNull()
    fun fill(r: CounselReview) {
        review = r
        if (f.isEmpty()) { f["full_name"] = r.fullName; f["enrollment_number"] = r.enrolment; f["state_bar_council"] = r.stateBar; f["state"] = r.state }
    }

    fun upload(t: String, uri: Uri) = scope.launch {
        busy = t; busySince = System.currentTimeMillis(); error = ""; notice = ""; progress = 0f; kept = t to uri
        repo.upload(t, uri) { progress = it }.onSuccess { (s, n) -> standing = s; review = null; notice = n; kept = null }.onFailure { error = it.message.orEmpty() }
        progress = null; busy = ""
    }
    fun upload(uri: Uri) = upload(doc()?.type ?: CERT, uri)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) upload(uri) }
    val scan = rememberDocumentScannerLauncher { uri -> if (uri != null) upload(uri) }

    LaunchedEffect(step, standing?.onb?.allUploaded) {
        if (step >= 1 && review == null && standing?.onb?.allUploaded == true) repo.review().onSuccess { fill(it) }
    }

    fun problem(): String = when (step) {
        0 -> if (doc()?.uploaded != true || doc()?.problem != null) "Please add your Bar Council certificate or ID card." else ""
        1 -> when {
            f["enrollment_number"].isNullOrBlank() -> "Your Bar Council enrolment number is needed."
            f["state"].isNullOrBlank() -> "Choose the state you practise in."
            else -> ""
        }
        2 -> if (!terms) "Tick to confirm before opening your Counsel workspace." else ""
        else -> ""
    }

    fun go() {
        val p = problem()
        if (p.isNotEmpty()) { error = p; return }
        error = ""
        scope.launch {
            when (step) {
                0 -> { busy = "review"; repo.review().onSuccess { fill(it); step = 1 }.onFailure { error = it.message.orEmpty() }; busy = "" }
                1 -> {
                    busy = "save"
                    repo.saveReview(mapOf(
                        "full_name" to f["full_name"].orEmpty(), "enrollment_number" to f["enrollment_number"].orEmpty(),
                        "state_bar_council" to f["state_bar_council"].orEmpty(), "state" to f["state"].orEmpty(),
                    )).onSuccess { step = 2 }.onFailure { error = it.message.orEmpty() }
                    busy = ""
                }
                else -> {
                    busy = "submit"
                    repo.submit().onSuccess { (_, msg) -> onAdded(msg.ifBlank { "Your Counsel workspace is open." }) }
                        .onFailure { e ->
                            error = e.message.orEmpty()
                            if ((e as? com.legumlex.client.feature.counsel.CounselRefused)?.field.orEmpty().let { it == "enrollment_number" || it == "state" || it == "full_name" }) step = 1
                        }
                    busy = ""
                }
            }
        }
    }

    WizardFrame(
        title = "Add Counsel Workspace", steps = Steps, step = step,
        ready = busy.isEmpty() && !starting, saving = busy == "submit" || busy == "save" || busy == "review",
        saveLabel = "Open Counsel Workspace", savingLabel = if (busy == "submit") "Opening…" else "Checking…",
        error = error,
        onClose = { scope.launch { if (busy.isEmpty()) repo.discard(); onClose() } },
        onStep = { i -> if (busy.isEmpty() && i <= step) { error = ""; step = i } },
        onNext = if (step < Steps.lastIndex) ({ go() }) else null,
        onSave = { go() },
    ) {
        when (step) {
            0 -> {
                WizardHeading("Your Bar Council certificate or ID card", "Your identity, PAN and mobile are already verified. Scan the certificate, or choose a PDF, JPG or PNG of up to 10 MB. It is read as you send it.")
                if (doc() == null && !starting) androidx.compose.material3.TextButton({
                    error = ""; starting = true
                    scope.launch { repo.begin().onSuccess { standing = it }.onFailure { error = it.message.orEmpty() }; starting = false }
                }) { Text("Try Again", color = Lex.colors.foreground) }
                doc()?.let { d ->
                    DocCard(d, busy, busySince, false,
                        onScan = { scan() }, onPick = { picker.launch(arrayOf("application/pdf", "image/jpeg", "image/png")) }, onRemove = {}, canRemove = false, progress = progress)
                }
                if (notice.isNotBlank()) Text(notice, style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground)
                kept?.takeIf { busy.isEmpty() && error.isNotBlank() }?.let { (t, u) -> androidx.compose.material3.TextButton({ upload(t, u) }) { Text("Try Again With The Same File", color = Lex.colors.foreground) } }
            }
            1 -> {
                val r = review
                WizardHeading("Check your details", "What the certificate says stays as read. Type what could not be read.")
                if (r != null && r.unread.isNotEmpty()) Text("Your ${r.unread.joinToString(" and ")} could not be read automatically. Type it as it appears on your certificate; the firm checks it against the document.",
                    style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground)
                Spacer(Modifier.height(12.dp))
                Locked("Name", f["full_name"].orEmpty(), "As on your account.")
                if (r?.numberLocked == true) Locked("Enrolment Number", f["enrollment_number"].orEmpty(), "Read from your certificate.")
                else Field(f, "enrollment_number", "Enrolment Number", "Written like P&H/123/2015: state, number, year.", upper = true)
                Field(f, "state_bar_council", "State Bar Council", "The council that enrolled you.")
                WizardDropdown("State You Practise In", (r?.states.orEmpty()).map { it to it }, f["state"].orEmpty(), placeholder = "Choose your state") { f["state"] = it }
            }
            else -> {
                WizardHeading("Check and open", "Your Counsel workspace opens as soon as you send this. Trial credits come with it, once per enrolment number. The firm then verifies your enrolment.")
                WizardRows(listOfNotNull(
                    "Name" to f["full_name"].orEmpty(), "Enrolment Number" to f["enrollment_number"].orEmpty(),
                    f["state_bar_council"].orEmpty().takeIf { it.isNotBlank() }?.let { "State Bar Council" to it },
                    "State" to f["state"].orEmpty(),
                ))
                Spacer(Modifier.height(14.dp))
                WizardTick(terms, "I confirm these details are correct and the certificate is genuine and mine. I understand the firm verifies my enrolment.") { terms = it }
            }
        }
    }
}
