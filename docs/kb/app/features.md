# Client App Features

> Part of the Legum Lex Android knowledge base. Verified against: client app 6c069eb, staff app e52a8ca, customers_api 97de5b4, api 9df1506, cases cb0a820 (2026-11-07). One section per feature folder: screens, ViewModels, endpoints, notable behaviour, status and known gaps.

Label shorthand: `[CODE path:line]` means `[CODE lex@6c069eb path:line]`, and `K/` means `app/src/main/java/com/legumlex/client/`. Endpoint names are `ApiService` function names; their HTTP paths are in the endpoint table of the backend contract (common/backend) and in findings `client_app.md` section 5. Paths are relative to `.../customers_api/v1/` unless marked as absolute site URLs.

Cross-cutting gaps that apply to several features are collected in section 21.

## 1. auth

- Screens: `SplashScreen` (holds 900 ms) [CODE K/feature/auth/SplashScreen.kt:27], `LoginScreen` + `LoginViewModel` with three views: Choose / Email / Mobile.
- Endpoints: login, loginWithGoogle, googleConfig, otpRequest, otpStatus, otpSms, otpVerify.
- Behaviour:
  - `googleConfig()["sign_up"]` decides whether newcomers see "Onboard Now" [CODE K/feature/auth/LoginScreen.kt:95-98] [GIT 1830025, 297f7cf 2026-10-24].
  - The Lex Intelligence orb opens the desk (guest mode); "Onboard Now" opens the desk with `account=1` [CODE K/ui/navigation/NavGraph.kt:115-127].
  - `openSite()` opens site pages in the browser [CODE K/feature/auth/LoginScreen.kt:801-809].
  - Debug-only "Sign In as Test Contact" button [CODE K/feature/auth/LoginScreen.kt:190,321-334].
- Status: shipped since step 1 (11ac1ad, 2026-10-19) and redesigned 10-20 [GIT]. Details in auth.md.

## 2. calendar

- `CalendarScreen` with a private `CalendarViewModel` using `HearingsRepository` (hearings, upcomingHearings) [CODE K/feature/calendar/CalendarScreen.kt:68-104].
- Reached from Home's "What's On" next event and from More; no longer a tab (Notices replaced it 2026-10-20).
- 403 maps to a `Denied` state (correct permission handling) [CODE K/feature/calendar/CalendarScreen.kt:80].
- Does not touch the device calendar, although READ/WRITE_CALENDAR are declared.

## 3. cases (Matters / Litigation)

- Screens / VMs: `CasesScreen` + `CasesViewModel`, `CaseDetailScreen` + `CaseDetailViewModel`, `CaseDocViewerScreen`.
- Endpoints: cases, caseTimeline, caseNotices (`GET notices?case_id`), downloadCaseOrderDocument, downloadCaseDocument.
- Behaviour:
  - Case page rebuilt on the staff layout with client-only data (owner: "the staff app uses staff privileges… Don't adopt any such information") [SESSION 3adfe568 2026-10-24T17:58].
  - Tabs Hearings / Docket / Notices / Counsel; court, parties and acts in an info dialog [GIT b6c69ec, 64ed6ab 2026-10-24].
  - Orders read "Open Order", never "certified copy" [GIT db13981] [MEMORY feedback_no_certified_copy.md].
  - Document viewer streams to `cacheDir/casedoc` and renders with the platform `PdfRenderer` [CODE K/feature/cases/CaseDocViewerScreen.kt:195,231].
- Known gap: 403 shown as "Your session has expired. Please sign in again." [CODE K/feature/cases/CasesViewModel.kt:57; CaseDetailViewModel.kt:65; CaseDocViewerScreen.kt:258].

## 4. consultations

- Screens / VMs: `ConsultationsScreen` + VM (filter bar, timeline rows; default filter Active, "Awaiting You" last with a pulse [GIT 82eb79a 2026-10-20]); `ConsultationDetailScreen` + VM; `ConversationThreadScreen`; `AttachmentsCard` (upload, ML Kit scan, delete); `CloseConsultationSheet`; `ConsultationActionsMenu`; `AhcDocumentViewerScreen` and `DocFileViewerScreen` (PdfRenderer; `cacheDir/ahc`, `cacheDir/docreq`).
- Per-kind detail bodies in `detail/`: DetailAHC (renders `ahc.AhcDetailBody` via `AhcUiStateMapper`), DetailDocReq, DetailDigital, DetailConverted, DetailGeneral [CODE K/feature/consultations/detail/DetailAHC.kt:55,146].
- Kinds: general, legal_enquiry, agreement_review ("Agreement Check"), legal_notice, document_request, digital_office ("Video Session") [CODE K/feature/consultations/ConsultationModels.kt:18-25].
- Endpoints: consultations (filters id/phase/status/request_status/type/urgent/q/limit/offset/ahc_state/order_pending_first), deleteConsultation, cancelConsultation, consultationLinkage, updateConsultationPreferred, deleteConsultationAttachment, declineDocRequest, downloadAhcDocument, consultationAhc, createConsultation, consultationDocRequests, fulfillDocRequest, downloadDocRequestFile, consultationAttachments, uploadConsultationAttachment, consultationAppointment, sendConsultationReply.
- `ApiError.kt` treats 403 as a permission ("Your account does not include this…"), not a session [CODE K/feature/consultations/ApiError.kt:28-41].
- `createConsultation` is form-encoded and sends no files; attachments go afterwards through `consultations/{id}/attachments` (benign) [CODE customers_api contract, see backend_contract §3.1].
- Client sees only its own text and client-visible notes/messages (server `clientView()`) [SESSION 3adfe568 C18-C19] [GIT customers_api c596f6d].

## 5. desk ("Consult", Lex Intelligence)

