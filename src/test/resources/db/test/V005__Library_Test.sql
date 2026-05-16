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
-- Full-coverage test survey: Public Library Card Registration
-- and Media Checkout Request
--
-- Exercises every question type, all 6 operators, all 3 action
-- types, token patterns ({S#}, {Q#}, {EMAIL}, {PHONE}),
-- repeated step (checkout request), repeated section (renewal
-- within checkout), 3-level nested conditional chain, and
-- all conditional-show paths.
--
-- Survey ID: 2 (survey 1 = conference registration)
-- All IDs are assigned via NEXTVAL after the sequences already
-- consumed by earlier migration scripts.
--
-- ID assignment reference (insertion order):
--   surveys:            2
--   select_groups:      8–14
--   steps:              6–9
--   sections:           8–14   (section 0 already exists)
--   steps_sections:     8–14   (ss8–ss14)
--   questions:          36–70  (Q36–Q69, Q48b)
--   sections_questions: 36–70  (sq36–sq69, sq48b)
--   relationships:      18–31  (R18–R31 + R22b)
--   dimensions:         8–11
--   ontology:           16–25
--   metadata:           17–26
--   respondents:        3–4
--   select_items:       29–57
--
-- Engine coverage:
--   Question types (14/14):  CHECKBOX · DATE_PICKER · COMBOBOX · HTML ·
--                            INTEGER · DOUBLE · RADIO · TEXT · TEXTAREA ·
--                            MULTI_SELECT · CHECKBOX_GROUP · DATETIME ·
--                            EMAIL · PASSWORD
--   Operators (6/6):  BOOLEAN · GREATER_THAN · EQUAL · NOT_EQUAL ·
--                     FIELD_EXIST · CONTAINS
--   Actions (3/3):    SHOW · REPEAT · TEXT
--   Tokens:           {S#} (section names)  {Q#} (Q67 text)
--                     {EMAIL} (R_em)  {PHONE} (R_phone)
--   Repeated section: R23 — Q49 GREATER_THAN '0' REPEAT ss_checkout
--                     R29 — Q62 GREATER_THAN '0' REPEAT ss_renewal
--   Nested chain:     Q50→Q51→Q52 (R25,R26 — L1 CONTAINS, L2 FIELD_EXIST)
--   Cond. section:    ss10 DigitalAccess (R21 EQUAL 'TRUE')
--                     ss12 MediaPreferences (R24 CONTAINS 'dvd')
-- ============================================================

SET TIMEZONE TO 'America/Detroit';

DO $$
DECLARE
  -- Survey
  v_survey_id        bigint;

  -- Select groups
  v_sg_yesno         bigint;
  v_sg_mediatype     bigint;
  v_sg_literarygenre bigint;
  v_sg_pickupbranch  bigint;
  v_sg_visitfreq     bigint;
  v_sg_itemtype      bigint;
  v_sg_ebookformat   bigint;

  -- Steps
  v_step_welcome     bigint;
  v_step_patron      bigint;
  v_step_prefs       bigint;
  v_step_checkout    bigint;
  v_step_renewal     bigint;

  -- Sections
  v_sect_welcome     bigint;
  v_sect_patron      bigint;
  v_sect_digital     bigint;
  v_sect_collprefs   bigint;
  v_sect_mediaprefs  bigint;
  v_sect_checkout    bigint;
  v_sect_renewal     bigint;

  -- Steps_sections
  v_ss_welcome       bigint;
  v_ss_patron        bigint;
  v_ss_digital       bigint;
  v_ss_collprefs     bigint;
  v_ss_mediaprefs    bigint;
  v_ss_checkout      bigint;
  v_ss_renewal       bigint;

  -- Questions (Q36–Q69)
  v_q36  bigint;  v_q37  bigint;  v_q38  bigint;  v_q39  bigint;  v_q40  bigint;
  v_q41  bigint;  v_q42  bigint;  v_q43  bigint;  v_q44  bigint;  v_q45  bigint;
  v_q46  bigint;  v_q47  bigint;  v_q48  bigint;  v_q48b bigint;  v_q49  bigint;  v_q50  bigint;
  v_q51  bigint;  v_q52  bigint;  v_q53  bigint;  v_q54  bigint;  v_q55  bigint;
  v_q56  bigint;  v_q57  bigint;  v_q58  bigint;  v_q59  bigint;  v_q60  bigint;
  v_q61  bigint;  v_q62  bigint;  v_q63  bigint;  v_q64  bigint;  v_q65  bigint;
  v_q66  bigint;  v_q67  bigint;  v_q68  bigint;  v_q69  bigint;

  -- Sections_questions (SQ36–SQ69)
  v_sq36 bigint;  v_sq37 bigint;  v_sq38 bigint;  v_sq39 bigint;  v_sq40 bigint;
  v_sq41 bigint;  v_sq42 bigint;  v_sq43 bigint;  v_sq44 bigint;  v_sq45 bigint;
  v_sq46 bigint;  v_sq47 bigint;  v_sq48 bigint;  v_sq48b bigint; v_sq49 bigint;  v_sq50 bigint;
  v_sq51 bigint;  v_sq52 bigint;  v_sq53 bigint;  v_sq54 bigint;  v_sq55 bigint;
  v_sq56 bigint;  v_sq57 bigint;  v_sq58 bigint;  v_sq59 bigint;  v_sq60 bigint;
  v_sq61 bigint;  v_sq62 bigint;  v_sq63 bigint;  v_sq64 bigint;  v_sq65 bigint;
  v_sq66 bigint;  v_sq67 bigint;  v_sq68 bigint;  v_sq69 bigint;

  -- Dimensions
  v_dim_patron       bigint;
  v_dim_collection   bigint;
  v_dim_hold         bigint;
  v_dim_branch       bigint;

  -- Ontology (ont16–ont26)
  v_ont16 bigint;  v_ont17 bigint;  v_ont18 bigint;  v_ont19 bigint;  v_ont20 bigint;
  v_ont21 bigint;  v_ont22 bigint;  v_ont23 bigint;  v_ont24 bigint;  v_ont25 bigint;
  v_ont26 bigint;

BEGIN

-- ============================================================
-- SURVEY
-- ============================================================
-- SURVEYS (id, display_order, name, title, description, initial_display_key, post_survey_url) --
INSERT INTO survey.surveys(id, display_order, name, title, description, initial_display_key, post_survey_url)
VALUES (NEXTVAL('survey.surveys_seq'), 2,
        'LibraryCardReg',
        'Public Library Card Registration & Checkout Request',
        'Register for a library card, set collection preferences, and submit a media checkout request. Exercises every Elicit engine feature including repeated checkout steps, renewal sub-sections, and all conditional logic paths.',
        '0000-0001-0000-0001-0000-0000-0000', NULL)
RETURNING id INTO v_survey_id;

-- Build the initial display key using the actual generated survey ID
UPDATE survey.surveys
SET initial_display_key = LPAD(v_survey_id::text, 4, '0') || '-0001-0000-0001-0000-0000-0000'
WHERE id = v_survey_id;

-- ============================================================
-- SELECT GROUPS
-- SELECT GROUPS (id, survey_id, name, description, data_type) --
-- ============================================================
INSERT INTO survey.select_groups(id, survey_id, name, description, data_type)
VALUES (NEXTVAL('survey.select_groups_seq'), v_survey_id, 'YesNo',          'Boolean yes/no choice',                'Text')
RETURNING id INTO v_sg_yesno;

INSERT INTO survey.select_groups(id, survey_id, name, description, data_type)
VALUES (NEXTVAL('survey.select_groups_seq'), v_survey_id, 'MediaType',      'Library media categories',             'Text')
RETURNING id INTO v_sg_mediatype;

INSERT INTO survey.select_groups(id, survey_id, name, description, data_type)
VALUES (NEXTVAL('survey.select_groups_seq'), v_survey_id, 'LiteraryGenre',  'Reading genre preferences',            'Text')
RETURNING id INTO v_sg_literarygenre;

INSERT INTO survey.select_groups(id, survey_id, name, description, data_type)
VALUES (NEXTVAL('survey.select_groups_seq'), v_survey_id, 'PickupBranch',   'Library branch locations',             'Text')
RETURNING id INTO v_sg_pickupbranch;

INSERT INTO survey.select_groups(id, survey_id, name, description, data_type)
VALUES (NEXTVAL('survey.select_groups_seq'), v_survey_id, 'VisitFrequency', 'How often the patron visits',          'Text')
RETURNING id INTO v_sg_visitfreq;

INSERT INTO survey.select_groups(id, survey_id, name, description, data_type)
VALUES (NEXTVAL('survey.select_groups_seq'), v_survey_id, 'ItemType',       'Type of item being checked out',       'Text')
RETURNING id INTO v_sg_itemtype;

INSERT INTO survey.select_groups(id, survey_id, name, description, data_type)
VALUES (NEXTVAL('survey.select_groups_seq'), v_survey_id, 'EbookFormat',    'Preferred digital reading format',     'Text')
RETURNING id INTO v_sg_ebookformat;

-- ============================================================
-- STEPS
-- STEPS (id, survey_id, display_order, name, dimension_name, description) --
-- ============================================================
-- Step: Welcome --
INSERT INTO survey.steps(id, survey_id, display_order, name, dimension_name, description)
VALUES (NEXTVAL('survey.steps_seq'), v_survey_id, 1, 'Welcome', 'Welcome', 'Library welcome and terms of use consent')
RETURNING id INTO v_step_welcome;

-- Step: Patron --
INSERT INTO survey.steps(id, survey_id, display_order, name, dimension_name, description)
VALUES (NEXTVAL('survey.steps_seq'), v_survey_id, 2, 'Patron', 'Patron', 'Patron account and contact information')
RETURNING id INTO v_step_patron;

-- Step: Preferences --
INSERT INTO survey.steps(id, survey_id, display_order, name, dimension_name, description)
VALUES (NEXTVAL('survey.steps_seq'), v_survey_id, 3, 'Preferences', 'Preferences', 'Collection and visit preferences')
RETURNING id INTO v_step_prefs;

-- Step: CheckoutRequest — one step; checkout section and renewal section both repeat within it --
INSERT INTO survey.steps(id, survey_id, display_order, name, dimension_name, description)
VALUES (NEXTVAL('survey.steps_seq'), v_survey_id, 4, 'CheckoutRequest', 'Checkout', 'Checkout request items and renewal scheduling')
RETURNING id INTO v_step_checkout;

-- Step: Renewal removed — renewal section now lives inside the Checkout step.

-- ============================================================
-- SECTIONS
-- SECTIONS (id, survey_id, display_order, name, dimension_name, description) --
-- ============================================================
-- Section: Welcome --
INSERT INTO survey.sections(id, survey_id, display_order, name, dimension_name, description)
VALUES (NEXTVAL('survey.sections_seq'), v_survey_id, 1, 'Welcome', 'Welcome', 'Welcome and library terms of use')
RETURNING id INTO v_sect_welcome;

-- Section: Patron Information --
INSERT INTO survey.sections(id, survey_id, display_order, name, dimension_name, description)
VALUES (NEXTVAL('survey.sections_seq'), v_survey_id, 2, 'Patron Information', 'Patron', 'Account and contact details')
RETURNING id INTO v_sect_patron;

-- Section: Digital Access — conditionally shown when Q45 (wants digital) = TRUE --
INSERT INTO survey.sections(id, survey_id, display_order, name, dimension_name, description)
VALUES (NEXTVAL('survey.sections_seq'), v_survey_id, 3, 'Digital Access', 'DigitalAccess', 'Digital library card and notification preferences')
RETURNING id INTO v_sect_digital;

-- Section: Collection Preferences --
INSERT INTO survey.sections(id, survey_id, display_order, name, dimension_name, description)
VALUES (NEXTVAL('survey.sections_seq'), v_survey_id, 4, 'Collection Preferences', 'Preferences', 'Media interests and visit habits')
RETURNING id INTO v_sect_collprefs;

-- Section: Media Preferences — conditionally shown when Q50 (media types) CONTAINS 'dvd' --
INSERT INTO survey.sections(id, survey_id, display_order, name, dimension_name, description)
VALUES (NEXTVAL('survey.sections_seq'), v_survey_id, 5, 'Media Preferences', 'Media', 'DVD and media availability preferences')
RETURNING id INTO v_sect_mediaprefs;

-- Section: Checkout Request — repeated within Step: CheckoutRequest; {S#} token in name --
-- Note: {S1} (item title) omitted — TEXT relationship from inside the section causes an infinite loop in the engine.
INSERT INTO survey.sections(id, survey_id, display_order, name, dimension_name, description)
VALUES (NEXTVAL('survey.sections_seq'), v_survey_id, 6, 'Checkout {S#}', 'Checkout', 'Details for one checkout item')
RETURNING id INTO v_sect_checkout;

-- Section: Renewal — repeated section within Step: CheckoutRequest; {S#} token in name --
-- Note: {S1} (pickup date) omitted — TEXT relationship from inside the section causes an infinite loop in the engine.
INSERT INTO survey.sections(id, survey_id, display_order, name, dimension_name, description)
VALUES (NEXTVAL('survey.sections_seq'), v_survey_id, 7, 'Renewal {S#}', 'Renewal', 'Renewal scheduling for one renewal period')
RETURNING id INTO v_sect_renewal;

-- ============================================================
-- STEPS_SECTIONS
--   ss_welcome          = Step: Welcome    / Section: Welcome
--   ss_patron           = Step: Patron     / Section: Patron Information
--   ss_digital          = Step: Patron     / Section: Digital Access     (conditional EQUAL 'TRUE')
--   ss_collprefs        = Step: Prefs      / Section: Collection Prefs
--   ss_mediaprefs       = Step: Prefs      / Section: Media Preferences  (conditional CONTAINS 'dvd')
--   ss_checkout         = Step: Checkout   / Section: Checkout Request      (repeated section)
--   ss_renewal          = Step: Checkout   / Section: Renewal               (repeated section)
-- STEPS_SECTIONS (id, survey_id, step_id, step_display_order, section_id, section_display_order, display_key) --
-- ============================================================
INSERT INTO survey.steps_sections(id, survey_id, step_id, step_display_order, section_id, section_display_order, display_key)
VALUES (NEXTVAL('survey.steps_sections_seq'), v_survey_id, v_step_welcome,  1, v_sect_welcome,    1,
        LPAD(v_survey_id::text, 4, '0') || '-0001-0000-0001-0000-0000-0000')
RETURNING id INTO v_ss_welcome;

INSERT INTO survey.steps_sections(id, survey_id, step_id, step_display_order, section_id, section_display_order, display_key)
VALUES (NEXTVAL('survey.steps_sections_seq'), v_survey_id, v_step_patron,   2, v_sect_patron,     2,
        LPAD(v_survey_id::text, 4, '0') || '-0002-0000-0002-0000-0000-0000')
RETURNING id INTO v_ss_patron;

INSERT INTO survey.steps_sections(id, survey_id, step_id, step_display_order, section_id, section_display_order, display_key)
VALUES (NEXTVAL('survey.steps_sections_seq'), v_survey_id, v_step_patron,   2, v_sect_digital,    3,
        LPAD(v_survey_id::text, 4, '0') || '-0002-0000-0003-0000-0000-0000')
RETURNING id INTO v_ss_digital;

INSERT INTO survey.steps_sections(id, survey_id, step_id, step_display_order, section_id, section_display_order, display_key)
VALUES (NEXTVAL('survey.steps_sections_seq'), v_survey_id, v_step_prefs,    3, v_sect_collprefs,  4,
        LPAD(v_survey_id::text, 4, '0') || '-0003-0000-0004-0000-0000-0000')
RETURNING id INTO v_ss_collprefs;

INSERT INTO survey.steps_sections(id, survey_id, step_id, step_display_order, section_id, section_display_order, display_key)
VALUES (NEXTVAL('survey.steps_sections_seq'), v_survey_id, v_step_prefs,    3, v_sect_mediaprefs, 5,
        LPAD(v_survey_id::text, 4, '0') || '-0003-0000-0005-0000-0000-0000')
RETURNING id INTO v_ss_mediaprefs;

INSERT INTO survey.steps_sections(id, survey_id, step_id, step_display_order, section_id, section_display_order, display_key)
VALUES (NEXTVAL('survey.steps_sections_seq'), v_survey_id, v_step_checkout, 4, v_sect_checkout,   6,
        LPAD(v_survey_id::text, 4, '0') || '-0004-0000-0006-0000-0000-0000')
RETURNING id INTO v_ss_checkout;

INSERT INTO survey.steps_sections(id, survey_id, step_id, step_display_order, section_id, section_display_order, display_key)
VALUES (NEXTVAL('survey.steps_sections_seq'), v_survey_id, v_step_checkout, 4, v_sect_renewal,    7,
        LPAD(v_survey_id::text, 4, '0') || '-0004-0000-0007-0000-0000-0000')
RETURNING id INTO v_ss_renewal;

-- ============================================================
-- QUESTIONS
-- Columns: id, survey_id, type_id, text, short_text, tool_tip, variant,
--          required, min_value, max_value, validation_text, select_group_id,
--          mask, placeholder, default_value
--
-- type_id:  1=CHECKBOX  2=DATE_PICKER  3=COMBOBOX  4=HTML   5=INTEGER
--           6=DOUBLE    7=RADIO        8=TEXT       9=TEXTAREA
--          10=MULTI_SELECT  11=CHECKBOX_GROUP  12=DATETIME
--          13=EMAIL  14=PASSWORD
-- ============================================================

-- ---- Step: Welcome (Section: Welcome) ----

-- Q36: HTML — welcome block (type 4)
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), v_survey_id, 4,
        '<h1>Welcome to the Public Library</h1><p>Complete this form to register for a library card. You will create a login, set your collection preferences, and submit a checkout request for books, DVDs, and audio books.</p>',
        'Welcome', '', '', FALSE, NULL, NULL, '', NULL, NULL, NULL, NULL)
RETURNING id INTO v_q36;

-- Q37: CHECKBOX — agree to library terms (type 1); BOOLEAN SHOW → Q38
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), v_survey_id, 1,
        'I agree to the library terms of use, borrowing policies, and privacy notice.',
        'Terms of Use', '', '', TRUE, NULL, NULL, 'You must agree to the terms before continuing.', NULL, NULL, NULL, NULL)
RETURNING id INTO v_q37;

-- Q38: HTML — agreement confirmation (type 4); BOOLEAN SHOW target from Q37
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), v_survey_id, 4,
        '<p>Thank you. Your agreement has been recorded. Please continue to create your patron account.</p>',
        'Terms Confirmed', '', '', FALSE, NULL, NULL, '', NULL, NULL, NULL, NULL)
