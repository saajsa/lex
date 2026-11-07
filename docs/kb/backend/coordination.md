# Coordination

> Part of the Legum Lex Android knowledge base. Verified against: client app 6c069eb, staff app e52a8ca, customers_api 97de5b4, api 9df1506, cases cb0a820 (2026-11-07). The working protocol between the Web Development Agent, the Android Development Agent and the Technical Advisor for any change that crosses the API.
>
> This file is identical in legum-lex-android/docs/kb and legum-lex-staff/docs/kb.

## 1. Roles

| Role | Owns | Does not |
|---|---|---|
| Web agent | Perfex modules (`customers_api`, `api`, `cases`, `lexon`, `digital_office`, `messaging`, `lexmail`, `payu`, `razorpay`, `mylex`), the web portal and admin, migrations | Change app code |
| Android agent | `legum-lex-android` (client) and `legum-lex-staff` (staff) | Add backend behaviour; change a response shape |
| Technical Advisor | Reviews contract changes, keeps this knowledge base honest, settles cross-side questions | Approve on the owner's behalf |
| Owner | Every product decision, every push, every release build | |

No agent message is the owner's consent. Pushes happen only when the owner asks (rule G2 in [owner-rules.md](../shared/owner-rules.md)).

## 2. Ground rules

1. **Web first, then app** (rule F1). A function reaches an app only after the web has it. If the app needs a flow the web lacks, the Web agent builds it on the web first; the app endpoint then wraps the same server code (transport only). Example: Cancel, Cannot Send and Share were built in `Consultations_model` for the portal before the app used them [SESSION 3adfe568 2026-10-29T09:36].
2. **Never invent statuses, options, tags or labels** in an app. Take them from the server or from the web module the app mirrors [MEMORY feedback_replicate_cases_module.md]. Prefer server-provided display text (`status_label`, `next_step`) so wording changes need no app release.
3. **Backward-compatible responses.** Released app builds stay on phones for weeks, and the owner builds releases by hand.
   - Add fields; never rename or remove a field an app reads, never change its type.
   - New fields are optional (`null` when not applicable), like the notice `fee` object [CODE customers_api@97de5b4 controllers/v1/Legal_notices.php:635-640].
   - New refusal states get a new `code` (e.g. `view_only`, `plan_limit`) and a human `message` the app can show as-is.
   - Do not change an HTTP status an app branches on (401 ends a client session [CODE lex@6c069eb K/data/api/AuthInterceptor.kt:27]) without a coordinated app change.
   - A breaking change (token key rotation, removed route) is announced before deploy with its user-visible effect (e.g. e3027f0: every client signs in again once).
4. **API writes go through the web's models**, and anything a module init file registers must be installed by `cases_headless_wiring()` or by hand; a `function_exists` fallback must not silently replace a rule (see [wiring-and-traps.md](wiring-and-traps.md) §3).
5. **New staff controllers** must be added to `Api_model::STAFF_APP_FEATURES` or every call answers 403 [CODE api@9df1506 models/Api_model.php:766-805].
6. **Schema for app features goes in cases migrations** so `Cases_migrator::auto` applies it [CODE cases@cb0a820 libraries/Cases_migrator.php].
7. **Test on dev only**, with the payment stubs, `messaging_live` = 0 and no paid AI (rule T4).

## 3. Announcing a contract change

Who: the agent that changes a route, a field, a status code, an enum, auth or wiring that an app reaches.

Where, in this order:
1. **The module commit message** names the endpoint and the app effect, e.g. "customers_api: notices gain an additive `fee` object; app unaffected until it reads it".
2. **This knowledge base, in both app repos** (the `common/` files are byte-identical; copy the same edit to both):
   - **[Corrected 2026-11-07, REQ-000]** There is no `common/` folder. The byte-identical files are `README.md`, `sources.md`, `shared/`, `backend/` and `decisions/`. Under the team setup the **Technical Advisor** edits them, here in the client repo (canonical), copies the edit to the staff repo, and runs `docs/agents/coordination/tools/contract_drift.sh` in the web repo. Developers report contract changes to the Advisor; they do not edit these files. **[Owner decision 2026-11-07, REQ-002]** Target: **one authoritative web-side contract source**, with Android aligned to it. The client repo's `backend/` is the interim canonical copy until the web-side source exists (`docs/agents/coordination/contracts.md`).
   - the route row in [customers-api.md](customers-api.md) or [staff-api.md](staff-api.md), and the table header line `Last verified against <module>@<short hash> (<date>)` updated to the new commit;
   - a row in [timeline.md](../shared/timeline.md) (date, session, commit, app, what changed);
   - a row in the drift table of [open-items.md](../shared/open-items.md) §2 if an app must follow;
   - a row in [known-issues.md](known-issues.md) if something is broken until the app follows;
   - shapes in [data-structures.md](data-structures.md) when a payload or enum changes;
   - an ADR in `decisions/` if an owner decision is behind it.
