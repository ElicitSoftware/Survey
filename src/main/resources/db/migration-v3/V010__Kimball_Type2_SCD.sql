-- ***LICENSE_START***
-- Elicit Survey
-- %%
-- Copyright (C) 2025 The Regents of the University of Michigan - Rogel Cancer Center
-- %%
-- PolyForm Noncommercial License 1.0.0
-- <https://polyformproject.org/licenses/noncommercial/1.0.0>
-- ***LICENSE_END***
---

-- ================================================================================================
-- Kimball Type 2 Slowly Changing Dimensions for Survey Versioning — UPGRADE path.
--
-- ALTER-based migration for existing v2.x databases (the "brownfield" track — see
-- com.elicitsoftware.flyway.SchemaTrackFlywayCustomizer, which routes an existing database
-- into this src/main/resources/db/migration-v3/ location instead of the greenfield
-- src/main/resources/db/migration/ root). Implements research/Kimball_type_2.md's
-- "Schema Changes Per Table" section against the REAL constraint names created by
-- V001__Create_Survey_Schema.sql in this same folder (the doc's own DDL says "adjust name
-- to match actual constraint" everywhere — this file is that adjustment).
--
-- The greenfield track (db/migration/V001__Create_Survey_Schema.sql /
-- V002__Create_Reporting_Schema.sql) creates the identical end-state schema directly via
-- CREATE TABLE, with no ALTERs. Both tracks must converge on the same schema — see the
-- scd/** spec suite (src/test/java/com/elicitsoftware/scd/) for the shared acceptance test.
--
-- Old surrogate FK columns are intentionally NOT dropped here — see the doc's Open Question 1;
-- that drop is a separate, later migration gated behind a staging verification checklist.
-- ================================================================================================

--------------------------------
-- 0. Durable-key sequences
--------------------------------

-- Pre-existing gap in V001__Create_Survey_Schema.sql (frozen history, cannot be fixed there):
-- every other sequence gets GRANT USAGE except this one. Harmless to add here since a new
-- migration can't retroactively edit an already-applied one.
GRANT USAGE ON SEQUENCE survey.dimensions_seq TO ${survey_user};

CREATE SEQUENCE survey.questions_durable_seq;
GRANT USAGE ON SEQUENCE survey.questions_durable_seq TO ${survey_user};
CREATE SEQUENCE survey.sections_durable_seq;
GRANT USAGE ON SEQUENCE survey.sections_durable_seq TO ${survey_user};
CREATE SEQUENCE survey.steps_durable_seq;
GRANT USAGE ON SEQUENCE survey.steps_durable_seq TO ${survey_user};
CREATE SEQUENCE survey.select_groups_durable_seq;
GRANT USAGE ON SEQUENCE survey.select_groups_durable_seq TO ${survey_user};
CREATE SEQUENCE survey.select_items_durable_seq;
GRANT USAGE ON SEQUENCE survey.select_items_durable_seq TO ${survey_user};
CREATE SEQUENCE survey.steps_sections_durable_seq;
GRANT USAGE ON SEQUENCE survey.steps_sections_durable_seq TO ${survey_user};
CREATE SEQUENCE survey.sections_questions_durable_seq;
GRANT USAGE ON SEQUENCE survey.sections_questions_durable_seq TO ${survey_user};
CREATE SEQUENCE survey.relationships_durable_seq;
GRANT USAGE ON SEQUENCE survey.relationships_durable_seq TO ${survey_user};

--------------------------------
-- 1. questions — Type 2 columns
--------------------------------
ALTER TABLE survey.questions
    ADD COLUMN question_id      integer NOT NULL DEFAULT nextval('survey.questions_durable_seq'),
    ADD COLUMN version          integer NOT NULL DEFAULT 0,
    ADD COLUMN effective_from   timestamp with time zone DEFAULT '1970-01-01 00:00:00+00',
    ADD COLUMN effective_to     timestamp with time zone DEFAULT '9999-12-31 23:59:59+00',
    ADD COLUMN published_by     text,
    ADD COLUMN published_comment text,
    ADD COLUMN is_draft         boolean NOT NULL DEFAULT false;

CREATE UNIQUE INDEX questions_one_current_un
    ON survey.questions (question_id)
    WHERE effective_to = '9999-12-31 23:59:59+00';

CREATE UNIQUE INDEX questions_one_draft_un
    ON survey.questions (question_id)
    WHERE is_draft = true;

ALTER TABLE survey.questions ADD CONSTRAINT questions_id_version_un UNIQUE (question_id, version);

CREATE INDEX questions_durable_id_idx ON survey.questions (question_id);
CREATE INDEX questions_active_range_idx ON survey.questions (survey_id, effective_from, effective_to);

--------------------------------
-- 2. select_groups — Type 2 columns
--------------------------------
ALTER TABLE survey.select_groups
    ADD COLUMN select_group_id  integer NOT NULL DEFAULT nextval('survey.select_groups_durable_seq'),
    ADD COLUMN version          integer NOT NULL DEFAULT 0,
    ADD COLUMN effective_from   timestamp with time zone DEFAULT '1970-01-01 00:00:00+00',
    ADD COLUMN effective_to     timestamp with time zone DEFAULT '9999-12-31 23:59:59+00',
    ADD COLUMN published_by     text,
    ADD COLUMN published_comment text,
    ADD COLUMN is_draft         boolean NOT NULL DEFAULT false;

CREATE UNIQUE INDEX select_groups_one_current_un
    ON survey.select_groups (select_group_id)
    WHERE effective_to = '9999-12-31 23:59:59+00';

CREATE UNIQUE INDEX select_groups_one_draft_un
    ON survey.select_groups (select_group_id)
    WHERE is_draft = true;

ALTER TABLE survey.select_groups ADD CONSTRAINT select_groups_id_version_un UNIQUE (select_group_id, version);

CREATE INDEX select_groups_durable_id_idx ON survey.select_groups (select_group_id);
CREATE INDEX select_groups_active_range_idx ON survey.select_groups (survey_id, effective_from, effective_to);

-- Business-key uniqueness must be scoped to the currently-active row only — a
-- retired version and its successor legitimately share (survey_id, name).
ALTER TABLE survey.select_groups DROP CONSTRAINT select_groups_name_un;
CREATE UNIQUE INDEX select_groups_name_un
    ON survey.select_groups (survey_id, name)
    WHERE effective_to = '9999-12-31 23:59:59+00';

--------------------------------
-- 3. select_items — Type 2 columns + durable retarget of group_id -> select_group_id
--------------------------------
ALTER TABLE survey.select_items
    ADD COLUMN select_item_id   integer NOT NULL DEFAULT nextval('survey.select_items_durable_seq'),
    ADD COLUMN version          integer NOT NULL DEFAULT 0,
    ADD COLUMN effective_from   timestamp with time zone DEFAULT '1970-01-01 00:00:00+00',
    ADD COLUMN effective_to     timestamp with time zone DEFAULT '9999-12-31 23:59:59+00',
    ADD COLUMN published_by     text,
    ADD COLUMN published_comment text,
    ADD COLUMN is_draft         boolean NOT NULL DEFAULT false;

-- Drop the old surrogate FK before the rename/backfill below overwrites group_id's values.
ALTER TABLE survey.select_items DROP CONSTRAINT select_items_group_fk;

-- Also drop select_items_display_text_un BEFORE the backfill below, not after: it's a live
-- UNIQUE constraint on (select_group_id, display_text), checked row-by-row as the backfill
-- UPDATE runs. On real data, writing one row's new durable select_group_id can transiently
-- collide with another not-yet-updated row's still-surrogate value (e.g. the same display_text
-- "Yes"/"No" reused across different select_groups, with overlapping id ranges) even though
-- the final, fully-backfilled state has no true duplicates. Confirmed against a real
-- pre-Kimball database — dropping first avoids a spurious mid-statement constraint violation.
ALTER TABLE survey.select_items DROP CONSTRAINT select_items_display_text_un;

ALTER TABLE survey.select_items RENAME COLUMN group_id TO select_group_id;

-- Backfill: replace old surrogate id values with durable select_group_id values.
UPDATE survey.select_items si
   SET select_group_id = sg.select_group_id
  FROM survey.select_groups sg
 WHERE sg.id = si.select_group_id;

ALTER TABLE survey.select_items
    ADD COLUMN select_group_version integer NOT NULL DEFAULT 0;

ALTER TABLE survey.select_items
    ADD CONSTRAINT select_items_select_group_version_ck CHECK (select_group_version = 0);

-- version = 0: entity-existence check. Time-range join resolves current version per respondent.
-- Constraint keeps its original v2.x name (select_items_group_fk) — only the FK
-- target changed (surrogate -> durable composite), matching the greenfield track.
ALTER TABLE survey.select_items
    ADD CONSTRAINT select_items_group_fk
    FOREIGN KEY (select_group_id, select_group_version) REFERENCES survey.select_groups (select_group_id, version);

CREATE UNIQUE INDEX select_items_one_current_un
    ON survey.select_items (select_item_id)
    WHERE effective_to = '9999-12-31 23:59:59+00';

CREATE UNIQUE INDEX select_items_one_draft_un
    ON survey.select_items (select_item_id)
    WHERE is_draft = true;

ALTER TABLE survey.select_items ADD CONSTRAINT select_items_id_version_un UNIQUE (select_item_id, version);

CREATE INDEX select_items_durable_id_idx ON survey.select_items (select_item_id);
CREATE INDEX select_items_select_group_id_idx ON survey.select_items (select_group_id);
CREATE INDEX select_items_active_range_idx ON survey.select_items (survey_id, effective_from, effective_to);

-- Business-key uniqueness scoped to the currently-active row only (see select_groups
-- above for the same reasoning). select_items_display_text_un was already dropped
-- before the backfill above (see the comment there); only the partial-index recreation
-- happens here.
CREATE UNIQUE INDEX select_items_display_text_un
    ON survey.select_items (select_group_id, display_text)
    WHERE effective_to = '9999-12-31 23:59:59+00';

--------------------------------
-- 4. sections — Type 2 columns
--------------------------------
ALTER TABLE survey.sections
    ADD COLUMN section_id      integer NOT NULL DEFAULT nextval('survey.sections_durable_seq'),
    ADD COLUMN version         integer NOT NULL DEFAULT 0,
    ADD COLUMN effective_from  timestamp with time zone DEFAULT '1970-01-01 00:00:00+00',
    ADD COLUMN effective_to    timestamp with time zone DEFAULT '9999-12-31 23:59:59+00',
    ADD COLUMN published_by    text,
    ADD COLUMN published_comment text,
    ADD COLUMN is_draft        boolean NOT NULL DEFAULT false;

CREATE UNIQUE INDEX sections_one_current_un
    ON survey.sections (section_id)
    WHERE effective_to = '9999-12-31 23:59:59+00';

CREATE UNIQUE INDEX sections_one_draft_un
    ON survey.sections (section_id)
    WHERE is_draft = true;

ALTER TABLE survey.sections ADD CONSTRAINT sections_id_version_un UNIQUE (section_id, version);

CREATE INDEX sections_durable_id_idx ON survey.sections (section_id);
CREATE INDEX sections_active_range_idx ON survey.sections (survey_id, effective_from, effective_to);

-- Business-key uniqueness scoped to the currently-active row only (see select_groups above).
ALTER TABLE survey.sections DROP CONSTRAINT sections_survey_order_un;
CREATE UNIQUE INDEX sections_survey_order_un
    ON survey.sections (survey_id, display_order)
    WHERE effective_to = '9999-12-31 23:59:59+00';

ALTER TABLE survey.sections ALTER COLUMN display_order TYPE NUMERIC;

--------------------------------
-- 5. steps — Type 2 columns
--------------------------------
ALTER TABLE survey.steps
    ADD COLUMN step_id         integer NOT NULL DEFAULT nextval('survey.steps_durable_seq'),
    ADD COLUMN version         integer NOT NULL DEFAULT 0,
    ADD COLUMN effective_from  timestamp with time zone DEFAULT '1970-01-01 00:00:00+00',
    ADD COLUMN effective_to    timestamp with time zone DEFAULT '9999-12-31 23:59:59+00',
    ADD COLUMN published_by    text,
    ADD COLUMN published_comment text,
    ADD COLUMN is_draft        boolean NOT NULL DEFAULT false;

CREATE UNIQUE INDEX steps_one_current_un
    ON survey.steps (step_id)
    WHERE effective_to = '9999-12-31 23:59:59+00';

CREATE UNIQUE INDEX steps_one_draft_un
    ON survey.steps (step_id)
    WHERE is_draft = true;

ALTER TABLE survey.steps ADD CONSTRAINT steps_id_version_un UNIQUE (step_id, version);

CREATE INDEX steps_durable_id_idx ON survey.steps (step_id);
CREATE INDEX steps_active_range_idx ON survey.steps (survey_id, effective_from, effective_to);

-- Business-key uniqueness scoped to the currently-active row only (see select_groups above).
ALTER TABLE survey.steps DROP CONSTRAINT steps_survey_name_un;
CREATE UNIQUE INDEX steps_survey_name_un
    ON survey.steps (survey_id, name)
    WHERE effective_to = '9999-12-31 23:59:59+00';

ALTER TABLE survey.steps DROP CONSTRAINT steps_survey_display_order;
CREATE UNIQUE INDEX steps_survey_display_order
    ON survey.steps (survey_id, display_order)
    WHERE effective_to = '9999-12-31 23:59:59+00';

ALTER TABLE survey.steps ALTER COLUMN display_order TYPE NUMERIC;

--------------------------------
-- 6. questions.select_group_id — durable retarget (run after step 2 has populated
--    select_groups.select_group_id)
--------------------------------
ALTER TABLE survey.questions DROP CONSTRAINT select_groups_fk;

ALTER TABLE survey.questions
    ADD COLUMN select_group_version integer NOT NULL DEFAULT 0;

ALTER TABLE survey.questions
    ADD CONSTRAINT questions_select_group_version_ck CHECK (select_group_version = 0);

UPDATE survey.questions q
   SET select_group_id = sg.select_group_id
  FROM survey.select_groups sg
 WHERE sg.id = q.select_group_id;

-- version = 0: entity-existence check. Time-range join resolves current version per respondent.
-- Constraint keeps its original v2.x name (select_groups_fk) — only the FK target
-- changed (surrogate -> durable composite), matching the greenfield track.
ALTER TABLE survey.questions
    ADD CONSTRAINT select_groups_fk
    FOREIGN KEY (select_group_id, select_group_version) REFERENCES survey.select_groups (select_group_id, version);

--------------------------------
-- 7. steps_sections — Type 2 columns + durable retarget of step_id/section_id
--------------------------------
ALTER TABLE survey.steps_sections
    ADD COLUMN steps_sections_id integer NOT NULL DEFAULT nextval('survey.steps_sections_durable_seq'),
    ADD COLUMN version           integer NOT NULL DEFAULT 0,
    ADD COLUMN effective_from    timestamp with time zone DEFAULT '1970-01-01 00:00:00+00',
    ADD COLUMN effective_to      timestamp with time zone DEFAULT '9999-12-31 23:59:59+00',
    ADD COLUMN published_by      text,
    ADD COLUMN published_comment text,
    ADD COLUMN is_draft          boolean NOT NULL DEFAULT false,
    ADD COLUMN step_version      integer NOT NULL DEFAULT 0,
    ADD COLUMN section_version   integer NOT NULL DEFAULT 0;

ALTER TABLE survey.steps_sections
    ADD CONSTRAINT steps_sections_ref_versions_ck CHECK (step_version = 0 AND section_version = 0);

-- Drop old surrogate FKs before backfilling step_id/section_id with durable ids.
ALTER TABLE survey.steps_sections DROP CONSTRAINT steps_sections_steps_fk;
ALTER TABLE survey.steps_sections DROP CONSTRAINT steps_sections_fk;

UPDATE survey.steps_sections ss
   SET step_id = s.step_id
  FROM survey.steps s
 WHERE s.id = ss.step_id;

UPDATE survey.steps_sections ss
   SET section_id = sec.section_id
  FROM survey.sections sec
 WHERE sec.id = ss.section_id;

-- version = 0: entity-existence check. Time-range join resolves current version per respondent.
ALTER TABLE survey.steps_sections
    ADD CONSTRAINT steps_sections_steps_fk
    FOREIGN KEY (step_id, step_version) REFERENCES survey.steps (step_id, version);

ALTER TABLE survey.steps_sections
    ADD CONSTRAINT steps_sections_fk
    FOREIGN KEY (section_id, section_version) REFERENCES survey.sections (section_id, version);

CREATE UNIQUE INDEX steps_sections_one_current_un
    ON survey.steps_sections (steps_sections_id)
    WHERE effective_to = '9999-12-31 23:59:59+00';

CREATE UNIQUE INDEX steps_sections_one_draft_un
    ON survey.steps_sections (steps_sections_id)
    WHERE is_draft = true;

ALTER TABLE survey.steps_sections ADD CONSTRAINT steps_sections_id_version_un UNIQUE (steps_sections_id, version);

CREATE INDEX steps_sections_durable_id_idx ON survey.steps_sections (steps_sections_id);
CREATE INDEX steps_sections_active_range_idx ON survey.steps_sections (survey_id, effective_from, effective_to);

-- Business-key uniqueness scoped to the currently-active row only (see select_groups above).
ALTER TABLE survey.steps_sections DROP CONSTRAINT steps_sections_un;
CREATE UNIQUE INDEX steps_sections_un
    ON survey.steps_sections (survey_id, display_key)
    WHERE effective_to = '9999-12-31 23:59:59+00';

-- steps_sections has no single "display_order" column — it carries the parent
-- step's/section's display order as two denormalized columns; both must convert.
ALTER TABLE survey.steps_sections ALTER COLUMN step_display_order TYPE NUMERIC;
ALTER TABLE survey.steps_sections ALTER COLUMN section_display_order TYPE NUMERIC;

--------------------------------
-- 8. sections_questions — Type 2 columns + durable retarget of question_id/section_id
--------------------------------
ALTER TABLE survey.sections_questions
    ADD COLUMN sections_question_id integer NOT NULL DEFAULT nextval('survey.sections_questions_durable_seq'),
    ADD COLUMN version              integer NOT NULL DEFAULT 0,
    ADD COLUMN effective_from       timestamp with time zone DEFAULT '1970-01-01 00:00:00+00',
    ADD COLUMN effective_to         timestamp with time zone DEFAULT '9999-12-31 23:59:59+00',
    ADD COLUMN published_by        text,
    ADD COLUMN published_comment   text,
    ADD COLUMN is_draft            boolean NOT NULL DEFAULT false,
    ADD COLUMN question_version    integer NOT NULL DEFAULT 0,
    ADD COLUMN section_version     integer NOT NULL DEFAULT 0;

ALTER TABLE survey.sections_questions
    ADD CONSTRAINT sections_questions_ref_versions_ck CHECK (question_version = 0 AND section_version = 0);

-- Drop old surrogate FKs before backfilling section_id/question_id with durable ids.
ALTER TABLE survey.sections_questions DROP CONSTRAINT sections_questions_sections_fk;
ALTER TABLE survey.sections_questions DROP CONSTRAINT sections_questions_question_fk;

-- Also drop sections_questions_un BEFORE the backfill below, not after: it's a live UNIQUE
-- constraint on (survey_id, question_id, section_id, display_order), checked row-by-row as
-- each backfill UPDATE runs. On real data, writing one row's new durable id can transiently
-- collide with another not-yet-updated row's still-surrogate value even though the final,
-- fully-backfilled state has no true duplicates. Confirmed against a real pre-Kimball
-- database — dropping first avoids a spurious mid-statement constraint violation.
ALTER TABLE survey.sections_questions DROP CONSTRAINT sections_questions_un;

UPDATE survey.sections_questions sq
   SET section_id = sec.section_id
  FROM survey.sections sec
 WHERE sec.id = sq.section_id;

UPDATE survey.sections_questions sq
   SET question_id = q.question_id
  FROM survey.questions q
 WHERE q.id = sq.question_id;

-- version = 0: entity-existence check. Time-range join resolves current version per respondent.
ALTER TABLE survey.sections_questions
    ADD CONSTRAINT sections_questions_sections_fk
    FOREIGN KEY (section_id, section_version) REFERENCES survey.sections (section_id, version);

ALTER TABLE survey.sections_questions
    ADD CONSTRAINT sections_questions_question_fk
    FOREIGN KEY (question_id, question_version) REFERENCES survey.questions (question_id, version);

CREATE UNIQUE INDEX sections_questions_one_current_un
    ON survey.sections_questions (sections_question_id)
    WHERE effective_to = '9999-12-31 23:59:59+00';

CREATE UNIQUE INDEX sections_questions_one_draft_un
    ON survey.sections_questions (sections_question_id)
    WHERE is_draft = true;

ALTER TABLE survey.sections_questions ADD CONSTRAINT sections_questions_id_version_un UNIQUE (sections_question_id, version);

CREATE INDEX sections_questions_durable_id_idx ON survey.sections_questions (sections_question_id);
CREATE INDEX sections_questions_active_range_idx ON survey.sections_questions (survey_id, effective_from, effective_to);

-- Business-key uniqueness scoped to the currently-active row only (see select_groups above).
-- sections_questions_un was already dropped before the backfill above (see the comment there);
-- only the partial-index recreation happens here.
CREATE UNIQUE INDEX sections_questions_un
    ON survey.sections_questions (survey_id, question_id, section_id, display_order)
    WHERE effective_to = '9999-12-31 23:59:59+00';

ALTER TABLE survey.sections_questions ALTER COLUMN display_order TYPE NUMERIC;

--------------------------------
-- 9. relationships — Type 2 columns + rename downstream_s_id -> downstream_ss_id +
--    durable retarget of all five reference columns
--------------------------------
ALTER TABLE survey.relationships
    ADD COLUMN relationship_id         integer NOT NULL DEFAULT nextval('survey.relationships_durable_seq'),
    ADD COLUMN version                 integer NOT NULL DEFAULT 0,
    ADD COLUMN effective_from          timestamp with time zone DEFAULT '1970-01-01 00:00:00+00',
    ADD COLUMN effective_to            timestamp with time zone DEFAULT '9999-12-31 23:59:59+00',
    ADD COLUMN published_by           text,
    ADD COLUMN published_comment      text,
    ADD COLUMN is_draft               boolean NOT NULL DEFAULT false,
    ADD COLUMN upstream_step_version   integer NOT NULL DEFAULT 0,
    ADD COLUMN upstream_sq_version     integer NOT NULL DEFAULT 0,
    ADD COLUMN downstream_step_version integer NOT NULL DEFAULT 0,
    ADD COLUMN downstream_ss_version   integer NOT NULL DEFAULT 0,
    ADD COLUMN downstream_sq_version   integer NOT NULL DEFAULT 0;

ALTER TABLE survey.relationships
    ADD CONSTRAINT relationships_ref_versions_ck CHECK (
        upstream_step_version   = 0 AND
        upstream_sq_version     = 0 AND
        downstream_step_version = 0 AND
        downstream_ss_version   = 0 AND
        downstream_sq_version   = 0
    );

-- Drop the check constraint and the five old surrogate FKs before the rename/backfill below.
ALTER TABLE survey.relationships DROP CONSTRAINT downstream_ck;
ALTER TABLE survey.relationships DROP CONSTRAINT upstream_step_fk;
ALTER TABLE survey.relationships DROP CONSTRAINT upstream_sq_fk;
ALTER TABLE survey.relationships DROP CONSTRAINT downstream_step_fk;
ALTER TABLE survey.relationships DROP CONSTRAINT downstream_s_fk;
ALTER TABLE survey.relationships DROP CONSTRAINT downstream_sq_fk;

-- downstream_s_id is renamed to downstream_ss_id (ss = steps_sections convention)
ALTER TABLE survey.relationships RENAME COLUMN downstream_s_id TO downstream_ss_id;

UPDATE survey.relationships r
   SET upstream_step_id = s.step_id
  FROM survey.steps s
 WHERE s.id = r.upstream_step_id;

UPDATE survey.relationships r
   SET upstream_sq_id = sq.sections_question_id
  FROM survey.sections_questions sq
 WHERE sq.id = r.upstream_sq_id;

UPDATE survey.relationships r
   SET downstream_step_id = s.step_id
  FROM survey.steps s
 WHERE s.id = r.downstream_step_id;

UPDATE survey.relationships r
   SET downstream_ss_id = ss.steps_sections_id
  FROM survey.steps_sections ss
 WHERE ss.id = r.downstream_ss_id;

UPDATE survey.relationships r
   SET downstream_sq_id = sq.sections_question_id
  FROM survey.sections_questions sq
 WHERE sq.id = r.downstream_sq_id;

-- Recreate the check against the renamed column.
ALTER TABLE survey.relationships
    ADD CONSTRAINT downstream_ck CHECK ((downstream_step_id + downstream_sq_id + downstream_ss_id) > 0);

-- version = 0: entity-existence check. Time-range join resolves current version per respondent.
ALTER TABLE survey.relationships
    ADD CONSTRAINT upstream_step_fk
    FOREIGN KEY (upstream_step_id, upstream_step_version) REFERENCES survey.steps (step_id, version);

ALTER TABLE survey.relationships
    ADD CONSTRAINT upstream_sq_fk
    FOREIGN KEY (upstream_sq_id, upstream_sq_version) REFERENCES survey.sections_questions (sections_question_id, version);

ALTER TABLE survey.relationships
    ADD CONSTRAINT downstream_step_fk
    FOREIGN KEY (downstream_step_id, downstream_step_version) REFERENCES survey.steps (step_id, version);

ALTER TABLE survey.relationships
    ADD CONSTRAINT downstream_s_fk
    FOREIGN KEY (downstream_ss_id, downstream_ss_version) REFERENCES survey.steps_sections (steps_sections_id, version);

ALTER TABLE survey.relationships
    ADD CONSTRAINT downstream_sq_fk
    FOREIGN KEY (downstream_sq_id, downstream_sq_version) REFERENCES survey.sections_questions (sections_question_id, version);

CREATE UNIQUE INDEX relationships_one_current_un
    ON survey.relationships (relationship_id)
    WHERE effective_to = '9999-12-31 23:59:59+00';

CREATE UNIQUE INDEX relationships_one_draft_un
    ON survey.relationships (relationship_id)
    WHERE is_draft = true;

ALTER TABLE survey.relationships ADD CONSTRAINT relationships_id_version_un UNIQUE (relationship_id, version);

CREATE INDEX relationships_durable_id_idx ON survey.relationships (relationship_id);
CREATE INDEX relationships_active_range_idx ON survey.relationships (survey_id, effective_from, effective_to);

--------------------------------
-- 10. surveys — SCD Type 1 (in-place), no version/effective columns
--------------------------------
ALTER TABLE survey.surveys
    ADD COLUMN published_by      text,
    ADD COLUMN published_comment text;

--------------------------------
-- 11. answers — pin the exact question version seen
--------------------------------
ALTER TABLE survey.answers
    ADD COLUMN question_version integer NOT NULL DEFAULT 0;

UPDATE survey.answers SET question_version = 0 WHERE question_version IS NULL;

-- answers.step/section are display-order values, not surrogate FKs to steps.id/sections.id —
-- these two legacy FKs only ever "worked" by historical coincidence (steps/sections created in
-- display-order sequence — see the doc's "INSERT_MISSING_FACT_SECTION_SQL — Display-Order Join
-- Fix" section) and block the NUMERIC conversion outright (Postgres refuses an FK between
-- incompatible types). The greenfield track never creates them for the same reason.
ALTER TABLE survey.answers DROP CONSTRAINT answers_step_fk;
ALTER TABLE survey.answers DROP CONSTRAINT answers_section_fk;

ALTER TABLE survey.answers ALTER COLUMN step TYPE NUMERIC;
ALTER TABLE survey.answers ALTER COLUMN section TYPE NUMERIC;

--------------------------------
-- 12. metadata — durable-key retarget (rename surrogate columns, add durable columns)
--------------------------------
ALTER TABLE survey.metadata
    RENAME COLUMN question_id TO question_id_surrogate;
ALTER TABLE survey.metadata
    RENAME COLUMN section_question_id TO section_question_id_surrogate;
ALTER TABLE survey.metadata
    RENAME COLUMN step_section_id TO step_section_id_surrogate;

ALTER TABLE survey.metadata
    ADD COLUMN question_durable_id  integer,
    ADD COLUMN sections_question_id integer,
    ADD COLUMN steps_sections_id    integer;

UPDATE survey.metadata m
   SET question_durable_id = q.question_id
  FROM survey.questions q
 WHERE q.id = m.question_id_surrogate
   AND m.question_id_surrogate IS NOT NULL;

UPDATE survey.metadata m
   SET sections_question_id = sq.sections_question_id
  FROM survey.sections_questions sq
 WHERE sq.id = m.section_question_id_surrogate
   AND m.section_question_id_surrogate IS NOT NULL;

UPDATE survey.metadata m
   SET steps_sections_id = ss.steps_sections_id
  FROM survey.steps_sections ss
 WHERE ss.id = m.step_section_id_surrogate
   AND m.step_section_id_surrogate IS NOT NULL;

ALTER TABLE survey.metadata
    DROP CONSTRAINT metadata_un,
    ADD CONSTRAINT metadata_un UNIQUE (steps_sections_id, question_durable_id, sections_question_id, ontology_id, value);

-- metadata_element_ck is deliberately left untouched HERE: yes, Postgres transparently
-- rewrites a CHECK constraint's column references when the underlying column is renamed
-- (the three RENAME COLUMN statements above already did this) — but that means the
-- constraint keeps enforcing "at least one element column is set" against the now-dead
-- *_surrogate columns forever, never against the durable columns rows actually get
-- populated with. That's a real enforcement gap, not the intended behavior — see
-- V011__Fix_Metadata_Element_Check.sql, which drops and recreates this constraint
-- against the durable columns, matching db/migration's V001 directly.

ALTER TABLE survey.metadata
    DROP CONSTRAINT metadata_question_fk,
    DROP CONSTRAINT metadata_sect_quest_fk,
    DROP CONSTRAINT metadata_section_fk;

ALTER TABLE survey.metadata RENAME COLUMN question_durable_id TO question_id;

CREATE INDEX metadata_question_id_index        ON survey.metadata (question_id);
CREATE INDEX metadata_sections_question_id_idx  ON survey.metadata (sections_question_id);
CREATE INDEX metadata_steps_sections_id_idx     ON survey.metadata (steps_sections_id);

--------------------------------
-- 13. surveyreport.dim_step / dim_section — durable rekey columns
--------------------------------
ALTER TABLE surveyreport.dim_step
    ADD COLUMN step_id integer UNIQUE;

UPDATE surveyreport.dim_step ds
   SET step_id = s.step_id
  FROM survey.steps s
 WHERE s.id = ds.id;

ALTER TABLE surveyreport.dim_section
    ADD COLUMN section_id integer UNIQUE;

UPDATE surveyreport.dim_section dsec
   SET section_id = sec.section_id
  FROM survey.sections sec
 WHERE sec.id = dsec.id;
