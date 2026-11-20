/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 - 2026 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */
package com.elicitsoftware.flyway;

import com.elicitsoftware.PostgresTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * UC-008 BR-013 (Survey V024): the date dimension holds the 1970-01-01 sentinel and every day
 * from 2020-01-01 to 2050-12-31, and every row, seeded or generated, follows one set of column
 * rules.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class DimDateSchemaTest {

    /** 1970-01-01 plus the 11,323 days of 2020-01-01 .. 2050-12-31 (eight leap years). */
    private static final long EXPECTED_ROWS = 1 + 11_323;

    @Inject
    EntityManager em;

    private long count(String sql) {
        return ((Number) em.createNativeQuery(sql).getSingleResult()).longValue();
    }

    @Test
    void given_theDimension_when_counted_then_itSpansTheSentinelAnd2020Through2050() {
        // UC-008 BR-013: the span, with no gap and nothing beyond it
        assertEquals(EXPECTED_ROWS, count("SELECT COUNT(*) FROM surveyreport.dim_date"));
        assertEquals(19700101, count("SELECT MIN(datekey) FROM surveyreport.dim_date"));
        assertEquals(20200101, count("SELECT MIN(datekey) FROM surveyreport.dim_date WHERE datekey > 19700101"));
        assertEquals(20501231, count("SELECT MAX(datekey) FROM surveyreport.dim_date"));
        assertEquals(1, count("SELECT COUNT(*) FROM surveyreport.dim_date WHERE fulldate = DATE '2045-03-04'"));
        assertEquals(0, count("SELECT COUNT(*) FROM surveyreport.dim_date WHERE fulldate > DATE '2050-12-31'"));
    }

    @Test
    void given_everyRow_when_recomputedFromItsDate_then_everyColumnAgrees() {
        // UC-008 BR-013: the seeded decade and the generated years follow the same rules, and
        // Saturday and Sunday are the weekend (V004 had Friday and Saturday)
        assertEquals(0, count("""
                SELECT COUNT(*) FROM surveyreport.dim_date
                CROSS JOIN LATERAL (
                    SELECT ((extract(month FROM fulldate)::integer + 5) % 12) + 1 AS fiscal_month,
                           extract(year FROM fulldate)::integer
                               + CASE WHEN extract(month FROM fulldate) >= 7 THEN 1 ELSE 0 END AS fiscal_year
                ) AS f
                WHERE datekey <> 19700101 AND (
                       datekey <> to_char(fulldate, 'YYYYMMDD')::integer
                    OR datename <> to_char(fulldate, 'YYYY/MM/DD')
                    OR dayofweek <> extract(dow FROM fulldate) + 1
                    OR daynameofweek <> trim(to_char(fulldate, 'Day'))
                    OR dayofmonth <> extract(day FROM fulldate)
                    OR dayofyear <> extract(doy FROM fulldate)
                    OR weekdayweekend <> CASE WHEN extract(dow FROM fulldate) IN (0, 6) THEN 'Weekend' ELSE 'Weekday' END
                    OR weekofyear <> (extract(doy FROM fulldate)::integer - 1
                                      + extract(dow FROM date_trunc('year', fulldate))::integer) / 7 + 1
                    OR monthname <> trim(to_char(fulldate, 'Month'))
                    OR monthofyear <> extract(month FROM fulldate)
                    OR islastdayofmonth <> CASE WHEN fulldate = (date_trunc('month', fulldate) + INTERVAL '1 month - 1 day')::date THEN 'Y' ELSE 'N' END
                    OR calendarquarter <> extract(quarter FROM fulldate)
                    OR calendaryear <> extract(year FROM fulldate)
                    OR calendaryearmonth <> to_char(fulldate, 'YYYY-MM')
                    OR calendaryearqtr <> to_char(fulldate, 'YYYY') || 'Q' || extract(quarter FROM fulldate)::integer
                    OR fiscalmonthofyear <> f.fiscal_month
                    OR fiscalquarter <> (f.fiscal_month - 1) / 3 + 1
                    OR fiscalyear <> f.fiscal_year
                    OR fiscalyearmonth <> 'FY' || f.fiscal_year || '-' || lpad(f.fiscal_month::text, 2, '0')
                    OR fiscalyearqtr <> 'FY' || f.fiscal_year || 'Q' || ((f.fiscal_month - 1) / 3 + 1)
                )
                """));
        assertEquals(0, count("SELECT COUNT(*) FROM surveyreport.dim_date WHERE daynameofweek = 'Friday' AND weekdayweekend = 'Weekend'"));
        assertEquals(0, count("SELECT COUNT(*) FROM surveyreport.dim_date WHERE daynameofweek = 'Sunday' AND weekdayweekend = 'Weekday'"));
    }
}
