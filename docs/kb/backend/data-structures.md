# Data Structures

> Part of the Legum Lex Android knowledge base. Verified against: client app 6c069eb, staff app e52a8ca, customers_api 97de5b4, api 9df1506, cases cb0a820 (2026-11-07). Shared payload shapes and enumerations both apps and the web depend on. No secrets and no client data.
>
> This file is identical in legum-lex-android/docs/kb and legum-lex-staff/docs/kb.

Rule: never invent a status, option or tag value in an app. Take it from the server or from the web module it mirrors [MEMORY feedback_replicate_cases_module.md]. JSON numbers arrive in Kotlin as `Double` (Moshi), so convert ids with `(v as? Number)?.toInt()`.

---

## 1. Envelopes and errors

| Shape | Where | Ref |
|---|---|---|
| `{status: bool, data?, message?, meta?, code?}` | customers_api endpoints written for the app | [CODE customers_api@97de5b4 controllers/v1/*.php] |
| `{data}` / `{message}` (no `status`) | customers_api legacy Perfex endpoints: `authentication`, `invoices` list/detail, `register`, contracts… | same |
| `{status: bool, data?, message?}` + endpoint keys | api (staff) | [CODE api@9df1506 controllers/*.php] |
| 403 `{status:false, code:'view_only', message:'You have view-only access. Ask the account owner if you need to make changes.'}` | client writes by a view-only contact (inert headlessly today) | [CODE cases@cb0a820 helpers/cases_plan_helper.php:276-312] |
| 402 `{status:false, code:'plan_limit', unit, limit, used, message}` | `cases_plan_refusal()`; no app endpoint returns it yet | [CODE cases@cb0a820 helpers/cases_plan_helper.php:182-202] |
| 403 `{status:false, code:'no_permission'}` | customers_api dashboard methods without `cases` | [CODE customers_api@97de5b4 controllers/v1/Dashboard.php:34-38] |
| 403 `{message:'not_permission_to_perform_this_action'}` | `has_contact_permission` refusal | [CODE cases@cb0a820 helpers/cases_permissions_helper.php:28-54] |
| 403 `{error:'not_permitted'}` | client `POST notices` / extract | [GIT customers_api adac96d 2026-10-20] |
| 403 `{error:'unverified', message}` | OTP request for an unverified number | [GIT customers_api bd13fa2, dec2677 2026-10-19] |
| 401 `login_to_continue` | customers_api stored-token mismatch | [CODE customers_api@97de5b4 helpers/customers_api_helper.php:133-251] |
| 404 `{status:false, message:'Token is not found' \| 'Token expired or revoked'}` | api dead/missing token | [CODE api@9df1506 controllers/REST_Controller.php:797,845] |
| 403 `text_rest_api_key_permissions` | api feature outside STAFF_APP_FEATURES | [CODE api@9df1506 controllers/REST_Controller.php:1021-1083] |
| 410 Gone | api `digital_office/refund/{id}`, `Thirdparty` | [GIT api 7393723, c094c4c] |
| `cases_payment_refusal` message | staff payment recording refused by the books rules; show it, never work around it | [DOC cases/docs/developer/mobile_handoff_2026-11-01.md §3] |

## 2. Identity and accounts

**Client JWT claims**: `client_id, contact_id, client_logged_in, API_TIME`, plus `person` when the contact is the verified holder of the person's number (OTP sign-in and account switch). The app never reads `person`; it lets `accounts/switch` skip the code [CODE customers_api@97de5b4 helpers/customers_api_helper.php:66-89; controllers/v1/Authentication.php:167-172; controllers/v1/Accounts.php:167].

**Lead JWT claims**: `lead`, `lead_key`; valid only when equal to `tblcustomers_api_leads.token` [CODE customers_api@97de5b4 helpers/customers_api_lead_helper.php:16-59]. App role values `role` = `lead` or client; lead status `lead` / `awaiting_approval` / `rejected` [CODE lex@6c069eb K/data/auth/SessionRole.kt:19].

**Same person, several accounts** (messaging mig 005, cust_api caa66a8):
```
GET accounts → {status, data: {accounts: [{id, label, kind, current}], proved}}
POST accounts/switch {to} → {status, data: {token, contact, client}}      // proved
                         → {status, data: {code: true, to, ref}}           // a code is needed
POST accounts/switch/status {ref} → {status, data: {..., can_sms}}
POST accounts/switch/sms    {ref}
POST accounts/switch/verify {ref, to, code} → {status, data: {token, contact, client}} | {status, message, restart}
```
Profile adds `shared_with` and `can_verify`; a linked contact never verifies a number of its own [CODE lex@6c069eb K/feature/profile/Accounts.kt:82-160] [DOC mobile_handoff_2026-11-01.md §2].

**One mobile number**: `tblcontacts.phonenumber` / `tblstaff.phonenumber`, stored compact `+digits`; `tblmessaging_phones` is only a verification stamp, valid while equal to the record. Profile phone steps: `request, status, sms, verify, updates, remove` [MEMORY project_one_mobile_number.md] [CODE lex@6c069eb K/data/api/ApiService.kt:88-98].

**Staff login payload** (`staff` object of login/me): `is_admin`, `is_advocate`, `two_factor_enabled`, `capabilities.lexcent` → `{isAdmin, canView, canCreate, canEdit, canApprove, canDelete, canSubmitExpense, canRequestPay, canViewInvoices, detectedRoleKey (advocate|clerk|helper|expert|other), detectedRoleLabel}`, `capabilities.customers.{create,edit}`, `capabilities.projects.create` [CODE lexco@e52a8ca J/data/auth/AuthRepository.kt:98-136; J/data/auth/LexcentCapabilities.kt:17-38].

**Staff 2FA challenge**: `{status:true, two_factor:"email"|"app", ref, to}` → `POST auth/two_factor {ref, code}` → `{status, token, staff}`; `ref` is an HMAC reference valid 15 minutes [CODE api@9df1506 controllers/Auth.php:646-690].

**Contact permission ids**: cases 7, consultations 8, agreements 9, notices 10 (cases mig 246) [GIT customers_api 58e9b48 2026-10-14].

## 3. Invoices and payments

```
GET invoices/{id}/pay →
  {status, data: {due, due_formatted, can_change_amount, minimum,
                  gateways: [{id: "razorpay"|"payu", label}], refusal}}
POST invoices/{id}/pay {amount, gateway} →
  {choose: true, labels}                                 // ask which gateway
  {gateway: "payu", action, fields, txnid}               // signed form, auto-submitted in a WebView
  Razorpay order: key_id, order_id, amount (paise), prefill …
POST invoices/{id}/pay/verify {razorpay_order_id, razorpay_payment_id, razorpay_signature}
GET  invoices/{id}/pay/status?txnid → {status, data: {state, message, due}}
     state ∈ captured | failed | flagged | open (app polls until one of these)
```
[CODE customers_api@97de5b4 controllers/v1/Invoices.php] [CODE lex@6c069eb K/feature/finance/Checkout.kt:87-109,174-246]

- Part payment follows Perfex `allow_payment_amount_to_be_modified`; `minimum` bounds it [SESSION 3adfe568 2026-11-01T20:46].
- Gateway labels in UI: "Pay With Razorpay" / "Pay With PayU" (Title Case) [DOC mobile_handoff_2026-11-01.md:75].
- Only Razorpay and PayU exist; subscriptions and saved cards answer 404 [GIT cases 4f9ced1 2026-11-01].
- Payment modes are per business (`tblcases_payment_mode_books`); hide system and retired modes [DOC mobile_handoff_2026-11-01.md §3].
- Invoice issuer is one of two businesses: Nyay & Co. (legal work) or Legum Lex (platform); issuer block from `document_identity` [GIT customers_api 53e1729, f3844a0 2026-10-19].
- Fixed fees (enquiry, Agreement Check, Digital Office) are raised as invoices (`cases_raise_fee_invoice()`, lexon `Fee_invoice_service`, option `lexon_fee_invoice_issuer`) [SESSION 3adfe568 C18-C19].
- Staff Record Payment body: `{amount, date, paymentmode, transactionid, note, send_receipt}` [CODE lexco@e52a8ca J/feature/lexcent/RecordPaymentSheet.kt:43-99].

## 4. Legal notice fee object (since customers_api 97de5b4)

```
fee: null                          // no pay-first fee applies (or the helper is not loaded)
fee: {state, amount, invoice_id, pay_url, held}
  state ∈ none | unpaid | paid | cancelled
  held  = true while this is a pay-first fee and state != paid
```
[CODE customers_api@97de5b4 controllers/v1/Legal_notices.php:635-640] [CODE cases@cb0a820 helpers/cases_fee_helper.php:24,185-200]

Related fields the client app already reads from the server: `status_label` (may be "Awaiting Payment"), `needs_approval` (Outbound + Draft + not approved + a client-visible document), `next_step` [CODE customers_api@97de5b4 controllers/v1/Legal_notices.php:642-648] [CODE lex@6c069eb feature/notices/NoticesRepository.kt:38,45,98]. Inert in the app API until the fee helper loads (see [wiring-and-traps.md](wiring-and-traps.md)).

## 5. Consultations

- **Kinds** (`type`): `general`, `legal_enquiry`, `agreement_review` ("Agreement Check"), `legal_notice`, `document_request`, `digital_office` ("Video Session") [CODE lex@6c069eb K/feature/consultations/ConsultationModels.kt:18-25].
- **Phase**: `transactional` or `litigation` (staff can switch) [GIT api 5fb1e88, customers_api 06acc99 2026-09-23] [CODE lexco@e52a8ca J/feature/consultations/ConsultationDetailScreen.kt].
- **Staff stepper**: raised → acknowledged → in hand → closed; opening a client-raised consultation acknowledges it server-side (idempotent) [CODE lexco@e52a8ca J/feature/consultations/ConsultationDetailViewModel.kt:59-63].
- **Filters** (client list): id, phase, status, request_status, type, urgent, q, limit, offset, ahc_state, order_pending_first; default tab Active, "Awaiting You" last [CODE customers_api@97de5b4 controllers/v1/Consultations.php] [SESSION 3adfe568 2026-10-20T11:13].
- **Scheduling**: `request_status=scheduled` + date/time [CODE api@9df1506 controllers/Consultations.php].
- **Visibility**: messages shown to the client only with `visible_to_customer=1`; advocate notes only when made client-visible; replies and attachments carry a per-item visibility toggle in the staff app [SESSION 3adfe568 C18-C19].
- **Client actions** Cancel, Cannot Send (doc request decline) and Share live in `Consultations_model`, shared by the portal and the app [SESSION 3adfe568 2026-10-29T09:36].
- **Agreement Check confirm**: `contract_type ∈ {saas, lease, employment, nda, consulting, other}`, `parties`, `concern` [CODE customers_api@97de5b4 controllers/v1/Ahc_funnel.php].
- Status-name drift between surfaces (Completed/Resolved/Released, Declined/Rejected) is an open audit item [SESSION 3adfe568 audit ledger 2026-10-28].

## 6. Projects (cases Project Types, mig 259)

- Firm-editable project types, seeded with four; each type has ordered stages [SESSION 3adfe568 C29] [GIT cases 5d238d6, c44db6e, 8eb1417].
- Stage actions (staff): `move` (Move To: target, expected, official_date, note, reference, tell), `back`, `waiting` (who, authority_name), `official_date`, `assign` (milestone_id / task_id, staff_id); response carries `sends_at` (when the client is told) [CODE api@9df1506 config/routes.php; controllers/Project_stages.php].
- **Waiting on**: the Firm, You (the client), or an Authority; staff chip reads "With The Firm" for the firm [GIT staff a004c82 2026-10-25].
- Client sees "where it stands, at each stage" and "Needs You" when waiting on the client [GIT client 55368c3 2026-10-24].
- Members: any active staff member since 2026-11-03 (was the client's counsel only) [GIT cases c8c0813].
- Tasks always belong to a project. Task status: 5 complete, 4 reopen (Perfex) [CODE lexco@e52a8ca J/feature/tasks/TasksRepository.kt:60].
- Project files for clients: rows carry `view_only: true`; file stream sends `X-Lex-View-Only: 1` and `Cache-Control: private, no-store`; `?download` refused [CODE customers_api@97de5b4 controllers/v1/Projects.php:811-818,1026-1053].
- Perfex tab settings gate client tabs (`viewDiscussions`, `view_files`…) [CODE lex@6c069eb feature/projects/ProjectDetailViewModel.kt:94].
- No per-stage fees: projects are invoiced as one (rule F29).

## 7. Notification targets

**Staff** (server `api_notification_target($link)` in `api/helpers/api_notifications_helper.php`; data-only push): extras `screen`, `target_id`, `web`, `notification_id` (or `session_id` for Digital Office pushes). `screen` ∈ `task, case, consultation, project, invoice, do_session, digital_office, expense_queue, my_expenses, my_partner_pay, partner_pay_queue, tithe, advocate, profile`; anything else opens `web` unless `screen == "none"` [CODE lexco@e52a8ca J/push/NotifTarget.kt:18-50; J/MainActivity.kt:80-90].

**Client** (Beams `contact-{id}`): data key `route` with the matching id: `consultation` → `legumlex://consultation/{consultation_id}`, `invoice` → `legumlex://invoice/{invoice_id}`, `hearing` → `legumlex://hearing/{hearing_id}`, `project` → `legumlex://project/{project_id}`; `route=ahc` adds actions but no deep link. Notification id = hash of `route:target` (duplicates collapse) [CODE lex@6c069eb K/push/ClientFcmService.kt:65-125].

**Client bell** = firm announcements (`tblannouncements`, showtousers=1; read via `tbldismissed_announcements`) + "Requires Your Attention" (funnel matters + action queue) [GIT customers_api 1d18681, ce9cf58].

## 8. Digital Office realtime config (staff)

`GET digital_office` → `pusher: {key, cluster (default ap2), channel (default do-chamber), event (presence_changed), advocate_channel (private-do-advocate-{id}), advocate_event (session_event)}`; private events `session_event`, `waitroom_event` [CODE lexco@e52a8ca J/feature/digitaloffice/DigitalOfficeRepository.kt:171-183; DigitalOfficePresenceClient.kt:62-99].

## Open questions

- Full nested `data` shapes for most endpoints are not documented (top-level keys only).
- Names of the four seeded project types are not recorded in the findings.
