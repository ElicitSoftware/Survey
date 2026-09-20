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

-- V016: an example answer for a question, used by the Author designer to preview token
-- substitution. The runtime writes a respondent's answer to the upstream question into every text a
-- TEXT rule targets, so a sample of that answer belongs to the question it answers, not to each of
-- the rules that carry it (e.g. sample "Bob" for the name question previews "Hello {PName}" as
-- "Hello Bob" wherever a rule fills {PName}). Authoring metadata only: the Survey runtime
-- substitutes real answers and never reads this column.
ALTER TABLE survey.questions ADD COLUMN IF NOT EXISTS sample character varying(255);
