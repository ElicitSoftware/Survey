-- ***LICENSE_START***
-- Elicit FHHS
-- %%
-- Copyright (C) 2025 The Regents of the University of Michigan - Rogel Cancer Center
-- %%
-- PolyForm Noncommercial License 1.0.0
-- <https://polyformproject.org/licenses/noncommercial/1.0.0>
-- ***LICENSE_END***
---

-- ============================================================
-- Full-coverage test survey: Elicit Design and Administration
-- Conference Registration
--
-- Exercises every question type, all operators, all action types,
-- token patterns ({FNAME}, {S#}, {S1}, {Q#}),
-- repeated step, repeated section, and all conditional show paths.
--
-- ID assignment (all via NEXTVAL, insertion order):
--   surveys:          1
--   select_groups:    1-7
--   steps:            1-5
--   sections:         1-7
--   steps_sections:   1-7   (ss1–ss7; referenced by downstream_s_id)
--   questions:        1-34
--   sections_questions (sq): 1-34  (referenced by upstream/downstream_sq_id)
--   relationships:    1-16
-- ============================================================

SET TIMEZONE TO 'America/Detroit';

-- ============================================================
-- SURVEY
-- ============================================================
-- SURVEYS (id, display_order, name, title, description, initial_display_key, post_survey_url) --
INSERT INTO survey.surveys(id, display_order, name, title, description, initial_display_key, post_survey_url)
VALUES (NEXTVAL('survey.surveys_seq'), 1,
        'ElicitConferenceReg',
        'Elicit Design and Administration Conference Registration',
        'Full-coverage test survey exercising every question type, operator, action, token pattern, repeated step, repeated section, and all navigation paths.',
        '0001-0001-0000-0001-0000-0000-0000', NULL);

-- ============================================================
-- SELECT GROUPS  (survey_id = 1)
-- IDs 1-7 in insertion order
-- ============================================================
-- SELECT GROUPS (id, survey_id, name, description) --
INSERT INTO survey.select_groups(id, survey_id, name, description)
VALUES (NEXTVAL('survey.select_groups_seq'), 1, 'YesNo', 'Boolean yes/no choice');
INSERT INTO survey.select_groups(id, survey_id, name, description)
VALUES (NEXTVAL('survey.select_groups_seq'), 1, 'OrgType', 'Organization type for billing');
INSERT INTO survey.select_groups(id, survey_id, name, description)
VALUES (NEXTVAL('survey.select_groups_seq'), 1, 'Tracks', 'Conference track interests');
INSERT INTO survey.select_groups(id, survey_id, name, description)
VALUES (NEXTVAL('survey.select_groups_seq'), 1, 'PriorConferences', 'Prior conferences attended');
INSERT INTO survey.select_groups(id, survey_id, name, description)
VALUES (NEXTVAL('survey.select_groups_seq'), 1, 'DietaryNeeds', 'Dietary requirements for catering');
INSERT INTO survey.select_groups(id, survey_id, name, description)
VALUES (NEXTVAL('survey.select_groups_seq'), 1, 'SessionRole', 'Registrant role in the session');
INSERT INTO survey.select_groups(id, survey_id, name, description)
VALUES (NEXTVAL('survey.select_groups_seq'), 1, 'SessionFormat', 'Conference session format');

-- ============================================================
-- SELECT ITEMS  (survey_id = 1)
-- SELECT ITEMS (id, survey_id, group_id, display_text, display_order, coded_value) --
-- ============================================================
-- Group 1: YesNo --
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), 1, 1, 'Yes', '1', 'TRUE');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), 1, 1, 'No', '2', 'FALSE');

-- Group 2: OrgType --
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), 1, 2, 'Academic', '1', 'academic');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), 1, 2, 'Government', '2', 'government');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), 1, 2, 'Non-profit', '3', 'nonprofit');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), 1, 2, 'Commercial', '4', 'commercial');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), 1, 2, 'Other', '5', 'other');

-- Group 3: Tracks --
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), 1, 3, 'Survey Design', '1', 'design');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), 1, 3, 'Data Analysis', '2', 'analysis');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), 1, 3, 'Technology & Tools', '3', 'technology');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), 1, 3, 'Ethics & Policy', '4', 'ethics');

-- Group 4: PriorConferences --
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), 1, 4, 'Regional', '1', 'regional');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), 1, 4, 'National', '2', 'national');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), 1, 4, 'International', '3', 'international');

-- Group 5: DietaryNeeds --
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), 1, 5, 'No Restriction', '1', 'none');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), 1, 5, 'Vegetarian', '2', 'vegetarian');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), 1, 5, 'Vegan', '3', 'vegan');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), 1, 5, 'Gluten-Free', '4', 'glutenfree');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), 1, 5, 'Kosher', '5', 'kosher');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), 1, 5, 'Halal', '6', 'halal');

-- Group 6: SessionRole --
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), 1, 6, 'Attending', '1', 'attending');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), 1, 6, 'Presenting', '2', 'presenting');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), 1, 6, 'Moderating', '3', 'moderating');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), 1, 6, 'Other', '4', 'other');

-- Group 7: SessionFormat --
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), 1, 7, 'Workshop', '1', 'workshop');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), 1, 7, 'Panel', '2', 'panel');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), 1, 7, 'Keynote', '3', 'keynote');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), 1, 7, 'Poster Session', '4', 'poster');

-- ============================================================
-- STEPS  (survey_id = 1)
-- IDs 1-5 in insertion order
-- STEPS (id, survey_id, display_order, name, dimension_name, description) --
-- ============================================================
-- Step 1: Welcome --
INSERT INTO survey.steps(id, survey_id, display_order, name, dimension_name, description)
VALUES (NEXTVAL('survey.steps_seq'), 1, 1, 'Welcome', 'Welcome', 'Conference welcome and code-of-conduct consent');
-- Step 2: Account & Contact --
INSERT INTO survey.steps(id, survey_id, display_order, name, dimension_name, description)
VALUES (NEXTVAL('survey.steps_seq'), 1, 2, 'Account', 'Registrant', 'Registrant account and contact details');
-- Step 3: Attendance Details --
INSERT INTO survey.steps(id, survey_id, display_order, name, dimension_name, description)
VALUES (NEXTVAL('survey.steps_seq'), 1, 3, 'Attendance', 'Attendance', 'Conference attendance details and preferences');
-- Step 4: Session — repeated step (one instance per session attended) --
INSERT INTO survey.steps(id, survey_id, display_order, name, dimension_name, description)
VALUES (NEXTVAL('survey.steps_seq'), 1, 4, 'Session', 'Session', 'Session registration (repeated per session attended)');
-- Step 5: TimeSlot — repeated sections within each session step --
INSERT INTO survey.steps(id, survey_id, display_order, name, dimension_name, description)
VALUES (NEXTVAL('survey.steps_seq'), 1, 5, 'TimeSlot', 'TimeSlot', 'Time slot selection (repeated per available time slot)');

