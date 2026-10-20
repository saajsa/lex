package com.legumlex.client.ahc.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.legumlex.client.ahc.model.AdvocateNote
import com.legumlex.client.ahc.theme.LlColor
import com.legumlex.client.ahc.theme.LlDimen
import com.legumlex.client.ahc.theme.LlShape
import com.legumlex.client.ahc.theme.LlText
import com.legumlex.client.ahc.theme.RiskLevel

/* ============================================================================
 *  RiskHero — visual anchor of the RELEASED state. Score 0–100 mapped to the
 *  risk-level palette (LOW verdant / MEDIUM amber / HIGH·CRITICAL claret),
 *  the level label, and the plain-English teaser_summary.
 * ========================================================================== */
@Composable
fun RiskHero(
    score: Int?,
    level: RiskLevel?,
    teaser: String,
    issuesFlagged: Int,
    clausesReviewed: Int,
    modifier: Modifier = Modifier,
) {
    // A numeric verdict needs both a 0–100 score AND a level. When either is
    // missing (a summary-only / pre-analysis release), fall back to a neutral
    // "Reviewed" hero — never show a bogus number like -1 or a fabricated band.
    val hasVerdict = score != null && score in 0..100 && level != null
    val soft   = level?.soft   ?: LlColor.Paper1
    val border = level?.border ?: LlColor.Border
    Box(
        modifier
            .fillMaxWidth()
            .clip(LlShape.Card)
            .background(soft)
            .border(LlDimen.HairlineWidth, border, LlShape.Card)
            .padding(17.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasVerdict) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$score", style = LlText.heroScore.copy(color = level!!.solid))
                        Text("/ 100", style = LlText.mono.copy(fontSize = 10.sp))
                    }
                    Spacer(Modifier.width(16.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(level?.label ?: "Reviewed", style = LlText.heroVerdict)        // .vhero-vt
                    if (teaser.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(teaser, style = LlText.body.copy(color = LlColor.InkMuted))
                    }
                }
            }
            // The "N issues flagged" recap line only makes sense for a real
            // verdict; suppress it entirely on the neutral fallback.
            if (hasVerdict) {
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(LlDimen.HairlineWidth).background(LlColor.Ink.copy(alpha = 0.08f)))
                Spacer(Modifier.height(11.dp))
                Text(
                    if (clausesReviewed > 0) "$issuesFlagged issues flagged · $clausesReviewed clauses reviewed"
                    else "$issuesFlagged issues flagged",
                    style = LlText.mono.copy(letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified),
                )
            }
        }
    }
}

/* ============================================================================
 *  NoIssuesCard — empty findings "done with dignity": a verdant tick, not a
 *  blank list. Only shown when the advocate released a real verdict (a score)
 *  with zero findings — never on an incomplete / unscored release.
 * ========================================================================== */
@Composable
fun NoIssuesCard(modifier: Modifier = Modifier) {
    LlCardBody(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).background(LlColor.VerdantSoft),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.CheckCircle, null, Modifier.size(20.dp), tint = LlColor.Verdant) }
            Column {
                Text("No issues flagged", style = LlText.bodyTitle.copy(fontSize = 13.sp))
                Spacer(Modifier.height(2.dp))
                Text(
                    "Your advocate confirmed this agreement is clean.",
                    style = LlText.body.copy(fontSize = 11.5.sp),
                )
            }
        }
    }
}

/* ============================================================================
 *  AdvocateNoteCard — distinct, human, signed. Claret left-rule + serif body,
 *  set apart from the machine synopsis. Slate variant for the escalation "why".
 * ========================================================================== */
