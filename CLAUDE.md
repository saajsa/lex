# Legum Lex client app (com.legumlex.client)

Native Kotlin and Jetpack Compose. Single `:app` module, no DI, no local DB. Backend: Perfex `customers_api/v1` (JWT per contact) on the web install at `/home/tribero/devilbox/data/www/lex/htdocs`. Repo `saajsa/lex`, branch `main`.

This repo is worked on by the Legum Lex team. The charter (roles, approval protocol, hard rules) is `/home/tribero/devilbox/data/www/lex/htdocs/CLAUDE.md`, and it applies here in full. The Android Developer works here only on tasks the Technical Advisor assigns from an owner-approved REQ.

## Read first
- `docs/kb/README.md`, then the `app/` file for your task.
- `docs/kb/shared/owner-rules.md` and `docs/kb/backend/known-issues.md`.
- The older `README.md` and `docs/*.md` are stale (19 Oct). `docs/kb/app/docs-drift.md` lists where.

## Rules for this repo
- **Web first**: the app carries only functions the web already has. Never invent statuses or labels; never bypass a server rule.
- **Contracts**: the API contract is `docs/kb/backend/customers-api.md` (canonical). Changes come from the web side through the Advisor.
- **Do not touch** the owner's uncommitted Gradle files (`gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.properties`), `local.properties`, `keystore.properties`, `google-services.json` or the keystore.
- **Builds**: debug only, via `/mnt/b/and_studio/_build/`. Never install on the owner's phone. No release builds.
- **Design**: client kit with Mylex radii 4/6/8/12; flat; light theme default; every form a wizard.
- **KB edits**: update `docs/kb/app/*` with app changes. Shared KB files (`shared/`, `backend/`, `decisions/`, `README.md`, `sources.md`) must stay byte-identical with the staff repo, and only the Advisor edits them.
