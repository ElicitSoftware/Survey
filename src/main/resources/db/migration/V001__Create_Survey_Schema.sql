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
-- GREENFIELD structural schema (v3.0.0, Kimball Type 2 SCD baked in).
--
-- This is the schema every NEW installation gets from the very first
-- migration run. Existing v2.x installations instead run the historical
-- v2.x migrations followed by db/migration-v3/V010__Kimball_Type2_SCD.sql
-- (an ALTER-based upgrade) — see com.elicitsoftware.flyway.SchemaTrackFlywayCustomizer
-- for how a given database is routed to one track or the other. Both tracks
-- must converge on an identical resulting schema; see research/Kimball_type_2.md.
-- ============================================================

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
    -- Type 1 (in-place) change tracking only — surveys is a container, not a
    -- versioned structural element. See research/Kimball_type_2.md "surveys".
    published_by        text,
    published_comment   text,
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
-- select_groups — Type 2 SCD structural table (durable key: select_group_id)
--------------------------------
CREATE SEQUENCE survey.select_groups_seq INCREMENT 1 START 1;
GRANT USAGE ON SEQUENCE survey.select_groups_seq TO ${survey_user};
CREATE SEQUENCE survey.select_groups_durable_seq INCREMENT 1 START 1;
GRANT USAGE ON SEQUENCE survey.select_groups_durable_seq TO ${survey_user};
CREATE TABLE IF NOT EXISTS survey.select_groups
(
    id                 integer NOT NULL,
    survey_id          integer NOT NULL,
    name               character varying(255),
    description        character varying(255),
    data_type          CHARACTER VARYING(50) NOT NULL DEFAULT 'Text',
    select_group_id    integer NOT NULL DEFAULT nextval('survey.select_groups_durable_seq'),
    version            integer NOT NULL DEFAULT 0,
    effective_from     timestamp with time zone DEFAULT '1970-01-01 00:00:00+00',
    effective_to       timestamp with time zone DEFAULT '9999-12-31 23:59:59+00',
    is_draft           boolean NOT NULL DEFAULT false,
    published_by       text,
    published_comment  text,
    CONSTRAINT select_groups_pk PRIMARY KEY (id),
    CONSTRAINT select_groups_id_version_un UNIQUE (select_group_id, version)
);
-- Business-key uniqueness is scoped to the currently-active row only — a
-- retired version and its successor legitimately share (survey_id, name).
CREATE UNIQUE INDEX select_groups_name_un
    ON survey.select_groups (survey_id, name)
    WHERE effective_to = '9999-12-31 23:59:59+00';
CREATE UNIQUE INDEX select_groups_one_current_un
    ON survey.select_groups (select_group_id)
    WHERE effective_to = '9999-12-31 23:59:59+00';
CREATE UNIQUE INDEX select_groups_one_draft_un
    ON survey.select_groups (select_group_id)
    WHERE is_draft = true;
CREATE INDEX select_groups_durable_id_idx ON survey.select_groups (select_group_id);
CREATE INDEX select_groups_active_range_idx ON survey.select_groups (survey_id, effective_from, effective_to);
GRANT DELETE, UPDATE, INSERT, SELECT ON TABLE survey.select_groups TO ${survey_user};
--------------------------------
-- select_items — Type 2 SCD structural table (durable key: select_item_id)
--------------------------------
CREATE SEQUENCE survey.select_items_seq INCREMENT 1 START 1;
GRANT USAGE ON SEQUENCE survey.select_items_seq TO ${survey_user};
CREATE SEQUENCE survey.select_items_durable_seq INCREMENT 1 START 1;
GRANT USAGE ON SEQUENCE survey.select_items_durable_seq TO ${survey_user};
CREATE TABLE IF NOT EXISTS survey.select_items
(
    id                   integer NOT NULL,
    survey_id            integer NOT NULL,
    -- Named select_group_id from the start (the v2.x->v3 upgrade track renames
    -- this from "group_id" via an ALTER; a fresh install never carries the old name).
    select_group_id      integer NOT NULL,
    select_group_version integer NOT NULL DEFAULT 0,
    display_text         character varying(255),
    display_order        integer NOT NULL,
    coded_value          character varying(255),
    select_item_id        integer NOT NULL DEFAULT nextval('survey.select_items_durable_seq'),
    version               integer NOT NULL DEFAULT 0,
    effective_from        timestamp with time zone DEFAULT '1970-01-01 00:00:00+00',
    effective_to          timestamp with time zone DEFAULT '9999-12-31 23:59:59+00',
    is_draft              boolean NOT NULL DEFAULT false,
    published_by          text,
    published_comment     text,
    CONSTRAINT select_items_pk PRIMARY KEY (id),
    CONSTRAINT select_items_id_version_un UNIQUE (select_item_id, version),
    -- version = 0: entity-existence check only; the correct current version of
    -- select_groups is resolved at query time via the time-range predicate.
    CONSTRAINT select_items_select_group_version_ck CHECK (select_group_version = 0),
    CONSTRAINT select_items_group_fk FOREIGN KEY (select_group_id, select_group_version)
        REFERENCES survey.select_groups (select_group_id, version)
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
);
CREATE UNIQUE INDEX select_items_display_text_un
    ON survey.select_items (select_group_id, display_text)
    WHERE effective_to = '9999-12-31 23:59:59+00';
