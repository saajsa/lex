# Wiring And Traps

> Part of the Legum Lex Android knowledge base. Verified against: client app 6c069eb, staff app e52a8ca, customers_api 97de5b4, api 9df1506, cases cb0a820 (2026-11-07). How server code is wired for app requests, and the traps that make app traffic behave differently from the web.
>
> This file is identical in legum-lex-android/docs/kb and legum-lex-staff/docs/kb.

Read this before changing any backend code an app reaches, and before concluding that "the web does X, so the app API does X too".

---

## 1. No module init on API requests

- Perfex runs module init files (`cases/cases.php`, `messaging/messaging.php`, `digital_office/digital_office.php`, `lexon/lexon.php`, `razorpay/razorpay.php`, `api/api.php`…) from `_app_init` on the `pre_controller_constructor` hook. That hook is fired by `App_Controller` only; CI core fires only `pre_controller` [CODE application/config/hooks.php:74-79; application/core/App_Controller.php:18; system/core/CodeIgniter.php:508].
- Both API base controllers extend `CI_Controller`: `CustomerApi\RestController` [CODE customers_api@97de5b4 controllers/RestController.php:11] and `REST_Controller` [CODE api@9df1506 controllers/REST_Controller.php:16]. The staff `Auth` controller is also a plain `CI_Controller` [CODE api@9df1506 controllers/Auth.php].
- So **no hook, filter, helper or constant registered by a module init file exists in an app request** unless something below installs it by hand [MEMORY reference_api_no_module_init.md].

## 2. What replaces module init