-- ============================================================
-- SECTIONS  (survey_id = 1)
-- IDs 1-7 in insertion order
-- SECTIONS (id, survey_id, display_order, name, dimension_name, description) --
-- ============================================================
-- Section 1: Welcome --
INSERT INTO survey.sections(id, survey_id, display_order, name, dimension_name, description)
VALUES (NEXTVAL('survey.sections_seq'), 1, 1, 'Welcome', 'Welcome', 'Introduction and consent');
-- Section 2: Account --
INSERT INTO survey.sections(id, survey_id, display_order, name, dimension_name, description)
VALUES (NEXTVAL('survey.sections_seq'), 1, 2, 'Account', 'Registrant', 'Account and contact information');
-- Section 3: OrgBilling — conditionally shown when Q12 (org paying) = TRUE --
INSERT INTO survey.sections(id, survey_id, display_order, name, dimension_name, description)
VALUES (NEXTVAL('survey.sections_seq'), 1, 3, 'Organization Billing', 'OrgBilling', 'Organization billing details');
-- Section 4: Attendance Details --
INSERT INTO survey.sections(id, survey_id, display_order, name, dimension_name, description)
VALUES (NEXTVAL('survey.sections_seq'), 1, 4, 'Attendance Details', 'Attendance', 'Session count and conference track preferences');
-- Section 5: Dietary & Arrival — conditionally shown when Q15 (session count) > 1 --
INSERT INTO survey.sections(id, survey_id, display_order, name, dimension_name, description)
VALUES (NEXTVAL('survey.sections_seq'), 1, 5, 'Dietary & Arrival', 'Dietary', 'Dietary requirements and arrival details for multi-session attendees');
-- Section 6: Session — repeated with Step 4; uses {FNAME}, {S#}, {S1} tokens --
INSERT INTO survey.sections(id, survey_id, display_order, name, dimension_name, description)
VALUES (NEXTVAL('survey.sections_seq'), 1, 6, '{FNAME''s|Your} Session {S#} - {S1}', 'Session', 'Details for one conference session');
-- Section 7: TimeSlot — repeated section within Step 5; uses {S#}, {S1} tokens --
INSERT INTO survey.sections(id, survey_id, display_order, name, dimension_name, description)
VALUES (NEXTVAL('survey.sections_seq'), 1, 7, 'Time Slot {S#} - {S1}', 'TimeSlot', 'One available time slot for this session');

-- ============================================================
-- STEPS_SECTIONS  (survey_id = 1)
-- IDs 1-7 in insertion order; downstream_s_id in relationships references these IDs.
--   ss1 = Step 1 / Section 1   (Welcome / Welcome)
--   ss2 = Step 2 / Section 2   (Account / Account)
--   ss3 = Step 2 / Section 3   (Account / OrgBilling — conditional)
--   ss4 = Step 3 / Section 4   (Attendance / AttendanceDetails)
--   ss5 = Step 3 / Section 5   (Attendance / Dietary — conditional)
--   ss6 = Step 4 / Section 6   (Session / Session — repeated step)
--   ss7 = Step 5 / Section 7   (TimeSlot / TimeSlot — repeated section)
-- STEPS_SECTIONS (id, survey_id, step_id, step_display_order, section_id, section_display_order, display_key) --
-- ============================================================
INSERT INTO survey.steps_sections(id, survey_id, step_id, step_display_order, section_id, section_display_order, display_key)
VALUES (NEXTVAL('survey.steps_sections_seq'), 1, 1, 1, 1, 1, '0001-0001-0000-0001-0000-0000-0000');
INSERT INTO survey.steps_sections(id, survey_id, step_id, step_display_order, section_id, section_display_order, display_key)
VALUES (NEXTVAL('survey.steps_sections_seq'), 1, 2, 2, 2, 2, '0001-0002-0000-0002-0000-0000-0000');
INSERT INTO survey.steps_sections(id, survey_id, step_id, step_display_order, section_id, section_display_order, display_key)
VALUES (NEXTVAL('survey.steps_sections_seq'), 1, 2, 2, 3, 3, '0001-0002-0000-0003-0000-0000-0000');
INSERT INTO survey.steps_sections(id, survey_id, step_id, step_display_order, section_id, section_display_order, display_key)
VALUES (NEXTVAL('survey.steps_sections_seq'), 1, 3, 3, 4, 4, '0001-0003-0000-0004-0000-0000-0000');
INSERT INTO survey.steps_sections(id, survey_id, step_id, step_display_order, section_id, section_display_order, display_key)
VALUES (NEXTVAL('survey.steps_sections_seq'), 1, 3, 3, 5, 5, '0001-0003-0000-0005-0000-0000-0000');
INSERT INTO survey.steps_sections(id, survey_id, step_id, step_display_order, section_id, section_display_order, display_key)
VALUES (NEXTVAL('survey.steps_sections_seq'), 1, 4, 4, 6, 6, '0001-0004-0000-0006-0000-0000-0000');
INSERT INTO survey.steps_sections(id, survey_id, step_id, step_display_order, section_id, section_display_order, display_key)
VALUES (NEXTVAL('survey.steps_sections_seq'), 1, 5, 5, 7, 7, '0001-0005-0000-0007-0000-0000-0000');

-- ============================================================
-- QUESTIONS  (survey_id = 1)
-- IDs 1-34 in insertion order
-- Columns: id, survey_id, type_id, text, short_text, tool_tip, variant,
--          required, min_value, max_value, validation_text, select_group_id,
--          mask, placeholder, default_value
--
-- type_id:  1=CHECKBOX  2=DATE_PICKER  3=COMBOBOX  4=HTML   5=INTEGER
--           6=DOUBLE    7=RADIO        8=TEXT       9=TEXTAREA
--          10=MULTI_SELECT  11=CHECKBOX_GROUP  12=DATETIME
--          13=EMAIL  14=PASSWORD
-- ============================================================

-- ---- Step 1 — Welcome (Section 1) ----

-- Q1: HTML — welcome block (type 4)
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), 1, 4,
        '<h1>Welcome to the Elicit Design and Administration Conference</h1><p>Complete this form to register. You will create a login, provide contact details, select the sessions you plan to attend, and choose your preferred time slot for each session.</p>',
        'Welcome', '', '', FALSE, NULL, NULL, '', NULL, NULL, NULL, NULL);

