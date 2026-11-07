# Decisions

> Part of the Legum Lex Android knowledge base. Verified against: client app 6c069eb, staff app e52a8ca, customers_api 97de5b4, api 9df1506, cases cb0a820 (2026-11-07). Index of the architecture decision records (ADRs) for both apps.
>
> This file is identical in legum-lex-android/docs/kb and legum-lex-staff/docs/kb.

Each ADR has: Requirement (the owner's words with a reference), Alternatives considered, Decision, Reasons, Later changes (dated), Status (intended vs implemented, and where), Open questions. Evidence labels follow the knowledge-base convention ([CODE], [GIT], [SESSION], [MEMORY], [DOC], [ASSUMPTION]).

| ADR | Title | App | Status at 2026-11-07 |
|---|---|---|---|
| [ADR-001](ADR-001-web-first-parity.md) | Web-First Parity | both | in force |
| [ADR-002](ADR-002-payments.md) | Payments In The Client App | client | native checkout (Razorpay SDK + PayU), inline amount; production Razorpay only |
| [ADR-003](ADR-003-one-mobile-number.md) | One Mobile Number Per Person | both | implemented, in production since 2026-10-20 |
| [ADR-004](ADR-004-same-person-accounts.md) | One Person, Several Accounts, And The Account Switch | client | switch beside the bell; staff linking not done |
| [ADR-005](ADR-005-leads-in-the-app.md) | Leads And Guests In The Client App | client | implemented |
| [ADR-006](ADR-006-client-onboarding.md) | Client Onboarding: Self And By Staff | both | implemented; desk onboarding switch was off |
| [ADR-007](ADR-007-no-notice-creation-in-client-app.md) | No Legal Notice Creation In The Client App | client | in force; pay-first not in apps |
| [ADR-008](ADR-008-test-builds.md) | Self-Updating Test Builds: Built, Then Removed | both | removed; do not reintroduce |
| [ADR-009](ADR-009-staff-profile-mirrors-nyayco.md) | Staff Profile Is The Menu And Mirrors The Nyayco Counsel Page | staff | implemented |
| [ADR-010](ADR-010-lexmail-in-staff-app.md) | Lexmail In The Staff App | staff | implemented; some states untested |
| [ADR-011](ADR-011-lexmail-sender-pictures.md) | Lexmail Sender Pictures | staff | implemented |
| [ADR-012](ADR-012-staff-two-factor.md) | Staff Two-Factor Sign-In | staff | implemented, not phone-tested |
| [ADR-013](ADR-013-do-private-channel.md) | Digital Office Private Channel For Advocates | staff | implemented, not phone-tested |
| [ADR-014](ADR-014-project-members.md) | Project Members; Tasks Belong To Projects | both | counsel-only reversed 2026-11-03; staff copy stale |
| [ADR-015](ADR-015-display-casing.md) | Display Casing | staff (web) | implemented; not in client |
| [ADR-016](ADR-016-wizard-only-forms.md) | Wizard-Only Forms | both | in force |
| [ADR-017](ADR-017-design-languages.md) | Design Languages: Theme, Radii And Colour Pairings | both | implemented |
| [ADR-018](ADR-018-client-token-signing-key.md) | Client Token Signing Key Change (e3027f0) | client | implemented in customers_api; one forced sign-in |
| [ADR-019](ADR-019-notifications.md) | Notifications: Existing Infrastructure, Server Targets, App-Drawn Pushes | both | implemented; preferences deferred |
| [ADR-020](ADR-020-order-summaries-on-request.md) | Order Summaries On Request Only | both | implemented |
| [ADR-021](ADR-021-fewer-screens.md) | Fewer Screens: Tabs, Folded Cards, Inline Edits | both | implemented |
| [ADR-022](ADR-022-client-navigation.md) | Client App Navigation | client | implemented |

Adding an ADR: next number, file `ADR-NNN-<slug>.md`, same sections and header; add a row here and a row in [timeline.md](../shared/timeline.md). Supersede rather than edit history: record the reversal under "Later changes" with its date and reference.

## Open questions

- Candidates not yet written up: staff token scoping (api 7393723, 2026-10-14); order of Stage 4 mobile work in the GTM plan; K4 dead-token status (needs a decision first).
