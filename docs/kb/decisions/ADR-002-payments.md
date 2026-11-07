# ADR-002: Payments In The Client App

> Part of the Legum Lex Android knowledge base. Verified against: client app 6c069eb, staff app e52a8ca, customers_api 97de5b4, api 9df1506, cases cb0a820 (2026-11-07). How client payments moved from in-app, to website-only, to a WebView, to native checkout within six weeks.
>
> This file is identical in legum-lex-android/docs/kb and legum-lex-staff/docs/kb.

## Requirement

- 2026-10-19: "Since we are running an Android app without using in-app purchase, all payments will be made through Razorpay via a website link rather than through the Android app." [SESSION 3adfe568 2026-10-19T16:39]; "The invoice already provides you with a payment link… you can simply show the invoice on the web app to collect payment." [SESSION 3adfe568 2026-10-19T16:47]
- 2026-11-01: "You don't need to tell the client that he's paying on the website or outside it." / "why not trigger the payment processor within the app itself? Additionally, I also allow my clients to pay partially" [SESSION 3adfe568 2026-11-01T20:17]
- 2026-11-01: "I want an inline edit instead of editing it on the new screen. The new screen is a waste." / "do not switch back to the app screen until and unless the confirmation has arrived" [SESSION 3adfe568 2026-11-01T20:55]

## Alternatives considered

On 2026-10-19: (a) extend the existing in-app Razorpay SDK checkout to enquiry and Agreement Check fees (built: client 24b360d, customers_api 9085b68 `desk/pay`); (b) a custom website pay page `lexon/pay` (built, then removed); (c) every fee raised as an invoice paid on its public `invoice/{id}/{hash}` page (chosen) [SESSION 3adfe568 C18-C19].

On 2026-11-01 19:38-19:40 (AskUserQuestion): "Keep invoice page (Recommended)", "Build as the brief says"; then "Invoice page in WebView (Recommended)" (smallest Play risk, one billing path), "Brief's design" (choose gateway, signed PayU form, status, Razorpay SDK back; bigger Play-policy surface), "Keep browser". Owner chose the WebView, then reversed it 37 minutes later [SESSION 3adfe568 2026-11-01T19:40].

## Decision

Current (since 2026-11-01 21:09, client 8772b0d):
- The invoice screen shows the amount inline (editable when Perfex `allow_payment_amount_to_be_modified`, with "Pay All") and an inline gateway choice when the business takes both.
- `CheckoutActivity` is a pass-through: `POST invoices/{id}/pay` → Razorpay SDK sheet (`com.razorpay:checkout:1.6.40`, `standard-core` forced to 1.7.14) or a PayU signed form auto-submitted in a WebView → `pay/verify` (Razorpay) or `pay/status` polling (PayU) → full-screen "Confirming Your Payment…" until the server records the payment and the invoice re-reads itself.
- Fixed-fee pay buttons (Digital Office, desk, Agreement Check) go straight to the gateway.
- The app holds no secret and signs nothing; payments are recorded server-side by the same gateway objects as the web (`customers_api_register_gateways()`).
[CODE lex@6c069eb K/feature/finance/Checkout.kt; InvoiceDetailScreen.kt:401-500; app/build.gradle.kts:140-145,197] [CODE customers_api@97de5b4 controllers/v1/Invoices.php; helpers/customers_api_helper.php:28-48]

## Reasons

- 10-19: no in-app purchase surface on Play; one billing path; keeps supplier numbering and receipts.
- 11-01: the owner wants payment inside the app, part payments, and no mention of where the payment is taken; one billing path is kept because every payment is still against an invoice.

## Later changes

| When (UTC) | Change | Commits |
|---|---|---|
| before 2026-10-19 | In-app Razorpay SDK checkout for invoices already in the app (`InvoicesRepository.startPayment`, `feature/payment/Razorpay.kt`) | [SESSION 3adfe568 C18] |
| 2026-10-18 | Invoice payments use the invoice business's Razorpay account (two suppliers) | customers_api d70b574, e8df2ac, 99f2f2e |
| 2026-10-19 16:20 | Enquiry/AHC fees in-app Razorpay | client 24b360d |
| 2026-10-19 17:00-19:10 | **Invoice-only**: fees on the website; DO fee as invoice; Razorpay SDK removed | client 2755200, d02d037; customers_api 704b4d5, f102d10; lexon e41f7fe; digital_office 6edae0b |
| 2026-11-01 19:40 | **WebView** invoice page (PayActivity) | client 3ed3b7b |
| 2026-11-01 20:17 | **Native Razorpay SDK + PayU**, part payment; API `invoices/{id}/pay`, `/pay/verify`, `/pay/status` | client 23776d7; customers_api 5f0a1bd |
| 2026-11-01 20:55 | **Inline amount and gateway; confirm before return** | client 8772b0d |
| 2026-11-04 15:32 | **PayU set aside on production**: "Ignore PayU because we have Razorpay already active. We are going to use Razorpay in production." | [SESSION 89b46f22 2026-11-04T15:32] |

## Status

- Implemented and pushed in the client app at 6c069eb.
- `PayActivity` remains, for viewing invoices and for the lead fee page `cases/landing/lead_pay/{hash}`; the lead Agreement Check fee still uses it [CODE lex@6c069eb K/feature/finance/PayActivity.kt:141-145].
- Tested on dev in test mode: both gateways before 8772b0d, only PayU after; the Method chooser untested (no dev business has both keys).
- Intended for production: Razorpay only. The PayU path stays in the app but is not a priority. Production gateways were off on 2026-11-01; the owner said on 11-04 both businesses are live on Razorpay [SESSION 89b46f22 2026-11-04T15:06].
- Stale: [MEMORY feedback_app_payments_by_invoice.md] and the MainActivity KDoc still say the app takes no payments.
- The staff app takes no payments; it records them (Record Payment wizard).

## Open questions

- Should the PayU code path be hidden on production builds now that PayU is set aside?
- Should the lead Agreement Check fee move to native checkout?
