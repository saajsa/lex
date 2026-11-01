package com.legumlex.client.feature.finance

import android.annotation.SuppressLint
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.legumlex.client.data.api.ApiClient
import com.legumlex.client.ui.components.WizardChoice
import com.legumlex.client.ui.components.WizardFrame
import com.legumlex.client.ui.components.WizardHeading
import com.legumlex.client.ui.components.WizardRows
import com.legumlex.client.ui.components.WizardTextField
import com.legumlex.client.ui.theme.BodyBg
import com.legumlex.client.ui.theme.LegumLexTheme
import com.legumlex.client.ui.theme.PrimaryText
import com.razorpay.Checkout
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import retrofit2.HttpException
import java.util.Locale

/*
 * An invoice paid in the app (owner, 1 Nov 2026): the gateway itself opens
 * here, as on the invoice's page: the amount (part of the balance when the
 * firm allows it, as Perfex's "allow the amount to be modified"), the
 * gateway when the issuing business takes two, then Razorpay's own sheet or
 * PayU's checkout in place. The server makes the order and records the
 * payment (customers_api invoices/{id}/pay, /verify, /status); the app holds
 * no secret and signs nothing.
 */

data class PayGateway(val id: String, val label: String)

data class PayOptions(
    val number: String,
    val due: Double,
    val dueText: String,
    val canChange: Boolean,
    val minimum: Double,
    val gateways: List<PayGateway>,
    val refusal: String?,
)

/** PayU's signed form, posted by the WebView as a browser would. */
data class PayuForm(val action: String, val fields: Map<String, String>, val txnid: String)

sealed interface PayOutcome {
    data class Paid(val amount: String, val note: String = "") : PayOutcome
    data class NotPaid(val message: String) : PayOutcome
    data class Confirming(val message: String) : PayOutcome
}

data class CheckoutState(
    val loading: Boolean = true,
    val options: PayOptions? = null,
    val step: Int = 0,
    val amount: String = "",
    val gateway: String = "",
    val busy: Boolean = false,
    /** Paid at the gateway; the server is confirming and recording it. */
    val confirming: Boolean = false,
    val error: String? = null,
    val payu: PayuForm? = null,
    val outcome: PayOutcome? = null,
)

class CheckoutViewModel(app: Application) : AndroidViewModel(app) {
    private val api = ApiClient.get(app)
    private val _state = MutableStateFlow(CheckoutState())
    val state: StateFlow<CheckoutState> = _state.asStateFlow()
    var invoiceId = 0
        private set

    /** Razorpay's order, kept for the verify call when the sheet answers. */
    var razorpay: JSONObject? = null
        private set
    private var paying = 0.0

    fun load(id: Int) {
        if (invoiceId == id) return
        invoiceId = id
        viewModelScope.launch {
            runCatching {
                @Suppress("UNCHECKED_CAST")
                val d = api.invoicePayOptions(id)["data"] as? Map<String, Any?> ?: emptyMap()
                PayOptions(
                    number = d["number"] as? String ?: "",
                    due = (d["due"] as? Number)?.toDouble() ?: 0.0,
                    dueText = d["due_formatted"] as? String ?: "",
                    canChange = d["can_change_amount"] == true,
                    minimum = (d["minimum"] as? Number)?.toDouble() ?: 1.0,
                    gateways = (d["gateways"] as? List<*>).orEmpty().mapNotNull { it as? Map<*, *> }
                        .map { PayGateway(it["id"] as? String ?: "", it["label"] as? String ?: "") }.filter { it.id.isNotBlank() },
                    refusal = (d["refusal"] as? String)?.takeIf { it.isNotBlank() },
                )
            }.onSuccess { o ->
                _state.value = _state.value.copy(loading = false, options = o, amount = plain(o.due),
                    gateway = o.gateways.singleOrNull()?.id ?: "")
            }.onFailure { _state.value = _state.value.copy(loading = false, error = message(it)) }
        }
    }

