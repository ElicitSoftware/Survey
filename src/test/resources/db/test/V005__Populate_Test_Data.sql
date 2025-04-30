-- ***LICENSE_START***
-- Elicit FHHS
-- %%
-- Copyright (C) 2025 The Regents of the University of Michigan - Rogel Cancer Center
-- %%
-- PolyForm Noncommercial License 1.0.0
-- <https://polyformproject.org/licenses/noncommercial/1.0.0>
-- ***LICENSE_END***
---

-- SURVEYS (id, name, title, description, initial_display_key, post_survey_url) --
INSERT INTO survey.surveys(id, display_order, name, title, description, initial_display_key, post_survey_url)
VALUES (NEXTVAL('survey.surveys_seq'), 1, 'Sample Survey', 'Sample', 'Elicit Sample Survey',
        '0001-0001-0000-0001-0000-0000-0000', NULL);

-- SELECT GROUPS (id, name, description) --
INSERT INTO survey.select_groups(id, survey_id, name, description)
VALUES (NEXTVAL('survey.select_groups_seq'), 1, 'YesNo', 'Boolean');
INSERT INTO survey.select_groups(id, survey_id, name, description)
VALUES (NEXTVAL('survey.select_groups_seq'), 1, 'TypesOfPet', 'Types of pets');
INSERT INTO survey.select_groups(id, survey_id, name, description)
VALUES (NEXTVAL('survey.select_groups_seq'), 1, 'Relationships', 'Rrelationship between the respondent and others');
INSERT INTO survey.select_groups(id, survey_id, name, description)
VALUES (NEXTVAL('survey.select_groups_seq'), 1, 'Sex', 'Sex');
INSERT INTO survey.select_groups(id, survey_id, name, description)
VALUES (NEXTVAL('survey.select_groups_seq'), 1, 'RoommateTypes', 'Roommates, Family, Pets');

-- SELECT ITEMS (id, group_id, display_text, display_order, coded_value) --
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), 1, 1, 'Yes', '1', 'TRUE');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), 1, 1, 'No', '2', 'FALSE');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), 1, 2, 'Bird', '1', 'bird');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), 1, 2, 'Cat', '2', 'cat');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), 1, 2, 'Dog', '3', 'dog');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), 1, 2, 'Fish', '4', 'fish');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), 1, 2, 'Reptile', '5', 'reptile');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), 1, 3, 'Grandparent', '1', 'grandparent');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), 1, 3, 'Parent', '2', 'parent');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), 1, 3, 'Spouse', '3', 'spouse');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), 1, 3, 'Child', '4', 'child');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), 1, 3, 'Brother', '5', 'brother');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), 1, 3, 'Sister', '6', 'sister');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), 1, 4, 'Female', '1', 'female');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), 1, 4, 'Male', '2', 'male');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), 1, 4, 'Other', '3', 'other');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), 1, 4, 'Unknown', '4', 'unknown');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), 1, 5, 'Family Members', '1', 'family');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), 1, 5, 'Others', '2', 'roommates');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), 1, 5, 'Pets', '3', 'pets');

-- STEPS (id, name, title, description) --
INSERT INTO survey.steps(id, survey_id, display_order, name, dimension_name, description)
VALUES (NEXTVAL('survey.steps_seq'), 1, 1, 'Welcome', 'Welcome', 'explination about the survey');
INSERT INTO survey.steps(id, survey_id, display_order, name, dimension_name, description)
VALUES (NEXTVAL('survey.steps_seq'), 1, 2, 'Respondent', 'Proband','Information about the respondent');
INSERT INTO survey.steps(id, survey_id, display_order, name, dimension_name, description)
VALUES (NEXTVAL('survey.steps_seq'), 1, 3, 'Occupants', 'Occupants', 'Information about others living with the respondent');
INSERT INTO survey.steps(id, survey_id, display_order, name, dimension_name, description)
VALUES (NEXTVAL('survey.steps_seq'), 1, 4, 'Family', 'Family','Family Members');
INSERT INTO survey.steps(id, survey_id, display_order, name, dimension_name, description)
VALUES (NEXTVAL('survey.steps_seq'), 1, 5, 'Roommates', 'Roommates','Roommates');
INSERT INTO survey.steps(id, survey_id, display_order, name, dimension_name, description)
VALUES (NEXTVAL('survey.steps_seq'), 1, 6, 'Pets', 'Pets', 'Pets');

