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
-- V024: surveyreport.dim_date through 2050-12-31 (UC-008 BR-013, issue #128).
--
-- V004 seeded the date dimension by hand for 2020-01-01 .. 2029-12-31 plus the 1970-01-01
-- sentinel, so from 2030 every created, first-access and finalized date would have shown as a
-- null label in fact_respondents_view. This migration generates the calendar instead, with the
-- same column rules the seed used, and the span is one parameter: the two bounds of the
-- generate_series below. Rows that already exist are left alone (ON CONFLICT DO NOTHING), so
-- the migration is the same on the greenfield track and on the V2 upgrade track, and running
-- it on a database that has it already changes nothing.
--
-- Column rules (as in V004):
--   datekey            YYYYMMDD as an integer
--   datename           YYYY/MM/DD
--   dayofweek          1 = Sunday .. 7 = Saturday
--   weekofyear         weeks start on Sunday; week 1 is the week holding January 1
--   islastdayofmonth   'Y' / 'N'
--   fiscal*            the fiscal year starts on July 1 (July = fiscal month 1, FY = calendar
--                      year + 1 from July); labels FY2021-01 and FY2021Q1
--   weekdayweekend     Saturday and Sunday are 'Weekend'. V004 had this wrong (Friday and
--                      Saturday were 'Weekend', Sunday was 'Weekday'); the UPDATE at the end
--                      corrects the seeded rows.
--
INSERT INTO surveyreport.dim_date (
    datekey, fulldate, datename, dayofweek, daynameofweek, dayofmonth, dayofyear, weekdayweekend,
    weekofyear, monthname, monthofyear, islastdayofmonth, calendarquarter, calendaryear,
    calendaryearmonth, calendaryearqtr, fiscalmonthofyear, fiscalquarter, fiscalyear,
    fiscalyearmonth, fiscalyearqtr)
SELECT
    to_char(d, 'YYYYMMDD')::integer,
    d,
    to_char(d, 'YYYY/MM/DD'),
    (extract(dow FROM d) + 1)::smallint,
    trim(to_char(d, 'Day')),
    extract(day FROM d)::smallint,
    extract(doy FROM d)::smallint,
    CASE WHEN extract(dow FROM d) IN (0, 6) THEN 'Weekend' ELSE 'Weekday' END,
    ((extract(doy FROM d)::integer - 1 + extract(dow FROM date_trunc('year', d))::integer) / 7 + 1)::smallint,
    trim(to_char(d, 'Month')),
    extract(month FROM d)::smallint,
    CASE WHEN d = (date_trunc('month', d) + INTERVAL '1 month - 1 day')::date THEN 'Y' ELSE 'N' END,
    extract(quarter FROM d)::smallint,
    extract(year FROM d)::smallint,
    to_char(d, 'YYYY-MM'),
    to_char(d, 'YYYY') || 'Q' || extract(quarter FROM d)::integer,
    f.fiscal_month::smallint,
    ((f.fiscal_month - 1) / 3 + 1)::smallint,
    f.fiscal_year,
    'FY' || f.fiscal_year || '-' || lpad(f.fiscal_month::text, 2, '0'),
    'FY' || f.fiscal_year || 'Q' || ((f.fiscal_month - 1) / 3 + 1)
FROM (SELECT g::date AS d FROM generate_series(DATE '2020-01-01', DATE '2050-12-31', INTERVAL '1 day') AS g) AS days
CROSS JOIN LATERAL (
    SELECT ((extract(month FROM d)::integer + 5) % 12) + 1 AS fiscal_month,
           extract(year FROM d)::integer + CASE WHEN extract(month FROM d) >= 7 THEN 1 ELSE 0 END AS fiscal_year
) AS f
ON CONFLICT (datekey) DO NOTHING;

-- The seeded rows: Saturday and Sunday are the weekend, nothing else is.
UPDATE surveyreport.dim_date
   SET weekdayweekend = CASE WHEN dayofweek IN (1, 7) THEN 'Weekend' ELSE 'Weekday' END
 WHERE weekdayweekend <> CASE WHEN dayofweek IN (1, 7) THEN 'Weekend' ELSE 'Weekday' END;