RETURNING id INTO v_q38;

-- ---- Step: Patron (Section: Patron Information) ----

-- Q39: TEXT — first name (type 8); min/max/validation/placeholder
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), v_survey_id, 8,
        'First name',
        'First name', '', '', TRUE, 2, 50, 'First name must be at least 2 characters.', NULL, NULL, 'First name', NULL)
RETURNING id INTO v_q39;

-- Q40: TEXT — last name (type 8)
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), v_survey_id, 8,
        'Last name',
        'Last name', '', '', TRUE, 2, 50, 'Last name must be at least 2 characters.', NULL, NULL, 'Last name', NULL)
RETURNING id INTO v_q40;

-- Q41: EMAIL — email address (type 13); getKeyValues EMAIL case extracts value for {EMAIL} token
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), v_survey_id, 13,
        'Email address',
        'Email', 'This will be used to notify you of holds and due dates', '', TRUE, NULL, NULL, '', NULL, NULL, 'you@example.com', NULL)
RETURNING id INTO v_q41;

-- Q42: PASSWORD — PIN / password (type 14); tool_tip and validation_text
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), v_survey_id, 14,
        'Create a PIN or password for your library account',
        'PIN', 'Minimum 4 digits or 8 characters', '', TRUE, NULL, NULL, 'A PIN or password is required.', NULL, NULL, NULL, NULL)
