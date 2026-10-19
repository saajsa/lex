package com.legumlex.client.ahc

import com.legumlex.client.ahc.model.*
import com.legumlex.client.ahc.theme.FindingSeverity
import com.legumlex.client.ahc.theme.RiskLevel

/* ============================================================================
 *  Sample data — real-world strings from the workflow (Saaj, Adv. Anjali Rao,
 *  TechNova Pvt Ltd; ₹ INR; IST; dd MMM yyyy). Drives @Preview + UI tests.
 * ========================================================================== */
object AhcSamples {

    private val advRao = Advocate(
        initials = "AR",
        name = "Adv. Anjali Rao",
        role = "Associate · Contracts & Advisory",
    )

    private val msaDoc = SubmittedDoc(
        fileName = "TechNova-MSA-signed.pdf",
        sizeLabel = "640 KB",
        pageCount = 14,
        hasDocument = true,
    )

    private const val NAME = "Vendor MSA — TechNova Pvt Ltd"

    // ---- Pending (fresh, ~29:54 left) --------------------------------------
    val pendingFresh = AhcUiState.Pending(
        agreementName = NAME, doc = msaDoc, advocate = advRao,
        submittedAtLabel = "9:42 AM", etaLabel = "10:12 AM IST",
        remainingSeconds = 29 * 60 + 54,
    )

    // ---- Pending (mid-review, ~14:22 left) ---------------------------------
    val pendingHalfway = pendingFresh.copy(remainingSeconds = 14 * 60 + 22)

    // ---- Overdue (SLA elapsed) ---------------------------------------------
    val overdue = pendingFresh.copy(remainingSeconds = 0, etaLabel = "10:12 AM IST")

    // ---- Released · MODERATE (the reference layout) ------------------------
    val releasedModerate = AhcUiState.Released(
        agreementName = NAME, advocate = advRao, releasedAtLabel = "10:08 AM",
        riskScore = 62, riskLevel = RiskLevel.MEDIUM,
        teaserSummary = "Workable, but two clauses need attention before you sign. " +
            "Nothing here blocks the deal — these are fixes, not deal-breakers.",
        clausesReviewed = 18,
        advocateNote = AdvocateNote(
            paragraphs = listOf(
                "I've read the full agreement. It's workable, but not quite as-is — two things to fix before signing:",
                "The Bengaluru jurisdiction is standard for vendors and usually fine. Happy to send a redline if you'd like one.",
            ),
            bullets = listOf(
                "Liability is uncapped — ask for a cap at 12 months' fees.",
                "It auto-renews; set a reminder 60 days before renewal.",
            ),
            signedBy = "Adv. Anjali Rao", signedAt = "10:08 AM",
        ),
        parties = listOf(Party("You", "Customer"), Party("Other", "TechNova Pvt Ltd")),
        keyTerms = listOf(
            KeyTerm("Agreement type", "Master Services Agreement"),
            KeyTerm("Effective date", "01 Jul 2026"),
            KeyTerm("Duration", "12 months · auto-renew"),
            KeyTerm("Annual value", "₹18,00,000", emphasis = true),
        ),
        keyRisks = listOf(
            Finding("Unlimited liability", "No cap on what you could owe for damages.", FindingSeverity.CRITICAL),
            Finding("Auto-renewal clause", "Renews for 12 months unless cancelled 60 days prior.", FindingSeverity.WARN),
            Finding("Governing law — Bengaluru", "Disputes are heard in Bengaluru courts, not Mumbai.", FindingSeverity.WARN),
        ),
        missingProtections = listOf(
            Finding("No liability cap", "There's no ceiling on damages — add one.", FindingSeverity.CRITICAL),
            Finding("No exit for breach", "You can't terminate early if they default.", FindingSeverity.WARN),
        ),
        recommendations = listOf(
            "Ask for a liability cap of 12 months' fees before signing.",
            "Add a 30-day cure-and-terminate clause for material breach.",
            "Calendar a renewal reminder for 60 days before 01 Jul.",
        ),
        doc = msaDoc,
        activity = listOf(
            ActivityEntry("Submitted for review", "15 Jun, 9:42 AM", DotState.DONE),
            ActivityEntry("Lex review completed", "15 Jun, 9:58 AM", DotState.DONE),
            ActivityEntry("Adv. Rao released the report", "15 Jun, 10:08 AM", DotState.CURRENT),
        ),
    )

    // ---- Released · LOW (clean, released as-is, no note) -------------------
    val releasedLow = releasedModerate.copy(
        agreementName = "Rental — Bandra flat",
        riskScore = 88, riskLevel = RiskLevel.LOW,
        teaserSummary = "A standard leave-and-licence agreement with balanced terms. One minor point to note.",
        clausesReviewed = 12, advocateNote = null,
        keyTerms = listOf(
            KeyTerm("Agreement type", "Leave & Licence"),
            KeyTerm("Duration", "11 months"),
            KeyTerm("Monthly rent", "₹68,000", emphasis = true),
        ),
        keyRisks = listOf(
            Finding("Notice period — 2 months", "You must give two months' notice to vacate.", FindingSeverity.WARN),
        ),
        missingProtections = emptyList(),
        recommendations = listOf("Keep a signed copy and the rent receipts together for renewal."),
        doc = msaDoc.copy(fileName = "Rental-Bandra-flat.pdf", pageCount = 9, sizeLabel = "420 KB"),
    )

