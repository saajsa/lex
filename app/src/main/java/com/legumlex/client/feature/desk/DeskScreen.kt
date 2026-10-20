package com.legumlex.client.feature.desk

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HowToReg
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.legumlex.client.ahc.theme.Inter
import kotlinx.coroutines.delay

/* ============================================================================
 *  The Legum Lex desk (Consult) on a phone: the website's "Lex Intelligence"
 *  drawer (cases/views/public/site/partials/desk_panel.php around
 *  partials/reception.php), in its own colours whatever the phone's theme,
 *  as the drawer keeps them whatever the site's.
 *
 *  Before the first question: the drawer's intro (what has happened, where to
 *  start, the three steps), the composer, and its services (have an agreement
 *  read, track a notice). After: the conversation, and below it whatever the
 *  desk is asking for.
 * ========================================================================== */

private object D {
    val Page = Color(0xFF0B0F17)
    val Ink = Color(0xFFEBE7DF)
    val Me = Color(0xFF232C3D)
    val MeInk = Color(0xFFF5F2EC)
    val Line = Color(0x17FFFFFF)
    val Line2 = Color(0x26FFFFFF)
    val Raise = Color(0xFF111725)
    val Mark = Color(0xFF1A2130)
    val Gold = Color(0xFFF3C25E)
    val OnGold = Color(0xFF1B1406)
    val Live = Color(0xFF3FAE7C)
    val Warn = Color(0xFFF2A65A)
    val Muted = Ink.copy(alpha = 0.62f)
    val Quiet = Ink.copy(alpha = 0.5f)
}

private val Mono = FontFamily.Monospace

private fun body(size: Float = 14.5f, color: Color = D.Ink, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = Inter, fontSize = size.sp, lineHeight = (size * 1.55f).sp, color = color, fontWeight = weight)

