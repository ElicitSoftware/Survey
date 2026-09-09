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
-- Minimal, isolated, generic (non-FHHS) fixture for
-- QuestionManagerBranchCoverageTest — see research/Kimball_type_2.md.
--
-- Its own survey, separate from the V005/V005.5/V005.6 "Library Card
-- Registration" fixture and the V011 "ScdSpecFixture": exercises two
-- QuestionManager.buildDownstreamQuestions branches that no existing
-- fixture relationship reaches because every existing relationship that
-- sets downstream_step_id also sets downstream_s_id:
--   - SHOW,   downstreamStep != null, downstreamSection == null
--     (QuestionManager.buildDownstreamQuestions "Show a step" branch)
--   - REPEAT, downstreamStep != null, downstreamSection == null
--     (QuestionManager.buildRepeatedStep — currently an unimplemented stub)
--
-- The SHOW relationship (Q_A -> StepTwo) and the REPEAT relationship
-- (Q_B -> StepThree) deliberately target *different* downstream steps.
-- QuestionManager.allRelationshipsSatisfide's step-level branch requires
-- EVERY relationship sharing the same (upstreamStep, downstreamStep) pair —
-- via Relationship.findByDownstream_Step_ID — to evaluate true against the
-- SAME triggering answer. Two relationships with different upstream
-- questions and operators (BOOLEAN vs GREATER_THAN) both targeting one
-- downstream step would block each other outright.
--
-- IMPORTANT: today's engine writes Step/Section.displayOrder (not the
-- surrogate id) into survey.answers.step/section AND compares
-- relationships.upstream_step_id (a surrogate steps.id) directly against a
-- displayOrder value in QuestionManager.findRelationshipsByUpstreamQuestion.
-- Both only work because every existing fixture's low display_order values
-- (1, 2, 3...) happen to collide with *some* row's real id in the shared,
-- globally-sequenced steps/sections tables (see Kimball_type_2.md's
-- "INSERT_MISSING_FACT_SECTION_SQL — Display-Order Join Fix", which
-- documents this exact coincidence). Rather than gamble on that coincidence
-- holding for whatever ids happen to be free when this migration runs, this
-- fixture makes it hold *by construction*: each step/section's display_order
-- is set equal to its own generated id in a follow-up UPDATE.
-- ============================================================

DO $$
DECLARE
  v_survey_id     bigint;
  v_step_one      bigint;
  v_step_two      bigint;
  v_step_three    bigint;
  v_section_one   bigint;
  v_section_two   bigint;
  v_section_three bigint;
  v_sg_choice     bigint;
  v_q_a           bigint;
  v_q_b           bigint;
  v_q_c           bigint;
  v_sq_a          bigint;
  v_sq_b          bigint;
  v_sq_c          bigint;
