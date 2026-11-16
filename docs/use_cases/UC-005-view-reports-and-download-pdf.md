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
- The survey lists at least one report. A survey with none has no report page (A2, BR-014).

## Main Success Scenario

1. System calls each report configured for the survey, in its configured display order, passing the respondent's identifier and language to the report's configured service: over HTTP for an absolute address, in process for a path, which names a report built into Survey (UC-014, BR-015).
2. System renders each report's returned content in its own card on the page.
3. Respondent clicks "Generate PDF."
4. System builds a PDF of the respondent's submitted answers, temporarily caches it server-side, and opens it in a new browser tab for viewing/printing/saving.
5. If the survey is configured with a usable post-survey redirect URL (BR-017), system shows a "Next" button; clicking it navigates the respondent's browser to that URL, leaving the Survey application.

## Alternative Flows

**A1: A report's external service call fails**
- **Trigger:** The configured report service returns an error status, is unreachable, or times out.
- System renders that report's card with the report's name and one fixed message, in the respondent's language, that the report is not available right now. Nothing the service answered, no status and no address is shown. The same card is shown for a report address that is not called at all (BR-017).
- System logs the report's name, its address, the HTTP status and the service's answer or the failure's message (one line, at most 300 characters of it) at WARN for the operator, and continues rendering any other configured reports normally.
- In the PDF, that report's section carries the same message as text.

**A2: No reports configured for the survey**
- **Trigger:** The survey has zero `ReportDefinition` rows when the respondent is routed to the report page, whether from Finish (UC-004), from a returning respondent's sign-in (UC-001) or from a restored session.
- There is nothing to show and nothing to print, so the page shows no report cards and no "Generate PDF" button (BR-014).
- If the survey has a post-survey redirect URL, the system sends the browser straight to it, leaving the Survey application, as if the respondent had pressed "Next".
- If it has none, the page shows a completion notice and nothing else.

**A3: Respondent downloads the PDF more than once**
- **Trigger:** Respondent clicks "Generate PDF" again, or reopens a previously generated PDF's link.
- Reopening a generated PDF's link within its 10 minutes succeeds without regenerating; the entry is not removed after first use.
- Clicking "Generate PDF" again builds a new PDF, which replaces the one this page generated before; the earlier link then answers as expired (BR-018).

## Postconditions

**Success:**
- The respondent has viewed their configured report(s) and/or downloaded a PDF of their answers, or, for a survey that lists no reports, has been sent to its post-survey destination.

**Failure:**
- Individual failed reports are shown as errored cards; this does not prevent PDF download or viewing other reports.

## Business Rules

- **BR-012:** Reports render in the survey-configured display order.
- **BR-014:** A survey with no reports has no report page. "Generate PDF" is shown only when at least one report is listed, and a respondent routed to the page is sent straight to the survey's post-survey URL when it has one, on every path that leads there (Finish, returning sign-in, restored session). Without a post-survey URL, or with one that BR-017 does not allow, the page shows a completion notice only.
- **BR-015:** A report whose address starts with `/` is a built-in report service and is called in process by its path, never over HTTP to Survey itself (UC-014 BR-001). The startup diagnostics (UC-007) report such an address as built in rather than probing it.
- **BR-017:** An address that comes from the survey definition is used only when it is an absolute `http` or `https` address that names a server and carries no user information. A post-survey URL that is anything else (a relative address, another scheme) is treated as if the survey had none: no redirect, no "Next" button, the completion notice where BR-014 calls for it, and one WARN in the log naming the survey. A report address that is neither a built-in path (BR-015) nor such an address is not called; its card is the one of A1.
- **BR-018:** The PDF cache is bounded. Generating a PDF again replaces the one the page generated before. Expired PDFs are removed every five minutes and whenever a PDF is stored, not only when their link is asked for. The instance keeps at most 200 PDFs and 256 MB of them; past either ceiling the oldest are discarded first, never the one just generated. A link to a replaced, expired or discarded PDF answers as expired.
- **BR-019:** A report's drawing is plain SVG. A drawing that declares entities, whose document type carries an internal subset, or that is longer than 5,000,000 characters is not parsed; PDF generation then fails as it does for a drawing that is not well-formed SVG, and the respondent sees the PDF error notification. A document type that only names the SVG DTD is accepted.
- **BR-013:** The PDF download endpoint is intentionally unauthenticated (publicly reachable given a valid, time-limited cache key) — it relies on the cache key's unguessability and short lifetime rather than a login check.

## Notes / Known Gaps

- BR-013 is deliberate, not an oversight: the download is opened in a new browser tab, which carries nothing but the link, so the key in the link is the whole check. Survey has no HTTP security layer of its own, and no `quarkus.http.auth.permission.*` setting takes part in this. It is a meaningful access-control decision worth confirming with stakeholders during review, especially given the vision document's note that survey content may include PHI in clinical deployments.
