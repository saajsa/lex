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
import com.legumlex.client.ahc.theme.Inter

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
        NoticeStep.IN_UPLOAD, NoticeStep.IN_PROCESSING, NoticeStep.IN_CONFIRM, NoticeStep.IN_DONE -> "A Notice You Received"
        else -> "Send A Notice"
    }

    Column(Modifier.fillMaxSize().background(LlColor.Paper)) {
        Text(
            heading,
            fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = LlColor.Ink,
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
        Text("What Do You Need?", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, color = LlColor.Ink)
        Spacer(Modifier.height(6.dp))
        Text("A legal notice is a formal letter. Tell us the direction — your advocate handles the rest.",
            fontFamily = Inter, fontSize = 12.5.sp, color = LlColor.InkMuted)
        Spacer(Modifier.height(20.dp))
        BigCard(NoticeDirection.OUTBOUND, "Send A Legal Notice",
            "Someone owes you, or has wronged you, and you want to formally demand action.") { vm.chooseDirection(NoticeDirection.OUTBOUND) }
        Spacer(Modifier.height(14.dp))
        BigCard(NoticeDirection.INBOUND, "A Notice You Received",
            "Upload a legal notice you've received so your advocate can review it.") { vm.chooseDirection(NoticeDirection.INBOUND) }
    }
}

@Composable
private fun BigCard(dir: NoticeDirection, title: String, desc: String, onClick: () -> Unit) {
    val c = dirColors(dir)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(com.legumlex.client.ui.theme.Lex.colors.card)
            .border(1.dp, LlColor.Border, RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(18.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(c.soft), contentAlignment = Alignment.Center) {
            Icon(if (dir == NoticeDirection.OUTBOUND) painterResource(R.drawable.ic_ms_outgoing_mail) else painterResource(R.drawable.ic_ms_mark_email_unread), null, tint = c.solid, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = LlColor.Ink)
            Spacer(Modifier.height(5.dp))
            Text(desc, fontFamily = Inter, fontSize = 12.sp, color = LlColor.InkMuted)
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
    val needsType = s.selectedProblemKey != null && s.problems.firstOrNull { it.key == s.selectedProblemKey }?.typeId == null
    StepScaffold(1, NoticeDirection.OUTBOUND, "What Happened?",
        "Choose what's closest. Your advocate refines the legal basis later.",
        cta = "Continue", ctaEnabled = s.selectedProblemKey != null && s.selectedTypeId != null, onCta = vm::continueFromProblem) {
        TypesStatus(s, vm)
        Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            s.problems.forEach { card ->
                val sel = card.key == s.selectedProblemKey
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(com.legumlex.client.ui.theme.Lex.colors.card)
                        .border(if (sel) 2.dp else 1.dp, if (sel) LlColor.SlateMid else LlColor.Border, RoundedCornerShape(12.dp))
                        .clickable { vm.selectProblem(card) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // .optcard-ic — slate-tinted glyph per problem type
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(8.dp)).background(LlColor.SlateSoft), contentAlignment = Alignment.Center) {
                        Icon(painterResource(problemIcon(card.key)), null, tint = LlColor.Slate, modifier = Modifier.size(21.dp))
                    }
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text(card.title, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = LlColor.Ink)
                        Spacer(Modifier.height(2.dp))
                        Text(card.desc, fontFamily = Inter, fontSize = 11.sp, color = LlColor.InkMuted)
                    }
                    if (sel) Icon(painterResource(R.drawable.ic_ms_check_circle), null, tint = LlColor.Slate, modifier = Modifier.size(20.dp))
                }
            }
        }
        // No type matches this card: the server needs one, so the client names it.
        if (needsType && s.types.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            TypeChooser(s.types, s.selectedTypeId, "Which Kind Of Notice?",
                "None of the types matched this choice. Pick the closest; your advocate can change it.", vm::selectType)
        }
    }
}

/** Loading / failed state of the notice types, with a retry. */
@Composable
private fun TypesStatus(s: NewNoticeState, vm: NewNoticeViewModel) {
    val err = s.typesError ?: return
    Row(
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 12.dp).clip(RoundedCornerShape(8.dp))
            .background(LlColor.AmberSoft).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(err, fontFamily = Inter, fontSize = 11.5.sp, color = LlColor.Amber, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.clip(RoundedCornerShape(6.dp)).border(1.dp, LlColor.Amber, RoundedCornerShape(6.dp))
            .clickable(enabled = !s.typesLoading) { vm.loadTypes() }.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(if (s.typesLoading) "Loading…" else "Try Again", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = LlColor.Amber)
        }
    }
}

