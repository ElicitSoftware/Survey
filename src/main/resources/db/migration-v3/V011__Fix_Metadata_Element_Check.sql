---
-- ***LICENSE_START***
-- Elicit Survey
-- %%
-- Copyright (C) 2025 - 2026 Elicit Software LLC
-- %%
-- PolyForm Noncommercial License 1.0.0
-- <https://polyformproject.org/licenses/noncommercial/1.0.0>
-- ***LICENSE_END***
---

-- V010 now drops metadata's surrogate columns and recreates metadata_element_ck against the
-- durable columns itself (issue #111), so on this track this is an idempotent re-assertion of
-- the same constraint. It stays because every version must exist in both locations, and
-- db/migration's V011 is already applied on existing greenfield databases.
ALTER TABLE survey.metadata DROP CONSTRAINT IF EXISTS metadata_element_ck;
ALTER TABLE survey.metadata
    ADD CONSTRAINT metadata_element_ck
    CHECK ((steps_sections_id + question_id + sections_question_id) > 0);
