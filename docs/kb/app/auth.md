# Client App Authentication

> Part of the Legum Lex Android knowledge base. Verified against: client app 6c069eb, staff app e52a8ca, customers_api 97de5b4, api 9df1506, cases cb0a820 (2026-11-07). How people sign in to the client app, how the token is stored and sent, how sessions end, and how leads and account switching work.

Label shorthand: `[CODE path:line]` means `[CODE lex@6c069eb path:line]`, and `K/` means `app/src/main/java/com/legumlex/client/`. Server facts carry their own repo label.

## 1. Sign-in methods

| Method | Client call | Server endpoint | Notes | Evidence |
|---|---|---|---|---|
| Google | Credential Manager `GetGoogleIdOption` (filterByAuthorized=false, autoSelect=false) | `POST authentication/google` with `id_token`, `allow_lead=1` | Server client id from `googleConfig()["client_id"]`, fallback `BuildConfig.GOOGLE_WEB_CLIENT_ID`. With allow_lead an unknown person gets a lead token | [CODE K/feature/auth/LoginScreen.kt:160-163,743-786; K/data/api/ApiService.kt:38-45] [CODE customers_api@97de5b4 (backend_contract §1.2)] |
| Mobile code | `otpRequest(number)` → `{ref,to,message,resend_after}`; `otpStatus(ref)` polled every 5 s with a 1 s resend countdown; `otpSms(ref)` "Send by SMS instead"; `otpVerify(ref, code)` | `POST authentication/otp/{request,status,sms,verify}` | WhatsApp first, SMS fallback (MSG91 via messaging). 403 `error=unverified` when the number is not a verified one | [CODE K/feature/auth/LoginViewModel.kt:142-164; K/data/auth/AuthRepository.kt:36-61] [GIT customers_api c6b17bb 2026-10-18] |
| Email + password | `login(email, password)` | `POST authentication` | Shown as a secondary link. Google sign-in retires a contact's password on the server, which is why a dedicated debug contact exists | [CODE K/data/auth/AuthRepository.kt:30-33] [SESSION 3adfe568 C17 (history_early D10)] |
| Debug test contact | "Debug · Sign In as Test Contact" → `onEmailLogin(DEV_LOGIN_EMAIL, DEV_LOGIN_PASSWORD)` | as email | Only in debug builds with `DEV_LOGIN_EMAIL` set (local.properties) | [CODE K/feature/auth/LoginScreen.kt:190,321-334] |
| Debug lead token | intent extra `debug_lead_token` | none | Debug only; saves a lead JWT for `adb am start` testing | [CODE K/MainActivity.kt:50-57] |
| Guest desk | `guestDesk(turns, device)` (X-Skip-Auth) | `POST guest/desk` | Not a sign-in: two short messages, then Google inside the chat | [CODE K/feature/desk/DeskData.kt:117-135] |

`runAuth` reads `data.token`, `role` ("lead" or client), `client_id`, `contact_id`, `status`, then `TokenStore.save`. For a client it subscribes the Beams interest `contact-{id}`; leads never subscribe [CODE K/data/auth/AuthRepository.kt:148-181]. Errors become `SignInException(message, code, error)` from the server JSON `message` / `error` [CODE K/data/auth/AuthRepository.kt:186-197].

One live token per contact on the server (`tblcontacts.customer_api_key`): a new sign-in elsewhere (including a curl test) revokes the phone's token, which then gets a 401 [CODE customers_api@97de5b4 helpers/customers_api_helper.php:66-89] [SESSION 3adfe568 C17].

Google sign-in needs the Android OAuth client registered with the signing certificate's SHA-1: the debug keystore for debug builds and the Play app-signing key for Play builds; otherwise logcat shows "not registered to use OAuth2.0" [SESSION 3adfe568 C22] [MEMORY project_leads_in_app.md]. See build-test-release.md. **[Owner decision 2026-11-07, REQ-002]** Google Sign-In is configured; Google's production approval is pending.

## 2. Leads vs clients

- INTENDED (owner, 2026-10-20): anyone may sign in with Google; a person with no open account is a lead with a limited shell (desk, Agreement Check, My Enquiries, Become a Client), and stays a lead until the firm approves the account [SESSION 3adfe568 C21-C22 (history_early D11)] [CODE K/data/auth/SessionRole.kt:5-12].
- IMPLEMENTED:
  - `SessionRole.lead` gates the bell, profile, push, Digital Office and account switch, because a lead token opens none of the client endpoints and a refusal (401) would sign them out [CODE K/data/auth/SessionRole.kt:5-12; K/MainActivity.kt:136-176].
  - Lead status values: `lead`, `awaiting_approval`, `rejected` [CODE K/data/auth/SessionRole.kt:19].
  - Leads use their own routes and tabs (Home, Enquiries, More) and the `lead/*` API plus website funnel URLs (features.md section 12).
  - Server: a lead token is accepted only by `isLeadAuthorized()`, which base64-reads `lead`/`lead_key` claims without checking the signature and then requires the whole token to equal the stored `tblcustomers_api_leads.token`; `isAuthorized()` refuses it (no `contact_id`) [CODE customers_api@97de5b4 helpers/customers_api_lead_helper.php:16-59; helpers/customers_api_helper.php:155-164].
