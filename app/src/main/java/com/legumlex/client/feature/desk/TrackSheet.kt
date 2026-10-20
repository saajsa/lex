package com.legumlex.client.feature.desk

import com.legumlex.client.ui.theme.Lex
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.legumlex.client.ahc.theme.Inter
import kotlinx.coroutines.launch

/* ============================================================================
 *  Track a notice: the website's header modal (site/partials/modals, m-track,
 *  with partials/track_form and site.js §7), on the site's own public tracker
 *  (Landing::ajax_track). Only the tracking reference is taken, as there.
 * ========================================================================== */

// The app's palette, as the desk itself now uses (owner, 2026-10-20).
private val Page: Color   @Composable get() = Lex.colors.background
private val Ink: Color    @Composable get() = Lex.colors.foreground
private val Muted: Color  @Composable get() = Lex.colors.mutedForeground
private val Line2: Color  @Composable get() = Lex.colors.input
private val Raise: Color  @Composable get() = Lex.colors.card
private val Gold: Color   @Composable get() = Lex.colors.primary
private val OnGold: Color @Composable get() = Lex.colors.primaryForeground
private val Bad: Color    @Composable get() = Lex.colors.danger.solid

@Composable
private fun t(size: Float, color: Color = Ink, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = Inter, fontSize = size.sp, lineHeight = (size * 1.5f).sp, color = color, fontWeight = weight)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackSheet(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { DeskRepository(ctx) }
    val scope = rememberCoroutineScope()
    var ref by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var out by remember { mutableStateOf<Tracked?>(null) }

    val track: () -> Unit = {
        val r = ref.trim()
        if (r.isNotEmpty() && !busy) {
            busy = true; msg = ""
            scope.launch {
                repo.track(r).onSuccess { out = it }.onFailure { e ->
                    out = null
                    msg = if (e is TrackMiss) e.message.orEmpty() else "The tracker is not answering just now. Please try again."
                }
                busy = false
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Page, dragHandle = null) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(start = 22.dp, end = 10.dp, top = 10.dp, bottom = 22.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f).padding(top = 14.dp)) {
                    Text("LEGAL NOTICES", style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.5.sp, letterSpacing = 1.2.sp, color = Gold))
                    Spacer(Modifier.height(6.dp))
                    Text("Track a notice", style = t(24f, weight = FontWeight.SemiBold))
                    Spacer(Modifier.height(4.dp))
                    Text("The tracking reference in the email sent when your notice was dispatched.", style = t(14f, Muted))
                }
                Box(Modifier.size(44.dp).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Close, "Close", tint = Muted, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).background(Raise)
                        .border(1.dp, Line2, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (ref.isEmpty()) Text("Tracking reference", style = t(15f, Ink.copy(alpha = 0.42f)))
                    BasicTextField(ref, { ref = it.take(40) }, singleLine = true, textStyle = t(15f).copy(fontFamily = FontFamily.Monospace),
                        cursorBrush = SolidColor(Gold),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { track() }), modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).background(Gold).clickable(onClick = track).padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center) {
                    Text(if (busy) "Tracking…" else "Track", style = t(14.5f, OnGold, FontWeight.SemiBold))
                }
            }
            if (msg.isNotEmpty()) Text(msg, style = t(12.5f, Muted), modifier = Modifier.padding(top = 8.dp))
            out?.let { d -> Result(d) }
        }
    }
}

@Composable
private fun Result(d: Tracked) {
    val bad = Regex("Returned|Refused|Undeliverable", RegexOption.IGNORE_CASE).containsMatchIn(d.deliveryStatus.orEmpty())
    Spacer(Modifier.height(18.dp))
    Row(Modifier.padding(end = 12.dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TrackStep("Dispatched", d.dispatched ?: "Not yet", d.isDispatched, false, Modifier.weight(1f).fillMaxHeight())
        TrackStep("Delivered", d.delivered ?: if (bad) d.deliveryStatus.orEmpty() else "Not yet", d.isDelivered, !d.isDelivered && bad, Modifier.weight(1f).fillMaxHeight())
        TrackStep("Waiting period",
            d.waitingEnd?.let { if ((d.daysRemaining ?: 0) > 0) "${d.daysRemaining} days left" else "Ended $it" } ?: "Not yet",
            d.waitingEnd != null, false, Modifier.weight(1f).fillMaxHeight())
    }
    Spacer(Modifier.height(12.dp))
    Text(
        (if (d.isDemo) "Example notice. " else "") + "Notice " + d.reference + (d.method?.let { " · $it" } ?: "") +
            (d.tracker?.takeIf { it != d.reference }?.let { " · tracking $it" } ?: "") + (if (d.converted) " · now a case" else ""),
        style = t(12.5f, Muted), modifier = Modifier.padding(end = 12.dp),
    )
}

@Composable
private fun TrackStep(label: String, value: String, done: Boolean, bad: Boolean, modifier: Modifier) {
    val tone = if (bad) Bad else if (done) Gold else Line2
    Column(modifier.clip(RoundedCornerShape(12.dp)).border(1.dp, if (done || bad) tone.copy(alpha = 0.6f) else Line2, RoundedCornerShape(12.dp)).padding(12.dp)) {
        Box(Modifier.size(18.dp).clip(CircleShape).background(if (done) Gold else Color.Transparent).border(1.5.dp, tone, CircleShape), contentAlignment = Alignment.Center) {
            if (done) Icon(Icons.Outlined.Check, null, tint = OnGold, modifier = Modifier.size(12.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, style = t(12f, Muted))
        Text(value, style = t(13.5f, if (bad) Bad else Ink, FontWeight.SemiBold))
    }
}
