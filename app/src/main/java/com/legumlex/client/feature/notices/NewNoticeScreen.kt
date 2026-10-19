package com.legumlex.client.feature.notices

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.legumlex.client.R
import com.legumlex.client.ahc.theme.LlColor
import com.legumlex.client.ahc.theme.SourceSans
import com.legumlex.client.ahc.theme.Lora

@Composable
fun NewNoticeScreen(
    parentNoticeId: String? = null,
    recipientName: String? = null,
    onClose: () -> Unit,
    onOpenNotice: (String) -> Unit,
) {
    val vm: NewNoticeViewModel = viewModel()
    val s by vm.state.collectAsState()

    LaunchedEffect(parentNoticeId) {
        if (parentNoticeId != null) vm.initResponse(parentNoticeId, recipientName)
    }

    // The app-wide header (logo · back · notifications) sits above via the Scaffold;
    // its back exits the funnel. System back steps through the wizard when mid-flow.
    BackHandler(enabled = !vm.atRoot()) { vm.back() }

    // In-content step heading replaces the old per-screen header bar.
    val heading = when (s.step) {
        NoticeStep.FORK -> "Legal Notice"
        NoticeStep.IN_UPLOAD, NoticeStep.IN_PROCESSING, NoticeStep.IN_CONFIRM, NoticeStep.IN_DONE -> "Respond to a notice"
        else -> "Send a notice"
    }

    Column(Modifier.fillMaxSize().background(LlColor.Paper)) {
        Text(
            heading,
            fontFamily = Lora, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = LlColor.Ink,
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 4.dp),
        )
        Box(Modifier.weight(1f)) {
            when (s.step) {
                NoticeStep.FORK -> Fork(vm)
                NoticeStep.OUT_PROBLEM -> OutProblem(s, vm)
                NoticeStep.OUT_RECIPIENT -> OutRecipient(s, vm)
                NoticeStep.OUT_EXTRAS -> OutExtras(s, vm)
                NoticeStep.OUT_DONE -> Done(s, NoticeDirection.OUTBOUND, onOpenNotice, onClose)
                NoticeStep.IN_UPLOAD -> InUpload(vm)
                NoticeStep.IN_PROCESSING -> InProcessing()
                NoticeStep.IN_CONFIRM -> InConfirm(s, vm)
                NoticeStep.IN_DONE -> Done(s, NoticeDirection.INBOUND, onOpenNotice, onClose)
            }
        }
    }
}

/* ----------------------------------------------------------------- fork */

@Composable
private fun Fork(vm: NewNoticeViewModel) {
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Text("What do you need?", fontFamily = Lora, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, color = LlColor.Ink)
        Spacer(Modifier.height(6.dp))
        Text("A legal notice is a formal letter. Tell us the direction — your advocate handles the rest.",
            fontFamily = SourceSans, fontSize = 12.5.sp, color = LlColor.InkMuted)
        Spacer(Modifier.height(20.dp))
        BigCard(NoticeDirection.OUTBOUND, "Send a legal notice",
            "Someone owes you, or has wronged you, and you want to formally demand action.") { vm.chooseDirection(NoticeDirection.OUTBOUND) }
        Spacer(Modifier.height(14.dp))
        BigCard(NoticeDirection.INBOUND, "Respond to a notice you received",
            "You've received a legal notice and need help replying before the deadline.") { vm.chooseDirection(NoticeDirection.INBOUND) }
    }
}

@Composable
private fun BigCard(dir: NoticeDirection, title: String, desc: String, onClick: () -> Unit) {
    val c = dirColors(dir)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White)
            .border(1.dp, LlColor.Border, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(18.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)).background(c.soft), contentAlignment = Alignment.Center) {
            Icon(if (dir == NoticeDirection.OUTBOUND) painterResource(R.drawable.ic_ms_outgoing_mail) else painterResource(R.drawable.ic_ms_mark_email_unread), null, tint = c.solid, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = Lora, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = LlColor.Ink)
            Spacer(Modifier.height(5.dp))
            Text(desc, fontFamily = SourceSans, fontSize = 12.sp, color = LlColor.InkMuted)
        }
    }
}

/* -------------------------------------------------------------- outbound */

// Plain-language problem card → its Material Symbol (matches the design).
private fun problemIcon(key: String): Int = when (key) {
    "dues" -> R.drawable.ic_ms_payments
    "service" -> R.drawable.ic_ms_production_quantity_limits
    "property" -> R.drawable.ic_ms_home_work
    else -> R.drawable.ic_ms_more_horiz
}

