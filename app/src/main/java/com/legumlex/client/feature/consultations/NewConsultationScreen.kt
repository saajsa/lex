package com.legumlex.client.feature.consultations

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.legumlex.client.ui.components.EyebrowText
import com.legumlex.client.ui.components.LegumPrimaryButton
import com.legumlex.client.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewConsultationScreen(
    onBack: () -> Unit,
    onSuccess: (consultationId: String) -> Unit,
    onLaunchAgreementHealthCheckup: () -> Unit,
    /**
     * If provided (e.g. from home screen's "Legal Notice" service tile), skip
     * the TYPE picker and drop the user straight into the INTAKE step with
     * the kind already set. Null = normal flow starting at TYPE.
     */
    presetKind: ConsultationKind? = null,
) {
    val context = LocalContext.current
    val app     = remember(context) { context.applicationContext as Application }
    val vm: NewConsultationViewModel = viewModel(
        factory = viewModelFactory { initializer { NewConsultationViewModel(app) } }
    )
    val form by vm.form.collectAsState()
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is WizardEvent.Success -> onSuccess(event.consultationId)
                is WizardEvent.Error   -> snackbarHost.showSnackbar(event.message)
            }
        }
    }

    LaunchedEffect(presetKind) {
        if (presetKind != null && form.step == WizardStep.TYPE) {
            vm.setType(presetKind)
            vm.next()
        }
    }

    // Back returns to the previous wizard step; on the first step it hands off
    // to the host (which pops out of the flow). The app-wide header's back arrow
    // and the system/gesture back both route through here.
    BackHandler { if (form.step == WizardStep.TYPE) onBack() else vm.back() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        containerColor = MaterialTheme.colorScheme.background,
        // The app-wide header already consumes the status-bar inset.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
        ) {
            // In-content step header — the app-wide bar carries only the brand mark,
            // so the wizard states its own step + title here.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 14.dp, bottom = 2.dp),
            ) {
                EyebrowText(
                    "New request · Step ${form.step.ordinal + 1} of 3",
                    color = TertiaryText,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text  = topBarTitle(form),
                    style = MaterialTheme.typography.headlineSmall,
                    color = PrimaryText,
                )
            }

            StepProgressBar(currentStep = form.step.ordinal, totalSteps = 3)

            Box(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                when (form.step) {
                    // Picking the Agreement Health Checkup tile short-circuits
                    // the generic wizard and launches the dedicated AHC funnel
                    // (upload → extracting → confirm → payment → review).
                    WizardStep.TYPE   -> StepType(form.type) { kind ->
                        if (kind == ConsultationKind.AHC) onLaunchAgreementHealthCheckup()
                        else vm.setType(kind)
                    }
                    WizardStep.INTAKE -> StepIntake(form, vm)
                    WizardStep.REVIEW -> StepReview(form)
                }
            }

            WizardFooter(form, vm)
        }
    }
}

private fun topBarTitle(form: NewConsultationForm): String = when (form.step) {
    WizardStep.TYPE   -> "What can we help with?"
    WizardStep.INTAKE -> when (form.type) {
        ConsultationKind.LEGAL_NOTICE -> "Legal Notice intake"
        ConsultationKind.GENERAL      -> "Tell us about your matter"
        else                          -> "Intake"
    }
    WizardStep.REVIEW -> "Review & submit"
}

// ─── Progress bar ─────────────────────────────────────────────────────────────

@Composable
private fun StepProgressBar(currentStep: Int, totalSteps: Int) {
    Row(
        modifier            = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(totalSteps) { i ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (i <= currentStep) AccentInk else BorderSubtle),
            )
        }
    }
}

// ─── Step 1: Type selection ────────────────────────────────────────────────────

private data class TypeOption(
    val kind: ConsultationKind,
    val icon: ImageVector,
    val headline: String,
    val description: String,
)

// Only client-initiated kinds belong here. DOC_REQUEST and DIGITAL_OFFICE are
// advocate-driven (doc requests sent to the client; chamber is a walk-in flow)
// so they're not exposed as picker options.
private val typeOptions = listOf(
    TypeOption(ConsultationKind.GENERAL,      Icons.Outlined.ChatBubbleOutline,
        "General inquiry", "Ask a question or discuss your situation with an advocate."),
    TypeOption(ConsultationKind.AHC,          Icons.Outlined.FindInPage,
        "Agreement Check", "Upload an agreement — your advocate reviews the risks and releases a report."),
    // Legal Notice now has its own dedicated funnel (feature/notices) backed by the
    // real tblcase_legal_notices system — it is no longer created as a consultation.
)

