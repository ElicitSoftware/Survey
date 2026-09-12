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
-- Performance Optimization Indexes for Survey Schema
-- ================================================================================================
-- This migration adds indexes to improve query performance for common access patterns
-- across the survey application tables.
-- ================================================================================================

-- ================================================================================================
-- HIGH PRIORITY INDEXES
-- ================================================================================================

-- Answers table: Composite index for survey and respondent queries (excluding deleted records)
-- Use case: Finding all answers for a specific respondent in a survey
CREATE INDEX IF NOT EXISTS idx_answers_survey_respondent 
ON survey.answers(survey_id, respondent_id) 
WHERE deleted = false;

-- Answers table: Composite index for question-based queries
-- Use case: Analyzing responses to specific questions across respondents
CREATE INDEX IF NOT EXISTS idx_answers_question_respondent 
ON survey.answers(question_id, respondent_id) 
WHERE deleted = false;

-- Answers table: Index for temporal queries on saved responses
-- Use case: Finding recently saved/modified answers
CREATE INDEX IF NOT EXISTS idx_answers_saved_dt 
ON survey.answers(saved_dt) 
WHERE saved_dt IS NOT NULL;

-- Respondents table: Index for completion reporting
-- Use case: Finding completed surveys within a date range
CREATE INDEX IF NOT EXISTS idx_respondents_finalized_dt 
ON survey.respondents(finalized_dt) 
WHERE finalized_dt IS NOT NULL;

-- Respondents table: Index for tracking survey starts
-- Use case: Finding surveys started within a date range
CREATE INDEX IF NOT EXISTS idx_respondents_first_access_dt 
ON survey.respondents(first_access_dt) 
WHERE first_access_dt IS NOT NULL;

-- Respondents table: Composite index for active respondent queries
-- Use case: Finding all active respondents for a specific survey
CREATE INDEX IF NOT EXISTS idx_respondents_active_survey 
ON survey.respondents(survey_id, active) 
WHERE active = true;

-- Metadata table: Composite index for survey-specific metadata lookups
-- Use case: Retrieving metadata for survey configuration
CREATE INDEX IF NOT EXISTS idx_metadata_survey_ontology 
ON survey.metadata(survey_id, ontology_id);

-- Relationships table: Composite index for relationship evaluation
-- Use case: Finding relationships by action and operator type
CREATE INDEX IF NOT EXISTS idx_relationships_action_operator 
ON survey.relationships(action_id, operator_id);

-- ================================================================================================
-- MEDIUM PRIORITY INDEXES
-- ================================================================================================

-- Sections Questions table: Index for ordered section rendering
-- Use case: Retrieving questions in order for a specific section
CREATE INDEX IF NOT EXISTS idx_sections_questions_section_order 
ON survey.sections_questions(section_id, display_order);

-- Steps Sections table: Index for ordered step rendering
-- Use case: Retrieving sections in order for a specific step
CREATE INDEX IF NOT EXISTS idx_steps_sections_step_order 
ON survey.steps_sections(step_id, step_display_order);

-- Respondent Post Survey Actions table: Index for retry/error handling
-- Use case: Finding failed post-survey actions that need retry
CREATE INDEX IF NOT EXISTS idx_respondent_psa_status 
ON survey.respondent_psa(status, tries) 
WHERE uploaded_dt IS NULL;

-- ================================================================================================
-- Index Statistics and Comments
-- ================================================================================================

COMMENT ON INDEX survey.idx_answers_survey_respondent IS 
'Optimizes queries filtering answers by survey_id and respondent_id, excluding deleted records';

COMMENT ON INDEX survey.idx_answers_question_respondent IS 
'Optimizes queries filtering answers by question_id and respondent_id, excluding deleted records';

COMMENT ON INDEX survey.idx_answers_saved_dt IS 
'Optimizes temporal queries on answer save timestamps';

COMMENT ON INDEX survey.idx_respondents_finalized_dt IS 
'Optimizes queries for completed surveys within date ranges';

COMMENT ON INDEX survey.idx_respondents_first_access_dt IS 
'Optimizes queries for survey start tracking and analytics';

COMMENT ON INDEX survey.idx_respondents_active_survey IS 
'Optimizes queries for active respondents in specific surveys';

COMMENT ON INDEX survey.idx_metadata_survey_ontology IS 
'Optimizes survey metadata lookups by survey and ontology';

COMMENT ON INDEX survey.idx_relationships_action_operator IS 
'Optimizes relationship evaluation queries by action and operator';

COMMENT ON INDEX survey.idx_sections_questions_section_order IS 
'Optimizes ordered retrieval of questions within sections';

COMMENT ON INDEX survey.idx_steps_sections_step_order IS 
'Optimizes ordered retrieval of sections within steps';

COMMENT ON INDEX survey.idx_respondent_psa_status IS 
'Optimizes queries for failed post-survey actions needing retry';