private val EXAMPLES = listOf("A cheque I got bounced…", "My tenant owes me rent…", "I got a legal notice…",
    "I want to set up a firm…", "My boss has not paid me…", "Someone took my land…")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeskScreen(
    onClose: () -> Unit,
    onOpenMatters: () -> Unit,
    onOpenConsultation: (Int) -> Unit,
    onAgreement: () -> Unit,
) {
    val vm: DeskViewModel = viewModel()
    val ui by vm.ui.collectAsState()
    var tracking by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    // The fee is paid on the website: look again on coming back.
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) vm.resumed()
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                DeskEvent.OpenMatters -> onOpenMatters()
                is DeskEvent.OpenConsultation -> onOpenConsultation(e.id)
                is DeskEvent.OpenUrl -> runCatching {
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(e.url)))
                }
            }
        }
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.addFiles(it) }
    val mimes = remember(ui.terms.documentTypes) { mimesFor(ui.terms.documentTypes) }
    val attach: (() -> Unit)? = if (ui.terms.takesFiles) ({ pick.launch(mimes) }) else null

    // The question's draft lives here, so a "Change" can hand it back; a
    // starter or a "Change" puts the cursor at its end and the focus on it.
    var draft by remember { mutableStateOf(TextFieldValue("")) }
    val compose = remember { FocusRequester() }
    var focusTick by remember { mutableIntStateOf(0) }
    val put: (String) -> Unit = { draft = TextFieldValue(it, TextRange(it.length)); focusTick++ }
    LaunchedEffect(ui.prefillTick) { if (ui.prefillTick > 0) put(ui.prefill) }
    LaunchedEffect(focusTick) { if (focusTick > 0) { delay(80); runCatching { compose.requestFocus() } } }

    val typing = WindowInsets.isImeVisible
    var services by remember { mutableStateOf(false) }
    // Opening or closing the curtain must not hand focus to the question box,
    // or the keyboard springs up under the client's thumb.
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val showServices: (Boolean) -> Unit = { focusManager.clearFocus(force = true); services = it }
    androidx.activity.compose.BackHandler(enabled = services) { showServices(false) }
    // The keyboard put away is the box let go: it returns to rest.
    var hadKeyboard by remember { mutableStateOf(false) }
    LaunchedEffect(typing) {
        if (typing) hadKeyboard = true else if (hadKeyboard) { hadKeyboard = false; focusManager.clearFocus() }
    }
    Column(Modifier.fillMaxSize().background(D.Page).imePadding()) {
        Head(onClose)
        if (!ui.opened) {
            // The services hang from the header like a curtain: pulled down,
            // they push the desk down beneath them.
            Curtain(
                open = services && !typing,
                firm = ui.terms.firm,
                onToggle = { showServices(it) },
                onReview = { showServices(false); onAgreement() },
                onTrack = { showServices(false); tracking = true },
            )
            // The intro at the top, the middle left empty, the composer at the
            // foot, as a chat is laid out.
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Intro()
                if (!typing) {
                    Spacer(Modifier.height(18.dp))
                    Process()
                }
                Spacer(Modifier.height(16.dp))
            }
            Box(Modifier.padding(horizontal = 14.dp)) {
                if (ui.termsLoaded && !ui.terms.isActive) {
                    Text("The desk is not taking new enquiries just now. Please write to your advocate on one of your matters, or try again later.",
                        style = body(14f, D.Muted), modifier = Modifier.padding(vertical = 12.dp))
                } else {
                    QuestionDock(draft, { draft = it }, compose, ui, attach, vm::removeFile,
                        onFocus = { if (it && services) services = false }) { vm.submitQuestion(draft.text) }
                }
            }
            if (ui.termsLoaded) Disclaimer(ui.terms, compact = true)
        } else {
            Conversation(ui, vm::change, Modifier.weight(1f))
            Box(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                when (val d = ui.dock) {
                    Dock.Question -> QuestionDock(draft, { draft = it }, compose, ui, attach, vm::removeFile) { vm.submitQuestion(draft.text) }
                    Dock.Clarify -> ClarifyDock(ui.error, vm::submitClarify, vm::sendAsItIs)
                    Dock.Docs -> DocsDock(ui, attach, vm::removeFile, vm::docsGo)
                    is Dock.Actions -> Column { Chips(d.list.map { Triple(it.label, it.main) { vm.act(it.id) } }); ErrorLine(ui.error) }
                    is Dock.Entity -> EntityDock(d.hint, vm::chooseEntity)
                    Dock.Country -> CountryDock(ui, vm::chooseCountry, vm::otherCountry)
                    Dock.Phone -> PhoneDock(vm.nri, vm.dial, ui.terms.phoneRequired, ui.error, vm::phoneProblem, vm::givePhone, vm::noPhone)
                    is Dock.Waiting -> WaitLine(d.label)
                }
            }
            if (ui.termsLoaded) Disclaimer(ui.terms, compact = true)
        }
    }

    if (tracking) TrackSheet(onDismiss = { tracking = false })
}

private fun mimesFor(types: List<String>): Array<String> = types.mapNotNull {
    when (it) {
        "pdf" -> "application/pdf"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "heic" -> "image/heic"
        "heif" -> "image/heif"
        else -> null
    }
}.distinct().ifEmpty { listOf("*/*") }.toTypedArray()

// ---- The drawer's head and foot ---------------------------------------------

@Composable
private fun Head(onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(9.dp)).background(D.Gold), contentAlignment = Alignment.Center) {
            Text(">_", style = TextStyle(fontFamily = Mono, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = D.OnGold))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Lex Intelligence", style = body(16.5f, weight = FontWeight.SemiBold).copy(lineHeight = 20.sp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(D.Live))
                Spacer(Modifier.width(6.dp))
                Text("counsel desk · taking enquiries", style = TextStyle(fontFamily = Mono, fontSize = 11.5.sp, color = D.Muted))
            }
        }
        Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Close, "Close the desk", tint = D.Muted, modifier = Modifier.size(22.dp))
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(D.Line))
}

@Composable
private fun Foot(firm: String) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(D.Line))
    Text("Legum Lex is not a law firm. What you write here goes to the advocates of " + firm.ifBlank { "the firm" } + if (firm.endsWith(".")) "" else ".",
        style = body(11.5f, D.Ink.copy(alpha = 0.58f)), modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp))
}

