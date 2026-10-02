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
-- V021 (upgrade track): one reporting schema per survey (UC-008 BR-006).
--
-- A database on this track holds the single site-wide star that V002 created in surveyreport,
-- built for the one survey whose id is 1 and fed by two triggers on survey.respondents. From
-- here on every survey has a star schema of its own, created and filled by the ETL from
-- survey.answers at the next startup (regenerated, not migrated: the surrogate ids will differ,
-- so an extract keyed on them has to be pulled again -- see the umbrella's DeploymentScript.md).
--
-- This migration therefore:
--   1. drops the two triggers and their functions, so no respondent insert or update writes
--      into a reporting schema any more (fact_respondents becomes a view, UC-008 BR-010);
--   2. drops everything in surveyreport except the conformed dim_date and dim_status (and the
--      latter's sequence): the fact tables, both views, dim_step, dim_section and every dim_<tag>
--      with its sequence. FHHS's indexes on fact_sections go with the table;
--   3. adds survey.surveys.report_schema, the per-survey schema name.
--
-- The drop here and the regeneration at the next startup are not one transaction: a startup
-- that fails after this migration leaves the site without reporting tables until a build
-- succeeds. Reports are rebuilt from answers, so nothing is lost; the upgrade procedure says so.
--

-- 1. Triggers and functions. V002 created the functions without a schema qualifier, so they
--    landed in Flyway's default schema; both likely homes are tried.
DROP TRIGGER IF EXISTS fact_respondent_insert ON survey.respondents;
DROP TRIGGER IF EXISTS fact_update ON survey.respondents;
DROP FUNCTION IF EXISTS insert_fact_respondent();
DROP FUNCTION IF EXISTS update_fact_respondent();
DROP FUNCTION IF EXISTS survey.insert_fact_respondent();
DROP FUNCTION IF EXISTS survey.update_fact_respondent();
DROP FUNCTION IF EXISTS public.insert_fact_respondent();
DROP FUNCTION IF EXISTS public.update_fact_respondent();

-- 2. The site-wide star.
DO $$
DECLARE
    r record;
BEGIN
    FOR r IN SELECT viewname AS name FROM pg_views WHERE schemaname = 'surveyreport' LOOP
        EXECUTE format('DROP VIEW IF EXISTS surveyreport.%I CASCADE', r.name);
    END LOOP;
    FOR r IN SELECT tablename AS name FROM pg_tables
              WHERE schemaname = 'surveyreport' AND tablename NOT IN ('dim_date', 'dim_status') LOOP
        EXECUTE format('DROP TABLE IF EXISTS surveyreport.%I CASCADE', r.name);
    END LOOP;
    FOR r IN SELECT sequencename AS name FROM pg_sequences
              WHERE schemaname = 'surveyreport' AND sequencename <> 'dim_status_seq' LOOP
        EXECUTE format('DROP SEQUENCE IF EXISTS surveyreport.%I CASCADE', r.name);
    END LOOP;
END $$;

-- 3. The per-survey schema name.
ALTER TABLE survey.surveys
    ADD COLUMN report_schema character varying(63);

ALTER TABLE survey.surveys
    ADD CONSTRAINT surveys_report_schema_un UNIQUE (report_schema);

ALTER TABLE survey.surveys
    ADD CONSTRAINT surveys_report_schema_ck
        CHECK (report_schema IS NULL OR report_schema ~ '^[a-z_][a-z0-9_]{0,62}$');

COMMENT ON COLUMN survey.surveys.report_schema IS
    'The survey''s own reporting schema at this site; assigned by the first build (UC-008 BR-006), renamed by UC-010, cleared by UC-011. Never carried in a survey definition file.';