-- Q2: CHECKBOX — code of conduct consent (type 1); BOOLEAN SHOW → Q3
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), 1, 1,
        'I agree to the conference code of conduct and cancellation policy.',
        'Code of Conduct', '', '', TRUE, NULL, NULL, 'You must agree before continuing.', NULL, NULL, NULL, NULL);

-- Q3: HTML — consent confirmation (type 4); BOOLEAN SHOW target from Q2
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), 1, 4,
        '<p>Thank you. Your agreement has been recorded. Please continue to create your account.</p>',
        'Agreement Confirmed', '', '', FALSE, NULL, NULL, '', NULL, NULL, NULL, NULL);

-- ---- Step 2 — Account & Contact (Section 2) ----

-- Q4: TEXT — first name (type 8); FIELD_EXIST TEXT → {FNAME} token; min/max/validation/placeholder
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), 1, 8,
        'First name',
        'First name', '', '', TRUE, 2, 50, 'First name must be at least 2 characters', NULL, NULL, 'First name', NULL);

-- Q5: TEXT — last name (type 8); uses {FNAME} token in text
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), 1, 8,
        '{FNAME''s|Your} last name',
        'Last name', '', '', TRUE, 2, 50, 'Last name must be at least 2 characters', NULL, NULL, 'Last name', NULL);

-- Q6: EMAIL — work email (type 13); tool_tip and placeholder
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), 1, 13,
        'Work email address',
        'Email', 'This will be your login username', '', TRUE, NULL, NULL, '', NULL, NULL, 'you@organization.org', NULL);

-- Q7: PASSWORD — create password (type 14); tool_tip and validation_text
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), 1, 14,
        'Create a password',
        'Password', 'Minimum 8 characters', '', TRUE, NULL, NULL, 'Password is required', NULL, NULL, NULL, NULL);

-- Q8: TEXT — work phone (type 8); mask and placeholder
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), 1, 8,
        'Work phone number',
        'Phone', '', '', TRUE, NULL, NULL, 'A valid 10-digit phone number is required', NULL, '(999) 999-9999', '(555) 867-5309', NULL);

-- Q9: DATE_PICKER — date of birth (type 2); uses {FNAME} token in text
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), 1, 2,
        'What is {FNAME''s|your} date of birth?',
        'Date of birth', '', '', TRUE, NULL, NULL, '', NULL, NULL, NULL, NULL);

-- Q10: RADIO — attending from outside US (type 7); FIELD_EXIST SHOW → Q11; YesNo group
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), 1, 7,
        'Will you be attending from outside the United States?',
        'International', '', '', TRUE, NULL, NULL, '', 1, NULL, NULL, NULL);

-- Q11: TEXT — country of residence (type 8); FIELD_EXIST SHOW target from Q10; placeholder
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), 1, 8,
        'Country of residence',
        'Country', '', '', FALSE, NULL, NULL, '', NULL, NULL, 'e.g., Canada, Germany, Brazil', NULL);

-- Q12: RADIO — org paying registration (type 7); EQUAL 'TRUE' SHOW → Section 3; uses {FNAME} token; YesNo group
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), 1, 7,
        'Is {FNAME|your} registration being paid by an organization?',
        'Org billing', '', '', TRUE, NULL, NULL, '', 1, NULL, NULL, NULL);

-- ---- Step 2 Section 2 — Organization Billing (Section 3, conditional) ----

-- Q13: TEXT — organization name (type 8); EQUAL SHOW target
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), 1, 8,
        'Organization name',
        'Organization', '', '', TRUE, NULL, NULL, '', NULL, NULL, NULL, NULL);

-- Q14: COMBOBOX — organization type (type 3); OrgType group
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), 1, 3,
        'Organization type',
        'Org type', '', '', TRUE, NULL, NULL, '', 2, NULL, NULL, NULL);

-- ---- Step 3 — Attendance Details (Section 4) ----

-- Q15: INTEGER — session count (type 5); variant=vertical; REPEAT → Step 4; GREATER THAN '1' SHOW → Section 5
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), 1, 5,
        'How many conference sessions do you plan to attend?',
        'Session count', '', 'vertical', TRUE, 0, 20, 'Must be between 0 and 20', NULL, NULL, NULL, NULL);

-- Q16: CHECKBOX_GROUP — conference tracks (type 11); variant=vertical; Tracks group; CONTAINS 'technology' SHOW → Q17; CONTAINS 'ethics' SHOW → Q19
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), 1, 11,
        'Which conference tracks interest you?',
        'Tracks', '', 'vertical', FALSE, NULL, NULL, '', 3, NULL, NULL, NULL);

-- Q17: TEXT — software tools (type 8); CONTAINS 'technology' SHOW target; FIELD_EXIST SHOW → Q18 (nested chain level 2→3)
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), 1, 8,
        'List any survey software tools you currently use',
        'Software tools', '', '', FALSE, NULL, NULL, '', NULL, NULL, 'e.g., Qualtrics, REDCap, Elicit', NULL);

-- Q18: TEXT — primary platform (type 8); FIELD_EXIST SHOW target from Q17 (nested chain: Q16→Q17→Q18, level 3)
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), 1, 8,
        'Which of those tools is your primary platform?',
        'Primary tool', '', '', FALSE, NULL, NULL, '', NULL, NULL, 'e.g., Qualtrics', NULL);