-- SECTIONS (id, sub_section, name, description) --
INSERT INTO survey.sections(id, survey_id, display_order, name, dimension_name, description)
VALUES (NEXTVAL('survey.sections_seq'), 1, 1, 'Welcome','Welcome', 'Description of the survey');
INSERT INTO survey.sections(id, survey_id, display_order, name, dimension_name, description)
VALUES (NEXTVAL('survey.sections_seq'), 1, 2, 'Respondent','Respondent', 'Who is taking the survey');
INSERT INTO survey.sections(id, survey_id, display_order, name, dimension_name, description)
VALUES (NEXTVAL('survey.sections_seq'), 1, 3, 'Occupants', 'Occupants','Who lives in the home');
INSERT INTO survey.sections(id, survey_id, display_order, name, dimension_name, description)
VALUES (NEXTVAL('survey.sections_seq'), 1, 4, '{R1''s|Your} Family Member {S#} - {S1}', 'Family Member',
        'Information about family members in the house');
INSERT INTO survey.sections(id, survey_id, display_order, name, dimension_name, description)
VALUES (NEXTVAL('survey.sections_seq'), 1, 5, '{R1''s|Your} Roommate {S#} - {S1}', 'Roommate',
        'Information about the roommates in the house');
INSERT INTO survey.sections(id, survey_id, display_order, name, dimension_name, description)
VALUES (NEXTVAL('survey.sections_seq'), 1, 6, '{R1''s|Your} Pet {S#} - {S1}', 'Pet',
        'Information about the pets in the house');

-- STEPS SECTIONS (id, survey_id, step_id, step_display_order, section_id, section_display_order, display_key) --
INSERT INTO survey.steps_sections(id, survey_id, step_id, step_display_order, section_id, section_display_order,
                                  display_key)
VALUES (NEXTVAL('survey.steps_sections_seq'), 1, 1, 1, 1, 1, '0001-0001-0000-0001-0000-0000-0000');
INSERT INTO survey.steps_sections(id, survey_id, step_id, step_display_order, section_id, section_display_order,
                                  display_key)
VALUES (NEXTVAL('survey.steps_sections_seq'), 1, 2, 2, 2, 2, '0001-0002-0000-0002-0000-0000-0000');
INSERT INTO survey.steps_sections(id, survey_id, step_id, step_display_order, section_id, section_display_order,
                                  display_key)
VALUES (NEXTVAL('survey.steps_sections_seq'), 1, 3, 3, 3, 3, '0001-0003-0000-0003-0000-0000-0000');
INSERT INTO survey.steps_sections(id, survey_id, step_id, step_display_order, section_id, section_display_order,
                                  display_key)
VALUES (NEXTVAL('survey.steps_sections_seq'), 1, 4, 4, 4, 4, '0001-0004-0000-0004-0000-0000-0000');
INSERT INTO survey.steps_sections(id, survey_id, step_id, step_display_order, section_id, section_display_order,
                                  display_key)
VALUES (NEXTVAL('survey.steps_sections_seq'), 1, 5, 5, 5, 5, '0001-0005-0000-0005-0000-0000-0000');
INSERT INTO survey.steps_sections(id, survey_id, step_id, step_display_order, section_id, section_display_order,
                                  display_key)
VALUES (NEXTVAL('survey.steps_sections_seq'), 1, 6, 6, 6, 6, '0001-0006-0000-0006-0000-0000-0000');

-- QUESTIONS (id, type_id, text, short_text, tool_tip, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value) --
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value,
                             max_value, validation_text, select_group_id)
