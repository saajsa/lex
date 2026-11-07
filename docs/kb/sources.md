# Sources

> Part of the Legum Lex Android knowledge base. Verified against: client app 6c069eb, staff app e52a8ca, customers_api 97de5b4, api 9df1506, cases cb0a820 (2026-11-07). This file lists every source the knowledge base was recovered from, so any claim can be traced back.
> This file is identical in legum-lex-android/docs/kb and legum-lex-staff/docs/kb.

Recovery was read-only. No session record, memory note, plan, repository or database was changed to produce this knowledge base.

## Session records (Claude Code)

Session files are JSONL under `~/.claude/projects/<project>/<session-id>.jsonl` on the owner's development machine (WSL). A reference such as `[SESSION 3adfe568 2026-11-01T20:17]` names the first eight characters of the session ID and the UTC timestamp of the message or compaction summary.

| Session ID | Project folder | Span (UTC) | Size | Compaction summaries | Role |
|---|---|---|---|---|---|
| `3adfe568-6618-4388-b3f4-1c826a687dee` | `-home-tribero-devilbox-data-www-lex-htdocs` | 2026-09-12T06:27 to 2026-11-07 | 2.1 GB | 38 | **Primary record.** Nearly all Android work on both apps, and the backend work done for them. App work starts 2026-10-12. |
| `fabd99ad-786a-447e-aaf5-59e9af71547c` | `-home-tribero-devilbox-data-www-lex-htdocs` | 2026-09-18T16:33 to 2026-10-04T15:24 | 351 MB | 23 | Web work before the apps; backend groundwork they depend on. |
| `19acbaeb-e777-4991-ab6a-2239990def26` | `-home-tribero-devilbox-data-www-lex-htdocs-modules-cases` | 2026-10-04T06:08 to 2026-11-04T17:10 | 725 MB | 26 | Web Development Agent. Backend changes after the apps' last commits. |
| `89b46f22-139b-42ba-b473-da8c3408fc75` | `-home-tribero-devilbox-data-www-lex-htdocs-modules-cases` | 2026-11-04T10:32 to 2026-11-07T02:49 | 34 MB | 3 | Web Development Agent (plans, fees, view-only contacts). |
| `321556b5…`, `677fe42c…`, `858d473f…`, `d925e75b…` | `-home-tribero-devilbox-data-www-lex-htdocs` | October 2026 | under 400 KB | 0 | Checked; nothing app-relevant. |

The primary session also has a `subagents/` folder (222 agent transcripts) and a `tool-results/` folder. Subagents were consulted only where their prompt concerned the apps or their APIs.

How the records were read: a streaming reader pulled every compaction summary (`isCompactSummary`) and every genuine owner message (excluding tool results, skill bodies and system reminders), each keeping its session ID and timestamp. Targeted searches of the raw files then recovered the reasoning behind decisions. Totals extracted: 90 compaction summaries and 1,870 owner messages (649 in the primary session, 168 of them app-related).

## Memory notes

Location: `~/.claude/projects/-home-tribero-devilbox-data-www-lex-htdocs/memory/` (136 notes; index `MEMORY.md`). A memory note records what was true when it was written and may be stale; every `[MEMORY ...]` claim here was cross-checked against code or a later session.

App-relevant notes: `project_android_client_app`, `project_staff_app`, `project_staff_app_lexmail`, `project_staff_notifications`, `project_leads_in_app`, `project_client_onboarding_2026_10`, `project_desk_onboarding`, `project_lexon_client_onboarding`, `project_one_mobile_number`, `project_display_casing`, `project_profile_mirrors_nyayco`, `project_lexmail_sender_pictures`, `project_app_api_wiring`, `project_app_test_builds`, `project_mobile_app_backlog`, `project_mobile_handoff_2026_11`, `feedback_web_first_parity`, `feedback_app_payments_by_invoice`, `feedback_client_app_scope`, `feedback_client_app_radii`, `feedback_text_colour_pairings`, `feedback_wizard_forms`, `feedback_whatsapp_content`, `feedback_manual_push`, `feedback_check_before_reporting`, `reference_api_no_module_init`, `reference_phone_dev_over_usb`, `reference_alert_float_invisible`.

Notes found stale at 2026-11-07 are listed in `shared/open-items.md`.

## Saved plans

`~/.claude/plans/`: `desk-onboarding.md` (2026-10-23), `woolly-swimming-kurzweil-lexcent-audit.md` (2026-10-31), `woolly-swimming-kurzweil.md` (2026-11-02), `whimsical-conjuring-trinket.md` and `adaptive-stargazing-cray.md` (2026-11-07; the latter is this knowledge base's plan).

## Git repositories

| Repository | Path | Remote | Commit range | Verified at |
|---|---|---|---|---|
| Client app | `/mnt/b/and_studio/legum-lex-android` | `saajsa/lex` | 112 commits, `d0822cf` (2026-10-19) to `6c069eb` (2026-11-01) | `6c069eb` |
| Staff app | `/mnt/b/and_studio/legum-lex-staff` | `saajsa/lexco` | 95 commits, `47287d8` (2026-10-21) to `e52a8ca` (2026-11-01) | `e52a8ca` |
| customers_api (client API) | `modules/customers_api` | per-module repo | | `97de5b4` (2026-11-07) |
| api (staff API) | `modules/api` | per-module repo | | `9df1506` (2026-11-01) |
| cases | `modules/cases` | per-module repo | | `cb0a820` (2026-11-07) |
| messaging, payu, razorpay, lexon, digital_office, lexmail | `modules/<name>` | per-module repos | | `3eb8f17`, `fab13c0`, `5db8989`, `7f56a67`, `b165e41`, `99c792c` |

Module paths are under `/home/tribero/devilbox/data/www/lex/htdocs/`. Commit messages carry `Claude-Session:` links to the session that made them.

## Existing documentation (not edited)

- Client app: `README.md` and `docs/{README,API,ARCHITECTURE,CHANGELOG,DEVELOPMENT}.md`, last changed 2026-10-19.
- Staff app: `README.md` and `docs/{README,API,ARCHITECTURE,CHANGELOG,DESIGN_BRIEF,DEVELOPER_GUIDE,DEVELOPMENT}.md`, last changed 2026-10-21.
- Both are out of date; see `app/docs-drift.md` in each repo.
- Backend: `modules/customers_api/{README,MOBILE_APP_BACKEND_SUMMARY,DASHBOARD_API_*}.md`, `modules/api/README.md`, `modules/cases/docs/developer/{mobile_handoff_2026-11-01,api_services,architecture,permissions}.md`, `modules/cases/docs/biz/2026-11-02-platform-capability-map.md`.

## Excluded on purpose

- **Secret files were never opened:** `local.properties`, `keystore.properties`, `google-services.json`, keystores, and the production database defaults file.
- **No values were copied:** no secret, token or real client's personal data appears in this knowledge base.
- **Unrelated project:** `-mnt-b-ragnarok-offline-20250211` was not used.

## Open questions

- Earlier client-app work (before the repository's first commit on 2026-10-19, when it arrived already at 0.2.1) cannot be recovered from these sessions.
- It is unverified whether the latest local pushes reached GitHub; the readers made no network calls.
