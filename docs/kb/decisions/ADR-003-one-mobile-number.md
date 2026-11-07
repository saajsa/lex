# ADR-003: One Mobile Number Per Person

> Part of the Legum Lex Android knowledge base. Verified against: client app 6c069eb, staff app e52a8ca, customers_api 97de5b4, api 9df1506, cases cb0a820 (2026-11-07). Why each contact and staff member has exactly one phone number, and what verification means.
>
> This file is identical in legum-lex-android/docs/kb and legum-lex-staff/docs/kb.

## Requirement

> "consolidate it into a single mobile number taken from a single source" [SESSION 3adfe568 2026-10-20T05:17]. Verifying a different number replaces the record; Remove stops codes and WhatsApp only [SESSION 3adfe568 C20].

## Alternatives considered

- Keep lexon `mobile_number` plus the messaging verification table as separate stores.
- One `phonenumber` field as the single source, with verification as a stamp (chosen after a three-run plan audit) [SESSION 3adfe568 C20-C21].

## Decision

- `tblcontacts.phonenumber` / `tblstaff.phonenumber` stored compact `+digits`; `tblmessaging_phones` is only a verification stamp, valid while it equals the record.
- Core helpers `lex_phone_normalize/store/holder` in `application/helpers/my_functions_helper.php` and hooks in `application/config/my_hooks.php` (tracked core files).
- Duplicates refused only on manual staff entry; otherwise logged.
- lexon migration 135 drops `mobile_number`, `mobile_verified`, `mobile_otp_sent_at`, `tbllexon_mobile_otp`.
- Client Profile shows the one number as Verified / Not Verified (client c9f0a11).
[MEMORY project_one_mobile_number.md] [GIT client c9f0a11; customers_api 2845923; messaging 2415888 2026-10-20]

## Reasons

- One number for sign-in codes, WhatsApp and SMS twins, and KYC, with no contradictions between stores.
- Razorpay passes `+` through, so the compact `+digits` form works for payments too.

## Later changes

- 2026-10-23: verified mobile is integral to registration (messaging migrations 003 'onboard', 004 'register') [SESSION 3adfe568 2026-10-23T18:16].
- 2026-11-01: "same person" links: a linked contact never verifies a number of its own; staff phone-on-record answers 409 [DOC cases/docs/developer/mobile_handoff_2026-11-01.md §2] [GIT api 9df1506]. See ADR-004.

## Status

Implemented in core, messaging, lexon, customers_api and the client app; pushed and deployed to production on 2026-10-20 [SESSION 3adfe568 C21]. The staff app verifies a client's mobile during onboarding (`client_onboard/phone/*`) [CODE lexco@e52a8ca J/feature/onboarding/OnboardingViewModel.kt:168-235].

## Open questions

None.