CREATE UNIQUE INDEX select_items_one_current_un
    ON survey.select_items (select_item_id)
    WHERE effective_to = '9999-12-31 23:59:59+00';
CREATE UNIQUE INDEX select_items_one_draft_un
    ON survey.select_items (select_item_id)
    WHERE is_draft = true;
CREATE INDEX select_items_durable_id_idx ON survey.select_items (select_item_id);
CREATE INDEX select_items_select_group_id_idx ON survey.select_items (select_group_id);
CREATE INDEX select_items_active_range_idx ON survey.select_items (survey_id, effective_from, effective_to);
GRANT DELETE, UPDATE, INSERT, SELECT ON TABLE survey.select_items TO ${survey_user};
--------------------------------
-- steps — Type 2 SCD structural table (durable key: step_id)
--------------------------------
CREATE SEQUENCE survey.steps_seq INCREMENT 1 START 1;
GRANT USAGE ON SEQUENCE survey.steps_seq TO ${survey_user};
CREATE SEQUENCE survey.steps_durable_seq INCREMENT 1 START 1;
GRANT USAGE ON SEQUENCE survey.steps_durable_seq TO ${survey_user};
CREATE TABLE IF NOT EXISTS survey.steps
(
    id             integer NOT NULL,
    survey_id      integer NOT NULL,
    -- NUMERIC (not integer) to support decimal midpoint insertion when adding
    -- a new step between two existing ones without renumbering.
    display_order  NUMERIC NOT NULL,
    name           character varying(255),
    dimension_name CHARACTER VARYING(50) NOT NULL,
    description    character varying(255),
    step_id            integer NOT NULL DEFAULT nextval('survey.steps_durable_seq'),
    version            integer NOT NULL DEFAULT 0,
    effective_from     timestamp with time zone DEFAULT '1970-01-01 00:00:00+00',
    effective_to       timestamp with time zone DEFAULT '9999-12-31 23:59:59+00',
    is_draft           boolean NOT NULL DEFAULT false,
    published_by       text,
    published_comment  text,
    CONSTRAINT steps_pk PRIMARY KEY (id),
    CONSTRAINT steps_id_version_un UNIQUE (step_id, version)
);
CREATE UNIQUE INDEX steps_survey_name_un
    ON survey.steps (survey_id, name)
    WHERE effective_to = '9999-12-31 23:59:59+00';
CREATE UNIQUE INDEX steps_survey_display_order
    ON survey.steps (survey_id, display_order)
    WHERE effective_to = '9999-12-31 23:59:59+00';
CREATE UNIQUE INDEX steps_one_current_un
    ON survey.steps (step_id)
    WHERE effective_to = '9999-12-31 23:59:59+00';
CREATE UNIQUE INDEX steps_one_draft_un
    ON survey.steps (step_id)
    WHERE is_draft = true;
