package com.legumlex.client.ahc.components

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.legumlex.client.ahc.model.ActivityEntry
import com.legumlex.client.ahc.model.DotState
import com.legumlex.client.ahc.model.Finding
import com.legumlex.client.ahc.model.KeyTerm
import com.legumlex.client.ahc.model.Party
import com.legumlex.client.ahc.model.SubmittedDoc
import com.legumlex.client.ahc.theme.LlColor
import com.legumlex.client.ahc.theme.LlDimen
import com.legumlex.client.ahc.theme.LlShape
import com.legumlex.client.ahc.theme.LlText

/* ============================================================================
 *  Signal components — the skimmable lists & rows shared across released /
 *  pending / escalated. Color is meaning only; body stays ink-on-paper.
 * ========================================================================== */

/* ---- Findings list (.finding) --------------------------------------------
 * Used for BOTH "Key risks" and "Missing protections". Left stripe color =
 * FindingSeverity (claret / amber / verdant). Each row tappable → deep link.
 */
@Composable
fun FindingList(
    findings: List<Finding>,
    onClick: (Finding) -> Unit,
    modifier: Modifier = Modifier,
) {
    LlCard(modifier) {
        Column {
            findings.forEachIndexed { i, f ->
                FindingRow(f, onClick)
                if (i != findings.lastIndex) LlDivider()
            }
        }
    }
}

@Composable
private fun FindingRow(finding: Finding, onClick: (Finding) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick(finding) }
            .padding(end = 14.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // .stripe — 3px severity bar, full row height, rounded outer edge
        Box(
            Modifier
                .width(3.dp)
                .height(38.dp)
                .clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))
                .background(finding.severity.stripe),   // FindingSeverity palette
        )
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(finding.title, style = LlText.bodyTitle)                 // .finding-t
            Spacer(Modifier.height(3.dp))
            Text(finding.detail, style = LlText.body.copy(fontSize = 11.5.sp, lineHeight = 16.sp)) // .finding-x
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, null, Modifier.size(14.dp), tint = LlColor.InkFaint)
    }
}

/* ---- Recommended next steps (checklist) ---------------------------------- */
@Composable
fun RecommendationList(items: List<String>, modifier: Modifier = Modifier) {
    LlCard(modifier) {
        Column(Modifier.padding(horizontal = LlDimen.CardPad, vertical = 6.dp)) {
            items.forEachIndexed { i, line ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    // verdant check token — positive forward action
                    Box(
                        Modifier.size(20.dp).clip(RoundedCornerShape(50))
                            .background(LlColor.VerdantSoft),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.Check, null, Modifier.size(13.dp), tint = LlColor.Verdant) }
                    Spacer(Modifier.width(11.dp))
                    Text(line, style = LlText.body.copy(color = LlColor.Ink), modifier = Modifier.weight(1f))
                }
                if (i != items.lastIndex) LlDivider()
            }
        }
    }
}

/* ---- Parties & key terms (.kv) -------------------------------------------- */
@Composable
fun KeyTermsCard(parties: List<Party>, terms: List<KeyTerm>, modifier: Modifier = Modifier) {
    LlCardBody(modifier) {
        Column {
            // party chips
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                parties.forEach { p ->
                    Row(
                        Modifier.clip(LlShape.Button)
                            .background(LlColor.Paper1)
                            .border(LlDimen.HairlineWidth, LlColor.Border, LlShape.Button)
                            .padding(horizontal = 11.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(p.role.uppercase(), style = LlText.eyebrow.copy(fontSize = 9.sp))
                        Text(p.name, style = LlText.bodyTitle.copy(fontSize = 11.5.sp))
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            terms.forEachIndexed { i, t ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(t.key, style = LlText.label)                    // .kv-k
                    Spacer(Modifier.width(14.dp))
                    Text(
                        t.value,
                        style = if (t.emphasis)
                            LlText.countdownNum.copy(fontSize = 15.sp)   // .kv-v.big (mono ₹)
                        else LlText.bodyTitle.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                    )
                }
                if (i != terms.lastIndex) LlDivider()
            }
        }
    }
}

/* ---- Activity timeline (.tl) ---------------------------------------------- */
@Composable
fun ActivityTimeline(entries: List<ActivityEntry>, modifier: Modifier = Modifier) {
    LlCardBody(modifier) {
        Column {
            entries.forEachIndexed { i, e ->
                Row {
                    // dot + connector column
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(11.dp)) {
                        val dotColor = when (e.state) {
                            DotState.DONE    -> LlColor.Verdant     // verdant = done
                            DotState.CURRENT -> LlColor.Ink         // ink = now
                            DotState.TODO    -> LlColor.BorderStrong
                        }
                        Spacer(Modifier.height(3.dp))
                        Box(Modifier.size(9.dp).clip(RoundedCornerShape(50)).background(dotColor))
                        if (i != entries.lastIndex) {
                            Box(Modifier.width(1.5.dp).height(28.dp).background(LlColor.Paper2))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f).padding(bottom = if (i != entries.lastIndex) 14.dp else 0.dp)) {
                        Text(e.title, style = LlText.bodyTitle.copy(fontSize = 12.sp))   // .tl-t
                        Spacer(Modifier.height(2.dp))
                        Text(e.timestamp, style = LlText.monoStamp)                       // .tl-m
                    }
                }
            }
        }
    }
}

/* ---- "What you submitted" doc row (.docrow) ------------------------------- */
@Composable
fun SubmittedDocRow(
    doc: SubmittedDoc,
    onPreview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LlCard(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (doc.hasDocument) Modifier.clickable { onPreview() } else Modifier)
                .padding(horizontal = 14.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(LlColor.Paper2),
                contentAlignment = Alignment.Center,
            ) { DocGlyph(LlColor.InkMuted) }
            Column(Modifier.weight(1f)) {
                Text(doc.fileName, style = LlText.bodyTitle.copy(fontSize = 12.5.sp), maxLines = 1)
                // Only show metadata we actually have — never "· 0 pages".
                val meta = buildList {
                    if (doc.sizeLabel.isNotBlank()) add(doc.sizeLabel)
                    if (doc.pageCount > 0) add(if (doc.pageCount == 1) "1 page" else "${doc.pageCount} pages")
                }.joinToString(" · ")
                if (meta.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(meta, style = LlText.mono.copy(fontSize = 10.sp))
                }
            }
            if (doc.hasDocument) {
                Icon(Icons.Default.Visibility, "Preview", Modifier.size(18.dp), tint = LlColor.InkFaint)
            }
        }
    }
}

/** Small "document" glyph stand-in (Material Symbols `draft` in the mock). */
@Composable
fun DocGlyph(tint: Color, sizeDp: Int = 18) {
    // Description icon is the closest stock Material equivalent to `draft`.
    Icon(Icons.Default.Description, null, Modifier.size(sizeDp.dp), tint = tint)
}
