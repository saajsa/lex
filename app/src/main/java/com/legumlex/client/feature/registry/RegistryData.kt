package com.legumlex.client.feature.registry

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.legumlex.client.data.api.ApiClient
import com.legumlex.client.data.api.ApiService
import com.legumlex.client.ui.navigation.Route
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/* The contract register (customers_api `registry`), read-only: what Mylex's
 * /mylex/register and /mylex/register/{id} show. Entering and editing stays
 * on the portal. */

data class RegisterAgreement(
    val id: String,
    val title: String,
    val reference: String?,
    val counterparty: String?,
    val counterpartyType: String?,
    val category: String?,
    val status: String,
    val effective: LocalDate?,
    val expiry: LocalDate?,
    val signed: LocalDate?,
    val renewalType: String?,
    val renewalTermMonths: Int?,
    val noticeDays: Int?,
    val noticeBy: LocalDate?,
    val value: Double?,
    val currency: String,
    val hasSignedCopy: Boolean,
    val openDeadlines: Int,
    val paymentTerms: String? = null,
    val governingLaw: String? = null,
)

data class Deadline(
    val id: String,
    val agreementId: String,
    val agreementTitle: String?,
    val kindLabel: String,
    val title: String,
    val description: String?,
    val due: LocalDate?,
    val status: String,        // open | done | waived
    val bucket: String?,       // overdue | soon | later | closed
    val daysToDue: Int?,
)

data class RegistryOverview(
    val active: Int,
    val expiring90: Int,
    val notice30: Int,
    val noCopy: Int,
    val overdue: Int,
    val soon: Int,
    val upcoming: List<Deadline>,
    val agreements: List<RegisterAgreement>,
)

data class RegistryDetail(val agreement: RegisterAgreement, val deadlines: List<Deadline>)

sealed interface Loadable<out T> {
    data object Loading : Loadable<Nothing>
    data class Ready<T>(val data: T) : Loadable<T>
    data class Failed(val message: String) : Loadable<Nothing>
}

class RegistryRepository(private val api: ApiService) {
    suspend fun overview(): Result<RegistryOverview> = runCatching {
        val d = api.registry().dataMap()
        val p = d["portfolio"] as? Map<*, *>
        val o = d["deadlines"] as? Map<*, *>
        RegistryOverview(
            active     = p.int("active"),
            expiring90 = p.int("expiring_90"),
            notice30   = p.int("notice_30"),
            noCopy     = p.int("no_copy"),
            overdue    = o.int("overdue"),
            soon       = o.int("soon"),
            upcoming   = (d["upcoming"] as? List<*>).orEmpty().mapNotNull { (it as? Map<*, *>)?.toDeadline() },
            agreements = (d["agreements"] as? List<*>).orEmpty().mapNotNull { (it as? Map<*, *>)?.toAgreement() },
        )
    }.friendly()

    suspend fun detail(id: String): Result<RegistryDetail> = runCatching {
        val d = api.registryAgreement(id).dataMap()
        RegistryDetail(
            agreement = (d["agreement"] as Map<*, *>).toAgreement(),
            deadlines = (d["obligations"] as? List<*>).orEmpty().mapNotNull { (it as? Map<*, *>)?.toDeadline() },
        )
    }.friendly()
}

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.dataMap(): Map<String, Any?> = this["data"] as? Map<String, Any?> ?: emptyMap()
private fun Map<*, *>?.int(k: String) = (this?.get(k) as? Number)?.toInt() ?: 0
private fun Map<*, *>.str(k: String) = (this[k] as? String)?.takeIf { it.isNotBlank() }
private fun Map<*, *>.date(k: String) = str(k)?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

private fun Map<*, *>.toAgreement() = RegisterAgreement(
    id                = (this["id"] as? Number)?.toInt()?.toString() ?: str("id").orEmpty(),
    title             = str("title") ?: "Untitled agreement",
    reference         = str("reference_no"),
    counterparty      = str("counterparty_name"),
    counterpartyType  = str("counterparty_type"),
    category          = str("category"),
    status            = str("status") ?: "active",
    effective         = date("effective_date"),
    expiry            = date("expiry_date"),
    signed            = date("signed_date"),
    renewalType       = str("renewal_type"),
    renewalTermMonths = (this["renewal_term_months"] as? Number)?.toInt(),
    noticeDays        = (this["notice_period_days"] as? Number)?.toInt(),
    noticeBy          = date("notice_deadline_date"),
    value             = str("contract_value")?.toDoubleOrNull(),
    currency          = str("currency") ?: "INR",
    hasSignedCopy     = this["has_signed_copy"] == true,
    openDeadlines     = int("open_deadlines"),
    paymentTerms      = str("payment_terms"),
    governingLaw      = str("governing_law"),
)

