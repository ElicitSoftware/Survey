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
-- Mirrors db/migration/V020 at the same version number (see ManualSchemaMigrator: every version
-- must exist in both tracks, or a post-repair validate() fails on it as a pending migration).
-- The work is identical either way: a database on the upgrade path holds exactly the same
-- undelimited placeholders a converged one does, and the rewrite is idempotent -- a phrase that
-- already carries <NAME> is skipped -- so whichever track reaches it first, the other is a no-op.
-- V020: a token is written <NAME> where it is used.
--
-- A placeholder is still {phrase|default}. What changes is how the token inside the phrase is
-- recognized. It used to be found by containment -- phrase.contains(token) in Author, and
-- string.contains(key) over whole segments of text in the Survey runtime -- which cannot tell a
-- token from an ordinary word. "surname" contains "name"; "S10" contains "S1"; and prose that
-- happens to mention the word was rewritten in place of the placeholder:
--
--     "Please write your name below. {name|this person}"  ->  "your Alice below. name"
--
-- Delimited, a reference matches whole or not at all.
--
-- This runs against Author's database as well as Survey's: Author owns no migrations of its own
-- and maps its entities onto the schema these migrations create (Author application.properties).
--
-- Every version is rewritten, history included, not only the current row. A Type 2 history row is
-- what a respondent anchored to an earlier revision still reads, and the new runtime fills only a
-- delimited reference -- so leaving history alone would show those respondents the default text
-- where a name belongs.

-- Token names become upper case. Matching below is case-insensitive either way, so the order of
-- these two steps does not matter; doing it first is what makes the rewritten text agree with the
-- rule that fills it.
UPDATE survey.relationships
   SET token = upper(trim(token))
 WHERE token IS NOT NULL
   AND token <> ''
   AND token <> upper(trim(token));

-- Rewrites the phrases of every placeholder in one string, leaving defaults and prose alone.
--
-- Placeholder by placeholder rather than by one regex over the whole text: only the part before
-- the bar is a phrase, and a literal replace() of a whole placeholder cannot stray outside it.
-- \m and \M are word boundaries, so "name" is delimited inside "name's" but not inside "surname",
-- and a phrase that already carries <NAME> is skipped so the function is safe to run twice.
CREATE OR REPLACE FUNCTION survey.delimit_tokens(txt text, tokens text[])
RETURNS text AS $$
DECLARE
    result      text := txt;
    placeholder text;
    phrase      text;
    tail        text;
    rewritten   text;
    replacement text;
    tok         text;
    bar         integer;
BEGIN
    IF txt IS NULL OR tokens IS NULL OR array_length(tokens, 1) IS NULL THEN
        RETURN txt;
    END IF;
    FOR placeholder IN
        SELECT DISTINCT m[1] FROM regexp_matches(txt, '(\{[^{}]*\})', 'g') AS m
    LOOP
        bar := position('|' in placeholder);
        IF bar > 0 THEN
            phrase := substring(placeholder from 2 for bar - 2);
            tail   := substring(placeholder from bar);          -- '|default}'
        ELSE
            phrase := substring(placeholder from 2 for length(placeholder) - 2);
            tail   := '}';
        END IF;

        rewritten := phrase;
        FOREACH tok IN ARRAY tokens LOOP
            -- Token names are word characters; anything else is not a name this can safely
            -- splice into a regular expression, and is left for an author to fix by hand.
            IF tok ~ '^[A-Za-z0-9_]+$' AND rewritten !~ ('<' || upper(tok) || '>') THEN
                rewritten := regexp_replace(rewritten, '\m' || tok || '\M',
                                            '<' || upper(tok) || '>', 'gi');
            END IF;
        END LOOP;

        replacement := '{' || rewritten || tail;
        IF replacement <> placeholder THEN
            result := replace(result, placeholder, replacement);
        END IF;
    END LOOP;
    RETURN result;
END;
$$ LANGUAGE plpgsql;

-- One survey's tokens, as an array.
CREATE OR REPLACE FUNCTION survey.tokens_of(p_survey_id integer)
RETURNS text[] AS $$
    SELECT coalesce(array_agg(DISTINCT upper(trim(token))), ARRAY[]::text[])
      FROM survey.relationships
     WHERE survey_id = p_survey_id
       AND token IS NOT NULL
       AND trim(token) <> '';
$$ LANGUAGE sql STABLE;

