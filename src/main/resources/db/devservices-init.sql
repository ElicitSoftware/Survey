-- Roles required by Flyway GRANT statements in the migration scripts.
-- This script runs once when the Dev Services PostgreSQL container is created.
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'survey_user') THEN
        CREATE ROLE survey_user LOGIN PASSWORD 'dev';
    END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'surveyadmin_user') THEN
        CREATE ROLE surveyadmin_user LOGIN PASSWORD 'dev';
    END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'surveyreport_user') THEN
        CREATE ROLE surveyreport_user LOGIN PASSWORD 'dev';
    END IF;
END $$;
