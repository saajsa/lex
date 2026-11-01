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
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.legumlex.client.data.api.ApiClient
import com.legumlex.client.ui.components.WizardChoice
import com.legumlex.client.ui.theme.BodyBg
import com.legumlex.client.ui.theme.LegumLexTheme
import com.legumlex.client.ui.theme.PrimaryText
import com.legumlex.client.ui.theme.SecondaryText
import com.razorpay.Checkout
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import retrofit2.HttpException
import java.util.Locale

/*
 * An invoice paid in the app (owner, 1 Nov 2026). The amount, and the
 * gateway where the issuing business takes two, are chosen on the invoice
 * itself; this Activity only carries the payment: it asks the server for the
 * order (customers_api invoices/{id}/pay), opens Razorpay's own sheet or
 * PayU's checkout, and stays up, confirming, until the server has recorded
 * the payment (/verify, /status). Only then does the client go back, with
 * the outcome (PaymentEvents). The app holds no secret and signs nothing.
 */

data class PayGateway(val id: String, val label: String)

/** What paying an invoice offers (GET invoices/{id}/pay). */
data class PayOptions(
    val due: Double,
    val dueText: String,
    val canChange: Boolean,
    val minimum: Double,
    val gateways: List<PayGateway>,
    val refusal: String?,
)

@Suppress("UNCHECKED_CAST")
fun payOptionsFrom(r: Map<String, Any?>): PayOptions {
    val d = r["data"] as? Map<String, Any?> ?: emptyMap()
    return PayOptions(
        due = (d["due"] as? Number)?.toDouble() ?: 0.0,
        dueText = d["due_formatted"] as? String ?: "",
        canChange = d["can_change_amount"] == true,
        minimum = (d["minimum"] as? Number)?.toDouble() ?: 1.0,
        gateways = (d["gateways"] as? List<*>).orEmpty().mapNotNull { it as? Map<*, *> }
            .map { PayGateway(it["id"] as? String ?: "", it["label"] as? String ?: "") }.filter { it.id.isNotBlank() },
        refusal = (d["refusal"] as? String)?.takeIf { it.isNotBlank() },
    )
}

sealed interface PayOutcome {
    val invoiceId: Int
    data class Paid(override val invoiceId: Int, val amount: String, val note: String = "") : PayOutcome
    data class NotPaid(override val invoiceId: Int, val message: String) : PayOutcome
    data class Confirming(override val invoiceId: Int, val message: String) : PayOutcome
}

/** What a checkout came to, for the screen it returns to (the invoice shows it). */
object PaymentEvents {
    private val _events = MutableSharedFlow<PayOutcome>(extraBufferCapacity = 4)
    val events: SharedFlow<PayOutcome> = _events.asSharedFlow()

    /** True when a screen took it; else the caller says it another way. */
    fun emit(o: PayOutcome): Boolean = _events.subscriptionCount.value > 0 && _events.tryEmit(o)

    /** The invoice a screen has just read again after a payment: the checkout closes only then. */
    val refreshed = MutableStateFlow(0)

    fun text(o: PayOutcome): String = when (o) {
        is PayOutcome.Paid -> o.note.ifBlank { "Payment of ${o.amount} received. Thank you." }
        is PayOutcome.NotPaid -> o.message
        is PayOutcome.Confirming -> o.message
    }
}

/** PayU's signed form, posted by the WebView as a browser would. */
data class PayuForm(val action: String, val fields: Map<String, String>, val txnid: String)

data class CheckoutState(
    /** What the screen says while it works ("Opening Razorpay…", "Confirming Your Payment…"). */
    val working: String = "Opening Payment…",
    val choose: List<PayGateway> = emptyList(),
    val payu: PayuForm? = null,
    val outcome: PayOutcome? = null,
)

class CheckoutViewModel(app: Application) : AndroidViewModel(app) {
    private val api = ApiClient.get(app)
    private val _state = MutableStateFlow(CheckoutState())
    val state: StateFlow<CheckoutState> = _state.asStateFlow()
    private var invoiceId = 0
    private var amount: String? = null
    private var started = false
    private var paying = 0.0

    /** Razorpay's order, kept for the verify call when the sheet answers. */
    var razorpay: JSONObject? = null
        private set

