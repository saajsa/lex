# Open Items

> Part of the Legum Lex Android knowledge base. Verified against: client app 6c069eb, staff app e52a8ca, customers_api 97de5b4, api 9df1506, cases cb0a820 (2026-11-07). Everything unfinished, untested, deferred, drifted or assumed, in one place, so a reader knows what is not settled.
>
> This file is identical in legum-lex-android/docs/kb and legum-lex-staff/docs/kb.

Integration problems with severity and owner are in [known-issues.md](../backend/known-issues.md); this file lists them only by reference where they are also open work.

---

## 1. Blocking backend fix (web)

1. **Load `cases_plan_helper.php`, `cases_fee_helper.php` and `cases_access_log_helper.php` (and register their `invoice_status_changed` handlers and `cases_access_log_register`) in `cases_headless_wiring()`.** Today they load only from `cases/cases.php:232-234` [CODE cases@cb0a820 cases.php:232-234], and neither API runs module init, so view-only refusal, notice fees, plan limits, plan activation on payment, notice-fee release on payment and access logging do not run for app traffic. Every call is `function_exists`-guarded, so it fails silently (e.g. [CODE cases@cb0a820 models/Register_model.php:1916]). Blocks any app work on view mode, plans or notice fees. Details: [wiring-and-traps.md](../backend/wiring-and-traps.md) §3.

## 2. Drift between backend and apps after 1 Nov 2026

Neither app changed after 2026-11-01 [GIT]. Backend changes since then that the apps have not caught up with:

| Backend change | App gap | Ref |
|---|---|---|
| cases c8c0813 (11-03): project members = any active staff | Staff copy still says "Only X's counsel can be on the project." / "No counsel is assigned…"; stale comments; stale api doc comment at `Project_stages.php:97` | [CODE lexco@e52a8ca feature/projects/NewProjectScreen.kt:204,207; StageWizards.kt:168,170] |
| cust_api e6eb3e1 (11-03): project files view-only | Client streams the file to cache and hands it to an external app (`ACTION_VIEW` chooser), so it can be saved or shared; ignores `view_only` and `X-Lex-View-Only`; the new Files-tab 403 shows as a generic error | [CODE lex@6c069eb feature/projects/ProjectFilesScreen.kt:144-156,385-390] |
| cust_api e3027f0 (11-04): new token key | No app change needed; every client user signs in again once after deploy | [CODE customers_api@97de5b4 libraries/Authorization_token.php:63-96] |
| cases ce0e8be (11-04): `cases_require_admin_2fa` | Staff API does not check it; an admin without 2FA can use the staff app once the option is on | [CODE cases@cb0a820 cases.php:2242-2257] |
| lexon 7f56a67 / digital_office 6fa4525 (11-04): refusals when no gateway is on; AI monthly cap | Unverified that the apps show the server message rather than spin or crash | [GIT 2026-11-04] |
| cust_api 3cbc2a5 (11-05): view-only contacts | Inert headlessly; client has no `view_only` branch and maps several 403s to "Your session has expired"; API exposes no `access_mode`/`can_act` flag up front; staff app has no Access choice for contacts | [CODE lex@6c069eb feature/cases/CasesViewModel.kt:57; CaseDetailViewModel.kt:65; CaseDocViewerScreen.kt:258] |
| cases f8cb17e..7413d31 (11-05/06): platform plans | No plan card, plan-invoice cancel or 402 `plan_limit` handling in either app; plan invoices paid in an app do not activate the plan (inert headlessly) | [CODE cases@cb0a820 helpers/cases_plan_helper.php:182-202] |
| cust_api e68688b, 97de5b4 (11-05/07): notices paid first | Client does not read `fee`, has no "Pay the fee" step; approve can return 409; staff app has no "Fee unpaid" badge or disabled despatch | [CODE customers_api@97de5b4 controllers/v1/Legal_notices.php:335-352] |
| cases 88e75c5 (11-06): notice client-approval line; seat-limit message | Parity candidates for the staff app | [GIT 88e75c5] |
| cases mig 278/279, df3aac8, 8768a5e (11-03): project from consultation, invoice links, task templates, stages on calendar | No app parity (optional under web-first) | [GIT] |

## 3. Unfinished or deferred work

