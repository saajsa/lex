# ADR-009: Staff Profile Is The Menu And Mirrors The Nyayco Counsel Page

> Part of the Legum Lex Android knowledge base. Verified against: client app 6c069eb, staff app e52a8ca, customers_api 97de5b4, api 9df1506, cases cb0a820 (2026-11-07). Why the staff Profile replaced the menu and shows exactly what nyayco.com shows.
>
> This file is identical in legum-lex-android/docs/kb and legum-lex-staff/docs/kb.

## Requirement

> "The profile screen should replace the menu screen completely." [SESSION 3adfe568 2026-10-25T15:54]
> "any information in the council profile should be the same as what is shown on the Nyayco or council profile. Even for admin" [SESSION 3adfe568 2026-10-30T09:09]

## Alternatives considered

- Keep a separate Menu/More screen plus a profile (removed).
- Build the counsel profile in the API separately from the site (rejected: two builders drift).
- One builder used by the site and the API (chosen).

## Decision

- Profile replaces Menu; the More route is removed; every "you"/menu button opens Profile (staff baada46) [CODE lexco@e52a8ca J/ui/navigation/NavGraph.kt:75-76].
- Profile layout from the owner's reference: full-bleed grey portrait, black panel, counsel data (api fe40218, staff de07305).
- One builder `cases_nyay_profile()` used by the Nyayco `modals.php` and `auth/profile` (`staff.nyayco`), for admins too (cases 0c93b97, api fc8b425, staff ed9c176). Verified by building Nyayco twice and diffing [SESSION 3adfe568 2026-10-30T09:30].
- Figures: Active Clients, Live Matters, Open Projects; "Across The Firm" for admins (api 67b2363, staff 101dae7).

## Reasons

- Fewer screens; the site and the app cannot drift.

## Later changes

- 2026-10-25 20:01: Calendar and Cause List added under Work on Profile (staff 339e7c4).

## Status

Implemented at e52a8ca. Someone not on Nyayco gets the older screen; on dev only one staff member is on Nyayco. All profile edits happen on the web ("Edit On The Web"); the staff API has no profile update [CODE lexco@e52a8ca J/feature/more/ProfileScreen.kt:113-115].

## Open questions

None.
