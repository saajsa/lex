# ADR-015: Display Casing

> Part of the Legum Lex Android knowledge base. Verified against: client app 6c069eb, staff app e52a8ca, customers_api 97de5b4, api 9df1506, cases cb0a820 (2026-11-07). How names and descriptions typed in all caps or all lower case are shown, without changing stored data.
>
> This file is identical in legum-lex-android/docs/kb and legum-lex-staff/docs/kb.

## Requirement

> "You should recommend how we can fix this by using a standardised way of showing the text" [SESSION 3adfe568 2026-10-27T18:52]; "yes, build 1–4, web first" [SESSION 3adfe568 2026-10-27T18:54]

## Alternatives considered

- Tidy text at save time (point 5 of the proposal): not authorised, because legal names must match their documents.
- Display-only casing on the web and in the app with one shared fixture (chosen).

## Decision

Names in Title Case, descriptions in sentence case, references untouched; only all-caps or all-lower text is re-cased. Web helpers `cases_display_name/text` (`cases_text_helper.php`); staff `TextCase.kt`; one fixture `display_text_cases.json` tested by PHP and by `TextCaseTest` [GIT cases fbea5f7; core 98d2d4b; staff c4ad558] [CODE lexco@e52a8ca J/ui/TextCase.kt:21-155; app/src/test/java/com/legumlex/staff/ui/TextCaseTest.kt].

## Reasons

- Consistent display without altering legal records; the fixture keeps web and app identical.

## Later changes

None after 2026-10-28.

## Status

Implemented on the web and in the staff app (pushed 2026-10-28). **Not in the client app.** Save-time tidy awaits the owner [SESSION 3adfe568 2026-10-27T19:04].

## Open questions

- Should the client app adopt TextCase?
