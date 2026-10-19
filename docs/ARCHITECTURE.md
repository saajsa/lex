# Architecture — Legum Lex Android Client

> Developer reference for the native Android client of the Legum Lex legal practice.
> Last reviewed: 2026-05-20. Keep this file current when the structure changes.

---

## 1. What this app is

A native Android app for **clients** of Legum Lex (an Indian legal firm). It is a
read-and-light-write front end over **Perfex CRM**, talking exclusively to the
custom **`customers_api`** Perfex module over HTTPS + JWT.

The firm serves two distinct client populations, and the app surfaces both:

| Population | Centred on | App surfaces |
|---|---|---|
| **Litigation clients** | Court cases, hearings, courts | Matters tab, Hearings, Calendar |
| **Non-litigation / corporate clients** | Advisory engagements tracked as Perfex Projects | Projects tab, Tasks, Milestones |

Shared across both: Home dashboard, Consultations (incl. the Digital Chamber
walk-in feature), Invoices, the document Vault, and Notifications.

There is no local database. The server is the single source of truth; the app
holds only the JWT and a little identity (`client_id`, `contact_id`) in
DataStore. Screens fetch on entry and support pull-to-refresh.

---

## 2. Tech stack

| Layer | Choice | Version source |
|---|---|---|
| Language | Kotlin | `2.0.21` |
| UI | Jetpack Compose + Material 3 | compose-bom `2024.12.01` |
| Min / Target SDK | 28 (Android 9) / 35 (Android 15) | `app/build.gradle.kts` |
| Build | AGP `8.7.2`, Gradle wrapper | `gradle/libs.versions.toml` |
| Navigation | navigation-compose `2.8.5` | — |
| HTTP | Retrofit `2.11` + OkHttp `4.12` + Moshi `1.15` | — |
| Storage | DataStore Preferences (JWT + identity) | — |
| Auth | JWT bearer; Google Sign-In via Credential Manager | — |
| Images | Coil `2.7` | — |
| Fonts | Google Fonts downloadable: Space Grotesk, Inter, JetBrains Mono | — |
| Icons | Material Symbols (`material-icons-extended`) | — |
| Camera | CameraX `1.4.1` (document capture) | — |
| Splash | `androidx.core:core-splashscreen` (native Android 12 splash) | — |

All versions are pinned in `gradle/libs.versions.toml` (version catalog). Do not
let Android Studio bump AGP/Kotlin/Gradle without verifying a clean build first.

---

## 3. Package layout

Root package: `com.legumlex.client`

```
com/legumlex/client/
├── LegumLexApp.kt          Application subclass (app-wide init lives here)
├── MainActivity.kt         Single Activity. installSplashScreen() + Compose host + AppShell
│
├── data/
│   ├── api/
│   │   ├── ApiService.kt       Retrofit interface — every endpoint
│   │   ├── ApiClient.kt        Lazy singleton; builds OkHttp + Retrofit + Moshi
│   │   └── AuthInterceptor.kt  Attaches "Authorization: Bearer <jwt>"
│   └── auth/
│       ├── TokenStore.kt       DataStore-backed JWT / client_id / contact_id
│       └── AuthRepository.kt   Login / Google login / session bootstrap
│
├── ui/
│   ├── theme/         Color, Type, Shape, Spacing, Theme — the design system
│   ├── navigation/    Routes, BottomNav, NavGraph
│   ├── components/    Shared composables (LegumButton, LegumTextField, StatusBadge, EyebrowText)
│   └── illustrations/ LegalIllustrations.kt — Compose Canvas vector art (splash, onboarding)
│
└── feature/          One package per feature — see §4
    ├── auth/         Splash, Welcome, Login, Register (placeholder)
    ├── home/         Dashboard: action queue, next event, Digital Chamber
    ├── cases/        Matters list + detail (Overview / Timeline / Hearings)
    ├── hearings/     Hearing list + detail
    ├── calendar/     Hearing calendar
    ├── consultations/ Consultation list, detail (per-type), new-consultation flow, thread
    ├── projects/     Projects list + detail, Task detail (corporate clients)
    ├── finance/      Invoices list + invoice detail
    ├── documents/    Vault list + upload wizard
    └── more/         More menu (profile, sign out, links to Vault / Invoices)
```

