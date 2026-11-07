# ADR-016: Wizard-Only Forms

> Part of the Legum Lex Android knowledge base. Verified against: client app 6c069eb, staff app e52a8ca, customers_api 97de5b4, api 9df1506, cases cb0a820 (2026-11-07). Why every form in both apps is a multi-step wizard.
>
> This file is identical in legum-lex-android/docs/kb and legum-lex-staff/docs/kb.

## Requirement

Product-wide rule from 2026-08-18 [MEMORY feedback_wizard_forms.md]. Staff: "For wizards and forms. Implement a multistep form wizard with dropdowns." [SESSION 3adfe568 2026-10-23T06:39]. Client: "I want the client app to use the same wizard in its own design language." [SESSION 3adfe568 2026-10-24T17:05]

## Alternatives considered

- Flat forms and option-row lists (rejected).
- One wizard kit per app, drawn in that app's language (chosen).

## Decision

`WizardFrame` kit (WizardHeading, WizardLabel, WizardDropdown, WizardChoice, WizardTextArea, WizardReview…) in both apps; one decision per step, progress, back without loss, per-step validation, Review step; choices are dropdowns; the server stays authoritative [CODE lexco@e52a8ca J/ui/components/WizardFrame.kt:62-76] [CODE lex@6c069eb K/ui/components/WizardFrame.kt:70-81,182-398] [GIT client ad85d82 2026-10-24].

## Reasons

- Clients are NRI and corporate; a wall of fields gets abandoned [MEMORY feedback_wizard_forms.md].

## Later changes

- 2026-10-23: desk onboarding by conversation counts as a wizard, with "Or Use The Form" [SESSION 3adfe568 C26-C28].
- 2026-10-26 / 10-28: staff tester and audit loops converted remaining forms (New Consultation, Record Payment, Add Hearing, New Discussion in the client) [GIT staff 8658dd0…1e6d0d3, feb5859; client 18f17be].
- Exception kept on the web: Perfex Settings pages (owner, 2026-10-18) [MEMORY feedback_wizard_forms.md].

## Status

In force. Staff Update Hearing and advocate wizards still use their own frames (audit item) [SESSION 3adfe568 audit ledger 2026-10-28].

## Open questions

None.