-- Every translatable column of every version, as one relation, keyed the way translations are.
-- Every version, not just the current one: a translation's source_hash names the text it was made
-- from, which for a survey that has been revised is often a history row.
CREATE OR REPLACE VIEW survey.translatable_source AS
    SELECT survey_id, question_key AS element_key, 'questions'::text AS element_type,
           'text'::text AS field, text AS source FROM survey.questions
    UNION ALL SELECT survey_id, question_key, 'questions', 'short_text', short_text FROM survey.questions
    UNION ALL SELECT survey_id, question_key, 'questions', 'tool_tip', tool_tip FROM survey.questions
    UNION ALL SELECT survey_id, question_key, 'questions', 'placeholder', placeholder FROM survey.questions
    UNION ALL SELECT survey_id, question_key, 'questions', 'validation_text', validation_text FROM survey.questions
    UNION ALL SELECT survey_id, section_key, 'sections', 'name', name FROM survey.sections
    UNION ALL SELECT survey_id, section_key, 'sections', 'description', description FROM survey.sections
    UNION ALL SELECT survey_id, step_key, 'steps', 'name', name FROM survey.steps
    UNION ALL SELECT survey_id, step_key, 'steps', 'description', description FROM survey.steps
    UNION ALL SELECT survey_id, select_item_key, 'select_items', 'display_text', display_text FROM survey.select_items
    UNION ALL SELECT id, survey_key, 'surveys', 'title', title FROM survey.surveys
    UNION ALL SELECT id, survey_key, 'surveys', 'description', description FROM survey.surveys
    UNION ALL SELECT survey_id, relationship_key, 'relationships', 'default_upstream_value',
           default_upstream_value FROM survey.relationships;

-- Rehash first, while the base text is still undelimited.
--
-- source_hash is the hash of the base text a translation was made from, and the only record of
-- whether that translation is still current: the runtime compares it against the element's text
-- as it stands. Rewriting the base text without touching this would mark every translation of
-- every survey out of date; rehashing from the element's *current* text would do the opposite and
-- quietly mark a genuinely stale translation current.
--
-- So each row is matched to the exact version it was made from -- the one whose text still hashes
-- to source_hash -- and given the hash of that same text delimited. A translation that was current
-- stays current, one that had gone stale stays stale by the same wording, and one whose source is
-- no longer in history keeps its hash and stays stale, which is what it already was.
UPDATE survey.translations t
   SET source_hash = encode(sha256(convert_to(
           survey.delimit_tokens(src.source, survey.tokens_of(t.survey_id)), 'UTF8')), 'hex')
  FROM survey.translatable_source src
 WHERE src.element_key = t.element_key
   AND src.element_type = t.element_type
   AND src.field = t.field
   AND src.source IS NOT NULL
   AND t.source_hash = encode(sha256(convert_to(src.source, 'UTF8')), 'hex');

-- Now the base text itself.
UPDATE survey.questions q
   SET text            = survey.delimit_tokens(q.text,            survey.tokens_of(q.survey_id)),
       short_text      = survey.delimit_tokens(q.short_text,      survey.tokens_of(q.survey_id)),
       tool_tip        = survey.delimit_tokens(q.tool_tip,        survey.tokens_of(q.survey_id)),
       placeholder     = survey.delimit_tokens(q.placeholder,     survey.tokens_of(q.survey_id)),
       validation_text = survey.delimit_tokens(q.validation_text, survey.tokens_of(q.survey_id));

UPDATE survey.sections s
   SET name        = survey.delimit_tokens(s.name,        survey.tokens_of(s.survey_id)),
       description = survey.delimit_tokens(s.description, survey.tokens_of(s.survey_id));

UPDATE survey.steps s
   SET name        = survey.delimit_tokens(s.name,        survey.tokens_of(s.survey_id)),
       description = survey.delimit_tokens(s.description, survey.tokens_of(s.survey_id));

UPDATE survey.select_items i
   SET display_text = survey.delimit_tokens(i.display_text, survey.tokens_of(i.survey_id));

UPDATE survey.surveys s
   SET title       = survey.delimit_tokens(s.title,       survey.tokens_of(s.id)),
       description = survey.delimit_tokens(s.description, survey.tokens_of(s.id));

-- A rule's upstream value is authored prose, not an answer the respondent typed, and may hold
-- placeholders of its own -- "{<G1>'s|your} mother" -- which the runtime resolves before it
-- splices the value in. Undelimited, the phrase fills nothing and the respondent reads the
-- default, so these two columns need the same rewrite the texts above get.
-- default_upstream_value is translatable (Author TranslatableFields), which is why it joins
-- translatable_source above and is rehashed with everything else; override_upstream_value is not
-- translated but carries the same placeholders and is rewritten alongside it.
UPDATE survey.relationships r
   SET default_upstream_value  = survey.delimit_tokens(r.default_upstream_value,
                                                       survey.tokens_of(r.survey_id)),
       override_upstream_value = survey.delimit_tokens(r.override_upstream_value,
                                                       survey.tokens_of(r.survey_id));

-- And the translated strings, which carry the same placeholders in the target language. The
-- default after the bar is the translator's prose and is left exactly as written.
UPDATE survey.translations t
   SET value = survey.delimit_tokens(t.value, survey.tokens_of(t.survey_id));

DROP VIEW survey.translatable_source;
DROP FUNCTION survey.delimit_tokens(text, text[]);
DROP FUNCTION survey.tokens_of(integer);
