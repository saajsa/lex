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
    val termMonths: Int? = null,
    val jurisdiction: String? = null,
    val ownerName: String? = null,
    val notes: String? = null,
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
    /** What falls due, when the obligation is a sum (a payment, a price revision). */
    val amount: Double? = null,
    val currency: String? = null,
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
        val agreement = d["agreement"] as? Map<*, *> ?: error("This agreement could not be read. Please try again.")
        RegistryDetail(
            agreement = agreement.toAgreement(),
            deadlines = (d["obligations"] as? List<*>).orEmpty().mapNotNull { (it as? Map<*, *>)?.toDeadline() },
        )
    }.friendly()
}

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.dataMap(): Map<String, Any?> = this["data"] as? Map<String, Any?> ?: emptyMap()
/** A JSON number (Moshi: Double) or a numeric string, as an Int. */
private fun num(v: Any?): Int? = when (v) {
    is Number -> v.toInt()
    is String -> v.trim().toIntOrNull() ?: v.trim().toDoubleOrNull()?.toInt()
    else -> null
}
private fun dbl(v: Any?): Double? = when (v) {
    is Number -> v.toDouble()
    is String -> v.trim().toDoubleOrNull()
    else -> null
}
private fun Map<*, *>?.int(k: String) = num(this?.get(k)) ?: 0
private fun Map<*, *>.str(k: String) = (this[k] as? String)?.takeIf { it.isNotBlank() }
private fun Map<*, *>.date(k: String) = str(k)?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

private fun Map<*, *>.toAgreement() = RegisterAgreement(
    id                = num(this["id"])?.toString() ?: str("id").orEmpty(),
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
    renewalTermMonths = num(this["renewal_term_months"]),
    noticeDays        = num(this["notice_period_days"]),
    noticeBy          = date("notice_deadline_date"),
    value             = dbl(this["contract_value"]),
    currency          = str("currency") ?: "INR",
    hasSignedCopy     = this["has_signed_copy"] == true,
    openDeadlines     = int("open_deadlines"),
    paymentTerms      = str("payment_terms"),
    governingLaw      = str("governing_law"),
    termMonths        = num(this["term_months"]),
    jurisdiction      = str("jurisdiction"),
    ownerName         = str("owner_name"),
    notes             = str("notes"),
)

private fun Map<*, *>.toDeadline() = Deadline(
    id             = num(this["id"])?.toString().orEmpty(),
    agreementId    = num(this["agreement_id"])?.toString().orEmpty(),
    agreementTitle = str("agreement_title"),
    kindLabel      = str("kind_label") ?: "Deadline",
    title          = str("title") ?: "Deadline",
    description    = str("description"),
    due            = date("due_date"),
    status         = str("status") ?: "open",
    bucket         = str("bucket"),
    daysToDue      = num(this["days_to_due"]),
    amount         = dbl(this["amount"])?.takeIf { it != 0.0 },
    currency       = str("currency"),
)

private fun <T> Result<T>.friendly(): Result<T> = recoverCatching { e ->
    throw IllegalStateException(
        when {
            e is HttpException && e.code() == 401 -> "Your session has expired. Please sign in again."
            e is HttpException && e.code() == 403 -> "Your account does not include the register. Please ask the firm."
            e is HttpException && e.code() == 404 -> "This agreement is not on your register."
            e is HttpException && e.code() in 500..599 -> "A server error occurred. Please try again shortly."
            e is java.io.IOException -> "Network error. Please try again."
            // Our own error(...) messages are written for the client; anything else is not.
            e is IllegalStateException && !e.message.isNullOrBlank() -> e.message!!
            else -> "The register could not be read. Please try again."
        }
    )
}

class RegistryViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = RegistryRepository(ApiClient.get(app))
    private val _state = MutableStateFlow<Loadable<RegistryOverview>>(Loadable.Loading)
    val state: StateFlow<Loadable<RegistryOverview>> = _state.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()
    /** A reload that failed while the register was on screen: the data stays, this says why it is not fresh. */
    private val _staleReason = MutableStateFlow<String?>(null)
    val staleReason: StateFlow<String?> = _staleReason.asStateFlow()

    init { load() }

    fun load() {
        if (_refreshing.value) return
        viewModelScope.launch {
            val shown = _state.value is Loadable.Ready
            if (shown) _refreshing.value = true else _state.value = Loadable.Loading
            repo.overview()
                .onSuccess { _state.value = Loadable.Ready(it); _staleReason.value = null }
                .onFailure { if (shown) _staleReason.value = it.message else _state.value = Loadable.Failed(it.message ?: "") }
            _refreshing.value = false
        }
    }
}

class RegistryAgreementViewModel(app: Application, saved: SavedStateHandle) : AndroidViewModel(app) {
    private val id: String = saved[Route.RegistryAgreement.ARG] ?: ""
    private val repo = RegistryRepository(ApiClient.get(app))
    private val _state = MutableStateFlow<Loadable<RegistryDetail>>(Loadable.Loading)
    val state: StateFlow<Loadable<RegistryDetail>> = _state.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()
    private val _staleReason = MutableStateFlow<String?>(null)
    val staleReason: StateFlow<String?> = _staleReason.asStateFlow()

    init { load() }

    fun load() {
        if (_refreshing.value) return
        viewModelScope.launch {
            val shown = _state.value is Loadable.Ready
            if (shown) _refreshing.value = true else _state.value = Loadable.Loading
            repo.detail(id)
                .onSuccess { _state.value = Loadable.Ready(it); _staleReason.value = null }
                .onFailure { if (shown) _staleReason.value = it.message else _state.value = Loadable.Failed(it.message ?: "") }
            _refreshing.value = false
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

/** ₹1,23,456.50 — a sum in full, with Indian grouping (an obligation's amount). */
internal fun fullInr(amount: Double, currency: String? = "INR"): String {
    val sym = if (currency.isNullOrBlank() || currency.equals("INR", true)) "₹" else "$currency "
    val nf = NumberFormat.getNumberInstance(Locale("en", "IN")).apply {
        minimumFractionDigits = if (amount % 1.0 == 0.0) 0 else 2
        maximumFractionDigits = 2
    }
    return sym + nf.format(amount)
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

/** Title Case for a stored value: snake_case words capitalised, but a word already
 *  carrying capitals (GST, NDA, McKinsey) is left as written. */
internal fun title(s: String?): String =
    s.orEmpty().replace('_', ' ').split(' ').filter { it.isNotBlank() }
        .joinToString(" ") { w -> if (w.any { it.isUpperCase() }) w else w.replaceFirstChar { it.titlecase() } }

/** "36 months" → "3 Years"; "18 months" → "18 Months". */
internal fun monthsLabel(m: Int): String = when {
    m > 0 && m % 12 == 0 -> (m / 12).let { y -> "$y ${if (y == 1) "Year" else "Years"}" }
    else -> "$m ${if (m == 1) "Month" else "Months"}"
}
