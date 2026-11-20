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

--------------------------------
-- Test profile only. Flyway keeps its history in survey_test here (%test.quarkus.flyway.owner.schemas),
-- a schema it creates itself, so the schema-level USAGE that db/testcontainers-init.sql grants on
-- survey cannot cover it. V017 grants ${survey_user} SELECT on the history table; this grants the
-- USAGE on its schema that a deployment's survey schema already has, so the startup diagnostics
-- report reads the history as the application user in tests the way it does in production (UC-007).
--------------------------------
GRANT USAGE ON SCHEMA "${flyway:defaultSchema}" TO ${survey_user};