CREATE INDEX steps_durable_id_idx ON survey.steps (step_id);
CREATE INDEX steps_active_range_idx ON survey.steps (survey_id, effective_from, effective_to);
GRANT DELETE, UPDATE, INSERT, SELECT ON TABLE survey.steps TO ${survey_user};
--------------------------------
-- sections — Type 2 SCD structural table (durable key: section_id)
--------------------------------
CREATE SEQUENCE survey.sections_seq INCREMENT 1 START 1;
GRANT USAGE ON SEQUENCE survey.sections_seq TO ${survey_user};
CREATE SEQUENCE survey.sections_durable_seq INCREMENT 1 START 1;
GRANT USAGE ON SEQUENCE survey.sections_durable_seq TO ${survey_user};
CREATE TABLE IF NOT EXISTS survey.sections
(
    id             integer NOT NULL,
    survey_id      integer NOT NULL,
    display_order  NUMERIC NOT NULL,
    name           character varying(255),
    dimension_name CHARACTER VARYING(50) NOT NULL,
    description    character varying(255),
    section_id         integer NOT NULL DEFAULT nextval('survey.sections_durable_seq'),
    version            integer NOT NULL DEFAULT 0,
    effective_from     timestamp with time zone DEFAULT '1970-01-01 00:00:00+00',
    effective_to       timestamp with time zone DEFAULT '9999-12-31 23:59:59+00',
    is_draft           boolean NOT NULL DEFAULT false,
    published_by       text,
    published_comment  text,
    CONSTRAINT sections_pk PRIMARY KEY (id),
    CONSTRAINT sections_id_version_un UNIQUE (section_id, version)
);
CREATE UNIQUE INDEX sections_survey_order_un
    ON survey.sections (survey_id, display_order)
    WHERE effective_to = '9999-12-31 23:59:59+00';
CREATE UNIQUE INDEX sections_one_current_un
    ON survey.sections (section_id)
    WHERE effective_to = '9999-12-31 23:59:59+00';
CREATE UNIQUE INDEX sections_one_draft_un
    ON survey.sections (section_id)
    WHERE is_draft = true;
