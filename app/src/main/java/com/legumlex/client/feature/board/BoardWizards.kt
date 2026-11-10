package com.legumlex.client.feature.board

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.legumlex.client.data.api.uriToFilePart
import com.legumlex.client.ui.components.LegumSecondaryButton
import com.legumlex.client.ui.components.WizardChoice
import com.legumlex.client.ui.components.WizardDialog
import com.legumlex.client.ui.components.WizardDropdown
import com.legumlex.client.ui.components.WizardFrame
import com.legumlex.client.ui.components.WizardHeading
import com.legumlex.client.ui.components.WizardLabel
import com.legumlex.client.ui.components.WizardRows
import com.legumlex.client.ui.components.WizardTextArea
import com.legumlex.client.ui.components.WizardTextField
import com.legumlex.client.ui.components.WizardTick
import com.legumlex.client.ui.theme.BorderStrong
import com.legumlex.client.ui.theme.Lex
import com.legumlex.client.ui.theme.PrimaryText
import com.legumlex.client.ui.theme.SecondaryText
import com.legumlex.client.ui.theme.Surface1
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.launch

// Every board form is a wizard (owner rule), opened over its screen as the
// app's other wizards are (WizardDialog + WizardFrame). Each says what the
// server said when it refuses.

/** A date field: shows the date, opens the calendar; "Clear" empties it when allowed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, value: LocalDate?, onChange: (LocalDate?) -> Unit, help: String = "", clearable: Boolean = true) {
    var open by remember { mutableStateOf(false) }
    WizardLabel(label)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Surface1).border(1.dp, BorderStrong, RoundedCornerShape(8.dp))
            .clickable { open = true }.padding(horizontal = 16.dp, vertical = 15.dp),
    ) {
        Text(value?.let { BoardDates.long(it) } ?: "Choose A Date", color = if (value != null) PrimaryText else SecondaryText, modifier = Modifier.weight(1f))
        if (clearable && value != null) {
            Text("Clear", color = SecondaryText, modifier = Modifier.clickable { onChange(null) })
        }
    }
    if (help.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text(help, color = SecondaryText, style = MaterialTheme.typography.bodySmall) }
    Spacer(Modifier.height(16.dp))
    if (open) {
        val st = rememberDatePickerState(initialSelectedDateMillis = (value ?: BoardDates.today()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text("Choose") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) { DatePicker(state = st) }
    }
}

private fun LocalDate?.api(): String = this?.let { BoardDates.api(it) }.orEmpty()

// ---- import -------------------------------------------------------------------

@Composable
fun ImportWizard(onClose: () -> Unit, onDone: (ImportDone) -> Unit) {
    val context = LocalContext.current
    val repo = remember { BoardRepository(context) }
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableStateOf(0) }
    var fileName by rememberSaveable { mutableStateOf("") }
    var uri by remember { mutableStateOf<android.net.Uri?>(null) }
    var preview by remember { mutableStateOf<ImportPreview?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) {
            uri = u
            fileName = context.contentResolver.query(u, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "Chosen file"
            error = null
        }
    }

    WizardDialog(onDismiss = onClose) {
        WizardFrame(
            title = "Import Cases", steps = listOf("File", "Preview"), step = step,
            ready = if (step == 0) uri != null else (preview?.let { it.new + it.existing > 0 } == true),
            saving = busy, saveLabel = "Import Cases", savingLabel = if (step == 0) "Reading…" else "Importing…",
            nextLabel = "Upload And Preview", error = error, onClose = onClose, onStep = { error = null; step = it },
            onNext = if (step == 0) {
                {
                    val u = uri
                    if (u != null) {
                        busy = true; error = null
                        scope.launch {
                            val part = uriToFilePart(context, u)
                            if (part == null) error = "That file could not be read. Please choose it again."
                            else repo.importPreview(part)
                                .onSuccess { preview = it; step = 1 }
                                .onFailure { error = if (it.isOffline()) "The file is uploaded to be read, so this needs a connection. Please try again online." else it.boardMessage() }
                            busy = false
                        }
                    }
                }
            } else null,
            onSave = {
                val p = preview
                if (p != null) {
                    busy = true; error = null
                    scope.launch {
                        repo.importConfirm(p.importId).onSuccess(onDone).onFailure { error = it.boardMessage() }
                        busy = false
                    }
                }
            },
        ) {
            if (step == 0) {
                WizardHeading("Bring Your Cases", "Choose the myCases.txt file saved from the eCourts Services app, or a spreadsheet saved as CSV. Nothing is added until you confirm.")
                LegumSecondaryButton(if (uri == null) "Choose A File" else "Choose Another File", { pick.launch(arrayOf("text/plain", "application/json", "text/csv", "text/comma-separated-values", "application/csv", "application/octet-stream", "text/*")) })
                if (fileName.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    WizardRows(listOf("File" to fileName))
                }
            } else preview?.let { p ->
                WizardHeading("What Was Found", "Check this before the cases are added to your diary.")
                WizardRows(
                    listOf(
                        "Found In The File" to p.found.toString(), "New, Will Be Added" to p.new.toString(), "Already In Your Diary" to p.existing.toString(),
                        "Repeated In The File" to p.duplicates.toString(), "Could Not Be Read" to p.errorCount.toString(),
                    ),
                )
                // What the check right after confirming costs, in the server's own sentence.
                p.cost?.line?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(14.dp))
                    Text(it, color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                }
                if (p.errors.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp)); WizardLabel("Problems")
                    p.errors.take(5).forEach { Text("Row ${it.row}: ${it.message}", color = SecondaryText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 4.dp)) }
                }
                if (p.sample.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp)); WizardLabel("A Few Of Them")
                    p.sample.take(5).forEach {
                        Text(
                            listOf(it.caseNo.ifBlank { it.cnr }, it.court, BoardDates.show(it.nextDate, BoardDates.today())).filter { s -> s.isNotBlank() }.joinToString("  ·  ") + if (it.isNew) "" else "  (already in your diary)",
                            color = PrimaryText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

// ---- add a case by CNR: find it, see it, add it (as the web does) ------------------

/** The Review step's facts, in the web's order and words; a blank value is left out. */
internal fun cnrFacts(c: CnrPreview, today: LocalDate): List<Pair<String, String>> = listOf(
    "Case Number" to c.caseNo,
    "Court" to (c.court + if (c.bench.isNotBlank()) " · ${c.bench}" else ""),
    "Next Date" to (BoardDates.parse(c.nextDate)?.let { BoardDates.long(it) } ?: "Not given"),
    "Stage" to c.stage,
).filter { it.second.isNotBlank() }

