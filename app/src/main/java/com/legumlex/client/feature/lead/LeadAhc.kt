package com.legumlex.client.feature.lead

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.legumlex.client.BuildConfig
import com.legumlex.client.data.api.ApiClient
import com.legumlex.client.feature.healthchecks.funnel.DropTile
import com.legumlex.client.feature.healthchecks.funnel.humanSize
import com.legumlex.client.ui.components.WizardFrame
import com.legumlex.client.ui.components.WizardHeading
import com.legumlex.client.ui.components.WizardRows
import com.legumlex.client.ui.components.WizardTick
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
 * The Agreement Check for a lead (owner, 2026-10-20): the website's own
 * (lexon/api/funnel/details, start, consent, intake), as a visitor signed in
 * with Google meets it, in three steps: what it is and its fee, the
 * agreement, and where it went. A priced check is paid on the website
 * (cases/landing/lead_pay), which asks once for what an invoice needs; the
 * report comes to My Enquiries and by email.
 */

private class Picked(val name: String, val size: Long, val mime: String, val bytes: ByteArray)

private sealed interface Outcome {
    data object Reading : Outcome
    data class Pay(val hash: String) : Outcome
    data class Unusable(val message: String) : Outcome
}

@Composable
fun LeadAhcScreen(onClose: () -> Unit, onEnquiries: () -> Unit) {
    val context = LocalContext.current
    val api = remember(context) { ApiClient.get(context) }
    val site = remember { BuildConfig.API_BASE_URL.substringBefore("customers_api/") }
    val scope = rememberCoroutineScope()

    var step by remember { mutableIntStateOf(1) }
    var price by remember { mutableStateOf<String?>(null) }   // "FREE" or "₹…"
    var types by remember { mutableStateOf(listOf("pdf", "doc", "docx")) }
    var maxSize by remember { mutableLongStateOf(10_485_760L) }
    var agreed by remember { mutableStateOf(false) }
    var file by remember { mutableStateOf<Picked?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var outcome by remember { mutableStateOf<Outcome?>(null) }

    LaunchedEffect(Unit) {
        runCatching {
            val f = api.funnelDetails(site + "lexon/api/funnel/details/ahc")["funnel"] as? Map<*, *>
            price = f?.get("display_price") as? String
            ((f?.get("config") as? Map<*, *>)?.get("document_types") as? List<*>)?.mapNotNull { it as? String }?.let { types = it }
            ((f?.get("config") as? Map<*, *>)?.get("max_file_size") as? Number)?.let { maxSize = it.toLong() }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            error = ""
            val p = withContext(Dispatchers.IO) {
                runCatching {
                    val cr = context.contentResolver
                    val (name, size) = cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                        if (c.moveToFirst()) (c.getString(0) ?: "agreement") to c.getLong(1) else "agreement" to 0L
                    } ?: ("agreement" to 0L)
                    if (size > maxSize) null
                    else Picked(name, size, cr.getType(uri) ?: "application/octet-stream", cr.openInputStream(uri)!!.use { it.readBytes() })
                }.getOrNull()
            }
            if (p == null) error = "That file could not be used. It must be a ${types.joinToString(", ") { it.uppercase() }} of up to ${humanSize(maxSize)}."
            else if (types.none { p.name.lowercase().endsWith(".$it") }) error = "Send the agreement as a ${types.joinToString(", ") { it.uppercase() }} file."
            else file = p
        }
    }

    fun send() = scope.launch {
        val f = file ?: return@launch
        busy = true; error = ""
        try {
            val st = api.funnelStart(site + "lexon/api/funnel/start/ahc")
            val hash = (st["session_hash"] as? String).orEmpty()
            if (st["success"] != true || hash.isEmpty()) error((st["error"] as? String) ?: "The check could not be started.")
            api.funnelConsent(site + "lexon/api/funnel/consent/$hash")
            val parts = listOf(
                MultipartBody.Part.createFormData("document_type", "agreement"),
                MultipartBody.Part.createFormData("file", f.name, f.bytes.toRequestBody(f.mime.toMediaTypeOrNull())),
            )
            val r = api.funnelIntake(site + "lexon/api/funnel/intake/$hash", parts)
            outcome = when {
                r["step"] == "unusable" -> Outcome.Unusable((r["error"] as? String).orEmpty().ifBlank { "This document could not be read." })
                r["success"] != true -> error((r["error"] as? String) ?: "The agreement could not be sent.")
                r["require_payment"] == true -> Outcome.Pay(hash)
                else -> Outcome.Reading
            }
            step = 3
        } catch (e: HttpException) {
            error = runCatching { JSONObject(e.response()?.errorBody()?.string().orEmpty()).optString("error") }.getOrNull()
                .orEmpty().ifBlank { "The agreement could not be sent. Please try again." }
        } catch (e: Exception) {
            error = e.message?.takeIf { it.isNotBlank() } ?: "The agreement could not be sent. Please try again."
        }
        busy = false
    }

    // The app's wizard (owner, 2026-10-24). Sent is the last step and cannot be
    // stepped back from: Back there leaves, as close does.
    val o = outcome
    WizardFrame(
        title = "Agreement Check",
        steps = listOf("About", "Your Agreement", "Sent"),
        step = step - 1,
        ready = when (step) {
            1 -> agreed && price != null
            2 -> file != null
            else -> true
        },
        saving = busy,
        savingLabel = "Sending…",
        nextLabel = if (step == 2) "Send The Agreement" else "Next",
        saveLabel = when (o) {
            is Outcome.Pay -> "Pay On The Website"
            is Outcome.Unusable -> "Try Another File"
            else -> "Go To My Enquiries"
        },
        error = error.ifBlank { null },
        onClose = onClose,
        onStep = { i -> if (busy) Unit else if (step == 3) onClose() else step = i + 1 },
        onNext = if (step == 2) ({ send(); Unit }) else null,
        onSave = {
            when (o) {
                is Outcome.Pay -> {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(site + "cases/landing/lead_pay/" + o.hash)))
                    onEnquiries()
                }
                is Outcome.Unusable -> { file = null; outcome = null; step = 2 }
                else -> onEnquiries()
            }
        },
    ) {
        when (step) {
            1 -> {
                WizardHeading("Agreement Check",
                    "Send an agreement before you sign it. It is read for the terms that matter and the risks in it, and an advocate of the firm reviews the reading before it comes to you.")
                WizardRows(listOf(
                    "Fee" to when (price) { null -> "…"; "FREE" -> "Free"; else -> price!! },
                    "Files" to types.joinToString(", ") { it.uppercase() } + ", up to " + humanSize(maxSize),
                    "Report" to "In My Enquiries, and by email",
                ))
                Spacer(Modifier.height(16.dp))
                WizardTick(agreed, "I agree to the firm reading this agreement and keeping it with my enquiry.") { agreed = it }
            }
            2 -> {
                WizardHeading("Your Agreement", "One file: the agreement as you have it. A scan is read as well as a typed document.")
                val f = file
                if (f == null) DropTile(onClick = { picker.launch(arrayOf("*/*")) },
                    hint = types.joinToString(", ") { it.uppercase() } + " — up to " + humanSize(maxSize))
                else LexCard {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(f.name, style = MaterialTheme.typography.titleSmall, color = Lex.colors.foreground)
                            Text(humanSize(f.size), style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground)
                        }
                        TextButton(onClick = { file = null }, enabled = !busy) { Text("Change", color = Lex.colors.foreground) }
                    }
                }
            }
            else -> when (o) {
                is Outcome.Pay -> WizardHeading("Pay The Fee",
                    "Your agreement is with us. It is read once the fee is paid, on the firm's website: the first time, it asks the name and State to put on the invoice.")
                is Outcome.Unusable -> WizardHeading("This Document Could Not Be Read", o.message)
                else -> WizardHeading("Your Agreement Is Being Read",
                    "An advocate reviews the reading before it comes to you. It will be in My Enquiries, and we will write to you when it is ready.")
            }
        }
    }
}
