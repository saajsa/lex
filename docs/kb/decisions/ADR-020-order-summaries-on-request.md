# ADR-020: Order Summaries On Request Only

> Part of the Legum Lex Android knowledge base. Verified against: client app 6c069eb, staff app e52a8ca, customers_api 97de5b4, api 9df1506, cases cb0a820 (2026-11-07). Why court-order summaries are generated only when staff press a button.
>
> This file is identical in legum-lex-android/docs/kb and legum-lex-staff/docs/kb.

## Requirement

> "Automatic order summaries will waste tokens… Order summaries should be optional only" [SESSION 3adfe568 2026-10-23T09:58]

## Alternatives considered

- Summarise every uploaded order automatically (previous behaviour).
- Summarise on request (chosen).

## Decision

cases migration 258 adds `ocr_status 'none'`; staff press "Generate summary" (`POST api/hearings/{id}/summarise`); the client app shows no placeholder line for an order without a summary (client a80ce24, customers_api 9db3c0d) [GIT api f12dc3a; cases mig 258] [MEMORY feedback_order_summaries_on_request.md].

## Reasons

- AI cost control.

## Later changes

- 2026-11-04 (web): a monthly AI call cap at each Claude client [GIT lexon 7f56a67, cases d62b845]; refusal handling in the apps unverified.

## Status

Implemented on web, staff and client.

## Open questions

None.