---

## 4. The feature-module pattern

Every feature follows the same MVVM shape. Use `feature/cases/` or
`feature/projects/` as the canonical template when adding a feature.

```
feature/<name>/
├── <Name>Models.kt       Plain Kotlin data classes + enums for the domain
├── <Name>Repository.kt   Calls ApiService, parses loose maps into Models
├── <Name>ViewModel.kt    AndroidViewModel — exposes a StateFlow<UiState>
├── <Name>Screen.kt       Compose list/overview screen
└── <Name>DetailScreen.kt Compose detail screen (+ its own DetailViewModel)
```

### State management

- ViewModels extend **`AndroidViewModel`** (they need `Application` to build
  `ApiClient`). No DI framework — `ApiClient.get(app)` is a lazy singleton.
- Each screen has a sealed **`UiState`**: `Loading` / `Ready(data)` / `Error(message)`.
  See `HomeUiState` in `feature/home/HomeModels.kt`.
- State is exposed as `StateFlow`; the screen collects with
  `collectAsStateWithLifecycle()`.
- Pull-to-refresh uses a **separate** `isRefreshing: StateFlow<Boolean>` so the
  screen keeps showing stale content during a refresh instead of flicking to a
  skeleton. On a refresh failure, stale content is kept (no error screen). See
  `HomeViewModel.refresh()`.

### Repository conventions

- API responses are deliberately **loose** — `Map<String, Any?>`. There are no
  Moshi DTO classes. Each repository unwraps `data` and hand-parses fields with
  null-tolerant helpers (`toIntSafe`, `toDoubleSafe`).
- Date/time parsing is **format-tolerant**: each repository tries several
  patterns (`yyyy-MM-dd`, `dd-MM-yyyy`, …) and returns null on no match — never
  throws. See the parser block at the bottom of `HomeRepository.kt`.
- Dashboard-style screens fan out parallel calls with `async {}` inside a
  `coroutineScope`, with one call designated the "auth canary" (its failure
  surfaces `Error`; the rest degrade to null). See `HomeRepository.loadDashboard()`.

### Error mapping

ViewModels translate `retrofit2.HttpException` codes into human messages:
`401/403` → "session expired", `5xx` → "server error", else generic. See
`HomeViewModel.fetch()`.

---

## 5. Networking & auth

- **`ApiClient`** — `@Volatile` lazy singleton. Builds Moshi (with
  `KotlinJsonAdapterFactory`), an `HttpLoggingInterceptor` (BODY in debug, NONE
  in release), and Retrofit against `BuildConfig.API_BASE_URL`.
- **`AuthInterceptor`** — reads the JWT from `TokenStore` (via `runBlocking`) and
  sets `Authorization: Bearer <jwt>` on every request. Requests carrying the
  `X-Skip-Auth: 1` header (login, Google login, `google_config`) skip this and
  the header is stripped before the request goes out.
- **`TokenStore`** — DataStore Preferences (`name = "auth"`). Stores `jwt`,
  `client_id`, `contact_id`. `clear()` on sign-out.
  *Production hardening TODO:* wrap the token with `androidx.security.crypto`
  before persistence.
- **JWT audience** — Google Sign-In id_tokens are validated server-side against
  the Perfex option `lexon_gmail_oauth_client_id`. The app fetches the correct
  Web Client ID at runtime via `GET authentication/google_config` and falls back
  to `BuildConfig.GOOGLE_WEB_CLIENT_ID`. This is why dev and prod can use
  different OAuth clients without recompiling.

See `docs/API.md` for the full endpoint catalogue and the server module layout.

---

## 6. Navigation

Defined in `ui/navigation/`:

