# Legum Lex Client App — Changelog

## 0.2.1 — 2026-06-21 — Legal Notices funnel + polish

`versionName 0.2.1` · `versionCode 4`

### Added — Legal Notices (bidirectional, document-first)

A full client-side Legal Notices feature backed by the real
`tblcase_legal_notices` system (no longer a dead-end consultation blob).

- **Bidirectional model** — Outbound (you send) and Inbound (you received)
  notices, with **threading**: an outbound response nests under the inbound
  root via `parent_notice_id`; the inbound rail is derived from its child's
  status.
- **Notices list** (`NoticesScreen`) — filter (All/Outbound/Inbound/Active/
  Closed with counts), sort (deadline/recent/amount/status), priority
  grouping (Needs you / In progress / Closed), thread-preserving rows.
- **Notice detail** (`NoticeDetailScreen`) — lifecycle tracking rail, ref/
  service-method/tracking facts, response threading, advocate panel,
  contextual footer (Approve & send / Respond / Message advocate).
- **Send-a-notice funnel** (`NewNoticeScreen`) — minimal-typing intake;
  notice type chosen from real `tblcase_notice_types` (never invented),
  AI-assisted extraction for inbound scans.
- **Approval gate** — outbound drafts require explicit client approval
  (`client_approved_at`) before dispatch.
- **Inline document viewing** — PDFs/images stream and render in-app
  (`NoticeDocViewerScreen`, `PdfRenderer`), no download step.
- Backend `customers_api/v1/notices*` (list/types/extract/{id}/approve/
  document), migration 139 (`client_approved_at`). Smoke-tested end-to-end.

### Added — Notifications

In-app notifications feature plus an app-wide header bell. All notice/
home screens now use the shared `AppHeader` (centered logo · back · bell)
instead of per-screen headers.

### Added — Home cards

Agreement Check and Legal Notice cards implemented per the Lexcore design
(correct Material Symbols icons, animations, descriptions under titles).

### Changed — Indian (INR) currency formatting

Claim/amount figures now use lakh/crore digit grouping — e.g.
`₹2,10,000` · `₹69,02,505` · `₹1,00,00,000` — via `money()` /
`indianGrouping()` in `NoticeUi.kt`.

### Changed — Document classification (draft vs final)

The notice Documents section now classifies each file by kind (Draft /
Legal notice / Proof of service / Received notice / Supporting) with
distinct icon, colour and a clear label. A "Legal Notice Document" reads
as a **DRAFT** (amber badge) while the notice is pre-dispatch, and as the
**final notice** once dispatched — keyed off the notice's lifecycle status.

### Fixed

- Fonts (Source Serif 4 / IBM Plex Sans / IBM Plex Mono) now render —
  bundled as static TTFs in `res/font` (the downloadable Google Fonts
  provider was silently falling back without Play Services).
- Corrected corrupted `LlColor` tokens (claret mid/soft/border).
- Send-a-notice problem cards: selecting one no longer selects all (keyed
  on `selectedProblemKey`).
- Swept unused icon imports across the notices feature.

## 2026-06-10 — Digital Office cabin queue (waitroom)

### Added — Waitroom data + Home banner + action sheet

Clients can now wait in a busy advocate's queue (free) and pay only when
they reach the head and get a 5-minute prompt window. Three new files
plus integrations on Home.

- **`DoModels`** — `DoWaitroomStatus` enum + `DoWaitroomRow` data class.
- **`DoRepository`** — `listWaitroom / joinWaitroom / leaveWaitroom`.
  Returns structured failures via a typed `AdvocateBusyJoinQueue` exception
  so the caller can route into the queue path instead of surfacing a
  generic error.
- **`DoWaitroomViewModel`** — Activity-scoped (`sharedDoWaitroomViewModel`)
  so the home banner and the action sheet stay in lockstep. 15s polling
  fallback (no Pusher Channels dependency); optimistic merge on join.
  `bookDirectly: SharedFlow<Int>` for the `cabin_free` race so the screen
  routes the user straight into booking instead of queueing behind no one.
- **`DoWaitroomBanner`** — sticky banner above the existing
  `LiveSessionBanner` slot. Two visual states:
  - **PROMPTED** — loud emerald card with live mm:ss countdown.
  - **WAITING** — neutral blue card, position + ETA, "tap to manage" hint.
- **`DoWaitroomSheet`** — modal bottom sheet. Prompted state: primary
  "Pay & start intake" routes into the existing DoIntake flow with the
  advocate id; secondary "Forfeit slot & leave queue". Waiting state:
  primary "Leave queue".