    fun amount(v: String) {
        val clean = v.filter { it.isDigit() || it == '.' }.let { s ->
            val dot = s.indexOf('.')
            if (dot < 0) s else s.substring(0, dot + 1) + s.substring(dot + 1).replace(".", "").take(2)
        }
        _state.value = _state.value.copy(amount = clean.take(12), error = null)
    }

    fun gateway(id: String) { _state.value = _state.value.copy(gateway = id, error = null) }
    fun step(i: Int) { _state.value = _state.value.copy(step = i, error = null) }

    /** Why the amount entered can't be paid, or "". */
    fun amountProblem(s: CheckoutState = _state.value): String {
        val o = s.options ?: return ""
        val a = s.amount.toDoubleOrNull() ?: return "Enter the amount to pay."
        return when {
            a < o.minimum -> "The minimum payable amount is ${money(o.minimum)}."
            a > o.due + 0.001 -> "That is more than the balance due of ${o.dueText}."
            else -> ""
        }
    }

    /** Ask the server for the order (Razorpay) or the signed form (PayU). */
    fun start(onRazorpay: (JSONObject) -> Unit) {
        val s = _state.value
        if (s.busy || amountProblem(s).isNotEmpty()) return
        _state.value = s.copy(busy = true, error = null)
        viewModelScope.launch {
            runCatching { api.invoicePay(invoiceId, s.amount, s.gateway.ifBlank { null }) }
                .onSuccess { r ->
                    @Suppress("UNCHECKED_CAST")
                    val d = r["data"] as? Map<String, Any?> ?: emptyMap()
                    paying = (d["amount"] as? Number)?.toDouble() ?: s.amount.toDoubleOrNull() ?: 0.0
                    when (d["gateway"]) {
                        "payu" -> {
                            @Suppress("UNCHECKED_CAST")
                            val f = (d["fields"] as? Map<String, Any?>).orEmpty().mapValues { it.value?.toString().orEmpty() }
                            _state.value = _state.value.copy(busy = false,
                                payu = PayuForm(d["action"] as? String ?: "", f, d["txnid"] as? String ?: ""))
                        }
                        "razorpay" -> {
                            val o = razorpayOptions(d)
                            if (o == null) {
                                _state.value = _state.value.copy(busy = false, error = "Payments are not set up yet. Please contact the firm.")
                            } else {
                                razorpay = o
                                onRazorpay(o)
                            }
                        }
                        // Both offered and none chosen: should not happen, as the
                        // Method step always sends one. Ask again.
                        else -> _state.value = _state.value.copy(busy = false, error = "Choose how to pay.")
                    }
                }
                .onFailure { _state.value = _state.value.copy(busy = false, error = message(it)) }
        }
    }

    fun razorpayPaid(paymentId: String, orderId: String, signature: String) {
        _state.value = _state.value.copy(busy = true, confirming = true)
        viewModelScope.launch {
            runCatching { api.invoicePayVerify(invoiceId, orderId, paymentId, signature) }
                .onSuccess { finish(PayOutcome.Paid(money(paying))) }
                .onFailure {
                    finish(PayOutcome.Confirming("Your payment was made, but it could not be confirmed here. It will show on the invoice once the firm's records have it."))
                }
        }
    }

    fun razorpayFailed(code: Int) {
        finish(PayOutcome.NotPaid(if (code == Checkout.PAYMENT_CANCELED) "No payment was made."
            else "The payment did not go through. Nothing was charged; you can try again."))
    }

