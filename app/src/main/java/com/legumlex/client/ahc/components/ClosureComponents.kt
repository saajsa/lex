package com.legumlex.client.ahc.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.legumlex.client.ahc.model.ClosureKind
import com.legumlex.client.ahc.model.ClosureLink
import com.legumlex.client.ahc.model.ClosureLinkTarget
import com.legumlex.client.ahc.model.RecapRow
import com.legumlex.client.ahc.theme.LlColor
import com.legumlex.client.ahc.theme.LlText

/* ============================================================================
 *  Closure components — the terminal "Closed" layouts (.closehero / recap /
 *  linkcard). Subdued, ink-on-paper, no severity stripes. A visible ending.
 * ========================================================================== */

private data class ClosurePalette(val bg: Color, val fg: Color, val icon: ImageVector)

private fun closurePalette(kind: ClosureKind): ClosurePalette = when (kind) {
    ClosureKind.COMPLETED               -> ClosurePalette(LlColor.VerdantSoft, LlColor.Verdant, Icons.Default.CheckCircle)
    ClosureKind.RESOLVED_VIA_CONSULTATION -> ClosurePalette(LlColor.SlateSoft, LlColor.SlateMid, Icons.Default.Handshake)
    ClosureKind.CONVERTED_TO_CASE       -> ClosurePalette(LlColor.Paper2, LlColor.Ink, Icons.Default.Gavel)
    ClosureKind.CANCELLED               -> ClosurePalette(LlColor.Paper2, LlColor.InkMuted, Icons.Default.Cancel)
    ClosureKind.OTHER                   -> ClosurePalette(LlColor.Paper2, LlColor.InkMuted, Icons.Default.Lock)
}

/** .closehero — centred icon + serif title + subtitle + "Closed on" stamp. */
@Composable
fun CloseHero(
    kind: ClosureKind,
    title: String,
    subtitle: String,
    closedOnLabel: String,
    modifier: Modifier = Modifier,
) {
    val p = closurePalette(kind)
    Column(
        modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(top = 28.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(58.dp).clip(RoundedCornerShape(16.dp)).background(p.bg),
            contentAlignment = Alignment.Center,
        ) { Icon(p.icon, null, Modifier.size(29.dp), tint = p.fg) }
        Spacer(Modifier.height(16.dp))
        Text(title, style = LlText.emptyTitle.copy(fontSize = 20.sp), textAlign = TextAlign.Center)
        Spacer(Modifier.height(7.dp))
        Text(
            subtitle,
            style = LlText.body.copy(color = LlColor.InkMuted),
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 282.dp),
        )
        if (closedOnLabel.isNotBlank()) {
            Spacer(Modifier.height(13.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, null, Modifier.size(14.dp), tint = LlColor.InkFaint)
                Spacer(Modifier.width(6.dp))
                Text("Closed on $closedOnLabel", style = LlText.mono)
            }
        }
    }
}

/** .dcard with .kv rows — labelled recap summary. */
@Composable
fun RecapCard(rows: List<RecapRow>, modifier: Modifier = Modifier) {
    LlCardBody(modifier) {
        Column {
            rows.forEachIndexed { i, r ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 9.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(r.key, style = LlText.label.copy(fontSize = 11.5.sp))
                    Spacer(Modifier.width(14.dp))
                    Text(
                        r.value,
                        style = if (r.mono) LlText.mono.copy(color = LlColor.Ink, fontSize = 12.5.sp)
                        else LlText.bodyTitle.copy(fontSize = 12.5.sp),
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (i != rows.lastIndex) LlDivider()
            }
        }
    }
}

/** .linkcard — tappable link to the consultation / case that resolved this. */
@Composable
fun LinkCard(link: ClosureLink, onClick: () -> Unit, modifier: Modifier = Modifier) {
    LlCard(modifier) {
        Row(
            Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 14.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(LlColor.Paper2),
                contentAlignment = Alignment.Center,
            ) {
                val icon = if (link.target == ClosureLinkTarget.CASE) Icons.Default.Gavel else Icons.Default.Forum
                Icon(icon, null, Modifier.size(20.dp), tint = LlColor.InkMuted)
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(link.title, style = LlText.bodyTitle.copy(fontSize = 12.5.sp))
                    Text(link.reference, style = LlText.mono.copy(fontSize = 11.sp))
                }
                if (link.meta.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(link.meta, style = LlText.body.copy(fontSize = 11.sp))
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp), tint = LlColor.InkFaint)
        }
    }
}