- Files: `DeskScreen`, `DeskViewModel`, `DeskData` (`DeskRepository`), `AccountDesk`, `TrackSheet`.
- Port of the website drawer `cases/views/public/site/partials/desk_panel.php` [CODE K/feature/desk/DeskScreen.kt:113-123]; replaced the old three-step new-consultation form [CODE K/ui/navigation/NavGraph.kt:291-294] [GIT a5067c1 2026-10-19].
- Flow: question → `deskCheck` reading → clarify → documents → entity / country / phone docks → `deskEnquiry` (multipart) → `deskPay` (fee invoice, opened via `PayActivity.open`) or `deskDiscard` [CODE K/feature/desk/DeskScreen.kt:190].
- Leads: each call switches to `leadDesk*` variants when `SessionRole.isLead` [CODE K/feature/desk/DeskData.kt:111-115].
- Guest mode before sign-in: `guestDesk(turns, device)`, two short messages, keyed by a per-install UUID (SharedPreferences "desk"); Google sign-in renders inside the chat [CODE K/feature/desk/DeskData.kt:117-135]. Owner: > "If I am not signed in, I may be allowed to say 'hi' or 'hello'… limit it to two messages and 140 characters" [SESSION 3adfe568 2026-10-20T17:57].
- Account opening by conversation (`AccountDesk`): leadOnboardingTurn / Answer / Document / Submit. Server switch `lexon_desk_onboarding` was off on dev and prod at 2026-10-24 [SESSION 3adfe568 C28] [MEMORY project_desk_onboarding.md]. **[Owner decision 2026-11-07, REQ-002]** `lexon_desk_onboarding` is **active on production and under testing**; do not treat it as unused. Dev value is 0.
- Notice tracker: `trackNotice(site + "cases/landing/ajax_track")`, unauthenticated [CODE K/feature/desk/DeskData.kt:325-326].
- "Have an agreement read" goes to `AhcFunnel` (client) or `LeadAhc` (lead).
- Is immersive (no header, no footer).
- Gap to check: since lexon 7f56a67 / digital_office 6fa4525 / cases d62b845 (2026-11-04), fee calls can be refused when no gateway is on, and AI calls can hit a monthly cap; whether the desk shows these server messages is unverified [GIT 2026-11-04] (web_impact §1.B).

## 6. digital_office

- Screens:
  - `DigitalOfficeScreen`: the "office" route, opened from the header icon; port of `digital_office/views/public/chamber_site.php` [CODE K/feature/digital_office/DigitalOfficeScreen.kt:120-133]; polls `dashboardChamber` every 30 s [CODE :161].
  - `DoLandingScreen` + VM (advocate profile).
  - `DoIntakeScreen` + VM (walk-in intake); polls the session every 4 s [CODE K/feature/digital_office/DoIntakeViewModel.kt:275]; fee invoice opened through `PayActivity.open` [CODE DoIntakeScreen.kt:122].
  - `DoWaitroomBanner` / `DoWaitroomSheet` / `DoWaitroomViewModel`: poll every 15 s, pause in the background and on 401 [CODE DoWaitroomViewModel.kt:85-93,176].
  - `VideoLobbyScreen`: pre-call lobby; the call is Google Meet, opened externally [CODE VideoLobbyScreen.kt:65]; requests CAMERA at runtime [CODE VideoLobbyScreen.kt:123].
  - `SelfView`: CameraX preview plus an AudioRecord level meter; nothing is recorded; requests CAMERA and RECORD_AUDIO at runtime [CODE K/feature/digital_office/SelfView.kt:12-14,66-67].
- Endpoints: startDoSession, submitDoIntake, doFeeInvoice, getDoSession, cancelDoSession, getDoWaitroom, joinDoWaitroom, leaveDoWaitroom, consultationDoSession, dashboardChamber.
- `sessions POST` 403 `code=needs_registration` is handled as "not registered for the office" [CODE K/feature/digital_office/DoRepository.kt:200].
- Links to the web `/mylex/office` [CODE K/feature/digital_office/DoModels.kt:106].
- Leads get no Digital Office [CODE K/MainActivity.kt:136-137,166].
- The backend `digital_office/sessions/{id}/pay` (in-app Razorpay for DO) is not used; the app pays the fee invoice instead (backend_contract §3.1).
- Gap: the "private DO channel" of the 11-01 handoff has no client-side code (only the `contact-{id}` Beams interest) [MEMORY project_mobile_handoff_2026_11.md] [ASSUMPTION: server-side or staff-only].

## 7. documents (Vault)

- `VaultScreen` + `VaultWizardViewModel`: wizard Welcome (intent tiles) → Picker → Search → Results.
- Intents: case file, hearing doc, legal notice, consultation doc, agreement, shared with me, search by name [CODE K/feature/documents/VaultWizard.kt:39-47].
- Endpoints: documents(...), downloadDocument (streamed to `cacheDir/docs`, opened through the FileProvider with `ACTION_VIEW`) [CODE K/feature/documents/DocumentsRepository.kt:51; VaultScreen.kt:624].
- 403 handled as "This account does not include these documents" [CODE K/feature/documents/VaultWizard.kt:253-257].
- Note: files are handed to an external viewer app, which can save or share them (same concern as project files, section 15).

## 8. finance