    /** PayU's page closed (finished, or closed by the client): ask the server what happened. */
    fun payuClosed() {
        val f = _state.value.payu ?: return
        _state.value = _state.value.copy(payu = null, busy = true, confirming = true)
        viewModelScope.launch {
            var last: Map<String, Any?> = emptyMap()
            // The return records at once; a UPI payment may take a little longer.
            for (i in 0 until 10) {
                @Suppress("UNCHECKED_CAST")
                last = runCatching { api.invoicePayStatus(invoiceId, f.txnid)["data"] as? Map<String, Any?> }.getOrNull() ?: last
                if (last["state"] in setOf("captured", "failed", "flagged", "open")) break
                delay(3000)
            }
            val msg = last["message"] as? String ?: ""
            finish(when (last["state"]) {
                "captured" -> PayOutcome.Paid(money(paying))
                "flagged" -> PayOutcome.Paid(money(paying), msg)
                "failed", "open" -> PayOutcome.NotPaid(msg.ifBlank { "No payment was made." })
                else -> PayOutcome.Confirming(msg.ifBlank { "Your payment is being confirmed. It will show on the invoice once PayU confirms it." })
            })
        }
    }

    /** Pay again from the first step. */
    fun again() {
        _state.value = _state.value.copy(outcome = null, step = 0, busy = false, error = null)
        val id = invoiceId
        invoiceId = 0
        load(id)
    }

    private fun finish(o: PayOutcome) { _state.value = _state.value.copy(busy = false, confirming = false, outcome = o) }

    private fun razorpayOptions(d: Map<String, Any?>): JSONObject? {
        val key = (d["key_id"] as? String)?.takeIf { it.isNotBlank() } ?: return null
        val order = (d["order_id"] as? String)?.takeIf { it.isNotBlank() } ?: return null
        @Suppress("UNCHECKED_CAST")
        val prefill = d["prefill"] as? Map<String, Any?>
        // Razorpay's Android format: amount as a string, flat dotted keys for
        // theme and prefill (a malformed option shows as "couldn't reach the server").
        return JSONObject().apply {
            put("key", key)
            put("name", (d["name"] as? String)?.takeIf { it.isNotBlank() } ?: "Legum Lex")
            put("description", d["description"] as? String ?: "Invoice")
            put("currency", d["currency"] as? String ?: "INR")
            put("amount", ((d["amount_paise"] as? Number)?.toLong() ?: 0L).toString())
            put("order_id", order)
            put("theme.color", "#0A0A0A")
            (prefill?.get("email") as? String)?.takeIf { it.isNotBlank() }?.let { put("prefill.email", it) }
            (prefill?.get("contact") as? String)?.takeIf { it.isNotBlank() }?.let { put("prefill.contact", it) }
            put("retry", JSONObject().apply { put("enabled", true); put("max_count", 3) })
        }
    }

    private fun message(e: Throwable): String = when (e) {
        is HttpException -> runCatching { JSONObject(e.response()?.errorBody()?.string().orEmpty()).optString("message") }
            .getOrNull()?.takeIf { it.isNotBlank() } ?: "The payment could not be started (error ${e.code()})."
        is java.io.IOException -> "Network error. Please try again."
        else -> e.message ?: "The payment could not be started. Please try again."
    }

    companion object {
        fun plain(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else String.format(Locale.US, "%.2f", v)
        fun money(v: Double): String = "₹" + java.text.NumberFormat.getNumberInstance(Locale("en", "IN"))
            .apply { minimumFractionDigits = 2; maximumFractionDigits = 2 }.format(v)
    }
}

class CheckoutActivity : ComponentActivity(), PaymentResultWithDataListener {
    private val vm: CheckoutViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val id = intent.getIntExtra(EXTRA_INVOICE, 0)
        if (id < 1) { finish(); return }
        vm.load(id)
        setContent {
            LegumLexTheme {
                Box(Modifier.fillMaxSize().background(BodyBg).statusBarsPadding().navigationBarsPadding()) {
                    CheckoutScreen(vm, onClose = ::finish, onRazorpay = { o ->
                        Checkout().apply { setKeyID(o.getString("key")) }.open(this@CheckoutActivity, o)
                    })
                }
            }
        }
    }

