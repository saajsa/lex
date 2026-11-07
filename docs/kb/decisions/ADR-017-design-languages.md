# ADR-017: Design Languages: Theme, Radii And Colour Pairings

> Part of the Legum Lex Android knowledge base. Verified against: client app 6c069eb, staff app e52a8ca, customers_api 97de5b4, api 9df1506, cases cb0a820 (2026-11-07). The look each app uses and the owner instructions that shaped it.
>
> This file is identical in legum-lex-android/docs/kb and legum-lex-staff/docs/kb.

## Requirement

- Client: "examine the Mylex design language, components, and UI, and then implement the same design language on our mobile app" [SESSION 3adfe568 2026-10-19T10:09]; "The dark theme is apparently too black … looks like a negative theme of a lighter theme." [SESSION 3adfe568 2026-10-19T11:07]; radii: "the curves are really strong, and the radius is higher than what the client app usually uses." [MEMORY feedback_client_app_radii.md, owner 2026-10-24]
- Staff: "I also want to change the theme and design language of my app, along with the icon." [SESSION 3adfe568 2026-10-21T18:16]; "For the icon, you must use the Legumlex Mono icon that we're already using for our client app. Just add a staff badge… Design the login… keep it without any radius" [SESSION 3adfe568 2026-10-21T19:18]; "black text on a yellow background is good, and a black background with white text" [SESSION 3adfe568 2026-10-22T16:03]
- Both: light default [SESSION 3adfe568 2026-10-23T09:58] [SESSION 3adfe568 2026-10-24T17:05].

## Alternatives considered

- Staff theme options offered on 10-21: client app family (recommended), identical, or a distinct identity; the owner answered with a reference image, giving "Edge".
- Staff icon: inverted client mark (first choice), then the client mono mark with a badge (owner's correction).
- Client dark theme: inverted light (rejected as "negative") vs layered dark (chosen).

## Decision

- **Client**: Mylex zinc tokens (`LexColors`), layered dark (#141417 page / #1C1C20 card), warm-grey palette with paper grain, colour only red/green, Barlow Condensed titles, Inter, Fraunces in Digital Office, yellow #F5C518 Consult button with black type, radii 4/6/8/12, Appearance switch Follow Phone/Light/Dark [GIT client d0a4397, dc6cf8d, 1ba0f64, 3a1c08d, ad85d82] [CODE lex@6c069eb K/ui/theme/].
- **Staff**: Edge (white page, heavy type, Manrope static cuts, colour only good/bad), Frame sign-in/splash with no radius, then yellow #FFC21A / ink pairings from the Caseboard reference, text only on solid pairs, raised Home, no ripple; icon = client mono mark + badge, "STAFF" then "Co." (black on yellow) [GIT staff 7237b66, cbb6ed5, f55dc2a, 1f65a28] [CODE lexco@e52a8ca J/ui/theme/Color.kt; J/feature/caseboard/Folder.kt:42-51; tools/staff_icon.py].
- Both: flat, no gradients (except the client Home picture cards' fading tint, which the owner asked for) [MEMORY feedback_flat_design.md].

## Reasons

- The client app should feel like the client web portal; the staff app has its own identity under the same brand mark.

## Later changes

- 2026-10-19: logo replaced with the company mark ("You are using a logo that I did not approve" [SESSION 3adfe568 2026-10-19T13:47]).
- 2026-10-22: staff edge rail and side panel removed (a36b066).
- 2026-10-25: staff badge "STAFF" → "Co." (1f65a28).
- 2026-10-26: UI refinement reserved to the owner (rule F23).

## Status

Implemented at both HEADs. Repo design docs are stale (staff `docs/design/edge.md` still describes the edge rail and "STAFF" badge) [DOC legum-lex-staff/docs/design/edge.md].

## Open questions

None.
