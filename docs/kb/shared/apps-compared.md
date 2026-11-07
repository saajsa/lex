# Apps Compared

> Part of the Legum Lex Android knowledge base. Verified against: client app 6c069eb, staff app e52a8ca, customers_api 97de5b4, api 9df1506, cases cb0a820 (2026-11-07). What the two apps share and where they diverge, so a change in one is not assumed to apply to the other.
>
> This file is identical in legum-lex-android/docs/kb and legum-lex-staff/docs/kb.

Client = `/mnt/b/and_studio/legum-lex-android` (saajsa/lex, `com.legumlex.client`, "Legum Lex"). Staff = `/mnt/b/and_studio/legum-lex-staff` (saajsa/lexco, `com.legumlex.staff`, launcher label "Lex Co"). Both are two independent repos; there is no shared library module. Shared code is copied by hand (e.g. WizardFrame, SwipeRevealRow, the display-text fixture).

## 1. Identity and size

| | Client | Staff | Ref |
|---|---|---|---|
| HEAD | 6c069eb 2026-11-01 21:21 UTC | e52a8ca 2026-11-01 20:12 UTC | [GIT] |
| Commits | 112 (from d0822cf 2026-10-19, app already at 0.2.1) | 95 (from 47287d8 2026-10-21, app already at 0.1.2) | [GIT] |
| Version | 0.4.1 (code 8) | 0.2.0 (code 4) in source; production staff app was 0.2.2 on 2026-10-30 | [CODE lex@6c069eb app/build.gradle.kts:54-55] [CODE lexco@e52a8ca app/build.gradle.kts:43-44] [SESSION 3adfe568 2026-10-30] |
| Kotlin files | 176 | about 46,000 lines | [CODE] |
| Backend | `customers_api` (module), base `…/customers_api/v1/` | `api` (module), base `…/api/` | [CODE both build.gradle.kts] |

## 2. Shared (same approach in both)

- Toolchain, SDK levels, library set and versions (see [conventions.md](conventions.md) §1).
- Single activity, no DI, `AndroidViewModel` + `ApiClient.get(app)`, `Map<String, Any?>` responses, `Result<T>` repositories.
- Interceptor chain `X-Long-Call` (120 s) → `AuthInterceptor` (`X-Skip-Auth: 1` bypass) → logging.
- DataStore `auth` token store in plain text; SharedPreferences `appearance`/`theme`, default LIGHT.
- Google sign-in by Credential Manager `GetGoogleIdOption` (filterByAuthorized=false, autoSelect=false) with the server client id fetched at runtime (`google_config`), falling back to `BuildConfig.GOOGLE_WEB_CLIENT_ID`.
- Pusher Beams over FCM, app-drawn notifications, `EmptyMessagingService` removed.
- `WizardFrame` kit for multi-step forms; `SwipeRevealRow`.
- ML Kit document scanner for uploads (client: attachments, desk account docs, lead onboarding; staff: case/hearing scans, KYC).
- Web-first rule: neither app may add a function the web lacks (F1 in [owner-rules.md](owner-rules.md)).

## 3. Divergent

