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
                WizardHeading("What Was Found", "Check this before the cases are added to your board.")
                WizardRows(
                    listOf(
                        "Cases Found" to p.found.toString(), "New" to p.new.toString(), "Already On Your Board" to p.existing.toString(),
                        "Repeated In The File" to p.duplicates.toString(), "Rows With Problems" to p.errorCount.toString(),
                    ),
                )
                if (p.errors.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp)); WizardLabel("Problems")
                    p.errors.take(5).forEach { Text("Row ${it.row}: ${it.message}", color = SecondaryText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 4.dp)) }
                }
                if (p.sample.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp)); WizardLabel("A Few Of Them")
                    p.sample.take(5).forEach {
                        Text(
                            listOf(it.caseNo.ifBlank { it.cnr }, it.court, BoardDates.show(it.nextDate, BoardDates.today())).filter { s -> s.isNotBlank() }.joinToString("  ·  ") + if (it.isNew) "" else "  (on your board)",
                            color = PrimaryText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

// ---- a case: add or edit ----------------------------------------------------------

@Composable
fun CaseWizard(existing: BoardCase?, onClose: () -> Unit, onSaved: (BoardCase) -> Unit) {
    val context = LocalContext.current
    val repo = remember { BoardRepository(context) }
    val scope = rememberCoroutineScope()
    val e = existing
    var step by rememberSaveable { mutableStateOf(0) }
    var cnr by rememberSaveable { mutableStateOf(e?.cnr.orEmpty()) }
    var caseNo by rememberSaveable { mutableStateOf(e?.caseNo.orEmpty()) }
    var caseType by rememberSaveable { mutableStateOf(e?.caseType.orEmpty()) }
    var pet by rememberSaveable { mutableStateOf(e?.petitioner.orEmpty()) }
    var res by rememberSaveable { mutableStateOf(e?.respondent.orEmpty()) }
    var court by rememberSaveable { mutableStateOf(e?.court.orEmpty()) }
    var room by rememberSaveable { mutableStateOf(e?.room.orEmpty()) }
    var stage by rememberSaveable { mutableStateOf(e?.stage.orEmpty()) }
    var next by rememberSaveable { mutableStateOf(e?.nextDate.orEmpty()) }
    var note by rememberSaveable { mutableStateOf(e?.note.orEmpty()) }
    var check by rememberSaveable { mutableStateOf(e?.morningCheck == true) }
    var checkCost by remember { mutableStateOf<Int?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { repo.credits().onSuccess { checkCost = it.costCheck } }
    val nextDate = BoardDates.parse(next)

    WizardDialog(onDismiss = onClose) {
        WizardFrame(
            title = if (e == null) "Add A Case" else "Edit Case", steps = listOf("Case", "Parties", "Hearing", "Review"), step = step,
            ready = step != 0 || cnr.isNotBlank(), saving = saving, saveLabel = if (e == null) "Add Case" else "Save Changes",
            error = error, onClose = onClose, onStep = { error = null; step = it },
            onSave = {
                saving = true; error = null
                val f = mutableMapOf(
                    "case_no" to caseNo.trim(), "case_type" to caseType.trim(), "petitioner" to pet.trim(), "respondent" to res.trim(),
                    "court" to court.trim(), "room" to room.trim(), "stage" to stage.trim(), "next_date" to next, "note" to note.trim(),
                    "morning_check" to if (check) "1" else "0",
                )
                scope.launch {
                    val r = if (e == null) repo.caseAdd(f + ("cnr" to cnr.trim())) else repo.caseEdit(e.id, f)
                    r.onSuccess(onSaved).onFailure {
                        error = it.boardMessage()
                        // A refused CNR or duplicate is on the first step.
                        if (it.boardCode() in setOf("cnr", "duplicate")) step = 0
                    }
                    saving = false
                }
            },
        ) {
            when (step) {
                0 -> {
                    WizardHeading(if (e == null) "The Case" else "Edit The Case", "The CNR is the case's 16-character number from the eCourts site or app.")
                    WizardTextField("CNR", cnr, { cnr = it.uppercase().filter { ch -> !ch.isWhitespace() }.take(16) }, locked = e != null, placeholder = "PBLD010012342026")
                    WizardTextField("Case Number", caseNo, { caseNo = it }, placeholder = "CS/123/2026")
                    WizardTextField("Case Type", caseType, { caseType = it }, placeholder = "Civil Suit")
                }
                1 -> {
                    WizardHeading("The Parties And The Court", "")
                    WizardTextField("Petitioner", pet, { pet = it })
                    WizardTextField("Respondent", res, { res = it })
                    WizardTextField("Court", court, { court = it }, help = "Cases in the same court are grouped together on the board.")
                    WizardTextField("Court Room", room, { room = it }, placeholder = "Court No. 12")
                }
                2 -> {
                    WizardHeading("The Next Hearing", "")
                    WizardTextField("Stage", stage, { stage = it }, placeholder = "Evidence")
                    DateField("Next Date", nextDate, { next = it.api() })
                    WizardTextField("Note", note, { note = it }, lines = 3)
                    WizardTick(check, "Check each morning whether this case is listed" + (checkCost?.let { " (costs $it credits each time)" } ?: "")) { check = it }
                }
                else -> {
                    WizardHeading("Check And Save", "")
                    WizardRows(
                        listOf(
                            "CNR" to cnr, "Case Number" to caseNo, "Case Type" to caseType, "Petitioner" to pet, "Respondent" to res,
                            "Court" to court, "Court Room" to room, "Stage" to stage, "Next Date" to (nextDate?.let { BoardDates.long(it) } ?: ""),
                            "Morning Check" to if (check) "On" else "Off",
                        ),
                    )
                }
            }
        }
    }
}

// ---- add a case by CNR (premium) ----------------------------------------------

@Composable
fun CnrWizard(onClose: () -> Unit, onBuy: () -> Unit, onAdded: (BoardCase, Int) -> Unit) {
    val context = LocalContext.current
    val repo = remember { BoardRepository(context) }
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableStateOf(0) }
    var cnr by rememberSaveable { mutableStateOf("") }
    var cost by remember { mutableStateOf<Int?>(null) }
    var balance by remember { mutableStateOf<Int?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { repo.credits().onSuccess { cost = it.costAdd; balance = it.balance } }

    WizardDialog(onDismiss = onClose) {
        WizardFrame(
            title = "Add By CNR", steps = listOf("CNR", "Review"), step = step, ready = cnr.length >= 16 || step == 1,
            saving = saving, savingLabel = "Reading The Court Record…", saveLabel = "Add Case" + (cost?.let { " ($it Credits)" } ?: ""),
            error = error, onClose = onClose, onStep = { error = null; code = null; step = it },
            onSave = {
                saving = true; error = null; code = null
                scope.launch {
                    repo.caseByCnr(cnr.trim()).onSuccess { (c, bal) -> onAdded(c, bal) }
                        .onFailure { error = it.boardMessage(); code = it.boardCode() }
                    saving = false
                }
            },
        ) {
            if (step == 0) {
                WizardHeading("The Case's CNR", "The platform reads the case from the court's record and adds it to your board.")
                WizardTextField("CNR", cnr, { cnr = it.uppercase().filter { ch -> !ch.isWhitespace() }.take(16) }, placeholder = "PBLD010012342026", trailing = { CnrScanButton { cnr = it } })
            } else {
                WizardHeading("Check And Add", "Reading from the court's record is a paid action.")
                WizardRows(listOf("CNR" to cnr, "Cost" to (cost?.let { "$it Credits" } ?: ""), "Your Balance" to (balance?.let { "$it Credits" } ?: "")))
                if (code == "insufficient_credits") {
                    Spacer(Modifier.height(14.dp))
                    LegumSecondaryButton("Buy Credits", onBuy)
                }
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