-- Q19: TEXTAREA — ethical considerations (type 9); CONTAINS 'ethics' SHOW target; tool_tip and placeholder
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), 1, 9,
        'Describe any ethical considerations you''d like addressed at the conference',
        'Ethical considerations', 'Responses may be used to shape panel topics', '', FALSE, NULL, NULL, '', NULL, NULL, 'e.g., informed consent practices, data anonymization...', NULL);

-- Q20: MULTI_SELECT — prior conferences (type 10); PriorConferences group; CONTAINS 'international' SHOW → Q21
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), 1, 10,
        'Select any prior conferences you have attended',
        'Prior conferences', '', '', FALSE, NULL, NULL, '', 4, NULL, NULL, NULL);

-- Q21: TEXT — countries previously presented (type 8); CONTAINS 'international' SHOW target
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), 1, 8,
        'List the countries where you have previously presented',
        'Countries presented', '', '', FALSE, NULL, NULL, '', NULL, NULL, NULL, NULL);

-- Q22: DOUBLE — years of experience (type 6); min/max, placeholder, and default_value
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), 1, 6,
        'How many years of survey research experience do you have?',
        'Years experience', '', '', FALSE, 0, 50, 'Must be between 0 and 50', NULL, NULL, 'e.g., 7.5', '1.0');

-- ---- Step 3 Section 2 — Dietary & Arrival (Section 5, conditional: shown when Q15 >= 2) ----

-- Q23: COMBOBOX — dietary requirement (type 3); DietaryNeeds group; GREATER THAN SHOW target
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), 1, 3,
        'Primary dietary requirement for catering',
        'Dietary', '', '', TRUE, NULL, NULL, '', 5, NULL, NULL, NULL);

-- Q24: DATE_PICKER — arrival date (type 2)
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), 1, 2,
        'Arrival date',
        'Arrival date', '', '', FALSE, NULL, NULL, '', NULL, NULL, NULL, NULL);

-- Q25: DATETIME — preferred check-in date and time (type 12)
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), 1, 12,
        'Preferred check-in date and time',
        'Check-in', '', '', FALSE, NULL, NULL, '', NULL, NULL, NULL, NULL);

-- ---- Step 4 — Session (Section 6, repeated step) ----

-- Q26: TEXT — session title (type 8); required, min/max; populates {S1} via R15; {FNAME}'s section name references this via {S1}
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), 1, 8,
        'Session title or topic',
        'Session title', '', '', TRUE, 2, 200, 'Session title must be at least 2 characters', NULL, NULL, 'e.g., Survey Design Best Practices', NULL);

-- Q27: INTEGER — time slot count (type 5); GREATER_THAN '0' REPEAT → ss7 (TimeSlot section)
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), 1, 5,
        'How many time slots does this session offer?',
        'Time slot count', '', 'vertical', TRUE, 1, 5, 'Must be between 1 and 5', NULL, NULL, NULL, NULL);

-- Q28: RADIO — session role (type 7); SessionRole group; NOT_EQUAL 'attending' SHOW → Q29
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), 1, 7,
        'What is your role in this session?',
        'Session role', '', '', TRUE, NULL, NULL, '', 6, NULL, NULL, NULL);

-- Q29: TEXT — role description (type 8); NOT_EQUAL 'attending' SHOW target from Q28
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), 1, 8,
        'Please describe your involvement in this session',
        'Role description', '', '', FALSE, NULL, NULL, '', NULL, NULL, 'e.g., co-presenter, panel discussant...', NULL);

-- Q30: CHECKBOX — accessibility accommodations (type 1); BOOLEAN SHOW → Q31; tool_tip
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), 1, 1,
        'Do you require accessibility accommodations for this session?',
        'Accessibility', 'We will contact you to arrange accommodations', '', FALSE, NULL, NULL, '', NULL, NULL, NULL, NULL);

-- Q31: TEXTAREA — accommodation needs (type 9); BOOLEAN SHOW target from Q30; placeholder
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), 1, 9,
        'Describe your accommodation needs for this session',
        'Accommodation needs', '', '', FALSE, NULL, NULL, '', NULL, NULL, 'e.g., wheelchair access, sign language interpreter...', NULL);

-- ---- Step 5 — Time Slot (Section 7, repeated section within Session step) ----

-- Q32: DATE_PICKER — time slot date (type 2); {Q#} token in text; FIELD_EXIST SHOW 'S1' → ss7 name
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), 1, 2,
        'Date for time slot {Q#}',
        'Time slot date', '', '', TRUE, NULL, NULL, '', NULL, NULL, NULL, NULL);

-- Q33: COMBOBOX — session format for this time slot (type 3); SessionFormat group
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), 1, 3,
        'Session format for this time slot',
        'Format', '', '', TRUE, NULL, NULL, '', 7, NULL, NULL, NULL);

-- Q34: TEXT — room or venue (type 8); optional; placeholder
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), 1, 8,
        'Room or venue for this time slot',
        'Venue', '', '', FALSE, NULL, NULL, '', NULL, NULL, 'e.g., Main Hall, Room 204', NULL);

-- Q35: TEXT — state or province (type 8); shown when Q10 = FALSE (domestic respondents); same location ontology as Q11
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), 1, 8,
        'State or province of residence',
        'State', '', '', FALSE, NULL, NULL, '', NULL, NULL, 'e.g., California, Ontario', NULL);

-- ============================================================
-- SECTIONS_QUESTIONS  (survey_id = 1)
-- IDs 1-35 in insertion order (sq1–sq35); referenced by upstream/downstream_sq_id in relationships.
-- SECTIONS_QUESTIONS (id, survey_id, question_id, section_id, display_order) --
-- ============================================================
-- Section 1 — Welcome (sq 1-3)
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), 1, 1, 1, 1);  -- sq1:  Q1 HTML welcome
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), 1, 2, 1, 2);  -- sq2:  Q2 CHECKBOX consent
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), 1, 3, 1, 3);  -- sq3:  Q3 HTML confirmation