    // Razorpay's sheet answers the Activity that opened it.
    override fun onPaymentSuccess(paymentId: String?, data: PaymentData?) {
        val order = data?.orderId ?: vm.razorpay?.optString("order_id")
        val sig = data?.signature
        if (paymentId == null || order == null || sig == null) {
            vm.razorpayPaid(paymentId.orEmpty(), order.orEmpty(), sig.orEmpty())
        } else {
            vm.razorpayPaid(paymentId, order, sig)
        }
    }

    override fun onPaymentError(code: Int, response: String?, data: PaymentData?) = vm.razorpayFailed(code)

    companion object {
        private const val EXTRA_INVOICE = "invoice_id"

        fun open(context: Context, invoiceId: Int) {
            context.startActivity(Intent(context, CheckoutActivity::class.java).putExtra(EXTRA_INVOICE, invoiceId))
        }

        /** The invoice id in an invoice page link (…/invoice/{id}/{hash}), or null. */
        fun invoiceIn(url: String): Int? = Regex("/invoice/(\\d+)/[A-Za-z0-9]+").find(url)?.groupValues?.get(1)?.toIntOrNull()
    }
}

@Composable
private fun CheckoutScreen(vm: CheckoutViewModel, onClose: () -> Unit, onRazorpay: (JSONObject) -> Unit) {
    val s by vm.state.collectAsState()
    val o = s.options

    s.payu?.let { f ->
        PayuPage(f, vm.invoiceId, onDone = vm::payuClosed)
        return
    }
    if (s.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = PrimaryText) }
        return
    }
    val outcome = s.outcome
    if (o == null || o.refusal != null || outcome != null) {
        // One step: the answer, then Done (or Try Again).
        val again = outcome is PayOutcome.NotPaid
        WizardFrame(
            title = "Pay Invoice", steps = listOf("Payment"), step = 0, ready = true, saving = false,
            saveLabel = if (again) "Try Again" else "Done", error = if (o == null) s.error else null,
            onClose = onClose, onStep = {}, onSave = { if (again) vm.again() else onClose() },
        ) {
            when {
                outcome is PayOutcome.Paid -> {
                    WizardHeading("Payment Received", outcome.note.ifBlank { "Thank you. Your payment has been recorded on the invoice." })
                    WizardRows(listOf("Invoice" to (o?.number ?: ""), "Paid" to outcome.amount))
                }
                outcome is PayOutcome.Confirming -> WizardHeading("Payment Being Confirmed", outcome.message)
                outcome is PayOutcome.NotPaid -> WizardHeading("Payment Not Made", outcome.message)
                o == null -> WizardHeading("Pay Invoice", "The invoice could not be opened for payment.")
                else -> {
                    WizardHeading("This Invoice Can't Be Paid Here", o.refusal.orEmpty())
                    WizardRows(listOf("Invoice" to o.number, "Balance Due" to o.dueText))
                }
            }
        }
        return
    }

    val choose = o.gateways.size > 1
    val steps = if (choose) listOf("Amount", "Method") else listOf("Amount")
    val last = s.step == steps.lastIndex
    val problem = vm.amountProblem(s)
    val payLabel = "Pay " + (s.amount.toDoubleOrNull()?.let { CheckoutViewModel.money(it) } ?: "")
    WizardFrame(
        title = "Pay Invoice",
        steps = steps,
        step = s.step,
        ready = problem.isEmpty() && (!last || s.gateway.isNotBlank()),
        saving = s.busy,
        saveLabel = payLabel,
        savingLabel = if (s.confirming) "Confirming…" else "Opening…",
        error = s.error,
        onClose = onClose,
        onStep = vm::step,
        onSave = { vm.start(onRazorpay) },
    ) {
        when (s.step) {
            0 -> {
                WizardHeading("Amount", if (o.canChange) "You may pay the whole balance or part of it now." else "The balance due on this invoice.")
                WizardRows(listOf("Invoice" to o.number, "Balance Due" to o.dueText))
                Spacer(Modifier.height(18.dp))
                WizardTextField(
                    "Amount To Pay", s.amount, vm::amount,
                    help = if (o.canChange) "Up to ${o.dueText}." else "",
                    error = if (s.amount.isNotEmpty()) problem else "",
                    locked = !o.canChange, prefix = "₹", keyboardType = KeyboardType.Decimal,
                )
            }
            else -> {
                WizardHeading("Method", "Choose how to pay. Cards, UPI, net banking and wallets are taken by both.")
                o.gateways.forEach { g ->
                    WizardChoice("Pay With ${g.label}", s.gateway == g.id) { vm.gateway(g.id) }
                }
            }
        }
    }
}

