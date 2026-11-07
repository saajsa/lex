# Conventions

> Part of the Legum Lex Android knowledge base. Verified against: client app 6c069eb, staff app e52a8ca, customers_api 97de5b4, api 9df1506, cases cb0a820 (2026-11-07). The Kotlin, Compose, architecture, design, copy and wizard conventions both apps follow.
>
> This file is identical in legum-lex-android/docs/kb and legum-lex-staff/docs/kb.

`K/` = client `app/src/main/java/com/legumlex/client/`; `J/` = staff `app/src/main/java/com/legumlex/staff/`.

---

## 1. Toolchain (same in both apps)

| Item | Value at HEAD | Ref |
|---|---|---|
| Kotlin | 2.0.21, plugins kotlin-android + kotlin-compose | [CODE lex@6c069eb gradle/libs.versions.toml:3] [CODE lexco@e52a8ca gradle/libs.versions.toml:3] |
| Compose BOM | 2024.12.01; Material3, material-icons-extended, ui-text-google-fonts 1.7.6 | same files :4 |
| AGP / Gradle | 8.7.2 / 8.10.2 at HEAD. **Uncommitted** working-tree bumps to 8.10.1 / 8.11.1 belong to the owner (rule G4 in [owner-rules.md](owner-rules.md)) | [GIT status 2026-11-07] |
| compileSdk / minSdk / targetSdk | 36 / 28 / 36 (Play required 36 on 2026-10-20) | [CODE lex@6c069eb app/build.gradle.kts:48-53] [CODE lexco@e52a8ca app/build.gradle.kts:37-42] [GIT e23854e 2026-10-20] |
| JVM target | 17 | both app/build.gradle.kts |
| Libraries | Retrofit 2.11.0 + converter-moshi, OkHttp 4.12.0 + logging, Moshi 1.15.1 (+kotlin reflect), Coil 2.7.0, navigation-compose 2.8.5, lifecycle 2.8.7, DataStore Preferences 1.1.1, coroutines 1.9.0, Credential Manager 1.3.0 + googleid 1.1.1, ML Kit document scanner 16.0.0-beta1, Firebase BOM 33.7.0 + messaging, Pusher Beams 1.10.0 | [CODE both app/build.gradle.kts, libs.versions.toml] |
| Build types | `debug` and `release` only. No flavors. A `preview`/"Test" type was added and removed on 2026-10-30; do not reintroduce it | [GIT 8da8bbe, c249409 2026-10-30] |
| Secrets | Read from `local.properties` (debug `API_BASE_URL`, `BEAMS_INSTANCE_ID`, dev sign-in fields) and `keystore.properties` (release signing). Never open or print these files. Release hard-codes the production base URL and blanks the dev fields | [CODE both app/build.gradle.kts] |

## 2. Architecture

