# ADR-006: Client Onboarding: Self In The Client App, By Staff In The Staff App

> Part of the Legum Lex Android knowledge base. Verified against: client app 6c069eb, staff app e52a8ca, customers_api 97de5b4, api 9df1506, cases cb0a820 (2026-11-07). How a client account is opened from each app, and the rules both share.
>
> This file is identical in legum-lex-android/docs/kb and legum-lex-staff/docs/kb.

## Requirement

> "Client App will onboard client by self and the staff app will do by staff." [SESSION 3adfe568 2026-10-23T10:32]
> "When I say 'onboarding on staff app,' I don't mean onboarding staff members. I mean onboarding of clients." [SESSION 3adfe568 2026-10-24T16:50]
> "drop the password, option A . Also, verifying your mobile should be integral to registration." [SESSION 3adfe568 2026-10-23T18:16]

## Alternatives considered

- Separate app-specific onboarding logic per surface (the old `Admin_onboard.php`, `create_customer_admin`) vs one shared service (chosen).
- Self-registration by form only vs by conversation in Lex Intelligence (both: conversation with "Or Use The Form", Option A).
- A portal password vs sign-in by Google or mobile code only (chosen: no password).

## Decision

- Shared rules in lexon `Registration_service` (state array; begin/upload/remove/review/submit_by_staff/resubmit/reopen). Staff API `api/client_onboard` replaced `Admin_onboard.php` (deleted) and `create_customer_admin` [SESSION 3adfe568 C25-C26].
- Staff: WizardFrame steps Type, Files, Details, Address, Client, Review; account active at once with KYC pending ("nothing is verified by the AI"); first access by Google or mobile code, no password email; `capabilities.customers.create/edit` gate; client mobile verified with a code [CODE lexco@e52a8ca J/feature/onboarding/OnboardingViewModel.kt:22-235].
- Client (lead): 6-step wizard Type, Files, Details, Address, Contact, Confirm; rejected → Fix And Resubmit; any verified Google account may self-register; basic KYC document checks block self-registration ("It cannot be X's Aadhaar card and Y's PAN card") [CODE lex@6c069eb K/feature/lead/LeadOnboarding.kt:189] [SESSION 3adfe568 2026-10-23T19:15].
- Desk onboarding: lexon `Enrolment` computes turns server-side; `lead/onboarding/turn|answer|phone/*`; consent names "the firm and its service providers"; verified mobile mandatory (messaging migration 004 'register'); switch `lexon_desk_onboarding` default 0 [SESSION 3adfe568 C26-C28]. **[Owner decision 2026-11-07, REQ-002]** `lexon_desk_onboarding` is **active on production and under testing**; do not treat it as unused. Dev value is 0.
- Long document reads use `X-Long-Call` (120 s) in both apps.

## Reasons

- One set of KYC and approval rules for web, client app and staff app.
- No passwords to leak or reset; Google and verified mobile prove identity.

## Later changes

- 2026-10-23 PAN-required rule after an API test resubmit wiped a PAN (lexon 4f9de79, bffee2b) [SESSION 3adfe568 C26].
- 2026-10-24: "Onboard Now" on sign-in opens the desk in account mode; staff Home "Onboard A Client" / "Continue Onboarding" (staff 534eaef).
- 2026-10-29: PAN lock in Edit KYC (staff 073de22).

## Status

Implemented in both apps and the backend. Desk onboarding was off on dev and production at 2026-10-24 ("why are my releases running old? I dont see onboarding implemented yet." [SESSION 3adfe568 2026-10-24T11:36]). `client_onboard/reopen` exists but the staff app does not call it. KYC approve/reject is not in the staff app.

## Open questions

- Is `lexon_desk_onboarding` on in production now?
