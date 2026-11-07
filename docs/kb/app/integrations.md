# Client App Integrations

> Part of the Legum Lex Android knowledge base. Verified against: client app 6c069eb, staff app e52a8ca, customers_api 97de5b4, api 9df1506, cases cb0a820 (2026-11-07). The client app's external connections: API client, push, payments, document scanner, document viewers and absolute website URLs.

Label shorthand: `[CODE path:line]` means `[CODE lex@6c069eb path:line]`, and `K/` means `app/src/main/java/com/legumlex/client/`.

## 1. API client

[CODE K/data/api/ApiClient.kt]
- Base URL: `BuildConfig.API_BASE_URL` [:49].
  - release: hard-coded production `https://www.legumlex.com/customers_api/v1/` [CODE app/build.gradle.kts:101] (backend_contract §1.1);
  - debug: `API_BASE_URL` from `local.properties`, falling back to a `.invalid` placeholder [CODE app/build.gradle.kts:21-37]. Since 2026-11-01 it is `http://localhost:8088/customers_api/v1/` (USB proxy) [MEMORY reference_phone_dev_over_usb.md] [SESSION 3adfe568 2026-11-01T09:48].
- Moshi converter with `KotlinJsonAdapterFactory`; responses are `Map<String, Any?>` (architecture.md section 6).
- OkHttp interceptors, in order [:32-46]:
  1. `X-Long-Call: 1` → read/write timeout 120 s, header stripped (used by `leadOnboardingDocument`, server reads identity documents while the call is open) [CODE K/data/api/ApiService.kt:727];
  2. `AuthInterceptor` (auth.md section 4);
  3. `HttpLoggingInterceptor` BODY in debug, NONE in release.
- Timeouts: connect 20 s, read 30 s. No certificate pinning, no retry, no response cache.
- Multipart: `uriToFilePart(context, uri, field="file")` reads the whole file into memory [CODE K/data/api/FileUpload.kt].
- 133 Retrofit functions; 9 never called (`me`, seven old `dashboard/*`, `registerDevice`) [CODE K/data/api/ApiService.kt]. Backend: 0 app calls without a route [CODE customers_api@97de5b4 (backend_contract §3.3)].
- Backend facts the app depends on: customers_api controllers extend `CI_Controller`, so no Perfex module init runs; `cases_headless_wiring()` stands in, and some newer helpers (plan, fee, access log) are not loaded [CODE customers_api@97de5b4 controllers/RestController.php:240-255] [CODE cases@cb0a820 helpers/cases_headless_helper.php:18-131] (backend_contract §4.1, §4.3).

## 2. Absolute website URLs (funnel and site pages)

`<site>` = `BuildConfig.API_BASE_URL.substringBefore("customers_api/")` [CODE K/feature/lead/LeadData.kt:49; K/feature/desk/DeskData.kt:325].

| Use | URL | Auth | Evidence |
|---|---|---|---|
| Lead enquiries | `POST <site>lexon/api/funnel/my_sessions`, `thread/{hash}`, `thread/{hash}/reply` | lead JWT (lexon Funnel accepts it as identity) | [CODE K/feature/lead/LeadData.kt:49-102] [CODE lexon@7f56a67 controllers/Funnel.php:226-231] |
| Lead Agreement Check | `GET .../funnel/details/ahc`, `POST start/ahc`, `consent/{hash}`, `intake/{hash}` (multipart) | lead JWT / session hash | [CODE K/feature/lead/LeadAhc.kt:58-156] |
| Notice tracker | `POST <site>cases/landing/ajax_track` | none (X-Skip-Auth; CSRF-excluded on the server) | [CODE K/feature/desk/DeskData.kt:325-326] |
| Lead fee page | `<site>cases/landing/lead_pay/{hash}` in PayActivity | page | [CODE K/feature/lead/LeadAhc.kt:156] |
| Invoice page | `<site>invoice/{id}/{hash}` (view, share) | public hash | [CODE K/feature/finance/MoneyFormat.kt:45-50] |
| Digital Office web | `/mylex/office` | browser | [CODE K/feature/digital_office/DoModels.kt:106] |
| Site pages from sign-in | `openSite(path)` | browser | [CODE K/feature/auth/LoginScreen.kt:801-809] |