- Screens / VMs: `InvoicesScreen` + VM (balance card, statement link, rows); `InvoiceDetailScreen` + VM (letterhead, balance hero, inline pay `PayInline`, breakdown, payments).
- Activities: `CheckoutActivity` (+ VM, `PaymentEvents`) in `Checkout.kt`, `PayActivity` (WebView). Full flow in integrations.md.
- `MoneyFormat.kt`: `paymentUrlFor(invoice)` builds `<site>/invoice/{id}/{hash}` [CODE K/feature/finance/MoneyFormat.kt:45-50].
- Endpoints: invoices, invoicePayOptions, invoicePay, invoicePayVerify, invoicePayStatus.
- 403 handled as "Your account does not include billing" [CODE K/feature/finance/InvoicesViewModel.kt:89].
- Invoices read as issuer (Nyay & Co. or Legum Lex) with logo [GIT 551bcb4 2026-10-19].
- INTENDED vs IMPLEMENTED: the 10-19 rule "the app takes no payments" [MEMORY feedback_app_payments_by_invoice.md] was reversed on 11-01; code at HEAD pays in-app [GIT 3ed3b7b, 23776d7, 8772b0d 2026-11-01]. The code is newer.
- Status: built and pushed; Method chooser untested (no dev business with both gateways), Razorpay not re-run after 8772b0d, production gateways were off on 11-01 [SESSION 3adfe568 2026-11-01T21:09 (history_late D9)].
- Gap: paying a plan invoice or notice-fee invoice through the app probably does not trigger plan activation / notice-fee release, because those `invoice_status_changed` handlers live in `cases.php`, which the app API never loads [ASSUMPTION, static reading; web_impact §1.A].

## 9. healthchecks/funnel (Agreement Check for clients)

- `AhcFunnelScreen` / VM / Repository; steps Upload → Reading → Confirm → Payment → Review → Result [CODE K/feature/healthchecks/funnel/AhcFunnelModels.kt:9-16].
- Supporting UI: `ResumeFunnelBanner`, `InProgressHomeBanner`, `DiscardDraftDialog`, `SaveAndExitSheet`, `OnboardingAhcHandoffScreen`.
- Endpoints: ahcCurrent, ahcStart, ahcConfirm, ahcUpload, ahcDeleteUpload, ahcAdvance, ahcPayInit, ahcStatus, ahcAbandon.
- Payment: `PayActivity.open(event.url)` [CODE K/feature/healthchecks/funnel/AhcFunnelScreen.kt:45]; an invoice URL is routed to `CheckoutActivity` (integrations.md).
- Chrome: framedWizard.

## 10. hearings

- `HearingDetailScreen` + VM; deep link `legumlex://hearing/{id}`. `HearingsRepository` calls hearings and upcomingHearings.
- No hearings list screen; lists live in Calendar and case detail.
- Gap: 403 mapped to "session expired" [CODE K/feature/hearings/HearingDetailViewModel.kt:38].

## 11. home

- `HomeScreen`, `HomeViewModel`, `HomeRepository`, `HomeWorkspaces.kt` (HomeHeader, WorkspaceEntrances tiles Litigation / Projects / Registry, ConsultationFigures), `ActionHub`.
- Parallel load: profile (auth canary), dashboardActionItems, dashboardNextEvent, dashboardChamber, notificationsUnreadCount, workspaces [CODE K/feature/home/HomeRepository.kt:49-75]; plus dismissActionItem, restoreActionItem.
- "What's On" (next event) leads the page; labels say "Services", not "Workspace" [GIT 1f2460a, 6a94364 2026-10-24].
- DO waitroom banner shows on Home [CODE K/feature/home/HomeScreen.kt:337].
- 403 → `denied` state with "does not yet include access" text [CODE K/feature/home/HomeViewModel.kt:147,160].
- Dead: `ActionQueueBlock` (queue moved into Notifications) [CODE K/feature/home/HomeScreen.kt:403].
- Server note: since customers_api eab7c0a (2026-11-05) action items are gated per function (notices / consultations) rather than by `cases` [GIT eab7c0a]; no app change needed.

## 12. lead

Owner, 2026-10-20: "Anyone may sign in with Google; someone the firm has no open account for is a lead" [CODE K/data/auth/SessionRole.kt:5-12].

- Screens: `LeadHomeScreen`, `LeadEnquiriesScreen`, `LeadThreadScreen`, `LeadMoreScreen` (sign-out only), `LeadAhcScreen`, `LeadOnboardingScreen` (files `LeadScreens.kt`, `LeadData.kt`, `LeadAhc.kt`, `LeadOnboarding.kt`).
- `LeadRepository` calls website funnel endpoints by **absolute URL** with the lead JWT: `lexon/api/funnel/my_sessions`, `thread/{hash}`, `thread/{hash}/reply` [CODE K/feature/lead/LeadData.kt:49-102].
- `LeadAhc`: `lexon/api/funnel/details/ahc`, `start/ahc`, `consent/{hash}`, `intake/{hash}`, then pays at `cases/landing/lead_pay/{hash}` inside `PayActivity` (WebView, not Checkout) [CODE K/feature/lead/LeadAhc.kt:58-156].
- `OnboardingRepository`: 6-step WizardFrame Type, Files, Details, Address, Contact, Confirm [CODE K/feature/lead/LeadOnboarding.kt:189]; endpoints `lead/onboarding`, begin, document (X-Long-Call, 120 s), remove, review, submit, discard, reopen, phone/{step}.
- Rejected registrations can be fixed and resubmitted; approval is detected on ON_RESUME / pull-to-refresh and at launch via `lead/me` [GIT 743ea87 2026-10-23] (auth.md).
- Status: lead token check is stored-token equality on the server, so the 11-04 key change did not affect leads (backend_contract §4.5).

## 13. more

- `MoreScreen`: rows grouped Mylex-style (file, consultations, litigation extras, account); ends with Sign Out [CODE K/feature/more/MoreScreen.kt:65-71].
- Sign-out: `AuthRepository.signOut()` then Login with `popUpTo(0)` [CODE K/ui/navigation/NavGraph.kt:352-380].

## 14. notices