- **Single activity, Compose only, no DI framework.** Singletons (`object`) and direct construction. ViewModels are mostly `AndroidViewModel`s that build repositories with `ApiClient.get(app)` [CODE lex@6c069eb K/feature/notifications/NotificationHub.kt:7] [CODE lexco@e52a8ca J/data/api/ApiClient.kt:14-54].
- **Per feature: Models / Repository / ViewModel / Screen** under `feature/<name>/`.
- **No DTOs.** Retrofit returns `Map<String, Any?>` ("intentionally loose"); repositories hand-parse and return `Result<T>` (via `runCatching`). Staff has shared helpers `apiOk, dataObject, dataList, asObject, asObjectList, asInt, asStr, asStrOrNull, asBool, apiMessage` [CODE lexco@e52a8ca J/data/api/ApiParsing.kt:11-59]; the client parses per repository [CODE lex@6c069eb K/data/api/ApiService.kt:20-22].
- **Moshi decodes every JSON number as `Double`.** Never `toString().toIntOrNull()` an id ("23.0" fails). Use `(v as? Number)?.toInt()` or the staff `asInt()` [SESSION 3adfe568 C19] [CODE lexco@e52a8ca J/data/auth/AuthRepository.kt:99-101]. A real bug from this: notification ids dropped by `asStr()` sent taps to the web until switched to `asInt()` [SESSION 3adfe568 2026-10-26T19:57].
- **Retrofit `Map` calls throw `HttpException` on 4xx**: read `errorBody()` to get the server's `message` / `code` [SESSION 3adfe568 C19].
- **UI state** is a `StateFlow` of a sealed Loading / Ready / Error (staff `UiState<T>` [CODE lexco@e52a8ca J/data/UiState.kt:4]). Many screens use `collectAsState` rather than `collectAsStateWithLifecycle` [CODE lex@6c069eb e.g. feature/profile/Accounts.kt:189].
- **OkHttp interceptor order** (both): (1) `X-Long-Call: 1` raises read/write timeouts to 120 s and strips the header; (2) `AuthInterceptor` (skips when `X-Skip-Auth: 1`, strips it); (3) logging BODY in debug, NONE in release. Connect 20 s, read 30 s. No pinning, retry or cache [CODE lex@6c069eb K/data/api/ApiClient.kt:32-49] [CODE lexco@e52a8ca J/data/api/ApiClient.kt].
- **Tokens** live in DataStore Preferences file `auth`, in plain text [CODE lex@6c069eb K/data/auth/TokenStore.kt:13-52] [CODE lexco@e52a8ca J/data/auth/TokenStore.kt:90-108]. The interceptor reads them with `runBlocking`.
- **Theme choice** is SharedPreferences `appearance` / key `theme`, values SYSTEM / LIGHT / DARK, default LIGHT, loaded before `setContent` [CODE both ui/theme/ThemePrefs.kt].
- **Compose colour tokens are read at composition time.** Client: `Lex.colors` is `@Composable`, so helpers that use it must be `@Composable` and colours must be hoisted out of `DrawScope` [SESSION 3adfe568 C17]. Staff: tokens in `Color.kt` are getters over `EdgeTheme.dark`; never cache one in a top-level `val` or the theme freezes [CODE lexco@e52a8ca J/ui/theme/Color.kt:12-20] [SESSION 3adfe568 C23].
- **Declare properties used by `init` before the `init` block** (DeskViewModel crash) [SESSION 3adfe568 C28].
- **Infinite transitions** can render invisible; a blinking cursor uses a `LaunchedEffect` toggle (530 ms) [SESSION 3adfe568 C21].
- **Sign-out / account switch restart `MainActivity`** with `NEW_TASK|CLEAR_TASK` so no ViewModel survives (staff sign-out, client account switch) [CODE lexco@e52a8ca J/ui/navigation/SignOut.kt:14-18] [CODE lex@6c069eb K/feature/profile/Accounts.kt:192-197].
- **Push** is Pusher Beams over FCM in both. Each app removes Beams' `EmptyMessagingService` and posts every notification itself from its own `MessagingService` subclass, so the logo and deep link are always applied [CODE lex@6c069eb AndroidManifest.xml:82-105] [CODE lexco@e52a8ca AndroidManifest.xml:35-47]. Interests: client `contact-{id}`, staff `staff-{id}`.
- **File viewing** downloads into `cacheDir/<folder>` and renders PDFs with the platform `PdfRenderer`; cache folders are wiped at sign-out (client) [CODE lex@6c069eb K/data/auth/AuthRepository.kt:126-138].
- **Navigation**: one `NavHost` from `splash`, slide+fade transitions; screens receive lambdas, not the NavController (staff) [CODE lexco@e52a8ca J/ui/navigation/NavGraph.kt:79-86].
- **Tests**: the client has none; the staff app has one unit test, `TextCaseTest`, fed by a JSON fixture shared with the web [CODE lexco@e52a8ca app/src/test/java/com/legumlex/staff/ui/TextCaseTest.kt].

## 3. Build and run

- Built on Windows from WSL through batch files that set `JAVA_HOME` to Android Studio's JBR and call `gradlew.bat`: `/mnt/b/and_studio/_build/client.bat` (`assembleDebug`), `client-release.bat` (`bundleRelease`), and the staff `lexstaff.bat` [CODE /mnt/b/and_studio/_build/*.bat] [MEMORY project_staff_app.md].
- Install with Windows `adb.exe` to the owner's phone, `install -r`. Debug builds reach dev at `http://localhost:8088/{customers_api/v1,api}/` through `adb reverse tcp:8088 tcp:8088` and `node /mnt/b/and_studio/usbproxy.mjs`; re-run `adb reverse` after each replug [MEMORY reference_phone_dev_over_usb.md]. The older cloudflared quick-tunnel method still described in build-file comments is obsolete.
- Debug network-security config permits cleartext to `localhost`, `10.0.2.2`, `lex.localhost` (client debug also `payubiz.in` for the PayU sandbox) [CODE lex@6c069eb app/src/debug/res/xml/network_security_config.xml].
- The agent never builds release (rule T9).