/** A required pick from the real notice types (tblcase_notice_types). */
@Composable
private fun TypeChooser(types: List<NoticeType>, selected: Int?, title: String, sub: String, onPick: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = 18.dp)) {
        Text(title, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = LlColor.Ink)
        Spacer(Modifier.height(3.dp))
        Text(sub, fontFamily = Inter, fontSize = 11.sp, color = LlColor.InkMuted)
        Spacer(Modifier.height(10.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(com.legumlex.client.ui.theme.Lex.colors.card)
                .border(1.dp, LlColor.Border, RoundedCornerShape(12.dp)),
        ) {
            types.forEachIndexed { i, t ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(LlColor.Paper2))
                val on = t.id == selected
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(t.id) }.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(t.name, fontFamily = Inter, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, fontSize = 12.5.sp,
                        color = if (on) LlColor.Ink else LlColor.InkMuted, modifier = Modifier.weight(1f))
                    if (on) Icon(painterResource(R.drawable.ic_ms_check_circle), null, tint = LlColor.Slate, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun OutRecipient(s: NewNoticeState, vm: NewNoticeViewModel) {
    StepScaffold(2, NoticeDirection.OUTBOUND, "Who Is It Against?",
        "The person or company the notice is for. That's all we need to start.",
        cta = "Continue", ctaEnabled = true, onCta = vm::continueFromRecipient) {
        Column(Modifier.padding(horizontal = 18.dp)) {
            Field("Recipient Name *", s.recipientName, vm::setRecipientName,
                error = if (s.showRecipientError) "Please enter who the notice is for" else null)
            Spacer(Modifier.height(14.dp))
            Text("Their Details (Optional)", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = LlColor.SlateMid)
            Spacer(Modifier.height(10.dp))
            Field("Address", s.recipientAddress, vm::setRecipientAddress)
            Spacer(Modifier.height(10.dp))
            Field("Contact Number", s.recipientContact, vm::setRecipientContact, keyboard = KeyboardType.Phone)
            Spacer(Modifier.height(10.dp))
            Field("Email", s.recipientEmail, vm::setRecipientEmail, keyboard = KeyboardType.Email)
        }
    }
}

@Composable
private fun OutExtras(s: NewNoticeState, vm: NewNoticeViewModel) {
    StepScaffold(3, NoticeDirection.OUTBOUND, "Anything To Add?",
        "All optional — your advocate can follow up for anything missing.",
        cta = if (s.isSubmitting) "Submitting…" else "Submit Request", ctaEnabled = !s.isSubmitting,
        submitting = s.isSubmitting, onCta = vm::submitOutbound) {
        Column(Modifier.padding(horizontal = 18.dp)) {
            Field("Claim Amount (₹, Whole Rupees)", s.claimAmount, vm::setClaimAmount, keyboard = KeyboardType.Number)
            Spacer(Modifier.height(12.dp))
            Field("What Happened? (One Line)", s.context, vm::setContext, singleLine = false)
            s.error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, fontFamily = Inter, fontSize = 11.5.sp, color = LlColor.ClaretMid)
            }
        }
    }
}

/* --------------------------------------------------------------- inbound */

@Composable
private fun InUpload(vm: NewNoticeViewModel) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let(vm::pickFile) }
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Text("Upload The Notice You Received", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, color = LlColor.Ink)
        Spacer(Modifier.height(6.dp))
        Text("Pick a photo or the PDF. We'll read out the key details for your advocate.",
            fontFamily = Inter, fontSize = 12.5.sp, color = LlColor.InkMuted)
        Spacer(Modifier.height(20.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(LlColor.Paper1)
                .border(1.5.dp, LlColor.BorderStrong, RoundedCornerShape(12.dp))
                .clickable { picker.launch("*/*") }.padding(vertical = 36.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(58.dp).clip(RoundedCornerShape(12.dp)).background(com.legumlex.client.ui.theme.Lex.colors.card).border(1.dp, LlColor.Border, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_ms_cloud_upload), null, tint = LlColor.Amber, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text("Tap To Upload", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, color = LlColor.Ink)
            Spacer(Modifier.height(4.dp))
            Text("PDF, JPG or PNG", fontFamily = Inter, fontSize = 10.5.sp, color = LlColor.InkFaint)
        }
    }
}

