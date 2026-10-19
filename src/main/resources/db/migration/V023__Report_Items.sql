--
-- ***LICENSE_START***
-- Elicit Survey
-- %%
-- Copyright (C) 2025 - 2026 The Regents of the University of Michigan - Rogel Cancer Center
-- %%
-- PolyForm Noncommercial License 1.0.0
-- <https://polyformproject.org/licenses/noncommercial/1.0.0>
-- ***LICENSE_END***
--
-- V023: report marks (UC-013).
--
-- survey.report_items marks what a survey's default reports show (umbrella research plan
-- default_reports.md, section 2.1): a question, a question placement or a section placement,
-- for the RESPONDENT report (echoed back to the respondent) or the ADMINISTRATOR report
-- (monitored by a coordinator). It follows survey.metadata field for field: exactly one of the
-- three element columns is set, each a durable id (steps_sections.steps_sections_id,
-- questions.question_id, sections_questions.sections_question_id) so the mark survives a
-- revision of the element, with no foreign key because a durable id names a family of versioned
-- rows. The mark is authored content: minted in Author, carried in the .elicit file as the
-- report_items table (seven fields, like metadata) and installed by Admin's apply, which matches
-- rows by report_item_key. Survey only reads it (the built-in respondent report, a later release).
--
-- An optional label replaces the question's text on the report ("Your height" for "What is your
-- height in centimeters?"). It is translated through survey.translations like a report's name,
-- so the element_type check gains 'report_items'; the label's element key is report_item_key.
--
CREATE SEQUENCE survey.report_items_seq INCREMENT 1 START 1;
GRANT USAGE ON SEQUENCE survey.report_items_seq TO ${survey_user};

CREATE TABLE survey.report_items
(
    id                   integer                NOT NULL,
    survey_id            integer                NOT NULL,
    report_item_key      uuid                   NOT NULL DEFAULT gen_random_uuid(),
    report_type          character varying(13)  NOT NULL,
    steps_sections_id    integer,
    question_id          integer,
    sections_question_id integer,
    label                character varying(255),
    display_order        integer                NOT NULL DEFAULT 0,
    CONSTRAINT report_items_pk PRIMARY KEY (id),
    CONSTRAINT report_items_key_un UNIQUE (report_item_key),
    CONSTRAINT report_items_survey_fk FOREIGN KEY (survey_id)
        REFERENCES survey.surveys (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT report_items_type_ck CHECK (report_type IN ('RESPONDENT', 'ADMINISTRATOR')),
    CONSTRAINT report_items_element_ck CHECK (num_nonnulls(steps_sections_id, question_id, sections_question_id) = 1)
);
CREATE INDEX report_items_survey_idx ON survey.report_items USING btree (survey_id, report_type, display_order);
GRANT DELETE, INSERT, SELECT, UPDATE ON TABLE survey.report_items TO ${survey_user};

COMMENT ON TABLE survey.report_items IS
    'What a survey''s default reports show (UC-013): one element per row, for the RESPONDENT or ADMINISTRATOR report, by durable id. Authored in Author, installed by Admin''s apply, read by Survey.';
COMMENT ON COLUMN survey.report_items.label IS
    'Optional text shown on the report instead of the element''s own text; translated through survey.translations with element_type report_items.';

ALTER TABLE survey.translations
    DROP CONSTRAINT translations_element_type_ck;
ALTER TABLE survey.translations
    ADD CONSTRAINT translations_element_type_ck
        CHECK (element_type IN ('surveys','steps','sections','questions','select_items','relationships','reports','report_items'));
