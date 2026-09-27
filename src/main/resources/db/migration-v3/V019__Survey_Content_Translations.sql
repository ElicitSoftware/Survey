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

-- Mirrors db/migration/V019 at the same version number (see ManualSchemaMigrator: every version
-- must exist in both tracks). A database still on the upgrade path gets the table here; a converged
-- one gets it from db/migration. IF NOT EXISTS keeps either order idempotent.
--
-- V019: other languages of a survey's content (docs/research/i18n_survey.md, sections 3.2 and 3.3).
--
-- One row per (survey, element, field, language): the translated value of one base-language column
-- of one element, attached to that element by its *_key UUID and the name of the column, never by a
-- surrogate id (reallocated on every import) or a durable id (per instance). Base text stays in the
-- structural row; nothing here replaces it.
--
-- Type 2 in the same shape as the eight structural tables, for the same reason: a respondent who
-- started under one wording keeps seeing it, a correction is a new version, and a removal is a
-- closed effective_to, never a delete and never an empty value.
--------------------------------
-- translations - Type 2 SCD table (durable key: translation_id); the ninth, and the first that is
-- not structural. It holds no durable id of any other table, so versioning a question - which mints
-- a new surrogate id under the same question_key - leaves its translations attached and untouched.
--------------------------------
CREATE SEQUENCE IF NOT EXISTS survey.translations_seq INCREMENT 1 START 1;
GRANT USAGE ON SEQUENCE survey.translations_seq TO ${survey_user};
CREATE SEQUENCE IF NOT EXISTS survey.translations_durable_seq INCREMENT 1 START 1;
GRANT USAGE ON SEQUENCE survey.translations_durable_seq TO ${survey_user};

CREATE TABLE IF NOT EXISTS survey.translations
(
    id                 integer NOT NULL,
    survey_id          integer NOT NULL,
    -- Which table the translated element lives in. Validation metadata: it appears in no index,
    -- because element_key is a UUID and already unique across every table.
    element_type       character varying(32) NOT NULL,
    -- The owning row's *_key. Never a surrogate id, never a durable id.
    element_key        uuid NOT NULL,
    -- Column name of the base text this row translates (text, short_text, tool_tip, placeholder,
    -- validation_text, name, description, display_text, default_upstream_value). Constrained by the
    -- translatable-field whitelist in code, not here, so adding a field is not a migration.
    field              character varying(32) NOT NULL,
    -- BCP-47 tag, written as the mounted chrome bundles write it: es-419, ar.
    language           character varying(35) NOT NULL,
    -- The translated text. Unbounded on purpose: a translation runs longer than its source (Spanish
    -- and French by 15-25%), and nothing in the database may truncate a sentence a respondent is
    -- reading. The length a translator is held to is a per-string budget enforced in Author.
    value              text NOT NULL,
    -- Lower-case hex SHA-256 of the UTF-8 bytes of the base text this was translated from, no trim.
    -- varchar, not char(64): bpchar compares with trailing-space semantics and Hibernate maps a
    -- String to varchar, so char(64) fails schema validation in Author.
    -- A row whose hash no longer matches the element's current base text is stale: the base text was
    -- edited after the translation was made, so the runtime serves the base text instead.
    source_hash        character varying(64) NOT NULL,
    -- Authoring-only snapshot of that base text, for the stale diff in Author's Translations view.
    -- Not exported, on the same footing as questions.sample.
    source_text        text,
    translation_id     integer NOT NULL DEFAULT nextval('survey.translations_durable_seq'),
    -- Cross-instance-portable identity, minted once in Author and preserved verbatim by export,
    -- import and update - see select_group_key for the same pattern on a structural table.
    translation_key    uuid NOT NULL,
    version            integer NOT NULL DEFAULT 0,
    effective_from     timestamp with time zone DEFAULT '1970-01-01 00:00:00+00',
    effective_to       timestamp with time zone DEFAULT '9999-12-31 23:59:59+00',
    published_by       text,
    published_comment  text,
    CONSTRAINT translations_pk PRIMARY KEY (id),
    CONSTRAINT translations_surveys_fk FOREIGN KEY (survey_id) REFERENCES survey.surveys (id),
    CONSTRAINT translations_id_version_un UNIQUE (translation_id, version),
    CONSTRAINT translations_key_version_un UNIQUE (translation_key, version),
    CONSTRAINT translations_element_type_ck
        CHECK (element_type IN ('surveys','steps','sections','questions','select_items','relationships','reports'))
);

-- One current row per durable id, as on the other eight; also the trigger's lookup.
CREATE UNIQUE INDEX IF NOT EXISTS translations_one_current_un
    ON survey.translations (translation_id)
    WHERE effective_to = '9999-12-31 23:59:59+00';
-- One current row per target string per language. Integrity, and the shape Author's grid reads.
CREATE UNIQUE INDEX IF NOT EXISTS translations_target_current_un
    ON survey.translations (survey_id, language, element_key, field)
    WHERE effective_to = '9999-12-31 23:59:59+00';
-- Admin's update service finds the current row by its cross-instance key.
CREATE INDEX IF NOT EXISTS translations_key_current_idx
    ON survey.translations (translation_key)
    WHERE effective_to = '9999-12-31 23:59:59+00';
-- Runtime point lookup and the review SQL join, as of a respondent's first access.
CREATE INDEX IF NOT EXISTS translations_lookup_idx
    ON survey.translations (element_key, field, language, effective_from, effective_to);
-- Runtime bulk load of one survey and language into the translator cache, as-of. The INCLUDE
-- columns answer the staleness check without touching the heap; only value is fetched from it.
CREATE INDEX IF NOT EXISTS translations_load_idx
    ON survey.translations (survey_id, language, effective_from, effective_to)
    INCLUDE (element_key, field, source_hash);

DROP TRIGGER IF EXISTS translations_scd_close_predecessor ON survey.translations;
CREATE TRIGGER translations_scd_close_predecessor BEFORE INSERT ON survey.translations
    FOR EACH ROW EXECUTE FUNCTION survey.scd_close_predecessor('translation_id');

-- Full DML, as on every other definition table. The Survey runtime only reads translations, but
-- ${survey_user} is also the user the Author tool connects as against its own database (see the
-- compose file's author service), and Author is where translations are written.
GRANT DELETE, UPDATE, INSERT, SELECT ON TABLE survey.translations TO ${survey_user};

--------------------------------
-- surveys - the language the content is written in, and the set it is published in.
--------------------------------
-- The language every string falls back to when no current translation applies.
ALTER TABLE survey.surveys ADD COLUMN IF NOT EXISTS base_language character varying(35) NOT NULL DEFAULT 'en';
-- Comma-separated BCP-47 tags the author has marked publishable. A site serves one of them only
-- when its own chrome mount also carries that language, so one file suits every site.
ALTER TABLE survey.surveys ADD COLUMN IF NOT EXISTS content_languages character varying(255);

--------------------------------
-- answers - the rendered label, in the language the respondent was reading.
--------------------------------
-- display_text keeps the base language: the ETL, Admin, the respondent export and every report read
-- it. display_text_local is the same sentence rendered in the respondent's language, written by the
-- same pass that builds display_text, and null when the label was shown in the base language.
-- text, not varchar(8000) like display_text, for the same reason translations.value is unbounded.
ALTER TABLE survey.answers ADD COLUMN IF NOT EXISTS display_text_local text;
ALTER TABLE survey.answers ADD COLUMN IF NOT EXISTS display_language character varying(35);
