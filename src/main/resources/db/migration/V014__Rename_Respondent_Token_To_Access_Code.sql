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
-- Rename the respondent's login credential from "token" to "access code".
--
-- "Token" was used for two unrelated things: the credential a respondent types in to
-- reach a survey (survey.respondents.token) and the placeholder key substituted into
-- question text (survey.relationships.token). The respondent credential is now called
-- the access code everywhere; survey.relationships.token is deliberately left alone.
--
-- PostgreSQL carries a column rename through the indexes, constraints and views that
-- reference it, so only the names that still say "token" need changing here. The
-- survey.status view (owned by the Admin app) keeps an output column named "token"
-- after this rename; Admin's own migration renames that column.
--
-- Deployment order matters: Admin and FHHS map this column by name, so they must be
-- upgraded in the same release, after Survey has applied this migration.
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
