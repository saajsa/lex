# ADR-022: Client App Navigation

> Part of the Legum Lex Android knowledge base. Verified against: client app 6c069eb, staff app e52a8ca, customers_api 97de5b4, api 9df1506, cases cb0a820 (2026-11-07). Why the client bar is Home, Consultations, Consult, Notices, More, with Digital Office in the header.
>
> This file is identical in legum-lex-android/docs/kb and legum-lex-staff/docs/kb.

## Requirement

> "Do we really need navigation for the same at the bottom? Digital office is another important function that we do not want to dilute…" [SESSION 3adfe568 2026-10-19T11:12]; "We don't need a consult card… Consult in the middle" [SESSION 3adfe568 2026-10-20T12:20]; Notices replaces Calendar [SESSION 3adfe568 2026-10-20T13:45]; "Instead of 'opening an account,' it should say 'Onboard Now.'" [SESSION 3adfe568 2026-10-24T16:43]

## Alternatives considered

- 10-19: four tabs Home · Consultations · Digital Office · More, workspaces as Home cards not tabs.
- 10-19 evening: action queue as an icon beside the bell (reversed 10-20).
- 10-20: notched bar with a centre Consult (chosen).

## Decision

Black notched bar: Home, Consultations | raised yellow Consult (blinking cursor, opens the desk) | Notices, More. Digital Office is a camera icon in the header beside the bell; the account switch sits there too; the action queue lives inside Notifications. `navigateTab()` always pops to Home and opens each tab at its root, without saving tab state. Leads get Home, Enquiries, More [CODE lex@6c069eb K/ui/navigation/BottomNav.kt:31-116; Routes.kt:310-327; NavGraph.kt:752-771].

## Reasons

- Digital Office is important enough to sit apart from the tabs; Consult is the primary action.

## Later changes

- 2026-10-24: Home shows "What's On" first; "Services" not "Workspace" (client 1f2460a, 6a94364).
- 2026-11-01: account switch beside the bell (ADR-004).

## Status

Implemented at 6c069eb. Repo `docs/ARCHITECTURE.md` still describes the old bar [DOC legum-lex-android/docs/ARCHITECTURE.md].

## Open questions

None.
