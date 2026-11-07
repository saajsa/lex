# ADR-018: Client Token Signing Key Change (e3027f0)

> Part of the Legum Lex Android knowledge base. Verified against: client app 6c069eb, staff app e52a8ca, customers_api 97de5b4, api 9df1506, cases cb0a820 (2026-11-07). Why client-app JWTs are now signed with a server secret, and what it did to signed-in users.
>
> This file is identical in legum-lex-android/docs/kb and legum-lex-staff/docs/kb.

## Requirement

GTM fact sheet finding: the client JWT was signed with a public value (`base_url()`) [SESSION 3adfe568 2026-11-01T10:18]. Fixed as GTM item 0.2 in the Web agent's session [SESSION 89b46f22 C1-C2 2026-11-04].

## Alternatives considered

- Keep `base_url()` (public: anyone could mint a token shape; leads already relied on stored-token equality instead).
- A tracked random `jwt_key` [ASSUMPTION: implied, since the tracked `jwt_key` was blanked rather than filled].
- Derive the secret from `APP_ENC_KEY` (chosen).

## Decision

`hash_hmac('sha256','customers_api.token.v1', APP_ENC_KEY)`; the tracked `jwt_key` blanked (customers_api e3027f0, 2026-11-04 10:48) [CODE customers_api@97de5b4 libraries/Authorization_token.php:63-96].

## Reasons

- Closes token forgery; a 64-hex secret also fixes the dev "Provided key is too short" failure [MEMORY project_mobile_app_backlog.md item 5].

## Later changes

None. The staff API has its own key and was not changed [SESSION 89b46f22 2026-11-04T09:51].

## Status

Implemented in customers_api. Effect: every client token issued before the deploy fails to verify, so every client-app user is signed out once (the app ends the session on 401 [CODE lex@6c069eb K/data/api/AuthInterceptor.kt:14-27]). Lead tokens are unaffected (stored-token equality). If `APP_ENC_KEY` is missing on an environment, token issue throws. No app change needed.

## Open questions

- Has e3027f0 reached production, and were testers told about the forced sign-in?