RETURNING id INTO v_q42;

-- Q43: TEXT — phone number (type 8); placeholder shows format; length validator (min=max=12) enforces ###-###-####
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), v_survey_id, 8,
        'Primary phone number',
        'Phone', '', '', FALSE, 12, 12, 'Please enter a valid phone number (###-###-####).', NULL, NULL, '###-###-####', NULL)
RETURNING id INTO v_q43;

-- Q44: DATE_PICKER — date of birth (type 2)
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), v_survey_id, 2,
        'What is your date of birth?',
        'Date of birth', 'Required for cardholders under 18', '', TRUE, NULL, NULL, '', NULL, NULL, NULL, NULL)
RETURNING id INTO v_q44;

-- Q45: RADIO — wants digital access (type 7); YesNo group; EQUAL 'TRUE' SHOW → Section: Digital Access
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), v_survey_id, 7,
        'Would you like access to the digital library (eBooks, audiobooks, and streaming media)?',
        'Digital access', '', 'vertical', TRUE, NULL, NULL, '', v_sg_yesno, NULL, NULL, NULL)
RETURNING id INTO v_q45;

-- ---- Step: Patron (Section: Digital Access, conditional: Q45 = TRUE) ----

-- Q46: COMBOBOX — preferred ebook format (type 3); EbookFormat group; EQUAL 'TRUE' SHOW target
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), v_survey_id, 3,
        'Preferred digital reading format',
        'Ebook format', '', '', TRUE, NULL, NULL, '', v_sg_ebookformat, NULL, NULL, NULL)
RETURNING id INTO v_q46;

-- Q47: CHECKBOX — opt in to new arrival notifications (type 1); BOOLEAN SHOW → Q48
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), v_survey_id, 1,
        'Notify me by email when new titles matching my interests become available.',
        'Arrival notifications', '', '', FALSE, NULL, NULL, '', NULL, NULL, NULL, NULL)
RETURNING id INTO v_q47;

-- Q48: CHECKBOX — notify via email (type 1); BOOLEAN SHOW target from Q47; {EMAIL} token from R_em
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), v_survey_id, 1,
        'Send via email {EMAIL}',
        'Notify via email', '', '', FALSE, NULL, NULL, '', NULL, NULL, NULL, NULL)
RETURNING id INTO v_q48;

-- Q48b: CHECKBOX — notify via SMS (type 1); BOOLEAN SHOW target from Q47; {PHONE} token from R_phone
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), v_survey_id, 1,
        'Send via SMS {PHONE}',
        'Notify via SMS', '', '', FALSE, NULL, NULL, '', NULL, NULL, NULL, NULL)
RETURNING id INTO v_q48b;

-- ---- Step: Preferences (Section: Collection Preferences) ----

-- Q49: INTEGER — max checkout items (type 5); GREATER_THAN '0' REPEAT → Step: CheckoutRequest
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), v_survey_id, 5,
        'How many items would you like to check out today?',
        'Checkout count', '', 'vertical', TRUE, 1, 10, 'Must be between 1 and 10.', NULL, NULL, NULL, NULL)
RETURNING id INTO v_q49;

-- Q50: CHECKBOX_GROUP — media types (type 11); MediaType group; variant=vertical
--      CONTAINS 'dvd' SHOW → Section: Media Preferences
--      CONTAINS 'audiobook' SHOW → Q51 (chain level 1→2)
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), v_survey_id, 11,
        'Which types of media do you want to check out?',
        'Media types', '', 'vertical', TRUE, NULL, NULL, '', v_sg_mediatype, NULL, NULL, NULL)
RETURNING id INTO v_q50;

