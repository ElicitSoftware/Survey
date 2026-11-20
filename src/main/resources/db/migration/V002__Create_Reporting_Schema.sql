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
-- Reporting schema: the common part.
--
-- surveyreport holds only what every survey shares, the conformed dimensions dim_date and
-- dim_status. Each survey's own star (dim_step, dim_section, dim_question, dim_item, the
-- dim_<tag> tables, fact_sections and the fact_respondents view) lives in a schema of its own,
-- named on survey.surveys.report_schema (V021) and created by the ETL the first time the survey
-- is built -- see UC-008 BR-006/BR-007 and com.elicitsoftware.etl.Sql. Nothing here is keyed to
-- one survey, and no trigger writes into a reporting schema: fact_respondents is a view.
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
