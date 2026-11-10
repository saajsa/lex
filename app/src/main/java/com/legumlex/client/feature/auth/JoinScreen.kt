package com.legumlex.client.feature.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.legumlex.client.data.api.ApiClient
import com.legumlex.client.ui.brand.LegumMark
import com.legumlex.client.ui.theme.AccentInk
import com.legumlex.client.ui.theme.BorderColor
import com.legumlex.client.ui.theme.Display
import com.legumlex.client.ui.theme.InverseText
import com.legumlex.client.ui.theme.Lex
import com.legumlex.client.ui.theme.LocalSpacing
import com.legumlex.client.ui.theme.PrimaryText
import com.legumlex.client.ui.theme.SecondaryText
import com.legumlex.client.ui.theme.grain

/** The two ways in on Join (REQ-013). */
enum class JoinChoice { Client, Advocate }

/** What a choice does: the client opens an account (Onboard Now), the advocate registers as counsel. */
internal fun runJoin(choice: JoinChoice, onOpenAccount: () -> Unit, onRegisterCounsel: () -> Unit) = when (choice) {
    JoinChoice.Client -> onOpenAccount()
    JoinChoice.Advocate -> onRegisterCounsel()
}

/** Join is reachable only while the server lets newcomers open an account (google_config's sign_up). */
internal fun joinReachable(signUp: Boolean?): Boolean = signUp == true

const val ONBOARD_NOW_LINE = "Your PAN, Aadhaar and mobile number. The firm approves every new account."

/**
 * Join (REQ-013), opened by Discover on Sign In: large type, two choices (a tap selects one; "Join In" acts on the
 * selected one), the monogram at the foot. Back returns to Sign In. If sign-up is switched off it is not shown.
 */
@Composable
fun JoinScreen(onBack: () -> Unit, onOpenAccount: () -> Unit, onRegisterCounsel: () -> Unit) {
    val context = LocalContext.current
    val spacing = LocalSpacing.current
    // Asked as Sign In asks it; until answered, nothing is offered. A "no" (or no answer) sends the person back.
    var signUp by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) {
        signUp = runCatching { ApiClient.get(context).googleConfig()["sign_up"] == true }.getOrDefault(false)
    }
    LaunchedEffect(signUp) { if (signUp == false) onBack() }
    BackHandler(onBack = onBack)
    var choice by rememberSaveable { mutableStateOf(JoinChoice.Client) }

    Box(Modifier.fillMaxSize().background(Lex.colors.background).grain()) {
        if (!joinReachable(signUp)) return@Box
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.s4).padding(top = spacing.s3, bottom = spacing.s4),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClickLabel = "Back", onClick = onBack), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = PrimaryText)
                }
            }
            Spacer(Modifier.height(spacing.s5))
            // Large type, as in the reference: the first line strong, the second quiet. The monogram stands large at the right.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("Join", fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 64.sp, lineHeight = 66.sp, color = PrimaryText)
                    Text("Legum Lex", fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 40.sp, lineHeight = 44.sp, color = Lex.colors.faint)
                }
                LegumMark(size = 88.dp, fillColor = Lex.colors.muted)
            }
            Spacer(Modifier.height(spacing.s6))
            JoinCard(
                selected = choice == JoinChoice.Client, eyebrow = "Client", title = "Onboard Now", line = ONBOARD_NOW_LINE,
                onClick = { choice = JoinChoice.Client },
            )
            Spacer(Modifier.height(spacing.s3))
            JoinCard(
                selected = choice == JoinChoice.Advocate, eyebrow = "Advocate", title = "Register As Counsel", line = null,
                onClick = { choice = JoinChoice.Advocate },
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(spacing.s6))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                LegumMark(size = 30.dp, fillColor = PrimaryText)
                Spacer(Modifier.weight(1f))
                JoinInPill { runJoin(choice, onOpenAccount, onRegisterCounsel) }
            }
        }
    }
}

@Composable
private fun JoinCard(selected: Boolean, eyebrow: String, title: String, line: String?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Lex.colors.card)
            .border(if (selected) 2.dp else 1.dp, if (selected) PrimaryText else BorderColor, shape)
            .clickable(onClickLabel = title, onClick = onClick)
            .semantics { contentDescription = "$eyebrow: $title" }
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        JoinFigure(advocate = eyebrow == "Advocate")
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(eyebrow, style = MaterialTheme.typography.labelMedium, color = SecondaryText)
            Text(title, style = MaterialTheme.typography.headlineSmall, color = PrimaryText)
            if (line != null) Text(line, style = MaterialTheme.typography.bodySmall, color = SecondaryText)
        }
        Icon(
            if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, contentDescription = null,
            tint = if (selected) PrimaryText else Lex.colors.faint, modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * The figure on a choice card: a soft square tile (radius 8) with the person on it. The client is a plain bust in the theme's ink; the
 * advocate is the same bust in a black coat with the two white bands at the collar. The coat is near-black on the tile's mid grey, so
 * it keeps its edge in light and dark. The card's text names the choice, so the figure is not announced.
 */
@Composable
private fun JoinFigure(advocate: Boolean) {
    // In dark the advocate's near-black coat needs a lighter tile behind it; the client's tile stays as it is.
    val tile = if (advocate && Lex.colors.isDark) Lex.colors.faint else Lex.colors.muted
    Box(Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(tile)) {
        val ink = androidx.compose.ui.graphics.ColorFilter.tint(PrimaryText)
        if (advocate) {
            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.legumlex.client.R.drawable.ic_join_advocate), contentDescription = null, modifier = Modifier.size(64.dp))
            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.legumlex.client.R.drawable.ic_join_advocate_head), contentDescription = null, colorFilter = ink, modifier = Modifier.size(64.dp))
        } else {
            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.legumlex.client.R.drawable.ic_join_client), contentDescription = null, colorFilter = ink, modifier = Modifier.size(64.dp))
        }
    }
}

/** "Join In" with its round arrow, the reference's pill; it acts on the selected choice. */
@Composable
private fun JoinInPill(onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.clip(shape).background(AccentInk).clickable(onClickLabel = "Join In", onClick = onClick)
            .semantics { contentDescription = "Join In" }.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Join In", style = MaterialTheme.typography.labelLarge, color = InverseText)
        // An off-white disc with a dark arrow on the near-black pill (both themes: the pill is AccentInk, the disc its inverse).
        Box(Modifier.size(36.dp).clip(CircleShape).background(InverseText), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = AccentInk, modifier = Modifier.size(18.dp))
        }
    }
}