- Screens / VMs: `NoticesScreen` (filters, sort), `NoticeDetailScreen` (tracker layout, approve footer), `NoticeDocViewerScreen` + VM (`cacheDir/noticedoc`, PdfRenderer), `NoticesRepository`, `NoticesViewModel`, `NoticeDetailViewModel`.
- Endpoints: notices, notice, approveNotice (`PUT notices/{id}/approve`), downloadNoticeDocument.
- No notice creation in the app; the API also refuses it (403 `not_permitted`) [GIT customers_api adac96d 2026-10-20] [MEMORY feedback_client_app_scope.md]. Owner: > "This is a client app. The client app should not be able to create legal notices" [SESSION 3adfe568 2026-10-20T05:08].
- 403 on list → `NO_ACCESS` (screens hide) [CODE K/feature/notices/NoticesViewModel.kt:13,133,181]; but some paths still map 401 and 403 to "session expired" [CODE K/feature/notices/NoticesViewModel.kt:194; NoticeDocViewerScreen.kt:231].
- Not caught up with the backend (2026-11-05 to 11-07): pay-first notices (`fee {state, amount, invoice_id, pay_url, held}`, "Awaiting Payment", next_step text) and a possible 409 from approve are not read by the app [GIT customers_api e68688b, 97de5b4]. Probably inert over HTTP today because `cases_fee_helper.php` is not loaded headlessly [ASSUMPTION, backend_contract §4.3].

## 15. notifications

- `NotificationsScreen` / VM / Repository with its own header (back, title, mark all read).
- Endpoints: notifications, markNotificationRead, markAllNotificationsRead.
- Content is firm announcements plus the "Requires Your Attention" / action queue [GIT 751b1b9 2026-10-19] [SESSION 3adfe568 C18-C20].
- Tap routing: action item → `openActionItem`; `consultation/{id}` link → ConsultationDetail; ATTENTION → Consultations tab [CODE K/ui/navigation/NavGraph.kt:246-266].

## 16. profile

- `ProfileScreen` / VM / Repository, `ProfileHub`, `Accounts.kt`.
- Name, email and phone are read-only; one verified mobile number per person via `profilePhone(step = request|status|sms|verify|updates|remove)` [CODE K/data/api/ApiService.kt:88-98] [MEMORY project_one_mobile_number.md].
- Appearance: Follow Phone / Light / Dark (`ThemePrefs`) [CODE K/feature/profile/ProfileScreen.kt:140-148].
- Account switch lives in `Accounts.kt` but is drawn in the header since 6c069eb (auth.md).

## 17. projects

- Screens / VMs: `ProjectsScreen` + VM (cards that swipe left to Open, `SwipeRevealRow`) [CODE K/feature/projects/ProjectsScreen.kt:211]; `ProjectDetailScreen` + VM (tabs Stages / Tasks / Files / Discussions gated by project `settings`, e.g. `viewDiscussions`) [CODE ProjectDetailViewModel.kt:94]; `ProjectMilestonesScreen`; `ProjectFilesScreen`; `ProjectDiscussionsScreen` ("New Discussion" when `canStart`); `ProjectActivityScreen`; `TaskDetailScreen` + VM (`canComment` nav arg).
- Endpoints: projects, project, projectTasks, projectMilestones, projectFiles, projectActivity, task, postTaskComment, createProjectTask, uploadProjectFile, downloadProjectFile (to `cacheDir/proj_*` / `projfile_*`), projectDiscussions, projectDiscussion, startProjectDiscussion, postDiscussionComment.
- Not read-only any more: comments, task creation, uploads and new discussions exist [GIT 18f17be 2026-10-25]. Project Types stage tracker and "Needs You" [GIT 55368c3 2026-10-24].
- Known gap (web moved on 2026-11-03): project files are view-only for clients on the web; the API now flags rows `view_only: true`, sends `X-Lex-View-Only: 1` and `Cache-Control: no-store`, refuses `?download`, and gates on the Files tab [GIT customers_api e6eb3e1]. The app ignores all of these and hands the cached file to an external app via `ACTION_VIEW` with `FLAG_GRANT_READ_URI_PERMISSION` [CODE K/feature/projects/ProjectFilesScreen.kt:144-156,385-390]. INTENDED (owner rule on web): view only, no download. IMPLEMENTED (app): the receiving app can save or share.
- Server rule change 2026-11-03: project members / task assignees are any active staff, not only the client's counsel [GIT cases c8c0813]; the client app shows what the server returns.

## 18. registry

- `RegistryScreen`, `RegistryAgreementScreen`; `RegistryData` (Repository, `RegistryViewModel`, `RegistryAgreementViewModel`).
- Read-only mirror of `/mylex/register` [CODE K/feature/registry/RegistryScreens.kt:51-55]. Endpoints: registry, registryAgreement.
- Reached from the Registry workspace tile. Owner (staff app context, 10-26): > "Do not implement the registry yet." [SESSION 3adfe568 2026-10-26T19:21]; registry notifications open the web.

## 19. scanner

- `rememberDocumentScannerLauncher`: thin ML Kit `GmsDocumentScanner` binding returning a PDF Uri [CODE K/feature/scanner/DocumentScanner.kt:17-27].
- Used by `AttachmentsCard`, the desk's account documents and `LeadOnboarding`.

## 20. ahc/ (root package, Agreement Check result kit)

- `AhcDetailScreen` / `AhcDetailBody`, components (Closure, Countdown, FeatureCards, Primitives `LlCard`…, Signal components), `model/AhcModels`, own `theme/Tokens.kt` (`LlColor`) and `Type.kt`, `Previews.kt` (@Preview gallery), `SampleData.kt`.
- Used by consultations `DetailAHC` through `AhcUiStateMapper` [CODE K/feature/consultations/detail/DetailAHC.kt:55,146].
- Its tokens leak into InvoiceDetail and Projects screens, so two token systems coexist (architecture.md section 2).
- Push `route=ahc` adds progress / "View report" / Reply actions but has no deep link (integrations.md).

