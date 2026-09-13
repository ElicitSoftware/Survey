---
-- ***LICENSE_START***
-- Elicit Survey
-- %%
-- Copyright (C) 2025 The Regents of the University of Michigan - Rogel Cancer Center
-- %%
-- PolyForm Noncommercial License 1.0.0
-- <https://polyformproject.org/licenses/noncommercial/1.0.0>
-- ***LICENSE_END***
---

-- V010's own comment claimed Postgres's rename-triggered CHECK-constraint rewrite would
-- keep metadata_element_ck enforcing correctly after step_section_id/question_id/
-- section_question_id were renamed to *_surrogate. That's wrong: the constraint keeps
-- checking the renamed-away surrogate columns forever, never the new durable columns
-- (steps_sections_id/question_id/sections_question_id) that rows are actually populated
-- with going forward — so on an upgraded database, NULL + NULL + NULL > 0 (NULL, not
-- violated) silently lets a metadata row with no element set through.
--
-- Explicitly drop and recreate the constraint against the durable columns, matching what
-- db/migration's V001 creates directly. Mirrored into db/migration at the same version
-- number (see that location's V011 for why) so a not-yet-upgraded v2.x database gets the
-- correct constraint in the same boot that runs this whole upgrade track.
ALTER TABLE survey.metadata DROP CONSTRAINT IF EXISTS metadata_element_ck;
ALTER TABLE survey.metadata
    ADD CONSTRAINT metadata_element_ck
    CHECK ((steps_sections_id + question_id + sections_question_id) > 0);
