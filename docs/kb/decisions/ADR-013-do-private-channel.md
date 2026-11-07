# ADR-013: Digital Office Private Channel For Advocates

> Part of the Legum Lex Android knowledge base. Verified against: client app 6c069eb, staff app e52a8ca, customers_api 97de5b4, api 9df1506, cases cb0a820 (2026-11-07). Why the advocate's live Digital Office events moved to a private Pusher channel authorised by the staff token.
>
> This file is identical in legum-lex-android/docs/kb and legum-lex-staff/docs/kb.

## Requirement

Handoff brief: "Digital Office: live alerts use private Pusher channels. The staff app needs an API auth endpoint (Authtoken) for `private-do-advocate-{id}`." [DOC cases/docs/developer/mobile_handoff_2026-11-01.md:148]

## Alternatives considered

- Keep the public `do-advocate-{id}` channel (the earlier design documented in the staff DEVELOPER_GUIDE) [DOC legum-lex-staff/docs/DEVELOPER_GUIDE.md].
- Skip the private channel in the app (the state before e52a8ca: it crashed sign-in and was skipped) [MEMORY project_staff_app.md].
- Authorise the private channel with the staff token (chosen).

## Decision

`POST digital_office/pusher_auth (socket_id, channel_name)` with the `Authtoken` header (api 9df1506). The app's `DigitalOfficePresenceClient` uses `HttpChannelAuthorizer(API_BASE_URL + "digital_office/pusher_auth")` and `subscribePrivate` when the server's `advocate_channel` starts with `private-`, binding `session_event` and `waitroom_event`; without a token it skips the private channel [CODE lexco@e52a8ca J/feature/digitaloffice/DigitalOfficePresenceClient.kt:43-99].

## Reasons

- Session and waitroom events concern clients; a private channel limits them to the advocate.

## Later changes

None after 2026-11-01.

## Status

Implemented at e52a8ca; **not phone-tested**. The class KDoc still calls both channels public (stale). The client app has no realtime channel; the "private DO channel" in memory has no client-side code [CODE lex@6c069eb push/PushSetup.kt]. The staff notifications channel `notifications-channel-{id}` is still public (known issue K21).

## Open questions

None.
