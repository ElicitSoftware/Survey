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

-- Mirrors db/migration/V016 at the same version number (see ManualSchemaMigrator: every version
-- must exist in both tracks). A database still on the upgrade path gets the column here; a
-- converged one gets it from db/migration. IF NOT EXISTS keeps either order idempotent.
ALTER TABLE survey.questions ADD COLUMN IF NOT EXISTS sample character varying(255);
