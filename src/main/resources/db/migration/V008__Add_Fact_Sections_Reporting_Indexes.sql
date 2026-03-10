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
-- Reporting Performance Indexes: surveyreport schema
-- ================================================================================================
-- These indexes support ETL/reporting access patterns across fact and dimension tables,
-- including respondent/section/step joins, survey-level filters, and dimension lookups.
-- Note: Overlapping indexes were intentionally reduced to limit write overhead.

-- ================================================================================================
-- HIGH PRIORITY INDEXES
-- ================================================================================================

-- Optimizes respondent-scoped self-joins with section and step instance matching.
CREATE INDEX IF NOT EXISTS idx_fact_sections_respondent_demographic_cancer
ON surveyreport.fact_sections(
    respondent_id,
    section_key,
    step_key,
    step_instance,
    section_instance
);

-- Optimizes respondent-scoped filtering by step key.
CREATE INDEX IF NOT EXISTS idx_fact_sections_respondent_step
ON surveyreport.fact_sections(
    respondent_id,
    step_key
);

-- Fact Sections table: Composite index for survey-specific reporting
-- Use case: Analyzing fact sections for specific surveys and respondents
CREATE INDEX IF NOT EXISTS idx_fact_sections_survey_respondent 
ON surveyreport.fact_sections(survey_id, respondent_id);

-- Fact Sections table: Composite index for section and step aggregations
-- Use case: Aggregating data by section and step for reporting
CREATE INDEX IF NOT EXISTS idx_fact_sections_section_step 
ON surveyreport.fact_sections(section_key, step_key);

-- Fact Sections table: Composite index for step instance queries
-- Use case: Finding specific instances of steps across respondents
CREATE INDEX IF NOT EXISTS idx_fact_sections_step_instance 
ON surveyreport.fact_sections(step_key, step_instance);

-- Fact Sections table: Composite index for section instance queries
-- Use case: Finding specific instances of sections across respondents
CREATE INDEX IF NOT EXISTS idx_fact_sections_section_instance 
ON surveyreport.fact_sections(section_key, section_instance);

-- Fact Sections table: Index for name-based queries
-- Use case: Finding fact sections by name (family member names, etc.)
CREATE INDEX IF NOT EXISTS idx_fact_sections_name 
ON surveyreport.fact_sections(name) 
WHERE name IS NOT NULL;

-- Fact Respondents table: Composite index for date-based analysis
-- Use case: Temporal analysis of survey creation and completion
CREATE INDEX IF NOT EXISTS idx_fact_respondents_date_keys 
ON surveyreport.fact_respondents(created_key, finalized_key);

-- Fact Respondents table: Composite index for completion rate queries
-- Use case: Calculating completion rates by survey over time
CREATE INDEX IF NOT EXISTS idx_fact_respondents_survey_finalized 
ON surveyreport.fact_respondents(survey_id, finalized_key);

-- Fact Respondents table: Index for survey-specific queries
-- Use case: Finding all respondents for a specific survey
CREATE INDEX IF NOT EXISTS idx_fact_respondents_survey 
ON surveyreport.fact_respondents(survey_id);

-- ================================================================================================
-- DIMENSION TABLE INDEXES
-- ================================================================================================

-- Dim Step table: Index already exists on value, adding covering index for common queries
-- Use case: Looking up step information by value with id
CREATE INDEX IF NOT EXISTS idx_dim_step_value_id 
ON surveyreport.dim_step(value, id);

-- Dim Section table: Index already exists on value, adding covering index for common queries
-- Use case: Looking up section information by value with id
CREATE INDEX IF NOT EXISTS idx_dim_section_value_id 
ON surveyreport.dim_section(value, id);

COMMENT ON INDEX surveyreport.idx_fact_sections_survey_respondent IS 
'Optimizes survey-specific reporting queries on fact sections';

COMMENT ON INDEX surveyreport.idx_fact_sections_respondent_demographic_cancer IS
'Optimizes respondent-scoped self-joins and filters by section and step instances';

COMMENT ON INDEX surveyreport.idx_fact_sections_respondent_step IS
'Optimizes respondent-scoped filtering by step_key';

COMMENT ON INDEX surveyreport.idx_fact_sections_section_step IS 
'Optimizes section and step aggregation queries for reporting';

COMMENT ON INDEX surveyreport.idx_fact_sections_step_instance IS 
'Optimizes queries for specific step instances across respondents';

COMMENT ON INDEX surveyreport.idx_fact_sections_section_instance IS 
'Optimizes queries for specific section instances across respondents';

COMMENT ON INDEX surveyreport.idx_fact_sections_name IS 
'Optimizes name-based lookups in fact sections';

COMMENT ON INDEX surveyreport.idx_fact_respondents_date_keys IS 
'Optimizes temporal analysis queries on respondent facts';

COMMENT ON INDEX surveyreport.idx_fact_respondents_survey_finalized IS 
'Optimizes completion rate calculations by survey';

COMMENT ON INDEX surveyreport.idx_fact_respondents_survey IS 
'Optimizes survey-specific respondent queries';

COMMENT ON INDEX surveyreport.idx_dim_step_value_id IS 
'Covering index for step dimension lookups';

COMMENT ON INDEX surveyreport.idx_dim_section_value_id IS 
'Covering index for section dimension lookups';