`lexon/api/funnel/.+` is CSRF-excluded on the server [CODE lexon@7f56a67 config/csrf_exclude_uris.php:43].

## 3. Push (Pusher Beams over FCM)

- `PushSetup.init` (from `Application.onCreate`) returns early if `BEAMS_INSTANCE_ID` is blank, else `PushNotifications.start` [CODE K/push/PushSetup.kt:30-42]. `google-services` plugin is applied only if `app/google-services.json` exists [CODE app/build.gradle.kts:41-44].
- **Interest** `contact-{contactId}`, added by `registerContact` at client sign-in, `leadStanding` (lead becomes client) and account switch; `clear()` calls `clearDeviceInterests` at sign-out / forget [CODE K/push/PushSetup.kt:45-58,80]. Leads never subscribe [CODE K/data/auth/AuthRepository.kt:172].
- Server publishes to `contact-{id}` (customers_api `helpers/push_helper.php:27-74`, dev allow-list `lex_dev_push_allow`) [CODE customers_api@97de5b4 (backend_contract §3.1)]. No device registration call: `registerDevice` (`mobile_devices/register`) is declared but unused.
- **Channel** `legum_lex_default` ("Updates", IMPORTANCE_HIGH), created in `Application.onCreate`; the legacy `hearing_reminders` channel is deleted [CODE K/push/PushSetup.kt:65-78; K/LegumLexApp.kt:14]. The manifest names it as the FCM default channel [CODE AndroidManifest.xml:82-105].
- **Service** `push.ClientFcmService` extends the Pusher `MessagingService`; Beams' `EmptyMessagingService` is removed with `tools:node="remove"` so this service gets every payload [CODE AndroidManifest.xml:82-105; K/push/ClientFcmService.kt]:
  - always posts the notification itself; title and body from the notification or data `title` / `body`;
  - data keys forwarded as intent extras;
  - deep link from `route`: consultation → `legumlex://consultation/{consultation_id}`, invoice → `legumlex://invoice/{invoice_id}`, hearing → `legumlex://hearing/{hearing_id}`, project → `legumlex://project/{project_id}` [:92-108];
  - `route=ahc` adds progress / "View report" / "Reply" + "View" actions but **no deep link**, so a tap only opens the app [:65-81];
  - notification id = hash of `route:target`, so duplicate pushes collapse [:116-125].
- Deep links are handled by `navController.handleDeepLink` in `AppShell` (see architecture.md section 4) [CODE K/MainActivity.kt:90-97].
- **Missing runtime POST_NOTIFICATIONS request (verified).** The manifest declares `POST_NOTIFICATIONS`, but no code requests it at runtime: the only `RequestPermission()` launchers are CAMERA / RECORD_AUDIO in `SelfView.kt:66-67` and CAMERA in `VideoLobbyScreen.kt:123` [CODE AndroidManifest.xml; grep `POST_NOTIFICATIONS|RequestPermission` over app/src/main/java at 6c069eb]. With targetSdk 36, on Android 13+ notifications stay blocked until the user enables them in system settings [ASSUMPTION: standard platform behaviour, not tested on a fresh install].
- "Private DO channel" from the 11-01 handoff: no client-side code (no extra interest) [MEMORY project_mobile_handoff_2026_11.md] [ASSUMPTION: server or staff-app only].

## 4. Payments

### 4.1 INTENDED vs IMPLEMENTED history

| Date | Rule / build | Evidence |
|---|---|---|
| 2026-10-19 | Owner: > "Since we are running an Android app without using in-app purchase, all payments will be made through Razorpay via a website link rather than through the Android app." Razorpay SDK removed (d02d037); every fee raised as an invoice paid on its public page | [SESSION 3adfe568 2026-10-19T16:39] [GIT d02d037] [MEMORY feedback_app_payments_by_invoice.md] |
| 2026-11-01 19:40 | Handoff brief; owner chose "Invoice page in WebView": PayActivity (3ed3b7b) | [SESSION 3adfe568 2026-11-01T19:40] [GIT 3ed3b7b] |
| 2026-11-01 20:17 | Reversal: pay natively in the app, allow part payments, never tell the client where the payment is taken. CheckoutActivity + Razorpay SDK re-added (23776d7); server `invoices/{id}/pay`, `/pay/verify`, `/pay/status` (customers_api 5f0a1bd) | [SESSION 3adfe568 2026-11-01T20:46] [GIT 23776d7, customers_api 5f0a1bd] |
| 2026-11-01 20:55 | Amount and gateway inline on the invoice screen; checkout becomes a pass-through with a blocking "Confirming Your Payment…" (8772b0d) | [GIT 8772b0d] [SESSION 3adfe568 2026-11-01T21:09] |

