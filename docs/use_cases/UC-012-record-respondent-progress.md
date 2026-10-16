# UC-012: Record Respondent Progress

## Overview

- **ID:** UC-012
- **Name:** Record Respondent Progress
- **Primary Actor:** Admin Module
- **Secondary Actors:** Respondent
- **Goal:** Keep, on the respondent's own rows, three facts a progress report needs and the answer rows alone cannot give: when each question was first put in front of the respondent, when the respondent last did anything, and which values the respondent changed rather than accepted. The Admin module reads them for its participation report; nothing in Survey shows them to the respondent.
- **Status:** Implemented

## Preconditions

- The respondent has entered the survey (UC-001). Everything here happens as a side effect of UC-001, UC-002 and UC-003; the respondent takes no extra step and sees no extra page.

## Main Success Scenario

1. The respondent lands on a page: the first page at login (UC-001), the next or previous one (UC-002 step 7 and 9), or a section chosen from the review (UC-003).
2. The system renders the page from that section instance's answer rows and stamps `presented_dt` on every row of the page that has no stamp yet (BR-001). Rows that already carry one keep it.
3. The system records the moment as the respondent's `last_activity_dt` (BR-004).
4. The respondent changes a value (UC-002 step 2).
5. The system saves the value, stamps `saved_dt` on that row (BR-002), re-renders the page and repeats steps 2 and 3, so a question a branching rule has just revealed on this page is stamped at its first display.
6. The respondent finishes (UC-004). `finalized_dt` is set as before; the stamps stay as they are, so a finished respondent's path reads as presented from the first page to the last.

## Alternative Flows

**A1: A question carries a default value**
- **Trigger:** The question's configuration supplies a `default_value`, so its row is created with a value the respondent has not typed.
- The system stores the value but leaves `saved_dt` empty (BR-002). The default is shown, validates, and reaches the review, the PDF and the reporting schema exactly as before; only the stamp says it was never changed. When a field re-submits the same value, the unchanged-value check leaves the stamp empty too.

**A2: A checkbox is left unchecked**
- **Trigger:** A CHECKBOX question is presented and the respondent does not tick it, or unticks it.
- The system stores no value for it (UC-002 BR-005) and stamps nothing beyond `presented_dt`. A presented checkbox with no value *is* the answer No (BR-003); no reader needs an explicit `false`.

**A3: Rows exist for pages not yet reached**
- **Trigger:** Login builds the answer rows of every unconditional question of the survey at once (UC-001), and a rule may build rows for a later page.
- Those rows carry no `presented_dt` until their page is rendered. A row without the stamp means "eligible under the rules, not yet seen"; a row with it means "seen". For a finished respondent the two sets coincide, because finishing requires passing every page.

**A4: The review page**
- **Trigger:** The respondent opens the review (UC-003).
- The review lists every answered row by its own query and stamps nothing: a value seen in a summary is not a question presented. Editing from the review opens the section as a page (step 1) and stamps as usual.

**A5: A database upgraded from an earlier release**
- **Trigger:** The migration that adds the two columns runs on a database with respondents in it.
- For every finished respondent, each answer row's `presented_dt` is set from its `created_dt`, because finishing proves every surviving row was shown; in-progress and not-started respondents' rows are left unstamped, since nothing on record says which pages they reached. `last_activity_dt` is set to the respondent's `finalized_dt`, else the latest `saved_dt` among their answers, else their `first_access_dt`. Existing `saved_dt` values are left as they are: the database cannot tell a default that was accepted from one that was retyped.

## Postconditions

**Success:**
- Every answer row of a page the respondent has seen carries the instant it was first shown; rows for pages not yet reached carry none.
- `respondents.last_activity_dt` holds the instant of the respondent's latest page render or save.
- `answers.saved_dt` is set exactly on the rows whose value the respondent changed.

**Failure:**
- The stamps are written in the same transaction as the render or the save they belong to, so a failed render or save leaves them as they were.

## Business Rules

- **BR-001:** `presented_dt` is stamped per row, by the page render. A page is one section instance, and every render of one passes through the one query that returns exactly that page's live rows (`QuestionManager.getAnswersBySection`); the stamp is set there, only where it is still empty, on only those rows. Rows built for other pages at login or by a rule are not touched, and the review page, which does not render a page, stamps nothing.
- **BR-002:** `saved_dt` means "the respondent changed this value". It is set when a save changes the stored value and never at row creation, so a row created with a question's default value has a value and no stamp. The reporting rebuild keeps its `saved_dt IS NOT NULL` filter on fact rows: it is redundant with the `text_value IS NOT NULL` beside it for every row a respondent typed into, and an accepted default has reached the reporting schema since before this rule and still does.
- **BR-003:** A CHECKBOX is never stored as `false` (UC-002 BR-005). Its answer is read from the two stamps: `presented_dt` set and `text_value` empty is No; `presented_dt` empty is not asked. Storing `false` would make EQUAL and NOT_EQUAL rules fire on it, print it on the review page and add a `false` member to every checkbox dimension of the reporting schema, for a fact the stamps already give.
- **BR-004:** `last_activity_dt` is set with every page render and every save. A save stamps it in its own transaction. A page render runs in a transaction of its own, and the stamp is written just before it in a transaction that joins the caller's when one is open: session restore (UC-002 A4) logs the respondent in and renders the page inside one transaction that already holds their row, and a stamp from inside the nested render would wait on it until the transaction timed out. Login sets the stamp through the first page render; finishing sets `finalized_dt`, which is later than any activity, so a report takes the greater of the two.
- **BR-005:** Both columns are nullable and carry no constraint beyond their type, so an older respondent export (Admin BR-099) that lacks them still imports; the Admin module's respondent export and import carry them from the release that adds this use case.

## Notes / Known Gaps

- The participation report that reads these stamps is the Admin module's (umbrella `docs/research/default_reports.md`, section 4). Until it exists the stamps are written and read by nothing but the respondent export.
- A respondent who opens a page and leaves without answering is "presented" for every question on it, including ones below the fold. The stamp records the page, not the respondent's eyes.
