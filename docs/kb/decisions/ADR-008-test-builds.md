# ADR-008: Self-Updating Test Builds: Built, Then Removed

> Part of the Legum Lex Android knowledge base. Verified against: client app 6c069eb, staff app e52a8ca, customers_api 97de5b4, api 9df1506, cases cb0a820 (2026-11-07). Why both apps briefly had a self-updating "Test" copy and why it must not come back unasked.
>
> This file is identical in legum-lex-android/docs/kb and legum-lex-staff/docs/kb.

## Requirement

> "Can we install an inline update for internal testing instead of uploading again and again" [SESSION 3adfe568 2026-10-30T06:19]; the intent: stop uploading builds to Play for small changes and test on production data [SESSION 3adfe568 2026-10-30T08:21].

## Alternatives considered

- Own update server (chosen), Firebase App Distribution, Play in-app update prompt.
- GitHub Actions or GitHub uploads: rejected, "No GitHub, own server only" [SESSION 3adfe568 2026-10-30T06:21].

## Decision

Built and pushed: a `preview` build type (AGP reserves `test`), `.test` packages, `api/app_update` store, cases migration 264, `app_test_contacts`, publish.sh/upload.py (api 42bbda2, customers_api 322d7d7, cases f74ea5a, staff 8d64030 + b61279e, client 053c482) [GIT 2026-10-30].

## Reasons

- At the time: avoid repeated Play uploads.
- On review, the agent judged it too big for one phone that is normally plugged in; a USB-installed preview build would have been enough [SESSION 3adfe568 2026-10-30T08:22].

## Later changes

1. "this is much bigger than I had expected … we will undo what we have built so far, but safely" [SESSION 3adfe568 2026-10-30T08:39]: reverted with `git revert` and pushed (api 318d34d, customers_api 26de484, cases 227d167, staff 5c1054d + 4f07886, client 45f591e + efe89b9). A minimal USB preview copy was kept.
2. Google sign-in failed in it (`.test` package has no Android OAuth client).
3. "Uninstall the Lex Co Test. We do not require it." / "Remove it from both apps and push" [SESSION 3adfe568 2026-10-30T09:54-09:55]: preview build type removed (staff c249409, client 8da8bbe); test keystore and `TEST_STORE_*` lines deleted [SESSION 3adfe568 C38].

## Status

Removed. Only `debug` and `release` exist in both apps [CODE lex@6c069eb app/build.gradle.kts] [CODE lexco@e52a8ca app/build.gradle.kts]. Do not reintroduce unless asked [MEMORY project_app_test_builds.md] (that note is stale where it calls the keystore a leftover). The install route is debug over USB.

## Open questions

None.
