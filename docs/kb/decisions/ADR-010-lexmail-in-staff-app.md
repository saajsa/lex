# ADR-010: Lexmail In The Staff App

> Part of the Legum Lex Android knowledge base. Verified against: client app 6c069eb, staff app e52a8ca, customers_api 97de5b4, api 9df1506, cases cb0a820 (2026-11-07). Scope, placement and safety of mail in the staff app.
>
> This file is identical in legum-lex-android/docs/kb and legum-lex-staff/docs/kb.

## Requirement

> "Next big feature that we need to add to staff app is Lexmail." [SESSION 3adfe568 2026-10-29T19:39]; tab order: "Litigation can go the first one. Push Consultation to first and then lexmail" [SESSION 3adfe568 2026-10-29T19:44]

## Alternatives considered

AskUserQuestion [SESSION 3adfe568 2026-10-29T19:43]: scope read + reply (recommended), read only, or everything; entry top-bar icon (recommended), Home card, or a bottom tab; push none (recommended) or on new mail.

## Decision

Read + reply, a **bottom tab** (bar: Consultations, Mail, Home, Office, Lexcent; Caseboard now opens from the Home Litigation card), no push. Shared `Mail_service` used by web and API; HTML in a sandboxed WebView (JavaScript off, network loads blocked until "Show Images"); 24 MB attachment cap; forwards carry their attachments (also fixed on the web); connecting a mailbox stays on the web; a 403 hides the tab [GIT api 50540c9, e7f9b4a; lexmail 74ce3b1, ac8c6ac, 7156e87, acfa72e; staff cb3e278, f581c06] [CODE lexco@e52a8ca J/feature/mail/].

## Reasons

- Advocates answer client mail on the move; one mail service keeps the web and the app identical (rule F1).
- Read + reply keeps the surface small; no push avoids noise.

## Later changes

- 2026-10-30: sender pictures (ADR-011).

## Status

Implemented and pushed. Read-only and no-access states not phone-tested [SESSION 3adfe568 C36].

## Open questions

None.
