package com.legumlex.client.feature.board

import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.legumlex.client.ui.theme.Display
import com.legumlex.client.ui.theme.Lex

/*
 * Counsel Home, first steps (REQ-008 7g): while board/home says first_steps.show, the ring and the two steps stand in place of the
 * Specialty Score and the tiles. The server decides what counts as "no cases yet" (show); the app only draws it.
 */

/** board/home `first_steps`: show, how many cases, the balance, and what the trial gave (all optional; absent means today's Home). */
data class FirstSteps(val show: Boolean, val cases: Int, val balance: Int, val trialCredits: Int, val trialGiven: Boolean)

internal fun parseFirstSteps(d: Any?): FirstSteps? {
    val m = d as? Map<*, *> ?: return null
    if (m["show"] != true) return null
    fun n(k: String) = (m[k] as? Number)?.toInt() ?: m[k]?.toString()?.toIntOrNull() ?: 0
    return FirstSteps(true, n("cases"), n("balance"), n("trial_credits"), m["trial_given"] == true)
}

/** The credits step: ticked when there are credits in hand; otherwise the second step to do. */
data class CreditsStep(val done: Boolean, val title: String, val body: String)

private const val PAYS = "Credits pay for the morning eCourts check."

fun creditsStep(fs: FirstSteps): CreditsStep = when {
    fs.balance <= 0 -> CreditsStep(false, "Add Credits", PAYS)
    fs.trialGiven && fs.balance == fs.trialCredits -> CreditsStep(true, "Credits In Hand", "Your ${fs.trialCredits} trial credits are in your account. $PAYS")
    else -> CreditsStep(true, "Credits In Hand", "You have ${fs.balance} credits in your account. $PAYS")
}

/** The ring is half full once credits are in hand: one of the two steps done. */
fun firstStepsProgress(fs: FirstSteps): Float = if (fs.balance > 0) 0.5f else 0f

@Composable
internal fun FirstStepsBlock(fs: FirstSteps, onRefresh: () -> Unit, onCredits: () -> Unit) {
    val context = LocalContext.current
    var importing by rememberSaveable { mutableStateOf(false) }
    var byCnr by rememberSaveable { mutableStateOf(false) }
    val credits = creditsStep(fs)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StepsRing(firstStepsProgress(fs))
            Column(Modifier.weight(1f)) {
                Text("First Steps To Your Specialty Score", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Lex.colors.foreground)
                Text("It appears once your cases are in.", style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground, modifier = Modifier.padding(top = 2.dp))
            }
        }
        StepCard(number = "1", done = false, title = "Bring Your Cases", body = "From the eCourts app's case list or a spreadsheet, or one at a time by CNR.") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StepButton("Import Cases", Icons.Outlined.FileDownload, primary = true) { importing = true }
                StepButton("Add By CNR", Icons.Outlined.QrCode2, primary = false) { byCnr = true }
            }
        }
        StepCard(number = "2", done = credits.done, title = credits.title, body = credits.body) {
            StepButton("Buy Credits", Icons.Outlined.AccountBalanceWallet, primary = false, onClick = onCredits)
        }
    }
    if (importing) ImportWizard(onClose = { importing = false }, onDone = {
        importing = false
        Toast.makeText(context, "${it.added} added, ${it.updated} updated", Toast.LENGTH_LONG).show()
        onRefresh()
    })
    if (byCnr) CnrWizard(onClose = { byCnr = false }, onBuy = { byCnr = false; onCredits() }, onAdded = { _, bal ->
        byCnr = false
        Toast.makeText(context, "Case added. Balance: ${creditsText(bal)} credits.", Toast.LENGTH_LONG).show()
        onRefresh()
    })
}

/** The 54 dp ring: a grey track, and a gold arc as long as the steps done. It turns slowly, and stays still for reduced motion. */
@Composable
private fun StepsRing(progress: Float) {
    val still = rememberReducedMotion()
    val turn by rememberInfiniteTransition(label = "ring").animateFloat(0f, 360f, infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Restart), label = "turn")
    val track = Lex.colors.border
    Canvas(Modifier.size(54.dp).rotate(if (still) 0f else turn)) {
        val w = 6.dp.toPx()
        val inset = w / 2
        val s = Size(size.width - w, size.height - w)
        drawArc(track, 0f, 360f, false, Offset(inset, inset), s, style = Stroke(w))
        if (progress > 0f) drawArc(Color(0xFFF5C518), -90f, 360f * progress, false, Offset(inset, inset), s, style = Stroke(w, cap = StrokeCap.Round))
    }
}

@Composable
private fun StepCard(number: String, done: Boolean, title: String, body: String, actions: @Composable () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(Lex.colors.card).border(1.dp, Lex.colors.border, shape).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(26.dp).clip(CircleShape).background(if (done) Lex.colors.border else Color(0xFF151513)),
                contentAlignment = Alignment.Center,
            ) {
                if (done) Icon(Icons.Outlined.Check, null, tint = Color(0xFF151513), modifier = Modifier.size(14.dp))
                else Text(number, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFFF5C518))
            }
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Lex.colors.foreground)
        }
        Text(body, fontSize = 13.sp, lineHeight = 19.5.sp, color = Lex.colors.secondaryForeground, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(12.dp))
        actions()
    }
}

@Composable
private fun StepButton(text: String, icon: ImageVector, primary: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier.clip(shape)
            .then(if (primary) Modifier.background(Color(0xFF151513)) else Modifier.border(BorderStroke(1.dp, Lex.colors.border), shape))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, tint = if (primary) Color.White else Lex.colors.foreground, modifier = Modifier.size(16.dp))
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = if (primary) Color.White else Lex.colors.foreground)
    }
}