    // ---- Released · HIGH (claret, soft nudge in the note) ------------------
    val releasedHigh = releasedModerate.copy(
        riskScore = 34, riskLevel = RiskLevel.HIGH,
        teaserSummary = "Several clauses tilt heavily against you. Signable, but worth a conversation first.",
        advocateNote = AdvocateNote(
            paragraphs = listOf(
                "I've gone through this carefully. It's signable, but the balance is not in your favour on a few points.",
                "None of this is urgent, but book a consultation when convenient — I'd like to talk you through the indemnity clause before you commit.",
            ),
            bullets = listOf(
                "Broad indemnity — you cover their losses with no cap.",
                "Termination is one-sided in their favour.",
                "IP assignment is wider than the scope of work.",
            ),
            signedBy = "Adv. Anjali Rao", signedAt = "10:08 AM",
        ),
    )

    // ---- Released with a non-fatal section failure -------------------------
    val releasedPartialFailure = releasedModerate.copy(partialFailureSection = "the document preview")

    // ---- Escalated · awaiting booking --------------------------------------
    val escalatedAwaiting = AhcUiState.Escalated(
        agreementName = NAME, advocate = advRao, escalatedAtLabel = "10:10 AM",
        escalationReason = "This agreement has structural issues that really need a conversation — a written summary risks being misread.",
        booking = EscalationBooking.AwaitingBooking,
        activity = listOf(
            ActivityEntry("Submitted for review", "15 Jun, 9:42 AM", DotState.DONE),
            ActivityEntry("Lex review completed", "15 Jun, 9:58 AM", DotState.DONE),
            ActivityEntry("Adv. Rao escalated — asked to meet", "15 Jun, 10:10 AM", DotState.CURRENT),
        ),
    )

    // ---- Escalated · consultation booked -----------------------------------
    val escalatedBooked = escalatedAwaiting.copy(
        booking = EscalationBooking.Booked(reference = "#C-218", whenLabel = "Started 15 Jun, 10:55 AM"),
        activity = escalatedAwaiting.activity + ActivityEntry("Consultation #C-218 started", "15 Jun, 10:55 AM", DotState.CURRENT),
    )

    // ---- Closed · completed (released, no follow-up) -----------------------
    val closedCompleted = AhcUiState.Closed(
        agreementName = NAME, advocate = advRao,
        kind = ClosureKind.COMPLETED,
        title = "Closed · Completed",
        subtitle = "You reviewed the released report and marked this done.",
        closedOnLabel = "15 Jun 2026",
        recap = listOf(
            RecapRow("Verdict", "Moderate risk · 62/100"),
            RecapRow("Findings", "5 flagged"),
            RecapRow("Released", "15 Jun, 10:08 AM", mono = true),
        ),
        link = null,
        reportDoc = msaDoc,
        stripText = "No follow-up was needed.",
        activity = listOf(
            ActivityEntry("Submitted for review", "15 Jun, 9:42 AM", DotState.DONE),
            ActivityEntry("Report released", "15 Jun, 10:08 AM", DotState.DONE),
            ActivityEntry("Closed", "15 Jun, 6:04 PM", DotState.DONE),
        ),
    )

    // ---- Closed · resolved via consultation --------------------------------
    val closedResolved = AhcUiState.Closed(
        agreementName = NAME, advocate = advRao,
        kind = ClosureKind.RESOLVED_VIA_CONSULTATION,
        title = "Closed · Resolved via consultation",
        subtitle = "Escalated to a personal consultation, which has since wrapped up.",
        closedOnLabel = "15 Jun 2026",
        recap = listOf(
            RecapRow("Outcome", "Escalated · not released"),
            RecapRow("Resolved by", "Consultation #C-218"),
        ),
        link = ClosureLink(
            ClosureLinkTarget.CONSULTATION, "Personal consultation", "#C-218", "Open the consultation",
        ),
        reportDoc = null,
        stripText = null,
        activity = listOf(
            ActivityEntry("Submitted for review", "15 Jun, 9:42 AM", DotState.DONE),
            ActivityEntry("Escalated to a consultation", "15 Jun, 10:10 AM", DotState.DONE),
            ActivityEntry("Closed", "15 Jun, 5:40 PM", DotState.DONE),
        ),
    )

    // ---- Closed · client cancelled -----------------------------------------
    val closedCancelled = AhcUiState.Closed(
        agreementName = NAME, advocate = advRao,
        kind = ClosureKind.CANCELLED,
        title = "Closed · You cancelled this",
        subtitle = "You stood this request down before it was completed.",
        closedOnLabel = "13 Jun 2026",
        recap = listOf(
            RecapRow("Your reason", "“Settled it directly with the other party.”"),
        ),
        link = null,
        reportDoc = null,
        stripText = "Nothing was sent. No follow-up is open.",
        activity = listOf(
            ActivityEntry("Submitted for review", "12 Jun, 2:05 PM", DotState.DONE),
            ActivityEntry("Closed", "13 Jun, 11:24 AM", DotState.DONE),
        ),
    )

    // ---- Loading / Empty / Error -------------------------------------------
    val loading = AhcUiState.Loading
    val empty = AhcUiState.Empty(agreementName = NAME)
    val error = AhcUiState.Error(agreementName = NAME, code = "AHC-503 · request timed out")
}
