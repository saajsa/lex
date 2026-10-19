# Legum Lex — Android Client App

Native Android app for clients of the Legum Lex legal practice. Built against the
`customers_api` Perfex module. Designed from the Bizlink design system handoff
(14-screen wireframes, light theme v1).

**Status:** Active development. Core features implemented (Home, Cases,
Hearings, Consultations, Projects, Invoices, Vault, auth incl. Google Sign-In).

**Developer docs:** see [`docs/`](docs/) — start with
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md). The notes below are the original
scaffold readme and may lag behind `docs/`.

---

## Stack

| Layer | Choice |
|---|---|
| Language | Kotlin 2.0 |
| UI | Jetpack Compose + Material 3 |
| Min SDK | 28 (Android 9) |
| Target SDK | 35 (Android 15) |
| Navigation | androidx.navigation:navigation-compose |
| HTTP | Retrofit + OkHttp + Moshi |
| Storage | DataStore Preferences (auth tokens) |
| Auth | JWT against `customers_api` |
| Fonts | Google Fonts downloadable (Space Grotesk, Inter, JetBrains Mono) |
| Icons | Material Symbols (via material-icons-extended) |
| Camera | CameraX (for KYC document capture, later) |
| Biometric | androidx.biometric (later) |

---

## Open in Android Studio

1. **File → Open** → select this directory (`legum-lex-android`).
2. Android Studio detects Gradle, prompts to download Gradle 8.10.2 and the
   wrapper jar — let it.
3. First sync downloads AGP 8.7.2, Kotlin 2.0.21, Compose BOM, plus all
   dependencies. Allow ~5 min on first run.
4. Once synced, run `app` on an emulator (Pixel 6, API 34+) or a connected device.

If the IDE asks about updating Gradle / AGP, hold the versions in
`gradle/libs.versions.toml` until you've confirmed the project builds — these
are tested-known-good versions.

---

## API base URL

Configured in `app/build.gradle.kts` per buildType:

| Build | URL | Why |
|---|---|---|
| `debug` | `http://10.0.2.2/customers_api/v1/` | Android emulator's alias for the host machine's localhost. Hits your devilbox Perfex instance directly. |
| `release` | `https://your-production-host/customers_api/v1/` | Set this before shipping. |

**Running on a real device?** Replace `10.0.2.2` with your dev machine's LAN IP
(e.g. `http://192.168.1.42/customers_api/v1/`) in the debug `buildConfigField`,
and ensure your phone is on the same Wi-Fi.

---

## Known backend issues (block real auth)

The `customers_api` module has two bugs that will prevent login from working
until they're fixed in the Perfex codebase:

1. **Token comparison bug** in `helpers/customers_api_helper.php:49` — compares
   stored JWT (no prefix) to the full `Authorization` header value. This app
   sends the raw JWT (no `Bearer ` prefix) as a workaround — see
   `AuthInterceptor.kt`.
2. **Routes file duplication bug** in `config/routes.php:17` — duplicates the
   controller segment for 2-segment routes. Hearings, Consultations, Documents,
   Notifications will 404 until explicit routes are added.

Both need server-side fixes before the app can talk to anything beyond
`/dashboard/*` and `/authentication`.

---

## Design system

The design tokens (color, typography, spacing, shape) are translated from
`design_handoff_client_app/design_system/colors_and_type.css` into:

| Token kind | File |
|---|---|
| Color | `app/src/main/java/com/legumlex/client/ui/theme/Color.kt` |
| Typography | `app/src/main/java/com/legumlex/client/ui/theme/Type.kt` |
| Shape | `app/src/main/java/com/legumlex/client/ui/theme/Shape.kt` |
| Spacing (8px grid) | `app/src/main/java/com/legumlex/client/ui/theme/Spacing.kt` |
| Theme composition | `app/src/main/java/com/legumlex/client/ui/theme/Theme.kt` |

The wireframe prototype is preserved at
`<perfex>/modules/client_app_prototype/` — open `index.html` in any browser
to compare visual intent against what's shipping.

---

## Project layout

```
app/src/main/
├── AndroidManifest.xml
├── java/com/legumlex/client/
│   ├── LegumLexApp.kt        ← Application subclass
│   ├── MainActivity.kt       ← Single Compose host
│   ├── ui/
│   │   ├── theme/            ← Color, Type, Shape, Spacing, Theme
│   │   ├── navigation/       ← Routes, BottomNav, NavGraph
│   │   └── components/       ← (shared composables — added as needed)
│   ├── data/
│   │   ├── api/              ← Retrofit ApiService, ApiClient, AuthInterceptor
│   │   └── auth/             ← TokenStore (DataStore)
│   └── feature/
│       ├── auth/             ← Login, Registration 1-4 (TBD)
│       ├── home/             ← HomeScreen (placeholder)
│       ├── cases/            ← CasesScreen (placeholder)
│       ├── hearings/         ← HearingsScreen (placeholder)
│       └── more/             ← MoreScreen (placeholder)
└── res/
    ├── values/               ← strings, colors, themes
    ├── xml/                  ← network security, backup rules
    ├── drawable/             ← ic_launcher_foreground (placeholder)
    └── mipmap-anydpi-v26/    ← adaptive icon manifest
```

---

## What's next

The handoff defines 14 screens. Build order, with the foundation in place:

1. **Auth** — Splash → Welcome carousel → Login (foundation for everything that talks to the API)
2. **Home dashboard** — most visible surface, sets the visual standard
3. **Cases** — list, then detail (3 sub-tabs: Overview / Timeline / Hearings)
4. **Hearings** — list + calendar + detail
5. **Notifications + Profile** — under More tab
6. **Onboarding (Registration steps 1–4)** — camera, OTP, password (do last; depends on KYC API)

Each screen ships as its own commit with the corresponding spec from
`HANDOFF.md` cited.

---

## Compliance reminders

Before launch:
- DPDP Act 2023 consent capture in onboarding
- UIDAI rules for Aadhaar handling (mask, never plaintext storage)
- Crashlytics + analytics opt-in, not opt-out
- Lock-screen notification redaction (per handoff UX rule 7)
- Certificate pinning for production API
- ProGuard rules for the model classes added later