    fun begin(id: Int, amount: String?, gateway: String?, onRazorpay: (JSONObject) -> Unit) {
        if (started) return
        started = true
        invoiceId = id
        this.amount = amount
        pay(gateway, onRazorpay)
    }

    /** The business takes both and none was chosen yet: the client picks. */
    fun choose(gateway: String, onRazorpay: (JSONObject) -> Unit) {
        _state.value = _state.value.copy(choose = emptyList())
        pay(gateway, onRazorpay)
    }

    private fun pay(gateway: String?, onRazorpay: (JSONObject) -> Unit) {
        _state.value = _state.value.copy(working = "Opening Payment…")
        viewModelScope.launch {
            runCatching { api.invoicePay(invoiceId, amount.orEmpty(), gateway) }
                .onSuccess { r ->
                    @Suppress("UNCHECKED_CAST")
                    val d = r["data"] as? Map<String, Any?> ?: emptyMap()
                    paying = (d["amount"] as? Number)?.toDouble() ?: amount?.toDoubleOrNull() ?: 0.0
                    when {
                        d["choose"] is List<*> -> {
                            @Suppress("UNCHECKED_CAST")
                            val labels = d["labels"] as? Map<String, Any?> ?: emptyMap()
                            _state.value = _state.value.copy(choose = (d["choose"] as List<*>).mapNotNull { it as? String }
                                .map { PayGateway(it, labels[it] as? String ?: it) })
                        }
                        d["gateway"] == "payu" -> {
                            @Suppress("UNCHECKED_CAST")
                            val f = (d["fields"] as? Map<String, Any?>).orEmpty().mapValues { it.value?.toString().orEmpty() }
                            _state.value = _state.value.copy(payu = PayuForm(d["action"] as? String ?: "", f, d["txnid"] as? String ?: ""))
                        }
                        else -> {
                            val o = razorpayOptions(d)
                            if (o == null) finish(PayOutcome.NotPaid(invoiceId, "Payments are not set up yet. Please contact the firm."))
                            else {
                                razorpay = o
                                _state.value = _state.value.copy(working = "Opening Razorpay…")
                                onRazorpay(o)
                            }
                        }
                    }
                }
                .onFailure { finish(PayOutcome.NotPaid(invoiceId, message(it))) }
        }
    }

    fun razorpayPaid(paymentId: String, orderId: String, signature: String) {
        _state.value = _state.value.copy(working = CONFIRMING)
        viewModelScope.launch {
            runCatching { api.invoicePayVerify(invoiceId, orderId, paymentId, signature) }
                .onSuccess { finish(PayOutcome.Paid(invoiceId, money(paying))) }
                .onFailure {
                    finish(PayOutcome.Confirming(invoiceId, "Your payment was made, but it could not be confirmed yet. It will show on the invoice once the firm's records have it."))
                }
        }
    }

    fun razorpayFailed(code: Int) {
        finish(PayOutcome.NotPaid(invoiceId, if (code == Checkout.PAYMENT_CANCELED) "No payment was made."
            else "The payment did not go through. Nothing was charged; you can try again."))
    }

    /** PayU's page closed (finished, or closed by the client): ask the server until it knows. */
    fun payuClosed() {
        val f = _state.value.payu ?: return
        _state.value = _state.value.copy(payu = null, working = CONFIRMING)
        viewModelScope.launch {
            var last: Map<String, Any?> = emptyMap()
            // The return records at once; a UPI payment may take a little longer.
            for (i in 0 until 20) {
                @Suppress("UNCHECKED_CAST")
                last = runCatching { api.invoicePayStatus(invoiceId, f.txnid)["data"] as? Map<String, Any?> }.getOrNull() ?: last
                if (last["state"] in setOf("captured", "failed", "flagged", "open")) break
                delay(3000)
            }
            val msg = last["message"] as? String ?: ""
            finish(when (last["state"]) {
                "captured" -> PayOutcome.Paid(invoiceId, money(paying))
                "flagged" -> PayOutcome.Paid(invoiceId, money(paying), msg)
                "failed", "open" -> PayOutcome.NotPaid(invoiceId, msg.ifBlank { "No payment was made." })
                else -> PayOutcome.Confirming(invoiceId, msg.ifBlank { "Your payment is being confirmed. It will show on the invoice once PayU confirms it." })
            })
        }
    }