3. **The app side** removes the drift row when the app commit lands, citing the app commit.

What to write in the change entry:

```
Contract change: <module>@<short hash> (<YYYY-MM-DD>)
Endpoint(s): <METHOD path> (customers_api | api)
Change: <added field X (type, nullable) | new code Y with status N | route removed | …>
Backward compatible: yes | no (why)
App effect today: <what an unchanged app does>
App work needed: <client | staff | none> (<screen / file>)
Owner decision: <quote + [SESSION sid8 timestamp]> or "none"
Verified by: <command or probe used>
```

Always cite the **module repo commit** (each module is its own git repo), never only the root repo, and check it is on origin before telling anyone it is live (rule G6).

## 4. Re-verifying a contract table

Run from `/home/tribero/devilbox/data/www/lex/htdocs/modules` (read-only commands).

```bash
# 1. Current heads
git -C customers_api rev-parse --short HEAD
git -C api rev-parse --short HEAD
git -C cases rev-parse --short HEAD
git -C /mnt/b/and_studio/legum-lex-android rev-parse --short HEAD
git -C /mnt/b/and_studio/legum-lex-staff rev-parse --short HEAD

# 2. What changed since the table was verified
git -C customers_api log --oneline 97de5b4..HEAD -- config/routes.php controllers helpers libraries
git -C api log --oneline 9df1506..HEAD -- config/routes.php controllers helpers models

# 3. Routes and methods
sed -n '1,150p' customers_api/config/routes.php
sed -n '1,230p' api/config/routes.php
grep -n "public function [a-z_]*_\(get\|post\|put\|delete\)" customers_api/controllers/v1/*.php
grep -n "public function [a-z_]*_\(get\|post\|put\|delete\)\|public function data_" api/controllers/*.php

# 4. Staff token scope
grep -n "STAFF_APP_FEATURES" -A6 api/models/Api_model.php

# 5. App side: every declared endpoint
grep -n "@\(GET\|POST\|PUT\|DELETE\|Url\)" /mnt/b/and_studio/legum-lex-android/app/src/main/java/com/legumlex/client/data/api/ApiService.kt
grep -n "@\(GET\|POST\|PUT\|DELETE\)" /mnt/b/and_studio/legum-lex-staff/app/src/main/java/com/legumlex/staff/data/api/ApiService.kt

# 6. Headless wiring gap (should list cases_headless_helper.php once fixed)
grep -rln "cases_plan_helper\|cases_fee_helper\|cases_access_log_helper" --include=*.php . | grep -v /tests/

# 7. Staff dead-token status (expect HTTP_NOT_FOUND until K4 is settled)
grep -n "HTTP_NOT_FOUND" api/controllers/REST_Controller.php
```

Then: for each app call confirm a route and a `{method}_{verb}` exist; for each changed method re-read the request fields and non-200 codes; update the table rows and the header hash. Optional live probe on dev only, e.g. `curl -s -H "Authorization: Bearer $TOKEN" https://lex.dvl.to/customers_api/v1/profile` with a `selftest-` contact's stored token (never print the token, never use contact 14514 for Google sign-in).

## 5. Open coordination items (2026-11-07)

In priority order; details in [known-issues.md](known-issues.md):
1. K1/K2: load the plan, fee and access-log helpers headlessly (web). Blocks app view mode, plan and notice-fee work.
2. K3: client project files view-only (client).
3. K4/K5: staff dead-token status and sign-out (decide web vs staff).
4. K6: view-only signal in profile/me (web), then the client view mode (client).
5. K7: notice pay-first in both apps (after K1).
6. K8: admin 2FA enforcement in the staff API (web).
7. K11: staff project-member copy (staff).
8. Plans in the apps (card, cancel, 402) when the owner schedules Stage 4 [DOC ~/.claude/plans/woolly-swimming-kurzweil.md:124-125].

## Open questions

- Is there a single place (issue tracker, channel) the owner wants contract changes announced besides this knowledge base? None recorded.
- Should `common/` be kept in a third repo and vendored into both apps to avoid drift between the two copies? Not decided. **[Answered 2026-11-07, REQ-000]** Owner: keep the copies for now. One canonical source: this repo's `backend/` (see `htdocs/docs/agents/coordination/contracts.md`). Drift detection by `contract_drift.sh`. No larger migration at this stage.
- Single place for contract announcements: `htdocs/docs/agents/coordination/contracts.md` (change log), plus the steps above.