## 20a. board/ (Counsel workspace, REQ-008, built 2026-11-07 to 11-08; committed from lex 603eaad 2026-11-08, gap fixes lex 08b9dc4 2026-11-10, audit fixes lex 3ecc52f/86dadcb 2026-11-10)

- **Workspace switch:** `BoardHub` (singleton, cleared in `AuthRepository.forget()` and `switchedTo()`) asks `GET board/summary` once the profile is read. 200 with `counsel: true` (`isCounsel(access)`) turns the whole app into the Counsel workspace: Home becomes Today, the header shows "Legum Lex / COUNSEL", a credits chip, a Sign Out symbol button (confirm dialog, then Login) and the profile circle (opens Profile). The bar is Today, the gold animated Print button in the notch, Credits and Upgrade (`counselTabs`, `LegumBottomBar(counsel = true)`). 404 `board_off`, 403 or `counsel: false` keeps the normal client app.
- **Home (`CounselHome.kt`, `CounselScore.kt`; round 4, canvas 7f and 7e), the first tab:** from `GET board/home`. A profile card (initials disc, "Adv." name, enrolment · Bar Council, years at the Bar over YRS, icon rows for chambers, email, mobile, courts and districts; a ^ folds it and the choice is kept in SharedPreferences `counsel_home`). The Chambers row has a small inline edit glyph opening `ChambersWizard` (2 steps: Address; City And PIN Code; `POST board/chambers` {address 191, city 100, zip 15}, 422 `address` returns to step 1; request shape in `chambersFields`). The specialty ring is drawn in a Compose Canvas, one arc per specialty with cases in the web's order and colours, no numbers on the arcs; a tap on an arc or its legend row thickens it, fades the others to 25%, shows "Suits · 7 cases · 7 civil points" and counts the centre up (400 ms) to that count with its name; a tap elsewhere returns. It draws in once on first show; with the animator scale at 0 nothing moves. Then the legend, the criminal and civil milestone bars (10, 25, 50, 100) and four tiles (Diary opens the cases list, This Month opens the Calendar).
- **Calendar (`CounselToday.kt` `CalendarPage`, `CounselCalendar.kt`; canvas 6a; it was Today), the second tab:** months across the top (tap for the date picker), a Sunday-to-Saturday week with the chosen day as a black pill and a dot where `board/days` counts listings (swipe for the next week), then a black sheet with the day's heading and count (no Print on it) and the day's cases from `board/day` as a timeline: case number, parties with a small outlined VS, `judge_line`, `court_line`, full text. A case whose date needs a look is the light card with the two answer buttons. Search (any case, any date) and Add (CNR, Import, Export) stay at the foot. [Superseded 2026-11-10 (lex 3ecc52f): see §20c] The chosen day is kept in `BoardHub.chosen`.
- **Bar:** Home · Calendar · [Print] · Credits · Upgrade. Print prints the day chosen on the Calendar (today until one is chosen).
- **Upgrade (`UpgradeScreen.kt`, canvas 9b, UpgradePlatform):** "light the way": it opens dark (the header, status bar and bar band go dark with it, `UpgradeRoom.dark`); the tagline "Justice, / in plain sight." (Cormorant Garamond, bundled) comes into focus; a cord on the right with a pill (drag down, haptic tick, or tap) lights the room. Then six 2-across tiles (Cases, Hearings, Clients, Documents, Mail, Accounts) rise in with line icons that draw themselves in, "A much bigger journey awaits." in Cormorant italic, COMING SOON typed in tracked capitals after a gold rule with a gold caret, and the Counsel panel slides in; a small switch bottom right puts the light out. Type scales with the height left (k), no links or buttons, nothing collected, everything instant when the animator scale is 0.
- **Dates follow eCourts:** after `import/confirm` the server returns `mismatches[]`; the "Which Date Is Right?" dialog and Needs Your Eye offer "eCourts is right" (`date/accept`) or "Keep my date" (`date/keep`). The app has no free date typing.
- **Credits (`BoardCreditsScreen.kt`):** balance with "eCourts checked" line, spend tiles (today, month, all time), pack carousel with `packLasts` text [Superseded 2026-11-10 (lex 3ecc52f): see §20c], history (20, Show More) [Superseded 2026-11-10: paged, see the 2026-11-10 update below]. Pay first: the cart calls `credits/checkout`, opens the server's `pay_url` in a Custom Tab (androidx.browser, no payment SDK), and on return polls `credits/order` (5 times, 2 s) [Superseded 2026-11-10: one ask per resume plus Check Again, see below] for paid, pending, failed, expired or flagged. A banner shows the outcome; "failed" reads "Nothing was charged. You can try again." [Superseded 2026-11-10 (lex 3ecc52f): see §20c] Money, costs and packs are shown as the server sends them.
- **Icon rule:** actions are symbol buttons with a content description and a long-press tooltip (`IconAction`); long help text sits behind an (i) (`InfoTip`). Dialog buttons stay icon-only for now (Advisor decision).
- **Retired:** the junior views (By Junior, juniors screen), result entry and name/court wizards, the Home tile and More row, `BoardScreen`, `BoardJuniorsScreen`, `BoardHomeTile`, and free date correction. `BoardSheetScreen` (PdfRenderer, Share) takes no junior in the Counsel flow.
- **Workspace memory (cold start):** `BoardHub.expectCounsel` (SharedPreferences `board_workspace`, cleared at sign-out and account switch) opens a known counsel's chrome at once. Only a board/summary refusal (404 or 403) decides No (`decideAccess`): a never-known account on the first refusal, a remembered counsel only after two (one retry after 1.5 s); an unreachable server decides nothing; the profile shortcut is gone; a No is asked again on app resume (at most once a minute). With nothing known the header subtitle and bottom bar stay blank until the server answers (`counselChrome`, `workspaceUnknown`).
- **Weak signal:** each loaded day is saved under `cache/board/` and shown read-only when offline [Corrected 2026-11-10: before lex 08b9dc4 the saved copy was written but never read back; see below]; sheets go to `cache/board/sheets/` (FileProvider path `board`). `"board"` is in `DocCacheDirs`, so sign-out and account switching wipe it.
- **Refusals:** 402 `insufficient_credits` and `premium_paused`, `cap_reached`, `busy`, `ecourts_off` show the server's message; no 402 or 403 maps to "session expired". Dates pin to Asia/Kolkata (`BoardDates`).
- **Debug only:** [Superseded 2026-11-10: now a menu of two accounts, see below] the "Adv (debug)" sign-in button uses `BuildConfig.DEV_ADV_EMAIL` and `DEV_ADV_PASSWORD` from `local.properties` (gitignored), like the test-contact button; it hides when blank, and release builds carry blanks.
- **Tests:** `app/src/test/.../board/` `BoardDatesTest`, `BoardModelsTest`, `CounselTodayTest` (JVM, `testImplementation junit`).
- **Cause List (`BoardSheetScreen`):** day bar (previous, calendar, next, back to today) over `board/sheet?date=`, Print (Android PrintManager) and Share. The format is changed on the web only; an (i) says so. The server's `format.summary` (from `board/day`) shows beside the (i), in the server's words.
- **Case page:** court and bench (`bench`, else the room), Needs Your Eye card (eCourts is right / Keep my date) from `needs_eye` and `ecourts_date`, daily-check switch (`morning_check` through the case edit) [Superseded 2026-11-10 (lex 3ecc52f): see §20c], Remove From Your Diary wording as the web. List filters: All, Listed Today, Tomorrow, Needs Your Eye, Check Off (client-side).
- **Export:** an icon above Your Cases downloads `board/export` as myCases.txt (cache/board/export) and shares it.
- **Date History and Add The CNR:** the case page lists `history` (label and date, newest first); a case with `has_cnr: false` offers a 2-step Add The CNR wizard (`POST board/cases/{id}/cnr`, free, the server's 422 messages shown).
- **Not built:** format wizard and Match My Specimen (web only); pending-purchases list and credits CSV (skipped).

### 20b. 2026-11-10 update: gap fixes (REQ-008 008.D1 and 008.G2; owner "Fix gaps")

Verified at lex@08b9dc4 unless stated. Phone-tested by the Advisor on 2026-11-10 with both dev counsel accounts [SESSION 3adfe568 2026-11-10].

- **Chrome:** `counselChromeFor(access, mode)` decides the Counsel header and bar [CODE K/feature/board/BoardHub.kt:123]; NavGraph shows `CounselHomeScreen` on the Home tab when it is true [CODE K/ui/navigation/NavGraph.kt:245-249]; the bar is `CounselBar` [CODE K/ui/navigation/BottomNav.kt:272]. The workspace chooser greets with the name, or "Welcome" when there is none (`greetingName`) [CODE K/feature/workspace/WorkspaceChoice.kt:65].
- **Home first steps (7g):** shown while `board/home` `first_steps.show`: Bring Your Cases (Import Cases, Add By CNR) and Credits In Hand (trial line or balance line, Buy Credits); the ring is half full once there is a balance [CODE K/feature/board/CounselFirstSteps.kt:77-87] [GIT 5086514 2026-11-09, 77d61e1 2026-11-09].
- **Credits, pay first:** the cart calls `credits/checkout`, stores a `PendingOrder(order, pack, startedAt)` in `cache/board/pending_order.txt` (wiped at sign-out with the rest of `cache/board`), and opens `pay_url` in a Custom Tab [CODE K/feature/board/PendingOrder.kt:6-30; K/feature/board/BoardCreditsScreen.kt:138-149]. The order is asked once on every resume (also after the app was closed) and by a "Check Again" button: no polling loop, no give-up [CODE K/feature/board/BoardCreditsScreen.kt:113-135,265]. [Superseded 2026-11-10 (lex 3ecc52f): see §20c] `orderVerdict()` maps the server status to Paid, Failed (also expired), Flagged or Pending, with the notes `PENDING_NOTE`, `FAILED_NOTE`, `FLAGGED_NOTE` [CODE K/feature/board/PendingOrder.kt:49-58].
- **Credits history:** the first rows come with `board/credits`; reaching the last row reads the next 50 from `credits/history?limit=&offset=` (`{total, items[]}`) until `total` is reached [CODE K/feature/board/BoardCreditsScreen.kt:204-218; K/feature/board/BoardRepository.kt:173; K/feature/board/BoardModels.kt:262].
- **Saved copy of a day:** `dayOrSaved(date)` falls back to the device copy only when the server cannot be reached (offline or 5xx); a refusal (403, 404, 422) is never answered from the copy [CODE K/feature/board/BoardRepository.kt:17,55-66]. Calendar shows "Saved copy from 3:20 PM" (or "9 Nov, 3:20 PM") ". Connect to refresh." [CODE K/feature/board/BoardDates.kt:96-100; K/feature/board/CounselToday.kt:280].
- **Case wizard, dates follow eCourts:** [Superseded 2026-11-10 (lex 3ecc52f): see §20c] on edit, Next Date is read-only for a case with a CNR (`nextDateLocked`), with the help line "Dates follow eCourts. Use Accept eCourts Date or Keep My Date on the case." [CODE K/feature/board/BoardWizards.kt:185-186,254]. The server refuses a changed date on a CNR case with 422 `next_date` and accepts the unchanged date echoed back (see customers-api.md §2.16). Phone check: Save Changes on a CNR case kept the date and raised no flag.
- **Retired calls removed:** juniors, courts, entries and the junior sheet are gone from `ApiService` and the models (the server answers 410 `retired`). Residue: the `BoardSheet` route still carries a `{junior}` path segment ("own") although the screen no longer takes it [CODE K/ui/navigation/Routes.kt:71-77; K/ui/navigation/NavGraph.kt:452].
- **Credit wording:** `creditsLabel(n)` gives "1 credit" / "N credits" with Indian grouping, used in the wizard tick, toasts, Credits notes and First Steps [CODE K/feature/board/BoardDates.kt:17] (found on the phone as "costs 1 credits").
- **Debug only, two dev accounts:** "Adv (debug)" opens a menu: "Counsel · With Cases" (`DEV_ADV_EMAIL`, from `local.properties`, dev account `loop.appadvocate@example.org`) and "Counsel · Empty" (`DEV_ADV_EMPTY_EMAIL`, default `loop.boardadvocate@example.org`); both sign in with `DEV_ADV_PASSWORD` from `local.properties` (never recorded here). An item hides when its email is blank; release builds carry blanks [CODE K/feature/auth/LoginScreen.kt:361-375; app/build.gradle.kts:37-41,104-106,115-116] [GIT 386650e 2026-11-10].
- **Tests (JVM):** `BoardDatesTest`, `BoardModelsTest`, `CounselScoreTest`, `CounselTodayTest`, `DayFallbackTest` (with `NextDateLockTest`), `EnrolmentReviewTest`, `FirstStepsTest`, `PendingOrderTest`, `ScoreDisplayTest`, `WorkspaceTest`, plus `feature/workspace/GreetingTest`; 140 tests green at 08b9dc4 [GIT 08b9dc4 2026-11-10].

### 20c. 2026-11-10 board audit (REQ-008 008.A-A; owner "Now audit the whole board functionality…")

Verified at lex@86dadcb; commits lex 3ecc52f (audit fixes) and 86dadcb (Not This Case note) [GIT 3ecc52f 2026-11-10, 86dadcb 2026-11-10]. Server side board edeae14 / 82b066d, customers_api 0b2f484 (customers-api.md §2.16). Phone-tested by the Advisor on 2026-11-10 with "Counsel · With Cases" [SESSION 3adfe568 2026-11-10].

- **Add By CNR, as the web:** `CnrWizard` steps Find, Add. "Find This Case (4 Credits)" calls `cases/cnr/lookup` (long-call), the found case is shown (court and bench, next date, stage) with the line "Finding this case on eCourts used N credits. Adding it to your diary is free; from tomorrow it is checked every morning for M credit(s)."; "Add To My Diary" calls `cases/cnr/confirm`; "Not This Case" makes no call and shows a neutral note `notThisCaseNote()` ("Nothing was added. The 4 credits used to find it are not returned.") [CODE K/feature/board/BoardWizards.kt:205-228,512; K/data/api/ApiService.kt:912-919]. The one-step `cases/cnr` is no longer called. Phone: lookup → Not This Case charged 4 and added nothing; lookup → Add To My Diary charged 4 once and added the case.
- **Add By Hand:** `HandCaseWizard` (Case: number, petitioner, respondent; Court: court, room, next date by picker) posts `board/cases` without a CNR; refusals on the first step's fields return there [CODE K/feature/board/BoardWizards.kt:295-314]. Reached from the Calendar's "+" menu and the Cases list [CODE K/feature/board/BoardCasesScreen.kt:261]. Phone: CS/09901/2026 added, free.
- **Set Next Date (hand cases only):** `SetDateWizard` (Date, then Confirm with the web's sentence) posts `cases/{id}/date/correct` [CODE K/feature/board/BoardWizards.kt:346; K/data/api/ApiService.kt:923; K/feature/board/BoardCasesScreen.kt:153-154]. Date History marks a `superseded` row "· Replaced" [CODE K/feature/board/BoardCasesScreen.kt:289-291; K/feature/board/BoardModels.kt:82,218]. Phone: 20 → 27 Nov, the 20 Nov row shown as Replaced and stamped (not deleted) on the server.
- **Edit Case removed** (web-first: the web case page has no edit): `CaseWizard` and the typed-CNR "Add A Case" path are gone; kept are the Daily Check switch (sends `morning_check` only), Add The CNR, Add By CNR, Add By Hand, Import. This supersedes the 20b `nextDateLocked` rule.
- **Hand cases:** the CNR row reads "Not yet. This case was added by hand." (`cnrLine`), no Update From Court button; CNR cases show "Update From Court Now (4 Credits)" [CODE K/feature/board/BoardCasesScreen.kt:157,274].
- **Import (`ImportFlow`, Home and Calendar alike):** the preview shows the server's `cost.line` before Confirm; confirm is a long call and is retried once on a timeout, a `repeated: true` reply counting as success; afterwards "N added, M updated… X checked on eCourts just now, Y not checked (your credits did not cover them)", then Which Date Is Right? [CODE K/feature/board/BoardWizards.kt:483; K/data/api/ApiService.kt:872-875; K/feature/board/BoardModels.kt:144,276].
- **Pay first:** `failed` → `NotCompleted` ("Payment not completed. If you did pay, it will be matched and your credits added."), kept and asked about for 3 hours (`FAILED_KEEP_MILLIS`); only `expired` and `cancelled` say nothing was charged; a 404 resolves the order; the banner has Dismiss; Buy is disabled while an order is pending; a missing browser or blank `pay_url` is caught and the order is not stored [CODE K/feature/board/PendingOrder.kt:45,57-58,63-64].
- **Credits:** grouped history (`history_rows`, then `credits/history?grouped=1` paging), server pack lines (`lasts`) and "N% bonus" chip (the app's `packLasts` recomputation deleted), "Expiring Next" from `next_lapse` [CODE K/feature/board/BoardCreditsScreen.kt:208-209,256-273; K/data/api/ApiService.kt:959]. Phone: "Morning check, 3 cases", "5% bonus", "Expiring Next: 90 credits, Trial, …".
- **Calendar:** Needs Your Eye card (eCourts Is Right / Keep My Date) from `today.needs_eye`, low-credit banner, "N cases tracked · About N days of checks", short banner "Only X of your Y cases were checked this morning" [CODE K/feature/board/CounselToday.kt:465,481,512-519].
- **Freshness:** Home and Calendar refresh on resume and when the IST date changes; `BoardHub.rollover` moves the chosen day; `BoardHub.applyBalance` updates the header chip after every spend; the case page re-reads the case after the switch and Update From Court so Date History stays [CODE K/feature/board/CounselToday.kt:212; K/feature/board/BoardHub.kt:47,60].
- **Paused counsel:** 403 `paused` marks `BoardHub.paused` and shows `WorkspacePausedScreen` ("Paused, for now.", Sign Out) over the app; the workspaces-status check is the fallback [CODE K/feature/board/BoardHub.kt:29-32,86-98,114; K/MainActivity.kt:338; K/feature/workspace/WorkspaceChoice.kt:231].
- **Robustness:** two-refusal rule on Today; `not_flagged` counts as done; scanner pattern = the server's [CODE K/feature/board/CnrScan.kt:23-24]; WizardFrame Close/Back disabled while saving (all wizards) [CODE K/ui/components/WizardFrame.kt:104,110]; cause-list pages rendered lazily; malformed JSON not "offline"; saved-copy time in IST; debug Empty item hidden without a password.
- **Copy:** Title Case labels, "Cause List" for "Sheet", "Update From Court Now", "eCourts is right: its date is used", units on every cost.
- **Tests (JVM):** new `BoardAuditTest` plus the existing board suites; build and unit tests green at 86dadcb [GIT 86dadcb 2026-11-10].

## 21. Cross-cutting gaps (all features)

| Gap | Where | Evidence |
|---|---|---|
| 403 handled inconsistently: some screens treat it as permission (Calendar, Consultations `ApiError`, Vault, Invoices, Home, Notices list), others as "session expired" (Cases, CaseDetail, CaseDocViewer, HearingDetail, Notices detail path, NoticeDocViewer) | per-VM `when (code)` | [CODE grep "403" at 6c069eb] |
| `code: view_only` (403) never read; no view-only mode (actions not hidden) | all write screens | [CODE grep, no hits] [GIT customers_api 3cbc2a5 2026-11-05]; server check probably inert headlessly [ASSUMPTION, backend_contract §4.3] |
| HTTP 402 `plan_limit` not handled (no endpoint returns it yet) | n/a | web_impact §1.B |
| Notice fee / "Awaiting Payment" fields unread | notices | [GIT customers_api 97de5b4] |
| Files opened in external apps (Vault, Project files) | documents, projects | [CODE VaultScreen.kt:624; ProjectFilesScreen.kt:388] |
| No display casing helper (`TextCase`) in the client app (staff app has one) | all | [SESSION 3adfe568 (history_late §5)] |
| Parity candidates from the web after 11-01 (project from consultation, link invoices to project, task templates, stages on calendar) | projects | web_impact §3 item 12 |

## Open questions

- Should Vault downloads follow the same view-only rule as project files? No owner rule found for the Vault [ASSUMPTION].
- Exact refusal shapes for funnel / Chamber fees with no gateway on, and for the AI monthly cap, and whether the desk and funnel screens show them (not verified).
- Whether the remaining "old-style" detail screens noted on 10-19 have all been redone (history_early §5 leaves it unclear).

## Counsel registration and workspaces (REQ-010, built 2026-11-09; committed lex 954a106 2026-11-09)

- **Door:** sign-in screen link "Advocate? Register As Counsel" (own line, apart from Onboard Now) opens `counsel/register` (dark, Google). Google sign-in, then `GET lead/counsel` triage shows the server's message for each outcome (new, client, counsel, pending, rejected, staff). New or rejected goes on to the wizard `counsel/register/form` (Certificate, PAN, Review, Mobile, Confirm); submit's client token replaces the lead session (`AuthRepository.switchedTo`). Code: `feature/counsel/`.
- **Debug only:** `DebugLeadShortcut` on the door uses the stored lead token instead of Google; guarded by `BuildConfig.DEBUG`, checked by `DebugLeadShortcutTest`. The existing `am start --es debug_lead_token <jwt>` (debug builds) stores a lead token.
- **Chooser:** `feature/workspace/`. At every opening (cold start, fresh sign-in, account switch) the app reads `workspaces` from the profile answer; `choose` true opens "Where to today?" (`choose`); counsel_only opens as Counsel; otherwise the Client Portal. `WorkspaceSession` is in memory only: nothing is remembered between openings (BoardHub.expectCounsel and its preference are gone). In Portal mode the board is not asked. Switch Workspace: Profile card and the Counsel header, both back to the chooser.
- **Add / Remove Counsel Workspace:** Profile "Workspaces" card (server `status`, `note`, `can_add`, `can_remove`; Fix And Resubmit when rejected); wizard `counsel/add` (Certificate, Details, Confirm) over `workspace*`; Remove over `workspace/remove` with the server's refusal shown.
- **Workspace changes while open:** on resume `syncWorkspaces` compares the list: Counsel gone moves a person in it to the Portal with a note; Counsel added is only a note. A board refusal in Counsel mode (two refusals) moves to the Portal with the server's message.
- **Push:** `data.workspace` (client | counsel). A push for a workspace not held is dropped; a tap opens in that workspace (cold start: no question; warm and in the other one: switches with a note).
- **Uploads (counsel flows):** the picked file is kept so "Try Again With The Same File" needs no re-pick; sending progress then reading; offline says so and that details are kept.
- Design gap: 10a shows figures on the cards; the workspaces answer has none, so the cards carry fixed lines only.