CREATE INDEX sections_durable_id_idx ON survey.sections (section_id);
CREATE INDEX sections_active_range_idx ON survey.sections (survey_id, effective_from, effective_to);
GRANT DELETE, UPDATE, INSERT, SELECT ON TABLE survey.sections TO ${survey_user};
--------------------------------
-- steps_sections — join table; Type 2 SCD (durable key: steps_sections_id),
-- step_id/section_id are durable references (not surrogate ids)
--------------------------------
CREATE SEQUENCE survey.steps_sections_seq INCREMENT 1 START 1;
GRANT USAGE ON SEQUENCE survey.steps_sections_seq TO ${survey_user};
CREATE SEQUENCE survey.steps_sections_durable_seq INCREMENT 1 START 1;
GRANT USAGE ON SEQUENCE survey.steps_sections_durable_seq TO ${survey_user};
CREATE TABLE IF NOT EXISTS survey.steps_sections
(
    id                    integer               NOT NULL,
    survey_id             integer               NOT NULL,
    step_id               integer               NOT NULL,
    step_version          integer               NOT NULL DEFAULT 0,
    step_display_order    NUMERIC               NOT NULL,
    section_id            integer               NOT NULL,
    section_version       integer               NOT NULL DEFAULT 0,
    section_display_order NUMERIC               NOT NULL,
    display_key           character varying(34) NOT NULL,
    steps_sections_id      integer NOT NULL DEFAULT nextval('survey.steps_sections_durable_seq'),
    version                integer NOT NULL DEFAULT 0,
    effective_from         timestamp with time zone DEFAULT '1970-01-01 00:00:00+00',
    effective_to           timestamp with time zone DEFAULT '9999-12-31 23:59:59+00',
    is_draft               boolean NOT NULL DEFAULT false,
    published_by           text,
    published_comment      text,
    CONSTRAINT steps_sections_pk PRIMARY KEY (id),
    CONSTRAINT steps_sections_id_version_un UNIQUE (steps_sections_id, version),
    -- version = 0: entity-existence check only; the correct current version of
    -- the referenced step/section is resolved at query time via the time-range predicate.
    CONSTRAINT steps_sections_ref_versions_ck CHECK (step_version = 0 AND section_version = 0),
    CONSTRAINT steps_sections_fk FOREIGN KEY (section_id, section_version)
        REFERENCES survey.sections (section_id, version)
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT steps_sections_steps_fk FOREIGN KEY (step_id, step_version)
        REFERENCES survey.steps (step_id, version)
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT steps_sections_survey_fk FOREIGN KEY (survey_id)
        REFERENCES survey.surveys (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
);
CREATE UNIQUE INDEX steps_sections_un
    ON survey.steps_sections (survey_id, display_key)
    WHERE effective_to = '9999-12-31 23:59:59+00';
CREATE UNIQUE INDEX steps_sections_one_current_un
    ON survey.steps_sections (steps_sections_id)
    WHERE effective_to = '9999-12-31 23:59:59+00';
CREATE UNIQUE INDEX steps_sections_one_draft_un
    ON survey.steps_sections (steps_sections_id)
    WHERE is_draft = true;
CREATE INDEX IF NOT EXISTS steps_sections_survey_index ON survey.steps_sections USING btree (survey_id ASC NULLS LAST);
CREATE INDEX steps_sections_durable_id_idx ON survey.steps_sections (steps_sections_id);
CREATE INDEX steps_sections_active_range_idx ON survey.steps_sections (survey_id, effective_from, effective_to);
GRANT DELETE, INSERT, SELECT, UPDATE ON TABLE survey.steps_sections TO ${survey_user};
--------------------------------
-- questions — Type 2 SCD structural table (durable key: question_id)
--------------------------------
CREATE SEQUENCE survey.questions_seq INCREMENT 1 START 1;
GRANT USAGE ON SEQUENCE survey.questions_seq TO ${survey_user};
CREATE SEQUENCE survey.questions_durable_seq INCREMENT 1 START 1;
GRANT USAGE ON SEQUENCE survey.questions_durable_seq TO ${survey_user};
CREATE TABLE IF NOT EXISTS survey.questions
(
    id                    integer  NOT NULL,
    survey_id             integer NOT NULL,
    type_id               integer  NOT NULL,
    text                  character varying(8000) COLLATE pg_catalog."default" NOT NULL,
    short_text            character varying(100) COLLATE pg_catalog."default",
    tool_tip              character varying(255) COLLATE pg_catalog."default",
    required              boolean NOT NULL DEFAULT false,
    min_value             integer,
    max_value             integer,
    validation_text       character varying(255) COLLATE pg_catalog."default",
    select_group_id       integer,
    select_group_version  integer NOT NULL DEFAULT 0,
    mask                  character varying(255) COLLATE pg_catalog."default",
    placeholder           character varying(255) COLLATE pg_catalog."default",
    default_value         character varying(255) COLLATE pg_catalog."default",
    variant               character varying(255) COLLATE pg_catalog."default",
    question_id           integer NOT NULL DEFAULT nextval('survey.questions_durable_seq'),
    version               integer NOT NULL DEFAULT 0,
    effective_from        timestamp with time zone DEFAULT '1970-01-01 00:00:00+00',
    effective_to          timestamp with time zone DEFAULT '9999-12-31 23:59:59+00',
    is_draft              boolean NOT NULL DEFAULT false,
    published_by          text,
    published_comment     text,
    CONSTRAINT questions_pk PRIMARY KEY (id),
    CONSTRAINT questions_id_version_un UNIQUE (question_id, version),
    CONSTRAINT questions_select_group_version_ck CHECK (select_group_version = 0),
    CONSTRAINT select_groups_fk FOREIGN KEY (select_group_id, select_group_version)
        REFERENCES survey.select_groups (select_group_id, version)
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT type_fk FOREIGN KEY (type_id)
        REFERENCES survey.question_types (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
);
CREATE UNIQUE INDEX questions_one_current_un
    ON survey.questions (question_id)
    WHERE effective_to = '9999-12-31 23:59:59+00';
CREATE UNIQUE INDEX questions_one_draft_un
    ON survey.questions (question_id)
    WHERE is_draft = true;
CREATE INDEX questions_durable_id_idx ON survey.questions (question_id);
CREATE INDEX questions_active_range_idx ON survey.questions (survey_id, effective_from, effective_to);
GRANT DELETE, UPDATE, INSERT, SELECT ON TABLE survey.questions TO ${survey_user};
--------------------------------
-- sections_questions — join table; Type 2 SCD (durable key: sections_question_id),
-- question_id/section_id are durable references (not surrogate ids)
--------------------------------
CREATE SEQUENCE survey.sections_questions_seq INCREMENT 1 START 1;
GRANT USAGE ON SEQUENCE survey.sections_questions_seq TO ${survey_user};
CREATE SEQUENCE survey.sections_questions_durable_seq INCREMENT 1 START 1;
GRANT USAGE ON SEQUENCE survey.sections_questions_durable_seq TO ${survey_user};
CREATE TABLE IF NOT EXISTS survey.sections_questions
(
    id                   integer NOT NULL,
    survey_id            integer NOT NULL,
    question_id          integer NOT NULL,
    question_version     integer NOT NULL DEFAULT 0,
    section_id           integer NOT NULL,
    section_version      integer NOT NULL DEFAULT 0,
    display_order        NUMERIC NOT NULL,
    sections_question_id  integer NOT NULL DEFAULT nextval('survey.sections_questions_durable_seq'),
    version               integer NOT NULL DEFAULT 0,
    effective_from        timestamp with time zone DEFAULT '1970-01-01 00:00:00+00',
    effective_to          timestamp with time zone DEFAULT '9999-12-31 23:59:59+00',
    is_draft              boolean NOT NULL DEFAULT false,
    published_by          text,
    published_comment     text,
    CONSTRAINT sections_questions_pk PRIMARY KEY (id),
    CONSTRAINT sections_questions_id_version_un UNIQUE (sections_question_id, version),
    CONSTRAINT sections_questions_ref_versions_ck CHECK (question_version = 0 AND section_version = 0),
    CONSTRAINT sections_questions_question_fk FOREIGN KEY (question_id, question_version)
        REFERENCES survey.questions (question_id, version)
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT sections_questions_sections_fk FOREIGN KEY (section_id, section_version)
        REFERENCES survey.sections (section_id, version)
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT sections_questions_survey_fk FOREIGN KEY (survey_id)
        REFERENCES survey.surveys (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
);
CREATE UNIQUE INDEX sections_questions_un
    ON survey.sections_questions (survey_id, question_id, section_id, display_order)
    WHERE effective_to = '9999-12-31 23:59:59+00';
CREATE UNIQUE INDEX sections_questions_one_current_un
    ON survey.sections_questions (sections_question_id)
    WHERE effective_to = '9999-12-31 23:59:59+00';
CREATE UNIQUE INDEX sections_questions_one_draft_un
    ON survey.sections_questions (sections_question_id)
    WHERE is_draft = true;
CREATE INDEX IF NOT EXISTS sections_questions_survey_index ON survey.sections_questions USING btree (survey_id ASC NULLS LAST);
CREATE INDEX sections_questions_durable_id_idx ON survey.sections_questions (sections_question_id);
CREATE INDEX sections_questions_active_range_idx ON survey.sections_questions (survey_id, effective_from, effective_to);
GRANT DELETE, INSERT, SELECT, UPDATE ON TABLE survey.sections_questions TO ${survey_user};
--------------------------------
-- relationships — Type 2 SCD structural table (durable key: relationship_id);
-- all five cross-references are durable (not surrogate ids).
-- Column renamed from the v2.x "downstream_s_id" to "downstream_ss_id"
-- (ss = steps_sections convention) from the start.
--------------------------------
CREATE SEQUENCE survey.relationships_seq INCREMENT 1 START 1;
GRANT USAGE ON SEQUENCE survey.relationships_seq TO ${survey_user};
CREATE SEQUENCE survey.relationships_durable_seq INCREMENT 1 START 1;
GRANT USAGE ON SEQUENCE survey.relationships_durable_seq TO ${survey_user};
CREATE TABLE IF NOT EXISTS survey.relationships
(
    id                      integer NOT NULL,
    survey_id               integer NOT NULL,
    upstream_step_id        integer,
    upstream_step_version   integer NOT NULL DEFAULT 0,
    upstream_sq_id          integer NOT NULL,
    upstream_sq_version     integer NOT NULL DEFAULT 0,
    downstream_step_id      integer,
    downstream_step_version integer NOT NULL DEFAULT 0,
    downstream_ss_id        integer,
    downstream_ss_version   integer NOT NULL DEFAULT 0,
    downstream_sq_id        integer,
    downstream_sq_version   integer NOT NULL DEFAULT 0,
    operator_id             integer NOT NULL,
    action_id               integer NOT NULL,
    description             character varying(255),
    token                   character varying(10),
    reference_value         character varying(255),
    default_upstream_value  character varying(255),
    override_upstream_value character varying(255),
    relationship_id         integer NOT NULL DEFAULT nextval('survey.relationships_durable_seq'),
    version                 integer NOT NULL DEFAULT 0,
    effective_from          timestamp with time zone DEFAULT '1970-01-01 00:00:00+00',
    effective_to            timestamp with time zone DEFAULT '9999-12-31 23:59:59+00',
    is_draft                boolean NOT NULL DEFAULT false,
    published_by            text,
    published_comment       text,
    CONSTRAINT relationships_pk PRIMARY KEY (id),
    CONSTRAINT relationships_id_version_un UNIQUE (relationship_id, version),
    CONSTRAINT relationships_ref_versions_ck CHECK (
        upstream_step_version   = 0 AND
        upstream_sq_version     = 0 AND
        downstream_step_version = 0 AND
        downstream_ss_version   = 0 AND
        downstream_sq_version   = 0
    ),
    CONSTRAINT action_fk FOREIGN KEY (action_id)
        REFERENCES survey.action_types (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT downstream_s_fk FOREIGN KEY (downstream_ss_id, downstream_ss_version)
        REFERENCES survey.steps_sections (steps_sections_id, version)
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT downstream_sq_fk FOREIGN KEY (downstream_sq_id, downstream_sq_version)
        REFERENCES survey.sections_questions (sections_question_id, version)
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT downstream_step_fk FOREIGN KEY (downstream_step_id, downstream_step_version)
        REFERENCES survey.steps (step_id, version)
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
    CONSTRAINT upstream_sq_fk FOREIGN KEY (upstream_sq_id, upstream_sq_version)
        REFERENCES survey.sections_questions (sections_question_id, version)
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT upstream_step_fk FOREIGN KEY (upstream_step_id, upstream_step_version)
        REFERENCES survey.steps (step_id, version)
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT downstream_ck CHECK ((downstream_step_id + downstream_sq_id + downstream_ss_id) > 0)
);
CREATE UNIQUE INDEX relationships_one_current_un
    ON survey.relationships (relationship_id)
    WHERE effective_to = '9999-12-31 23:59:59+00';
CREATE UNIQUE INDEX relationships_one_draft_un
    ON survey.relationships (relationship_id)
    WHERE is_draft = true;
CREATE INDEX IF NOT EXISTS relationships_downstream_section_index ON survey.relationships USING btree (downstream_ss_id ASC NULLS LAST);
CREATE INDEX IF NOT EXISTS relationships_downstream_sq_index ON survey.relationships USING btree (downstream_sq_id ASC NULLS LAST);
CREATE INDEX IF NOT EXISTS relationships_downstream_step_index ON survey.relationships USING btree (downstream_step_id ASC NULLS LAST);
CREATE INDEX IF NOT EXISTS relationships_survey_index ON survey.relationships USING btree (survey_id ASC NULLS LAST);
CREATE INDEX IF NOT EXISTS relationships_upstream_sq_index ON survey.relationships USING btree (upstream_sq_id ASC NULLS LAST);
CREATE INDEX IF NOT EXISTS relationships_upstream_step_index ON survey.relationships USING btree (upstream_step_id ASC NULLS LAST);
CREATE INDEX relationships_durable_id_idx ON survey.relationships (relationship_id);
CREATE INDEX relationships_active_range_idx ON survey.relationships (survey_id, effective_from, effective_to);
GRANT DELETE, INSERT, SELECT, UPDATE ON TABLE survey.relationships TO ${survey_user};
--------------------------------
CREATE SEQUENCE survey.answers_seq INCREMENT 1 START 1;
GRANT USAGE ON SEQUENCE survey.answers_seq TO ${survey_user};
CREATE TABLE IF NOT EXISTS survey.answers
(
    id                     integer                 NOT NULL,
    survey_id              integer                 NOT NULL,
    respondent_id          integer                 NOT NULL,
    -- NUMERIC (not integer) to match steps.display_order/sections.display_order,
    -- which support decimal midpoint insertion.
    step                   NUMERIC                 NOT NULL DEFAULT 0,
    step_instance          integer                 NOT NULL DEFAULT 0,
    section                NUMERIC,
    section_instance       integer                 NOT NULL DEFAULT 0,
    question_display_order integer,
    question_instance      integer                 NOT NULL DEFAULT 0,
    section_question_id    integer,
    question_id            integer,
    -- Pins the exact questions.version the respondent saw at answer time,
    -- independent of any later reword. See research/Kimball_type_2.md Gap ETL-2.
    question_version       integer                 NOT NULL DEFAULT 0,
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
GRANT USAGE ON SEQUENCE survey.dimensions_seq TO ${survey_user};
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
-- metadata — bridge table to the ontology/reporting chain. NOT versioned itself
-- (research/Kimball_type_2.md "The metadata Table" / "Rule 1"): its three element
-- columns are durable integer references from the start, so one metadata row
-- automatically covers every version of the referenced entity. Deliberately no
-- FK constraint on these three columns back to the structural tables — a plain
-- durable-id column isn't a unique FK target the way (durable_id, version) is,
-- and the doc's own migration drops the old surrogate FKs without replacing them
-- (indexes only).
--------------------------------
CREATE SEQUENCE survey.metadata_seq INCREMENT 1 START 1;
GRANT USAGE ON SEQUENCE survey.metadata_seq TO ${survey_user};
CREATE TABLE IF NOT EXISTS survey.metadata
(
    id                   integer NOT NULL,
    survey_id            integer NOT NULL,
    steps_sections_id    integer,
    question_id          integer,
    sections_question_id integer,
    ontology_id          integer NOT NULL,
    value                character varying(255),
    CONSTRAINT metadata_pk PRIMARY KEY (id),
    CONSTRAINT metadata_un UNIQUE (steps_sections_id, question_id, sections_question_id, ontology_id, value),
    CONSTRAINT metadata_ontology_fk FOREIGN KEY (ontology_id)
        REFERENCES survey.ontology (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT metadata_survey_fk FOREIGN KEY (survey_id)
        REFERENCES survey.surveys (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT metadata_element_ck CHECK ((steps_sections_id + question_id + sections_question_id) > 0)
);
CREATE INDEX IF NOT EXISTS metadata_ontology_index ON survey.metadata USING btree (ontology_id ASC NULLS LAST);
CREATE INDEX metadata_question_id_index ON survey.metadata USING btree (question_id ASC NULLS LAST);
CREATE INDEX metadata_sections_question_id_idx ON survey.metadata USING btree (sections_question_id ASC NULLS LAST);
CREATE INDEX metadata_steps_sections_id_idx ON survey.metadata USING btree (steps_sections_id ASC NULLS LAST);
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
    tries                 integer       NOT NULL DEFAULT 0,
    status 					varchar(255) NOT NULL,
    error_msg				varchar(255),
    created_dt              timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    uploaded_dt				timestamp with time zone,
    CONSTRAINT respondent_psa_pk PRIMARY KEY (id),
    CONSTRAINT respondent_psa_respondent_fk FOREIGN KEY (post_survey_action_id) REFERENCES survey.post_survey_actions (id),
    CONSTRAINT respondent_psa_psa_fk FOREIGN KEY (respondent_id) REFERENCES survey.respondents (id),
    CONSTRAINT respondent_psa_un UNIQUE (respondent_id, post_survey_action_id)
);
GRANT DELETE, INSERT, SELECT, UPDATE ON survey.respondent_psa TO ${survey_user};
