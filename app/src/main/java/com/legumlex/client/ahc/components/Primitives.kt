package com.legumlex.client.ahc.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.legumlex.client.ahc.model.Advocate
import com.legumlex.client.ahc.theme.LlColor
import com.legumlex.client.ahc.theme.LlDimen
import com.legumlex.client.ahc.theme.LlShape
import com.legumlex.client.ahc.theme.LlText

/* ============================================================================
 *  Shared primitives — the building blocks every AHC state reuses.
 *  All match the HTML look: white surface, 1px subtle border, restrained
 *  elevation, ink-on-paper. NOTE: Material Icons stand in for the Material
 *  Symbols glyphs used in the web mock — closest-equivalent names are noted.
 * ========================================================================== */

/** .dcard / .card — white surface, 1px --border, very soft shadow. */
@Composable
fun LlCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .shadow(1.dp, LlShape.Card, clip = false)   // --shadow (restrained)
            .clip(LlShape.Card)
            .background(LlColor.Surface)                  // Surface (#FFFFFF)
            .border(LlDimen.HairlineWidth, LlColor.Border, LlShape.Card), // --border
    ) { content() }
}

/** Padded card body == .dcard-b. */
@Composable
fun LlCardBody(
    modifier: Modifier = Modifier,
    pad: PaddingValues = PaddingValues(horizontal = LlDimen.CardPad, vertical = 14.dp),
    content: @Composable () -> Unit,
) {
    LlCard(modifier) { Box(Modifier.padding(pad)) { content() } }
}

/** .dsec-head — uppercase section label + optional trailing link. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 0.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title.uppercase(), style = LlText.sectionHeader)   // .dsec-t
        trailing?.invoke()
    }
}

/** Thin divider == 1px --paper2 hairline used inside cards. */
@Composable
fun LlDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(LlDimen.HairlineWidth).background(LlColor.Paper2))
}

/* ---- Buttons -------------------------------------------------------------- */

private enum class BtnKind { INK, GHOST }

@Composable
private fun LlButtonBase(
    text: String,
    kind: BtnKind,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    small: Boolean = false,
    enabled: Boolean = true,
) {
    val bg = if (kind == BtnKind.INK) LlColor.Ink else LlColor.Surface
    val fg = if (kind == BtnKind.INK) LlColor.Paper else LlColor.Ink
    val border = if (kind == BtnKind.GHOST)
        BorderStroke(LlDimen.HairlineWidth, LlColor.Border) else null
    val vPad = if (small) 9.dp else 11.dp
    val hPad = if (small) 13.dp else 16.dp

    Row(
        modifier
            .heightIn(min = LlDimen.TouchTarget)             // 44dp hit target
            .clip(LlShape.Button)
            .background(if (enabled) bg else bg.copy(alpha = 0.4f))
            .then(if (border != null) Modifier.border(border, LlShape.Button) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = hPad, vertical = vPad),
        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) Icon(leadingIcon, null, Modifier.size(16.dp), tint = fg)
        Text(
            text.uppercase(),
            style = LlText.button.copy(color = fg, fontSize = if (small) 10.5.sp else 11.5.sp),
        )
    }
}

/** .btn-ink — primary action (ink fill, paper text). */
@Composable
fun LlPrimaryButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null, small: Boolean = false, enabled: Boolean = true,
) = LlButtonBase(text, BtnKind.INK, onClick, modifier, leadingIcon, small, enabled)

/** .btn-ghost — secondary action (white fill, 1px border). */
@Composable
fun LlGhostButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null, small: Boolean = false, enabled: Boolean = true,
) = LlButtonBase(text, BtnKind.GHOST, onClick, modifier, leadingIcon, small, enabled)

/* ---- Status chip / kind chip ---------------------------------------------- */

/** .chip — soft-bg status pill with a leading dot. Pass any status palette. */
@Composable
fun StatusChip(
    label: String,
    softBg: Color,
    text: Color,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
) {
    Row(
        modifier
            .clip(LlShape.Chip)
            .background(softBg)
            .padding(horizontal = 9.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) Icon(leadingIcon, null, Modifier.size(13.dp), tint = text)
        else Box(Modifier.size(5.dp).clip(RoundedCornerShape(50)).background(text))
        Text(label, style = LlText.eyebrow.copy(color = text, letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified))
    }
}

/* ---- Square-rounded avatar (.av) ------------------------------------------ */

@Composable
fun LlAvatar(
    initials: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 44.dp,
    bg: Color = LlColor.Slate,        // .av default slate; .av.ink for ink
    avatarUrl: String? = null,        // staff profile image; overlays the initials
) {
    val shape = RoundedCornerShape(size * 0.27f)  // ~12dp at 44dp
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        // Initials are the always-present fallback layer; the photo overlays it
        // once loaded, so a missing/slow/broken image degrades to initials.
        Text(
            initials,
            style = LlText.heroVerdict.copy(color = Color.White, fontSize = (size.value * 0.34f).sp),
        )
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().clip(shape),
            )
        }
    }
}

/* ---- Top app bar (.appbar) ------------------------------------------------ */

/** Back + agreement name (truncating) + optional overflow + status eyebrow. */
@Composable
fun AhcTopBar(
    title: String,
    onBack: () -> Unit,
    onOverflow: () -> Unit,
    modifier: Modifier = Modifier,
    eyebrow: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 8.dp, top = 14.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.Top) {
            IconButtonSquare(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
            Spacer(Modifier.width(2.dp))
            Column(Modifier.padding(top = 1.dp)) {
                eyebrow?.invoke()
                if (eyebrow != null) Spacer(Modifier.height(7.dp))
                Text(
                    title,                                   // .appbar-title (truncating)
                    style = LlText.appBarTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButtonSquare(Icons.Default.MoreVert, "More", onOverflow, tint = LlColor.InkMuted)
    }
}

/** .icbtn — 38dp transparent square icon button. */
@Composable
fun IconButtonSquare(
    icon: ImageVector,
    contentDesc: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LlColor.Ink,
) {
    Box(
        modifier
            .size(LlDimen.TouchTarget)                       // 44dp hit target (visual 38)
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDesc, Modifier.size(22.dp), tint = tint) }
}

/* ---- Advocate row (.advc) ------------------------------------------------- */

@Composable
fun AdvocateRow(
    advocate: Advocate,
    onMessage: () -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,        // .advc.ro — closed: lock chip instead of Message
) {
    LlCard(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = LlDimen.CardPad, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LlAvatar(advocate.initials, size = 44.dp, bg = LlColor.Slate, avatarUrl = advocate.avatarUrl)
            Column(Modifier.weight(1f)) {
                Text(advocate.name, style = LlText.bodyTitle)            // .advc-n
                Spacer(Modifier.height(2.dp))
                Text(advocate.role, style = LlText.label.copy(fontSize = 11.sp)) // .advc-d
            }
            if (readOnly) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(Icons.Default.Lock, null, Modifier.size(13.dp), tint = LlColor.InkFaint)
                    Text("Closed", style = LlText.mono.copy(fontSize = 10.sp))
                }
            } else {
                LlPrimaryButton("Message", onMessage, leadingIcon = Icons.AutoMirrored.Filled.Send, small = true)
            }
        }
    }
}