@Composable
private fun InProcessing() {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator(color = LlColor.SlateMid, strokeWidth = 3.dp)
        Spacer(Modifier.height(22.dp))
        Text("Reading Your Notice…", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, color = LlColor.Ink)
        Spacer(Modifier.height(8.dp))
        Text("Pulling out the sender, type, claim and reply date.",
            fontFamily = Inter, fontSize = 12.sp, color = LlColor.InkMuted)
    }
}

@Composable
private fun InConfirm(s: NewNoticeState, vm: NewNoticeViewModel) {
    StepScaffold(null, NoticeDirection.INBOUND, "Confirm What We Found",
        if (s.extractFailed) "We couldn't read it automatically — please fill these in." else "Tap any field to correct it.",
        cta = if (s.isSubmitting) "Submitting…" else "Submit", ctaEnabled = !s.isSubmitting && s.selectedTypeId != null,
        submitting = s.isSubmitting, onCta = vm::submitInbound) {
        TypesStatus(s, vm)
        Column(Modifier.padding(horizontal = 18.dp)) {
            Field("Who Sent It?", s.senderName, vm::setSenderName)
            Spacer(Modifier.height(12.dp))
            Field("Their Contact", s.senderContact, vm::setSenderContact, keyboard = KeyboardType.Phone)
            Spacer(Modifier.height(12.dp))
            Field("Claim Amount (₹, Whole Rupees)", s.claimAmountIn, vm::setClaimAmountIn, keyboard = KeyboardType.Number)
            Spacer(Modifier.height(12.dp))
            DateField("Reply Date On The Notice", s.responseDueDate, vm::setResponseDueDate)
        }
        // The server needs a type; when the notice didn't name one, the client picks it.
        val named = s.types.firstOrNull { it.id == s.selectedTypeId && s.extracted?.noticeTypeId == it.id }
        if (named == null && s.types.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            TypeChooser(s.types, s.selectedTypeId, "Which Kind Of Notice Is It?",
                "Pick the closest; your advocate can change it.", vm::setInboundType)
        } else if (named != null) {
            Spacer(Modifier.height(12.dp))
            Text("Type · ${named.name}", fontFamily = Inter, fontSize = 11.5.sp, color = LlColor.InkMuted, modifier = Modifier.padding(horizontal = 18.dp))
        }
        Column(Modifier.padding(horizontal = 18.dp)) {
            s.error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, fontFamily = Inter, fontSize = 11.5.sp, color = LlColor.ClaretMid)
            }
        }
    }
}

/* ----------------------------------------------------------------- done */

@Composable
private fun Done(s: NewNoticeState, dir: NoticeDirection, onOpenNotice: (String) -> Unit, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(66.dp).clip(RoundedCornerShape(12.dp)).background(LlColor.VerdantSoft), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_ms_check_circle), null, tint = LlColor.Verdant, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(if (dir == NoticeDirection.OUTBOUND) "Notice Request Submitted" else "We've Logged Your Notice",
            fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = LlColor.Ink)
        Spacer(Modifier.height(9.dp))
        Text(
            if (dir == NoticeDirection.OUTBOUND) "Your advocate has your request. You can follow the notice here."
            else "Your advocate has the notice. You can follow it here.",
            fontFamily = Inter, fontSize = 12.5.sp, color = LlColor.InkMuted,
        )
        s.createdReference?.let {
            Spacer(Modifier.height(18.dp))
            Column(
                Modifier.clip(RoundedCornerShape(12.dp)).background(LlColor.Paper1).border(1.dp, LlColor.Border, RoundedCornerShape(12.dp))
                    .padding(horizontal = 22.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("REFERENCE", fontFamily = Inter, fontSize = 9.5.sp, letterSpacing = 1.2.sp, color = LlColor.InkFaint)
                Spacer(Modifier.height(3.dp))
                Text(it, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = LlColor.Ink)
            }
        }
        Spacer(Modifier.height(24.dp))
        PrimaryButton("Track Notice") { s.createdNoticeId?.let(onOpenNotice) ?: onClose() }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.clickable(onClick = onClose).padding(8.dp)) {
            Text("Back To Notices", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = LlColor.InkMuted)
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
                    Text("Step $step / 3", fontFamily = Inter, fontSize = 11.sp, color = c.mid)
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
                Text(title, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, color = LlColor.Ink)
                Spacer(Modifier.height(5.dp))
                Text(subtitle, fontFamily = Inter, fontSize = 12.5.sp, color = LlColor.InkMuted)
                Spacer(Modifier.height(14.dp))
            }
            content()
            Spacer(Modifier.height(20.dp))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(LlColor.Border))
        Column(Modifier.padding(18.dp)) {
            PrimaryButton(cta, enabled = ctaEnabled, submitting = submitting, onClick = onCta)
        }
    }
}