    fun cancel() = finish(PayOutcome.NotPaid(invoiceId, "No payment was made."))

    private fun finish(o: PayOutcome) { _state.value = _state.value.copy(outcome = o) }

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
        const val CONFIRMING = "Confirming Your Payment…"

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
        val openRazorpay: (JSONObject) -> Unit = { o -> Checkout().apply { setKeyID(o.getString("key")) }.open(this, o) }
        vm.begin(id, intent.getStringExtra(EXTRA_AMOUNT), intent.getStringExtra(EXTRA_GATEWAY), openRazorpay)
        setContent {
            LegumLexTheme {
                val s by vm.state.collectAsState()
                LaunchedEffect(s.outcome) {
                    s.outcome?.let { o ->
                        // The screen beneath shows it, read again first so nothing stale shows
                        // behind; with none listening, a toast does.
                        PaymentEvents.refreshed.value = 0
                        if (PaymentEvents.emit(o)) {
                            kotlinx.coroutines.withTimeoutOrNull(5000) { PaymentEvents.refreshed.first { it == o.invoiceId } }
                        } else {
                            Toast.makeText(this@CheckoutActivity, PaymentEvents.text(o), Toast.LENGTH_LONG).show()
                        }
                        finish()
                    }
                }
                Box(Modifier.fillMaxSize().background(BodyBg).statusBarsPadding().navigationBarsPadding()) {
                    val f = s.payu
                    when {
                        f != null -> PayuPage(f, id, onDone = vm::payuClosed)
                        s.choose.isNotEmpty() -> ChooseGateway(s.choose, onChoose = { vm.choose(it, openRazorpay) }, onCancel = vm::cancel)
                        else -> Working(s.working)
                    }
                }
            }
        }
    }

    // Razorpay's sheet answers the Activity that opened it.
    override fun onPaymentSuccess(paymentId: String?, data: PaymentData?) {
        vm.razorpayPaid(paymentId.orEmpty(), data?.orderId ?: vm.razorpay?.optString("order_id").orEmpty(), data?.signature.orEmpty())
    }

    override fun onPaymentError(code: Int, response: String?, data: PaymentData?) = vm.razorpayFailed(code)

    companion object {
        private const val EXTRA_INVOICE = "invoice_id"
        private const val EXTRA_AMOUNT = "amount"
        private const val EXTRA_GATEWAY = "gateway"

        /** Pay [amount] (null: the whole balance) through [gateway] (null: the business's own, asked if it has two). */
        fun open(context: Context, invoiceId: Int, amount: String? = null, gateway: String? = null) {
            context.startActivity(Intent(context, CheckoutActivity::class.java).putExtra(EXTRA_INVOICE, invoiceId)
                .putExtra(EXTRA_AMOUNT, amount).putExtra(EXTRA_GATEWAY, gateway))
        }

        /** The invoice id in an invoice page link (…/invoice/{id}/{hash}), or null. */
        fun invoiceIn(url: String): Int? = Regex("/invoice/(\\d+)/[A-Za-z0-9]+").find(url)?.groupValues?.get(1)?.toIntOrNull()
    }
}

/** While the order is made, or the payment is being confirmed: nothing to press, nothing to leave by. */
@Composable
private fun Working(text: String) {
    BackHandler { }
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = PrimaryText)
        Spacer(Modifier.height(20.dp))
        Text(text, color = PrimaryText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        if (text == CheckoutViewModel.CONFIRMING) {
            Spacer(Modifier.height(8.dp))
            Text("Please keep the app open. This can take a few seconds.", color = SecondaryText, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
    }
}

/** A fee from a business that takes both gateways, opened without one chosen. */
@Composable
private fun ChooseGateway(list: List<PayGateway>, onChoose: (String) -> Unit, onCancel: () -> Unit) {
    BackHandler(onBack = onCancel)
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Center) {
        Text("Choose How To Pay", color = PrimaryText, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text("Cards, UPI, net banking and wallets are taken by both.", color = SecondaryText, fontSize = 14.sp)
        Spacer(Modifier.height(20.dp))
        list.forEach { g -> WizardChoice("Pay With ${g.label}", false) { onChoose(g.id) } }
        Spacer(Modifier.height(8.dp))
        Text("Cancel", color = PrimaryText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(onClick = onCancel).padding(vertical = 12.dp))
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