@Composable
private fun StepType(selected: ConsultationKind, onSelect: (ConsultationKind) -> Unit) {
    val spacing = LocalSpacing.current
    Column(
        modifier            = Modifier.fillMaxWidth().padding(horizontal = spacing.s5),
        verticalArrangement = Arrangement.spacedBy(spacing.s3),
    ) {
        Spacer(Modifier.height(spacing.s2))
        typeOptions.forEach { opt ->
            TypeCard(opt, isSelected = selected == opt.kind, onClick = { onSelect(opt.kind) })
        }
        Spacer(Modifier.height(spacing.s3))
    }
}

@Composable
private fun TypeCard(opt: TypeOption, isSelected: Boolean, onClick: () -> Unit) {
    val spacing = LocalSpacing.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) AccentInk else Surface1)
            .border(
                width = if (isSelected) 0.dp else 1.dp,
                color = if (isSelected) Color.Transparent else BorderSubtle,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(spacing.s4),
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.s4),
        ) {
            Box(
                modifier         = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) Color.White.copy(alpha = 0.15f) else Surface2),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    opt.icon, null,
                    tint     = if (isSelected) Color.White else PrimaryText,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = opt.headline,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isSelected) Color.White else PrimaryText,
                )
                Text(
                    text  = opt.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) Color.White.copy(alpha = 0.75f) else SecondaryText,
                )
            }
            if (isSelected) {
                Icon(Icons.Outlined.CheckCircle, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ─── Step 2: Intake ───────────────────────────────────────────────────────────

private val MATTER_CATEGORIES = listOf(
    "employment" to "Employment",
    "property"   to "Property",
    "family"     to "Family",
    "corporate"  to "Corporate",
    "criminal"   to "Criminal",
    "consumer"   to "Consumer",
    "tax"        to "Tax",
    "other"      to "Other",
)

private val URGENCY_OPTIONS = listOf(
    "today"      to "Today",
    "this_week"  to "This week",
    "this_month" to "This month",
    "exploring"  to "Just exploring",
)

private val PRIOR_STEPS = listOf(
    "tried_directly" to "Tried to resolve directly",
    "sent_emails"    to "Sent emails / letters",
    "hired_counsel"  to "Hired prior counsel",
    "none"           to "None of the above",
)

private val NOTICE_BASES = listOf(
    "cheque_bounce"   to "Cheque bounce",
    "contract_breach" to "Contract breach",
    "employment"      to "Employment dispute",
    "consumer"        to "Consumer complaint",
    "recovery"        to "Money recovery",
    "other"           to "Other",
)

@Composable
private fun StepIntake(form: NewConsultationForm, vm: NewConsultationViewModel) {
    val spacing = LocalSpacing.current
    val isNotice = form.type == ConsultationKind.LEGAL_NOTICE
    val isGeneral = form.type == ConsultationKind.GENERAL

    Column(
        modifier            = Modifier.fillMaxWidth().padding(horizontal = spacing.s5),
        verticalArrangement = Arrangement.spacedBy(spacing.s5),
    ) {
        Spacer(Modifier.height(spacing.s2))

        // Quick brief — what to expect
        IntakeHint(
            text = "A few targeted answers help your advocate prep faster — and shave hours off the back-and-forth later. * = required.",
        )

        // ─ Section A · Subject + matter category + urgency ──────────────────
        SectionHeader("Matter")

        OutlinedTextField(
            value         = form.tag,
            onValueChange = vm::setTag,
            label         = { Text("Subject (optional)") },
            placeholder   = { Text("e.g. Vendor not honouring service contract") },
            singleLine    = true,
            modifier      = Modifier.fillMaxWidth(),
            shape         = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction      = ImeAction.Next,
            ),
            colors = consultationFieldColors(),
        )

        FieldLabel("Matter category *")
        ChipFlow(
            options  = MATTER_CATEGORIES,
            selected = setOf(form.category),
            onClick  = { v -> vm.setCategory(if (form.category == v) "" else v) },
        )

        FieldLabel("How urgent is this? *")
        ChipFlow(
            options  = URGENCY_OPTIONS,
            selected = setOf(form.urgency),
            onClick  = { v -> vm.setUrgency(if (form.urgency == v) "" else v) },
        )

        // ─ Section B · What happened (required) ─────────────────────────────
        SectionHeader("What happened")

        OutlinedTextField(
            value         = form.summary,
            onValueChange = vm::setSummary,
            label         = { Text("Describe the situation *") },
            placeholder   = {
                Text(
                    if (isNotice)
                        "Brief background — what went wrong, what the other side did, what you've already tried."
                    else
                        "What does the other side want, or what do you want from them? Include dates and amounts if relevant.",
                )
            },
            minLines      = 5,
            maxLines      = 12,
            modifier      = Modifier.fillMaxWidth(),
            shape         = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            supportingText = {
                val len = form.summary.trim().length
                Text(
                    text  = if (len < 30) "Minimum 30 characters ($len/30)" else "$len characters",
                    color = if (len < 30) StatusDanger.solid else TertiaryText,
                )
            },
            colors = consultationFieldColors(),
        )

        // ─ Section C · Parties (general + notice) ───────────────────────────
        if (isGeneral || isNotice) {
            SectionHeader("People involved", subtitle = "Optional — but speeds up triage.")

            OutlinedTextField(
                value         = form.partyRole,
                onValueChange = vm::setPartyRole,
                label         = { Text("Your role") },
                placeholder   = { Text("e.g. Buyer, tenant, employee, customer") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = consultationFieldColors(),
            )
            OutlinedTextField(
                value         = form.counterPartyName,
                onValueChange = vm::setCounterPartyName,
                label         = { Text("Other party's name") },
                placeholder   = { Text("Individual or business") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                colors = consultationFieldColors(),
            )
            OutlinedTextField(
                value         = form.relationship,
                onValueChange = vm::setRelationship,
                label         = { Text("How are they related to you?") },
                placeholder   = { Text("e.g. Former employer, landlord, vendor") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = consultationFieldColors(),
            )
        }

        // ─ Section D · Prior steps (general + notice) ───────────────────────
        if (isGeneral || isNotice) {
            FieldLabel("What have you already tried?")
            ChipFlow(
                options  = PRIOR_STEPS,
                selected = form.priorSteps,
                onClick  = { vm.togglePriorStep(it) },
                multi    = true,
            )
        }

        // ─ Section E · Legal Notice specifics ───────────────────────────────
        if (isNotice) {
            SectionHeader("Notice specifics", subtitle = "Who the notice goes to, and what you're asking for.")

            OutlinedTextField(
                value         = form.noticeRecipientName,
                onValueChange = vm::setNoticeRecipientName,
                label         = { Text("Recipient name *") },
                placeholder   = { Text("Person or business the notice is for") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                colors = consultationFieldColors(),
            )
            OutlinedTextField(
                value         = form.noticeRecipientAddress,
                onValueChange = vm::setNoticeRecipientAddress,
                label         = { Text("Recipient address") },
                placeholder   = { Text("Full postal address — used to serve the notice") },
                minLines      = 2,
                maxLines      = 4,
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = consultationFieldColors(),
            )
            OutlinedTextField(
                value         = form.noticeRecipientContact,
                onValueChange = vm::setNoticeRecipientContact,
                label         = { Text("Recipient phone / email") },
                placeholder   = { Text("If known") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(12.dp),
                colors = consultationFieldColors(),
            )
            OutlinedTextField(
                value         = form.noticeClaimAmount,
                onValueChange = vm::setNoticeClaimAmount,
                label         = { Text("Claim amount (₹)") },
                placeholder   = { Text("Digits only — leave blank if not monetary") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = consultationFieldColors(),
            )
            FieldLabel("Basis for the notice")
            ChipFlow(
                options  = NOTICE_BASES,
                selected = setOf(form.noticeBasis),
                onClick  = { v -> vm.setNoticeBasis(if (form.noticeBasis == v) "" else v) },
            )
            OutlinedTextField(
                value         = form.noticeDemand,
                onValueChange = vm::setNoticeDemand,
                label         = { Text("What are you asking for?") },
                placeholder   = { Text("e.g. Pay outstanding ₹50,000 within 15 days, return possession, …") },
                minLines      = 2,
                maxLines      = 5,
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = consultationFieldColors(),
            )
        }

        // ─ Section F · Priority + urgent ────────────────────────────────────
        SectionHeader("Priority")

        Row(horizontalArrangement = Arrangement.spacedBy(spacing.s2)) {
            listOf("Low", "Medium", "High").forEach { p ->
                PriorityChip(label = p, selected = form.priority == p, onClick = { vm.setPriority(p) })
            }
        }

        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (form.isUrgent) StatusDanger.softBg else Surface1)
                .clickable { vm.setUrgent(!form.isUrgent) }
                .padding(spacing.s4),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text  = "Mark as urgent",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = if (form.isUrgent) StatusDanger.solid else PrimaryText,
                )
                Text(
                    text  = "Advocate will prioritise this request",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (form.isUrgent) StatusDanger.solid.copy(alpha = 0.7f) else TertiaryText,
                )
            }
            Switch(
                checked         = form.isUrgent,
                onCheckedChange = vm::setUrgent,
                colors          = SwitchDefaults.colors(checkedThumbColor = StatusDanger.solid, checkedTrackColor = StatusDanger.softBg),
            )
        }

        Spacer(Modifier.height(spacing.s3))
    }
}

// ─── Intake atoms ─────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, subtitle: String? = null) {
    Column {
        Text(
            text  = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = PrimaryText,
        )
        if (subtitle != null) {
            Text(
                text  = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TertiaryText,
            )
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text  = text,
        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
        color = SecondaryText,
    )
}

@Composable
private fun IntakeHint(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Surface2)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Outlined.Info, null, tint = TertiaryText, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = SecondaryText)
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ChipFlow(
    options: List<Pair<String, String>>,
    selected: Set<String>,
    onClick: (String) -> Unit,
    multi: Boolean = false,
) {
    // FlowRow keeps chips on multiple lines without forcing a horizontal scroll.
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement   = Arrangement.spacedBy(8.dp),
        modifier              = Modifier.fillMaxWidth(),
    ) {
        options.forEach { (value, label) ->
            val sel = selected.contains(value)
            ChoiceChip(label = label, selected = sel, onClick = { onClick(value) }, multi = multi)
        }
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit, multi: Boolean) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) AccentInk else Surface1)
            .border(1.dp, if (selected) Color.Transparent else BorderSubtle, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (multi && selected) {
            Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text  = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) Color.White else SecondaryText,
        )
    }
}