private fun Map<*, *>.toDeadline() = Deadline(
    id             = (this["id"] as? Number)?.toInt()?.toString().orEmpty(),
    agreementId    = (this["agreement_id"] as? Number)?.toInt()?.toString().orEmpty(),
    agreementTitle = str("agreement_title"),
    kindLabel      = str("kind_label") ?: "Deadline",
    title          = str("title") ?: "Deadline",
    description    = str("description"),
    due            = date("due_date"),
    status         = str("status") ?: "open",
    bucket         = str("bucket"),
    daysToDue      = (this["days_to_due"] as? Number)?.toInt(),
)

private fun <T> Result<T>.friendly(): Result<T> = recoverCatching { e ->
    throw IllegalStateException(
        when {
            e is HttpException && e.code() == 403 -> "Your account does not include the register. Please ask the firm."
            e is HttpException && e.code() == 404 -> "This agreement is not on your register."
            e is java.io.IOException -> "Network error. Please try again."
            else -> e.message ?: "Something went wrong."
        }
    )
}

class RegistryViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = RegistryRepository(ApiClient.get(app))
    private val _state = MutableStateFlow<Loadable<RegistryOverview>>(Loadable.Loading)
    val state: StateFlow<Loadable<RegistryOverview>> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            if (_state.value !is Loadable.Ready) _state.value = Loadable.Loading
            repo.overview()
                .onSuccess { _state.value = Loadable.Ready(it) }
                .onFailure { _state.value = Loadable.Failed(it.message ?: "") }
        }
    }
}

class RegistryAgreementViewModel(app: Application, saved: SavedStateHandle) : AndroidViewModel(app) {
    private val id: String = saved[Route.RegistryAgreement.ARG] ?: ""
    private val repo = RegistryRepository(ApiClient.get(app))
    private val _state = MutableStateFlow<Loadable<RegistryDetail>>(Loadable.Loading)
    val state: StateFlow<Loadable<RegistryDetail>> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = Loadable.Loading
            repo.detail(id)
                .onSuccess { _state.value = Loadable.Ready(it) }
                .onFailure { _state.value = Loadable.Failed(it.message ?: "") }
        }
    }
}

// ---- Formatting, as the portal writes it ------------------------------------

private val dayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

internal fun LocalDate?.long(): String = this?.format(dayMonthYear) ?: "Not Recorded"

/** ₹3,000 · ₹1.49 L · ₹1.2 Cr — Indian grouping, lakh and crore as the portal shows them. */
internal fun compactInr(amount: Double?, currency: String = "INR"): String {
    if (amount == null || amount == 0.0) return "—"
    val sym = if (currency.equals("INR", true)) "₹" else "$currency "
    fun trim(v: Double) = NumberFormat.getNumberInstance(Locale.ENGLISH).apply { maximumFractionDigits = 2 }.format(v)
    return when {
        amount >= 1_00_00_000 -> "$sym${trim(amount / 1_00_00_000)} Cr"
        amount >= 1_00_000    -> "$sym${trim(amount / 1_00_000)} L"
        else -> sym + NumberFormat.getNumberInstance(Locale("en", "IN")).apply { maximumFractionDigits = 0 }.format(amount)
    }
}

/** "187 days overdue", "Today", "In 14 days". */
internal fun dueNote(days: Int?): String = when {
    days == null -> ""
    days < 0  -> "${-days} ${if (days == -1) "Day" else "Days"} Overdue"
    days == 0 -> "Due Today"
    else      -> "In $days ${if (days == 1) "Day" else "Days"}"
}

/** Under the end date: "Ended 187 days ago" or "143 days left". */
internal fun endsNote(expiry: LocalDate?): String? {
    expiry ?: return null
    val d = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), expiry).toInt()
    return when {
        d < 0 -> "Ended ${-d} ${if (d == -1) "day" else "days"} ago"
        d == 0 -> "Ends today"
        else -> "$d ${if (d == 1) "day" else "days"} left"
    }
}

internal fun renewalLabel(type: String?): String = when (type?.lowercase()) {
    "auto" -> "Auto-Renews"
    "mutual" -> "Renews By Mutual Agreement"
    "extension_option" -> "Extension Option"
    else -> "Ends Without Renewal"
}

internal fun title(s: String?): String =
    s.orEmpty().replace('_', ' ').split(' ').filter { it.isNotBlank() }
        .joinToString(" ") { w -> w.lowercase().replaceFirstChar { it.titlecase() } }