@Composable
private fun OutProblem(s: NewNoticeState, vm: NewNoticeViewModel) {
    StepScaffold(1, NoticeDirection.OUTBOUND, "What happened?",
        "Choose what's closest. Your advocate refines the legal basis later.",
        cta = "Continue", ctaEnabled = s.selectedProblemKey != null, onCta = vm::continueFromProblem) {
        Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            s.problems.forEach { card ->
                val sel = card.key == s.selectedProblemKey
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(Color.White)
                        .border(if (sel) 2.dp else 1.dp, if (sel) LlColor.SlateMid else LlColor.Border, RoundedCornerShape(13.dp))
                        .clickable { vm.selectProblem(card) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // .optcard-ic — slate-tinted glyph per problem type
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(11.dp)).background(LlColor.SlateSoft), contentAlignment = Alignment.Center) {
                        Icon(painterResource(problemIcon(card.key)), null, tint = LlColor.Slate, modifier = Modifier.size(21.dp))
                    }
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text(card.title, fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = LlColor.Ink)
                        Spacer(Modifier.height(2.dp))
                        Text(card.desc, fontFamily = SourceSans, fontSize = 11.sp, color = LlColor.InkMuted)
                    }
                    if (sel) Icon(painterResource(R.drawable.ic_ms_check_circle), null, tint = LlColor.Slate, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun OutRecipient(s: NewNoticeState, vm: NewNoticeViewModel) {
    StepScaffold(2, NoticeDirection.OUTBOUND, "Who is it against?",
        "The person or company the notice is for. That's all we need to start.",
        cta = "Continue", ctaEnabled = true, onCta = vm::continueFromRecipient) {
        Column(Modifier.padding(horizontal = 18.dp)) {
            Field("Recipient name *", s.recipientName, vm::setRecipientName,
                error = if (s.showRecipientError) "Please enter who the notice is for" else null)
            Spacer(Modifier.height(14.dp))
            Text("Their details (optional)", fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = LlColor.SlateMid)
            Spacer(Modifier.height(10.dp))
            Field("Address", s.recipientAddress, vm::setRecipientAddress)
            Spacer(Modifier.height(10.dp))
            Field("Contact number", s.recipientContact, vm::setRecipientContact, keyboard = KeyboardType.Phone)
            Spacer(Modifier.height(10.dp))
            Field("Email", s.recipientEmail, vm::setRecipientEmail, keyboard = KeyboardType.Email)
        }
    }
}

@Composable
private fun OutExtras(s: NewNoticeState, vm: NewNoticeViewModel) {
    StepScaffold(3, NoticeDirection.OUTBOUND, "Anything to add?",
        "All optional — your advocate can follow up for anything missing.",
        cta = if (s.isSubmitting) "Submitting…" else "Submit request", ctaEnabled = !s.isSubmitting,
        submitting = s.isSubmitting, onCta = vm::submitOutbound) {
        Column(Modifier.padding(horizontal = 18.dp)) {
            Field("Claim amount (₹)", s.claimAmount, vm::setClaimAmount, keyboard = KeyboardType.Number)
            Spacer(Modifier.height(12.dp))
            Field("What happened? (one line)", s.context, vm::setContext, singleLine = false)
            s.error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, fontFamily = SourceSans, fontSize = 11.5.sp, color = LlColor.ClaretMid)
            }
        }
    }
}

/* --------------------------------------------------------------- inbound */

@Composable
private fun InUpload(vm: NewNoticeViewModel) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let(vm::pickFile) }
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Text("Upload the notice you received", fontFamily = Lora, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, color = LlColor.Ink)
        Spacer(Modifier.height(6.dp))
        Text("Snap a photo or pick the PDF. We'll read it and help you respond in time.",
            fontFamily = SourceSans, fontSize = 12.5.sp, color = LlColor.InkMuted)
        Spacer(Modifier.height(20.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(LlColor.Paper1)
                .border(1.5.dp, LlColor.BorderStrong, RoundedCornerShape(13.dp))
                .clickable { picker.launch("*/*") }.padding(vertical = 36.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(58.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).border(1.dp, LlColor.Border, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_ms_cloud_upload), null, tint = LlColor.Amber, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text("Tap to upload", fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, color = LlColor.Ink)
            Spacer(Modifier.height(4.dp))
            Text("PDF, JPG or PNG", fontFamily = SourceSans, fontSize = 10.5.sp, color = LlColor.InkFaint)
        }
    }
}

@Composable
private fun InProcessing() {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator(color = LlColor.SlateMid, strokeWidth = 3.dp)
        Spacer(Modifier.height(22.dp))
        Text("Reading your notice…", fontFamily = Lora, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, color = LlColor.Ink)
        Spacer(Modifier.height(8.dp))
        Text("Pulling out the sender, type, claim and your deadline.",
            fontFamily = SourceSans, fontSize = 12.sp, color = LlColor.InkMuted)
    }
}