-- Q51: TEXT — favorite audiobook narrator (type 8); CONTAINS 'audiobook' SHOW target (chain L2);
--      FIELD_EXIST SHOW → Q52 (chain level 2→3)
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), v_survey_id, 8,
        'Favorite audiobook narrator (if any)',
        'Narrator', '', '', FALSE, NULL, NULL, '', NULL, NULL, 'e.g., Scott Brick, Bahni Turpin', NULL)
RETURNING id INTO v_q51;

-- Q52: TEXT — preferred audio streaming platform (type 8); FIELD_EXIST SHOW target from Q51 (chain L3)
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), v_survey_id, 8,
        'Preferred audio streaming or download service',
        'Streaming service', '', '', FALSE, NULL, NULL, '', NULL, NULL, 'e.g., Libby, Hoopla, Audible', NULL)
RETURNING id INTO v_q52;

-- Q53: MULTI_SELECT — preferred literary genres (type 10); LiteraryGenre group;
--      CONTAINS 'mystery' SHOW → Q54
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), v_survey_id, 10,
        'Select your preferred reading genres',
        'Genres', '', '', FALSE, NULL, NULL, '', v_sg_literarygenre, NULL, NULL, NULL)
RETURNING id INTO v_q53;

-- Q54: TEXTAREA — favorite mystery/thriller authors (type 9); CONTAINS 'mystery' SHOW target; tool_tip
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), v_survey_id, 9,
        'List your favorite mystery or thriller authors',
        'Mystery authors', 'We will prioritize these for your hold queue', '', FALSE, NULL, NULL, '', NULL, NULL, 'e.g., Agatha Christie, Gillian Flynn, Michael Connelly', NULL)
RETURNING id INTO v_q54;

-- Q55: DOUBLE — average reading hours per week (type 6); min/max, placeholder, default_value
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), v_survey_id, 6,
        'On average, how many hours per week do you spend reading or listening to audio books?',
        'Weekly reading hours', '', '', FALSE, 0, 168, 'Must be between 0 and 168.', NULL, NULL, 'e.g., 5.5', '2.0')
RETURNING id INTO v_q55;

-- Q56: RADIO — library visit frequency (type 7); VisitFrequency group; NOT_EQUAL 'never' SHOW → Q57
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), v_survey_id, 7,
        'How often do you visit a library branch in person?',
        'Visit frequency', '', 'vertical', TRUE, NULL, NULL, '', v_sg_visitfreq, NULL, NULL, NULL)
RETURNING id INTO v_q56;

-- Q57: TEXT — preferred branch (type 8); NOT_EQUAL 'never' SHOW target from Q56; placeholder
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), v_survey_id, 8,
        'Which branch do you visit most often?',
        'Preferred branch', '', '', FALSE, NULL, NULL, '', NULL, NULL, 'e.g., Main Branch', NULL)
RETURNING id INTO v_q57;

-- ---- Step: Preferences (Section: Media Preferences, conditional: Q50 CONTAINS 'dvd') ----

-- Q58: TEXT — favorite DVD / video genres (type 8); CONTAINS 'dvd' SHOW target; placeholder
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), v_survey_id, 8,
        'Describe your favorite DVD or video genres',
        'DVD genres', '', '', FALSE, NULL, NULL, '', NULL, NULL, 'e.g., Action, Documentary, Family', NULL)
RETURNING id INTO v_q58;

-- Q59: DATE_PICKER — earliest media availability date (type 2)
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), v_survey_id, 2,
        'I am available to pick up media items from this date',
        'Availability date', '', '', FALSE, NULL, NULL, '', NULL, NULL, NULL, NULL)
RETURNING id INTO v_q59;

-- Q60: DATETIME — preferred media pickup time (type 12)
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), v_survey_id, 12,
        'Preferred date and time for media pickup',
        'Pickup time', '', '', FALSE, NULL, NULL, '', NULL, NULL, NULL, NULL)
RETURNING id INTO v_q60;

-- ---- Step: CheckoutRequest (Section: Checkout Request, repeated step per item) ----

-- Q61: TEXT — item title (type 8); FIELD_EXIST SHOW token 'S1' → Section: Checkout name;
--      min/max/validation/placeholder
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), v_survey_id, 8,
        'Title of the item you want to check out',
        'Item title', '', '', TRUE, 1, 255, 'Please enter the item title.', NULL, NULL, 'e.g., The Great Gatsby, Inception (DVD)', NULL)
RETURNING id INTO v_q61;

-- Q62: INTEGER — number of renewals needed (type 5); GREATER_THAN '0' REPEAT → ss_renewal
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), v_survey_id, 5,
        'How many renewals do you anticipate needing for this item?',
        'Renewals', '', 'vertical', TRUE, 0, 3, 'Must be between 0 and 3.', NULL, NULL, NULL, NULL)
RETURNING id INTO v_q62;

-- Q63: RADIO — item type (type 7); ItemType group; NOT_EQUAL 'book' SHOW → Q64
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), v_survey_id, 7,
        'What type of item is this?',
        'Item type', '', 'vertical', TRUE, NULL, NULL, '', v_sg_itemtype, NULL, NULL, NULL)
RETURNING id INTO v_q63;

-- Q64: TEXT — format or edition notes (type 8); NOT_EQUAL 'book' SHOW target from Q63; placeholder
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), v_survey_id, 8,
        'Format or edition notes (optional)',
        'Format notes', '', '', FALSE, NULL, NULL, '', NULL, NULL, 'e.g., Blu-ray, unabridged, MP3 CD', NULL)
RETURNING id INTO v_q64;

-- Q65: CHECKBOX — place on hold if unavailable (type 1); BOOLEAN SHOW → Q66
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), v_survey_id, 1,
        'Place this item on hold if it is not currently available.',
        'Place on hold', 'We will notify you when the item is ready', '', FALSE, NULL, NULL, '', NULL, NULL, NULL, NULL)
RETURNING id INTO v_q65;

-- Q66: TEXTAREA — special hold instructions (type 9); BOOLEAN SHOW target from Q65; placeholder
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), v_survey_id, 9,
        'Any special instructions for this hold request',
        'Hold instructions', '', '', FALSE, NULL, NULL, '', NULL, NULL, 'e.g., Specific edition needed, contact by phone only', NULL)
RETURNING id INTO v_q66;

-- ---- Step: Renewal (Section: Renewal, repeated section within checkout step) ----

-- Q67: DATE_PICKER — renewal pickup date (type 2); uses {Q#} in text; FIELD_EXIST SHOW 'S1' → sec renewal name
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), v_survey_id, 2,
        'Pickup date for renewal {Q#}',
        'Renewal date', '', '', TRUE, NULL, NULL, '', NULL, NULL, NULL, NULL)
RETURNING id INTO v_q67;

-- Q68: COMBOBOX — pickup branch for this renewal (type 3); PickupBranch group
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), v_survey_id, 3,
        'Preferred pickup branch for this renewal',
        'Pickup branch', '', '', TRUE, NULL, NULL, '', v_sg_pickupbranch, NULL, NULL, NULL)
RETURNING id INTO v_q68;

-- Q69: TEXT — alternate contact for renewal (type 8); optional; placeholder
INSERT INTO survey.questions(id, survey_id, type_id, text, short_text, tool_tip, variant, required, min_value, max_value, validation_text, select_group_id, mask, placeholder, default_value)
VALUES (NEXTVAL('survey.questions_seq'), v_survey_id, 8,
        'Alternate contact name for this renewal (if different from account holder)',
        'Alternate contact', '', '', FALSE, NULL, NULL, '', NULL, NULL, 'Full name of alternate contact', NULL)
RETURNING id INTO v_q69;

-- ============================================================
-- SECTIONS_QUESTIONS
-- SECTIONS_QUESTIONS (id, survey_id, question_id, section_id, display_order) --
-- ============================================================

-- Section: Welcome (sq36–sq38)
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), v_survey_id, v_q36, v_sect_welcome, 1)
RETURNING id INTO v_sq36;  -- sq36: Q36 HTML welcome

INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), v_survey_id, v_q37, v_sect_welcome, 2)
RETURNING id INTO v_sq37;  -- sq37: Q37 CHECKBOX terms

INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), v_survey_id, v_q38, v_sect_welcome, 3)
RETURNING id INTO v_sq38;  -- sq38: Q38 HTML confirmation

-- Section: Patron Information (sq39–sq45)
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), v_survey_id, v_q39, v_sect_patron, 1)
RETURNING id INTO v_sq39;  -- sq39: Q39 TEXT first name

INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), v_survey_id, v_q40, v_sect_patron, 2)
RETURNING id INTO v_sq40;  -- sq40: Q40 TEXT last name

INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), v_survey_id, v_q41, v_sect_patron, 3)
RETURNING id INTO v_sq41;  -- sq41: Q41 EMAIL

INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), v_survey_id, v_q42, v_sect_patron, 4)
RETURNING id INTO v_sq42;  -- sq42: Q42 PASSWORD

INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), v_survey_id, v_q43, v_sect_patron, 5)
RETURNING id INTO v_sq43;  -- sq43: Q43 TEXT phone

INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), v_survey_id, v_q44, v_sect_patron, 6)
RETURNING id INTO v_sq44;  -- sq44: Q44 DATE_PICKER DOB

INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), v_survey_id, v_q45, v_sect_patron, 7)
RETURNING id INTO v_sq45;  -- sq45: Q45 RADIO digital access

-- Section: Digital Access (sq46–sq48)
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), v_survey_id, v_q46, v_sect_digital, 1)
RETURNING id INTO v_sq46;  -- sq46: Q46 COMBOBOX ebook format

INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), v_survey_id, v_q47, v_sect_digital, 2)
RETURNING id INTO v_sq47;  -- sq47: Q47 CHECKBOX notifications

INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), v_survey_id, v_q48, v_sect_digital, 3)
RETURNING id INTO v_sq48;  -- sq48: Q48 CHECKBOX notify via email

INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), v_survey_id, v_q48b, v_sect_digital, 4)
RETURNING id INTO v_sq48b;  -- sq48b: Q48b CHECKBOX notify via SMS

-- Section: Collection Preferences (sq49–sq57)
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), v_survey_id, v_q49, v_sect_collprefs, 1)
RETURNING id INTO v_sq49;  -- sq49: Q49 INTEGER checkout count

INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), v_survey_id, v_q50, v_sect_collprefs, 2)
RETURNING id INTO v_sq50;  -- sq50: Q50 CHECKBOX_GROUP media types

INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), v_survey_id, v_q51, v_sect_collprefs, 3)
RETURNING id INTO v_sq51;  -- sq51: Q51 TEXT narrator (chain L2)

INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), v_survey_id, v_q52, v_sect_collprefs, 4)
RETURNING id INTO v_sq52;  -- sq52: Q52 TEXT streaming (chain L3)

INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), v_survey_id, v_q53, v_sect_collprefs, 5)
RETURNING id INTO v_sq53;  -- sq53: Q53 MULTI_SELECT genres

INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), v_survey_id, v_q54, v_sect_collprefs, 6)
RETURNING id INTO v_sq54;  -- sq54: Q54 TEXTAREA mystery authors

INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), v_survey_id, v_q55, v_sect_collprefs, 7)
RETURNING id INTO v_sq55;  -- sq55: Q55 DOUBLE reading hours

INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), v_survey_id, v_q56, v_sect_collprefs, 8)
RETURNING id INTO v_sq56;  -- sq56: Q56 RADIO visit frequency

INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), v_survey_id, v_q57, v_sect_collprefs, 9)
RETURNING id INTO v_sq57;  -- sq57: Q57 TEXT preferred branch

-- Section: Media Preferences (sq58–sq60)
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), v_survey_id, v_q58, v_sect_mediaprefs, 1)
RETURNING id INTO v_sq58;  -- sq58: Q58 TEXT DVD genres

INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), v_survey_id, v_q59, v_sect_mediaprefs, 2)
RETURNING id INTO v_sq59;  -- sq59: Q59 DATE_PICKER availability

INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), v_survey_id, v_q60, v_sect_mediaprefs, 3)
RETURNING id INTO v_sq60;  -- sq60: Q60 DATETIME pickup time

-- Section: Checkout Request (sq61–sq66)
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), v_survey_id, v_q61, v_sect_checkout, 1)
RETURNING id INTO v_sq61;  -- sq61: Q61 TEXT item title → {S1}

INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), v_survey_id, v_q62, v_sect_checkout, 2)
RETURNING id INTO v_sq62;  -- sq62: Q62 INTEGER renewals → REPEAT ss_renewal

INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), v_survey_id, v_q63, v_sect_checkout, 3)
RETURNING id INTO v_sq63;  -- sq63: Q63 RADIO item type

INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), v_survey_id, v_q64, v_sect_checkout, 4)
RETURNING id INTO v_sq64;  -- sq64: Q64 TEXT format notes

INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), v_survey_id, v_q65, v_sect_checkout, 5)
RETURNING id INTO v_sq65;  -- sq65: Q65 CHECKBOX hold request

INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), v_survey_id, v_q66, v_sect_checkout, 6)
RETURNING id INTO v_sq66;  -- sq66: Q66 TEXTAREA hold instructions

-- Section: Renewal (sq67–sq69)
INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), v_survey_id, v_q67, v_sect_renewal, 1)
RETURNING id INTO v_sq67;  -- sq67: Q67 DATE_PICKER renewal date {Q#}

INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), v_survey_id, v_q68, v_sect_renewal, 2)
RETURNING id INTO v_sq68;  -- sq68: Q68 COMBOBOX pickup branch

INSERT INTO survey.sections_questions(id, survey_id, question_id, section_id, display_order)
VALUES (NEXTVAL('survey.sections_questions_seq'), v_survey_id, v_q69, v_sect_renewal, 3)
RETURNING id INTO v_sq69;  -- sq69: Q69 TEXT alternate contact

-- ============================================================
-- RELATIONSHIPS.
--
-- Columns: id, survey_id, upstream_step_id, upstream_sq_id,
--          downstream_step_id, downstream_sq_id, downstream_s_id,
--          operator_id, action_id, token, description,
--          reference_value, default_upstream_value
--
-- operator_id:  1=BOOLEAN  2=GREATER_THAN  3=EQUAL  4=NOT_EQUAL
--               5=FIELD_EXIST  6=CONTAINS
-- action_id:    1=SHOW  2=REPEAT  3=TEXT
--
-- Coverage map:
--   Operators:    BOOLEAN(R18,R19,R20,R22,R22b,R31) GREATER_THAN(R23,R29) EQUAL(R21)
--                 NOT_EQUAL(R28,R30) FIELD_EXIST(R26,R_em,R_phone)
--                 CONTAINS(R24,R25,R27)
--   Actions:      SHOW(R18,R19,R20,R21,R22,R22b,R24,R25,R26,R27,R28,R30,R31)
--                 REPEAT(R23,R29)  TEXT(R_em,R_phone)
--   Tokens:       {EMAIL}(R_em)  {PHONE}(R_phone)  {S#} auto-numbered in section names
--   Repeated section: R23 — Q49 GREATER_THAN '0' REPEAT ss_checkout
--                    R29 — Q62 GREATER_THAN '0' REPEAT ss_renewal
--   Cond. step show:  R19  — Q37 BOOLEAN SHOW step_patron
--                     R20  — Q37 BOOLEAN SHOW step_prefs
--   Nested chain: Q50→Q51→Q52
--                 R25: Q50 CONTAINS 'audiobook' SHOW Q51 (L1→L2)
--                 R26: Q51 FIELD_EXIST SHOW Q52           (L2→L3)
--   Cond. section show: R21 ss_digital; R24 ss_mediaprefs
-- ============================================================

-- R18: Q37(sq37) BOOLEAN SHOW → Q38(sq38)
--      Operator: BOOLEAN | Action: SHOW | Exercises: BOOLEAN op; show confirmation after terms checked
INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_sq_id, downstream_s_id, operator_id, action_id, token, description, reference_value, default_upstream_value)
VALUES (NEXTVAL('survey.relationships_seq'), v_survey_id, v_step_welcome, v_sq37, NULL, v_sq38, NULL, 1, 1, NULL, 'Show terms-confirmed message when patron checks the agreement box', '', '');

