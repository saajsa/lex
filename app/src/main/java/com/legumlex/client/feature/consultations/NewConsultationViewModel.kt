package com.legumlex.client.feature.consultations

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.legumlex.client.data.api.ApiClient
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Wizard now has three steps — TYPE → INTAKE → REVIEW. SCHEDULE was dropped
 * because the firm controls timing; the client doesn't book the advocate.
 * Intake is one shared rich form with kind-conditional sections (parties,
 * prior steps, notice specifics) so the advocate gets a triage-ready brief
 * instead of a one-line "describe your situation" textbox.
 */
enum class WizardStep { TYPE, INTAKE, REVIEW }

data class NewConsultationForm(
    val step: WizardStep = WizardStep.TYPE,

    // Step 1 — kind selection
    val type: ConsultationKind = ConsultationKind.GENERAL,

    // Step 2 — intake
    val tag: String = "",            // subject (optional)
    val summary: String = "",        // free-text "what happened" (required)

    val category: String = "",       // matter area: employment, property, family…
    val urgency: String = "",        // today / this_week / this_month / exploring

    val partyRole: String = "",      // buyer, tenant, employee…
    val counterPartyName: String = "",
    val relationship: String = "",   // free text — how do you know them?

    val priorSteps: Set<String> = emptySet(), // tried_directly, sent_emails, hired_counsel, none

    // Legal-notice-specific (only collected when type == LEGAL_NOTICE)
    val noticeRecipientName: String = "",
    val noticeRecipientAddress: String = "",
    val noticeRecipientContact: String = "",
    val noticeClaimAmount: String = "",
    val noticeBasis: String = "",        // cheque_bounce, contract_breach, employment, consumer, other
    val noticeDemand: String = "",

    val priority: String = "Medium",
    val isUrgent: Boolean = false,

    val isSubmitting: Boolean = false,
)

sealed interface WizardEvent {
    data class Success(val consultationId: String) : WizardEvent
    data class Error(val message: String) : WizardEvent
}

class NewConsultationViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ConsultationsRepository(ApiClient.get(app))

    private val _form = MutableStateFlow(NewConsultationForm())
    val form: StateFlow<NewConsultationForm> = _form.asStateFlow()

    private val _events = MutableSharedFlow<WizardEvent>()
    val events: SharedFlow<WizardEvent> = _events.asSharedFlow()

    // ── Setters ────────────────────────────────────────────────────────────────
    fun setType(kind: ConsultationKind) = _form.update { it.copy(type = kind) }
    fun setTag(v: String)               = _form.update { it.copy(tag = v) }
    fun setSummary(v: String)           = _form.update { it.copy(summary = v) }

    fun setCategory(v: String) = _form.update { it.copy(category = v) }
    fun setUrgency(v: String)  = _form.update { it.copy(urgency = v) }

    fun setPartyRole(v: String)        = _form.update { it.copy(partyRole = v) }
    fun setCounterPartyName(v: String) = _form.update { it.copy(counterPartyName = v) }
    fun setRelationship(v: String)     = _form.update { it.copy(relationship = v) }

    fun togglePriorStep(v: String) = _form.update { f ->
        val set = f.priorSteps.toMutableSet()
        // "None" is mutually exclusive with the other options.
        if (v == "none") {
            if (set.contains("none")) f.copy(priorSteps = emptySet())
            else f.copy(priorSteps = setOf("none"))
        } else {
            set.remove("none")
            if (set.contains(v)) set.remove(v) else set.add(v)
            f.copy(priorSteps = set)
        }
    }

    fun setNoticeRecipientName(v: String)    = _form.update { it.copy(noticeRecipientName = v) }
    fun setNoticeRecipientAddress(v: String) = _form.update { it.copy(noticeRecipientAddress = v) }
    fun setNoticeRecipientContact(v: String) = _form.update { it.copy(noticeRecipientContact = v) }
    fun setNoticeClaimAmount(v: String)      = _form.update { it.copy(noticeClaimAmount = v.filter { c -> c.isDigit() }) }
    fun setNoticeBasis(v: String)            = _form.update { it.copy(noticeBasis = v) }
    fun setNoticeDemand(v: String)           = _form.update { it.copy(noticeDemand = v) }

    fun setPriority(v: String) = _form.update { it.copy(priority = v) }
    fun setUrgent(v: Boolean)  = _form.update { it.copy(isUrgent = v) }

    // ── Step nav ───────────────────────────────────────────────────────────────
    fun next() = _form.update {
        val next = when (it.step) {
            WizardStep.TYPE   -> WizardStep.INTAKE
            WizardStep.INTAKE -> WizardStep.REVIEW
            WizardStep.REVIEW -> it.step
        }
        it.copy(step = next)
    }

    fun back() = _form.update {
        val prev = when (it.step) {
            WizardStep.TYPE   -> it.step
            WizardStep.INTAKE -> WizardStep.TYPE
            WizardStep.REVIEW -> WizardStep.INTAKE
        }
        it.copy(step = prev)
    }

    /** Required: category + urgency + ≥30-char summary + (for Notice) recipient name. */
    fun canAdvanceFromIntake(): Boolean {
        val f = _form.value
        val base = f.category.isNotBlank() &&
                   f.urgency.isNotBlank() &&
                   f.summary.trim().length >= 30
        if (!base) return false
        if (f.type == ConsultationKind.LEGAL_NOTICE) {
            return f.noticeRecipientName.trim().isNotEmpty()
        }
        return true
    }

    fun submit() = viewModelScope.launch {
        val f = _form.value
        if (f.isSubmitting) return@launch
        _form.update { it.copy(isSubmitting = true) }

        // Serialize the structured intake into a [INTAKE:v1] block prepended
        // to the free-text summary. Existing admin UI still reads `note` as
        // text; a future admin-side parser can pretty-print the JSON block.
        val composedNote = composeNote(f)

        repo.create(
            type          = f.type.apiValue,
            tag           = f.tag.trim(),
            note          = composedNote,
            isUrgent      = f.isUrgent,
            priority      = f.priority,
            preferredDate = "",     // SCHEDULE step retired — never collect a date
            preferredTime = "",
            caseLinkId    = "",
        ).onSuccess { id ->
            _events.emit(WizardEvent.Success(id))
        }.onFailure { e ->
            _form.update { it.copy(isSubmitting = false) }
            _events.emit(WizardEvent.Error(e.message ?: "Submission failed"))
        }
    }

    private fun composeNote(f: NewConsultationForm): String {
        val json = JSONObject().apply {
            putIfNotBlank("category", f.category)
            putIfNotBlank("urgency", f.urgency)
            putIfNotBlank("party_role", f.partyRole)
            putIfNotBlank("counter_party_name", f.counterPartyName)
            putIfNotBlank("relationship", f.relationship)
            if (f.priorSteps.isNotEmpty()) put("prior_steps", f.priorSteps.toList())
            if (f.type == ConsultationKind.LEGAL_NOTICE) {
                val notice = JSONObject().apply {
                    putIfNotBlank("recipient_name", f.noticeRecipientName)
                    putIfNotBlank("recipient_address", f.noticeRecipientAddress)
                    putIfNotBlank("recipient_contact", f.noticeRecipientContact)
                    putIfNotBlank("claim_amount", f.noticeClaimAmount)
                    putIfNotBlank("basis", f.noticeBasis)
                    putIfNotBlank("demand", f.noticeDemand)
                }
                if (notice.length() > 0) put("notice", notice)
            }
        }
        return buildString {
            if (json.length() > 0) {
                append("[INTAKE:v1]\n").append(json.toString()).append("\n[/INTAKE]\n\n")
            }
            append(f.summary.trim())
        }
    }
}

private fun JSONObject.putIfNotBlank(key: String, value: String) {
    if (value.isNotBlank()) put(key, value.trim())
}
