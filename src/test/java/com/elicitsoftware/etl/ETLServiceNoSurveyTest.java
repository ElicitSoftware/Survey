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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers {@link ETLService#shouldBuildReportingSchema(long, long)}, the guard that keeps
 * {@link ETLService#init()} from generating the reporting schema before a survey exists.
 * <p>
 * The case that matters operationally is the first one: a deployment whose database has
 * been migrated but has no survey definition imported yet. Everything the build derives
 * comes from a survey, so running it then writes an empty reporting schema -- and because
 * a non-empty {@code surveyreport.dim_section} is the marker for "already built", a survey
 * imported afterwards would never get its dimensions added. Skipping the build leaves that
 * marker clear so the next startup after an import does the real work.
 * <p>
 * These are plain unit tests rather than {@code @QuarkusTest} cases because the shared test
 * fixture always has survey_id=1 installed (see {@link ETLServiceTest}), so the no-survey
 * state cannot be reached in that JVM without tearing down data other tests depend on.
 * {@link ETLServiceTest#countSurveysSeesTheFixtureSurvey()} ties this decision function to
 * the real query against a live database.
 */
class ETLServiceNoSurveyTest {

    @Test
    @DisplayName("no survey imported yet: skip the build so a later import can still be picked up")
    void skipsWhenNoSurveyExists() {
        assertFalse(ETLService.shouldBuildReportingSchema(0, 0),
                "with no survey there is nothing to derive dimensions from; building now would "
                        + "mark the schema as built and lock out a later import");
    }

    @Test
    @DisplayName("no survey and a populated dim_section: still skip")
    void skipsWhenNoSurveyEvenIfDimSectionPopulated() {
        assertFalse(ETLService.shouldBuildReportingSchema(0, 12),
                "the survey count is the deciding factor, not leftover dimension rows");
    }

    @Test
    @DisplayName("survey imported and dim_section empty: build the reporting schema")
    void buildsWhenSurveyExistsAndSchemaNeverBuilt() {
        assertTrue(ETLService.shouldBuildReportingSchema(1, 0),
                "this is the startup immediately after a survey definition is imported");
    }

    @Test
    @DisplayName("survey imported and dim_section already populated: nothing to do")
    void skipsWhenSchemaAlreadyBuilt() {
        assertFalse(ETLService.shouldBuildReportingSchema(1, 12),
                "a populated dim_section is this schema's marker for already built");
    }

    @Test
    @DisplayName("several surveys installed: the build is driven by any survey existing")
    void buildsWithMultipleSurveys() {
        assertTrue(ETLService.shouldBuildReportingSchema(4, 0));
        assertFalse(ETLService.shouldBuildReportingSchema(4, 1));
    }
}