- **Approval on launch.** At every launch, a signed-in lead calls `AuthRepository.leadStanding()` → `GET lead/me` with a 4 s timeout [CODE K/ui/navigation/NavGraph.kt:89-113; K/data/auth/AuthRepository.kt:63-87]:
  - `role == "client"`: the response carries a client token (`client.token`, `client_id`, `contact_id`), which replaces the lead token, registers the Beams interest, and the app opens as the client;
  - otherwise it stores the status, `review_note`, `can_register`, `account_desk` into SessionRole;
  - offline / failure returns null and the app stays as it was.
  - The same check runs on ON_RESUME and pull-to-refresh in the lead shell [GIT 743ea87 2026-10-23].

## 3. TokenStore

[CODE K/data/auth/TokenStore.kt:11-52]
- DataStore Preferences named `"auth"`; keys `jwt`, `client_id`, `contact_id`, `role`, `lead_status`.
- **Unencrypted.** The comment says production should layer EncryptedSharedPreferences; never done. `security-crypto` is declared but unused [CODE K/data/auth/TokenStore.kt:11-12; app/build.gradle.kts].
- `loadRole()` pushes the stored role into `SessionRole`.
- `allowBackup=false` keeps the DataStore out of backups [CODE AndroidManifest.xml:20].

## 4. AuthInterceptor and 401 handling

[CODE K/data/api/AuthInterceptor.kt:14-31]
- `X-Skip-Auth: 1` → header stripped, request sent without auth. Used by login, loginWithGoogle, googleConfig, otp*, guestDesk, trackNotice.
- Otherwise `Authorization: Bearer <jwt>` (token read with `runBlocking`). The server accepts the header with or without `Bearer ` [CODE customers_api@97de5b4 (backend_contract §1.1)].
- **Central 401**: if the response is 401, a JWT was sent, and the stored JWT is still the one sent, call `SessionEvents.end()`. The comparison means a burst of 401s ends the session once.
- `AppShell` collects `SessionEvents.ended`: `AuthRepository.forget()` then Login with `popUpTo(0)` (skipped on Splash, Login, Register) [CODE K/MainActivity.kt:99-118].
- Server causes of 401: invalid/expired JWT (7-day lifetime from `API_TIME`), lead token on a client route, header ≠ stored token (signed in elsewhere), contact or client inactive [CODE customers_api@97de5b4 helpers/customers_api_helper.php:133-251; config/jwt.php:26,38].

### 403 handling gaps

- The interceptor reacts **only to 401**. 403 is left to each ViewModel [CODE K/data/api/AuthInterceptor.kt:27].
- Per-screen mapping is inconsistent: Calendar, `ApiError` (consultations), Vault, Invoices, Home and the Notices list treat 403 as "your account does not include this"; Cases, CaseDetail, CaseDocViewer, HearingDetail, a Notices detail path and NoticeDocViewer show "Your session has expired. Please sign in again." without actually ending the session [CODE K/feature/cases/CasesViewModel.kt:57; CaseDetailViewModel.kt:65; CaseDocViewerScreen.kt:258; K/feature/hearings/HearingDetailViewModel.kt:38; K/feature/notices/NoticesViewModel.kt:194; NoticeDocViewerScreen.kt:231].
- The app never reads `code: view_only` (customers_api 3cbc2a5, 2026-11-05), `no_permission`, or `not_permission_to_perform_this_action` [CODE grep at 6c069eb] [GIT 3cbc2a5]. The server view-only refusal is probably inert over HTTP today because its helper is not loaded headlessly [ASSUMPTION, backend_contract §4.3].
- No up-front signal tells the app a contact is view-only (`access_mode` appears nowhere in customers_api responses) [CODE customers_api@97de5b4 grep (web_impact §3 item 4)].

## 5. Session restore

[CODE K/ui/navigation/NavGraph.kt:89-113]
1. Splash reads the token and calls `loadRole()`.
2. A lead runs `leadStanding()` (section 2).
3. Route to Login (no token), LeadHome (lead) or Home (client).
- The JWT is not validated at launch. The first 401 (often the Home `profile` canary [CODE K/feature/home/HomeRepository.kt:49-75]) returns the user to Login.

## 6. Sign-out

