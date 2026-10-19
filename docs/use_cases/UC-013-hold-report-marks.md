# UC-013: Hold Report Marks

## Overview

- **ID:** UC-013
- **Name:** Hold Report Marks
- **Primary Actor:** Admin Module
- **Secondary Actors:** Authoring Module
- **Goal:** Keep, with each installed survey, the author's choice of what the survey's default reports show: which questions, question placements or sections the respondent report echoes back, and which ones the administrator report monitors, in what order and under what label. Survey owns the `survey` schema, so it owns the table; Author writes the marks, Admin installs them with the survey definition, and Survey's own report service reads them.
- **Status:** Implemented (the table and its translation hook; the respondent report reads it, UC-014; the administrator report is Admin's)

## Preconditions

- The survey is installed (Admin UC-014 / UC-017), or is being installed by the apply that carries the marks.

## Main Success Scenario

1. The author marks an element for a report in Author, which mints a `report_item_key` for the mark and exports it in the survey definition file as a `report_items` record alongside `metadata`.
2. Admin's apply installs the survey definition and inserts each mark into `survey.report_items`, resolving the element it names by durable id, exactly as it resolves a `metadata` row (BR-001, BR-002).
3. A later apply of the same survey matches each mark by `report_item_key`: a changed one is updated in place, a new one inserted, and one whose key has left the file deleted (BR-003).
4. The respondent report reads the survey's RESPONDENT marks in `display_order`, and the administrator report its ADMINISTRATOR marks, showing each element under its label when one is set and its own text otherwise (BR-004).

## Alternative Flows

**A1: The label is translated**
- **Trigger:** The survey publishes a content language and the author translated a mark's label.
- The translation is a `survey.translations` row with `element_type` `report_items`, `element_key` the mark's `report_item_key` and `field` `label`, versioned and served like a report's translated name (UC-009): shown where the survey publishes the language and the site offers it, and falling back to the base label when stale or absent.

**A2: A file from before this release**
- **Trigger:** A survey definition file with no `report_items` records is applied.
- The survey installs with no marks: its default reports have nothing marked and, until the author marks something, the respondent report shows nothing but the survey's headings. Nothing fails; the table is empty for that survey.

**A3: A mark names an element that is retired**
- **Trigger:** The element a mark points at has been retired (closed `effective_to`) by a later revision.
- The mark stays; the report resolves the element as of the respondent's snapshot anchor (UC-002 BR-009), so a respondent who saw the element still has it on their report and a later respondent does not.

## Postconditions

**Success:**
- `survey.report_items` holds one row per mark of the survey, each naming exactly one element by durable id, with its report type, order and optional label.

**Failure:**
- A row that names no element, more than one, or an unknown report type is refused by the database, so the apply that carries it fails as a whole (Admin UC-014 BR-049, UC-017 BR-066).

## Business Rules

- **BR-001:** A mark names exactly one of a step-section placement (`steps_sections_id`), a question (`question_id`) or a question placement (`sections_question_id`), by **durable id**, with no foreign key: a durable id names every version of the element, so the mark survives a reword or a reorder, the same rule as `survey.metadata`. The check is a strict exactly-one, unlike metadata's.
- **BR-002:** A mark belongs to one report, `RESPONDENT` or `ADMINISTRATOR`; an element shown on both reports has two marks. The two sets differ by nature: a consent question is monitored but not echoed, a free-text "your goals" is echoed but cannot be aggregated.
- **BR-003:** `report_item_key` is the mark's cross-instance identity, minted once in Author and carried verbatim by export, import and update, like every other `*_key`. The table is Type 1: a changed mark is updated in place, and one whose key has left the republished file is deleted, because a mark has no history worth keeping and a report must not show what the author un-marked.
- **BR-004:** `label` is optional and replaces the element's own text on the report; it is translatable through `survey.translations` with `element_type` `report_items` and `field` `label`. Nothing else on a mark is translated.
- **BR-005:** Survey never writes this table. It is authored content on the same footing as `metadata`: created, changed and removed only by applying a survey definition.

## Notes / Known Gaps

- The respondent report in Survey reads the `RESPONDENT` marks (UC-014). The administrator report in Admin (umbrella `docs/research/default_reports.md`, section 4) is a later release; until then the `ADMINISTRATOR` rows are installed and read by nothing.
- Author's Reports page and the `report_items` records in the `.elicit` file, and Admin's parsers for them, are the other halves of this release (Author and Admin, phase 3 of the plan).
