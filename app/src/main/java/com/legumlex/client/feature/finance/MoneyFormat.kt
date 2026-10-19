package com.legumlex.client.feature.finance

import com.legumlex.client.BuildConfig
import java.text.NumberFormat
import java.util.Locale

// Indian locale renders large amounts with lakhs/crores grouping (e.g. ₹12,34,567.00),
// which matches what clients see on the Perfex side.
private val numberFormat: NumberFormat by lazy {
    NumberFormat.getNumberInstance(Locale("en", "IN")).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
}

fun formatMoney(amount: Double, symbol: String): String {
    val sign = if (amount < 0) "-" else ""
    return "$sign$symbol${numberFormat.format(kotlin.math.abs(amount))}"
}

/**
 * A bare numeric string for an editable amount field — no currency symbol, no
 * grouping. Whole values drop the decimals (3000 → "3000"); fractional values
 * keep two places (3000.5 → "3000.50").
 */
fun plainAmount(amount: Double): String {
    val rounded = kotlin.math.round(amount * 100) / 100.0
    return if (rounded % 1.0 == 0.0) rounded.toLong().toString()
           else String.format(Locale.US, "%.2f", rounded)
}

/**
 * Derives the public Perfex/Razorpay payment URL for an invoice from the API
 * base URL. The base URL ends with /customers_api/v1/; we strip that suffix
 * and append /invoice/{id}/{hash}.
 */
fun paymentUrlFor(invoice: Invoice): String {
    val base = BuildConfig.API_BASE_URL
        .removeSuffix("/")
        .removeSuffix("/customers_api/v1")
    return "$base/invoice/${invoice.id}/${invoice.hash}"
}
