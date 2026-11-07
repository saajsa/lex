# Client API Contract

> Part of the Legum Lex Android knowledge base. Verified against: client app 6c069eb, staff app e52a8ca, customers_api 97de5b4, api 9df1506, cases cb0a820 (2026-11-07). The full `customers_api` contract the client app uses, with the app screen behind each route.
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
| `isAuthorized()` order | (1) module inactive → 404; (2) JWT invalid/expired → 401; (3) no `contact_id` (lead token) → 401; (4) contact missing, empty key or header ≠ stored token → 401 `login_to_continue`; (5) contact inactive → 401; (6) register contact permissions 7-10; (7) client inactive → 401; (8) `cases_view_only_refusal()` → 403 `view_only` (**inert headlessly**, see [wiring-and-traps.md](wiring-and-traps.md)); (9) sets `$GLOBALS['cases_api_actor']` | [CODE customers_api@97de5b4 helpers/customers_api_helper.php:133-251] |
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
| GET | notices/{id}/document/{fileId} | document_get (stream; only `visible_to_customer=1`) | none | stream | 404 | ✓ | NoticeDocViewerScreen |
| GET | notices/types | types_get | none | `{s,d}` | none | ✗ | none |
| POST | notices (fallback → index_post) | index_post | notice fields | `{s,d,code}` | 201, **403** `not_permitted`, 404, 422, 500 | ✗ | none by design (rule F16) |
| POST | legal_notices/extract (default routing) | extract_post | file | `{s,code,m}` | 400, 403 | ✗ | none |

The `fee`, "Awaiting Payment", pay-first `next_step` and the 409 are **inert in the app API today** because `cases_fee_helper.php` is not loaded; the endpoint falls back to a direct `client_approved_at` update [CODE customers_api@97de5b4 controllers/v1/Legal_notices.php:335-352]. The app reads `status_label`, `needs_approval` and `next_step` from the server, so the text would appear without an app change once the helper loads; `fee` is not read at all [CODE lex@6c069eb feature/notices/NoticesRepository.kt:38,45,98].

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
| 3cbc2a5 | 2026-11-05 | View-only write refusal | Inert headlessly; no app handling |
| 33f7a37 | 2026-11-05 | Access-log actor global | None (logger not registered headlessly) |
| eab7c0a | 2026-11-05 | Action items by notices/consultations permission | Works over HTTP |
| e68688b | 2026-11-05 | Approval through `cases_notice_approve()`; 409 | Inert headlessly |
| 97de5b4 | 2026-11-07 | Notice `fee` object, "Awaiting Payment" | Inert headlessly; not read |

## Open questions

- Nested `data` shapes are documented only where ApiService KDoc states them; full shapes are not in this table.
- Does anything (web or staff) rely on the dead `documents/upload` route?
- Has e3027f0 been deployed to production?