@Composable
private fun Kicker(s: String, modifier: Modifier = Modifier) =
    Text("// $s", style = TextStyle(fontFamily = Mono, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = D.Muted), modifier = modifier)

// ---- Before the first question --------------------------------------------------

@Composable
private fun Intro() {
    Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 22.dp)) {
        Text("What has happened?", style = body(25f, weight = FontWeight.SemiBold).copy(lineHeight = 30.sp, letterSpacing = (-0.4).sp))
        Spacer(Modifier.height(8.dp))
        Text("In your own words: what happened, who is involved, and what you would like counsel to do.", style = body(14.5f, D.Ink.copy(alpha = 0.7f)))
    }
}

/** The drawer's three steps, and the status line that walks them. */
@Composable
private fun Process() {
    val steps = listOf(
        Triple(Icons.Outlined.Hub, "Analysed by AI", "> analysing your enquiry"),
        Triple(Icons.Outlined.HowToReg, "Reviewed by an advocate", "> routing to an advocate for review"),
        Triple(Icons.Outlined.Timer, "Usually answered within 30 minutes", "> reply on its way, usually within 30 minutes"),
    )
    var at by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(2_600); at = (at + 1) % steps.size } }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Box(Modifier.fillMaxWidth()) {
            Canvas(Modifier.fillMaxWidth().height(40.dp)) {
                val y = size.height / 2
                drawLine(Color(0x26FFFFFF), Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx())
                drawLine(D.Gold, Offset(0f, y), Offset(size.width * (at * 2 + 1) / 6f, y), 2.dp.toPx())
            }
            Row(Modifier.fillMaxWidth()) {
                steps.forEachIndexed { i, (icon, _, _) ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(D.Page)
                                .border(1.5.dp, if (i == at) D.Gold else D.Line2, RoundedCornerShape(11.dp)),
                            contentAlignment = Alignment.Center,
                        ) { Icon(icon, null, tint = if (i == at) D.Gold else D.Muted, modifier = Modifier.size(20.dp)) }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            steps.forEachIndexed { i, (_, label, _) ->
                Text(label, style = body(13f, if (i == at) D.Ink else D.Muted, FontWeight.SemiBold).copy(lineHeight = 17.sp),
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f).padding(horizontal = 4.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(steps[at].third, style = TextStyle(fontFamily = Mono, fontSize = 12.5.sp, color = D.Gold), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Disclaimer(t: DeskTerms, compact: Boolean = false) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = D.Ink)) {
                append(if (t.priced) "Submitting an enquiry costs ${t.displayPrice}, paid once it is written. Nothing is charged before then." else "Submitting an enquiry is free.")
            }
            append(" The advocates read every enquiry, and usually reply within 30 minutes of it reaching them.")
        },
        style = body(if (compact) 11.5f else 13f, D.Muted).copy(lineHeight = if (compact) 16.sp else 19.sp),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = if (compact) 8.dp else 16.dp),
    )
}

/**
 * The services, hung from the header's rule like a curtain: a curved tab to
 * pull down (or tap), the panel dropping open and pushing the desk down under
 * it; pull up, tap the tab or go back to draw it again.
 */
