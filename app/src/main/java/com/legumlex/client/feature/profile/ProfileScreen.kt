package com.legumlex.client.feature.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.legumlex.client.ui.components.Eyebrow
import com.legumlex.client.ui.components.KeyValueRow
import com.legumlex.client.ui.components.LegumPrimaryButton
import com.legumlex.client.ui.components.LegumSecondaryButton
import com.legumlex.client.ui.components.LegumTextField
import com.legumlex.client.ui.components.LexCard
import com.legumlex.client.ui.components.LexEmpty
import com.legumlex.client.ui.components.PageHeader
import com.legumlex.client.ui.components.StatusBadge
import com.legumlex.client.ui.theme.Lex
import com.legumlex.client.ui.theme.StatusPalette
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/* ============================================================================
 *  Your Profile — the Mylex profile on a phone: what we hold for you, as we
 *  hold it (name, email and phone are read-only, because they appear on
 *  filings), your verified mobile, and the account facts that answer "is this
 *  really my login".
 * ========================================================================== */

@Composable
fun ProfileScreen() {
    val vm: ProfileViewModel = viewModel()
    val state by vm.state.collectAsState()
    val verify by vm.verify.collectAsState()
    val note by vm.note.collectAsState()

    when (val s = state) {
        ProfileState.Loading -> Box(Modifier.fillMaxSize().background(Lex.colors.background), Alignment.Center) {
            CircularProgressIndicator(color = Lex.colors.foreground, strokeWidth = 2.dp)
        }
        is ProfileState.Failed -> Column(
            Modifier.fillMaxSize().background(Lex.colors.background).padding(24.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LexEmpty("Your Profile Could Not Be Loaded", s.message.ifBlank { "Please try again." })
            TextButton(onClick = vm::load) { Text("Try Again", color = Lex.colors.foreground) }
        }
        is ProfileState.Ready -> ProfileBody(s.profile, verify, note, vm)
    }
}

@Composable
private fun ProfileBody(p: Profile, verify: VerifyFlow, note: String?, vm: ProfileViewModel) {
    Column(
        Modifier.fillMaxSize().background(Lex.colors.background).verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PageHeader(eyebrow = "Your Account", title = "Your Profile")

        Notice(
            title = "Held As On Record",
            body = "Your name, email address and phone number appear on filings and on service of notice, so they are held as we have them. To correct one, write to us and we will update the record for you.",
            palette = Lex.colors.info,
        )

        LexCard(title = "Your Details") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Photo", style = MaterialTheme.typography.bodyMedium, color = Lex.colors.mutedForeground, modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(56.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Lex.colors.muted)
                        .border(1.dp, Lex.colors.border, androidx.compose.foundation.shape.CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(p.initials, style = MaterialTheme.typography.titleMedium, color = Lex.colors.foreground)
                    p.avatarUrl?.let {
                        coil.compose.AsyncImage(model = it, contentDescription = "Your photo",
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.size(56.dp).clip(androidx.compose.foundation.shape.CircleShape))
                    }
                }
            }
            HorizontalDivider(color = Lex.colors.border)
            KeyValueRow("Full Name", p.name.ifBlank { "Not Recorded" }, trailing = { Locked() })
            HorizontalDivider(color = Lex.colors.border)
            KeyValueRow("Email Address", p.email ?: "Not Recorded", trailing = {
                if (p.emailVerified) StatusBadge("Verified", Lex.colors.success) else Locked()
            })
            HorizontalDivider(color = Lex.colors.border)
            KeyValueRow("Phone Number", p.phone ?: "Not Recorded", trailing = { Locked() })
            p.position?.let {
                HorizontalDivider(color = Lex.colors.border)
                KeyValueRow("Position", it)
            }
        }

        MobileCard(p.mobile, verify, note, vm)

        LexCard(title = "Account") {
            val rows = listOfNotNull(
                p.company?.let { "Client Account" to it },
                "Your Role" to p.role,
                "Client Since" to stamp(p.clientSince),
                "You Joined" to stamp(p.joined),
                "Last Sign-In" to (p.lastLogin?.let { stamp(it, true) } ?: "Not recorded yet"),
                p.passwordChanged?.let { "Password Last Changed" to stamp(it) },
            )
            rows.forEachIndexed { i, (k, v) ->
                if (i > 0) HorizontalDivider(color = Lex.colors.border)
                KeyValueRow(k, v)
            }
        }

        if (p.permissions.isNotEmpty()) {
            LexCard(title = "What You Can Reach") {
                Column(Modifier.padding(16.dp)) {
                    Text("The parts of the portal and this app your account includes.",
                        style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground)
                    Spacer(Modifier.height(10.dp))
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        p.permissions.forEach { StatusBadge(it, Lex.colors.neutral) }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

/**
 * The verified mobile: what sign-in codes and case updates go to. Verifying is
 * the contact's own act — a code to the number proves it is theirs.
 */
@Composable
private fun MobileCard(m: Mobile, v: VerifyFlow, note: String?, vm: ProfileViewModel) {
    // Removing the number ends sign-in by code: a contact who signs in no
    // other way would be locked out, so it is confirmed first.
    var confirmRemove by remember { mutableStateOf(false) }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove This Number?") },
            text = {
                Text("You will no longer be able to sign in with a code to ${m.number.orEmpty().ifBlank { "this number" }}, " +
                    "and case updates on WhatsApp and SMS will stop. Make sure you can sign in with Google or a password first.")
            },
            confirmButton = {
                TextButton(onClick = { confirmRemove = false; vm.removeNumber() }) {
                    Text("Remove Number", color = Lex.colors.danger.softText)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text("Keep It", color = Lex.colors.foreground) }
            },
            containerColor = Lex.colors.card,
        )
    }
    LexCard(title = "Mobile Number", trailing = {
        if (m.verified) StatusBadge("Verified", Lex.colors.success) else StatusBadge("Not Verified", Lex.colors.neutral)
    }) {
        note?.let {
            val isError by vm.noteIsError.collectAsState()
            Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) { Notice(null, it, if (isError) Lex.colors.danger else Lex.colors.success) }
        }
        if (!v.open) {
            if (m.verified) {
                KeyValueRow("Number", m.number.orEmpty())
                HorizontalDivider(color = Lex.colors.border)
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Case Updates On WhatsApp", style = MaterialTheme.typography.bodyMedium, color = Lex.colors.foreground)
                        Text("Hearing dates, orders and notices, by WhatsApp or SMS.",
                            style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground)
                    }
                    Switch(
                        checked = m.updates, onCheckedChange = vm::setUpdates,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Lex.colors.primaryForeground, checkedTrackColor = Lex.colors.primary,
                            uncheckedThumbColor = Lex.colors.faint, uncheckedTrackColor = Lex.colors.muted,
                            uncheckedBorderColor = Lex.colors.input,
                        ),
                    )
                }
                HorizontalDivider(color = Lex.colors.border)
                Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    if (m.available) TextButton(onClick = vm::openVerify) { Text("Change Number", color = Lex.colors.foreground) }
                    TextButton(onClick = { confirmRemove = true }) { Text("Remove", color = Lex.colors.danger.softText) }
                }
            } else {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "A verified number lets you sign in with a code instead of a password, and brings case updates on WhatsApp.",
                        style = MaterialTheme.typography.bodyMedium, color = Lex.colors.secondaryForeground,
                    )
                    Spacer(Modifier.height(12.dp))
                    if (m.available) {
                        LegumPrimaryButton(text = "Verify A Number", onClick = vm::openVerify, leadingIcon = Icons.Outlined.Smartphone)
                    } else {
                        Text("Verification codes cannot be sent at present. Please try again later.",
                            style = MaterialTheme.typography.bodySmall, color = Lex.colors.mutedForeground)
                    }
                }
            }
        }

        AnimatedVisibility(v.open) { VerifySteps(v, vm) }
    }
}

