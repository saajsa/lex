package com.legumlex.client.feature.finance

import com.legumlex.client.data.api.ApiService
import com.legumlex.client.feature.payment.RazorpayOptions
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class InvoicesRepository(private val api: ApiService) {

    suspend fun list(): Result<List<Invoice>> = runCatching {
        val response = api.invoices(id = null)
        @Suppress("UNCHECKED_CAST")
        val data = response["data"] as? List<Map<String, Any?>> ?: emptyList()
        data.map(::parseInvoice)
    }

    suspend fun detail(id: String): Result<InvoiceDetail> = runCatching {
        val response = api.invoices(id = id)
        @Suppress("UNCHECKED_CAST")
        val data = response["data"] as? Map<String, Any?>
            ?: error("Invoice not found")
        parseDetail(data)
    }

    /**
     * Open a Razorpay order for the outstanding balance. Returns the SDK
     * config the screen hands to openRazorpayCheckout. Throws if the invoice
     * is already paid / cancelled / has no balance.
     */
    suspend fun startPayment(invoiceId: String, amountRupees: String? = null): Result<RazorpayOptions> = runCatching {
        val response = api.startInvoicePayment(invoiceId, amountRupees?.takeIf { it.isNotBlank() })
        @Suppress("UNCHECKED_CAST")
        val data = response["data"] as? Map<String, Any?>
            ?: error(response["message"]?.toString() ?: "Could not start payment")
        com.legumlex.client.feature.payment.razorpayOptionsFrom(data, "Invoice payment")
    }

    /** Verify after the SDK reports success. Throws on signature failure. */
    suspend fun verifyPayment(
        invoiceId: String,
        orderId: String,
        paymentId: String,
        signature: String,
    ): Result<Unit> = runCatching {
        val response = api.verifyInvoicePayment(invoiceId, orderId, paymentId, signature)
        val ok = response["status"]?.toString()?.toBooleanStrictOrNull() == true
        if (!ok) error(response["message"]?.toString() ?: "Payment verification failed")
        Unit
    }
}

private fun parseInvoice(m: Map<String, Any?>): Invoice {
    val date    = parseDate(m["date"] as? String)
    val dueDate = parseDate(m["duedate"] as? String)
    val total      = (m["total"]?.toString()?.toDoubleOrNull()) ?: 0.0
    // The list endpoint doesn't include total_left_to_pay; default to full total.
    val leftToPay  = (m["total_left_to_pay"]?.toString()?.toDoubleOrNull()) ?: total
    return Invoice(
        id              = m["id"]?.toString() ?: "",
        number          = (m["formatted_number"] as? String)?.takeIf { it.isNotBlank() }
                            ?: formatFallback(m),
        date            = date,
        dueDate         = dueDate,
        total           = total,
        leftToPay       = leftToPay,
        currencySymbol  = (m["symbol"] as? String)?.takeIf { it.isNotBlank() } ?: "",
        status          = InvoiceStatus.derive(m["status"]?.toString(), dueDate),
        hash            = m["hash"] as? String ?: "",
        projectName     = (m["project_name"] as? String)?.takeIf { it.isNotBlank() },
    )
}

private fun parseDetail(m: Map<String, Any?>): InvoiceDetail {
    val inv = parseInvoice(m)

    @Suppress("UNCHECKED_CAST")
    val itemsRaw = m["items"] as? List<Map<String, Any?>> ?: emptyList()
    val items = itemsRaw.map {
        InvoiceItem(
            description     = (it["description"] as? String).orEmpty(),
            longDescription = (it["long_description"] as? String)?.takeIf { l -> l.isNotBlank() },
            qty             = it["qty"]?.toString()?.toDoubleOrNull() ?: 0.0,
            rate            = it["rate"]?.toString()?.toDoubleOrNull() ?: 0.0,
            unit            = (it["unit"] as? String)?.takeIf { u -> u.isNotBlank() },
        )
    }

    @Suppress("UNCHECKED_CAST")
    val paymentsRaw = m["payments"] as? List<Map<String, Any?>> ?: emptyList()
    val payments = paymentsRaw.map {
        Payment(
            date          = parseDate(it["date"] as? String),
            amount        = it["amount"]?.toString()?.toDoubleOrNull() ?: 0.0,
            method        = (it["payment_method"] as? String)?.takeIf { v -> v.isNotBlank() }
                              ?: (it["paymentmode"] as? String),
            transactionId = (it["transactionid"] as? String)?.takeIf { v -> v.isNotBlank() },
        )
    }

    return InvoiceDetail(
        invoice    = inv,
        subtotal   = m["subtotal"]?.toString()?.toDoubleOrNull() ?: 0.0,
        tax        = m["total_tax"]?.toString()?.toDoubleOrNull() ?: 0.0,
        discount   = m["discount_total"]?.toString()?.toDoubleOrNull() ?: 0.0,
        adjustment = m["adjustment"]?.toString()?.toDoubleOrNull() ?: 0.0,
        terms      = (m["terms"] as? String)?.takeIf { it.isNotBlank() },
        notes      = (m["clientnote"] as? String)?.takeIf { it.isNotBlank() },
        items      = items,
        payments   = payments,
        issuer     = (m["issuer"] as? Map<*, *>)?.let { i ->
            Issuer(
                name    = (i["name"] as? String).orEmpty(),
                legal   = (i["legal"] as? String).orEmpty(),
                address = (i["address"] as? String).orEmpty(),
                gstin   = (i["gstin"] as? String).orEmpty(),
                email   = (i["email"] as? String).orEmpty(),
                logoUrl = (i["logo_url"] as? String)?.takeIf { it.isNotBlank() },
            )
        }?.takeIf { it.name.isNotBlank() },
    )
}

private fun formatFallback(m: Map<String, Any?>): String {
    val prefix = (m["prefix"] as? String).orEmpty()
    val num    = m["number"]?.toString().orEmpty()
    return prefix + num.padStart(6, '0')
}

private val datePatterns = listOf("yyyy-MM-dd", "dd-MM-yyyy", "yyyy/MM/dd")

private fun parseDate(s: String?): LocalDate? {
    if (s.isNullOrBlank() || s == "0000-00-00") return null
    for (p in datePatterns) {
        try { return LocalDate.parse(s, DateTimeFormatter.ofPattern(p)) } catch (_: DateTimeParseException) {}
    }
    return null
}
