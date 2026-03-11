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
-- Performance Indexes: surveyreport and survey schemas
-- ================================================================================================
-- These indexes support ETL/reporting access patterns across fact and dimension tables,
-- including respondent/section/step joins, survey-level filters, dimension lookups,
-- and operational query patterns in survey.subjects/respondents/messages.
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

-- ================================================================================================
-- ADDITIONAL SURVEY SCHEMA INDEXES (merged from former V010)
-- ================================================================================================

-- ================================================================================================
-- CRITICAL PRIORITY - Missing Foreign Key Indexes
-- ================================================================================================

-- Subjects table foreign key indexes (critical for JOINs and referential integrity checks)
CREATE INDEX IF NOT EXISTS idx_subjects_survey_fk
ON survey.subjects(survey_id);

CREATE INDEX IF NOT EXISTS idx_subjects_respondent_fk
ON survey.subjects(respondent_id);

-- Note: idx_subjects_department_fk may already exist, but created explicitly for clarity.
CREATE INDEX IF NOT EXISTS idx_subjects_department_fk
ON survey.subjects(department_id);

-- Align name with existing shared-schema scripts to avoid duplicate indexes.
CREATE INDEX IF NOT EXISTS idx_respondents_survey
ON survey.respondents(survey_id);

-- Messages table foreign key indexes
CREATE INDEX IF NOT EXISTS idx_messages_message_type_fk
ON survey.messages(message_type);

-- ================================================================================================
-- HIGH PRIORITY - Text Search Optimization
-- ================================================================================================

-- Functional indexes for case-insensitive text search.
CREATE INDEX IF NOT EXISTS idx_subjects_firstname_lower
ON survey.subjects(LOWER(firstname));

CREATE INDEX IF NOT EXISTS idx_subjects_lastname_lower
ON survey.subjects(LOWER(lastname));

CREATE INDEX IF NOT EXISTS idx_subjects_middlename_lower
ON survey.subjects(LOWER(middlename))
WHERE middlename IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_subjects_email_lower
ON survey.subjects(LOWER(email))
WHERE email IS NOT NULL;

-- Composite index for common name searches (last name + first name)
CREATE INDEX IF NOT EXISTS idx_subjects_name_search
ON survey.subjects(LOWER(lastname), LOWER(firstname));

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
-- MEDIUM PRIORITY - Composite Indexes for Common Query Patterns
-- ================================================================================================

-- Subject lookups by department and date (for reporting and filtered searches)
CREATE INDEX IF NOT EXISTS idx_subjects_dept_date
ON survey.subjects(department_id, created_dt DESC);

-- Subject lookups with xid filtering
CREATE INDEX IF NOT EXISTS idx_subjects_dept_date_xid
ON survey.subjects(department_id, created_dt, xid)
WHERE xid IS NOT NULL;

-- Email lookup with department context
CREATE INDEX IF NOT EXISTS idx_subjects_email_dept
ON survey.subjects(email, department_id)
WHERE email IS NOT NULL;

-- ================================================================================================
-- MEDIUM PRIORITY - Message Processing Optimization
-- ================================================================================================

-- Composite index for unsent messages ordered by creation date
CREATE INDEX IF NOT EXISTS idx_messages_unsent_created
ON survey.messages(created_dt DESC)
WHERE sent_dt IS NULL;

-- Composite index for message history by subject and type
CREATE INDEX IF NOT EXISTS idx_messages_subject_type_sent
ON survey.messages(subject_id, message_type, sent_dt);

-- Composite index for sent messages with recency
CREATE INDEX IF NOT EXISTS idx_messages_sent_recent
ON survey.messages(sent_dt DESC, subject_id)
WHERE sent_dt IS NOT NULL;

-- ================================================================================================
-- MEDIUM PRIORITY - Respondent Query Optimization
-- ================================================================================================

-- Composite index for survey completion analysis
CREATE INDEX IF NOT EXISTS idx_respondents_survey_finalized
ON survey.respondents(survey_id, finalized_dt);

-- ================================================================================================
-- LOW PRIORITY - Additional Optimizations
-- ================================================================================================

-- Phone number searches (less common but can be filtered)
CREATE INDEX IF NOT EXISTS idx_subjects_phone
ON survey.subjects(phone)
WHERE phone IS NOT NULL;

-- Department + XID lookup is already covered by unique constraint/index:
-- subjects_xid_department_un (xid, department_id)

-- ================================================================================================
-- Index Statistics and Comments (survey schema)
-- ================================================================================================

COMMENT ON INDEX survey.idx_subjects_firstname_lower IS
'Supports case-insensitive filtering on subject first name';

COMMENT ON INDEX survey.idx_subjects_lastname_lower IS
'Supports case-insensitive filtering on subject last name';

COMMENT ON INDEX survey.idx_subjects_email_lower IS
'Supports case-insensitive filtering on subject email';

COMMENT ON INDEX survey.idx_respondents_token IS
'Optimizes token-based respondent lookups';

COMMENT ON INDEX survey.idx_messages_unsent_created IS
'Optimizes scheduled processing of unsent messages';

COMMENT ON INDEX survey.idx_subjects_dept_date IS
'Optimizes department-scoped subject queries ordered by creation time';

-- After creating indexes, update statistics for query planner.
ANALYZE survey.subjects;
ANALYZE survey.respondents;
ANALYZE survey.messages;
ANALYZE survey.departments;