-- Section 2 — Account (sq 4-12)
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), 1, 4, 2, 1);  -- sq4:  Q4 TEXT first name
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), 1, 5, 2, 2);  -- sq5:  Q5 TEXT last name
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), 1, 6, 2, 3);  -- sq6:  Q6 EMAIL
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), 1, 7, 2, 4);  -- sq7:  Q7 PASSWORD
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), 1, 8, 2, 5);  -- sq8:  Q8 TEXT phone
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), 1, 9, 2, 6);  -- sq9:  Q9 DATE_PICKER dob
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), 1, 10, 2, 7); -- sq10: Q10 RADIO international
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), 1, 11, 2, 8); -- sq11: Q11 TEXT country
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), 1, 12, 2, 10); -- sq12: Q12 RADIO org billing (display_order bumped; sq35 takes 9)

-- Section 3 — OrgBilling (sq 13-14)
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), 1, 13, 3, 1); -- sq13: Q13 TEXT org name
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), 1, 14, 3, 2); -- sq14: Q14 COMBOBOX org type

-- Section 4 — Attendance Details (sq 15-22)
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), 1, 15, 4, 1); -- sq15: Q15 INTEGER session count
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), 1, 16, 4, 2); -- sq16: Q16 CHECKBOX_GROUP tracks
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), 1, 17, 4, 3); -- sq17: Q17 TEXT software tools
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), 1, 18, 4, 4); -- sq18: Q18 TEXT primary platform
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), 1, 19, 4, 5); -- sq19: Q19 TEXTAREA ethical
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), 1, 20, 4, 6); -- sq20: Q20 MULTI_SELECT conferences
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), 1, 21, 4, 7); -- sq21: Q21 TEXT countries presented
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), 1, 22, 4, 8); -- sq22: Q22 DOUBLE years experience

-- Section 5 — Dietary & Arrival (sq 23-25)
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), 1, 23, 5, 1); -- sq23: Q23 COMBOBOX dietary
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), 1, 24, 5, 2); -- sq24: Q24 DATE_PICKER arrival
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), 1, 25, 5, 3); -- sq25: Q25 DATETIME check-in

-- Section 6 — Session (sq 26-31)
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), 1, 26, 6, 1); -- sq26: Q26 TEXT session title
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), 1, 27, 6, 2); -- sq27: Q27 INTEGER time slot count
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), 1, 28, 6, 3); -- sq28: Q28 RADIO session role
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), 1, 29, 6, 4); -- sq29: Q29 TEXT role description
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), 1, 30, 6, 5); -- sq30: Q30 CHECKBOX accessibility
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), 1, 31, 6, 6); -- sq31: Q31 TEXTAREA accommodation

-- Section 7 — TimeSlot (sq 32-34)
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), 1, 32, 7, 1); -- sq32: Q32 DATE_PICKER time slot date {Q#}
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), 1, 33, 7, 2); -- sq33: Q33 COMBOBOX session format
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), 1, 34, 7, 3); -- sq34: Q34 TEXT room/venue
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), 1, 35, 2, 9); -- sq35: Q35 TEXT state/province (Section 2, display_order=9; EQUAL 'FALSE' SHOW target from Q10)

-- ============================================================
-- RELATIONSHIPS  (survey_id = 1)
-- 16 rows covering all 6 operators, all 3 action types, and all token types.
--
-- Columns: id, survey_id, upstream_step_id, upstream_sq_id,
--          downstream_step_id, downstream_sq_id, downstream_s_id,
--          operator_id, action_id, token, description,
--          reference_value, default_upstream_value
--
-- operator_id:     1=BOOLEAN  2=GREATER_THAN  3=EQUAL  4=NOT_EQUAL
--                  5=FIELD_EXIST  6=CONTAINS
-- action_id:       1=SHOW  2=REPEAT  3=TEXT
-- downstream_s_id: references steps_sections.id (ss1–ss7)
--   ss1=Step1/Sec1  ss2=Step2/Sec2  ss3=Step2/Sec3  ss4=Step3/Sec4
--   ss5=Step3/Sec5  ss6=Step4/Sec6  ss7=Step5/Sec7
--
-- Coverage map:
--   Operators:  BOOLEAN(R1,R14) GREATER_THAN(R10,R11,R12) EQUAL(R5)
--               NOT_EQUAL(R13) FIELD_EXIST(R2,R3,R4,R7,R15,R16)
--               CONTAINS(R6,R8,R9)
--   Actions:    SHOW(R1,R2,R5,R6,R7,R8,R9,R10,R13,R14,R15,R16)
--               REPEAT(R11,R12)  TEXT(R3,R4)
--   Tokens:     FNAME(R3,R4) S1(R15,R16)
--               {Q#} used in Q32 text (no relationship needed)
--   Section show:  R5(ss3 OrgBilling) R10(ss5 Dietary)
--   Repeated step: R11(Step4)
--   Repeated sec:  R12(ss7 TimeSlot)
--   Nested chain:  R6(Q16→Q17) R7(Q17→Q18)  [3-level: Q16→Q17→Q18]
-- ============================================================

-- R1: Q2(sq2) BOOLEAN SHOW → Q3(sq3)
--     Operator: BOOLEAN | Action: SHOW | Exercises: BOOLEAN op, SHOW on question
INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_sq_id, downstream_s_id, operator_id, action_id, token, description, reference_value, default_upstream_value)
VALUES (NEXTVAL('survey.relationships_seq'), 1, 1, 2, NULL, 3, NULL, 1, 1, NULL, 'Show consent confirmation HTML when code of conduct checked', '', '');

-- R2: Q10(sq10) EQUAL 'TRUE' SHOW → Q11(sq11)
--     Operator: EQUAL | Action: SHOW | Exercises: EQUAL op, show country only for international (TRUE) respondents
INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_sq_id, downstream_s_id, operator_id, action_id, token, description, reference_value, default_upstream_value)
VALUES (NEXTVAL('survey.relationships_seq'), 1, 2, 10, NULL, 11, NULL, 3, 1, NULL, 'Show country field when respondent is attending from outside the US', 'TRUE', '');

-- R3: Q4(sq4) FIELD_EXIST TEXT 'FNAME' → Account section (ss2)
--     Operator: FIELD_EXIST | Action: TEXT | Token: FNAME | Exercises: TEXT action in question text
INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_sq_id, downstream_s_id, operator_id, action_id, token, description, reference_value, default_upstream_value)
VALUES (NEXTVAL('survey.relationships_seq'), 1, 2, 4, NULL, NULL, 2, 5, 3, 'FNAME', 'Replace {FNAME} token in Account section questions', '', '');

