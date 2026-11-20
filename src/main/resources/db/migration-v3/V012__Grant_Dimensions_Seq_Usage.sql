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

-- Mirrors db/migration's V012 at the same version number (see that file for the full
-- explanation). This track's own V010 already granted this ("Pre-existing gap in
-- V001__Create_Survey_Schema.sql (frozen history, cannot be fixed there): every other
-- sequence gets GRANT USAGE except this one. Harmless to add here..."), so on an
-- already-upgraded database this is a harmless, idempotent re-grant. Kept at the same
-- version number as the greenfield fix purely to keep both tracks' version numbering
-- aligned, matching the V011 precedent.
GRANT USAGE ON SEQUENCE survey.dimensions_seq TO ${survey_user};
