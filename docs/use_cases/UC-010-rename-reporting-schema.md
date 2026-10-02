# UC-010: Rename Reporting Schema

## Overview

- **ID:** UC-010
- **Name:** Rename Reporting Schema
- **Primary Actor:** Admin Module (the Elicit Admin application, calling over HTTP on an administrator's behalf)
- **Secondary Actors:** System Operator (the analyst or BI owner whose queries name the schema)
- **Goal:** Give a survey's reporting schema the name the site wants -- shorter, or matching a naming convention -- without losing anything in it, because the name derived from the survey's name (UC-008 BR-006) is a default, not a contract.
- **Status:** Implemented

## Preconditions

- The survey exists and has been built at least once, so `surveys.report_schema` names an existing schema.
- The caller can reach Survey's HTTP port on the internal network. The endpoint carries no authentication (UC-008 BR-004).

## Main Success Scenario

1. An administrator chooses a new name for the survey's reporting schema in Admin, which posts to `/api/etl/schema/<survey_key>/rename?name=<new_name>`.
2. The system takes the rebuild lock (UC-008 BR-005), so no build and no finalize-time load runs against the schema while it is renamed.
3. The system validates the name against UC-008 BR-006 and checks that no other survey holds it.
4. In one transaction the system renames the schema (`ALTER SCHEMA ... RENAME TO ...`) and stores the new name on `surveys.report_schema`.
5. The system answers `200` with `{"status":"ok","message":"<summary>","schema":"<new_name>"}` and logs the rename at INFO.

## Alternative Flows

**A1: The name is not a valid schema name**
- **Trigger:** The name fails UC-008 BR-006 (characters, length, a reserved name).
- System answers `400` with `{"status":"invalid","message":"<what is wrong>"}` and changes nothing.

**A2: The name is taken**
- **Trigger:** Another survey's `report_schema` is the name, or a schema of that name exists in the database that no survey owns.
- System answers `409` with `{"status":"taken","message":"..."}` and changes nothing.

**A3: The survey has no schema yet**
- **Trigger:** `surveys.report_schema` is null because the survey has never been built.
- System answers `409` with `{"status":"unbuilt","message":"..."}`. The name is assigned by the first build (UC-008 step 3); the administrator builds first and renames after.

**A4: The survey does not exist**
- **Trigger:** No row of `survey.surveys` carries the key.
- System answers `404` as in UC-008 A6.

**A5: The new name is the current name**
- **Trigger:** `name` equals `surveys.report_schema`.
- System answers `200` and changes nothing.

**A6: The rename fails in the database**
- **Trigger:** `ALTER SCHEMA` throws.
- System rolls the transaction back, so the schema keeps its old name and the column its old value, logs at ERROR and answers `500` with the root cause (UC-008 BR-003).

## Postconditions

**Success:**
- The schema and every table, view, sequence, index, constraint and grant in it carry on under the new name; nothing in it was dropped or rebuilt, and no fact row or surrogate id changed. `surveys.report_schema` is the new name, so the next build (UC-008), the next finalize-time load (UC-004) and FHHS's next report resolve to it.

**Failure:**
- Nothing changed.

## Business Rules

- **BR-001:** The rename is one transaction: the schema's new name and the survey's `report_schema` column change together or not at all.
- **BR-002:** The rename is a rename, never a copy or a rebuild. PostgreSQL resolves the views, sequence defaults and foreign keys inside the schema by object id, so they keep working; the surrogate ids an extract may be keyed on are unchanged.
- **BR-003:** Nothing in Elicit may hard-code a survey schema's name. Every module reads it from `surveys.report_schema` at the time it needs it, so a rename takes effect everywhere at once. What a rename does break is queries and BI connections outside Elicit that name the old schema; the Admin action says so before the administrator confirms.
- **BR-004:** The new name obeys the same rule as a derived one (UC-008 BR-006), and uniqueness is checked against both the other surveys' names and the schemas actually present in the database.

## Notes / Known Gaps

- The rename is serialized with builds and finalize-time loads by the same lock, so a load that started before the rename finishes against the old name and the next one sees the new name.

## Reference

Traces to FR-027. Implemented by `ETLSchemaResource` (`POST /api/etl/schema/{surveyKey}/rename`) over `ETLService.renameReportingSchema(UUID, String)`. Verified by `ETLSchemaResourceTest` (a rename keeps the views answering; an invalid name, a taken name and an unknown key are refused and nothing changes). The caller is Admin's rename action on the survey's page.
