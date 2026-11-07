# ADR-014: Project Members: Counsel Only, Then Any Active Staff; Tasks Belong To Projects

> Part of the Legum Lex Android knowledge base. Verified against: client app 6c069eb, staff app e52a8ca, customers_api 97de5b4, api 9df1506, cases cb0a820 (2026-11-07). Who may be on a project team, and why there are no free-standing tasks.
>
> This file is identical in legum-lex-android/docs/kb and legum-lex-staff/docs/kb.

## Requirement

> "Tasks are not an independent function. Tasks are sub-functions of projects." [SESSION 3adfe568 2026-10-25T06:45]
> "the client has to be assigned to that particular staff member. Only then should the name be visible on the list." [SESSION 3adfe568 2026-10-25T06:49]

## Alternatives considered

AskUserQuestion [SESSION 3adfe568 2026-10-25T06:46]: assignment through a Who Does What step or other options; web standalone tasks "Project required" or optional; staff app "Projects first, tasks nested". Owner took all three recommended options. Team source: `tblcases_counselors` (chosen) vs `tblcustomer_admins`; admin bypass rejected.

## Decision

25 Oct: counsel-only teams from `tblcases_counselors`, no admin bypass, guards in core and the api model copies, `cases_project_team_helper.php`, rows that break the rule hidden not deleted; tasks require a project; staff New Project steps Client, Type, Team, Tasks (Who Does What), Dates, Review; My Work became projects-only with a Mine toggle [GIT core 87cdd2f; cases 98ffb3c; api 54a8aa9, 97abc96; customers_api e7d736e; staff 669d8e6] [MEMORY project_counsel_only_projects.md].

## Reasons

- Clients see only people who act for them; tasks always have a matter context.

## Later changes

- 2026-10-28: the api forked models lacked the rule (open audit item) [SESSION 3adfe568 C35].
- **2026-11-03: reversed** by cases c8c0813: "Project members: any active staff member, not the client's counsel (owner, 3 Nov 2026)". `cases_client_staff_ids($client_id)` now returns every active staff id; the picker, save filters, task assignees and both APIs follow one rule [GIT c8c0813 2026-11-03] [CODE cases@cb0a820 helpers/cases_project_team_helper.php]. The owner's words for this were not found in any session.

## Status

- Tasks-belong-to-projects: in force (web and staff app).
- Members: server allows any active staff (staff API `project_stages/eligible_staff`, Myprojects, Mytasks; client API task assignees) [CODE api@9df1506 controllers/Project_stages.php:97-101] [CODE customers_api@97de5b4 controllers/v1/Tasks.php:132]. **Staff app copy is stale**: "Only X's counsel can be on the project." [CODE lexco@e52a8ca feature/projects/NewProjectScreen.kt:204,207; StageWizards.kt:168,170]. [MEMORY project_counsel_only_projects.md] is stale.

## Open questions

- Confirm the 3 Nov reversal with the owner (commit message is the only source).
