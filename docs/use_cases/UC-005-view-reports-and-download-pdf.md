# UC-005: View Reports & Download PDF

## Overview

- **ID:** UC-005
- **Name:** View Reports & Download PDF
- **Primary Actor:** Respondent
- **Secondary Actor:** External Report Service
- **Goal:** After finalizing, the respondent sees whatever configured report(s) the survey produces (e.g., a risk assessment from an external clinical service) and can download a PDF summary of their submitted answers.
- **Status:** Implemented

## Preconditions

- The respondent has finalized their survey (UC-004), or is a returning respondent who had already finalized on a prior visit (UC-001, alternative routing).

## Main Success Scenario

1. System calls each report configured for the survey, in its configured display order, passing the respondent's identifier to the report's configured external service endpoint.
2. System renders each report's returned content in its own card on the page.
3. Respondent clicks "Generate PDF."
4. System builds a PDF of the respondent's submitted answers, temporarily caches it server-side, and opens it in a new browser tab for viewing/printing/saving.
5. If the survey is configured with a post-survey redirect URL, system shows a "Next" button; clicking it navigates the respondent's browser to that URL, leaving the Survey application.

## Alternative Flows

**A1: A report's external service call fails**
- **Trigger:** The configured report service returns an error status, is unreachable, or times out.
- System renders that report's card with an error message instead of report content (including a special-cased, more specific message when the failure looks like a licensing rejection from a known downstream service), and continues rendering any other configured reports normally.

**A2: No reports configured for the survey**
- **Trigger:** The survey has zero `ReportDefinition` rows.
- No report cards are shown; the PDF download and any configured "Next" redirect are still available.

**A3: Respondent downloads the PDF more than once**
- **Trigger:** Respondent clicks "Generate PDF" again, or reopens a previously generated PDF's link.
- System reuses its short-lived (10-minute) server-side cache entry for that PDF rather than removing it after first use, so repeat opens/retries within that window succeed without regenerating.

## Postconditions

**Success:**
- The respondent has viewed their configured report(s) and/or downloaded a PDF of their answers.

**Failure:**
- Individual failed reports are shown as errored cards; this does not prevent PDF download or viewing other reports.

## Business Rules

- **BR-012:** Reports render in the survey-configured display order.
- **BR-013:** The PDF download endpoint is intentionally unauthenticated (publicly reachable given a valid, time-limited cache key) — it relies on the cache key's unguessability and short lifetime rather than a login check.

## Notes / Known Gaps

- BR-013 is a deliberate configuration in `application.properties` (the `/api/pdf/*` path is explicitly set to `permit`), not an oversight — flagging it here because it is a meaningful access-control decision worth confirming with stakeholders during review, especially given the vision document's note that survey content may include PHI in clinical deployments.
