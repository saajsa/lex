# ADR-019: Notifications: Existing Infrastructure, Server Targets, App-Drawn Pushes

> Part of the Legum Lex Android knowledge base. Verified against: client app 6c069eb, staff app e52a8ca, customers_api 97de5b4, api 9df1506, cases cb0a820 (2026-11-07). Where notifications come from in each app and how a tap opens the right record.
>
> This file is identical in legum-lex-android/docs/kb and legum-lex-staff/docs/kb.

## Requirement

Client: build notifications only if no infrastructure exists [SESSION 3adfe568 2026-10-19T16:39]. Staff: "Opening a consultation must acknowledge it, and that should be implemented in a foolproof way" / "Tapping a notification should take you to the exact function" [SESSION 3adfe568 2026-10-26T19:21]; "The notifications sent to the phone, do not carry the logo of the company." [SESSION 3adfe568 2026-10-27T02:15]

## Alternatives considered

- Client: a new client notification store vs reuse of announcements and the action queue (chosen).
- Staff dev push guard [SESSION 3adfe568 2026-10-29T09:36]: allow-list (recommended, chosen), block all, leave as is.

## Decision

- **Client bell** = firm announcements (`tblannouncements` showtousers=1, read via `tbldismissed_announcements`) + "Requires Your Attention" (funnel matters + action queue `Action_queue_model::attention()`); action queue merged into Notifications on 10-20 [GIT customers_api 1d18681, ce9cf58] [SESSION 3adfe568 C18-C20].
- **Staff**: the server resolves a target (`api_notification_target($link)`); the app maps `screen` to a route, otherwise opens the web; every bell rings the phone; pushes are data-only at high priority so the app draws them with the logo; a tap marks the inbox row read; opening a client-raised consultation acknowledges it server-side (idempotent) [GIT api b05b4dd, e88b436; staff feb5859, 59f235e] [CODE lexco@e52a8ca J/push/NotifTarget.kt].
- Dev allow-list `lex_dev_push_allow` [SESSION 3adfe568 2026-10-29T09:36].

## Reasons

- Reuse what the web already has (rule F1); one place decides where a tap goes.

## Later changes

- Deferred: per-person channel settings, batching, quiet hours (web first) [SESSION 3adfe568 2026-10-26T19:57].
- Registry notifications open the web ("Do not implement the registry yet.").

## Status

Implemented at both HEADs. Open owner questions: a client-specific notification store; bell badge counting waiting items; unread count `isread` vs `isread_inline` [SESSION 3adfe568 C18] [SESSION 3adfe568 audit ledger 2026-10-28].

## Open questions

None.
