package com.legumlex.client.feature.board

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.legumlex.client.ui.components.KeyValueRow
import com.legumlex.client.ui.components.LegumSecondaryButton
import com.legumlex.client.ui.components.LexCard
import com.legumlex.client.ui.components.PageHeader
import com.legumlex.client.ui.components.StatusChip
import com.legumlex.client.ui.components.WizardDialog
import com.legumlex.client.ui.components.WizardDropdown
import com.legumlex.client.ui.components.WizardFrame
import com.legumlex.client.ui.components.WizardHeading
import com.legumlex.client.ui.components.WizardLabel
import com.legumlex.client.ui.components.WizardRows
import com.legumlex.client.ui.components.WizardTextField
import com.legumlex.client.ui.components.WizardTick
import com.legumlex.client.ui.theme.Lex
import com.legumlex.client.ui.theme.SecondaryText
import kotlinx.coroutines.launch

// REQ-038 038.5: the Cases filters, paging, and the counsel's own folders and tags, as the web's board/cases and case page.

/** The size of one Cases page, as the web's (100). */
const val CASES_PAGE = 100

data class LabelRef(val id: Int, val name: String)
data class BoardLabel(val id: Int, val name: String, val cases: Int)
data class CourtOption(val key: String, val court: String, val cases: Int)
data class BoardFilters(val counts: Map<String, Int>, val courts: List<CourtOption>, val folders: List<BoardLabel>, val tags: List<BoardLabel>)
data class CasePage(val total: Int, val offset: Int, val limit: Int, val items: List<BoardCase>)

/** What the Cases list asks for: the chip (all, today, tomorrow, undated, eye, disposed), a court_key, a folder id or "none", a tag id, the search. */
data class CaseQuery(val filter: String = "all", val court: String = "", val folder: String = "", val tag: Int = 0, val q: String = "")

enum class LabelKind(val wire: String, val noun: String, val plural: String) {
    Folders("folders", "Folder", "Folders"), Tags("tags", "Tag", "Tags"),
}

internal fun parseLabelRef(m: Map<String, Any?>) = LabelRef(m["id"].int(), m["name"].str())
private fun parseLabel(m: Map<String, Any?>) = BoardLabel(m["id"].int(), m["name"].str(), m["cases"].int())

internal fun parseFilters(d: Map<String, Any?>) = BoardFilters(
    counts = d["counts"].map().mapValues { it.value.int() },
    courts = d["courts"].list().map { CourtOption(it["court_key"].str(), it["court"].str(), it["cases"].int()) },
    folders = d["folders"].list().map(::parseLabel),
    tags = d["tags"].list().map(::parseLabel),
)

internal fun parseCasePage(d: Map<String, Any?>): CasePage {
    val items = d["items"].list().map(::parseCase)
    return CasePage(d["total"].intOrNull() ?: items.size, d["offset"].int(), d["limit"].intOrNull() ?: CASES_PAGE, items)
}

/** The web's chips, in its order and words, each with its count ("Listed Today 3"). */
internal val FILTER_CHIPS = listOf("all" to "All", "today" to "Listed Today", "tomorrow" to "Listed Tomorrow", "undated" to "Undated", "eye" to "Needs Your Eye", "disposed" to "Disposed")

/** "1 to 100 of 240", the web's pager line; null on a single page. */
internal fun pagerLine(p: CasePage): String? =
    if (p.total <= CASES_PAGE && p.offset == 0) null
    else "${creditsText(p.offset + 1)} to ${creditsText(minOf(p.total, p.offset + CASES_PAGE))} of ${creditsText(p.total)}"

/** The delete confirm, as the web asks it. */
internal fun deleteAsk(kind: LabelKind, l: BoardLabel): String {
    val n = "${creditsText(l.cases)} ${if (l.cases == 1) "case" else "cases"}"
    return if (kind == LabelKind.Folders) "Its $n stay, in no folder." else "It comes off $n. The cases stay."
}

