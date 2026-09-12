-- ***LICENSE_START***
-- Elicit FHHS
-- %%
-- Copyright (C) 2025 The Regents of the University of Michigan - Rogel Cancer Center
-- %%
-- PolyForm Noncommercial License 1.0.0
-- <https://polyformproject.org/licenses/noncommercial/1.0.0>
-- ***LICENSE_END***
---

--------------------------------
-- Reporting schema
--------------------------------
CREATE TABLE surveyreport.dim_date(
    DateKey integer NOT NULL,
    FullDate date NULL,
    DateName character varying(11) NOT NULL,
    DayOfWeek smallint NOT NULL,
    DayNameOfWeek character varying(10) NOT NULL,
    DayOfMonth smallint NOT NULL,
    DayOfYear smallint NOT NULL,
    WeekdayWeekend character varying(10) NOT NULL,
    WeekOfYear smallint NOT NULL,
    MonthName character varying(10) NOT NULL,
    MonthOfYear smallint NOT NULL,
    IsLastDayOfMonth character varying(1) NOT NULL,
    CalendarQuarter smallint NOT NULL,
    CalendarYear smallint NOT NULL,
    CalendarYearMonth character varying(10) NOT NULL,
    CalendarYearQtr character varying(10) NOT NULL,
    FiscalMonthOfYear smallint NOT NULL,
    FiscalQuarter smallint   NOT NULL,
    FiscalYear integer NOT NULL,
    FiscalYearMonth character varying(10) NOT NULL,
    FiscalYearQtr character varying(10) NOT NULL,
    CONSTRAINT dim_date_pk PRIMARY KEY (DateKey)
);
GRANT SELECT ON surveyreport.dim_date TO ${survey_user};
GRANT SELECT ON surveyreport.dim_date TO ${surveyreport_user};
--------------------------------
CREATE SEQUENCE IF NOT EXISTS surveyreport.dim_status_seq INCREMENT 1 START 1;
CREATE TABLE IF NOT EXISTS surveyreport.dim_status(
      id integer NOT NULL DEFAULT NEXTVAL('surveyreport.dim_status_seq'),
      value character varying(50),
      CONSTRAINT dim_status_pk PRIMARY KEY (id),
      CONSTRAINT dim_status_un UNIQUE (value)
);
CREATE INDEX dim_status_idx ON surveyreport.dim_status(id);
GRANT SELECT ON surveyreport.dim_status TO ${survey_user};
GRANT SELECT ON surveyreport.dim_status TO ${surveyreport_user};
--------------------------------
CREATE TABLE IF NOT EXISTS surveyreport.dim_step(
      id integer NOT NULL,
      value character varying(50) NOT NULL,
      CONSTRAINT dim_step_pk PRIMARY KEY (id),
      CONSTRAINT dim_step_un UNIQUE (value)
);
CREATE INDEX dim_step_idx ON surveyreport.dim_step(id);
CREATE INDEX dim_step_value_index ON surveyreport.dim_step USING btree (value ASC NULLS LAST);
GRANT INSERT, SELECT, UPDATE ON surveyreport.dim_step TO ${survey_user};
GRANT SELECT ON surveyreport.dim_step TO ${surveyreport_user};
--------------------------------
CREATE TABLE IF NOT EXISTS surveyreport.dim_section(
      id integer NOT NULL,
      value character varying(50) NOT NULL,
      CONSTRAINT dim_section_pk PRIMARY KEY (id),
      CONSTRAINT dim_section_un UNIQUE (value)
);
CREATE INDEX dim_section_idx ON surveyreport.dim_section(id);
CREATE INDEX dim_section_value_index ON surveyreport.dim_section USING btree (value ASC NULLS LAST);
GRANT INSERT, SELECT, UPDATE ON surveyreport.dim_section TO ${survey_user};
GRANT SELECT ON surveyreport.dim_section TO ${surveyreport_user};
--------------------------------
CREATE SEQUENCE surveyreport.fact_sections_seq INCREMENT 1 START 1;
CREATE TABLE IF NOT EXISTS surveyreport.fact_sections
(
    id integer NOT NULL DEFAULT NEXTVAL('surveyreport.fact_sections_seq'),
    survey_id INTEGER NOT NULL,
    respondent_id INTEGER NOT NULL,
    step_key INTEGER NOT NULL DEFAULT 0,
    name CHARACTER VARYING(50),
    step_instance INTEGER NOT NULL DEFAULT 0,
    section_key INTEGER NOT NULL,
    section_instance INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fact_sections_pk PRIMARY KEY (id),
    CONSTRAINT step_fk FOREIGN KEY (step_key) REFERENCES surveyreport.dim_step(id),
    CONSTRAINT section_fk FOREIGN KEY (section_key) REFERENCES surveyreport.dim_section(id)
);
CREATE INDEX fact_sections_respondent_id_idx ON surveyreport.fact_sections(respondent_id);
GRANT USAGE ON SEQUENCE surveyreport.fact_sections_seq TO ${survey_user};
GRANT INSERT, SELECT, UPDATE ON surveyreport.fact_sections TO ${survey_user};
GRANT SELECT ON surveyreport.fact_sections TO ${surveyreport_user};
--------------------------------
CREATE TABLE IF NOT EXISTS surveyreport.fact_respondents
(
    id INTEGER NOT NULL,
    survey_id INTEGER NOT NULL,
    created_key INTEGER NOT NULL,
    first_access_key INTEGER NOT NULL,
    finalized_key INTEGER NOT NULL,
    active BOOLEAN NOT NULL,
    logins INTEGER NOT NULL DEFAULT 0,
    status INTEGER NOT NULL DEFAULT 0, -- 0 = not started, 1 = in progress 2 = finished
    duration INTERVAL DEFAULT '0',
    CONSTRAINT fact_respondents_pk PRIMARY KEY (id),
    CONSTRAINT fact_respondents_created_key_fk FOREIGN KEY (created_key) REFERENCES surveyreport.dim_date(datekey),
    CONSTRAINT fact_respondents_first_access_fk FOREIGN KEY (first_access_key) REFERENCES surveyreport.dim_date(datekey),
    CONSTRAINT fact_respondents_finalized_fk FOREIGN KEY (finalized_key) REFERENCES surveyreport.dim_date(datekey)
);
GRANT INSERT, SELECT, UPDATE ON surveyreport.fact_respondents TO ${survey_user};
GRANT SELECT ON surveyreport.fact_respondents TO ${surveyreport_user};
--------------------------------
-- create a trigger to always insert the respondent fact row after a new respondent is inserted.
CREATE OR REPLACE FUNCTION insert_fact_respondent() RETURNS TRIGGER AS $fact_insert$
BEGIN
    --
    -- Create a row in surveyreport.fact_respondents for the fact survey
    --
    IF (TG_OP = 'INSERT' AND new.survey_id = 1) THEN
        INSERT INTO surveyreport.fact_respondents (id,survey_id,active,logins,created_key,first_access_key,finalized_key,status,duration)
        VALUES(new.id, new.survey_id, new.active, new.logins,
           COALESCE(to_char(new.created_dt,'YYYYMMDD')::numeric, 19700101),
           COALESCE(to_char(new.first_access_dt,'YYYYMMDD')::numeric, 19700101),
           COALESCE(to_char(new.finalized_dt,'YYYYMMDD')::numeric, 19700101),
           CASE
               WHEN new.first_access_dt IS NULL AND new.finalized_dt IS NULL THEN 0
               WHEN new.first_access_dt IS NOT NULL AND new.finalized_dt IS NULL THEN 1
               ELSE 2
               END,
           CASE
               WHEN new.finalized_dt IS NULL then INTERVAL '0'
               ELSE new.finalized_dt - new.created_dt
               END);
        END IF;
        RETURN NULL;