-- R19: Q37(sq37) BOOLEAN SHOW → Step: Patron / Section: Patron Information (ss_patron)
--      Operator: BOOLEAN | Action: SHOW | Makes the Patron step visible when the patron accepts the terms.
--      Setting both downstream_step_id and downstream_s_id ensures buildDisplayKey uses the correct step
--      display order (2) so section answers are stored under the right display key. Filter 3 in
--      getInitialStepSectionsQuestion excludes ss_patron questions from init(), saving DB space when
--      the patron does not accept.
INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_sq_id, downstream_s_id, operator_id, action_id, token, description, reference_value, default_upstream_value)
VALUES (NEXTVAL('survey.relationships_seq'), v_survey_id, v_step_welcome, v_sq37, v_step_patron, NULL, v_ss_patron, 1, 1, NULL, 'Show Patron step when patron accepts the terms of use', '', '');

-- R20: Q37(sq37) BOOLEAN SHOW → Step: Preferences / Section: Collection Preferences (ss_collprefs)
--      Operator: BOOLEAN | Action: SHOW | Makes the Preferences step visible when the patron accepts the terms.
--      Same pattern as R19 — both downstream_step_id and downstream_s_id are set so the display key
--      is correct and ss_collprefs questions are excluded from init() until Q37 fires.
INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_sq_id, downstream_s_id, operator_id, action_id, token, description, reference_value, default_upstream_value)
VALUES (NEXTVAL('survey.relationships_seq'), v_survey_id, v_step_welcome, v_sq37, v_step_prefs, NULL, v_ss_collprefs, 1, 1, NULL, 'Show Preferences step when patron accepts the terms of use', '', '');

-- R_em: Q41(sq41) FIELD_EXIST TEXT 'em' → ss_digital
--       Q45 is now display_order=1, so ss_digital exists before Q41 is saved.
--       replaceText(R_em) runs sectionSQL → finds ss_digital section header → stores Dependent.
--       getSectionKeyValues(Q48) then resolves {EMAIL} from that Dependent when Q48 is shown.
--       Operator: FIELD_EXIST | Action: TEXT | Token: EMAIL
INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_sq_id, downstream_s_id, operator_id, action_id, token, description, reference_value, default_upstream_value)
VALUES (NEXTVAL('survey.relationships_seq'), v_survey_id, v_step_patron, v_sq41, NULL, 13, v_ss_digital, 5, 3, 'EMAIL', 'Substitute {EMAIL} token in Digital Access section with patron email address', '', '');

-- R_phone: Q43(sq43) FIELD_EXIST TEXT 'phone' → ss_digital
--          Same pattern as R_em. getSectionKeyValues(Q48b) resolves {PHONE} when Q48b is shown.
--          Operator: FIELD_EXIST | Action: TEXT | Token: phone
INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_sq_id, downstream_s_id, operator_id, action_id, token, description, reference_value, default_upstream_value)
VALUES (NEXTVAL('survey.relationships_seq'), v_survey_id, v_step_patron, v_sq43, NULL, 14, v_ss_digital, 5, 3, 'PHONE', 'Substitute {PHONE} token in Digital Access section with patron phone number', '', '');

-- R21: Q45(sq45) EQUAL 'TRUE' SHOW → Digital Access section (ss_digital)
--      Operator: EQUAL | Action: SHOW | Exercises: EQUAL op, conditional section show
INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_sq_id, downstream_s_id, operator_id, action_id, token, description, reference_value, default_upstream_value)
VALUES (NEXTVAL('survey.relationships_seq'), v_survey_id, v_step_patron, v_sq45, NULL, NULL, v_ss_digital, 3, 1, NULL, 'Show Digital Access section when patron opts in to digital library', 'TRUE', '');

-- R22: Q47(sq47) BOOLEAN SHOW → Q48(sq48)
--      Operator: BOOLEAN | Action: SHOW | Exercises: second BOOLEAN SHOW (within conditional section)
INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_sq_id, downstream_s_id, operator_id, action_id, token, description, reference_value, default_upstream_value)
VALUES (NEXTVAL('survey.relationships_seq'), v_survey_id, v_step_patron, v_sq47, NULL, v_sq48, NULL, 1, 1, NULL, 'Show email notification checkbox when patron opts in to arrival alerts', '', '');

-- R22b: Q47(sq47) BOOLEAN SHOW → Q48b(sq48b)
INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_sq_id, downstream_s_id, operator_id, action_id, token, description, reference_value, default_upstream_value)
VALUES (NEXTVAL('survey.relationships_seq'), v_survey_id, v_step_patron, v_sq47, NULL, v_sq48b, NULL, 1, 1, NULL, 'Show SMS notification checkbox when patron opts in to arrival alerts', '', '');

-- R23: Q49(sq49) GREATER_THAN '0' REPEAT → Checkout section (ss_checkout) in CheckoutRequest step
--      Operator: GREATER_THAN | Action: REPEAT | Exercises: REPEAT on section; repeats checkout section N times.
--      downstream_step_id = v_step_checkout is required so:
--        (a) buildRepeatedSections stores repeated instances under step 4 (not step 3 where Q49 lives), and
--        (b) sqlStep filter 1 excludes ss_checkout from buildInitialStepAnswers (prevents phantom instance 0).
INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_sq_id, downstream_s_id, operator_id, action_id, token, description, reference_value, default_upstream_value)
VALUES (NEXTVAL('survey.relationships_seq'), v_survey_id, v_step_prefs, v_sq49, v_step_checkout, NULL, v_ss_checkout, 2, 2, NULL, 'Repeat Checkout section once for each item the patron wants to check out', '0', '');

-- R24: Q50(sq50) CONTAINS 'dvd' SHOW → Media Preferences section (ss_mediaprefs)
--      Operator: CONTAINS | Action: SHOW | Exercises: CONTAINS on CHECKBOX_GROUP, conditional section
INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_sq_id, downstream_s_id, operator_id, action_id, token, description, reference_value, default_upstream_value)
VALUES (NEXTVAL('survey.relationships_seq'), v_survey_id, v_step_prefs, v_sq50, NULL, NULL, v_ss_mediaprefs, 6, 1, NULL, 'Show Media Preferences section when DVDs are selected', 'dvd', '');

-- R25: Q50(sq50) CONTAINS 'audiobook' SHOW → Q51(sq51)   [chain level 1 → 2]
--      Operator: CONTAINS | Action: SHOW | Exercises: start of 3-level nested SHOW chain
INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_sq_id, downstream_s_id, operator_id, action_id, token, description, reference_value, default_upstream_value)
VALUES (NEXTVAL('survey.relationships_seq'), v_survey_id, v_step_prefs, v_sq50, NULL, v_sq51, NULL, 6, 1, NULL, 'Show favorite narrator question when audio books are selected', 'audiobook', '');

-- R26: Q51(sq51) FIELD_EXIST SHOW → Q52(sq52)   [chain level 2 → 3]
--      Operator: FIELD_EXIST | Action: SHOW | Exercises: second link of 3-level nested chain
INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_sq_id, downstream_s_id, operator_id, action_id, token, description, reference_value, default_upstream_value)
VALUES (NEXTVAL('survey.relationships_seq'), v_survey_id, v_step_prefs, v_sq51, NULL, v_sq52, NULL, 5, 1, NULL, 'Show preferred streaming service question once a narrator has been entered (nested chain L3)', '', '');