/** The sentence under the found case, in the web's words. */
internal fun cnrAddLine(charged: Int, checkCost: Int?) =
    "Finding this case on eCourts used ${creditsLabel(charged)}. Adding it to your diary is free" +
        (checkCost?.let { "; from tomorrow it is checked every morning for ${creditsLabel(it)}." } ?: ".")

@Composable
fun CnrWizard(onClose: () -> Unit, onBuy: () -> Unit, onAdded: (BoardCase, Int) -> Unit) {
    val context = LocalContext.current
    val repo = remember { BoardRepository(context) }
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableStateOf(0) }
    var cnr by rememberSaveable { mutableStateOf("") }
    var cost by remember { mutableStateOf<Int?>(null) }
    var checkCost by remember { mutableStateOf<Int?>(null) }
    var lookup by remember { mutableStateOf<CnrLookup?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf<String?>(null) }
    // A plain note (not an error): what Not This Case did.
    var note by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { repo.credits().onSuccess { cost = it.costAdd; checkCost = it.costCheck } }
    // The review step needs the lookup; without it (the screen came back after the process died) the CNR step is shown.
    val found = lookup
    val at = if (step == 1 && found == null) 0 else step

    WizardDialog(onDismiss = onClose) {
        WizardFrame(
            title = "Add By CNR", steps = listOf("Find", "Add"), step = at, ready = at == 1 || cnr.length == 16,
            saving = saving, savingLabel = if (at == 0) "Reading The Court Record…" else "Adding…",
            saveLabel = "Add To My Diary", nextLabel = "Find This Case" + (cost?.let { " (${creditsTitle(it)})" } ?: ""),
            error = error, onClose = onClose, onStep = { error = null; code = null; step = it },
            onNext = if (at == 0) {
                {
                    saving = true; error = null; code = null; note = null
                    scope.launch {
                        repo.cnrLookup(cnr.trim())
                            .onSuccess { lookup = it; BoardHub.applyBalance(it.balance); step = 1 }
                            .onFailure { error = it.boardMessage(); code = it.boardCode() }
                        saving = false
                    }
                }
            } else null,
            onSave = {
                val l = lookup
                if (l != null) {
                    saving = true; error = null; code = null
                    scope.launch {
                        repo.cnrConfirm(l.lookupId)
                            .onSuccess { onAdded(it, l.balance) }
                            .onFailure {
                                error = it.boardMessage(); code = it.boardCode()
                                // An expired lookup (2 hours) starts again at the CNR; the server's words are kept on screen.
                                if (code == "lookup_expired") { lookup = null; step = 0 }
                            }
                        saving = false
                    }
                }
            },
        ) {
            if (at == 0) {
                WizardHeading("Find The Case", "The 16 characters on the case's page on eCourts.")
                WizardTextField("CNR", cnr, { cnr = it.uppercase().filter { ch -> !ch.isWhitespace() }.take(16) }, placeholder = "PBLD010012342026", trailing = { CnrScanButton { cnr = it } })
                note?.let { Text(it, color = PrimaryText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp)) }
                Text(
                    "Finding this case on eCourts uses ${cost?.let { creditsLabel(it) } ?: "credits"}. Adding it to your diary is free.",
                    color = SecondaryText, style = MaterialTheme.typography.bodySmall,
                )
                if (code == "insufficient_credits") {
                    Spacer(Modifier.height(14.dp))
                    LegumSecondaryButton("Buy Credits", onBuy)
                }
            } else found?.let { l ->
                WizardHeading(l.case.title.ifBlank { l.case.cnr }, "CNR ${l.case.cnr}" + if (l.case.caseType.isNotBlank()) " · ${l.case.caseType}" else "")
                WizardRows(cnrFacts(l.case, BoardDates.today()))
                Spacer(Modifier.height(14.dp))
                Text(cnrAddLine(l.charged, checkCost), color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(14.dp))
                // Not This Case: no call; the credits used to find it are kept (said plainly).
                LegumSecondaryButton("Not This Case", { lookup = null; cnr = ""; error = null; note = notThisCaseNote(l.charged); step = 0 }, enabled = !saving)
            }
        }
    }
}

