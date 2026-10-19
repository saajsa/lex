package com.legumlex.client.feature.finance

import com.legumlex.client.ui.theme.LexColors
import com.legumlex.client.ui.theme.Lex
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Composable
import com.legumlex.client.ui.theme.StatusPalette
import java.time.LocalDate

enum class InvoiceStatus(val label: String, private val tone: (LexColors) -> StatusPalette) {
    DRAFT     ("Draft",     { it.neutral }),
    UNPAID    ("Unpaid",    { it.warning }),
    PARTIAL   ("Partial",   { it.blue }),
    PAID      ("Paid",      { it.success }),
    OVERDUE   ("Overdue",   { it.danger }),
    CANCELLED ("Cancelled", { it.neutral });

    val palette: StatusPalette @Composable @ReadOnlyComposable get() = tone(Lex.colors)

    companion object {
        // Combine the raw Perfex status code with a due-date check so a still-
        // marked-Unpaid invoice past its due date is shown as Overdue.
        fun derive(rawCode: String?, dueDate: LocalDate?): InvoiceStatus {
            val today = LocalDate.now()
            return when (rawCode) {
                "2" -> PAID
                "3" -> PARTIAL
                "4" -> OVERDUE
                "5" -> CANCELLED
                "6" -> DRAFT
                "1" -> if (dueDate != null && dueDate.isBefore(today)) OVERDUE else UNPAID
                else -> UNPAID
            }
        }
    }
}

data class Invoice(
    val id: String,
    val number: String,            // formatted, e.g. "INV-000001"
    val date: LocalDate?,
    val dueDate: LocalDate?,
    val total: Double,
    val leftToPay: Double,
    val currencySymbol: String,
    val status: InvoiceStatus,
    val hash: String,              // for payment URL
    val projectName: String?,
    /** The business that issued it (Nyay & Co. or Legum Lex), as its letterhead names it. */
    val issuerName: String = "",
)

data class InvoiceDetail(
    val invoice: Invoice,
    val subtotal: Double,
    val tax: Double,
    val discount: Double,
    val adjustment: Double,
    val terms: String?,
    val notes: String?,
    val items: List<InvoiceItem>,
    val payments: List<Payment>,
    /** Who issued it, as the invoice prints it (cases_document_supplier). */
    val issuer: Issuer? = null,
    /** The client, as the web invoice's Billed To prints it. */
    val billedTo: BilledTo? = null,
    /** The footer's Document ID and its "scan to verify" QR. */
    val verify: Verify? = null,
    /** The footer's left column: the site and the issuer's billing address. */
    val footerWebsite: String = "",
    val footerEmail: String = "",
)

/**
 * The business that issued an invoice — Nyay & Co. or Legum Lex — or, for an
 * invoice issued before there were two, the frozen record ([legacy]).
 * [fromLines] are the web's From block, line for line. [logoUrl] is the
 * business's own logo; without one its [name] is the wordmark, and a legacy
 * invoice carries the platform's mark (as the web does).
 */
data class Issuer(
    val name: String,
    val legal: String,
    val address: String,
    val gstin: String,
    val pan: String,
    val email: String,
    val logoUrl: String?,
    val fromLines: List<String>,
    val legacy: Boolean,
)

data class BilledTo(
    val name: String, val entity: String, val gstin: String, val pan: String, val cin: String,
    val address: String, val attn: String, val email: String, val phone: String,
)

/** The Document ID (HMAC of id, total and date, keyed by the invoice) and its QR (PNG bytes). */
class Verify(val code: String, val url: String, val qrPng: ByteArray?)

data class InvoiceItem(
    val description: String,
    val longDescription: String?,
    val qty: Double,
    val rate: Double,
    val unit: String?,
) {
    val amount: Double get() = qty * rate
}

data class Payment(
    val date: LocalDate?,
    val amount: Double,
    val method: String?,
    val transactionId: String?,
)