@Composable
private fun PrimaryButton(label: String, enabled: Boolean = true, submitting: Boolean = false, onClick: () -> Unit) {
    // Theme tokens, so the button reads in dark mode as well as light.
    val fg = if (enabled) com.legumlex.client.ui.theme.Lex.colors.primaryForeground else LlColor.InkMuted
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(if (enabled) com.legumlex.client.ui.theme.Lex.colors.primary else LlColor.Paper2)
            .clickable(enabled = enabled && !submitting, onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (submitting) {
            CircularProgressIndicator(color = fg, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label.uppercase(), fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, letterSpacing = 0.5.sp, color = fg)
        if (!submitting) {
            Spacer(Modifier.width(7.dp))
            Icon(painterResource(R.drawable.ic_ms_arrow_forward), null, tint = fg, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun Field(
    label: String, value: String, onChange: (String) -> Unit,
    error: String? = null, keyboard: KeyboardType = KeyboardType.Text, singleLine: Boolean = true,
) {
    Column {
        Text(label, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = LlColor.Ink)
        Spacer(Modifier.height(7.dp))
        OutlinedTextField(
            value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(),
            singleLine = singleLine, isError = error != null,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = LlColor.SlateMid, unfocusedBorderColor = LlColor.Border,
                focusedContainerColor = com.legumlex.client.ui.theme.Lex.colors.card,
                unfocusedContainerColor = com.legumlex.client.ui.theme.Lex.colors.card,
                focusedTextColor = LlColor.Ink, unfocusedTextColor = LlColor.Ink,
                errorBorderColor = LlColor.ClaretMid,
            ),
        )
        error?.let {
            Spacer(Modifier.height(5.dp))
            Text(it, fontFamily = Inter, fontSize = 10.5.sp, color = LlColor.ClaretMid)
        }
    }
}

/** A date chosen from the calendar, kept as yyyy-MM-dd and shown as "d MMM yyyy". */
@Composable
private fun DateField(label: String, value: String, onChange: (String) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val current = runCatching { java.time.LocalDate.parse(value) }.getOrNull()
    Column {
        Text(label, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = LlColor.Ink)
        Spacer(Modifier.height(7.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(com.legumlex.client.ui.theme.Lex.colors.card)
                .border(1.dp, LlColor.Border, RoundedCornerShape(8.dp))
                .clickable {
                    val d = current ?: todayIst()
                    android.app.DatePickerDialog(ctx, { _, y, m, day ->
                        onChange(java.time.LocalDate.of(y, m + 1, day).toString())
                    }, d.year, d.monthValue - 1, d.dayOfMonth).show()
                }
                .padding(horizontal = 14.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(current?.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy")) ?: "Choose A Date",
                fontFamily = Inter, fontSize = 13.sp, color = if (current != null) LlColor.Ink else LlColor.InkFaint, modifier = Modifier.weight(1f))
            Icon(painterResource(R.drawable.ic_ms_schedule), null, tint = LlColor.InkMuted, modifier = Modifier.size(18.dp))
            if (current != null) {
                Spacer(Modifier.width(10.dp))
                Box(Modifier.clip(RoundedCornerShape(6.dp)).clickable { onChange("") }.padding(4.dp)) {
                    Text("Clear", fontFamily = Inter, fontSize = 11.sp, color = LlColor.InkMuted)
                }
            }
        }
    }
}