VALUES (NEXTVAL('survey.questions_seq'), 1, 4,
        '<h1>Welcome to the Elicit sample survey.</h1><br/><p>This sample survey was written to be simple enough for everyone to understand while still showcasing the power of Elicit.</p><p>Over the next few screens, you will see examples of:</p><ul><li>Question types: checkboxes, radio, text, multi-select etc...</li><li>Tooltips</li><li>Repeating questions - using previous answers to repeat section.</li><li>Dynamic text replacement - using previous answers to build new questions</li></ul></p>',
        'Introduction', 'HTML datatype', '', FALSE, null, null, '', NULL);
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value,
                             max_value, validation_text, select_group_id)
VALUES (NEXTVAL('survey.questions_seq'), 1, 1, 'By continuing, you agree to participate in this anonymous survey.',
        'Consent', 'Boolean datatype', '', TRUE, null, null, '', NULL);
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value,
                             max_value, validation_text, select_group_id)
VALUES (NEXTVAL('survey.questions_seq'), 1, 7, 'Are you filling out this questionnaire for someone else?',
        'Is Respondent', 'Radio datatype (select one)', '', TRUE, null, null, '', 1);
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value,
                             max_value, validation_text, select_group_id)
VALUES (NEXTVAL('survey.questions_seq'), 1, 8, 'What is the name of the person you are filling this out for?',
        'Respondent''s Name', 'Text datatype', '', TRUE, 2, 50, 'Respondent''s name must be greater than 2 characters',
        NULL);
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value,
                             max_value, validation_text, select_group_id)
VALUES (NEXTVAL('survey.questions_seq'), 1, 2, 'What is {NAME''s|your} Birthday?', 'Birthday', 'Date datatype', '',
        TRUE, null, null, 'Respondent''s Birthday', NULL);
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value,
                             max_value, validation_text, select_group_id)
VALUES (NEXTVAL('survey.questions_seq'), 1, 5, 'What is {NAME''s|your} current age?', 'Age', 'Number datatype',
        'vertical', TRUE, 0, 115, 'Age must be between 0 and 115', NULL);
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value,
                             max_value, validation_text, select_group_id)
VALUES (NEXTVAL('survey.questions_seq'), 1, 7, 'What sex {is NAME| are you}?', 'Sex', 'Radio datatype not required', '',
        FALSE, null, null,
        'I realize this should be Gender for the humans but I want to use this question for an example in the reporting.',
        4);
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value,
                             max_value, validation_text, select_group_id)
VALUES (NEXTVAL('survey.questions_seq'), 1, 11, 'Who lives with {R1|you}', 'Types of roommates',
        'Combo Box with multi select ', 'vertical', FALSE, null, null, '', 5);
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value,
                             max_value, validation_text, select_group_id)
VALUES (NEXTVAL('survey.questions_seq'), 1, 5, 'How many family members live with {R1|you}?', 'family count',
        'Number with Repeat action type', 'vertical', TRUE, 0, null, 'Count must be => 0', NULL);
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value,
                             max_value, validation_text, select_group_id)
VALUES (NEXTVAL('survey.questions_seq'), 1, 5, 'How many roommates {does R1|do you} have?', 'roommate count',
        'Number with Repeat action type', 'vertical', TRUE, 0, null, 'Count must be => 0', NULL);
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value,
                             max_value, validation_text, select_group_id)
VALUES (NEXTVAL('survey.questions_seq'), 1, 5, 'How many pets are in {R1''s|your} the home?', 'pet count',
        'Number with Repeat action type', 'vertical', TRUE, 0, null, 'Count must be => 0', NULL);
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value,
                             max_value, validation_text, select_group_id)
VALUES (NEXTVAL('survey.questions_seq'), 1, 8, 'What is the name of {R1''s|your} {Q#} family member?',
        'family member name', 'Text for display later. ', '', TRUE, 2, 50,
        'Family member''s name must be greater than 2 characters', NULL);
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value,
                             max_value, validation_text, select_group_id)
VALUES (NEXTVAL('survey.questions_seq'), 1, 8, 'What is the name of {R1''s|your} {Q#} roommate?', 'roommate name',
        'Text for display later. ', '', TRUE, 2, 50, 'Roommate''s name must be greater than 2 characters', NULL);
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value,
                             max_value, validation_text, select_group_id)
