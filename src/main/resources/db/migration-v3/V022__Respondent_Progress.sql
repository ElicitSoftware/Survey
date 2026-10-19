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
-- V022 (upgrade track): respondent progress stamps (UC-012).
--
-- Two nullable timestamps a progress report needs and the answer rows alone cannot give:
--   answers.presented_dt      when the page holding the row was first rendered (BR-001). Rows are
--                             built at login for every unconditional question, so without it
--                             "presented" can only be inferred, and only for finished respondents.
--   respondents.last_activity_dt  the respondent's latest page render or save (BR-004).
-- answers.saved_dt keeps its column and changes meaning in code: from this release it is set only
-- when the respondent changes a value, never at row creation for a default (BR-002).
--
-- Back-fill (A5). A finished respondent passed every page, so each surviving answer row was shown:
-- its created_dt is the best instant on record. In-progress and not-started respondents are left
-- unstamped, since nothing says which pages they reached. last_activity_dt is the latest instant
-- on record for anyone who has logged in. On a greenfield database both updates touch no row.
--
ALTER TABLE survey.answers
    ADD COLUMN presented_dt timestamp with time zone;

COMMENT ON COLUMN survey.answers.presented_dt IS
    'When the page holding this row was first rendered for the respondent (UC-012 BR-001); null for a row built for a page not yet reached.';

ALTER TABLE survey.respondents
    ADD COLUMN last_activity_dt timestamp with time zone;

COMMENT ON COLUMN survey.respondents.last_activity_dt IS
    'The respondent''s latest page render or save (UC-012 BR-004); null until they have opened a page.';

UPDATE survey.answers a
   SET presented_dt = a.created_dt
  FROM survey.respondents r
 WHERE r.id = a.respondent_id
   AND r.finalized_dt IS NOT NULL
   AND a.presented_dt IS NULL;

UPDATE survey.respondents r
   SET last_activity_dt = COALESCE(r.finalized_dt,
                                   (SELECT MAX(a.saved_dt) FROM survey.answers a WHERE a.respondent_id = r.id),
                                   r.first_access_dt)
 WHERE r.first_access_dt IS NOT NULL
   AND r.last_activity_dt IS NULL;
