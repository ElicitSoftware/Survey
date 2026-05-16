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
-- Tess Tester — completed Library Card Registration response
-- Respondent: id=3, token='test1', survey_id=1 (LibraryCardReg)
--
-- Covers all active paths through the V005 survey:
--   Terms accepted → Patron + Preferences + Checkout steps shown
--   Digital access opted in → Digital Access section shown
--   Media types: audiobook + dvd → narrator chain (L1→L2→L3) + Media Prefs shown
--   2 checkout items → Checkout section repeated x2
--     Item 1 (book, 1 renewal, hold) → Renewal section x1
--     Item 2 (audiobook, 0 renewals, hold, format notes)
--   Genres: scifi only (mystery path NOT taken)
--   Visit frequency: daily → preferred branch shown
--
-- Insertion order to satisfy referential integrity:
--   1. respondents   (→ surveys)
--   2. answers       (→ respondents, surveys, steps, sections, sections_questions, questions)
--   3. dependents    (→ respondents, answers, relationships)
--   4. sequence sync (setval for respondents_seq, answers_seq, dependents_seq)
-- ============================================================

SET TIMEZONE TO 'America/Detroit';

-- ============================================================
-- RESPONDENT
-- ============================================================
INSERT INTO survey.respondents (id, survey_id, token, active, logins, created_dt, first_access_dt, finalized_dt)
VALUES (1, 1, 'test1', false, 1,
        '2026-05-07 20:41:43.912+00',
        '2026-05-07 20:41:43.890+00',
        '2026-05-07 20:45:17.250+00');

