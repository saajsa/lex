# ADR-001: Web-First Parity

> Part of the Legum Lex Android knowledge base. Verified against: client app 6c069eb, staff app e52a8ca, customers_api 97de5b4, api 9df1506, cases cb0a820 (2026-11-07). Why an app may only carry functions the web app already has.
>
> This file is identical in legum-lex-android/docs/kb and legum-lex-staff/docs/kb.

## Requirement

> "No function that does not exist on the web app can be adopted on the mobile app. This is a firm rule." [SESSION 3adfe568 C18 ~2026-10-19T12:30]

Asked when the agent proposed an app-only Consult desk for existing clients. Followed by: "Go ahead, and add the live self-view to the web first" [SESSION 3adfe568 2026-10-19T12:38].

## Alternatives considered

- Build app-only features (an app Consult desk; the app-only Digital Office live self-view as it existed).
- Adapt the web feature first, then render it natively in the app over an endpoint that runs the same server code (chosen).

## Decision

The web is the source of truth. New app endpoints are transport only and wrap the web's server code (e.g. `cases_desk_read()`, `Funnel_orchestrator`, `Digital_office_model::register_stats()`, `Consultations_model`) [SESSION 3adfe568 C18] [MEMORY feedback_web_first_parity.md]. If the app needs something the web lacks, the web is changed first.

## Reasons

- One product, one set of server rules; no drift between portal and app.
- Fixes land once, on the web, and reach both apps.

## Later changes

- 2026-10-19: Digital Office live self-view built on the web first (digital_office 19f7235) [GIT].
- 2026-10-22: staff app follows the web advocate workflow screen by screen: "The workflow for an advocate must work in a similar manner as the workflow works for an advocate on the the web app." [SESSION 3adfe568 2026-10-22T12:40]; bench address built on the web first [SESSION 3adfe568 C24].
- 2026-10-27: display casing "web first" [SESSION 3adfe568 2026-10-27T18:54].
- 2026-10-28: app-only staff features retired (My Cases scope, DO refund) and API writes moved onto the web models [GIT api 72981aa, 6e7d74b; staff 5b92392, c094c4c].
- 2026-10-29: client-only actions (Cancel, Cannot Send, Share): options "hide in app" (recommended) or "build on the portal"; owner chose build on the portal [SESSION 3adfe568 2026-10-29T09:36].
- 2026-11-01 and 2026-11-04: restated from the web side; mobile is Stage 4 of the GTM plan, after the web is ready [SESSION 19acbaeb 2026-11-01T19:31] [SESSION 89b46f22 2026-11-04T09:51].

## Status

Intended and implemented: in force at both app HEADs. Known exceptions still present: the staff app acknowledges a consultation on open, flagged as app-only behaviour the web lacks [MEMORY project_staff_app.md]; whether the web now does the same is unverified.

## Open questions

- Has the web adopted acknowledge-on-open, or is it still an app-only behaviour?