[CODE K/data/auth/AuthRepository.kt:89-107,126-138]
- `signOut()`: best-effort `GET logout` with a 5 s timeout (server revokes the token); skipped for leads, whose token is not a contact's.
- `forget()` (also used after a 401):
  1. `PushSetup.clear()` (`clearDeviceInterests`) before credentials go, so a queued push does not fire;
  2. clear `ProfileHub`, `NotificationHub`, `ActionHub`;
  3. `tokens.clear()` (whole "auth" DataStore);
  4. delete cached document dirs `docs`, `ahc`, `casedoc`, `docreq`, `noticedoc` and `proj_*` / `projfile_*` files.
- UI path: More → Sign Out → Login with `popUpTo(0)` [CODE K/ui/navigation/NavGraph.kt:352-380]; LeadMore for leads.

## 7. Account switch (one person, several client accounts)

INTENDED (owner, 1 Nov 2026, via the mobile handoff brief): a client who is also the contact of their companies moves between accounts as in the portal's account menu; the brief suggested the profile or drawer header [CODE K/feature/profile/Accounts.kt:51-58] [DOC cases/docs/developer/mobile_handoff_2026-11-01.md (history_late D10)]. First built as a Your Accounts card on Profile (3ed3b7b), then moved by the owner to a switch icon beside the bell (6c069eb) [GIT 2026-11-01].

IMPLEMENTED [CODE K/feature/profile/Accounts.kt]:
- `AccountsViewModel.load()` → `GET accounts` → `{accounts:[{id,label,kind,current}], proved}` [:82-98].
- `AccountSwitch` dropdown in `AppHeader`, only with 2 or more accounts, keyed to the signed-in contact so a re-sign-in does not show the previous person's list [:187-228] [GIT 6c069eb].
- `choose()` → `POST accounts/switch` (`to`). Answer: `{token, contact, client}` (no code needed) or `{code:true, ref, to}` [:101-119].
- Code step: `WizardDialog`/`WizardFrame` "Switch Account", 6 digits auto-submit → `POST accounts/switch/verify`; `status` polled every 10 s up to 12 times until `can_sms` allows `sms` [:121-160,230-269]. Server codes 422 / 429 / 503 [CODE customers_api@97de5b4 (backend_contract §1.2)].
- `done()` → `AuthRepository.switchedTo(jwt, client, contact)`: clears hubs and doc cache, saves the new token, adds the Beams interest for the new contact **without removing the old ones** (by design: updates for every account used on the phone keep arriving; sign-out clears all) [CODE K/data/auth/AuthRepository.kt:109-122]. Then MainActivity restarts with NEW_TASK | CLEAR_TASK [CODE K/feature/profile/Accounts.kt:192-197].
- **`person` claim is server-side only.** The app never reads it. customers_api writes `person` into the JWT at OTP sign-in when the contact is the person's holder [CODE customers_api@97de5b4 controllers/v1/Authentication.php:167-172], and the switch issues it too [CODE customers_api@97de5b4 controllers/v1/Accounts.php:167]. A token carrying `person` lets the switch skip the code; that is why "a sign-in by code" already proves the person.

## 8. Token key change e3027f0 (2026-11-04): one forced re-sign-in

- Before: client JWTs were signed with a key derived from `base_url()`, a public value (flagged in the 11-01 GTM fact sheet) [SESSION 3adfe568 2026-11-01T10:18 (history_late §5)].
- customers_api e3027f0 (another session, GTM 0.2): key is `hash_hmac('sha256','customers_api.token.v1', APP_ENC_KEY)`; tracked `jwt_key` blanked [GIT e3027f0 2026-11-04] [CODE customers_api@97de5b4 libraries/Authorization_token.php:63-96].
- Effect once deployed: every client token issued earlier fails to decode → 401 → interceptor ends the session → every client-app user signs in once more. No app change needed [CODE K/data/api/AuthInterceptor.kt:14-27].
- Sources differ on leads: web_impact says lead tokens are signed out too; backend_contract (code reading of `customers_api_lead_from_request`, which skips signature checks and compares to the stored token) says lead tokens are unaffected. The backend_contract reading is code-based and is the stronger claim [CODE customers_api@97de5b4 helpers/customers_api_lead_helper.php:16-59].
- Side effect: also fixes the dev "Provided key is too short" token failure [MEMORY project_mobile_app_backlog.md item 5].
- If `APP_ENC_KEY` is missing on an environment, token issue throws [SESSION 89b46f22 2026-11-04T09:51].
- Testers and users should be told to expect one sign-out (web_impact §3 item 8).

## Open questions

- Has e3027f0 been deployed to production yet, i.e. has the one-time forced sign-in already happened? (backend_contract open question 8.)
- Should a 403 ever end the session? Today it never does centrally, but some screens tell the user their session expired. An owner decision on wording and behaviour is pending.
- Should the token be moved to encrypted storage (security-crypto is already declared)? No owner decision found.