/** PayU's checkout in place: the signed form posted, closed when PayU sends the client back. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PayuPage(f: PayuForm, invoiceId: Int, onDone: () -> Unit) {
    var loading by remember { mutableStateOf(true) }
    val holder = remember { arrayOfNulls<WebView>(1) }
    BackHandler {
        val w = holder[0]
        if (w != null && w.canGoBack()) w.goBack() else onDone()
    }
    Column(Modifier.fillMaxSize().background(BodyBg)) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Pay With PayU", color = PrimaryText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("Close", color = PrimaryText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { onDone() }.padding(8.dp))
        }
        Box(Modifier.fillMaxWidth().height(2.dp)) { if (loading) LinearProgressIndicator(Modifier.fillMaxWidth()) }
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { ctx ->
                WebView(ctx).also { w ->
                    holder[0] = w
                    w.layoutParams = android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                    w.settings.javaScriptEnabled = true
                    w.settings.domStorageEnabled = true
                    w.settings.setSupportMultipleWindows(false)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(w, true)
                    var finished = false
                    w.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                            val scheme = req.url.scheme.orEmpty()
                            if (scheme != "http" && scheme != "https") {
                                openGatewayLink(view.context, req.url.toString()) { view.loadUrl(it) }
                                return true
                            }
                            return false
                        }

                        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                            loading = true
                            // PayU's result was taken by the server, which sends the client
                            // back (payu/done, then the invoice's page, the return_url).
                            val path = Uri.parse(url).path.orEmpty()
                            if (!finished && (path.contains("/payu/done/") || path.contains("/invoice/$invoiceId/"))) {
                                finished = true
                                view.stopLoading()
                                onDone()
                            }
                        }

                        override fun onPageFinished(view: WebView, url: String) { loading = false }
                    }
                    // As the web's payu/redirect page: a form that posts itself.
                    val esc = { v: String -> android.text.Html.escapeHtml(v) }
                    val inputs = f.fields.entries.joinToString("") { (k, v) -> "<input type=\"hidden\" name=\"${esc(k)}\" value=\"${esc(v)}\">" }
                    val html = "<!doctype html><html><body><form id=\"p\" method=\"post\" action=\"${esc(f.action)}\">$inputs</form>" +
                        "<script>document.getElementById('p').submit();</script></body></html>"
                    w.loadDataWithBaseURL(f.action, html, "text/html", "UTF-8", null)
                }
            },
            onRelease = { it.destroy() },
        )
    }
}

/** UPI, intent:// and other app links a gateway opens: the phone's apps, else the gateway's fallback page. */
internal fun openGatewayLink(context: Context, link: String, onFallback: (String) -> Unit) {
    try {
        val i = if (link.startsWith("intent:")) Intent.parseUri(link, Intent.URI_INTENT_SCHEME) else Intent(Intent.ACTION_VIEW, Uri.parse(link))
        i.addCategory(Intent.CATEGORY_BROWSABLE)
        i.component = null
        i.selector = null
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i)
    } catch (e: ActivityNotFoundException) {
        val fallback = runCatching { Intent.parseUri(link, Intent.URI_INTENT_SCHEME).getStringExtra("browser_fallback_url") }.getOrNull()
        if (fallback != null) onFallback(fallback)
        else Toast.makeText(context, "No app on this phone can open that payment option.", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "That payment option could not be opened.", Toast.LENGTH_LONG).show()
    }
}
