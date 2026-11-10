# Client API Contract

> Part of the Legum Lex Android knowledge base. Verified against: client app 6c069eb, staff app e52a8ca, customers_api ee69445, api 9df1506, cases 00bd1f0 (2026-11-07; REQ-001 deltas applied on top of the 97de5b4 and cb0a820 review). The full `customers_api` contract the client app uses, with the app screen behind each route.
>
> This file is identical in legum-lex-android/docs/kb and legum-lex-staff/docs/kb.

**Last verified against customers_api@97de5b4 (2026-11-07)**

Paths are relative to `…/customers_api/v1/`. Module paths are relative to `modules/`. `K/` = client `app/src/main/java/com/legumlex/client/`. Source: [CODE customers_api@97de5b4 config/routes.php:8-144, controllers/v1/*.php] cross-checked with [CODE lex@6c069eb K/data/api/ApiService.kt].

## 1. Authentication and envelope

**Last verified against customers_api@97de5b4 (2026-11-07)**

| Layer | Behaviour | Ref |
|---|---|---|
| Base URL | Release `https://www.legumlex.com/customers_api/v1/`; debug from `local.properties` `API_BASE_URL` (now `http://localhost:8088/customers_api/v1/` over USB) | [CODE lex@6c069eb app/build.gradle.kts:36,101] [MEMORY reference_phone_dev_over_usb.md] |
| Base class | `CustomerApi\RestController extends CI_Controller`: **no module init runs**; constructor calls `cases_headless_wiring()` | [CODE customers_api@97de5b4 controllers/RestController.php:240-255] |
| REST layer | `rest_auth`, `rest_enable_keys`, `rest_enable_limits` all false; auth is in the controllers | [CODE customers_api@97de5b4 config/customer_rest.php:113,326,489] |
| Token | JWT in `Authorization` (`Bearer ` optional). 7 days from `API_TIME`. Signed with `hash_hmac('sha256','customers_api.token.v1',APP_ENC_KEY)` since e3027f0 (2026-11-04); before that the key came from `base_url()` (public) | [CODE customers_api@97de5b4 config/jwt.php:26,38; libraries/Authorization_token.php:69-96] [GIT e3027f0 2026-11-04] |
| Contact token | `customers_api_contact_token()` → `{client_id, contact_id, client_logged_in, API_TIME}` (+ `person` when the contact is the person's holder); stored as the contact's **single** live token in `tblcontacts.customer_api_key`. Response adds `token, email, name, picture` | [CODE customers_api@97de5b4 helpers/customers_api_helper.php:66-89; controllers/v1/Authentication.php:167-172] |
| `isAuthorized()` order | (1) module inactive → 404; (2) JWT invalid/expired → 401; (3) no `contact_id` (lead token) → 401; (4) contact missing, empty key or header ≠ stored token → 401 `login_to_continue`; (5) contact inactive → 401; (6) register contact permissions 7-10; (7) client inactive → 401; (8) `cases_view_only_refusal()` → 403 `view_only` (active on app requests since cases@b18dfd3, REQ-001; invoice `pay`, `pay/verify` and `pay/status` are exempt since cases@00bd1f0, owner Q9; see [wiring-and-traps.md](wiring-and-traps.md)); (9) sets `$GLOBALS['cases_api_actor']` | [CODE customers_api@97de5b4 helpers/customers_api_helper.php:133-251] |
| Contact permission | `has_contact_permission(x)`: cases 7, consultations 8, agreements 9, notices 10, plus Perfex invoices, projects, contracts, estimates, proposals, support. Fail → 403 `{message: not_permission_to_perform_this_action}` | [CODE cases@cb0a820 helpers/cases_permissions_helper.php:28-54] |
| Lead token | `isLeadAuthorized()` → `customers_api_lead_from_request()`: reads the `lead`/`lead_key` claims **without checking the signature**, then requires the whole token to `hash_equals` `tblcustomers_api_leads.token`. 7 days. Lead tokens reach only `lead/*` and `guest/desk` (and lexon's website funnel, which also accepts them) | [CODE customers_api@97de5b4 helpers/customers_api_lead_helper.php:16-59] [CODE lexon@7f56a67 controllers/Funnel.php:226-231] |
| CSRF | `customers_api\/.+` excluded | [CODE customers_api@97de5b4 config/csrf_exclude_uris.php] |
| Envelope | App-era endpoints: `{status: bool, data?, message?, meta?, code?}`. Legacy Perfex endpoints (`authentication`, invoices list, register, contracts…) return the model array, usually `{data}` or `{message}` with no `status` | [CODE customers_api@97de5b4 controllers/v1/*.php] |
| Routing | Explicit routes `config/routes.php:8-136`; fallback `customers_api/v1/X` → `v1/X/X`; REST dispatches `{method}_{httpverb}` | [CODE customers_api@97de5b4 config/routes.php:142-144] |

App side: `AuthInterceptor` adds the JWT; `X-Skip-Auth: 1` on login, OTP, Google, google_config, guest desk, notice tracker. A 401 on a request carrying the stored token ends the session once; 403 does not [CODE lex@6c069eb K/data/api/AuthInterceptor.kt:17-31].

## 2. Route table

**Last verified against customers_api@97de5b4 (2026-11-07)**

Legend: **Gate** after `isAuthorized()` ("auth" = isAuthorized only, "lead" = isLeadAuthorized, "none" = unauthenticated). `{s,d,m}` = `{status,data,message}`. **App** = ✓ used, ◇ declared in ApiService but never called, ✗ not declared. **Screen** = the client screen or flow that calls it.

### 2.1 Authentication, profile, accounts

| Method | Path | Controller::method | Gate | Request | Response | Codes | App | Screen |
|---|---|---|---|---|---|---|---|---|
| POST | authentication | Authentication::authentication_post | none + throttle | email, password | `{data:{client_id,contact_id,…,token}, message}` | 400, 422, 429 | ✓ | LoginScreen (email link; debug test-contact button) |
| POST | authentication/otp/{request\|status\|sms\|verify} | otp_{step}_post | none | number / ref / code | messaging step body; verify → token; request may be 403 `error=unverified` | 400, 403, 404 | ✓ | LoginScreen Mobile view (status polled 5 s) |
| GET | authentication/me | me_get | token inline | none | `{s,d}` | 401 | ◇ | none |
| GET | authentication/google_config | google_config_get | none | none | `{client_id, sign_up}` | 503 | ✓ | LoginScreen (client id; "Onboard Now" shown when `sign_up`) |
| POST | authentication/google | google_post | none | id_token, allow_lead | contact token, or a lead token when `allow_lead` and no contact | 401, 403, 404, 422, 500 | ✓ | LoginScreen Google; guest desk sign-in |
| GET | logout | Logout::logout_get | auth | none | `{message}` | none | ✓ | More → Sign Out (best effort, 5 s) |
| POST | register | Register::register_post | none | Perfex fields | `{message}` | 400, 422, 429 | ✗ | none (old password sign-up; still live) |
| GET | profile | Profile::profile_get | auth | none | `{s,d}` incl. `shared_with`, `can_verify` | 404 | ✓ | Profile; Home load canary; ProfileHub |
| POST | profile/phone/{request\|status\|sms\|verify\|updates\|remove} | Profile::phone_post | auth | number, ref, code, on | `{s,m,d?}` | 404 | ✓ | Profile → Mobile card |
| GET | accounts | Accounts::index_get | auth | none | `{s,d:{accounts:[{id,label,kind,current}], proved}}` | none | ✓ | Header AccountSwitch (shown when 2+ accounts) |
| POST | accounts/switch | Accounts::switch_post | auth | to | `{s,d:{token,contact,client}}` or `{code:true,to,ref}` | 422, 429, 503 | ✓ | AccountSwitch |
| POST | accounts/switch/{status\|sms\|verify} | {status,sms,verify}_post | auth | ref, to, code | `{s,d}` / `{s,m,restart}` | 422, 429 | ✓ | "Switch Account" code dialog (status polled 10 s × 12) |
| POST | mobile_devices/register | Mobile_devices::register_post | auth | device_id, platform, fcm_token, device_name, os_version, app_version | `{s,m}` | 422 | ◇ | none (push uses Beams interests) |

### 2.2 Home and dashboard (Dashboard.php, gate auth; some need `cases`, else 403 `code:no_permission`)

| Method | Path | ::method | Extra gate | App | Screen |
|---|---|---|---|---|---|
| GET | dashboard/workspaces | workspaces_get | none | ✓ | Home (Services tiles) |
| GET | dashboard/action_items | action_items_get (since eab7c0a: notice items need `notices`, consultation/ahc items need `consultations`) | none | ✓ | Home load → ActionHub → Notifications "Requires Your Attention" |
| POST | dashboard/action_dismiss | action_dismiss_post (key; 422) | none | ✓ | Notifications |
| POST | dashboard/action_restore | action_restore_post (key, all; 422) | none | ✓ | Notifications |
| GET | dashboard/next_event | next_event_get | none | ✓ | Home "What's On" |
| GET | dashboard/chamber | chamber_get | none | ✓ | Home; Digital Office tab (polled 30 s) |
| GET | dashboard/stats · recent_activity · next_hearing · active_services · legal_health · active_matters | *_get | cases | ◇ | none (old dashboard) |
| GET | dashboard/profile | profile_get (404) | none | ◇ | none |
| GET | dashboard/balance | balance_get | none | ✗ | none |

### 2.3 Litigation (gate `cases`)

| Method | Path | ::method | Request | Codes | App | Screen |
|---|---|---|---|---|---|---|
| GET | cases | Cases::cases_get | id (detail), status | 400, 404 | ✓ | CasesScreen, CaseDetailScreen |
| GET | cases/timeline/{id} | timeline_get | none | 404 | ✓ | CaseDetailScreen |
| GET | cases/order-document/{orderId} | order_document_get (stream) | none | 404 | ✓ | CaseDocViewerScreen ("Open Order") |
| GET | cases/document/{fileId} | document_get (stream) | none | 404 | ✓ | CaseDocViewerScreen (Docket) |
| GET | hearings | Hearings::index_get | id, case_id | 400, 404 | ✓ | HearingDetailScreen, CalendarScreen |
| GET | hearings/upcoming | upcoming_get | none | none | ✓ | CalendarScreen |

### 2.4 Consultations (gate `consultations`, permission 8)

| Method | Path | ::method | Request | Response / codes | App | Screen |
|---|---|---|---|---|---|---|
| GET | consultations | index_get | id, phase, status, request_status, type, urgent, q, limit, offset, ahc_state, order_pending_first | `{s,d,meta}`; 400, 404 | ✓ | ConsultationsScreen, ConsultationDetailScreen |
| POST | consultations/create | create_post | type, tag, note, is_urgent, priority, preferred_date, preferred_time, case_link_id, files[] | **201**, 422, 500 | ✓ (form-encoded; files uploaded afterwards) | Desk / consultation creation |
| DELETE | consultations/{id} | index_delete | none | 403, 404, 409 | ✓ | ConsultationActionsMenu |
| POST | consultations/{id}/cancel | cancel_post | reason | idempotent | ✓ | CloseConsultationSheet |
| GET | consultations/{id}/linkage | linkage_get | none | `{parent,linked}`; 404 | ✓ | ConsultationDetail |
| PUT | consultations/{id}/preferred | preferred_put | preferred_date, preferred_time | 404, 409, 422 | ✓ | ConsultationDetail |
| GET/POST | consultations/{id}/attachments | attachments_get / _post | file, tag | 404 | ✓ | AttachmentsCard (upload, ML Kit scan) |
| DELETE | consultations/attachments/{fid} | attachments_delete | none | 403, 404 | ✓ | AttachmentsCard |
| GET | consultations/doc_requests/{cid} | doc_requests_get | none | 404 | ✓ | DetailDocReq |
| POST | consultations/doc_requests/{rid}/fulfill | doc_request_fulfill_post | file | 400, 404, 409, 422 | ✓ | DetailDocReq |
| POST | consultations/doc_requests/{rid}/decline | doc_request_decline_post | reason | none | ✓ | DetailDocReq |
| GET | consultations/doc_requests/{rid}/file | doc_request_file_get (stream) | none | 404 | ✓ | DocFileViewerScreen |
| GET | consultations/ahc/{id} | ahc_get | none | 404 | ✓ | DetailAHC |
| GET | consultations/{id}/ahc/document | ahc_document_get (stream) | none | 404 | ✓ | AhcDocumentViewerScreen |
| GET | consultations/appointment/{id} | appointment_get | none | 404 | ✓ | ConsultationDetail |
| GET | consultations/do_session/{id} | do_session_get | none | 404 | ✓ | DetailDigital |
| POST | consultations/reply/{id} | reply_post | message | **201**, 404, 422, 500 | ✓ | ConversationThreadScreen |

### 2.5 Agreement Check funnel (Ahc_funnel.php, gate `consultations` since 2026-11-01)

All return `{s,d:shape,m?}` [CODE customers_api@97de5b4 controllers/v1/Ahc_funnel.php:40-46]. Screen: AhcFunnelScreen (Upload → Reading → Confirm → Payment → Review → Result).

| Method | Path | ::method | Request | Codes | App |
|---|---|---|---|---|---|
| GET | ahc/current | current_get | none | none | ✓ |
| POST | ahc/start | start_post | none | none | ✓ |
| POST | ahc/upload | upload_post | file | 409, 413, 415, 422, 500 | ✓ |
| DELETE | ahc/upload | upload_delete | file_id (query) | 422 | ✓ |
| POST | ahc/advance | advance_post (runs AI extraction) | none | 409 | ✓ |
| PUT | ahc/confirm | confirm_put | contract_type ∈ {saas, lease, employment, nda, consulting, other}, parties, concern | 409, 422 | ✓ |
| POST | ahc/pay/init | pay_init_post (fee link → PayActivity) | none | 409, 502 | ✓ |
| GET | ahc/status | status_get | none | none | ✓ |
| POST | ahc/abandon | abandon_post | none | none | ✓ |

### 2.6 Digital Office (gate auth only)

| Method | Path | ::method | Request | Codes | App | Screen |
|---|---|---|---|---|---|---|
| POST | digital_office/sessions | sessions_post | advocate_staff_id, intake_text | **201**, 403, 409, 422, 500 | ✓ | DoIntakeScreen |
| GET | digital_office/sessions/{id} | sessions_get | none | 400, 404 | ✓ | DoIntakeScreen (polled 4 s) |
| POST | digital_office/sessions/{id}/pay | pay_post | razorpay_order_id, razorpay_payment_id, razorpay_signature | 400, 404, 409, 422 | ✗ | none (app pays the fee invoice) |
| POST | digital_office/sessions/{id}/invoice | invoice_post | none | 404, 409 | ✓ | DoIntakeScreen → PayActivity/Checkout |
| POST | digital_office/sessions/{id}/intake | intake_post | intake_text | 400, 404, 409, 422, 502 | ✓ | DoIntakeScreen |
| POST | digital_office/sessions/{id}/cancel | cancel_post | none | 400, 404, 409 | ✓ | DoIntakeScreen |
| GET | digital_office/waitroom | waitroom_get | none | none | ✓ | DoWaitroomBanner/Sheet (polled 15 s) |
| POST | digital_office/waitroom/join | join_post | advocate_staff_id | **201**, 409, 422 | ✓ | DoLandingScreen |
| POST | digital_office/waitroom/leave | leave_post | waitroom_id | 409, 422 | ✓ | DoWaitroomSheet |

### 2.7 Notifications (gate auth)

| Method | Path | ::method | Codes | App | Screen |
|---|---|---|---|---|---|
| GET | notifications | index_get | none | ✓ | NotificationsScreen |
| GET | notifications/unread_count | unread_count_get | none | ✓ | AppShell badge (60 s per route change, ON_RESUME) |
| PUT | notifications/{id}/read | read_put | 404 | ✓ | NotificationsScreen |
| PUT | notifications/mark_all_read | mark_all_read_put | none | ✓ | NotificationsScreen header |

### 2.8 Legal notices (gate `notices`, permission 10)

| Method | Path | ::method | Request | Response | Codes | App | Screen |
|---|---|---|---|---|---|---|---|
| GET | notices | index_get | case_id | `{s,d:[rows]}`; rows carry `fee` (null or `{state,amount,invoice_id,pay_url,held}`) since 97de5b4 | none | ✓ | NoticesScreen; CaseDetail Notices tab (`case_id`) |
| GET | notices/{id} | view_get | none | detail; status may read "Awaiting Payment" since 97de5b4 | 404 | ✓ | NoticeDetailScreen |
| PUT | notices/{id}/approve | approve_put | none | detail (+fee) | 404, **409** (shared approval refused) | ✓ | NoticeDetailScreen approve footer |
| GET | notices/{id}/document/{fileId} | document_get (stream; only `visible_to_customer=1`) | none | stream | 403 `{status:false, code:'notice_fee_unpaid', message}` while a pay-first notice's fee is unpaid (customers_api@ee69445); 404 | ✓ | NoticeDocViewerScreen |
| GET | notices/types | types_get | none | `{s,d}` | none | ✗ | none |
| POST | notices (fallback → index_post) | index_post | notice fields | `{s,d,code}` | 201, **403** `not_permitted`, 404, 422, 500 | ✗ | none by design (rule F16) |
| POST | legal_notices/extract (default routing) | extract_post | file | `{s,code,m}` | 400, 403 | ✗ | none |

[Corrected 2026-11-07, REQ-001: since cases@b18dfd3 the fee helper loads on app requests, so `fee`, "Awaiting Payment", pay-first `next_step` and the 409 are live; while held, the detail lists `documents: []` (customers_api@ee69445). Verified over HTTP on dev against the `selftest-req001` fixture.] Previously: the `fee`, "Awaiting Payment", pay-first `next_step` and the 409 were **inert in the app API** because `cases_fee_helper.php` was not loaded; the endpoint falls back to a direct `client_approved_at` update [CODE customers_api@97de5b4 controllers/v1/Legal_notices.php:335-352]. The app reads `status_label`, `needs_approval` and `next_step` from the server, so the text would appear without an app change once the helper loads; `fee` is not read at all [CODE lex@6c069eb feature/notices/NoticesRepository.kt:38,45,98].

### 2.9 Documents, Registry

| Method | Path | ::method | Gate | Codes | App | Screen |
|---|---|---|---|---|---|---|
| GET | documents | Documents::index_get (rel_type, q, tag, case_id, rel_id, id) | any of cases/consultations/agreements/notices | 400, 404 | ✓ | VaultScreen wizard |
| GET | documents/download/{id} | download_get (stream) | same | 403, 404 | ✓ | Vault (cacheDir/docs, FileProvider) |
| (route) | documents/upload | **no method exists** (dead route) | none | error | ✗ | none |
| GET | registry | Registry::index_get | agreements (9) | none | ✓ | RegistryScreen (read-only) |
| GET | registry/{id} | detail_get | agreements | 404 | ✓ | RegistryAgreementScreen |

### 2.10 Invoices (gate `invoices`; constructor calls `customers_api_register_gateways()`)

| Method | Path | ::method | Request | Response | Codes | App | Screen |
|---|---|---|---|---|---|---|---|
| GET | invoices | invoices_get (legacy; `?id=` merges document_identity) | id | `{data…}` model envelope | model | ✓ | InvoicesScreen, InvoiceDetailScreen |
| GET | invoices/{id}/pay | pay_get | none | `{s,d:{due,due_formatted,can_change_amount,minimum,gateways[],refusal}}` | none | ✓ | InvoiceDetailScreen inline pay |
| POST | invoices/{id}/pay | pay_post | amount, gateway (razorpay\|payu) | Razorpay order, PayU signed form `{gateway:payu,action,fields,txnid}`, or `{choose,labels}` | 400, 404, 409, 502, 503 | ✓ | CheckoutActivity |
| POST | invoices/{id}/pay/verify | pay_verify_post | razorpay_order_id, razorpay_payment_id, razorpay_signature | `{s,d}` (records in-request) | 404, 422, 502 | ✓ | CheckoutActivity (Razorpay) |
| GET | invoices/{id}/pay/status | pay_status_get | txnid | `{s,d:{state,message,due}}` | 404 | ✓ | CheckoutActivity (PayU poll 3 s × 20) |

### 2.11 Projects and tasks (gate `projects`)

| Method | Path | ::method | Request | Codes | App | Screen |
|---|---|---|---|---|---|---|
| GET | projects | list_get | status | none | ✓ | ProjectsScreen |
| GET | projects/{id} | detail_get | none | 404 | ✓ | ProjectDetailScreen |
| GET | projects/{id}/tasks · milestones · files · activity | tasks_get / milestones_get / files_get / activity_get (Perfex tab settings via `tabAllowed`); files rows carry `view_only:true` since e6eb3e1 | none | 403 tab off | ✓ | ProjectDetail tabs, ProjectMilestonesScreen, ProjectFilesScreen, ProjectActivityScreen |
| POST | projects/{id}/tasks | tasks_post | name, startdate, duedate, priority, milestone, description | 201, 403, 422, 500 | ✓ | ProjectDetail (create task) |
| POST | projects/{id}/files | files_post | file | 201, 403, 422, 500 | ✓ | ProjectFilesScreen |
| GET | projects/files/{fileId} | file_get (stream). Since e6eb3e1: `?download` → 403, `X-Lex-View-Only: 1`, `Cache-Control: private, no-store`, Files-tab gate | download | 403, 404 | ✓ | ProjectFilesScreen.openFile (hands to external app: see known issues) |
| GET/POST | projects/{id}/discussions | discussions_get / _post | subject, description | 201, 403, 422 | ✓ | ProjectDiscussionsScreen ("New Discussion" wizard) |
| GET | projects/discussions/{id} | discussion_get | none | none | ✓ | ProjectDiscussionsScreen |
| POST | projects/discussions/{id}/comments | discussion_comment_post | content | 422, 500 | ✓ | ProjectDiscussionsScreen |
| GET | tasks/{id} | Tasks::tasks_get | none | 403, 404 | ✓ | TaskDetailScreen |
| POST | tasks/{id}/comments | comments_post | content | 403, 404, 422, 500 | ✓ | TaskDetailScreen (`canComment`) |
| GET/POST/PUT | v1/projects (legacy `?id=&group=`) | projects_get/post/put | id, group | 500 | ✗ | none (shadowed) |

Task assignees on the server now include every active staff member (cases c8c0813) through `cases_project_types_helper` [CODE customers_api@97de5b4 controllers/v1/Tasks.php:132].

### 2.12 Desk (Lex Intelligence)

| Method | Path | ::method | Gate | Request | Codes | App | Screen |
|---|---|---|---|---|---|---|---|
| GET | desk | Desk::index_get | auth | none | 403, 404 | ✓ | DeskScreen |
| POST | desk/check | check_post | auth | text | none | ✓ | DeskScreen (reading) |
| POST | desk/enquiry | enquiry_post (multipart) | auth | enquiry_text, entity_type, phone, country, country_other, desk_reading, session_hash, attachments[] | 404, 422, 500 | ✓ | DeskScreen |
| POST | desk/pay | pay_post (fee link → invoice) | auth | session_hash | none | ✓ | DeskScreen → PayActivity.open |
| POST | desk/discard | discard_post | auth | session_hash | none | ✓ | DeskScreen |
| GET/POST | lead/desk[/check\|enquiry\|pay\|discard] | Lead_desk (extends Desk; pay_url = `cases/landing/lead_pay/{hash}`) | lead | as above | as above | ✓ | DeskScreen as a lead |
| POST | guest/desk | Guest_desk::index_post | none; IP 20/h + device limits | turns (JSON), device (≥16 chars) | 422 | ✓ | DeskScreen before sign-in (Lex orb) |

### 2.13 Lead and lead onboarding (gate lead)

| Method | Path | ::method | Request | Codes | App | Screen |
|---|---|---|---|---|---|---|
| GET | lead/me | Lead::me_get (returns a client token once the account opens) | none | none | ✓ | Splash (lead standing, 4 s) |
| GET | lead/onboarding | index_get | none | none | ✓ | LeadOnboardingScreen |
| POST | lead/onboarding/begin | begin_post | entity_type | 403, 422 | ✓ | LeadOnboarding (Type) |
| POST | lead/onboarding/document | document_post (multipart, X-Long-Call) | document_type, desk, document | 409 | ✓ | LeadOnboarding (Files), AccountDesk |
| POST | lead/onboarding/remove | remove_post | document_type | 404 | ✓ | LeadOnboarding |
| GET | lead/onboarding/review | review_get | none | 409 | ✓ | LeadOnboarding (Confirm) |
| POST | lead/onboarding/submit | submit_post | form fields | 409, 422 (`field`, `code`) | ✓ | LeadOnboarding, AccountDesk |
| POST | lead/onboarding/discard · reopen | discard_post / reopen_post | none | 409, 422 | ✓ | LeadOnboarding (Fix And Resubmit) |
| POST | lead/onboarding/phone/{request\|status\|sms\|verify} | phone_post | number, code | 404, 409 | ✓ | LeadOnboarding (Contact) |
| GET | lead/onboarding/turn | turn_get | open | none | ✓ | AccountDesk (conversation) |
| POST | lead/onboarding/answer | answer_post | id, step, code, fields | none | ✓ | AccountDesk |

`Lead_onboarding` installs the dev mail guard and `Email_brand` by hand and loads `lexon/registration_service` [CODE customers_api@97de5b4 controllers/v1/Lead_onboarding.php:43-64]. The conversation needs `lexon_desk_onboarding` on (off at 2026-10-24) [SESSION 3adfe568 C28]. **[Owner decision 2026-11-07, REQ-002]** `lexon_desk_onboarding` is **active on production and under testing**; do not treat it as unused. Dev value is 0.

### 2.14 Absolute URLs the client calls (not customers_api)

| Call | URL | Backend | Auth | Screen |
|---|---|---|---|---|
| funnelMySessions (POST) | `<site>lexon/api/funnel/my_sessions` | lexon Funnel::my_sessions | lead token | LeadEnquiriesScreen |
| funnelThread (POST) | `…/funnel/thread/{hash}` | Funnel::thread | lead token | LeadThreadScreen |
| funnelReply (POST) | `…/funnel/thread/{hash}/reply` (message) | thread_reply | lead token | LeadThreadScreen |
| funnelDetails (GET) | `…/funnel/details/ahc` | lexon routes.php:13 | none | LeadAhcScreen |
| funnelStart (POST) | `…/funnel/start/ahc` | routes.php:14 | lead token | LeadAhcScreen |
| funnelConsent (POST) | `…/funnel/consent/{hash}` (consent=1) | routes.php:15 | session hash | LeadAhcScreen |
| funnelIntake (POST multipart) | `…/funnel/intake/{hash}` (document_type=agreement, file) | routes.php:16 | session hash | LeadAhcScreen |
| trackNotice (POST, skip auth) | `<site>cases/landing/ajax_track` (reference) | cases Landing::ajax_track (CSRF-excluded) | none | Desk TrackSheet |

`<site>` = `API_BASE_URL` before `customers_api/` [CODE lex@6c069eb K/feature/lead/LeadData.kt:49]. Site pages opened in `PayActivity` or the browser: `invoice/{id}/{hash}` (redirected to native checkout), `cases/landing/lead_pay/{hash}`, `mylex/office` [CODE lex@6c069eb K/feature/finance/PayActivity.kt:141-145; digital_office/DoModels.kt:106].

### 2.15 Legacy Perfex endpoints (no app use)

contracts (GET/POST, `contracts`), estimates (GET), proposals (GET/POST), tickets (GET/POST, `support`), knowledge_base (GET, **no auth**), miscellaneous (auth) [CODE customers_api@97de5b4 controllers/v1/{Contracts,Estimates,Proposals,Tickets,Knowledge_base,Miscellaneous}.php].

### 2.16 Daily Board (board module, gate auth + contact permission 11 / board_enabled)

**Last verified against customers_api@0b2f484 / board@82b066d / lex@86dadcb (2026-11-10)** (earlier verification: customers_api@94ab097 / board@98771bd / lex@08b9dc4, 2026-11-10, superseded by the board audit 008.A-W/A-A).

Controller `v1/Board.php` [CODE customers_api@0b2f484 controllers/v1/Board.php:8-38 (route list in the docblock), config/routes.php:69-92]. Gate: `isAuthorized()`, then `board_enabled()` (else 404 `board_off`), then `board_contact_is_counsel()`; a contact who holds the workspace but is paused (D7) gets **403 `paused`** ("Your Counsel workspace is paused while you are engaged with the firm. Your cases and credits are kept."), any other non-counsel 403 `forbidden` [CODE Board.php:46-71; board@82b066d helpers/board_helper.php:405-413]. Every refusal is `{status:false, code, message[, field]}`; `not_found` → 404, `insufficient_credits` → 402 (carries `cost`, `balance`), `ecourts_off`/`ecourts_failed`/`busy` → 503, other model refusals 422 [CODE Board.php:83-101]. `summary`, `today`, `home` and `credits` grant the trial if not yet given (`ensure_trial`). Screens are client-app `feature/board` (see app/features.md §20a-20c). Cross-check: contracts.md rows dated 2026-11-10 REQ-008 (G1.x, A-W).

| Method | Path | ::method | Request | Codes | App | Screen |
|---|---|---|---|---|---|---|
| GET | board/summary | summary_get (+ `statuses`, `counsel: true`, `workspace: "counsel"`) | none | 403 `paused`/`forbidden`, 404 | ✓ | BoardHub (workspace decision; `paused` → paused screen) |
| GET | board/today | today_get (+ `format`): counsel, listed, credits (low, days_label, low_days), short, needs_eye[] | none | 403 | ✓ | Calendar: Needs Your Eye card, low-credit banner, days line |
| GET | board/home | home_get: profile, score, tiles, first_steps | none | none | ✓ | Counsel Home |
| POST | board/chambers | chambers_post | address, city, zip | 422 `address` | ✓ | ChambersWizard (Home) |
| GET | board/day | day_get (+ `format`); `counts.listed` = items shown | date | none | ✓ | Calendar day; saved-copy fallback |
| GET | board/days | days_get `{from, n, days[{date, listed}]}`; superseded history dates do not count | from, n (1-62, default 7) | 422 `date` (field `from`) | ✓ | Calendar week row |
| POST | board/cases/{id}/date/accept | date_post (eCourts Is Right) | none | 404, 422 `not_flagged` | ✓ | Needs Your Eye, import mismatches (`not_flagged` = already settled) |
| POST | board/cases/{id}/date/keep | date_post (Keep My Date) | none | 404, 422 `not_flagged` | ✓ | as above |
| POST | board/cases/{id}/date/correct | date_post → `date_correct`: **hand (no-CNR) cases only** | next_date (d-m-Y or Y-m-d, 4-digit year) | 422 `next_date` (a CNR case: "Use eCourts Is Right or Keep My Date."; a two-digit or unreadable year) | ✓ (since lex 3ecc52f) | Set Next Date wizard (hand cases) [CODE board@82b066d models/Board_model.php:253] |
| POST | board/import | import_post: preview, `import_id`, plus `cost{per_case, cases, total, balance, covers, line}` | file (multipart) or content, filename | 422 `no_file` | ✓ | Import (cost line before Confirm) |
| POST | board/import/confirm | import_post('confirm'): applies (an existing case keeps its dates), then checks the imported cases on eCourts (1 credit a case) under the cron's lock; returns `check{checked, unchecked, stopped}`, `mismatches[]`. **A repeated confirm** of the same import_id within 2 h returns the first result with `repeated: true` and applies nothing | import_id | 402, 422 `import_expired`; `check.stopped` may be `busy_check` | ✓ (long-call) | Import → Which Date Is Right? [CODE Board.php:211-235; board models/Board_model.php:1211-1220] |
| GET | board/cases | cases_get list | q, limit (100), offset, order | none | ✓ | Your Cases, search |
| GET | board/cases/{id} | cases_get one (`case_page`); `history[]` rows carry `superseded` (bool) | none | 404 | ✓ | Case page (Date History "· Replaced") [CODE board models/Board_model.php:674] |
| POST | board/cases | cases_post: **HAND add** (the web's method) | case_no, petitioner, respondent, court, next_date required; room, case_type, stage optional | 422 per field, 422 `duplicate`; **422 `use_add_by_cnr`** (field `cnr`) when a CNR is sent | ✓ (since lex 3ecc52f) | Add By Hand wizard [CODE Board.php:274-291] |
| POST/PUT | board/cases/{id} | cases_post / cases_put → `case_save(..., true)`; `junior_id` is no longer a key (ignored) | case_keys (cnr, case_no, case_type, petitioner, respondent, court, court_code, room, stage, next_date, note, morning_check) | 404; 422 `next_date` when the date of a CNR case is changed (unchanged echo accepted) | ✓ (POST, `morning_check` only) | Daily Check switch (Edit Case removed at lex 3ecc52f) |
| DELETE | board/cases/{id} | cases_delete | none | 404 | ✓ | Remove From Your Diary |
| POST | board/cases/{id}/cnr | cnr_post: give a hand-added case its CNR (free) | cnr | 422 | ✓ | Add The CNR wizard |
| POST | board/cases/cnr/lookup | premium_post('cnr_lookup'): Add By CNR step one, **4 credits**, the case previewed, nothing in the diary yet | cnr | 402, 422 `duplicate`/`cap_reached`/`premium_paused`, 503 | ✓ (since lex 3ecc52f, long-call) | Add By CNR: Find This Case (4 Credits) [CODE Board.php:403-417; board libraries/Board_ecourts.php:298] |
| POST | board/cases/cnr/confirm | premium_post('cnr_confirm'): step two, free, the case goes into the diary. "Not This Case" needs no call (the lookup lapses in 2 h, credits stay spent) | lookup_id | 422 `lookup_expired` | ✓ | Add By CNR: Add To My Diary [CODE board libraries/Board_ecourts.php:334-338] |
| POST | board/cases/cnr | premium_post('add'): one-step Add By CNR, 4 credits | cnr | 402, 422, 503 | ✗ (since lex 3ecc52f; kept for older builds) | none |
| POST | board/cases/{id}/read | premium_post('read'): Update From Court, 4 credits; clears the date flag and sets `ec_date` to the date it wrote | none | 402, 422 `no_cnr`, 503 | ✓ (CNR cases only) | Case page: Update From Court Now |
| GET | board/sheet | sheet_get: the day's cause list PDF in the counsel's format (portrait; A4 or Legal) | date | 410 `retired` when `junior` is sent | ✓ | Cause List (Print, Share) |
| GET | board/export | export_get: myCases.txt | none | none | ✓ | Export icon |
| GET | board/credits | credits_get: balance, expiring (30 days), packs[] (+ base, gst_amount, gst_percent, bonus_pct, bonus_credits, lasts), costs{add, check}, history (raw, limit 50), purchases, credit_months, spent{}, `next_lapse` {credits, expires_at, kind}\|null, `history_rows` (grouped, first 60), checked{} | limit | none | ✓ | Credits: pack lines and bonus, Expiring Next, grouped history [CODE Board.php:346-371] |
| GET | board/credits/history | credits_get('history'): raw ledger page `{total, items[], limit, offset}` (limit 1-200, default 50); with **`grouped=1`** the web's grouped rows `{total, items[{kind, day, when, delta, balance_after, what, cases}], limit, offset, grouped: true}` | limit, offset, grouped | none | ✓ (grouped) | Credits history paging [CODE Board.php:326-346] |
| POST | board/credits/checkout | credits_post: pay first, no invoice until paid; `{order, pay_url, purchase_id, amount, currency, pack, expires_at, reused}`; a reused order never already carries a payment | pack | 422 | ✓ | Credits cart → Custom Tab |
| GET | board/credits/order | credits_get('order'): pending, paid, failed, expired, cancelled or flagged. `failed` is not final: the order stays payable and a later verified capture is honoured | order | 404 `not_found` | ✓ | Credits (each resume, Check Again; `failed` = "not completed, if you paid it will be matched") |
| POST | board/credits/buy | credits_post: with `flow=pay_first` it is checkout; **without flow: 410 `retired`**, no invoice (was the old invoice-first way) | pack[, flow] | 410, 422 | ✗ | none (NOT backward compatible for builds that used the old way; the app has used checkout throughout) [CODE Board.php:380-394] |
| GET/POST/PUT/DELETE | board/juniors[/{id}] · board/courts · board/entries | **retired**: always 410 `retired` (owner, 8 Nov 2026; tables kept) | n/a | 410 | ✗ | removed from the app at lex 08b9dc4 |

Pay-first page: `pay_url` is the web's `board/pay/{token}`; it opens the existing order at its own amount (no new order per visit since board edeae14) and returns to `/board/pay/{token}/success` or `/failed`, then the app asks `credits/order`.

Server behaviour the app relies on (board@edeae14..82b066d, no shape change): the morning check charges only live counsel and skips decided or disposed cases; a case eCourts could not answer is tried once a morning; the daily debit is once per case per day; refunds keep the expiry of the credits they return; superseded future dates stop counting as listed; dates with a two-digit year are refused. On dev, every paid eCourts path uses the stub for clients in `board_ecourts_stub_clients` (dev mail guard required; never production) [CODE board@82b066d libraries/Board_ecourts.php:82-90].

### 2.17 Workspaces and counsel registration (REQ-010)

**Last verified against customers_api@94ab097 / lex@08b9dc4 (2026-11-10)**

| Method | Path | ::method | Gate | Request | Codes | App | Screen |
|---|---|---|---|---|---|---|---|
| GET | workspace | Workspace::index_get `{items[{kind: counsel\|portal, label, status, enter, note, can_add, can_remove}], choose, counsel_only}` + eligibility, draft | auth | none | none | ✓ | Chooser, Profile "Workspaces" card |
| POST | workspace/begin · document · discard | begin_post / document_post / discard_post | auth | document (file), document_type `bar_council_id` | 409, 422 | ✓ | Add Counsel Workspace wizard |
| GET/POST | workspace/review | review_get / review_post | auth | enrollment_number, state_bar_council, state | 422 | ✓ | Add Counsel (Details) |
| POST | workspace/submit | submit_post: opens at once, trial credits added | auth | terms=1 | 409, 422 | ✓ | Add Counsel (Confirm) |
| POST | workspace/remove | remove_post: ledger and invoices kept, certificate deleted, number reserved | auth | none | 409, 422 | ✓ | Profile, Remove |
| GET | lead/counsel | Lead_counsel::index_get: outcome new\|client\|counsel\|pending\|rejected\|staff | lead | none | 404 `board_off` | ✓ | Counsel door triage |
| POST | lead/counsel/begin · document · remove · discard | *_post | lead | document (file), document_type `bar_council_id`\|`pan` | 409, 422 | ✓ | Counsel registration wizard |
| GET/POST | lead/counsel/review | review_get / review_post | lead | full_name, enrollment_number, state_bar_council, pan_number, state | 422 | ✓ | Counsel wizard (Review) |
| POST | lead/counsel/phone/{request\|status\|sms\|verify} | phone_post | lead | number, code | 404, 409 | ✓ | Counsel wizard (Mobile) |
| POST | lead/counsel/submit | submit_post: workspace opens at once; the answer carries the client token to switch to | lead | terms=1 | 409, 422 | ✓ | Counsel wizard (Confirm) |

[CODE customers_api@94ab097 config/routes.php:46-51; controllers/v1/Workspace.php:8-24,94-179; controllers/v1/Lead_counsel.php:8-24,98-223]. Exact codes per action are as the controllers return them through the Lexon registration service; the table lists the codes the docblocks and refusal helpers name [ASSUMPTION: not every branch traced].

## 3. Cross-check summary

| Measure | Value |
|---|---|
| ApiService methods | 133 (125 relative, 8 absolute) |
| App calls with no matching route | 0 |
| Declared, never called | 8: `dashboardStats, dashboardProfile, nextHearing, recentActivity, activeServices, legalHealth, dashboardActiveMatters, registerDevice`; plus `me()` with no call site found [ASSUMPTION: name grep] |
| Backend verb methods / unused by the app | 139 / about 22 |
| Push | No API route: Beams interest `contact-{id}`; server publishes from `helpers/push_helper.php:27-74` with dev allow-list `lex_dev_push_allow` |

Mismatches visible in code: (1) notice `fee`, "Awaiting Payment", `view_only`, `X-Lex-View-Only`, `code: view_only` are not consumed by the app; (2) `approve` can now return 409, not traced into the ViewModel [ASSUMPTION]; (3) `createConsultation` sends no `files[]` (benign: attachments are uploaded afterwards). Field names for auth/OTP, accounts, profile phone, AHC confirm, DO, desk enquiry, lead onboarding document, project writes and invoice pay all match [CODE both sides].

## 4. Changes since the app HEAD (git log 5f0a1bd..97de5b4)

| Commit | Date | Change | Client effect |
|---|---|---|---|
| e6eb3e1 | 2026-11-03 | Project files view-only, `?download` 403, headers, Files-tab gate | App never sends `download`; ignores the signals |
| e3027f0 | 2026-11-04 | Token key from `APP_ENC_KEY` | One forced sign-in per client; lead tokens unaffected (stored-token equality) |
| 3cbc2a5 | 2026-11-05 | View-only write refusal | Inert headlessly; no app handling [Corrected 2026-11-07, REQ-001: active on app requests since cases@b18dfd3] |
| 33f7a37 | 2026-11-05 | Access-log actor global | None (logger not registered headlessly) |
| eab7c0a | 2026-11-05 | Action items by notices/consultations permission | Works over HTTP |
| e68688b | 2026-11-05 | Approval through `cases_notice_approve()`; 409 | Inert headlessly [Corrected 2026-11-07, REQ-001: active on app requests since cases@b18dfd3] |
| 97de5b4 | 2026-11-07 | Notice `fee` object, "Awaiting Payment" | Inert headlessly; not read [Corrected 2026-11-07, REQ-001: active on app requests since cases@b18dfd3] |
| cases b18dfd3 | 2026-11-07 | Plan and fee helpers + paid-invoice listeners load on app requests (REQ-001) | View-only writes now 403 `view_only`; notice approve may 409; `fee` populated. App does not yet handle 403/409 (shows "session expired" on approve, `NoticesViewModel.kt:194`): app work U1-U4 is a later REQ |
| customers_api ee69445 | 2026-11-07 | Held pay-first notice: document 403 `notice_fee_unpaid`, `documents: []` | App shows no files for a held notice; a stale file link answers 403 |
| cases 00bd1f0 | 2026-11-07 | View-only contacts may pay: invoices pay, pay/verify, pay/status exempt (owner Q9) | None needed |
| customers_api 001ed0f · 8240796 · 3599b20 | 2026-11-09 | REQ-010 010.9: `workspace*` and `lead/counsel*` endpoints; begin 409 carries `next=add_counsel` | Built in the app at lex 954a106, f10203e |
| customers_api 94ab097 · board 98771bd · cases 5c6e6c3 | 2026-11-10 | REQ-008 G1: juniors/courts/entries and `sheet?junior` → 410 `retired`; 422 `next_date` on a changed CNR-case date; `credits/history` paged; `day.counts.listed` = items shown | App follows at lex 08b9dc4 (paged history, Next Date locked, retired calls removed) |

## Open questions

- Nested `data` shapes are documented only where ApiService KDoc states them; full shapes are not in this table.
- Does anything (web or staff) rely on the dead `documents/upload` route?
- Has e3027f0 been deployed to production?
