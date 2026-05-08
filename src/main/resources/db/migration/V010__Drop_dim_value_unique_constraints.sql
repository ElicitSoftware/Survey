-- ***LICENSE_START***
-- Elicit Survey
-- %%
-- Copyright (C) 2025 The Regents of the University of Michigan - Rogel Cancer Center
-- %%
-- PolyForm Noncommercial License 1.0.0
-- <https://polyformproject.org/licenses/noncommercial/1.0.0>
-- ***LICENSE_END***
---

-- Remove UNIQUE constraints on the value column in dim_step and dim_section.
-- Step and section dimension_name values are only required to be unique per
-- row (enforced by the PK on id). Multiple surveys can legitimately share the
-- same dimension name (e.g., "Welcome"), so a global UNIQUE constraint on
-- value causes false conflicts in the ETL upsert.

ALTER TABLE surveyreport.dim_step    DROP CONSTRAINT IF EXISTS dim_step_un;
ALTER TABLE surveyreport.dim_section DROP CONSTRAINT IF EXISTS dim_section_un;