-- ============================================================
-- ANSWERS  (48 rows)
-- Columns: id, survey_id, respondent_id,
--          step, step_instance, section, section_instance,
--          question_display_order, question_instance,
--          section_question_id, question_id,
--          display_key, display_text, text_value,
--          deleted, created_dt, saved_dt
--
-- step / section values are FKs to steps.id / sections.id:
--   steps:    1=Welcome  2=Patron  3=Preferences  4=CheckoutRequest
--   sections: 1=Welcome  2=PatronInfo  3=DigitalAccess
--             4=CollectionPrefs  5=MediaPrefs  6=Checkout{S#}  7=Renewal{S#}
--
-- section_question_id / question_id (1–35) map to V005 insertion order:
--   1=sq36/Q36(HTML welcome)     2=sq37/Q37(CHECKBOX terms)      3=sq38/Q38(HTML confirm)
--   4=sq39/Q39(TEXT firstname)   5=sq40/Q40(TEXT lastname)       6=sq41/Q41(TEXT email)
--   7=sq42/Q42(PASSWORD pin)     8=sq43/Q43(TEXT phone)          9=sq44/Q44(DATE dob)
--  10=sq45/Q45(RADIO digital)   11=sq46/Q46(COMBOBOX ebook)     12=sq47/Q47(CHECKBOX notify)
--  13=sq48/Q48(CHECKBOX email)  14=sq48b/Q48b(CHECKBOX sms)     15=sq49/Q49(INTEGER checkout)
--  16=sq50/Q50(CBGROUP media)   17=sq51/Q51(TEXT narrator)      18=sq52/Q52(TEXT streaming)
--  19=sq53/Q53(MULTISEL genre)  20=sq54/Q54(TEXTAREA mystery)   21=sq55/Q55(DOUBLE hours)
--  22=sq56/Q56(RADIO visit)     23=sq57/Q57(TEXT branch)        24=sq58/Q58(TEXT dvdgenre)
--  25=sq59/Q59(DATE avail)      26=sq60/Q60(DATETIME pickup)    27=sq61/Q61(TEXT title)
--  28=sq62/Q62(INTEGER renewals) 29=sq63/Q63(RADIO itemtype)    30=sq64/Q64(TEXT format)
--  31=sq65/Q65(CHECKBOX hold)   32=sq66/Q66(TEXTAREA holdinst)  33=sq67/Q67(DATE renewdate)
--  34=sq68/Q68(COMBOBOX branch) 35=sq69/Q69(TEXT altcontact)
-- ============================================================

-- ---- Step 1: Welcome / Section 1: Welcome ----
-- Section header
INSERT INTO survey.answers (id, survey_id, respondent_id, step, step_instance, section, section_instance, question_display_order, question_instance, section_question_id, question_id, display_key, display_text, text_value, deleted, created_dt, saved_dt)
VALUES (1, 1, 1, 1, 0, 1, 0, NULL, 0, NULL, NULL, '0001-0001-0000-0001-0000-0000-0000', 'Welcome', NULL, false, '2026-05-07 20:41:44.048+00', NULL);

-- Q36: HTML welcome block
INSERT INTO survey.answers (id, survey_id, respondent_id, step, step_instance, section, section_instance, question_display_order, question_instance, section_question_id, question_id, display_key, display_text, text_value, deleted, created_dt, saved_dt)
VALUES (2, 1, 1, 1, 0, 1, 0, NULL, 0, 1, 1, '0001-0001-0000-0001-0000-0001-0000', '<h1>Welcome to the Public Library</h1><p>Complete this form to register for a library card. You will create a login, set your collection preferences, and submit a checkout request for books, DVDs, and audio books.</p>', NULL, false, '2026-05-07 20:41:44.070+00', NULL);

-- Q37: CHECKBOX — terms agreed (true)
INSERT INTO survey.answers (id, survey_id, respondent_id, step, step_instance, section, section_instance, question_display_order, question_instance, section_question_id, question_id, display_key, display_text, text_value, deleted, created_dt, saved_dt)
VALUES (3, 1, 1, 1, 0, 1, 0, NULL, 0, 2, 2, '0001-0001-0000-0001-0000-0002-0000', 'I agree to the library terms of use, borrowing policies, and privacy notice.', 'true', false, '2026-05-07 20:41:44.085+00', '2026-05-07 20:41:46.231+00');

-- Q38: HTML terms confirmation (shown via R18 BOOLEAN)
INSERT INTO survey.answers (id, survey_id, respondent_id, step, step_instance, section, section_instance, question_display_order, question_instance, section_question_id, question_id, display_key, display_text, text_value, deleted, created_dt, saved_dt)
VALUES (4, 1, 1, 1, 0, 1, 0, NULL, 0, 3, 3, '0001-0001-0000-0001-0000-0003-0000', '<p>Thank you. Your agreement has been recorded. Please continue to create your patron account.</p>', NULL, false, '2026-05-07 20:41:46.279+00', NULL);

-- ---- Step 2: Patron / Section 2: Patron Information (shown via R19) ----
-- Section header
INSERT INTO survey.answers (id, survey_id, respondent_id, step, step_instance, section, section_instance, question_display_order, question_instance, section_question_id, question_id, display_key, display_text, text_value, deleted, created_dt, saved_dt)
VALUES (5, 1, 1, 2, 0, 2, 0, NULL, 0, NULL, NULL, '0001-0002-0000-0002-0000-0000-0000', 'Patron Information', NULL, false, '2026-05-07 20:41:46.332+00', NULL);

-- Q39: TEXT — First name = Tess
INSERT INTO survey.answers (id, survey_id, respondent_id, step, step_instance, section, section_instance, question_display_order, question_instance, section_question_id, question_id, display_key, display_text, text_value, deleted, created_dt, saved_dt)
VALUES (6, 1, 1, 2, 0, 2, 0, NULL, 0, 4, 4, '0001-0002-0000-0002-0000-0001-0000', 'First name', 'Tess', false, '2026-05-07 20:41:46.353+00', '2026-05-07 20:41:51.716+00');

-- Q40: TEXT — Last name = Tester
INSERT INTO survey.answers (id, survey_id, respondent_id, step, step_instance, section, section_instance, question_display_order, question_instance, section_question_id, question_id, display_key, display_text, text_value, deleted, created_dt, saved_dt)
VALUES (7, 1, 1, 2, 0, 2, 0, NULL, 0, 5, 5, '0001-0002-0000-0002-0000-0002-0000', 'Last name', 'Tester', false, '2026-05-07 20:41:46.372+00', '2026-05-07 20:41:53.306+00');

-- Q41: TEXT — Email = Tess@Tester.tst (also feeds {EMAIL} token via R_em)
INSERT INTO survey.answers (id, survey_id, respondent_id, step, step_instance, section, section_instance, question_display_order, question_instance, section_question_id, question_id, display_key, display_text, text_value, deleted, created_dt, saved_dt)
VALUES (8, 1, 1, 2, 0, 2, 0, NULL, 0, 6, 6, '0001-0002-0000-0002-0000-0003-0000', 'Email address', 'Tess@Tester.tst', false, '2026-05-07 20:41:46.389+00', '2026-05-07 20:42:04.201+00');

-- Q42: PASSWORD — PIN = 1234
INSERT INTO survey.answers (id, survey_id, respondent_id, step, step_instance, section, section_instance, question_display_order, question_instance, section_question_id, question_id, display_key, display_text, text_value, deleted, created_dt, saved_dt)
VALUES (9, 1, 1, 2, 0, 2, 0, NULL, 0, 7, 7, '0001-0002-0000-0002-0000-0004-0000', 'Create a PIN or password for your library account', '1234', false, '2026-05-07 20:41:46.422+00', '2026-05-07 20:42:07.323+00');

-- Q43: TEXT — Phone = 555-555-1234 (also feeds {PHONE} token via R_phone)
INSERT INTO survey.answers (id, survey_id, respondent_id, step, step_instance, section, section_instance, question_display_order, question_instance, section_question_id, question_id, display_key, display_text, text_value, deleted, created_dt, saved_dt)
VALUES (10, 1, 1, 2, 0, 2, 0, NULL, 0, 8, 8, '0001-0002-0000-0002-0000-0005-0000', 'Primary phone number', '555-555-1234', false, '2026-05-07 20:41:46.436+00', '2026-05-07 20:42:16.929+00');

-- Q44: DATE_PICKER — Date of birth = 2000-01-01
INSERT INTO survey.answers (id, survey_id, respondent_id, step, step_instance, section, section_instance, question_display_order, question_instance, section_question_id, question_id, display_key, display_text, text_value, deleted, created_dt, saved_dt)
VALUES (11, 1, 1, 2, 0, 2, 0, NULL, 0, 9, 9, '0001-0002-0000-0002-0000-0006-0000', 'What is your date of birth?', '2000-01-01', false, '2026-05-07 20:41:46.466+00', '2026-05-07 20:42:28.691+00');

-- Q45: RADIO — Digital access = TRUE (triggers R21 EQUAL 'TRUE' SHOW Digital Access section)
INSERT INTO survey.answers (id, survey_id, respondent_id, step, step_instance, section, section_instance, question_display_order, question_instance, section_question_id, question_id, display_key, display_text, text_value, deleted, created_dt, saved_dt)
VALUES (12, 1, 1, 2, 0, 2, 0, NULL, 0, 10, 10, '0001-0002-0000-0002-0000-0007-0000', 'Would you like access to the digital library (eBooks, audiobooks, and streaming media)?', 'TRUE', false, '2026-05-07 20:41:46.480+00', '2026-05-07 20:42:29.249+00');

-- ---- Step 3: Preferences / Section 4: Collection Preferences (shown via R20) ----
-- Section header
INSERT INTO survey.answers (id, survey_id, respondent_id, step, step_instance, section, section_instance, question_display_order, question_instance, section_question_id, question_id, display_key, display_text, text_value, deleted, created_dt, saved_dt)
VALUES (13, 1, 1, 3, 0, 4, 0, NULL, 0, NULL, NULL, '0001-0003-0000-0004-0000-0000-0000', 'Collection Preferences', NULL, false, '2026-05-07 20:41:46.528+00', NULL);

-- Q49: INTEGER — Checkout count = 2 (triggers R23 GREATER_THAN '0' REPEAT Checkout section x2)
INSERT INTO survey.answers (id, survey_id, respondent_id, step, step_instance, section, section_instance, question_display_order, question_instance, section_question_id, question_id, display_key, display_text, text_value, deleted, created_dt, saved_dt)
VALUES (14, 1, 1        , 3, 0, 4, 0, NULL, 0, 15, 15, '0001-0003-0000-0004-0000-0001-0000', 'How many items would you like to check out today?', '2', false, '2026-05-07 20:41:46.544+00', '2026-05-07 20:42:40.004+00');

-- Q50: CHECKBOX_GROUP — Media types = audiobook,dvd
--   triggers R24 CONTAINS 'dvd'       → Media Preferences section
--   triggers R25 CONTAINS 'audiobook' → Q51 narrator (chain L1)
INSERT INTO survey.answers (id, survey_id, respondent_id, step, step_instance, section, section_instance, question_display_order, question_instance, section_question_id, question_id, display_key, display_text, text_value, deleted, created_dt, saved_dt)
VALUES (15, 1, 1, 3, 0, 4, 0, NULL, 0, 16, 16, '0001-0003-0000-0004-0000-0002-0000', 'Which types of media do you want to check out?', 'audiobook,dvd', false, '2026-05-07 20:41:46.572+00', '2026-05-07 20:42:41.650+00');

-- Q53: MULTI_SELECT — Genres = scifi,mystery (triggers R27 CONTAINS 'mystery' → Q54 shown)
INSERT INTO survey.answers (id, survey_id, respondent_id, step, step_instance, section, section_instance, question_display_order, question_instance, section_question_id, question_id, display_key, display_text, text_value, deleted, created_dt, saved_dt)
VALUES (16, 1, 1, 3, 0, 4, 0, NULL, 0, 19, 19, '0001-0003-0000-0004-0000-0005-0000', 'Select your preferred reading genres', 'scifi,mystery', false, '2026-05-07 20:41:46.608+00', '2026-05-07 20:42:57.721+00');

-- Q54: TEXTAREA — Favorite mystery/thriller authors (shown via R27 CONTAINS 'mystery')
INSERT INTO survey.answers (id, survey_id, respondent_id, step, step_instance, section, section_instance, question_display_order, question_instance, section_question_id, question_id, display_key, display_text, text_value, deleted, created_dt, saved_dt)
VALUES (49, 1, 1, 3, 0, 4, 0, NULL, 0, 20, 20, '0001-0003-0000-0004-0000-0006-0000', 'List your favorite mystery or thriller authors', 'Agatha Christie, Gillian Flynn', false, '2026-05-07 20:42:57.731+00', '2026-05-07 20:43:00.112+00');

-- Q55: DOUBLE — Weekly reading hours = 2.0 (default value accepted)
INSERT INTO survey.answers (id, survey_id, respondent_id, step, step_instance, section, section_instance, question_display_order, question_instance, section_question_id, question_id, display_key, display_text, text_value, deleted, created_dt, saved_dt)
VALUES (17, 1, 1, 3, 0, 4, 0, NULL, 0, 21, 21, '0001-0003-0000-0004-0000-0007-0000', 'On average, how many hours per week do you spend reading or listening to audio books?', '2.0', false, '2026-05-07 20:41:46.630+00', '2026-05-07 20:41:46.628+00');

-- Q56: RADIO — Visit frequency = daily (triggers R28 NOT_EQUAL 'never' → Q57 branch)
INSERT INTO survey.answers (id, survey_id, respondent_id, step, step_instance, section, section_instance, question_display_order, question_instance, section_question_id, question_id, display_key, display_text, text_value, deleted, created_dt, saved_dt)
VALUES (18, 1, 1, 3, 0, 4, 0, NULL, 0, 22, 22, '0001-0003-0000-0004-0000-0008-0000', 'How often do you visit a library branch in person?', 'daily', false, '2026-05-07 20:41:46.646+00', '2026-05-07 20:43:01.690+00');

-- ---- Step 2: Patron / Section 3: Digital Access (shown via R21 EQUAL 'TRUE') ----
-- Section header
INSERT INTO survey.answers (id, survey_id, respondent_id, step, step_instance, section, section_instance, question_display_order, question_instance, section_question_id, question_id, display_key, display_text, text_value, deleted, created_dt, saved_dt)
VALUES (19, 1, 1, 2, 0, 3, 0, NULL, 0, NULL, NULL, '0001-0002-0000-0003-0000-0000-0000', 'Digital Access', NULL, false, '2026-05-07 20:42:29.263+00', NULL);

-- Q46: COMBOBOX — Ebook format (text_value stored as object toString — as-is)
INSERT INTO survey.answers (id, survey_id, respondent_id, step, step_instance, section, section_instance, question_display_order, question_instance, section_question_id, question_id, display_key, display_text, text_value, deleted, created_dt, saved_dt)
VALUES (20, 1, 1, 2, 0, 3, 0, NULL, 0, 11, 11, '0001-0002-0000-0003-0000-0001-0000', 'Preferred digital reading format', 'MP3', false, '2026-05-07 20:42:29.272+00', '2026-05-07 20:42:34.662+00');

-- Q47: CHECKBOX — Arrival notifications = true (triggers R22+R22b BOOLEAN → Q48+Q48b)
INSERT INTO survey.answers (id, survey_id, respondent_id, step, step_instance, section, section_instance, question_display_order, question_instance, section_question_id, question_id, display_key, display_text, text_value, deleted, created_dt, saved_dt)
VALUES (21, 1, 1, 2, 0, 3, 0, NULL, 0, 12, 12, '0001-0002-0000-0003-0000-0002-0000', 'Notify me by email when new titles matching my interests become available.', 'true', false, '2026-05-07 20:42:29.283+00', '2026-05-07 20:42:35.458+00');

-- Q48: CHECKBOX — Notify via email {EMAIL} = true (shown via R22 BOOLEAN)
INSERT INTO survey.answers (id, survey_id, respondent_id, step, step_instance, section, section_instance, question_display_order, question_instance, section_question_id, question_id, display_key, display_text, text_value, deleted, created_dt, saved_dt)
VALUES (22, 1, 1, 2, 0, 3, 0, NULL, 0, 13, 13, '0001-0002-0000-0003-0000-0003-0000', 'Send via email Tess@Tester.tst', 'true', false, '2026-05-07 20:42:35.467+00', '2026-05-07 20:42:36.350+00');

-- Q48b: CHECKBOX — Notify via SMS {PHONE} = true (shown via R22b BOOLEAN)
INSERT INTO survey.answers (id, survey_id, respondent_id, step, step_instance, section, section_instance, question_display_order, question_instance, section_question_id, question_id, display_key, display_text, text_value, deleted, created_dt, saved_dt)
VALUES (23, 1, 1, 2, 0, 3, 0, NULL, 0, 14, 14, '0001-0002-0000-0003-0000-0004-0000', 'Send via SMS 555-555-1234', 'true', false, '2026-05-07 20:42:35.492+00', '2026-05-07 20:42:36.882+00');

-- ---- Step 4: CheckoutRequest / Section 6: Checkout 1 (instance 1; shown via R23 REPEAT) ----
-- Section header — Checkout 1
INSERT INTO survey.answers (id, survey_id, respondent_id, step, step_instance, section, section_instance, question_display_order, question_instance, section_question_id, question_id, display_key, display_text, text_value, deleted, created_dt, saved_dt)
VALUES (24, 1, 1, 4, 0, 6, 1, NULL, 0, NULL, NULL, '0001-0004-0000-0006-0001-0000-0000', 'Checkout 1', NULL, false, '2026-05-07 20:42:39.332+00', NULL);

-- Q61: TEXT — Item title = A Pattern Language
INSERT INTO survey.answers (id, survey_id, respondent_id, step, step_instance, section, section_instance, question_display_order, question_instance, section_question_id, question_id, display_key, display_text, text_value, deleted, created_dt, saved_dt)
VALUES (25, 1, 1, 4, 0, 6, 1, NULL, 0, 27, 27, '0001-0004-0000-0006-0001-0001-0000', 'Title of the item you want to check out', 'A Pattern Language', false, '2026-05-07 20:42:39.359+00', '2026-05-07 20:44:11.489+00');

-- Q62: INTEGER — Renewals needed = 1 (triggers R29 GREATER_THAN '0' REPEAT Renewal section x1)
INSERT INTO survey.answers (id, survey_id, respondent_id, step, step_instance, section, section_instance, question_display_order, question_instance, section_question_id, question_id, display_key, display_text, text_value, deleted, created_dt, saved_dt)
VALUES (26, 1, 1, 4, 0, 6, 1, NULL, 0, 28, 28, '0001-0004-0000-0006-0001-0002-0000', 'How many renewals do you anticipate needing for this item?', '1', false, '2026-05-07 20:42:39.367+00', '2026-05-07 20:44:14.965+00');

-- Q63: RADIO — Item type = book (R30 NOT_EQUAL 'book' not triggered; Q64 format notes not shown)
INSERT INTO survey.answers (id, survey_id, respondent_id, step, step_instance, section, section_instance, question_display_order, question_instance, section_question_id, question_id, display_key, display_text, text_value, deleted, created_dt, saved_dt)
VALUES (27, 1, 1, 4, 0, 6, 1, NULL, 0, 29, 29, '0001-0004-0000-0006-0001-0003-0000', 'What type of item is this?', 'book', false, '2026-05-07 20:42:39.381+00', '2026-05-07 20:44:15.981+00');

-- Q65: CHECKBOX — Place on hold = true (triggers R31 BOOLEAN → Q66 hold instructions)
INSERT INTO survey.answers (id, survey_id, respondent_id, step, step_instance, section, section_instance, question_display_order, question_instance, section_question_id, question_id, display_key, display_text, text_value, deleted, created_dt, saved_dt)
VALUES (28, 1, 1, 4, 0, 6, 1, NULL, 0, 31, 31, '0001-0004-0000-0006-0001-0005-0000', 'Place this item on hold if it is not currently available.', 'true', false, '2026-05-07 20:42:39.392+00', '2026-05-07 20:44:18.154+00');

-- ---- Step 4: CheckoutRequest / Section 6: Checkout 2 (instance 2; shown via R23 REPEAT) ----
-- Section header — Checkout 2
INSERT INTO survey.answers (id, survey_id, respondent_id, step, step_instance, section, section_instance, question_display_order, question_instance, section_question_id, question_id, display_key, display_text, text_value, deleted, created_dt, saved_dt)
VALUES (29, 1, 1, 4, 0, 6, 2, NULL, 0, NULL, NULL, '0001-0004-0000-0006-0002-0000-0000', 'Checkout 2', NULL, false, '2026-05-07 20:42:40.081+00', NULL);

-- Q61: TEXT — Item title = The timeless way of building
INSERT INTO survey.answers (id, survey_id, respondent_id, step, step_instance, section, section_instance, question_display_order, question_instance, section_question_id, question_id, display_key, display_text, text_value, deleted, created_dt, saved_dt)
VALUES (30, 1, 1, 4, 0, 6, 2, NULL, 0, 27, 27, '0001-0004-0000-0006-0002-0001-0000', 'Title of the item you want to check out', 'The timeless way of building', false, '2026-05-07 20:42:40.099+00', '2026-05-07 20:44:41.141+00');

-- Q62: INTEGER — Renewals needed = 0 (R29 not triggered; no renewal section for checkout 2)
INSERT INTO survey.answers (id, survey_id, respondent_id, step, step_instance, section, section_instance, question_display_order, question_instance, section_question_id, question_id, display_key, display_text, text_value, deleted, created_dt, saved_dt)
VALUES (31, 1, 1, 4, 0, 6, 2, NULL, 0, 28, 28, '0001-0004-0000-0006-0002-0002-0000', 'How many renewals do you anticipate needing for this item?', '0', false, '2026-05-07 20:42:40.107+00', '2026-05-07 20:44:44.744+00');

-- Q63: RADIO — Item type = audiobook (triggers R30 NOT_EQUAL 'book' → Q64 format notes)
INSERT INTO survey.answers (id, survey_id, respondent_id, step, step_instance, section, section_instance, question_display_order, question_instance, section_question_id, question_id, display_key, display_text, text_value, deleted, created_dt, saved_dt)
VALUES (32, 1, 1, 4, 0, 6, 2, NULL, 0, 29, 29, '0001-0004-0000-0006-0002-0003-0000', 'What type of item is this?', 'audiobook', false, '2026-05-07 20:42:40.120+00', '2026-05-07 20:44:47.650+00');

-- Q65: CHECKBOX — Place on hold = true (triggers R31 BOOLEAN → Q66 hold instructions)
INSERT INTO survey.answers (id, survey_id, respondent_id, step, step_instance, section, section_instance, question_display_order, question_instance, section_question_id, question_id, display_key, display_text, text_value, deleted, created_dt, saved_dt)
VALUES (33, 1, 1, 4, 0, 6, 2, NULL, 0, 31, 31, '0001-0004-0000-0006-0002-0005-0000', 'Place this item on hold if it is not currently available.', 'true', false, '2026-05-07 20:42:40.131+00', '2026-05-07 20:44:56.244+00');

-- ---- Step 3: Preferences / Section 5: Media Preferences (shown via R24 CONTAINS 'dvd') ----
-- Section header
INSERT INTO survey.answers (id, survey_id, respondent_id, step, step_instance, section, section_instance, question_display_order, question_instance, section_question_id, question_id, display_key, display_text, text_value, deleted, created_dt, saved_dt)
VALUES (34, 1, 1, 3, 0, 5, 0, NULL, 0, NULL, NULL, '0001-0003-0000-0005-0000-0000-0000', 'Media Preferences', NULL, false, '2026-05-07 20:42:40.965+00', NULL);

-- Q58: TEXT — DVD genres = Action
INSERT INTO survey.answers (id, survey_id, respondent_id, step, step_instance, section, section_instance, question_display_order, question_instance, section_question_id, question_id, display_key, display_text, text_value, deleted, created_dt, saved_dt)
VALUES (35, 1, 1, 3, 0, 5, 0, NULL, 0, 24, 24, '0001-0003-0000-0005-0000-0001-0000', 'Describe your favorite DVD or video genres', 'Action', false, '2026-05-07 20:42:40.974+00', '2026-05-07 20:43:11.222+00');

-- Q59: DATE_PICKER — Availability date = 2026-05-07
INSERT INTO survey.answers (id, survey_id, respondent_id, step, step_instance, section, section_instance, question_display_order, question_instance, section_question_id, question_id, display_key, display_text, text_value, deleted, created_dt, saved_dt)
VALUES (36, 1, 1, 3, 0, 5, 0, NULL, 0, 25, 25, '0001-0003-0000-0005-0000-0002-0000', 'I am available to pick up media items from this date', '2026-05-07', false, '2026-05-07 20:42:40.984+00', '2026-05-07 20:43:14.424+00');

-- Q60: DATETIME — Preferred pickup time = 2026-05-14T10:30 (exercises DATETIME type save/restore)
INSERT INTO survey.answers (id, survey_id, respondent_id, step, step_instance, section, section_instance, question_display_order, question_instance, section_question_id, question_id, display_key, display_text, text_value, deleted, created_dt, saved_dt)
VALUES (37, 1, 1, 3, 0, 5, 0, NULL, 0, 26, 26, '0001-0003-0000-0005-0000-0003-0000', 'Preferred date and time for media pickup', '2026-05-14T10:30', false, '2026-05-07 20:42:40.992+00', '2026-05-07 20:43:16.001+00');

-- Q51: TEXT — Favorite narrator = Scott Brick (shown via R25 CONTAINS 'audiobook'; triggers R26 FIELD_EXIST chain L2→L3)
INSERT INTO survey.answers (id, survey_id, respondent_id, step, step_instance, section, section_instance, question_display_order, question_instance, section_question_id, question_id, display_key, display_text, text_value, deleted, created_dt, saved_dt)
VALUES (38, 1, 1, 3, 0, 4, 0, NULL, 0, 17, 17, '0001-0003-0000-0004-0000-0003-0000', 'Favorite audiobook narrator (if any)', 'Scott Brick', false, '2026-05-07 20:42:41.695+00', '2026-05-07 20:42:49.131+00');

-- Q52: TEXT — Streaming service = Hoopla (shown via R26 FIELD_EXIST chain L3)
INSERT INTO survey.answers (id, survey_id, respondent_id, step, step_instance, section, section_instance, question_display_order, question_instance, section_question_id, question_id, display_key, display_text, text_value, deleted, created_dt, saved_dt)
VALUES (39, 1, 1, 3, 0, 4, 0, NULL, 0, 18, 18, '0001-0003-0000-0004-0000-0004-0000', 'Preferred audio streaming or download service', 'Hoopla', false, '2026-05-07 20:42:49.140+00', '2026-05-07 20:42:52.559+00');

-- Q57: TEXT — Preferred branch = Main (shown via R28 NOT_EQUAL 'never')
INSERT INTO survey.answers (id, survey_id, respondent_id, step, step_instance, section, section_instance, question_display_order, question_instance, section_question_id, question_id, display_key, display_text, text_value, deleted, created_dt, saved_dt)
VALUES (40, 1, 1, 3, 0, 4, 0, NULL, 0, 23, 23, '0001-0003-0000-0004-0000-0009-0000', 'Which branch do you visit most often?', 'Main', false, '2026-05-07 20:43:01.700+00', '2026-05-07 20:43:05.977+00');

-- ---- Step 4: CheckoutRequest / Section 7: Renewal 1 (instance 1; shown via R29 REPEAT) ----
-- Section header — Renewal 1
INSERT INTO survey.answers (id, survey_id, respondent_id, step, step_instance, section, section_instance, question_display_order, question_instance, section_question_id, question_id, display_key, display_text, text_value, deleted, created_dt, saved_dt)
VALUES (41, 1, 1, 4, 0, 7, 1, NULL, 0, NULL, NULL, '0001-0004-0000-0007-0001-0000-0000', 'Renewal 1', NULL, false, '2026-05-07 20:44:14.977+00', NULL);

-- Q67: DATE_PICKER — Renewal date = 2026-05-29
INSERT INTO survey.answers (id, survey_id, respondent_id, step, step_instance, section, section_instance, question_display_order, question_instance, section_question_id, question_id, display_key, display_text, text_value, deleted, created_dt, saved_dt)
VALUES (42, 1, 1, 4, 0, 7, 1, NULL, 0, 33, 33, '0001-0004-0000-0007-0001-0001-0000', 'Pickup date for renewal 0', '2026-05-29', false, '2026-05-07 20:44:14.997+00', '2026-05-07 20:45:02.003+00');

-- Q68: COMBOBOX — Pickup branch (text_value stored as object toString — as-is)
INSERT INTO survey.answers (id, survey_id, respondent_id, step, step_instance, section, section_instance, question_display_order, question_instance, section_question_id, question_id, display_key, display_text, text_value, deleted, created_dt, saved_dt)
VALUES (43, 1, 1, 4, 0, 7, 1, NULL, 0, 34, 34, '0001-0004-0000-0007-0001-0002-0000', 'Preferred pickup branch for this renewal', 'Main Branch', false, '2026-05-07 20:44:15.006+00', '2026-05-07 20:45:05.113+00');

-- Q69: TEXT — Alternate contact = Tim
INSERT INTO survey.answers (id, survey_id, respondent_id, step, step_instance, section, section_instance, question_display_order, question_instance, section_question_id, question_id, display_key, display_text, text_value, deleted, created_dt, saved_dt)
VALUES (44, 1, 1, 4, 0, 7, 1, NULL, 0, 35, 35, '0001-0004-0000-0007-0001-0003-0000', 'Alternate contact name for this renewal (if different from account holder)', 'Tim ', false, '2026-05-07 20:44:15.014+00', '2026-05-07 20:45:12.633+00');

-- ---- Step 4: CheckoutRequest / Section 6: Checkout 0 (base/phantom instance created by engine) ----
-- Section header — Checkout 0 (engine artifact from R31 BOOLEAN SHOW Q65→Q66)
INSERT INTO survey.answers (id, survey_id, respondent_id, step, step_instance, section, section_instance, question_display_order, question_instance, section_question_id, question_id, display_key, display_text, text_value, deleted, created_dt, saved_dt)
VALUES (45, 1, 1, 4, 0, 6, 0, NULL, 0, NULL, NULL, '0001-0004-0000-0006-0000-0000-0000', 'Checkout 0', NULL, false, '2026-05-07 20:44:18.162+00', NULL);

-- Q66: TEXTAREA — Hold instructions checkout 1 (shown via R31; no text entered)
INSERT INTO survey.answers (id, survey_id, respondent_id, step, step_instance, section, section_instance, question_display_order, question_instance, section_question_id, question_id, display_key, display_text, text_value, deleted, created_dt, saved_dt)
VALUES (46, 1, 1, 4, 0, 6, 1, NULL, 0, 32, 32, '0001-0004-0000-0006-0001-0006-0000', 'Any special instructions for this hold request', NULL, false, '2026-05-07 20:44:18.170+00', NULL);

-- Q64: TEXT — Format notes checkout 2 = MP3 (shown via R30 NOT_EQUAL 'book' since item type = audiobook)
INSERT INTO survey.answers (id, survey_id, respondent_id, step, step_instance, section, section_instance, question_display_order, question_instance, section_question_id, question_id, display_key, display_text, text_value, deleted, created_dt, saved_dt)
VALUES (47, 1, 1, 4, 0, 6, 2, NULL, 0, 30, 30, '0001-0004-0000-0006-0002-0004-0000', 'Format or edition notes (optional)', 'MP3', false, '2026-05-07 20:44:47.657+00', '2026-05-07 20:44:54.664+00');

-- Q66: TEXTAREA — Hold instructions checkout 2 (shown via R31; no text entered)
INSERT INTO survey.answers (id, survey_id, respondent_id, step, step_instance, section, section_instance, question_display_order, question_instance, section_question_id, question_id, display_key, display_text, text_value, deleted, created_dt, saved_dt)
VALUES (48, 1, 1, 4, 0, 6, 2, NULL, 0, 32, 32, '0001-0004-0000-0006-0002-0006-0000', 'Any special instructions for this hold request', NULL, false, '2026-05-07 20:44:56.250+00', NULL);

-- ============================================================
-- DEPENDENTS  (51 rows)
-- Columns: id, respondent_id, upstream_id, downstream_id, relationship_id, deleted
--
-- upstream_id / downstream_id are FKs to answers.id (1–48)
-- relationship_id is FK to relationships.id (1–17 from V005):
--   1=R18  2=R19  3=R20  4=R_em  5=R_phone  6=R21  7=R22  8=R22b  9=R23
--  10=R24 11=R25 12=R26 13=R27 14=R28 15=R29 16=R30 17=R31
-- ============================================================

-- R18: Q37 BOOLEAN SHOW → Q38 (terms checked → confirmation shown)
INSERT INTO survey.dependents (id, respondent_id, upstream_id, downstream_id, relationship_id, deleted) VALUES (1,  1, 3,  4,  1, false);

-- R19: Q37 BOOLEAN SHOW → ss_patron (terms checked → entire Patron section shown)
INSERT INTO survey.dependents (id, respondent_id, upstream_id, downstream_id, relationship_id, deleted) VALUES (2,  1, 3,  5,  2, false);
INSERT INTO survey.dependents (id, respondent_id, upstream_id, downstream_id, relationship_id, deleted) VALUES (3,  1, 3,  6,  2, false);
INSERT INTO survey.dependents (id, respondent_id, upstream_id, downstream_id, relationship_id, deleted) VALUES (4,  1, 3,  7,  2, false);
INSERT INTO survey.dependents (id, respondent_id, upstream_id, downstream_id, relationship_id, deleted) VALUES (5,  1, 3,  8,  2, false);
INSERT INTO survey.dependents (id, respondent_id, upstream_id, downstream_id, relationship_id, deleted) VALUES (6,  1, 3,  9,  2, false);
INSERT INTO survey.dependents (id, respondent_id, upstream_id, downstream_id, relationship_id, deleted) VALUES (7,  1, 3, 10,  2, false);
INSERT INTO survey.dependents (id, respondent_id, upstream_id, downstream_id, relationship_id, deleted) VALUES (8,  1, 3, 11,  2, false);
INSERT INTO survey.dependents (id, respondent_id, upstream_id, downstream_id, relationship_id, deleted) VALUES (9,  1, 3, 12,  2, false);

-- R20: Q37 BOOLEAN SHOW → ss_collprefs (terms checked → Collection Preferences section shown)
INSERT INTO survey.dependents (id, respondent_id, upstream_id, downstream_id, relationship_id, deleted) VALUES (10, 1, 3, 13,  3, false);
INSERT INTO survey.dependents (id, respondent_id, upstream_id, downstream_id, relationship_id, deleted) VALUES (11, 1, 3, 14,  3, false);
INSERT INTO survey.dependents (id, respondent_id, upstream_id, downstream_id, relationship_id, deleted) VALUES (12, 1, 3, 15,  3, false);
INSERT INTO survey.dependents (id, respondent_id, upstream_id, downstream_id, relationship_id, deleted) VALUES (13, 1, 3, 16,  3, false);
INSERT INTO survey.dependents (id, respondent_id, upstream_id, downstream_id, relationship_id, deleted) VALUES (14, 1, 3, 17,  3, false);
INSERT INTO survey.dependents (id, respondent_id, upstream_id, downstream_id, relationship_id, deleted) VALUES (15, 1, 3, 18,  3, false);

-- R21: Q45 EQUAL 'TRUE' SHOW → ss_digital (opted into digital → Digital Access section shown)
INSERT INTO survey.dependents (id, respondent_id, upstream_id, downstream_id, relationship_id, deleted) VALUES (16, 1, 12, 19,  6, false);
INSERT INTO survey.dependents (id, respondent_id, upstream_id, downstream_id, relationship_id, deleted) VALUES (17, 1, 12, 20,  6, false);
INSERT INTO survey.dependents (id, respondent_id, upstream_id, downstream_id, relationship_id, deleted) VALUES (18, 1, 12, 21,  6, false);

-- R_em: Q41 FIELD_EXIST TEXT {EMAIL} → ss_digital (email entered → {EMAIL} token substituted)
INSERT INTO survey.dependents (id, respondent_id, upstream_id, downstream_id, relationship_id, deleted) VALUES (19, 1,  8, 19,  4, false);
INSERT INTO survey.dependents (id, respondent_id, upstream_id, downstream_id, relationship_id, deleted) VALUES (20, 1,  8, 20,  4, false);
INSERT INTO survey.dependents (id, respondent_id, upstream_id, downstream_id, relationship_id, deleted) VALUES (21, 1,  8, 21,  4, false);

-- R_phone: Q43 FIELD_EXIST TEXT {PHONE} → ss_digital (phone entered → {PHONE} token substituted)
INSERT INTO survey.dependents (id, respondent_id, upstream_id, downstream_id, relationship_id, deleted) VALUES (22, 1, 10, 19,  5, false);
INSERT INTO survey.dependents (id, respondent_id, upstream_id, downstream_id, relationship_id, deleted) VALUES (23, 1, 10, 20,  5, false);
INSERT INTO survey.dependents (id, respondent_id, upstream_id, downstream_id, relationship_id, deleted) VALUES (24, 1, 10, 21,  5, false);

-- R22: Q47 BOOLEAN SHOW → Q48 (notifications opted in → email notify shown)
INSERT INTO survey.dependents (id, respondent_id, upstream_id, downstream_id, relationship_id, deleted) VALUES (25, 1, 21, 22,  7, false);

-- R22b: Q47 BOOLEAN SHOW → Q48b (notifications opted in → SMS notify shown)
INSERT INTO survey.dependents (id, respondent_id, upstream_id, downstream_id, relationship_id, deleted) VALUES (26, 1, 21, 23,  8, false);

-- R23: Q49 GREATER_THAN '0' REPEAT → ss_checkout (checkout count=2 → Checkout section repeated x2)
INSERT INTO survey.dependents (id, respondent_id, upstream_id, downstream_id, relationship_id, deleted) VALUES (27, 1, 14, 24,  9, false);
INSERT INTO survey.dependents (id, respondent_id, upstream_id, downstream_id, relationship_id, deleted) VALUES (28, 1, 14, 25,  9, false);
INSERT INTO survey.dependents (id, respondent_id, upstream_id, downstream_id, relationship_id, deleted) VALUES (29, 1, 14, 26,  9, false);
INSERT INTO survey.dependents (id, respondent_id, upstream_id, downstream_id, relationship_id, deleted) VALUES (30, 1, 14, 27,  9, false);
INSERT INTO survey.dependents (id, respondent_id, upstream_id, downstream_id, relationship_id, deleted) VALUES (31, 1, 14, 28,  9, false);
INSERT INTO survey.dependents (id, respondent_id, upstream_id, downstream_id, relationship_id, deleted) VALUES (32, 1, 14, 29,  9, false);
INSERT INTO survey.dependents (id, respondent_id, upstream_id, downstream_id, relationship_id, deleted) VALUES (33, 1, 14, 30,  9, false);
INSERT INTO survey.dependents (id, respondent_id, upstream_id, downstream_id, relationship_id, deleted) VALUES (34, 1, 14, 31,  9, false);
INSERT INTO survey.dependents (id, respondent_id, upstream_id, downstream_id, relationship_id, deleted) VALUES (35, 1, 14, 32,  9, false);
INSERT INTO survey.dependents (id, respondent_id, upstream_id, downstream_id, relationship_id, deleted) VALUES (36, 1, 14, 33,  9, false);

-- R24: Q50 CONTAINS 'dvd' SHOW → ss_mediaprefs (dvd selected → Media Preferences section shown)
INSERT INTO survey.dependents (id, respondent_id, upstream_id, downstream_id, relationship_id, deleted) VALUES (37, 1, 15, 34, 10, false);
INSERT INTO survey.dependents (id, respondent_id, upstream_id, downstream_id, relationship_id, deleted) VALUES (38, 1, 15, 35, 10, false);
INSERT INTO survey.dependents (id, respondent_id, upstream_id, downstream_id, relationship_id, deleted) VALUES (39, 1, 15, 36, 10, false);
INSERT INTO survey.dependents (id, respondent_id, upstream_id, downstream_id, relationship_id, deleted) VALUES (40, 1, 15, 37, 10, false);

-- R25: Q50 CONTAINS 'audiobook' SHOW → Q51 narrator (audiobook selected → narrator shown; chain L1→L2)
INSERT INTO survey.dependents (id, respondent_id, upstream_id, downstream_id, relationship_id, deleted) VALUES (41, 1, 15, 38, 11, false);

-- R26: Q51 FIELD_EXIST SHOW → Q52 streaming (narrator entered → streaming service shown; chain L2→L3)
INSERT INTO survey.dependents (id, respondent_id, upstream_id, downstream_id, relationship_id, deleted) VALUES (42, 1, 38, 39, 12, false);

-- R27: Q53 CONTAINS 'mystery' SHOW → Q54 mystery authors TEXTAREA
INSERT INTO survey.dependents (id, respondent_id, upstream_id, downstream_id, relationship_id, deleted) VALUES (52, 1, 16, 49, 13, false);

-- R28: Q56 NOT_EQUAL 'never' SHOW → Q57 preferred branch (daily visitor → branch shown)
INSERT INTO survey.dependents (id, respondent_id, upstream_id, downstream_id, relationship_id, deleted) VALUES (43, 1, 18, 40, 14, false);

-- R29: Q62 GREATER_THAN '0' REPEAT → ss_renewal (1 renewal needed → Renewal section x1)
INSERT INTO survey.dependents (id, respondent_id, upstream_id, downstream_id, relationship_id, deleted) VALUES (44, 1, 26, 41, 15, false);
INSERT INTO survey.dependents (id, respondent_id, upstream_id, downstream_id, relationship_id, deleted) VALUES (45, 1, 26, 42, 15, false);
INSERT INTO survey.dependents (id, respondent_id, upstream_id, downstream_id, relationship_id, deleted) VALUES (46, 1, 26, 43, 15, false);
INSERT INTO survey.dependents (id, respondent_id, upstream_id, downstream_id, relationship_id, deleted) VALUES (47, 1, 26, 44, 15, false);

-- R31: Q65 BOOLEAN SHOW → Q66 hold instructions (hold=true → instructions shown)
INSERT INTO survey.dependents (id, respondent_id, upstream_id, downstream_id, relationship_id, deleted) VALUES (48, 1, 28, 45, 17, false);
INSERT INTO survey.dependents (id, respondent_id, upstream_id, downstream_id, relationship_id, deleted) VALUES (49, 1, 28, 46, 17, false);

-- R30: Q63 NOT_EQUAL 'book' SHOW → Q64 format notes (audiobook type → format notes shown)
INSERT INTO survey.dependents (id, respondent_id, upstream_id, downstream_id, relationship_id, deleted) VALUES (50, 1, 32, 47, 16, false);

-- R31: Q65 BOOLEAN SHOW → Q66 hold instructions checkout 2 (hold=true → instructions shown)
INSERT INTO survey.dependents (id, respondent_id, upstream_id, downstream_id, relationship_id, deleted) VALUES (51, 1, 33, 48, 17, false);

-- ============================================================
-- SEQUENCE SYNC
-- Advance sequences to match inserted max IDs so that
-- subsequent application inserts do not collide.
-- ============================================================
SELECT setval('survey.respondents_seq', 1, true);
SELECT setval('survey.answers_seq',     49, true);
SELECT setval('survey.dependents_seq',  52, true);
