# Staff API Contract

> Part of the Legum Lex Android knowledge base. Verified against: client app 6c069eb, staff app e52a8ca, customers_api 97de5b4, api 9df1506, cases cb0a820 (2026-11-07). The full `api` module contract the staff app uses: authentication, token scopes, and every route with the screen behind it.
>
> This file is identical in legum-lex-android/docs/kb and legum-lex-staff/docs/kb.

**Last verified against api@9df1506 (2026-11-07)**

Paths are relative to `…/api/`. `J/` = staff `app/src/main/java/com/legumlex/staff/`. Source: [CODE api@9df1506 config/routes.php:5-220, controllers/*.php, models/Api_model.php] cross-checked with [CODE lexco@e52a8ca J/data/api/ApiService.kt].

## 1. Authentication

**Last verified against api@9df1506 (2026-11-07)**

| Layer | Behaviour | Ref |
|---|---|---|
| Base URL | Release `https://www.legumlex.com/api/`; debug from `local.properties` (now `http://localhost:8088/api/` over USB) | [CODE lexco@e52a8ca app/build.gradle.kts:93] |
| Sign-in controller | `Auth extends CI_Controller` (not REST_Controller): no token or scope check. `login` (POST email, password; throttle; 401 bad, 403 inactive, 429, 405 non-POST). With 2FA on, `two_factor_start`, then POST `auth/two_factor` (ref, code; 422 `error: expired\|wrong`). `google` (id_token). `otp/{step}` | [CODE api@9df1506 controllers/Auth.php:27-75,229-260,352-392] |
| Token issue | `issue_token()` inserts `tbluser_api {user: staffid, name: 'Staff App', expiration_date: +90 days, permission_enable: 0, request_limit 100000/3600 s, burst 1000}` and prunes expired rows. `logout` expires the row | [CODE api@9df1506 controllers/Auth.php:683-735] |
| Header | `Authtoken` (`token_header='authtoken'`, case-insensitive). JWT `token_expire_time` is about 10 years, so `tbluser_api.expiration_date` is the real expiry | [CODE api config/jwt.php:24,46 (git-ignored; working copy read 2026-11-07)] |
| REST_Controller order | `validateToken()` (JWT) → `Api_model::check_token()` → `cases_headless_wiring()` → `api_books_helper` → `check_rate_limits()` | [CODE api@9df1506 controllers/REST_Controller.php:826-883] |
| `check_token()` | Row exists; not expired; for a Staff App token: not passed in a query string, staff active, `last_password_change` not newer than `quota_created_at` | [CODE api@9df1506 models/Api_model.php:703-740] |
| **Bad token → 404** | A missing token answers HTTP 404 `{status:false, message:'Token is not found'}` (line 797); an invalid, expired or revoked token answers HTTP 404 with the check's message, e.g. `Token expired or revoked` (line 845). **Not 401.** Rate limit → 429 | [CODE api@9df1506 controllers/REST_Controller.php:797,845] (verified 2026-11-07) |
| Staff identity | `api_token_staff()` reads `Authtoken`, finds the row with numeric `user`, checks expiry and active staff, returns the tblstaff row; missing → controllers answer 401 `Unauthorized`. It does **not** check `last_password_change` or a query-string token, unlike `check_token()` | [CODE api@9df1506 helpers/api_helper.php:260-286] |
| Consequence | `auth/me` (Auth controller, uses only `api_token_staff()`) still answers 200 after a password change, while every REST call answers 404 | [CODE api@9df1506 controllers/Auth.php; REST_Controller.php:845] |
| Function permissions | `api_staff_can($staff,$cap,$fn)` → `cases_can()` / `cases_can_any()`; `api_staff_client_ids($staff,$fn)` scopes rows to the member's clients in `tblcases_counselors` (null = admin, all); `api_consultation_scope_sql()`; `api_seed_staff_context()` sets `staff_user_id` for this request only | [CODE api@9df1506 helpers/api_helper.php:432-457,488-540] |
| CSRF | `api\/.+` in core `csrf_exclude_uris` | [CODE application/config/config.php:490] |
| Envelope | `{status: bool, data?, message?}` plus endpoint keys (caseboard, home, notifications `unread_count`) | [CODE api@9df1506 controllers/*.php] |
| Routing | Explicit routes `config/routes.php:5-203`; generics `api/X` → `X/data`, `api/X/{num}` → `X/data/{num}`; an unrouted `api/X/Y` falls to HMVC: controller X, method `Y_{verb}` (used by `courts/room`, `home`, `lexmail/*`, `advocate/*`) | [CODE api@9df1506 config/routes.php:206-220] |

App side: the staff `AuthInterceptor` adds `Authtoken` unless `X-Skip-Auth: 1` [CODE lexco@e52a8ca J/data/api/AuthInterceptor.kt:14-20]. There is **no automatic sign-out on any status**; `ApiParsing` maps 401 to "Your session has expired. Sign in again." and 404 to "Not found." [CODE lexco@e52a8ca J/data/api/ApiParsing.kt:59-72]. So after a 90-day expiry, a password change or deactivation, most screens say "Not found." until the user signs out by hand (or the next cold start, where Splash's `auth/me` check may still pass after a password change).

## 2. Token scopes (STAFF_APP_FEATURES)

In `_remap`, `feature = strtolower(controller class)` and `capability = method name minus data_`. `check_token_permission()` always refuses `thirdparty`. A **Staff App** token is allowed exactly these features, capability ignored [CODE api@9df1506 controllers/REST_Controller.php:1021-1083; models/Api_model.php:766-805]:

`advocate, caseboard, cases, client_onboard, consultations, courts, dashboard, digital_office, home, documents, hearings, lexcent, lexmail, myprojects, mytasks, notifications, project_stages`

An integration key needs `tbluser_api_permissions(api_id, feature, capability)` rows; with `permission_enable=0` it gets nothing (fail closed). Refusal → **403** `text_rest_api_key_permissions`.

Outside the scope (403 for a Staff App token): calendar, check, common, contacts, contracts, credit_notes, custom_fields, customers, estimates, expenses, invoices, items, leads, milestones, payments, projects, proposals, staffs, subscriptions, tasks, thirdparty, tickets, timesheets, zapier, connector_polling, key. Admin-UI controllers (`Api`, `Playground`, `Postman`, `Reporting`) use the web session [CODE api@9df1506 models/Api_model.php:766-769]. Note: the handoff's `common` (payment modes by `?book=`) and `contacts` (phone-on-record 409) changes sit in controllers a Staff App token cannot reach [SESSION 3adfe568 C38].

Adding a controller for the app means adding its lowercase class name to `STAFF_APP_FEATURES`.

## 3. Route table

**Last verified against api@9df1506 (2026-11-07)**

Every row except `auth/*` needs a valid Staff App token and a feature in STAFF_APP_FEATURES. **App**: ✓ used, ◇ declared never called, ✗ not in the app. **Screen** = staff screen or flow.

### 3.1 Auth (no REST gate)

| Method | Path | ::method | Request | Response | Codes | App | Screen |
|---|---|---|---|---|---|---|---|
| POST | auth/login | login | email, password | `{status, token, staff}` or, with 2FA on, `{status:true, two_factor:"email"\|"app", ref, to}` [CODE api@9df1506 controllers/Auth.php:646,667] | 401, 403, 405, 422, 429, 500 | ✓ | LoginScreen (skip-auth) |
| POST | auth/two_factor | two_factor | ref, code | `{status, token, staff}` | 405, 422 (`error`), 429, 500 | ✓ | LoginScreen TwoFactorStep |
| POST | auth/google | google | id_token | `{status, token, staff}` (or 2FA challenge) | none listed | ✓ | LoginScreen Google |
| GET | auth/google_config | google_config | none | client id | none | ✓ | LoginScreen |
| POST | auth/otp/{request\|status\|sms\|verify} | otp | number / ref / code | step body; verify also runs the 2FA step when on [CODE api@9df1506 controllers/Auth.php:191-192] | none listed | ✗ | none (no staff mobile-code sign-in in the app) |
| GET | auth/me | me | none | `{status, staff}` | 401 | ✓ | SplashScreen session check (3 s) |
| GET | auth/profile | profile | none | profile + counsel card + `staff.nyayco` | 401 | ✓ | ProfileScreen |
| POST | auth/logout | logout | none | `{status:true}` | none | ✓ | Profile → Sign Out |

### 3.2 Overview

| Method | Path | ::method | Gate | Response keys | Codes | App | Screen |
|---|---|---|---|---|---|---|---|
| GET | home | Home::data_get | staff | status, me, workspaces, conversion | 401 | ✓ | HomeScreen |
| GET | caseboard | Caseboard::data_get (months) | `cases_can('litigation','view')` | status, today, day, docket, figures, portfolio, rhythm, orders, waiting, windows, months, empty, generated_at | 401, 403 | ✓ | CaseboardScreen (from Home Litigation card) |
| GET | dashboard | Dashboard::data_get | staff | `{s,d}` | 401 | ✓ (only from unreachable DashboardScreen) | none reachable |

### 3.3 Litigation

| Method | Path | ::method | Gate | Request | Codes | App | Screen |
|---|---|---|---|---|---|---|---|
| GET | cases[/{id}] | Cases::data_get | staff; client scope (litigation) | status | 401, 404 | ✓ | CasesScreen, CaseDetailScreen |
| POST | cases/{id} | data_post (document upload) | create litigation | file, tag, visible_to_customer | 401, 403, 404, 422 | ✓ | DocumentSheets, ScanDocumentScreen |
| POST | cases/{id}/dispose · reopen · transfer | dispose_post / reopen_post / transfer_post | `editable_case()` | disposal_type, disposal_date, disposal_reason, appeal_deadline, appeal_note, client_note / reopen_reason / to_court_room_id, effective_date, order_reference, reason, notes, transfer_out | 422 | ✓ | CaseActionWizards |
| POST | cases/{id}/ecourts | ecourts_post | `editable_case()` | none | 422 `{status,message,found,verified}` | ✓ | CaseDetail eCourts check |
| POST | cases/{id}/reocr | reocr_post | edit litigation | none | 401, 403, 404, 422 | ✓ | CaseDetail |
| GET | hearings[/{id}] | Hearings::data_get | staff | range | 401, 404 | ✓ | CaseDetail Hearings tab, Calendar |
| PUT | hearings/{id} | data_put | edit litigation | status, date, time, hearing_purpose, description, adjournment_reason, next_date, next_time, upcoming_purpose, client_attendance, client_instructions, client_note | 401, 403, 404, 409, 422, 500 | ✓ | UpdateHearingScreen (emails the client) |
| POST | hearings | data_post (add) | create litigation | hearing fields | 401, 403, 422 | ✓ | AddHearingScreen |
| DELETE | hearings/{id} | data_delete | delete litigation | none | 401, 403, 404 | ✓ | CaseDetail |
| POST | hearings/{id} | data_post (document upload) | create litigation | file, tag, visible_to_customer | 401 | ✓ | ScanDocumentScreen |
| POST | hearings/{id}/orders | orders_post | create litigation | orders[] / file | 401, 403, 404, 422 → `{status,uploaded,failed,results}` | ✓ | DocumentSheets multi-order upload |
| POST | hearings/{id}/summarise | summarise_post | edit litigation | none | 401, 403, 404 | ✓ | CaseDetailParts "Generate summary" (on request only) |
| GET | hearings/causelist | causelist_get | staff | date (app's `scope` ignored, always firm) | 401 | ✓ | CauseListScreen |
| GET | hearings/density | density_get | staff | from, to (`scope` ignored) | 401 | ✓ | CalendarScreen |
| GET | courts | Courts::data_get | litigation view | none | 403 | ✓ | CourtsScreen |
| POST | courts · courts/room | data_post / room_post | admin | name, hierarchy, location, status / court_id, court_no, judge_name, type, bench_type, status, from_date, to_date, judge_gender, is_chief_justice | 403, 422 | ✓ | AddCourtWizard (admin) |
| GET | documents/download/{id} | Documents::download_get | staff | none | 401, 404 | ✓ | PdfViewerScreen |
| PUT/DELETE | documents/{id} | data_put / data_delete | edit/delete (any function) | tag, visible_to_customer | 401, 403, 404, 422 | ✓ | DocumentSheets |
| GET | documents[/{id}] | data_get | none | none | 404 | ✗ | none |

### 3.4 Consultations

`guard($id,$cap)` = staff + consultation scope + function permission; `require_admin()` = admin only.

| Method | Path | ::method | Request | Codes | App | Screen |
|---|---|---|---|---|---|---|
| GET | consultations[/{id}] | data_get (opening a client-raised one acknowledges it server-side) | phase, status, unack, assigned_to_me, q, view=list | 401, 404 | ✓ | ConsultationsBoardScreen, ConsultationDetailScreen |
| POST | consultations | data_post (create, JSON) | body map | 401, 404, 409, 422 | ✓ | NewConsultationScreen wizard |
| POST | consultations/{id} | data_post (reply) | message, visible_to_customer | as above | ✓ | ConsultationDetail Messages |
| PUT | consultations/{id} | data_put | acknowledge, status, priority, phase, request_status, date, time, client_note | 401, 404, 409, 422 | ✓ | ConsultationDetail triage/schedule |
| DELETE | consultations/{id} | data_delete | none | 401, 404, 422 | ✓ | ConsultationDetail |
| GET | consultations/form | form_get | client_id | 401, 403 | ✓ | NewConsultationScreen |
| GET | consultations/counters | counters_get | none | 401 | ✓ | Bottom-bar badge (ConsultationCountersHolder) |
| POST | consultations/bulk-acknowledge | bulk_acknowledge_post | ids (JSON) | 401, 404, 422 | ✓ | ConsultationsBoard |
| PUT/DELETE | consultations/replies/{id} | reply_put / reply_delete | visible_to_customer | 401, 404, 422 | ✓ | ConsultationDetail |
| GET/POST | consultations/{id}/attachments | attachments_get / _post | file, tag, visible_to_customer | 401, 404, 422 | ✓ | ConsultationDetail |
| PUT/DELETE | consultations/attachments/{fid} | attachment_put / attachment_delete | visible_to_customer | 401, 404, 409, 422 | ✓ | ConsultationDetail |
| GET | consultations/attachments/{fid}/download | attachment_download_get | none | 401, 404, 410 | ◇ | none |
| GET/POST | consultations/{id}/doc-requests | docrequests_get / _post | document_name, description, is_required | 401, 404, 422 | ✓ | ConsultationDetail document requests |
| PUT/DELETE | consultations/doc-requests/{rid} | docrequest_put / _delete | same | 401, 404, 422 | ✓ | same |
| POST | consultations/doc-requests/{rid}/waive | docrequest_waive_post | reason | 401, 404 | ✓ | same |
| POST | consultations/{id}/doc-requests/send | docrequests_send_post | note | 401, 404, 422 | ✓ | same |
| GET | consultations/doc-requests/{rid}/file | docrequest_file_get | none | 401, 404, 410 | ◇ | none |
| GET | consultations/funnel/pending | funnel_pending_get | admin | none | ◇ | none |
| GET | consultations/{id}/funnel | funnel_get | admin | 404 | ✓ | ConsultationDetail AHC review (admin) |
| POST | consultations/{id}/funnel/retry | funnel_retry_post | admin | 404, 422, 500, 502 | ✓ | same |
| POST | consultations/{id}/funnel/approve | funnel_approve_post | guard edit | advocate_notes; 401, 404, 422 | ✓ | same |
| GET | consultations/{id}/funnel/document | funnel_document_get | admin | 404 | ◇ | none |

### 3.5 Digital Office (gate staff 401; advocate/admin checks inside 403)

| Method | Path | Request | Codes | App | Screen |
|---|---|---|---|---|---|
| GET | digital_office[/{id}] (includes `pusher{key,cluster,channel,event,advocate_channel,advocate_event}`) | none | 401 | ✓ | MyOfficeScreen, SessionDetailScreen |
| POST | digital_office | status (presence online/offline) | 422 | ✓ | MyOfficeScreen toggle |
| POST | digital_office/complete/{id} | none | none listed | ✓ | SessionDetailScreen |
| POST | digital_office/cancel/{id} | reason | none listed | ✓ | SessionDetailScreen |
| POST | digital_office/note/{id} | note | none listed | ✓ | SessionDetailScreen |
| GET | digital_office/waitroom | advocate_id | 403 | ✓ | MyOfficeScreen |
| GET | digital_office/history | filter | none listed | ✓ | SessionHistoryScreen |
| GET | digital_office/chamber | filter | 403 non-admin | ✓ | AdminChamberScreen (entry shown to admins) |
| GET | digital_office/google/auth_url | base | 503 | ✓ | GoogleCalendarScreen (Custom Tab, returns via `lexcostaff://google-connected`) |
| POST | digital_office/google/disconnect | none | none listed | ✓ | GoogleCalendarScreen |
| POST | digital_office/pusher_auth | socket_id, channel_name | 401, 403 | ✓ (Pusher `HttpChannelAuthorizer`, not in ApiService) | DigitalOfficePresenceClient private channel |
| POST | digital_office/refund/{id} | none | **410 Gone** | ✗ | none (refund retired) |
| POST | digital_office/simulate | dev/admin | none listed | ✗ | none |

### 3.6 Projects, tasks, notifications

| Method | Path | ::method | Gate | Request | Codes | App | Screen |
|---|---|---|---|---|---|---|---|
| GET | mytasks[/{id}] | Mytasks::data_get | staff | none | 401, 403, 404 | ✓ | TasksScreen (My Work), TaskDetailScreen |
| PUT | mytasks/{id} | data_put | staff | status (5 complete, 4 reopen) | 401, 403, 404, 422 | ✓ | TaskDetailScreen |
| GET | myprojects[/{id}] | Myprojects::data_get | staff | none | 401 | ✓ | ProjectsPane |
| GET | project_stages/types · clients · eligible_staff | types_get / clients_get (admin or view customers) / eligible_staff_get (now all active staff) | staff | client_id | 401 | ✓ | NewProjectScreen, StageWizards |
| POST | project_stages | index_post | `staff_can create projects` | name, client_id, type_id, start_date, deadline, members, assign, tell | **201**, 403, 422, 500 | ✓ | NewProjectScreen |
| GET | project_stages/{id} | data_get | staff | none | 404 | ✓ | StagesSection |
| POST | project_stages/{id}/{move\|back\|waiting\|official_date\|assign} | move_post … | `can_edit($id)`; assign needs create/edit tasks | target, expected, official_date, note, reference, tell, assign / expected / who, authority_name / date, milestone_id / task_id, staff_id | 403, 404, 422; app reads `sends_at` | ✓ | StageWizards (Move To, Move Back, Waiting On, Official Date), assignee chip |
| GET | notifications | Notifications::data_get | staff | none → `{status,data,unread_count}` | 401 | ✓ | NotificationsScreen, Home bell |
| PUT | notifications/{id} | data_put (always marks read; app's `isread` ignored) | staff | none | 401 | ✓ | NotificationsScreen; push tap (`markPushRead`) |
| DELETE | notifications/{id} | data_delete | staff | none | 401, 404, 422 | ✓ | NotificationsScreen |
| POST | notifications | data_post (mark all read) → `{status,updated}` | staff | none | 401 | ✓ | NotificationsScreen |

### 3.7 Client onboarding (constructor: `api_token_staff()` + `staff_can('create','customers')`, 401/403)

| Method | Path | Request | Codes | App | Screen |
|---|---|---|---|---|---|
| GET | client_onboard/types | none | none | ✓ | OnboardingScreen (Type) |
| GET | client_onboard | none (draft standing) | none | ✓ | OnboardingScreen; Home "Continue Onboarding" |
| POST | client_onboard/begin | entity_type | 422 | ✓ | OnboardingScreen |
| POST | client_onboard/document | document_type + file (X-Long-Call) | 409 | ✓ | OnboardingScreen (Files) |
| POST | client_onboard/remove | document_type | 404 | ✓ | OnboardingScreen |
| GET | client_onboard/review | none | 409 | ✓ | OnboardingScreen (Review) |
| GET | client_onboard/lead | email | none | ✓ | OnboardingScreen (lead lookup) |
| POST | client_onboard/phone/{request\|status\|sms\|verify\|attest} | number, code, confirm | none listed | ✓ | OnboardingScreen (Client step, 60 s resend) |
| POST | client_onboard/submit | `staff_can edit customers` | 403, 409, 422 | ✓ | OnboardingScreen |
| POST | client_onboard/discard | none | none | ✓ | OnboardingScreen |
| POST | client_onboard/reopen | registration_id; edit customers | none listed | ✗ | none (route exists, no app call) |

### 3.8 Lexcent (constructor 401; gates `require_staff`, `can_submit_expense`, `can_request_pay`, `require_approver`, `require_admin`; tithe finalise needs `staff_can('owner','finance_console')`)

All 34 routes in `config/routes.php:130-165` are used by the app except **POST lexcent/bill-read** (AI bill reading: FILE bill or bill_base64; 403/422; `{status,message,unavailable}`), which is not in the staff ApiService. Requests are JSON `@Body` maps; the server's `json_or_post()` accepts JSON or form. 201 on expense and partner-pay create; 409 for state conflicts; `invoices/{id}/pdf` streams a file [CODE api@9df1506 controllers/Lexcent.php] [CODE lexco@e52a8ca J/data/api/ApiService.kt:611-769].

| Group | Paths | Screen |
|---|---|---|
| Capabilities | GET lexcent/capabilities | RoleGate / MoneyHub |
| Own expenses | GET lexcent/my-expenses; POST lexcent/expenses; PUT/DELETE lexcent/expenses/{id} | MyExpensesScreen, NewExpenseScreen (receipt required above ₹500 server-side) |
| Own partner pay | GET lexcent/my-partner-pay; POST lexcent/partner-pay; PUT/DELETE lexcent/partner-pay/{id} | MyPartnerPayScreen, NewPartnerPayScreen |
| Admin submissions | GET lexcent/admin/submissions; POST …/submissions/{id}/{approve,reject,request-changes,reimburse} | SubmissionsQueueScreen |
| Admin partner pay | GET lexcent/admin/partner-pay; POST …/partner-pay/{id}/{approve,mark-paid} | PartnerPayApprovalScreen |
| P&L | GET lexcent/admin/pl/summary; GET lexcent/admin/pl | LexcentPLScreen |
| Tithe | GET lexcent/admin/tithe/periods[/{id}]; POST …/periods/{id}/{recompute,finalise,reopen,disbursements}; DELETE lexcent/admin/tithe/disbursements/{id} | TitheScreen |
| Invoices | GET lexcent/invoices/summary; GET lexcent/invoices (status, q, page); GET lexcent/invoices/{id}; POST …/{id}/payments `{amount,date,paymentmode,transactionid,note,send_receipt}`; POST …/{id}/cancel; POST …/{id}/send; GET …/{id}/pdf | MoneyHubScreen sheet, InvoiceDetailScreen, RecordPaymentSheet (through Perfex `Payments_model`) |

### 3.9 Advocate self-onboarding (`require_staff()` per method)

| Method | Path | Codes | App | Screen |
|---|---|---|---|---|
| GET | advocate/status | none | ✓ | AdvocateOnboardingScreens (steps derived from status) |
| POST | advocate/start · restart | none | ✓ | same |
| POST | advocate/upload | 409, 422, 500 (document_type, document) | ✓ | same (bar_council_id, pan, aadhaar) |
| POST | advocate/review | JSON | ✓ | same |
| GET | advocate/agreement | 409 | ✓ | same |
| POST | advocate/execute | 409, 422, 500 | ✓ | same (Leegality eSign; dev Leegality is LIVE, never call in tests) |
| POST | advocate/refresh | 404 | ✓ | same |

### 3.10 Lexmail (constructor: staff 401, `api_seed_staff_context`, `mail_acl->can_access` 403, then `mail_service->as_staff`)

| Method | Path | Request | App | Screen |
|---|---|---|---|---|
| GET | lexmail/accounts | none | ✓ | MailScreen |
| GET | lexmail/folders/{account} | none | ✓ | MailScreen |
| GET | lexmail/messages/{account}/{folder or unified} | q, filter, offset (X-Long-Call) | ✓ | MailScreen |
| GET | lexmail/message/{id} | marks read (X-Long-Call) | ✓ | MailThreadScreen |
| POST | lexmail/mark | id, field, value | ✓ | MailScreen / thread |
| POST | lexmail/archive · trash | id | ✓ | MailScreen swipe |
| POST | lexmail/send | multipart: account_id, to, cc, bcc, subject, body, in_reply_to_id, forward_of_id, forward_attachments; 413 (24 MB cap) | ✓ | MailComposeScreen wizard |
| GET | lexmail/recipients | q | ✓ | MailComposeScreen |
| GET | lexmail/attachment/{id} | stream | ✓ | PdfViewerScreen (`mail-{id}`) |
| POST | lexmail/sync/{account} | (X-Long-Call) | ✓ | background after send/move |

A 403 from any lexmail call sets `MailUnreadHolder.open=false` and hides the Mail tab [CODE lexco@e52a8ca J/feature/mail/MailViewModels.kt:32-41,121].

## 4. Push and realtime

- Push: Beams interest `staff-{id}`; server hook `api_register_notification_push` (installed by `cases_headless_wiring()`); target resolved by `api_notification_target($link)` in `api/helpers/api_notifications_helper.php`; pushes are data-only, high priority [GIT api e88b436 2026-10-27] [CODE lexco@e52a8ca J/push/NotifTarget.kt].
- Realtime: public `do-chamber` / `presence_changed`; private `private-do-advocate-{id}` (`session_event`, `waitroom_event`) authorised by `POST digital_office/pusher_auth` with `Authtoken`; public `notifications-channel-{staffId}` / `notification` (empty payload) [CODE lexco@e52a8ca J/feature/digitaloffice/DigitalOfficePresenceClient.kt:43-99; J/feature/notifications/NotificationsRealtimeClient.kt:25-74].

## 5. Cross-check summary

| Measure | Value |
|---|---|
| ApiService methods | 150 relative + Pusher auth |
| App calls with no route | 0 (every called controller is in STAFF_APP_FEATURES) |
| Declared, never called | 4: `downloadConsultationAttachment`, `downloadConsultationDocRequestFile`, `pendingFunnelReviews`, `downloadConsultationFunnelDocument` |
| Verb methods / unused by the app | 258 / about 130 (mostly stock Perfex, out of token scope) |
| Mismatches | Dead token answers 404 with no auto sign-out; `auth/me` disagrees after a password change; benign: `scope` on causelist/density and `isread` on notifications are ignored |

## Open questions

- Should the staff API answer 401 for a dead token (web change), or should the app treat 404 `Token expired or revoked` / `Token is not found` as a session end (app change)? See [known-issues.md](known-issues.md).
- Should `cases_require_admin_2fa` be enforced in `Auth::login`/`google` for admins?
- Does the `api` module offer staff OTP sign-in that the app should use (rule F1 allows it since the web has it)? Not decided.
