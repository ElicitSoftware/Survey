package com.elicitsoftware.etl;

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

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** UC-008 BR-006: the rule a survey's reporting schema name follows. */
class ReportSchemaNamesTest {

    @Test
    void derive_lowerCasesAndPrefixes() {
        assertEquals("report_librarycardreg", ReportSchemaNames.derive("LibraryCardReg", n -> false));
    }

    @Test
    void derive_collapsesRunsOfOtherCharactersToOneUnderscoreAndTrimsTheEnds() {
        assertEquals("report_family_history_survey", ReportSchemaNames.derive("Family History Survey", n -> false));
        assertEquals("report_q3_2026_follow_up", ReportSchemaNames.derive("  Q3/2026 -- Follow-up! ", n -> false));
        assertEquals("report_caf", ReportSchemaNames.derive("Café", n -> false));
    }

    @Test
    void derive_fallsBackWhenNothingOfTheNameSurvives() {
        assertEquals("report_survey", ReportSchemaNames.derive("日本語", n -> false));
        assertEquals("report_survey", ReportSchemaNames.derive(null, n -> false));
    }

    @Test
    void derive_fitsSixtyThreeBytes() {
        String name = ReportSchemaNames.derive("x".repeat(100), n -> false);
        assertEquals(63, name.length());
        assertTrue(name.startsWith("report_x"));
    }

    @Test
    void derive_appendsACounterWhileTheNameIsTaken() {
        Set<String> taken = Set.of("report_twins", "report_twins_2");
        assertEquals("report_twins_3", ReportSchemaNames.derive("Twins", taken::contains));
    }

    @Test
    void derive_counterStillFitsSixtyThreeBytes() {
        String base = ReportSchemaNames.derive("y".repeat(100), n -> false);
        String next = ReportSchemaNames.derive("y".repeat(100), base::equals);
        assertEquals(63, next.length());
        assertTrue(next.endsWith("_2"));
    }

    @Test
    void objection_acceptsAnUnquotedLowerCaseIdentifier() {
        assertNull(ReportSchemaNames.objection("report_fhh"));
        assertNull(ReportSchemaNames.objection("_x1"));
    }

    @Test
    void objection_refusesWhatPostgresWouldQuoteOrElicitUses() {
        assertNotNull(ReportSchemaNames.objection(null));
        assertNotNull(ReportSchemaNames.objection(""));
        assertNotNull(ReportSchemaNames.objection("Report_FHH"));
        assertNotNull(ReportSchemaNames.objection("1report"));
        assertNotNull(ReportSchemaNames.objection("report fhh"));
        assertNotNull(ReportSchemaNames.objection("r".repeat(64)));
        assertNotNull(ReportSchemaNames.objection("survey"));
        assertNotNull(ReportSchemaNames.objection("surveyreport"));
        assertNotNull(ReportSchemaNames.objection("public"));
        assertNotNull(ReportSchemaNames.objection("information_schema"));
        assertNotNull(ReportSchemaNames.objection("pg_catalog"));
    }
}
