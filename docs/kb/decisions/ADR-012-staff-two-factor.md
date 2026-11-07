# ADR-012: Staff Two-Factor Sign-In

> Part of the Legum Lex Android knowledge base. Verified against: client app 6c069eb, staff app e52a8ca, customers_api 97de5b4, api 9df1506, cases cb0a820 (2026-11-07). How the staff app honours a staff member's two-factor setting.
>
> This file is identical in legum-lex-android/docs/kb and legum-lex-staff/docs/kb.

## Requirement

Owner decision on OTP sign-in (2026-10-19): staff with 2FA must still pass it [SESSION 3adfe568 C16 2026-10-19T09:22]. The mobile handoff brief section 4 asks for two-factor sign-in in the staff app [DOC cases/docs/developer/mobile_handoff_2026-11-01.md].

## Alternatives considered

No alternatives were recorded. The 2026-10-19 decision (staff with 2FA must still pass it) ruled out skipping 2FA for app tokens; the build mirrors Perfex's own email and authenticator codes with a signed reference.

## Decision

`auth/login`, `auth/google` and `auth/otp/verify` call `two_factor_start()` when `two_factor_auth_enabled` is set: email codes are sent at once; the answer is `{status:true, two_factor:"email"|"app", ref, to}` with an HMAC `ref` valid 15 minutes. `POST auth/two_factor {ref, code}` → token (422 `expired` / `wrong`) [CODE api@9df1506 controllers/Auth.php:51,132,191,646-711]. App: `TwoFactorNeeded(ref, kind, to)` → `LoginUiState.TwoFactor` → one-step WizardFrame "Two-Factor Sign-In" (NumberPassword for app codes, text for email) [CODE lexco@e52a8ca J/feature/auth/LoginScreen.kt:443-463; J/data/auth/AuthRepository.kt:16,84-87].

## Reasons

- Same assurance as the web; no new secret in the app.

## Later changes

- 2026-11-04 (web): option `cases_require_admin_2fa` forces admins without 2FA to set it up, in web sessions only; the staff API does not check it [CODE cases@cb0a820 cases.php:2242-2257].

## Status

Implemented at api 9df1506 / staff e52a8ca; **not phone-tested**. Gap: admin 2FA enforcement not applied to the staff API (known issue K8).

## Open questions

None.