@Composable
private fun PriorityChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val color = when (label) {
        "High" -> StatusDanger.solid
        "Low"  -> StatusSettled.solid
        else   -> StatusPending.solid
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) color else Surface1)
            .border(1.dp, if (selected) Color.Transparent else BorderSubtle, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text  = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) Color.White else SecondaryText,
        )
    }
}

// ─── Step 3: Review ───────────────────────────────────────────────────────────

@Composable
private fun StepReview(form: NewConsultationForm) {
    val spacing = LocalSpacing.current
    Column(
        modifier            = Modifier.fillMaxWidth().padding(horizontal = spacing.s5),
        verticalArrangement = Arrangement.spacedBy(spacing.s4),
    ) {
        Spacer(Modifier.height(spacing.s2))

        // Type card
        val typeOpt = typeOptions.first { it.kind == form.type }
        Card(colors = CardDefaults.cardColors(containerColor = Surface1), shape = RoundedCornerShape(12.dp)) {
            Row(
                modifier              = Modifier.padding(spacing.s4),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.s3),
            ) {
                Box(
                    modifier         = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Surface2),
                    contentAlignment = Alignment.Center,
                ) { Icon(typeOpt.icon, null, tint = PrimaryText, modifier = Modifier.size(22.dp)) }
                Column {
                    Text(typeOpt.headline, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = PrimaryText)
                    Text("Request type", style = MaterialTheme.typography.bodySmall, color = TertiaryText)
                }
            }
        }

        // Matter summary
        Card(colors = CardDefaults.cardColors(containerColor = Surface1), shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(spacing.s4), verticalArrangement = Arrangement.spacedBy(spacing.s3)) {
                if (form.tag.isNotBlank()) {
                    ReviewRow("Subject", form.tag)
                    HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)
                }
                form.category.takeIf { it.isNotBlank() }?.let {
                    ReviewRow("Category", labelFor(MATTER_CATEGORIES, it))
                    HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)
                }
                form.urgency.takeIf { it.isNotBlank() }?.let {
                    ReviewRow("Urgency", labelFor(URGENCY_OPTIONS, it))
                    HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)
                }
                ReviewRow("What happened", form.summary.trim().take(260) + if (form.summary.length > 260) "…" else "")
            }
        }

        // People involved (when present)
        val anyParty = form.partyRole.isNotBlank() || form.counterPartyName.isNotBlank() || form.relationship.isNotBlank()
        if (anyParty) {
            Card(colors = CardDefaults.cardColors(containerColor = Surface1), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(spacing.s4), verticalArrangement = Arrangement.spacedBy(spacing.s3)) {
                    if (form.partyRole.isNotBlank())         ReviewRow("Your role", form.partyRole)
                    if (form.counterPartyName.isNotBlank())  ReviewRow("Other party", form.counterPartyName)
                    if (form.relationship.isNotBlank())      ReviewRow("Relationship", form.relationship)
                }
            }
        }

        // Prior steps
        if (form.priorSteps.isNotEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = Surface1), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(spacing.s4)) {
                    ReviewRow(
                        "Prior steps taken",
                        form.priorSteps.joinToString(", ") { labelFor(PRIOR_STEPS, it) },
                    )
                }
            }
        }

        // Notice specifics
        if (form.type == ConsultationKind.LEGAL_NOTICE) {
            Card(colors = CardDefaults.cardColors(containerColor = Surface1), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(spacing.s4), verticalArrangement = Arrangement.spacedBy(spacing.s3)) {
                    ReviewRow("Recipient", form.noticeRecipientName.ifBlank { "—" })
                    if (form.noticeRecipientAddress.isNotBlank()) {
                        HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)
                        ReviewRow("Address", form.noticeRecipientAddress)
                    }
                    if (form.noticeRecipientContact.isNotBlank()) {
                        HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)
                        ReviewRow("Contact", form.noticeRecipientContact)
                    }
                    if (form.noticeClaimAmount.isNotBlank()) {
                        HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)
                        ReviewRow("Claim amount", "₹${form.noticeClaimAmount}")
                    }
                    if (form.noticeBasis.isNotBlank()) {
                        HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)
                        ReviewRow("Basis", labelFor(NOTICE_BASES, form.noticeBasis))
                    }
                    if (form.noticeDemand.isNotBlank()) {
                        HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)
                        ReviewRow("Demand", form.noticeDemand)
                    }
                }
            }
        }

        // Priority + urgent
        Card(colors = CardDefaults.cardColors(containerColor = Surface1), shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(spacing.s4), verticalArrangement = Arrangement.spacedBy(spacing.s3)) {
                ReviewRow("Priority", form.priority)
                if (form.isUrgent) {
                    HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)
                    ReviewRow("Urgency", "Marked urgent")
                }
            }
        }

        Text(
            text  = "Once submitted, your advocate will review this request and reach out — they'll propose a time if a call is needed.",
            style = MaterialTheme.typography.bodySmall,
            color = TertiaryText,
        )

        Spacer(Modifier.height(spacing.s3))
    }
}