VALUES (NEXTVAL('survey.questions_seq'), 1, 8, 'What is the name of pet {Q#}?', 'pet name', 'Text for display later. ',
        '', TRUE, 1, 50, 'Pet''s name can not be blank', NULL);
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value,
                             max_value, validation_text, select_group_id)
VALUES (NEXTVAL('survey.questions_seq'), 1, 3, '{NAME} is {R1''s|my}:', 'relationship', 'Combo Box select one', '',
        TRUE, null, null, '', 3);
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value,
                             max_value, validation_text, select_group_id)
VALUES (NEXTVAL('survey.questions_seq'), 1, 3, 'What type of pet is {NAME}?', 'Pet type', 'Combo Box select one', '',
        TRUE, null, null, '', 2);

-- SECTIONS QUESTIONS (id, survey_id, question_id, section_id, display_order) --
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), 1, 1, 1, 1);
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), 1, 2, 1, 2);
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), 1, 3, 1, 3);
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), 1, 4, 1, 4);
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), 1, 5, 2, 1);
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), 1, 7, 2, 2);
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), 1, 8, 3, 1);
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), 1, 9, 3, 2);
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), 1, 12, 3, 3);
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), 1, 10, 3, 4);
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), 1, 13, 3, 5);
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), 1, 11, 3, 6);
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), 1, 14, 3, 7);
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), 1, 15, 4, 1);
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), 1, 6, 4, 2);
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), 1, 7, 4, 3);
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), 1, 6, 5, 1);
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), 1, 7, 5, 2);
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), 1, 16, 6, 1);
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), 1, 6, 6, 2);
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), 1, 7, 6, 3);

-- RELATIONSHIPS (id, survey_id, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_s_id, downstream_sq_id, operator_id, action_id, description, token, reference_value, default_upstream_value, override_upstream_value) --
INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_sq_id,
                                 downstream_s_id, operator_id, action_id, token, description, reference_value,
                                 default_upstream_value)
VALUES (NEXTVAL('survey.relationships_seq'), 1, 1, 2, null, 3, null, 1, 1, null, 'Show Participant', '', '');
INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_sq_id,
                                 downstream_s_id, operator_id, action_id, token, description, reference_value,
                                 default_upstream_value)
VALUES (NEXTVAL('survey.relationships_seq'), 1, 1, 3, null, 4, null, 1, 1, null, 'Show Respondent''s Name', '', '');
INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_sq_id,
                                 downstream_s_id, operator_id, action_id, token, description, reference_value,
                                 default_upstream_value)
VALUES (NEXTVAL('survey.relationships_seq'), 1, 1, 4, null, NULL, 2, 5, 3, 'NAME', 'Replace Respondent''s Name', '',
        '');
INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_sq_id,
                                 downstream_s_id, operator_id, action_id, token, description, reference_value,
                                 default_upstream_value)
VALUES (NEXTVAL('survey.relationships_seq'), 1, 1, 4, 3, NULL, 3, 5, 3, 'R1', 'Replace Respondent''s Name', '', '');
INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_sq_id,
                                 downstream_s_id, operator_id, action_id, token, description, reference_value,
                                 default_upstream_value)
VALUES (NEXTVAL('survey.relationships_seq'), 1, 1, 4, 4, NULL, 4, 5, 3, 'R1', 'Replace Respondent''s Name', '', '');
INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_sq_id,
                                 downstream_s_id, operator_id, action_id, token, description, reference_value,
                                 default_upstream_value)
VALUES (NEXTVAL('survey.relationships_seq'), 1, 1, 4, 5, NULL, 5, 5, 3, 'R1', 'Replace Respondent''s Name', '', '');
INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_sq_id,
                                 downstream_s_id, operator_id, action_id, token, description, reference_value,
                                 default_upstream_value)
VALUES (NEXTVAL('survey.relationships_seq'), 1, 1, 4, 6, NULL, 6, 5, 3, 'R1', 'Replace Respondent''s Name', '', '');
INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_sq_id,
                                 downstream_s_id, operator_id, action_id, token, description, reference_value,
                                 default_upstream_value)
VALUES (NEXTVAL('survey.relationships_seq'), 1, 1, 3, 2, NULL, 2, 5, 1, null, 'Show Respondent section', '', '');
INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_sq_id,
                                 downstream_s_id, operator_id, action_id, token, description, reference_value,
                                 default_upstream_value)
