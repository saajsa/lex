# Client App Build, Test and Release

> Part of the Legum Lex Android knowledge base. Verified against: client app 6c069eb, staff app e52a8ca, customers_api 97de5b4, api 9df1506, cases cb0a820 (2026-11-07). How the client app is built, configured, tested on a phone and released. No secret values appear here; only property names.

Label shorthand: `[CODE path:line]` means `[CODE lex@6c069eb path:line]`.

## 1. Toolchain and versions

| Item | Value at HEAD | Evidence |
|---|---|---|
| AGP | 8.7.2 (uncommitted bump to 8.10.1) | [CODE gradle/libs.versions.toml:2] [GIT `git diff` 2026-11-07] |
| Gradle wrapper | 8.10.2 (uncommitted bump to 8.11.1) | [CODE gradle/wrapper/gradle-wrapper.properties:3] |
| Kotlin | 2.0.21 (kotlin-android + kotlin-compose plugins) | [CODE gradle/libs.versions.toml:3] |
| Compose BOM | 2024.12.01 | [CODE gradle/libs.versions.toml:4] |
| compileSdk / minSdk / targetSdk | 36 / 28 / 36 | [CODE app/build.gradle.kts:48,52,53] |
| versionCode / versionName | 8 / "0.4.1" | [CODE app/build.gradle.kts:54-55] [GIT 08ef6ab 2026-10-23] |
| JVM target | 17 | [CODE app/build.gradle.kts:125-129] |
| namespace / applicationId | com.legumlex.client | [CODE app/build.gradle.kts:47,51] |
| Gradle properties | parallel, caching, configuration cache; nonTransitiveRClass | [CODE gradle.properties] |
| Project | rootProject "Legum Lex", single module `:app` | [CODE settings.gradle.kts] |

Key libraries: Compose material3, navigation-compose 2.8.5, lifecycle 2.8.7, DataStore 1.1.1, Retrofit 2.11.0 + Moshi 1.15.1, OkHttp 4.12.0, Coil 2.7.0, Credential Manager 1.3.0, CameraX 1.4.1, ML Kit document scanner 16.0.0-beta1, Razorpay checkout 1.6.40 (literal, `standard-core` forced to 1.7.14), Firebase BOM 33.7.0 messaging, Pusher Beams 1.10.0. Declared but unused: security-crypto, biometric [CODE app/build.gradle.kts:143-208].

**Uncommitted working tree (2026-11-07):** `gradle/libs.versions.toml` (AGP 8.7.2 → 8.10.1) and `gradle/wrapper/gradle-wrapper.properties` (Gradle 8.10.2 → 8.11.1). Made 2026-10-27 and left uncommitted on purpose: they wait for the owner's own release build to pass [SESSION 3adfe568 2026-10-27T02:58 (history_late §1, §5)] [GIT status 2026-11-07]. Owner: > "I will build it manually." [SESSION 3adfe568 2026-10-27T02:58]. Whether it builds cleanly is not verified.

## 2. Build types and BuildConfig

Only `debug` and `release` exist [CODE app/build.gradle.kts:84-122].

| | debug | release |
|---|---|---|
| Minify (R8) | off | on: `proguard-android-optimize.txt` + `proguard-rules.pro` |
| Native symbols | defaultConfig FULL | `ndk.debugSymbolLevel = "SYMBOL_TABLE"` (Play requirement) [GIT b306a48 2026-10-20] |
| `API_BASE_URL` | from local.properties (`.invalid` fallback) | production customers_api v1, hard-coded |
| `DEV_LOGIN_EMAIL` / `DEV_LOGIN_PASSWORD` | from local.properties | blank |
| `GOOGLE_WEB_CLIENT_ID`, `BEAMS_INSTANCE_ID` | build config / local.properties | build config |
| Signing | Android debug keystore | `signingConfigs.findByName("release")`; null if keystore.properties is missing, so the bundle comes out unsigned (loud failure) |

BuildConfig field names (both types): `API_BASE_URL`, `GOOGLE_WEB_CLIENT_ID`, `BEAMS_INSTANCE_ID`, `DEV_LOGIN_EMAIL`, `DEV_LOGIN_PASSWORD` [CODE app/build.gradle.kts:93-105].

Properties read (files not opened for this KB):
- `local.properties`: `BEAMS_INSTANCE_ID`, `DEV_LOGIN_EMAIL`, `DEV_LOGIN_PASSWORD`, `API_BASE_URL` [CODE app/build.gradle.kts:21-37];
- `keystore.properties`: `storeFile`, `storePassword`, `keyAlias`, `keyPassword`; a `keystore.properties.sample` is committed [CODE app/build.gradle.kts:15-17,65-72];
- `app/google-services.json`: the google-services plugin is applied only if it exists [CODE app/build.gradle.kts:41-44]. It is git-ignored [GIT d0822cf .gitignore].

