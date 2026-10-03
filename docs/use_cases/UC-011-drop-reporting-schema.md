# UC-011: Drop Reporting Schema

## Overview

- **ID:** UC-011
- **Name:** Drop Reporting Schema
- **Primary Actor:** Authoring Module (the Elicit Author application, deleting a survey from its own database)
- **Secondary Actors:** System Operator (reads the outcome in the log)
- **Goal:** Remove a survey's reporting schema, with everything in it, when the survey it reports is about to be deleted, so that no schema outlives its survey in a database that holds many short-lived drafts.
- **Status:** Implemented

## Preconditions

- `elicit.etl.drop.enabled=true` on this instance (BR-001). It is `false` by default; the Author stack's preview instance sets it.
- The caller can reach Survey's HTTP port on the internal network. The endpoint carries no authentication (UC-008 BR-004).

## Main Success Scenario

1. Author is about to delete a survey (Author UC-047) and sends `DELETE /api/etl/schema/<survey_key>` before its own delete.
2. The system takes the rebuild lock (UC-008 BR-005).
3. The system looks the survey up by key and reads `surveys.report_schema`.
4. In one transaction the system drops the schema with everything in it (`DROP SCHEMA ... CASCADE`) and sets `surveys.report_schema` to null.
5. The system answers `200` with `{"status":"ok","message":"<summary>"}` and logs the drop at INFO.
6. Author deletes the survey.

## Alternative Flows

**A1: The survey has no schema**
- **Trigger:** `surveys.report_schema` is null: the survey was never built, or was dropped already.
- System answers `200` with a message saying there was nothing to drop. The call is idempotent.

**A2: The drop is not enabled on this instance**
- **Trigger:** `elicit.etl.drop.enabled=false` (the default, and every site).
- System answers `403` with `{"status":"disabled","message":"..."}` and touches nothing.

**A3: The survey does not exist**
- **Trigger:** No row of `survey.surveys` carries the key.
- System answers `404` as in UC-008 A6.

**A4: The drop fails in the database**
- **Trigger:** `DROP SCHEMA` throws.
- System rolls the transaction back, so the schema and the column are as they were, logs at ERROR and answers `500` with the root cause (UC-008 BR-003). Author then refuses its delete, so the survey and its schema stay together.

## Postconditions

**Success:**
- The schema is gone and the survey's `report_schema` is null. The survey itself is untouched: if Author's delete then fails, the survey remains, without a schema, and the next build (UC-008) creates it again from the survey's answers.

**Failure:**
- Nothing changed.

## Business Rules

- **BR-001:** **The drop is off unless a deployment turns it on.** The endpoint exists on every Survey because the preview is the same image, and it is unauthenticated like every REST path Survey exposes. Unlike the build (UC-008 BR-004) it can do something a restart would not: the next build regenerates the schema, but with new surrogate ids, which breaks an extract keyed on them. `elicit.etl.drop.enabled` therefore defaults to `false`, and only a database that holds no reporting anyone depends on, Author's, turns it on.
- **BR-002:** The drop and the clearing of `surveys.report_schema` are one transaction, and the drop runs under the rebuild lock, so no build creates the schema again while it is being dropped and no finalize-time load writes into a schema that is going.
- **BR-003:** The drop goes before the delete, not after. Author's delete removes the row that holds the schema's only name; dropping first means a failure on either side leaves no schema that nothing names. A survey whose delete failed after its drop is simply an unbuilt survey.
- **BR-004:** The drop does not depend on `elicit.etl.enabled`: a schema built while the ETL was on still has to go after it is turned off.

## Notes / Known Gaps

- Between the drop and Author's delete, the survey exists with a null `report_schema`. A finalize in that window is harmless (UC-004 skips a survey with no schema). A startup or a build of that survey in the window would create the schema again and Author's delete would then orphan it; the window is one HTTP round-trip and Author never builds, so this is noted rather than guarded.
- Admin has no survey deletion, so no site calls this endpoint today. If Admin gains one, it calls this.

## Reference

Traces to FR-028. Implemented by `ETLSchemaResource` (`DELETE /api/etl/schema/{surveyKey}`) over `ETLService.dropReportingSchema(UUID)`. Verified by `ETLSchemaResourceTest` (the schema and `report_schema` are gone and a second call answers 200; another survey's schema is untouched; the next build creates the schema again; the disabled instance answers 403). The caller is Author UC-047.
