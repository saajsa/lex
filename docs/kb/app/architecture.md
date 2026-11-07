# Client App Architecture

> Part of the Legum Lex Android knowledge base. Verified against: client app 6c069eb, staff app e52a8ca, customers_api 97de5b4, api 9df1506, cases cb0a820 (2026-11-07). How the client app (com.legumlex.client) is put together: packages, shell, navigation, state holders and data flow.

Label shorthand in this file: `[CODE path:line]` means `[CODE lex@6c069eb path:line]`, and `K/` means `app/src/main/java/com/legumlex/client/`.

## 1. Shape of the app

- Repo `/mnt/b/and_studio/legum-lex-android` (GitHub saajsa/lex), branch main, HEAD `6c069eb` (2026-11-01, "Switch Account beside the bell, not on Profile"). 112 commits, 176 Kotlin files, one Gradle module `:app` [GIT 6c069eb 2026-11-01] [CODE settings.gradle.kts].
- Single activity (`MainActivity`) plus two payment activities (`PayActivity`, `CheckoutActivity`, both not exported). The whole UI is Jetpack Compose [CODE app/src/main/AndroidManifest.xml:30-44].
- **No DI framework.** Singletons and direct construction everywhere; ViewModels are mostly `AndroidViewModel`s that call `ApiClient.get(app)` [CODE K/feature/notifications/NotificationHub.kt:7 "Plain singleton (the app uses no DI)"].
- **No local database.** Everything is server truth, fetched on screen entry (see section 7).
- Governing owner rule (firm rule, still in force): > "No function that does not exist on the web app can be adopted on the mobile app. This is a firm rule." [SESSION 3adfe568 ~2026-10-19T12:30] [MEMORY feedback_web_first_parity.md]. Many screens are explicit ports of web pages (desk, chamber, registry), see features.md.

## 2. Packages

| Package | Contents | Evidence |
|---|---|---|
| `data/api` | `ApiService` (133 Retrofit functions), `ApiClient`, `AuthInterceptor`, `FileUpload` (`uriToFilePart`) | [CODE K/data/api/] |
| `data/auth` | `TokenStore`, `AuthRepository`, `SessionEvents`, `SessionRole` | [CODE K/data/auth/] |
| `feature/<name>` | one package per feature: auth, calendar, cases, consultations (+ `detail/`), desk, digital_office, documents, finance, healthchecks/funnel, hearings, home, lead, more, notices, notifications, profile, projects, registry, scanner | [CODE K/feature/] |
| `push/` | `PushSetup`, `ClientFcmService` | [CODE K/push/] |
| `ui/theme` | `LexColors`/`Lex.colors`, `LegumLexTheme`, type, shapes, `ThemePrefs`, `Grain` | [CODE K/ui/theme/] |
| `ui/components` | `WizardFrame.kt` kit, `Mylex.kt` kit, timelines, buttons, badges, `SwipeRevealRow` | [CODE K/ui/components/] |
| `ui/navigation` | `Routes.kt`, `NavGraph.kt`, `AppHeader.kt`, `BottomNav.kt` | [CODE K/ui/navigation/] |
| `ui/brand`, `ui/illustrations` | `LegumMark`, `LegumWordmark`; Canvas art | [CODE K/ui/brand/, K/ui/illustrations/LegalIllustrations.kt] |
| `ui/` helpers | `CaseTitle.kt`, `JudgeFormat.kt` | [CODE K/ui/] |
| `ahc/` (root) | Agreement Check result UI kit with its **own** tokens (`ahc/theme/Tokens.kt` `LlColor`, `Type.kt`), models, components, previews, sample data | [CODE K/ahc/] |

Two token systems coexist: `ui/theme` (`Lex.colors`) and `ahc/theme` (`LlColor`, Inter). The second is also used outside AHC, by InvoiceDetail and Projects screens [CODE K/feature/finance/InvoiceDetailScreen.kt:427; K/feature/projects/ProjectDiscussionsScreen.kt:422].

## 3. Application and activity

**`LegumLexApp`** (Application) [CODE K/LegumLexApp.kt:9-20]:
- deletes the legacy `hearing_reminders` notification channel (hearing reminders now come from the firm by email and push);
- calls `PushSetup.init` (Beams, see integrations.md);
- calls `com.razorpay.Checkout.preload`.