/** A filter chip: ink when chosen, outlined otherwise (radius 6). */
@Composable
private fun FilterPill(label: String, count: Int, on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Row(
        Modifier.clip(shape).background(if (on) Lex.colors.foreground else Lex.colors.card)
            .border(1.dp, if (on) Lex.colors.foreground else Lex.colors.input, shape).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = if (on) Lex.colors.background else Lex.colors.foreground)
        Text(creditsText(count), style = MaterialTheme.typography.labelMedium, color = if (on) Lex.colors.background else Lex.colors.secondaryForeground)
    }
}

/** One picker option: its value, its name, how many cases. */
internal data class PickOption(val value: String, val name: String, val cases: Int? = null)

/** What a picker pill says: the chosen option's name, else the picker's own label. */
internal fun pillText(label: String, options: List<PickOption>, selected: String): String =
    options.firstOrNull { it.value == selected && selected.isNotEmpty() }?.name ?: label

internal fun courtOptions(f: BoardFilters) = f.courts.map { PickOption(it.key, it.court, it.cases) }
internal fun folderOptions(f: BoardFilters) = listOf(PickOption("none", "In No Folder")) + f.folders.map { PickOption(it.id.toString(), it.name, it.cases) }
internal fun tagOptions(f: BoardFilters) = f.tags.map { PickOption(it.id.toString(), it.name, it.cases) }

/** A small pill picker: "Court ▾", or the chosen value in ink with a clear ×; the options (with counts) in a menu. */
@Composable
private fun PillPicker(label: String, options: List<PickOption>, selected: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val set = selected.isNotEmpty() && options.any { it.value == selected }
    val shape = RoundedCornerShape(6.dp)
    val fg = if (set) Lex.colors.background else Lex.colors.foreground
    Box {
        Row(
            Modifier.clip(shape).background(if (set) Lex.colors.foreground else Lex.colors.card)
                .border(1.dp, if (set) Lex.colors.foreground else Lex.colors.input, shape).clickable { open = true }
                .padding(start = 12.dp, end = if (set) 4.dp else 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(pillText(label, options, selected), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = fg,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 140.dp))
            if (set) Icon(Icons.Outlined.Close, "Clear $label", tint = fg, modifier = Modifier.padding(start = 2.dp).size(22.dp).clip(RoundedCornerShape(4.dp)).clickable { onSelect("") }.padding(3.dp))
            else Icon(Icons.Outlined.ExpandMore, null, tint = fg, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(open, { open = false }) {
            if (options.isEmpty()) DropdownMenuItem(text = { Text("None yet") }, onClick = { open = false }, enabled = false)
            options.forEach { o ->
                DropdownMenuItem(
                    text = { Text(o.name + (o.cases?.let { "  ${creditsText(it)}" } ?: ""), fontWeight = if (o.value == selected) FontWeight.SemiBold else FontWeight.Normal) },
                    onClick = { open = false; onSelect(o.value) },
                )
            }
        }
    }
}

/** The chips, then one row of Court, Folder and Tag pills and the Folders And Tags button: compact, so the first case shows. */
@Composable
fun CaseFilterBar(f: BoardFilters?, q: CaseQuery, onChange: (CaseQuery) -> Unit, onManage: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FILTER_CHIPS.forEach { (k, label) -> FilterPill(label, f?.counts?.get(k) ?: 0, q.filter == k) { onChange(q.copy(filter = k)) } }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val fs = f ?: BoardFilters(emptyMap(), emptyList(), emptyList(), emptyList())
                PillPicker("Court", courtOptions(fs), q.court) { onChange(q.copy(court = it)) }
                PillPicker("Folder", folderOptions(fs), q.folder) { onChange(q.copy(folder = it)) }
                PillPicker("Tag", tagOptions(fs), if (q.tag > 0) q.tag.toString() else "") { onChange(q.copy(tag = it.toIntOrNull() ?: 0)) }
            }
            IconAction(Icons.Outlined.Folder, "Folders And Tags", onManage, size = 36.dp)
        }
    }
}

