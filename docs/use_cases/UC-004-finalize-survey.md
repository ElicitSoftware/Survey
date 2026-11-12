# UC-004: Finalize Survey

## Overview

- **ID:** UC-004
- **Name:** Finalize Survey
- **Primary Actor:** Respondent
- **Secondary Actor:** External Post-Survey-Action Service
- **Goal:** Permanently close out the respondent's survey session: mark them inactive/complete, clean up abandoned branches, feed the completed survey into the analytical (ETL) tables, and notify any configured downstream systems.
- **Status:** Implemented

## Preconditions

- The respondent has reached the Review page and clicked Finish (UC-003).

## Main Success Scenario

1. System marks the respondent as no longer active and records the finalize timestamp.
2. System permanently discards any answers and dependency records that were soft-deleted during the survey (e.g., branches shown then later hidden by an answer change).
3. System extracts the respondent's completed section path into the analytical ("fact") tables used for downstream reporting.
4. System delivers the survey's post-survey actions for the respondent (UC-015): each is called in execution order, as its author configured it and with the credential the site configured for it, and a failed call is retried on a schedule.
5. Respondent is routed to the reports view (UC-005), which sends them straight to the survey's post-survey URL when the survey lists no reports (UC-005 BR-014).

## Alternative Flows

**A1: A post-survey action's target service rejects or errors on the notification**
- **Trigger:** The external service returns a non-success HTTP status or the call otherwise fails (network error, timeout, malformed URL).
- System records the failure (including a human-readable error message — with special-cased detection for a licensing-related rejection from a known downstream cancer-risk-calculator service) against that specific action for the respondent, but does not block finalization or the other configured actions from proceeding. The scheduled retry of UC-015 (A3) calls it again later.

**A2: A previously-sent post-survey action is retried**
- **Trigger:** Finalize runs again for a respondent who already has a recorded attempt for a given post-survey action (e.g., re-finalization).
- System marks the delivery as being resent and clears the prior error before calling again (UC-015 A5).
- Note: there is no guard preventing `finalize` from running more than once for the same respondent — repeated finalize calls will unconditionally re-mark the respondent inactive and reset the finalize timestamp, unlike the (separate, unused-in-this-path) deactivate logic elsewhere in the code, which only sets the finalize timestamp once.

## Postconditions

**Success:**
- The respondent is inactive and finalized; the fact tables in their survey's own reporting schema (UC-008 BR-006) reflect their path, or, when the survey has no schema yet because it has never been built, the load was skipped with a log line and the next build's back-fill will supply the rows; every configured post-survey action has been attempted at least once (success or recorded failure).

**Failure:**
- Individual post-survey action failures are recorded per-action; they do not roll back the respondent's inactive/finalized state or the ETL extraction.

## Business Rules

- **BR-009:** Soft-deleted answers and dependency records are only permanently purged at finalize time, not when the branching change happens.
- **BR-010:** Post-survey actions execute in the survey-configured execution order and are independent of each other — one failing does not prevent the others from running (UC-015 BR-006).
- **BR-011:** A post-survey action's delivery is recorded with Survey's own states, `DELIVERED` on success and `CALL_FAILED` on a failed call, and a failed call is retried on a schedule up to a limit; the rules are UC-015's (BR-004, BR-005). Until Elicit#95 no success status was recorded and `tries` was never incremented.

## Notes / Known Gaps

- The former gaps of BR-011 (no success status, `tries` never incremented) are closed by UC-015.