@Composable
private fun InConfirm(s: NewNoticeState, vm: NewNoticeViewModel) {
    StepScaffold(null, NoticeDirection.INBOUND, "Confirm what we found",
        if (s.extractFailed) "We couldn't read it automatically — please fill these in." else "Tap any field to correct it.",
        cta = if (s.isSubmitting) "Submitting…" else "Submit", ctaEnabled = !s.isSubmitting,
        submitting = s.isSubmitting, onCta = vm::submitInbound) {
        Column(Modifier.padding(horizontal = 18.dp)) {
            Field("Who sent it?", s.senderName, vm::setSenderName)
            Spacer(Modifier.height(12.dp))
            Field("Their contact", s.senderContact, vm::setSenderContact, keyboard = KeyboardType.Phone)
            Spacer(Modifier.height(12.dp))
            Field("Claim amount (₹)", s.claimAmountIn, vm::setClaimAmountIn, keyboard = KeyboardType.Number)
            Spacer(Modifier.height(12.dp))
            Field("Respond by (YYYY-MM-DD)", s.responseDueDate, vm::setResponseDueDate)
            s.error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, fontFamily = SourceSans, fontSize = 11.5.sp, color = LlColor.ClaretMid)
            }
        }
    }
}

/* ----------------------------------------------------------------- done */

@Composable
private fun Done(s: NewNoticeState, dir: NoticeDirection, onOpenNotice: (String) -> Unit, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(66.dp).clip(RoundedCornerShape(19.dp)).background(LlColor.VerdantSoft), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_ms_check_circle), null, tint = LlColor.Verdant, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(if (dir == NoticeDirection.OUTBOUND) "Notice request submitted" else "We've logged your notice",
            fontFamily = Lora, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = LlColor.Ink)
        Spacer(Modifier.height(9.dp))
        Text(
            if (dir == NoticeDirection.OUTBOUND) "Your advocate is preparing your notice. You'll be asked to approve the draft before it's sent."
            else "Your advocate will review it and help you respond before the deadline.",
            fontFamily = SourceSans, fontSize = 12.5.sp, color = LlColor.InkMuted,
        )
        s.createdReference?.let {
            Spacer(Modifier.height(18.dp))
            Column(
                Modifier.clip(RoundedCornerShape(12.dp)).background(LlColor.Paper1).border(1.dp, LlColor.Border, RoundedCornerShape(12.dp))
                    .padding(horizontal = 22.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("REFERENCE", fontFamily = SourceSans, fontSize = 9.5.sp, letterSpacing = 1.2.sp, color = LlColor.InkFaint)
                Spacer(Modifier.height(3.dp))
                Text(it, fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = LlColor.Ink)
            }
        }
        Spacer(Modifier.height(24.dp))
        PrimaryButton("Track notice", LlColor.Ink) { s.createdNoticeId?.let(onOpenNotice) ?: onClose() }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.clickable(onClick = onClose).padding(8.dp)) {
            Text("Back to notices", fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = LlColor.InkMuted)
        }
    }
}

/* -------------------------------------------------------------- helpers */

@Composable
private fun StepScaffold(
    step: Int?, dir: NoticeDirection, title: String, subtitle: String,
    cta: String, ctaEnabled: Boolean, submitting: Boolean = false, onCta: () -> Unit,
    content: @Composable () -> Unit,
) {
    val c = dirColors(dir)
    Column(Modifier.fillMaxSize()) {
        if (step != null) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Step $step / 3", fontFamily = SourceSans, fontSize = 11.sp, color = c.mid)
                    DirectionBadge(dir)
                }
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(3.dp)).background(LlColor.Paper2)) {
                    Box(Modifier.fillMaxWidth(step / 3f).height(4.dp).clip(RoundedCornerShape(3.dp)).background(c.mid))
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(LlColor.Paper2))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 6.dp)) {
                Text(title, fontFamily = Lora, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, color = LlColor.Ink)
                Spacer(Modifier.height(5.dp))
                Text(subtitle, fontFamily = SourceSans, fontSize = 12.5.sp, color = LlColor.InkMuted)
                Spacer(Modifier.height(14.dp))
            }
            content()
            Spacer(Modifier.height(20.dp))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(LlColor.Border))
        Column(Modifier.padding(18.dp)) {
            PrimaryButton(cta, c.solid, enabled = ctaEnabled, submitting = submitting, onClick = onCta)
        }
    }
}

@Composable
private fun PrimaryButton(label: String, color: Color, enabled: Boolean = true, submitting: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (enabled) color else LlColor.BorderStrong)
            .clickable(enabled = enabled && !submitting, onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (submitting) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label.uppercase(), fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, letterSpacing = 0.5.sp, color = Color.White)
        if (!submitting) {
            Spacer(Modifier.width(7.dp))
            Icon(painterResource(R.drawable.ic_ms_arrow_forward), null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun Field(
    label: String, value: String, onChange: (String) -> Unit,
    error: String? = null, keyboard: KeyboardType = KeyboardType.Text, singleLine: Boolean = true,
) {
    Column {
        Text(label, fontFamily = SourceSans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = LlColor.Ink)
        Spacer(Modifier.height(7.dp))
        OutlinedTextField(
            value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(),
            singleLine = singleLine, isError = error != null,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = LlColor.SlateMid, unfocusedBorderColor = LlColor.Border,
                focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
                errorBorderColor = LlColor.ClaretMid,
            ),
        )
        error?.let {
            Spacer(Modifier.height(5.dp))
            Text(it, fontFamily = SourceSans, fontSize = 10.5.sp, color = LlColor.ClaretMid)
        }
    }
}