Client app:
- Lead Agreement Check fee still pays through the page in `PayActivity` (not the native checkout) [SESSION 3adfe568 2026-11-01T21:09].
- Client app has no `TextCase` display casing [SESSION 3adfe568 2026-10-27T19:04].
- Stale in-code text: MainActivity KDoc "The app takes no payments"; TokenStore comment about encryption that was never done; build.gradle.kts cloudflared comments [CODE lex@6c069eb K/MainActivity.kt:34-37; TokenStore.kt:11-12; app/build.gradle.kts:26-30,85-92].
- Dead code: `HomeScreen.ActionQueueBlock`, `Route.Register`; 8 unused ApiService methods (old dashboard, `registerDevice`, `me`) [CODE lex@6c069eb].
- Repo docs (README, ARCHITECTURE, API, DEVELOPMENT, CHANGELOG, last touched 2026-10-19) are stale throughout [DOC legum-lex-android/README.md, docs/*.md].
- Whether old-style detail screens noted on 10-19 (ahc/*, invoice detail variants, calendar, hearing detail) were all redone is unclear [SESSION 3adfe568 C18].

Staff app:
- Optional "same person" linking for staff (handoff brief) not done; staff Documents gating kept as "any function" [SESSION 3adfe568 C38].
- Removal of any subscription screens unverified (only the API answers 404); removal of gateway lists other than Razorpay/PayU not confirmed [SESSION 3adfe568 C38].
- Remaining redesign screens: Notices, Counsel desk, Documents, Agreements; no API for edit case, link invoice, raise notice from the app [SESSION 3adfe568 C22-C24].
- Not in the app although the API has them: `lexcent/bill-read` (AI bill reading), `client_onboard/reopen`, `auth/otp/*` sign-in, KYC approve/reject [CODE api@9df1506 config/routes.php] [MEMORY project_staff_app.md].
- Dead code: `DashboardScreen` (965 lines), `CaseboardBandScreen`, `ConsultationsScreen`, `Route.Hearings`, `Route.LexcentAdminHome`, 4 unused download/funnel endpoints, ML Kit text-recognition dependency [CODE lexco@e52a8ca].
- Audit ledger open items (2026-10-28): unread count `isread` vs `isread_inline` (owner's call); Update Hearing and advocate wizards use their own frames; replies left behind when a consultation is deleted; all-caps small labels (owner's UI pass); status-name drift (Completed/Resolved/Released, Declined/Rejected); no client note on status change, close with reason, escalate; WhatsApp "accept or decline within N hours" wording [SESSION 3adfe568 audit ledger 2026-10-28].
- Repo docs (README, ARCHITECTURE, DEVELOPER_GUIDE, DEVELOPMENT, API, CHANGELOG, design/edge.md) are stale [DOC legum-lex-staff/docs/*].

Both / backend:
- Notification follow-ups: per-person channel preferences, batching, quiet hours (web first) [SESSION 3adfe568 2026-10-26T19:57].
- Save-time text tidy awaits the owner's decision [SESSION 3adfe568 2026-10-27T18:54].
- Registry in the apps: deferred by the owner [SESSION 3adfe568 2026-10-26T19:21].
- UI refinement: reserved to the owner [SESSION 3adfe568 2026-10-26T19:21].
- Per-function permissions in the staff API only partly mapped (`api_staff_can()` "any practice function" for Documents and similar); rerun `cases/tests/public/browser/rbac_matrix.mjs` after any change [MEMORY rbac-deferred-mobile.md].
- Staff-app contact and onboarding writes skip the web's contact access-mode filter and seat-limit message (cases.php:2304 hooks not registered in API requests) [CODE cases@cb0a820 cases.php:2304].
- Old password sign-up endpoint (`POST register`) still live on production [SESSION 3adfe568 audit ledger 2026-10-28].
- Older backlog: `api/controllers/Advocate.php:257` `advocate_temp` documents never made permanent (still present, not re-verified); `agreement_draft` scoping looks reworked (not re-verified) [MEMORY project_mobile_app_backlog.md] [CODE api@9df1506 controllers/Advocate.php:257].
- GTM fact sheet findings: enquiry attachments readable by link; no privacy notice or consent records; eCourts token stored in plain text (the public JWT key was fixed by e3027f0) [SESSION 3adfe568 2026-11-01T10:18].
- Production follow-ups: cleanup of leaked hidden-milestone activity rows; Lexcent matcher gap (already-recorded payments not recognised) [SESSION 3adfe568 C31-C36].
- Desk onboarding switch `lexon_desk_onboarding` off on dev and prod (at 2026-10-24) [SESSION 3adfe568 C28]. **[Owner decision 2026-11-07, REQ-002]** `lexon_desk_onboarding` is **active on production and under testing**; do not treat it as unused. Dev value is 0.
- Daily Board for advocates: brief only, awaiting owner decisions [SESSION 89b46f22 2026-11-07T01:42]. [Superseded 2026-11-10: built on web and in the client app under REQ-008; see app/features.md §20a-20b and backend/customers-api.md §2.16.]
- **Daily Board, open owner decisions (2026-11-10):** (a) the daily eCourts check's margin: 1 credit charged per case against a full read costing about ₹1.50; (b) production's first run charges every counsel's tracked cases 1 credit each once the read cap is raised. Not built either way [SESSION 3adfe568 2026-11-10].
- **Specimen privacy (board):** the specimen folder's `.htaccess` deny cannot be tested on dev (nginx); production is Apache. Deferred to REQ-004 (isolated test site) [SESSION 3adfe568 2026-11-10].
- **Board leftovers (2026-11-10):** the client `BoardSheet` route still carries a `{junior}` path segment though the screen no longer uses it [CODE lex@08b9dc4 app/src/main/java/com/legumlex/client/ui/navigation/Routes.kt:71-77]; nothing pushed for G1 (board 98771bd, customers_api 94ab097, cases 5c6e6c3) or G2 (lex 386650e, 08b9dc4).

External / ops:
- GoDaddy site-wide bot challenge blocked the published app's API calls (2026-10-20); whether fixed is unknown [SESSION 3adfe568 C22]. **[Owner decision 2026-11-07, REQ-002]** Both Android apps already communicate successfully with the production backend.
- Android OAuth clients: client debug + Play app-signing SHA-1, and `com.legumlex.staff`, must be registered for Google sign-in; status unknown [SESSION 3adfe568 C22]. **[Owner decision 2026-11-07, REQ-002]** Google Sign-In is configured; Google's production approval is pending.
- Production gateways were off on 2026-11-01 ("This Invoice Can't Be Paid Here") and the Legum Lex Razorpay keys were empty; the owner said on 11-04 both businesses are live on Razorpay [SESSION 3adfe568 2026-11-01T21:09] [SESSION 89b46f22 2026-11-04T15:06]. Newer source says live; not re-checked.
- Was the client repo saajsa/lex made private (owner, 2026-10-30: "Make it private")? Unverified [SESSION 3adfe568 2026-10-30].
- Uncommitted Gradle bumps in both apps wait on the owner's build (do not touch) [GIT status 2026-11-07].

## 4. Built but not tested on a device

| Item | App | Ref |
|---|---|---|
| Two-factor sign-in step | staff | [MEMORY project_mobile_handoff_2026_11.md] |
| DO private channel (`pusher_auth`) | staff | [SESSION 3adfe568 C38] |
| Lexmail read-only and no-access states | staff | [SESSION 3adfe568 C36] |
| DO call panel with a live client; in-session yellow state | staff | [SESSION 3adfe568 C36] |
| Advocate wizard | staff | [SESSION 3adfe568 C36] |
| Project deep link into the focused card; Mine with tasks | staff | [SESSION 3adfe568 C36] |
| api `common` (payment modes by `?book=`) and `contacts` (phone-on-record 409) over HTTP; the staff token lacks those scopes | staff | [SESSION 3adfe568 C38] |
| Gateway Method chooser (no dev business has keys for both) | client | [SESSION 3adfe568 2026-11-01T21:09] |
| Razorpay after 8772b0d (only PayU re-run) | client | [SESSION 3adfe568 2026-11-01T21:09] |
| PayU WebView against dev since the 11-04 "Razorpay only" decision | client | [SESSION 89b46f22 2026-11-04T15:32] |
| Sign-in "Onboard Now" and the 10-24 evening screens on a fresh install | client | [SESSION 3adfe568 C28-C30] |
| Staff session after 90-day expiry or password change ("Not found." everywhere) | staff | [CODE api@9df1506 controllers/REST_Controller.php:845] |
| Uncommitted AGP 8.10.1 / Gradle 8.11.1 builds | both | [GIT status] |

## 5. Every [ASSUMPTION] in the findings

| Assumption | Source finding | How to settle |
|---|---|---|
| customers_api/api commits of 2026-10-02..10-17 (token scoping, safer sign-up) came from other sessions | history_early | grep 19acbaeb owner log; later partly confirmed (7393723 in 19acbaeb) |
| Status of older backlog bugs (Legal_notices documents_model binding, agreement draft filter) | history_early | re-read code |
| The 10-29 15:02 quote "We really need the project details screen" means "don't need" | history_late | the chosen answer "Fold it into the card" supports it |
| The helper gap makes view-only, notice fees and plan activation silent no-ops at runtime (static reading; loading gap itself now verified) | web_impact, backend_contract | HTTP probe on dev with a view-only `selftest-` contact; pay a notice-fee invoice through the Razorpay stub |
| Ticket departments matter only if an app lists them | web_impact | none needed |
| `me()` is unused in the client (grep by name) | backend_contract | search for qualified calls |
| `PUT notices/{id}/approve` 409 is not handled in the client ViewModel | backend_contract | read `NoticeDetailViewModel` |
| `app_modules` is not autoloaded before `cases_headless_wiring()` in customers_api, so inactive modules count as active | backend_contract | log in RestController constructor |
| No Perfex autoload config pulls the plan/fee helpers in | backend_contract | caller has since verified the loading gap by grep |
| A long-running cron process does not see a changed dev switch | backend_contract | test |
| e6eb3e1: save/share UI in the client is not disabled by the server signal | backend_contract | confirmed by client_app reading (`ACTION_VIEW` chooser) |
| Client app does not request POST_NOTIFICATIONS at runtime (grep found none) | client_app | read MainActivity / PushSetup; test on Android 13+ |
| `lexgw.bat` lives in Windows %TEMP% | client_app | not needed; `_build/*.bat` replace it |
| Keystore folder is `/mnt/b/and_studio/keystore` | client_app | ask the owner; never open it |
| Whether `app/release/*` artifacts are tracked in the client repo | client_app | `git ls-files app/release` |
| ML Kit text-recognition is a dead dependency (staff) | staff_app | grep confirmed no use; remove with owner approval |
| Pusher/Beams/ML Kit/credentials ship consumer R8 rules (staff) | staff_app | test a release build |
| Staff `allowBackup=true` may back up the plaintext token store | staff_app | read backup_rules.xml / data_extraction_rules.xml |
| Staff invoice footer hard-coding "LEGUM LEX" conflicts with two-supplier invoicing | staff_app | open a Nyay & Co. invoice in the app |
| `./gradlew testDebugUnitTest` runs TextCaseTest | staff_app | run it |
| `docs/design/edge.md` rules may not describe current screens | staff_app | review |
| Public `notifications-channel-{id}` is low impact (empty payload) | staff_app | confirm payload |

## 6. Stale memory notes

| Note | What is stale | Newer source |
|---|---|---|
| `feedback_app_payments_by_invoice.md` | Says the app takes no payments and the SDK was removed | In-app checkout since 2026-11-01 (23776d7, 8772b0d) [CODE lex@6c069eb feature/finance/Checkout.kt] |
| `project_counsel_only_projects.md` | Counsel-only project members | Any active staff since 2026-11-03 [GIT cases c8c0813] |
| `project_app_test_builds.md` | Lists the test keystore and `TEST_STORE_*` as leftovers | Deleted per compaction 38 [SESSION 3adfe568 C38] |
| `project_mobile_app_backlog.md` | Items 4 (Lexcent base64 receipt) and 5 (JWT key too short) open; item 1 open | 4 fixed [CODE api@9df1506 controllers/Lexcent.php:251-260]; 5 fixed by e3027f0; 1 moot (no notice creation) |
| `project_staff_app.md` | DO private channel "crashed sign-in and was skipped"; mobile-code sign-in as Phase II; "pushed at c094c4c" | Private channel implemented in e52a8ca; HEAD is e52a8ca |
| `payu-beside-razorpay.md` | "Every flow Razorpay takes must also take PayU" | Owner 2026-11-04: Razorpay in production, PayU set aside [SESSION 89b46f22 2026-11-04T15:32] |
| `project_android_client_app.md` (index line) | "sign-in step 1 done (11ac1ad)" as the headline state | Client HEAD 6c069eb, 0.4.1 with leads, desk, payments, account switch |
| `feedback_manual_push.md` | Description "Commit freely" contradicts body "Commit when asked" | Body (more specific) |
| `feedback_flat_design.md` | "Colour only red/green" read as an app-wide rule | Staff app uses yellow/ink pairings [MEMORY feedback_text_colour_pairings.md] |
| `project_mobile_handoff_2026_11.md` | Mentions PayActivity as the payment path | Native CheckoutActivity is the path; PayActivity only views pages and lead fees |

## Open questions

- Should the three helpers be loaded in `cases_headless_wiring()` or required per controller? The Web agent decides; see [coordination.md](../backend/coordination.md).
- Which of the dead code items may be deleted? Needs the owner.
- Is signing out on any `auth/me` failure at Splash (including offline) intended in the staff app?