@Composable
private fun Curtain(open: Boolean, firm: String, onToggle: (Boolean) -> Unit, onReview: () -> Unit, onTrack: () -> Unit) {
    var pulled by remember { mutableStateOf(0f) }
    val drag = Modifier.pointerInput(open) {
        detectVerticalDragGestures(
            onDragStart = { pulled = 0f },
            onVerticalDrag = { _, dy ->
                pulled += dy
                if (!open && pulled > 24.dp.toPx()) { pulled = 0f; onToggle(true) }
                if (open && pulled < -24.dp.toPx()) { pulled = 0f; onToggle(false) }
            },
        )
    }
    Column(Modifier.fillMaxWidth().then(drag)) {
        androidx.compose.animation.AnimatedVisibility(
            open,
            enter = androidx.compose.animation.expandVertically(tween(280), expandFrom = Alignment.Top) + androidx.compose.animation.fadeIn(tween(200)),
            exit = androidx.compose.animation.shrinkVertically(tween(240), shrinkTowards = Alignment.Top) + androidx.compose.animation.fadeOut(tween(160)),
        ) {
            Column(Modifier.fillMaxWidth().background(D.Raise)) {
                Kicker("services", Modifier.padding(start = 18.dp, top = 12.dp, bottom = 4.dp))
                Service(Icons.Outlined.Description, "Review", "An advocate reads your agreement for risk, before you sign it.", onReview)
                Box(Modifier.padding(horizontal = 18.dp).fillMaxWidth().height(1.dp).background(D.Line))
                Service(Icons.Outlined.LocalShipping, "Track", "Follow a legal notice sent for you, from its postal reference.", onTrack)
                Box(Modifier.fillMaxWidth().height(1.dp).background(D.Line))
                Text("Legum Lex is not a law firm. What you write here goes to the advocates of " + firm.ifBlank { "the firm" } + if (firm.endsWith(".")) "" else ".",
                    style = body(11.5f, D.Ink.copy(alpha = 0.58f)), modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
            }
        }
        // The curtain's hem: a soft swag hanging from the rule, the tab in it.
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Box(
                Modifier.width(196.dp).height(34.dp)
                    .clip(CurtainHem)
                    .background(D.Raise)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                    ) { onToggle(!open) },
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                    Text("Services", style = TextStyle(fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (open) D.Gold else D.Muted))
                    Spacer(Modifier.width(4.dp))
                    Icon(if (open) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown, null,
                        tint = if (open) D.Gold else D.Muted, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/** A curtain's swag: flat along the rule, curving down to a rounded hem and back. */
private val CurtainHem = androidx.compose.foundation.shape.GenericShape { size, _ ->
    val w = size.width; val h = size.height
    moveTo(0f, 0f)
    cubicTo(w * 0.16f, 0f, w * 0.18f, h, w * 0.34f, h)
    lineTo(w * 0.66f, h)
    cubicTo(w * 0.82f, h, w * 0.84f, 0f, w, 0f)
    close()
}

/** One service: a single-word name and two lines on what it does. */
@Composable
private fun Service(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, D.Line2, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = D.Gold, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = body(15.5f, weight = FontWeight.SemiBold).copy(lineHeight = 20.sp))
            Spacer(Modifier.height(2.dp))
            Text(sub, style = body(12.5f, D.Muted).copy(lineHeight = 17.sp), minLines = 2, maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = D.Muted, modifier = Modifier.size(20.dp))
    }
}

// ---- The conversation ---------------------------------------------------------------

@Composable
private fun Conversation(ui: DeskUi, onChange: (Step) -> Unit, modifier: Modifier) {
    val list = rememberLazyListState()
    // Each desk line's words come in one after another, once.
    val revealed = remember { mutableStateMapOf<Long, Boolean>() }
    LaunchedEffect(ui.lines.size, ui.typing) {
        val n = ui.lines.size + if (ui.typing) 1 else 0
        if (n > 0) list.animateScrollToItem(n - 1)
    }
    LazyColumn(modifier.fillMaxWidth(), state = list, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        items(ui.lines.size, key = { ui.lines[it].id }) { i ->
            val line = ui.lines[i]
            when (line.who) {
                Who.ME -> MeLine(line, !ui.locked) { line.step?.let(onChange) }
                // The desk's mark heads each run of what it says, not every line.
                Who.DESK -> DeskLine(line, revealed, mark = i == 0 || ui.lines[i - 1].who != Who.DESK)
                Who.NOTE -> NoteLine(line)
            }
        }
        if (ui.typing) item(key = "typing") { Typing() }
    }
}

@Composable
private fun DeskMark() {
    Box(Modifier.size(26.dp).clip(RoundedCornerShape(7.dp)).background(D.Mark), contentAlignment = Alignment.Center) {
        Text(">_", style = TextStyle(fontFamily = Mono, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = D.Gold))
    }
}

@Composable
private fun DeskLine(line: Line, revealed: SnapshotStateMap<Long, Boolean>, mark: Boolean) {
    // The words, one after another (the page's rcp-w): 26ms apart.
    val total = line.paras.sumOf { (it as? Para.Text)?.s?.split(' ')?.size ?: 0 }
    var shown by remember(line.id) { mutableIntStateOf(if (revealed[line.id] == true) total else 0) }
    LaunchedEffect(line.id) {
        while (shown < total) { delay(26); shown++ }
        revealed[line.id] = true
    }
    Row(Modifier.fillMaxWidth()) {
        if (mark) DeskMark() else Spacer(Modifier.width(26.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            var before = 0
            line.paras.forEach { p ->
                when (p) {
                    is Para.Text -> {
                        val words = p.s.split(' ').size
                        val upto = (shown - before).coerceIn(0, words)
                        before += words
                        Words(p.s, upto, p.flag)
                    }
                    is Para.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        p.items.forEach { Row { Text("–  ", style = body(color = D.Gold)); Text(it, style = body()) } }
                    }
                    is Para.Facts -> Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, D.Line2, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        p.rows.forEach { (k, v) ->
                            Column {
                                Text(k.uppercase(), style = TextStyle(fontFamily = Mono, fontSize = 10.5.sp, letterSpacing = 0.8.sp, color = D.Muted))
                                Text(v, style = body(14f))
                            }
                        }
                    }
                    is Para.Stages -> Stages(p)
                    Para.Seal -> Seal()
                }
            }
        }
    }
}

/** A paragraph with **bold**, showing its first [upto] words. */
@Composable
private fun Words(s: String, upto: Int, flag: Boolean) {
    val words = s.split(' ')
    val text = buildAnnotatedString {
        var bold = false
        words.forEachIndexed { i, w ->
            var word = w
            val open = word.startsWith("**")
            if (open) { bold = true; word = word.removePrefix("**") }
            val close = word.contains("**")
            word = word.replace("**", "")
            val style = if (bold) SpanStyle(fontWeight = FontWeight.SemiBold, color = Color.White) else SpanStyle()
            withStyle(if (i < upto) style else style.copy(color = Color.Transparent)) { append(word) }
            if (close) bold = false
            if (i < words.size - 1) append(' ')
        }
    }
    if (flag) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(2.dp).fillMaxHeight().background(D.Gold))
            Spacer(Modifier.width(10.dp))
            Text(text, style = body())
        }
    } else Text(text, style = body())
}

