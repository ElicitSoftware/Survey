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
-- V021: one reporting schema per survey (UC-008 BR-006).
--
-- Every survey gets a star schema of its own, created by the ETL the first time the survey is
-- built and named here. The name is derived from the survey's name at this site, assigned once,
-- changed only by a rename (UC-010) and cleared only by a drop (UC-011). It is site-local data:
-- a survey definition file never carries it, and an import never sets it.
--
-- On the greenfield track there is nothing to drop: V002 creates only the common dim_date and
-- dim_status in surveyreport, and no trigger ever wrote into a reporting schema. The upgrade
-- track's V021 drops the single site-wide star this track never had.
--
ALTER TABLE survey.surveys
    ADD COLUMN report_schema character varying(63);

ALTER TABLE survey.surveys
    ADD CONSTRAINT surveys_report_schema_un UNIQUE (report_schema);

ALTER TABLE survey.surveys
    ADD CONSTRAINT surveys_report_schema_ck
        CHECK (report_schema IS NULL OR report_schema ~ '^[a-z_][a-z0-9_]{0,62}$');

COMMENT ON COLUMN survey.surveys.report_schema IS
    'The survey''s own reporting schema at this site; assigned by the first build (UC-008 BR-006), renamed by UC-010, cleared by UC-011. Never carried in a survey definition file.';