/** A row's folder and tag badges. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LabelBadges(c: BoardCase) {
    if (c.folder == null && c.tags.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        c.folder?.let { StatusChip(it.name, Lex.colors.neutral, Icons.Outlined.Folder) }
        c.tags.forEach { StatusChip(it.name, Lex.colors.neutral) }
    }
}

/** Folders And Tags: the counsel's own; New and Rename through a two-step wizard, Delete after a confirm. */
@Composable
fun FoldersAndTagsDialog(onClose: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { BoardRepository(context) }
    val scope = rememberCoroutineScope()
    var f by remember { mutableStateOf<BoardFilters?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableStateOf(0) }
    var editing by remember { mutableStateOf<Pair<LabelKind, BoardLabel?>?>(null) }
    var deleting by remember { mutableStateOf<Pair<LabelKind, BoardLabel>?>(null) }
    LaunchedEffect(tick) { repo.filters().onSuccess { f = it; error = null }.onFailure { error = it.boardMessage() } }

    WizardDialog(onDismiss = onClose) {
        Column(Modifier.fillMaxSize().background(Lex.colors.background).statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PageHeader(eyebrow = "Your Own", title = "Folders And Tags")
            error?.let { NoteLine(it, danger = true) }
            LabelKind.entries.forEach { kind ->
                val rows = if (kind == LabelKind.Folders) f?.folders.orEmpty() else f?.tags.orEmpty()
                LexCard {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(kind.plural, style = MaterialTheme.typography.labelLarge, color = Lex.colors.foreground, modifier = Modifier.weight(1f))
                        TextButton({ editing = kind to null }) { Text("New ${kind.noun}") }
                    }
                    Text(if (kind == LabelKind.Folders) "A case sits in one folder." else "A case can carry many tags.",
                        style = MaterialTheme.typography.bodySmall, color = Lex.colors.secondaryForeground, modifier = Modifier.padding(horizontal = 16.dp))
                    if (f != null && rows.isEmpty()) Text("None yet.", style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground, modifier = Modifier.padding(16.dp))
                    rows.forEach { l ->
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${l.name}  ", style = MaterialTheme.typography.bodyMedium, color = Lex.colors.foreground)
                            Text(creditsText(l.cases), style = MaterialTheme.typography.bodySmall, color = Lex.colors.secondaryForeground, modifier = Modifier.weight(1f))
                            TextButton({ editing = kind to l }) { Text("Rename") }
                            TextButton({ deleting = kind to l }) { Text("Delete", color = Lex.colors.danger.softText) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            LegumSecondaryButton("Close", onClose)
        }
    }

    editing?.let { (kind, l) ->
        LabelNameWizard(kind, l, onClose = { editing = null }, onSaved = { editing = null; tick++; onChanged() })
    }
    deleting?.let { (kind, l) ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${l.name}?") },
            text = { Text(deleteAsk(kind, l)) },
            confirmButton = {
                TextButton({
                    deleting = null
                    scope.launch { repo.labelDelete(kind, l.id).onSuccess { tick++; onChanged() }.onFailure { error = it.boardMessage() } }
                }) { Text("Delete", color = Lex.colors.danger.softText) }
            },
            dismissButton = { TextButton({ deleting = null }) { Text("Keep") } },
        )
    }
}

/** New or Rename: the name, then "Save it as ...?". A refused name returns to the first step with the server's words. */
@Composable
private fun LabelNameWizard(kind: LabelKind, l: BoardLabel?, onClose: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { BoardRepository(context) }
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableStateOf(0) }
    var name by rememberSaveable { mutableStateOf(l?.name.orEmpty()) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val title = (if (l == null) "New " else "Rename ") + kind.noun

    WizardDialog(onDismiss = onClose) {
        WizardFrame(
            title = title, steps = listOf("Name", "Confirm"), step = step, ready = step == 1 || name.isNotBlank(),
            saving = saving, saveLabel = "Save", error = error, onClose = onClose, onStep = { error = null; step = it },
            onSave = {
                saving = true; error = null
                scope.launch {
                    repo.labelSave(kind, l?.id ?: 0, name).onSuccess { onSaved() }.onFailure { error = it.boardMessage(); step = 0 }
                    saving = false
                }
            },
        ) {
            if (step == 0) {
                WizardHeading("Name", "")
                WizardTextField("Name", name, { name = it.take(60) })
            } else {
                WizardHeading("Check And Save", "")
                Text("Save it as ${name.trim()}?", color = SecondaryText, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** The case page's Folder And Tags card. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FolderAndTagsCard(c: BoardCase, onFile: () -> Unit) {
    LexCard {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Folder And Tags", style = MaterialTheme.typography.labelLarge, color = Lex.colors.mutedForeground, modifier = Modifier.weight(1f))
            TextButton(onFile) { Text("File This Case") }
        }
        KeyValueRow("Folder", c.folder?.name ?: "In No Folder")
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
            if (c.tags.isEmpty()) Text("No Tags.", style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground)
            else FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { c.tags.forEach { StatusChip(it.name, Lex.colors.neutral) } }
        }
    }
}

/** File This Case: the folder, then the tags (the counsel's own, made under Folders And Tags on Cases). */
@Composable
fun FileCaseWizard(c: BoardCase, onClose: () -> Unit, onSaved: (BoardCase) -> Unit) {
    val context = LocalContext.current
    val repo = remember { BoardRepository(context) }
    val scope = rememberCoroutineScope()
    var f by remember { mutableStateOf<BoardFilters?>(null) }
    var step by rememberSaveable { mutableStateOf(0) }
    var folder by rememberSaveable { mutableStateOf(c.folder?.id ?: 0) }
    var tags by remember { mutableStateOf(c.tags.map { it.id }.toSet()) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { repo.filters().onSuccess { f = it }.onFailure { error = it.boardMessage() } }

    WizardDialog(onDismiss = onClose) {
        WizardFrame(
            title = "File This Case", steps = listOf("Folder", "Tags"), step = step, ready = f != null,
            saving = saving, saveLabel = "Save", error = error, onClose = onClose, onStep = { error = null; step = it },
            onSave = {
                saving = true; error = null
                scope.launch {
                    repo.caseLabels(c.id, folder, tags).onSuccess(onSaved).onFailure { error = it.boardMessage(); if (it.boardCode() == "folder_not_found") step = 0 }
                    saving = false
                }
            },
        ) {
            val fs = f
            if (step == 0) {
                WizardHeading("Folder", "")
                WizardDropdown("Folder", listOf("0" to "In No Folder") + fs?.folders.orEmpty().map { it.id.toString() to it.name }, folder.toString()) { folder = it.toIntOrNull() ?: 0 }
                if (fs != null && fs.folders.isEmpty() && fs.tags.isEmpty()) Text("Make folders and tags on Cases, under Folders And Tags.", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            } else {
                WizardHeading("Tags", "")
                if (fs?.tags.isNullOrEmpty()) Text("No tags yet.", color = SecondaryText, style = MaterialTheme.typography.bodySmall)
                fs?.tags.orEmpty().forEach { t -> WizardTick(t.id in tags, t.name) { on -> tags = if (on) tags + t.id else tags - t.id } }
                Spacer(Modifier.height(8.dp))
                WizardLabel("Saving")
                WizardRows(listOf("Folder" to (fs?.folders?.firstOrNull { it.id == folder }?.name ?: "In No Folder"),
                    "Tags" to (fs?.tags.orEmpty().filter { it.id in tags }.joinToString(", ") { it.name }.ifBlank { "None" })))
            }
        }
    }
}
