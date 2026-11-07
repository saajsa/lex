# ADR-007: No Legal Notice Creation In The Client App

> Part of the Legum Lex Android knowledge base. Verified against: client app 6c069eb, staff app e52a8ca, customers_api 97de5b4, api 9df1506, cases cb0a820 (2026-11-07). Why clients view and approve notices but never create them, and how notices became paid first.
>
> This file is identical in legum-lex-android/docs/kb and legum-lex-staff/docs/kb.

## Requirement

> "This is a client app. The client app should not be able to create legal notices… expired agreements… should be archived… the consult desk must obey the theme of the app." [SESSION 3adfe568 2026-10-20T05:08]

## Alternatives considered

- Keep the client "Send-a-notice funnel" (`NewNoticeScreen`, present in 0.2.1).
- Remove creation; notices are raised by staff from a consultation ("Send notice") (chosen).

## Decision

App `NewNotice` removed (client 11e8a48, e130de9); `POST notices` and `legal_notices/extract` answer 403 `not_permitted` (customers_api adac96d; dead body kept); ended agreements archived (cases 8277e45) [GIT 2026-10-20]. The client can view notices, open documents (only `visible_to_customer=1`), track by reference and approve a Draft.

## Reasons

- A legal notice is the firm's professional act, drafted and sent by an advocate.

## Later changes

- Side effect flagged on 10-20: archiving at expiry also suppresses the web end-of-term question; the portal promises an Archived view that does not exist [MEMORY feedback_client_app_scope.md].
- 2026-11-05/07 (web): **notices paid first**. "That's not how legal notices work. If I draft and get paid later, he already has the legal notice… we invoice for the money first, send it afterwards…" [SESSION 89b46f22 2026-11-06T19:48]; "All notices have to be paid first, even if they are time-bound…" [SESSION 89b46f22 2026-11-06T19:50]. A fixed fee invoice (Nyay & Co.) is raised when staff start the notice; the draft stays hidden and despatch is refused until paid; approval raises no invoice. This reversed the 5 Nov "invoice at approval" design [GIT cases a1cb209, cb0a820; customers_api e68688b, 97de5b4].

## Status

- No creation: implemented in both client app and API.
- Pay-first: implemented on the web; additive API fields exist but are inert for app traffic (fee helper not loaded, see [wiring-and-traps.md](../backend/wiring-and-traps.md)); the client app has no "Pay the fee" step and the staff app no "Fee unpaid" badge.

## Open questions

- Should the client app show "Pay the fee" through native checkout (`invoice_id`) or `pay_url`?
