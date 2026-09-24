# UC-008: Rebuild Reporting Schema

## Overview

- **ID:** UC-008
- **Name:** Rebuild Reporting Schema
- **Primary Actor:** Admin Module (the Elicit Admin application, calling over HTTP)
- **Secondary Actors:** System Operator (reads the outcome in Admin's dialog and in this log)
- **Goal:** Bring the `surveyreport` star schema up to date with the survey definitions installed right now -- dimension rows, dimension tables, fact columns and views -- without restarting Survey, so a survey the Admin module has just installed or updated is reportable at once.
- **Status:** Implemented

## Preconditions

- Survey is running with `elicit.etl.enabled=true` (the default). The startup build (`ETLService.init()`) has already run or been skipped; either way the schema objects the migrations create exist.
- The caller can reach Survey's HTTP port on the internal network. The endpoint carries no authentication (see BR-004).

## Main Success Scenario

1. The Admin module has applied a survey definition (Admin UC-014, UC-017 or UC-018) and posts to `/api/etl/build` with no body.
2. The system takes the rebuild lock, so a second request arriving while a build runs waits for it rather than interleaving DDL with it.
3. The system upserts `surveyreport.dim_step` and `dim_section` from the current steps and sections, keyed on their durable ids, so a renamed step updates its row in place.
4. The system creates a `dim_<name>` table for every dimension named in the metadata that has no table yet, adds a `<tag>_key` column to `fact_sections` for every tagged question that has none yet, and recreates `fact_respondents_view` and `fact_sections_view` over the resulting columns.
5. The system back-fills `fact_sections` for every finalized respondent who has no fact rows yet, the same per-respondent load UC-004 performs at finalize.
6. The system answers `200` with `{"status":"ok","message":"<summary>"}`, the summary naming what each step did, and logs the same summary at INFO.

## Alternative Flows

**A1: The reporting ETL is disabled on this instance**
- **Trigger:** `elicit.etl.enabled=false` (an instance that only renders surveys, such as the Author stack's preview).
- System answers `409` with `{"status":"disabled","message":"Reporting ETL is disabled (elicit.etl.enabled=false)"}` and touches nothing.

**A2: A build step fails**
- **Trigger:** Any step in 3-5 throws. The known case is two surveys sharing a step or section dimension name: `dim_step.value` and `dim_section.value` are unique per site (`dim_step_un`, `dim_section_un`), so the upsert in step 3 fails with a duplicate-key error.
- System rolls back the failing step (each runs in its own transaction; the earlier steps' work stands), logs the exception at ERROR, and answers `500` with `{"status":"failed","message":"<root cause>"}`, where the root cause is the database driver's own text naming the constraint. The application keeps running; nothing propagates out of the request.

**A3: No survey is installed**
- **Trigger:** `survey.surveys` is empty when the request arrives.
- System answers `200` with a message saying there is nothing to build. Building against empty source tables would create an empty schema that the startup build's "already built" marker would then never revisit (see `ETLService.shouldBuildReportingSchema`).

**A4: The schema is already up to date**
- **Trigger:** The request repeats one that already ran, or nothing changed.
- System runs every step anyway -- each is a no-op when its work is done -- and answers `200`. The endpoint is idempotent (BR-001).

## Postconditions

**Success:**
- Every current step and section has a dimension row, every metadata dimension has a table, every tagged question has a fact column, both views select the current column set, and every finalized respondent has fact rows.

**Failure:**
- The steps before the failing one are committed; the failing one is rolled back. Repeating the request after the cause is fixed completes the build.

## Business Rules

- **BR-001:** The rebuild is idempotent and runs the same code as the startup build. Every step only creates what is missing or upserts by durable key, and the views are dropped and recreated, so the request can be repeated freely and never has to be paired with a restart.
- **BR-002:** The rebuild is synchronous: the response is sent when the build is done, so the caller's success means the schema is ready. The caller bounds its wait (Admin uses 60 seconds); a build that outlives it still completes here.
- **BR-003:** A failure is reported, not thrown. The response distinguishes "disabled by configuration" (409) from "the build broke" (500), and the 500 message is the innermost cause so an operator can act on it. The one known failure -- a dimension name shared by two surveys -- is a limitation of the site-wide `dim_step_un`/`dim_section_un` constraints, surfaced here and not worked around.
- **BR-004:** The endpoint is unauthenticated, like every REST path Survey exposes, and is intended for the Admin module on the internal network. It reads no respondent data and can do nothing a restart of Survey would not; a deployment that exposes Survey's `/api` paths publicly must keep this one behind the same boundary as the report and post-survey-action services.
- **BR-005:** Concurrent requests are serialised, never interleaved: the DDL steps assume they are the only writer of the reporting schema.

## Notes / Known Gaps

- The per-site uniqueness of dimension names (A2) predates this use case and is unchanged by it. Two surveys on one site must use distinct step and section dimension names until the reporting schema is keyed by survey.
- `fact_sections_view` and `fact_respondents_view` swallow their own failures (they log and return a message rather than throwing, because a dependent view an operator created by hand can block the drop); such a failure appears inside the 200 summary rather than as a 500.

## Reference

Traces to FR-017 and NFR-010. Implemented by `ETLBuildResource` (`POST /api/etl/build`) over `ETLService.rebuildReportingSchema()`, which reuses the startup sequence in `ETLService.init()`. Verified by `ETLBuildResourceTest` (HTTP round-trip: idempotent success and the `dim_step_un` failure) and `ETLBuildResourceUnitTest` (status mapping, the disabled branch, root-cause unwrapping, JSON escaping). The caller is Admin UC-018 (and UC-014/UC-017), which reports the outcome in its result dialog without failing the apply.