The memory note `feedback_app_payments_by_invoice.md` is older than the code; the code is the current truth. Owner comment in code: "The app holds no secret and signs nothing" [CODE K/feature/finance/Checkout.kt:76-83].

### 4.2 Inline pay on InvoiceDetailScreen

[CODE K/feature/finance/InvoiceDetailScreen.kt:401-508; InvoiceDetailViewModel.kt:38-57]
- Options: `GET invoices/{id}/pay` → `{due, due_formatted, can_change_amount, minimum, gateways[{id,label}], refusal}` [CODE K/feature/finance/Checkout.kt:87-109].
- Amount field editable when `canChange` (Perfex `allow_payment_amount_to_be_modified`), with a "Pay All" shortcut; gateway selector only when 2+ gateways.
- "Pay Invoice" → `CheckoutActivity.open(context, id, amount, gateway)` [:473].
- "View Full Invoice" → `PayActivity.view(paymentUrlFor(inv))`; "Share Link" shares the invoice URL [:508].
- The screen collects `PaymentEvents.events`; on a match `vm.paid()` re-reads the invoice and sets `PaymentEvents.refreshed = id`, then shows a snackbar [:138-145].

### 4.3 CheckoutActivity (Razorpay SDK / PayU WebView)

[CODE K/feature/finance/Checkout.kt:174-461] (`ComponentActivity`, `PaymentResultWithDataListener`)
1. `begin` → `POST invoices/{id}/pay` (amount, gateway). Three answers:
   - `{choose, labels}` → `ChooseGateway` UI;
   - `{gateway:"payu", action, fields, txnid}` → `PayuPage` WebView that auto-posts the server-signed form (a self-submitting HTML form, because loading the postUrl drew blank);
   - otherwise Razorpay: options from the server (key id, order id, amount in paise as a string, theme `#0A0A0A`, prefill, retry max 3) → `Checkout().open(activity, options)` [:174-207,252-271].
2. Razorpay success → `POST invoices/{id}/pay/verify` (razorpay_order_id, payment_id, signature); verify failure shows "Confirming". Errors: cancelled → "No payment was made"; others → "did not go through" [:209-223].
3. PayU: WebView intercepts `/payu/done/` or a return to `/invoice/{id}/` [:418-428], then polls `GET invoices/{id}/pay/status?txnid` every 3 s, up to 20 times, until captured / failed / flagged / open [:226-246].
4. UPI and `intent://` links go to the phone's apps, with `browser_fallback_url` as fallback (`openGatewayLink`) [:446-461].
5. **Confirm before return**: the outcome goes to `PaymentEvents.emit(o)` (succeeds only if a subscriber exists); the activity then waits up to 5 s for `refreshed == invoiceId` before finishing; with no subscriber it shows a Toast [:301-313].
- Server side: `customers_api_register_gateways()` registers `razorpay_gateway` and `payu_gateway` on API requests so app payments are recorded by the same gateway objects as the web [CODE customers_api@97de5b4 helpers/customers_api_helper.php:28-48].
- Razorpay SDK `com.razorpay:checkout:1.6.40` as a literal (not in the version catalog) with `standard-core` forced to 1.7.14 because the SDK POM asks for `latest.integration`; R8 keeps `com.razorpay.**` [CODE app/build.gradle.kts:143-147,197; app/proguard-rules.pro].
- PayU test sandbox has an http step: debug-only network config allows cleartext to `payubiz.in` [CODE app/src/debug/res/xml/network_security_config.xml].
- Known: Razorpay added a ₹0.02 customer convenience fee in testing (an account setting, recorded as paid) [SESSION 3adfe568 (history_late §4)].

### 4.4 PayActivity (WebView over site pages)

