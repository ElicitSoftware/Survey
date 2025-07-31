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
--------------------------------
-- Survey schema
--------------------------------
CREATE SEQUENCE survey.surveys_seq INCREMENT 1 START 1;
GRANT USAGE ON SEQUENCE survey.surveys_seq TO ${survey_user};
GRANT USAGE ON SEQUENCE survey.surveys_seq TO ${surveyadmin_user};
CREATE TABLE IF NOT EXISTS survey.surveys
(
    id                  integer                NOT NULL,
    name                character varying(255) NOT NULL,
    display_order       integer                NOT NULL,
    title               character varying(255) NOT NULL,
    description         character varying(2000),
    initial_display_key character varying(255),
    post_survey_url     character varying(255),
    CONSTRAINT surveys_pk PRIMARY KEY (id),
    CONSTRAINT surveys_name_un UNIQUE (name),
    CONSTRAINT surveys_display_order_un UNIQUE (display_order)
);
GRANT DELETE, UPDATE, INSERT, SELECT ON TABLE survey.surveys TO ${survey_user};
GRANT DELETE, UPDATE, INSERT, SELECT ON TABLE survey.surveys TO ${surveyadmin_user};
--------------------------------
CREATE SEQUENCE survey.respondents_seq INCREMENT 1 START 1;
GRANT USAGE ON SEQUENCE survey.respondents_seq TO ${survey_user};
CREATE TABLE IF NOT EXISTS survey.respondents
(
    id              integer                  NOT NULL,
    survey_id       integer                  NOT NULL,
    token           character varying(255)   NOT NULL,
    active          boolean                  NOT NULL DEFAULT true,
    logins          integer                  NOT NULL DEFAULT 0,
    created_dt      timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    first_access_dt timestamp with time zone,
    finalized_dt    timestamp with time zone,
    CONSTRAINT respondents_pk PRIMARY KEY (id),
    CONSTRAINT respondents_token_un UNIQUE (survey_id, token),
    CONSTRAINT respondents_surveys_fk FOREIGN KEY (survey_id)
        REFERENCES survey.surveys (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
);
CREATE INDEX IF NOT EXISTS respondents_survey_index ON survey.respondents USING btree (survey_id ASC NULLS LAST);
GRANT DELETE, UPDATE, INSERT, SELECT ON TABLE survey.respondents TO ${survey_user};
--------------------------------
CREATE SEQUENCE survey.question_types_seq INCREMENT 1 START 1;
GRANT USAGE ON SEQUENCE survey.question_types_seq TO ${survey_user};
CREATE TABLE IF NOT EXISTS survey.question_types
(
    id          integer NOT NULL,
    name        character varying(255),
    data_type   character varying(255),
    description character varying(255),
    CONSTRAINT question_types_pk PRIMARY KEY (id),
    CONSTRAINT question_types_name_un UNIQUE (name)
);
GRANT DELETE, UPDATE, INSERT, SELECT ON TABLE survey.question_types TO ${survey_user};
--------------------------------
CREATE SEQUENCE survey.operator_types_seq INCREMENT 1 START 1;
GRANT USAGE ON SEQUENCE survey.operator_types_seq TO ${survey_user};
CREATE TABLE IF NOT EXISTS survey.operator_types
(
    id          integer NOT NULL,
    name        character varying(255),
    description character varying(255),
    symbol      character varying(10),
    CONSTRAINT operator_types_pk PRIMARY KEY (id),
    CONSTRAINT operator_types_name_un UNIQUE (name)
);
GRANT DELETE, UPDATE, INSERT, SELECT ON TABLE survey.operator_types TO ${survey_user};
--------------------------------
CREATE SEQUENCE survey.action_types_seq INCREMENT 1 START 1;
GRANT USAGE ON SEQUENCE survey.action_types_seq TO ${survey_user};
CREATE TABLE IF NOT EXISTS survey.action_types
(
    id          integer NOT NULL,
    name        character varying(255),
    description character varying(255),
    CONSTRAINT action_types_pk PRIMARY KEY (id),
    CONSTRAINT action_types_name_un UNIQUE (name)
);
GRANT DELETE, UPDATE, INSERT, SELECT ON TABLE survey.action_types TO ${survey_user};
--------------------------------
CREATE SEQUENCE survey.select_groups_seq INCREMENT 1 START 1;
GRANT USAGE ON SEQUENCE survey.select_groups_seq TO ${survey_user};
CREATE TABLE IF NOT EXISTS survey.select_groups
(
    id          integer NOT NULL,
    survey_id   integer NOT NULL,
    name        character varying(255),
    description character varying(255),
    data_type CHARACTER VARYING(50) NOT NULL DEFAULT 'Text',
    CONSTRAINT select_groups_pk PRIMARY KEY (id),
    CONSTRAINT select_groups_name_un UNIQUE (survey_id, name)
);
GRANT DELETE, UPDATE, INSERT, SELECT ON TABLE survey.select_groups TO ${survey_user};
--------------------------------
CREATE SEQUENCE survey.select_items_seq INCREMENT 1 START 1;
GRANT USAGE ON SEQUENCE survey.select_items_seq TO ${survey_user};
CREATE TABLE IF NOT EXISTS survey.select_items
(
    id            integer NOT NULL,
    survey_id     integer NOT NULL,
    group_id      integer NOT NULL,
    display_text  character varying(255),
    display_order integer NOT NULL,
    coded_value   character varying(255),
    CONSTRAINT select_items_pk PRIMARY KEY (id),
    CONSTRAINT select_items_display_text_un UNIQUE (group_id, display_text),
    CONSTRAINT select_items_group_fk FOREIGN KEY (group_id)
        REFERENCES survey.select_groups (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
);
GRANT DELETE, UPDATE, INSERT, SELECT ON TABLE survey.select_items TO ${survey_user};
--------------------------------
CREATE SEQUENCE survey.steps_seq INCREMENT 1 START 1;
GRANT USAGE ON SEQUENCE survey.steps_seq TO ${survey_user};
CREATE TABLE IF NOT EXISTS survey.steps
(
    id            integer NOT NULL,
    survey_id     integer NOT NULL,
    display_order integer NOT NULL,
    name          character varying(255),
    dimension_name CHARACTER VARYING(50) NOT NULL,
    description   character varying(255),
    CONSTRAINT steps_pk PRIMARY KEY (id),
    CONSTRAINT steps_survey_name_un UNIQUE (survey_id, name),
    CONSTRAINT steps_survey_display_order UNIQUE (survey_id, display_order)
);
GRANT DELETE, UPDATE, INSERT, SELECT ON TABLE survey.steps TO ${survey_user};
--------------------------------
CREATE SEQUENCE survey.sections_seq INCREMENT 1 START 1;
GRANT USAGE ON SEQUENCE survey.sections_seq TO ${survey_user};
CREATE TABLE IF NOT EXISTS survey.sections
(
    id            integer NOT NULL,
    survey_id     integer NOT NULL,
    display_order integer NOT NULL,
    name          character varying(255),
    dimension_name CHARACTER VARYING(50) NOT NULL,
    description   character varying(255),
    CONSTRAINT sections_pk PRIMARY KEY (id),
    CONSTRAINT sections_survey_order_un UNIQUE (survey_id, display_order)
);
GRANT DELETE, UPDATE, INSERT, SELECT ON TABLE survey.sections TO ${survey_user};
--------------------------------
CREATE SEQUENCE survey.steps_sections_seq INCREMENT 1 START 1;
GRANT USAGE ON SEQUENCE survey.steps_sections_seq TO ${survey_user};
CREATE TABLE IF NOT EXISTS survey.steps_sections
(
    id                    integer               NOT NULL,
    survey_id             integer               NOT NULL,
    step_id               integer               NOT NULL,
    step_display_order    integer               NOT NULL,
    section_id            integer               NOT NULL,
    section_display_order integer               NOT NULL,
    display_key           character varying(34) NOT NULL,
    CONSTRAINT steps_sections_pk PRIMARY KEY (id),
    CONSTRAINT steps_sections_un UNIQUE (survey_id, display_key),
    CONSTRAINT steps_sections_fk FOREIGN KEY (section_id)
        REFERENCES survey.sections (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT steps_sections_steps_fk FOREIGN KEY (step_id)
        REFERENCES survey.steps (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT steps_sections_survey_fk FOREIGN KEY (survey_id)
        REFERENCES survey.surveys (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
);
CREATE INDEX IF NOT EXISTS steps_sections_survey_index ON survey.steps_sections USING btree (survey_id ASC NULLS LAST);
GRANT DELETE, INSERT, SELECT, UPDATE ON TABLE survey.steps_sections TO ${survey_user};
--------------------------------
CREATE SEQUENCE survey.questions_seq INCREMENT 1 START 1;
GRANT USAGE ON SEQUENCE survey.questions_seq TO ${survey_user};
CREATE TABLE IF NOT EXISTS survey.questions
(
    id              integer  NOT NULL,
    survey_id       integer NOT NULL,
    type_id         integer  NOT NULL,
    text            character varying(8000) COLLATE pg_catalog."default" NOT NULL,
    short_text      character varying(100) COLLATE pg_catalog."default",
    tool_tip        character varying(255) COLLATE pg_catalog."default",
    required        boolean NOT NULL DEFAULT false,
    min_value       integer,
    max_value       integer,
    validation_text character varying(255) COLLATE pg_catalog."default",
    select_group_id integer,
    mask            character varying(255) COLLATE pg_catalog."default",
    placeholder     character varying(255) COLLATE pg_catalog."default",
    default_value   character varying(255) COLLATE pg_catalog."default",
    variant         character varying(255) COLLATE pg_catalog."default",
    CONSTRAINT questions_pk PRIMARY KEY (id),
    CONSTRAINT select_groups_fk FOREIGN KEY (select_group_id)
        REFERENCES survey.select_groups (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT type_fk FOREIGN KEY (type_id)
        REFERENCES survey.question_types (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
);
GRANT DELETE, UPDATE, INSERT, SELECT ON TABLE survey.questions TO ${survey_user};
--------------------------------
CREATE SEQUENCE survey.sections_questions_seq INCREMENT 1 START 1;
GRANT USAGE ON SEQUENCE survey.sections_questions_seq TO ${survey_user};
CREATE TABLE IF NOT EXISTS survey.sections_questions
(
    id            integer NOT NULL,
    survey_id     integer NOT NULL,
    question_id   integer NOT NULL,
    section_id    integer NOT NULL,
    display_order integer NOT NULL,
    CONSTRAINT sections_questions_pk PRIMARY KEY (id),
    CONSTRAINT sections_questions_un UNIQUE (survey_id, question_id, section_id, display_order),
    CONSTRAINT sections_questions_question_fk FOREIGN KEY (question_id)
        REFERENCES survey.questions (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT sections_questions_sections_fk FOREIGN KEY (section_id)
        REFERENCES survey.sections (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT sections_questions_survey_fk FOREIGN KEY (survey_id)
        REFERENCES survey.surveys (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
);
CREATE INDEX IF NOT EXISTS sections_questions_survey_index ON survey.sections_questions USING btree (survey_id ASC NULLS LAST);
GRANT DELETE, INSERT, SELECT, UPDATE ON TABLE survey.sections_questions TO ${survey_user};
--------------------------------
CREATE SEQUENCE survey.relationships_seq INCREMENT 1 START 1;
GRANT USAGE ON SEQUENCE survey.relationships_seq TO ${survey_user};
CREATE TABLE IF NOT EXISTS survey.relationships
(
    id                      integer NOT NULL,
    survey_id               integer NOT NULL,
    upstream_step_id        integer,
    upstream_sq_id          integer NOT NULL,
    downstream_step_id      integer,
    downstream_s_id         integer,
    downstream_sq_id        integer,
    operator_id             integer NOT NULL,
    action_id               integer NOT NULL,
    description             character varying(255),
    token                   character varying(10),
    reference_value         character varying(255),
    default_upstream_value  character varying(255),
    override_upstream_value character varying(255),
    CONSTRAINT relationships_pk PRIMARY KEY (id),
    CONSTRAINT action_fk FOREIGN KEY (action_id)
        REFERENCES survey.action_types (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT downstream_s_fk FOREIGN KEY (downstream_s_id)
        REFERENCES survey.steps_sections (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT downstream_sq_fk FOREIGN KEY (downstream_sq_id)
        REFERENCES survey.sections_questions (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT downstream_step_fk FOREIGN KEY (downstream_step_id)
        REFERENCES survey.steps (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT operator_fk FOREIGN KEY (operator_id)
        REFERENCES survey.operator_types (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT relationships_survey_fk FOREIGN KEY (survey_id)
        REFERENCES survey.surveys (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT upstream_sq_fk FOREIGN KEY (upstream_sq_id)
        REFERENCES survey.sections_questions (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT upstream_step_fk FOREIGN KEY (upstream_step_id)
        REFERENCES survey.steps (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT downstream_ck CHECK ((downstream_step_id + downstream_sq_id + downstream_s_id) > 0)
);
CREATE INDEX IF NOT EXISTS relationships_downstream_section_index ON survey.relationships USING btree (downstream_s_id ASC NULLS LAST);
CREATE INDEX IF NOT EXISTS relationships_downstream_sq_index ON survey.relationships USING btree (downstream_sq_id ASC NULLS LAST);
CREATE INDEX IF NOT EXISTS relationships_downstream_step_index ON survey.relationships USING btree (downstream_step_id ASC NULLS LAST);
CREATE INDEX IF NOT EXISTS relationships_survey_index ON survey.relationships USING btree (survey_id ASC NULLS LAST);
CREATE INDEX IF NOT EXISTS relationships_upstream_sq_index ON survey.relationships USING btree (upstream_sq_id ASC NULLS LAST);
CREATE INDEX IF NOT EXISTS relationships_upstream_step_index ON survey.relationships USING btree (upstream_step_id ASC NULLS LAST);
GRANT DELETE, INSERT, SELECT, UPDATE ON TABLE survey.relationships TO ${survey_user};
--------------------------------
CREATE SEQUENCE survey.answers_seq INCREMENT 1 START 1;
GRANT USAGE ON SEQUENCE survey.answers_seq TO ${survey_user};
CREATE TABLE IF NOT EXISTS survey.answers
(
    id                     integer                 NOT NULL,
    survey_id              integer                 NOT NULL,
    respondent_id          integer                 NOT NULL,
    step                   integer                 NOT NULL DEFAULT 0,
    step_instance          integer                 NOT NULL DEFAULT 0,
    section                integer,
    section_instance       integer                 NOT NULL DEFAULT 0,
    question_display_order integer,
    question_instance      integer                 NOT NULL DEFAULT 0,
    section_question_id    integer,
    question_id            integer,
    display_key            character varying(34)   NOT NULL,
    display_text           character varying(8000) NOT NULL,
    text_value             character varying(255),
    deleted                boolean                 NOT NULL DEFAULT false,
    created_dt             timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    saved_dt               timestamp with time zone,
    CONSTRAINT answers_pk PRIMARY KEY (id),
    CONSTRAINT answers_un UNIQUE (respondent_id, display_key),
    CONSTRAINT answers_questions_fk FOREIGN KEY (question_id)
        REFERENCES survey.questions (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT answers_respondent_fk FOREIGN KEY (respondent_id)
        REFERENCES survey.respondents (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT answers_section_fk FOREIGN KEY (section)
        REFERENCES survey.sections (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT answers_step_fk FOREIGN KEY (step)
        REFERENCES survey.steps (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT answers_survey_fk FOREIGN KEY (survey_id)
        REFERENCES survey.surveys (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT section_question_id_fk FOREIGN KEY (section_question_id)
        REFERENCES survey.sections_questions (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
);
CREATE INDEX IF NOT EXISTS answers_display_key_index ON survey.answers USING btree (display_key ASC NULLS LAST);
CREATE INDEX IF NOT EXISTS answers_respondent_index ON survey.answers USING btree (respondent_id ASC NULLS LAST);
CREATE INDEX IF NOT EXISTS answers_survey_index ON survey.answers USING btree (survey_id ASC NULLS LAST);
GRANT DELETE, INSERT, SELECT, UPDATE ON TABLE survey.answers TO ${survey_user};
--------------------------------
CREATE SEQUENCE survey.dependents_seq INCREMENT 1 START 1;
GRANT USAGE ON SEQUENCE survey.dependents_seq TO ${survey_user};
CREATE TABLE IF NOT EXISTS survey.dependents
(
    id              integer NOT NULL,
    respondent_id   integer NOT NULL,
    upstream_id     integer NOT NULL,
    downstream_id   integer NOT NULL,
    relationship_id integer NOT NULL,
    deleted         boolean NOT NULL DEFAULT false,
    CONSTRAINT dependents_pk PRIMARY KEY (id),
    CONSTRAINT dependents_un UNIQUE (respondent_id, upstream_id, downstream_id, relationship_id),
    CONSTRAINT dependents_downstream_fk FOREIGN KEY (downstream_id)
        REFERENCES survey.answers (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT dependents_relationships_fk FOREIGN KEY (relationship_id)
        REFERENCES survey.relationships (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT dependents_respondents_fk FOREIGN KEY (respondent_id)
        REFERENCES survey.respondents (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT dependents_upstream_fk FOREIGN KEY (upstream_id)
        REFERENCES survey.answers (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
);
CREATE INDEX IF NOT EXISTS dependents_downstream_index ON survey.dependents USING btree (downstream_id ASC NULLS LAST);
CREATE INDEX IF NOT EXISTS dependents_relationship_index ON survey.dependents USING btree (relationship_id ASC NULLS LAST);
CREATE INDEX IF NOT EXISTS dependents_respondent_index ON survey.dependents USING btree (respondent_id ASC NULLS LAST);
CREATE INDEX IF NOT EXISTS dependents_upstream_index ON survey.dependents USING btree (upstream_id ASC NULLS LAST);
GRANT DELETE, INSERT, SELECT, UPDATE ON TABLE survey.dependents TO ${survey_user};
--------------------------------
CREATE SEQUENCE survey.dimensions_seq INCREMENT 1 START 1;
CREATE TABLE survey.dimensions
(
    id INTEGER NOT NULL DEFAULT NEXTVAL('survey.dimensions_seq'),
    name CHARACTER VARYING(50),
    CONSTRAINT dimensions_pk PRIMARY KEY(id),
    CONSTRAINT dimensions_un UNIQUE (name)
);
CREATE INDEX IF NOT EXISTS dimensions_name_index ON survey.dimensions USING btree (name ASC NULLS LAST);
--------------------------------
CREATE SEQUENCE survey.ontology_seq INCREMENT 1 START 1;
GRANT USAGE ON SEQUENCE survey.ontology_seq TO ${survey_user};
GRANT DELETE, INSERT, SELECT, UPDATE ON TABLE survey.dimensions TO ${survey_user};

CREATE TABLE IF NOT EXISTS survey.ontology
(
    id        integer                NOT NULL,
    survey_id integer                NOT NULL,
    name      character varying(255) NOT NULL,
    tag       character varying(255) NOT NULL,
    dimension INTEGER,
    CONSTRAINT ontology_pk PRIMARY KEY (id),
    CONSTRAINT ontology_dimensions_fk FOREIGN KEY (dimension) REFERENCES survey.dimensions(id),
    CONSTRAINT ontology_un UNIQUE (name, tag)
);
CREATE INDEX IF NOT EXISTS ontology_name_index ON survey.ontology USING btree (name ASC NULLS LAST);
CREATE INDEX IF NOT EXISTS ontology_tag_index ON survey.ontology USING btree (tag ASC NULLS LAST);
CREATE INDEX IF NOT EXISTS ontology_dimension_index ON survey.ontology USING btree (dimension ASC NULLS LAST);
GRANT DELETE, UPDATE, INSERT, SELECT ON TABLE survey.ontology TO ${survey_user};
--------------------------------
CREATE SEQUENCE survey.metadata_seq INCREMENT 1 START 1;
GRANT USAGE ON SEQUENCE survey.metadata_seq TO ${survey_user};
CREATE TABLE IF NOT EXISTS survey.metadata
(
    id                  integer NOT NULL,
    survey_id           integer NOT NULL,
    step_section_id     integer,
    question_id         integer,
    section_question_id integer,
    ontology_id         integer NOT NULL,
    value               character varying(255),
    CONSTRAINT metadata_pk PRIMARY KEY (id),
    CONSTRAINT metadata_un UNIQUE (step_section_id, question_id, section_question_id, ontology_id, value),
    CONSTRAINT metadata_ontology_fk FOREIGN KEY (ontology_id)
        REFERENCES survey.ontology (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT metadata_question_fk FOREIGN KEY (question_id)
        REFERENCES survey.questions (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT metadata_sect_quest_fk FOREIGN KEY (section_question_id)
        REFERENCES survey.sections_questions (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT metadata_section_fk FOREIGN KEY (step_section_id)
        REFERENCES survey.steps_sections (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT metadata_survey_fk FOREIGN KEY (survey_id)
        REFERENCES survey.surveys (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT metadata_element_ck CHECK ((step_section_id + question_id + section_question_id) > 0)
);
CREATE INDEX IF NOT EXISTS metadata_ontology_index ON survey.metadata USING btree (ontology_id ASC NULLS LAST);
CREATE INDEX IF NOT EXISTS metadata_question_index ON survey.metadata USING btree (question_id ASC NULLS LAST);
CREATE INDEX IF NOT EXISTS metadata_section_question_index ON survey.metadata USING btree (section_question_id ASC NULLS LAST);
CREATE INDEX IF NOT EXISTS metadata_step_section_index ON survey.metadata USING btree (step_section_id ASC NULLS LAST);
GRANT DELETE, INSERT, SELECT, UPDATE ON TABLE survey.metadata TO ${survey_user};
--------------------------------
CREATE SEQUENCE survey.reports_seq INCREMENT 1 START 1;
GRANT USAGE ON SEQUENCE survey.reports_seq TO ${survey_user};
CREATE TABLE IF NOT EXISTS survey.reports
(
    id            integer                NOT NULL,
    survey_id     integer                NOT NULL,
    name          character varying(255) NOT NULL,
    description   character varying(255) NOT NULL,
    url           character varying(255),
    display_order integer                NOT NULL,
    CONSTRAINT reports_pk PRIMARY KEY (id),
    CONSTRAINT reports_un UNIQUE (survey_id, name),
    CONSTRAINT reports_survey_fk FOREIGN KEY (survey_id)
        REFERENCES survey.surveys (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
);
CREATE INDEX reports_survey_index ON survey.reports USING btree (survey_id ASC NULLS LAST);
GRANT DELETE, UPDATE, INSERT, SELECT ON TABLE survey.reports TO ${survey_user};
--------------------------------
CREATE SEQUENCE survey.post_survey_actions_seq INCREMENT 1 START 1;
GRANT USAGE ON SEQUENCE survey.post_survey_actions_seq TO ${survey_user};
CREATE TABLE survey.post_survey_actions
(
    id              integer       NOT NULL,
    survey_id       integer       NOT NULL,
    name            varchar(255) NOT NULL,
    description     varchar(255) NOT NULL,
    URL             varchar(255),
    execution_order integer       NOT NULL,
    CONSTRAINT post_survey_actions_pk PRIMARY KEY (id),
    CONSTRAINT post_survey_actions_survey_fk FOREIGN KEY (survey_id) REFERENCES survey.surveys (id),
    CONSTRAINT post_survey_actions_un UNIQUE (survey_id, name)
);
GRANT DELETE, INSERT, SELECT, UPDATE ON survey.post_survey_actions TO ${survey_user};
--------------------------------
CREATE SEQUENCE survey.respondent_psa_seq INCREMENT 1 START 1;
GRANT USAGE ON SEQUENCE survey.respondent_psa_seq TO ${survey_user};
CREATE TABLE survey.respondent_psa
(
    id                      integer       NOT NULL,
    respondent_id           integer       NOT NULL,
    post_survey_action_id   integer       NOT NULL,
    status 					varchar(255) NOT NULL,
    error_msg				varchar(255),
    created_dt              timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_dt				timestamp with time zone,
    CONSTRAINT respondent_psa_pk PRIMARY KEY (id),
    CONSTRAINT respondent_psa_respondent_fk FOREIGN KEY (post_survey_action_id) REFERENCES survey.post_survey_actions (id),
    CONSTRAINT respondent_psa_psa_fk FOREIGN KEY (respondent_id) REFERENCES survey.respondents (id),
    CONSTRAINT respondent_psa_un UNIQUE (respondent_id, post_survey_action_id)
);
GRANT DELETE, INSERT, SELECT, UPDATE ON survey.respondent_psa TO ${survey_user};