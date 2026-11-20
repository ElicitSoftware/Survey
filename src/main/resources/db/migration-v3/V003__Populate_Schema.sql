-- ***LICENSE_START***
-- Elicit FHHS
-- %%
-- Copyright (C) 2025 The Regents of the University of Michigan - Rogel Cancer Center
-- %%
-- PolyForm Noncommercial License 1.0.0
-- <https://polyformproject.org/licenses/noncommercial/1.0.0>
-- ***LICENSE_END***
---

SET TIMEZONE TO 'America/Detroit';

INSERT INTO survey.action_types
VALUES (NEXTVAL('survey.action_types_seq'), 'SHOW', 'DISPLAY THE DOWNSTREAM QUESTION');
INSERT INTO survey.action_types
VALUES (NEXTVAL('survey.action_types_seq'), 'REPEAT',
        'REPEAT THE DOWNSTREAM QUESTION THE NUMBER OF TIMES OF THE UPSTREAM QUESTIONS VALUE');
INSERT INTO survey.action_types
VALUES (NEXTVAL('survey.action_types_seq'), 'TEXT', 'Replace the default text with the value of this text. ');

INSERT INTO survey.question_types
VALUES (NEXTVAL('survey.question_types_seq'), 'CHECKBOX', 'Boolean', 'BOOLEAN VALUE');
INSERT INTO survey.question_types
VALUES (NEXTVAL('survey.question_types_seq'), 'DATE_PICKER', 'Date', 'Date Picker');
INSERT INTO survey.question_types
VALUES (NEXTVAL('survey.question_types_seq'), 'COMBOBOX', 'Text', 'ComboBox');
INSERT INTO survey.question_types
VALUES (NEXTVAL('survey.question_types_seq'), 'HTML', '', 'HTML Informational');
INSERT INTO survey.question_types
VALUES (NEXTVAL('survey.question_types_seq'), 'INTEGER', 'Number', 'Integer');
INSERT INTO survey.question_types
VALUES (NEXTVAL('survey.question_types_seq'), 'DOUBLE', 'Number', 'Double');
INSERT INTO survey.question_types
VALUES (NEXTVAL('survey.question_types_seq'), 'RADIO', 'Number', 'TEXT VALUE');
INSERT INTO survey.question_types
VALUES (NEXTVAL('survey.question_types_seq'), 'TEXT', 'Text', 'TEXT VALUE');
INSERT INTO survey.question_types
VALUES (NEXTVAL('survey.question_types_seq'), 'TEXTAREA', 'Text', 'TEXT AREA VALUE');
INSERT INTO survey.question_types
VALUES (NEXTVAL('survey.question_types_seq'), 'MULTI_SELECT', 'Text', 'multi select combobox');
INSERT INTO survey.question_types
VALUES (NEXTVAL('survey.question_types_seq'), 'CHECKBOX_GROUP', 'Text', 'multi select checkboxes');
INSERT INTO survey.question_types
VALUES (NEXTVAL('survey.question_types_seq'), 'DATETIME', 'Text', 'Date Time picker');
INSERT INTO survey.question_types
VALUES (NEXTVAL('survey.question_types_seq'), 'EMAIL', 'Text', 'Email');
INSERT INTO survey.question_types
VALUES (NEXTVAL('survey.question_types_seq'), 'PASSWORD', 'Text', 'Password');

INSERT INTO survey.operator_types
VALUES (NEXTVAL('survey.operator_types_seq'), 'BOOLEAN', 'BOOLEAN VALUE (YES/NO, TRUE/FALSE, 0/1)', 'BOOLEAN');
INSERT INTO survey.operator_types
VALUES (NEXTVAL('survey.operator_types_seq'), 'GREATER THAN', 'NUMERICAL X >= Y', '>=');
INSERT INTO survey.operator_types
VALUES (NEXTVAL('survey.operator_types_seq'), 'EQUAL', 'X = Y', '=');
INSERT INTO survey.operator_types
VALUES (NEXTVAL('survey.operator_types_seq'), 'NOT_EQUAL', 'X!=Y', '!=');
INSERT INTO survey.operator_types
VALUES (NEXTVAL('survey.operator_types_seq'), 'FIELD_EXIST', 'If field presented', '');
INSERT INTO survey.operator_types
VALUES (NEXTVAL('survey.operator_types_seq'), 'CONTAINS', 'true if an array contains the value', 'In');