[CODE K/feature/finance/PayActivity.kt:42-152]
- `open(url)`: an invoice link (`/invoice/{id}/{hash}`, matched by `CheckoutActivity.invoiceIn`) is redirected to CheckoutActivity; anything else (e.g. a lead's `cases/landing/lead_pay/{hash}`) is shown in the WebView [:141-145]. `view(url)` always shows the page.
- Closes when the page returns to its start URL or to PayU's done page.
- Callers: DeskScreen.kt:190, DoIntakeScreen.kt:122, VideoLobbyScreen.kt:351, AhcFunnelScreen.kt:45, LeadAhc.kt:156. Fixed-fee pay buttons (DO, desk, Agreement Check) therefore go straight to the gateway when they hold an invoice link.

### 4.5 Payment status at 11-07

- Both gateways tested on dev in test mode before 8772b0d; only PayU after it. Method chooser untested (no dev business with keys for both). Production gateways were off on 11-01 ("This Invoice Can't Be Paid Here"), Legum Lex Razorpay keys empty [SESSION 3adfe568 2026-11-01T21:09 (history_late D9)].
- Since 2026-11-04 funnel / Chamber fee calls refuse cleanly when no gateway is on; whether the app shows the message is unverified [GIT lexon 7f56a67, digital_office 6fa4525].
- Plan and notice-fee invoices paid in the app probably do not trigger their `invoice_status_changed` handlers (in `cases.php`, not loaded headlessly) [ASSUMPTION, web_impact §1.A].

## 5. Document scanner

- ML Kit document scanner 16.0.0-beta1; `rememberDocumentScannerLauncher` wraps `GmsDocumentScanner` and returns a PDF Uri [CODE K/feature/scanner/DocumentScanner.kt:17-27].
- Used by `AttachmentsCard` (consultations), the desk's account documents, and `LeadOnboarding`.

## 6. Document viewers

| Viewer | Source endpoint | Cache | Renderer | Evidence |
|---|---|---|---|---|
| `CaseDocViewerScreen` | downloadCaseOrderDocument / downloadCaseDocument (@Streaming) | `cacheDir/casedoc` | platform `PdfRenderer` | [CODE K/feature/cases/CaseDocViewerScreen.kt:195,231] |
| `NoticeDocViewerScreen` | downloadNoticeDocument | `cacheDir/noticedoc` | PdfRenderer | [CODE K/feature/notices/] |
| `AhcDocumentViewerScreen` | downloadAhcDocument | `cacheDir/ahc/ahc_{cid}.pdf` | PdfRenderer | [CODE K/feature/consultations/] |
| `DocFileViewerScreen` | downloadDocRequestFile | `cacheDir/docreq` | PdfRenderer | [CODE K/feature/consultations/] |
| Vault | downloadDocument | `cacheDir/docs` | external app via FileProvider + `ACTION_VIEW` | [CODE K/feature/documents/DocumentsRepository.kt:51; VaultScreen.kt:624] |
| Project files | downloadProjectFile | `cacheDir/proj_*`, `projfile_*` | external app via `ACTION_VIEW` chooser + `FLAG_GRANT_READ_URI_PERMISSION` | [CODE K/feature/projects/ProjectFilesScreen.kt:144-156,385-390] |

- FileProvider `${applicationId}.fileprovider`, cache paths `docs/` and `ahc/` [CODE app/src/main/res/xml/file_paths.xml].
- All caches wiped at sign-out and account switch [CODE K/data/auth/AuthRepository.kt:126-138].
- Gap: project files are view-only on the web since 2026-11-03 (server sends `view_only`, `X-Lex-View-Only: 1`, `no-store`); the app ignores this and lets an external app save or share [GIT customers_api e6eb3e1] (features.md section 17).

## 7. Other device integrations

- Google Credential Manager 1.3.0 + googleid 1.1.1 for Google sign-in (auth.md).
- CameraX 1.4.1 + AudioRecord: Digital Office self-view only; Google Meet opened externally for the call [CODE K/feature/digital_office/SelfView.kt; VideoLobbyScreen.kt:65].

## Open questions

- Should the app request POST_NOTIFICATIONS (for example after first sign-in)? Needs an owner decision and the web-first rule does not obviously apply (device-only concern).
- Is PayU configured on production at all, and has the PayU WebView path been run against dev since the owner said production uses Razorpay only? (web_impact §4.)
- Should `route=ahc` pushes get a deep link to the consultation?