**Removed Test build channel.** On 2026-10-30 a self-updating "Test" channel was built for both apps (`preview` build type because AGP reserves `test`, `.test` package, an `api/app_update` store, migration 264), then reverted the same morning, then the minimal USB "Test" copy and its keystore lines were removed too [GIT client 053c482, 45f591e, efe89b9, 8da8bbe 2026-10-30] [SESSION 3adfe568 2026-10-30T08:39, 09:54-09:55]. Owner: > "Remove it from both apps and push" [SESSION 3adfe568 2026-10-30T09:55]. Do not reintroduce it unasked [MEMORY project_app_test_builds.md]. (That memory note still calls the test keystore a leftover; compaction 38, the newer source, says it was deleted.)

## 3. R8 / ProGuard

[CODE app/proguard-rules.pro]
- Retrofit: keep Signature, annotations, Continuation / Call / Response.
- Moshi: `@com.squareup.moshi.*` members and JsonQualifier (Kotlin reflection adapter is used).
- OkHttp / conscrypt: dontwarn.
- `@JavascriptInterface` members kept (WebViews).
- Razorpay: `-keep class com.razorpay.** { *; }`, `-optimizations !method/inlining/*`, keep `onPayment*` methods.

## 4. Network security config

- main (also ships in release): cleartext only to 10.0.2.2, localhost, lex.localhost [CODE app/src/main/res/xml/network_security_config.xml]. Leftover but harmless.
- debug override `app/src/debug/res/xml/network_security_config.xml`: adds **payubiz.in**, because the PayU test sandbox returns over http [CODE app/src/debug/res/xml/network_security_config.xml] [SESSION 3adfe568 (history_late §4)]. This is the only file under `src/debug`.

## 5. Signing: debug key vs Play key

- Release signing belongs to the owner. `keystore.properties` is absent from the agent's environment; the agent edits versions, the owner builds and signs [SESSION 3adfe568 C21, C28] [SESSION 3adfe568 2026-10-20T18:49: "do not build it. Ill build it manually. you just fix it"].
- Debug builds are signed with the local debug keystore; Play-distributed builds carry Play's signing certificate [ASSUMPTION: Play App Signing is in use, implied by "Play app-signing SHA-1" in the session record]. Consequences:
  - a debug APK cannot be installed over the Play build (signature mismatch); the Play build must be uninstalled first, and the owner's approval is needed because the phone normally runs the production Play app [SESSION 3adfe568 C28] [SESSION 3adfe568 2026-10-30T06:14: "The app on mobile is prod app not debug."];
  - Google sign-in needs an Android OAuth client registered for each certificate's SHA-1 (debug keystore and Play app-signing key); missing registration shows "not registered to use OAuth2.0" / NoCredentialException [SESSION 3adfe568 C22] [MEMORY project_leads_in_app.md].
- `docs/DEVELOPMENT.md` contains a release SHA-1 fingerprint; not copied here.

## 6. Windows build route (from WSL)

The repo is on the Windows drive (`B:\and_studio\legum-lex-android` = `/mnt/b/and_studio/legum-lex-android`). Builds run on Windows with Android Studio's JBR [CODE /mnt/b/and_studio/_build/*.bat (outside repo)]:

