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
4. For each post-survey action configured on the survey (in a fixed execution order), system sends a notification (webhook) to the configured external service, including the respondent's identifier.
5. Respondent is routed to the reports view (UC-005).

## Alternative Flows

**A1: A post-survey action's target service rejects or errors on the notification**
- **Trigger:** The external service returns a non-success HTTP status or the call otherwise fails (network error, timeout, malformed URL).
- System records the failure (including a human-readable error message — with special-cased detection for a licensing-related rejection from a known downstream cancer-risk-calculator service) against that specific action for the respondent, but does not block finalization or the other configured actions from proceeding.
- Note: there is no automatic retry of a failed post-survey action from the Survey app itself; the failure is only recorded.

**A2: A previously-sent post-survey action is retried**
- **Trigger:** Finalize runs again for a respondent who already has a recorded attempt for a given post-survey action (e.g., re-finalization).
- System marks the retry attempt distinctly (resending) and clears the prior error before attempting the call again.
- Note: there is no guard preventing `finalize` from running more than once for the same respondent — repeated finalize calls will unconditionally re-mark the respondent inactive and reset the finalize timestamp, unlike the (separate, unused-in-this-path) deactivate logic elsewhere in the code, which only sets the finalize timestamp once.

## Postconditions

**Success:**
- The respondent is inactive and finalized; the ETL fact tables reflect their path; every configured post-survey action has been attempted at least once (success or recorded failure).

**Failure:**
- Individual post-survey action failures are recorded per-action; they do not roll back the respondent's inactive/finalized state or the ETL extraction.

## Business Rules

- **BR-009:** Soft-deleted answers and dependency records are only permanently purged at finalize time, not when the branching change happens.
- **BR-010:** Post-survey actions execute in the survey-configured execution order and are independent of each other — one failing does not prevent the others from running.
- **BR-011:** There is no terminal "succeeded" status recorded for a post-survey action even when its call returns success; success is inferred only by the absence of a recorded failure.

## Notes / Known Gaps

- BR-011 and the retry-count field (`tries`) being declared but never incremented are both implementation gaps rather than intended behavior — a monitoring/reporting tool built against `respondent_psa.status` cannot currently distinguish "succeeded" from "attempted, not yet reconciled."
