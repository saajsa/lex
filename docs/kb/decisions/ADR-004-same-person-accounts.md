# ADR-004: One Person, Several Accounts, And The Account Switch

> Part of the Legum Lex Android knowledge base. Verified against: client app 6c069eb, staff app e52a8ca, customers_api 97de5b4, api 9df1506, cases cb0a820 (2026-11-07). How a person who is a contact of several clients switches account, and where the switch sits.
>
> This file is identical in legum-lex-android/docs/kb and legum-lex-staff/docs/kb.

## Requirement

The mobile handoff brief (from the owner's request to the Web agent: "We are yet to integrate and update our mobile apps with these features that we have built so far." [SESSION 19acbaeb 2026-11-01T19:31]) asked for a switcher "e.g. in the profile or drawer header" [DOC cases/docs/developer/mobile_handoff_2026-11-01.md]. Then: "I want the profile switch to be available next to the bell icon, with the switch icon." [SESSION 3adfe568 2026-11-01T21:11]

## Alternatives considered

- A "Your Accounts" card on Profile (first build, client 3ed3b7b).
- A drawer header (the client has no drawer).
- A switch icon beside the bell in the header (owner's choice).

## Decision

- Server: messaging migration 005 links contacts that are the same person; customers_api `GET accounts`, `POST accounts/switch`, `accounts/switch/{status,sms,verify}`; JWT `person` claim at OTP sign-in and switch [GIT messaging ee122f7, 658e5e7, 3eb8f17; customers_api caa66a8].
- Proof: a token carrying `person` switches without a code; otherwise one login code goes to the holder's verified number (WhatsApp, SMS fallback) [CODE customers_api@97de5b4 controllers/v1/Accounts.php:167].
- App: `AccountSwitch` dropdown in `AppHeader`, shown only with 2+ accounts, keyed to the signed-in contact; on success the app saves the new token, clears hubs and doc cache, adds the Beams interest and restarts MainActivity [CODE lex@6c069eb K/feature/profile/Accounts.kt:82-269; K/data/auth/AuthRepository.kt:109-122].

## Reasons

- One person with several client relationships should not juggle sign-ins.
- The header placement follows the owner's preference for fewer screens and immediate access.

## Later changes

- 2026-11-01 20:10: Your Accounts card on Profile (client 3ed3b7b).
- 2026-11-01 21:21: moved beside the bell; keyed to the signed-in contact to fix a stale list after re-sign-in (client 6c069eb) [SESSION 3adfe568 2026-11-01T21:22].

## Status

Implemented in the client app at 6c069eb. Old accounts' Beams interests are kept after a switch, so pushes for every account used on the phone keep arriving (sign-out clears all). The optional staff "same person" linking from the brief is not done [SESSION 3adfe568 C38].

## Open questions

- Is keeping old accounts' push interests after a switch intended?