VALUES (NEXTVAL('survey.relationships_seq'), 1, 1, 3, 3, NULL, 3, 5, 1, null, 'Show Occupants Section', '', '');
INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_sq_id,
                                 downstream_s_id, operator_id, action_id, token, description, reference_value,
                                 default_upstream_value)
VALUES (NEXTVAL('survey.relationships_seq'), 1, 3, 7, 3, 8, 3, 6, 1, null, 'Show Family Count', 'family', '');
INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_sq_id,
                                 downstream_s_id, operator_id, action_id, token, description, reference_value,
                                 default_upstream_value)
VALUES (NEXTVAL('survey.relationships_seq'), 1, 3, 8, 3, 9, 3, 2, 2, null, 'Show Family names', '0', '');
INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_sq_id,
                                 downstream_s_id, operator_id, action_id, token, description, reference_value,
                                 default_upstream_value)
VALUES (NEXTVAL('survey.relationships_seq'), 1, 3, 9, 4, NULL, null, 5, 1, 'S1', 'Show X family sections', '', '');
INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_sq_id,
                                 downstream_s_id, operator_id, action_id, token, description, reference_value,
                                 default_upstream_value)
VALUES (NEXTVAL('survey.relationships_seq'), 1, 3, 9, 4, NULL, null, 5, 3, 'NAME', '', '', '');
INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_sq_id,
                                 downstream_s_id, operator_id, action_id, token, description, reference_value,
                                 default_upstream_value)
VALUES (NEXTVAL('survey.relationships_seq'), 1, 3, 7, 3, 10, 3, 6, 1, null, 'Show Roommate Count', 'roommates', '');
INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_sq_id,
                                 downstream_s_id, operator_id, action_id, token, description, reference_value,
                                 default_upstream_value)
VALUES (NEXTVAL('survey.relationships_seq'), 1, 3, 10, 3, 11, 3, 2, 2, null, 'Show Roommates names', '0', '');
INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_sq_id,
                                 downstream_s_id, operator_id, action_id, token, description, reference_value,
                                 default_upstream_value)
VALUES (NEXTVAL('survey.relationships_seq'), 1, 3, 11, 5, NULL, null, 5, 1, 'S1', 'Show X roommate sections', '', '');
INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_sq_id,
                                 downstream_s_id, operator_id, action_id, token, description, reference_value,
                                 default_upstream_value)
VALUES (NEXTVAL('survey.relationships_seq'), 1, 3, 11, 5, NULL, null, 5, 3, 'NAME', '', '', '');
INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_sq_id,
                                 downstream_s_id, operator_id, action_id, token, description, reference_value,
                                 default_upstream_value)
VALUES (NEXTVAL('survey.relationships_seq'), 1, 3, 7, 3, 12, 3, 6, 1, null, 'Show Pet Count', 'pets', '');
INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_sq_id,
                                 downstream_s_id, operator_id, action_id, token, description, reference_value,
                                 default_upstream_value)
VALUES (NEXTVAL('survey.relationships_seq'), 1, 3, 12, 3, 13, 3, 2, 2, null, 'Show Pets Names', '0', '');
INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_sq_id,
                                 downstream_s_id, operator_id, action_id, token, description, reference_value,
                                 default_upstream_value)
VALUES (NEXTVAL('survey.relationships_seq'), 1, 3, 13, 6, NULL, null, 5, 1, 'S1', 'Show X pet sections', '', '');
INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_sq_id,
                                 downstream_s_id, operator_id, action_id, token, description, reference_value,
                                 default_upstream_value)
VALUES (NEXTVAL('survey.relationships_seq'), 1, 3, 13, 6, NULL, null, 5, 3, 'NAME', '', '', '');

-- REPORTS (id, survey_id, name, description, url, display_order) --

