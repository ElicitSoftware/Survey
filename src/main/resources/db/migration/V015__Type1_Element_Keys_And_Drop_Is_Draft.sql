--
-- ***LICENSE_START***
-- Elicit Survey
-- %%
-- Copyright (C) 2025 - 2026 The Regents of the University of Michigan - Rogel Cancer Center
-- %%
-- PolyForm Noncommercial License 1.0.0
-- <https://polyformproject.org/licenses/noncommercial/1.0.0>
-- ***LICENSE_END***
--

--------------------------------
-- 1. Element keys on the five Type 1 definition tables.
--
-- The survey definition file (ELICIT_SURVEY_EXPORT_V1, written by Author and applied by
-- Admin) identifies every record by a cross-instance-portable UUID so that a later
-- update matches the rows a site already holds. V001/V010 gave that key to surveys and
-- to the eight Type 2 structural tables; Admin's export/update already read
-- report_key, post_survey_action_key, dimension_key, ontology_key and metadata_key as
-- well, but nothing created them outside Admin's test bootstrap. Existing rows receive
-- a generated key; imports and updates carry the file's key verbatim.
--
-- These tables are not versioned, so the key alone is unique (no (key, version) pair).
--------------------------------
ALTER TABLE survey.reports
    ADD COLUMN IF NOT EXISTS report_key uuid NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE survey.post_survey_actions
    ADD COLUMN IF NOT EXISTS post_survey_action_key uuid NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE survey.dimensions
    ADD COLUMN IF NOT EXISTS dimension_key uuid NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE survey.ontology
    ADD COLUMN IF NOT EXISTS ontology_key uuid NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE survey.metadata
    ADD COLUMN IF NOT EXISTS metadata_key uuid NOT NULL DEFAULT gen_random_uuid();

CREATE UNIQUE INDEX IF NOT EXISTS reports_key_un             ON survey.reports (report_key);
CREATE UNIQUE INDEX IF NOT EXISTS post_survey_actions_key_un ON survey.post_survey_actions (post_survey_action_key);
CREATE UNIQUE INDEX IF NOT EXISTS dimensions_key_un          ON survey.dimensions (dimension_key);
CREATE UNIQUE INDEX IF NOT EXISTS ontology_key_un            ON survey.ontology (ontology_key);
CREATE UNIQUE INDEX IF NOT EXISTS metadata_key_un            ON survey.metadata (metadata_key);

--------------------------------
-- 2. Drop is_draft from the eight Type 2 structural tables.
--
-- The draft row was designed for an authoring tool that would publish inside the
-- deployed database. Authoring now happens in the Author app's own database and reaches
-- a site as a definition file: Admin's import never creates draft rows and its export
-- never writes them, and Author keeps plain working copies. No application produces or
-- consumes is_draft = true, so the column and its one-draft-per-durable-id partial
-- indexes go. "Current" remains effective_to = '9999-12-31 23:59:59+00'; a closed
-- effective_to is a retired row.
--------------------------------
DROP INDEX IF EXISTS survey.select_groups_one_draft_un;
DROP INDEX IF EXISTS survey.select_items_one_draft_un;
DROP INDEX IF EXISTS survey.steps_one_draft_un;
DROP INDEX IF EXISTS survey.sections_one_draft_un;
DROP INDEX IF EXISTS survey.steps_sections_one_draft_un;
DROP INDEX IF EXISTS survey.questions_one_draft_un;
DROP INDEX IF EXISTS survey.sections_questions_one_draft_un;
DROP INDEX IF EXISTS survey.relationships_one_draft_un;

ALTER TABLE survey.select_groups      DROP COLUMN IF EXISTS is_draft;
ALTER TABLE survey.select_items       DROP COLUMN IF EXISTS is_draft;
ALTER TABLE survey.steps              DROP COLUMN IF EXISTS is_draft;
ALTER TABLE survey.sections           DROP COLUMN IF EXISTS is_draft;
ALTER TABLE survey.steps_sections     DROP COLUMN IF EXISTS is_draft;
ALTER TABLE survey.questions          DROP COLUMN IF EXISTS is_draft;
ALTER TABLE survey.sections_questions DROP COLUMN IF EXISTS is_draft;
ALTER TABLE survey.relationships      DROP COLUMN IF EXISTS is_draft;
