-- ***LICENSE_START***
-- Elicit Survey
-- %%
-- Copyright (C) 2025 The Regents of the University of Michigan - Rogel Cancer Center
-- %%
-- PolyForm Noncommercial License 1.0.0
-- <https://polyformproject.org/licenses/noncommercial/1.0.0>
-- ***LICENSE_END***
---

-- ============================================================
-- ETL fixture gap-fill: every metadata->answers join path exercised.
--
-- V005's 11 metadata rows all join through `section_question_id`. The
-- `question_id`-direct and `step_section_id` join paths used by
-- Sql.FIND_DIMENSTION_VALUES_SQL / Sql.FIND_MISSING_FACT_SECTION_DIMENSIONS_SQL
-- had no fixture coverage at all. Two new tag-only (dimension = NULL)
-- ontology entries are added here, each wired through one of the
-- untested paths, using ids/values that are fully deterministic given
-- V005's insertion order on a fresh test database:
--
--   - survey.questions.id = 2        -> Q37 "terms of use" (CHECKBOX),
--     Tess's answer text_value = 'true' (V005.5, answers.id = 3)
--   - survey.steps_sections.id = 1   -> ss_welcome (step 1 / section 1),
--     matches WELCOME_SECTION display key '0001-0001-0000-0001-...'
--     used throughout QuestionManagerTest
--
-- Kept as separate/synthetic tags (not reusing the real 'terms_consent'
-- tag) so the new paths cannot collide with, or silently overwrite, the
-- constant-value assertion the existing section_question_id-path row
-- already exercises.
-- ============================================================

DO $$
DECLARE
  v_ont_step_probe      bigint;
  v_ont_question_probe  bigint;
BEGIN

  -- Tag-only ontology entry (no survey.dimensions row) exercised via the
  -- step_section_id join path. metadata.value is a constant, so the
  -- resulting dimension value does not depend on which specific answer
  -- row (section header vs. question) within Welcome/Welcome matches.
  INSERT INTO survey.ontology(id, survey_id, name, tag, dimension)
  VALUES (NEXTVAL('survey.ontology_seq'), 1, 'Welcome Step Reached', 'welcome_reached', NULL)
  RETURNING id INTO v_ont_step_probe;

  INSERT INTO survey.metadata(id, survey_id, step_section_id, question_id, section_question_id, ontology_id, value)
  VALUES (NEXTVAL('survey.metadata_seq'), 1, 1, NULL, NULL, v_ont_step_probe, 'WelcomeStepReached');

  -- Tag-only ontology entry exercised via the question_id-direct join
  -- path. metadata.value is NULL, so the resulting dimension value is
  -- derived from Tess's raw answers.text_value ('true' for Q37).
  INSERT INTO survey.ontology(id, survey_id, name, tag, dimension)
  VALUES (NEXTVAL('survey.ontology_seq'), 1, 'Terms Consent Direct Probe', 'terms_consent_direct_probe', NULL)
  RETURNING id INTO v_ont_question_probe;

  INSERT INTO survey.metadata(id, survey_id, step_section_id, question_id, section_question_id, ontology_id, value)
  VALUES (NEXTVAL('survey.metadata_seq'), 1, NULL, 2, NULL, v_ont_question_probe, NULL);

END $$;