@Composable
private fun VerifySteps(v: VerifyFlow, vm: ProfileViewModel) {
    var code by remember(v.ref) { mutableStateOf("") }
    Column(Modifier.padding(16.dp)) {
        Eyebrow(if (v.ref == null) "Step 1 Of 2 · Your Number" else "Step 2 Of 2 · The Code")
        Spacer(Modifier.height(10.dp))
        v.error?.let { Notice(null, it, Lex.colors.danger); Spacer(Modifier.height(10.dp)) }
        v.info?.let { Notice(null, it, Lex.colors.success); Spacer(Modifier.height(10.dp)) }

        if (v.ref == null) {
            LegumTextField(
                value = v.number, onValueChange = vm::onNumber, label = "Mobile Number",
                leadingIcon = Icons.Outlined.Smartphone, keyboardType = KeyboardType.Phone, enabled = !v.busy,
                modifier = Modifier.fillMaxWidth(),
                supportingText = "The code comes on WhatsApp, or by SMS.",
            )
            Spacer(Modifier.height(12.dp))
            LegumPrimaryButton(text = if (v.busy) "Sending…" else "Send Code", onClick = vm::sendCode,
                enabled = v.number.isNotBlank() && !v.busy)
            Spacer(Modifier.height(8.dp))
            LegumSecondaryButton(text = "Cancel", onClick = vm::closeVerify)
        } else {
            Text("A code has been sent by WhatsApp to ${v.sentTo}. Please enter the 6 digits.",
                style = MaterialTheme.typography.bodyMedium, color = Lex.colors.secondaryForeground)
            Spacer(Modifier.height(12.dp))
            LegumTextField(
                value = code,
                onValueChange = {
                    code = it.filter(Char::isDigit).take(6)
                    if (code.length == 6 && !v.busy) vm.submitCode(code)
                },
                label = "6-Digit Code", leadingIcon = Icons.Outlined.Lock, keyboardType = KeyboardType.NumberPassword,
                enabled = !v.busy, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            LegumPrimaryButton(text = if (v.busy) "Verifying…" else "Verify", onClick = { vm.submitCode(code) },
                enabled = code.length == 6 && !v.busy)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                if (v.canSms) TextButton(onClick = vm::sendBySms) { Text("Send By SMS Instead", color = Lex.colors.foreground) }
                TextButton(onClick = vm::sendCode, enabled = v.resendIn == 0 && !v.busy) {
                    Text(
                        if (v.resendIn > 0) "Send A New Code In %d:%02d".format(v.resendIn / 60, v.resendIn % 60) else "Send A New Code",
                        color = if (v.resendIn == 0) Lex.colors.foreground else Lex.colors.faint,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = vm::changeNumber) { Text("Change Number", color = Lex.colors.mutedForeground) }
                TextButton(onClick = vm::closeVerify) { Text("Cancel", color = Lex.colors.mutedForeground) }
            }
        }
    }
}

/** kt-alert, light: a tinted box with an icon, an optional bold lead, the text. */
@Composable
private fun Notice(title: String?, body: String, palette: StatusPalette) {
    Surface(color = palette.softBg, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Info, null, tint = palette.softText, modifier = Modifier.size(18.dp).padding(top = 1.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                title?.let { Text(it, style = MaterialTheme.typography.titleSmall, color = palette.softText) }
                Text(body, style = MaterialTheme.typography.bodySmall, color = palette.softText)
            }
        }
    }
}

@Composable
private fun Locked() = Icon(Icons.Outlined.Lock, "Held as on record", tint = Lex.colors.faint, modifier = Modifier.size(16.dp))

private fun stamp(raw: String?, withTime: Boolean = false): String {
    raw ?: return "Not Recorded"
    return runCatching {
        val t = LocalDateTime.parse(raw.replace(' ', 'T'))
        t.format(DateTimeFormatter.ofPattern(if (withTime) "d MMM yyyy, h:mm a" else "d MMM yyyy", Locale.ENGLISH))
    }.getOrDefault(raw)
}
