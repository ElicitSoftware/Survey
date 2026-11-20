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

-- Mirrors db/migration-v3/V011 at the same version number (see that file for the full
-- explanation of the bug being fixed). This location's V001 already creates
-- metadata_element_ck against the correct durable columns, so on a genuinely fresh
-- database this is an idempotent no-op re-assertion of the same constraint. Its real job
-- is for a database that already ran the old, buggy db/migration-v3 V010 and got
-- repaired onto this location: ManualSchemaMigrator's next boot finds this migration
-- pending and applies it directly, fixing the constraint in place with no special-cased
-- repair logic needed.
ALTER TABLE survey.metadata DROP CONSTRAINT IF EXISTS metadata_element_ck;
ALTER TABLE survey.metadata
    ADD CONSTRAINT metadata_element_ck
    CHECK ((steps_sections_id + question_id + sections_question_id) > 0);