-- R4: Q4(sq4) FIELD_EXIST TEXT 'FNAME' → Session section (ss6, Step 4)
--     Operator: FIELD_EXIST | Action: TEXT | Token: FNAME | Exercises: TEXT action in repeated step section name
INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_sq_id, downstream_s_id, operator_id, action_id, token, description, reference_value, default_upstream_value)
VALUES (NEXTVAL('survey.relationships_seq'), 1, 2, 4, 4, NULL, 6, 5, 3, 'FNAME', 'Replace {FNAME} token in Session section name', '', '');

-- R5: Q12(sq12) EQUAL 'TRUE' SHOW → OrgBilling section (ss3)
--     Operator: EQUAL | Action: SHOW | Exercises: EQUAL op, conditional section show
INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_sq_id, downstream_s_id, operator_id, action_id, token, description, reference_value, default_upstream_value)
VALUES (NEXTVAL('survey.relationships_seq'), 1, 2, 12, NULL, NULL, 3, 3, 1, NULL, 'Show Organization Billing section when registration is org-paid', 'TRUE', '');

-- R6: Q16(sq16) CONTAINS 'technology' SHOW → Q17(sq17)
--     Operator: CONTAINS | Action: SHOW | Exercises: CONTAINS on CHECKBOX_GROUP, start of nested chain
INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_sq_id, downstream_s_id, operator_id, action_id, token, description, reference_value, default_upstream_value)
VALUES (NEXTVAL('survey.relationships_seq'), 1, 3, 16, NULL, 17, NULL, 6, 1, NULL, 'Show software tools question when Technology track selected', 'technology', '');

-- R7: Q17(sq17) FIELD_EXIST SHOW → Q18(sq18)
--     Operator: FIELD_EXIST | Action: SHOW | Exercises: nested 3-level SHOW chain (Q16→Q17→Q18)
INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_sq_id, downstream_s_id, operator_id, action_id, token, description, reference_value, default_upstream_value)
VALUES (NEXTVAL('survey.relationships_seq'), 1, 3, 17, NULL, 18, NULL, 5, 1, NULL, 'Show primary platform question once software tools entered (nested chain level 3)', '', '');

-- R8: Q16(sq16) CONTAINS 'ethics' SHOW → Q19(sq19)
--     Operator: CONTAINS | Action: SHOW | Exercises: second CONTAINS on same upstream, SHOW TEXTAREA
INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_sq_id, downstream_s_id, operator_id, action_id, token, description, reference_value, default_upstream_value)
VALUES (NEXTVAL('survey.relationships_seq'), 1, 3, 16, NULL, 19, NULL, 6, 1, NULL, 'Show ethics textarea when Ethics & Policy track selected', 'ethics', '');

-- R9: Q20(sq20) CONTAINS 'international' SHOW → Q21(sq21)
--     Operator: CONTAINS | Action: SHOW | Exercises: CONTAINS on MULTI_SELECT
INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_sq_id, downstream_s_id, operator_id, action_id, token, description, reference_value, default_upstream_value)
VALUES (NEXTVAL('survey.relationships_seq'), 1, 3, 20, NULL, 21, NULL, 6, 1, NULL, 'Show countries presented when International conference selected', 'international', '');

-- R10: Q15(sq15) GREATER_THAN '1' SHOW → Dietary section (ss5)
--      Operator: GREATER_THAN (count > 1 = attending 2+ sessions) | Action: SHOW | Exercises: GREATER_THAN, conditional section show
INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_sq_id, downstream_s_id, operator_id, action_id, token, description, reference_value, default_upstream_value)
VALUES (NEXTVAL('survey.relationships_seq'), 1, 3, 15, NULL, NULL, 5, 2, 1, NULL, 'Show Dietary & Arrival section when attending 2 or more sessions', '1', '');

-- R11: Q15(sq15) GREATER_THAN '0' REPEAT → Step 4 (session step)
--      Operator: GREATER_THAN (count > 0 = 1+ sessions) | Action: REPEAT | Exercises: REPEAT on step
INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_sq_id, downstream_s_id, operator_id, action_id, token, description, reference_value, default_upstream_value)
VALUES (NEXTVAL('survey.relationships_seq'), 1, 3, 15, 4, NULL, NULL, 2, 2, NULL, 'Repeat Session step N times (once per session attended)', '0', '');

-- R12: Q27(sq27) GREATER_THAN '0' REPEAT → TimeSlot section (ss7) in Step 5
--      Operator: GREATER_THAN | Action: REPEAT | Exercises: REPEAT on section within repeated step
INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_sq_id, downstream_s_id, operator_id, action_id, token, description, reference_value, default_upstream_value)
VALUES (NEXTVAL('survey.relationships_seq'), 1, 4, 27, 5, NULL, 7, 2, 2, NULL, 'Repeat Time Slot section N times for this session', '0', '');

-- R13: Q28(sq28) NOT_EQUAL 'attending' SHOW → Q29(sq29)
--      Operator: NOT_EQUAL | Action: SHOW | Exercises: NOT_EQUAL op; show description for presenting/moderating/other roles
INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_sq_id, downstream_s_id, operator_id, action_id, token, description, reference_value, default_upstream_value)
VALUES (NEXTVAL('survey.relationships_seq'), 1, 4, 28, NULL, 29, NULL, 4, 1, NULL, 'Show role description for presenter, moderator, and other roles', 'attending', '');

-- R14: Q30(sq30) BOOLEAN SHOW → Q31(sq31)
--      Operator: BOOLEAN | Action: SHOW | Exercises: second BOOLEAN SHOW (in repeated step context)
INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_sq_id, downstream_s_id, operator_id, action_id, token, description, reference_value, default_upstream_value)
VALUES (NEXTVAL('survey.relationships_seq'), 1, 4, 30, NULL, 31, NULL, 1, 1, NULL, 'Show accommodation needs textarea when accessibility checkbox ticked', '', '');

