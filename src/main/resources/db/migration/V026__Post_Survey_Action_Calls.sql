-- ***LICENSE_START***
-- Elicit Survey
-- %%
-- Copyright (C) 2025 - 2026 The Regents of the University of Michigan - Rogel Cancer Center
-- %%
-- PolyForm Noncommercial License 1.0.0
-- <https://polyformproject.org/licenses/noncommercial/1.0.0>
-- ***LICENSE_END***
--
-- V026: configurable post-survey action calls (UC-015 BR-001, BR-002; FR-037; issue #192,
-- Elicit#95).
--
-- An action is called with its own method, and sends the subject's External ID and department
-- code only when its author turned that on. Both columns default to today's call, POST without
-- the External ID, so every existing action keeps behaving exactly as before. The site's
-- credential for an action is configuration keyed by post_survey_action_key, never a column.
--
-- IF NOT EXISTS keeps the migration the same on the greenfield track and on the V2 upgrade
-- track, and safe on a database that has it already.
--
ALTER TABLE survey.post_survey_actions
    ADD COLUMN IF NOT EXISTS method character varying(10) NOT NULL DEFAULT 'POST';
ALTER TABLE survey.post_survey_actions
    ADD COLUMN IF NOT EXISTS sends_external_id boolean NOT NULL DEFAULT false;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'post_survey_actions_method_ck') THEN
        ALTER TABLE survey.post_survey_actions
            ADD CONSTRAINT post_survey_actions_method_ck CHECK (method IN ('POST', 'GET', 'PUT'));
    END IF;
END $$;