// ---- add a case by hand (a case not yet on eCourts, free) ---------------------------

/** The refusal codes whose field is on the first step. */
internal fun handFirstStep(code: String?): Boolean = code in setOf("case_no", "petitioner", "respondent", "duplicate")

/** The fields the server takes for a hand case; never a CNR (the server refuses one: Add By CNR is the way in for those). */
internal fun handFields(caseNo: String, pet: String, res: String, court: String, room: String, date: LocalDate?): Map<String, String> = mapOf(
    "case_no" to caseNo.trim(), "petitioner" to pet.trim(), "respondent" to res.trim(),
    "court" to court.trim(), "room" to room.trim(), "next_date" to date.api(),
)

@Composable
fun HandCaseWizard(onClose: () -> Unit, onAdded: (BoardCase) -> Unit) {
    val context = LocalContext.current
    val repo = remember { BoardRepository(context) }
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableStateOf(0) }
    var caseNo by rememberSaveable { mutableStateOf("") }
    var pet by rememberSaveable { mutableStateOf("") }
    var res by rememberSaveable { mutableStateOf("") }
    var court by rememberSaveable { mutableStateOf("") }
    var room by rememberSaveable { mutableStateOf("") }
    var next by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val date = BoardDates.parse(next)

    WizardDialog(onDismiss = onClose) {
        WizardFrame(
            title = "Add By Hand", steps = listOf("Case", "Court"), step = step,
            ready = if (step == 0) caseNo.isNotBlank() && pet.isNotBlank() && res.isNotBlank() else court.isNotBlank() && date != null,
            saving = saving, saveLabel = "Add To My Diary", error = error, onClose = onClose, onStep = { error = null; step = it },
            onSave = {
                saving = true; error = null
                scope.launch {
                    repo.handAdd(handFields(caseNo, pet, res, court, room, date))
                        .onSuccess(onAdded)
                        .onFailure { error = it.boardMessage(); if (handFirstStep(it.boardCode())) step = 0 }
                    saving = false
                }
            },
        ) {
            if (step == 0) {
                WizardHeading("A Case Not Yet On eCourts", "")
                WizardTextField("Case Number", caseNo, { caseNo = it }, placeholder = "CS/123/2026")
                WizardTextField("Petitioner", pet, { pet = it })
                WizardTextField("Respondent", res, { res = it })
            } else {
                WizardTextField("Court", court, { court = it }, placeholder = "Civil Judge (Jr. Div.) Ludhiana")
                WizardTextField("Court Room", room, { room = it }, placeholder = "Court No. 12")
                DateField("Next Date", date, { next = it.api() }, clearable = false)
                Text(
                    "Adding a case by hand is free. Its daily check is on, but eCourts can only match it once the case has a CNR.",
                    color = SecondaryText, style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

// ---- Set Next Date (a hand case only) -----------------------------------------------

@Composable
fun SetDateWizard(c: BoardCase, onClose: () -> Unit, onSaved: (BoardCase) -> Unit) {
    val context = LocalContext.current
    val repo = remember { BoardRepository(context) }
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableStateOf(0) }
    var next by rememberSaveable { mutableStateOf(c.nextDate.orEmpty()) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val date = BoardDates.parse(next)

    WizardDialog(onDismiss = onClose) {
        WizardFrame(
            title = "Set Next Date", steps = listOf("Date", "Confirm"), step = step, ready = step == 1 || date != null,
            saving = saving, saveLabel = "Set Next Date", error = error, onClose = onClose, onStep = { error = null; step = it },
            onSave = {
                saving = true; error = null
                scope.launch {
                    repo.dateSet(c.id, date.api()).onSuccess(onSaved).onFailure { error = it.boardMessage(); step = 0 }
                    saving = false
                }
            },
        ) {
            if (step == 0) {
                WizardHeading("Next Date", "Choose the date the court has given for this case.")
                DateField("Next Date", date, { next = it.api() }, clearable = false)
            } else {
                WizardHeading("Check And Set", "")
                Text(
                    "This sets the next date of ${c.heading} yourself, because eCourts has no record of it yet. Once the case has a CNR its dates come from eCourts. Setting the date is free.",
                    color = SecondaryText, style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(12.dp))
                WizardRows(listOf("Next Date" to (date?.let { BoardDates.long(it) } ?: "")))
            }
        }
    }
}

// ---- give a hand-added case its CNR (free) ----------------------------------------

@Composable
fun AddCnrWizard(caseId: Int, caseName: String, onClose: () -> Unit, onDone: (BoardCase) -> Unit) {
    val context = LocalContext.current
    val repo = remember { BoardRepository(context) }
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableStateOf(0) }
    var cnr by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    WizardDialog(onDismiss = onClose) {
        WizardFrame(
            title = "Add The CNR", steps = listOf("CNR", "Review"), step = step, ready = cnr.length >= 16 || step == 1,
            saving = saving, savingLabel = "Saving…", saveLabel = "Add The CNR",
            error = error, onClose = onClose, onStep = { error = null; step = it },
            onSave = {
                saving = true; error = null
                scope.launch {
                    repo.caseAddCnr(caseId, cnr.trim()).onSuccess { onDone(it) }
                        .onFailure {
                            error = it.boardMessage()
                            // A refused CNR or a duplicate is put right on the first step.
                            if (it.boardCode() in setOf("cnr", "duplicate")) step = 0
                        }
                    saving = false
                }
            },
        ) {
            if (step == 0) {
                WizardHeading("The Case's CNR", "The CNR is the case's 16-character number from the eCourts site or app. Adding it is free.")
                WizardTextField("CNR", cnr, { cnr = it.uppercase().filter { ch -> !ch.isWhitespace() }.take(16) }, placeholder = "PBLD010012342026", trailing = { CnrScanButton { cnr = it } })
            } else {
                WizardHeading("Check And Add", "The CNR lets the platform check this case on eCourts each morning.")
                WizardRows(listOf("Case" to caseName, "CNR" to cnr))
            }
        }
    }
}

// ---- chambers (the Home profile card) -------------------------------------------

/** Where the counsel sits: address, then city and PIN code. The web's wizard, its wording and limits. */
@Composable
fun ChambersWizard(onClose: () -> Unit, onSaved: (String) -> Unit) {
    val context = LocalContext.current
    val repo = remember { BoardRepository(context) }
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableStateOf(0) }
    var address by rememberSaveable { mutableStateOf("") }
    var city by rememberSaveable { mutableStateOf("") }
    var zip by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    WizardDialog(onDismiss = onClose) {
        WizardFrame(
            title = "Your Chambers", steps = listOf("Address", "City And PIN Code"), step = step,
            ready = step != 0 || address.isNotBlank(), saving = saving, saveLabel = "Save Chambers",
            error = error, onClose = onClose, onStep = { error = null; step = it },
            onSave = {
                saving = true; error = null
                scope.launch {
                    repo.chambersSave(address, city, zip).onSuccess(onSaved).onFailure {
                        error = it.boardMessage()
                        if (it.boardCode() == "address") step = 0
                    }
                    saving = false
                }
            },
        ) {
            if (step == 0) {
                WizardHeading("Where You Sit", "The address of your chambers.")
                WizardTextField("Address", address, { address = it.take(191) }, placeholder = "214, District Courts")
            } else {
                WizardHeading("City And PIN Code", "")
                WizardTextField("City", city, { city = it.take(100) }, placeholder = "Ludhiana")
                WizardTextField("PIN Code", zip, { zip = it.take(15) }, placeholder = "141001")
            }
        }
    }
}

/** The line shown after an import is confirmed, in the web's words. */
fun importDoneLine(d: ImportDone): String {
    var say = "${d.added} added, ${d.updated} updated. You now have ${d.total} ${if (d.total == 1) "case" else "cases"} in your diary."
    if (d.check.checked > 0) {
        say += " ${d.check.checked} checked on eCourts just now" + (if (d.check.unchecked > 0) ", ${d.check.unchecked} not checked (your credits did not cover them)" else "") + "."
    } else if (d.check.stopped != null) say += " They are checked on eCourts from tomorrow morning."
    if (d.mismatches.isNotEmpty()) say += " ${d.mismatches.size} ${if (d.mismatches.size == 1) "date" else "dates"} to settle."
    return say
}

/**
 * The whole import, the same from Home and from the Calendar: the wizard, then what was done (added, updated, checked on eCourts, not
 * checked), then "Which Date Is Right?" for the dates eCourts and the file disagree on. [onChanged] runs when the diary has changed.
 */
@Composable
fun ImportFlow(onClose: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    var done by remember { mutableStateOf<ImportDone?>(null) }
    var mismatches by remember { mutableStateOf<List<Mismatch>>(emptyList()) }
    var wizard by remember { mutableStateOf(true) }
    if (wizard) ImportWizard(onClose = onClose, onDone = {
        wizard = false; done = it
        onChanged()
    })
    // The import spent credits on the checks: the header's balance follows.
    LaunchedEffect(done != null) { if (done != null) BoardRepository(context).credits().onSuccess { c -> BoardHub.applyBalance(c.balance) } }
    done?.let { d ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {},
            title = { Text("Cases Imported") },
            text = { Text(importDoneLine(d)) },
            confirmButton = {
                TextButton({
                    done = null
                    if (d.mismatches.isNotEmpty()) mismatches = d.mismatches else onClose()
                }) { Text(if (d.mismatches.isNotEmpty()) "Settle Dates" else "Done") }
            },
            containerColor = Lex.colors.card,
        )
    }
    if (mismatches.isNotEmpty()) MismatchDialog(mismatches, BoardDates.today(), onDone = { mismatches = emptyList(); onChanged(); onClose() })
}

/** What Not This Case did, said plainly (no call is made; the lookup's credits are kept by the server). */
internal fun notThisCaseNote(charged: Int) = "Nothing was added. The ${creditsLabel(charged)} used to find it ${if (charged == 1) "is" else "are"} not returned."
