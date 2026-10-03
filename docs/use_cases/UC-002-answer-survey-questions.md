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

**A3: A question or section repeats based on a numeric answer**
- **Trigger:** An upstream answer indicates a repeat count (e.g., "how many siblings do you have?").
- System generates that many repeated instances of the configured downstream question or section, and removes extra repeated instances if the respondent later lowers the count.
- The rule may name only the target question, with no downstream step or section, when that question sits in the upstream question's own section; the repeated instances are then created in that section. This is the only shape a repeat inside the survey's first step can take, because every section or step a rule names as its downstream is hidden at login (UC-001) until a rule reveals it.

**A3b: A question or section repeats once per selected item**
- **Trigger:** The upstream question of a Repeat rule is one the respondent answers by selecting several items from a list (`MULTI_SELECT` or `CHECKBOX_GROUP`), e.g. "Which games did you attend?".
- System generates one instance of the configured downstream question or section for each item selected, so the number of instances is the number of items selected, and presents them in the order the items appear in the list, whatever order the respondent picked them in (BR-012).
- Each instance belongs to one item. A rule that reads the same upstream question and carries a token fills it, in that instance, with the text of that item, so a section named `{<GAME>|this game}` is titled with the game it asks about (BR-013).
- When the respondent changes the selection, the instance of every item no longer selected is removed and an instance is added for every item newly selected. The instances of items that stay selected are untouched, with their answers. An item that is unselected and selected again gets its instance back with the answers it had.
- Clearing the selection removes every instance.
- The target is a question or a section, as in A3; a Repeat rule on a step builds nothing, whatever it reads.

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
- **BR-008:** An answer identifies its step and section by display order (that is what its display key encodes), while a branching rule identifies its upstream and downstream step, section and question by durable id. Whenever the two are compared -- finding the rules an answer triggers, the sibling rules that must all hold, the rules whose text a step or section answer carries, or the initial questions of a shown step -- the display order is resolved to the durable id (or back) as of the respondent's snapshot anchor, never compared directly. The seeded surveys happen to number their steps so the two coincide; an authored survey does not.
- **BR-009:** Survey structure is versioned (Kimball Type 2), so a durable id -- a step, section, step/section placement, question placement, question, select group, select item or rule -- has one row per published version. Every structural lookup made on a respondent's behalf resolves the durable id to the single row whose effective window covers that respondent's snapshot anchor (`respondents.first_access_dt`, or now for a respondent not yet anchored). A reference by durable key is therefore never loaded as a JPA association: the entities carry the durable id as a plain column and the runtime resolves it through an as-of finder (`Step.findAsOf`, `Section.findAsOf`, `StepsSections.findAsOf`, `SectionsQuestion.findAsOf`, `Question.findAsOf`, `SelectItem.findByGroupAsOf`) with the respondent's anchor. Only an answer's own pins (`answers.question_id`, `answers.section_question_id`) are surrogate row ids and may be navigated directly. A respondent anchored before a revision keeps the earlier version for their whole session; one anchored after it gets the revision throughout -- and both can save answers.

- **BR-010:** A rule's token is a slot in a text, and only two things can fill it: the constant the author wrote on the rule (`relationships.default_upstream_value`) or the respondent's answer to the question the rule reads. Which one wins follows from what that answer is. A question that collects nothing -- the display-only types, which are exactly the ones whose `question_types.data_type` is empty (`HTML`, `MODAL`) -- can only be filled from the constant. A coded answer carries no prose (a choice question stores its option's `coded_value`, a boolean or number a bare figure), so the constant wins and the stored value is the fallback. A free-text answer is the respondent's own words, so the answer wins and the constant is the fallback. A date or time answer is stored in ISO form and is formatted for the respondent's content language before it reaches a sentence, so `{birthdate|NA}` reads "April 30, 1965" rather than "1965-04-30". Several rules may fill one token: a rule with nothing to contribute leaves what another one supplied, and only when no rule fills it does the text fall back to the placeholder's own default.

- **BR-011:** A placeholder is `{phrase|default}`, and the phrase holds two different kinds of thing: prose, and `<TOKEN>` references. The prose and the default are the author's words and are translated with the rest of the element's text -- the whole question, section name or step name is one translatable string, so a placeholder's phrase is never translated apart from the sentence it sits in. Only the bracketed references are substituted, and only from a rule's values. A phrase that names a token as a bare word (`{Does S1|Do you}`) names nothing the runtime can fill, so the respondent reads the default; the runtime logs that phrase once so a definition written before the brackets is visible without walking the survey. A value the author wrote on a rule may be a placeholder in its own right (`{<G1>'s|your} mother`), so authored values are resolved to a fixed point, bounded, before they reach a sentence. The respondent's own answers are spliced in once, HTML-escaped and never re-read, so free text containing braces is shown as typed rather than treated as a placeholder.

- **BR-012:** A Repeat rule takes its instances from the question it reads. A question answered with a number gives the instances 1 to N. A question answered by selecting several items from a list (`MULTI_SELECT`, `CHECKBOX_GROUP`) gives one instance per selected item, and the instance number is **the item's position in its list** (1 for the first item, in display order, as of the respondent's snapshot anchor), not a running count: selecting the second and fourth items gives instances 2 and 4. The number is written where every repeat writes it -- the question instance for a repeated question, the section instance for a repeated section -- so nothing downstream of the display key changes. Tying the number to the item rather than to a count is what keeps answers with the item they are about: with a running count, adding the first item of the list to a selection of the second and fourth would renumber those two and show the answers given for one under the title of another. The instances are built only while the rule's own condition holds, as for any rule, so the operator is normally `FIELD_EXIST`; a selected code that is no longer in the list as of the respondent's anchor gives no instance. Whether a question gives a count or a list of items is decided by its type, which is why a numeric comparison (`GREATER THAN`) on a multi-select never holds and builds nothing. Because the number is a position and a later revision may put another item at it, the reporting fact row of a repeated section names the item itself by its portable key (UC-008 BR-012).