**`MainActivity`** [CODE K/MainActivity.kt]:
- `installSplashScreen()`, `enableEdgeToEdge()`, then `ThemePrefs.load` before `setContent`.
- Theme from `ThemePrefs.choice` (LIGHT / DARK / SYSTEM; default LIGHT, owner 2026-10-24) [CODE K/MainActivity.kt:59-66; K/ui/theme/ThemePrefs.kt:13-27].
- Debug builds only: intent extra `debug_lead_token` saves a lead JWT so the lead shell can be opened with `adb am start` [CODE K/MainActivity.kt:50-57].
- `onNewIntent` hoists the new intent into Compose state so deep links are re-processed (activity is `singleTask`) [CODE K/MainActivity.kt:72-76].
- Stale KDoc: says "The app takes no payments: fees and invoices are paid on the website". Untrue since 23776d7 [CODE K/MainActivity.kt:34-37] [GIT 23776d7 2026-11-01].

**`AppShell`** (composable in MainActivity.kt) [CODE K/MainActivity.kt:80-212]:
- Deep links handled once per intent identity via `navController.handleDeepLink` [:90-97].
- Collects `SessionEvents.ended`: runs `AuthRepository.forget()` and navigates to Login with `popUpTo(0)`, unless already on Splash, Login or Register [:99-118].
- Unread-badge refresh (`NotificationHub.refresh`) at most once per 60 s on route change, and on every ON_RESUME; skipped for leads [:120-145].
- `ProfileHub.refresh` once a signed-in screen is reached [:146-153].
- Scaffold: `topBar = AppHeader(...)`, `bottomBar = LegumBottomBar(navController, lead)` [:157-181].
- Per-route status/nav-bar icon colours; Splash and Login drawn edge to edge; 28 dp bottom lift so content clears the raised centre circle; `Modifier.grain()` paper texture over page content [:183-210].

## 4. Navigation

- `ui/navigation/Routes.kt`: `sealed class Route(path)` with `ARG` constants and `build()` helpers [CODE K/ui/navigation/Routes.kt:17-203].
- `NavGraph.kt`: one `NavHost`, start destination Splash, slide+fade transitions [CODE K/ui/navigation/NavGraph.kt:71-87].

**Route inventory** [CODE K/ui/navigation/Routes.kt:17-203]:

| Group | Routes |
|---|---|
| Top level | splash, login, register (dead, see section 9), home, matters, consultations, more, registry, office, profile |
| Lead | lead, lead/enquiries, lead/more, lead/agreement-check, lead/open-account, lead/enquiry/{hash} |
| Litigation | matters/{caseId}, hearings/d/{hearingId}, calendar, matters/doc/{kind}/{id}?name= |
| Consultations | consultations/d/{id}, consultations/new?preset=&account= (the desk), consultations/d/{id}/thread, consultations/{id}/ahc-document, consultations/doc-file/{rid}?name=, consultations/{id}/video-lobby |
| Notices | notices, notices/d/{id}, notices/{id}/doc/{fileId}?name= |
| Money | invoices, invoices/d/{id} |
| Projects | projects, projects/{id}, projects/{id}/milestones, /files, /discussions, /discussions/{did}, /activity, tasks/{id}?canComment= |
| Other | vault, notifications, healthchecks/funnel, onboarding/ahc, registry/{agreementId}, digital_office/intake?advocate=, digital_office/landing?advocate= |

**Deep links** (`navDeepLink` in NavGraph): `legumlex://hearing/{hearingId}` [:409-410], `legumlex://consultation/{id}` [:431-432], `legumlex://project/{id}` [:619-620], `legumlex://invoice/{id}` [:736-737]. The manifest declares the four schemes on MainActivity [CODE AndroidManifest.xml:52-77].

**Chrome tiers** (what the shell draws per route) [CODE K/ui/navigation/Routes.kt:205-301]:

| Tier | Header | Bottom bar | Routes |
|---|---|---|---|
| normal | yes | yes | tabs and most detail screens |
| wizard | yes | no | healthchecks/funnel, vault, DO intake, lead AHC, lead onboarding |
| framedWizard | no (WizardFrame draws its own head) | no | funnel, DO intake, lead AHC, lead onboarding |
| immersive | no | no | document viewers, video lobby, onboarding/ahc, DO landing, consultations/new (the desk) |
| own header | Notifications draws its own header | | notifications |

The functions are `showAppHeader` / `showAppFooter` / `showHeaderBack` plus the immersive, wizard and framedWizard sets.

