# API Reference — `customers_api`

> The backend the app talks to. Last reviewed: 2026-05-20.

---

## 1. The backend module

The server is a custom Perfex CRM module, **`customers_api`** (Perfex is PHP /
CodeIgniter 3). It lives **inside the Perfex install** as a nested git repo:

```
<perfex>/modules/customers_api/        ← repo: git@github.com:saajsa/api.git
├── controllers/v1/                    ← one controller per resource
├── config/routes.php                  ← URL → controller/method routing
├── helpers/customers_api_helper.php
└── models/Customers_api_model.php
```

On the dev machine: `/home/tribero/devilbox/data/www/lex/htdocs/modules/customers_api/`.

> **Deploy note:** `customers_api` is a separate repo with its own remote.
> Changes are committed locally and **pushed manually by the maintainer**, then
> pulled on production. Do not assume a commit is live.

---

## 2. Base URL & environments

Set per build type in `app/build.gradle.kts` as `BuildConfig.API_BASE_URL`:

| Build | Base URL |
|---|---|
| `release` | `https://www.legumlex.com/customers_api/v1/` |
| `debug` | A Cloudflare tunnel URL → local devilbox (changes on every restart — see `docs/DEVELOPMENT.md`) |

All paths below are relative to `…/customers_api/v1/`.

---

## 3. Auth scheme

- `POST authentication` (email + password) or `POST authentication/google`
  (Google id_token) returns a **JWT**.
- Every other request must send `Authorization: Bearer <jwt>` — done
  automatically by `AuthInterceptor`.
- Login / Google login / `google_config` are unauthenticated; the app marks them
  with the `X-Skip-Auth: 1` header so the interceptor skips them.
- `401` / `403` → session invalid; the app routes the user back to Login.

---

## 4. Response envelope

Responses are JSON. Successful payloads wrap the body in a `data` key:

```json
{ "status": true, "data": { ... } }      // object
{ "status": true, "data": [ ... ] }      // list
```

The app does not have DTO classes — repositories unwrap `data` and hand-parse.
See `HomeRepository.kt` for the canonical parsing approach.

---

## 5. Endpoint catalogue

Mirrors `data/api/ApiService.kt`. Controllers are in `controllers/v1/`.

### Authentication
| Method | Path | Purpose |
|---|---|---|
| POST | `authentication` | Email + password login → JWT |
| POST | `authentication/google` | Google id_token login → JWT |
| GET | `authentication/google_config` | Returns the OAuth Web Client ID for this environment |
| GET | `authentication/me` | Current session identity |
| GET | `logout` | Invalidate session |

### Dashboard (Home screen)
| Method | Path | Purpose |
|---|---|---|
| GET | `dashboard/profile` | Client profile (auth canary for Home) |
| GET | `dashboard/action_items` | The Home action queue |
| GET | `dashboard/next_event` | Next hearing or appointment |
| GET | `dashboard/balance` | Outstanding / overdue invoice summary |
| GET | `dashboard/chamber` | Digital Chamber roster + session state |
| GET | `dashboard/stats`, `dashboard/next_hearing`, `dashboard/recent_activity`, `dashboard/active_services`, `dashboard/legal_health`, `dashboard/active_matters` | Misc dashboard slices |

`action_items` kinds include: `invoice_unpaid`, `invoice_overdue`,
`contract_unsigned`, `doc_request_pending`, `consultation_awaiting_reply`,
`inbound_notice_response_due`, `appointment_payment_required`,
`task_awaiting_feedback`, `project_milestone_approaching`.

### Cases / Hearings
| Method | Path | Purpose |
|---|---|---|
| GET | `cases` (`?id=`, `?status=`) | Case list or single case |
| GET | `cases/timeline/{id}` | Case timeline |
| GET | `hearings` (`?id=`) | Hearing list or single |
| GET | `hearings/upcoming` | Upcoming hearings |

### Consultations
| Method | Path | Purpose |
|---|---|---|
| GET | `consultations` (`?id=`) | List or single |
| GET | `consultations/ahc/{id}` | After-hearing-conference detail |
| GET | `consultations/doc_requests/{id}` | Document requests on a consultation |
| GET | `consultations/appointment/{id}` | Appointment detail |
| POST | `consultations/create` | New consultation |
| POST | `consultations/reply/{id}` | Post a message to the thread |

### Projects & Tasks (corporate clients — read-only v1)
| Method | Path | Purpose |
|---|---|---|
| GET | `projects` (`?status=`) | Project list |
| GET | `projects/{id}` | Project detail (incl. `settings` tab-visibility flags) |
| GET | `projects/{id}/tasks` | Tasks on the project |
| GET | `projects/{id}/milestones` | Milestones |
| GET | `projects/{id}/files` | Files visible to the client |
| GET | `projects/{id}/activity` | Activity log |
| GET | `tasks/{id}` | Single task detail |

Project ownership is enforced server-side: another client's project returns
**404** (not 403) to avoid leaking existence.

### Invoices / Documents / Notifications / Devices
| Method | Path | Purpose |
|---|---|---|
| GET | `invoices` (`?id=`) | List or single |
| GET | `documents` (`?rel_type=`, `?q=`, `?tag=`, `?case_id=`, `?rel_id=`) | Document search |
| GET | `documents/download/{id}` | Streamed file download (`@Streaming`) |
| GET | `notifications` | Notification list |
| GET | `notifications/unread_count` | Unread badge count |
| POST | `mobile_devices/register` | FCM device registration |

---

## 6. Routing caveat (`config/routes.php`)

CodeIgniter's REST routing in this module defaults to a catch-all
`customers_api/(:any)/(:any)` → `$1/$2/$2`. That means **any controller whose
method is `index` (or otherwise not named after the controller) needs an
explicit route line** added *above* the catch-all, or it 404s.

When you add a server endpoint, add its explicit route in `config/routes.php` in
the explicit-routes block. Projects/Tasks REST routes are already wired there
(`v1/projects/list`, `v1/projects/detail/$1`, `v1/projects/{id}/{sub}`,
`v1/tasks/tasks/$1`).
