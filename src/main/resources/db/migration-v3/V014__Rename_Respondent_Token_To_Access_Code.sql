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
-- Mirrors db/migration's V014 at the same version number (see that file for the full
-- explanation of the rename).
--
-- This track's frozen V001 and V008 created the same column, unique constraint and
-- three indexes under the same names as the greenfield track, so the body below is
-- byte-identical to the greenfield V014 and a database that converges onto
-- db/migration after repair() ends up with the same names either way.
--------------------------------

ALTER TABLE survey.respondents RENAME COLUMN token TO access_code;

ALTER TABLE survey.respondents
    RENAME CONSTRAINT respondents_token_un TO respondents_access_code_un;

ALTER INDEX IF EXISTS survey.idx_respondents_token
    RENAME TO idx_respondents_access_code;

ALTER INDEX IF EXISTS survey.idx_respondents_token_active
    RENAME TO idx_respondents_access_code_active;

ALTER INDEX IF EXISTS survey.idx_respondents_survey_token
    RENAME TO idx_respondents_survey_access_code;

COMMENT ON INDEX survey.idx_respondents_access_code IS
'Optimizes access-code-based respondent lookups';
