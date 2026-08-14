# UC-002: Answer Survey Questions

## Overview

- **ID:** UC-002
- **Name:** Answer Survey Questions
- **Primary Actor:** Respondent
- **Goal:** The respondent works through the survey's questions section by section, answering each one; the system adapts which subsequent questions, sections, or steps appear based on the answers given (a decision-tree, not a fixed linear form).
- **Status:** Implemented

## Preconditions

- The respondent has successfully entered the survey (UC-001) and the survey is still active for them.

## Main Success Scenario

1. System presents the current section's questions to the respondent, one input control per question (text, number, date, single-select, multi-select, checkbox, etc., depending on the question's configured type).
2. Respondent provides a value for a question.
3. System validates the value against the question's configured rules (required, min/max, format) as soon as the respondent leaves the field.
4. System saves the answer.
5. System re-evaluates the survey's branching rules against the newly saved answer, and shows, hides, or repeats any downstream questions, sections, or steps whose configured condition now matches.
6. Respondent continues answering questions revealed by step 5 until the section is complete.
7. Respondent clicks Next.
8. System validates every question in the current section; if all are valid, it advances the respondent to the next section (or to Review if this was the last section) and repeats from step 1.
9. Respondent may also click Previous at any point to return to the prior section, or Review to jump straight to reviewing everything answered so far.

## Alternative Flows

**A1: Required question left unanswered, or answer fails validation**
- **Trigger:** Respondent clicks Next while one or more questions in the section are invalid or missing.
- System blocks navigation, highlights the first invalid question, scrolls it into view, and shows a notification asking the respondent to correct the errors.

**A2: Respondent changes a previously given answer**
- **Trigger:** Respondent edits an answer that had already triggered downstream questions/sections/steps to appear.
- System recalculates the branching rules for the changed answer. Any downstream question, section, or step whose triggering condition no longer holds is removed (soft-deleted) from the respondent's path; anything that now qualifies is added.
- Special case: for a checkbox question, unchecking it (a "false" value) is treated the same as giving no answer at all — the underlying saved value is cleared rather than stored as "false".

**A3: A question repeats based on a numeric answer**
- **Trigger:** An upstream answer indicates a repeat count (e.g., "how many siblings do you have?").
- System generates that many repeated instances of the configured downstream question or section, and removes extra repeated instances if the respondent later lowers the count.

**A4: Respondent revisits the survey after a browser refresh or reconnect**
- **Trigger:** The browser session was interrupted and later reconnects.
- System attempts to restore the respondent's identity and in-progress navigation state from the browser session; if it cannot be restored, the respondent is returned to the login page (UC-001).

**A5: A question of type MODAL is configured**
- **Trigger:** The section contains a question whose type is `MODAL`.
- Known gap: this question type is not implemented in the rendering code — a placeholder is shown instead of the intended modal-dialog input. Any survey relying on this question type will not function as designed.

## Postconditions

**Success:**
- All questions in the completed section (and any sections/steps they triggered) have saved answers, and the respondent's navigation history reflects the path taken.

**Failure:**
- The respondent remains on the current section with unsaved or invalid input clearly flagged; no progression occurs.

## Business Rules

- **BR-004:** Every question's required/min/max/format validation rule is defined per-question in the survey configuration and enforced before section navigation is allowed.
- **BR-005:** For a CHECKBOX question, an unchecked (false) value is stored as no value (null), not as the literal text "false" — there is no explicit false state, only "answered true" or "not answered."
- **BR-006:** A branching rule's condition is evaluated using the operator configured on the relationship (e.g., equality, contains, field-exists) against the upstream answer's value; if the answer or the configured reference value cannot be parsed for that operator, the rule silently evaluates to not-satisfied rather than raising an error to the respondent.
- **BR-007:** Changing an answer that already caused downstream questions/sections/steps to be shown re-evaluates every affected relationship and removes any downstream answers whose condition is no longer met.

## Notes / Known Gaps

- The `MODAL` question type is present in the type vocabulary (`GlobalStrings.QUESTION_TYPE_MODAL`) but its rendering is an unfinished placeholder (see A5). This is a code gap, not an intended limitation.
- The branching engine's "less than" comparison for non-date numeric answers is implemented identically to "greater than" (both use `>=`), so a "less than" rule would not behave as its name implies if one were ever configured. Currently no "less than" operator is seeded in the reference data, so this defect is latent but present in the code.