| Area | Client | Staff | Ref |
|---|---|---|---|
| **Auth header** | `Authorization: Bearer <JWT>` | `Authtoken: <token>` | [CODE lex@6c069eb K/data/api/AuthInterceptor.kt:24-25] [CODE lexco@e52a8ca J/data/api/AuthInterceptor.kt:14-20] |
| **Token** | JWT, 7 days from `API_TIME`, one live token per contact (`tblcontacts.customer_api_key`); signed with an `APP_ENC_KEY`-derived secret since e3027f0 (2026-11-04) | JWT whose real expiry is the `tbluser_api` row (+90 days sliding); scoped "Staff App" token limited to `STAFF_APP_FEATURES` | [CODE customers_api@97de5b4 libraries/Authorization_token.php:69-96] [CODE api@9df1506 controllers/Auth.php:683-735; models/Api_model.php:766-805] |
| **Sign-in methods** | Google (with `allow_lead`), email+password, mobile code (WhatsApp then SMS), debug test-contact button, guest desk before sign-in | Password, Google, then two-factor step if enabled; **no mobile-code sign-in in the app** although `auth/otp/*` exists | [CODE lex@6c069eb K/feature/auth/LoginScreen.kt] [CODE lexco@e52a8ca J/feature/auth/LoginScreen.kt:443-463] |
| **Roles in app** | client vs lead (`SessionRole`); lead has its own shell (Home, Enquiries, More) | `is_admin`, `is_advocate`, capabilities (`lexcent`, `customers.create/edit`, `projects.create`) from the login/me payload | [CODE lex@6c069eb K/data/auth/SessionRole.kt] [CODE lexco@e52a8ca J/data/auth/AuthRepository.kt:98-136] |
| **401 handling** | Central: a 401 on a request carrying the stored JWT ends the session once (`SessionEvents.end()` → Login). 403 does not | No central handling; 401 becomes the message "Your session has expired. Sign in again."; no redirect | [CODE lex@6c069eb K/data/api/AuthInterceptor.kt:17-31] [CODE lexco@e52a8ca J/data/api/ApiParsing.kt:59-72] |
| **Dead/revoked token** | 401 from `isAuthorized()` → sign-out | Server answers **404** `Token expired or revoked`; app shows "Not found." on every screen; `auth/me` may still answer 200 | [CODE api@9df1506 controllers/REST_Controller.php:797,845] [CODE lexco@e52a8ca J/data/api/ApiParsing.kt:60-67] |
| **403 handling** | Several ViewModels map 403 to "Your session has expired" (wrong for `view_only`) | Shows the server's message ("a refusal, not a lost session") | [CODE lex@6c069eb feature/cases/CasesViewModel.kt:57, CaseDetailViewModel.kt:65] [CODE lexco@e52a8ca J/data/api/ApiParsing.kt:59-72] |
| **Session restore** | Splash 900 ms; token not validated at launch (first 401 sends to Login); leads check `lead/me` (4 s) and may be upgraded to a client token | Splash 3 s; `GET auth/me` must succeed or the token is wiped (**offline cold start signs out**) | [CODE lex@6c069eb K/ui/navigation/NavGraph.kt:89-113] [CODE lexco@e52a8ca J/feature/auth/SplashScreen.kt:49-59] |
| **Endpoints declared** | 133 (125 relative + 8 absolute `@Url`, incl. lexon funnel and `cases/landing/ajax_track`) | 150 relative + Pusher auth | [CODE both ApiService.kt] |
| **Push interest** | `contact-{id}`; account switch adds interests without clearing old ones | `staff-{id}`; per-device on/off switch on Profile | [CODE lex@6c069eb K/push/PushSetup.kt:45-58] [CODE lexco@e52a8ca J/push/PushSetup.kt:27-68] |
| **Push payload** | Data keys `route` + target id → `legumlex://{consultation, invoice, hearing, project}/{id}`; `route=ahc` gets no deep link | Server-resolved target `screen, target_id, web, notification_id`; data-only, high priority; tap marks the inbox row read | [CODE lex@6c069eb K/push/ClientFcmService.kt:65-125] [CODE lexco@e52a8ca J/push/NotifTarget.kt:18-60] |
| **Channel** | `legum_lex_default` "Updates" | `office_alerts` "Office alerts" | [CODE both] |
| **Realtime** | none (polling: chamber 30 s, DO intake 4 s, waitroom 15 s) | Pusher Channels: public `do-chamber`, private `private-do-advocate-{id}` via `digital_office/pusher_auth`, public `notifications-channel-{id}` | [CODE lex@6c069eb feature/digital_office] [CODE lexco@e52a8ca J/feature/digitaloffice/DigitalOfficePresenceClient.kt] |
| **Deep links** | `legumlex://` hearing, consultation, invoice, project | `lexcostaff://google-connected` (Calendar OAuth return) | [CODE both AndroidManifest.xml] |
| **Payments** | Native: `CheckoutActivity` (Razorpay SDK 1.6.40, PayU signed form in a WebView), inline amount on invoice; `PayActivity` WebView for viewing and lead fee page | Records payments (Record Payment wizard → Perfex `Payments_model`); takes none | [CODE lex@6c069eb K/feature/finance/Checkout.kt] [CODE lexco@e52a8ca J/feature/lexcent/RecordPaymentSheet.kt] |
| **Bottom bar** | Black notched bar: Home, Consultations, (raised yellow Consult), Notices, More; Digital Office and account switch in the header | Consultations, Mail, (raised Home), Office, Lexcent; Lexcent hidden without access, Mail hidden on 403 | [CODE lex@6c069eb K/ui/navigation/BottomNav.kt; Routes.kt:310-327] [CODE lexco@e52a8ca J/ui/navigation/Routes.kt:209-219] |
| **Menu** | More screen + Profile | Profile is the menu | [GIT baada46] |
| **Projects UI** | List with swipe-to-Open; detail screen with tabs (Stages, Tasks, Files, Discussions) | My Work: cards open in place, detail folded into the card; no project screen | [GIT e988434, 6702227] |
| **Theme engine** | `LegumLexTheme` + `LocalLexColors`; `Lex.colors` composable | `LegumStaffTheme`; getters over `EdgeTheme.dark` | [CODE both ui/theme] |
| **Components** | `ui/components/Mylex.kt` (LexCard, StatCard, StatusChip…), DayTimeline, `ahc/` kit | `StaffUi.kt` (933 lines: StaffCard, StatusPill, SlidingTabs, Sym…), StaffMotion, `pressable` | [CODE both ui/components] |
| **Display casing** | none | `TextCase` + shared fixture test | [CODE lexco@e52a8ca J/ui/TextCase.kt] |
| **Backups** | `allowBackup=false` | `allowBackup=true` (token store exclusion unverified) | [CODE both AndroidManifest.xml] |
| **Permissions** | INTERNET, CAMERA, RECORD_AUDIO, POST_NOTIFICATIONS, plus unused USE_BIOMETRIC, READ/WRITE_CALENDAR | INTERNET, POST_NOTIFICATIONS (requested at runtime on API 33+) | [CODE both AndroidManifest.xml] [CODE lexco@e52a8ca J/MainActivity.kt:110-116] |
| **Release minify** | R8 on; Razorpay keep rules | R8 on; Moshi/Retrofit keep rules only | [CODE both proguard-rules.pro] |
| **NDK symbols** | release `SYMBOL_TABLE` | `FULL` | [CODE both app/build.gradle.kts] |
| **Unused deps** | security-crypto, biometric | ML Kit text-recognition [ASSUMPTION: dead] | [CODE grep] |
| **Tests** | none | `TextCaseTest` | [CODE] |

## Open questions

- Should the staff app gain the client's central session-end handling (and should the staff API answer 401 rather than 404 for a dead token)? See [known-issues.md](../backend/known-issues.md).
- Is any code meant to move into a shared module? No decision recorded.
