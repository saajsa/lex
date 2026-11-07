# Legum Lex Android knowledge base

> Verified against: client app 6c069eb, staff app e52a8ca, customers_api 97de5b4, api 9df1506, cases cb0a820 (2026-11-07). Start here.
> This file is identical in legum-lex-android/docs/kb and legum-lex-staff/docs/kb.

This knowledge base records what the two Legum Lex Android apps do, why they were built that way, what they depend on in the backend, and what is unfinished. It was recovered on 2026-11-07 from session records, memory notes, git history and the code itself.

It serves three readers:
- the Technical Advisor overseeing both development agents;
- the Android Development Agent;
- the Web Development Agent, when an API change touches the apps.

## The two apps

| | Client app | Staff ("Admin") app |
|---|---|---|
| Repository | `legum-lex-android` (`saajsa/lex`) | `legum-lex-staff` (`saajsa/lexco`) |
| Package | `com.legumlex.client` | `com.legumlex.staff` |
| Launcher name | Legum Lex | Lex Co |
| Users | Clients and leads of the firm | Staff, advocates, administrators |
| Backend API | `customers_api/v1` (JWT per contact) | `api` (staff `Authtoken`) |
| Version at HEAD | 0.4.1 (code 8) | 0.2.0 (code 4) |

Both apps are native Kotlin and Jetpack Compose, and both talk to the firm's Perfex CRM platform.

## Layout

The same `docs/kb/` tree exists in both repositories. Only `app/` differs: it describes the app in that repository.

| Path | Contents | Identical in both repos? |
|---|---|---|
| `README.md` | This index | yes |
| `sources.md` | Session IDs and spans, memory notes, plans, commit ranges, exclusions | yes |
| `app/architecture.md` | Packages, navigation, shell, state, data flow | no |
| `app/features.md` | Each feature folder: screens, endpoints, status | no |
| `app/auth.md` | Sign-in, tokens, session restore, error handling, sign-out | no |
| `app/integrations.md` | API client, push, realtime, payments, documents | no |
| `app/build-test-release.md` | Gradle, build types, phone testing, signing (no secrets), release | no |
| `app/docs-drift.md` | Where the older `README.md` and `docs/*.md` are now wrong | no |
| `shared/owner-rules.md` | Dated ledger of the owner's instructions, current or superseded | yes |
| `shared/conventions.md` | Code, design-system, copy and form conventions | yes |
| `shared/apps-compared.md` | What the two apps share and where they differ | yes |
| `shared/timeline.md` | Chronological history with sessions and commits | yes |
| `shared/open-items.md` | Unfinished, untested, drift, assumptions, stale memory notes | yes |
| `backend/customers-api.md` | Client API contract (verified commit in the header) | yes |
| `backend/staff-api.md` | Staff API contract (verified commit in the header) | yes |
| `backend/wiring-and-traps.md` | How API requests run, and the traps | yes |
| `backend/data-structures.md` | Shared payloads and enums | yes |
| `backend/known-issues.md` | Integration problems, with who should fix them | yes |
| `backend/coordination.md` | How the Web agent, Android agent and Advisor change an API safely | yes |
| `decisions/` | One record per significant decision (ADR style), indexed in `decisions/README.md` | yes |

## Evidence labels

Every factual claim carries one of these labels. A claim never gets a stronger label than its weakest support. Where sources disagree, both are given and the newer one is named.

| Label | Meaning |
|---|---|
| `[CODE repo@hash path:line]` | Verified by reading the code at that commit |
| `[GIT hash date]` | Commit message or diff |
| `[SESSION sid8 timestamp]` | Session record: a compaction summary or the owner's own message (UTC) |
| `[MEMORY file]` | Memory note; may be stale, cross-checked where possible |
| `[DOC path]` | Existing project documentation |
| `[ASSUMPTION]` | Inference not verified; also listed in `shared/open-items.md` |

**Intended vs implemented.** Pages separate what the owner asked for (intended) from what the code does at the verified commit (implemented) wherever the two differ.

## Keeping it true

**After changing an app,** update `app/`, the relevant `decisions/` record and `shared/timeline.md`, and record the new commit in each file's header.

**After a backend change to `customers_api` or `api`,** the Web Development Agent follows `backend/coordination.md` and re-verifies the affected contract table against the new commit.

**Shared files** (everything outside `app/`) must stay byte-identical in both repositories: edit once, then copy to the other repository.

**Canonical source (owner decision 2026-11-07, REQ-000):** the client repo's copy (`legum-lex-android/docs/kb/`) is canonical for every shared file, and its `backend/` is the canonical API contract for the whole platform. The web KB's `10-android-api-contracts.md`, `10a` and `10b` are summaries. The Technical Advisor maintains the shared files. The drift check is `htdocs/docs/agents/coordination/tools/contract_drift.sh`. **[Owner decision 2026-11-07, REQ-002]** Target: **one authoritative web-side contract source**, with Android aligned to it. The client repo's `backend/` is the interim canonical copy until the web-side source exists (`docs/agents/coordination/contracts.md`).

**Never put a secret, token, key, password or a real client's personal data in these files.**

## Most important findings at 2026-11-07

These are summarised here; details and evidence are in `shared/open-items.md` and `backend/known-issues.md`.

1. **The apps silently skip three newer backend features.** The helpers for plan limits and view-only contacts, notice fees, and file-access logging load only from `cases/cases.php`. App API requests do not load them, so for app traffic those checks are skipped without any error.
2. **Neither app knows about the backend changes made after its last commit (1 Nov).** Those changes include view-only contacts, notice fees, the `plan_limit` answer, and project membership open to any active staff member. A new token-signing key also signs out every client-app user once, when it reaches production.
3. **The staff API answers a bad or expired token with 404, not 401.** The staff app has no handler for this, so an expired session shows as "not found".
4. **The client app never asks for notification permission.** It declares the permission but does not request it at runtime, so on Android 13 and later push notifications may be silent.
5. **The older docs in both repositories are out of date** (19 and 21 Oct). Each `app/docs-drift.md` lists where.

## Open questions

Collected in `shared/open-items.md` (unfinished and untested work, backend drift after 1 Nov, every `[ASSUMPTION]`, and memory notes found stale).
