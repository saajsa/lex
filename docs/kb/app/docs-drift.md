# Client App Docs Drift

> Part of the Legum Lex Android knowledge base. Verified against: client app 6c069eb, staff app e52a8ca, customers_api 97de5b4, api 9df1506, cases cb0a820 (2026-11-07). Where the existing in-repo docs of the client app are now wrong, item by item. Those docs were not edited.

Label shorthand: `[CODE path:line]` means `[CODE lex@6c069eb path:line]`, and `K/` means `app/src/main/java/com/legumlex/client/`.

The in-repo docs are `README.md`, `docs/README.md`, `docs/ARCHITECTURE.md`, `docs/API.md`, `docs/DEVELOPMENT.md`, `docs/CHANGELOG.md` (plus `docs/Legum Lex - ClientStaff.zip`). All were committed in the first commit d0822cf (2026-10-19) and not touched since; their contents date from May and June 2026 ("Last reviewed: 2026-05-20"; CHANGELOG to 2026-06-21) [GIT d0822cf 2026-10-19] [DOC docs/*]. This knowledge base supersedes them.

## README.md

[DOC README.md] (it already says it "may lag")

| README says | Now | Evidence |
|---|---|---|
| Target SDK 35 | 36 (compileSdk 36 too) | [CODE app/build.gradle.kts:48,53] [GIT e23854e 2026-10-20] |
| Fonts: Google Fonts downloadable (Space Grotesk, Inter, JetBrains Mono) | bundled Barlow Condensed Bold, Inter, Fraunces under res/font | [CODE app/src/main/res/font/] |
| Debug URL `http://10.0.2.2/customers_api/v1/`; release "your-production-host" | debug from local.properties (now the localhost:8088 USB proxy); release hard-coded production | [CODE app/build.gradle.kts:21-37,101] |
| Known backend issue: app sends raw JWT (no `Bearer` prefix) | app sends `Authorization: Bearer <jwt>` | [CODE K/data/api/AuthInterceptor.kt:24-25] |
| routes.php bug; "Hearings… will 404" | obsolete; every app call has a route | [CODE customers_api@97de5b4 (backend_contract §3.3)] |
| Project layout with placeholder screens, "Registration 1-4 (TBD)" | obsolete; see architecture.md and features.md | [CODE K/feature/] |
| Designed from the Bizlink design system | Mylex / zinc language, own `LexColors`, plus the `ahc/theme` set | [CODE K/ui/theme/Color.kt] |
| CameraX for KYC; Biometric (later) | CameraX only for the Digital Office self-view; biometric declared, unused | [CODE K/feature/digital_office/SelfView.kt:12-14] |
| Gradle 8.10.2, AGP 8.7.2 | correct at HEAD, but the working tree has an uncommitted bump to 8.11.1 / 8.10.1 | [GIT `git diff` 2026-11-07] |

## docs/README.md

[DOC docs/README.md]
- Says DEVELOPMENT.md §3 "the tunnel" is required before the first debug run. The tunnel method is retired; the USB proxy is current (build-test-release.md section 7).
- "Keep these files current… in the same commit" was not followed after d0822cf.

## docs/ARCHITECTURE.md

[DOC docs/ARCHITECTURE.md] ("Last reviewed: 2026-05-20")

| Says | Now | Evidence |
|---|---|---|
| Target 35 | 36 | [CODE app/build.gradle.kts:53] |
| Fonts as README | Barlow Condensed, Inter, Fraunces | [CODE res/font] |
| Bottom nav Home → Consultations → Matters → Projects → More | client: Home, Consultations, (Consult centre circle), Notices, More; lead: Home, Enquiries, More; Digital Office in header | [CODE K/ui/navigation/Routes.kt:310-327; BottomNav.kt:31-116] |
| `mainShellRoutes` | replaced by `showAppHeader` / `showAppFooter` / `showHeaderBack` and immersive / wizard / framedWizard sets | [CODE K/ui/navigation/Routes.kt:205-301] |
| Auth flow Splash → Welcome → Login | no Welcome screen; `Route.Register` is dead | [CODE K/ui/navigation/NavGraph.kt:89-113; Routes.kt:21] |
| Light theme only in v1, dark deferred | dark exists; user picks Follow Phone / Light / Dark; Light default | [CODE K/ui/theme/ThemePrefs.kt:13-27] |
| Palette names `AccentInk`, `StatusOngoing`… | `LexColors` / `Lex.colors` with `StatusPalette` | [CODE K/ui/theme/Color.kt:1-60,129] |
| TokenStore stores jwt, client_id, contact_id | also `role`, `lead_status` | [CODE K/data/auth/TokenStore.kt:13-21] |
| Convention `collectAsStateWithLifecycle` | many screens use `collectAsState` | [CODE K/feature/profile/Accounts.kt:189; InvoiceDetailScreen.kt:135-136] |
| 401/403 → session expired, mapped in VMs | central 401 via `AuthInterceptor` → `SessionEvents`; 403 handled per screen, inconsistently | [CODE K/data/api/AuthInterceptor.kt:27] (auth.md section 4) |
| Feature inventory | omits desk, lead, notices, notifications screen, registry, profile/accounts, healthchecks funnel, digital_office tab, payment activities, push | [CODE K/feature/; K/push/] |
| Projects read-only v1 | comments, task creation, uploads, discussions | [GIT 18f17be 2026-10-25] |
| Home: Digital Chamber roster + 45-min countdown | chamber is its own "office" route from the header; action queue moved to Notifications | [CODE K/feature/digital_office/DigitalOfficeScreen.kt; K/ui/navigation/NavGraph.kt:55-67] |
| Package layout | omits `push/`, `ahc/`, `ui/brand`, `feature/desk|lead|notices|notifications|profile|registry|scanner|healthchecks|digital_office`, `data/auth/SessionEvents|SessionRole` | [CODE K/] |

## docs/API.md

[DOC docs/API.md] ("Last reviewed: 2026-05-20")
- Lists `dashboard/balance`: not in ApiService (the backend has it, unused) [CODE K/data/api/ApiService.kt].
- Describes `consultations/ahc/{id}` as "After-hearing-conference": it is the Agreement Check (agreement_review) [CODE K/feature/consultations/ConsultationModels.kt:18-25].
- Describes `mobile_devices/register` as FCM registration: declared but unused; push uses Beams interests `contact-{id}` [CODE K/push/PushSetup.kt:45-58].
- Missing endpoint groups: OTP, `accounts/*`, `profile` and `profile/phone/{step}`, `registry`, notices approve and document, notification read endpoints, `dashboard/workspaces` and `action_dismiss` / `action_restore`, all `ahc/*`, `invoices/{id}/pay*`, consultation write endpoints, `digital_office/*`, project write endpoints, `desk/*`, `guest/desk`, `lead/*`, the funnel `@Url` endpoints, `trackNotice` [CODE K/data/api/ApiService.kt].
- "401 / 403 → back to Login": only 401 does that [CODE K/data/api/AuthInterceptor.kt:27].
- Auth: the token key changed on the server on 2026-11-04 (customers_api e3027f0); API.md predates it [GIT e3027f0].

## docs/DEVELOPMENT.md

[DOC docs/DEVELOPMENT.md] ("Last reviewed: 2026-05-20")
- Paths `B:\Android Studio\legum-lex-android`: now `B:\and_studio\legum-lex-android` (the owner renamed the folder because the space broke tooling) [SESSION 3adfe568 C16].
- "Put the tunnel URL into the debug API_BASE_URL in app/build.gradle.kts": it comes from `local.properties`, and the current route is the USB proxy, not cloudflared [CODE app/build.gradle.kts:21-37] [MEMORY reference_phone_dev_over_usb.md].
- Build-variant table lacks `BEAMS_INSTANCE_ID`, `DEV_LOGIN_EMAIL`, `DEV_LOGIN_PASSWORD` [CODE app/build.gradle.kts:93-105].
- Release signing "keystore kept at B:\Android Studio\keystore": the build now reads `keystore.properties` (`storeFile` etc.) [CODE app/build.gradle.kts:15-17,65-72]; the keystore folder is probably `/mnt/b/and_studio/keystore` [ASSUMPTION: seen in a directory listing].
- Roadmap "Projects/Tasks v2, read-only": done and exceeded (writes exist).
- No mention of the batch-file / JBR build route, adb install, or phone testing (build-test-release.md sections 6-7).
- Contains a release SHA-1 fingerprint: not a secret, not copied.

## docs/CHANGELOG.md

[DOC docs/CHANGELOG.md]
- Latest entry 0.2.1 (2026-06-21); the app is 0.4.1 (8). Missing all of 0.3.x / 0.4.x and later: sign-in redesign and OTP, Mylex reskin and dark mode, Digital Office tab, desk, leads and guest desk, Notices tab, WizardFrame, case page rebuild, projects writes and stages, account switch, in-app payments [GIT d0822cf..6c069eb].
- Its 0.2.1 entry describes a client "Send-a-notice funnel (`NewNoticeScreen`)". No such screen exists; clients only view and approve notices; the API refuses creation [GIT 11e8a48, customers_api adac96d 2026-10-20].

## In-code staleness (comments, not docs)

| Where | Says | Now |
|---|---|---|
| `K/MainActivity.kt:34-37` KDoc | "The app takes no payments: fees and invoices are paid on the website" | in-app payments since 23776d7 |
| `K/data/auth/TokenStore.kt:11-12` | production should layer EncryptedSharedPreferences | never done; token unencrypted |
| `app/build.gradle.kts:26-30,85-92` | cloudflared quick-tunnel instructions | USB proxy |
| `HomeScreen.ActionQueueBlock`, `Route.Register` | present | unused |

## Memory notes that disagree with the code

- `feedback_app_payments_by_invoice.md`: "app takes no payments (SDK removed)". Code at 6c069eb pays in-app with the Razorpay SDK and PayU; the code (2026-11-01) is newer [GIT 23776d7, 8772b0d].
- `project_app_test_builds.md`: lists the test keystore as a leftover; compaction 38 says it was deleted [SESSION 3adfe568 compaction 38].
- `project_counsel_only_projects.md`: predates the 2026-11-03 reversal (any active staff as project members) [GIT cases c8c0813].

## Open questions

- Should the in-repo docs be replaced by pointers to this knowledge base, or rewritten? Owner decision; not done here by instruction.
- What is in `docs/Legum Lex - ClientStaff.zip` (2026-06-16)? Not opened.