**Tab switching**: `navigateTab()` always pops to Home (or LeadHome) and opens the tab at its root; no tab state is saved or restored [CODE K/ui/navigation/NavGraph.kt:752-771]. This was a deliberate fix for "tabs always lead home" [SESSION 3adfe568 2026-10-20 (history: audit iterations 18573f3 to 9318c81)].

## 5. Header and bottom bar

**Header** `AppHeader` [CODE K/ui/navigation/AppHeader.kt:40-140]:
- Legum Lex mark with "Legum Lex / Client Portal" (or "Enquiries" for a lead);
- back arrow on non-root screens;
- Digital Office icon (`onOffice`, clients only);
- `AccountSwitch` (keyed by the profile email / signed-in contact; shown only with 2+ accounts) [CODE K/feature/profile/Accounts.kt:187-228] [GIT 6c069eb 2026-11-01];
- bell with unread count badge;
- initials or photo opening Profile.

Leads get no bell, profile, Digital Office or account switch [CODE K/MainActivity.kt:136-176].

**Bottom bar** `LegumBottomBar` [CODE K/ui/navigation/BottomNav.kt:31-116]:
- Black bar with a round centre notch; a raised yellow "Consult" circle with a blinking `>` cursor sits in the notch and opens the desk (`consultations/new`).
- Current tab shows its filled glyph with a dot; the owning tab is resolved from the back-stack hierarchy.
- Client tabs: **Home, Consultations | Consult | Notices, More** [CODE K/ui/navigation/Routes.kt:310-327]. Notices replaced Calendar on 2026-10-20; Digital Office moved to the header the same day [SESSION 3adfe568 2026-10-20T12:20, 13:45].
- Lead tabs: Home, Enquiries, More.

Owner instruction behind the layout: > "We don't need a consult card… Consult in the middle" [SESSION 3adfe568 2026-10-20T12:20].

## 6. Pattern: Repository / ViewModel / Screen

