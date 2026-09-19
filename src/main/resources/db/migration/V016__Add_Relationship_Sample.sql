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

-- V016: an example answer for a TEXT rule's token, used by the Author designer to preview how the
-- token is substituted into the target's text (e.g. sample "Bob" for {PName} previews "Hello Bob").
-- Authoring metadata only: the Survey runtime substitutes real answers and never reads this column.
ALTER TABLE survey.relationships ADD COLUMN IF NOT EXISTS sample character varying(255);