| Script | Does |
|---|---|
| `/mnt/b/and_studio/_build/client.bat` | `JAVA_HOME=C:\Program Files\Android\Android Studio\jbr`, `cd /d B:\and_studio\legum-lex-android`, `gradlew.bat assembleDebug --console=plain -q` |
| `/mnt/b/and_studio/_build/client-release.bat` | same, `bundleRelease` (owner's job) |
| `lexgw.bat` (older) | `%USERPROFILE%\AppData\Local\Temp\lexgw.bat`: JBR `JAVA_HOME` + `gradlew.bat %*`, run as `timeout 595 cmd.exe /c '…lexgw.bat' assembleDebug -q`, with a scratch `kc.sh` compile helper [SESSION 3adfe568 2026-10-19T11:46 (C17)]. Not found on the mounted drives on 11-07 [ASSUMPTION: still in Windows %TEMP%] |

History: the repo arrived without a Gradle wrapper; one was generated and copied in; `cmd.exe /c` quoting failed, hence the batch files [SESSION 3adfe568 C17, C28].

## 7. Phone testing over USB

- C: is nearly full, so emulators fail; the owner's phone is the test device [MEMORY project_android_client_app.md] [SESSION 3adfe568 C17-C18]. Owner: > "use the phone, uninstall it" [SESSION 3adfe568 2026-10-19T09:52].
- Install: `adb.exe` at `/mnt/c/Users/<windows-user>/AppData/Local/Android/Sdk/platform-tools/`, `-s <serial> install -r …/app-debug.apk` [SESSION 3adfe568 2026-10-19T11:46].
- USB dev loop (since 2026-11-01; cloudflared quick tunnels died or were blocked) [MEMORY reference_phone_dev_over_usb.md] [SESSION 3adfe568 2026-11-01T09:48]:
  1. `node /mnt/b/and_studio/usbproxy.mjs`: listens on 127.0.0.1:8088, forwards to devilbox with Host `lex.dvl.to` and `x-forwarded-proto: https`, rewrites `https://lex.dvl.to/` to `http://localhost:8088/` in text bodies and Location headers [CODE /mnt/b/and_studio/usbproxy.mjs];
  2. `adb reverse tcp:8088 tcp:8088` after every replug (adb drops it);
  3. debug `API_BASE_URL=http://localhost:8088/customers_api/v1/` in local.properties (cleartext to localhost is allowed).
- The build.gradle.kts comments still describe the cloudflared method [CODE app/build.gradle.kts:26-30,85-92].
- Test sign-in: the debug "Sign In as Test Contact" button with a dedicated dev contact (password reset on 2026-11-01) [SESSION 3adfe568 2026-11-01T09:48].
- Device-loop gotchas: uiautomator dumps lag, adb drops empty `""` extras, taps land on stale positions; trust screenshots and tap from uiautomator bounds [SESSION 3adfe568 (history_late §4)].
- Standing rule: screenshot every changed screen in every state (and both themes) before finalising [MEMORY feedback_ui_inspection_loop.md].

## 8. Tests

- **None.** `app/src` has only `main/` and `debug/`; no `test/` or `androidTest/`, no test dependencies [CODE app/src listing; app/build.gradle.kts]. (The staff app has a `TextCaseTest`; the client app has nothing equivalent.)
- Server-side selftests exist in cases (e.g. `mobile_handoff`), but they run in-process and do not exercise the app [GIT cases f74b869] (web_impact §1.A notes they can pass where real HTTP fails).

## 9. Release and Play status (as known)

| Date | Event | Evidence |
|---|---|---|
| 2026-10-20 15:05 | Owner: > "push and prepare for closed test release"; 0.3.0 (5) pushed, release AAB built unsigned, owner signs | [SESSION 3adfe568 2026-10-20T15:05] [GIT 0020428] |
| 2026-10-20 18:41-18:57 | 0.4.0 (6); Play required target API 36 (e23854e) and native debug symbols (b306a48); versionCode 6 already used on Play → 7 (f2a5adf) | [GIT 631df7f, e23854e, b306a48, f2a5adf] |
| 2026-10-20 19:21 | Owner asked "Bump it up to 0.7.1 … internal testing"; the record shows 0.4.1 code 8 instead (uncommitted, committed 10-23 as 08ef6ab). Whether the owner accepted 0.4.1 is not confirmed | [SESSION 3adfe568 2026-10-20T19:21] [GIT 08ef6ab] |
| 2026-10-20 19:32 | Published app showed "network error" on Google and OTP sign-in: GoDaddy site-wide JS bot challenge on production; needs a hosting fix | [SESSION 3adfe568 C22] **[Owner decision 2026-11-07, REQ-002]** Both Android apps already communicate successfully with the production backend. The bot-challenge problem is no longer blocking. |
| 2026-10-24 | "Why are my releases running old?": desk onboarding switch `lexon_desk_onboarding` off on prod, not an app issue | [SESSION 3adfe568 2026-10-24T11:36] **[Owner decision 2026-11-07, REQ-002]** `lexon_desk_onboarding` is **active on production and under testing**; do not treat it as unused. Dev value is 0. |
| 2026-11-07 | Whether a Play build with the 10-25 to 11-01 work (projects writes, account switch, in-app payments) has shipped is **unknown** | [SESSION 3adfe568 (history_late §6 item 4)] **[Owner decision 2026-11-07, REQ-002]** Both Android apps already communicate successfully with the production backend. (Which build is on the Play store is not recorded here.) |

- `app/release/` holds `app-release.aab`, `app-release.apk`, baselineProfiles and `output-metadata.json` from local builds; the folder is git-ignored (`/app/release` in `.gitignore`), so these are untracked [GIT `git check-ignore` 2026-11-07].
- Repo visibility: the owner said "Make it private" for saajsa/lex on 10-30; whether done is unverified [SESSION 3adfe568 (history_late §6 item 3)].

## Open questions

- Does the AGP 8.10.1 / Gradle 8.11.1 bump build, and will the owner commit it?
- Current Play track and version for com.legumlex.client (closed / internal test, 0.4.1 code 8 or later).
- Were the GoDaddy bot-challenge exemption and the Android OAuth SHA-1 registrations done?
- Whether the saajsa/lex repo was made private.
