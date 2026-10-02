---
-- ***LICENSE_START***
-- Elicit Survey
-- %%
-- Copyright (C) 2025 The Regents of the University of Michigan - Rogel Cancer Center
-- %%
-- PolyForm Noncommercial License 1.0.0
-- <https://polyformproject.org/licenses/noncommercial/1.0.0>
-- ***LICENSE_END***
---

-- ================================================================================================
-- Performance Indexes: survey.respondents
-- ================================================================================================
-- The fact and dimension indexes that used to be here (on the single site-wide surveyreport
-- star) are created per survey schema by the ETL's templates, com.elicitsoftware.etl.Sql,
-- since each survey's fact_sections, dim_step and dim_section now live in a schema of its own
-- (UC-008 BR-007). fact_respondents is a view and needs none.
-- Note: Indexes for survey.subjects and survey.messages are in Admin V0.0.5.

-- ================================================================================================
-- SURVEY.RESPONDENTS INDEXES
-- ================================================================================================
-- Note: Indexes for survey.subjects and survey.messages have been moved to Admin
-- migration V0.0.5 since those tables are created by the Admin service.

-- Align name with existing shared-schema scripts to avoid duplicate indexes.
CREATE INDEX IF NOT EXISTS idx_respondents_survey
ON survey.respondents(survey_id);

-- ================================================================================================
-- HIGH PRIORITY - Token and Authentication
-- ================================================================================================

-- Primary token lookup index (used heavily in respondent authentication)
CREATE INDEX IF NOT EXISTS idx_respondents_token
ON survey.respondents(token);

-- Composite index for active token lookups with partial index
CREATE INDEX IF NOT EXISTS idx_respondents_token_active
ON survey.respondents(token, active)
WHERE active = true;

-- Composite index for survey + token lookup (used in named query)
CREATE INDEX IF NOT EXISTS idx_respondents_survey_token
ON survey.respondents(survey_id, token);

-- ================================================================================================
-- MEDIUM PRIORITY - Respondent Query Optimization
-- ================================================================================================

-- Composite index for survey completion analysis
CREATE INDEX IF NOT EXISTS idx_respondents_survey_finalized
ON survey.respondents(survey_id, finalized_dt);

-- ================================================================================================
-- Index Statistics and Comments (survey schema)
-- ================================================================================================

COMMENT ON INDEX survey.idx_respondents_token IS
'Optimizes token-based respondent lookups';

-- After creating indexes, update statistics for query planner.
ANALYZE survey.respondents;