END;
$fact_insert$ LANGUAGE plpgsql;

CREATE TRIGGER fact_respondent_insert
    AFTER INSERT ON survey.respondents
    FOR EACH ROW EXECUTE FUNCTION insert_fact_respondent();
--------------------------------
-- create a trigger to always insert the respondent fact row after a new respondent is inserted.
CREATE OR REPLACE FUNCTION update_fact_respondent() RETURNS TRIGGER AS $fact_update$
BEGIN
    --
    -- Create a row in surveyreport.fact_respondents for the fact survey
    --
    IF (TG_OP = 'UPDATE' AND new.survey_id = 1) THEN
        UPDATE surveyreport.fact_respondents
        set active = new.active,
        logins = new.logins,
        first_access_key = COALESCE(to_char(new.first_access_dt,'YYYYMMDD')::numeric, 19700101),
        finalized_key = COALESCE(to_char(new.finalized_dt,'YYYYMMDD')::numeric, 19700101),
        status =
           CASE
               WHEN new.first_access_dt IS NULL AND new.finalized_dt IS NULL THEN 0
               WHEN new.first_access_dt IS NOT NULL AND new.finalized_dt IS NULL THEN 1
               ELSE 2
               END,
        duration =
           CASE
               WHEN new.finalized_dt IS NULL then INTERVAL '0'
               ELSE new.finalized_dt - new.created_dt
               END
        where id = new.id;
    END IF;
    RETURN NULL;
END;
$fact_update$ LANGUAGE plpgsql;

CREATE TRIGGER fact_update
    AFTER UPDATE ON survey.respondents
    FOR EACH ROW EXECUTE FUNCTION update_fact_respondent();