-- R15: Q26(sq26) FIELD_EXIST SHOW 'S1' → Step 4 (populates {S1} in Session section name)
--      Operator: FIELD_EXIST | Action: SHOW | Token: S1 | Exercises: {S1} token population in repeated step name
INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_sq_id, downstream_s_id, operator_id, action_id, token, description, reference_value, default_upstream_value)
VALUES (NEXTVAL('survey.relationships_seq'), 1, 4, 26, 4, NULL, NULL, 5, 1, 'S1', 'Populate {S1} in Session section name with session title', '', '');

-- R16: Q32(sq32) FIELD_EXIST SHOW 'S1' → Step 5 (populates {S1} in TimeSlot section name)
--      Operator: FIELD_EXIST | Action: SHOW | Token: S1 | Exercises: {S1} token in repeated section name
INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_sq_id, downstream_s_id, operator_id, action_id, token, description, reference_value, default_upstream_value)
VALUES (NEXTVAL('survey.relationships_seq'), 1, 5, 32, 5, NULL, NULL, 5, 1, 'S1', 'Populate {S1} in Time Slot section name with time slot date', '', '');

-- R17: Q10(sq10) EQUAL 'FALSE' SHOW → Q35(sq35)
--      Operator: EQUAL | Action: SHOW | Exercises: second EQUAL on same upstream (paired with R2); domestic respondents see state/province field
INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_sq_id, downstream_s_id, operator_id, action_id, token, description, reference_value, default_upstream_value)
VALUES (NEXTVAL('survey.relationships_seq'), 1, 2, 10, NULL, 35, NULL, 3, 1, NULL, 'Show state/province field when respondent is attending from within the US', 'FALSE', '');

-- ============================================================
-- DIMENSIONS
-- 7 named reporting dimensions; each causes a <tag>_key FK column in fact_sections.
-- No survey_id column — dimensions are global across surveys.
-- ============================================================
INSERT INTO survey.dimensions(id, name) VALUES (NEXTVAL('survey.dimensions_seq'), 'Consent');      -- dim 1
INSERT INTO survey.dimensions(id, name) VALUES (NEXTVAL('survey.dimensions_seq'), 'Geography');    -- dim 2
INSERT INTO survey.dimensions(id, name) VALUES (NEXTVAL('survey.dimensions_seq'), 'Billing');      -- dim 3
INSERT INTO survey.dimensions(id, name) VALUES (NEXTVAL('survey.dimensions_seq'), 'Organization'); -- dim 4
INSERT INTO survey.dimensions(id, name) VALUES (NEXTVAL('survey.dimensions_seq'), 'Diet');         -- dim 5
INSERT INTO survey.dimensions(id, name) VALUES (NEXTVAL('survey.dimensions_seq'), 'Role');         -- dim 6
INSERT INTO survey.dimensions(id, name) VALUES (NEXTVAL('survey.dimensions_seq'), 'Format');       -- dim 7

-- ============================================================
-- ONTOLOGY  (survey_id = 1)
-- 15 rows: 7 dimensioned (add <tag>_key FK column to fact_sections),
--          2 core standalone (multi-value questions; no FK column),
--          6 extended standalone (free-text / optional enrichment).
-- Columns: id, survey_id, name, tag, dimension
-- dimension IDs 1-7 correspond to the dimensions inserts above (seq starts at 1).
-- ============================================================

-- Core dimensioned — dimension FK present; ETL adds <tag>_key column to fact_sections
INSERT INTO survey.ontology(id, survey_id, name, tag, dimension)
VALUES (NEXTVAL('survey.ontology_seq'), 1, 'Code of Conduct Consent',  'consent',          1); -- ont 1 → Consent
INSERT INTO survey.ontology(id, survey_id, name, tag, dimension)
VALUES (NEXTVAL('survey.ontology_seq'), 1, 'International Attendee',   'international',    2); -- ont 2 → Geography
INSERT INTO survey.ontology(id, survey_id, name, tag, dimension)
VALUES (NEXTVAL('survey.ontology_seq'), 1, 'Organization Billing',     'org_billing',      3); -- ont 3 → Billing
INSERT INTO survey.ontology(id, survey_id, name, tag, dimension)
VALUES (NEXTVAL('survey.ontology_seq'), 1, 'Organization Type',        'org_type',         4); -- ont 4 → Organization
INSERT INTO survey.ontology(id, survey_id, name, tag, dimension)
VALUES (NEXTVAL('survey.ontology_seq'), 1, 'Dietary Requirement',      'dietary',          5); -- ont 5 → Diet
INSERT INTO survey.ontology(id, survey_id, name, tag, dimension)
VALUES (NEXTVAL('survey.ontology_seq'), 1, 'Session Role',             'session_role',     6); -- ont 6 → Role
INSERT INTO survey.ontology(id, survey_id, name, tag, dimension)
VALUES (NEXTVAL('survey.ontology_seq'), 1, 'Session Format',           'session_format',   7); -- ont 7 → Format

-- Core standalone — multi-value questions; standalone dim_<tag> table, no FK column in fact_sections
INSERT INTO survey.ontology(id, survey_id, name, tag, dimension)
VALUES (NEXTVAL('survey.ontology_seq'), 1, 'Conference Track',         'track',            NULL); -- ont 8
INSERT INTO survey.ontology(id, survey_id, name, tag, dimension)
VALUES (NEXTVAL('survey.ontology_seq'), 1, 'Prior Conference Type',    'prior_conference', NULL); -- ont 9

-- Extended standalone — free-text and optional enrichment; standalone dim_<tag> tables
INSERT INTO survey.ontology(id, survey_id, name, tag, dimension)
VALUES (NEXTVAL('survey.ontology_seq'), 1, 'Location',                 'location',         NULL); -- ont 10: country (sq11) and state/province (sq35) as peer dim_location rows
INSERT INTO survey.ontology(id, survey_id, name, tag, dimension)
VALUES (NEXTVAL('survey.ontology_seq'), 1, 'Session Title',            'session_title',    NULL); -- ont 11
INSERT INTO survey.ontology(id, survey_id, name, tag, dimension)
VALUES (NEXTVAL('survey.ontology_seq'), 1, 'Years of Experience',      'experience',       NULL); -- ont 12
INSERT INTO survey.ontology(id, survey_id, name, tag, dimension)
VALUES (NEXTVAL('survey.ontology_seq'), 1, 'Accessibility Required',   'accessibility',    NULL); -- ont 13
INSERT INTO survey.ontology(id, survey_id, name, tag, dimension)
VALUES (NEXTVAL('survey.ontology_seq'), 1, 'Primary Survey Platform',  'primary_platform', NULL); -- ont 14
INSERT INTO survey.ontology(id, survey_id, name, tag, dimension)
VALUES (NEXTVAL('survey.ontology_seq'), 1, 'Time Slot Venue',          'venue',            NULL); -- ont 15

