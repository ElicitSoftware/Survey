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

-- The real fix. V001__Create_Survey_Schema.sql grants USAGE on every sequence it creates
-- immediately after CREATE SEQUENCE, except this one — survey.dimensions_seq's usage grant
-- was simply never added. db/migration-v3's V010 already patched this for upgraded
-- databases (see the comment there); this greenfield location never was. GRANT is
-- idempotent, so this is also safe to run on a database that somehow already has it.
GRANT USAGE ON SEQUENCE survey.dimensions_seq TO ${survey_user};