-- R27: Q53(sq53) CONTAINS 'mystery' SHOW → Q54(sq54)
--      Operator: CONTAINS | Action: SHOW | Exercises: CONTAINS on MULTI_SELECT, show TEXTAREA
INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_sq_id, downstream_s_id, operator_id, action_id, token, description, reference_value, default_upstream_value)
VALUES (NEXTVAL('survey.relationships_seq'), v_survey_id, v_step_prefs, v_sq53, NULL, v_sq54, NULL, 6, 1, NULL, 'Show mystery author textarea when Mystery/Thriller genre is selected', 'mystery', '');

-- R28: Q56(sq56) NOT_EQUAL 'never' SHOW → Q57(sq57)
--      Operator: NOT_EQUAL | Action: SHOW | Exercises: NOT_EQUAL op; show branch for in-person visitors
INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_sq_id, downstream_s_id, operator_id, action_id, token, description, reference_value, default_upstream_value)
VALUES (NEXTVAL('survey.relationships_seq'), v_survey_id, v_step_prefs, v_sq56, NULL, v_sq57, NULL, 4, 1, NULL, 'Show preferred branch question when patron visits at least rarely', 'never', '');

-- R29: Q62(sq62) GREATER_THAN '0' REPEAT → Renewal section (ss_renewal) in Checkout step
--      Operator: GREATER_THAN | Action: REPEAT | Exercises: REPEAT on section (within repeated section).
--      downstream_step_id = v_step_checkout is required so repeated renewal instances land in step 4
--      and sqlStep filter 1 excludes ss_renewal from buildInitialStepAnswers (prevents phantom instance 0).
INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_sq_id, downstream_s_id, operator_id, action_id, token, description, reference_value, default_upstream_value)
VALUES (NEXTVAL('survey.relationships_seq'), v_survey_id, v_step_checkout, v_sq62, v_step_checkout, NULL, v_ss_renewal, 2, 2, NULL, 'Repeat Renewal section once for each renewal period the patron needs', '0', '');

-- R30: Q63(sq63) NOT_EQUAL 'book' SHOW → Q64(sq64)
--      Operator: NOT_EQUAL | Action: SHOW | Exercises: second NOT_EQUAL; show format notes for DVD/audio
INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_sq_id, downstream_s_id, operator_id, action_id, token, description, reference_value, default_upstream_value)
VALUES (NEXTVAL('survey.relationships_seq'), v_survey_id, v_step_checkout, v_sq63, NULL, v_sq64, NULL, 4, 1, NULL, 'Show format/edition notes for DVDs, audio books, and eMagazines', 'book', '');

-- R31: Q65(sq65) BOOLEAN SHOW → Q66(sq66)
--      Operator: BOOLEAN | Action: SHOW | Exercises: third BOOLEAN SHOW (in repeated step context)
INSERT INTO survey.relationships(id, survey_id, upstream_step_id, upstream_sq_id, downstream_step_id, downstream_sq_id, downstream_s_id, operator_id, action_id, token, description, reference_value, default_upstream_value)
VALUES (NEXTVAL('survey.relationships_seq'), v_survey_id, v_step_checkout, v_sq65, NULL, v_sq66, NULL, 1, 1, NULL, 'Show hold instructions textarea when patron requests a hold', '', '');

-- R32 and R33 removed: both had the upstream question (Q61/Q67) inside the downstream section (ss_checkout/ss_renewal).
-- The engine's TEXT handler calls saveAnswer for every answer in the downstream section, including the upstream question
-- itself, which re-triggers buildDownstreamQuestions → infinite loop. Section names now use {S#} auto-numbering only.

-- ============================================================
-- DIMENSIONS  (global — no survey_id; UNIQUE on name)
-- ============================================================
INSERT INTO survey.dimensions(id, name) VALUES (NEXTVAL('survey.dimensions_seq'), 'PatronProfile')
RETURNING id INTO v_dim_patron;

INSERT INTO survey.dimensions(id, name) VALUES (NEXTVAL('survey.dimensions_seq'), 'Collection')
RETURNING id INTO v_dim_collection;

INSERT INTO survey.dimensions(id, name) VALUES (NEXTVAL('survey.dimensions_seq'), 'HoldService')
RETURNING id INTO v_dim_hold;

INSERT INTO survey.dimensions(id, name) VALUES (NEXTVAL('survey.dimensions_seq'), 'Branch')
RETURNING id INTO v_dim_branch;

-- ============================================================
-- ONTOLOGY
-- 6 dimensioned (FK column added to fact_sections by ETL)
-- 4 standalone (no FK column; separate dim_<tag> table)f
-- Columns: id, survey_id, name, tag, dimension
-- ============================================================

-- Dimensioned —
INSERT INTO survey.ontology(id, survey_id, name, tag, dimension)
VALUES (NEXTVAL('survey.ontology_seq'), v_survey_id, 'Terms Consent',   'terms_consent',   v_dim_patron)
RETURNING id INTO v_ont16;

INSERT INTO survey.ontology(id, survey_id, name, tag, dimension)
VALUES (NEXTVAL('survey.ontology_seq'), v_survey_id, 'Digital Access',  'digital_access',  v_dim_patron)
RETURNING id INTO v_ont17;

INSERT INTO survey.ontology(id, survey_id, name, tag, dimension)
VALUES (NEXTVAL('survey.ontology_seq'), v_survey_id, 'Ebook Format',    'ebook_format',    v_dim_collection)
RETURNING id INTO v_ont18;

INSERT INTO survey.ontology(id, survey_id, name, tag, dimension)
VALUES (NEXTVAL('survey.ontology_seq'), v_survey_id, 'Media Interest',  'media_interest',  v_dim_collection)
RETURNING id INTO v_ont19;

INSERT INTO survey.ontology(id, survey_id, name, tag, dimension)
VALUES (NEXTVAL('survey.ontology_seq'), v_survey_id, 'Literary Genre',  'literary_genre',  v_dim_collection)
RETURNING id INTO v_ont20;

INSERT INTO survey.ontology(id, survey_id, name, tag, dimension)
VALUES (NEXTVAL('survey.ontology_seq'), v_survey_id, 'Item Type',       'item_type',       v_dim_hold)
RETURNING id INTO v_ont21;

INSERT INTO survey.ontology(id, survey_id, name, tag, dimension)
VALUES (NEXTVAL('survey.ontology_seq'), v_survey_id, 'Hold Request',    'hold_request',    v_dim_hold)
RETURNING id INTO v_ont22;

INSERT INTO survey.ontology(id, survey_id, name, tag, dimension)
VALUES (NEXTVAL('survey.ontology_seq'), v_survey_id, 'Pickup Branch',   'pickup_branch',   v_dim_branch)
RETURNING id INTO v_ont23;

-- Standalone —
INSERT INTO survey.ontology(id, survey_id, name, tag, dimension)
VALUES (NEXTVAL('survey.ontology_seq'), v_survey_id, 'Favorite Narrator',   'favorite_narrator', NULL)
RETURNING id INTO v_ont24;

INSERT INTO survey.ontology(id, survey_id, name, tag, dimension)
VALUES (NEXTVAL('survey.ontology_seq'), v_survey_id, 'Weekly Reading Hours', 'reading_hours',    NULL)
RETURNING id INTO v_ont25;

INSERT INTO survey.ontology(id, survey_id, name, tag, dimension)
VALUES (NEXTVAL('survey.ontology_seq'), v_survey_id, 'Item Title',          'item_title',        NULL)
RETURNING id INTO v_ont26;

-- ============================================================
-- METADATA
-- Binds a sections_question to an ontology tag for ETL reporting.
-- value = NULL → ETL uses the respondent's actual answer text_value.
-- value = 'constant' → ETL uses the fixed string for every respondent (used for
--                       step/section-level tags where the value is always the same).
-- Columns: id, survey_id, step_section_id, question_id, section_question_id, ontology_id, value
-- ============================================================
INSERT INTO survey.metadata(id, survey_id, step_section_id, question_id, section_question_id, ontology_id, value)
VALUES (NEXTVAL('survey.metadata_seq'), v_survey_id, NULL, NULL, v_sq37, v_ont16, 'Consented');  -- sq37 Q37 terms consent → terms_consent (constant: show 'Consented' not raw boolean)

INSERT INTO survey.metadata(id, survey_id, step_section_id, question_id, section_question_id, ontology_id, value)
VALUES (NEXTVAL('survey.metadata_seq'), v_survey_id, NULL, NULL, v_sq45, v_ont17, NULL);  -- sq45 Q45 digital access    → digital_access

INSERT INTO survey.metadata(id, survey_id, step_section_id, question_id, section_question_id, ontology_id, value)
VALUES (NEXTVAL('survey.metadata_seq'), v_survey_id, NULL, NULL, v_sq46, v_ont18, NULL);  -- sq46 Q46 ebook format      → ebook_format

INSERT INTO survey.metadata(id, survey_id, step_section_id, question_id, section_question_id, ontology_id, value)
VALUES (NEXTVAL('survey.metadata_seq'), v_survey_id, NULL, NULL, v_sq50, v_ont19, NULL);  -- sq50 Q50 media interest    → media_interest

INSERT INTO survey.metadata(id, survey_id, step_section_id, question_id, section_question_id, ontology_id, value)
VALUES (NEXTVAL('survey.metadata_seq'), v_survey_id, NULL, NULL, v_sq53, v_ont20, NULL);  -- sq53 Q53 literary genre    → literary_genre

INSERT INTO survey.metadata(id, survey_id, step_section_id, question_id, section_question_id, ontology_id, value)
VALUES (NEXTVAL('survey.metadata_seq'), v_survey_id, NULL, NULL, v_sq63, v_ont21, NULL);  -- sq63 Q63 item type         → item_type

INSERT INTO survey.metadata(id, survey_id, step_section_id, question_id, section_question_id, ontology_id, value)
VALUES (NEXTVAL('survey.metadata_seq'), v_survey_id, NULL, NULL, v_sq65, v_ont22, NULL);  -- sq65 Q65 hold request      → hold_request

INSERT INTO survey.metadata(id, survey_id, step_section_id, question_id, section_question_id, ontology_id, value)
VALUES (NEXTVAL('survey.metadata_seq'), v_survey_id, NULL, NULL, v_sq68, v_ont23, NULL);  -- sq68 Q68 pickup branch     → pickup_branch

INSERT INTO survey.metadata(id, survey_id, step_section_id, question_id, section_question_id, ontology_id, value)
VALUES (NEXTVAL('survey.metadata_seq'), v_survey_id, NULL, NULL, v_sq51, v_ont24, NULL);  -- sq51 Q51 favorite narrator → favorite_narrator

INSERT INTO survey.metadata(id, survey_id, step_section_id, question_id, section_question_id, ontology_id, value)
VALUES (NEXTVAL('survey.metadata_seq'), v_survey_id, NULL, NULL, v_sq55, v_ont25, NULL);  -- sq55 Q55 reading hours     → reading_hours

INSERT INTO survey.metadata(id, survey_id, step_section_id, question_id, section_question_id, ontology_id, value)
VALUES (NEXTVAL('survey.metadata_seq'), v_survey_id, NULL, NULL, v_sq61, v_ont26, NULL);  -- sq61 Q61 item title        → item_title

-- ============================================================
-- REPORTS  (survey_id = v_survey_id)
-- ============================================================
INSERT INTO survey.reports(id, survey_id, name, description, url, display_order)
VALUES (NEXTVAL('survey.reports_seq'), v_survey_id,
        'Patron Checkout Summary',
        'Summary report of patron registration details and checkout requests',
        'http://localhost:8080/survey-summary/report', 1);

-- ============================================================
-- POST-SURVEY ACTIONS  (survey_id = v_survey_id)
-- ============================================================
INSERT INTO survey.post_survey_actions(id, survey_id, name, description, url, execution_order)
VALUES (NEXTVAL('survey.post_survey_actions_seq'), v_survey_id,
        'Notify ILS System',
        'Notify the Integrated Library System of the new patron registration and checkout request',
        'http://localhost:8080/api/ils/notify', 1);

-- ============================================================
-- RESPONDENTS  (survey_id = v_survey_id)
-- ============================================================
-- INSERT INTO survey.respondents (id, survey_id, token, active, logins, created_dt, first_access_dt, finalized_dt)
-- VALUES (NEXTVAL('survey.respondents_seq'), v_survey_id, 'libtest', TRUE, 0, current_timestamp, NULL, NULL);

-- INSERT INTO survey.respondents (id, survey_id, token, active, logins, created_dt, first_access_dt, finalized_dt)
-- VALUES (NEXTVAL('survey.respondents_seq'), v_survey_id, 'lib-inactive', FALSE, 0, current_timestamp, NULL, NULL);

-- ============================================================
-- SELECT ITEMS
-- Columns: id, survey_id, group_id, display_text, display_order, coded_value
-- ============================================================

-- Group: YesNo --
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), v_survey_id, v_sg_yesno, 'Yes', 1, 'TRUE');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), v_survey_id, v_sg_yesno, 'No',  2, 'FALSE');