-- ============================================================
-- METADATA  (survey_id = 1)
-- 16 rows — one per (section_question, ontology) binding.
-- value='' → ETL uses respondent's raw answer.
-- sq11 and sq35 both bind to ont 10 (location); country and state/province are peer rows in dim_location.
-- section_question_id values 1-35 match sections_questions insertion order (NEXTVAL seq 1-35).
-- ontology_id values 1-15 match ontology insertion order above.
-- Columns: id, survey_id, step_section_id, question_id, section_question_id, ontology_id, value
-- ============================================================
INSERT INTO survey.metadata(id, survey_id, step_section_id, question_id, section_question_id, ontology_id, value)
VALUES (NEXTVAL('survey.metadata_seq'), 1, NULL, NULL,  2,  1, ''); -- sq2  Q2 consent              → consent (#1)
INSERT INTO survey.metadata(id, survey_id, step_section_id, question_id, section_question_id, ontology_id, value)
VALUES (NEXTVAL('survey.metadata_seq'), 1, NULL, NULL, 10,  2, ''); -- sq10 Q10 international       → international (#2)
INSERT INTO survey.metadata(id, survey_id, step_section_id, question_id, section_question_id, ontology_id, value)
VALUES (NEXTVAL('survey.metadata_seq'), 1, NULL, NULL, 11, 10, ''); -- sq11 Q11 country             → location (#10)
INSERT INTO survey.metadata(id, survey_id, step_section_id, question_id, section_question_id, ontology_id, value)
VALUES (NEXTVAL('survey.metadata_seq'), 1, NULL, NULL, 12,  3, ''); -- sq12 Q12 org billing         → org_billing (#3)
INSERT INTO survey.metadata(id, survey_id, step_section_id, question_id, section_question_id, ontology_id, value)
VALUES (NEXTVAL('survey.metadata_seq'), 1, NULL, NULL, 14,  4, ''); -- sq14 Q14 org type            → org_type (#4)
INSERT INTO survey.metadata(id, survey_id, step_section_id, question_id, section_question_id, ontology_id, value)
VALUES (NEXTVAL('survey.metadata_seq'), 1, NULL, NULL, 16,  8, ''); -- sq16 Q16 tracks              → track (#8)
INSERT INTO survey.metadata(id, survey_id, step_section_id, question_id, section_question_id, ontology_id, value)
VALUES (NEXTVAL('survey.metadata_seq'), 1, NULL, NULL, 18, 14, ''); -- sq18 Q18 primary platform    → primary_platform (#14)
INSERT INTO survey.metadata(id, survey_id, step_section_id, question_id, section_question_id, ontology_id, value)
VALUES (NEXTVAL('survey.metadata_seq'), 1, NULL, NULL, 20,  9, ''); -- sq20 Q20 prior conferences   → prior_conference (#9)
INSERT INTO survey.metadata(id, survey_id, step_section_id, question_id, section_question_id, ontology_id, value)
VALUES (NEXTVAL('survey.metadata_seq'), 1, NULL, NULL, 22, 12, ''); -- sq22 Q22 years experience    → experience (#12)
INSERT INTO survey.metadata(id, survey_id, step_section_id, question_id, section_question_id, ontology_id, value)
VALUES (NEXTVAL('survey.metadata_seq'), 1, NULL, NULL, 23,  5, ''); -- sq23 Q23 dietary requirement → dietary (#5)
INSERT INTO survey.metadata(id, survey_id, step_section_id, question_id, section_question_id, ontology_id, value)
VALUES (NEXTVAL('survey.metadata_seq'), 1, NULL, NULL, 26, 11, ''); -- sq26 Q26 session title       → session_title (#11)
INSERT INTO survey.metadata(id, survey_id, step_section_id, question_id, section_question_id, ontology_id, value)
VALUES (NEXTVAL('survey.metadata_seq'), 1, NULL, NULL, 28,  6, ''); -- sq28 Q28 session role        → session_role (#6)
INSERT INTO survey.metadata(id, survey_id, step_section_id, question_id, section_question_id, ontology_id, value)
VALUES (NEXTVAL('survey.metadata_seq'), 1, NULL, NULL, 30, 13, ''); -- sq30 Q30 accessibility       → accessibility (#13)
INSERT INTO survey.metadata(id, survey_id, step_section_id, question_id, section_question_id, ontology_id, value)
VALUES (NEXTVAL('survey.metadata_seq'), 1, NULL, NULL, 33,  7, ''); -- sq33 Q33 session format      → session_format (#7)
INSERT INTO survey.metadata(id, survey_id, step_section_id, question_id, section_question_id, ontology_id, value)
VALUES (NEXTVAL('survey.metadata_seq'), 1, NULL, NULL, 34, 15, ''); -- sq34 Q34 venue               → venue (#15)
INSERT INTO survey.metadata(id, survey_id, step_section_id, question_id, section_question_id, ontology_id, value)
VALUES (NEXTVAL('survey.metadata_seq'), 1, NULL, NULL, 35, 10, ''); -- sq35 Q35 state/province      → location (#10)

-- ============================================================
-- RESPONDENTS
-- ============================================================
INSERT INTO survey.respondents (id, survey_id, token, active, logins, created_dt, first_access_dt, finalized_dt)
VALUES (NEXTVAL('survey.respondents_seq'), 1, 'test', TRUE, 0, current_timestamp, NULL, NULL);
INSERT INTO survey.respondents (id, survey_id, token, active, logins, created_dt, first_access_dt, finalized_dt)
VALUES (NEXTVAL('survey.respondents_seq'), 1, 'inactive-token', FALSE, 0, current_timestamp, NULL, NULL);

-- Sentinel empty section required by engine edge-case handling --
INSERT INTO survey.sections(id, survey_id, display_order, name, dimension_name, description)
VALUES (0, 1, 0, '', '', '');
