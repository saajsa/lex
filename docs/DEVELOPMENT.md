# Development Guide — Legum Lex Android

> How to build, run, and ship the app. Last reviewed: 2026-05-20.

---

## 1. Prerequisites

- Android Studio (latest stable) on Windows or macOS.
- The project is checked out at `B:\Android Studio\legum-lex-android` on the dev
  machine (accessible from WSL at `/mnt/b/Android Studio/legum-lex-android`).
- For end-to-end testing you also need the Perfex backend running locally
  (devilbox) — see §3.

---

## 2. Open & build

1. **File → Open** → select the `legum-lex-android` directory.
2. Let Gradle sync. First sync downloads AGP 8.7.2, Kotlin 2.0.21, the Compose
   BOM and all dependencies (~5 min).
3. Run the `app` configuration on an emulator (API 34+) or a connected device.
4. If Android Studio offers to upgrade Gradle/AGP/Kotlin — **decline** until a
   clean build is confirmed. Versions are pinned in `gradle/libs.versions.toml`.

`BuildConfig.API_BASE_URL` is baked in at compile time, so **any URL change
requires a Gradle sync + rebuild** of the debug variant.

---

## 3. Backend & the Cloudflare tunnel (dev)

The release build points at `https://www.legumlex.com`. The **debug** build
points at the local devilbox Perfex instance, exposed to the device through a
**Cloudflare quick tunnel**.

Start a tunnel:

```bash
~/bin/cloudflared tunnel --url http://localhost:80 --http-host-header lex.dvl.to
```

It prints a random URL like `https://<random-words>.trycloudflare.com`. Put that
into the **debug** `API_BASE_URL` in `app/build.gradle.kts`:

```kotlin
buildConfigField("String", "API_BASE_URL",
    "\"https://<random-words>.trycloudflare.com/customers_api/v1/\"")
```

then Gradle-sync and rebuild.

### Known pain point — quick tunnels die

A quick tunnel dies whenever its process stops, and **gets a new random
hostname every restart**. Symptom in the app: **HTTP 530** on sign-in
("origin unreachable"). Each restart forces another `build.gradle.kts` edit +
Gradle sync.

To restart: confirm no `cloudflared` is running (`pgrep -af cloudflared`),
relaunch the command above, grab the new URL, update `build.gradle.kts`, rebuild.

**Recommended fix:** set up a **named Cloudflare tunnel** (free Cloudflare
account, ~10 min one-time). A named tunnel keeps a *fixed* hostname across
restarts, so `API_BASE_URL` never has to change again. Strongly worth doing — it
ends the recurring 530 / re-sync cycle.

---

## 4. Build variants & config

`app/build.gradle.kts` → `buildTypes`:

| Field | `debug` | `release` |
|---|---|---|
| `API_BASE_URL` | Cloudflare tunnel → devilbox | `https://www.legumlex.com/customers_api/v1/` |
| `GOOGLE_WEB_CLIENT_ID` | shared OAuth Web Client ID | same |
| `isMinifyEnabled` | `false` | `true` (R8 + `proguard-rules.pro`) |

`GOOGLE_WEB_CLIENT_ID` is a compile-time fallback. At runtime the app prefers
the value from `GET authentication/google_config`, so the server controls which
OAuth client each environment validates against.

---

## 5. Google Sign-In / OAuth

- Sign-in uses **Credential Manager** + the `googleid` library.
- The id_token's `aud` is validated server-side against the Perfex option
  `lexon_gmail_oauth_client_id`.
- **Each signing keystore's SHA-1 must be registered** in the Google Cloud
  Console as an Android OAuth client, or sign-in fails on that build with
  "no Google accounts on the device".
  - Release keystore SHA-1 currently registered:
    `A1:07:5F:2D:B9:64:07:5B:D5:31:C7:74:1C:69:F2:09:54:39:BC:31`
  - The debug keystore's SHA-1 must also be registered for debug-build sign-in.
- Get a keystore's SHA-1:
  `keytool -list -v -keystore <path> -alias <alias>`

---

## 6. Release signing

Release builds are signed with a keystore kept at `B:\Android Studio\keystore`
(not in the repo). You need the store path, key alias, store password and key
password to assemble a release. Keep these out of version control.

---

## 7. Deploying backend changes

The `customers_api` module is a **separate nested git repo**
(`git@github.com:saajsa/api.git`) inside the Perfex install. Workflow:

1. Edit + commit inside `…/modules/customers_api/`.
2. **The maintainer pushes manually** — automated pushes are not used here.
3. Pull on production, then smoke-test the affected endpoints against
   `https://www.legumlex.com/customers_api/v1/`.

Perfex *core* changes (e.g. invoice HTML templates) live in the **main Perfex
repo**, not `customers_api`, and deploy separately.

---

## 8. Known gotchas

- **HTTP 530 on sign-in** → the Cloudflare tunnel is down. Restart it (§3).
- **Sign-in says "no Google accounts"** → the build's keystore SHA-1 is not
  registered in Google Cloud Console (§5).
- **"Invalid Google sign-in token"** → the OAuth client the app used does not
  match the server's `lexon_gmail_oauth_client_id`.
- **A new endpoint 404s** → it needs an explicit route in the server's
  `config/routes.php` above the catch-all (see `docs/API.md` §6).
- **Changing `API_BASE_URL` had no effect** → it is a `BuildConfig` constant;
  you must Gradle-sync and rebuild.

---

## 9. Roadmap / not yet done

- **Automated tests** — the project currently has no `test/` or `androidTest/`
  sources and no test dependencies. Planned but deferred. Good first targets:
  the repository date/JSON parsers (pure JVM logic, unit-testable without an
  emulator).
- **Encrypted token storage** — wrap the JWT via `androidx.security.crypto`
  before persisting in DataStore.
- **Projects/Tasks v2** — current Projects is read-only (no comments, creation,
  or uploads).
- **Compliance before launch** — DPDP Act 2023 consent capture, UIDAI Aadhaar
  masking, analytics opt-in, lock-screen notification redaction, certificate
  pinning for the production API.
