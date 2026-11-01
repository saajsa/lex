package com.legumlex.client.feature.finance

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.legumlex.client.ahc.theme.LlColor
import com.legumlex.client.ui.theme.LegumLexTheme

/**
 * A page of the firm's site shown inside the app: an invoice to read in
 * full, or a lead's fee page (cases/landing/lead_pay), which first asks
 * what to put on the invoice. An invoice to pay goes to the app's own
 * checkout instead ([open] hands invoice links to [CheckoutActivity]).
 *
 * It closes itself once a payment comes back to the page it started on (or
 * to PayU's done page), and the screen beneath re-reads its state on
 * resume. UPI and other app links the gateways open (upi://, intent://)
 * are handed to the phone's apps.
 */
class PayActivity : ComponentActivity() {

    private var web: WebView? = null
    private var left = false            // we went to a gateway or another page
    private var loading by mutableStateOf(true)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val start = intent.getStringExtra(EXTRA_URL).orEmpty()
        if (!start.startsWith("http")) { finish(); return }
        val startPath = Uri.parse(start).path.orEmpty().trimEnd('/')

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val w = web
                if (w != null && w.canGoBack()) w.goBack() else finish()
            }
        })

        setContent {
            LegumLexTheme {
                Column(Modifier.fillMaxSize().background(LlColor.Paper)) {
                    Row(
                        Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Payment", color = LlColor.Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f))
                        Text("Done", color = LlColor.Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { finish() }.padding(8.dp))
                    }
                    Box(Modifier.fillMaxWidth().height(2.dp)) {
                        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    AndroidView(
                        modifier = Modifier.weight(1f).fillMaxWidth().navigationBarsPadding(),
                        factory = { ctx ->
                            WebView(ctx).also { w ->
                                web = w
                                w.settings.javaScriptEnabled = true
                                w.settings.domStorageEnabled = true
                                w.settings.setSupportMultipleWindows(false)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(w, true)
                                w.webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                                        val url = req.url
                                        val scheme = url.scheme.orEmpty()
                                        if (scheme != "http" && scheme != "https") {
                                            openGatewayLink(this@PayActivity, url.toString()) { w.loadUrl(it) }
                                            return true
                                        }
                                        return false
                                    }

                                    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                                        loading = true
                                        val u = Uri.parse(url)
                                        val path = u.path.orEmpty().trimEnd('/')
                                        // Back on the page it started from after a payment, or on PayU's done page: finished.
                                        if (path.contains("/payu/done/") || (left && path == startPath)) {
                                            finish()
                                            return
                                        }
                                        if (path != startPath) left = true
                                    }

                                    override fun onPageFinished(view: WebView, url: String) { loading = false }
                                }
                                w.loadUrl(start)
                            }
                        },
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        web?.destroy()
        web = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_URL = "url"

        /** Pay: an invoice in the app's checkout; any other fee page (a lead's) here. */
        fun open(context: Context, url: String) {
            val invoice = CheckoutActivity.invoiceIn(url)
            if (invoice != null) CheckoutActivity.open(context, invoice) else view(context, url)
        }

        /** Show the page itself (an invoice to read, a lead's fee page). */
        fun view(context: Context, url: String) {
            context.startActivity(Intent(context, PayActivity::class.java).putExtra(EXTRA_URL, url))
        }
    }
}