@Composable
fun AdvocateNoteCard(
    note: AdvocateNote,
    modifier: Modifier = Modifier,
    accent: Color = LlColor.Claret,       // claret on released; slate when escalated
) {
    // Flat quote — a left accent rule + text on paper; no card, shadow or border.
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(accent)) // .anote left rule
        Spacer(Modifier.width(15.dp))
        Column(Modifier.weight(1f)) {
            // Oversized serif open-quote glyph — marks this as a spoken note.
            Text(
                "“",
                style = LlText.heroVerdict.copy(
                    fontSize = 40.sp,
                    lineHeight = 28.sp,
                    color = accent.copy(alpha = 0.35f),
                ),
                modifier = Modifier.padding(bottom = 2.dp),
            )
            note.paragraphs.forEachIndexed { i, p ->
                Text(p, style = LlText.noteBody)
                if (i == 0 && note.bullets.isNotEmpty()) {
                    Spacer(Modifier.height(9.dp))
                    note.bullets.forEach { b ->
                        Row(Modifier.padding(bottom = 6.dp)) {
                            Text("•  ", style = LlText.noteBody)
                            Text(b, style = LlText.noteBody, modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                } else if (i != note.paragraphs.lastIndex) {
                    Spacer(Modifier.height(10.dp))
                }
            }
            Spacer(Modifier.height(13.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, null, Modifier.size(13.dp), tint = accent)
                Spacer(Modifier.width(7.dp))
                Text("${note.signedBy} · ${note.signedAt}", style = LlText.mono)
            }
        }
    }
}

/* ============================================================================
 *  WhatHappensNext — reassuring 3-step timeline for the PENDING state.
 *  Received → Lex reads it → Advocate reviews & releases.
 * ========================================================================== */
data class NextStep(val title: String, val state: StepState)
enum class StepState { DONE, NOW, TODO }

@Composable
fun WhatHappensNext(steps: List<NextStep>, modifier: Modifier = Modifier) {
    LlCardBody(modifier) {
        Column {
            steps.forEachIndexed { i, s ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val (bg, fg, icon) = when (s.state) {
                        StepState.DONE -> Triple(LlColor.Verdant, Color.White, Icons.Default.Check)
                        StepState.NOW  -> Triple(LlColor.Ink, Color.White, Icons.Default.Autorenew)
                        StepState.TODO -> Triple(LlColor.Paper2, LlColor.InkFaint, Icons.Default.RadioButtonUnchecked)
                    }
                    Box(
                        Modifier.size(24.dp).clip(RoundedCornerShape(50)).background(bg),
                        contentAlignment = Alignment.Center,
                    ) { Icon(icon, null, Modifier.size(15.dp), tint = fg) }
                    Spacer(Modifier.width(11.dp))
                    Text(
                        s.title,
                        style = if (s.state == StepState.TODO)
                            LlText.label.copy(fontSize = 12.5.sp, color = LlColor.InkFaint)
                        else LlText.bodyTitle.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Medium),
                        modifier = Modifier.weight(1f),
                    )
                    if (s.state == StepState.NOW) Text("now", style = LlText.mono.copy(fontSize = 9.5.sp))
                }
                if (i != steps.lastIndex) LlDivider()
            }
        }
    }
}

/* ============================================================================
 *  ContextStrip (.strip) — quiet explanatory note. Slate = informational link
 *  to the escalation path; neutral variant available.
 * ========================================================================== */
@Composable
fun ContextStrip(text: String, modifier: Modifier = Modifier, neutral: Boolean = false) {
    val bg = if (neutral) LlColor.Paper1 else LlColor.SlateSoft
    val fg = if (neutral) LlColor.InkMuted else LlColor.SlateMid
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Default.Info, null, Modifier.size(17.dp).padding(top = 1.dp), tint = fg)
        Text(text, style = LlText.body.copy(color = fg, fontSize = 11.5.sp))
    }
}

/* ============================================================================
 *  PartialFailureBlock (.blkerr) — one section failed but the rest is fine.
 *  Dashed border, amber glyph, inline retry. NOT a full-screen error.
 * ========================================================================== */
@Composable
fun PartialFailureBlock(section: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(LlShape.Card)
            .background(LlColor.Surface)
            .border(LlDimen.HairlineWidth, LlColor.BorderStrong, LlShape.Card) // (dashed in mock)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(LlColor.AmberSoft),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.Info, null, Modifier.size(21.dp), tint = LlColor.Amber) }
        Column(Modifier.weight(1f)) {
            Text("$section could not be loaded", style = LlText.bodyTitle.copy(fontSize = 13.sp))
            Spacer(Modifier.height(2.dp))
            Text("Everything else is ready.", style = LlText.body.copy(fontSize = 11.5.sp))
        }
        LlGhostButton("Retry", onRetry, small = true)
    }
}
