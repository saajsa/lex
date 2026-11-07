# ADR-005: Leads And Guests In The Client App

> Part of the Legum Lex Android knowledge base. Verified against: client app 6c069eb, staff app e52a8ca, customers_api 97de5b4, api 9df1506, cases cb0a820 (2026-11-07). Why anyone may sign in with Google, what a lead can do, and the guest desk before sign-in.
>
> This file is identical in legum-lex-android/docs/kb and legum-lex-staff/docs/kb.

## Requirement

Owner idea: Lex Intelligence for Google-sign-in leads, with a lead screen [SESSION 3adfe568 2026-10-20T15:39]. Answers: fees "Pay on the website"; leads stay leads until invoiced, billed through a contactless client: "Before accepting my work, do check the feasibility of this with the codebase"; self-onboarding in the app with admin final approval, enforced on the web too [SESSION 3adfe568 C21]. Guest: "If I am not signed in, I may be allowed to say 'hi' or 'hello'… limit it to two messages and 140 characters… The chat will render a Google sign-in button" [SESSION 3adfe568 2026-10-20T17:57]

## Alternatives considered

- Refuse non-clients at sign-in ("You are not a registered client").
- A lead role with a limited shell (chosen).
- For guests: no access before sign-in vs a short guest chat (chosen; the agent first explained the Google refusal as an OAuth registration problem, the owner reframed it).

## Decision

- `POST authentication/google` with `allow_lead=1` returns a lead JWT when no contact exists (`tblcustomers_api_leads`, cases migration 254); the lead shell has Home, Enquiries, More: desk, Agreement Check, My Enquiries, Become A Client [GIT client 69f546f; customers_api 1cc92a7].
- Because the JWT key was `base_url()` (public), a lead token counts only when equal to the stored token (`customers_api_lead_from_request()`) [CODE customers_api@97de5b4 helpers/customers_api_lead_helper.php:16-59].
- Lead fees paid on `cases/landing/lead_pay/{hash}`; billed through a contactless client linked by `tblclients.leadid`; web approval gate on lexon registration (`registration_confirmed=0` until KYC approval) [MEMORY project_leads_in_app.md].
- Guest desk: 2 turns × 140 chars, short AI answers (`cases_desk_guest_reply`, `CASES_DESK_GUEST_TURNS/CHARS`), web `Landing::ajax_desk_guest`, app `guest/desk` (20/h per IP; app 2/day per install, keyed by a per-install UUID) [GIT customers_api 20914b0] [CODE lex@6c069eb K/feature/desk/DeskData.kt:117-135].

## Reasons

- Every Google sign-in becomes a chance to start a matter.
- Leads cannot reach client data: a lead token is refused by `isAuthorized()` (no `contact_id`) [CODE customers_api@97de5b4 helpers/customers_api_helper.php:155-164].

## Later changes

- 2026-10-20: lead registration wizard "Open An Account" with firm approval (client e9825d1, customers_api 18d535e) and guest desk (client d8021b7…a4a6290).
- 2026-10-23: rejected registration can be fixed and resubmitted; approval detected on resume (client 743ea87).
- 2026-10-24: "Onboard Now" replaces "Open An Account"; account opening by conversation (see ADR-006).
- 2026-11-04: the client JWT key became secret (ADR-018); the stored-token check for leads remains.

## Status

Implemented in the client app and customers_api. Google sign-in on release builds needs the Android OAuth client registered with the Play signing SHA-1 [SESSION 3adfe568 C22]; status unknown.

## Open questions

- With the JWT key now secret, should lead tokens also be signature-checked?
