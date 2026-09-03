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
-- Minimal, isolated fixture for the Kimball Type 2 SCD spec suite
-- (src/test/java/com/elicitsoftware/scd/**) — see research/Kimball_type_2.md.
--
-- Deliberately its own survey, separate from the V005/V005.5/V005.6
-- "Library Card Registration" fixture used by QuestionManagerTest and the
-- ETL characterization tests: the scd/** spec classes version, retire,
-- draft, and publish rows once the Type 2 migration lands, and that
-- mutation must never touch Track A's data.
--
-- Only plain inserts against TODAY's columns — this is harmless before the
-- Type 2 migration exists (nothing references the not-yet-created
-- version/effective_from/effective_to/is_draft columns) and remains valid
-- afterward (those columns are added NOT NULL DEFAULT ..., so existing
-- rows — including these — get backfilled automatically per the doc's
-- Migration Strategy step 2).
--
-- One step, one section, one steps_sections join row, one question, one
-- sections_questions join row, one select_group + select_item, and one
-- simple relationship — just enough surface for each *VersioningSpecTest
-- to close-current/insert-new against a real row.
-- ============================================================

DO $$
DECLARE
  v_survey_id   bigint;
  v_step_id     bigint;
  v_section_id  bigint;
  v_ss_id       bigint;
  v_sg_id       bigint;
  v_question_id bigint;
  v_sq_id       bigint;
BEGIN

  INSERT INTO survey.surveys(id, name, display_order, title, description, initial_display_key, post_survey_url)
  VALUES (NEXTVAL('survey.surveys_seq'), 'ScdSpecFixture', 900,
          'SCD Spec Fixture', 'Minimal survey used only by the Type 2 SCD spec test suite.',
          NULL, NULL)
  RETURNING id INTO v_survey_id;

  INSERT INTO survey.steps(id, survey_id, display_order, name, dimension_name, description)
  VALUES (NEXTVAL('survey.steps_seq'), v_survey_id, 1, 'ScdStep', 'ScdStep', 'Single step')
  RETURNING id INTO v_step_id;

  INSERT INTO survey.sections(id, survey_id, display_order, name, dimension_name, description)
  VALUES (NEXTVAL('survey.sections_seq'), v_survey_id, 1, 'ScdSection', 'ScdSection', 'Single section')
  RETURNING id INTO v_section_id;

  INSERT INTO survey.steps_sections(id, survey_id, step_id, step_display_order, section_id, section_display_order, display_key)
  VALUES (NEXTVAL('survey.steps_sections_seq'), v_survey_id, v_step_id, 1, v_section_id, 1,
          LPAD(v_survey_id::text, 4, '0') || '-0001-0000-0001-0000-0000-0000')
  RETURNING id INTO v_ss_id;

  INSERT INTO survey.select_groups(id, survey_id, name, description, data_type)
  VALUES (NEXTVAL('survey.select_groups_seq'), v_survey_id, 'ScdSelectGroup', 'Single select group', 'Text')
  RETURNING id INTO v_sg_id;

  INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
  VALUES (NEXTVAL('survey.select_items_seq'), v_survey_id, v_sg_id, 'Option A', 1, 'A');

  -- type_id 7 = RADIO, seeded by V003/earlier migrations (see survey.question_types).
  INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, required,
                                min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value, variant)
  VALUES (NEXTVAL('survey.questions_seq'), v_survey_id, 7, 'Original wording', 'ScdQ', '', false,
          NULL, NULL, NULL, v_sg_id, NULL, NULL, NULL, NULL)
  RETURNING id INTO v_question_id;

  INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
  VALUES (NEXTVAL('survey.sections_questions_seq'), v_survey_id, v_question_id, v_section_id, 1)
  RETURNING id INTO v_sq_id;

  -- operator_id 1 = BOOLEAN, action_id 1 = SHOW (see survey.operator_types / action_types
  -- seeded by V003/earlier migrations, per the mapping documented in V005__Library_Test.sql)
  -- — a trivial self-referential-shaped relationship, present only so
  -- RelationshipsVersioningSpecTest has a row to version.
  INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id,
                                    downstream_step_id, downstream_s_id, downstream_sq_id,
                                    operator_id, action_id, description, token, reference_value)
  VALUES (NEXTVAL('survey.relationships_seq'), v_survey_id, v_step_id, v_sq_id,
          v_step_id, NULL, v_sq_id,
          1, 1, 'ScdSpecFixture self-reference', NULL, 'TRUE');

END $$;
