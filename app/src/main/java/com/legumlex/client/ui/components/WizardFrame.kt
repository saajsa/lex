package com.legumlex.client.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.legumlex.client.ahc.theme.Inter
import com.legumlex.client.ui.theme.AccentInk
import com.legumlex.client.ui.theme.BodyBg
import com.legumlex.client.ui.theme.BorderColor
import com.legumlex.client.ui.theme.BorderStrong
import com.legumlex.client.ui.theme.InverseText
import com.legumlex.client.ui.theme.PrimaryText
import com.legumlex.client.ui.theme.SecondaryText
import com.legumlex.client.ui.theme.StatusDanger
import com.legumlex.client.ui.theme.Surface1
import com.legumlex.client.ui.theme.Surface2
import com.legumlex.client.ui.theme.TertiaryText

// The app's wizard (owner, 2026-10-24: the staff app's wizard, in this app's
// own Mylex zinc language): a close button and title, the steps named and
// filled up to the current one, the step's body, and Back / Next — the last
// step's button does the step's work. Every multi-step form uses it.

/** Fields and buttons: Mylex rounded-lg; cards: rounded-xl. */
private val FieldShape = RoundedCornerShape(8.dp)
private val CardShape = RoundedCornerShape(12.dp)

@Composable
fun WizardFrame(
    title: String,
    steps: List<String>,
    step: Int,
    ready: Boolean,
    saving: Boolean,
    saveLabel: String,
    error: String?,
    onClose: () -> Unit,
    onStep: (Int) -> Unit,
    onSave: () -> Unit,
    /** What the busy last step says while it works (default "Saving…"). */
    savingLabel: String = "Saving…",
    /** Next on a step other than the last, when the step itself does work (Upload, Send…). */
    nextLabel: String = "Next",
    /** False hides Back / Next, for a step whose body carries its own actions. */
    showActions: Boolean = true,
    /** Overrides Next on any step (a step that must call the server before moving on). */
    onNext: (() -> Unit)? = null,
    body: @Composable ColumnScope.() -> Unit,
) {
    val last = step == steps.lastIndex
    val back: () -> Unit = { if (step == 0) onClose() else onStep(step - 1) }
    BackHandler(onBack = back)

    Column(Modifier.fillMaxSize().background(BodyBg).imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable(onClickLabel = "Close", onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Close, contentDescription = "Close", tint = PrimaryText, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.weight(1f))
            Text(title, color = PrimaryText, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(44.dp))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            steps.forEachIndexed { i, label ->
                Column(Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50))
                        .background(if (i <= step) AccentInk else Surface2))
                    Spacer(Modifier.height(6.dp))
                    Text(label, color = if (i <= step) PrimaryText else TertiaryText, fontFamily = Inter,
                        fontWeight = if (i == step) FontWeight.SemiBold else FontWeight.Normal, fontSize = 11.5.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 16.dp),
        ) {
            body()
            error?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, color = StatusDanger.solid, fontFamily = Inter, fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
        if (showActions) Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 20.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.height(52.dp).clip(FieldShape).background(Surface2).clickable(onClick = back)
                    .padding(horizontal = 22.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (step == 0) "Cancel" else "Back", color = PrimaryText, fontFamily = Inter,
                    fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
            Row(
                Modifier.weight(1f).height(52.dp).clip(FieldShape)
                    .background(if (ready && !saving) AccentInk else Surface2)
                    .clickable(enabled = ready && !saving) {
                        when {
                            onNext != null -> onNext()
                            last -> onSave()
                            else -> onStep(step + 1)
                        }
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val ink = if (ready && !saving) InverseText else TertiaryText
                if (saving) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = TertiaryText)
                    Spacer(Modifier.width(10.dp))
                }
                Text(if (saving) savingLabel else if (last) saveLabel else nextLabel, color = ink, fontFamily = Inter,
                    fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                if (!last && !saving) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun WizardHeading(title: String, sub: String) {
    Text(title, color = PrimaryText, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 26.sp,
        lineHeight = 31.sp, letterSpacing = (-0.6).sp)
    if (sub.isNotBlank()) {
        Spacer(Modifier.height(6.dp))
        Text(sub, color = SecondaryText, fontFamily = Inter, fontSize = 14.sp, lineHeight = 20.sp)
    }
    Spacer(Modifier.height(22.dp))
}

@Composable
fun WizardLabel(text: String) {
    Text(text, color = SecondaryText, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.sp)
    Spacer(Modifier.height(8.dp))
}

/**
 * A dropdown field: the label above, the chosen option (or a placeholder) in
 * an outlined box with a chevron, and the options in a menu as wide as it.
 * Options are value to label; a sub-line per option shows in the menu only.
 */
@Composable
fun WizardDropdown(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    placeholder: String = "Select",
    subs: Map<String, String> = emptyMap(),
    disabled: Set<String> = emptySet(),
    onSelect: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val chosen = options.firstOrNull { it.first == selected }?.second
    var fieldWidth by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    WizardLabel(label)
    Box(Modifier.fillMaxWidth().onGloballyPositioned { fieldWidth = it.size.width }) {
        Row(
            Modifier.fillMaxWidth().clip(FieldShape).background(Surface1)
                .border(1.dp, if (open) PrimaryText else BorderStrong, FieldShape)
                .clickable { open = true }.padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(chosen ?: placeholder, color = if (chosen != null) PrimaryText else TertiaryText, fontFamily = Inter,
                fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null,
                tint = SecondaryText, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(
            expanded = open, onDismissRequest = { open = false },
            modifier = Modifier.width(with(density) { fieldWidth.toDp() }).background(Surface1),
        ) {
            options.forEach { (value, text) ->
                val off = value in disabled
                DropdownMenuItem(
                    enabled = !off,
                    text = {
                        Column {
                            Text(text, color = if (off) TertiaryText else PrimaryText, fontFamily = Inter, fontSize = 15.sp,
                                fontWeight = if (value == selected) FontWeight.SemiBold else FontWeight.Normal)
                            subs[value]?.takeIf { it.isNotBlank() }?.let {
                                Text(it, color = SecondaryText, fontFamily = Inter, fontSize = 12.sp)
                            }
                        }
                    },
                    trailingIcon = if (value == selected) {
                        { Icon(Icons.Outlined.Check, contentDescription = null, tint = PrimaryText, modifier = Modifier.size(18.dp)) }
                    } else null,
                    onClick = { open = false; onSelect(value) },
                )
            }
        }
    }
    Spacer(Modifier.height(14.dp))
}

/** One option to pick: ink when chosen, outlined otherwise. */
@Composable
fun WizardChoice(label: String, on: Boolean, sub: String = "", enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.padding(bottom = 8.dp).fillMaxWidth().clip(FieldShape)
            .background(if (on) AccentInk else Surface1)
            .then(if (on) Modifier else Modifier.border(1.dp, BorderStrong, FieldShape))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = if (on) InverseText else if (enabled) PrimaryText else TertiaryText,
                fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            if (sub.isNotBlank()) Text(sub, color = if (on) InverseText.copy(alpha = 0.7f) else SecondaryText,
                fontFamily = Inter, fontSize = 12.5.sp, lineHeight = 17.sp)
        }
        if (on) Icon(Icons.Outlined.Check, contentDescription = null, tint = InverseText, modifier = Modifier.size(18.dp))
    }
}

/** A several-line text box, in the field's outline. */
@Composable
fun WizardTextArea(value: String, onChange: (String) -> Unit, placeholder: String, maxChars: Int = 2000, minHeight: Int = 120) {
    Box(
        Modifier.fillMaxWidth().heightIn(min = minHeight.dp).clip(FieldShape).background(Surface1)
            .border(1.dp, BorderStrong, FieldShape).padding(16.dp),
    ) {
        if (value.isEmpty()) Text(placeholder, color = TertiaryText, fontFamily = Inter, fontSize = 15.sp)
        BasicTextField(
            value = value, onValueChange = { onChange(it.take(maxChars)) },
            textStyle = TextStyle(color = PrimaryText, fontFamily = Inter, fontSize = 15.sp, lineHeight = 22.sp),
            cursorBrush = SolidColor(PrimaryText), modifier = Modifier.fillMaxWidth().heightIn(min = (minHeight - 32).dp),
        )
    }
}

/** The last step's summary: a heading, then label and value rows in a card. */
@Composable
fun WizardReview(title: String = "Check and send", note: String, rows: List<Pair<String, String>>) {
    WizardHeading(title, note)
    WizardRows(rows)
}

/** Label and value rows in a card, for a review or a summary. */
@Composable
fun WizardRows(rows: List<Pair<String, String>>) {
    Column(
        Modifier.fillMaxWidth().clip(CardShape).background(Surface1)
            .border(1.dp, BorderColor, CardShape).padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        rows.forEach { (k, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Text(k, color = SecondaryText, fontFamily = Inter, fontSize = 14.sp, modifier = Modifier.width(118.dp))
                Text(v.ifBlank { "—" }, color = PrimaryText, fontFamily = Inter, fontWeight = FontWeight.Medium,
                    fontSize = 14.sp, modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * A text field for a wizard step: the label above, the value in the field's
 * outline, and a help line (or the error, in red) beneath. [locked] shows the
 * value as read and not to be changed, on the muted fill with a lock.
 */
@Composable
fun WizardTextField(
    label: String,
    value: String,
    onChange: (String) -> Unit = {},
    help: String = "",
    error: String = "",
    locked: Boolean = false,
    enabled: Boolean = true,
    prefix: String = "",
    placeholder: String = "",
    keyboardType: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
    lines: Int = 1,
) {
    WizardLabel(label)
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(FieldShape).background(if (locked || !enabled) Surface2 else Surface1)
            .border(1.dp, when { error.isNotBlank() -> StatusDanger.solid; focused -> PrimaryText; locked -> BorderColor; else -> BorderStrong }, FieldShape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = if (lines > 1) Alignment.Top else Alignment.CenterVertically,
    ) {
        if (prefix.isNotBlank()) {
            Text(prefix, color = SecondaryText, fontFamily = Inter, fontSize = 15.sp)
            Spacer(Modifier.width(6.dp))
        }
        Box(Modifier.weight(1f)) {
            if (value.isEmpty() && placeholder.isNotBlank()) Text(placeholder, color = TertiaryText, fontFamily = Inter, fontSize = 15.sp)
            BasicTextField(
                value = value, onValueChange = onChange, readOnly = locked, enabled = enabled && !locked,
                singleLine = lines == 1, minLines = lines,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
                textStyle = TextStyle(color = if (locked) SecondaryText else PrimaryText, fontFamily = Inter, fontSize = 15.sp, lineHeight = 22.sp),
                cursorBrush = SolidColor(PrimaryText),
                modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
            )
        }
        if (locked) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Outlined.Lock, contentDescription = "Read from the documents", tint = TertiaryText, modifier = Modifier.size(16.dp))
        }
    }
    val line = error.ifBlank { help }
    if (line.isNotBlank()) {
        Spacer(Modifier.height(6.dp))
        Text(line, color = if (error.isNotBlank()) StatusDanger.solid else SecondaryText, fontFamily = Inter, fontSize = 12.5.sp, lineHeight = 17.sp)
    }
    Spacer(Modifier.height(16.dp))
}

/** A box to tick, with its sentence beside it (a consent, a confirmation). */
@Composable
fun WizardTick(checked: Boolean, text: String, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(FieldShape).clickable { onChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.padding(top = 2.dp).size(22.dp).clip(RoundedCornerShape(5.dp))
                .background(if (checked) AccentInk else Surface1)
                .then(if (checked) Modifier else Modifier.border(1.5.dp, BorderStrong, RoundedCornerShape(5.dp))),
            contentAlignment = Alignment.Center,
        ) { if (checked) Icon(Icons.Outlined.Check, contentDescription = null, tint = InverseText, modifier = Modifier.size(16.dp)) }
        Spacer(Modifier.width(12.dp))
        Text(text, color = PrimaryText, fontFamily = Inter, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
    }
}

/**
 * A wizard opened over a screen (New Task, Verify Mobile): a full-screen
 * dialog whose window is drawn edge to edge, undimmed, with the page's
 * background under the status and navigation bars, and the frame kept clear
 * of both.
 */
@Composable
fun WizardDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    // A dialog's window is told no insets, and where it ends varies by device:
    // the page's own screen and navigation bar, measured here, set how far the
    // dialog runs past the bar, and the frame is padded by that much.
    val density = LocalDensity.current
    val navBottom = androidx.compose.foundation.layout.WindowInsets.systemBars.getBottom(density)
    val screen = androidx.compose.ui.platform.LocalView.current.rootView.let { v ->
        IntArray(2).also { v.getLocationOnScreen(it) }[1] + v.height
    }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val window = (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
        val dark = com.legumlex.client.ui.theme.Lex.colors.isDark
        androidx.compose.runtime.SideEffect {
            window?.let { w ->
                // Android keeps a dialog under the status bar, whatever it asks for;
                // undimmed, the page's own head shows above it. Its foot is drawn
                // under the navigation bar, so the frame is padded clear of that.
                w.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                w.setDimAmount(0f)
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(w, false)
                @Suppress("DEPRECATION")
                run {
                    w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                    w.statusBarColor = android.graphics.Color.TRANSPARENT
                    w.navigationBarColor = android.graphics.Color.TRANSPARENT
                }
                androidx.core.view.WindowCompat.getInsetsController(w, w.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }
        var over by remember { mutableStateOf(0) }
        Box(
            Modifier.fillMaxSize()
                .onGloballyPositioned { c -> over = ((c.positionOnScreen().y + c.size.height) - (screen - navBottom)).toInt().coerceAtLeast(0) }
                .background(BodyBg).padding(bottom = with(density) { over.toDp() }),
        ) { content() }
    }
}