- Per feature: `*Models.kt` (data classes + parsing), `*Repository`, `*ViewModel`, `*Screen` [CODE K/feature/*].
- **Loose-map JSON.** `ApiService` returns `Map<String, Any?>` (or `ResponseBody` for streams); there are no DTOs: "intentionally loose" [CODE K/data/api/ApiService.kt:20-22]. Repositories hand-parse.
  - Moshi decodes JSON numbers as `Double`, so ids arrive as `23.0`; parsing uses `(v as? Number)` / `asInt()` helpers rather than `toString().toIntOrNull()` [SESSION 3adfe568 C19 (history_early §4)]. A bug where numeric ids were dropped by `asStr()` sent notification taps to the web; fixed with `asInt()` [SESSION 3adfe568 (history_late §4)].
  - Retrofit `Map` calls throw `HttpException` on 4xx, so error messages are read from `errorBody()`; `feature/consultations/ApiError.kt` parses `message`/`code` [CODE K/feature/consultations/ApiError.kt:22-41].
- Repositories wrap calls in `runCatching` and return `Result`.
- ViewModels expose `StateFlow` UiStates (Loading / Ready / Error, some with `denied`) and launch in `viewModelScope`.
- Screens collect with `collectAsState` in many places (not `collectAsStateWithLifecycle` everywhere) [CODE K/feature/profile/Accounts.kt:189; K/feature/finance/InvoiceDetailScreen.kt:135-136].
- Dashboard fan-out: `coroutineScope { async {...} }` in parallel, with `api.profile()` as the auth canary [CODE K/feature/home/HomeRepository.kt:49-75].
- Multi-step forms use the `WizardFrame` kit (owner 2026-10-24: "Every multi-step form uses it") [CODE K/ui/components/WizardFrame.kt:70-81,182-398] [MEMORY feedback_wizard_forms.md].

## 7. Hubs, singletons and event buses

| Object | Holds | Evidence |
|---|---|---|
| `ApiClient` | lazy `@Volatile` Retrofit/OkHttp instance | [CODE K/data/api/ApiClient.kt] |
| `SessionEvents` | `ended` SharedFlow (session over), `notice` StateFlow | [CODE K/data/auth/SessionEvents.kt] |
| `SessionRole` | lead flag/status, reviewNote, name, canRegister, accountDesk | [CODE K/data/auth/SessionRole.kt] |
| `NotificationHub` | unread count | [CODE K/feature/notifications/NotificationHub.kt] |
| `ProfileHub` | signed-in Profile | [CODE K/feature/profile/ProfileHub.kt] |
| `ActionHub` | action-queue bundle, set by `HomeViewModel` | [CODE K/feature/home/ActionHub.kt; K/feature/home/HomeViewModel.kt:141] |
| `PaymentEvents` | payment-outcome SharedFlow + `refreshed` StateFlow | [CODE K/feature/finance/Checkout.kt:119-134] |
| `ThemePrefs` | theme choice (SharedPreferences "appearance") | [CODE K/ui/theme/ThemePrefs.kt] |
| `PushSetup` | Beams start/interest/clear | [CODE K/push/PushSetup.kt] |
| `AhcSamples` | preview data only | [CODE K/ahc/SampleData.kt] |

Hubs are cleared by `AuthRepository.forget()` and `switchedTo()`; the account switch additionally restarts the activity to drop any other in-memory state [CODE K/data/auth/AuthRepository.kt:96-122; K/feature/profile/Accounts.kt:192-197].

## 8. Data flow and persistence

```
Screen --(collect StateFlow)--> ViewModel --suspend--> Repository --> ApiService (Retrofit)
                                                            |              |
                                                     parse Map<String,Any?>   OkHttp: X-Long-Call -> AuthInterceptor -> logging
                                                                              |
                                                          401 with current JWT -> SessionEvents.end() -> AppShell: forget() + Login
```

- Network: base `BuildConfig.API_BASE_URL` (customers_api v1), plus a few absolute `@Url` calls to the website (lexon funnel, cases landing). Details in integrations.md.
- Persistence [CODE K/data/auth/TokenStore.kt:13-21; K/ui/theme/ThemePrefs.kt:13-27; K/feature/desk/DeskData.kt:117-121]:
  - DataStore `"auth"`: jwt, client_id, contact_id, role, lead_status (plain text; see auth.md);
  - SharedPreferences `"appearance"` (`theme`) and `"desk"` (`device`, a per-install UUID for the guest allowance);
  - cacheDir: `docs/`, `ahc/`, `casedoc/`, `docreq/`, `noticedoc/`, `proj_*`, `projfile_*`, all wiped at sign-out and account switch [CODE K/data/auth/AuthRepository.kt:13,126-138].
- `allowBackup=false` [CODE AndroidManifest.xml:20].

## 9. Dead and leftover code at 6c069eb

| Item | State | Evidence |
|---|---|---|
| `Route.Register` | no composable; referenced only in visibility sets | [CODE K/ui/navigation/Routes.kt:21; K/MainActivity.kt:111] |
| `HomeScreen.ActionQueueBlock` | defined, no call site (queue lives in Notifications) | [CODE K/feature/home/HomeScreen.kt:403; K/ui/navigation/NavGraph.kt:55-67,251] |
| ApiService functions never called | `me`, `dashboardStats`, `dashboardProfile`, `nextHearing`, `recentActivity`, `activeServices`, `legalHealth`, `dashboardActiveMatters`, `registerDevice` | [CODE K/data/api/ApiService.kt:35,127-142,166,621] |
| Dependencies declared, unused | `security-crypto 1.1.0-alpha06`, `biometric 1.2.0-alpha05` (no `EncryptedSharedPreferences`, no `BiometricPrompt`) | [CODE app/build.gradle.kts; grep no hits] |
| Permissions declared, unused | READ_CALENDAR, WRITE_CALENDAR, USE_BIOMETRIC (no CalendarContract code) | [CODE AndroidManifest.xml] |
| Stale comments | MainActivity "takes no payments"; TokenStore "production should layer EncryptedSharedPreferences"; build.gradle.kts cloudflared comments | [CODE K/MainActivity.kt:34-37; K/data/auth/TokenStore.kt:11-12; app/build.gradle.kts:26-30,85-92] |
| Release network config | main `network_security_config.xml` (also used by release) allows cleartext to 10.0.2.2, localhost, lex.localhost | [CODE app/src/main/res/xml/network_security_config.xml] |
| `ahc/Previews.kt`, `ahc/SampleData.kt` | @Preview gallery and sample data, compiled into the app | [CODE K/ahc/] |

## Open questions

- Whether the second token system (`ahc/theme`) should be folded into `ui/theme`; no owner decision recorded [ASSUMPTION].
- Whether the unused calendar/biometric permissions draw Play review questions (not checked).