@Composable
private fun MeLine(line: Line, canChange: Boolean, onChange: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Column(
            Modifier.padding(start = 48.dp).clip(RoundedCornerShape(14.dp, 14.dp, 4.dp, 14.dp)).background(D.Me).padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            val s = (line.paras.firstOrNull() as? Para.Text)?.s.orEmpty()
            Text(if (line.step == Step.QUESTION && s.length > 420) s.take(420).trimEnd() + "…" else s, style = body(color = D.MeInk))
            line.files.forEach { f ->
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AttachFile, null, tint = D.Gold, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(f, style = body(12.5f, D.MeInk.copy(alpha = 0.8f)))
                }
            }
        }
        if (canChange) {
            Text("Change", style = TextStyle(fontFamily = Mono, fontSize = 11.5.sp, color = D.Ink.copy(alpha = 0.58f)),
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onChange).padding(horizontal = 8.dp, vertical = 6.dp))
        }
    }
}

@Composable
private fun NoteLine(line: Line) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (line.warn) D.Warn.copy(alpha = 0.1f) else D.Mark)
            .border(1.dp, if (line.warn) D.Warn.copy(alpha = 0.35f) else D.Line2, RoundedCornerShape(12.dp)).padding(12.dp),
    ) {
        Icon(Icons.Outlined.ErrorOutline, null, tint = if (line.warn) D.Warn else D.Muted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            line.paras.forEach { if (it is Para.Text) Words(it.s, Int.MAX_VALUE, false) }
        }
    }
}