- **BR-013:** Inside an instance built from a selected item (BR-012), a token filled by a rule that reads that same multi-select question holds **the item's text** (`select_items.display_text`), in the base rendering, and its translation in the respondent's content language where the survey publishes one. This applies to the Repeat rule itself when it carries a token and to any other rule reading the same question whose target lies in the instance -- a Text rule on the repeated section, for example. It outranks the rule's constant, which BR-010 would otherwise prefer for a coded answer: the constant is the same for every instance and the item is the one thing that tells them apart. The constant, and after it BR-010's fallbacks, apply only when the instance names no item (the list no longer holds that position). The item's text is spliced in as an answer is: HTML-escaped and not re-read as a placeholder. Outside such an instance a multi-select fills a token as BR-010 says.

- **BR-014:** A repeated section has one marker answer per instance (the row that carries the section's title and that navigation is built from), and a question revealed inside an instance belongs to that instance's marker. The marker is therefore looked up with its section instance. Looking it up with the instance zeroed gave a question shown inside a repeated section a second marker, instance 0, that depended on an answer inside the section it marks; removing an instance then followed that dependency back into the section without end. The reverse holds for structure: a placement (`steps_sections`) is the same row whichever instance a respondent is looking at and its key carries no instances, so the section a marker names is resolved with both the step instance and the section instance zeroed. That is what lets a repeated section's title be rebuilt from its template whenever a token changes, and rendered in the respondent's content language.

## Notes / Known Gaps

- The `MODAL` question type is present in the type vocabulary (`GlobalStrings.QUESTION_TYPE_MODAL`) but its rendering is an unfinished placeholder (see A5). This is a code gap, not an intended limitation.
- The branching engine's "less than" comparison for non-date numeric answers is implemented identically to "greater than" (both use `>=`), so a "less than" rule would not behave as its name implies if one were ever configured. Currently no "less than" operator is seeded in the reference data, so this defect is latent but present in the code.
- `ElicitComboBox` (the wrapper behind a single-select combo-box question) never pre-fills a previously-saved answer when its component is constructed — every other selection-type wrapper (`ElicitRadioButtonGroup`, `ElicitCheckboxGroup`, `ElicitMultiSelectComboBox`) calls `setValue(answer)` in its constructor when a saved `textValue` exists, but `ElicitComboBox`'s constructor does not. A respondent revisiting a section they already answered sees that combo-box question rendered blank. Covered by `ElicitSelectionFieldsTest#comboBox_construction_doesNotPrefillValueFromAnswer`.
- `ElicitTimePicker.setValue(Answer)` is a stubbed-out `// TODO` that does nothing, so a previously-saved time answer never reaches the field either — the same blank-on-revisit gap as the combo-box one above, for TIME questions. Covered by `ElicitDateTimeFieldsTest#timePicker_construction_neverAppliesInitialValue_becauseSetValueIsUnimplemented`.
- `ElicitTimePicker`'s min/max handling interprets `Question.minValue`/`maxValue` as `LocalTime.ofNanoOfDay(int)`, but those columns are plain `Integer`s. A full day is ~86.4 trillion nanoseconds, far past `Integer` range, so only fractions of a second near midnight are actually reachable through this path — a min/max time-of-day constraint on a TIME question cannot be expressed correctly with the current column type. Covered by `ElicitDateTimeFieldsTest#timePicker_minMax_areInterpretedAsNanoOfDay`.
- Outside an instance built from a selected item (BR-013), a choice question whose rule carries no constant fills its token with the option's stored `coded_value` ("2") rather than the option's text, because that is what `answers.text_value` holds for a choice answer. Every choice-sourced rule in the Family History Survey supplies a constant, so this is latent there, but it is why the constant outranks the answer for coded types (BR-010) instead of the other way round.
- `GlobalStrings.QUESTIION_TYPE_MULTI_SELECT_COMBOBOX` is `"MULTI_SELECT_COMBOBOX"`, while the seeded type is named `MULTI_SELECT`. `Answer.getSelectedItems()` and `setSelectedItems()` test against that constant, so neither ever matches a multi-select answer -- the same kind of stale-name defect as the one BR-010 replaced, in a different place.
