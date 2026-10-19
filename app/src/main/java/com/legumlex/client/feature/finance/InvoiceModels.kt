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
)

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
