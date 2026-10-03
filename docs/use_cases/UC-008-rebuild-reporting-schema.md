# UC-008: Rebuild Reporting Schema

## Overview

- **ID:** UC-008
- **Name:** Rebuild Reporting Schema
- **Primary Actor:** Admin Module (the Elicit Admin application, calling over HTTP)
- **Secondary Actors:** System Operator (reads the outcome in Admin's dialog and in this log)
- **Goal:** Bring a survey's reporting star schema up to date with the survey definition installed right now -- its own schema, dimension rows, dimension tables, fact columns and views -- without restarting Survey, so a survey the Admin module has just installed or updated is reportable at once, and so every survey at a site is reportable, not only the first one installed.
- **Status:** Implemented

## Preconditions

- Survey is running with `elicit.etl.enabled=true` (the default). The startup build (`ETLService.init()`) has already run or been skipped; either way the common schema `surveyreport` with `dim_date` and `dim_status`, which the migrations create, exists.
- The caller can reach Survey's HTTP port on the internal network. The endpoint carries no authentication (see BR-004).
- The database role Survey's owner datasource connects as (`elicit_owner`) may create schemas in the database.

## Main Success Scenario

1. The Admin module has applied a survey definition (Admin UC-014, UC-017 or UC-018) and posts to `/api/etl/build?survey=<survey_key>` with no body, naming the survey it just applied by its portable key.
2. The system takes the rebuild lock, so a second request arriving while a build runs waits for it rather than interleaving DDL with it.
3. The system looks the survey up by `survey_key`. If it has no reporting schema yet (`surveys.report_schema` is null), the system derives a schema name from the survey's name (BR-006), stores it on the survey and creates the schema with its grants and its fixed tables: `dim_step`, `dim_section`, `dim_question`, `dim_item` and `fact_sections` with their indexes (BR-007). A schema that exists is left as it is.
4. The system upserts `dim_step` and `dim_section` in the survey's schema from the survey's current steps and sections, keyed on their durable ids, so a renamed step updates its row in place.
5. The system creates a `dim_<name>` table in the survey's schema for every dimension named in the survey's metadata that has no table yet, adds a `<tag>_key` column to the survey's `fact_sections` for every tagged question that has none yet, and recreates the `fact_respondents` view, `fact_respondents_view` and `fact_sections_view` in the survey's schema over the resulting columns.
6. The system back-fills the survey's `fact_sections` for every finalized respondent of the survey who has no fact rows yet, the same per-respondent load UC-004 performs at finalize.
7. The system answers `200` with `{"status":"ok","message":"<summary>"}`, the summary naming the survey, its schema and what each step did, and logs the same summary at INFO.

## Alternative Flows

**A1: The reporting ETL is disabled on this instance**
- **Trigger:** `elicit.etl.enabled=false` (an instance that only renders surveys, such as the Author stack's preview).
- System answers `409` with `{"status":"disabled","message":"Reporting ETL is disabled (elicit.etl.enabled=false)"}` and touches nothing.

**A2: A build step fails**
- **Trigger:** Any step in 3-6 throws.
- System rolls back the failing step (each runs in its own transaction; the earlier steps' work stands), logs the exception at ERROR, and answers `500` with `{"status":"failed","message":"<root cause>"}`, where the root cause is the database driver's own text. The application keeps running; nothing propagates out of the request. Because every step only creates what is missing, the next build of the survey completes what this one left (BR-001).

**A3: No survey is installed**
- **Trigger:** `survey.surveys` is empty when a request without `survey` arrives.
- System answers `200` with a message saying there is nothing to build.

**A4: The schema is already up to date**
- **Trigger:** The request repeats one that already ran, or nothing changed.
- System runs every step anyway -- each is a no-op when its work is done -- and answers `200`. The endpoint is idempotent (BR-001).

**A5: The request names no survey**
- **Trigger:** The request is `POST /api/etl/build` without `survey`.
- System builds every installed survey in turn, each as in steps 3-6 and each under the lock, and answers once: `200` with one summary line per survey when every build ran, or `500` naming the surveys that failed when any did. A failure in one survey does not stop the others.

**A6: The request names a survey that does not exist**
- **Trigger:** No row of `survey.surveys` carries the `survey_key` given.
- System answers `404` with `{"status":"unknown","message":"No survey has the key <key>"}` and touches nothing.

**A7: Startup**
- **Trigger:** The application starts with `elicit.etl.enabled=true` and at least one survey installed.
- System runs steps 3-6 for every installed survey, logging each outcome and continuing past a survey whose build fails, so one broken survey never stops the others from being reportable.

## Postconditions

**Success:**
- The survey has a reporting schema of its own, named on `surveys.report_schema`. In it, every current step and section has a dimension row, every metadata dimension of the survey has a table, every tagged question of the survey has a fact column, the three views select the current column set, and every finalized respondent of the survey has fact rows.
- No object of another survey was read or changed.

**Failure:**
- The steps before the failing one are committed; the failing one is rolled back. Repeating the request after the cause is fixed completes the build.

## Business Rules

- **BR-001:** The rebuild is idempotent and runs the same code as the startup build. Every step only creates what is missing or upserts by durable key, and the views are dropped and recreated, so the request can be repeated freely and never has to be paired with a restart.
- **BR-002:** The rebuild is synchronous: the response is sent when the build is done, so the caller's success means the schema is ready. The caller bounds its wait (Admin uses 60 seconds); a build that outlives it still completes here.
- **BR-003:** A failure is reported, not thrown. The response distinguishes "disabled by configuration" (409) and "no such survey" (404) from "the build broke" (500), and the 500 message is the innermost cause so an operator can act on it.
- **BR-004:** The endpoint is unauthenticated, like every REST path Survey exposes, and is intended for the Admin module on the internal network. It reads no respondent data and can do nothing a restart of Survey would not; a deployment that exposes Survey's `/api` paths publicly must keep this one behind the same boundary as the report and post-survey-action services.
- **BR-005:** Concurrent requests are serialized, never interleaved: the DDL steps assume they are the only writer of a reporting schema. One lock covers every survey.
- **BR-006:** **Every survey has a reporting schema of its own.** Its name is `report_` followed by the survey's name in lower case, with every run of characters outside `a-z` and `0-9` replaced by one `_`, leading and trailing `_` removed, truncated so the whole name fits in 63 bytes, and with `_2`, `_3`, ... appended when another survey already holds the name. The name is assigned once, the first time the survey is built, and stored on `surveys.report_schema`; renaming the survey afterwards does not change it. It is site-local: it is never carried in a survey definition file, and importing or updating a definition never sets or changes it. Only UC-010 changes it. A name must match `^[a-z_][a-z0-9_]{0,62}$` and may not be `survey`, `surveyreport`, `public`, `information_schema` or begin with `pg_`.
- **BR-007:** **Identifiers inside a survey's schema are the same for every survey**, and the same as the single site-wide schema had before this use case: `dim_step`, `dim_section`, `dim_<tag>`, `fact_sections` with `<tag>_key` columns, `fact_respondents`, `fact_respondents_view` and `fact_sections_view`. Only the schema qualifier differs between surveys, so a query moves from one survey to another by changing one word. `dim_date` and `dim_status` are shared by every survey and stay in `surveyreport`; the views join them there. `fact_sections.survey_id` stays, redundant inside the schema, so a `UNION` across surveys' schemas is still possible. The schema's owner is the role Survey's owner datasource connects as; `surveyreport_user` can read every table and view in it, and `survey_user` holds on `dim_step`, `dim_section` and `fact_sections` the grants it held on the site-wide tables.
- **BR-008:** **Uniqueness of dimension names is per survey.** `dim_step.value` and `dim_section.value` are unique within one survey's schema, so two surveys at one site may use the same step or section dimension name, which until this use case made the build fail.
- **BR-009:** **Every statement of the build reads one survey.** Discovery of dimension tables and fact columns reads only the survey's own metadata and ontology, the dimension upserts read only its steps and sections, and the back-fill selects only its finalized respondents. Nothing a second survey installs can appear in the first survey's schema, and nothing it installs can hide anything from it either: `survey.dimensions` is site-wide, so when the column discovery sets a named dimension's table aside (its columns come from the tags that use it), it considers only the dimensions this survey's own ontology uses -- another survey's dimension `Gender` must not swallow this survey's tag-only `gender`.
- **BR-010:** **`fact_respondents` is a view, not a table.** It selects every respondent of the survey from `survey.respondents` with the date keys, the status (0 not started, 1 in progress, 2 finished) and the duration computed on the fly, so a respondent's status is current the moment it changes and no trigger writes into a reporting schema during a respondent insert or update. The duration is `finalized_dt - first_access_dt`, the time the respondent spent answering, and `0` until the respondent finishes; this is the one definition of duration the platform reports. `fact_respondents_view` joins `surveyreport.dim_date` with a `LEFT JOIN`, so a date the dimension does not hold yields a null label and never hides the respondent.
- **BR-011:** **A fact row names the step and section it is about by their dimension keys**, resolved as of the respondent's snapshot anchor (`respondents.first_access_dt`): `fact_sections.step_key` is the `dim_step.id` of the step whose display order the answer records, and `section_key` is the `dim_section.id` of the section the step placed at that section display order. Before this use case the two columns held the display orders themselves, which named the right step only while a step's surrogate id equaled its display order and named the right section only for the first section of a step.
- **BR-012:** **A section instance built from a selected item names the question and the item.** For a fact row whose `section_instance` is greater than 0, the system finds the Repeat rule in effect at the respondent's anchor whose downstream placement is the row's step and section and whose upstream question is a `MULTI_SELECT` or `CHECKBOX_GROUP` (UC-002 BR-012); when there is one, `fact_sections.question_key` is the `dim_question.id` of that upstream question and `item_key` is the `dim_item.id` of the item at position `section_instance` of the question's list, as of the same anchor, ordered by display order and then by item id -- the order the instances were built in. Every other row carries `-1` in both, the "no value" member every dimension holds. `dim_question` is keyed by the portable `question_key` and `dim_item` by the portable `select_item_key`, so reordering or rewording a list updates one dimension row and moves no fact, and rows written before and after a reorder report the same item under different instance numbers. A count-driven repeat, a step shown per repeated free-text answer, and a repeated question (which has no fact row of its own) are not touched. `fact_sections_view` exposes the two as `question` and `item`. A reporting tag named `question` or `item` would claim the same column; the build refuses such a tag.

## Notes / Known Gaps

- `fact_sections_view` and `fact_respondents_view` swallow their own failures (they log and return a message rather than throwing, because a dependent view an operator created by hand can block the drop); such a failure appears inside the 200 summary rather than as a 500.
- A survey's schema is created when the survey is first built, not when it is imported; between the import and the first build (which Admin triggers at once) the survey has no schema and UC-004's load skips it with a log line. The next build's back-fill picks those respondents up.
- A schema name is derived from the survey's name at the site, so two sites that install the same definition may hold its star under different names. Nothing in Elicit hard-codes a survey schema's name: FHHS resolves it from `surveys.report_schema` by its survey key at query time.
- The database created before this use case holds the single site-wide star in `surveyreport`. The upgrade migration drops those objects and the next startup regenerates every survey's schema from `survey.answers`; surrogate ids differ from before, so an extract keyed on them must be pulled again. The procedure is in the umbrella's `DeploymentScript.md`.

## Reference

Traces to FR-010, FR-017, FR-026, FR-029 and NFR-010. Implemented by `ETLBuildResource` (`POST /api/etl/build`) over `ETLService.rebuildReportingSchema(Optional<UUID>)`, which runs the same per-survey sequence `ETLService.init()` runs at startup; `ReportSchemaNames` derives and validates schema names. Verified by `ETLBuildResourceTest` (HTTP round-trip: idempotent success, a second survey reusing a step dimension name building its own schema, an unknown key), `ETLServiceTest` (two surveys with overlapping names each get a schema whose columns are disjoint; the back-fill is per survey; step and section keys resolve to dimension ids; the per-item columns), `ReportSchemaNamesTest` (the naming rule), and `ETLBuildResourceUnitTest` (status mapping, the disabled branch, root-cause unwrapping, JSON escaping). The caller is Admin UC-018 (and UC-014/UC-017), which reports the outcome in its result dialog without failing the apply.
