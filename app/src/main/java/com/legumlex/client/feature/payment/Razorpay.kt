package com.legumlex.client.feature.payment

import android.app.Activity
import androidx.compose.runtime.compositionLocalOf
import com.razorpay.Checkout
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.json.JSONObject

/**
 * Temporary bypass while Razorpay reviews the merchant account. When true (and
 * only in debug builds), [openRazorpayCheckout] short-circuits the SDK and
 * fabricates a Success result so the back-half of the payment flow (verify
 * call, snackbar, refresh) is still exercisable end-to-end.
 *
 * The fabricated signature will fail server-side HMAC verification — that's
 * expected: the goal is to drive the UI states, not to record a real payment.
 *
 * Remove this flag (and the branch in [openRazorpayCheckout]) once Razorpay
 * approves the account and Checkout works against test keys.
 */
// Intentionally NOT a `const val` — `const val` gets inlined at every read
// site, and a stale incremental build can leave callers with the old value
// baked in. Plain `val` forces a runtime read.
//
// Real Checkout is now enabled against the configured (test) keys. The stub
// branch below is kept dormant so the back-half UI flow stays drivable if it's
// ever flipped back on temporarily; remove it once live keys are in use.
val DEBUG_STUB_RAZORPAY: Boolean = false

/** SharedFlow the stub publishes fake results into. Mirrors the one MainActivity
 *  exposes through LocalRazorpayResults so callers can collect uniformly. */
private val _stubResults = MutableSharedFlow<RazorpayResult>(extraBufferCapacity = 1)
val stubRazorpayResults: SharedFlow<RazorpayResult> = _stubResults

/**
 * Result of a Razorpay Checkout sheet, surfaced by the host Activity (it
 * implements PaymentResultWithDataListener; the SDK discovers the interface
 * via reflection so the listener must live there). Composables consume this
 * via [LocalRazorpayResults], a SharedFlow scoped to the Activity lifetime.
 */
sealed interface RazorpayResult {
    data class Success(
        val paymentId: String,
        val orderId: String,
        val signature: String,
    ) : RazorpayResult

    data class Failed(
        val code: Int,
        val message: String,
    ) : RazorpayResult
}

/**
 * Hot flow of Razorpay Checkout outcomes, provided by the host Activity. A
 * composable opens the checkout via [openRazorpayCheckout] and collects this
 * flow to receive the success/failure event.
 *
 * Default is a flow that never emits — convenient for previews + ensures any
 * non-Activity consumer fails loudly rather than silently swallowing the
 * payment result.
 */
val LocalRazorpayResults = compositionLocalOf<SharedFlow<RazorpayResult>> {
    MutableSharedFlow()
}

/**
 * Booking-side payment parameters returned by the customers_api DO sessions
 * endpoint in the paid path. Mapped here so we never depend on a raw map at
 * the call site.
 */
data class RazorpayOptions(
    val keyId: String,
    val orderId: String,
    val amountPaise: Int,
    val currency: String,
    val name: String,
    val description: String,
    val prefillEmail: String?,
    val prefillContact: String?,
)

/**
 * The checkout block the server hands back for any order it opened (invoice,
 * enquiry fee, Agreement Check fee): {key_id, order_id, amount_paise,
 * currency, name, description, prefill:{email, contact}}.
 */
@Suppress("UNCHECKED_CAST")
fun razorpayOptionsFrom(data: Map<String, Any?>, fallbackDescription: String): RazorpayOptions {
    val prefill = data["prefill"] as? Map<String, Any?>
    return RazorpayOptions(
        keyId          = data["key_id"]?.toString()?.takeIf { it.isNotBlank() } ?: error("Payments are not set up yet."),
        orderId        = data["order_id"]?.toString() ?: error("Missing order_id"),
        // JSON numbers arrive as Double via the converter (300 -> 300.0), so
        // "300.0".toIntOrNull() is null. Parse via Number/Double, not toInt.
        amountPaise    = (data["amount_paise"] as? Number)?.toInt()
            ?: data["amount_paise"]?.toString()?.toDoubleOrNull()?.toInt()
            ?: error("Missing amount"),
        currency       = (data["currency"]  as? String) ?: "INR",
        name           = (data["name"]      as? String)?.takeIf { it.isNotBlank() } ?: "Legum Lex",
        description    = (data["description"] as? String)?.takeIf { it.isNotBlank() } ?: fallbackDescription,
        prefillEmail   = (prefill?.get("email")   as? String)?.takeIf { it.isNotBlank() },
        prefillContact = (prefill?.get("contact") as? String)?.takeIf { it.isNotBlank() },
    )
}

/**
 * Open the Razorpay Checkout sheet. The SDK takes over and eventually calls
 * back into MainActivity's PaymentResultWithDataListener, which republishes
 * the outcome on [LocalRazorpayResults] for whoever's collecting.
 *
 * Theme colour is fixed to the brand black so the SDK's chrome matches the
 * app rather than the default Razorpay blue.
 */
fun openRazorpayCheckout(activity: Activity, options: RazorpayOptions) {
    if (DEBUG_STUB_RAZORPAY) {
        // Skip the SDK entirely. Emit a fake Success carrying the real
        // order_id (so the server can locate the invoice token) but a bogus
        // signature + payment_id. The verify endpoint will 422 on the HMAC
        // check, surfacing the failure snackbar — sufficient for driving
        // every UI state except the genuine success path.
        _stubResults.tryEmit(
            RazorpayResult.Success(
                paymentId = "pay_DEBUG_STUB",
                orderId   = options.orderId,
                signature = "DEBUG_STUB_SIGNATURE",
            )
        )
        return
    }
    val checkout = Checkout().apply {
        setKeyID(options.keyId)
    }
    // Option format mirrors Razorpay's own Android integration-steps doc:
    //   amount      → STRING, not integer (the SDK silently fails on int)
    //   theme.color → flat dot-notation key, not a nested object
    //   prefill.*   → flat dot-notation keys, not a nested object
    // The SDK catches malformed-options exceptions internally and surfaces
    // them as the misleading "couldn't reach the server" error.
    val payload = JSONObject().apply {
        put("name", options.name)
        put("description", options.description)
        put("currency", options.currency)
        put("amount", options.amountPaise.toString())
        put("order_id", options.orderId)
        put("theme.color", "#0A0A0A")
        options.prefillEmail?.takeIf  { it.isNotBlank() }?.let { put("prefill.email", it) }
        options.prefillContact?.takeIf{ it.isNotBlank() }?.let { put("prefill.contact", it) }
        // Retry IS a nested object per the docs (different from theme/prefill).
        put("retry", JSONObject().apply {
            put("enabled", true)
            put("max_count", 3)
        })
    }
    checkout.open(activity, payload)
}