| Mechanism | Where it runs | What it installs | Ref |
|---|---|---|---|
| `cases_headless_wiring()` (idempotent, static flag) | customers_api `RestController` constructor on **every** client request; api `REST_Controller` after token validation; `api_seed_staff_context()` → `api_mail_wiring()` / `api_invoice_listeners()` | Dev mail guard (if `lex_dev_mail_guard`); `Email_brand` letterhead filters; cases billing, books, billing_hooks, mail_identity helpers (issuer/book filters on money writes); messaging `email_template_sent` → WhatsApp/SMS twin; `api_register_notification_push` (staff bell → phone); `customer_registration_confirmed` / `lead_converted_to_customer` → cases_lead_helper (+ outreach attribution); `invoice_status_changed` → Paid settles Digital Office sessions and lexon fee invoices (skipped while `cases_receipt_invoicing()`) | [CODE cases@cb0a820 helpers/cases_headless_helper.php:18-131] [CODE customers_api@97de5b4 controllers/RestController.php:254-255] [CODE api@9df1506 controllers/REST_Controller.php:854-865; helpers/api_helper.php:405-448] |
| `customers_api_register_gateways()` | `Invoices` controller constructor only | `register_payment_gateway('razorpay_gateway','razorpay')` and `payu_gateway` when active, so app payments are recorded by the same gateway objects as the web. Added in 5f0a1bd after Razorpay app payments could not be recorded | [CODE customers_api@97de5b4 helpers/customers_api_helper.php:28-48; controllers/v1/Invoices.php:23] [GIT 5f0a1bd 2026-11-01] |
| Helpers required when `customers_api_helper.php` loads | every client request | razorpay_invoice_helper, payu_helper, cases_billing_helper, cases_project_types_helper; `cases_permissions_helper` at line 219 | [CODE customers_api@97de5b4 helpers/customers_api_helper.php:15-20,219] |
| Contact permissions | `isAuthorized()` → `cases_register_contact_permission()` | permission ids 7-10 (without them `has_contact_permission` refuses everything) | [CODE customers_api@97de5b4 helpers/customers_api_helper.php:217-224] |
| `api_books_helper` | api REST_Controller | books helper for staff money writes | [CODE api@9df1506 controllers/REST_Controller.php:866-869] |
| By-hand loads per controller | e.g. Accounts (messaging_helper), Lead_onboarding (Dev_mail_guard, Email_brand, lexon registration_service), Legal_notices (cases legal_notice_helper), staff Cases::dispose (cases_date/text/mail_identity/notifications helpers, `cases/documents_model` by path), Courts gate (cases_rbac/access_control/security/bench), `api_staff_can` (cases_rbac_helper), `Funnel_state` | as named | [CODE customers_api@97de5b4 controllers/v1/*.php] [CODE api@9df1506 controllers/*.php] [SESSION 3adfe568 C16, C24] |

Rule since 2026-10-28: API writes go through the web's models and notifications (no forked logic), and the headless wiring carries the web's hooks [GIT api 72981aa, 6e7d74b 2026-10-28] [SESSION 19acbaeb 2026-10-31T04:40]. Section 3 is a violation of that rule.

Caveat: `cases_headless_wiring()` decides "module active" with `$CI->app_modules->is_active()` if `app_modules` is loaded, else `is_dir()`. In customers_api the wiring runs before `checkModuleStatus()` loads `app_modules`, so an installed but deactivated module is treated as active there [CODE cases@cb0a820 helpers/cases_headless_helper.php:28-30] [ASSUMPTION: `app_modules` is not autoloaded].

## 3. VERIFIED gap: plan, fee and access-log helpers never load for app traffic

`cases_plan_helper.php`, `cases_fee_helper.php` and `cases_access_log_helper.php` are required only from `cases/cases.php:232-234` (plus `cases/cron/plan_terms.php` for the plan helper in cron). `cases_access_log_register` is hooked there on `app_init` (line 235). Neither `cases_headless_wiring()`, `customers_api` nor `api` requires them [CODE cases@cb0a820 cases.php:232-235] [CODE grep of modules/ 2026-11-07: loaders are only cases/cases.php and cases/cron/plan_terms.php].

Every call site is guarded with `function_exists(...)`, so app requests silently take the fallback, for example the Registry limit check `if (!$system && function_exists('cases_plan_allows') && …)` [CODE cases@cb0a820 models/Register_model.php:1916] and the view-only check in `isAuthorized()` [CODE customers_api@97de5b4 helpers/customers_api_helper.php:237-245].

| Feature (commit) | Intended | What app traffic actually gets |
|---|---|---|
| View-only contact refusal (customers_api 3cbc2a5, 2026-11-05) | 403 `{status:false, code:'view_only', message:…}` on writes by a view-only contact | Not enforced: a view-only contact can write from the client app |
| Access logging (customers_api 33f7a37, cases 3fbd56f) | Who viewed or downloaded a file, with `$GLOBALS['cases_api_actor']` | The actor global is set, but the logger is not registered: nothing is logged |
| Notice approval through `cases_notice_approve()` (customers_api e68688b) | Shared approval with fee check; 409 on refusal | Falls back to a bare `client_approved_at` update [CODE customers_api@97de5b4 controllers/v1/Legal_notices.php:335-352] |
| Notice `fee` object, "Awaiting Payment", pay-first `next_step` (customers_api 97de5b4) | Additive fields on list/detail | `fee_gate()` returns null: `fee` always null, no label, no guidance |
| Plan limits (cases f8cb17e..7413d31) | 402 `plan_limit` when over a limit | Never refused from an API path (no app write endpoint returns it today) |
| Plan activation on payment (`cases.php:526-535`, `invoice_status_changed` → `cases_plan_invoice_paid`) | A paid plan invoice activates the plan | A plan invoice paid in the client app (`pay/verify` records in-request) or recorded in the staff app does not activate the plan; no cron catches up; after 30 days `cases_plan_lapse_unpaid` may lapse it [CODE customers_api@97de5b4 controllers/v1/Invoices.php:904-975] [CODE cases@cb0a820 helpers/cases_plan_helper.php ~600-635] |
| Notice fee release on payment (`cases.php:543-551` → `cases_notice_fee_release`) | Paid fee invoice releases the held notice | Notice stays held (draft hidden, 'created' event not sent, despatch refused) if its invoice is paid through an app |
| Chamber access-fee waiver by plan (digital_office b165e41) | Business plan waives the access fee | Unverified whether `cases_plan_*` is callable from `Digital_office_model::plan_waives_access` in an API request [ASSUMPTION] |

The loading gap is verified by reading and grep; the runtime effect has not been probed over HTTP. Probe on dev: (1) POST a write as a view-only `selftest-` contact through customers_api (200 today, 403 when fixed); (2) `GET notices` for a pay-first notice (`fee` null today); (3) pay a notice-fee invoice through the Razorpay stub via `invoices/{id}/pay/verify` and check the notice is released.

**Likely fix (web, owner of cases):** require the three helpers in `cases_headless_wiring()` and register the two `invoice_status_changed` handlers and `cases_access_log_register` there. The selftests pass today because they call the functions in-process, where `cases.php` is loaded [ASSUMPTION].

## 4. Migrations auto-run only on admin pages and cron

- `Cases_migrator::auto()` runs pending migrations for cases, then lexcent and finance (if active), under a MySQL lock `lex_module_migrations`. Fingerprint in the non-autoloaded option `cases_auto_migrate_fingerprint`; failures retried hourly [CODE cases@cb0a820 libraries/Cases_migrator.php:25-47,142-205] [GIT 991d9ce 2026-11-01].
- Triggers: `admin_init` (a non-AJAX GET by a logged-in staff member, skipped on `/migrate/`) and `after_cron_run` [CODE cases@cb0a820 cases.php:496,512-515].
- **Neither API triggers it.** After a pull, app requests can hit a schema without new columns until a staff member opens an admin page or the next cron tick (cron every ≤300 s via `modules/cases/cron/tick.sh`) [MEMORY project_perfex_cron.md].
- Migrations of other modules (lexon, digital_office, messaging, customers_api, api) are **not** in the auto set [CODE cases@cb0a820 libraries/Cases_migrator.php]. Owner position: "Migrations run on their own" [SESSION 3adfe568 2026-11-01T21:27]; production 277 had already run on 11-01. Schema changes for app features should therefore live in cases migrations [MEMORY project_do_migrations_and_gate.md].

## 5. Option cache trap

- Perfex caches autoloaded options at boot. `update_option()` writes the DB only, so a later `get_option()` in the same request returns the old value; `update_option()` on a missing name creates it with autoload=1 [MEMORY reference_perfex_option_cache.md]. Cases_migrator writes its options directly with autoload 0 for this reason [CODE cases@cb0a820 libraries/Cases_migrator.php:196-205].
- In the app API paths, option reads are boot-time flags (`lex_dev_mail_guard`, `lex_dev_push_allow`) not written mid-request, so it is not a live trap today. The only `update_option()` calls in `api/` are in the admin UI `Api.php` and stock models out of the staff token's scope [CODE grep api/ customers_api/].
- After changing a dev switch, the next request sees it; a long-running cron process may not [ASSUMPTION].

## 6. Other traps that bit app work

| Trap | Effect | Fix / rule | Ref |
|---|---|---|---|
| **Moshi decodes every number as `Double`** | `"23.0".toIntOrNull()` is null; ids dropped, taps went to the web | `(v as? Number)?.toInt()`, staff `asInt()` | [SESSION 3adfe568 C19, 2026-10-26T19:57] |
| One live client token per contact | A curl OTP sign-in revoked the phone's token | Tests reuse the stored `tblcontacts.customer_api_key` | [SESSION 3adfe568 C17] |
| Google sign-in retires a contact's password | Debug email sign-in broke | Dedicated dev contact 14514; never Google-sign-in with it | [MEMORY project_android_client_app.md] |
| Staff token dead → HTTP 404 | App shows "Not found." | See [known-issues.md](known-issues.md) | [CODE api@9df1506 controllers/REST_Controller.php:797,845] |
| Client token key changed (e3027f0) | All pre-deploy client tokens fail: one forced sign-in; `APP_ENC_KEY` must exist on every environment or token issue throws | Tell testers; lead tokens unaffected | [CODE customers_api@97de5b4 libraries/Authorization_token.php:63-96] |
| CI query-builder trap | A settings/permission helper run mid-query broke the task-detail query ("No tables used", 500) | Do lookups before building a query | [SESSION 3adfe568 2026-10-25T19:29] [DOC mobile_handoff_2026-11-01.md ground rules] |
| Model loaded by `require_once` before `App_Model` | Chamber 500 | Load the model through CI first | [SESSION 3adfe568 C18] |
| Path-cased model loads | `Unable to locate model Documents_model` in staff dispose | Load `cases/documents_model` by path | [SESSION 3adfe568 C24] [MEMORY reference_ci_load_model_binding.md] |
| Forked api model copies | Payments_model copy lost the invoice; counsel rule missing in copies | Writes through web models; `get($id, [], $playground)` | [SESSION 3adfe568 C35-C36] |
| PHP 8 `vsprintf` ValueError in a notification hook | Admin pages crashed | try/catch padding; core `_l` pads (e105e95) | [SESSION 3adfe568 2026-10-26T19:57] |
| Code after `?>` in a helper | Dev printed raw code | Keep code inside PHP, no closing tag | [SESSION 3adfe568 C35] |
| `cases_stat_where` missing in API | Nyayco counts read 0 (swallowed by try/catch) | `cases_nyay_derived` loads `cases/cases_stats` itself | [SESSION 3adfe568 C38] |
| Prod MySQL clock 12.5 h behind PHP | Wrong message times | Stamp from PHP | [SESSION 3adfe568 C16] [MEMORY project_camplex_module.md] |
| Prod `wait_timeout=60` | Writes discarded after long API calls | See the memory note before adding long external calls | [MEMORY project_prod_mysql_wait_timeout.md] |
| Client ID is a string | Strict `===` int ownership checks refuse owners | Cast before comparing | [MEMORY reference_client_id_is_string.md] |
| Cases access helper returns an array | Casting to bool always passes | Check the array content | [MEMORY reference_cases_access_helper_shape.md] |

## Open questions

- Fix location for §3: `cases_headless_wiring()` (preferred, one place) or per controller? Web agent to decide and announce per [coordination.md](coordination.md).
- How do lexon, digital_office and messaging migrations reach production after a pull, given they are not in `Cases_migrator::auto`?
- Is `app_modules` loaded before the wiring in customers_api (affects hooks of deactivated modules)?