@Composable
private fun ReviewRow(label: String, value: String) {
    Column {
        EyebrowText(label, color = TertiaryText)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = PrimaryText)
    }
}

private fun labelFor(options: List<Pair<String, String>>, value: String): String =
    options.firstOrNull { it.first == value }?.second ?: value

// ─── Footer CTA ───────────────────────────────────────────────────────────────

@Composable
private fun WizardFooter(
    form: NewConsultationForm,
    vm: NewConsultationViewModel,
) {
    val spacing = LocalSpacing.current
    val canAdvance = when (form.step) {
        WizardStep.TYPE   -> true
        WizardStep.INTAKE -> vm.canAdvanceFromIntake()
        WizardStep.REVIEW -> !form.isSubmitting
    }

    Surface(
        tonalElevation = 0.dp,
        color          = MaterialTheme.colorScheme.background,
        modifier       = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = spacing.s5).padding(top = spacing.s3, bottom = spacing.s6)) {
            HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp, modifier = Modifier.padding(bottom = spacing.s4))
            LegumPrimaryButton(
                text     = when (form.step) {
                    WizardStep.REVIEW -> if (form.isSubmitting) "Submitting…" else "Submit request"
                    else              -> "Continue"
                },
                onClick  = { if (form.step == WizardStep.REVIEW) vm.submit() else vm.next() },
                enabled  = canAdvance,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

@Composable
private fun consultationFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = AccentInk,
    unfocusedBorderColor = BorderSubtle,
    focusedLabelColor    = AccentInk,
    cursorColor          = AccentInk,
)