-- Group: MediaType — coded_values match CONTAINS reference_values in R24 and R25 --
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), v_survey_id, v_sg_mediatype, 'Books',       1, 'book');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), v_survey_id, v_sg_mediatype, 'DVDs',        2, 'dvd');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), v_survey_id, v_sg_mediatype, 'Audio Books', 3, 'audiobook');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), v_survey_id, v_sg_mediatype, 'eBooks',      4, 'ebook');

-- Group: LiteraryGenre — coded_value 'mystery' matches R27 reference_value --
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), v_survey_id, v_sg_literarygenre, 'Mystery / Thriller', 1, 'mystery');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), v_survey_id, v_sg_literarygenre, 'Sci-Fi / Fantasy',   2, 'scifi');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), v_survey_id, v_sg_literarygenre, 'Romance',            3, 'romance');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), v_survey_id, v_sg_literarygenre, 'Non-Fiction',        4, 'nonfiction');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), v_survey_id, v_sg_literarygenre, 'Biography',          5, 'biography');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), v_survey_id, v_sg_literarygenre, 'History',            6, 'history');

-- Group: PickupBranch — used by Q68 COMBOBOX in Renewal section --
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), v_survey_id, v_sg_pickupbranch, 'Main Branch',  1, 'main');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), v_survey_id, v_sg_pickupbranch, 'North Branch', 2, 'north');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), v_survey_id, v_sg_pickupbranch, 'South Branch', 3, 'south');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), v_survey_id, v_sg_pickupbranch, 'East Branch',  4, 'east');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), v_survey_id, v_sg_pickupbranch, 'West Branch',  5, 'west');

-- Group: VisitFrequency — coded_value 'never' matches R28 reference_value --
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), v_survey_id, v_sg_visitfreq, 'Daily',   1, 'daily');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), v_survey_id, v_sg_visitfreq, 'Weekly',  2, 'weekly');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), v_survey_id, v_sg_visitfreq, 'Monthly', 3, 'monthly');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), v_survey_id, v_sg_visitfreq, 'Rarely',  4, 'rarely');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), v_survey_id, v_sg_visitfreq, 'Never',   5, 'never');

-- Group: ItemType — coded_value 'book' matches R30 NOT_EQUAL reference_value --
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), v_survey_id, v_sg_itemtype, 'Book',       1, 'book');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), v_survey_id, v_sg_itemtype, 'DVD',        2, 'dvd');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), v_survey_id, v_sg_itemtype, 'Audio Book', 3, 'audiobook');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), v_survey_id, v_sg_itemtype, 'eMagazine',  4, 'emagazine');

-- Group: EbookFormat — used by Q46 COMBOBOX in Digital Access section --
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), v_survey_id, v_sg_ebookformat, 'EPUB', 1, 'epub');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), v_survey_id, v_sg_ebookformat, 'PDF',  2, 'pdf');
INSERT INTO survey.select_items(id, survey_id, group_id, display_text, display_order, coded_value)
VALUES (NEXTVAL('survey.select_items_seq'), v_survey_id, v_sg_ebookformat, 'MOBI', 3, 'mobi');

END $$;
