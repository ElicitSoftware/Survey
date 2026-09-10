-- Runs once, immediately after the Testcontainers Postgres container reports healthy and
-- before Flyway (or the app) ever connects. Mirrors the manual local-dev setup documented
-- in CONTRIBUTING.md: the Flyway migrations only GRANT on individual tables, they don't
-- create the roles, create the schemas the tables live in, or grant schema-level USAGE.
CREATE ROLE survey_user LOGIN PASSWORD 'SURVEYPW';
CREATE ROLE surveyadmin_user LOGIN PASSWORD 'SURVEYPW';
CREATE ROLE surveyreport_user LOGIN PASSWORD 'SURVEYPW';
CREATE ROLE elicit_owner LOGIN PASSWORD 'SURVEYPW' CREATEROLE CREATEDB;

-- Mirrors CONTRIBUTING.md's "CREATE DATABASE survey_test OWNER elicit_owner" — ownership
-- (not just role privileges) is what lets elicit_owner create additional schemas later,
-- e.g. Flyway's own history-table schema (%test.quarkus.flyway.owner.schemas=survey_test).
ALTER DATABASE survey OWNER TO elicit_owner;

CREATE SCHEMA IF NOT EXISTS survey AUTHORIZATION elicit_owner;
CREATE SCHEMA IF NOT EXISTS surveyreport AUTHORIZATION elicit_owner;

GRANT USAGE ON SCHEMA survey, surveyreport TO survey_user, surveyadmin_user, surveyreport_user, elicit_owner;