BEGIN

  INSERT INTO survey.surveys(id, name, display_order, title, description, initial_display_key, post_survey_url)
  VALUES (NEXTVAL('survey.surveys_seq'), 'BranchFixture', 901,
          'Branch Coverage Fixture', 'Minimal generic survey used only by QuestionManagerBranchCoverageTest.',
          NULL, NULL)
  RETURNING id INTO v_survey_id;

  -- Placeholder display_order values (-1/-2/-3) must be distinct within the survey to
  -- satisfy steps_survey_display_order / sections_survey_order_un until the follow-up
  -- UPDATE below sets each row's real display_order equal to its own id.
  INSERT INTO survey.steps(id, survey_id, display_order, name, dimension_name, description)
  VALUES (NEXTVAL('survey.steps_seq'), v_survey_id, -1, 'BranchStepOne', 'BranchStepOne', 'Step containing the upstream questions')
  RETURNING id INTO v_step_one;

  INSERT INTO survey.steps(id, survey_id, display_order, name, dimension_name, description)
  VALUES (NEXTVAL('survey.steps_seq'), v_survey_id, -2, 'BranchStepTwo', 'BranchStepTwo', 'Step reached only via the SHOW downstream-step-only relationship')
  RETURNING id INTO v_step_two;

  INSERT INTO survey.steps(id, survey_id, display_order, name, dimension_name, description)
  VALUES (NEXTVAL('survey.steps_seq'), v_survey_id, -3, 'BranchStepThree', 'BranchStepThree', 'Step targeted only by the REPEAT downstream-step-only relationship')
  RETURNING id INTO v_step_three;

  -- Make each step's display_order equal to its own id (see note above).
  UPDATE survey.steps SET display_order = id WHERE id = v_step_one;
  UPDATE survey.steps SET display_order = id WHERE id = v_step_two;
  UPDATE survey.steps SET display_order = id WHERE id = v_step_three;

  INSERT INTO survey.sections(id, survey_id, display_order, name, dimension_name, description)
  VALUES (NEXTVAL('survey.sections_seq'), v_survey_id, -1, 'BranchSectionOne', 'BranchSectionOne', 'Section holding Q_A and Q_B')
  RETURNING id INTO v_section_one;

  INSERT INTO survey.sections(id, survey_id, display_order, name, dimension_name, description)
  VALUES (NEXTVAL('survey.sections_seq'), v_survey_id, -2, 'BranchSectionTwo', 'BranchSectionTwo', 'Section holding Q_C, inside StepTwo')
  RETURNING id INTO v_section_two;

  INSERT INTO survey.sections(id, survey_id, display_order, name, dimension_name, description)
  VALUES (NEXTVAL('survey.sections_seq'), v_survey_id, -3, 'BranchSectionThree', 'BranchSectionThree', 'Section inside StepThree; never reached because buildRepeatedStep is a stub')
  RETURNING id INTO v_section_three;

  UPDATE survey.sections SET display_order = id WHERE id = v_section_one;
  UPDATE survey.sections SET display_order = id WHERE id = v_section_two;
  UPDATE survey.sections SET display_order = id WHERE id = v_section_three;

  INSERT INTO survey.steps_sections(id, survey_id, step_id, step_display_order, section_id, section_display_order, display_key)
  VALUES (NEXTVAL('survey.steps_sections_seq'), v_survey_id, v_step_one, v_step_one, v_section_one, v_section_one,
          LPAD(v_survey_id::text, 4, '0') || '-' || LPAD(v_step_one::text, 4, '0') || '-0000-'
              || LPAD(v_section_one::text, 4, '0') || '-0000-0000-0000');

  INSERT INTO survey.steps_sections(id, survey_id, step_id, step_display_order, section_id, section_display_order, display_key)
  VALUES (NEXTVAL('survey.steps_sections_seq'), v_survey_id, v_step_two, v_step_two, v_section_two, v_section_two,
          LPAD(v_survey_id::text, 4, '0') || '-' || LPAD(v_step_two::text, 4, '0') || '-0000-'
              || LPAD(v_section_two::text, 4, '0') || '-0000-0000-0000');

  INSERT INTO survey.steps_sections(id, survey_id, step_id, step_display_order, section_id, section_display_order, display_key)
  VALUES (NEXTVAL('survey.steps_sections_seq'), v_survey_id, v_step_three, v_step_three, v_section_three, v_section_three,
          LPAD(v_survey_id::text, 4, '0') || '-' || LPAD(v_step_three::text, 4, '0') || '-0000-'
              || LPAD(v_section_three::text, 4, '0') || '-0000-0000-0000');

  UPDATE survey.surveys
  SET initial_display_key = LPAD(v_survey_id::text, 4, '0') || '-' || LPAD(v_step_one::text, 4, '0') || '-0000-'
      || LPAD(v_section_one::text, 4, '0') || '-0000-0000-0000'
  WHERE id = v_survey_id;

  INSERT INTO survey.select_groups(id, survey_id, name, description, data_type)
  VALUES (NEXTVAL('survey.select_groups_seq'), v_survey_id, 'BranchChoice', 'Used only to round-trip SelectItem.selectGroupId', 'Text')
  RETURNING id INTO v_sg_choice;

  INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
  VALUES (NEXTVAL('survey.select_items_seq'), v_survey_id, v_sg_choice, 'Alpha', 1, 'alpha');
  INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
  VALUES (NEXTVAL('survey.select_items_seq'), v_survey_id, v_sg_choice, 'Beta', 2, 'beta');

  -- Q_A: CHECKBOX (type_id 1) — drives the SHOW step-only branch via BOOLEAN operator.
  INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, required,
                                min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
  VALUES (NEXTVAL('survey.questions_seq'), v_survey_id, 1, 'Show StepTwo?', 'Q_A', '', false,
          NULL, NULL, NULL, NULL, NULL, NULL, NULL)
  RETURNING id INTO v_q_a;

  -- Q_B: INTEGER (type_id 5) — drives the REPEAT step-only branch via GREATER_THAN operator.
  INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, required,
                                min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
  VALUES (NEXTVAL('survey.questions_seq'), v_survey_id, 5, 'How many times should StepThree repeat?', 'Q_B', '', false,
          0, 10, NULL, NULL, NULL, NULL, NULL)
  RETURNING id INTO v_q_b;

  -- Q_C: RADIO (type_id 7) — in SectionTwo/StepTwo; only reachable if the SHOW branch fires;
  -- also used to round-trip SelectItem.selectGroupId.
  INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, required,
                                min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
  VALUES (NEXTVAL('survey.questions_seq'), v_survey_id, 7, 'Pick one', 'Q_C', '', false,
          NULL, NULL, NULL, v_sg_choice, NULL, NULL, NULL)
  RETURNING id INTO v_q_c;

  INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
  VALUES (NEXTVAL('survey.sections_questions_seq'), v_survey_id, v_q_a, v_section_one, 1)
  RETURNING id INTO v_sq_a;

  INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
  VALUES (NEXTVAL('survey.sections_questions_seq'), v_survey_id, v_q_b, v_section_one, 2)
  RETURNING id INTO v_sq_b;

  INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
  VALUES (NEXTVAL('survey.sections_questions_seq'), v_survey_id, v_q_c, v_section_two, 1)
  RETURNING id INTO v_sq_c;

  -- R_show_step: Q_A BOOLEAN SHOW -> StepTwo only (downstream_s_id and downstream_sq_id
  -- both NULL — every existing fixture relationship sets downstream_s_id alongside
  -- downstream_step_id, which always routes through the downstreamSection branch first).
  -- upstream_step_id = v_step_one: QuestionManager.findRelationshipsByUpstreamQuestion
  -- compares this surrogate id directly against Q_A's displayOrder-based key segment,
  -- which is v_step_one too (display_order was set equal to id above).
  INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id,
                                    downstream_step_id, downstream_s_id, downstream_sq_id,
                                    operator_id, action_id, description, token, reference_value, default_upstream_value)
  VALUES (NEXTVAL('survey.relationships_seq'), v_survey_id, v_step_one, v_sq_a,
          v_step_two, NULL, NULL,
          1, 1, 'Show StepTwo (step-only SHOW branch) when Q_A is checked', NULL, '', '');

  -- R_repeat_step: Q_B GREATER_THAN '0' REPEAT -> StepThree only (a separate downstream
  -- step from R_show_step's — see header comment on allRelationshipsSatisfide). buildRepeatedStep
  -- is currently an unimplemented stub (QuestionManager.java: "TODO" / "not yet implemented"),
  -- so this relationship is expected to be a no-op today — see repeatStepOnlyBranch_isCurrentlyANoOp.
  INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id,
                                    downstream_step_id, downstream_s_id, downstream_sq_id,
                                    operator_id, action_id, description, token, reference_value, default_upstream_value)
  VALUES (NEXTVAL('survey.relationships_seq'), v_survey_id, v_step_one, v_sq_b,
          v_step_three, NULL, NULL,
          2, 2, 'Repeat StepThree (step-only REPEAT branch, buildRepeatedStep stub) when Q_B > 0', NULL, '0', '');

END $$;