@Composable
private fun Typing() {
    val tr = rememberInfiniteTransition(label = "typing")
    val p by tr.animateFloat(0f, 3f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "typing-p")
    Row(verticalAlignment = Alignment.CenterVertically) {
        DeskMark()
        Spacer(Modifier.width(12.dp))
        repeat(3) { i ->
            Box(Modifier.padding(end = 5.dp).size(6.dp).alpha(if (p.toInt() == i) 1f else 0.35f).clip(CircleShape).background(D.Ink))
        }
    }
}

private val STAGES = listOf("Securing your enquiry", "Sending it to the chamber", "Recording it on the file", "Notifying counsel")

@Composable
private fun Stages(p: Para.Stages) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (p.done) "Delivered." else "Taking your enquiry in…", style = body(weight = FontWeight.SemiBold, color = Color.White))
        STAGES.forEachIndexed { i, s ->
            val done = p.done || i < p.active
            val active = !p.done && i == p.active
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(18.dp).clip(CircleShape).background(if (done) D.Gold else Color.Transparent)
                        .border(1.5.dp, if (done || active) D.Gold else D.Line2, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) Icon(Icons.Outlined.Check, null, tint = D.OnGold, modifier = Modifier.size(12.dp))
                    else if (active) Pulse()
                }
                Spacer(Modifier.width(10.dp))
                Text(s, style = body(13.5f, if (done || active) D.Ink else D.Muted))
            }
        }
    }
}

@Composable
private fun Pulse() {
    val tr = rememberInfiniteTransition(label = "pulse")
    val a by tr.animateFloat(0.3f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "pulse-a")
    Box(Modifier.size(7.dp).alpha(a).clip(CircleShape).background(D.Gold))
}

/** The seal: a ring drawn round, then the tick. */
@Composable
private fun Seal() {
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val tr = rememberInfiniteTransition(label = "seal")
    val k by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(1_400, easing = LinearEasing), RepeatMode.Restart), label = "seal-k")
    val gold = D.Gold
    Canvas(Modifier.size(44.dp)) {
        val r = size.minDimension / 2 - 2.dp.toPx()
        drawCircle(gold, r, style = Stroke(2.dp.toPx()))
        drawCircle(gold.copy(alpha = (1f - k) * 0.35f), r + k * 6.dp.toPx(), style = Stroke(1.dp.toPx()))
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width * 0.32f, size.height * 0.51f); lineTo(size.width * 0.44f, size.height * 0.64f); lineTo(size.width * 0.69f, size.height * 0.37f)
        }
        drawPath(path, gold, style = Stroke(2.5.dp.toPx()))
    }
}

// ---- The dock ------------------------------------------------------------------------------

@Composable
private fun ErrorLine(s: String) {
    if (s.isNotEmpty()) Text(s, style = body(12.5f, D.Warn), modifier = Modifier.padding(top = 6.dp, start = 4.dp))
}

@Composable
private fun WaitLine(label: String) {
    val tr = rememberInfiniteTransition(label = "cursor")
    val a by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "cursor-a")
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("> " + label.ifBlank { "" }, style = TextStyle(fontFamily = Mono, fontSize = 12.5.sp, color = D.Gold))
        Box(Modifier.padding(start = 3.dp).size(width = 7.dp, height = 14.dp).alpha(a).background(D.Gold))
    }
}