-- ONTOLOGY (id, name, tag) --
INSERT INTO survey.ontology(id, survey_id, name, tag)
VALUES (NEXTVAL('survey.ontology_seq'), 1, 'Sample', 'Respondent');
INSERT INTO survey.ontology(id, survey_id, name, tag)
VALUES (NEXTVAL('survey.ontology_seq'), 1, 'Sample', 'Age');
INSERT INTO survey.ontology(id, survey_id, name, tag)
VALUES (NEXTVAL('survey.ontology_seq'), 1, 'Sample', 'Sex');
INSERT INTO survey.ontology(id, survey_id, name, tag)
VALUES (NEXTVAL('survey.ontology_seq'), 1, 'Sample', 'Relationship');
INSERT INTO survey.ontology(id, survey_id, name, tag)
VALUES (NEXTVAL('survey.ontology_seq'), 1, 'Sample', 'PetType');
INSERT INTO survey.ontology(id, survey_id, name, tag)
VALUES (NEXTVAL('survey.ontology_seq'), 1, 'Sample', 'Family');
INSERT INTO survey.ontology(id, survey_id, name, tag)
VALUES (NEXTVAL('survey.ontology_seq'), 1, 'Sample', 'Roommates');
INSERT INTO survey.ontology(id, survey_id, name, tag)
VALUES (NEXTVAL('survey.ontology_seq'), 1, 'Sample', 'Pets');
INSERT INTO survey.ontology(id, survey_id, name, tag)
VALUES (NEXTVAL('survey.ontology_seq'), 1, 'Sample', 'Birthday');

-- METADATA (id, survey_id, step_section_id, question_id, section_question_id, ontology_id) --
INSERT INTO survey.metadata(id, survey_id, step_section_id, question_id, section_question_id, ontology_id, value)
VALUES (NEXTVAL('survey.metadata_seq'), 1, 1, 3, NULL, 1, '');
INSERT INTO survey.metadata(id, survey_id, step_section_id, question_id, section_question_id, ontology_id, value)
VALUES (NEXTVAL('survey.metadata_seq'), 1, NULL, 6, NULL, 2, '');
INSERT INTO survey.metadata(id, survey_id, step_section_id, question_id, section_question_id, ontology_id, value)
VALUES (NEXTVAL('survey.metadata_seq'), 1, NULL, 7, NULL, 3, '');
INSERT INTO survey.metadata(id, survey_id, step_section_id, question_id, section_question_id, ontology_id, value)
VALUES (NEXTVAL('survey.metadata_seq'), 1, NULL, NULL, 14, 4, '');
INSERT INTO survey.metadata(id, survey_id, step_section_id, question_id, section_question_id, ontology_id, value)
VALUES (NEXTVAL('survey.metadata_seq'), 1, NULL, NULL, 19, 5, '');
INSERT INTO survey.metadata(id, survey_id, step_section_id, question_id, section_question_id, ontology_id, value)
VALUES (NEXTVAL('survey.metadata_seq'), 1, 4, NULL, NULL, 6, '');
INSERT INTO survey.metadata(id, survey_id, step_section_id, question_id, section_question_id, ontology_id, value)
VALUES (NEXTVAL('survey.metadata_seq'), 1, 5, NULL, NULL, 7, '');
INSERT INTO survey.metadata(id, survey_id, step_section_id, question_id, section_question_id, ontology_id, value)
VALUES (NEXTVAL('survey.metadata_seq'), 1, 6, NULL, NULL, 8, '');
INSERT INTO survey.metadata(id, survey_id, step_section_id, question_id, section_question_id, ontology_id, value)
VALUES (NEXTVAL('survey.metadata_seq'), 1, NULL, NULL, 5, 9, '');

INSERT INTO survey.respondents (id, survey_id, token, active, logins, created_dt, first_access_dt, finalized_dt)
VALUES (NEXTVAL('survey.respondents_seq'), 1, 'test', TRUE, 0, current_timestamp, null, null);
INSERT INTO survey.respondents (id, survey_id, token, active, logins, created_dt, first_access_dt, finalized_dt)
VALUES (NEXTVAL('survey.respondents_seq'), 1, 'inactive-token', FALSE, 0, current_timestamp, null, null);

INSERT INTO survey.sections(id, survey_id, display_order, name, dimension_name, description)
VALUES (0, 1, 0, '', '','');
