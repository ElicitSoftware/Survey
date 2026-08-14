# UC-003: Review Answers

## Overview

- **ID:** UC-003
- **Name:** Review Answers
- **Primary Actor:** Respondent
- **Goal:** Before finalizing, the respondent can see a consolidated, read-only summary of every section and answer they have given, grouped by section, so they can confirm accuracy or go back and change something.
- **Status:** Implemented

## Preconditions

- The respondent has an active session and has answered at least the required questions for the section(s) reached so far.

## Main Success Scenario

1. Respondent navigates to the Review page (from within the answering flow, UC-002).
2. System loads every answer the respondent has given, grouped into a card per section, in the order they occurred.
3. Respondent reads through the summarized answers.
4. Respondent clicks Finish.
5. System finalizes the survey (UC-004) and routes the respondent to the reports view (UC-005).

## Alternative Flows

**A1: Respondent wants to change an answer before finishing**
- **Trigger:** Respondent clicks Previous from the Review page.
- System returns the respondent to the prior section in the answering flow (UC-002) so they can revise an answer, then would need to navigate forward through the flow again to return to Review.

**A2: Session state missing when Review is opened**
- **Trigger:** The respondent's survey/respondent session data is not present (e.g., session expired).
- System shows a notification and redirects the respondent to the login page (UC-001).

## Postconditions

**Success:**
- The respondent has seen a complete summary of their answers and either proceeded to finalize or returned to make changes.

**Failure:**
- The respondent is redirected to log in again; no review is shown.

## Business Rules

- **BR-008:** The Review summary only reflects answers that are currently active (not soft-deleted by a branching change); anything removed by a changed upstream answer does not appear.

## Notes / Known Gaps

- The Finish button's enabled/disabled state uses the exact same condition as the Previous button (whether a "previous" section exists in the respondent's path). This is very likely a copy-paste defect — Finish being unavailable purely because there happens to be no previous section does not match the intended review-and-submit behavior. Flagging as observed code behavior, not a deliberate design decision.
