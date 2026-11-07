# ADR-011: Lexmail Sender Pictures

> Part of the Legum Lex Android knowledge base. Verified against: client app 6c069eb, staff app e52a8ca, customers_api 97de5b4, api 9df1506, cases cb0a820 (2026-11-07). Where the picture beside a sender comes from, and how people and brands are drawn.
>
> This file is identical in legum-lex-android/docs/kb and legum-lex-staff/docs/kb.

## Requirement

> "How can we implement Lex Mail to identify branding if it is provided by the mailer?" [SESSION 3adfe568 2026-10-30T13:11]; plan rejected first with: "Audit the plan so that it does not conflict with the existing functionality … look for any logical gaps and the practicality" [SESSION 3adfe568 2026-10-30T13:22]

## Alternatives considered

Answers chosen: Google Workspace logo; web and app; Gmail-style; logos in colour, people in grey [SESSION 3adfe568 2026-10-30T13:11].

## Decision

`Mail_avatar` (lexmail 569e14c, migration 109) resolves in order: the mailbox's Google picture, a staff photo, a contact photo, then a BIMI logo. DNS lookups only in cron; API schema-tolerant. The staff app draws people in grey and logos in colour (SVG via `coil-svg`), else initials (staff cfc70cb) [MEMORY project_lexmail_sender_pictures.md] [CODE lexco@e52a8ca J/feature/mail/MailRepository.kt:53-57].

## Reasons

- Recognisable senders without fetching remote content at read time.

## Later changes

None after 2026-10-30.

## Status

Implemented and pushed in lexmail and the staff app on 2026-10-30.

## Open questions

None.
