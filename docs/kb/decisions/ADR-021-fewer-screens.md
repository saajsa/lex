# ADR-021: Fewer Screens: Tabs, Folded Cards, Inline Edits

> Part of the Legum Lex Android knowledge base. Verified against: client app 6c069eb, staff app e52a8ca, customers_api 97de5b4, api 9df1506, cases cb0a820 (2026-11-07). Why project details became tabs (client) and a folded card (staff), and why edits are inline.
>
> This file is identical in legum-lex-android/docs/kb and legum-lex-staff/docs/kb.

## Requirement

> "I do not like too much scrolling, so use tabs" [SESSION 3adfe568 2026-10-25T16:18]
> "An additional screen means an additional workflow and an additional tab, which is extra work for the user." [SESSION 3adfe568 2026-10-29T15:02]
> "I want an inline edit instead of editing it on the new screen. The new screen is a waste." [SESSION 3adfe568 2026-11-01T20:55]

## Alternatives considered

Staff project details [SESSION 3adfe568 2026-10-29T15:02]: "Fold it into the card (Recommended)", "Keep the screen, drop swipe", "Keep everything".

## Decision

- Client project page: head, then tabs (Stages, Tasks, Files, Discussions), then an ⓘ dialog, nothing repeated (client e988434); separate screens kept for notification links.
- Staff: ProjectDetailScreen deleted; the My Work card gains stage actions and reassignment; deep links open My Work with the card focused (`focusProjectId`) (staff 6702227, 821b2fd, 97b9be8).
- Client invoice: amount and gateway inline; the pay wizard screen removed (client 8772b0d).
- Staff: no Hearings screen ("Hearings screen is not needed. Cases screen provides for hearings." [SESSION 3adfe568 2026-10-23T07:00]); hearings open in place.

## Reasons

- Every extra screen is an extra workflow for the user.

## Later changes

- 2026-10-25: swipe left to Open replaced the ⋯ menu (both apps); removed in the staff app on 10-29 when the detail folded into the card; the client app keeps it [CODE lex@6c069eb ProjectsScreen.kt:211].
- The 10-29 quote opens with "We really need the project details screen", which by context and the chosen answer means "don't need" [ASSUMPTION].

## Status

Implemented at both HEADs.

## Open questions

None.