- **`HomeScreen`** integration: banner injected, sheet shown on tap,
  `Toast` for non-fatal events (joined queue, left queue, errors), new
  `onStartDoSessionWith(advocateId)` callback wired in `NavGraph`.

### Added — In-app path to JOIN a queue

Previously, hitting Walk-in when all counsel were busy showed a dead-end
error. Now:

- **`DoIntakeViewModel`** detects the server's
  `code=advocate_busy, action=join_waitroom` signal (typed
  `AdvocateBusyJoinQueue` from the repo) and emits a new
  `DoIntakeEvent.OfferJoinQueue(advocateStaffId, advocateName)`.
- **`DoIntakeScreen`** surfaces it as a long-duration snackbar with a
  **"Join queue"** action. Accept → `waitroomVm.join(advId)` →
  optimistic merge → snackbar banner is visible on Home immediately
  via `onBack()`.
- **`DoIntakeViewModel.load()`** no longer silently substitutes a
  different advocate when the user-targeted one is busy. The explicit
  pick is honoured; busy state engages the queue path on submit.

### Added — Queue-aware home roster

`ChamberAdvocate` model now carries `queueCount` + `etaMinutes`. The
home `DigitalOfficeRoster` and `DigitalOfficeHero` cards display the busy
line as e.g. *"Adv. So-and-so · busy · 2 waiting · ~14 min"*. Server
`/customers_api/v1/dashboard/chamber` returns the same fields per advocate.

### Added — Cabin queue endpoints (mobile contract)

Routed under `/customers_api/v1/digital_office/`:

| Verb | Path                  | Purpose |
|------|-----------------------|---------|
| GET  | `/waitroom`           | Caller's active queue rows across advocates |
| POST | `/waitroom/join`      | Body `advocate_staff_id`; returns row or `cabin_free` code |
| POST | `/waitroom/leave`     | Body `waitroom_id` |

`POST /sessions` now enforces the cabin/queue gate: cabin busy + no
prompted row → `{code:advocate_busy, action:join_waitroom}`. On payment
success the server marks the caller's prompted row as `promoted`.

### Fixed — `session_id` decimal corruption (URL routing)

Moshi parses naked JSON numbers as `Double`, so the server's
`"session_id":23` arrived as `Double(23.0)` and `toString()` produced
`"23.0"`. The next call to `/sessions/23.0/intake` then fell through to
the `POST /sessions` collection handler and 422'd with a misleading
*"advocate_staff_id is required"* message.

`DoRepository.kt` now has two coercion helpers — `Any?.asIntString()` and
`Any?.asIntOrZero() / asIntOrNull()` — applied wherever a JSON-decoded id
flows back into a URL path or into a model: `startSession`, `submitIntake`,
`parseSession` (id, appointmentId, consultationId, advocate.staffId), and
`parseWaitroomRow` (every int field).

### Fixed — "Join now" silent no-op on the live banner

When `meetLink` AND `consultationId` were both null (Meet not generated
yet — common when intake hadn't completed) the button click was dropped
silently. Now three-tier:

1. `meetLink` present → launch Meet (wrapped in `try/catch
   ActivityNotFoundException` with a Toast fallback).
2. `consultationId` present → open consultation detail.
3. Neither → Toast *"Your session isn't ready yet — opening Digital Office."*
   + route via `onOpenDigitalOffice` so the user lands somewhere actionable.

### Removed — Dead 402 (`onboarding_required`) branch

`DoIntakeViewModel.friendly()` no longer maps HTTP 402 since the server
side dropped the onboarding gate. Harmless dead code; cleaned for clarity.

### Notes

- No Pusher Channels (websocket) dependency on the client app — relies on
  Pusher Beams push to interest `contact-{id}` for the "your turn" cue,
  plus 15 s polling while the app is foregrounded.
- Realtime delivery from server publishes on `do-client-{clientId}` channel
  for any web client that's also open; mobile uses the push + poll combo.

### Known gaps / next session

Advocate ↔ client state sync needs a coherence pass:

- Optimistic merge on join may briefly desynchronise with the next poll.
- Countdown drift if app is backgrounded mid-prompt — UI ticks from the
  stale `promptSecondsLeft` until the next poll refreshes it.
- `bookDirectly` SharedFlow uses `tryEmit`; with `replay=0` an emission
  before the collector mounts is lost.
- Push payload should include `prompt_expires_at` so the cold-start UI
  can render an accurate countdown without waiting for the first poll.
