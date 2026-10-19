# UC-014: Render the Respondent Report

## Overview

- **ID:** UC-014
- **Name:** Render the Respondent Report
- **Primary Actor:** Respondent
- **Secondary Actors:** Admin Module
- **Goal:** Show a respondent who has finished the answers the survey's author marked for them, under the survey's title and the date they finished, in their own language, as a card on the report page and as a part of the merged PDF, and give the Admin module the same report when it prints a respondent's reports, so that a survey built in Author has a report without anyone writing a report service.
- **Status:** Implemented

## Preconditions

- The survey lists a report whose address is the built-in service's path, `/api/reports/respondent-summary` (Author UC-048 BR-001 seeds it into every new survey).
- The respondent exists; the report is meaningful once they have finished (UC-004), which is when the report page shows it and when Admin prints it.

## Main Success Scenario

1. The report page (UC-005) reaches the report whose address is a path: it calls the built-in service in process, with the respondent's identifier and the page's language (BR-001).
2. The system resolves the language: the one the request names, else the language the respondent answered in, else the survey's base language (BR-002).
3. The system reads the survey's `RESPONDENT` marks (UC-013) and the respondent's current answer rows, and keeps the rows the marks cover: a question wherever it appears, a question in one placement, or every question of a section as mounted in one step (BR-003).
4. The system arranges the kept rows as the survey presented them: by step, then section, then each repeat instance under its own header, then the items of the section (BR-004).
5. The system renders each item under its label, or the element's own text when the mark has none, with its value shown by question type: a chosen option's text, Yes or No for a checkbox, a number or date in the language's format, text as the respondent typed it (BR-005). An item the respondent did not answer is left out (BR-006).
6. The system returns the report: its name as the survey lists it, the survey's title and the completion date, the body as HTML for the card, and the same content as a PDF part for the merged download (BR-007), and logs that the respondent's answers were read (BR-008).
7. The report page shows the card; "Generate PDF" merges the part with the other reports' (UC-005).

## Alternative Flows

**A1: The Admin module prints the respondent's reports**
- **Trigger:** Admin requests the report over HTTP at the path, resolved under its Survey address (Admin UC-005 BR-023).
- The system runs steps 2 to 6 for the respondent the request names and answers with the same report as JSON; Admin merges the PDF part like any other report service's.

**A2: Nothing is marked**
- **Trigger:** The survey has no `RESPONDENT` marks, or none of them covers an answer the respondent gave.
- The report holds its name, the survey's title and the completion date, and no items. Nothing fails.

**A3: The respondent has not finished**
- **Trigger:** The request names a respondent with no finalize instant.
- The report renders what has been answered so far and shows no completion date.

**A4: The request names no respondent**
- **Trigger:** The identifier matches no respondent.
- The service answers not found; the report page shows the report's card with an error message like any failed report service (UC-005 A1).

**A5: A marked element was retired**
- **Trigger:** A mark names an element that a later revision retired.
- The respondent's answer rows name the versions they were made against, so a respondent who saw the element still has it on their report and a later respondent does not (UC-013 A3).

## Postconditions

**Success:**
- The respondent sees, on the card and in the PDF, exactly the marked answers they gave, in their language, in survey order.

**Failure:**
- The card carries an error message and the merged PDF omits the part; nothing is stored.

## Business Rules

- **BR-001:** **A report whose address is a path is called in process.** The report page never makes an HTTP request to Survey itself: a `survey.reports` row whose `url` starts with `/` names a service built into Survey, dispatched by that path without a network hop, so a development instance restarting under its own request cannot deadlock and no base-URL setting is needed. An absolute `http(s)` address is called over HTTP as before (UC-005). The same service answers over HTTP at that path for the Admin module, which resolves the path under its own Survey address.
- **BR-002:** **The language is the request's, then the respondent's, then the survey's.** The report page passes the language it is showing the survey in (UC-009). A request without one, Admin's print for instance, gets the language the respondent's latest saved answer was displayed in, and a respondent whose answers carry none gets the survey's base language. Every string that has a current translation in that language is shown translated (UC-009 BR-005, BR-008): the report's name, step and section names, question texts, option texts and mark labels (`report_items`, field `label`).
- **BR-003:** **Marks select answer rows by durable id, as of the respondent's snapshot anchor.** A question mark keeps every current answer row whose question has that durable id, in any section and instance; a placement mark keeps the rows of that placement; a section mark keeps every question row of that section as mounted in that step, found through the placement current at the respondent's anchor (UC-009 BR-010). Rows flagged deleted are never shown. A question whose type shows no value, `HTML`, `MODAL` or `PASSWORD`, is never shown even when a section mark covers it.
- **BR-004:** **Survey order, not mark order, is the layout.** Items are grouped under the step and section they were answered in, in the order the respondent met them, each repeat instance under its own header row with the token already filled; within one section the author's mark order decides, and the survey's order breaks ties.
- **BR-005:** **Values render by type.** `RADIO` and `COMBOBOX` show the chosen option's display text, never its code; `MULTI_SELECT` and `CHECKBOX_GROUP` show each chosen option's text as a list; `CHECKBOX` shows Yes or No, No also for a box never touched; `INTEGER` and `DOUBLE` are formatted for the language; `DATE_PICKER`, `DATE_TIME_PICKER` and `TIME_PICKER` are formatted for the language when the stored value parses and shown as stored otherwise; `TEXT`, `TEXTAREA` and `EMAIL` are shown as typed, escaped. The text of every item, header and label is escaped: a report never renders authored or answered text as HTML.
- **BR-006:** **An unanswered item is left out.** A marked question with no saved value does not appear, so the report says what the respondent said and nothing about what they skipped. A checkbox is the exception of BR-005.
- **BR-007:** **One content, two renderings.** The HTML card and the PDF part carry the same headings, labels and values, in the same order; the PDF part is a sequence of text blocks and two-column tables the existing merge understands (UC-005), under the report's name as its title. For a right-to-left language the card is marked `dir="rtl"`.
- **BR-008:** **Every rendering is logged.** The report is a read of one respondent's answers, which may be PHI (C-010). Each rendering writes one log line naming the survey key, the respondent identifier, the language and the caller, in process or HTTP, and never an answer.

## Notes / Known Gaps

- The PDF merge draws with a Latin-only standard font (UC-005), so the PDF part of a report in Arabic does not render its text; the card does. A Unicode font for the merge is a separate change.
- The author-written closing text the plan proposes (umbrella `docs/research/default_reports.md`, section 3.1) is not modeled yet; the report ends with its last section.
- Repeat instances are shown in the order of their instance numbers, which for a per-item repeat are the chosen items' positions in the list, not 1..n (UC-002).

## Reference

Traces to FR-035, FR-034 and NFR-012. Implemented by `RespondentSummaryService` (the content), `RespondentSummaryResource` (`POST /api/reports/respondent-summary`), `BuiltInReports` (the path dispatch `ReportView` uses) and a read-only `ReportItem` mapping of `survey.report_items`. Verified by `RespondentSummaryServiceTest` (marks of each scope on the library fixture, value rendering per type, omission of unanswered and valueless questions, the language fallback), `RespondentSummaryResourceTest` (the HTTP round trip and the not-found case) and `ReportViewTest` (a path report renders a card without an HTTP call).