- **`Routes.kt`** — `sealed class Route(path)`. Nested/detail routes carry an
  `ARG` constant and a `build(id)` helper. `mainShellRoutes` is the set of
  destinations that show the bottom bar; `bottomTabs` is the ordered tab list.
- **Bottom nav order:** Home → Consultations → Matters → Projects → More.
  Vault is **not** a bottom tab — it is reached via the More menu.
- **`NavGraph.kt`** — single `NavHost`, `startDestination = Splash`. Defines
  app-wide transitions (horizontal slide + fade; forward slides in from the
  right, back reverses). Screens receive navigation as `onOpenX` lambdas — they
  never touch `NavController` directly.
- **`BottomNav.kt`** — `LegumBottomBar`. `AppShell` in `MainActivity` shows it
  only when the current route is in `mainShellRoutes`.

Auth flow (Splash → Welcome → Login) lives outside the shell and uses
`popUpTo(...) { inclusive = true }` so users can't navigate back into it.

---

## 7. Design system

Tokens translated from the Bizlink handoff into `ui/theme/`:

| File | Holds |
|---|---|
| `Color.kt` | Zinc neutral ramp, semantic colors, `StatusPalette` accents |
| `Type.kt` | Space Grotesk (display), Inter (body), JetBrains Mono (numerals) |
| `Shape.kt` | Corner radii |
| `Spacing.kt` | 8px-grid spacing scale |
| `Theme.kt` | `LegumLexTheme` composition |

- **Light theme only** in v1; dark theme deferred.
- **`AccentInk`** (`#18181B`, near-black) is the brand ink — buttons, splash bg.
- **`StatusPalette`** = `(solid, softBg, softText)` triplets. Use **only** for
  status badges, never as decoration:
  - `StatusOngoing` blue · `StatusSettled` green · `StatusPending` amber
  - `StatusClosed` gray · `StatusAdjourned` purple · `StatusDanger` red
- Shared UI primitives are in `ui/components/` — prefer `LegumButton`,
  `LegumTextField`, `StatusBadge`, `EyebrowText` over raw Material widgets.
- `ui/illustrations/LegalIllustrations.kt` holds Compose `Canvas` vector art
  (scales, case file, calendar, shield) used on splash and onboarding.

---

## 8. Feature inventory

| Feature | Screens | Notes |
|---|---|---|
| Auth | Splash, Welcome, Login, Register | Register is a placeholder; Google + email login work |
| Home | HomeScreen | Action queue, next event, Digital Chamber roster + 45-min session countdown |
| Cases (Matters) | List, Detail | Detail tabs: Overview / Timeline / Hearings |
| Hearings | List, Detail, Calendar | — |
| Consultations | List, Detail, New, Thread | Per-type detail renderers (AHC, DocReq, Digital, Converted, General) |
| Projects | List, Detail, Task detail | **Read-only v1**; corporate clients; tabs gated by per-project `settings` flags |
| Finance | Invoices list, Invoice detail | — |
| Documents | Vault, Upload wizard | CameraX capture |
| More | MoreScreen | Profile, sign out, links to Vault / Invoices |

---

## 9. Conventions & gotchas

- **No DI framework.** Singletons (`ApiClient`) and direct construction. Keep it
  that way unless the team decides otherwise.
- **No DTOs.** Responses are `Map<String, Any?>`; repositories hand-parse. When
  adding an endpoint, add a parser, not a Moshi class.
- **Use `Icons.AutoMirrored.*`** for any RTL-mirrored icon (back arrows, list,
  login) — the plain `Icons.Outlined.*` variants are deprecated.
- **`feature/cases/` is the reference implementation.** Copy its structure.
- **Two client populations** — when adding a feature, decide whether it is
  litigation, corporate, or shared, and place it in the right tab.
- See `docs/DEVELOPMENT.md` for build/run, the Cloudflare tunnel workflow,
  Google OAuth setup, release signing, and the server deploy process.