/** The composer: what has happened, with a paperclip where files are taken. */
@Composable
private fun QuestionDock(value: TextFieldValue, onValue: (TextFieldValue) -> Unit, focus: FocusRequester, ui: DeskUi, attach: (() -> Unit)?, onRemove: (Int) -> Unit,
                         onFocus: (Boolean) -> Unit = {}, onSend: () -> Unit) {
    // Answers to focus: the box lifts, its rule brightens and it opens up for
    // writing; at rest it sits quieter and lower.
    var focused by remember { mutableStateOf(false) }
    val rule by androidx.compose.animation.core.animateFloatAsState(if (focused) 1f else 0.45f, tween(180), label = "rule")
    val ruleW by androidx.compose.animation.core.animateDpAsState(if (focused) 2.dp else 1.dp, tween(180), label = "ruleW")
    val room by androidx.compose.animation.core.animateDpAsState(if (focused) 96.dp else 44.dp, tween(220), label = "room")
    val text = value.text
    // The examples type themselves into the empty box, letter by letter, and
    // erase for the next; they stop the moment the client writes.
    // Focused, it rests on a whole example and leaves the cursor to the field.
    var shown by remember { mutableStateOf("") }
    var ex by remember { mutableIntStateOf(0) }
    LaunchedEffect(text.isEmpty(), focused) {
        if (text.isNotEmpty()) return@LaunchedEffect
        if (focused) { shown = EXAMPLES[ex]; return@LaunchedEffect }
        while (true) {
            val line = EXAMPLES[ex]
            for (i in 1..line.length) { shown = line.take(i); delay(if (line[i - 1] == ' ') 90 else 55) }
            delay(1_500)
            for (i in line.length downTo 0) { shown = line.take(i); delay(22) }
            delay(350)
            ex = (ex + 1) % EXAMPLES.size
        }
    }
    Column {
        FileChips(ui.files, onRemove)
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (focused) D.Mark else D.Raise)
                .border(ruleW, D.Gold.copy(alpha = rule), RoundedCornerShape(18.dp))
                .padding(start = 16.dp, end = 10.dp, top = 14.dp, bottom = 10.dp),
        ) {
            Box(Modifier.fillMaxWidth().heightIn(min = room)) {
                if (text.isEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(shown, style = body(15.5f, D.Ink.copy(alpha = if (focused) 0.32f else 0.42f)))
                    if (!focused) Box(Modifier.padding(start = 1.dp).width(2.dp).height(18.dp).background(D.Gold.copy(alpha = 0.7f)))
                }
                BasicTextField(
                    value, { if (it.text.length <= ui.terms.maxChars) onValue(it) },
                    textStyle = body(15.5f, D.MeInk), cursorBrush = SolidColor(D.Gold),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 168.dp).focusRequester(focus)
                        .onFocusChanged { if (it.isFocused != focused) { focused = it.isFocused; onFocus(it.isFocused) } },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (attach != null) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).border(1.dp, D.Line2, RoundedCornerShape(10.dp)).clickable(onClick = attach),
                        contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Add, "Attach files", tint = D.Ink, modifier = Modifier.size(20.dp)) }
                }
                Spacer(Modifier.weight(1f))
                if (text.length > 4000) Text("${text.length} / ${ui.terms.maxChars}", style = TextStyle(fontFamily = Mono, fontSize = 11.sp, color = D.Muted), modifier = Modifier.padding(end = 10.dp))
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(D.Gold).clickable(onClick = onSend), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Outlined.Send, "Send", tint = D.OnGold, modifier = Modifier.size(19.dp))
                }
            }
        }
        ErrorLine(ui.error)
    }
}