## 4. Design systems

| | Client app | Staff app |
|---|---|---|
| Name | Mylex language (the client web portal), warm-grey variant | "Edge" (from the owner's reference image), later yellow/ink |
| Palette | `LexColors` zinc/warm grey light and dark; accent black on light, white on dark; colour only for status (muted red overdue, green done) [CODE lex@6c069eb K/ui/theme/Color.kt:1-60] | White / #0E0E0E page, ink #111111, solid status pairs (green #1F7A45, red #B3261E with white), yellow `#FFC21A` "Sun" with ink [CODE lexco@e52a8ca J/ui/theme/Color.kt:23-88; J/feature/caseboard/Folder.kt:42-51] |
| Accent | Consult centre button `#F5C518` with black type | Home tab `#FFC21A` with ink glyph |
| Texture | `Modifier.grain()` paper speck over page content; chrome solid | Flat; `Modifier.pressable` settle-in press, no ripple |
| Type | Barlow Condensed Bold (titles), Inter, Fraunces (Digital Office) | Manrope static cuts (families still named `SpaceGrotesk`/`Inter` in code), mono for figures |
| Radii | 4 / 6 / 8 / 12 (Mylex) [CODE lex@6c069eb K/ui/theme/Shape.kt] | Larger curves on cards; sign-in and splash "Frame" square, no radius |
| Second token set | `ahc/theme` (`LlColor`, Inter) used by Agreement Check, invoice and project screens [CODE lex@6c069eb InvoiceDetailScreen.kt:427] | none |
| Text rule | Flat, few colours | Text only on solid pairs: black on yellow, white on black, ink on white/grey (rule F12) |
| Default theme | Light | Light |

Do not cross the systems: a staff layout ported to the client is redrawn with the client kit (rule F11) [MEMORY feedback_client_app_radii.md].

## 5. Wizard rules

- Every multi-field form is a wizard: one decision per step, visible progress, back without losing input, per-step validation, a Review step before submit [MEMORY feedback_wizard_forms.md].
- Both apps have a `WizardFrame` kit: client `K/ui/components/WizardFrame.kt` (WizardFrame, WizardHeading, WizardLabel, WizardDropdown, WizardChoice, WizardTextArea, WizardReview, WizardRows, WizardTextField, WizardTick, WizardDialog; fields 8 dp, cards 12 dp) [CODE lex@6c069eb K/ui/components/WizardFrame.kt:70-81,182-398]; staff `J/ui/components/WizardFrame.kt` [CODE lexco@e52a8ca WizardFrame.kt:62-76]. The client kit was taken from the staff kit and redrawn in client language [GIT ad85d82 2026-10-24].
- Choices are dropdowns (`WizardDropdown`), not lists of option rows. Free text stays free where the web has it free (e.g. adjournment reason) [MEMORY feedback_wizard_forms.md].
- The server stays authoritative for validation: a wizard must not enforce a rule the backend does not have [MEMORY feedback_wizard_forms.md].
- Known exceptions still on their own frames: staff Update Hearing and advocate wizards [SESSION 3adfe568 audit ledger 2026-10-28].
- Desk onboarding by conversation counts as a wizard; it offers "Or Use The Form" [SESSION 3adfe568 C26-C28].

## 6. Copy rules

Apply to every user-visible string in both apps (rules F4-F10 in [owner-rules.md](owner-rules.md)):
- No em dashes. Use colons, commas or parentheses.
- "Platform", never "software".
- "Advocate" for litigation only; "counsel" otherwise ("Counsel On Record").
- Title Case for labels, buttons, chips, tabs, empty-state headings; sentence case for help text.
- A descriptor names its own subject ("No Hearings Scheduled").
- Formal register. Validation messages are professional, not chatty.
- Never "certified copy": "Order", "Open Order".
- Never tell the client where a payment is processed (no "pay on the website").
- Client messages are information, never advice.
- Display casing (staff): names in Title Case and descriptions in sentence case at display time only, through `TextCase` [CODE lexco@e52a8ca J/ui/TextCase.kt:21-155]. The client app has no TextCase yet.

## Open questions

- Should the client app adopt `TextCase` (shared fixture) as the staff app has? Not decided [SESSION 3adfe568 2026-10-27T19:04].
- Should the client's second token set (`ahc/theme`) be merged into `ui/theme`? Not raised with the owner.