@Composable
private fun FileChips(files: List<DeskFile>, onRemove: (Int) -> Unit) {
    if (files.isEmpty()) return
    Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        files.forEachIndexed { i, f ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(D.Mark).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AttachFile, null, tint = D.Gold, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp))
                Text(f.name + " · " + DeskViewModel.mb(f.size), style = body(13f), maxLines = 1, modifier = Modifier.weight(1f))
                Box(Modifier.size(40.dp).clickable { onRemove(i) }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Close, "Remove", tint = D.Muted, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun Compose(placeholder: String, keyboard: KeyboardOptions, prefix: String? = null, initial: String = "",
                    valid: ((String) -> Boolean)? = null, format: (String) -> String = { it }, onSend: (String) -> Unit) {
    var v by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(D.Raise).border(1.5.dp, D.Gold.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (prefix != null) { Text(prefix, style = body(15.5f, D.Muted)); Spacer(Modifier.width(8.dp)) }
        Box(Modifier.weight(1f).padding(vertical = 8.dp)) {
            if (v.isEmpty()) Text(placeholder, style = body(15.5f, D.Ink.copy(alpha = 0.42f)))
            BasicTextField(v, { v = format(it) }, textStyle = body(15.5f, D.MeInk), cursorBrush = SolidColor(D.Gold), keyboardOptions = keyboard,
                modifier = Modifier.fillMaxWidth().heightIn(max = 140.dp).focusRequester(focus))
        }
        if (valid != null && valid(v)) Icon(Icons.Outlined.Check, null, tint = D.Live, modifier = Modifier.padding(horizontal = 6.dp).size(18.dp))
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(D.Gold).clickable { onSend(v) }, contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Outlined.Send, "Send", tint = D.OnGold, modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
private fun Quiet(label: String, onClick: () -> Unit) {
    Text(label, style = body(13f, D.Ink.copy(alpha = 0.7f)).copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline),
        modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 8.dp))
}

@Composable
private fun ClarifyDock(error: String, onSend: (String) -> Unit, onAsIs: () -> Unit) {
    Column {
        Compose("A little more about it…", KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), onSend = onSend)
        ErrorLine(error)
        Quiet("Send it as it is", onAsIs)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(list: List<Triple<String, Boolean, () -> Unit>>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        list.forEach { (label, main, go) ->
            Box(
                Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(50)).background(if (main) D.Gold else Color.Transparent)
                    .border(1.5.dp, if (main) D.Gold else D.Line2, RoundedCornerShape(50)).clickable(onClick = go).padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, style = body(14f, if (main) D.OnGold else D.Ink, FontWeight.SemiBold)) }
        }
    }
}

@Composable
private fun DocsDock(ui: DeskUi, attach: (() -> Unit)?, onRemove: (Int) -> Unit, onGo: () -> Unit) {
    Column {
        FileChips(ui.files, onRemove)
        val n = ui.files.size
        Chips(listOfNotNull(
            attach?.let { Triple("Attach Documents", false, it) },
            Triple(if (n > 0) "Continue with $n ${if (n == 1) "File" else "Files"}" else "Carry On Without", true, onGo),
        ))
        ErrorLine(ui.error)
    }
}

@Composable
private fun EntityDock(hint: String, onPick: (String) -> Unit) {
    Column {
        Chips(listOf("business" to "Business", "individual" to "Individual", "nri" to "NRI").map { (v, l) -> Triple(l, hint == v) { onPick(v) } })
        Text("If the advocates take the matter on, they will ask for the exact legal form then, not now.", style = body(12f, D.Muted), modifier = Modifier.padding(top = 8.dp, start = 4.dp))
    }
}

@Composable
private fun CountryDock(ui: DeskUi, onPick: (String) -> Unit, onOther: (String) -> Unit) {
    var other by remember { mutableStateOf(false) }
    Column {
        Chips(ui.terms.countries.map { c -> Triple("${c.name}  ${c.dial}", false) { onPick(c.code) } } + Triple("Somewhere else", other) { other = true })
        if (other) {
            Spacer(Modifier.height(10.dp))
            Compose("Dubai, Singapore, Germany…", KeyboardOptions(capitalization = KeyboardCapitalization.Words), onSend = onOther)
        }
        ErrorLine(ui.error)
    }
}

@Composable
private fun PhoneDock(nri: Boolean, dial: String?, required: Boolean, error: String, problem: (String) -> String, onSend: (String) -> Unit, onNone: () -> Unit) {
    Column {
        Compose(
            placeholder = if (nri) "+1 416 555 0123" else "98765 43210",
            keyboard = KeyboardOptions(keyboardType = KeyboardType.Phone),
            prefix = if (nri) null else "+91",
            initial = if (nri && dial != null) "$dial " else "",
            valid = { problem(it).isEmpty() },
            // Shown as it will be dialled: five and five, a leading 91 or 0 dropped.
            format = { raw ->
                if (nri || raw.trimStart().startsWith("+")) raw else {
                    var d = raw.filter { it.isDigit() }
                    if (d.length > 10 && d.startsWith("91")) d = d.drop(2)
                    d = d.trimStart('0').take(10)
                    if (d.length > 5) d.take(5) + " " + d.drop(5) else d
                }
            },
            onSend = onSend,
        )
        ErrorLine(error)
        if (!required) Quiet("I would rather not give a number", onNone)
    }
}
